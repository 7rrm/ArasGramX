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
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

import tw.nekomimi.nekogram.MeeroAutoReply;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v99: per-chat auto-reply rules.
 *
 * A rule swaps the global reply text for one private chat; every other
 * engine gate (private-only, freshness, cooldown, not-while-viewing) still
 * applies. Added via the stock dialogs picker (onlySelect with a delegate,
 * same pattern RegexFiltersSettingActivity uses).
 */
public class MeeroAutoReplyRulesActivity extends BaseNekoSettingsActivity {

    private int headerRow;
    private int addRow;
    private int infoRow;
    private int rulesStartRow;
    private int rulesEndRow;

    private final ArrayList<Long> rules = new ArrayList<>();

    @Override
    protected void updateRows() {
        super.updateRows();
        reloadRules();
        headerRow = addRow();
        addRow = addRow();
        rulesStartRow = rowCount;
        for (int i = 0; i < rules.size(); i++) addRow();
        rulesEndRow = rowCount;
        infoRow = addRow();
    }

    private void reloadRules() {
        rules.clear();
        rules.addAll(MeeroAutoReply.getRuleDialogIds());
    }

    // MeeroX v129: opt into the fixed glass design (chrome, cards,
    // mock switches, entrance stagger) via the shared support pass.
    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroRulesTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String nameOf(long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        String name = user != null ? UserObject.getUserName(user) : null;
        return TextUtils.isEmpty(name) ? getString(R.string.MeeroRulesChatFallback) : name;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == addRow) {
            pickChat();
        } else if (position >= rulesStartRow && position < rulesEndRow) {
            long dialogId = rules.get(position - rulesStartRow);
            showRuleOptions(dialogId);
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
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.MeeroRulesPickPrivate)).show();
                    return true;
                }
                editRuleText(dialogId, MeeroAutoReply.getRuleText(dialogId));
                return true;
            }
            return false;
        });
        presentFragment(activity);
    }

    private void showRuleOptions(long dialogId) {
        new AlertDialog.Builder(getParentActivity())
                .setTitle(nameOf(dialogId))
                .setItems(new CharSequence[]{getString(R.string.MeeroRulesEdit), getString(R.string.MeeroRulesDelete)}, (dialog, which) -> {
                    if (which == 0) {
                        editRuleText(dialogId, MeeroAutoReply.getRuleText(dialogId));
                    } else {
                        MeeroAutoReply.removeRule(dialogId);
                        updateRows();
                        listAdapter.notifyDataSetChanged();
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void editRuleText(long dialogId, String currentText) {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setText(currentText != null ? currentText : getString(R.string.MeeroAutoReplyDefaultText));
        editText.setSelection(editText.getText().length());
        editText.setHint(getString(R.string.MeeroAutoReplyTextHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(nameOf(dialogId));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                MeeroAutoReply.removeRule(dialogId);
            } else {
                MeeroAutoReply.upsertRule(dialogId, value);
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
            return type == TYPE_TEXT || type == TYPE_DETAIL_SETTINGS;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == headerRow) {
                        headerCell.setText(getString(R.string.MeeroRulesTitle));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == addRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroRulesAdd), "", true);
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position >= rulesStartRow && position < rulesEndRow) {
                        long dialogId = rules.get(position - rulesStartRow);
                        String text = MeeroAutoReply.getRuleText(dialogId);
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(nameOf(dialogId), text == null ? "" : text.replace("\n", " "), position + 1 < rulesEndRow);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroRulesInfo));
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
            } else if (position >= rulesStartRow && position < rulesEndRow) {
                return TYPE_DETAIL_SETTINGS;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
