package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroStrings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroAutoReply;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v101: auto-reply exclusions.
 *
 * People on this list never receive the auto-reply at all - the engine gate
 * beats every rule, including a per-chat custom text rule. Removing a person
 * restores normal behavior instantly. Added via the stock dialogs picker
 * (onlySelect with a delegate, same pattern the rules screen uses).
 */
public class MeeroAutoReplyExclusionsActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int addRow;
    private int infoRow;
    private int exclusionStartRow;
    private int exclusionEndRow;

    private final ArrayList<Long> exclusions = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reloadExclusions();
        headerRow = addRow();
        addRow = addRow();
        exclusionStartRow = rowCount;
        for (int i = 0; i < exclusions.size(); i++) addRow();
        exclusionEndRow = rowCount;
        infoRow = addRow();
    }

    private void reloadExclusions() {
        exclusions.clear();
        exclusions.addAll(MeeroAutoReply.getExclusionIds());
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s(89);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String nameOf(long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        String name = user != null ? UserObject.getUserName(user) : null;
        return TextUtils.isEmpty(name) ? MeeroStrings.s(213) : name;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == addRow) {
            pickChat();
        } else if (position >= exclusionStartRow && position < exclusionEndRow) {
            long dialogId = exclusions.get(position - exclusionStartRow);
            showExclusionOptions(dialogId);
        }
    }

    private void pickChat() {
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("allowGlobalSearch", false);
        args.putBoolean("checkCanWrite", false);
        DialogsActivity activity = new DialogsActivity(args);
        activity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids != null && !dids.isEmpty()) {
                long dialogId = dids.get(0).dialogId;
                if (parentLayout != null) parentLayout.removeFragmentFromStack(fragment, true);
                if (!DialogObject.isUserDialog(dialogId)) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, MeeroStrings.s(219)).show();
                    return true;
                }
                MeeroAutoReply.addExclusion(dialogId);
                updateRows();
                listAdapter.notifyDataSetChanged();
                return true;
            }
            return false;
        });
        presentFragment(activity);
    }

    private void showExclusionOptions(long dialogId) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(nameOf(dialogId))
                .setItems(new CharSequence[]{MeeroStrings.s(87)}, (dialog, which) -> {
                    MeeroAutoReply.removeExclusion(dialogId);
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_TEXT || type == TYPE_DETAIL_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(MeeroStrings.s(89));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(MeeroStrings.s(84), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= exclusionStartRow && position < exclusionEndRow) {
                        long dialogId = exclusions.get(position - exclusionStartRow);
                        detailCell.setTextAndValue(nameOf(dialogId), MeeroStrings.s(88), position + 1 < exclusionEndRow);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(MeeroStrings.s(85));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == addRow) {
                return TYPE_TEXT;
            } else if (position >= exclusionStartRow && position < exclusionEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
