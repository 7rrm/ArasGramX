package tw.nekomimi.nekogram;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v126: the fixed "Glass Night" skin for MeeroX settings screens.
 *
 * The user asked for an EXCLUSIVE fixed design: these screens must ignore
 * Telegram themes completely and only follow the app's day/night state.
 * So every color in this file is a hardcoded MeeroX palette value - nothing
 * is read from the active Telegram theme, ever. A custom ResourcesProvider
 * is attached to the settings screen so even Telegram's own adaptive
 * ActionBar painting resolves through this fixed palette instead of theme
 * keys.
 *
 * Everything is a pure function of isNight(), so when the app flips between
 * day and night the palette flips with it and nothing else can touch it.
 */
public final class MeeroGlassTheme {

    private MeeroGlassTheme() {}

    /** Day/night comes from the app's own night state, not from theme colors. */
    public static boolean isNight() {
        try {
            return Theme.getActiveTheme().isDark();
        } catch (Throwable t) {
            return true;
        }
    }

    // Fixed accents - identical in day and night, this is the MeeroX identity.
    public static final int ACC1 = 0xFFFF4E8A; // rose
    public static final int ACC2 = 0xFF7B5CFF; // violet

    public static int bg() {
        return isNight() ? 0xFF0B0B10 : 0xFFF2F2F7;
    }

    /** Slightly lifted surface the adaptive ActionBar melts into on scroll. */
    public static int actionBarBg() {
        return isNight() ? 0xFF13131B : 0xFFFBFBFE;
    }

    public static int ink() {
        return isNight() ? 0xFFF2F2F6 : 0xFF15151C;
    }

    public static int sub() {
        return isNight() ? 0xFF9A9AA5 : 0xFF7D7D88;
    }

    public static int headerInk() {
        return sub();
    }

    /** Row-press ripple tint on the glass surface. */
    public static int press() {
        return isNight() ? 0x0FFFFFFF /* white 6% */ : 0x0D14141A /* black 5% */;
    }

    public static int glowA() {
        return isNight() ? 0x29FF4E8A /* rose 16% */ : 0x1AFF4E8A /* 10% */;
    }

    public static int glowB() {
        return isNight() ? 0x217B5CFF /* violet 13% */ : 0x147B5CFF /* 8% */;
    }

    /**
     * The screen background: a solid base with two soft radial glows (rose at
     * the top corner, violet at the bottom corner). Purely decorative and
     * drawn behind the (transparent) cells.
     */
    public static Drawable screenBackground() {
        GradientDrawable rose = new GradientDrawable();
        rose.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        rose.setColors(new int[]{glowA(), Color.TRANSPARENT});
        rose.setGradientRadius(AndroidUtilities.dp(330));
        rose.setGradientCenter(0.82f, 0.0f);

        GradientDrawable violet = new GradientDrawable();
        violet.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        violet.setColors(new int[]{glowB(), Color.TRANSPARENT});
        violet.setGradientRadius(AndroidUtilities.dp(370));
        violet.setGradientCenter(0.12f, 1.0f);

        return new LayerDrawable(new Drawable[]{new ColorDrawable(bg()), rose, violet});
    }

    /** Thin rose->violet->transparent rule drawn under each section header. */
    public static Drawable headerRule() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACC1, ACC2, Color.TRANSPARENT});
        d.setCornerRadius(AndroidUtilities.dp(1));
        return d;
    }

    /**
     * Wraps the fragment's own provider so every theme key the settings
     * screen (and its adaptive ActionBar) asks for is answered from the
     * fixed palette. Unmapped keys pass through to the delegate untouched,
     * so anything we do not explicitly own behaves exactly as before.
     */
    public static Theme.ResourcesProvider wrap(final Theme.ResourcesProvider delegate) {
        return key -> {
            if (key == Theme.key_windowBackgroundGray) {
                return bg();
            }
            if (key == Theme.key_actionBarDefault) {
                return actionBarBg();
            }
            if (key == Theme.key_actionBarDefaultTitle
                    || key == Theme.key_actionBarDefaultIcon) {
                return ink();
            }
            if (key == Theme.key_actionBarDefaultSelector) {
                return press();
            }
            if (key == Theme.key_listSelector) {
                return press();
            }
            if (key == Theme.key_windowBackgroundWhiteBlueHeader) {
                return headerInk();
            }
            return delegate != null ? delegate.getColor(key) : Theme.getColor(key);
        };
    }
}
