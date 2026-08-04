package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.MeeroBubbleStyles;
import tw.nekomimi.nekogram.MeeroTickStyles;
import tw.nekomimi.nekogram.NekoConfig;
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
    private final AbstractConfigCell headerAppearance = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MeeroGroupAppearance)));
    // MeeroX v125: ONE combined row owns both shape pickers - its name tells
    // the user it holds two features, and the tap opens the shared modern
    // sheet on the bubbles tab (the read-marks tab lives inside the same
    // sheet). The old separate tick-style row is gone.
    private final AbstractConfigCell bubbleStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("MeeroPickerRowTitle", NekoConfig.meeroBubbleStyle, bubbleStyleNames(), () -> showBubbleStyleDialog()));
    private final AbstractConfigCell cardsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroCards, getString(R.string.MeeroCardsInfo)));
    private final AbstractConfigCell dialogsStyleRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroDialogsStyle, getString(R.string.MeeroDialogsStyleInfo)));
    private final AbstractConfigCell glassBordersRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroGlassBorders, getString(R.string.MeeroGlassBordersInfo)));
    private final AbstractConfigCell iosShadowsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosShadows, getString(R.string.MeeroIosShadowsInfo)));
    private final AbstractConfigCell iosIconsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosIcons, getString(R.string.MeeroIosIconsInfo)));
    // Mirrors the General screen's row. The stored value is the option's
    // index, so the four labels have to stay in this order and iOS has to
    // stay last - a different order here would write a value the other screen
    // reads back as a different style.
    private final AbstractConfigCell switchStyleRow = cellGroup.appendCell(new ConfigCellSelectBox("SwitchStyle", NaConfig.INSTANCE.getSwitchStyle(), new String[]{
            getString(R.string.Default),
            getString(R.string.StyleModern),
            getString(R.string.StyleMaterialDesign3),
            getString(R.string.StyleIos)
    }, null));
    private final AbstractConfigCell iosRowRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosRow, getString(R.string.MeeroIosRowInfo)));
    private final AbstractConfigCell iosStoriesRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosStories, getString(R.string.MeeroIosStoriesInfo)));
    private final AbstractConfigCell iosCallRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosCall, getString(R.string.MeeroIosCallInfo)));
    private final AbstractConfigCell iosAlertsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosAlerts, getString(R.string.MeeroIosAlertsInfo)));
    private final AbstractConfigCell iosMediaGridRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMediaGrid, getString(R.string.MeeroIosMediaGridInfo)));
    private final AbstractConfigCell dividerAppearance = cellGroup.appendCell(new ConfigCellDivider());

    // Chat - things that only show up inside a conversation.
    private final AbstractConfigCell headerChat = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MeeroGroupChat)));
    private final AbstractConfigCell tapMenuRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroTapMenu, getString(R.string.MeeroTapMenuInfo)));
    private final AbstractConfigCell menuBlurRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroMenuBlur, getString(R.string.MeeroMenuBlurInfo)));
    // MeeroX v107: separate switch for the full-screen fog behind the
    // bottom-bar chats popup (menuBlur above frosts the menu panel itself).
    private final AbstractConfigCell chatsMenuFogRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroChatsMenuFog, getString(R.string.MeeroChatsMenuFogInfo)));
    private final AbstractConfigCell iosInputPillRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosInputPill, getString(R.string.MeeroIosInputPillInfo)));
    private final AbstractConfigCell iosWaveformRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosWaveform, getString(R.string.MeeroIosWaveformInfo)));
    private final AbstractConfigCell iosCodeRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosCode, getString(R.string.MeeroIosCodeInfo)));
    private final AbstractConfigCell iosSelectionRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSelection, getString(R.string.MeeroIosSelectionInfo)));
    // MeeroX v92: delivery ticks - a dedicated master switch (off returns the
    // official Android ticks). MeeroX v125: the tick-shape picker row that
    // used to sit beneath it was merged into the single combined row above
    // ("Bubbles & read marks"), whose sheet hosts both pickers as tabs - one
    // row, two features, no duplicates.
    private final AbstractConfigCell ticksSwitchRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroTicksSwitch, getString(R.string.MeeroTicksSwitchInfo)));
    private final AbstractConfigCell storyDownloadRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroStoryDownload, getString(R.string.MeeroStoryDownloadInfo)));
    // MeeroX v95: the ghost swipe-read toggle moved into GhostModeActivity
    // (circle-style row) so all ghost features live in one place.
    private final AbstractConfigCell dividerChat = cellGroup.appendCell(new ConfigCellDivider());

    // Navigation - moving between screens and lists.
    private final AbstractConfigCell headerNavigation = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MeeroGroupNavigation)));
    private final AbstractConfigCell iosSearchRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSearch, getString(R.string.MeeroIosSearchInfo)));
    private final AbstractConfigCell iosFastScrollRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosFastScroll, getString(R.string.MeeroIosFastScrollInfo)));
    // Mirrors the Experimental screen's row, index-valued in the same way.
    // Predictive is always listed even below API 34, exactly as it is there -
    // dropping it would shift iOS from 3 to 2 and reinterpret the preference.
    private final AbstractConfigCell backAnimationStyleRow = cellGroup.appendCell(new ConfigCellSelectBox(null, NaConfig.INSTANCE.getBackAnimationStyle(), new String[]{
            getString(R.string.BackAnimationClassic),
            getString(R.string.BackAnimationSpring),
            getString(R.string.BackAnimationPredictive),
            getString(R.string.BackAnimationIos),
    }, null));
    private final AbstractConfigCell dividerNavigation = cellGroup.appendCell(new ConfigCellDivider());

    // Motion and feedback - how the interface reacts to a touch.
    private final AbstractConfigCell headerMotion = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MeeroGroupMotion)));
    private final AbstractConfigCell iosAnimRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosAnim, getString(R.string.MeeroIosAnimInfo)));
    private final AbstractConfigCell iosMenuAnimRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosMenuAnim, getString(R.string.MeeroIosMenuAnimInfo)));
    private final AbstractConfigCell iosHapticsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosHaptics, getString(R.string.MeeroIosHapticsInfo)));
    private final AbstractConfigCell iosLoadingRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosLoading, getString(R.string.MeeroIosLoadingInfo)));
    private final AbstractConfigCell dividerMotion = cellGroup.appendCell(new ConfigCellDivider());

    // Sound and launch.
    private final AbstractConfigCell headerSound = cellGroup.appendCell(new ConfigCellHeader(getString(R.string.MeeroGroupSound)));
    private final AbstractConfigCell iosSoundsRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosSounds, getString(R.string.MeeroIosSoundsInfo)));
    private final AbstractConfigCell iosIntroRow = cellGroup.appendCell(new ConfigCellTextCheck(NekoConfig.meeroIosIntro, getString(R.string.MeeroIosIntroInfo)));
    private final AbstractConfigCell dividerSound = cellGroup.appendCell(new ConfigCellDivider());

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

        return superView;
    }

    static String tickStyleName(int style) {
        switch (style) {
            case 1:  return getString(R.string.meeroTickName1);
            case 2:  return getString(R.string.meeroTickName2);
            case 3:  return getString(R.string.meeroTickName3);
            case 4:  return getString(R.string.meeroTickName4);
            case 5:  return getString(R.string.meeroTickName5);
            case 6:  return getString(R.string.meeroTickName6);
            case 7:  return getString(R.string.meeroTickName7);
            case 8:  return getString(R.string.meeroTickName8);
            case 9:  return getString(R.string.meeroTickName9);
            case 10: return getString(R.string.meeroTickName10);
            case 11: return getString(R.string.meeroTickName11);
            case 12: return getString(R.string.meeroTickName12);
            case 13: return getString(R.string.meeroTickName13);
            case 14: return getString(R.string.meeroTickName14);
            case 15: return getString(R.string.meeroTickName15);
            default: return getString(R.string.meeroTickName0);
        }
    }

    static String tickStyleDesc(int style) {
        switch (style) {
            case 1:  return getString(R.string.meeroTickDesc1);
            case 2:  return getString(R.string.meeroTickDesc2);
            case 3:  return getString(R.string.meeroTickDesc3);
            case 4:  return getString(R.string.meeroTickDesc4);
            case 5:  return getString(R.string.meeroTickDesc5);
            case 6:  return getString(R.string.meeroTickDesc6);
            case 7:  return getString(R.string.meeroTickDesc7);
            case 8:  return getString(R.string.meeroTickDesc8);
            case 9:  return getString(R.string.meeroTickDesc9);
            case 10: return getString(R.string.meeroTickDesc10);
            case 11: return getString(R.string.meeroTickDesc11);
            case 12: return getString(R.string.meeroTickDesc12);
            case 13: return getString(R.string.meeroTickDesc13);
            case 14: return getString(R.string.meeroTickDesc14);
            case 15: return getString(R.string.meeroTickDesc15);
            default: return getString(R.string.meeroTickDesc0);
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
            case 1:  return getString(R.string.meeroBubbleName1);
            case 2:  return getString(R.string.meeroBubbleName2);
            case 3:  return getString(R.string.meeroBubbleName3);
            case 4:  return getString(R.string.meeroBubbleName4);
            // MeeroX v124: the three shapes added with the picker sheet.
            case 5:  return getString(R.string.meeroBubbleName5);
            case 6:  return getString(R.string.meeroBubbleName6);
            case 7:  return getString(R.string.meeroBubbleName7);
            default: return getString(R.string.meeroBubbleName0);
        }
    }

    static String bubbleStyleDesc(int style) {
        switch (style) {
            case 1:  return getString(R.string.meeroBubbleDesc1);
            case 2:  return getString(R.string.meeroBubbleDesc2);
            case 3:  return getString(R.string.meeroBubbleDesc3);
            case 4:  return getString(R.string.meeroBubbleDesc4);
            case 5:  return getString(R.string.meeroBubbleDesc5);
            case 6:  return getString(R.string.meeroBubbleDesc6);
            case 7:  return getString(R.string.meeroBubbleDesc7);
            default: return getString(R.string.meeroBubbleDesc0);
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

    @Override
    public String getTitle() {
        return getString(R.string.MeeroSettingsTitle);
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
    }
}
