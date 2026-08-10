package tw.nekomimi.nekogram;

/**
 * MeeroX v189 (batch 3C): the iOS-side metrics of the intro screen.
 *
 * IntroActivity keeps the on/off switch and Telegram's own stock numbers;
 * the four iOS values (17pt button label, 50dp SolidRoundedButtonNode
 * height, 76dp bottom margin, 28pt semibold headline) come from the sealed
 * motion table (dom 'C'). The literal fallbacks are byte-identical, so a
 * build without the lib renders exactly the same intro.
 */
public final class MeeroIntro {

    private MeeroIntro() {
    }

    private static volatile float[] rec;

    private static float ir(int i, float legacy) {
        float[] r = rec;
        if (r == null && MeeroCore.motionCore()) {
            r = MeeroCore.nIntroRecipe();
            if (r != null && r.length == 4) rec = r; else r = null;
        }
        return r != null ? r[i] : legacy;
    }

    /** iOS labels solid buttons at 17pt regular. */
    public static float buttonText() {
        return ir(0, 17f);
    }

    /** iOS's SolidRoundedButtonNode height, dp. */
    public static float buttonHeight() {
        return ir(1, 50f);
    }

    /** Bottom margin under the button, dp. */
    public static float buttonBottom() {
        return ir(2, 76f);
    }

    /** Intro headline size (semibold), pt. */
    public static float headline() {
        return ir(3, 28f);
    }
}
