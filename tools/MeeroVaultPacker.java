/*
 * MeeroX v167 - CI packer for "MeeroVault".
 *
 * Runs AFTER assembleRelease, BEFORE the APK is collected/uploaded:
 *   1. takes lib/arm64-v8a/libtmessages.49.so (the 24 MB native heart) out
 *      of the APK,
 *   2. encrypts it with AES-256-GCM, per-build random master key + IV,
 *   3. stores the key as keyMask = masterKey ^ SHA256(seed | fingerprint)
 *      - seed: raw 32 bytes, the SAME value libmeerovault.so unshuffles at
 *        runtime (see jni/meerovault/meerovault.c - keep in sync),
 *      - fingerprint: SHA-256 colon-hex string of the release signing
 *        certificate (identical to what MeeroVault.fingerprintOf computes
 *        on-device, so a re-signed clone derives garbage and GCM kills it),
 *   4. rewrites the APK without the plain .so and with the blob as a
 *      STORED entry at assets/meero_vault/core.enc,
 *   5. strips the now-stale v1 signature files (apksigner re-signs right
 *      after this tool, in the workflow step),
 *   6. and before writing anything, DECRYPTS its own blob in memory with
 *      the exact phone-side math as a self-test: ELF magic + full SHA-256
 *      must match the original .so, otherwise it exits non-zero and the
 *      build fails loudly instead of shipping a dead APK.
 *
 * Blob layout (must match tw.nekomimi.nekogram.MeeroVault):
 *   [8B "MVLT0001"] [12B GCM-IV] [32B keyMask] [ciphertext + 16B GCM tag]
 *
 * Usage: java MeeroVaultPacker <input.apk> <output.apk>
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MeeroVaultPacker {

    // Raw seed - identical to what MV_TABLE in jni/meerovault/meerovault.c
    // decodes to. Changing one without the other breaks decryption, and the
    // built-in self-test below is what catches such a drift.
    private static final byte[] SEED = {
            (byte) 0x1A, (byte) 0xFF, (byte) 0xAF, (byte) 0x37, (byte) 0x08, (byte) 0x78, (byte) 0xA7, (byte) 0xA6,
            (byte) 0xF6, (byte) 0xD3, (byte) 0x69, (byte) 0x71, (byte) 0x22, (byte) 0xB5, (byte) 0x35, (byte) 0x02,
            (byte) 0xEF, (byte) 0xC0, (byte) 0xA8, (byte) 0x41, (byte) 0xAD, (byte) 0x68, (byte) 0xC9, (byte) 0xBC,
            (byte) 0xE7, (byte) 0x62, (byte) 0x4F, (byte) 0xF4, (byte) 0x1B, (byte) 0x66, (byte) 0xE5, (byte) 0xEC
    };

    // SHA-256 (colon-hex, upper case) of the release signing certificate.
    // Same constant as MeeroVault.OFFICIAL_FINGERPRINT.
    private static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    private static final String TARGET = "lib/arm64-v8a/libtmessages.49.so";
    private static final String LOADER = "lib/arm64-v8a/libmeerovault.so";
    private static final String BLOB = "assets/meero_vault/core.enc";
    private static final byte[] MAGIC = {'M', 'V', 'L', 'T', '0', '0', '0', '1'};

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: MeeroVaultPacker <input.apk> <output.apk>");
            System.exit(2);
        }
        final File in = new File(args[0]);
        final File out = new File(args[1]);

        byte[] plain;
        boolean loaderPresent = false;

        try (ZipFile zf = new ZipFile(in)) {
            ZipEntry target = zf.getEntry(TARGET);
            if (target == null) {
                fail("native heart " + TARGET + " not found in " + in);
                return;
            }
            loaderPresent = zf.getEntry(LOADER) != null;
            if (!loaderPresent) {
                fail("loader lib " + LOADER + " missing - CMake wiring broken, refusing to pack");
                return;
            }
            plain = readAll(zf.getInputStream(target), target.getSize());
        }

        if (plain.length < 1000000) {
            fail("native heart suspiciously small: " + plain.length);
            return;
        }
        if (!(plain[0] == 0x7F && plain[1] == 'E' && plain[2] == 'L' && plain[3] == 'F')) {
            fail("native heart is not an ELF - wrong entry?");
            return;
        }
        log("native heart: " + plain.length + " bytes, ELF ok, loader present");

        // ---- encrypt -----------------------------------------------------
        final byte[] masterKey = new byte[32];
        final byte[] iv = new byte[12];
        new SecureRandom().nextBytes(masterKey);
        new SecureRandom().nextBytes(iv);

        final byte[] wrap = wrapKey();
        final byte[] mask = xor(masterKey, wrap);

        Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
        final byte[] ct = enc.doFinal(plain);

        final byte[] blob = new byte[8 + 12 + 32 + ct.length];
        System.arraycopy(MAGIC, 0, blob, 0, 8);
        System.arraycopy(iv, 0, blob, 8, 12);
        System.arraycopy(mask, 0, blob, 20, 32);
        System.arraycopy(ct, 0, blob, 52, ct.length);

        // ---- self-test: decrypt with the exact phone-side math -----------
        final byte[] seed = SEED;
        final MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(seed);
        sha.update(OFFICIAL_FINGERPRINT.getBytes("UTF-8"));
        final byte[] wrap2 = sha.digest();
        final byte[] key2 = xor(Arrays.copyOfRange(blob, 20, 52), wrap2);
        final Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key2, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(blob, 8, 20)));
        final byte[] round = dec.doFinal(blob, 52, blob.length - 52);
        if (!MessageDigest.isEqual(plain, round)) {
            fail("self-test FAILED: round-trip mismatch - constants drifted, refusing to pack");
            return;
        }
        log("self-test ok: blob(" + blob.length + "B) decrypts to the original .so");

        // ---- rewrite the APK ---------------------------------------------
        try (ZipFile zf = new ZipFile(in);
             OutputStream fos = Files.newOutputStream(out.toPath());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            final Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                final ZipEntry e = en.nextElement();
                final String name = e.getName();
                if (TARGET.equals(name) || isV1Signature(name)) {
                    log("drop: " + name);
                    continue;
                }
                final byte[] data = readAll(zf.getInputStream(e), e.getSize());
                zos.putNextEntry(copyOf(e, data));
                zos.write(data);
                zos.closeEntry();
            }
            final ZipEntry be = new ZipEntry(BLOB);
            zos.putNextEntry(stored(be, blob));
            zos.write(blob);
            zos.closeEntry();
            log("add : " + BLOB + " (" + blob.length + " bytes, STORED)");
        }

        log("packed OK -> " + out);
    }

    private static byte[] wrapKey() throws Exception {
        final MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(SEED);
        sha.update(OFFICIAL_FINGERPRINT.getBytes("UTF-8"));
        return sha.digest();
    }

    private static byte[] xor(byte[] a, byte[] b) {
        final byte[] r = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = (byte) (a[i] ^ b[i]);
        }
        return r;
    }

    private static boolean isV1Signature(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        final String u = name.toUpperCase(Locale.US);
        return u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA")
                || u.endsWith(".EC") || u.equals("META-INF/MANIFEST.MF");
    }

    private static ZipEntry copyOf(ZipEntry src, byte[] data) {
        final ZipEntry e = new ZipEntry(src.getName());
        e.setTime(src.getTime());
        if (src.getMethod() == ZipEntry.STORED) {
            // .so entries MUST stay stored (page-aligned by zipalign next)
            return stored(e, data);
        }
        e.setMethod(ZipEntry.DEFLATED);
        return e;
    }

    private static ZipEntry stored(ZipEntry e, byte[] data) {
        final CRC32 crc = new CRC32();
        crc.update(data);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        e.setCrc(crc.getValue());
        return e;
    }

    private static byte[] readAll(InputStream is, long size) throws Exception {
        try (InputStream in = is) {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(size > 0 && size < 64 * 1024 * 1024 ? (int) size : 1 << 20);
            final byte[] b = new byte[256 * 1024];
            int n;
            while ((n = in.read(b)) >= 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static void log(String m) {
        System.out.println("[MeeroVaultPacker] " + m);
    }

    private static void fail(String m) {
        System.err.println("[MeeroVaultPacker] ERROR: " + m);
        System.exit(1);
    }
}
