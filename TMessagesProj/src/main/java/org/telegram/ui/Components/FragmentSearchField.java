package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentSearchField extends FrameLayout implements FactorAnimator.Target, Theme.Colorable {
    private static final int ANIMATOR_ID_CLOSE_BUTTON_VISIBLE = 0;
    private static final int ANIMATOR_ID_SEARCH_ICON_VISIBLE = 1;
    private static final int ANIMATOR_ID_SEARCH_FILTERS_WIDTH = 2;

    private final BoolAnimator animatorCloseIconVisible = new BoolAnimator(ANIMATOR_ID_CLOSE_BUTTON_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, false);
    private final BoolAnimator animatorSearchIconVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_ICON_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);
    private final FactorAnimator animatorSearchFiltersWidth = new FactorAnimator(ANIMATOR_ID_SEARCH_FILTERS_WIDTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280);

    private final Theme.ResourcesProvider resourcesProvider;

    private final ImageView searchIcon;
    private final ImageView closeIcon;
    private final LinearLayout additionalIconsLayout;
    private boolean closeButtonForcedVisible;
    public final EditTextBoldCursor editText;
    private BlurredBackgroundDrawable blurredBackgroundDrawable;

    public FragmentSearchField(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotX(getPaddingLeft());
                setPivotY(getMeasuredHeight() / 2.0f);
            }

            // MeeroX: the idle centred group has to come apart the moment the
            // field takes focus, and re-form when it loses it. Overriding the
            // callback rather than calling setOnFocusChangeListener leaves
            // that listener free - DialogsActivity installs its own on this
            // same EditText to open the search, and a second setter would
            // silently replace it.
            @Override
            protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previouslyFocusedRect) {
                super.onFocusChanged(focused, direction, previouslyFocusedRect);
                meeroRefreshIdleGroup();
            }

            // The placeholder is what the group is centred on, so a new hint
            // means new arithmetic. It is set after construction and changes
            // when the topics tab slides in.
            @Override
            public void setHint(CharSequence hint) {
                super.setHint(hint);
                meeroRefreshIdleGroup();
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && editText.length() == 0 && hasRemovableFilters()) {
                    if (hasRemovableFilters()) {
                        FiltersView.MediaFilterData filterToRemove = currentSearchFilters.get(currentSearchFilters.size() - 1);
                        if (searchFiltersListener != null) {
                            searchFiltersListener.onSearchFilterCleared(filterToRemove);
                        }
                        removeSearchFilter(filterToRemove);
                    }
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        // iOS sets the search field at 17pt, the same body size it uses
        // everywhere else; 15 makes the placeholder look shrunken by contrast.
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, meeroIosSearch() ? 17 : 15);
        editText.setCursorWidth(1.5f);
        editText.setInputType(editText.getInputType() | InputType.TYPE_TEXT_VARIATION_FILTER);
        editText.setSingleLine(true);
        editText.setBackground(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setPadding(dp(48), 0, dp(48), 0);
        editText.setClipToPadding(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!currentSearchFilters.isEmpty()) {
                    if (s.length() > 0 && selectedFilterIndex >= 0) {
                        selectedFilterIndex = -1;
                        onFiltersChanged();
                    }
                }
                checkCloseButtonVisible();
                // MeeroX: the first character typed ends the idle state, and
                // clearing the last one restores it.
                meeroRefreshIdleGroup();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            editText.setLocalePreferredLineHeightForMinimumUsed(false);
        }
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));

        searchIcon = new ImageView(context);
        // iOS scales the magnifier down to about 16pt and lets it sit closer
        // to the edge; Android's 24dp glyph dominates the short pill.
        searchIcon.setScaleType(meeroIosSearch() ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER);
        searchIcon.setImageResource(R.drawable.outline_search_1_24);
        final int meeroIconSize = meeroIosSearch() ? 17 : 24;
        // MeeroX: while the field is idle iOS centres the magnifier and the
        // placeholder together as one group, rather than parking the glyph on
        // the edge with the text beside it. The icon is therefore laid out
        // nudged by meeroPositionSearchIcon() in onLayout, which is where the
        // measured placeholder width is finally known. The gravity below still
        // has to name a side: once the field is focused the group slides back
        // to the edge, and CENTER_VERTICAL alone would leave the glyph
        // horizontally stuck in the middle behind the typed text.
        addView(searchIcon, LayoutHelper.createFrame(meeroIconSize, meeroIconSize, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), meeroIosSearch() ? 16 : 12, 0, meeroIosSearch() ? 16 : 12, 0));

        additionalIconsLayout = new LinearLayout(context);
        additionalIconsLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(additionalIconsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 32, 0, 32, 0));

        closeIcon = new ImageView(context);
        closeIcon.setScaleType(ImageView.ScaleType.CENTER);
        closeIcon.setImageResource(R.drawable.miniplayer_close);
        closeIcon.setVisibility(GONE);
        closeIcon.setOnClickListener(v -> {
            if (hasRemovableFilters()) {
                if (searchFiltersListener != null) {
                    searchFiltersListener.hideActionMode();
                }
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    if (searchFiltersListener != null && currentSearchFilters.get(i).removable) {
                        searchFiltersListener.onSearchFilterCleared(currentSearchFilters.get(i));
                    }
                }
                clearSearchFilters();
            } else if (onCloseSearch != null) {
                onCloseSearch.run();
            } else {
                editText.getText().clear();
            }
        });
        addView(closeIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 12, 0, 12, 0));

        searchFilterLayout = new LinearLayout(getContext()) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                animatorSearchFiltersWidth.animateTo(getMeasuredWidth());
                super.onLayout(changed, l, t, r, b);
            }
        };
        searchFilterLayout.setOrientation(LinearLayout.HORIZONTAL);
        searchFilterLayout.setVisibility(View.VISIBLE);
        addView(searchFilterLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 4, 0, 4, 0));

        setWillNotDraw(false);
        checkUi_editTextPaddings();
        updateColors();
    }

    public void addAdditionalIcon(View icon) {
        additionalIconsLayout.addView(icon);
    }

    private Drawable bg;

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        if (bg != null) {
            bg.setBounds(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom()
            );
            bg.draw(canvas);
        }
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setBounds(
                    getPaddingLeft() - dp(4),
                    getPaddingTop() - dp(4),
                    getWidth() - getPaddingRight() + dp(4),
                    (getHeight() - getPaddingBottom()) + dp(4));
            blurredBackgroundDrawable.draw(canvas);
        }
        // MeeroX: hairline edge over whichever background was just drawn,
        // matching the reference app's 1dp / 18% outline.
        // iOS has no outline on its search bar - the fill alone defines it -
        // so the hairline is skipped while the iOS style is on.
        if (meeroBorderRadius > 0 && !meeroIosSearch() && tw.nekomimi.nekogram.MeeroGlass.enabled()) {
            android.graphics.RectF r = new android.graphics.RectF(
                    getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            tw.nekomimi.nekogram.MeeroGlass.drawBorder(canvas, r, meeroBorderRadius, resourcesProvider);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    /**
     * MeeroX: the iOS search field is a full pill rather than a lightly
     * rounded box. 26dp fully rounds the 48dp-tall field; the theme still
     * supplies the colour.
     */
    /** MeeroX: hairline edge radius, set alongside the background. */
    private int meeroBorderRadius;

    private static int meeroFieldRadius() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroDialogsStyle.Bool() ? dp(26) : dp(20);
        } catch (Throwable e) {
            return dp(20);
        }
    }

    /**
     * MeeroX: iOS search bar styling.
     *
     * The pill shape was already right - meeroFieldRadius returns a full 26dp
     * round - so what separates this from UISearchBar is everything inside it:
     * iOS fills the bar with a solid tint instead of showing the list through
     * it, drops the outline entirely, and sets the magnifier smaller and
     * dimmer than the placeholder next to it.
     */
    public static boolean meeroIosSearch() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosSearch.Bool();
        } catch (Throwable e) {
            return false;
        }
    }

    public void setupBlurredBackground(BlurredBackgroundDrawable drawable) {
        drawable.setRadius(meeroFieldRadius());
        meeroBorderRadius = meeroFieldRadius();
        drawable.setPadding(dp(4));
        blurredBackgroundDrawable = drawable;
    }

    public void setBlurredBackgroundVisibility(float visibility) {
        final int alpha = (int) (255 * visibility);
        boolean changed = false;
        if (blurredBackgroundDrawable != null) {
            if (blurredBackgroundDrawable.getAlpha() != alpha) {
                blurredBackgroundDrawable.setAlpha(alpha);
                changed = true;
            }
        }
        if (bg != null) {
            if (bg.getAlpha() != (255 - alpha)) {
                bg.setAlpha(255 - alpha);
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkUi_editTextPaddings();
    }

    /** MeeroX: gap between the magnifier and the placeholder, measured off iOS. */
    private static final float MEERO_ICON_TEXT_GAP = 9f;

    /**
     * MeeroX: whether the idle centred group applies right now.
     *
     * Only while the field is genuinely idle. Once it has focus or any text
     * the placeholder is gone and the caret belongs at the leading edge, so
     * the group returns to where upstream puts it. Search filters take the
     * leading space for themselves, so they opt out too.
     */
    private boolean meeroIdleCentered() {
        // Guarded because the EditText subclass above can reach this through
        // setHint() and onFocusChanged() while the constructor is still
        // running - editText itself and currentSearchFilters are assigned
        // further down the class, so both are still null at that point.
        if (!meeroIosSearch() || editText == null || currentSearchFilters == null) {
            return false;
        }
        final CharSequence hint = editText.getHint();
        return editText.length() == 0
                && !editText.isFocused()
                && currentSearchFilters.isEmpty()
                && hint != null
                && hint.length() > 0;
    }

    /** MeeroX: width the placeholder will occupy, in pixels. */
    private float meeroHintWidth() {
        final CharSequence hint = editText.getHint();
        if (hint == null || hint.length() == 0) {
            return 0;
        }
        return editText.getPaint().measureText(hint, 0, hint.length());
    }

    /**
     * MeeroX: left edge of the centred magnifier + placeholder group.
     *
     * Both the icon's offset and the text's padding are derived from this one
     * figure so the two cannot drift apart - they are computed in different
     * passes (measure for the padding, layout for the icon) and any second
     * copy of the arithmetic would eventually disagree.
     */
    private int meeroGroupLeft(int iconWidth, float hintWidth) {
        final int inner = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        final int groupWidth = (int) (iconWidth + dp(MEERO_ICON_TEXT_GAP) + hintWidth);
        return getPaddingLeft() + Math.max(0, (inner - groupWidth) / 2);
    }

    private void checkUi_editTextPaddings() {
        final int filtersWidth = (int) animatorSearchFiltersWidth.getFactor() + dp(6); //searchFilterLayout.getWidth();
        final int pStart = Math.max(filtersWidth, dp(48));
        final int pEnd = dp(48) + additionalIconsLayout.getMeasuredWidth();

        int pLeft = LocaleController.isRTL ? pEnd : pStart;
        int pRight = LocaleController.isRTL ? pStart : pEnd;

        // MeeroX: iOS centres the magnifier and the placeholder as one group
        // while the bar is idle, instead of pinning the glyph to the edge.
        // The padding is what positions the hint - editText is gravity LEFT on
        // LTR and RIGHT on RTL, so setting both sides puts the text in the
        // same place either way: LEFT draws it at pLeft, RIGHT draws it ending
        // at width - pRight, and those are the two edges of the same span.
        if (meeroIdleCentered() && getMeasuredWidth() > 0) {
            final int iconW = searchIcon.getMeasuredWidth() > 0
                    ? searchIcon.getMeasuredWidth() : dp(17);
            final float hintW = meeroHintWidth();
            final int textLeft = meeroGroupLeft(iconW, hintW) + iconW + (int) dp(MEERO_ICON_TEXT_GAP);
            final int textRight = (int) (textLeft + hintW);
            final int candidateLeft = textLeft;
            final int candidateRight = getMeasuredWidth() - textRight;
            // Never let the centring squeeze the text against an edge - if the
            // placeholder is long enough that the group no longer fits, fall
            // back to the stock padding rather than clipping it.
            if (candidateLeft >= 0 && candidateRight >= 0) {
                pLeft = candidateLeft;
                pRight = candidateRight;
            }
        }

        AndroidUtilities.rectTmp2.set(
            pLeft, 0,
            editText.getMeasuredWidth() - pRight,
            editText.getMeasuredHeight()
        );
        editText.setClipBounds(AndroidUtilities.rectTmp2);
        editText.setPadding(pLeft, 0, pRight, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        meeroPositionSearchIcon();
    }

    /**
     * MeeroX: re-runs the centring after something it depends on changed.
     *
     * Called from focus, text and hint changes. It is safe before the view has
     * been measured - checkUi_editTextPaddings falls back to the stock padding
     * while getMeasuredWidth() is still zero, and onLayout runs the icon side
     * again once real dimensions exist.
     */
    private void meeroRefreshIdleGroup() {
        // Same construction-order guard as meeroIdleCentered: this can be
        // reached from setHint() before the fields these two touch exist.
        if (!meeroIosSearch() || editText == null || searchIcon == null
                || additionalIconsLayout == null || currentSearchFilters == null) {
            return;
        }
        checkUi_editTextPaddings();
        meeroPositionSearchIcon();
    }

    /**
     * MeeroX: slides the magnifier to the head of the centred group.
     *
     * Done as a translation after layout rather than a margin, because the
     * offset depends on the measured placeholder width - a margin would have
     * to be right before the text is measured, and it changes with the
     * locale's wording.
     */
    private void meeroPositionSearchIcon() {
        if (!meeroIdleCentered()) {
            searchIcon.setTranslationX(0);
            return;
        }
        final int iconW = searchIcon.getMeasuredWidth();
        if (iconW <= 0) {
            searchIcon.setTranslationX(0);
            return;
        }
        searchIcon.setTranslationX(meeroGroupLeft(iconW, meeroHintWidth()) - searchIcon.getLeft());
    }

    public boolean isSectionBackground;
    public void setSectionBackground() {
        isSectionBackground = true;
        setPadding(dp(3), dp(3), dp(3), dp(3));
        updateColors();
    }

    private boolean isWhiteBackground;

    public void setWhiteBackground() {
        isWhiteBackground = true;
        updateColors();
    }

    @Override
    public void updateColors() {
        final boolean isDark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        final int meeroRad = meeroFieldRadius();
        // MeeroX: iOS fills its search bar with a solid tint rather than
        // letting the list show through. UISearchBar sits at roughly 12% of
        // the label colour on dark and 8% on light; the stock 0.07/0.05 here
        // is faint enough that the field reads as an outline, not a surface.
        final float fillAlpha = meeroIosSearch()
                ? (isDark ? 0.12f : 0.08f)
                : (isDark ? 0.07f : 0.05f);
        bg = isSectionBackground ?
            Theme.createRoundRectDrawableShadowed(meeroRad, getThemedColor(Theme.key_windowBackgroundWhite)) :
            Theme.createRoundRectDrawable(meeroRad, isWhiteBackground ? getThemedColor(Theme.key_windowBackgroundWhite) : getThemedColor(Theme.key_windowBackgroundWhiteBlackText, fillAlpha));
        // iOS draws the magnifier smaller and dimmer than the text beside it.
        searchIcon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, meeroIosSearch() ? 0.45f : 0.6f), PorterDuff.Mode.MULTIPLY);
        closeIcon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.6f), PorterDuff.Mode.MULTIPLY);
        closeIcon.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, dp(17)));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.5f));
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(getThemedColor(Theme.key_groupcreate_cursor));
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }

        for (int i = 0, N = additionalIconsLayout.getChildCount(); i < N; i++) {
            final View view = additionalIconsLayout.getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                final ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.getIconView() != null) {
                    item.getIconView().setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.6f), PorterDuff.Mode.MULTIPLY);
                }
                view.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, dp(17)));
            }
        }

        for (int i = 0, N = searchFilterLayout.getChildCount(); i < N; i++) {
            if (searchFilterLayout.getChildAt(i) instanceof ActionBarMenuItem.SearchFilterView) {
                ((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).updateColors();
            }
        }

        invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    private Runnable onCloseSearch;

    public void setCloseButtonOnClickListener(Runnable onCloseSearch) {
        this.onCloseSearch = onCloseSearch;
    }

    public void setCloseButtonVisible(boolean visible) {
        closeButtonForcedVisible = visible;
        checkCloseButtonVisible();
    }

    private void checkCloseButtonVisible() {
        animatorCloseIconVisible.setValue(closeButtonForcedVisible || editText.length() > 0, true);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_CLOSE_BUTTON_VISIBLE) {
            FragmentFloatingButton.setAnimatedVisibility(closeIcon, factor);
            closeIcon.setRotation((1 - factor) * 90);
        } else if (id == ANIMATOR_ID_SEARCH_ICON_VISIBLE) {
            FragmentFloatingButton.setAnimatedVisibility(searchIcon, factor);
        } else if (id == ANIMATOR_ID_SEARCH_FILTERS_WIDTH) {
            checkUi_editTextPaddings();
        }
    }


    public interface SearchFiltersListener {
        void onSearchFilterCleared(FiltersView.MediaFilterData filterData);
        void hideActionMode();
    }

    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private final ArrayList<FiltersView.MediaFilterData> currentSearchFilters = new ArrayList<>();
    private final LinearLayout searchFilterLayout;
    private SearchFiltersListener searchFiltersListener;
    private int selectedFilterIndex;

    private boolean hasRemovableFilters() {
        if (currentSearchFilters.isEmpty()) {
            return false;
        }
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                return true;
            }
        }
        return false;
    }

    public void setSearchFiltersListener(SearchFiltersListener searchFiltersListener) {
        this.searchFiltersListener = searchFiltersListener;
    }

    public void addSearchFilter(FiltersView.MediaFilterData filter) {
        currentSearchFilters.add(filter);
        if (true /*searchContainer.getTag() != null*/) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
    }

    public void removeSearchFilter(FiltersView.MediaFilterData filter) {
        if (!filter.removable) {
            return;
        }
        currentSearchFilters.remove(filter);
        if (selectedFilterIndex < 0 || selectedFilterIndex > currentSearchFilters.size() - 1) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
        if (searchFiltersListener != null) {
            searchFiltersListener.hideActionMode();
        }
    }

    public void clearSearchFiltersWithCallback() {
        if (!currentSearchFilters.isEmpty()) {
            if (searchFiltersListener != null) {
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    if ( currentSearchFilters.get(i).removable) {
                        searchFiltersListener.onSearchFilterCleared(currentSearchFilters.get(i));
                    }
                }
            }
//                clearSearchFilters();
        }
    }

    public void clearSearchFilters() {
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                currentSearchFilters.remove(i);
                i--;
            }
        }
        onFiltersChanged();
    }

    private void onFiltersChanged() {
        final boolean visible = !currentSearchFilters.isEmpty();

        animatorSearchIconVisible.setValue(!visible, true);


        ArrayList<FiltersView.MediaFilterData> localFilters = new ArrayList<>(currentSearchFilters);

        if (true /*searchContainer != null && searchContainer.getTag() != null*/) {
            TransitionSet transition = new TransitionSet();
            ChangeBounds changeBounds = new ChangeBounds();
            changeBounds.setDuration(150);
            transition.addTransition(new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    if (view instanceof ActionBarMenuItem.SearchFilterView) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                    return ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f);
                }
                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    if (view instanceof ActionBarMenuItem.SearchFilterView) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X,  view.getScaleX(), 0.5f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y,  view.getScaleX(), 0.5f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                    return ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0);
                }
            }.setDuration(150)).addTransition(changeBounds);
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            transition.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    notificationsLocker.lock();
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    notificationsLocker.unlock();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    notificationsLocker.unlock();
                }

                @Override
                public void onTransitionPause(Transition transition) {

                }

                @Override
                public void onTransitionResume(Transition transition) {

                }
            });
            TransitionManager.beginDelayedTransition(searchFilterLayout, transition);
        }


        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            boolean removed = localFilters.remove(((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).getFilter());
            if (!removed) {
                searchFilterLayout.removeViewAt(i);
                i--;
            }
        }

        for (int i = 0; i < localFilters.size(); i++) {
            FiltersView.MediaFilterData filter = localFilters.get(i);
            ActionBarMenuItem.SearchFilterView searchFilterView;
            if (filter.reaction != null) {
                searchFilterView = new ActionBarMenuItem.ReactionFilterView(getContext(), resourcesProvider, false);
            } else {
                searchFilterView = new ActionBarMenuItem.SearchFilterView(getContext(), resourcesProvider, false);
            }

            searchFilterView.setGlass();
            searchFilterView.setData(filter);
            searchFilterView.setOnClickListener(view -> {
                int index = currentSearchFilters.indexOf(searchFilterView.getFilter());
                if (selectedFilterIndex != index) {
                    selectedFilterIndex = index;
                    onFiltersChanged();
                    return;
                }
                if (searchFilterView.getFilter().removable) {
                    if (!searchFilterView.isSelectedForDelete()) {
                        searchFilterView.setSelectedForDelete(true);
                    } else {
                        FiltersView.MediaFilterData filterToRemove = searchFilterView.getFilter();
                        removeSearchFilter(filterToRemove);


                        if (searchFiltersListener != null) {
                            searchFiltersListener.onSearchFilterCleared(filterToRemove);
                        }
                    }
                }
            });
            searchFilterLayout.addView(searchFilterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, LocaleController.isRTL ? 6 : 0, 0, LocaleController.isRTL ? 0 : 6, 0));
        }


        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            ((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).setExpanded(i == selectedFilterIndex);
        }
        searchFilterLayout.setTag(visible ? 1 : null);
    }
}
