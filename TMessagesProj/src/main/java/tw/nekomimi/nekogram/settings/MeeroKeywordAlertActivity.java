package tw.nekomimi.nekogram.settings;

import tw.nekomimi.nekogram.MeeroKeywordAlert;
import tw.nekomimi.nekogram.MeeroStrings;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class MeeroKeywordAlertActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int headerRow;
    private int addRow;
    private int entryStartRow;
    private int entryEndRow;
    private int emptyRow;
    
    // 📝 سجل التنبيهات
    private int logHeaderRow;
    private int logStartRow;
    private int logEndRow;
    private int emptyLogRow;
    private int clearLogRow;
    
    private int infoRow;

    private final ArrayList<MeeroKeywordAlert.Entry> entries = new ArrayList<>();
    private final ArrayList<MeeroKeywordAlert.LogItem> logItems = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        reloadLog();
        
        masterRow = addRow();
        headerRow = addRow();
        addRow = addRow();
        entryStartRow = rowCount;
        for (int i = 0; i < entries.size(); i++) addRow();
        entryEndRow = rowCount;
        emptyRow = entries.isEmpty() ? addRow() : -1;
        
        // 📝 سجل التنبيهات (بدون فاصل)
        logHeaderRow = addRow();
        logStartRow = rowCount;
        int logCount = logItems.size();
        for (int i = 0; i < logCount; i++) addRow();
        logEndRow = rowCount;
        emptyLogRow = logItems.isEmpty() ? addRow() : -1;
        clearLogRow = logItems.isEmpty() ? -1 : addRow();
        
        infoRow = addRow();
    }

    private void reload() {
        entries.clear();
        entries.addAll(MeeroKeywordAlert.getEntries());
    }

    private void reloadLog() {
        logItems.clear();
        logItems.addAll(MeeroKeywordAlert.getLog());
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return MeeroStrings.s(162);
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
        if (dialogId == 0) return MeeroStrings.s(156);
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

    private String timeOf(long timestamp) {
        return new SimpleDateFormat("HH:mm dd/MM", Locale.US).format(new Date(timestamp));
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroKeywordAlert.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroKeywordAlert.Bool());
        } else if (position == infoRow) {
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, 160);
        } else if (position == addRow) {
            showAddKindDialog();
        } else if (position == clearLogRow && clearLogRow >= 0) {
            new AlertDialog.Builder(getParentActivity())
                .setTitle(MeeroStrings.s("MeeroKeywordLogClear"))
                .setMessage(MeeroStrings.s("MeeroKeywordLogClearConfirm"))
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    MeeroKeywordAlert.clearLog();
                    updateRows();
                    listAdapter.notifyDataSetChanged();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
        } else if (position >= entryStartRow && position < entryEndRow) {
            MeeroKeywordAlert.Entry entry = entries.get(position - entryStartRow);
            showEntryOptions(entry);
        }
    }

    private void showAddKindDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        new AlertDialog.Builder(context)
                .setTitle(MeeroStrings.s(153))
                .setItems(new CharSequence[]{MeeroStrings.s(155), MeeroStrings.s(154)}, (dialog, which) -> {
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
        editText.setHint(MeeroStrings.s(163) + " (مثال: فلوس, اجتماع, عاجل)");
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        new AlertDialog.Builder(context)
                .setTitle(titleOf(dialogId))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    String words = editText.getText().toString().trim();
                    if (!TextUtils.isEmpty(words)) {
                        MeeroKeywordAlert.upsertEntry(dialogId, words);
                    } else {
                        MeeroKeywordAlert.removeEntry(dialogId);
                    }
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
                .setItems(new CharSequence[]{MeeroStrings.s(157), getString(R.string.Delete)}, (dialog, which) -> {
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
                        checkCell.setTextAndCheck(MeeroStrings.s(161), NekoConfig.meeroKeywordAlert.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(MeeroStrings.s(159));
                    } else if (position == logHeaderRow) {
                        headerCell.setText(MeeroStrings.s("MeeroKeywordLogHeader"));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(MeeroStrings.s(153), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(MeeroStrings.s(158), "", true);
                    } else if (position == emptyLogRow) {
                        textCell.setTextAndValue(MeeroStrings.s("MeeroKeywordLogEmpty"), "", true);
                    } else if (position == clearLogRow) {
                        textCell.setTextAndValue(MeeroStrings.s("MeeroKeywordLogClear"), "", true);
                    } else if (position == infoRow) {
                        textCell.setTextAndValue(MeeroStrings.s(268), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= entryStartRow && position < entryEndRow) {
                        MeeroKeywordAlert.Entry entry = entries.get(position - entryStartRow);
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(titleOf(entry.dialogId), entry.words, position + 1 < entryEndRow);
                    } else if (position >= logStartRow && position < logEndRow) {
                        MeeroKeywordAlert.LogItem item = logItems.get(position - logStartRow);
                        String title = MeeroStrings.f("MeeroKeywordLogEntryFormat", item.who, item.chat);
                        String detail = MeeroStrings.f("MeeroKeywordLogDetailFormat", item.matchedWord, timeOf(item.timestamp));
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(title, detail, position + 1 < logEndRow);
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow) {
                return TYPE_CHECK;
            } else if (position == headerRow || position == logHeaderRow) {
                return TYPE_HEADER;
            } else if (position == addRow || position == emptyRow || 
                       position == emptyLogRow || position == clearLogRow || position == infoRow) {
                return TYPE_TEXT;
            } else if (position >= entryStartRow && position < entryEndRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position >= logStartRow && position < logEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
