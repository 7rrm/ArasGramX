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
            fill.setColor(dark ? lighten(base, 0.10f) : base);

            final int m = dp(SIDE_MARGIN_DP);
            final float r = dp(RADIUS_DP);
            final android.graphics.Rect b = getBounds();

            // Overdraw past the flat edges so consecutive rows join seamlessly.
            float top = b.top;
            float bottom = b.bottom;
            if (position == POS_MIDDLE || position == POS_LAST) {
                top -= r;
            }
            if (position == POS_MIDDLE || position == POS_FIRST) {
                bottom += r;
            }
            rect.set(b.left + m, top, b.right - m, bottom);

            java.util.Arrays.fill(radii, r);
            canvas.save();
            canvas.clipRect(b.left, b.top, b.right, b.bottom);
            path.reset();
            path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW);
            canvas.drawPath(path, fill);

            // A hairline separator between rows inside the same card.
            if (position == POS_FIRST || position == POS_MIDDLE) {
                hairline.setColor(Theme.getColor(Theme.key_divider, rp));
                hairline.setAlpha(60);
                final float inset = dp(SIDE_MARGIN_DP + 16);
                canvas.drawLine(b.left + inset, b.bottom - hairline.getStrokeWidth() / 2f,
                        b.right - dp(SIDE_MARGIN_DP), b.bottom - hairline.getStrokeWidth() / 2f, hairline);
            }
            canvas.restore();
        }

        private static boolean isDark(int color) {
            return (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) < 128;
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
