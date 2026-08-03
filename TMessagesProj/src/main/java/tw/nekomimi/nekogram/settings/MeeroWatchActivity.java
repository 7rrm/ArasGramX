package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroWatch;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v102: account watching management screen.
 *
 * Master switch (off = nothing is watched, exact stock behavior). Every
 * watched person has his own ON/OFF check row - pause and resume any time.
 * Add from chats or by entering a username / id. Long-press a person to
 * remove him. The change log lives in {@link MeeroWatchLogActivity}.
 */
public class MeeroWatchActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int msgTrackRow;
    private int msgNotifyRow;
    private int watchHeaderRow;
    private int addRow;
    private int watchStartRow;
    private int watchEndRow;
    private int logRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<MeeroWatch.Entry> entries = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        masterRow = addRow();
        // v111: full person watch - tracking switch; its alert row only
        // exists while tracking is on.
        msgTrackRow = addRow();
        msgNotifyRow = NekoConfig.meeroWatchMsgTrack.Bool() ? addRow() : -1;
        watchHeaderRow = addRow();
        addRow = addRow();
        watchStartRow = rowCount;
        for (int i = 0; i < entries.size(); i++) addRow();
        watchEndRow = rowCount;
        emptyRow = entries.isEmpty() ? addRow() : -1;
        logRow = addRow();
        infoRow = addRow();
    }

    private void reload() {
        entries.clear();
        entries.addAll(MeeroWatch.getEntries());
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroWatchTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            try {
                listView.setOnItemLongClickListener((view, position, x, y) -> {
                    if (position >= watchStartRow && position < watchEndRow) {
                        showRemoveDialog(entries.get(position - watchStartRow).id);
                        return true;
                    }
                    return false;
                });
            } catch (Throwable ignore) {}
        }
    }

    private String nameOf(long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        String name = user != null ? UserObject.getUserName(user) : null;
        return TextUtils.isEmpty(name) ? getString(R.string.MeeroRulesChatFallback) : name;
    }

    private String handleOf(long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        if (user != null && !TextUtils.isEmpty(user.username)) return "@" + user.username;
        return "ID " + dialogId;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroWatchEnabled.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroWatchEnabled.Bool());
        } else if (position == msgTrackRow) {
            NekoConfig.meeroWatchMsgTrack.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroWatchMsgTrack.Bool());
            updateRows(); // shows/hides the alert row
            if (listAdapter != null) listAdapter.notifyDataSetChanged();
        } else if (position == msgNotifyRow) {
            NekoConfig.meeroWatchMsgNotify.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroWatchMsgNotify.Bool());
        } else if (position == infoRow) {
            // v111: the long explanation moved into a popup (user-requested).
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, R.string.MeeroWatchInfo);
        } else if (position == addRow) {
            showAddChooser();
        } else if (position >= watchStartRow && position < watchEndRow && position >= 0) {
            MeeroWatch.Entry e = entries.get(position - watchStartRow);
            MeeroWatch.setOn(e.id, !e.on);
            e.on = !e.on;
            ((TextCheckCell) view).setChecked(e.on);
        } else if (position == logRow) {
            presentFragment(new MeeroWatchLogActivity());
        }
    }

    private void showAddChooser() {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.MeeroWatchAdd))
                .setItems(new CharSequence[]{getString(R.string.MeeroWatchAddFromChats), getString(R.string.MeeroWatchAddByHandle)}, (dialog, which) -> {
                    if (which == 0) {
                        pickChat();
                    } else {
                        showHandleInput();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
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
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroRulesPickPrivate)).show();
                    return true;
                }
                addPerson(dialogId);
                return true;
            }
            return false;
        });
        presentFragment(activity);
    }

    private void showHandleInput() {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setHint(getString(R.string.MeeroWatchHandleHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.MeeroWatchAddByHandle));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.MeeroWatchAdd), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            if (value.startsWith("@")) value = value.substring(1);
            if (TextUtils.isEmpty(value)) return;
            if (AndroidUtilities.isNumeric(value)) {
                try {
                    long id = Long.parseLong(value);
                    if (id > 0 && MessagesController.getInstance(UserConfig.selectedAccount).getUser(id) != null) {
                        addPerson(id);
                    } else {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroWatchNotFound)).show();
                    }
                } catch (Throwable ignore) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroWatchNotFound)).show();
                }
            } else {
                MessagesController.getInstance(UserConfig.selectedAccount).getUserNameResolver().resolve(value, peerId -> {
                    if (peerId != null && peerId > 0) {
                        addPerson(peerId);
                    } else {
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroWatchNotFound)).show();
                    }
                });
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
    }

    private void addPerson(long dialogId) {
        if (!MeeroWatch.add(dialogId)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroWatchAlready)).show();
            return;
        }
        MeeroWatch.onAdded(dialogId);
        updateRows();
        listAdapter.notifyDataSetChanged();
        BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroWatchAdded)).show();
    }

    private void showRemoveDialog(long dialogId) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(nameOf(dialogId))
                .setItems(new CharSequence[]{getString(R.string.MeeroWatchRemove)}, (dialog, which) -> {
                    MeeroWatch.remove(dialogId);
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
            return type == TYPE_CHECK || type == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_CHECK:
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == masterRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroWatchTitle), NekoConfig.meeroWatchEnabled.Bool(), true);
                    } else if (position == msgTrackRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroWatchMsgTrack), NekoConfig.meeroWatchMsgTrack.Bool(), true);
                    } else if (position == msgNotifyRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroWatchMsgNotify), NekoConfig.meeroWatchMsgNotify.Bool(), true);
                    } else if (position >= watchStartRow && position < watchEndRow) {
                        MeeroWatch.Entry e = entries.get(position - watchStartRow);
                        checkCell.setTextAndValueAndCheck(nameOf(e.id), handleOf(e.id), e.on, true, true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == watchHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroWatchWatchedHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroWatchAdd), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroWatchNoOne), "", true);
                    } else if (position == logRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroWatchLogRow), "", true);
                    } else if (position == infoRow) {
                        // v111: usage-guide button replaces the long footer.
                        textCell.setTextAndValue(getString(R.string.MeeroUsageGuide), "", true);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow || position == msgTrackRow || position == msgNotifyRow
                    || (position >= watchStartRow && position < watchEndRow && watchEndRow > watchStartRow)) {
                return TYPE_CHECK;
            } else if (position == watchHeaderRow) {
                return TYPE_HEADER;
            } else if (position == addRow || position == emptyRow || position == logRow || position == infoRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
