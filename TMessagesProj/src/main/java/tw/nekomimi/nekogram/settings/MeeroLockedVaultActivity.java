package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.TopicsFragment;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroChatLock;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v107: hidden-chats vault ("المحادثات المخفية").
 *
 * The only way in is the bottom-bar chats long-press popup (user-approved:
 * bar_only), and only when chat lock is on with at least one locked chat.
 * The screen is covered by the same opaque gate as locked chats - one unlock
 * (system biometric/device lock or the in-app 8-digit code, per the chosen
 * method) opens it for the session; leaving the screen locks the vault again
 * ({@link MeeroChatLock#lockVault}). Tapping an entry opens the chat with its
 * own per-entry unlock already granted for this session
 * ({@link MeeroChatLock#markUnlocked}), which is instantly re-armed when the
 * chat is closed - so the vault is the single point of entry and the gate
 * rules stay unchanged.
 */
public class MeeroLockedVaultActivity extends BaseNekoSettingsActivity {

    private int listStartRow;
    private int listEndRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<Long> locked = new ArrayList<>();

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // Cover the vault before the prompt pops - the list behind must never
        // be readable while the gate is pending.
        if (MeeroChatLock.hasHiddenDialogs() && !MeeroChatLock.isVaultUnlocked() && view instanceof ViewGroup) {
            MeeroChatLock.attachGateCover((ViewGroup) view,
                    getString(R.string.MeeroVaultTitle),
                    getString(R.string.MeeroVaultGateHint),
                    () -> MeeroChatLock.maybePromptVault(this));
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        MeeroChatLock.maybePromptVault(this);
        updateRows();
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Vault locks the moment it is left - the next visit asks again.
        MeeroChatLock.lockVault();
    }

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        listStartRow = rowCount;
        for (int i = 0; i < locked.size(); i++) addRow();
        listEndRow = rowCount;
        emptyRow = locked.isEmpty() ? addRow() : -1;
        infoRow = addRow();
    }

    private void reload() {
        locked.clear();
        if (NekoConfig.meeroChatLock.Bool()) {
            locked.addAll(MeeroChatLock.getLockedIds());
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroVaultTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
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

    private String subtitleOf(long dialogId) {
        TLRPC.Dialog dialog = MessagesController.getInstance(UserConfig.selectedAccount).dialogs_dict.get(dialogId);
        int unread = dialog != null ? dialog.unread_count : 0;
        if (unread > 0) {
            return LocaleController.formatString(R.string.MeeroVaultUnread, unread);
        }
        return getString(R.string.MeeroChatLockRowDetail);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position >= listStartRow && position < listEndRow) {
            final long dialogId = locked.get(position - listStartRow);
            // Grant the per-entry unlock so the chat gate does not double-ask
            // right after the vault gate passed; the chat relocks on exit as
            // usual (ChatActivity.onFragmentDestroy -> lockAgain).
            MeeroChatLock.markUnlocked(dialogId);
            Bundle bundle = new Bundle();
            if (dialogId < 0) {
                bundle.putLong("chat_id", -dialogId);
                if (MessagesController.getInstance(UserConfig.selectedAccount).isForum(dialogId)) {
                    presentFragment(new TopicsFragment(bundle));
                } else {
                    presentFragment(new ChatActivity(bundle));
                }
            } else {
                bundle.putLong("user_id", dialogId);
                presentFragment(new ChatActivity(bundle));
            }
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_DETAIL_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= listStartRow && position < listEndRow) {
                        long dialogId = locked.get(position - listStartRow);
                        detailCell.setTextAndValue(titleOf(dialogId), subtitleOf(dialogId), position + 1 < listEndRow);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroVaultEmpty), "", false);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroVaultInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position >= listStartRow && position < listEndRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == emptyRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
