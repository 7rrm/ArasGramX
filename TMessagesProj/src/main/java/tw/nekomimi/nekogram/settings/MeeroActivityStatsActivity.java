package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.MeeroActivityStats;
import tw.nekomimi.nekogram.MeeroCards;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v102-v104: activity details screen (read-only stats).
 *
 * Overview counts (today / 7d / 30d / total), app-open counter since v102,
 * a top-10 table of the most chatted private dialogs, and the user's busiest
 * hours. Data comes from the locally stored message database via
 * {@link MeeroActivityStats} - nothing ever leaves the device.
 *
 * v104 "activity pro": adds a hand-drawn 24-hour bar chart of outgoing
 * messages, the quiet ("dry") chats block - private dialogs whose last
 * message is incoming, i.e. you still owe a reply - and one-tap export of
 * the whole report as shareable/copyable plain text.
 */
public class MeeroActivityStatsActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_CHART = 100;

    private int overviewHeaderRow;
    private int todayRow;
    private int weekRow;
    private int monthRow;
    private int totalRow;
    private int opensRow;
    private int proHeaderRow;
    private int chartRow;
    private int chartInfoRow;
    private int dryHeaderRow;
    private int dryCountRow;
    private int dryStartRow;
    private int dryEndRow;
    private int dryInfoRow;
    private int topHeaderRow;
    private int topStartRow;
    private int topEndRow;
    private int hoursHeaderRow;
    private int hourStartRow;
    private int hourEndRow;
    private int exportRow;
    private int statusRow;
    private int infoRow;

    private MeeroActivityStats.Summary summary;
    private final int[] peakHours = new int[3];

    @Override
    protected void updateRows() {
        super.updateRows();
        boolean ready = summary != null && summary.ready;
        overviewHeaderRow = addRow();
        todayRow = addRow();
        weekRow = addRow();
        monthRow = addRow();
        totalRow = addRow();
        opensRow = addRow();
        // --- activity pro ---
        proHeaderRow = addRow();
        chartRow = ready && summary.hasHourly ? addRow() : -1;
        chartInfoRow = chartRow >= 0 ? addRow() : -1;
        dryHeaderRow = addRow();
        dryCountRow = addRow();
        dryStartRow = rowCount;
        int dryShown = ready ? summary.dryTop.size() : 0;
        for (int i = 0; i < dryShown; i++) addRow();
        dryEndRow = rowCount;
        dryInfoRow = addRow();
        topHeaderRow = addRow();
        topStartRow = rowCount;
        int topCount = ready ? summary.top.size() : 0;
        for (int i = 0; i < topCount; i++) addRow();
        topEndRow = rowCount;
        computePeaks();
        hoursHeaderRow = addRow();
        hourStartRow = rowCount;
        boolean anyHour = ready && summary.hasHourly;
        for (int i = 0; i < (anyHour ? 3 : 0); i++) addRow();
        hourEndRow = rowCount;
        exportRow = addRow();
        statusRow = !ready ? addRow() : -1;
        infoRow = addRow();
    }

    private void computePeaks() {
        // greedy top-3 distinct hours, busiest first
        peakHours[0] = peakHours[1] = peakHours[2] = -1;
        if (summary == null || !summary.ready || !summary.hasHourly) return;
        boolean[] used = new boolean[24];
        for (int slot = 0; slot < 3; slot++) {
            int best = -1, bestVal = 0;
            for (int h = 0; h < 24; h++) {
                if (!used[h] && summary.hourly[h] > bestVal) {
                    bestVal = summary.hourly[h];
                    best = h;
                }
            }
            if (best < 0) break;
            used[best] = true;
            peakHours[slot] = best;
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MeeroStatsTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        MeeroActivityStats.compute(UserConfig.selectedAccount, result -> {
            if (getParentActivity() == null) return;
            summary = result;
            updateRows();
            listAdapter.notifyDataSetChanged();
        });
    }

    private String countValue(int count) {
        return count + " " + getString(R.string.MeeroStatsMsgWord);
    }

    private String opensValue() {
        int opens = NekoConfig.meeroStatsOpens.Int();
        int since = NekoConfig.meeroStatsSince.Int();
        if (since == 0) return "0";
        String date = new SimpleDateFormat("dd/MM", Locale.US).format(new Date(since * 1000L));
        return opens + "  (" + getString(R.string.MeeroStatsFrom) + " " + date + ")";
    }

    private String nameOf(long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        String name = user != null ? UserObject.getUserName(user) : null;
        return TextUtils.isEmpty(name) ? getString(R.string.MeeroRulesChatFallback) : name;
    }

    private String hourRange(int h) {
        return String.format(Locale.US, "%02d:00 – %02d:00", h, (h + 1) % 24);
    }

    /** How long this quiet chat has been waiting for your reply. */
    private String dryValue(long lastIncomingSec) {
        long ageSec = System.currentTimeMillis() / 1000L - lastIncomingSec;
        long days = ageSec / 86400L;
        if (days <= 0) return getString(R.string.MeeroStatsDryToday);
        if (days == 1) return getString(R.string.MeeroStatsDryYesterday);
        return LocaleController.formatString(R.string.MeeroStatsDryDays, days);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == exportRow) {
            showExportDialog();
        }
    }

    // ---------------- export ----------------

    private String buildReport() {
        boolean ready = summary != null && summary.ready;
        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCCA ").append(getString(R.string.MeeroStatsTitle)).append("\n");
        sb.append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date())).append("\n\n");
        sb.append(getString(R.string.MeeroStatsToday)).append(": ").append(ready ? String.valueOf(summary.today) : "0").append("\n");
        sb.append(getString(R.string.MeeroStatsWeek)).append(": ").append(ready ? String.valueOf(summary.week) : "0").append("\n");
        sb.append(getString(R.string.MeeroStatsMonth)).append(": ").append(ready ? String.valueOf(summary.month) : "0").append("\n");
        sb.append(getString(R.string.MeeroStatsTotal)).append(": ").append(ready ? String.valueOf(summary.total) : "0").append("\n");
        sb.append(getString(R.string.MeeroStatsOpens)).append(": ").append(NekoConfig.meeroStatsOpens.Int()).append("\n");
        sb.append(getString(R.string.MeeroStatsDryHeader)).append(": ").append(ready ? String.valueOf(summary.dryCount) : "0").append("\n\n");
        sb.append(getString(R.string.MeeroStatsTopHeader)).append(":\n");
        if (ready) {
            int i = 1;
            for (MeeroActivityStats.TopChat tc : summary.top) {
                sb.append(i++).append(". ").append(nameOf(tc.dialogId)).append(" - ").append(tc.count).append("\n");
            }
            sb.append("\n").append(getString(R.string.MeeroStatsHoursHeader)).append(":\n");
            for (int slot = 0; slot < 3; slot++) {
                int h = peakHours[slot];
                if (h < 0) break;
                sb.append("- ").append(hourRange(h)).append(" : ").append(summary.hourly[h]).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void showExportDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        final String report = buildReport();
        String preview = report.length() > 600 ? report.substring(0, 600) + "\u2026" : report;
        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.MeeroStatsExport))
                .setMessage(preview)
                .setPositiveButton(getString(R.string.MeeroStatsShare), (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, report);
                        context.startActivity(Intent.createChooser(intent, getString(R.string.MeeroStatsExport)));
                    } catch (Throwable ignore) {}
                })
                .setNeutralButton(getString(R.string.Copy), (dialog, which) -> {
                    AndroidUtilities.addToClipboard(report);
                    BulletinFactory.of(MeeroActivityStatsActivity.this)
                            .createSimpleBulletin(R.raw.copy, getString(R.string.TextCopied)).show();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    // ---------------- 24h bar chart view ----------------

    /** Hand-drawn hourly histogram, tinted with the app chart palette. */
    private static final class HourlyChartView extends View {

        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barMaxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int[] hours = new int[24];
        private int max;
        private int maxIdx = -1;

        HourlyChartView(Context context) {
            super(context);
            int accent = Theme.getColor(Theme.key_statisticChartLine_blue);
            barPaint.setColor(accent);
            barPaint.setAlpha(110);
            barMaxPaint.setColor(accent);
            textPaint.setColor(Theme.getColor(Theme.key_statisticChartSignature));
            textPaint.setTextSize(AndroidUtilities.dp(10));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setData(int[] hourly) {
            hours = hourly == null ? new int[24] : hourly;
            max = 0;
            maxIdx = -1;
            for (int i = 0; i < 24 && i < hours.length; i++) {
                if (hours[i] > max) {
                    max = hours[i];
                    maxIdx = i;
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = AndroidUtilities.dp(20);
            float right = getWidth() - AndroidUtilities.dp(20);
            float top = AndroidUtilities.dp(18);
            float tickBase = getHeight() - AndroidUtilities.dp(18);
            float bottom = tickBase - AndroidUtilities.dp(6);
            float usable = bottom - top;
            float step = (right - left) / 24f;
            float barW = step * 0.58f;
            for (int i = 0; i < 24; i++) {
                int v = i < hours.length ? hours[i] : 0;
                if (v <= 0 || max <= 0) continue;
                float bh = Math.max(usable * v / (float) max, AndroidUtilities.dp(2));
                float x = left + step * i + (step - barW) / 2f;
                Paint p = i == maxIdx ? barMaxPaint : barPaint;
                canvas.drawRoundRect(x, bottom - bh, x + barW, bottom, AndroidUtilities.dp(2), AndroidUtilities.dp(2), p);
            }
            // max value floats over the tallest bar
            if (maxIdx >= 0 && max > 0) {
                float x = left + step * maxIdx + step / 2f;
                canvas.drawText(String.valueOf(max), x, top - AndroidUtilities.dp(5), textPaint);
            }
            // hour ticks 0 / 6 / 12 / 18 / 23 keep the axis readable
            int[] ticks = {0, 6, 12, 18, 23};
            for (int t : ticks) {
                float x = left + step * t + step / 2f;
                canvas.drawText(String.format(Locale.US, "%02d", t), x, tickBase + AndroidUtilities.dp(10), textPaint);
            }
        }
    }

    // ---------------- adapter ----------------

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            // the screen stays read-only except the export row
            return holder.getAdapterPosition() == exportRow;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            if (viewType == TYPE_CHART) {
                HourlyChartView chart = new HourlyChartView(mContext);
                chart.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(160)));
                return new RecyclerListView.Holder(chart);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            boolean ready = summary != null && summary.ready;
            String NA = "\u2026";
            switch (holder.getItemViewType()) {
                case TYPE_CHART:
                    HourlyChartView chart = (HourlyChartView) holder.itemView;
                    if (ready) {
                        chart.setData(summary.hourly);
                    }
                    if (MeeroCards.enabled()) {
                        MeeroCards.attach(holder.itemView, new MeeroCards.CardDrawable(MeeroCards.POS_SINGLE, resourcesProvider));
                    }
                    break;
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == overviewHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsOverview));
                    } else if (position == proHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsProHeader));
                    } else if (position == dryHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsDryHeader));
                    } else if (position == topHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsTopHeader));
                    } else if (position == hoursHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsHoursHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    if (position == todayRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsToday), ready ? countValue(summary.today) : NA, true);
                    } else if (position == weekRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsWeek), ready ? countValue(summary.week) : NA, true);
                    } else if (position == monthRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsMonth), ready ? countValue(summary.month) : NA, true);
                    } else if (position == totalRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsTotal), ready ? countValue(summary.total) : NA, true);
                    } else if (position == opensRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsOpens), opensValue(), true);
                    } else if (position == dryCountRow) {
                        String value = ready ? summary.dryCount + " " + getString(R.string.MeeroStatsDryWord) : NA;
                        textCell.setTextAndValue(getString(R.string.MeeroStatsDryHeader), value, true);
                    } else if (position >= dryStartRow && position < dryEndRow && ready) {
                        int i = position - dryStartRow;
                        MeeroActivityStats.DryChat dc = summary.dryTop.get(i);
                        textCell.setTextAndValue(nameOf(dc.dialogId), dryValue(dc.lastIncomingSec), position + 1 < dryEndRow);
                    } else if (position >= topStartRow && position < topEndRow && ready) {
                        int i = position - topStartRow;
                        MeeroActivityStats.TopChat tc = summary.top.get(i);
                        textCell.setTextAndValue(nameOf(tc.dialogId), countValue(tc.count), position + 1 < topEndRow);
                    } else if (position >= hourStartRow && position < hourEndRow && ready) {
                        int i = position - hourStartRow;
                        int h = peakHours[i];
                        textCell.setTextAndValue(hourRange(h), countValue(summary.hourly[h]), position + 1 < hourEndRow);
                    } else if (position == exportRow) {
                        textCell.setTextAndValue(getString(R.string.MeeroStatsExport), "\uD83D\uDCE4", false);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == chartInfoRow) {
                        cell.setText(getString(R.string.MeeroStatsChartInfo));
                    } else if (position == dryInfoRow) {
                        cell.setText(getString(R.string.MeeroStatsDryInfo));
                    } else if (position == statusRow) {
                        cell.setText(getString(R.string.MeeroStatsLoading));
                    } else if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroStatsInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == chartRow && chartRow >= 0) {
                return TYPE_CHART;
            } else if (position == overviewHeaderRow || position == proHeaderRow || position == dryHeaderRow
                    || position == topHeaderRow || position == hoursHeaderRow) {
                return TYPE_HEADER;
            } else if (position == chartInfoRow || position == dryInfoRow || position == statusRow || position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }
    }
}
