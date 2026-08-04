package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroQuickReply;
import tw.nekomimi.nekogram.MeeroUsageGuide;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v117: quick-reply templates manager.
 *
 * Lives inside the Auto-reply screen family. Rows: the master switch, an add
 * button, one row per saved template (tap = edit, the dialog's neutral
 * button = delete), and the v111-style usage-guide button. The engine and
 * the send-button popup are in {@link MeeroQuickReply} and
 * ChatActivityEnterView; the store is a small local JSON config string -
 * no network, no sending, templates only insert text for the user to send
 * themselves. Off = stock Telegram exactly.
 */
public class MeeroQuickReplyActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int addRow;
    private int infoRow;

    private ArrayList<MeeroQuickReply.Template> templates = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        templates = MeeroQuickReply.list();
        masterRow = addRow();
        addRow = addRow();
        for (int i = 0; i < templates.size(); i++) {
            addRow();
        }
        infoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroQuickReplyTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroQuickReply.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroQuickReply.Bool());
        } else if (position == addRow) {
            showTemplateEditor(null);
        } else if (position == infoRow) {
            MeeroUsageGuide.show(this, R.string.MeeroQuickReplyUsage);
        } else {
            int index = position - 2;
            if (index >= 0 && index < templates.size()) {
                showTemplateEditor(templates.get(index));
            }
        }
    }

    /** Add (null) or edit one template; the dialog's neutral button deletes. */
    private void showTemplateEditor(final MeeroQuickReply.Template editing) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        if (editing != null) {
            editText.setText(editing.text);
        }
        editText.setSelection(editText.getText().length());
        editText.setHint(getString(R.string.MeeroQuickReplyHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(editing == null ? getString(R.string.MeeroQuickReplyAdd) : getString(R.string.MeeroQuickReplyEditTitle))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    final String value = editText.getText().toString();
                    final boolean ok = editing == null ? MeeroQuickReply.add(value) : MeeroQuickReply.update(editing.id, value);
                    AndroidUtilities.makeAccessibilityAnnouncement(getString(ok ? R.string.MeeroQuickReplySaved : R.string.MeeroQuickReplyFull));
                    refreshRows();
                });
        if (editing != null) {
            builder.setNeutralButton(getString(R.string.Delete), (dialog, which) -> {
                MeeroQuickReply.delete(editing.id);
                AndroidUtilities.makeAccessibilityAnnouncement(getString(R.string.MeeroQuickReplyDeleted));
                refreshRows();
            });
        }
        builder.setNegativeButton(getString(R.string.Cancel), null)
                .create()
                .show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
    }

    private void refreshRows() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRows();
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
            if (holder.getItemViewType() == TYPE_CHECK) {
                if (position == masterRow) {
                    ((TextCheckCell) holder.itemView).setTextAndCheck(getString(R.string.meeroQuickReply), NekoConfig.meeroQuickReply.Bool(), true);
                }
            } else if (holder.getItemViewType() == TYPE_TEXT) {
                TextCell textCell = (TextCell) holder.itemView;
                if (position == addRow) {
                    textCell.setTextAndIcon(getString(R.string.MeeroQuickReplyAdd), R.drawable.deproko_baseline_text_add_24, true);
                } else if (position == infoRow) {
                    textCell.setTextAndValue(getString(R.string.MeeroUsageGuide), "", false);
                } else {
                    int index = position - 2;
                    if (index >= 0 && index < templates.size()) {
                        textCell.setTextAndValue(MeeroQuickReply.previewOf(templates.get(index).text), "", true);
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == masterRow) {
                return TYPE_CHECK;
            }
            return TYPE_TEXT;
        }
    }
}
