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

    /**
     * v127: single source of truth for the master switch, read lazily so a
     * provider built once keeps following the toggle on every draw.
     */
    public static boolean enabled() {
        try {
            return NekoConfig.meeroGlassSettings.Bool();
        } catch (Throwable t) {
            return true;
        }
    }

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

    // ---------------- v127: cells layer ----------------

    /** Hairline separator between rows inside a section card. */
    public static int sep() {
        return isNight() ? 0x12FFFFFF /* white 7% */ : 0x1214141A /* black 7% */;
    }

    /** Off-state switch track. */
    public static int trackOff() {
        return isNight() ? 0x2EFFFFFF /* white 18% */ : 0xFFD9D9E0;
    }

    /** Section card: translucent fill over the glow background. */
    public static int cardFill() {
        return isNight() ? 0x0DFFFFFF /* white 5% */ : 0xA8FFFFFF /* white 66% */;
    }

    public static int cardStroke() {
        return isNight() ? 0x17FFFFFF /* white 9% */ : 0xD9FFFFFF /* white 85% */;
    }

    /** Value chip behind the current selection of a row. */
    public static int chipFill() {
        return 0x24FF4E8A; // rose 14%
    }

    public static int chipStroke() {
        return 0x40FF4E8A; // rose 25%
    }

    /** v128: bottom-sheet surface (picker sheet) - lifted, but of the family. */
    public static int sheetBg() {
        return isNight() ? 0xFF12121A : 0xFFFBFBFE;
    }

    /** v128: segmented-control track inside the picker sheet. */
    public static int segTrack() {
        return isNight() ? 0xFF1C1C26 : 0xFFE9E9F0;
    }

    /**
     * Rounded section-card background; corners rounded only on the outer
     * edges of the card so stacked rows read as one glass panel.
     */
    public static Drawable card(boolean roundTop, boolean roundBottom) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(cardFill());
        d.setStroke(AndroidUtilities.dp(1), cardStroke());
        float rTop = AndroidUtilities.dp(roundTop ? 18 : 0);
        float rBot = AndroidUtilities.dp(roundBottom ? 18 : 0);
        d.setCornerRadii(new float[]{rTop, rTop, rTop, rTop, rBot, rBot, rBot, rBot});
        return d;
    }

    public static Drawable chipBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(chipFill());
        d.setStroke(AndroidUtilities.dp(1), chipStroke());
        d.setCornerRadius(AndroidUtilities.dp(12));
        return d;
    }

    /**
     * Cell-level provider (v127). Attached to the standard Telegram cells
     * this screen constructs, so texts, the Switch widget and row hairlines
     * all resolve through the fixed palette - the Switch even re-resolves
     * per frame, which makes the master toggle self-healing on recycled
     * views. When the switch is off every key falls back to the global
     * theme resolution, i.e. exact stock.
     */
    public static Theme.ResourcesProvider cells() {
        return key -> {
            if (!enabled()) {
                return Theme.getColor(key);
            }
            if (key == Theme.key_windowBackgroundWhiteBlackText
                    || key == Theme.key_dialogTextBlack) {
                return ink();
            }
            if (key == Theme.key_windowBackgroundWhiteGrayText2
                    || key == Theme.key_dialogIcon) {
                return sub();
            }
            if (key == Theme.key_windowBackgroundWhiteValueText) {
                return ACC1;
            }
            if (key == Theme.key_windowBackgroundWhiteBlueHeader) {
                return headerInk();
            }
            if (key == Theme.key_switchTrack || key == Theme.key_fill_RedNormal) {
                return trackOff();
            }
            if (key == Theme.key_switchTrackChecked
                    || key == Theme.key_switch2TrackChecked) {
                return ACC1;
            }
            if (key == Theme.key_switchTrackBlueSelector) {
                return isNight() ? 0x1AFFFFFF : 0x1A14141A;
            }
            if (key == Theme.key_switchTrackBlueSelectorChecked) {
                return isNight() ? 0x2EFFFFFF : 0x26000000;
            }
            if (key == Theme.key_divider) {
                return sep();
            }
            return Theme.getColor(key);
        };
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
