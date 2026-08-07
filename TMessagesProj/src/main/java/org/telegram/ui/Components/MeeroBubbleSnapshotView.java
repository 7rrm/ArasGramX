package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.ChatMessageCell;

/**
 * MeeroX: a still image of the bubble the user long-pressed, shown inside the
 * message-menu popup window.
 *
 * Upstream draws the highlighted bubble inside contentView while the menu is a
 * separate system PopupWindow, so the bubble can never be raised above the
 * menu. Instead of fighting that, the bubble is captured to a bitmap and put in
 * the popup itself, which is what iOS effectively shows:
 *
 *     [reaction bar]
 *     [bubble]
 *     [menu]
 *
 * Tall messages become scrollable rather than being shrunk to an unreadable
 * size - again matching iOS, where a long message can be scrolled while its
 * menu is open.
 */
public class MeeroBubbleSnapshotView extends FrameLayout {

    /** Never take more than this share of the screen height. */
    private static final float MAX_SCREEN_FRACTION = 0.42f;

    /** Set by ChatActivity once it knows how much room the menu leaves. */
    private int maxContentHeight;
    private int maxContentWidth;

    /** Uniform shrink applied when the bubble is wider than the popup may be. */
    private float fitScale = 1f;

    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    /** Where the captured bubble sits inside the source cell, in px. */
    private final int bubbleLeft, bubbleTop, bubbleWidth, bubbleHeight;

    /**
     * MeeroX v159: iOS tall-message mode. When the whole stack does not fit,
     * ChatActivity switches the copy to full height and the popup container
     * scrolls the stack itself, exactly like iOS's context-menu scroll view.
     * The internal 42% cap + internal scroll (v157, kept for shorter stacks)
     * is bypassed, so maxScroll becomes 0 and this view stops consuming
     * touches, letting the container drive the gesture.
     */
    private boolean fullHeightMode;

    public void setFullHeightMode(boolean f) {
        if (fullHeightMode != f) {
            fullHeightMode = f;
            requestLayout();
        }
    }

    /** How far the content is scrolled when the bubble is taller than we allow. */
    private float scroll;
    private float maxScroll;
    private float lastTouchY;
    private boolean dragging;
    private final int touchSlop;

    private MeeroBubbleSnapshotView(Context context, Bitmap bitmap, int bubbleLeft, int bubbleTop, int bubbleWidth, int bubbleHeight) {
        super(context);
        this.bitmap = bitmap;
        this.bubbleLeft = bubbleLeft;
        this.bubbleTop = bubbleTop;
        this.bubbleWidth = bubbleWidth;
        this.bubbleHeight = bubbleHeight;
        this.touchSlop = dp(6);
        setWillNotDraw(false);
    }

    /**
     * Renders the bubble of {@code cell} into a bitmap.
     *
     * @return null when the cell cannot be captured, in which case the caller
     *         should fall back to upstream behaviour.
     */
    public static MeeroBubbleSnapshotView capture(Context context, ChatMessageCell cell) {
        if (cell == null || cell.getMeasuredWidth() <= 0 || cell.getMeasuredHeight() <= 0) {
            return null;
        }
        int left, top, right, bottom;
        try {
            final int pad = cell.getPaddingTop();
            left = cell.getBackgroundDrawableLeft();
            right = cell.getBackgroundDrawableRight();
            top = pad + cell.getBackgroundDrawableTop();
            bottom = pad + cell.getBackgroundDrawableBottom();
        } catch (Throwable e) {
            return null;
        }
        if (right <= left || bottom <= top) {
            // Channel-post cells and other layouts without a bubble background
            // report degenerate bounds here; capturing the whole cell still
            // forms the iOS stack correctly for them instead of silently
            // dropping to the overlapping upstream menu.
            left = 0;
            top = cell.getPaddingTop();
            right = cell.getMeasuredWidth();
            bottom = cell.getMeasuredHeight();
        }
        // The sender avatar sits outside the bubble background, so capturing
        // only the bubble sliced it in half. Grow the region to cover it.
        try {
            final org.telegram.messenger.ImageReceiver avatar = cell.getAvatarImage();
            if (avatar != null) {
                final int aLeft = (int) avatar.getImageX();
                final int aRight = (int) avatar.getImageX2();
                final int aTop = (int) avatar.getImageY();
                final int aBottom = (int) avatar.getImageY2();
                if (aRight > aLeft && aBottom > aTop) {
                    left = Math.min(left, aLeft);
                    right = Math.max(right, aRight);
                    top = Math.min(top, aTop);
                    bottom = Math.max(bottom, aBottom);
                }
            }
        } catch (Throwable ignore) {
        }

        // A little breathing room so the tail and any outbounds content (the
        // reaction chips, the effect animation) are not clipped away.
        final int padX = dp(8);
        final int padY = dp(6);
        left = Math.max(0, left - padX);
        top = Math.max(0, top - padY);
        right = Math.min(cell.getMeasuredWidth(), right + padX);
        bottom = Math.min(cell.getMeasuredHeight(), bottom + padY);

        final int w = right - left;
        final int h = bottom - top;
        if (w <= 0 || h <= 0) {
            return null;
        }
        // Guard against absurd allocations on very long messages.
        if ((long) w * h > 4096L * 4096L) {
            return null;
        }

        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        } catch (Throwable e) {
            return null;
        }

