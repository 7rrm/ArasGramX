package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroChatLock;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v106: chat lock management screen ("قفل المحادثات").
 *
 * Master switch, stock dialogs picker for adding a chat (anything except
 * Saved Messages and the service account), and the locked list - tapping an
 * entry unlocks it (with confirmation, restoring notifications only if WE
 * muted it). Engine in {@link MeeroChatLock}.
 */
public class MeeroChatLockActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int headerRow;
    private int addRow;
    private int listStartRow;
    private int listEndRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<Long> locked = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        masterRow = addRow();
        headerRow = addRow();
        addRow = addRow();
        listStartRow = rowCount;
        for (int i = 0; i < locked.size(); i++) addRow();
        listEndRow = rowCount;
        emptyRow = locked.isEmpty() ? addRow() : -1;
        infoRow = addRow();
    }

    private void reload() {
        locked.clear();
        locked.addAll(MeeroChatLock.getLockedIds());
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroChatLockTitle);
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

    private String titleOf(long dialogId) {
        MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = mc.getUser(dialogId);
            String name = user != null ? UserObject.getUserName(user) : null;
            if (!TextUtils.isEmpty(name)) return name;
        } else {
            TLRPC.Chat chat = mc.getChat(-dialogId);
            if (chat != null && !TextUtils.isEmpty(chat.title)) return chat.title;
        }
        return getString(R.string.MeeroRulesChatFallback);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroChatLock.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroChatLock.Bool());
        } else if (position == addRow) {
            pickChat();
        } else if (position >= listStartRow && position < listEndRow) {
            long dialogId = locked.get(position - listStartRow);
            confirmUnlock(dialogId);
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
                if (dialogId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId() || dialogId == 777000) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroChatLockInvalid)).show();
                    return true;
                }
                MeeroChatLock.addLocked(UserConfig.selectedAccount, dialogId);
                updateRows();
                listAdapter.notifyDataSetChanged();
                return true;
            }
            return false;
        });
        presentFragment(activity);
    }

    private void confirmUnlock(final long dialogId) {
        Context context = getParentActivity();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.MeeroChatLockRemove))
                .setMessage(LocaleController.formatString(R.string.MeeroChatLockRemoveConfirm, titleOf(dialogId)))
                .setPositiveButton(getString(R.string.MeeroChatLockRemove), (dialog, which) -> {
                    MeeroChatLock.removeLocked(UserConfig.selectedAccount, dialogId);
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
            return type == TYPE_CHECK || type == TYPE_TEXT || type == TYPE_DETAIL_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_CHECK:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == masterRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroChatLockMaster), NekoConfig.meeroChatLock.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(getString(R.string.MeeroChatLockHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroChatLockAdd), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroChatLockEmpty), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= listStartRow && position < listEndRow) {
                        long dialogId = locked.get(position - listStartRow);
                        detailCell.setTextAndValue(titleOf(dialogId), getString(R.string.MeeroChatLockRowDetail), position + 1 < listEndRow);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroChatLockInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow) {
                return TYPE_CHECK;
            } else if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == addRow || position == emptyRow) {
                return TYPE_TEXT;
            } else if (position >= listStartRow && position < listEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
