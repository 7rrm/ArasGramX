package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.MeeroDeleteHunter;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v103: delete/edit catcher screen ("صائد الحاذف").
 *
 * Master switch and the newest-first log (150 max): who deleted/edited, the
 * original text (old <- new for edits) and the exact time. Everything stays
 * on device; while the switch is off the hunting hooks do nothing at all.
 */
public class MeeroDeleteHunterActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int logHeaderRow;
    private int logStartRow;
    private int logEndRow;
    private int emptyRow;
    private int clearRow;
    private int infoRow;

    private final ArrayList<MeeroDeleteHunter.LogItem> items = new ArrayList<>();

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
    public void onResume() {
        super.onResume();
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    private String timeOf(long sec) {
        return new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date(sec * 1000L));
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
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
                    if (position >= logStartRow && position < logEndRow) {
                        MeeroDeleteHunter.LogItem item = items.get(position - logStartRow);
                        String title = item.who + "  •  " + MeeroDeleteHunter.kindText(item.kind);
                        String detail;
                        if ("edit".equals(item.kind)) {
                            String oldV = TextUtils.isEmpty(item.oldValue) ? "—" : item.oldValue;
                            String newV = TextUtils.isEmpty(item.newValue) ? "—" : item.newValue;
                            detail = oldV + "  ←  " + newV + "  •  " + timeOf(item.t);
                        } else {
                            String oldV = TextUtils.isEmpty(item.oldValue) ? "—" : item.oldValue;
                            detail = oldV + "  •  " + timeOf(item.t);
                        }
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(title, detail, position + 1 < logEndRow);
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
            } else if (position >= logStartRow && position < logEndRow && logEndRow > logStartRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == emptyRow || position == clearRow && clearRow >= 0) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
