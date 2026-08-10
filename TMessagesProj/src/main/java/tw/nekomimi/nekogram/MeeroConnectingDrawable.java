package tw.nekomimi.nekogram;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;

/**
 * MeeroX: the spinner iOS shows beside "Connecting..." in the title.
 *
 * Telegram for Android says the same words and animates the three dots after
 * them, but draws nothing else. iOS puts a small open ring next to the label
 * that keeps turning for as long as the state lasts, which is what makes the
 * header read as busy rather than as a sentence that happens to end in dots.
 *
 * The geometry is the one Telegram for iOS already uses for its indefinite
 * rings, from RadialProgressContentNode.swift:
 *
 *     lineWidth = max(1.6, 2.25 * factor)      // factor = diameter / 50
 *     pathDiameter = width - lineWidth - 2.5 * 2
 *
 * and the two periods driving it are that file's own: the layer turns once
 * every 1500ms while the sweep runs 0 -> 2 over 2500ms. The same pair is
 * already used by the download ring in MediaActionDrawable, so the two spin
 * at matching speeds instead of each picking its own.
 *
 * The sweep going to 2 rather than 1 is what gives the ring its shape: over
 * the first half the arc grows from a stub to very nearly a closed circle,
 * and over the second half the tail catches up with the head so it shrinks
 * back to a stub - one continuous motion with no visible restart.
 */
public class MeeroConnectingDrawable extends Drawable {

    /* v189 (batch 3C): every number below (the 50pt reference, the 2.25/1.6
     * stroke rule, the 2.5 path inset, the 1500/2500 ms periods, the 12deg
     * stub floor, the 21dp size derived from the reference screenshot's
     * 41px-over-103px avatar, and the dt clamp/frame) comes from the sealed
     * motion table (dom 'C'); the literals are the byte-identical fallback. */
    private static volatile float[] rec;

    private static float rg(int i, float legacy) {
        float[] r = rec;
        if (r == null && MeeroCore.motionCore()) {
            r = MeeroCore.nRingRecipe();
            if (r != null && r.length == 10) rec = r; else r = null;
        }
        return r != null ? r[i] : legacy;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private long lastFrame;
    private float rotation;
    private float sweep;
    private int alpha = 255;

    public MeeroConnectingDrawable() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** MeeroX: whether the spinner is drawn at all. */
    public static boolean enabled() {
        try {
            return NekoConfig.meeroIosLoading.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final float diameter = Math.min(bounds.width(), bounds.height());
        if (diameter <= 0) {
            return;
        }

        final long now = SystemClock.elapsedRealtime();
        // A first frame, or a return from the background, would otherwise
        // advance the animation by however long the gap was and make the ring
        // jump. Anything longer than a slow frame is treated as one frame.
        long dt = lastFrame == 0 ? 0 : now - lastFrame;
        if (dt < 0 || dt > (long) rg(8, 100f)) {
            dt = (long) rg(9, 16f);
        }
        lastFrame = now;

        rotation += 360f * dt / rg(4, 1500f);
        if (rotation >= 360f) {
            rotation -= 360f;
        }
        sweep += 2f * dt / rg(5, 2500f);
        if (sweep >= 2f) {
            sweep -= 2f;
        }

        // iOS's factor: the ring's diameter over the 50pt reference. Both are
        // taken in the same unit - pixels here - so the ratio is the same
        // number the Swift code works with.
        final float factor = diameter / dpf2(rg(0, 50f));
        final float lineWidth = Math.max(dpf2(rg(2, 1.6f)), dpf2(rg(1, 2.25f)) * factor);
        paint.setStrokeWidth(lineWidth);
        paint.setAlpha(alpha);

        final float inset = lineWidth / 2f + dpf2(rg(3, 2.5f)) * factor;
        rect.set(bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset);

        // First half of the sweep grows the head away from the tail; second
        // half brings the tail up behind it. Both are the same arc, so the
        // ring never blinks between the two.
        final float start;
        final float length;
        if (sweep <= 1f) {
            start = 0f;
            length = 360f * sweep;
        } else {
            start = 360f * (sweep - 1f);
            length = 360f * (2f - sweep);
        }

        canvas.save();
        canvas.rotate(rotation, rect.centerX(), rect.centerY());
        canvas.drawArc(rect, start - 90f, Math.max(rg(6, 12f), length), false, paint);
        canvas.restore();

        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return dp((int) rg(7, 21f));
    }

    @Override
    public int getIntrinsicHeight() {
        return dp((int) rg(7, 21f));
    }
}
