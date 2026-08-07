package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

public class ChatScrimPopupContainerLayout extends LinearLayout {

    private ReactionsContainerLayout reactionsLayout;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
    private View bottomView;
    private int maxHeight;
    private float popupLayoutLeftOffset;
    private float progressToSwipeBack;
    private float bottomViewYOffset;
    private float expandSize;
    private float bottomViewReactionsOffset;

    public ChatScrimPopupContainerLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
    }

    // MeeroX v159: the single onLayout() lives at the bottom of this class -
    // it now also applies the tall-stack offset. The old empty override that
    // used to sit here was merged into it (duplicate method = build error).

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        if (reactionsLayout != null && popupWindowLayout != null) {
            reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
            ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = 0;
            popupLayoutLeftOffset = 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int maxWidth = reactionsLayout.getMeasuredWidth();
            if (popupWindowLayout.getSwipeBack() != null && popupWindowLayout.getSwipeBack().getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getSwipeBack().getMeasuredWidth();
            }
            if (popupWindowLayout.getMeasuredWidth() > maxWidth) {
                maxWidth = popupWindowLayout.getMeasuredWidth();
            }
            if (reactionsLayout.showCustomEmojiReaction()) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
            }
            reactionsLayout.measureHint();

            int reactionsLayoutTotalWidth = reactionsLayout.getTotalWidth();
            View menuContainer = popupWindowLayout.getSwipeBack() != null ? popupWindowLayout.getSwipeBack().getChildAt(0) : popupWindowLayout.getChildAt(0);
            int maxReactionsLayoutWidth = menuContainer.getMeasuredWidth() + dp(16) + dp(16) + dp(36);
            int hintTextWidth = reactionsLayout.getHintTextWidth();
            if (hintTextWidth > maxReactionsLayoutWidth) {
                maxReactionsLayoutWidth = hintTextWidth;
            } else if (maxReactionsLayoutWidth > maxWidth) {
                maxReactionsLayoutWidth = maxWidth;
            }
            reactionsLayout.bigCircleOffset = dp(36);
            if (reactionsLayout.showCustomEmojiReaction()) {
                reactionsLayout.getLayoutParams().width = reactionsLayoutTotalWidth;
                reactionsLayout.bigCircleOffset = Math.max(reactionsLayoutTotalWidth - menuContainer.getMeasuredWidth() - dp(36), dp(36));
            } else if (reactionsLayoutTotalWidth > maxReactionsLayoutWidth) {
                int maxFullCount = ((maxReactionsLayoutWidth - dp(16)) / dp(36)) + 1;
                int newWidth = maxFullCount * dp(36) + dp(8);
                if (hintTextWidth + dp(24) > newWidth) {
                    newWidth = hintTextWidth + dp(24);
                }
                if (newWidth > reactionsLayoutTotalWidth || maxFullCount == reactionsLayout.getItemsCount()) {
                    newWidth = reactionsLayoutTotalWidth;
                }
                reactionsLayout.getLayoutParams().width = newWidth;
            } else {
                reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
            }
            int widthDiff = 0;
            if (reactionsLayout.getMeasuredWidth() != maxWidth || !reactionsLayout.showCustomEmojiReaction()) {
                if (popupWindowLayout.getSwipeBack() != null) {
                    widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - popupWindowLayout.getSwipeBack().getChildAt(0).getMeasuredWidth();
                }
                if (reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT && reactionsLayout.getLayoutParams().width + widthDiff > maxWidth) {
                    widthDiff = maxWidth - reactionsLayout.getLayoutParams().width + dp(8);
                }
                if (widthDiff < 0) {
                    widthDiff = 0;
                }
                ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = widthDiff;
                popupLayoutLeftOffset = 0;
                updatePopupTranslation();
            } else {
                popupLayoutLeftOffset = (maxWidth - menuContainer.getMeasuredWidth()) * 0.25f;
                reactionsLayout.bigCircleOffset -= popupLayoutLeftOffset;
                if (reactionsLayout.bigCircleOffset < dp(36)) {
                    popupLayoutLeftOffset = 0;
                    reactionsLayout.bigCircleOffset = dp(36);
                }
                updatePopupTranslation();
            }
            if (bottomView != null) {
                if (reactionsLayout.showCustomEmojiReaction()) {
                    bottomView.getLayoutParams().width = menuContainer.getMeasuredWidth() + dp(16);
                    updatePopupTranslation();
                } else {
                    bottomView.getLayoutParams().width = LayoutHelper.MATCH_PARENT;
                }
                if (popupWindowLayout.getSwipeBack() != null) {
                    ((LayoutParams) bottomView.getLayoutParams()).rightMargin = widthDiff + dp(36);
                } else {
                    ((LayoutParams) bottomView.getLayoutParams()).rightMargin = dp(36);
                }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        maxHeight = getMeasuredHeight();
    }

    private void updatePopupTranslation() {
        float x = (1f - progressToSwipeBack) * popupLayoutLeftOffset;
        popupWindowLayout.setTranslationX(x);
        if (bottomView != null) {
            bottomView.setTranslationX(x);
        }
    }

    public void applyViewBottom(FrameLayout bottomView) {
        this.bottomView = bottomView;
    }

    public void setReactionsLayout(ReactionsContainerLayout reactionsLayout) {
        this.reactionsLayout = reactionsLayout;
        if (reactionsLayout != null) {
            reactionsLayout.setChatScrimView(this);
        }
    }

    public void setPopupWindowLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout) {
        this.popupWindowLayout = popupWindowLayout;
        popupWindowLayout.setOnSizeChangedListener(() -> {
            if (bottomView != null) {
                bottomViewYOffset = popupWindowLayout.getVisibleHeight() - popupWindowLayout.getMeasuredHeight();
                updateBottomViewPosition();
            }
        });
        if (popupWindowLayout.getSwipeBack() != null) {
            popupWindowLayout.getSwipeBack().addOnSwipeBackProgressListener((layout, toProgress, progress) -> {
                if (bottomView != null) {
                    bottomView.setAlpha(1f - progress);
                }
                progressToSwipeBack = progress;
                updatePopupTranslation();
            });
        }
    }

    private void updateBottomViewPosition() {
        if (bottomView != null) {
            // MeeroX v159: the tall-stack offset scrolls the alert strip
            // together with the rest of the stack.
            bottomView.setTranslationY(bottomViewYOffset + expandSize + bottomViewReactionsOffset + (meeroTallStack ? meeroDisplayedStackOffset() : 0));
        }
    }

    /** Stack offset including damped rubber-band overscroll. */
    private float meeroDisplayedStackOffset() {
        return meeroStackOffset + meeroOverscroll * 0.35f;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setExpandSize(float expandSize) {
        // MeeroX v159: compose with the tall-stack scroll offset.
        popupWindowLayout.setTranslationY(expandSize + (meeroTallStack ? meeroDisplayedStackOffset() : 0));
        this.expandSize = expandSize;
        updateBottomViewPosition();
    }

    public void setPopupAlpha(float alpha) {
        popupWindowLayout.setAlpha(alpha);
        if (bottomView != null) {
            bottomView.setAlpha(alpha);
        }
    }

    public void setReactionsTransitionProgress(float v) {
        popupWindowLayout.setReactionsTransitionProgress(v);
        if (bottomView != null) {
            bottomView.setAlpha(v);
            float scale = 0.5f + v * 0.5f;
            bottomView.setPivotX(bottomView.getMeasuredWidth());
            bottomView.setPivotY(0);
            bottomViewReactionsOffset = -popupWindowLayout.getMeasuredHeight() * (1f - v);
            updateBottomViewPosition();
            bottomView.setScaleX(scale);
            bottomView.setScaleY(scale);
        }
    }

    // ------------------------------------------------------------------
    // MeeroX v159: iOS tall-message stack scrolling.
    //
    // Telegram-iOS (submodules/TelegramUI/Components/ContextControllerImpl/
    // ContextControllerExtractedPresentationNode.swift) puts the extracted
    // message, the reaction bar and the action list inside ONE scroll view;
    // when the stack is taller than the screen it opens pre-scrolled to the
    // BOTTOM (menu docked, message top off-screen), the whole stack scrolls,
    // and overshooting either edge dismisses the menu.
    //
    // Our popup already stacks [reactions]/[bubble copy]/[menu] vertically,
    // so this is reproduced here without a ScrollView: the container keeps a
    // constant screen-sized height, every child is translated by
    // meeroStackOffset, and the offset is dragged within
    // [visibleH - totalH, 0]. ChatActivity enables the mode only for stacks
    // that do not fit - short stacks keep the exact v157/v158 behaviour.
    // ------------------------------------------------------------------

    private boolean meeroTallStack;
    /** Current scroll position: 0 = stack top revealed, minOffset = menu docked. */
    private float meeroStackOffset;
    /** visibleH - totalContentH (negative when the stack does not fit). */
    private float meeroMinOffset;
    /** Rubber-band distance past a bound while a drag is in progress. */
    private float meeroOverscroll;
    private boolean meeroStackInit;
    private boolean meeroDragging;
    private float meeroLastY, meeroLastX;
    private float meeroDownAccumY, meeroDownAccumX;
    private final int meeroTouchSlop = dp(10);
    private Runnable meeroOnOverscrollDismiss;
    private ValueAnimator meeroSpring;

    public void setMeeroTallStack(boolean enabled, Runnable onOverscrollDismiss) {
        meeroTallStack = enabled;
        meeroOnOverscrollDismiss = onOverscrollDismiss;
        meeroStackOffset = 0f;
        meeroOverscroll = 0f;
        meeroStackInit = false;
        meeroDragging = false;
    }

    public boolean isMeeroTallStack() {
        return meeroTallStack;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!meeroTallStack) {
            return;
        }
        // True stack height = bottom edge of the last laid-out child (children
        // are laid out past the container's own height when the stack is
        // taller than the visible window).
        float total = 0;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                total = child.getBottom();
                final android.view.ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp instanceof android.view.ViewGroup.MarginLayoutParams) {
                    total += ((android.view.ViewGroup.MarginLayoutParams) lp).bottomMargin;
                }
                break;
            }
        }
        meeroMinOffset = Math.min(0, getMeasuredHeight() - total);
        if (!meeroStackInit) {
            meeroStackInit = true;
            // Open bottom-anchored: the menu is fully visible at the bottom,
            // the message continues above the top edge - iOS defaultScrollY.
            meeroStackOffset = meeroMinOffset;
        }
        applyMeeroTranslations();
    }

    /** Applies the uniform stack offset to every child (bottomView adds its own). */
    private void applyMeeroTranslations() {
        if (!meeroTallStack) {
            return;
        }
        final float off = meeroDisplayedStackOffset();
        for (int i = 0, n = getChildCount(); i < n; i++) {
            final View child = getChildAt(i);
            if (child == bottomView) {
                continue;
            }
            // The menu carries its own swipe-back expand translation.
            child.setTranslationY(off + (child == popupWindowLayout ? expandSize : 0));
        }
        updateBottomViewPosition();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (meeroTallStack) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (meeroSpring != null) {
                        meeroSpring.cancel();
                        meeroSpring = null;
                    }
                    meeroLastY = ev.getY();
                    meeroLastX = ev.getX();
                    meeroDownAccumY = 0;
                    meeroDownAccumX = 0;
                    meeroDragging = false;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (!meeroDragging) {
                        meeroDownAccumY += ev.getY() - meeroLastY;
                        meeroDownAccumX += ev.getX() - meeroLastX;
                        meeroLastY = ev.getY();
                        meeroLastX = ev.getX();
                        // Vertical moves only: horizontal swipes belong to the
                        // menu's swipe-back gestures.
                        if (Math.abs(meeroDownAccumY) > meeroTouchSlop && Math.abs(meeroDownAccumY) > Math.abs(meeroDownAccumX)) {
                            meeroDragging = true;
                            return true;
                        }
                    }
                    break;
                }
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (meeroTallStack && (meeroDragging || ev.getActionMasked() == MotionEvent.ACTION_DOWN)) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    if (!meeroDragging) {
                        break;
                    }
                    final float dy = ev.getY() - meeroLastY;
                    meeroLastY = ev.getY();
                    meeroLastX = ev.getX();
                    if (meeroSpring != null) {
                        meeroSpring.cancel();
                        meeroSpring = null;
                    }
                    float next = meeroStackOffset + dy;
                    meeroOverscroll = 0;
                    if (next > 0) {
                        meeroOverscroll = next;
                        meeroStackOffset = 0;
                    } else if (next < meeroMinOffset) {
                        meeroOverscroll = next - meeroMinOffset;
                        meeroStackOffset = meeroMinOffset;
                    } else {
                        meeroStackOffset = next;
                    }
                    applyMeeroTranslations();
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    final boolean wasDragging = meeroDragging;
                    meeroDragging = false;
                    if (Math.abs(meeroOverscroll) > dp(64) && meeroOnOverscrollDismiss != null) {
                        // iOS: yanking the stack past an edge closes the menu.
                        meeroOverscroll = 0;
                        meeroOnOverscrollDismiss.run();
                    } else if (meeroOverscroll != 0) {
                        final float from = meeroOverscroll;
                        meeroSpring = ValueAnimator.ofFloat(from, 0f);
                        meeroSpring.setDuration(200);
                        meeroSpring.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                        meeroSpring.addUpdateListener(a -> {
                            meeroOverscroll = (float) a.getAnimatedValue();
                            applyMeeroTranslations();
                        });
                        meeroSpring.start();
                    }
                    return wasDragging || super.onTouchEvent(ev);
                }
            }
            return true;
        }
        return super.onTouchEvent(ev);
    }
}
