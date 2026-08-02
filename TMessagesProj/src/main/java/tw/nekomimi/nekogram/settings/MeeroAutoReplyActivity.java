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
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v98: dedicated Auto-reply screen.
 *
 * Master switch (off by default - replies are sent under the user's name),
 * reply text editor with a {name} placeholder, per-chat cooldown and send
 * delay pickers. The engine itself lives in {@link tw.nekomimi.nekogram.MeeroAutoReply}.
 */
public class MeeroAutoReplyActivity extends BaseNekoSettingsActivity {

    private int autoReplyRow;
    private int autoReplyInfoRow;
    private int textRow;
    private int cooldownRow;
    private int delayRow;
    private int boundsInfoRow;

    private static final int[] COOLDOWN_MINUTES = {0, 5, 10, 30, 60};
    private static final int[] DELAY_SECONDS = {0, 3, 5, 10};

    @Override
    protected void updateRows() {
        super.updateRows();
        autoReplyRow = addRow();
        autoReplyInfoRow = addRow();
        textRow = addRow();
        cooldownRow = addRow();
        delayRow = addRow();
        boundsInfoRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroAutoReplyTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private String currentReplyText() {
        String value = NekoConfig.meeroAutoReplyText.String();
        if (TextUtils.isEmpty(value)) {
            value = getString(R.string.MeeroAutoReplyDefaultText);
        }
        return value;
    }

    private String replyTextPreview() {
        return currentReplyText().replace('\n', ' ');
    }

    private String cooldownName(int minutes) {
        switch (minutes) {
            case 0:
                return getString(R.string.MeeroCooldownEveryMessage);
            case 5:
                return getString(R.string.MeeroCooldown5);
            case 10:
                return getString(R.string.MeeroCooldown10);
            case 60:
                return getString(R.string.MeeroCooldown60);
            case 30:
            default:
                return getString(R.string.MeeroCooldown30);
        }
    }

    private String delayName(int seconds) {
        switch (seconds) {
            case 0:
                return getString(R.string.MeeroDelayInstant);
            case 3:
                return getString(R.string.MeeroDelay3);
            case 5:
                return getString(R.string.MeeroDelay5);
            case 10:
            default:
                return getString(R.string.MeeroDelay10);
        }
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == autoReplyRow) {
            NekoConfig.meeroAutoReply.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroAutoReply.Bool());
        } else if (position == textRow) {
            showTextEditor();
        } else if (position == cooldownRow) {
            showCooldownPicker();
        } else if (position == delayRow) {
            showDelayPicker();
        }
    }

    private void showTextEditor() {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setText(currentReplyText());
        editText.setSelection(editText.getText().length());
        editText.setHint(getString(R.string.MeeroAutoReplyTextHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.MeeroAutoReplyText));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            String value = editText.getText().toString().trim();
            // Store empty when it matches the localized default: future app
            // translations then follow the interface language for free.
            if (value.equals(getString(R.string.MeeroAutoReplyDefaultText))) {
                value = "";
            }
            NekoConfig.meeroAutoReplyText.setConfigString(value);
            listAdapter.notifyItemChanged(textRow);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        dialog.show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
    }

    private void showCooldownPicker() {
        int current = NekoConfig.meeroAutoReplyCooldown.Int();
        CharSequence[] names = new CharSequence[COOLDOWN_MINUTES.length];
        for (int i = 0; i < COOLDOWN_MINUTES.length; i++) {
            names[i] = cooldownName(COOLDOWN_MINUTES[i]) + (COOLDOWN_MINUTES[i] == current ? "  ✓" : "");
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.MeeroAutoReplyCooldown))
                .setItems(names, (dialog, which) -> {
                    NekoConfig.meeroAutoReplyCooldown.setConfigInt(COOLDOWN_MINUTES[which]);
                    listAdapter.notifyItemChanged(cooldownRow);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void showDelayPicker() {
        int current = NekoConfig.meeroAutoReplyDelay.Int();
        CharSequence[] names = new CharSequence[DELAY_SECONDS.length];
        for (int i = 0; i < DELAY_SECONDS.length; i++) {
            names[i] = delayName(DELAY_SECONDS[i]) + (DELAY_SECONDS[i] == current ? "  ✓" : "");
        }
        new AlertDialog.Builder(getParentActivity())
                .setTitle(getString(R.string.MeeroAutoReplyDelay))
                .setItems(names, (dialog, which) -> {
                    NekoConfig.meeroAutoReplyDelay.setConfigInt(DELAY_SECONDS[which]);
                    listAdapter.notifyItemChanged(delayRow);
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
                    if (position == autoReplyRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroAutoReplyTitle), NekoConfig.meeroAutoReply.Bool(), true);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == textRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyText), replyTextPreview(), true);
                    } else if (position == cooldownRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyCooldown), cooldownName(NekoConfig.meeroAutoReplyCooldown.Int()), true);
                    } else if (position == delayRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyDelay), delayName(NekoConfig.meeroAutoReplyDelay.Int()), true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == autoReplyInfoRow) {
                        cell.setText(getString(R.string.MeeroAutoReplyInfo));
                    } else if (position == boundsInfoRow) {
                        cell.setText(getString(R.string.MeeroAutoReplyBounds));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == autoReplyRow) {
                return TYPE_CHECK;
            } else if (position == textRow || position == cooldownRow || position == delayRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
