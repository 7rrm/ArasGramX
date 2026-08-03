package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
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
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroAutoReply;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v103: random reply texts pool.
 *
 * Master switch + the text list. When on, each outgoing auto-reply picks one
 * random text from this list. A per-chat rule text always wins - the pool is
 * only the general reply. {name} resolves in pool texts exactly like the
 * normal reply text.
 */
public class MeeroAutoReplyPoolActivity extends BaseNekoSettingsActivity {

    private int masterRow;
    private int headerRow;
    private int addRow;
    private int poolStartRow;
    private int poolEndRow;
    private int emptyRow;
    private int infoRow;

    private final ArrayList<String> texts = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reload();
        masterRow = addRow();
        headerRow = addRow();
        addRow = addRow();
        poolStartRow = rowCount;
        for (int i = 0; i < texts.size(); i++) addRow();
        poolEndRow = rowCount;
        emptyRow = texts.isEmpty() ? addRow() : -1;
        infoRow = addRow();
    }

    private void reload() {
        texts.clear();
        texts.addAll(MeeroAutoReply.getPoolTexts());
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroPoolTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == masterRow) {
            NekoConfig.meeroAutoReplyPoolOn.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroAutoReplyPoolOn.Bool());
        } else if (position == addRow) {
            showTextEditor(-1, null);
        } else if (position >= poolStartRow && position < poolEndRow) {
            showTextOptions(position - poolStartRow);
        }
    }

    private void showTextOptions(int index) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(texts.get(index))
                .setItems(new CharSequence[]{getString(R.string.MeeroPoolEdit), getString(R.string.MeeroPoolDelete)}, (dialog, which) -> {
                    if (which == 0) {
                        showTextEditor(index, texts.get(index));
                    } else {
                        MeeroAutoReply.removePoolText(index);
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void showTextEditor(int index, String currentText) {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        if (currentText != null) {
            editText.setText(currentText);
            editText.setSelection(editText.getText().length());
        }
        editText.setHint(getString(R.string.MeeroAutoReplyTextHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.MeeroPoolAdd));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            if (TextUtils.isEmpty(value)) return;
            if (index >= 0) {
                MeeroAutoReply.setPoolText(index, value);
            } else {
                MeeroAutoReply.addPoolText(value);
            }
            updateRows();
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
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
                        checkCell.setTextAndCheck(getString(R.string.MeeroPoolMaster), NekoConfig.meeroAutoReplyPoolOn.Bool(), true);
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(getString(R.string.MeeroPoolTitle));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroPoolAdd), "", true);
                    } else if (position == emptyRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroPoolNone), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= poolStartRow && position < poolEndRow) {
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(texts.get(position - poolStartRow).replace("\n", " "), "", position + 1 < poolEndRow);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroPoolInfo));
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
            } else if (position >= poolStartRow && position < poolEndRow && poolEndRow > poolStartRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
