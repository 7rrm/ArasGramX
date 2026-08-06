package org.telegram.ui.ActionBar;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieImageView;

public class ActionBarMenuSubItem extends FrameLayout {

    /**
     * MeeroX: whether context rows follow iOS's metrics.
     *
     * Shares the menu switch, since the row and the panel around it have to
     * agree - a 14dp iOS panel wrapped around 16sp Material rows looks worse
     * than either done consistently.
     */
    private static boolean meeroIosRows() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosMenuAnim.Bool() || tw.nekomimi.nekogram.NekoConfig.meeroIosPopupMenu.Bool() || tw.nekomimi.nekogram.NekoConfig.meeroIosMsgMenu.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    /**
     * MeeroX: full iOS popup look (v153) - gates only what the popup-menu
     * switch owns (44dp rows, trailing icon, end-side text gap). The shared
     * row cosmetics above continue to honour the older menu-animation switch.
     * The message context-menu switch (v155) shares the same row metrics so
     * both menus look identical, like iOS.
     */
    private static boolean meeroIosPopup() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosPopupMenu.Bool() || tw.nekomimi.nekogram.NekoConfig.meeroIosMsgMenu.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public AnimatedEmojiSpan.TextViewEmojis textView;
    public TextView subtextView;
    public RLottieImageView imageView;
    public boolean checkViewLeft;
    public CheckBox2 checkView;
    private ImageView rightIcon;
    private BackupImageView backupImageView;

    private int textColor;
    private int iconColor;
    private PorterDuff.Mode iconColorMode;
    private int selectorColor;

    int selectorRad = 12;
    boolean top;
    boolean bottom;

