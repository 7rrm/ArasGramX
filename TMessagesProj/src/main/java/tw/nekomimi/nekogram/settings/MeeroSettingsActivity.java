package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ActionBar.Theme.ResourcesProvider;
import org.telegram.ui.Cells.CollapseTextCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroBubbleStyles;
import tw.nekomimi.nekogram.MeeroGlassSupport;
import tw.nekomimi.nekogram.MeeroGlassTheme;
import tw.nekomimi.nekogram.MeeroJanitor;
import tw.nekomimi.nekogram.MeeroTickStyles;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellSelectBox;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import xyz.nextalone.nagram.NaConfig;

/**
 * MeeroX: one screen holding every MeeroX switch, grouped by what it changes.
 *
 * The switches were added one per batch and ended up scattered - eighteen of
 * them in a single unbroken run under one "MeeroX" header in Chat settings,
 * plus the switch style over in General and the back-gesture style in
 * Experimental. Finding "voice waveform" meant scrolling past a dozen
 * unrelated rows, and nothing told the user the three screens were related.
 *
 * This is a presentation change only. Every row below binds to the same
 * static ConfigItem the old rows bind to, so a toggle here writes the same
 * preference the old screen writes and both show the new value the moment
 * they are next drawn. Nothing was added, removed, renamed or re-numbered.
 *
 * The old rows are deliberately left where they are. Removing them would
 * break anyone's muscle memory and, more importantly, would mean editing
 * three working screens to add one - so the risky part of the change would be
 * the part that had nothing to do with the feature.
 *
 * One thing that is NOT safe here: reusing the cell objects from those
 * screens. AbstractConfigCell keeps a single cellGroup field set by
 * bindCellGroup(), so a cell appended to a second group forgets the first,
 * and CellGroup.needSetDivider() then calls rows.indexOf(cell) on the wrong
 * list - that returns -1 and rows.get(0) reads the wrong row, or worse walks
 * off the end. Every cell below is therefore a fresh object; only the
 * ConfigItem behind it is shared, which is exactly the part that should be.
 */
