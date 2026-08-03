package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import tw.nekomimi.nekogram.MeeroActivityStats;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * MeeroX v102: activity details screen (read-only stats).
 *
 * Overview counts (today / 7d / 30d / total), app-open counter since v102,
 * a top-10 table of the most chatted private dialogs, and the user's busiest
 * hours. Data comes from the locally stored message database via
 * {@link MeeroActivityStats} - nothing ever leaves the device.
 */
public class MeeroActivityStatsActivity extends BaseNekoSettingsActivity {

    private int overviewHeaderRow;
    private int todayRow;
    private int weekRow;
    private int monthRow;
    private int totalRow;
    private int opensRow;
    private int topHeaderRow;
    private int topStartRow;
    private int topEndRow;
    private int hoursHeaderRow;
    private int hourStartRow;
    private int hourEndRow;
    private int statusRow;
    private int infoRow;

    private MeeroActivityStats.Summary summary;
    private final int[] peakHours = new int[3];

    @Override
    protected void updateRows() {
        super.updateRows();
        overviewHeaderRow = addRow();
        todayRow = addRow();
        weekRow = addRow();
        monthRow = addRow();
        totalRow = addRow();
        opensRow = addRow();
        topHeaderRow = addRow();
        topStartRow = rowCount;
        int topCount = summary != null && summary.ready ? summary.top.size() : 0;
        for (int i = 0; i < topCount; i++) addRow();
        topEndRow = rowCount;
        computePeaks();
        hoursHeaderRow = addRow();
        hourStartRow = rowCount;
        boolean anyHour = summary != null && summary.ready && summary.hasHourly;
        for (int i = 0; i < (anyHour ? 3 : 0); i++) addRow();
        hourEndRow = rowCount;
        statusRow = (summary == null || !summary.ready) ? addRow() : -1;
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

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        // read-only screen
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            boolean ready = summary != null && summary.ready;
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == overviewHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsOverview));
                    } else if (position == topHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsTopHeader));
                    } else if (position == hoursHeaderRow) {
                        headerCell.setText(getString(R.string.MeeroStatsHoursHeader));
                    }
                    break;
                case TYPE_TEXT:
                    TextCell textCell = (TextCell) holder.itemView;
                    String NA = "…";
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
                    } else if (position >= topStartRow && position < topEndRow) {
                        int i = position - topStartRow;
                        MeeroActivityStats.TopChat tc = summary.top.get(i);
                        textCell.setTextAndValue(nameOf(tc.dialogId), countValue(tc.count), position + 1 < topEndRow);
                    } else if (position >= hourStartRow && position < hourEndRow) {
                        int i = position - hourStartRow;
                        int h = peakHours[i];
                        textCell.setTextAndValue(hourRange(h), countValue(summary.hourly[h]), position + 1 < hourEndRow);
                    }
                    break;
                case TYPE_INFO_PRIVACY:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    if (position == statusRow) {
                        cell.setText(getString(R.string.MeeroStatsLoading));
                    } else if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroStatsInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == overviewHeaderRow || position == topHeaderRow || position == hoursHeaderRow) {
                return TYPE_HEADER;
            } else if (position == statusRow || position == infoRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }
    }
}
