package tw.nekomimi.nekogram;

import android.animation.ValueAnimator;
import android.os.Build;

/**
 * MeeroX v231 (owner order «خيار 1 - تسريع الحركات»): one global 0.75x
 * duration scale for the ValueAnimator family in this process - fragment
 * transitions, popups, sheets, the keyboard, bottom bars and friends all
 * play 25% faster, so the whole app feels snappier with zero layout or
 * logic changes. Sticker/Lottie playback runs on its own clock and
 * physics springs keep their own timing; both stay untouched.
 *
 * ValueAnimator.setDurationScale(float) ships in Android 13+ (API 33),
 * but the pre-release compileSdk 37 jar used by CI does not expose the
 * symbol at build time, so the call is made BY REFLECTION - linked by
 * name at runtime, resolved on-device, and every failure path (older
 * Android, missing surface) silently keeps the stock 1.0x pace. apply()
 * is idempotent, sticky and silent; called at boot, on every
 * LaunchActivity resume, and instantly when the Motion-settings switch
 * flips.
 */
public final class MeeroFastMotion {

    private static final float FAST_SCALE = 0.75f;

    private MeeroFastMotion() {
    }

    public static boolean isOn() {
        try {
            return NekoConfig.meeroFastAnimations.Bool();
        } catch (Throwable ignore) {
            return true;
        }
    }

    public static void apply() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                java.lang.reflect.Method m = ValueAnimator.class.getDeclaredMethod("setDurationScale", float.class);
                m.invoke(null, isOn() ? FAST_SCALE : 1.0f);
            }
        } catch (Throwable ignore) {
            // cosmetic pace only - never block startup or a settings flip
        }
    }
}
