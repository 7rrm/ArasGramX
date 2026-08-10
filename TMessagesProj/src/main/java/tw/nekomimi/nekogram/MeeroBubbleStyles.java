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

/**
 * MeeroX v124 - chat-bubble shape library and the single source of truth for
 * every custom bubble outline.
 *
 * Style 1 ("official iOS") reproduces, point for point, the outline Telegram
 * for iOS draws in
 * submodules/TelegramPresentationData/Sources/ChatMessageBubbleImages.swift
 * (TelegramMessenger/Telegram-iOS):
 *
 *     bottomEllipse = CGRect(x: 24, y: 16, w: 27, h: 17)   // bulge, bottom half
 *     topEllipse    = CGRect(x: 33, y: 14, w: 23, h: 21)   // carved-out notch
 *     connector     = fill(CGRect x:16.5, y:16.5, w:16.5, h:~8)
 *
 * Solving that pair inside the iOS 33pt tile leaves a crescent that starts
 * 9pt left of the body edge and 8.5pt above the baseline, sweeps to a tip
 * 4.74pt past the edge exactly ON the baseline, then follows the carved
 * ellipse back up to the edge; the connector column keeps the edge running
 * down to the chord so no gap shows. An iOS pt tracks an Android dp closely,
 * so the absolute pt numbers are used as dp.
 *
 * The real bubbles and the picker previews can never drift apart:
 * Theme.MessageDrawable renders through the same append*Tail() helpers that
 * drawPreview() below draws with.
 */
public final class MeeroBubbleStyles {

    public static final int COUNT = 8;

    public static final int STOCK = 0;        // stock Telegram for Android, untouched
    public static final int IOS_OFFICIAL = 1; // official iOS: 18dp corners + crescent tail
    public static final int IOS_MODERN = 2;   // 18dp corners, no tail
    public static final int CAPSULE = 3;      // half-height pill ends, no tail
    public static final int CLASSIC = 4;      // 10dp corners + small wedge tail
    public static final int SHARP = 5;        // 6dp straight corners, no tail
    public static final int INSTAGRAM = 6;    // 22dp extra-rounded, no tail
    public static final int WHATSAPP = 7;     // 8dp corners + tiny curved nub

    private MeeroBubbleStyles() {
    }

    /* v188 (batch 3B): radius table / tailless mask / tail recipes / preview
     * constants arrive from the sealed native table (dom 'B'); every method
     * keeps its legacy literals as the byte-identical fallback. */
    private static float[] sTail(int style) {
        return MeeroCore.chatCore() ? MeeroCore.nBBTailParams(style) : null;
    }

    private static float[] sPrev;
    private static float pvc(int i, float fb) {
        float[] p = sPrev;
        if (p == null) {
            float[] n = MeeroCore.chatCore() ? MeeroCore.nBBPreviewConsts() : null;
            p = (n != null && n.length == 4) ? n : new float[0];
            sPrev = p;
        }
        return p.length == 4 ? p[i] : fb;
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
        if (MeeroCore.chatCore()) {
            return MeeroCore.nBBRadius(style, fallback);
        }
        switch (style) {
            case IOS_OFFICIAL:
            case IOS_MODERN:
                // iOS mainRadius is ~17pt; 18dp reproduces the iPhone curve.
                return 18;
            case CAPSULE:
                return 1000;
            case CLASSIC:
                return 10;
            case SHARP:
                return 6;
            case INSTAGRAM:
                return 22;
            case WHATSAPP:
                return 8;
            case STOCK:
            default:
                return fallback;
        }
    }

    /** True for the clean, tail-free styles. */
    public static boolean isTailless(int style) {
        if (MeeroCore.chatCore()) {
            return MeeroCore.nBBTailless(style);
        }
        return style == IOS_MODERN || style == CAPSULE || style == SHARP || style == INSTAGRAM;
    }

