package tw.nekomimi.nekogram;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
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

    /**
     * v129: the mock-accurate MeeroGlassSwitch is live only while BOTH the
     * master design switch and its own dedicated row are on. Read lazily,
     * so flipped rows redraw on the next frame.
     */
    /* v187 (batch 3A): palette + design constants arrive from the sealed
     * native table (dom 'G'). Every helper keeps its legacy literal as the
     * exact fallback when the table is unavailable, so behavior is
     * byte-identical either way. -1 from native = use the literal. */
    private static int g(int id, int nightColor, int dayColor) {
        if (MeeroCore.glassCore()) {
            int v = MeeroCore.nGtColor(id, isNight());
            if (v != -1) return v;
        }
        return isNight() ? nightColor : dayColor;
    }

    private static float[] sUi;
    private static float uic(int i, float fb) {
        float[] u = sUi;
        if (u == null) {
            float[] n = MeeroCore.glassCore() ? MeeroCore.nGlassUiConsts() : null;
            u = (n != null && n.length == 32) ? n : new float[0];
            sUi = u;
        }
        return u.length == 32 ? u[i] : fb;
    }

    public static boolean switchesEnabled() {
        try {
            return enabled() && NekoConfig.meeroGlassSwitches.Bool();
        } catch (Throwable t) {
            return enabled();
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
        return g(0, 0xFF0B0B10, 0xFFF2F2F7);
    }

    /** Slightly lifted surface the adaptive ActionBar melts into on scroll. */
    public static int actionBarBg() {
        return g(1, 0xFF13131B, 0xFFFBFBFE);
    }

    public static int ink() {
        return g(2, 0xFFF2F2F6, 0xFF15151C);
    }

    public static int sub() {
        return g(3, 0xFF9A9AA5, 0xFF7D7D88);
    }

    public static int headerInk() {
        return sub();
    }

    /** Row-press ripple tint on the glass surface. */
    public static int press() {
        return g(4, 0x0FFFFFFF /* white 6% */, 0x0D14141A /* black 5% */);
    }

    public static int glowA() {
        return g(5, 0x29FF4E8A /* rose 16% */, 0x1AFF4E8A /* 10% */);
    }

    public static int glowB() {
        return g(6, 0x217B5CFF /* violet 13% */, 0x147B5CFF /* 8% */);
    }

    // ---------------- v127: cells layer ----------------

    /** Hairline separator between rows inside a section card. */
    public static int sep() {
        return g(7, 0x12FFFFFF /* white 7% */, 0x1214141A /* black 7% */);
    }

    /** Off-state switch track. */
    public static int trackOff() {
        return g(8, 0x2EFFFFFF /* white 18% */, 0xFFD9D9E0);
    }

    /** Section card: translucent fill over the glow background. */
    public static int cardFill() {
        return g(9, 0x0DFFFFFF /* white 5% */, 0xA8FFFFFF /* white 66% */);
    }

    public static int cardStroke() {
        return g(10, 0x17FFFFFF /* white 9% */, 0xD9FFFFFF /* white 85% */);
    }

    /** Value chip behind the current selection of a row. */
    public static int chipFill() {
        return g(11, 0x24FF4E8A, 0x24FF4E8A); // rose 14%
    }

    public static int chipStroke() {
        return g(12, 0x40FF4E8A, 0x40FF4E8A); // rose 25%
    }

    /** v128: bottom-sheet surface (picker sheet) - lifted, but of the family. */
    public static int sheetBg() {
        return g(13, 0xFF12121A, 0xFFFBFBFE);
    }

    /** v128: segmented-control track inside the picker sheet. */
    public static int segTrack() {
        return g(14, 0xFF1C1C26, 0xFFE9E9F0);
    }

    /**
     * Rounded section-card background; corners rounded only on the outer
     * edges of the card so stacked rows read as one glass panel.
     */
    public static Drawable card(boolean roundTop, boolean roundBottom) {
        return card(roundTop, roundBottom, false);
    }

    /**
     * The glass card. v130 parity pass: radius is 20dp like the mock (.card
     * border-radius:20px), and rows joined to a row ABOVE them draw the
     * mock's in-card 1px separator (.row+.row::before), inset 16dp from the
     * inline-start edge exactly like inset-inline-start:16px - RTL moves it
     * to the physical right side, mirroring the CSS.
     */
    public static Drawable card(boolean roundTop, boolean roundBottom, boolean topHairline) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(cardFill());
        d.setStroke(AndroidUtilities.dp(uic(18, 1)), cardStroke());
        float rTop = AndroidUtilities.dp(roundTop ? uic(17, 20) : 0);
        float rBot = AndroidUtilities.dp(roundBottom ? uic(17, 20) : 0);
        d.setCornerRadii(new float[]{rTop, rTop, rTop, rTop, rBot, rBot, rBot, rBot});
        if (!topHairline) {
            return d;
        }
        final boolean rtl = LocaleController.isRTL;
        LayerDrawable layers = new LayerDrawable(new Drawable[]{d, new ColorDrawable(sep())});
        layers.setLayerHeight(1, Math.max(1, AndroidUtilities.dp(uic(19, 0.66f))));
        layers.setLayerGravity(1, android.view.Gravity.TOP);
        layers.setLayerInset(1, rtl ? 0 : AndroidUtilities.dp(uic(20, 16)), 0,
                rtl ? AndroidUtilities.dp(uic(20, 16)) : 0, 0);
        return layers;
    }

    public static Drawable chipBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(chipFill());
        d.setStroke(AndroidUtilities.dp(uic(18, 1)), chipStroke());
        d.setCornerRadius(AndroidUtilities.dp(uic(21, 12)));
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
        return cellsWith(null);
    }

    /**
     * Same as {@link #cells()} but unmapped keys fall back to the given
     * delegate instead of the global theme. Used by wrapLegacy() to chain
     * the cell palette behind the chrome palette for legacy-base screens.
     */
    public static Theme.ResourcesProvider cellsWith(final Theme.ResourcesProvider delegate) {
        return key -> {
            if (!enabled()) {
                return delegate != null ? delegate.getColor(key) : Theme.getColor(key);
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
                return g(15, 0x1AFFFFFF, 0x1A14141A);
            }
            if (key == Theme.key_switchTrackBlueSelectorChecked) {
                return g(16, 0x2EFFFFFF, 0x26000000);
            }
            if (key == Theme.key_divider) {
                return sep();
            }
            return delegate != null ? delegate.getColor(key) : Theme.getColor(key);
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
        rose.setGradientRadius(AndroidUtilities.dp(uic(22, 330)));
        rose.setGradientCenter(uic(24, 0.82f), uic(25, 0.0f));

        GradientDrawable violet = new GradientDrawable();
        violet.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        violet.setColors(new int[]{glowB(), Color.TRANSPARENT});
        violet.setGradientRadius(AndroidUtilities.dp(uic(23, 370)));
        violet.setGradientCenter(uic(26, 0.12f), uic(27, 1.0f));

        return new LayerDrawable(new Drawable[]{new ColorDrawable(bg()), rose, violet});
    }

    /** Thin rose->violet->transparent rule drawn under each section header. */
    public static Drawable headerRule() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACC1, ACC2, Color.TRANSPARENT});
        d.setCornerRadius(AndroidUtilities.dp(uic(28, 1)));
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

    /**
     * v129: provider for the LEGACY base (BaseNekoXSettingsActivity +
     * BaseNekoSettingsActivity family). Those screens build their ActionBar,
     * blur scrim, status-bar luminance and row backgrounds from
     * key_windowBackgroundWhite directly, so on top of the normal chrome
     * palette we additionally pin white -> the fixed bar color (the row
     * skin pass replaces the actual row backgrounds anyway, and their
     * switches are swapped to MeeroGlassSwitch, so the stock white-thumb
     * mapping is not needed here) and make the gray section-shadow key
     * transparent so themed separator bars disappear under glass. Cell
     * palette keys resolve too, because legacy cells are constructed with
     * this single provider. Everything is gated on the live toggle, so OFF
     * resolves exactly like before.
     */
    public static Theme.ResourcesProvider wrapLegacy(final Theme.ResourcesProvider delegate) {
        final Theme.ResourcesProvider chained = wrap(cellsWith(delegate));
        return key -> {
            if (enabled()) {
                if (key == Theme.key_windowBackgroundWhite) {
                    return actionBarBg();
                }
                if (key == Theme.key_windowBackgroundGrayShadow) {
                    return Color.TRANSPARENT;
                }
            }
            return chained.getColor(key);
        };
    }

    /**
     * v131: dialog palette layered on the fixed palette. AlertDialogs
     * constructed on a glass screen (import/export confirm, input sheets)
     * still resolve the themed dialog keys, which in many dark themes are
     * a cold blue-grey that clashes with the glass sheet colour - the
     * user's report #4. Chained on top of the normal chain so the window
     * chrome keys (ink, press, backgrounds) keep mapping too, and gated on
     * the live toggle like everything else: with the design off every key
     * resolves exactly as the delegate would.
     */
    public static Theme.ResourcesProvider dialog(final Theme.ResourcesProvider delegate) {
        final Theme.ResourcesProvider chained = wrapLegacy(delegate);
        return key -> {
            if (enabled()) {
                if (key == Theme.key_dialogBackground) {
                    return sheetBg();
                }
                if (key == Theme.key_dialogBackgroundGray) {
                    return g(14, 0xFF1C1C26, 0xFFE9E9F0);
                }
                if (key == Theme.key_dialogButton
                        || key == Theme.key_dialogTextBlue
                        || key == Theme.key_dialogTextBlue2
                        || key == Theme.key_dialogTextLink) {
                    return ACC1;
                }
                if (key == Theme.key_dialogTextGray) {
                    return sub();
                }
                if (key == Theme.key_dialogButtonSelector) {
                    return press();
                }
            }
            return chained.getColor(key);
        };
    }
}
