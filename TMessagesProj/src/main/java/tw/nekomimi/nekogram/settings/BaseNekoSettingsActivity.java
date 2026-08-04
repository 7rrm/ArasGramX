package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.CreationTextCell;
import org.telegram.ui.Cells.NotificationsCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import tw.nekomimi.nekogram.MeeroGlassSupport;
import tw.nekomimi.nekogram.MeeroGlassTheme;
import tw.nekomimi.nekogram.ui.cells.AccountCell;
import tw.nekomimi.nekogram.ui.cells.EmojiSetCell;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public abstract class BaseNekoSettingsActivity extends BaseFragment {

    public static final Object PARTIAL = new Object();

    public static final int TYPE_SHADOW = 1;
    public static final int TYPE_SETTINGS = 2;
    public static final int TYPE_CHECK = 3;
    public static final int TYPE_HEADER = 4;
    public static final int TYPE_NOTIFICATION_CHECK = 5;
    public static final int TYPE_DETAIL_SETTINGS = 6;
    public static final int TYPE_INFO_PRIVACY = 7;
    public static final int TYPE_TEXT = 8;
    public static final int TYPE_CHECKBOX = 9;
    public static final int TYPE_RADIO = 10;
    public static final int TYPE_ACCOUNT = 11;
    public static final int TYPE_EMOJI = 12;
    public static final int TYPE_EMOJI_SELECTION = 13;
    public static final int TYPE_CREATION = 14;
    public static final int TYPE_FLICKER = 15;
    public static final int TYPE_CHECK2 = 16;
    public static final int TYPE_CHECKBOX2 = 17;

    protected BlurredRecyclerView listView;
    protected BaseListAdapter listAdapter;
    protected LinearLayoutManager layoutManager;
    protected Theme.ResourcesProvider resourcesProvider;

    protected int rowCount;
    protected HashMap<String, Integer> rowMap = new HashMap<>(20);
    protected HashMap<Integer, String> rowMapReverse = new HashMap<>(20);


    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        updateRows();

        return true;
    }

    @Override
    public View createView(Context context) {
        fragmentView = new BlurContentView(context);
        fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        SizeNotifierFrameLayout frameLayout = (SizeNotifierFrameLayout) fragmentView;

        actionBar.setDrawBlurBackground(frameLayout);

        listView = new BlurredRecyclerView(context);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                frameLayout.invalidateBlur();
            }
        });
        listView.additionalClipBottom = dp(200);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setChangeDuration(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listAdapter = createAdapter(context);

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(this::onItemClick);
        listView.setClipToPadding(false);
        listView.setOnItemLongClickListener((view, position, x, y) -> {
            if (onItemLongClick(view, position, x, y)) {
                return true;
            }
            var holder = listView.findViewHolderForAdapterPosition(position);
            var key = getKey();
            if (key != null && holder != null && listAdapter.isEnabled(holder) && rowMapReverse.containsKey(position)) {
                showDialog(new AlertDialog.Builder(context).setItems(new CharSequence[]{getString(R.string.CopyLink)}, (dialogInterface, i) -> {
                    AndroidUtilities.addToClipboard(String.format(Locale.getDefault(), "https://%s/nasettings/%s?r=%s", getMessagesController().linkPrefix, getKey(), rowMapReverse.get(position)));
                    BulletinFactory.of(BaseNekoSettingsActivity.this).createCopyLinkBulletin().show();
                }).create());
                return true;
            }
            return false;
        });

        listView.setSections(true);
        actionBar.setAdaptiveBackground(listView);
        meeroApplyGlassChrome();
        return fragmentView;
    }

    // @Override
    protected void setParentLayout(ActionBarLayout layout) {
        if (layout != null && !hasWhiteActionBar()) {
            resourcesProvider = layout.getLastFragment().getResourceProvider();
            meeroProviderWrapped = false; // v129: re-wrap around the new delegate if glass wants it
            meeroEnsureGlassProvider();
        }
        super.setParentLayout(layout);
    }

    @Override
    public ActionBar createActionBar(Context context) {
        meeroEnsureGlassProvider();
        ActionBar actionBar = super.createActionBar(context);
        if (hasWhiteActionBar()) {
            actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
            actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setCastShadows(false);
        }
        actionBar.setTitle(getActionBarTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        return actionBar;
    }

    protected String getKey() {
        return null;
    }

    protected abstract void onItemClick(View view, int position, float x, float y);

    protected boolean onItemLongClick(View view, int position, float x, float y) {
        return false;
    }

    protected abstract BaseListAdapter createAdapter(Context context);

    protected abstract String getActionBarTitle();

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onResume() {
        super.onResume();
        meeroApplyGlassChrome();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    protected boolean hasWhiteActionBar() {
        return true;
    }

    protected CharSequence getSpannedString(int id) {
        var text = getString(id);
        var builder = new SpannableStringBuilder(text);
        int index1 = text.indexOf("**");
        int index2 = text.lastIndexOf("**");
        if (index1 >= 0 && index2 >= 0 && index1 != index2) {
            builder.replace(index2, index2 + 2, "");
            builder.replace(index1, index1 + 2, "");
            builder.setSpan(new URLSpanNoUnderline("https://core.telegram.org/api/datacenter"), index1, index2 - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    @Override
    public boolean isLightStatusBar() {
        if (!hasWhiteActionBar()) return super.isLightStatusBar();
        int color = getThemedColor(Theme.key_windowBackgroundWhite);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    protected int addRow() {
        return rowCount++;
    }

    // TODO: refactor the whole settings
    protected int addRow(String... keys) {
        var row = rowCount++;
        for (var key : keys) {
            rowMap.put(key, row);
        }
        rowMapReverse.put(row, keys[0]);
        return row;
    }

    public void scrollToRow(String key, Runnable unknown) {
        if (rowMap.containsKey(key)) {
            listView.highlightRow(() -> {
                // noinspection ConstantConditions
                int position = rowMap.get(key);
                layoutManager.scrollToPositionWithOffset(position, dp(60));
                return position;
            });
        } else {
            unknown.run();
        }
    }

    protected void updateRows() {
        rowCount = 0;
        rowMap.clear();
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return resourcesProvider;
    }

    /**
     * MeeroX v129: opt-in flag for the fixed glass skin on legacy-base
     * screens. Only the fourteen Meero* screens override this to return
     * true; every other screen on this base renders byte-identical to
     * stock, glass master switch notwithstanding.
     */
    protected boolean meeroGlassScreen() {
        return false;
    }

    private boolean meeroGlassActive() {
        return meeroGlassScreen() && MeeroGlassTheme.enabled();
    }

    private boolean meeroProviderWrapped;

    /**
     * Legacy screens paint their ActionBar, blur scrim, status-bar
     * luminance, section backgrounds and cells from this fragment's
     * resource provider. Wrapped exactly once so every consumer - cells
     * constructed with the field directly included - resolves through the
     * fixed palette while the glass design is on, and falls back to pure
     * stock resolution when it is off (the wrapper is live).
     */
    private void meeroEnsureGlassProvider() {
        if (meeroGlassActive() && !meeroProviderWrapped) {
            resourcesProvider = MeeroGlassTheme.wrapLegacy(resourcesProvider);
            meeroProviderWrapped = true;
        }
    }

    private boolean meeroChromeApplied;

    /**
     * Applies (or, after a toggle while this screen sat on the back stack,
     * rolls back) the glass chrome: screen background, sections painter and
     * row selector. Called from createView and onResume so the master
     * switch flips this screen live either way. Rollback restores the exact
     * stock calls the class made originally.
     */
    private void meeroApplyGlassChrome() {
        if (fragmentView == null || listView == null) {
            return;
        }
        if (meeroGlassActive()) {
            meeroEnsureGlassProvider();
            fragmentView.setBackground(MeeroGlassTheme.screenBackground());
            MeeroGlassSupport.applySectionsSkin(listView, true, MeeroGlassSupport::legacySectionsKeep);
        } else if (meeroChromeApplied) {
            fragmentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
            listView.setSections(true);
        }
        meeroChromeApplied = meeroGlassActive();
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        listView.setPadding(0, 0, 0, bottom);
        listView.setClipToPadding(false);
    }

    private class BlurContentView extends SizeNotifierFrameLayout {

        public Paint blurScrimPaint = new Paint();
        Rect rectTmp = new Rect();

        public BlurContentView(Context context) {
            super(context);
            needBlur = hasWhiteActionBar();
            blurBehindViews.add(this);
        }

        @Override
        protected void drawList(Canvas blurCanvas, boolean top, ArrayList<IViewWithInvalidateCallback> views) {
            for (int j = 0; j < listView.getChildCount(); j++) {
                View child = listView.getChildAt(j);
                if (child.getY() < listView.blurTopPadding + dp(100)) {
                    int restore = blurCanvas.save();
                    blurCanvas.translate(getX() + child.getX(), getY() + listView.getY() + child.getY());
                    child.draw(blurCanvas);
                    blurCanvas.restoreToCount(restore);
                }
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (hasWhiteActionBar() && listView.canScrollVertically(-1)) {
                rectTmp.set(0, 0, getMeasuredWidth(), 1);
                blurScrimPaint.setColor(getThemedColor(Theme.key_divider));
                drawBlurRect(canvas, getY(), rectTmp, blurScrimPaint, true);
            }
        }
    }

    protected abstract class BaseListAdapter extends RecyclerListView.SelectionAdapter {

        protected final Context mContext;

        public BaseListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_SETTINGS || type == TYPE_CHECK || type == TYPE_NOTIFICATION_CHECK || type == TYPE_DETAIL_SETTINGS || type == TYPE_TEXT | type == TYPE_CHECKBOX || type == TYPE_RADIO || type == TYPE_ACCOUNT || type == TYPE_EMOJI || type == TYPE_EMOJI_SELECTION || type == TYPE_CREATION || type == TYPE_CHECK2 || type == TYPE_CHECKBOX2;
        }

        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {

        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            var payload = holder.getPayload();
            onBindViewHolder(holder, position, PARTIAL.equals(payload));
            meeroApplyCard(holder, position);
            meeroStyleHeader(holder);
            meeroGlassBind(holder, position);
        }

        /** Per-adapter entrance state for the glass stagger (v129). */
        private final MeeroGlassSupport.Entrance meeroEntrance = new MeeroGlassSupport.Entrance();

        /**
         * MeeroX v129: the glass skin pass runs LAST on every bind, after
         * content and the older cards feature, so the fixed palette always
         * wins visually; with the design off (or on non-opted-in screens)
         * it restores/maintains the exact stock look instead.
         */
        private void meeroGlassBind(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (!meeroGlassScreen()) {
                return;
            }
            MeeroGlassSupport.skinLegacyRow(holder, position, this, meeroGlassActive(), meeroEntrance);
        }

        /**
         * MeeroX: turns the flat row list into iOS 26 grouped cards.
         *
         * The card shape depends on the neighbours, which are only known once
         * the rows exist - so it is decided here rather than in
         * onCreateViewHolder. Rows that are separators or dividers break a
         * group; everything between two breaks becomes one card.
         */
        private void meeroApplyCard(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (!tw.nekomimi.nekogram.MeeroCards.enabled()) {
                return;
            }
            final int type = holder.getItemViewType();
            if (!meeroIsCardRow(type)) {
                return;
            }
            // The reference layout gives every row its own card with a gap
            // between them, rather than merging a run of rows into one long
            // panel. POS_SINGLE draws all four corners rounded.
            final View v = holder.itemView;
            tw.nekomimi.nekogram.MeeroCards.attach(v,
                    new tw.nekomimi.nekogram.MeeroCards.CardDrawable(
                            tw.nekomimi.nekogram.MeeroCards.POS_SINGLE, resourcesProvider));

            // Give the icon the accent-tinted tile the reference uses, and
            // recolour the glyph to match.
            if (v instanceof TextCell) {
                final TextCell cell = (TextCell) v;
                // Each row gets its own hue so the list is scannable instead
                // of a column of identical badges.
                //
                // The tile is asked for by index and painted by the cell
                // itself. Setting it as the icon view's background - which is
                // what this did before - stretched the square to that view's
                // 24x31dp box and it came out as a clipped rectangle.
                cell.setMeeroIconTile(position);
                cell.setMeeroDividerEnabled(false);
            }
        }

        /**
         * MeeroX: section titles rendered the way the reference app does -
         * sitting on the page above their card, in the accent colour, with
         * 20dp/12dp padding rather than a filled row.
         */
        private void meeroStyleHeader(RecyclerView.ViewHolder holder) {
            if (!tw.nekomimi.nekogram.MeeroCards.enabled()) {
                return;
            }
            if (holder.getItemViewType() != TYPE_HEADER) {
                return;
            }
            final View v = holder.itemView;
            v.setBackground(null);
            // Sits just above its card, with more air above than below so the
            // title clearly belongs to the group under it.
            v.setPadding(dp(24), dp(18), dp(24), dp(4));
            if (v instanceof HeaderCell) {
                final HeaderCell header = (HeaderCell) v;
                header.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
                header.setTextSize(13);
            }
        }

        /**
         * Row types that live inside a card.
         *
         * Headers are deliberately excluded: the reference layout puts a
         * section title above its card in the accent colour, not inside it as
         * a first row.
         */
        private boolean meeroIsCardRow(int type) {
            return type == TYPE_SETTINGS || type == TYPE_CHECK
                    || type == TYPE_NOTIFICATION_CHECK || type == TYPE_DETAIL_SETTINGS
                    || type == TYPE_TEXT || type == TYPE_CHECKBOX || type == TYPE_RADIO
                    || type == TYPE_ACCOUNT || type == TYPE_CREATION
                    || type == TYPE_CHECK2 || type == TYPE_CHECKBOX2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext, resourcesProvider);
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_HEADER:
                    view = new HeaderCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_NOTIFICATION_CHECK:
                    view = new NotificationsCheckCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_DETAIL_SETTINGS:
                    view = new TextDetailSettingsCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO_PRIVACY:
                    view = new TextInfoPrivacyCell(mContext, resourcesProvider);
                    view.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                case TYPE_TEXT:
                    view = new TextCell(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECKBOX:
                    view = new TextCheckbox2Cell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_RADIO:
                    view = new TextRadioCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ACCOUNT:
                    view = new AccountCell(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_EMOJI:
                case TYPE_EMOJI_SELECTION:
                    view = new EmojiSetCell(mContext, viewType == TYPE_EMOJI_SELECTION);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CREATION:
                    CreationTextCell creationTextCell = new CreationTextCell(mContext, 70, resourcesProvider);
                    creationTextCell.startPadding = 61;
                    view = creationTextCell;
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_FLICKER:
                    view = new FlickerLoadingView(mContext, resourcesProvider);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK2:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECKBOX2:
                    CheckBoxCell checkBoxCell = new CheckBoxCell(mContext, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, getResourceProvider());
                    checkBoxCell.getCheckBoxRound().setDrawBackgroundAsArc(14);
                    checkBoxCell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                    checkBoxCell.setEnabled(true);
                    view = checkBoxCell;
                    view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            // noinspection ConstantConditions
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }
    }


}