@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class MeeroSettingsActivity extends BaseNekoXSettingsActivity {

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "meerox";
    }

    private final CellGroup cellGroup = new CellGroup(this);

    // Appearance - what an idle screen looks like.
    private final AbstractConfigCell headerAppearance = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupAppearance")));
    // MeeroX v126: the master switch for the fixed "Glass Night" skin of the
    // MeeroX settings screens (ROADMAP batch v126: foundation + chrome).
    // OFF returns the exact stock themed look; no other row cares about it.
    private final AbstractConfigCell glassDesignRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroGlassSettings, MeeroStrings.s("MeeroGlassSettingsInfo")));
    // MeeroX v129: mock-accurate switches sit directly under the master
    // design row. Own on/off; OFF = stock switch even under the glass skin.
    private final AbstractConfigCell glassSwitchesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroGlassSwitches, MeeroStrings.s("MeeroGlassSwitchesInfo")));
    // MeeroX v125: ONE combined row owns both shape pickers - its name tells
    // the user it holds two features, and the tap opens the shared modern
    // sheet on the bubbles tab (the read-marks tab lives inside the same
    // sheet). The old separate tick-style row is gone.
    private final AbstractConfigCell bubbleStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("MeeroPickerRowTitle", NekoConfig.meeroBubbleStyle, bubbleStyleNames(), () -> showBubbleStyleDialog()));
    private final AbstractConfigCell cardsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroCards, MeeroStrings.s("MeeroCardsInfo")));
    private final AbstractConfigCell dialogsStyleRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroDialogsStyle, MeeroStrings.s("MeeroDialogsStyleInfo")));
    private final AbstractConfigCell glassBordersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroGlassBorders, MeeroStrings.s("MeeroGlassBordersInfo")));
    private final AbstractConfigCell iosShadowsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosShadows, MeeroStrings.s("MeeroIosShadowsInfo")));
    private final AbstractConfigCell iosIconsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosIcons, MeeroStrings.s("MeeroIosIconsInfo")));
    // Mirrors the General screen's row. The stored value is the option's
    // index, so the four labels have to stay in this order and iOS has to
    // stay last - a different order here would write a value the other screen
    // reads back as a different style.
    private final AbstractConfigCell switchStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3),
            MeeroStrings.s("StyleIos")
    }, null));
    private final AbstractConfigCell iosRowRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosRow, MeeroStrings.s("MeeroIosRowInfo")));
    private final AbstractConfigCell iosStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosStories, MeeroStrings.s("MeeroIosStoriesInfo")));
    private final AbstractConfigCell iosCallRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosCall, MeeroStrings.s("MeeroIosCallInfo")));
    private final AbstractConfigCell iosAlertsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosAlerts, MeeroStrings.s("MeeroIosAlertsInfo")));
    private final AbstractConfigCell iosMediaGridRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMediaGrid, MeeroStrings.s("MeeroIosMediaGridInfo")));
    private final AbstractConfigCell dividerAppearance = cellGroup.appendCell(new ConfigCellDivider());

    // Chat - things that only show up inside a conversation.
    private final AbstractConfigCell headerChat = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupChat")));
    private final AbstractConfigCell tapMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroTapMenu, MeeroStrings.s("MeeroTapMenuInfo")));
    private final AbstractConfigCell menuBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroMenuBlur, MeeroStrings.s("MeeroMenuBlurInfo")));
    // MeeroX v107: separate switch for the full-screen fog behind the
    // bottom-bar chats popup (menuBlur above frosts the menu panel itself).
    private final AbstractConfigCell chatsMenuFogRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroChatsMenuFog, MeeroStrings.s("MeeroChatsMenuFogInfo")));
    private final AbstractConfigCell iosInputPillRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosInputPill, MeeroStrings.s("MeeroIosInputPillInfo")));
    // MeeroX v142: approved mock "preview-v142" - the iPhone chat header
    // (centered name/status pill + detached photo circle at the edge; tools
    // behind the photo tap / long-press glass menu).
    private final AbstractConfigCell iosWaveformRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosWaveform, MeeroStrings.s("MeeroIosWaveformInfo")));
    private final AbstractConfigCell iosCodeRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosCode, MeeroStrings.s("MeeroIosCodeInfo")));
    private final AbstractConfigCell iosSelectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSelection, MeeroStrings.s("MeeroIosSelectionInfo")));
    // MeeroX v159: approved polish - true-black AMOLED bubbles + one corner
    // radius for every in-bubble card.
    private final AbstractConfigCell amoledBubblesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroAmoledBubbles, MeeroStrings.s("MeeroAmoledBubblesInfo")));
    // MeeroX v164 (approved pick): the AMOLED bubble hairline - defaults OFF
    // so the full-pure-black blend stays for everyone who prefers it merged.
    private final AbstractConfigCell amoledStrokeRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroAmoledStroke, MeeroStrings.s("MeeroAmoledStrokeInfo")));
    private final AbstractConfigCell unifiedRadiiRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroUnifiedRadii, MeeroStrings.s("MeeroUnifiedRadiiInfo")));
    // MeeroX v92: delivery ticks - a dedicated master switch (off returns the
    // official Android ticks). MeeroX v125: the tick-shape picker row that
    // used to sit beneath it was merged into the single combined row above
    // ("Bubbles & read marks"), whose sheet hosts both pickers as tabs - one
    // row, two features, no duplicates.
    private final AbstractConfigCell ticksSwitchRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroTicksSwitch, MeeroStrings.s("MeeroTicksSwitchInfo")));
    private final AbstractConfigCell storyDownloadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroStoryDownload, MeeroStrings.s("MeeroStoryDownloadInfo")));
    // MeeroX v95: the ghost swipe-read toggle moved into GhostModeActivity
    // (circle-style row) so all ghost features live in one place.
    private final AbstractConfigCell dividerChat = cellGroup.appendCell(new ConfigCellDivider());

    // Navigation - moving between screens and lists.
    private final AbstractConfigCell headerNavigation = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupNavigation")));
    private final AbstractConfigCell iosSearchRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSearch, MeeroStrings.s("MeeroIosSearchInfo")));
    private final AbstractConfigCell iosFastScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosFastScroll, MeeroStrings.s("MeeroIosFastScrollInfo")));
    // Mirrors the Experimental screen's row, index-valued in the same way.
    // Predictive is always listed even below API 34, exactly as it is there -
    // dropping it would shift iOS from 3 to 2 and reinterpret the preference.
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getBackAnimationStyle(), new String[]{
            getString(R.string.BackAnimationClassic),
            getString(R.string.BackAnimationSpring),
            getString(R.string.BackAnimationPredictive),
            MeeroStrings.s("BackAnimationIos"),
    }, null));
    private final AbstractConfigCell dividerNavigation = cellGroup.appendCell(new ConfigCellDivider());

    // Motion and feedback - how the interface reacts to a touch.
    private final AbstractConfigCell headerMotion = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupMotion")));
    private final AbstractConfigCell iosAnimRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosAnim, MeeroStrings.s("MeeroIosAnimInfo")));
    private final AbstractConfigCell iosMenuAnimRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMenuAnim, MeeroStrings.s("MeeroIosMenuAnimInfo")));
    private final AbstractConfigCell iosPopupMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosPopupMenu, MeeroStrings.s("MeeroIosPopupMenuInfo")));
    private final AbstractConfigCell iosMsgMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMsgMenu, MeeroStrings.s("MeeroIosMsgMenuInfo")));
    private final AbstractConfigCell iosMainMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMainMenu, MeeroStrings.s("MeeroIosMainMenuInfo")));
    // MeeroX v159: approved polish bundle for the menus themselves.
    private final AbstractConfigCell swiftMenusRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroSwiftMenus, MeeroStrings.s("MeeroSwiftMenusInfo")));
    private final AbstractConfigCell sepFadeRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroSepFade, MeeroStrings.s("MeeroSepFadeInfo")));
    private final AbstractConfigCell flexWidthRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroFlexWidth, MeeroStrings.s("MeeroFlexWidthInfo")));
    private final AbstractConfigCell iosHapticsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosHaptics, MeeroStrings.s("MeeroIosHapticsInfo")));
    private final AbstractConfigCell iosLoadingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosLoading, MeeroStrings.s("MeeroIosLoadingInfo")));
    // MeeroX v164 (approved pick): startup smoothness pre-warm, one shot per
    // launch - lives with the motion rows since its whole job is feel.
    private final AbstractConfigCell smoothPassRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroSmoothPass, MeeroStrings.s("MeeroSmoothPassInfo")));
    private final AbstractConfigCell dividerMotion = cellGroup.appendCell(new ConfigCellDivider());

    // Storage - the auto cache janitor (MeeroX v159, approved feature). The
    // master switch defaults OFF; the three pickers only shape what an armed
    // janitor does. It deletes re-downloadable cloud-media copies only -
    // never messages, never the database, never music.
    private final AbstractConfigCell headerStorage = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupStorage")));
    private final AbstractConfigCell autoJanitorRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroAutoJanitor, MeeroStrings.s("MeeroAutoJanitorInfo")));
    private final AbstractConfigCell janitorLimitRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.meeroJanitorLimit, MeeroJanitor.limitTitles(), null));
    private final AbstractConfigCell janitorAgeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.meeroJanitorAge, new String[]{
            MeeroStrings.s("JanitorDays7"),
            MeeroStrings.s("JanitorDays14"),
            MeeroStrings.s("JanitorDays30"),
    }, null));
    private final AbstractConfigCell janitorModeRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NekoConfig.meeroJanitorMode, new String[]{
            MeeroStrings.s("JanitorModeDaily"),
            MeeroStrings.s("JanitorModeWeekly"),
            MeeroStrings.s("JanitorModeLimit"),
    }, null));
    private final AbstractConfigCell dividerStorage = cellGroup.appendCell(new ConfigCellDivider());

    // Sound and launch.
    private final AbstractConfigCell headerSound = cellGroup.appendCell(new ConfigCellHeader(MeeroStrings.s("MeeroGroupSound")));
    private final AbstractConfigCell iosSoundsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSounds, MeeroStrings.s("MeeroIosSoundsInfo")));
    private final AbstractConfigCell iosIntroRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosIntro, MeeroStrings.s("MeeroIosIntroInfo")));
    private final AbstractConfigCell dividerSound = cellGroup.appendCell(new ConfigCellDivider());

    // v180: the support microscope - renders the stub's boot timeline so
    // one screenshot replaces any future guessing, for him and for any
    // user of the public build who reports boot trouble.
    private final AbstractConfigCell bootLogRow = cellGroup.appendCell(new ConfigCellText("MeeroBootLogTitle", () -> showBootLogDialog()));

    private ListAdapter listAdapter;

    public MeeroSettingsActivity() {
        addRowsToMap(cellGroup);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);

        setupDefaultListeners();
        applyMeeroGlassChrome();
        applySectionsSkin();

        return superView;
    }

    /**
     * MeeroX v128 FIX: the base fragment called listView.setSections(true),
     * which attaches a decoration that paints THEMED section cards
     * (key_windowBackgroundWhite - a blue-gray in many dark themes) behind
     * the rows. Live that looks fine at rest, but while scrolling the
     * sections drawer repaints and the themed wash bleeds through our
     * translucent cards: the whole list flashed to image #2 in the user's
     * report. The same call also re-arms the themed row selector
     * (key_settings_listSelector).
     *
     * While the glass switch is on we re-install the SAME geometry
     * (12dp/16dp/topPadding) but with a painter that paints NOTHING - our
     * v127 cards are the only cards in town - and pin the row selector to
     * our fixed press tint. OFF restores the exact stock call.
     */
    private void applySectionsSkin() {
        // v129: shared with the legacy sub-screens (MeeroGlassSupport).
        MeeroGlassSupport.applySectionsSkin(listView, glassOn(),
                view -> !(view instanceof TextInfoPrivacyCell
                        || view instanceof ShadowSectionCell
                        || view instanceof GraySectionCell
                        || view instanceof CollapseTextCell));
    }

    // ------------------------------------------------------------------
    // MeeroX v126: fixed "Glass Night" chrome (batch v126 = foundation).
    //
    // HOW "theme-proof" works here, in three layers, without touching any
    // Telegram or NagramX rendering code:
    //  1. getResourceProvider(): while the glass switch is on we answer
    //     every theme-key lookup from the fixed MeeroGlassTheme palette, so
    //     even Telegram's own adaptive ActionBar (which animates between
    //     two theme keys on scroll) paints OUR fixed colors automatically.
    //  2. getThemeDescriptions(): with the glass on we return an empty
    //     list, so Telegram's theme-reload machinery simply has nothing to
    //     repaint on this screen - "not affected by themes" is structural,
    //     not a race against repaints. OFF returns the stock list verbatim.
    //  3. onBindMeeroGlass(): every row bind either applies the glass look
    //     or restores the exact stock colors, so the master switch flips
    //     live with a simple notifyDataSetChanged().
    // ------------------------------------------------------------------

    private boolean glassOn() {
        // v127: single source of truth lives in MeeroGlassTheme so the cell
        // providers and this screen always agree about the toggle state.
        return MeeroGlassTheme.enabled();
    }

    @Override
    public ResourcesProvider getResourceProvider() {
        ResourcesProvider base = super.getResourceProvider();
        return glassOn() ? MeeroGlassTheme.wrap(base) : base;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        if (glassOn()) {
            return new ArrayList<>();
        }
        return super.getThemeDescriptions();
    }

    private void applyMeeroGlassChrome() {
        if (fragmentView == null) {
            return;
        }
        if (glassOn()) {
            fragmentView.setBackground(MeeroGlassTheme.screenBackground());
        } else {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }
    }

    @Override
    protected void handleCellClick(View view, int position, float x, float y) {
        super.handleCellClick(view, position, x, y);
        if (position >= 0 && position < cellGroup.rows.size()
                && cellGroup.rows.get(position) == glassDesignRow) {
            // Live flip: palette + cells repaint right now. Everything reads
            // glassOn() lazily, so the provider and future binds follow the
            // new value by themselves; we only nudge the eager parts.
            if (glassOn()) {
                fragmentView.setBackground(MeeroGlassTheme.screenBackground());
                actionBar.setBackgroundColor(MeeroGlassTheme.actionBarBg());
                actionBar.setTitleColor(MeeroGlassTheme.ink());
                actionBar.setItemsColor(MeeroGlassTheme.ink(), false);
            }
            applySectionsSkin();
            if (!glassOn()) {
                // exact stock, mirroring the base fragment's theme keys
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                actionBar.setBackgroundColor(Theme.getColor(Theme.key_avatar_backgroundActionBarBlue));
                actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
                actionBar.setItemsColor(Theme.getColor(Theme.key_avatar_actionBarIconBlue), false);
            }
            glassEntrance.reset(); // replay the entrance under the new look
            listAdapter.notifyDataSetChanged();
        }
        if (position >= 0 && position < cellGroup.rows.size()
                && cellGroup.rows.get(position) == glassSwitchesRow) {
            // Redraw-only toggle: swapped switches repaint per frame from
            // the live flag, stock cells are untouched rows. Just rebind.
            listAdapter.notifyDataSetChanged();
        }
    }

    /** Applies the glass look - or restores exact stock - on every row bind. */
    private void onBindMeeroGlass(@NonNull RecyclerView.ViewHolder holder, int position) {
        // v131: the pass itself moved to MeeroGlassSupport.skinCellGroupRow
        // (verbatim port) so the four remaining newer-base screens render
        // through the very same code; this screen keeps only its per-screen
        // entrance state and toggle.
        MeeroGlassSupport.skinCellGroupRow(holder, position, cellGroup, glassOn(), glassEntrance);
    }

    // v129: the row skinning helpers (margins, header style, value chips,
    // text tinting, the entrance stagger) moved to MeeroGlassSupport so the
    // fourteen legacy-base Meero sub-screens replay the exact same look;
    // this screen keeps only its per-screen entrance state.
    private final MeeroGlassSupport.Entrance glassEntrance = new MeeroGlassSupport.Entrance();

    static String tickStyleName(int style) {
        switch (style) {
            case 1:  return MeeroStrings.s("meeroTickName1");
            case 2:  return MeeroStrings.s("meeroTickName2");
            case 3:  return MeeroStrings.s("meeroTickName3");
            case 4:  return MeeroStrings.s("meeroTickName4");
            case 5:  return MeeroStrings.s("meeroTickName5");
            case 6:  return MeeroStrings.s("meeroTickName6");
            case 7:  return MeeroStrings.s("meeroTickName7");
            case 8:  return MeeroStrings.s("meeroTickName8");
            case 9:  return MeeroStrings.s("meeroTickName9");
            case 10: return MeeroStrings.s("meeroTickName10");
            case 11: return MeeroStrings.s("meeroTickName11");
            case 12: return MeeroStrings.s("meeroTickName12");
            case 13: return MeeroStrings.s("meeroTickName13");
            case 14: return MeeroStrings.s("meeroTickName14");
            case 15: return MeeroStrings.s("meeroTickName15");
            default: return MeeroStrings.s("meeroTickName0");
        }
    }

    static String tickStyleDesc(int style) {
        switch (style) {
            case 1:  return MeeroStrings.s("meeroTickDesc1");
            case 2:  return MeeroStrings.s("meeroTickDesc2");
            case 3:  return MeeroStrings.s("meeroTickDesc3");
            case 4:  return MeeroStrings.s("meeroTickDesc4");
            case 5:  return MeeroStrings.s("meeroTickDesc5");
            case 6:  return MeeroStrings.s("meeroTickDesc6");
            case 7:  return MeeroStrings.s("meeroTickDesc7");
            case 8:  return MeeroStrings.s("meeroTickDesc8");
            case 9:  return MeeroStrings.s("meeroTickDesc9");
            case 10: return MeeroStrings.s("meeroTickDesc10");
            case 11: return MeeroStrings.s("meeroTickDesc11");
            case 12: return MeeroStrings.s("meeroTickDesc12");
            case 13: return MeeroStrings.s("meeroTickDesc13");
            case 14: return MeeroStrings.s("meeroTickDesc14");
            case 15: return MeeroStrings.s("meeroTickDesc15");
            default: return MeeroStrings.s("meeroTickDesc0");
        }
    }

    /**
     * Draws one tick mark for the picker dialog, tinted like dialog text so
     * the preview reads on any theme. "second" picks the mark that joins the
     * first on a read receipt, exactly as the cell layers them.
     */
    static Drawable tickStyleIcon(Context context, int style, boolean second) {
        if (style < 0 || style >= MeeroTickStyles.COUNT) {
            style = 0;
        }
        int res = (second ? MeeroTickStyles.SECONDS : MeeroTickStyles.SINGLES)[style];
        Drawable icon = context.getResources().getDrawable(res).mutate();
        icon.setColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN);
        return icon;
    }

    // MeeroX v122: bubble shapes. Mirrors the tick picker one-to-one so the
    // two selectors behave identically; the only difference is the preview -
    // here each row draws the actual bubble outline it would apply.
    private static final int BUBBLE_STYLE_COUNT = MeeroBubbleStyles.COUNT;

    static String bubbleStyleName(int style) {
        switch (style) {
            case 1:  return MeeroStrings.s("meeroBubbleName1");
            case 2:  return MeeroStrings.s("meeroBubbleName2");
            case 3:  return MeeroStrings.s("meeroBubbleName3");
            case 4:  return MeeroStrings.s("meeroBubbleName4");
            // MeeroX v124: the three shapes added with the picker sheet.
            case 5:  return MeeroStrings.s("meeroBubbleName5");
            case 6:  return MeeroStrings.s("meeroBubbleName6");
            case 7:  return MeeroStrings.s("meeroBubbleName7");
            default: return MeeroStrings.s("meeroBubbleName0");
        }
    }

    static String bubbleStyleDesc(int style) {
        switch (style) {
            case 1:  return MeeroStrings.s("meeroBubbleDesc1");
            case 2:  return MeeroStrings.s("meeroBubbleDesc2");
            case 3:  return MeeroStrings.s("meeroBubbleDesc3");
            case 4:  return MeeroStrings.s("meeroBubbleDesc4");
            case 5:  return MeeroStrings.s("meeroBubbleDesc5");
            case 6:  return MeeroStrings.s("meeroBubbleDesc6");
            case 7:  return MeeroStrings.s("meeroBubbleDesc7");
            default: return MeeroStrings.s("meeroBubbleDesc0");
        }
    }

    private static String[] bubbleStyleNames() {
        String[] names = new String[BUBBLE_STYLE_COUNT];
        for (int i = 0; i < BUBBLE_STYLE_COUNT; i++) {
            names[i] = bubbleStyleName(i);
        }
        return names;
    }

    private void showBubbleStyleDialog() {
        // MeeroX v124: the old AlertDialog list became the modern shared
        // bottom sheet (design A) - same skin for both pickers, tab #0.
        MeeroPickerSheet.open(getParentActivity(), MeeroPickerSheet.TAB_BUBBLES, () -> {
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    /**
     * v180: renders files/vaultdex/.bootlog (written by the plain boot
     * stub - phases with millisecond stamps, seal outcome, done marker).
     * Selectable text so it can be copied into any support chat too.
     */
    private void showBootLogDialog() {
        String text = "";
        try {
            final java.io.File f = new java.io.File(getParentActivity().getFilesDir(), "vaultdex/.bootlog");
            if (f.exists()) {
                final byte[] b = new byte[(int) Math.min(f.length(), 6000)];
                final java.io.FileInputStream in = new java.io.FileInputStream(f);
                final int n = in.read(b);
                in.close();
                if (n > 0) {
                    text = new String(b, 0, n, "UTF-8");
                }
            }
        } catch (Throwable ignored) {
        }
        if (text.length() == 0) {
            text = MeeroStrings.s("MeeroBootLogEmpty");
        }
        final ScrollView sv = new ScrollView(getParentActivity());
        final TextView tv = new TextView(getParentActivity());
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        final float dp = getParentActivity().getResources().getDisplayMetrics().density;
        final int pad = (int) (18 * dp);
        sv.setPadding(pad, pad, pad, pad);
        sv.addView(tv);
        final AlertDialog dlg = new AlertDialog.Builder(getParentActivity())
                .setTitle(MeeroStrings.s("MeeroBootLogDlgTitle"))
                .setView(sv)
                .setPositiveButton(getString(R.string.OK), null)
                .create();
        dlg.show();
    }

    @Override
    public String getTitle() {
        return MeeroStrings.s("MeeroSettingsTitle");
    }

    @Override
    public int getDrawable() {
        return R.drawable.msg_photo_settings_solar;
    }

    /**
     * MeeroX: base id for this screen's search entries.
     *
     * SettingsHelper builds a search result's guid as getBaseGuid() + the row
     * index, so two screens whose bases are closer together than their row
     * counts would hand out the same guid for different rows. The existing
     * screens sit at 10000, 11000, 12000 and 13000; 14000 continues that
     * spacing and leaves this screen the whole block to itself.
     */
    @Override
    public int getBaseGuid() {
        return 14000;
    }

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        protected View createDefaultViewByType(int viewType) {
            // v127: the standard cells, but born with the fixed MeeroX cell
            // palette - the provider is live, so it also serves the stock
            // look verbatim whenever the glass switch is off.
            if (viewType == CellGroup.ITEM_TYPE_TEXT_CHECK) {
                return new TextCheckCell(mContext, 21, false, MeeroGlassTheme.cells());
            }
            if (viewType == CellGroup.ITEM_TYPE_TEXT_SETTINGS_CELL) {
                return new TextSettingsCell(mContext, MeeroGlassTheme.cells());
            }
            if (viewType == CellGroup.ITEM_TYPE_HEADER) {
                return new HeaderCell(mContext, MeeroGlassTheme.cells());
            }
            if (viewType == CellGroup.ITEM_TYPE_TEXT_CHECK_ICON) {
                return new TextCell(mContext, 23, false, true, MeeroGlassTheme.cells());
            }
            return super.createDefaultViewByType(viewType);
        }

        @Override
        protected void onBindDefaultViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            // v126/v127: glass chrome / stock restore per row, card layout,
            // value chips and the entrance stagger (see onBindMeeroGlass)
            onBindMeeroGlass(holder, position);
        }
    }
}
