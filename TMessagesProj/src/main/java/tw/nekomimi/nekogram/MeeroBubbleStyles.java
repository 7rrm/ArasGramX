package tw.nekomimi.nekogram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * MeeroX v122 - chat-bubble shape library and the single source of truth for
 * every custom bubble outline.
 *
 * Style 1 ("official iOS") reproduces, point for point, the outline Telegram
 * for iOS draws in
 * submodules/TelegramPresentationData/Sources/ChatMessageBubbleImages.swift
 * (TelegramMessenger/Telegram-iOS):
 *
 *     bottomEllipse = CGRect(x: 24, y: 16, w: 27, h: 17)   // bulge, bottom half
 *     topEllipse    = CGRect(x: 33, y: 14, w: 23, h: 21)   // carved-out notch
 *
 * Solving that pair inside the iOS 33pt tile leaves a crescent that starts
 * 9pt left of the body edge and 8.5pt above the baseline, sweeps to a tip
 * 4.74pt past the edge exactly ON the baseline, then follows the carved
 * ellipse back up to the edge. An iOS pt tracks an Android dp closely, so the
 * absolute pt numbers below are used as dp.
 *
 * The real bubbles and the settings previews can never drift apart:
 * Theme.MessageDrawable renders through the same append*Tail() helpers that
 * the picker previews below draw with.
 */
public final class MeeroBubbleStyles {

    public static final int COUNT = 5;

    public static final int STOCK = 0;        // stock Telegram for Android, untouched
    public static final int IOS_OFFICIAL = 1; // official iOS: 18dp corners + crescent tail
    public static final int IOS_MODERN = 2;   // 18dp corners, no tail
    public static final int CAPSULE = 3;      // half-height pill ends, no tail
    public static final int CLASSIC = 4;      // 10dp corners + small wedge tail

    private MeeroBubbleStyles() {
    }

    /** Currently selected style; any failure leaves stock in charge. */
    public static int current() {
        try {
            return NekoConfig.meeroBubbleStyle.Int();
        } catch (Throwable ignore) {
            return STOCK;
        }
    }

    /**
     * Corner radius per style, in dp. Stock hands the caller's own radius
     * back so style 0 renders byte-for-byte like upstream. generatePath
     * clamps the radius to half of the bubble height, which is why CAPSULE
     * can simply ask for a huge value.
     */
    public static int radiusDp(int style, int fallback) {
        switch (style) {
            case IOS_OFFICIAL:
            case IOS_MODERN:
                // iOS mainRadius is ~17pt; 18dp reproduces the iPhone curve.
                return 18;
            case CAPSULE:
                return 1000;
            case CLASSIC:
                return 10;
            case STOCK:
            default:
                return fallback;
        }
    }

    /** True for the two clean, tail-free styles. */
    public static boolean isTailless(int style) {
        return style == IOS_MODERN || style == CAPSULE;
    }

    /**
     * Appends the official iOS crescent tail as ITS OWN closed contour(s), so
     * the corner of the body keeps its full radius and the crescent curls out
     * from behind it - exactly how ChatMessageBubbleImages.swift layers the
     * two ellipses. `dpu` is pixels-per-dp (callers pass
     * AndroidUtilities.density).
     *
     * The v122 build drew only the droplet and left a wedge of background
     * between the corner arc and the tail (the visible "break"). The missing
     * piece is the CONNECTOR the official file fills between the corner and
     * the tail chord:
     *
     *     fill(CGRect(x: 16.5, y: 16.5, w: 16.5, h: ~8))   // in the 33pt tile
     *
     * i.e. the body's edge keeps running down to the chord and the crescent
     * flows out of it with no gap. That column is drawn here first, mirrored
     * per side. The outgoing contour winds clockwise and the incoming one
     * counter-clockwise, matching generatePath's per-branch traversal so the
     * contours UNION with the body instead of cutting a hole in it.
     */
    public static void appendOfficialTail(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        if (outgoing) {
            // Connector column: keeps the edge straight down to the chord,
            // exactly like the official fill between the corner and the tail.
            path.addRect(edgeX - 16.5f * dpu, baseY - 16.5f * dpu, edgeX, baseY - 8.5f * dpu, Path.Direction.CW);
            // Crescent: ellipse-half (13.5 x 8.5) minus ellipse (11.5 x 10.5).
            path.moveTo(edgeX - 9f * dpu, baseY - 8.5f * dpu);
            path.lineTo(edgeX, baseY - 8.5f * dpu);
            path.quadTo(edgeX + 0.13f * dpu, baseY - 3.21f * dpu, edgeX + 4.74f * dpu, baseY);
            path.lineTo(edgeX + 4.5f * dpu, baseY);
            path.quadTo(edgeX - 9f * dpu, baseY, edgeX - 9f * dpu, baseY - 8.5f * dpu);
            path.close();
        } else {
            path.addRect(edgeX, baseY - 16.5f * dpu, edgeX + 16.5f * dpu, baseY - 8.5f * dpu, Path.Direction.CCW);
            path.moveTo(edgeX + 9f * dpu, baseY - 8.5f * dpu);
            path.lineTo(edgeX, baseY - 8.5f * dpu);
            path.quadTo(edgeX - 0.13f * dpu, baseY - 3.21f * dpu, edgeX - 4.74f * dpu, baseY);
            path.lineTo(edgeX - 4.5f * dpu, baseY);
            path.quadTo(edgeX + 9f * dpu, baseY, edgeX + 9f * dpu, baseY - 8.5f * dpu);
            path.close();
        }
    }

