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
 * MeeroX v106/v107: chat lock management screen ("قفل المحادثات").
 *
 * Master switch, v107 unlock-method row (system biometric/device lock vs.
 * in-app 8-digit code - the flow makes sure a code exists before the code
 * method can be activated, and "change code" verifies the current one
 * first), stock dialogs picker for adding a chat (anything except Saved
 * Messages and the service account), and the locked list - tapping an entry
 * unlocks it (with confirmation, restoring notifications only if WE muted
 * it). Engine in {@link MeeroChatLock}.
 */
public class MeeroChatLockActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int methodRow;
    private int changeCodeRow;
    private int autoRelockRow;
    private int relockGraceRow;
    private int auditRow;
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
        methodRow = addRow();
        // v107: "change code" only makes sense while the code method is on.
        changeCodeRow = MeeroChatLock.getMethod() == MeeroChatLock.METHOD_CODE8 ? addRow() : -1;
        // v110: auto-relock switch; its delay row only exists while it is on.
        autoRelockRow = addRow();
        relockGraceRow = NekoConfig.meeroChatLockAutoRelock.Bool() ? addRow() : -1;
        auditRow = addRow();
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

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s(64);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        // MeeroX v109 (user-requested): the section itself is behind the same
        // secret it manages - a snooper on an unlocked phone cannot reach the
        // unlock-code change or the locked list. The gates attach only when a
        // secret actually exists (first-time setup stays reachable).
        if (MeeroChatLock.needsLockSettingsGate(context instanceof android.app.Activity ? (android.app.Activity) context : null)) {
            MeeroChatLock.attachLockSettingsGate(this);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        listAdapter.notifyDataSetChanged();
        MeeroChatLock.maybePromptLockSettings(this);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // v109: leaving the section relocks it - the next visit asks again.
        MeeroChatLock.lockLockSettings();
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
        return MeeroStrings.s(213);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroChatLock.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroChatLock.Bool());
        } else if (position == methodRow) {
            chooseMethod();
        } else if (position == changeCodeRow) {
            verifyThenChangeCode();
        } else if (position == autoRelockRow) {
            NekoConfig.meeroChatLockAutoRelock.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroChatLockAutoRelock.Bool());
            refreshRows(); // shows/hides the delay row
        } else if (position == relockGraceRow) {
            pickRelockGrace();
        } else if (position == auditRow) {
            presentFragment(new MeeroLockAuditActivity());
        } else if (position == infoRow) {
            // v111: the full no-recovery/behavior guide lives in this popup.
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, 52);
        } else if (position == addRow) {
            pickChat();
        } else if (position >= listStartRow && position < listEndRow) {
            long dialogId = locked.get(position - listStartRow);
            confirmUnlock(dialogId);
        }
    }

    // ---------------- v107: unlock method + 8-digit code flows ----------------

    /** Method chooser. The code method can only be activated once a code
     *  actually exists - otherwise the user would lock everything behind a
     *  secret that was never set. */
    private void chooseMethod() {
        Context context = getParentActivity();
        if (context == null) return;
        CharSequence[] items = {
                MeeroStrings.s(57),
                MeeroStrings.s(56)
        };
        new AlertDialog.Builder(context)
                .setTitle(MeeroStrings.s(55))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        MeeroChatLock.setMethod(MeeroChatLock.METHOD_SYSTEM);
                        refreshRows();
                    } else {
                        if (MeeroChatLock.hasCode()) {
                            MeeroChatLock.setMethod(MeeroChatLock.METHOD_CODE8);
                            refreshRows();
                        } else {
                            startSetCodeFlow();
                        }
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** v110: how long after leaving the app every Meero lock snaps shut again
     *  (instant / 1 minute / 5 minutes - matches GRACE_NOW/MIN1/MIN5). */
    private void pickRelockGrace() {
        Context context = getParentActivity();
        if (context == null) return;
        CharSequence[] items = {
                MeeroStrings.s(211),
                MeeroStrings.s(209),
                MeeroStrings.s(208)
        };
        new AlertDialog.Builder(context)
                .setTitle(MeeroStrings.s(210))
                .setItems(items, (dialog, which) -> {
                    NekoConfig.meeroChatLockRelockGrace.setConfigInt(which);
                    refreshRows();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Enter 8 digits -> confirm the same 8 digits -> save + switch method.
     *  v108: runs on the new full-screen code lock (boxes + keypad) attached
     *  right over this settings screen, not a dialog. */
    private void startSetCodeFlow() {
        MeeroChatLock.showCodeLockOver(this,
                MeeroStrings.s(62),
                MeeroStrings.s(63),
                MeeroStrings.s(41),
                new MeeroChatLock.CodeCallback() {
                    @Override
                    public boolean onCode(final String first) {
                        // cover removes itself on true - chain the confirm
                        // screen right behind it.
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> promptConfirmCode(first), 220);
                        return true;
                    }

                    @Override
                    public void onCancelled() {}
                });
    }

    private void promptConfirmCode(final String first) {
        MeeroChatLock.showCodeLockOver(this,
                MeeroStrings.s(44),
                MeeroStrings.s(63),
                MeeroStrings.s(41),
                new MeeroChatLock.CodeCallback() {
                    @Override
                    public boolean onCode(String second) {
                        if (!first.equals(second)) {
                            return false; // red flash + shake, stays open
                        }
                        MeeroChatLock.setCode(second);
                        MeeroChatLock.setMethod(MeeroChatLock.METHOD_CODE8);
                        BulletinFactory.of(MeeroChatLockActivity.this)
                                .createSimpleBulletin(R.raw.contact_check, MeeroStrings.s(42))
                                .show();
                        refreshRows();
                        return true;
                    }

                    @Override
                    public void onCancelled() {}
                });
    }

    /** Change code = prove the current one first, then the set flow again. */
    private void verifyThenChangeCode() {
        MeeroChatLock.showCodeLockOver(this,
                MeeroStrings.s(40),
                MeeroStrings.s(47),
                MeeroStrings.s(43),
                new MeeroChatLock.CodeCallback() {
                    @Override
                    public boolean onCode(String code) {
                        if (!MeeroChatLock.verifyCode(code)) {
                            return false; // red flash + shake, stays open
                        }
                        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> startSetCodeFlow(), 220);
                        return true;
                    }

                    @Override
                    public void onCancelled() {}
                });
    }

    private void refreshRows() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, MeeroStrings.s(53)).show();
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
                .setTitle(MeeroStrings.s(59))
                .setMessage(MeeroStrings.f(60, titleOf(dialogId)))
                .setPositiveButton(MeeroStrings.s(59), (dialog, which) -> {
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
                        checkCell.setTextAndCheck(MeeroStrings.s(54), NekoConfig.meeroChatLock.Bool(), true);
                    } else if (position == autoRelockRow) {
                        checkCell.setTextAndCheck(MeeroStrings.s(17), NekoConfig.meeroChatLockAutoRelock.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(MeeroStrings.s(51));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == methodRow) {
                        // v107: shows the active unlock method, taps open the chooser.
                        textCell.setTextAndValue(MeeroStrings.s(55),
                                (MeeroChatLock.getMethod() == MeeroChatLock.METHOD_CODE8
                                        ? MeeroStrings.s(56) : MeeroStrings.s(57)), true);
                    } else if (position == changeCodeRow) {
                        textCell.setTextAndValue(MeeroStrings.s(40), "", true);
                    } else if (position == relockGraceRow) {
                        int g = NekoConfig.meeroChatLockRelockGrace.Int();
                        textCell.setTextAndValue(MeeroStrings.s(210),
                                (g == MeeroChatLock.GRACE_MIN1 ? MeeroStrings.s(209)
                                        : g == MeeroChatLock.GRACE_MIN5 ? MeeroStrings.s(208)
                                        : MeeroStrings.s(211)), true);
                    } else if (position == auditRow) {
                        textCell.setTextAndValue(MeeroStrings.s(164),
                                String.valueOf(MeeroChatLock.auditEntries().length()), true);
                    } else if (position == addRow) {
                        textCell.setTextAndValue(MeeroStrings.s(39), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(MeeroStrings.s(45), "", true);
                    } else if (position == infoRow) {
                        // v111: usage-guide button instead of the long footer.
                        textCell.setTextAndValue(MeeroStrings.s(268), "", false);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= listStartRow && position < listEndRow) {
                        long dialogId = locked.get(position - listStartRow);
                        detailCell.setTextAndValue(titleOf(dialogId), MeeroStrings.s(61), position + 1 < listEndRow);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow || position == autoRelockRow) {
                return TYPE_CHECK;
            } else if (position == headerRow) {
                return TYPE_HEADER;
            } else if (position == methodRow || position == changeCodeRow || position == relockGraceRow
                    || position == auditRow || position == addRow || position == emptyRow || position == infoRow) {
                return TYPE_TEXT;
            } else if (position >= listStartRow && position < listEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
