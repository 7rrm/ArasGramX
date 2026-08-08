package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;

import tw.nekomimi.nekogram.MeeroSmartFolders;

/**
 * MeeroX v161 (his pick: every mock feature except Quick Replies - this is
 * "المجلدات الذكية بقواعد"): one-tap smart folders that organize themselves.
 *
 * Rules run on Telegram's native synced folder engine through the official
 * FilterCreateActivity.saveFilterToServer pipeline, so created folders are
 * ordinary server folders: they sync to every device, honour the official
 * limit, and can be edited/deleted from Telegram's regular Folders screen.
 * Already-created presets show their rule summary again (tap = re-create
 * is skipped; the screen reports "موجود").
 */
public class MeeroSmartFoldersActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int presetStartRow;
    private int presetEndRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        headerRow = addRow();
        presetStartRow = rowCount;
        for (int i = 0; i < MeeroSmartFolders.presets().length; i++) addRow();
        presetEndRow = rowCount;
        infoRow = addRow();
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s("SmartFoldersTitle");
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == infoRow) {
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, "SmartFoldersInfo");
        } else if (position >= presetStartRow && position < presetEndRow) {
            final MeeroSmartFolders.Preset p = MeeroSmartFolders.presets()[position - presetStartRow];
            if (MeeroSmartFolders.exists(p)) {
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(MeeroStrings.s(p.titleKey))
                        .setMessage(MeeroStrings.s("SmartFolderExists"))
                        .setPositiveButton(getString(R.string.OK), null)
                        .show();
                return;
            }
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(MeeroStrings.s(p.titleKey))
                    .setMessage(MeeroStrings.s(p.ruleKey))
                    .setPositiveButton(MeeroStrings.s("SmartFolderCreate"), (d, w) ->
                            MeeroSmartFolders.create(this, p, () -> {
                                if (listView != null) listView.post(() -> {
                                    if (listAdapter != null) listAdapter.notifyDataSetChanged();
                                });
                            }))
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
            return type == TYPE_DETAIL_SETTINGS || type == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(MeeroStrings.s("SmartFoldersHeader"));
                    break;
                case TYPE_DETAIL_SETTINGS: {
                    TextDetailSettingsCell cell = (TextDetailSettingsCell) holder.itemView;
                    MeeroSmartFolders.Preset p = MeeroSmartFolders.presets()[position - presetStartRow];
                    cell.setMultilineDetail(true);
                    final boolean done = MeeroSmartFolders.exists(p);
                    cell.setTextAndValue(
                            p.emoticon + "  " + MeeroStrings.s(p.titleKey),
                            done ? MeeroStrings.s("SmartFolderDone") : MeeroStrings.s(p.ruleKey),
                            position + 1 < presetEndRow);
                    break;
                }
                case TYPE_TEXT: {
                    org.telegram.ui.Cells.TextCell cell = (org.telegram.ui.Cells.TextCell) holder.itemView;
                    cell.setTextAndValue(MeeroStrings.s("MeeroUsageGuide"), "", false);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position >= presetStartRow && position < presetEndRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == infoRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