    /**
     * Appends the classic wedge tail (style 4): a small curved triangle that
     * leans out past the corner with its tip resting on the baseline. Same
     * winding contract as appendOfficialTail().
     */
    public static void appendClassicTail(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        if (outgoing) {
            path.moveTo(edgeX, baseY - 9f * dpu);
            path.quadTo(edgeX + 4.8f * dpu, baseY - 2.5f * dpu, edgeX + 3.5f * dpu, baseY);
            path.lineTo(edgeX - 9f * dpu, baseY);
            path.close();
        } else {
            path.moveTo(edgeX, baseY - 9f * dpu);
            path.quadTo(edgeX - 4.8f * dpu, baseY - 2.5f * dpu, edgeX - 3.5f * dpu, baseY);
            path.lineTo(edgeX + 9f * dpu, baseY);
            path.close();
        }
    }

    /**
     * Live preview drawable used by the settings picker rows. Draws a mini
     * outgoing bubble in the current theme's outgoing colour through the very
     * same tail contours, so what is picked is exactly what ships to chat.
     */
    public static Drawable previewDrawable(int style) {
        return new Drawable() {
            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Path path = new Path();
            private int buildKey = -1;

            {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(Theme.getColor(Theme.key_chat_outBubble));
            }

            @Override
            protected void onBoundsChange(Rect bounds) {
                buildKey = -1;
            }

            @Override
            public void draw(Canvas canvas) {
                final Rect b = getBounds();
                final int key = b.left * 31 + b.top * 7 + b.right * 3 + b.bottom;
                if (key != buildKey) {
                    buildKey = key;
                    buildPreviewPath(path, style, b);
                }
                canvas.drawPath(path, fill);
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

            @Override
            public int getIntrinsicWidth() {
                return AndroidUtilities.dp(58);
            }

            @Override
            public int getIntrinsicHeight() {
                return AndroidUtilities.dp(32);
            }
        };
    }

    /**
     * Builds the mini outgoing bubble used by the previews: a clockwise
     * rounded rect (so the official contour unions with it) with tail room on
     * the right.
     */
    private static void buildPreviewPath(Path path, int style, Rect b) {
        final float dpu = AndroidUtilities.density;
        final float bodyRight = b.right - 7f * dpu;
        float rad = radiusDp(style, 12) * dpu;
        rad = Math.min(rad, (b.height() - 2f * dpu) / 2f);

        path.reset();
        final RectF rf = new RectF(b.left + dpu, b.top + dpu, bodyRight, b.bottom - dpu);
        path.addRoundRect(rf, rad, rad, Path.Direction.CW);

        final float baseY = b.bottom - dpu;
        if (style == IOS_OFFICIAL) {
            appendOfficialTail(path, bodyRight, baseY, true, dpu);
        } else if (style == CLASSIC) {
            appendClassicTail(path, bodyRight, baseY, true, dpu);
        }
    }
}