    /**
     * Appends the official iOS crescent tail: the CONNECTOR column first
     * (without it the corner arc retreats and the tail reads detached - the
     * v122 "break"), then the crescent as its own closed contour. `dpu` is
     * pixels-per-dp (callers pass AndroidUtilities.density).
     *
     * The outgoing contours wind clockwise and the incoming ones
     * counter-clockwise, matching generatePath's per-branch traversal so the
     * contours UNION with the body instead of cutting a hole in it.
     */
    public static void appendOfficialTail(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        final float[] tp = sTail(IOS_OFFICIAL);
        final float connW  = tp != null && tp.length == 10 ? tp[0] : 16.5f;
        final float connT  = tp != null && tp.length == 10 ? tp[1] : 16.5f;
        final float connB  = tp != null && tp.length == 10 ? tp[2] : 8.5f;
        final float inX    = tp != null && tp.length == 10 ? tp[3] : 9f;
        final float base   = tp != null && tp.length == 10 ? tp[4] : 8.5f;
        final float ctlX   = tp != null && tp.length == 10 ? tp[5] : 0.13f;
        final float ctlY   = tp != null && tp.length == 10 ? tp[6] : 3.21f;
        final float tipX   = tp != null && tp.length == 10 ? tp[7] : 4.74f;
        final float nearX  = tp != null && tp.length == 10 ? tp[8] : 4.5f;
        final float farX   = tp != null && tp.length == 10 ? tp[9] : 9f;
        if (outgoing) {
            path.addRect(edgeX - connW * dpu, baseY - connT * dpu, edgeX, baseY - connB * dpu, Path.Direction.CW);
            path.moveTo(edgeX - inX * dpu, baseY - base * dpu);
            path.lineTo(edgeX, baseY - base * dpu);
            path.quadTo(edgeX + ctlX * dpu, baseY - ctlY * dpu, edgeX + tipX * dpu, baseY);
            path.lineTo(edgeX + nearX * dpu, baseY);
            path.quadTo(edgeX - farX * dpu, baseY, edgeX - farX * dpu, baseY - base * dpu);
            path.close();
        } else {
            path.addRect(edgeX, baseY - connT * dpu, edgeX + connW * dpu, baseY - connB * dpu, Path.Direction.CCW);
            path.moveTo(edgeX + inX * dpu, baseY - base * dpu);
            path.lineTo(edgeX, baseY - base * dpu);
            path.quadTo(edgeX - ctlX * dpu, baseY - ctlY * dpu, edgeX - tipX * dpu, baseY);
            path.lineTo(edgeX - nearX * dpu, baseY);
            path.quadTo(edgeX + farX * dpu, baseY, edgeX + farX * dpu, baseY - base * dpu);
            path.close();
        }
    }

    /**
     * Appends the classic wedge tail (style 4): a small curved triangle that
     * leans out past the corner with its tip resting on the baseline. Same
     * winding contract as appendOfficialTail().
     */
    public static void appendClassicTail(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        final float[] tp = sTail(CLASSIC);
        final float topY = tp != null && tp.length == 5 ? tp[0] : 9f;
        final float ctlX = tp != null && tp.length == 5 ? tp[1] : 4.8f;
        final float ctlY = tp != null && tp.length == 5 ? tp[2] : 2.5f;
        final float tipX = tp != null && tp.length == 5 ? tp[3] : 3.5f;
        final float bckX = tp != null && tp.length == 5 ? tp[4] : 9f;
        if (outgoing) {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.quadTo(edgeX + ctlX * dpu, baseY - ctlY * dpu, edgeX + tipX * dpu, baseY);
            path.lineTo(edgeX - bckX * dpu, baseY);
            path.close();
        } else {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.quadTo(edgeX - ctlX * dpu, baseY - ctlY * dpu, edgeX - tipX * dpu, baseY);
            path.lineTo(edgeX + bckX * dpu, baseY);
            path.close();
        }
    }

