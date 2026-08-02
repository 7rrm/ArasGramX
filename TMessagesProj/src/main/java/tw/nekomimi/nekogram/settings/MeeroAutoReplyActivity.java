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

import java.util.Locale;

import tw.nekomimi.nekogram.MeeroAutoReply;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v99: dedicated Auto-reply screen.
 *
 * Master switch (off by default - replies are sent under the user's name),
 * a live reply preview ({name} shown with a sample name), per-chat rules,
 * cooldown and send-delay pickers. The engine lives in {@link MeeroAutoReply}.
 */
public class MeeroAutoReplyActivity extends BaseNekoSettingsActivity {

    private int autoReplyRow;
    private int autoReplyInfoRow;
    private int contentHeaderRow;
    private int textRow;
    private int rulesRow;
    private int timingHeaderRow;
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
        contentHeaderRow = addRow();
        textRow = addRow();
        rulesRow = addRow();
        timingHeaderRow = addRow();
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

    /** Live preview exactly as the other side receives it ({name} resolved). */
    private String replyTextPreview() {
        return currentReplyText().replace("{name}", getString(R.string.MeeroAutoReplySampleName));
    }

    private String rulesValue() {
        int count = MeeroAutoReply.getRuleCount();
        if (count == 0) return getString(R.string.MeeroRulesNone);
        String word = count == 1 ? getString(R.string.MeeroRulesWordOne) : getString(R.string.MeeroRulesWordMany);
        return String.format(Locale.US, "%d %s", count, word);
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
        } else if (position == rulesRow) {
            presentFragment(new MeeroAutoReplyRulesActivity());
        } else if (position == cooldownRow) {
            showCooldownPicker();
        } else if (position == delayRow) {
            showDelayPicker();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // The rules count may change on the management screen.
        listAdapter.notifyItemChanged(rulesRow);
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
            return type == TYPE_CHECK || type == TYPE_TEXT || type == TYPE_DETAIL_SETTINGS;
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
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == contentHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroRulesContentHeader));
                    } else if (position == timingHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroRulesTimingHeader));
                    }
                    break;
                case TYPE_DETAIL_SETTINGS:
                    TextDetailSettingsCell detailCell = (TextDetailSettingsCell) holder.itemView;
                    if (position == textRow) {
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(getString(R.string.MeeroAutoReplyText), replyTextPreview(), false);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == rulesRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroRulesTitle), rulesValue(), true);
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
            } else if (position == contentHeaderRow || position == timingHeaderRow) {
                return TYPE_HEADER;
            } else if (position == textRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == rulesRow || position == cooldownRow || position == delayRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
