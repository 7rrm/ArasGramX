package tw.nekomimi.nekogram;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.os.Build;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MeeroX v167 (his approved Layer-1+3 "vault", the same shelter scheme his
 * WiFi app used - implemented with first-party source control instead of a
 * black-box packer).
 *
 * The APK carries Telegram's 24 MB native heart (libtmessages.N.so) only as
 * an encrypted blob at assets/meero_vault/core.enc. On first launch this
 * loader decrypts it with AES-256-GCM into the app's private dir and loads
 * it from an absolute path. Pieces of the key: half is a shuffled seed that
 * lives in our native libmeerovault.so (kept plain, like the packer's own
 * shell), half is derived from the RELEASE SIGNING fingerprint - so a
 * re-signed clone produces garbage plaintext and dies at load, on top of
 * the v165 guard warning.
 *
 * Layout of core.enc:
 *   [8B  "MVLT0001"] [12B GCM-IV] [32B keyMask = masterKey ^ SHA256(seed | fingerprint)]
 *   [ciphertext + 16B GCM tag]
 *
 * If the blob is absent (debug builds, or any build that skipped the pack
 * step), every public entry returns false and the caller continues with the
 * stock System.loadLibrary path - zero behavioural change.
 *
 * Passive by design: anti-debug currently HARDENS (prctl dumpable-off,
 * TracerPid read surfaced to logs) but never kills a running user - false
 * positives on exotic ROMs must never punish his audience (his standing
 * order: users only enjoy the app).
 */
public final class MeeroVault {

    private MeeroVault() {
    }

    public static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    private static final String ASSET = "meero_vault/core.enc";
    private static final byte[] MAGIC = {'M', 'V', 'L', 'T', '0', '0', '0', '1'};

    private static volatile boolean tried;
    private static volatile boolean loaded;
    private static volatile File cachedSo;

    /**
     * Attempts the vault load of libtmessages. Returns true ONLY when the
     * blob existed AND was successfully decrypted+loaded. Any anomaly falls
     * back to false so the caller uses the stock loadLibrary path.
     */
    public static synchronized boolean tryLoad(Context context) {
        if (loaded) {
            return true;
        }
        if (tried) {
            return false;
        }
        tried = true;
        try {
            if (context == null) {
                return false;
            }
            sweepLegacyDexCache(context);
            final File outDir = new File(context.getFilesDir(), "vaultlibs");
            //noinspection ResultOfMethodCallIgnored
            outDir.mkdirs();
            final File outSo = new File(outDir, "libtmessages.49.so");
            // stamp invalidates the cache whenever the installed apk changes
            final File stamp = new File(outDir, ".stamp");
            final String apkStamp = String.valueOf(context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).lastUpdateTime);

            final byte[] blob = readAsset(context.getAssets());
            if (blob != null) {
                if (!(stamp.exists() && stamp.isFile() && apkStamp.equals(readFirstLine(stamp)) && outSo.exists() && outSo.length() > 1000000)) {
                    final byte[] plain = decrypt(blob, fingerprintOf(context));
                    if (plain == null || plain.length < 1000000) {
                        FileLog.e(new Throwable("meerovault: decrypt failed"));
                        return false;
                    }
                    FileOutputStream fos = new FileOutputStream(outSo);
                    try {
                        fos.write(plain);
                    } finally {
                        fos.close();
                    }
                    //noinspection ResultOfMethodCallIgnored
                    outSo.setReadable(true, false);
                    //noinspection ResultOfMethodCallIgnored
                    outSo.setExecutable(true, false);
                    FileOutputStream st = new FileOutputStream(stamp);
                    try {
                        st.write(apkStamp.getBytes("UTF-8"));
                    } finally {
                        st.close();
                    }
                }
                System.load(outSo.getAbsolutePath());
                loaded = true;
                cachedSo = outSo;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("meerovault: native core loaded from vault");
                }
                return true;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return false;
    }

    /** Direct System.load path used by the tiny C helper libraries we ship. */
    public static boolean vaultActive() {
        return loaded;
    }

    /**
     * v181 (batch 0): the runtime DEX vault is retired - boot is 100%
     * stock again. Devices that ran v168-v180 still carry the decrypted
     * 42 MB files/vaultdex cache (vault.apk + phase markers + .bootlog).
     * Sweep it once here; after that the exists() check costs ~nothing.
     */
    private static void sweepLegacyDexCache(Context context) {
        try {
            final File dir = new File(context.getFilesDir(), "vaultdex");
            if (!dir.exists()) {
                return;
            }
            final File[] kids = dir.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    if (k.isFile()) {
                        //noinspection ResultOfMethodCallIgnored
                        k.delete();
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        } catch (Throwable ignored) {
        }
    }

    private static byte[] readAsset(AssetManager am) {
        try {
            InputStream is = am.open(ASSET, AssetManager.ACCESS_BUFFER);
            try {
                final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(26 * 1024 * 1024);
                final byte[] b = new byte[256 * 1024];
                int n;
                while ((n = is.read(b)) >= 0) {
                    bos.write(b, 0, n);
                }
                final byte[] all = bos.toByteArray();
                for (int i = 0; i < MAGIC.length; i++) {
                    if (all.length < 64 || all[i] != MAGIC[i]) {
                        return null; // not a vault build
                    }
                }
                return all;
            } finally {
                is.close();
            }
        } catch (Throwable t) {
            return null; // blob absent -> plain build
        }
    }

    private static byte[] decrypt(byte[] blob, String fingerprint) {
        try {
            final byte[] seed = tw.nekomimi.nekogram.MeeroVaultSeed.fingerprintSeed();
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(seed);
            sha.update(fingerprint.getBytes("UTF-8"));
            final byte[] wrap = sha.digest();

            final byte[] iv = Arrays.copyOfRange(blob, 8, 20);
            final byte[] mask = Arrays.copyOfRange(blob, 20, 52);
            final byte[] key = new byte[32];
            for (int i = 0; i < 32; i++) {
                key[i] = (byte) (mask[i] ^ wrap[i]);
            }
            final Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return c.doFinal(blob, 52, blob.length - 52);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    @SuppressLint("PackageManagerGetSignatures")
    private static String fingerprintOf(Context context) {
        try {
            final PackageManager pm = context.getPackageManager();
            final String pkg = context.getPackageName();
            final Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo != null && pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : (pi.signingInfo != null ? pi.signingInfo.getSigningCertificateHistory() : null);
            } else {
                sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            if (sigs == null || sigs.length == 0) {
                return OFFICIAL_FINGERPRINT;
            }
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] d = md.digest(sigs[0].toByteArray());
            final StringBuilder sb = new StringBuilder(d.length * 3 - 1);
            for (int i = 0; i < d.length; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format(Locale.US, "%02X", d[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            return OFFICIAL_FINGERPRINT;
        }
    }

    private static String readFirstLine(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            try {
                final byte[] b = new byte[(int) Math.min(f.length(), 64)];
                final int n = fis.read(b);
                return n <= 0 ? "" : new String(b, 0, n, "UTF-8").trim();
            } finally {
                fis.close();
            }
        } catch (Throwable t) {
            return "";
        }
    }
}