    /**
     * Appends the WhatsApp-style tiny curved nub (style 7): shorter and
     * slimmer than the classic wedge, hugging the corner. Same winding
     * contract as appendOfficialTail().
     */
    public static void appendWhatsAppTail(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        final float[] tp = sTail(WHATSAPP);
        final float topY = tp != null && tp.length == 5 ? tp[0] : 8f;
        final float ctlX = tp != null && tp.length == 5 ? tp[1] : 3.6f;
        final float ctlY = tp != null && tp.length == 5 ? tp[2] : 2.4f;
        final float tipX = tp != null && tp.length == 5 ? tp[3] : 2.6f;
        final float bckX = tp != null && tp.length == 5 ? tp[4] : 8f;
        if (outgoing) {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.quadTo(edgeX + ctlX * dpu, baseY - ctlY * dpu, edgeX + tipX * dpu, baseY);
            path.lineTo(edgeX - bckX * dpu, baseY);
            path.close();
        } else {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.quadTo(edgeX - ctlX * dpu, baseY - ctlY * dpu, edgeX - tipX * dpu, baseY);
            path.lineTo(edgeX + bckX * dpu, baseY);
            path.close();
        }
    }

    /**
     * The small nub stock Telegram for Android draws, approximated as a
     * single soft triangle - used by the previews only, so style 0 reads
     * like the real thing. Same winding contract as appendOfficialTail().
     */
    private static void appendStockNub(Path path, float edgeX, float baseY, boolean outgoing, float dpu) {
        final float[] tp = sTail(STOCK);
        final float topY = tp != null && tp.length == 3 ? tp[0] : 6f;
        final float tipX = tp != null && tp.length == 3 ? tp[1] : 2.2f;
        final float bckX = tp != null && tp.length == 3 ? tp[2] : 7f;
        if (outgoing) {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.lineTo(edgeX + tipX * dpu, baseY);
            path.lineTo(edgeX - bckX * dpu, baseY);
            path.close();
        } else {
            path.moveTo(edgeX, baseY - topY * dpu);
            path.lineTo(edgeX - tipX * dpu, baseY);
            path.lineTo(edgeX + bckX * dpu, baseY);
            path.close();
        }
    }

    /**
     * Draws one mini bubble of `style` inside the given area. The area must
     * leave a 7dp tail allowance on the OUTING side (right for outgoing,
     * left for incoming); the body sits 1dp inside on all other sides and
     * any tail grows into the allowance. Every preview in the pickers is
     * drawn here, and it routes through the very same contours the chat
     * renderer appends - what you pick is what you get.
     */
    public static void drawPreview(Canvas canvas, int style, boolean incoming, float l, float t, float r, float b, int color) {
        final float dpu = AndroidUtilities.density;
        final float allow = pvc(0, 7f);   // tail allowance
        final float bodyR = incoming ? r : r - allow * dpu;
        final float bodyL = incoming ? l + allow * dpu : l;
        float rad = radiusDp(style, (int) pvc(2, 12f)) * dpu;
        rad = Math.min(rad, (b - t - pvc(3, 2f) * dpu) / 2f);

        final Path path = new Path();
        // v125: the body MUST wind the way the tail contours expect for each
        // side (outgoing CW, incoming CCW) - in generatePath the incoming
        // body happens to be CCW, which is why real bubbles were fine while
        // the CW incoming preview cancelled the overlap and spat the tail
        // out as a detached chunk.
        final float inD = pvc(1, 1f) * dpu;
        path.addRoundRect(new RectF(bodyL, t + inD, bodyR, b - inD), rad, rad, incoming ? Path.Direction.CCW : Path.Direction.CW);
        final float baseY = b - inD;
        if (style == IOS_OFFICIAL) {
            appendOfficialTail(path, incoming ? bodyL : bodyR, baseY, incoming, dpu);
        } else if (style == CLASSIC) {
            appendClassicTail(path, incoming ? bodyL : bodyR, baseY, incoming, dpu);
        } else if (style == WHATSAPP) {
            appendWhatsAppTail(path, incoming ? bodyL : bodyR, baseY, incoming, dpu);
        } else if (style == STOCK) {
            appendStockNub(path, incoming ? bodyL : bodyR, baseY, incoming, dpu);
        }

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawPath(path, paint);
    }

    /**
     * Card thumbnail drawable for the picker: a single outgoing mini bubble
     * in `color`. Merely a drawable wrapper over drawPreview(), so the cards
     * and the hero can never disagree.
     */
    public static Drawable previewDrawable(final int style, final int color) {
        return new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                final Rect b = getBounds();
                drawPreview(canvas, style, false, b.left, b.top, b.right, b.bottom, color);
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
    }
}
