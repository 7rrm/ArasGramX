package tw.nekomimi.nekogram;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.Signature;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.DexClassLoader;

/**
 * MeeroX v171 - DEX vault stub (whole-code encryption, the same scheme
 * his WiFi app used), now with two extra walls he ordered:
 *
 *   A) VAULT INTEGRITY META ("ختم سلامة الخزنة"): the encrypted archive
 *      carries a `vault_meta` entry = SHA-256(stub classes.dex) ||
 *      SHA-256(lib/arm64-v8a/libmeerovault.so), embedded at PACK time by
 *      MeeroDexPacker. Every boot this stub hashes its own dex + the
 *      seed library straight out of the installed APK and compares.
 *      Mismatch => the loader was tampered => silent death
 *      (killProcess + exit; nothing on screen - his "invisible" law).
 *      Unreadable components on exotic ROMs => check skipped, never a
 *      false kill for real users. The meta sits INSIDE the AES-GCM
 *      archive, so it cannot be forged without seed + fingerprint.
 *
 *   B) PREP SCREEN ("شاشة تحضير"): on the first boot after an update
 *      (cold vault cache) the stub launches the framework-only
 *      MeeroBootActivity in the separate :meeroboot process BEFORE the
 *      heavy decrypt+verify work, showing real decrypt percentage and a
 *      one-line "once per update" note. Main process keeps the proven
 *      v169 synchronous path (providers install before Application
 *      .onCreate so deferring is architecturally impossible), while the
 *      visible window now absorbs the wait => no more one-time ANR.
 *      Daily boots skip all of this entirely (stamp cache).
 *
 * Everything else is byte-compatible with v168/v169: STREAMING decrypt,
 * stamp cache by lastUpdateTime, read-only vault before load (the
 * v168 crash fix), InMemoryDexClassLoader fallback, full application
 * swap (mOuterContext / mInitialApplication / mAllApplications /
 * LoadedApk.mApplication / ApplicationInfo.className x2).
 *
 * Key derivation: SHA256(seed | release signing fingerprint) XOR
 * keyMask - a re-signed clone derives garbage and GCM kills it.
 */
public class MeeroDexApp extends Application {

    private static final String TAG = "MeeroDex";
    private static final String REAL_APP = "org.telegram.messenger.ApplicationLoader";
    private static final String ASSET = "meero_vault/dex.enc";
    private static final String VAULT_LIB_ENTRY = "lib/arm64-v8a/libmeerovault.so";
    private static final byte[] MAGIC = {'M', 'V', 'D', 'X', '0', '0', '0', '1'};

    public static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    private static volatile boolean vaultReady;
    private static byte[] seedCache;
    private File markerDir;

