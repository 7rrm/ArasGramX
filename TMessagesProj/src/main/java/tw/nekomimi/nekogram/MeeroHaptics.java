package tw.nekomimi.nekogram;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * MeeroX: iOS-weighted haptics.
 *
 * Telegram for Android only ever asks for two effects - KEYBOARD_TAP for
 * almost everything and LONG_PRESS for a press-and-hold - so a list scroll, a
 * confirmation and a failed code all buzz identically. iOS instead runs three
 * impact weights plus a separate set of notification patterns, and the weight
 * is what tells you whether something merely moved or actually went wrong.
 *
 * Android 11 added CONFIRM, REJECT and GESTURE_END, which map onto those
 * fairly closely. Older releases fall back to the two constants Telegram
 * already uses, so nothing regresses on them.
 */
public class MeeroHaptics {

    /** A selection moved: scrolling an index, changing a tab. */
    public static final int LIGHT = 0;
    /** Something was actioned: a button, a toggle. */
    public static final int MEDIUM = 1;
    /** Something completed successfully. */
    public static final int SUCCESS = 2;
    /** Something failed: a wrong code, a rejected input. */
    public static final int ERROR = 3;
    /** A press-and-hold registered. */
    public static final int LONG_PRESS = 4;

    public static boolean enabled() {
        try {
            return NekoConfig.meeroIosHaptics.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Telegram's own master switch still wins. */
    private static boolean vibrationAllowed() {
        try {
            return !NekoConfig.disableVibration.Bool();
        } catch (Throwable e) {
            return true;
        }
    }

    /* v189 (batch 3C): the weight -> platform-constant map comes from the
     * sealed motion table (dom 'C'); the switch below is the byte-identical
     * no-lib fallback. The public weight ids (0..4) are indices, not recipe. */
    private static volatile int[] map;

    private static int constantFor(int weight) {
        final boolean modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        int[] m = map;
        if (m == null && MeeroCore.motionCore()) {
            m = MeeroCore.nHapticsMap();
            if (m != null && m.length == 10) map = m; else m = null;
        }
        if (m != null && weight >= 0 && weight <= LONG_PRESS) {
            return m[weight * 2 + (modern ? 0 : 1)];
        }
        switch (weight) {
            case LIGHT:
                // A tick is lighter than a keyboard tap and is what iOS uses
                // for a selection change.
                return modern ? HapticFeedbackConstants.CLOCK_TICK : HapticFeedbackConstants.KEYBOARD_TAP;
            case SUCCESS:
                return modern ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.KEYBOARD_TAP;
            case ERROR:
                return modern ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS;
            case LONG_PRESS:
                return HapticFeedbackConstants.LONG_PRESS;
            case MEDIUM:
            default:
                return HapticFeedbackConstants.KEYBOARD_TAP;
        }
    }

    /**
     * Plays the effect for a weight.
     *
     * @param view the view the feedback belongs to
     */
    public static void perform(View view, int weight) {
        if (view == null || !vibrationAllowed()) {
            return;
        }
        final int constant = enabled()
                ? constantFor(weight)
                : (weight == LONG_PRESS ? HapticFeedbackConstants.LONG_PRESS : HapticFeedbackConstants.KEYBOARD_TAP);
        try {
            view.performHapticFeedback(constant,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Throwable ignore) {
        }
    }
}
