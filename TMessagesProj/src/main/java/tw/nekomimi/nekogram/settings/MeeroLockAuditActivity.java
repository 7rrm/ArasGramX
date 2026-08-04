package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;

import tw.nekomimi.nekogram.MeeroChatLock;

/**
 * MeeroX v110 (user-picked feature 4): unlock-attempt audit log
 * ("سجل محاولات الفتح").
 *
 * Lists the newest attempts recorded by {@link MeeroChatLock#recordAudit}:
 * when (relative time), where (locked chat / hidden vault / lock settings)
 * and the result (success or wrong code). Local-only data capped at the
 * engine's AUDIT_LIMIT, with a one-tap clear behind a confirmation. The
 * screen itself lives INSIDE the gated lock section, so reaching it already
 * means the owner unlocked the section first.
 */
public class MeeroLockAuditActivity extends BaseNekoSettingsActivity {

    private int listStartRow;
    private int listEndRow;
    private int emptyRow;
    private int clearRow;
    private int infoRow;

    private JSONArray entries = new JSONArray();

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroLockAudit);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        entries = MeeroChatLock.auditEntries();
        listStartRow = rowCount;
        for (int i = 0; i < entries.length(); i++) addRow();
        listEndRow = rowCount;
        emptyRow = entries.length() == 0 ? addRow() : -1;
        clearRow = entries.length() > 0 ? addRow() : -1;
        infoRow = addRow();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private String placeOf(JSONObject o) {
        int p = o.optInt("p", MeeroChatLock.AUDIT_CHAT);
        if (p == MeeroChatLock.AUDIT_VAULT) return getString(R.string.MeeroAuditVault);
        if (p == MeeroChatLock.AUDIT_SETTINGS) return getString(R.string.MeeroAuditSettings);
        return getString(R.string.MeeroAuditChat);
    }

    private String detailOf(JSONObject o) {
        long t = o.optLong("t", 0L);
        boolean ok = o.optBoolean("ok", false);
        CharSequence when = t > 0
                ? DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                : "";
        String result = getString(ok ? R.string.MeeroAuditSuccess : R.string.MeeroAuditFailed);
        return when + " · " + result;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == clearRow) {
            Context context = getParentActivity();
            if (context == null) return;
            new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.MeeroLockAuditClear))
                    .setMessage(getString(R.string.MeeroLockAuditClearConfirm))
                    .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                        MeeroChatLock.clearAudit();
                        BulletinFactory.of(this)
                                .createSimpleBulletin(R.raw.contact_check, getString(R.string.MeeroLockAuditCleared))
                                .show();
                        updateRows();
                        if (listAdapter != null) {
                            listAdapter.notifyDataSetChanged();
                        }
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
            // audit entries are display-only; only the clear row acts.
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroLockAuditEmpty), "", false);
                    } else if (position == clearRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroLockAuditClear), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= listStartRow && position < listEndRow) {
                        JSONObject o = entries.optJSONObject(position - listStartRow);
                        if (o != null) {
                            detailCell.setTextAndValue(placeOf(o), detailOf(o), position + 1 < listEndRow);
                        }
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroLockAuditInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == emptyRow || position == clearRow) {
                return TYPE_TEXT;
            } else if (position >= listStartRow && position < listEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
