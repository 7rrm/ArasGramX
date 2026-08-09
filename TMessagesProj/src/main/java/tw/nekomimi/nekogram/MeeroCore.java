package tw.nekomimi.nekogram;

import java.security.MessageDigest;

/**
 * MeeroX v183 (batch 2A) - the thin kept shell of libmeerocore.so.
 *
 * Every byte of logic (lock verify/derive, audit seal, api-key engine) is
 * native; this class only carries the native bindings + readiness probe +
 * a degraded-mode Java mirror of the v2 derive so a build without the lib
 * (never on our stock APKs) keeps EXACTLY the same user-visible behaviour.
 *
 * Name-pinned in proguard-rules.pro like MeeroVaultSeed: JNI bindings are
 * name-mangled, so this class must never be renamed.
 */
public final class MeeroCore {

    private MeeroCore() {
    }

    private static volatile boolean ready;

    static {
        boolean ok = false;
        try {
            /* libmeerovault must already be mapped: meerocore links against
             * it for the shared seed. */
            MeeroVaultSeed.fingerprintSeed();
            System.loadLibrary("meerocore");
            ok = true;
        } catch (Throwable ignored) {
        }
        ready = ok;
    }

    public static boolean ready() {
        return ready;
    }

    /**
     * Degraded-mode v2 derive (libmeerocore absent). Byte-identical to the
     * native nLockDerive material: seed(32) | "MCLK2" | NUL | salt | ":" | code.
     */
    public static byte[] javaV2Derive(String saltB64, String code) {
        try {
            final byte[] seed = MeeroVaultSeed.fingerprintSeed();
            if (seed == null || seed.length != 32) {
                return null;
            }
            final byte[] salt = saltB64.getBytes("UTF-8");
            final byte[] cd = code.getBytes("UTF-8");
            final byte[] m = new byte[32 + 6 + salt.length + 1 + cd.length];
            System.arraycopy(seed, 0, m, 0, 32);
            final byte[] tag = "MCLK2".getBytes("UTF-8");
            System.arraycopy(tag, 0, m, 32, 5);
            m[37] = 0;
            System.arraycopy(salt, 0, m, 38, salt.length);
            m[38 + salt.length] = ':';
            System.arraycopy(cd, 0, m, 39 + salt.length, cd.length);
            return MessageDigest.getInstance("SHA-256").digest(m);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------- lock core ---------------- */
    public static native byte[] nLegacyDigest(String saltB64, String code);

    public static native byte[] nLockDerive(String saltB64, String code);

    public static native boolean nConstEq(byte[] a, byte[] b);

    public static native String nAuditMac(String prevJson, String entryJson);

    /* ---------------- api-key engine ---------------- */
    public static native void nInstallKey0(int id, String hash);

    public static native int nKeyCount();

    public static native int nKeyId(int index);

    public static native String nKeyHash(int index);

    public static native int nKeyAdvance(int current);

    public static native boolean nIsKeyError(String errorText);
}
