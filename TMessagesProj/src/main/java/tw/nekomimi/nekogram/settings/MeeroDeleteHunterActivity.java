package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import tw.nekomimi.nekogram.MeeroDeleteHunter;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v103-v104: delete/edit catcher screen ("صائد الحاذف").
 *
 * Master switch and the newest-first log (150 max): who deleted/edited, the
 * original text (old <- new for edits) and the exact time. Everything stays
 * on device; while the switch is off the hunting hooks do nothing at all.
 *
 * v104: multi-select mode - a long-press on any entry turns on selection:
 * every entry gets a checkbox, the action bar offers "select all" and
 * "delete", and back leaves selection mode instead of closing the screen.
 * Selected entries are tracked by stabIe keys (time + sender + kind + text
 * hash) so an event arriving mid-selection can never shift the wrong rows.
 * Deleting only cleans this on-device log - conversations are untouched.
 */
public class MeeroDeleteHunterActivity extends BaseNekoSettingsActivity {

    private static final int MENU_SELECT_ALL = 1;
    private static final int MENU_DELETE = 2;

    private int masterRow;
    private int logHeaderRow;
    private int logStartRow;
    private int logEndRow;
    private int emptyRow;
    private int clearRow;
    private int infoRow;

    private final ArrayList<MeeroDeleteHunter.LogItem> items = new ArrayList<>();

