package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ThemeActivity;

import tw.nekomimi.nekogram.MeeroHaptics;
import tw.nekomimi.nekogram.MeeroThemeMixer;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v161 (his pick: every mock feature except Quick Replies - this is
 * "صانع الثيمات"): the Theme Mixer screen. Three taste-level picks compose
 * into a real, shareable .attheme that Telegram lists beside the built-in
 * themes. Nothing here touches the user's current theme until "طبّق" is
 * pressed, and going back to any previous theme is one tap from Telegram's
 * regular theme screen (row provided below).
 */
public class MeeroThemeMixerActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_MIXER_PREVIEW = 100;

    private int previewRow;
    private int headerRow;
    private int accentRow;
    private int bgRow;
    private int inBubbleRow;
    private int applyRow;
    private int restoreRow;
    private int infoRow;

    private MixerPreviewView previewView;

    @Override
    protected void updateRows() {
        super.updateRows();
        previewRow = addRow();
        headerRow = addRow();
        accentRow = addRow();
        bgRow = addRow();
        inBubbleRow = addRow();
        applyRow = addRow();
        restoreRow = addRow();
        infoRow = addRow();
    }

    @Override
    protected boolean meeroGlassScreen() {
        return true;
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.MixerTitle);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private void afterPick() {
        if (previewView != null) previewView.invalidate();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == accentRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.MixerAccent))
                    .setItems(MeeroThemeMixer.accentNames(), (d, which) -> {
                        NekoConfig.meeroMixerAccent.setConfigInt(which);
                        afterPick();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (position == bgRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.MixerBackground))
                    .setItems(MeeroThemeMixer.backgroundNames(), (d, which) -> {
                        NekoConfig.meeroMixerBg.setConfigInt(which);
                        afterPick();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (position == inBubbleRow) {
            new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.MixerInBubble))
                    .setItems(MeeroThemeMixer.inBubbleNames(), (d, which) -> {
                        NekoConfig.meeroMixerInBubble.setConfigInt(which);
                        afterPick();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .show();
        } else if (position == applyRow) {
            final boolean ok = MeeroThemeMixer.apply();
            if (MeeroHaptics.enabled()) {
                MeeroHaptics.perform(view, ok ? MeeroHaptics.SUCCESS : MeeroHaptics.ERROR);
            }
            Toast.makeText(getContext(),
                    getString(ok ? R.string.MixerApplied : R.string.MixerFailed),
                    Toast.LENGTH_LONG).show();
        } else if (position == restoreRow) {
            presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        } else if (position == infoRow) {
            tw.nekomimi.nekogram.MeeroUsageGuide.show(this, R.string.MixerInfo);
        }
    }

    // ------------------------------------------------------------ preview

    /** Toy chat surface rendered with the current three picks. */
    private static class MixerPreviewView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        MixerPreviewView(Context context) {
            super(context);
        }

        private void rr(Canvas canvas, float l, float t, float r, float b, float rad, int color) {
            rect.set(l, t, r, b);
            paint.setColor(color);
            canvas.drawRoundRect(rect, rad, rad, paint);
        }

        private void line(Canvas canvas, float l, float t, float w, float h, int color) {
            rr(canvas, l, t, l + w, t + h, h / 2f, color);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(196));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final MeeroThemeMixer.Accent a = MeeroThemeMixer.accent();
            final MeeroThemeMixer.Background b = MeeroThemeMixer.background();
            final int inColor = MeeroThemeMixer.inBubbleColor();
            final int onAccent = ColorUtils.calculateLuminance(a.color) < 0.55f ? 0xFFFFFFFF : 0xFF000000;
            final int textP = b.light ? 0xFF000000 : 0xFFFFFFFF;
            final int textS = b.light ? 0x66000000 : 0x66FFFFFF;

            final float w = getWidth(), h = getHeight();
            final float m = dp(14);
            // backdrop card (the elevated surface the real screen will use)
            rr(canvas, m, dp(10), w - m, h - dp(6), dp(16), b.elev);
            // inner chat area
            rr(canvas, m + dp(6), dp(16) + dp(4), w - m - dp(6), h - dp(12), dp(12), b.bg);

            // app bar slice
            rr(canvas, m + dp(6), dp(20), w - m - dp(6), dp(20) + dp(26), dp(10), b.elev);
            paint.setColor(a.color);
            canvas.drawCircle(m + dp(22), dp(33), dp(6), paint);
            line(canvas, m + dp(34), dp(29), dp(90), dp(5), textP);
            line(canvas, m + dp(34), dp(38), dp(56), dp(4), textS);

            // incoming bubble (other side) with its lines
            rr(canvas, m + dp(12), dp(58), m + dp(150), dp(88), dp(10), inColor);
            line(canvas, m + dp(20), dp(66), dp(102), dp(5), textP);
            line(canvas, m + dp(20), dp(76), dp(64), dp(4), textS);

            // outgoing bubble in the accent with a white check-ish tick
            rr(canvas, w - m - dp(150), dp(96), w - m - dp(12), dp(126), dp(10), a.color);
            line(canvas, w - m - dp(140), dp(104), dp(102), dp(5), onAccent);
            line(canvas, w - m - dp(140), dp(114), dp(60), dp(4), ColorUtils.setAlphaComponent(onAccent, 179));

            // FAB
            paint.setColor(a.color);
            canvas.drawCircle(w - m - dp(28), h - dp(36), dp(14), paint);
            line(canvas, w - m - dp(35), h - dp(38), dp(14), dp(4), onAccent);
        }
    }

    // ------------------------------------------------------------ adapter

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_MIXER_PREVIEW) {
                previewView = new MixerPreviewView(mContext);
                previewView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(previewView);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            if (holder.getItemViewType() == TYPE_MIXER_PREVIEW) {
                ((MixerPreviewView) holder.itemView).invalidate();
                return;
            }
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(getString(R.string.MixerHeader));
                    break;
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == accentRow) {
                        cell.setTextAndValue(getString(R.string.MixerAccent), getString(MeeroThemeMixer.accent().nameRes), true);
                    } else if (position == bgRow) {
                        cell.setTextAndValue(getString(R.string.MixerBackground), getString(MeeroThemeMixer.background().nameRes), true);
                    } else if (position == inBubbleRow) {
                        cell.setTextAndValue(getString(R.string.MixerInBubble), MeeroThemeMixer.inBubbleNames()[Math.max(0, Math.min(3, NekoConfig.meeroMixerInBubble.Int()))], true);
                    } else if (position == applyRow) {
                        cell.setText(getString(R.string.MixerApply), false);
                        cell.setTextColor(MeeroThemeMixer.accent().color);
                    } else if (position == restoreRow) {
                        cell.setText(getString(R.string.MixerRestore), true);
                    } else if (position == infoRow) {
                        cell.setText(getString(R.string.MeeroUsageGuide), false);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == previewRow) return TYPE_MIXER_PREVIEW;
            if (position == headerRow) return TYPE_HEADER;
            if (position == accentRow || position == bgRow || position == inBubbleRow
                    || position == applyRow || position == restoreRow || position == infoRow) return TYPE_TEXT;
            return TYPE_INFO_PRIVACY;
        }
    }
}
