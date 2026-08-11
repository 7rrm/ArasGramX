/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.ViewOutlineProviderImpl;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

public class ActionBarPopupWindow extends PopupWindow {

    private static Method layoutInScreenMethod;
    private static final Field superListenerField;
    private static DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private AnimatorSet windowAnimatorSet;
    private boolean animationEnabled = true;
    private int dismissAnimationDuration = 150;
    private boolean isClosingAnimated;
    private int currentAccount = UserConfig.selectedAccount;
    private boolean pauseNotifications;
    private long outEmptyTime = -1;
    private boolean scaleOut;

    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            /* ignored */
        }
        superListenerField = f;
    }

    private static final ViewTreeObserver.OnScrollChangedListener NOP = () -> {
        /* do nothing */
    };

    private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
    private ViewTreeObserver mViewTreeObserver;
    private AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();

    public void setScaleOut(boolean b) {
        scaleOut = b;
    }

    public interface OnDispatchKeyEventListener {
        void onDispatchKeyEvent(KeyEvent keyEvent);
    }

    public static class ActionBarPopupWindowLayout extends FrameLayout {
        public final static int FLAG_USE_SWIPEBACK = 1;
        public final static int FLAG_SHOWN_FROM_BOTTOM = 2;
        public final static int FLAG_DONT_USE_SCROLLVIEW = 4;
        public boolean updateAnimation;
        public boolean clipChildren;
        public boolean swipeBackGravityRight;
        public boolean swipeBackGravityBottom;

        private OnDispatchKeyEventListener mOnDispatchKeyEventListener;
        private float backScaleX = 1;
        private float backScaleY = 1;
        private boolean startAnimationPending = false;
        private int backAlpha = 255;
        private int lastStartedChild = 0;
        public boolean shownFromBottom;
        private boolean animationEnabled = true;
        private ArrayList<AnimatorSet> itemAnimators;
        private HashMap<View, Integer> positions = new HashMap<>();
        private int gapStartY = -1000000;
        private int gapEndY = -1000000;
        private final Rect bgPaddings = new Rect();
        private onSizeChangedListener onSizeChangedListener;
        private float reactionsEnterProgress = 1f;

        private PopupSwipeBackLayout swipeBackLayout;
        @Nullable
        private ScrollView scrollView;
        protected LinearLayout linearLayout;

        private int backgroundColor = Color.WHITE;
        protected Drawable backgroundDrawable;

        private boolean fitItems;
        private final Theme.ResourcesProvider resourcesProvider;
        private View topView;
        protected ActionBarPopupWindow window;

        public int subtractBackgroundHeight;
        Rect rect;

        public Rect getPadding() {
            return bgPaddings;
        }

        public ActionBarPopupWindowLayout(Context context) {
            this(context, null);
        }

        public ActionBarPopupWindowLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, R.drawable.popup_fixed_alert2, resourcesProvider);
        }

        public ActionBarPopupWindowLayout(Context context, int resId, Theme.ResourcesProvider resourcesProvider) {
            this(context, resId, resourcesProvider, 0);
        }

        public ActionBarPopupWindowLayout(Context context, int resId, Theme.ResourcesProvider resourcesProvider, int flags) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            if (resId != 0) {
                backgroundDrawable = getResources().getDrawable(resId).mutate();
                setPadding(dp(8), dp(8), dp(8), dp(8));
            }
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
                setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));
            }


            setWillNotDraw(false);

            if ((flags & FLAG_SHOWN_FROM_BOTTOM) > 0) {
                shownFromBottom = true;
            }

            if ((flags & FLAG_USE_SWIPEBACK) > 0) {
                swipeBackLayout = new PopupSwipeBackLayout(context, resourcesProvider);
                addView(swipeBackLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }

            if ((flags & FLAG_DONT_USE_SCROLLVIEW) == 0) {
                try {
                    scrollView = new ScrollView(context);
                    scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                        @Override
                        public void onScrollChanged() {
                            invalidate();
                        }
                    });
                    scrollView.setVerticalScrollBarEnabled(false);
                    if (swipeBackLayout != null) {
                        swipeBackLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, shownFromBottom ? Gravity.BOTTOM : Gravity.TOP));
                    } else {
                        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            linearLayout = new LinearLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (fitItems) {
                        int maxWidth = 0;
                        int fixWidth = 0;
                        gapStartY = -1000000;
                        gapEndY = -1000000;
                        ArrayList<View> viewsToFix = null;
                        for (int a = 0, N = getChildCount(); a < N; a++) {
                            View view = getChildAt(a);
                            if (view.getVisibility() == GONE) {
                                continue;
                            }
                            Object tag = view.getTag(R.id.width_tag);
                            Object tag2 = view.getTag(R.id.object_tag);
                            Object fitToWidth = view.getTag(R.id.fit_width_tag);
                            if (tag != null) {
                                view.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                            }
                            measureChildWithMargins(view, widthMeasureSpec, 0, heightMeasureSpec, 0);
                            if (fitToWidth != null) {

                            } else if (!(tag instanceof Integer) && tag2 == null) {
                                maxWidth = Math.max(maxWidth, view.getMeasuredWidth());
                                continue;
                            } else if (tag instanceof Integer) {
                                fixWidth = Math.max((Integer) tag, view.getMeasuredWidth());
                                gapStartY = view.getMeasuredHeight();
                                gapEndY = gapStartY + dp(6);
                            }
                            if (viewsToFix == null) {
                                viewsToFix = new ArrayList<>();
                            }
                            viewsToFix.add(view);
                        }
                        if (viewsToFix != null) {
                            for (int a = 0, N = viewsToFix.size(); a < N; a++) {
                                viewsToFix.get(a).getLayoutParams().width = Math.max(maxWidth, fixWidth);
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    if (child instanceof GapView && backgroundDrawable != null) {
                        return false;
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }
            };
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            if (scrollView != null) {
                scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (swipeBackLayout != null) {
                swipeBackLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, shownFromBottom ? Gravity.BOTTOM : Gravity.TOP));
            } else {
                addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            }
        }

        @Nullable
        public PopupSwipeBackLayout getSwipeBack() {
            return swipeBackLayout;
        }

        public int addViewToSwipeBack(View v) {
            swipeBackLayout.addView(v, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, shownFromBottom ? Gravity.BOTTOM : Gravity.TOP));
            return swipeBackLayout.getChildCount() - 1;
        }

        public void setFitItems(boolean value) {
            fitItems = value;
        }

        public void setShownFromBottom(boolean value) {
            shownFromBottom = value;
        }

        public void setDispatchKeyEventListener(OnDispatchKeyEventListener listener) {
            mOnDispatchKeyEventListener = listener;
        }

        public int getBackgroundColor() {
            return backgroundColor;
        }

        public void setBackgroundColor(int color) {
            // MeeroX: while the iOS skin owns the card, the tint is the iOS
            // material fill - callers asking for the theme grey are ignored.
            if (meeroGate && backgroundDrawable instanceof MeeroIosCardDrawable) {
                color = meeroIosCardColor();
            }
            if (backgroundColor != color && backgroundDrawable != null) {
                backgroundDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor = color, PorterDuff.Mode.MULTIPLY));
            }
        }

        // ---------------- MeeroX iOS popup skin (v153) ----------------
        // Only action-bar menus opt in through meeroEnableIosMenuSkin();
        // reactions, sheets and dialogs sharing this layout keep stock looks.
        private boolean meeroSkinEligible;
        private boolean meeroGate;
        private boolean meeroLastOn;
        private long meeroLastSig = -1;
        private Drawable meeroStockDrawable;
        private View meeroIosSpacer;
        private Drawable meeroCardDrawable;
        private final Paint meeroSepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private HashMap<ActionBarMenuSubItem, int[]> meeroSavedSel;
        private java.util.function.BooleanSupplier meeroCfgOverride;
        // MeeroX v159: hairline settle-fade clock (-1 = not settled yet).
        private long meeroSepSettledAt = -1;

        private static boolean meeroSepFadeOn() {
            try {
                return tw.nekomimi.nekogram.NekoConfig.meeroSepFade.Bool();
            } catch (Throwable ignore) {
                return false;
            }
        }

        private static boolean meeroFlexWidthOn() {
            try {
                return tw.nekomimi.nekogram.NekoConfig.meeroFlexWidth.Bool();
            } catch (Throwable ignore) {
                return false;
            }
        }

        public void meeroEnableIosMenuSkin() {
            meeroSkinEligible = true;
            meeroLastSig = -1;
            requestLayout();
        }

        /**
         * MeeroX v200 (owner report: tapping a choice - e.g. «حذف المحادثة» -
         * closed the menu but never ran it): is the iOS card skin actually
         * owning this layout right now? ActionBarMenuItem's outside-tap
         * dismiss needs it - the skinned card's blur/shadow padding and the
         * 8dp destructive spacer spill past the layout's measured box, so
         * taps INSIDE the visible card counted as "outside" and merely
         * dismissed the popup.
         */
        public boolean isMeeroIosSkinOn() {
            // v201: read the live switch - v200 read the measure-time sync
            // flag, which is only refreshed in meeroPreMeasureSync and can be
            // stale at touch time. meeroCfg() is the same live read the draw
            // path uses.
            return meeroSkinEligible && meeroCfg();
        }

        // MeeroX v202 DIAGNOSTIC (temporary, v195 school): every skinned DOWN
        // is recorded into the MeeroMenuWatch ring (coords vs measured box,
        // child-consumed?). An UNCONSUMED DOWN is the owner's dead tap by
        // definition and self-captures the ring to the clipboard + vaulted
        // toast s(466). Stock path byte-untouched when the skin is off.
        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (isMeeroIosSkinOn() && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                final boolean consumed = super.dispatchTouchEvent(ev);
                tw.nekomimi.nekogram.MeeroMenuWatch.onDown(getContext(), ev.getX(), ev.getY(), getWidth(), getHeight(), consumed);
                return consumed;
            }
            return super.dispatchTouchEvent(ev);
        }

        // MeeroX: same opt-in as above, but the skin follows a caller-provided
        // switch (used by the message context-menu so it can have its own setting).
        public void meeroEnableIosMenuSkin(java.util.function.BooleanSupplier cfg) {
            meeroCfgOverride = cfg;
            meeroEnableIosMenuSkin();
        }

        private boolean meeroCfg() {
            if (meeroCfgOverride != null) {
                try {
                    return meeroCfgOverride.getAsBoolean();
                } catch (Throwable ignore) {
                    return false;
                }
            }
            try {
                return tw.nekomimi.nekogram.NekoConfig.meeroIosPopupMenu.Bool();
            } catch (Throwable ignore) {
                return false;
            }
        }

        private static boolean meeroRedish(int c) {
            final int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            return r > 180 && g < 130 && b < 130;
        }

        private boolean meeroIsDestructive(View v) {
            if (!(v instanceof ActionBarMenuSubItem)) {
                return false;
            }
            if (v.getTag(R.id.meero_ios_destructive) != null) {
                return true;
            }
            ActionBarMenuSubItem item = (ActionBarMenuSubItem) v;
            return item.textView != null && meeroRedish(item.textView.getCurrentTextColor());
        }

        private int meeroIosCardColor() {
            return Theme.isCurrentThemeDark() ? 0xFF2A2A2F : 0xFFF9F9FC;
        }

        private int meeroSepColor() {
            return Theme.isCurrentThemeDark() ? 0x21FFFFFF : 0x1F000000;
        }

        private Drawable meeroIosCard() {
            if (meeroCardDrawable == null) {
                meeroCardDrawable = new MeeroIosCardDrawable(getContext());
            }
            return meeroCardDrawable;
        }

        private long meeroSignature() {
            long sig = 0xCBF29CE484222325L;
            final int n = linearLayout.getChildCount();
            for (int i = 0; i < n; i++) {
                View v = linearLayout.getChildAt(i);
                sig = (sig * 0x100000001B3L) ^ (v.getVisibility() * 31L + (v == meeroIosSpacer ? 11L : 7L));
            }
            return sig ^ n;
        }

        private void meeroPreMeasureSync() {
            final boolean on = meeroCfg();
            meeroGate = on;
            final long sig = meeroSignature();
            if (sig != meeroLastSig || on != meeroLastOn) {
                meeroLastSig = sig;
                meeroLastOn = on;
                meeroStructuralSync(on);
            }
        }

        private void meeroStructuralSync(boolean on) {
            if (on && !(backgroundDrawable instanceof MeeroIosCardDrawable)) {
                if (meeroStockDrawable == null) {
                    meeroStockDrawable = backgroundDrawable;
                }
                backgroundDrawable = meeroIosCard();
                backgroundDrawable.getPadding(bgPaddings);
                backgroundDrawable.setColorFilter(new PorterDuffColorFilter(meeroIosCardColor(), PorterDuff.Mode.MULTIPLY));
            } else if (!on && backgroundDrawable instanceof MeeroIosCardDrawable && meeroStockDrawable != null) {
                backgroundDrawable = meeroStockDrawable;
                meeroStockDrawable = null;
                backgroundDrawable.getPadding(bgPaddings);
            }
            // MeeroX v159: flexible width lets short menus follow their
            // content with a smaller floor instead of the fixed 252dp.
            final int mw = on ? (meeroFlexWidthOn() ? dp(200) : dp(252)) : 0;
            if (getMinimumWidth() != mw) {
                setMinimumWidth(mw);
            }
            // The split red card exists only for the contiguous run of
            // destructive rows that closes the visible list; mid-menu red
            // rows just stay red inside the main card.
            int firstDestIdx = -1;
            if (on) {
                final int n = linearLayout.getChildCount();
                int lastVis = -1;
                for (int i = n - 1; i >= 0; i--) {
                    View v = linearLayout.getChildAt(i);
                    if (v == meeroIosSpacer) continue;
                    if (v.getVisibility() != View.VISIBLE) continue;
                    lastVis = i;
                    break;
                }
                if (lastVis >= 0 && meeroIsDestructive(linearLayout.getChildAt(lastVis))) {
                    int fd = lastVis;
                    for (int i = lastVis - 1; i >= 0; i--) {
                        View v = linearLayout.getChildAt(i);
                        if (v == meeroIosSpacer) continue;
                        if (v.getVisibility() != View.VISIBLE) break;
                        if (!meeroIsDestructive(v)) break;
                        fd = i;
                    }
                    boolean aboveNonDest = false;
                    for (int i = 0; i < fd; i++) {
                        View v = linearLayout.getChildAt(i);
                        if (v != meeroIosSpacer && v.getVisibility() == View.VISIBLE) {
                            aboveNonDest = true;
                            break;
                        }
                    }
                    if (aboveNonDest) {
                        firstDestIdx = fd;
                    }
                }
            }
            if (on && firstDestIdx > 0) {
                View anchor = linearLayout.getChildAt(firstDestIdx);
                if (meeroIosSpacer == null) {
                    meeroIosSpacer = new View(getContext());
                    meeroIosSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
                }
                // The spacer must sit directly before the anchor; inserting at
                // the anchor's own index guarantees that, and checking for
                // "already predecessor" keeps steady-state frames mutation-free.
                if (meeroIosSpacer.getParent() == null) {
                    linearLayout.addView(meeroIosSpacer, linearLayout.indexOfChild(anchor));
                } else if (linearLayout.indexOfChild(meeroIosSpacer) != linearLayout.indexOfChild(anchor) - 1) {
                    linearLayout.removeView(meeroIosSpacer);
                    linearLayout.addView(meeroIosSpacer, linearLayout.indexOfChild(anchor));
                }
            } else if (meeroIosSpacer != null && meeroIosSpacer.getParent() != null) {
                linearLayout.removeView(meeroIosSpacer);
            }
            if (meeroSavedSel == null) {
                meeroSavedSel = new HashMap<>();
            }
            final int spacerIdx = (meeroIosSpacer != null && meeroIosSpacer.getParent() != null) ? linearLayout.indexOfChild(meeroIosSpacer) : -1;
            final int m = linearLayout.getChildCount();
            ArrayList<ActionBarMenuSubItem> visibleItems = new ArrayList<>();
            for (int i = 0; i < m; i++) {
                View v = linearLayout.getChildAt(i);
                if (v instanceof ActionBarMenuSubItem && v.getVisibility() == View.VISIBLE) {
                    visibleItems.add((ActionBarMenuSubItem) v);
                }
            }
            for (int i = 0; i < visibleItems.size(); i++) {
                ActionBarMenuSubItem item = visibleItems.get(i);
                final int myIdx = linearLayout.indexOfChild(item);
                final int prevIdx = i > 0 ? linearLayout.indexOfChild(visibleItems.get(i - 1)) : -1;
                final int nextIdx = i + 1 < visibleItems.size() ? linearLayout.indexOfChild(visibleItems.get(i + 1)) : Integer.MAX_VALUE;
                final boolean top = i == 0 || (spacerIdx > prevIdx && spacerIdx < myIdx);
                final boolean bottom = i == visibleItems.size() - 1 || (spacerIdx > myIdx && spacerIdx < nextIdx);
                if (on) {
                    if (!meeroSavedSel.containsKey(item)) {
                        meeroSavedSel.put(item, new int[]{item.top ? 1 : 0, item.bottom ? 1 : 0, item.selectorRad});
                    }
                    item.updateSelectorBackground(top, bottom, 10);
                    item.setMeeroDestructiveLook(meeroIsDestructive(item));
                } else {
                    int[] s = meeroSavedSel.remove(item);
                    if (s != null) {
                        item.updateSelectorBackground(s[0] == 1, s[1] == 1, s[2]);
                    }
                    item.setMeeroDestructiveLook(false);
                }
            }
        }

        @Keep
        public void setBackAlpha(int value) {
            if (backAlpha != value) {
                invalidate();
            }
            backAlpha = value;
        }

        @Keep
        public int getBackAlpha() {
            return backAlpha;
        }

        @Keep
        public void setBackScaleX(float value) {
            if (backScaleX != value) {
                backScaleX = value;
                invalidate();
                if (onSizeChangedListener != null) {
                    onSizeChangedListener.onSizeChanged();
                }
            }
        }

        @Keep
        public void setBackScaleY(float value) {
            if (backScaleY != value) {
                backScaleY = value;
                if (animationEnabled && updateAnimation) {
                    int height = getMeasuredHeight() - dp(16);
                    if (shownFromBottom) {
                        for (int a = lastStartedChild; a >= 0; a--) {
                            View child = getItemAt(a);
                            if (child == null || child.getVisibility() != VISIBLE || child instanceof GapView) {
                                continue;
                            }
                            Integer position = positions.get(child);
                            if (position != null && height - (position * dp(48) + dp(32)) > value * height) {
                                break;
                            }
                            lastStartedChild = a - 1;
                            startChildAnimation(child);
                        }
                    } else {
                        int count = getItemsCount();
                        int h = 0;
                        for (int a = 0; a < count; a++) {
                            View child = getItemAt(a);
                            if (child.getVisibility() != VISIBLE) {
                                continue;
                            }
                            h += child.getMeasuredHeight();
                            if (a < lastStartedChild) {
                                continue;
                            }
                            Integer position = positions.get(child);
                            if (position != null && h - dp(24) > value * height) {
                                break;
                            }
                            lastStartedChild = a + 1;
                            startChildAnimation(child);
                        }
                    }
                }
                invalidate();
                if (onSizeChangedListener != null) {
                    onSizeChangedListener.onSizeChanged();
                }
            }
        }

        @Override
        public void setBackgroundDrawable(Drawable drawable) {
            backgroundColor = Color.WHITE;
            backgroundDrawable = drawable;
            if (backgroundDrawable != null) {
                backgroundDrawable.getPadding(bgPaddings);
            }
        }

        private void startChildAnimation(View child) {
            if (animationEnabled) {
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(child, View.ALPHA, 0f, child.isEnabled() ? 1f : 0.5f),
                        ObjectAnimator.ofFloat(child, View.TRANSLATION_Y, dp(shownFromBottom ? 6 : -6), 0));
                animatorSet.setDuration(180);
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        itemAnimators.remove(animatorSet);

                        if (child instanceof ActionBarMenuSubItem) {
                            ((ActionBarMenuSubItem) child).onItemShown();
                        }
                    }
                });
                animatorSet.setInterpolator(decelerateInterpolator);
                animatorSet.start();
                if (itemAnimators == null) {
                    itemAnimators = new ArrayList<>();
                }
                itemAnimators.add(animatorSet);
            }
        }

        public void setAnimationEnabled(boolean value) {
            animationEnabled = value;
        }

        @Override
        public void addView(View child) {
            linearLayout.addView(child);
        }

        public void addView(View child, LinearLayout.LayoutParams layoutParams) {
            linearLayout.addView(child, layoutParams);
        }

        public int getViewsCount() {
            return linearLayout.getChildCount();
        }

        public int precalculateHeight() {
            int MOST_SPEC = View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST);
            linearLayout.measure(MOST_SPEC, MOST_SPEC);
            return linearLayout.getMeasuredHeight();
        }

        public void removeInnerViews() {
            linearLayout.removeAllViews();
        }

        public float getBackScaleX() {
            return backScaleX;
        }

        public float getBackScaleY() {
            return backScaleY;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (mOnDispatchKeyEventListener != null) {
                mOnDispatchKeyEventListener.onDispatchKeyEvent(event);
            }
            return super.dispatchKeyEvent(event);
        }

        Path path;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (swipeBackGravityRight) {
                setTranslationX(getMeasuredWidth() * (1f - backScaleX));
                if (topView != null) {
                    topView.setTranslationX(getMeasuredWidth() * (1f - backScaleX));
                    topView.setAlpha(1f - swipeBackLayout.transitionProgress);
                    float h = topView.getMeasuredHeight() - dp(16);
                    float yOffset = -h * swipeBackLayout.transitionProgress;
                    topView.setTranslationY(yOffset);
                    setTranslationY(yOffset);
                }
            }
            if (swipeBackGravityBottom) {
                setTranslationY(getMeasuredHeight() * (1f - backScaleY));
            }
            // MeeroX: feed the existing two-card background path the Y of our
            // injected 8dp spacer, so the iOS destructive group draws as its
            // own rounded card. fitItems owns these fields for other menus.
            if (meeroSkinEligible) {
                if (meeroGate && meeroIosSpacer != null && meeroIosSpacer.getParent() == linearLayout && backAlpha == 255 && backScaleX == 1f && backScaleY == 1f) {
                    gapStartY = linearLayout.getTop() + meeroIosSpacer.getTop();
                    gapEndY = gapStartY + meeroIosSpacer.getMeasuredHeight();
                } else if (!fitItems) {
                    gapStartY = -1000000;
                    gapEndY = -1000000;
                }
            }
            if (backgroundDrawable != null) {
                int start = gapStartY - (scrollView == null ? 0 : scrollView.getScrollY());
                int end = gapEndY - (scrollView == null ? 0 : scrollView.getScrollY());
                boolean hasGap = false;
                for (int i = 0; i < linearLayout.getChildCount(); i++) {
                    if (linearLayout.getChildAt(i) instanceof GapView && linearLayout.getChildAt(i).getVisibility() == View.VISIBLE) {
                        hasGap = true;
                        break;
                    }
                }
                for (int a = 0; a < 2; a++) {
                    if (a == 1 && start < -dp(16)) {
                        break;
                    }
                    int saveCount = canvas.getSaveCount();
                    boolean applyAlpha = true;
                    if (hasGap && backAlpha != 255) {
                        canvas.saveLayerAlpha(0, bgPaddings.top, getMeasuredWidth(), getMeasuredHeight(), backAlpha, Canvas.ALL_SAVE_FLAG);
                        applyAlpha = false;
                    }  else if (gapStartY != -1000000) {
                        canvas.save();
                        canvas.clipRect(0, bgPaddings.top, getMeasuredWidth(), getMeasuredHeight());
                    }
                    backgroundDrawable.setAlpha(applyAlpha ? backAlpha : 255);
                    if (shownFromBottom) {
                        final int height = getMeasuredHeight();
                        AndroidUtilities.rectTmp2.set(0, (int) (height * (1.0f - backScaleY)), (int) (getMeasuredWidth() * backScaleX), height);
                    } else {
                        if (start > -dp(16)) {
                            int h = (int) (getMeasuredHeight() * backScaleY);
                            if (a == 0) {
                                if (swipeBackLayout != null && swipeBackLayout.stickToRight) {
                                    AndroidUtilities.rectTmp2.set(getMeasuredWidth() - (int) (getMeasuredWidth() * backScaleX), (scrollView == null ? 0 : -scrollView.getScrollY()) + (gapStartY != -1000000 ? dp(1) : 0), getMeasuredWidth(), (gapStartY != -1000000 ? Math.min(h, start + dp(16)) : h) - subtractBackgroundHeight);
                                } else {
                                    AndroidUtilities.rectTmp2.set(0, (scrollView == null ? 0 : -scrollView.getScrollY()) + (gapStartY != -1000000 ? dp(1) : 0), (int) (getMeasuredWidth() * backScaleX), (gapStartY != -1000000 ? Math.min(h, start + dp(16)) : h) - subtractBackgroundHeight);
                                }
                            } else {
                                if (h < end) {
                                    if (gapStartY != -1000000) {
                                        canvas.restore();
                                    }
                                    continue;
                                }
                                if (swipeBackLayout != null && swipeBackLayout.stickToRight) {
                                    AndroidUtilities.rectTmp2.set(getMeasuredWidth() - (int) (getMeasuredWidth() * backScaleX), end, getMeasuredWidth(), h - subtractBackgroundHeight);
                                } else {
                                    AndroidUtilities.rectTmp2.set(0, end, (int) (getMeasuredWidth() * backScaleX), h - subtractBackgroundHeight);
                                }
                            }
                        } else {
                            if (swipeBackLayout != null && swipeBackLayout.stickToRight) {
                                AndroidUtilities.rectTmp2.set(getMeasuredWidth() - (int) (getMeasuredWidth() * backScaleX), (gapStartY < 0 ? 0 : -dp(16)), getMeasuredWidth(), (int) (getMeasuredHeight() * backScaleY) - subtractBackgroundHeight);
                            } else {
                                AndroidUtilities.rectTmp2.set(0, (gapStartY < 0 ? 0 : -dp(16)), (int) (getMeasuredWidth() * backScaleX), (int) (getMeasuredHeight() * backScaleY) - subtractBackgroundHeight);
                            }
                        }
                    }
                    if (reactionsEnterProgress != 1f) {
                        if (rect == null) {
                            rect = new Rect();
                        }
                        rect.set(AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.top, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.top);
                        AndroidUtilities.lerp(rect, AndroidUtilities.rectTmp2, reactionsEnterProgress, AndroidUtilities.rectTmp2);
                    }
                    backgroundDrawable.setBounds(AndroidUtilities.rectTmp2);
                    backgroundDrawable.draw(canvas);
                    if (clipChildren) {
                        AndroidUtilities.rectTmp2.left += bgPaddings.left;
                        AndroidUtilities.rectTmp2.top += bgPaddings.top;
                        AndroidUtilities.rectTmp2.right -= bgPaddings.right;
                        AndroidUtilities.rectTmp2.bottom -= bgPaddings.bottom;
                        canvas.clipRect(AndroidUtilities.rectTmp2);
                    }
                    if (hasGap) {
                        canvas.save();
                        AndroidUtilities.rectTmp.set(backgroundDrawable.getBounds());
                        AndroidUtilities.rectTmp.inset(dp(8), dp(8));
                        if (path == null) {
                            path = new Path();
                        } else {
                            path.rewind();
                        }
                        path.addRoundRect(AndroidUtilities.rectTmp, dp(12), dp(12), Path.Direction.CW);
                        canvas.clipPath(path);
                        for (int i = 0; i < linearLayout.getChildCount(); i++) {
                            if (linearLayout.getChildAt(i) instanceof GapView && linearLayout.getChildAt(i).getVisibility() == View.VISIBLE) {
                                canvas.save();
                                float x = 0, y = 0;
                                GapView child = (GapView) linearLayout.getChildAt(i);
                                View view = child;
                                while (view != this) {
                                    x += view.getX();
                                    y += view.getY();
                                    view = (View) view.getParent();
                                    if (view == null) {
                                        break;
                                    }
                                }
                                canvas.translate(x, y * (scrollView == null ? 1f : scrollView.getScaleY()) - (scrollView == null ? 0 : scrollView.getScrollY()));
                                child.draw(canvas);
                                canvas.restore();
                            }
                        }
                        canvas.restore();
                    }
                    canvas.restoreToCount(saveCount);
                }
            }
            // MeeroX: iOS hairline separators - drawn once the popup is fully
            // settled so the entrance scale never shows unscaled strokes.
            if (meeroSkinEligible && meeroGate && backAlpha == 255 && backScaleX == 1f && backScaleY == 1f && reactionsEnterProgress == 1f) {
                View prevVisible = null;
                final int scrollY = scrollView == null ? 0 : scrollView.getScrollY();
                final int contentTop = linearLayout.getTop();
                final boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                final int leadInset = bgPaddings.left + dp(16);
                // MeeroX v159: instead of snapping in at settle, the hairlines
                // fade in over 120ms (iOS settle feel). meeroSepFade OFF =
                // the previous instant appearance, same timing as v153-v158.
                int meeroSepAlphaNow = 255;
                if (meeroSepFadeOn()) {
                    final long now = System.currentTimeMillis();
                    if (meeroSepSettledAt < 0) {
                        meeroSepSettledAt = now;
                    }
                    final float ft = Math.min(1f, (now - meeroSepSettledAt) / 120f);
                    meeroSepAlphaNow = (int) (255 * ft);
                    if (ft < 1f) {
                        postInvalidateOnAnimation();
                    }
                } else {
                    meeroSepSettledAt = -1;
                }
                if (meeroSepAlphaNow > 0) {
                final int meeroSepBase = meeroSepColor();
                meeroSepPaint.setColor(meeroSepBase);
                // Compose the color's own alpha with the fade progress.
                meeroSepPaint.setAlpha((meeroSepBase >>> 24) * meeroSepAlphaNow / 255);
                final int n = linearLayout.getChildCount();
                for (int i = 0; i < n; i++) {
                    View v = linearLayout.getChildAt(i);
                    if (v == meeroIosSpacer) {
                        prevVisible = null;
                        continue;
                    }
                    if (v.getVisibility() != View.VISIBLE) {
                        continue;
                    }
                    if (prevVisible instanceof ActionBarMenuSubItem && v instanceof ActionBarMenuSubItem) {
                        final float y = contentTop + v.getTop() - scrollY;
                        if (rtl) {
                            canvas.drawRect(bgPaddings.left, y, getMeasuredWidth() - leadInset, y + 1, meeroSepPaint);
                        } else {
                            canvas.drawRect(leadInset, y, getMeasuredWidth() - bgPaddings.right, y + 1, meeroSepPaint);
                        }
                    }
                    prevVisible = v;
                }
                }
            } else {
                meeroSepSettledAt = -1;
            }
            if (reactionsEnterProgress != 1f) {
                canvas.saveLayerAlpha((float) AndroidUtilities.rectTmp2.left, (float) AndroidUtilities.rectTmp2.top, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.bottom, (int) (255 * reactionsEnterProgress), Canvas.ALL_SAVE_FLAG);
                float scale = 0.5f + reactionsEnterProgress * 0.5f;
                canvas.scale(scale, scale, AndroidUtilities.rectTmp2.right, AndroidUtilities.rectTmp2.top);
                super.dispatchDraw(canvas);
                canvas.restore();
            } else {
                super.dispatchDraw(canvas);
            }
        }

        public Drawable getBackgroundDrawable() {
            return backgroundDrawable;
        }

        public int getItemsCount() {
            return linearLayout.getChildCount();
        }

        public View getItemAt(int index) {
            return linearLayout.getChildAt(index);
        }

        public void scrollToTop() {
            if (scrollView != null) {
                scrollView.scrollTo(0, 0);
            }
        }

        public void setupRadialSelectors(int color) {
            int count = linearLayout.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                child.setBackground(Theme.createRadSelectorDrawable(color, a == 0 ? 6 : 0, a == count - 1 ? 6 : 0));
            }
        }

        public void updateRadialSelectors() {
            int count = linearLayout.getChildCount();
            View firstVisible = null;
            View lastVisible = null;
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                if (firstVisible == null) {
                    firstVisible = child;
                }
                lastVisible = child;
            }

            boolean prevGap = false;
            for (int a = 0; a < count; a++) {
                View child = linearLayout.getChildAt(a);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                Object tag = child.getTag(R.id.object_tag);
                if (child instanceof ActionBarMenuSubItem) {
                    ((ActionBarMenuSubItem) child).updateSelectorBackground(child == firstVisible || prevGap, child == lastVisible);
                }
                if (tag != null) {
                    prevGap = true;
                } else {
                    prevGap = false;
                }
            }
        }

        protected int getThemedColor(int key) {
            return Theme.getColor(key, resourcesProvider);
        }

        public void setOnSizeChangedListener(ActionBarPopupWindow.onSizeChangedListener onSizeChangedListener) {
            this.onSizeChangedListener = onSizeChangedListener;
        }

        public int getVisibleHeight() {
            return (int) (getMeasuredHeight() * backScaleY);
        }

        public void setTopView(View topView) {
            this.topView = topView;
        }

        public void setSwipeBackForegroundColor(int color) {
            getSwipeBack().setForegroundColor(color);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (meeroSkinEligible) {
                meeroPreMeasureSync();
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (swipeBackLayout != null) {
                swipeBackLayout.invalidateTransforms(!startAnimationPending);
            }
        }

        public void setParentWindow(ActionBarPopupWindow popupWindow) {
            window = popupWindow;
        }

        public void setReactionsTransitionProgress(float transitionEnterProgress) {
            this.reactionsEnterProgress = transitionEnterProgress;
            invalidate();
        }
    }

    public ActionBarPopupWindow() {
        super();
        init();
    }

    public ActionBarPopupWindow(Context context) {
        super(context);
        init();
    }

    public ActionBarPopupWindow(int width, int height) {
        super(width, height);
        init();
    }

    public ActionBarPopupWindow(View contentView) {
        super(contentView);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height, boolean focusable) {
        super(contentView, width, height, focusable);
        init();
    }

    public ActionBarPopupWindow(View contentView, int width, int height) {
        super(contentView, width, height);
        init();
    }

    public void setAnimationEnabled(boolean value) {
        animationEnabled = value;
    }

    @SuppressWarnings("PrivateAPI")
    public void setLayoutInScreen(boolean value) {
        try {
            if (layoutInScreenMethod == null) {
                layoutInScreenMethod = PopupWindow.class.getDeclaredMethod("setLayoutInScreenEnabled", boolean.class);
                layoutInScreenMethod.setAccessible(true);
            }
            layoutInScreenMethod.invoke(this, true);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void init() {
        View contentView = getContentView();
        if (contentView instanceof ActionBarPopupWindowLayout && ((ActionBarPopupWindowLayout) contentView).getSwipeBack() != null) {
            setTouchInterceptor((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    Drawable backgroundDrawable = ((ActionBarPopupWindowLayout) contentView).getBackgroundDrawable();
                    AndroidUtilities.rectTmp.set(backgroundDrawable.getBounds());
                    AndroidUtilities.rectTmp.offset(contentView.getX(), contentView.getY());
                    if (!AndroidUtilities.rectTmp.contains(e.getX(), e.getY())) {
                        dismiss();
                        return true;
                    }
                }
                return false;
            });
        }
        if (superListenerField != null) {
            try {
                mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                superListenerField.set(this, NOP);
            } catch (Exception e) {
                mSuperScrollListener = null;
            }
        }
    }

    public void setDismissAnimationDuration(int value) {
        dismissAnimationDuration = value;
    }

    private void unregisterListener() {
        if (mSuperScrollListener != null && mViewTreeObserver != null) {
            if (mViewTreeObserver.isAlive()) {
                mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
            }
            mViewTreeObserver = null;
        }
    }

    private void registerListener(View anchor) {
        if (mSuperScrollListener != null) {
            ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
            if (vto != mViewTreeObserver) {
                if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                if ((mViewTreeObserver = vto) != null) {
                    vto.addOnScrollChangedListener(mSuperScrollListener);
                }
            }
        }
    }

    public void dimBehind() {
        dimBehind(0.2f);
    }

    public void dimBehind(float amount) {
        View container = getContentView().getRootView();
        Context context = getContentView().getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
        p.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        p.dimAmount = amount;
        wm.updateViewLayout(container, p);
    }

    public void setFocusableFlag(boolean enable) {
        View container = getContentView().getRootView();
        Context context = getContentView().getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();

        if (p != null) {
            if (enable) {
                p.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            } else {
                p.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            }
            wm.updateViewLayout(container, p);
        }
    }

    private void dismissDim() {
        View container = getContentView().getRootView();
        Context context = getContentView().getContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        if (container.getLayoutParams() == null || !(container.getLayoutParams() instanceof WindowManager.LayoutParams)) {
            return;
        }
        WindowManager.LayoutParams p = (WindowManager.LayoutParams) container.getLayoutParams();
        try {
            if ((p.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                p.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                p.dimAmount = 0.0f;
                wm.updateViewLayout(container, p);
            }
        } catch (Exception e) {

        }
    }

    @Override
    public void showAsDropDown(View anchor, int xoff, int yoff) {
        try {
            super.showAsDropDown(anchor, xoff, yoff);
            registerListener(anchor);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /**
     * MeeroX: where the menu should appear to grow from.
     *
     * Android pins the pivot to the menu's top-right corner, so a menu always
     * unfolds from the same place no matter where it was summoned. iOS grows
     * its context menus out of the point that was actually touched, which is
     * what makes the menu feel attached to the thing it belongs to.
     *
     * Set in the popup's own coordinates before it is shown; -1 leaves the
     * stock corner behaviour alone.
     */
    private static float meeroPivotX = -1f;
    private static float meeroPivotY = -1f;

    public static void setMeeroPivot(float x, float y) {
        meeroPivotX = x;
        meeroPivotY = y;
    }

    public static void clearMeeroPivot() {
        meeroPivotX = -1f;
        meeroPivotY = -1f;
    }

    private static boolean meeroIosMenu() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosMenuAnim.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    // MeeroX v159: unify popup open/close on ~180ms with an ease-out curve
    // (iOS pacing), instead of the inherited per-item cascade timing.
    private static boolean meeroSwiftMenus() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroSwiftMenus.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static void meeroApplySwiftTiming(AnimatorSet set, int stockDuration) {
        if (meeroSwiftMenus()) {
            set.setDuration(180);
            set.setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT);
        } else {
            set.setDuration(stockDuration);
        }
    }

    public static AnimatorSet startAnimation(ActionBarPopupWindowLayout content) {
        content.startAnimationPending = true;
        content.setTranslationY(0);
        content.setAlpha(1.0f);
        if (meeroIosMenu() && meeroPivotX >= 0 && meeroPivotY >= 0) {
            // Clamped so a touch outside the menu's own box still yields a
            // pivot on its edge rather than off it, which would swing the
            // menu in from the side instead of scaling it up.
            content.setPivotX(Math.max(0, Math.min(content.getMeasuredWidth(), meeroPivotX)));
            content.setPivotY(Math.max(0, Math.min(content.getMeasuredHeight(), meeroPivotY)));
        } else {
            content.setPivotX(content.getMeasuredWidth());
            content.setPivotY(0);
        }
        final int count = content.getItemsCount();
        content.positions.clear();
        int visibleCount = 0;
        for (int a = 0; a < count; a++) {
            View child = content.getItemAt(a);
            if (child instanceof GapView) {
                continue;
            }
            child.setAlpha(0.0f);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            content.positions.put(child, visibleCount);
            visibleCount++;
        }
        if (content.shownFromBottom) {
            content.lastStartedChild = count - 1;
        } else {
            content.lastStartedChild = 0;
        }
        float finalScaleY = 1f;
        if (content.getSwipeBack() != null) {
            content.getSwipeBack().invalidateTransforms();
            finalScaleY = content.backScaleY;
        }
        AnimatorSet windowAnimatorSet = new AnimatorSet();
        ValueAnimator childtranslations = ValueAnimator.ofFloat(0, 1);
        childtranslations.addUpdateListener(anm -> {
            final int count2 = content.getItemsCount();
            final float t = (float) anm.getAnimatedValue();
            for (int a = 0; a < count2; a++) {
                View child = content.getItemAt(a);
                if (child instanceof GapView) {
                    continue;
                }
                float at = AndroidUtilities.cascade(t, content.shownFromBottom ? count2 - 1 - a : a, count2, 4);
                child.setTranslationY((1f - at) * dp(-6));
                child.setAlpha(at * (child.isEnabled() ? 1f : 0.5f));
            }
        });
        content.updateAnimation = false;
        content.clipChildren = true;
        windowAnimatorSet.playTogether(
                ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, finalScaleY),
                ObjectAnimator.ofInt(content, "backAlpha", 0, 255),
                childtranslations
        );
        meeroApplySwiftTiming(windowAnimatorSet, 150 + 16 * visibleCount);
        windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                content.startAnimationPending = false;
                int count = content.getItemsCount();
                for (int a = 0; a < count; a++) {
                    View child = content.getItemAt(a);
                    if (child instanceof GapView) {
                        continue;
                    }
                    child.setTranslationY(0);
                    child.setAlpha(child.isEnabled() ? 1f : 0.5f);
                }
            }
        });
        windowAnimatorSet.start();
        return windowAnimatorSet;
    }

    public void startAnimation() {
        startAnimation(false);
    }

    public void startAnimation(boolean fromStickersAlert) {
        if (animationEnabled) {
            if (windowAnimatorSet != null) {
                return;
            }

            ViewGroup viewGroup = (ViewGroup) getContentView();
            ActionBarPopupWindowLayout content = null;
            if (viewGroup instanceof ActionBarPopupWindowLayout) {
                content = (ActionBarPopupWindowLayout) viewGroup;
                content.startAnimationPending = true;
            } else {
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                        content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                        content.startAnimationPending = true;
                    }
                }
            }
            content.setTranslationY(0);
            content.setAlpha(1.0f);
            content.setPivotX(content.getMeasuredWidth());
            content.setPivotY(0);
            int count = content.getItemsCount();
            if (count > 100) {
                int height = AndroidUtilities.displayMetrics.heightPixels;
                int item = content.getItemAt(0).getMeasuredHeight();
                if (item > 0) {
                    int maxItems = height / item;
                    if (count > maxItems) {
                        count = maxItems;
                    }
                }
            }
            content.positions.clear();
            int visibleCount = 0;
            for (int a = 0; a < count; a++) {
                View child = content.getItemAt(a);
                child.setAlpha(0.0f);
                if (child.getVisibility() != View.VISIBLE) {
                    continue;
                }
                content.positions.put(child, visibleCount);
                visibleCount++;
            }
            if (content.shownFromBottom) {
                content.lastStartedChild = count - 1;
            } else {
                content.lastStartedChild = 0;
            }
            float finalScaleY = 1f;
            if (content.getSwipeBack() != null) {
                content.getSwipeBack().invalidateTransforms();
                if (!fromStickersAlert) {
                    finalScaleY = content.backScaleY;
                }
            }
            windowAnimatorSet = new AnimatorSet();
            windowAnimatorSet.playTogether(
                    ObjectAnimator.ofFloat(content, "backScaleY", 0.0f, finalScaleY),
                    ObjectAnimator.ofInt(content, "backAlpha", 0, 255));
            meeroApplySwiftTiming(windowAnimatorSet, 150 + 16 * visibleCount);
            windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowAnimatorSet = null;
                    ViewGroup viewGroup = (ViewGroup) getContentView();
                    ActionBarPopupWindowLayout content = null;
                    if (viewGroup instanceof ActionBarPopupWindowLayout) {
                        content = (ActionBarPopupWindowLayout) viewGroup;
                        content.startAnimationPending = false;
                    } else {
                        for (int i = 0; i < viewGroup.getChildCount(); i++) {
                            if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                                content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                                content.startAnimationPending = false;
                            }
                        }
                    }
                    int count = content.getItemsCount();
                    for (int a = 0; a < count; a++) {
                        View child = content.getItemAt(a);
                        if (child instanceof GapView) {
                            continue;
                        }
                        child.setAlpha(child.isEnabled() ? 1f : 0.5f);
                    }
                }
            });
            windowAnimatorSet.start();
        }
    }

    @Override
    public void update(View anchor, int xoff, int yoff, int width, int height) {
        super.update(anchor, xoff, yoff, width, height);
        registerListener(anchor);
    }

    @Override
    public void update(View anchor, int width, int height) {
        super.update(anchor, width, height);
        registerListener(anchor);
    }

    @Override
    public void showAtLocation(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        unregisterListener();
    }

    @Override
    public void dismiss() {
        dismiss(true);
    }

    public void setPauseNotifications(boolean value) {
        pauseNotifications = value;
    }

    public void dismiss(boolean animated) {
        setFocusable(false);
        dismissDim();
        if (windowAnimatorSet != null) {
            if (animated && isClosingAnimated) {
                return;
            }
            windowAnimatorSet.cancel();
            windowAnimatorSet = null;
        }
        isClosingAnimated = false;
        if (animationEnabled && animated) {
            isClosingAnimated = true;
            ViewGroup viewGroup = (ViewGroup) getContentView();
            ActionBarPopupWindowLayout content = null;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                if (viewGroup.getChildAt(i) instanceof ActionBarPopupWindowLayout) {
                    content = (ActionBarPopupWindowLayout) viewGroup.getChildAt(i);
                }
            }
            if (content != null) {
                if (content.itemAnimators != null && !content.itemAnimators.isEmpty()) {
                    for (int a = 0, N = content.itemAnimators.size(); a < N; a++) {
                        AnimatorSet animatorSet = content.itemAnimators.get(a);
                        animatorSet.removeAllListeners();
                        animatorSet.cancel();
                    }
                    content.itemAnimators.clear();
                }
            }
            windowAnimatorSet = new AnimatorSet();
            if (outEmptyTime > 0) {
                windowAnimatorSet.playTogether(ValueAnimator.ofFloat(0, 1f));
                windowAnimatorSet.setDuration(outEmptyTime);
            } else if (scaleOut) {
                windowAnimatorSet.playTogether(
                        ObjectAnimator.ofFloat(viewGroup, View.SCALE_Y, 0.8f),
                        ObjectAnimator.ofFloat(viewGroup, View.SCALE_X, 0.8f),
                        ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f));
                windowAnimatorSet.setDuration(dismissAnimationDuration);
            } else {
                windowAnimatorSet.playTogether(
                        ObjectAnimator.ofFloat(viewGroup, View.TRANSLATION_Y, dp((content != null && content.shownFromBottom) ? 5 : -5)),
                        ObjectAnimator.ofFloat(viewGroup, View.ALPHA, 0.0f));
                windowAnimatorSet.setDuration(dismissAnimationDuration);
            }

            windowAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    windowAnimatorSet = null;
                    isClosingAnimated = false;
                    setFocusable(false);
                    try {
                        ActionBarPopupWindow.super.dismiss();
                    } catch (Exception ignore) {

                    }
                    unregisterListener();
                    if (pauseNotifications) {
                        notificationsLocker.unlock();
                    }
                }
            });
            if (pauseNotifications) {
                notificationsLocker.lock();
            }
            windowAnimatorSet.start();
        } else {
            try {
                super.dismiss();
            } catch (Exception ignore) {

            }
            unregisterListener();
        }
    }

    public void setEmptyOutAnimation(long time) {
        outEmptyTime = time;
    }

    public interface onSizeChangedListener {
        void onSizeChanged();
    }

    /**
     * MeeroX: iOS menu card drawn from a pre-rendered nine-slice bitmap, so
     * the 14pt corner radius and the soft drop shadow survive the same
     * bounds-driven two-card (gap) drawing path the stock 9-patch uses. The
     * bitmap is neutral white; the regular MULTIPLY tint path recolours it,
     * exactly like the stock patch.
     */
    public static class MeeroIosCardDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect paddingRect = new Rect();
        private final Rect src = new Rect();
        private final Rect dst = new Rect();
        private Bitmap bitmap;
        private int cL, cT, cR, cB;

        public MeeroIosCardDrawable(Context context) {
            build();
        }

        private void build() {
            final int padL = dp(8), padT = dp(6), padR = dp(8), padB = dp(12);
            final int rad = dp(14);
            final int coreX = Math.max(2, dp(8));
            final int coreY = Math.max(2, dp(8));
            final int w = padL + rad + coreX + rad + padR;
            final int h = padT + rad + coreY + rad + padB;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFFFFFF);
            // Software canvas: the shadow layer blurs for real here, unlike a
            // hardware-accelerated dispatchDraw where it would be dropped.
            p.setShadowLayer(dp(4), 0, dp(2), 0x59000000);
            canvas.drawRoundRect(new RectF(padL, padT, w - padR, h - padB), rad, rad, p);
            bitmap = bmp;
            cL = padL + rad;
            cT = padT + rad;
            cR = padR + rad;
            cB = padB + rad;
            paddingRect.set(padL, padT, padR, padB);
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            final Rect b = getBounds();
            final int x0 = b.left, x3 = b.right;
            final int y0 = b.top, y3 = b.bottom;
            final int x1 = x0 + cL, x2 = x3 - cR;
            final int y1 = y0 + cT, y2 = y3 - cB;
            final int sw = bitmap.getWidth(), sh = bitmap.getHeight();
            slice(canvas, 0, 0, cL, cT, x0, y0, x1, y1);
            slice(canvas, cL, 0, sw - cR, cT, x1, y0, x2, y1);
            slice(canvas, sw - cR, 0, sw, cT, x2, y0, x3, y1);
            slice(canvas, 0, cT, cL, sh - cB, x0, y1, x1, y2);
            slice(canvas, cL, cT, sw - cR, sh - cB, x1, y1, x2, y2);
            slice(canvas, sw - cR, cT, sw, sh - cB, x2, y1, x3, y2);
            slice(canvas, 0, sh - cB, cL, sh, x0, y2, x1, y3);
            slice(canvas, cL, sh - cB, sw - cR, sh, x1, y2, x2, y3);
            slice(canvas, sw - cR, sh - cB, sw, sh, x2, y2, x3, y3);
        }

        private void slice(Canvas canvas, int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1) {
            if (sx1 <= sx0 || sy1 <= sy0 || dx1 <= dx0 || dy1 <= dy0) {
                return;
            }
            src.set(sx0, sy0, sx1, sy1);
            dst.set(dx0, dy0, dx1, dy1);
            canvas.drawBitmap(bitmap, src, dst, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public boolean getPadding(Rect padding) {
            padding.set(paddingRect);
            return true;
        }

        @Override
        public int getIntrinsicWidth() {
            return bitmap != null ? bitmap.getWidth() : -1;
        }

        @Override
        public int getIntrinsicHeight() {
            return bitmap != null ? bitmap.getHeight() : -1;
        }
    }

    public static class GapView extends FrameLayout {

        Drawable shadowDrawable;

        public GapView(Context context, Theme.ResourcesProvider resourcesProvider) {
            this(context, resourcesProvider, Theme.key_actionBarDefaultSubmenuSeparator);
        }

        public GapView(Context context, int color, int shadowColor) {
            super(context);
            this.shadowDrawable = Theme.getThemedDrawable(getContext(), R.drawable.greydivider, shadowColor);
            setBackgroundColor(color);
        }

        public GapView(Context context, Theme.ResourcesProvider resourcesProvider, int colorKey) {
            this(context, Theme.getColor(colorKey, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundGrayShadow, resourcesProvider));
        }

        public void setColor(int color) {
            setBackgroundColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (shadowDrawable != null) {
                shadowDrawable.setBounds(0, 0, getWidth(), getHeight());
                shadowDrawable.draw(canvas);
            }
        }
    }
}
