package tw.nekomimi.nekogram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Color;

import org.telegram.messenger.AndroidUtilities;
import android.graphics.Paint;
import android.view.View;

/**
 * MeeroX: the soft shadow iOS puts under anything that floats.
 *
 * iOS separates a floating surface from the page with a wide, very faint
 * shadow rather than an outline. Telegram's own helper,
 * Theme.createRoundRectDrawableShadowed, uses a 2dp blur at 0x11 alpha, which
 * is tight and dark enough to read as a border instead of depth.
 *
 * The numbers live here rather than inside MeeroCards so that context menus,
 * dialogs and sheets can use exactly the same values as the settings cards -
 * a shadow that differs between surfaces is more noticeable than no shadow at
 * all. Elevation is expressed in tiers because iOS scales the blur with how
 * far a surface sits above the page: a grouped card barely lifts, a popover
 * lifts clearly, and a modal sheet lifts most.
 */
public class MeeroShadow {

    /** A grouped card resting on the page. */
    public static final int TIER_CARD = 0;
    /** A popover or context menu floating over content. */
    public static final int TIER_MENU = 1;
    /** A modal sheet or dialog above everything. */
    public static final int TIER_MODAL = 2;

    private static final float[] BLUR_DP = {10f, 16f, 24f};
    private static final float[] DY_DP = {2f, 4f, 8f};

    /* v188 (batch 3B): the tier tables arrive from the sealed native table;
     * the legacy arrays above stay as the byte-identical fallback. Cache
     * slots: 3 tiers x day/night. */
    private static final float[][][] sTier = new float[3][2][];
    private static float tierPart(int tier, boolean dark, int part, float fb) {
        final int t = tier < 0 ? 0 : (tier > 2 ? 2 : tier);
        final int d = dark ? 1 : 0;
        float[] pack = sTier[t][d];
        if (pack == null) {
            float[] n = MeeroCore.chatCore() ? MeeroCore.nShadowTier(t, dark) : null;
            pack = (n != null && n.length == 3) ? n : new float[0];
            sTier[t][d] = pack;
        }
        return pack.length == 3 ? pack[part] : fb;
    }
    /**
     * Dark themes need a stronger shadow to register at all, since the page
     * behind is already close to black; light themes need far less before the
     * shadow starts reading as a grey border.
     */
    private static final int[] ALPHA_DARK = {0x40, 0x55, 0x66};
    private static final int[] ALPHA_LIGHT = {0x1A, 0x24, 0x33};

    public static boolean enabled() {
        try {
            return NekoConfig.meeroIosShadows.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    private static int clampTier(int tier) {
        if (tier < 0) return 0;
        if (tier >= BLUR_DP.length) return BLUR_DP.length - 1;
        return tier;
    }

    public static boolean isDark(int color) {
        return (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) < 128;
    }

    /**
     * Applies the shadow to a paint, or clears it when shadows are off.
     *
     * @param dark whether the surface behind is dark
     */
    public static void apply(Paint paint, int tier, boolean dark) {
        if (paint == null) {
            return;
        }
        if (!enabled()) {
            paint.clearShadowLayer();
            return;
        }
        final int t = clampTier(tier);
        paint.setShadowLayer(dp(tierPart(t, dark, 0, BLUR_DP[t])), 0,
                dp(tierPart(t, dark, 1, DY_DP[t])),
                Color.argb((int) tierPart(t, dark, 2, dark ? ALPHA_DARK[t] : ALPHA_LIGHT[t]), 0, 0, 0));
    }

    /** How much room the shadow needs around a surface, in pixels. */
    public static int inset(int tier) {
        if (!enabled()) {
            return 0;
        }
        final int t = clampTier(tier);
        final int nativeInset = MeeroCore.chatCore()
                ? MeeroCore.nShadowInset(t, AndroidUtilities.density) : -1;
        if (nativeInset >= 0) {
            return nativeInset;
        }
        return (int) Math.ceil(dp(BLUR_DP[t] + DY_DP[t]));
    }

    /**
     * Switches a host view to software rendering.
     *
     * Paint.setShadowLayer is ignored under hardware acceleration, so any view
     * hosting a shadowed drawable has to say so.
     */
    public static void prepare(View view) {
        if (view == null || !enabled()) {
            return;
        }
        if (view.getLayerType() != View.LAYER_TYPE_SOFTWARE) {
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }
}
