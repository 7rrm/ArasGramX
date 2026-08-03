package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TimePicker;

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
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v99-v100: dedicated Auto-reply screen.
 *
 * Master switch (off by default - replies are sent under the user's name),
 * a live reply preview ({name} shown with a sample name), per-chat rules,
 * cooldown and send-delay pickers, and (v100) an optional reply time window
 * with start/end 24-hour pickers. v104 extends the window into "window pro":
 * a weekday multi-picker (unchecked days never reply) and an optional night
 * reply text that swaps in for the general text while the window gates pass.
 * Every piece is independent; when the master switch or the window is off
 * the behavior is exactly stock. The engine lives in {@link MeeroAutoReply}.
 */
public class MeeroAutoReplyActivity extends BaseNekoSettingsActivity {

    private int autoReplyRow;
    private int autoReplyInfoRow;
    private int contentHeaderRow;
    private int textRow;
    private int rulesRow;
    private int exclusionsRow;
    private int poolRow;
    private int emojiRow;
    private int timingHeaderRow;
    private int cooldownRow;
    private int delayRow;
    private int windowRow;
    private int windowStartRow;
    private int windowEndRow;
    private int windowDaysRow;
    private int nightTextOnRow;
    private int nightTextRow;
    private int windowInfoRow;
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
        exclusionsRow = addRow();
        poolRow = addRow();
        emojiRow = addRow();
        timingHeaderRow = addRow();
        cooldownRow = addRow();
        delayRow = addRow();
        windowRow = addRow();
        windowStartRow = addRow();
        windowEndRow = addRow();
        windowDaysRow = addRow();
        nightTextOnRow = addRow();
        nightTextRow = addRow();
        windowInfoRow = addRow();
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

    private String exclusionsValue() {
        int count = MeeroAutoReply.getExclusionCount();
        if (count == 0) return getString(R.string.MeeroExclusionsNone);
        String word = count == 1 ? getString(R.string.MeeroExclusionsWordOne) : getString(R.string.MeeroExclusionsWordMany);
        return String.format(Locale.US, "%d %s", count, word);
    }

    private String poolValue() {
        int count = MeeroAutoReply.getPoolCount();
        if (count == 0) return getString(R.string.MeeroPoolNone);
        String word = count == 1 ? getString(R.string.MeeroPoolWordOne) : getString(R.string.MeeroPoolWordMany);
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

    /** Minutes-of-day as 24-hour HH:mm (clear and language-neutral). */
    private String timeValue(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
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
        } else if (position == exclusionsRow) {
            presentFragment(new MeeroAutoReplyExclusionsActivity());
        } else if (position == poolRow) {
            presentFragment(new MeeroAutoReplyPoolActivity());
        } else if (position == emojiRow) {
            NekoConfig.meeroAutoReplyRandomEmoji.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroAutoReplyRandomEmoji.Bool());
        } else if (position == cooldownRow) {
            showCooldownPicker();
        } else if (position == delayRow) {
            showDelayPicker();
        } else if (position == windowRow) {
            NekoConfig.meeroAutoReplyWindow.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroAutoReplyWindow.Bool());
        } else if (position == windowStartRow) {
            showTimePicker(NekoConfig.meeroAutoReplyWindowStart, R.string.MeeroAutoReplyWindowStart, windowStartRow);
        } else if (position == windowEndRow) {
            showTimePicker(NekoConfig.meeroAutoReplyWindowEnd, R.string.MeeroAutoReplyWindowEnd, windowEndRow);
        } else if (position == windowDaysRow) {
            showDaysPicker();
        } else if (position == nightTextOnRow) {
            NekoConfig.meeroAutoReplyNightTextOn.toggleConfigBool();
            ((TextCheckCell) view).setChecked(NekoConfig.meeroAutoReplyNightTextOn.Bool());
        } else if (position == nightTextRow) {
            showNightTextEditor();
        }
    }

    /** Day name by engine bitmask index: 0 = Sunday ... 6 = Saturday. */
    private String dayName(int i) {
        switch (i) {
            case 0:
                return getString(R.string.MeeroDaySun);
            case 1:
                return getString(R.string.MeeroDayMon);
            case 2:
                return getString(R.string.MeeroDayTue);
            case 3:
                return getString(R.string.MeeroDayWed);
            case 4:
                return getString(R.string.MeeroDayThu);
            case 5:
                return getString(R.string.MeeroDayFri);
            default:
                return getString(R.string.MeeroDaySat);
        }
    }

    private String daysValue() {
        int mask = NekoConfig.meeroAutoReplyWindowDays.Int();
        if (mask == 127) return getString(R.string.MeeroAutoReplyWindowDaysAll);
        if (mask == 0) return getString(R.string.MeeroAutoReplyWindowDaysNone);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append(" - ");
                sb.append(dayName(i));
            }
        }
        return sb.toString();
    }

    /** Weekday multi-picker built from TextCheckCells for the native look. */
    private void showDaysPicker() {
        Context context = getParentActivity();
        if (context == null) return;
        int mask = NekoConfig.meeroAutoReplyWindowDays.Int();
        final boolean[] tmp = new boolean[7];
        for (int i = 0; i < 7; i++) tmp[i] = (mask & (1 << i)) != 0;
        android.widget.LinearLayout layout = new android.widget.LinearLayout(context);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (int i = 0; i < 7; i++) {
            TextCheckCell cell = new TextCheckCell(context);
            cell.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            cell.setTextAndCheck(dayName(i), tmp[i], i < 6);
            final int day = i;
            cell.setOnClickListener(v -> {
                tmp[day] = !tmp[day];
                cell.setChecked(tmp[day]);
            });
            layout.addView(cell);
        }
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.MeeroAutoReplyWindowDays))
                .setView(layout)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    int newMask = 0;
                    for (int i = 0; i < 7; i++) {
                        if (tmp[i]) newMask |= 1 << i;
                    }
                    NekoConfig.meeroAutoReplyWindowDays.setConfigInt(newMask);
                    listAdapter.notifyItemChanged(windowDaysRow);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private String nightTextPreview() {
        String value = NekoConfig.meeroAutoReplyNightText.String();
        if (TextUtils.isEmpty(value)) {
            return getString(R.string.MeeroNightTextEmpty);
        }
        return value.replace("{name}", getString(R.string.MeeroAutoReplySampleName));
    }

    /** Same editor widget as the general reply text, for the night variant. */
    private void showNightTextEditor() {
        Context context = getParentActivity();
        if (context == null) return;
        final EditText editText = new EditText(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
        editText.setText(NekoConfig.meeroAutoReplyNightText.String());
        editText.setSelection(editText.getText().length());
        editText.setHint(getString(R.string.MeeroNightTextHint));
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);
        container.addView(editText, lp);
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.MeeroNightText))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    NekoConfig.meeroAutoReplyNightText.setConfigString(editText.getText().toString().trim());
                    listAdapter.notifyItemChanged(nightTextRow);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create()
                .show();
        editText.post(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // The rules/exclusions/pool counts may change on the management screens.
        listAdapter.notifyItemChanged(rulesRow);
        listAdapter.notifyItemChanged(exclusionsRow);
        listAdapter.notifyItemChanged(poolRow);
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

    /** 24-hour time picker; stores minutes-of-day in the given config item. */
    private void showTimePicker(ConfigItem item, int titleRes, int row) {
        Context context = getParentActivity();
        if (context == null) return;
        int minutes = item.Int();
        final TimePicker picker = new TimePicker(context);
        picker.setIs24HourView(true);
        picker.setHour(minutes / 60);
        picker.setMinute(minutes % 60);
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
        container.addView(picker, lp);
        new AlertDialog.Builder(context)
                .setTitle(getString(titleRes))
                .setView(container)
                .setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                    item.setConfigInt(picker.getHour() * 60 + picker.getMinute());
                    listAdapter.notifyItemChanged(row);
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
                    } else if (position == emojiRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroRandomEmoji), NekoConfig.meeroAutoReplyRandomEmoji.Bool(), true);
                    } else if (position == windowRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroAutoReplyWindowTitle), NekoConfig.meeroAutoReplyWindow.Bool(), true);
                    } else if (position == nightTextOnRow) {
                        checkCell.setTextAndCheck(getString(R.string.MeeroNightTextOn), NekoConfig.meeroAutoReplyNightTextOn.Bool(), true);
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
                    } else if (position == nightTextRow) {
                        detailCell.setMultilineDetail(true);
                        detailCell.setTextAndValue(getString(R.string.MeeroNightText), nightTextPreview(), false);
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == rulesRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroRulesTitle), rulesValue(), true);
                    } else if (position == exclusionsRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroExclusionsTitle), exclusionsValue(), true);
                    } else if (position == poolRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroPoolTitle), poolValue(), true);
                    } else if (position == cooldownRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyCooldown), cooldownName(NekoConfig.meeroAutoReplyCooldown.Int()), true);
                    } else if (position == delayRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyDelay), delayName(NekoConfig.meeroAutoReplyDelay.Int()), true);
                    } else if (position == windowStartRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyWindowStart), timeValue(NekoConfig.meeroAutoReplyWindowStart.Int()), true);
                    } else if (position == windowEndRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyWindowEnd), timeValue(NekoConfig.meeroAutoReplyWindowEnd.Int()), true);
                    } else if (position == windowDaysRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroAutoReplyWindowDays), daysValue(), true);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == autoReplyInfoRow) {
                        cell.setText(getString(R.string.MeeroAutoReplyInfo));
                    } else if (position == windowInfoRow) {
                        cell.setText(getString(R.string.MeeroAutoReplyWindowInfo));
                    } else if (position == boundsInfoRow) {
                        cell.setText(getString(R.string.MeeroAutoReplyBounds));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == autoReplyRow || position == windowRow || position == emojiRow || position == nightTextOnRow) {
                return TYPE_CHECK;
            } else if (position == contentHeaderRow || position == timingHeaderRow) {
                return TYPE_HEADER;
            } else if (position == textRow || position == nightTextRow) {
                return TYPE_DETAIL_SETTINGS;
            } else if (position == rulesRow || position == exclusionsRow || position == poolRow || position == cooldownRow || position == delayRow
                    || position == windowStartRow || position == windowEndRow || position == windowDaysRow) {
                return TYPE_TEXT;
            }
            return TYPE_INFO_PRIVACY;
        }
    }
}
