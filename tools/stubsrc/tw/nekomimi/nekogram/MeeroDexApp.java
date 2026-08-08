package tw.nekomimi.nekogram;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.os.Build;
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
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.DexClassLoader;

/**
 * MeeroX v168 - DEX vault stub (his option A: whole-code encryption,
 * the same scheme his WiFi app used).
 *
 * This class (+ MeeroDexFactory) is the ONLY Java code shipped plain in
 * the APK; every original classesN.dex ships encrypted inside
 * assets/meero_vault/dex.enc (see tools/MeeroDexPacker).
 *
 * Boot flow per process:
 *   1. framework instantiates MeeroDexFactory -> this Application,
 *   2. attachBaseContext: STREAMING AES-256-GCM decrypt of the dex
 *      archive into files/vaultdex/vault.apk (once, stamp-cached by
 *      lastUpdateTime - daily cold starts skip decryption entirely),
 *   3. a DexClassLoader is created over vault.apk and its dex elements
 *      are prepended into the app's PathClassLoader,
 *   4. onCreate: ApplicationLoader is instantiated from the vault,
 *      swapped in as the process-wide application (mOuterContext /
 *      mInitialApplication / mAllApplications / LoadedApk.mApplication /
 *      ApplicationInfo.className) and its onCreate() runs.
 *
 * If assets/meero_vault/dex.enc is ABSENT this stub stays completely
 * inert (plain/debug builds boot exactly like stock).
 *
 * Compiled OUTSIDE the app source set (tools/stubsrc -> javac+d8 in CI):
 * if it ever landed inside the encrypted dex the bootstrap would eat
 * itself, so it lives in this separate folder BY DESIGN.
 *
 * Key derivation identical to the .so vault: SHA256(seed | release
 * signing fingerprint) XOR keyMask - a re-signed clone decrypts garbage,
 * the GCM tag check fails and the boot dies loudly, on top of the v165
 * guard + v167 native vault.
 */
public class MeeroDexApp extends Application {

    private static final String TAG = "MeeroDex";
    private static final String REAL_APP = "org.telegram.messenger.ApplicationLoader";
    private static final String ASSET = "meero_vault/dex.enc";
    private static final byte[] MAGIC = {'M', 'V', 'D', 'X', '0', '0', '0', '1'};

    public static final String OFFICIAL_FINGERPRINT =
            "29:F7:3E:38:D2:13:8B:73:72:05:AC:C2:25:1B:28:45:2F:5A:50:99:66:FE:64:17:3D:B2:09:FE:DA:A7:9E:F7";

    private static volatile boolean vaultReady;
    private static byte[] seedCache;

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
        try {
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

        final boolean cached = stamp.exists() && vault.exists()
                && vault.length() > 1000000 && apkStamp.equals(readText(stamp));
        if (!cached) {
            if (seedEnsure() == null) {
                throw new IllegalStateException("seed lib unavailable");
            }
            final File tmp = new File(dir, "vault.tmp");
            try {
                decrypt(in, fingerprintOf(base), tmp);
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
            // API 34+ rule (this exact oversight crashed v168 on his
            // device - owned): a dynamically loaded dex file MUST be
            // read-only or DexClassLoader throws SecurityException.
            //noinspection ResultOfMethodCallIgnored
            vault.setReadOnly();
            writeText(stamp, apkStamp);
            Log.i(TAG, "dex vault decrypted (" + vault.length() + " bytes)");
        } else {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
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
            // Belt & braces for exotic ARTs: in-memory dex has no
            // writable-file rule at all. Costs the decrypt per cold boot
            // (no oat reuse) but always works on API 27+.
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
    }

    // ---- vault guts ------------------------------------------------------

    private static void decrypt(InputStream in, String fingerprint, File out) throws Exception {
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
        try {
            final byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = cis.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
            // GCM tag check fires here on tampered/wrong-key input
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