        final Canvas canvas = new Canvas(bmp);
        canvas.translate(-left, -top);

        final boolean wasInvalidatesParent = cell.isInvalidatesParentMeero();
        try {
            // While capturing, the cell must paint itself completely instead of
            // deferring pieces to ChatActivity's dispatchDraw.
            cell.setInvalidatesParent(false);
            if (cell.drawBackgroundInParent()) {
                canvas.save();
                canvas.translate(0, cell.getPaddingTop());
                cell.drawBackgroundInternal(canvas, true);
                canvas.restore();
            }
            cell.draw(canvas);
            // The sender avatar and name are painted by ChatActivity after the
            // cell, not inside cell.draw(), so a plain draw() left the avatar
            // out of the snapshot - it stayed behind in the blurred layer and
            // looked sliced. Render that pass here too.
            if (cell.hasNameLayout()) {
                canvas.save();
                cell.drawNamesLayout(canvas, 1f);
                canvas.restore();
            }
            if (cell.hasOutboundsContent()) {
                canvas.save();
                canvas.translate(0, cell.getPaddingTop());
                cell.drawOutboundsContent(canvas);
                canvas.restore();
            }
        } catch (Throwable e) {
            try {
                bmp.recycle();
            } catch (Throwable ignore) {
            }
            return null;
        } finally {
            try {
                cell.setInvalidatesParent(wasInvalidatesParent);
            } catch (Throwable ignore) {
            }
        }

        return new MeeroBubbleSnapshotView(context, bmp, left, top, w, h);
    }

    /** X of the captured region inside the source cell. */
    public int getBubbleLeftInCell() {
        return bubbleLeft;
    }

    /** Y of the captured region inside the source cell. */
    public int getBubbleTopInCell() {
        return bubbleTop;
    }

    public int getBubbleWidthPx() {
        return bubbleWidth;
    }

    public int getBubbleHeightPx() {
        return bubbleHeight;
    }

    /** True when the bubble was too tall to show at once and can be scrolled. */
    public boolean isScrollable() {
        return maxScroll > 0;
    }

    /**
     * Limits handed down from ChatActivity: how much space is left once the
     * reaction bar and the menu have taken theirs. Pass 0 for "no limit".
     */
    public void setMaxContentSize(int width, int height) {
        this.maxContentWidth = width;
        this.maxContentHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Shrink only if the bubble is physically wider than the space we have;
        // text stays at full size otherwise, so it remains readable.
        fitScale = 1f;
        if (maxContentWidth > 0 && bubbleWidth > maxContentWidth) {
            fitScale = maxContentWidth / (float) bubbleWidth;
        }
        final int scaledW = Math.max(1, (int) (bubbleWidth * fitScale));
        final int scaledH = Math.max(1, (int) (bubbleHeight * fitScale));

        int limit;
        if (fullHeightMode) {
            // Tall-stack mode: the container scrolls, so show the full bubble.
            limit = scaledH;
        } else {
            limit = (int) (AndroidUtilities.displaySize.y * MAX_SCREEN_FRACTION);
            if (maxContentHeight > 0) {
                limit = Math.min(limit, maxContentHeight);
            }
        }
        final int specSize = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY && specSize > 0) {
            limit = specSize;
        }
        final int h = Math.min(scaledH, Math.max(dp(40), limit));
        maxScroll = Math.max(0, scaledH - h);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        setMeasuredDimension(resolveSize(scaledW, widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        canvas.save();
        canvas.translate(0, -scroll);
        if (fitScale != 1f) {
            canvas.scale(fitScale, fitScale);
        }
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();

        if (maxScroll > 0) {
            drawScrollHint(canvas);
        }
    }

    /** Slim indicator so it is obvious the message can be scrolled. */
    private void drawScrollHint(Canvas canvas) {
        final float trackW = dp(3);
        final float inset = dp(2);
        final float h = getMeasuredHeight();
        final float thumbH = Math.max(dp(24), h * (h / (float) bubbleHeight));
        final float travel = h - thumbH;
        final float y = travel * (maxScroll <= 0 ? 0 : scroll / maxScroll);
        final float x = getMeasuredWidth() - inset - trackW;

        paint.setColor(0x33000000);
        final RectF r = new RectF(x, y, x + trackW, y + thumbH);
        canvas.drawRoundRect(r, trackW / 2f, trackW / 2f, paint);
        paint.setColor(0xFFFFFFFF);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (maxScroll <= 0) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = event.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                final float dy = lastTouchY - event.getY();
                if (!dragging && Math.abs(dy) > touchSlop) {
                    dragging = true;
                    lastTouchY = event.getY();
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                if (dragging) {
                    scroll += dy;
                    if (scroll < 0) scroll = 0;
                    if (scroll > maxScroll) scroll = maxScroll;
                    lastTouchY = event.getY();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
        }
        return true;
    }

    /** Frees the bitmap. Call from the popup's dismiss handler. */
    public void release() {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }
}