    // v104: selection-mode state
    private boolean selecting;
    private final HashSet<String> selected = new HashSet<>();
    private ActionBarMenuItem selectAllItem;
    private ActionBarMenuItem deleteItem;

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        masterRow = addRow();
        logHeaderRow = addRow();
        logStartRow = rowCount;
        for (int i = 0; i < items.size(); i++) addRow();
        logEndRow = rowCount;
        emptyRow = items.isEmpty() ? addRow() : -1;
        clearRow = items.isEmpty() ? -1 : addRow();
        infoRow = addRow();
    }

    private void reload() {
        items.clear();
        items.addAll(MeeroDeleteHunter.getLog());
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroHunterTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public View createView(Context context) {
        View v = super.createView(context);
        // Selection-mode toolbar actions (hidden while not selecting).
        ActionBarMenu menu = actionBar.createMenu();
        selectAllItem = menu.addItem(MENU_SELECT_ALL, R.drawable.msg_select_solar);
        selectAllItem.setContentDescription(getString(R.string.MeeroHunterSelectAll));
        deleteItem = menu.addItem(MENU_DELETE, R.drawable.msg_delete_solar);
        deleteItem.setContentDescription(getString(R.string.Delete));
        // Replaces the base listener: the back arrow leaves selection mode
        // first and only then closes the screen.
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selecting) {
                        setSelecting(false);
                    } else {
                        finishFragment();
                    }
                } else if (id == MENU_SELECT_ALL) {
                    selectAll();
                } else if (id == MENU_DELETE) {
                    confirmDeleteSelected();
                }
            }
        });
        updateSelectionUi();
        return v;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        // Gesture / hardware back also leaves selection mode first.
        if (selecting) {
            setSelecting(false);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    private String timeOf(long sec) {
        return new SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(new Date(sec * 1000L));
    }

    private boolean isLogRow(int position) {
        return logEndRow > logStartRow && position >= logStartRow && position < logEndRow;
    }

    // ---------------- selection mode ----------------

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (isLogRow(position)) {
            if (!selecting) {
                setSelecting(true);
            }
            toggleSelection(position - logStartRow);
            try {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Throwable ignore) {}
            return true;
        }
        return false;
    }

    private void toggleSelection(int index) {
        if (index < 0 || index >= items.size()) return;
        String key = MeeroDeleteHunter.keyOf(items.get(index));
        if (!selected.remove(key)) {
            selected.add(key);
        }
        updateSelectionUi();
        listAdapter.notifyItemChanged(logStartRow + index);
    }

    private void selectAll() {
        selected.clear();
        for (MeeroDeleteHunter.LogItem li : items) {
            selected.add(MeeroDeleteHunter.keyOf(li));
        }
        updateSelectionUi();
        listAdapter.notifyDataSetChanged();
    }

    private void setSelecting(boolean on) {
        if (selecting == on) return;
        selecting = on;
        if (!on) {
            selected.clear();
        }
        updateSelectionUi();
        listAdapter.notifyDataSetChanged();
    }

    private void updateSelectionUi() {
        if (selectAllItem != null) selectAllItem.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (deleteItem != null) deleteItem.setVisibility(selecting ? View.VISIBLE : View.GONE);
        if (selecting) {
            actionBar.setTitle(LocaleController.formatString(R.string.MeeroHunterSelectedCount, selected.size()));
        } else {
            actionBar.setTitle(getString(R.string.MeeroHunterTitle));
        }
    }

    private void confirmDeleteSelected() {
        Context context = getParentActivity();
        if (context == null) return;
        if (selected.isEmpty()) {
            BulletinFactory.of(this)
                    .createSimpleBulletin(R.raw.info, getString(R.string.MeeroHunterNothingSelected)).show();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.Delete))
                .setMessage(LocaleController.formatString(R.string.MeeroHunterDeleteConfirm, selected.size()))
                .setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    MeeroDeleteHunter.removeFromLog(new HashSet<>(selected));
                    setSelecting(false);
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    // ---------------- clicks ----------------

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (selecting) {
            // While selecting, taps only toggle entries; the rest is frozen.
            if (isLogRow(position)) {
                toggleSelection(position - logStartRow);
            }
            return;
        }
        if (position == masterRow) {
            NekoConfig.meeroDeleteHunter.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroDeleteHunter.Bool());
        } else if (position == clearRow && clearRow >= 0) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.MeeroWatchLogClear))
                    .setMessage(getString(R.string.MeeroHunterClearConfirm))
                    .setPositiveButton(getString(R.string.MeeroWatchLogClear), (dialog, which) -> {
                        MeeroDeleteHunter.clearLog();
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        }
    }

    private String detailOf(MeeroDeleteHunter.LogItem item) {
        String oldV = TextUtils.isEmpty(item.oldValue) ? "—" : item.oldValue;
        if ("edit".equals(item.kind)) {
            String newV = TextUtils.isEmpty(item.newValue) ? "—" : item.newValue;
            return oldV + "  ←  " + newV;
        }
        return oldV;
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_CHECK || type == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_CHECK:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == masterRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroHunterMaster), NekoConfig.meeroDeleteHunter.Bool(), true);
                    } else if (isLogRow(position)) {
                        // selection mode: title lines carry the who/kind and
                        // the content, the time moves to the value column and
                        // the checkbox mirrors the stable-key selection set
                        MeeroDeleteHunter.LogItem item = items.get(position - logStartRow);
                        String title = item.who + "  •  " + MeeroDeleteHunter.kindText(item.kind);
                        checkCell.setTextAndValueAndCheck(title + "\n" + detailOf(item), timeOf(item.t),
                                selected.contains(MeeroDeleteHunter.keyOf(item)), true, position + 1 < logEndRow);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == logHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroHunterLogHeader));
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (isLogRow(position)) {
                        // normal (non-selecting) rendering - exactly as v103
                        MeeroDeleteHunter.LogItem item = items.get(position - logStartRow);
                        String title = item.who + "  •  " + MeeroDeleteHunter.kindText(item.kind);
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(title, detailOf(item) + "  •  " + timeOf(item.t), position + 1 < logEndRow);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroHunterEmpty), "", true);
                    } else if (position == clearRow && clearRow >= 0) {
                        textCell.setTextAndValue(getString(R.string.MeeroWatchLogClear), "", true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroHunterInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow) {
                return TYPE_CHECK;
            } else if (position == logHeaderRow) {
                return TYPE_HEADER;
            } else if (logEndRow > logStartRow && position >= logStartRow && position < logEndRow) {
                // the same rows flip between the detail cell and a checkbox
                // cell when selection mode turns on/off
                return selecting ? TYPE_CHECK : TYPE_DETAIL_SETTINGS;
            } else if (position == emptyRow || position == clearRow && clearRow >= 0) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
