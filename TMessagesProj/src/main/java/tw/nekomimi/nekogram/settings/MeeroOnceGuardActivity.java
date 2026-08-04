package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v105: view-once guard screen ("حارس عرض-مرة").
 *
 * Master switch (off by default) and a saved-to-gallery counter. The engine
 * ({@link tw.nekomimi.nekogram.MeeroOnceGuard}) quietly downloads incoming
 * view-once photos/videos and copies them into the public gallery under
 * "MeeroX Once". While the switch is off nothing is fetched or stored.
 */
public class MeeroOnceGuardActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int countHeaderRow;
    private int countRow;
    private int infoRow;

    @Override
    protected void updateRows() {
        super.updateRows();
        masterRow = addRow();
        countHeaderRow = addRow();
        countRow = addRow();
        infoRow = addRow();
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroOnceTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        // MeeroX v109: consent defense - reaching this screen by ANY path
        // without a granted consent re-shows the sheet; رفض closes the
        // screen. تحصيل الموافقة ينحفظ مرة وحدة فقط وبضغطة «موافق».
        tw.nekomimi.nekogram.MeeroOnceGuard.ensureConsentDialog(this, null, true);
        if (listAdapter != null) {
            listAdapter.notifyItemChanged(countRow);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroOnceGuard.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroOnceGuard.Bool());
        } else if (position == infoRow) {
            // v111: usage-guide popup instead of the long footer.
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, R.string.MeeroOnceInfo);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // v111: the usage-guide row (TYPE_TEXT) is clickable too.
            return holder.getItemViewType() == TYPE_CHECK || holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_CHECK:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == masterRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroOnceMaster), NekoConfig.meeroOnceGuard.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == countHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroOnceCountHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == countRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroOnceCount), String.valueOf(NekoConfig.meeroOnceSavedCount.Int()), true);
                    } else if (position == infoRow) {
                        // v111: usage-guide button instead of the long footer.
                        textCell.setTextAndValue(getString(R.string.MeeroUsageGuide), "", true);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow) {
                return TYPE_CHECK;
            } else if (position == countHeaderRow) {
                return TYPE_HEADER;
            } else if (position == countRow || position == infoRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