    private int itemHeight = 48;
    protected final Theme.ResourcesProvider resourcesProvider;
    public Runnable openSwipeBackLayout;

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom) {
        this(context, false, top, bottom);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom) {
        this(context, needCheck ? 1 : 0, top, bottom, null);
    }

    public ActionBarMenuSubItem(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        this(context, 0, top, bottom, resourcesProvider);
    }

    public ActionBarMenuSubItem(Context context, boolean needCheck, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        this(context, needCheck ? 1 : 0, top, bottom, resourcesProvider);
    }

    public ActionBarMenuSubItem(Context context, int needCheck, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        this.top = top;
        this.bottom = bottom;

        // MeeroX: iOS context rows are 44pt tall.
        if (meeroIosPopup()) {
            itemHeight = 44;
        }

        textColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItem);
        iconColor = getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon);
        iconColorMode = PorterDuff.Mode.MULTIPLY;
        selectorColor = getThemedColor(Theme.key_dialogButtonSelector);

        updateBackground();
        // MeeroX: iOS context rows sit on a 16pt side inset, not 18.
        final int meeroPad = meeroIosRows() ? 16 : 18;
        setPadding(dp(meeroPad), 0, dp(meeroPad), 0);

        imageView = new RLottieImageView(context);
        // MeeroX: CENTER draws the drawable at its own size and clips whatever
        // falls outside the view, so pinning the frame to iOS's 16dp simply
        // cut 24dp glyphs off at the edges. FIT_CENTER scales them down to fit
        // instead, which is what "a 16pt icon" has to mean here.
        imageView.setScaleType(meeroIosRows() ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
        // Upstream v12.9.2 moved the default tint from MULTIPLY to SRC_IN:
        // SRC_IN repaints the glyph with the theme colour through its alpha
        // channel, so even hardcoded black-filled vectors pick the colour up.
        // That subsumes MeeroX's earlier white-fill workaround and keeps any
        // future black-filled icon compatible without asset edits.
        imageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        // ContextActionsContainerNode draws its glyphs at a fixed 16x16;
        // Android lets the drawable size itself, which on the solar icon set
        // comes out around 24 and leaves the row looking heavier than iOS's.
        // v153: with the popup switch the icon moves to iOS's trailing edge.
        if (meeroIosRows()) {
            boolean iconOnRight = meeroIosPopup() != LocaleController.isRTL;
            addView(imageView, LayoutHelper.createFrame(20, 40, Gravity.CENTER_VERTICAL | (iconOnRight ? Gravity.RIGHT : Gravity.LEFT)));
        } else {
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }

        textView = new AnimatedEmojiSpan.TextViewEmojis(context);
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextColor(textColor);
        // iOS derives the row's text size from the body size as 13/17, which
        // on the default 17pt body gives 13. Android's flat 16 makes the menu
        // read noticeably chunkier than the one it is imitating.
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, meeroIosRows() ? 15 : 16);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

        checkViewLeft = LocaleController.isRTL;
        makeCheckView(needCheck);
    }

    public void makeCheckView(int needCheck) {
        if (needCheck > 0) {
            checkView = new CheckBox2(getContext(), 26, resourcesProvider);
            checkView.setDrawUnchecked(false);
            checkView.setColor(-1, -1, Theme.key_actionBarDefaultSubmenuItem);
            checkView.setDrawBackgroundAsArc(-1);
            if (needCheck == 1) {
                checkViewLeft = !LocaleController.isRTL;
                addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
                textView.setPadding(!LocaleController.isRTL ? dp(34) : 0, 0, !LocaleController.isRTL ? 0 : dp(34), 0);
            } else {
                addView(checkView, LayoutHelper.createFrame(26, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
                textView.setPadding(LocaleController.isRTL ? dp(34) : 0, 0, LocaleController.isRTL ? 0 : dp(34), 0);
            }
        }
    }

    public void setEmojiCacheType(int cacheType) {
        textView.setCacheType(cacheType);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(itemHeight), View.MeasureSpec.EXACTLY));
        if (expandIfMultiline && textView.getLayout().getLineCount() > 1) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(itemHeight + 8), View.MeasureSpec.EXACTLY));
        }
    }

    public void setItemHeight(int itemHeight) {
        this.itemHeight = itemHeight;
    }

    public void setChecked(boolean checked) {
        if (checkView == null) {
            return;
        }
        checkView.setChecked(checked, true);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled());
        if (checkView != null && checkView.isChecked()) {
            info.setCheckable(true);
            info.setChecked(checkView.isChecked());
            info.setClassName("android.widget.CheckBox");
        }
    }

    public void setCheckColor(int colorKey) {
        checkView.setColor(-1, -1, colorKey);
    }

    public void setRightIcon(int icon) {
        setRightIcon(icon, null);
    }

    public void setRightIcon(int icon, OnClickListener listener) {
        if (rightIcon == null) {
            rightIcon = new ImageView(getContext());
            rightIcon.setScaleType(ImageView.ScaleType.CENTER);
            rightIcon.setColorFilter(iconColor, PorterDuff.Mode.MULTIPLY);
            if (LocaleController.isRTL) {
                rightIcon.setScaleX(-1);
            }
            addView(rightIcon, LayoutHelper.createFrame(24, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
        if (LocaleController.isRTL) {
            layoutParams.leftMargin = rightIcon != null ? dp(32) : 0;
        } else {
            layoutParams.rightMargin = rightIcon != null ? dp(32) : 0;
        }
        textView.setLayoutParams(layoutParams);
        setPadding(dp(LocaleController.isRTL ? listener != null ? 0 : 8 : 18), 0, dp(LocaleController.isRTL ? 18 : listener != null ? 0 : 8), 0);
        if (icon == 0) {
            rightIcon.setVisibility(View.GONE);
        } else {
            rightIcon.setVisibility(View.VISIBLE);
            rightIcon.setImageResource(icon);
            if (listener != null) {
                rightIcon.getLayoutParams().width = dp(40);
                rightIcon.setOnClickListener(listener);
                rightIcon.setBackground(Theme.createRadSelectorDrawable(selectorColor, 6, 0, 0, 6));
            }
        }
        // MeeroX: submenu rows keep a trailing chevron AND a trailing icon -
        // both would collide on iOS's trailing edge, so these rows keep the
        // chevron at the end and pull the glyph back to the leading side.
        if (meeroIosPopup() && rightIcon != null && checkView == null) {
            FrameLayout.LayoutParams iconLp = (FrameLayout.LayoutParams) imageView.getLayoutParams();
            iconLp.gravity = Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            imageView.setLayoutParams(iconLp);
            final boolean hasIcon = iconResId != 0 || imageView.getDrawable() != null;
            textView.setPadding(checkViewLeft ? 0 : dp(hasIcon ? 43 : 0), 0, checkViewLeft ? dp(hasIcon ? 43 : 0) : 0, 0);
            if (listener == null) {
                setPadding(dp(16), 0, dp(16), 0);
            }
        }
    }

    // MeeroX: destructive rows turn iOS-red while the popup switch is on and
    // restore the exact colours the caller gave them when it turns off.
    private boolean meeroDestApplied;
    private int meeroDestTextColor;
    private int meeroDestIconColor;
    private PorterDuff.Mode meeroDestIconMode;

    public void setMeeroDestructiveLook(boolean on) {
        if (on && !meeroDestApplied) {
            meeroDestApplied = true;
            meeroDestTextColor = textColor;
            meeroDestIconColor = iconColor;
            meeroDestIconMode = iconColorMode;
            final int iosRed = Theme.isCurrentThemeDark() ? 0xFFFF453A : 0xFFFF3B30;
            setTextColor(iosRed);
            setIconColor(iosRed);
        } else if (!on && meeroDestApplied) {
            meeroDestApplied = false;
            setTextColor(meeroDestTextColor);
            setIconColor(meeroDestIconColor, meeroDestIconMode);
        }
    }

    public void setTextAndIcon(CharSequence text, int icon) {
        setTextAndIcon(text, icon, null);
    }

    boolean expandIfMultiline;

    public void setMultiline() {
        setMultiline(true);
    }

    public void setMultiline(boolean changeSize) {
        textView.setLines(2);
        if (changeSize) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        } else {
            expandIfMultiline = true;
        }
        textView.setSingleLine(false);
        textView.setGravity(Gravity.CENTER_VERTICAL);
    }

    public void setTextAndIcon(CharSequence text, int icon, Drawable iconDrawable) {
        textView.setText(text);
        if (icon != 0 || iconDrawable != null || checkView != null) {
            if (iconDrawable != null) {
                iconResId = 0;
                imageView.setImageDrawable(iconDrawable);
            } else {
                iconResId = icon;
                imageView.setImageResource(icon);
            }
            imageView.setVisibility(VISIBLE);
            if (meeroIosPopup() && checkView == null) {
                // iOS: the text hugs the leading edge; the gap lives on the
                // trailing side where the icon now sits (30pt, not 43).
                textView.setPadding(checkViewLeft ? dp(icon != 0 || iconDrawable != null ? 30 : 0) : 0, 0, checkViewLeft ? 0 : dp(icon != 0 || iconDrawable != null ? 30 : 0), 0);
            } else {
                textView.setPadding(checkViewLeft ? (checkView != null ? dp(43) : 0) : dp(icon != 0 || iconDrawable != null ? 43 : 0), 0, checkViewLeft ? dp(icon != 0 || iconDrawable != null ? 43 : 0) : (checkView != null ? dp(43) : 0), 0);
            }
        } else {
            iconResId = 0;
            imageView.setVisibility(INVISIBLE);
            textView.setPadding(0, 0, 0, 0);
        }
    }

    public void setTextAndIcon(CharSequence text, ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        textView.setText(text);
        textView.setPadding(checkViewLeft ? (checkView != null ? dp(43) : 0) : dp(43), 0, checkViewLeft ? dp(43) : (checkView != null ? dp(43) : 0), 0);
        if (backupImageView == null) {
            backupImageView = new BackupImageView(getContext());
            backupImageView.setRoundRadius(dp(5));
            addView(backupImageView, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }
        imageView.setVisibility(INVISIBLE);
        backupImageView.setImage(imageLocation, imageFilter, thumb, parentObject);
    }


    public void setIconColorImage(int iconColor) {
        if (backupImageView != null) {
            backupImageView.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        }
    }

    public void setImageSize(int widthDp, int heightDp) {
        if (backupImageView != null) {
            backupImageView.setLayoutParams(LayoutHelper.createFrame(widthDp, heightDp, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        }
    }


    public ActionBarMenuSubItem setColors(int textColor, int iconColor) {
        setTextColor(textColor);
        setIconColor(iconColor);
        return this;
    }

    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            textView.setTextColor(this.textColor = textColor);
        }
    }

    public void setIconColor(int iconColor) {
        setIconColor(iconColor, PorterDuff.Mode.SRC_IN);
    }

    public void setIconColor(int iconColor, PorterDuff.Mode mode) {
        if (this.iconColor != iconColor || this.iconColorMode != mode) {
            imageView.setColorFilter(new PorterDuffColorFilter(this.iconColor = iconColor, this.iconColorMode = mode));
        }
    }

    private ValueAnimator enabledAnimator;
    private boolean enabled;
    public void setEnabledByColor(boolean enabled, int colorDisabled, int colorEnabled) {
        if (enabledAnimator != null) {
            enabledAnimator.cancel();
        }
        enabledAnimator = ValueAnimator.ofFloat(this.enabled ? 1.0f : 0.0f, enabled ? 1.0f : 0.0f);
        this.enabled = enabled;
        enabledAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            setTextColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
            setIconColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
        });
        enabledAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                final float t = enabled ? 1.0f : 0.0f;
                setTextColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
                setIconColor(ColorUtils.blendARGB(colorDisabled, colorEnabled, t));
            }
        });
        enabledAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enabledAnimator.start();
    }

    public void setEnabledByColor(boolean enabled, int textColorDisabled, int iconColorDisabled, int colorEnabled) {
        if (enabledAnimator != null) {
            enabledAnimator.cancel();
        }
        enabledAnimator = ValueAnimator.ofFloat(this.enabled ? 1.0f : 0.0f, enabled ? 1.0f : 0.0f);
        this.enabled = enabled;
        enabledAnimator.addUpdateListener(anm -> {
            final float t = (float) anm.getAnimatedValue();
            setTextColor(ColorUtils.blendARGB(textColorDisabled, colorEnabled, t));
            setIconColor(ColorUtils.blendARGB(iconColorDisabled, colorEnabled, t));
        });
        enabledAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                final float t = enabled ? 1.0f : 0.0f;
                setTextColor(ColorUtils.blendARGB(textColorDisabled, colorEnabled, t));
                setIconColor(ColorUtils.blendARGB(iconColorDisabled, colorEnabled, t));
            }
        });
        enabledAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        enabledAnimator.start();
    }

    private int iconResId;
    public int getIconResId() {
        return iconResId;
    }

    public void setIcon(int resId) {
        imageView.setImageResource(iconResId = resId);
    }

    public void setIcon(Drawable drawable) {
        iconResId = 0;
        imageView.setImageDrawable(drawable);
    }

    public void setAnimatedIcon(int resId) {
        iconResId = 0;
        imageView.setAnimation(resId, 24, 24);
    }

    public void onItemShown() {
        if (imageView.getAnimatedDrawable() != null) {
            imageView.getAnimatedDrawable().start();
        }
    }

    public void setVisibility(boolean visibility) {
        setVisibility(visibility ? View.VISIBLE : View.GONE);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setSubtextColor(int color) {
        if (subtextView != null) {
            subtextView.setTextColor(color);
        }
    }

    public void setSubtext(CharSequence text) {
        if (subtextView == null) {
            subtextView = new TextView(getContext());
            subtextView.setLines(1);
            subtextView.setSingleLine(true);
            subtextView.setGravity(Gravity.LEFT);
            subtextView.setEllipsize(TextUtils.TruncateAt.END);
            subtextView.setTextColor(getThemedColor(Theme.key_groupcreate_sectionText));
            subtextView.setVisibility(GONE);
            subtextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtextView.setPadding(LocaleController.isRTL ? 0 : dp(43), 0, LocaleController.isRTL ? dp(43) : 0, 0);
            addView(subtextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 0, 10, 0, 0));
        }
        boolean visible = !TextUtils.isEmpty(text);
        boolean oldVisible = subtextView.getVisibility() == VISIBLE;
        if (visible != oldVisible) {
            subtextView.setVisibility(visible ? VISIBLE : GONE);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textView.getLayoutParams();
            layoutParams.bottomMargin = visible ? dp(10) : 0;
            textView.setLayoutParams(layoutParams);
        }
        subtextView.setText(text);
    }

    public AnimatedEmojiSpan.TextViewEmojis getTextView() {
        return textView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void setSelectorColor(int selectorColor) {
        if (this.selectorColor != selectorColor) {
            this.selectorColor = selectorColor;
            updateBackground();
        }
    }

    public void updateSelectorBackground(boolean top, boolean bottom) {
        if (this.top == top && this.bottom == bottom) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        updateBackground();
    }

    public void updateSelectorBackground(boolean top, boolean bottom, int selectorRad) {
        if (this.top == top && this.bottom == bottom && this.selectorRad == selectorRad) {
            return;
        }
        this.top = top;
        this.bottom = bottom;
        this.selectorRad = selectorRad;
        updateBackground();
    }

    public void updateBackground() {
        setBackground(Theme.createRadSelectorDrawable(selectorColor, top ? selectorRad : 0, bottom ? selectorRad : 0));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public CheckBox2 getCheckView() {
        return checkView;
    }

    public void openSwipeBack() {
        if (openSwipeBackLayout != null) {
            openSwipeBackLayout.run();
        }
    }

    public ImageView getRightIcon() {
        return rightIcon;
    }
}
