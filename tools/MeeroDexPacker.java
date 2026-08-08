/*
 * MeeroX v168 - CI packer for the DEX vault (his option A).
 *
 * Runs after assembleRelease, BEFORE the native (.so) pack step:
 *   1. collects every root classesN.dex from the built APK,
 *   2. packs them (ordered, STORED) into one in-memory zip - that zip is
 *      exactly the archive DexClassLoader will open on-device,
 *   3. encrypts it with AES-256-GCM (per-build random master key + IV,
 *      keyMask = masterKey ^ SHA256(seed | signing fingerprint) - the
 *      SAME derivation the stub performs at boot; a re-signed clone
 *      derives garbage and the GCM tag check kills it),
 *   4. rewrites the APK without any classesN.dex, adding:
 *        - classes.dex        = the tiny STUB (MeeroDexApp +
 *                               MeeroDexFactory), produced by javac+d8
 *                               one step earlier in the workflow,
 *        - assets/meero_vault/dex.enc = the encrypted archive,
 *   5. strips stale v1 signature files (apksigner re-signs last),
 *   6. and self-tests BEFORE writing: in-memory decrypt with the exact
 *      phone-side math must yield a zip whose dex entries hash-match the
 *      originals byte-for-byte, or it exits non-zero and the build fails
 *      loudly instead of shipping an unbootable APK.
 *
 * Blob layout (must match MeeroDexApp):
 *   [8B "MVDX0001"] [12B GCM-IV] [32B keyMask] [ciphertext + 16B tag]
 *
 * Usage: java MeeroDexPacker <input.apk> <stub-classes.dex> <output.apk>
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class MeeroDexPacker {

    // Raw seed - identical to what libmeerovault.so unshuffles on-device
    // (jni/meerovault/meerovault.c + tools/MeeroVaultPacker.java).
    private static final byte[] SEED = {
            (byte) 0x1A, (byte) 0xFF, (byte) 0xAF, (byte) 0x37, (byte) 0x08, (byte) 0x78, (byte) 0xA7, (byte) 0xA6,
            (byte) 0xF6, (byte) 0xD3, (byte) 0x69, (byte) 0x71, (byte) 0x22, (byte) 0xB5, (byte) 0x35, (byte) 0x02,
            (byte) 0xEF, (byte) 0xC0, (byte) 0xA8, (byte) 0x41, (byte) 0xAD, (byte) 0x68, (byte) 0xC9, (byte) 0xBC,
            (byte) 0xE7, (byte) 0x62, (byte) 0x4F, (byte) 0xF4, (byte) 0x1B, (byte) 0x66, (byte) 0xE5, (byte) 0xEC
    };

    private static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    private static final String BLOB = "assets/meero_vault/dex.enc";
    private static final byte[] MAGIC = {'M', 'V', 'D', 'X', '0', '0', '0', '1'};

    // v171 wall A ((integrity seal)): an entry inside the ENCRYPTED archive
    // holding SHA-256(stub classes.dex) || SHA-256(meerovault.so). The boot
    // stub re-hashes its own loader bytes every cold start and dies quietly
    // on mismatch - a patched/copycat loader can never boot (and the seal
    // itself is unforgeable without seed + signing fingerprint, being
    // inside the AES-GCM container).
    private static final String META_ENTRY = "vault_meta";
    private static final String VAULT_LIB_ENTRY = "lib/arm64-v8a/libmeerovault.so";

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: MeeroDexPacker <input.apk> <stub-classes.dex> <output.apk>");
            System.exit(2);
        }
        final File in = new File(args[0]);
        final File stubDex = new File(args[1]);
        final File out = new File(args[2]);

        final byte[] stub = Files.readAllBytes(stubDex.toPath());
        if (stub.length < 1000 || stub.length > 1024 * 1024) {
            fail("stub dex size looks wrong: " + stub.length);
            return;
        }
        if (!(stub[0] == 'd' && stub[1] == 'e' && stub[2] == 'x')) {
            fail("stub is not a dex file");
            return;
        }

        // ---- gather the real classesN.dex + the seed library --------------
        final List<String> dexNames = new ArrayList<String>();
        final List<byte[]> dexBytes = new ArrayList<byte[]>();
        byte[] vaultLib = null;
        try (ZipFile zf = new ZipFile(in)) {
            final Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                final ZipEntry e = en.nextElement();
                if (isDexEntry(e.getName())) {
                    dexNames.add(e.getName());
                    dexBytes.add(readAll(zf.getInputStream(e), e.getSize()));
                } else if (VAULT_LIB_ENTRY.equals(e.getName())) {
                    vaultLib = readAll(zf.getInputStream(e), e.getSize());
                }
            }
        }
        if (vaultLib == null || vaultLib.length < 1000) {
            fail("meerovault library missing in " + in + " - refusing to seal");
            return;
        }
        final MessageDigest metasha = MessageDigest.getInstance("SHA-256");
        final byte[] stubHash = metasha.digest(stub);
        metasha.reset();
        final byte[] libHash = metasha.digest(vaultLib);
        final byte[] meta = new byte[64];
        System.arraycopy(stubHash, 0, meta, 0, 32);
        System.arraycopy(libHash, 0, meta, 32, 32);
        log("seal: stub " + stub.length + " B + vault lib " + vaultLib.length + " B hashed into " + META_ENTRY);
        if (dexNames.isEmpty()) {
            fail("no classesN.dex found in " + in);
            return;
        }
        // canonical order: classes.dex, classes2.dex, classes3.dex, ...
        final List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < dexNames.size(); i++) {
            order.add(i);
        }
        Collections.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Integer.compare(dexNumber(dexNames.get(a)), dexNumber(dexNames.get(b)));
            }
        });

        long plainTotal = 0;
        final MessageDigest plainDigest = MessageDigest.getInstance("SHA-256");
        // ---- build the vault archive (the future vault.apk) --------------
        final ByteArrayOutputStream archiveBuf = new ByteArrayOutputStream(64 * 1024 * 1024);
        try (ZipOutputStream az = new ZipOutputStream(archiveBuf)) {
            // integrity seal first: the boot stub reads it before anything else
            final ZipEntry me = new ZipEntry(META_ENTRY);
            az.putNextEntry(stored(me, meta));
            az.write(meta);
            az.closeEntry();
            for (int idx : order) {
                final byte[] d = dexBytes.get(idx);
                plainDigest.update(d);
                plainTotal += d.length;
                final ZipEntry e = new ZipEntry(dexNames.get(idx));
                az.putNextEntry(stored(e, d));
                az.write(d);
                az.closeEntry();
                log("vault <- " + dexNames.get(idx) + " (" + d.length + " B)");
            }
        }
        final byte[] plain = archiveBuf.toByteArray();
        final byte[] plainHash = plainDigest.digest();
        log("dex total " + plainTotal + " B, archive " + plain.length + " B");

        // ---- encrypt ------------------------------------------------------
        final byte[] masterKey = new byte[32];
        final byte[] iv = new byte[12];
        new SecureRandom().nextBytes(masterKey);
        new SecureRandom().nextBytes(iv);
        final byte[] mask = xor(masterKey, wrapKey());

        final Cipher enc = Cipher.getInstance("AES/GCM/NoPadding");
        enc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, iv));
        final byte[] ct = enc.doFinal(plain);

        final byte[] blob = new byte[8 + 12 + 32 + ct.length];
        System.arraycopy(MAGIC, 0, blob, 0, 8);
        System.arraycopy(iv, 0, blob, 8, 12);
        System.arraycopy(mask, 0, blob, 20, 32);
        System.arraycopy(ct, 0, blob, 52, ct.length);

        // ---- self-test with the exact phone-side math --------------------
        final MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(SEED);
        sha.update(OFFICIAL_FINGERPRINT.getBytes("UTF-8"));
        final byte[] key2 = xor(Arrays.copyOfRange(blob, 20, 52), sha.digest());
        final Cipher dec = Cipher.getInstance("AES/GCM/NoPadding");
        dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key2, "AES"),
                new GCMParameterSpec(128, Arrays.copyOfRange(blob, 8, 20)));
        final byte[] round = dec.doFinal(blob, 52, blob.length - 52);

        final MessageDigest roundDigest = MessageDigest.getInstance("SHA-256");
        int roundDexCount = 0;
        byte[] roundMeta = null;
        try (ZipInputStream rz = new ZipInputStream(new ByteArrayInputStream(round))) {
            final byte[] bb = new byte[256 * 1024];
            ZipEntry e;
            while ((e = rz.getNextEntry()) != null) {
                if (isDexEntry(e.getName())) {
                    // note: read WITHOUT closing rz - getNextEntry needs it open
                    while (true) {
                        final int rn = rz.read(bb);
                        if (rn < 0) {
                            break;
                        }
                        roundDigest.update(bb, 0, rn);
                    }
                    roundDexCount++;
                } else if (META_ENTRY.equals(e.getName())) {
                    final ByteArrayOutputStream mb = new ByteArrayOutputStream(64);
                    while (true) {
                        final int rn = rz.read(bb);
                        if (rn < 0) {
                            break;
                        }
                        mb.write(bb, 0, rn);
                    }
                    roundMeta = mb.toByteArray();
                }
            }
        }
        if (roundDexCount != dexNames.size() || !MessageDigest.isEqual(plainHash, roundDigest.digest())) {
            fail("self-test FAILED: round-trip mismatch - refusing to pack");
            return;
        }
        if (roundMeta == null || !Arrays.equals(roundMeta, meta)) {
            fail("self-test FAILED: integrity seal mismatch - refusing to pack");
            return;
        }
        log("self-test ok: blob(" + blob.length + " B) decrypts to all " + dexNames.size()
                + " dex files byte-exact + seal intact");

        // ---- rewrite the APK ----------------------------------------------
        try (ZipFile zf = new ZipFile(in);
             OutputStream fos = Files.newOutputStream(out.toPath());
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // stub first: some installers expect classes.dex at the head
            final ZipEntry se = new ZipEntry("classes.dex");
            zos.putNextEntry(se);
            zos.write(stub);
            zos.closeEntry();
            log("add : classes.dex (stub, " + stub.length + " B)");

            final Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                final ZipEntry e = en.nextElement();
                final String name = e.getName();
                if (isDexEntry(name) || isV1Signature(name)) {
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
            log("add : " + BLOB + " (" + blob.length + " B, STORED)");
        }

        log("packed OK -> " + out);
    }

    private static boolean isDexEntry(String name) {
        if (name.indexOf('/') >= 0) {
            return false;
        }
        return name.matches("classes[0-9]*\\.dex");
    }

    private static int dexNumber(String name) {
        // classes.dex -> 1, classes2.dex -> 2, ...
        final String mid = name.substring("classes".length(), name.length() - ".dex".length());
        return mid.isEmpty() ? 1 : Integer.parseInt(mid);
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
            final ByteArrayOutputStream bos = new ByteArrayOutputStream(size > 0 && size < 128 * 1024 * 1024 ? (int) size : 1 << 20);
            final byte[] b = new byte[256 * 1024];
            int n;
            while ((n = in.read(b)) >= 0) {
                bos.write(b, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static void log(String m) {
        System.out.println("[MeeroDexPacker] " + m);
    }

    private static void fail(String m) {
        System.err.println("[MeeroDexPacker] ERROR: " + m);
        System.exit(1);
    }
}
