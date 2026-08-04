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
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroKeywordAlert;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v105: keyword alert management screen ("منبه الكلمات").
 *
 * Master switch plus a list of keyword sets: each set is a chat picked with
 * the stock dialogs picker (or the global "all chats" entry) and a comma
 * separated word list. Tap a set to edit its words or remove it. Engine in
 * {@link MeeroKeywordAlert}.
 */
public class MeeroKeywordAlertActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int headerRow;
    private int addRow;
    private int entryStartRow;
    private int entryEndRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<MeeroKeywordAlert.Entry> entries = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        masterRow = addRow();
        headerRow = addRow();
        addRow = addRow();
        entryStartRow = rowCount;
        for (int i = 0; i < entries.size(); i++) addRow();
        entryEndRow = rowCount;
        emptyRow = entries.isEmpty() ? addRow() : -1;
        infoRow = addRow();
    }

    private void reload() {
        entries.clear();
        entries.addAll(MeeroKeywordAlert.getEntries());
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroKeywordTitle);
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
        if (dialogId == 0) return getString(R.string.MeeroKeywordAll);
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
            NekoConfig.meeroKeywordAlert.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroKeywordAlert.Bool());
        } else if (position == infoRow) {
            // v111: usage-guide popup instead of the long footer.
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, R.string.MeeroKeywordInfo);
        } else if (position == addRow) {
            showAddKindDialog();
        } else if (position >= entryStartRow && position < entryEndRow) {
            MeeroKeywordAlert.Entry entry = entries.get(position - entryStartRow);
            showEntryOptions(entry);
        }
    }

    private void showAddKindDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.MeeroKeywordAdd))
                .setItems(new CharSequence[]{getString(R.string.MeeroKeywordAddChat), getString(R.string.MeeroKeywordAddAll)}, (dialog, which) -> {
                    if (which == 0) {
                        pickChat();
                    } else {
                        showWordsEditor(0, "");
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
                showWordsEditor(dialogId, existingWords(dialogId));
                return true;
            }
            return false;
        });
        presentFragment(activity);
    }

    private String existingWords(long dialogId) {
        for (MeeroKeywordAlert.Entry e : entries) {
            if (e.dialogId == dialogId) return e.words;
        }
        return "";
    }

    /** The words editor: comma separated, applies to one dialog id (0 = all). */
    private void showWordsEditor(final long dialogId, String prefill) {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setText(prefill);
        editText.setSelection(editText.getText().length());
        editText.setHint(getString(R.string.MeeroKeywordWordsHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        new AlertDialog.Builder(context)
                .setTitle(titleOf(dialogId))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    MeeroKeywordAlert.upsertEntry(dialogId, editText.getText().toString());
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create()
                .show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
    }

    private void showEntryOptions(final MeeroKeywordAlert.Entry entry) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(titleOf(entry.dialogId))
                .setItems(new CharSequence[]{getString(R.string.MeeroKeywordEdit), getString(R.string.Delete)}, (dialog, which) -> {
                    if (which == 0) {
                        showWordsEditor(entry.dialogId, entry.words);
                    } else {
                        MeeroKeywordAlert.removeEntry(entry.dialogId);
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    }
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
                        checkCell.setTextAndCheck(getString(R.string.MeeroKeywordMaster), NekoConfig.meeroKeywordAlert.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(getString(R.string.MeeroKeywordHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroKeywordAdd), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroKeywordEmpty), "", true);
                    } else if (position == infoRow) {
                        // v111: usage-guide button instead of the long footer.
                        textCell.setTextAndValue(getString(R.string.MeeroUsageGuide), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= entryStartRow && position < entryEndRow) {
                        MeeroKeywordAlert.Entry entry = entries.get(position - entryStartRow);
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(titleOf(entry.dialogId), entry.words, position + 1 < entryEndRow);
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
            } else if (position == addRow || position == emptyRow || position == infoRow) {
                return TYPE_TEXT;
            } else if (position >= entryStartRow && position < entryEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
