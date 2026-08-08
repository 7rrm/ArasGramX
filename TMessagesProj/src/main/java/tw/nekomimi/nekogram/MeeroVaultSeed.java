package tw.nekomimi.nekogram;

/**
 * MeeroX v167 - JNI facade to libmeerovault.so, the tiny PLAIN native lib
 * that carries the shuffled vault seed (same role as the packer shell in
 * his WiFi app). Loading it also arms the passive Layer-3 hardening
 * (dumpable-off + tracer report) from its JNI_OnLoad.
 *
 * Kept deliberately tiny: any UnsatisfiedLinkError propagates to the
 * caller (MeeroVault.tryLoad catches Throwable) which then falls back to
 * the stock System.loadLibrary path - plain builds stay 100% stock.
 */
public final class MeeroVaultSeed {

    private MeeroVaultSeed() {
    }

    private static volatile boolean ready;

    public static synchronized byte[] fingerprintSeed() {
        if (!ready) {
            System.loadLibrary("meerovault");
            ready = true;
        }
        return fingerprintSeedNative();
    }

    private static native byte[] fingerprintSeedNative();
}
