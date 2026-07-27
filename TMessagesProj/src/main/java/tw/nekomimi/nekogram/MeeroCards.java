package tw.nekomimi.nekogram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX: iOS 26 style grouped cards for the settings screens.
 *
 * Instead of edge-to-edge rows on a flat sheet, rows are inset and drawn on a
 * rounded translucent card. A run of consecutive rows shares one card: the
 * first row rounds its top corners, the last rounds its bottom, and the ones in
 * between stay square so the card reads as a single piece.
 */
public class MeeroCards {

    /** Horizontal inset of the card from the screen edge. */
    public static final int SIDE_MARGIN_DP = 16;
    /** Corner radius of the card. */
    public static final int RADIUS_DP = 18;

    public static final int POS_SINGLE = 0;
    public static final int POS_FIRST  = 1;
    public static final int POS_MIDDLE = 2;
    public static final int POS_LAST   = 3;

    /** The fill colour a card is drawn with, for hosts that must match it. */
    public static int surfaceColor(Theme.ResourcesProvider rp) {
        final int base = Theme.getColor(Theme.key_windowBackgroundWhite, rp);
        return meeroLift(base);
    }

    /**
     * Lifts a surface just enough to read as a card on top of the page.
     *
     * A flat 10% shift only worked for grey themes: on a saturated
     * background - the purple and blue themes especially - adding the same
     * amount to every channel drags the hue around and the card ends up a
     * different colour from the page. Working in HSV keeps the hue and
     * saturation fixed and moves only the brightness, and the step scales
     * with saturation so vivid themes get a gentler lift.
     */
    private static int meeroLift(int base) {
        final float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        final float sat = hsv[1];
        final float val = hsv[2];
        // Vivid colours need a smaller step to stay recognisable.
        final float step = 0.10f - 0.05f * Math.min(1f, sat);
        if (val < 0.5f) {
            hsv[2] = Math.min(1f, val + step);
        } else {
            // Light themes read better with the card slightly recessed.
            hsv[2] = Math.max(0f, val - step * 0.35f);
        }
        return Color.HSVToColor(Color.alpha(base), hsv);
    }

    /**
     * MeeroX: the rounded tile that sits behind a row's icon.
     *
     * Taken from the reference layout's SettingsRow: a 40dp box with a 12dp
     * radius, filled with the accent colour at 15%. The icon itself is tinted
     * with the same accent, so the pair reads as one badge.
     */
    public static class IconTileDrawable extends Drawable {

        public static final int SIZE_DP = 40;
        public static final int RADIUS_DP = 12;
        private static final float FILL_ALPHA = 0.15f;

        private final Theme.ResourcesProvider rp;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public IconTileDrawable(Theme.ResourcesProvider rp) {
            this.rp = rp;
        }

        public static int accent(Theme.ResourcesProvider rp) {
            return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, rp);
        }

