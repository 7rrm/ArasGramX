package tw.nekomimi.nekogram;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX: the hairline border that sits on top of a glass surface.
 *
 * Measurements come from the reference app's GlassCard:
 *
 *     border = BorderStroke(1.dp, outline.copy(alpha = 0.18f))
 *     color  = surface.copy(alpha = 0.86f)
 *     cornerRadius = 20.dp
 *
 * The alpha values are ratios rather than fixed colours, so the outline tracks
 * whatever theme the user has picked instead of being tied to one palette.
 */
public class MeeroGlass {

    /** Border thickness from the reference. */
    public static final float BORDER_DP = 1f;
    /** Border opacity from the reference: 18%. */
    public static final float BORDER_ALPHA = 0.18f;

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

    public static boolean enabled() {
        try {
            return NekoConfig.meeroGlassBorders.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * The outline colour for a surface, derived from the theme.
     *
     * White on dark themes and black on light ones, both at 18% - this keeps
     * the edge readable without picking up a hue from the wallpaper.
     */
    public static int borderColor(Theme.ResourcesProvider rp) {
        final int base = Theme.getColor(Theme.key_windowBackgroundWhite, rp);
        final int nativeBorder = MeeroCore.glassCore() ? MeeroCore.nGlassBorder(base) : 0x7FFFFFFF;
        if (nativeBorder != 0x7FFFFFFF) {
            return nativeBorder;
        }
        final boolean dark = (0.299 * Color.red(base)
                + 0.587 * Color.green(base)
                + 0.114 * Color.blue(base)) < 128;
        final int tint = dark ? 0xFFFFFFFF : 0xFF000000;
        return Color.argb((int) (255 * BORDER_ALPHA),
                Color.red(tint), Color.green(tint), Color.blue(tint));
    }

    /**
     * Draws the hairline just inside {@code bounds}.
     *
     * Insetting by half the stroke keeps the whole line inside the shape;
     * without it the outer half is clipped and the border looks thinner on
     * one side than the other.
     */
    public static void drawBorder(Canvas canvas, RectF bounds, float radius, Theme.ResourcesProvider rp) {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, dp(uic(29, BORDER_DP))));
        p.setColor(borderColor(rp));
        final float half = p.getStrokeWidth() / 2f;
        final RectF r = new RectF(bounds);
        r.inset(half, half);
        canvas.drawRoundRect(r, radius, radius, p);
    }

    /**
     * Wraps a drawable and strokes a hairline around it.
     *
     * Used where the surface itself is produced by the fork's blur pipeline
     * and we only want to add the edge.
     */
    public static class BorderedDrawable extends Drawable {

        private final Drawable inner;
        private final float radius;
        private final Theme.ResourcesProvider rp;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private final float inset;

        public BorderedDrawable(Drawable inner, float radius, Theme.ResourcesProvider rp) {
            this(inner, radius, 0f, rp);
        }

        /**
         * @param inset how far the visible surface sits inside the drawable's
         *              bounds. The fork's blur panels reserve padding for
         *              their shadow, so the outline has to move in by the same
         *              amount or it lands out in the shadow instead of on the
         *              panel edge.
         */
        public BorderedDrawable(Drawable inner, float radius, float inset, Theme.ResourcesProvider rp) {
            this.inner = inner;
            this.radius = radius;
            this.inset = inset;
            this.rp = rp;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(1f, dp(uic(29, BORDER_DP))));
        }

        @Override
        public void draw(Canvas canvas) {
            if (inner != null) {
                inner.setBounds(getBounds());
                inner.draw(canvas);
            }
            if (!enabled()) {
                return;
            }
            stroke.setColor(borderColor(rp));
            final float half = stroke.getStrokeWidth() / 2f;
            rect.set(getBounds());
            rect.inset(inset + half, inset + half);
            canvas.drawRoundRect(rect, radius, radius, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
            if (inner != null) {
                inner.setAlpha(alpha);
            }
            stroke.setAlpha((int) (alpha * uic(30, BORDER_ALPHA)));
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            if (inner != null) {
                inner.setColorFilter(cf);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