    private static native byte[] seedNative(); // served by libmeerovault.so

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            ensureLoaded(base);
        } catch (Throwable t) {
            Log.e(TAG, "dex vault load failed - nothing else we can do here", t);
            vaultReady = false;
        }
    }

    private static void ensureLoaded(Context base) throws Exception {
        if (vaultReady) {
            return;
        }

        // 0. plain build? (no blob -> completely stock boot)
        InputStream in;
        long blobTotal = -1;
        try {
            try {
                final AssetFileDescriptor fd = base.getAssets().openFd(ASSET);
                blobTotal = fd.getLength();
                fd.close();
            } catch (Throwable t) {
                blobTotal = -1; // not STORED-readable -> progress shown without %
            }
            in = base.getAssets().open(ASSET, AssetManager.ACCESS_STREAMING);
        } catch (Throwable t) {
            return;
        }

        final File dir = new File(base.getFilesDir(), "vaultdex");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        final File vault = new File(dir, "vault.apk");
        final File stamp = new File(dir, ".stamp");
        final String apkStamp = String.valueOf(base.getPackageManager()
                .getPackageInfo(base.getPackageName(), 0).lastUpdateTime);

        // integrity capture FIRST: hash our own stub classes.dex + the
        // seed library straight from the installed APK (cheap: ~16 KB).
        final byte[][] componentHashes = captureComponentHashes(base);

        final boolean cached = stamp.exists() && vault.exists()
                && vault.length() > 1000000 && apkStamp.equals(readText(stamp));
        if (!cached) {
            if (seedEnsure() == null) {
                throw new IllegalStateException("seed lib unavailable");
            }
            maybeLaunchPrep(base, dir);
            final File tmp = new File(dir, "vault.tmp");
            try {
                decrypt(in, fingerprintOf(base), tmp, dir, blobTotal);
            } catch (Throwable t) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw t instanceof Exception ? (Exception) t : new IllegalStateException(t);
            }
            if (vault.exists()) {
                //noinspection ResultOfMethodCallIgnored
                vault.delete();
            }
            if (!tmp.renameTo(vault)) {
                throw new IllegalStateException("vault rename failed");
            }
            // API 34+ rule (this exact oversight crashed v168 - owned):
            // a dynamically loaded dex file MUST be read-only.
            //noinspection ResultOfMethodCallIgnored
            vault.setReadOnly();
            writeText(stamp, apkStamp);
            Log.i(TAG, "dex vault decrypted (" + vault.length() + " bytes)");
        } else {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
            // fresh boot from cache: no splash this time, drop stale markers
            cleanupMarkers(dir);
        }

        // integrity meta gate: loader bytes must match pack-time hashes
        if (!metaMatches(vault, componentHashes)) {
            dieSilently(vault, stamp, dir);
            return; // unreachable - dieSilently never returns
        }

        // 1. load the archive & prepend its elements into the host loader
        final ClassLoader host = base.getClassLoader();
        ClassLoader extra = null;
        Throwable fileErr = null;
        try {
            extra = new DexClassLoader(vault.getAbsolutePath(), null, null, host);
        } catch (Throwable t) {
            fileErr = t;
            Log.w(TAG, "file-based dex load failed, trying in-memory", t);
        }
        if (extra == null) {
            // Belt & braces for exotic ARTs (v168 fix kept): in-memory
            // dex has no writable-file rule at all.
            java.nio.ByteBuffer buf = null;
            try {
                buf = java.nio.ByteBuffer.allocateDirect((int) vault.length());
                final FileInputStream fis = new FileInputStream(vault);
                final byte[] tmp = new byte[1024 * 1024];
                int n;
                while ((n = fis.read(tmp)) >= 0) {
                    buf.put(tmp, 0, n);
                }
                fis.close();
                buf.flip();
                extra = new dalvik.system.InMemoryDexClassLoader(buf, host);
                Log.i(TAG, "in-memory dex load ok");
            } catch (Throwable t2) {
                throw new IllegalStateException("dex load failed (file+memory)", fileErr);
            }
        }
        final Object pathList = fieldOf(host, "pathList");
        final Object hostElements = fieldOf(pathList, "dexElements");
        final Object extraElements = fieldOf(fieldOf(extra, "pathList"), "dexElements");
        final int hn = Array.getLength(hostElements);
        final int en = Array.getLength(extraElements);
        final Object merged = Array.newInstance(hostElements.getClass().getComponentType(), hn + en);
        System.arraycopy(extraElements, 0, merged, 0, en);
        System.arraycopy(hostElements, 0, merged, en, hn);
        putField(pathList, "dexElements", merged);

        vaultReady = true;
        Log.i(TAG, "dex vault injected (" + en + " elements)");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!vaultReady) {
            // plain/debug build: no encrypted dex, but ALL classes are
            // present normally - keep parity by delegating the same way.
            Log.w(TAG, "no dex vault - plain-build delegation path");
        }
        try {
            final Context base = getBaseContext();
            final ClassLoader host = base.getClassLoader();
            final Class<?> rc = Class.forName(REAL_APP, true, host);
            //noinspection deprecation - most compatible instantiation on ART
            final Application realApp = (Application) rc.newInstance();

            final Object at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread")
                    .invoke(null);
            final Object pkg = fieldOf(base, "mPackageInfo");

            putField(pkg, "mApplication", realApp);
            putField(base, "mOuterContext", realApp);
            putField(at, "mInitialApplication", realApp);
            final Object all = fieldOf(at, "mAllApplications");
            if (all instanceof ArrayList) {
                ((ArrayList<?>) all).remove(this);
                //noinspection unchecked
                ((ArrayList<Object>) all).add(realApp);
            }

            final Object ai = pkg.getClass().getMethod("getApplicationInfo").invoke(pkg);
            if (ai instanceof ApplicationInfo) {
                ((ApplicationInfo) ai).className = REAL_APP;
            }
            try {
                final Object bindData = fieldOf(at, "mBoundApplication");
                final Object bi = fieldOf(bindData, "appInfo");
                if (bi instanceof ApplicationInfo) {
                    ((ApplicationInfo) bi).className = REAL_APP;
                }
            } catch (Throwable t) {
                Log.w(TAG, "bound appInfo rename skipped", t);
            }

            // attach the real app to the base context (hidden attach();
            // fall back to protected attachBaseContext on newer ARTs)
            Throwable attachErr;
            try {
                final Method attach = Application.class.getDeclaredMethod("attach", Context.class);
                attach.setAccessible(true);
                attach.invoke(realApp, base);
                attachErr = null;
            } catch (Throwable t) {
                try {
                    final Method abc = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
                    abc.setAccessible(true);
                    abc.invoke(realApp, base);
                    attachErr = null;
                } catch (Throwable t2) {
                    attachErr = t2;
                }
            }
            if (attachErr != null) {
                throw new IllegalStateException("real app attach failed", attachErr);
            }

            Log.i(TAG, "real application swapped in");
            realApp.onCreate();
            Log.i(TAG, "real application created - boot complete");
        } catch (Throwable t) {
            Log.e(TAG, "application swap failed - the app cannot continue like this", t);
        }
        // the whole one-time boot is finished -> release the splash even
        // if something in the swap path complained (deadline also saves us)
        markPrepDone();
    }

    // ---- vault guts -----------------------------------------------------

    private static void decrypt(InputStream in, String fingerprint, File out, File dir, long total) throws Exception {
        final byte[] head = new byte[52];
        int got = 0;
        while (got < head.length) {
            final int n = in.read(head, got, head.length - got);
            if (n < 0) {
                break;
            }
            got += n;
        }
        if (got < head.length) {
            throw new IllegalStateException("short blob");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (head[i] != MAGIC[i]) {
                throw new IllegalStateException("bad magic");
            }
        }
        final byte[] iv = new byte[12];
        final byte[] mask = new byte[32];
        System.arraycopy(head, 8, iv, 0, 12);
        System.arraycopy(head, 20, mask, 0, 32);

        final MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(seedEnsure());
        sha.update(fingerprint.getBytes("UTF-8"));
        final byte[] wrap = sha.digest();
        final byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (mask[i] ^ wrap[i]);
        }

        final Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        final CipherInputStream cis = new CipherInputStream(in, c);
        final FileOutputStream fos = new FileOutputStream(out);
        long wrote = 0;
        try {
            final byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = cis.read(buf)) >= 0) {
                fos.write(buf, 0, n);
                wrote += n;
                if (total > 0) {
                    writePct(dir, (int) ((wrote * 100) / total));
                }
                // GCM tag check fires at EOF on tampered/wrong-key input
            }
            if (total > 0) {
                writePct(dir, 100);
            }
        } finally {
            try {
                cis.close();
            } catch (Throwable ignored) {
            }
            fos.close();
        }
    }

    private static byte[] seedEnsure() {
        if (seedCache != null) {
            return seedCache;
        }
        try {
            System.loadLibrary("meerovault");
            final byte[] s = seedNative();
            if (s == null || s.length != 32) {
                return null;
            }
            seedCache = s;
            return s;
        } catch (Throwable t) {
            Log.e(TAG, "seed lib", t);
            return null;
        }
    }

    // ---- integrity meta (v171 wall A) -----------------------------------

    /**
     * SHA-256 of this stub's classes.dex and of the meerovault library,
     * read straight from the installed APK so nothing in-process can
     * spoof them. null = unreadable (exotic ROM) -> check skipped.
     */
    private static byte[][] captureComponentHashes(Context base) {
        ZipFile zf = null;
        try {
            final ApplicationInfo info = base.getApplicationInfo();
            String src = info != null ? info.sourceDir : null;
            if (src == null) {
                return null;
            }
            zf = new ZipFile(src);
            final byte[] dexHash = hashEntry(zf, "classes.dex");
            final byte[] libHash = hashEntry(zf, VAULT_LIB_ENTRY);
            if (dexHash == null || libHash == null) {
                return null;
            }
            return new byte[][]{dexHash, libHash};
        } catch (Throwable t) {
            Log.w(TAG, "component hash capture skipped", t);
            return null;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static byte[] hashEntry(ZipFile zf, String name) throws Exception {
        final ZipEntry e = zf.getEntry(name);
        if (e == null) {
            return null;
        }
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final InputStream in = zf.getInputStream(e);
        try {
            final byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) >= 0) {
                md.update(b, 0, n);
            }
        } finally {
            in.close();
        }
        return md.digest();
    }

    private static boolean metaMatches(File vault, byte[][] ours) {
        if (ours == null) {
            return true; // never false-kill a real user on a weird ROM
        }
        ZipFile zf = null;
        try {
            zf = new ZipFile(vault);
            final ZipEntry e = zf.getEntry("vault_meta");
            if (e == null) {
                return false; // archive without a seal = not ours
            }
            final byte[] m = new byte[64];
            final InputStream in = zf.getInputStream(e);
            int got = 0;
            try {
                while (got < m.length) {
                    final int n = in.read(m, got, m.length - got);
                    if (n < 0) {
                        break;
                    }
                    got += n;
                }
            } finally {
                in.close();
            }
            if (got != m.length) {
                return false;
            }
            return MessageDigest.isEqual(Arrays.copyOfRange(m, 0, 32), ours[0])
                    && MessageDigest.isEqual(Arrays.copyOfRange(m, 32, 64), ours[1]);
        } catch (Throwable t) {
            return false;
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** Tampered loader detected: die quietly. Per his law nothing shows. */
    private static void dieSilently(File vault, File stamp, File dir) {
        Log.e(TAG, "integrity seal broken - refusing to boot a tampered loader");
        //noinspection ResultOfMethodCallIgnored
        vault.delete();
        //noinspection ResultOfMethodCallIgnored
        stamp.delete();
        cleanupMarkers(dir);
        Process.killProcess(Process.myPid());
        System.exit(10);
    }

    // ---- prep screen (v171 wall-free UX, wall B) -------------------------

    private static void maybeLaunchPrep(Context base, File dir) {
        cleanupMarkers(dir);
        try {
            // only the default process gets a visible splash; background
            // processes (:push etc.) boot quietly exactly as in v169.
            String proc = null;
            try {
                final android.app.ActivityManager am =
                        (android.app.ActivityManager) base.getSystemService(Context.ACTIVITY_SERVICE);
                final int pid = Process.myPid();
                if (am != null && am.getRunningAppProcesses() != null) {
                    for (android.app.ActivityManager.RunningAppProcessInfo pi : am.getRunningAppProcesses()) {
                        if (pi.pid == pid) {
                            proc = pi.processName;
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "process name lookup failed", t);
            }
            if (proc != null && !base.getPackageName().equals(proc)) {
                return;
            }
            final Intent i = new Intent(base, MeeroBootActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            base.startActivity(i);
            Log.i(TAG, "prep splash shown in :meeroboot");
        } catch (Throwable t) {
            Log.w(TAG, "prep splash unavailable - silent v169-style boot", t);
        }
    }

    private static void writePct(File dir, int pct) {
        try {
            writeText(new File(dir, ".prep"), String.valueOf(Math.max(0, Math.min(100, pct))));
        } catch (Throwable t) {
            Log.w(TAG, "prep pct write failed", t);
        }
    }

    private void markPrepDone() {
        try {
            if (markerDir == null) {
                final Context base = getBaseContext();
                if (base == null) {
                    return;
                }
                markerDir = new File(base.getFilesDir(), "vaultdex");
            }
            writeText(new File(markerDir, ".done"), "1");
        } catch (Throwable t) {
            Log.w(TAG, "prep done marker failed", t);
        }
    }

    private static void cleanupMarkers(File dir) {
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(dir, ".prep").delete();
            //noinspection ResultOfMethodCallIgnored
            new File(dir, ".done").delete();
        } catch (Throwable ignored) {
        }
    }

    @android.annotation.SuppressLint("PackageManagerGetSignatures")
    private static String fingerprintOf(Context context) {
        try {
            final android.content.pm.PackageManager pm = context.getPackageManager();
            final String pkg = context.getPackageName();
            final Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = pi.signingInfo != null && pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : (pi.signingInfo != null ? pi.signingInfo.getSigningCertificateHistory() : null);
            } else {
                sigs = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES).signatures;
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

    // ---- tiny utils ------------------------------------------------------

    private static Object fieldOf(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " on " + o.getClass());
    }

    private static void putField(Object o, String name, Object v) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(o, v);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " on " + o.getClass());
    }

    private static String readText(File f) {
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

    private static void writeText(File f, String s) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(s.getBytes("UTF-8"));
        } finally {
            fos.close();
        }
    }
}