        @Override
        public void draw(Canvas canvas) {
            final android.graphics.Rect b = getBounds();
            final int size = dp(SIZE_DP);
            final float left = b.centerX() - size / 2f;
            final float top = b.centerY() - size / 2f;
            r.set(left, top, left + size, top + size);
            paint.setColor(accent(rp));
            paint.setAlpha((int) (255 * FILL_ALPHA));
            canvas.drawRoundRect(r, dp(RADIUS_DP), dp(RADIUS_DP), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    public static boolean enabled() {
        try {
            return NekoConfig.meeroCards.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Applies the card look to a row.
     *
     * @param position one of POS_SINGLE / POS_FIRST / POS_MIDDLE / POS_LAST
     */
    public static void apply(View view, int position, Theme.ResourcesProvider rp) {
        if (view == null) {
            return;
        }
        if (!enabled()) {
            view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, rp));
            return;
        }
        view.setBackground(new CardDrawable(position, rp));
        // Inset the content so the text is not flush against the rounded edge.
        final int pad = dp(6);
        view.setPadding(view.getPaddingLeft() + pad, view.getPaddingTop(),
                view.getPaddingRight() + pad, view.getPaddingBottom());
    }

    /** Background for a row that is part of a rounded card. */
    public static class CardDrawable extends Drawable {

        private final int position;
        private final Theme.ResourcesProvider rp;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hairline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float[] radii = new float[8];
        private final android.graphics.Path path = new android.graphics.Path();

        public CardDrawable(int position, Theme.ResourcesProvider rp) {
            this.position = position;
            this.rp = rp;
            hairline.setStyle(Paint.Style.STROKE);
            hairline.setStrokeWidth(Math.max(1f, dp(0.5f)));
        }

        @Override
        public void draw(Canvas canvas) {
            final int base = Theme.getColor(Theme.key_windowBackgroundWhite, rp);
            // Lift the card slightly off the page so it reads as a surface,
            // the way iOS 26 tints grouped cells.
            final boolean dark = isDark(base);
            fill.setColor(meeroLift(base));

            final int m = dp(SIDE_MARGIN_DP);
            final float r = dp(RADIUS_DP);
            final android.graphics.Rect b = getBounds();

            // Bleed a hair past the shared edges. Without this the rounding
            // left a sliver of page colour between rows, which read as a dark
            // line running through the card.
            // Every row is now a standalone card, so instead of bleeding into
            // its neighbours it insets vertically to leave a visible gap.
            final float gap = position == POS_SINGLE ? dp(3) : 0f;
            float top = b.top + gap;
            float bottom = b.bottom - gap;
            if (position == POS_MIDDLE || position == POS_LAST) {
                top -= 1f;
            }
            if (position == POS_MIDDLE || position == POS_FIRST) {
                bottom += 1f;
            }
            rect.set(b.left + m, top, b.right - m, bottom);

            // Square off the joins so consecutive rows read as one card. Only
            // the outer corners of the group stay rounded.
            java.util.Arrays.fill(radii, r);
            if (position == POS_MIDDLE) {
                java.util.Arrays.fill(radii, 0f);
            } else if (position == POS_FIRST) {
                radii[4] = radii[5] = radii[6] = radii[7] = 0f; // bottom corners
            } else if (position == POS_LAST) {
                radii[0] = radii[1] = radii[2] = radii[3] = 0f; // top corners
            }
            canvas.save();
            path.reset();
            path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW);
            canvas.drawPath(path, fill);

            // A hairline separator between rows inside the same card, inset on
            // both sides so it never touches the rounded edge. Drawn from the
            // card colour rather than key_divider, which is tuned for the page
            // background and came out almost black on top of the card.
            // A separator only makes sense inside a shared card; standalone
            // cards are already told apart by the gap between them.
            if (position == POS_FIRST || position == POS_MIDDLE) {
                hairline.setColor(dark ? lighten(fill.getColor(), 0.10f) : darken(fill.getColor(), 0.08f));
                final float inset = dp(SIDE_MARGIN_DP + 14);
                final float y = b.bottom - hairline.getStrokeWidth();
                canvas.drawLine(b.left + inset, y, b.right - inset, y, hairline);
            }
            canvas.restore();
        }

        private static boolean isDark(int color) {
            return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128;
        }

        private static int darken(int color, float amount) {
            final int r = (int) Math.max(0, Color.red(color) - 255 * amount);
            final int g = (int) Math.max(0, Color.green(color) - 255 * amount);
            final int b = (int) Math.max(0, Color.blue(color) - 255 * amount);
            return Color.argb(Color.alpha(color), r, g, b);
        }

        private static int lighten(int color, float amount) {
            final int r = (int) Math.min(255, Color.red(color) + 255 * amount);
            final int g = (int) Math.min(255, Color.green(color) + 255 * amount);
            final int b = (int) Math.min(255, Color.blue(color) + 255 * amount);
            return Color.argb(Color.alpha(color), r, g, b);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
