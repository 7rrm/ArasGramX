package tw.nekomimi.nekogram.ui.cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.helpers.TimeStringHelper;
import xyz.nextalone.nagram.NaConfig;

/**
 * MeeroX v218 (owner pick, AyuGram-parity request): inline circle strip
 * choosing the color of the trash icon drawn on deleted messages. Index 0
 * is the untouched legacy behaviour - the icon follows the time text paint.
 * The choice persists in NaConfig.deletedTrashColor; TimeStringHelper
 * rebuilds its cached span on change so the next message render picks it
 * up (recycled cells apply it as soon as they rebind).
 */
public class MeeroTrashColorCell extends LinearLayout {

    private static final int DEFAULT_COLOR = 0xFF8E8E93;

    private final ImageView trashPreview;
    private final CircleView[] circles = new CircleView[TimeStringHelper.MEERO_TRASH_COLORS.length];
    private final int[] labelIds = {
        R.string.meeroTrashColorDefault, R.string.meeroTrashColorRed,
        R.string.meeroTrashColorOrange, R.string.meeroTrashColorPink,
        R.string.meeroTrashColorFuchsia, R.string.meeroTrashColorPurple,
        R.string.meeroTrashColorIndigo, R.string.meeroTrashColorBlue
    };

    public MeeroTrashColorCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        final int hp = AndroidUtilities.dp(21);
        setPadding(hp, AndroidUtilities.dp(12), hp, AndroidUtilities.dp(14));
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(LocaleController.getString(R.string.meeroTrashColorTitle));
        titleRow.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        trashPreview = new ImageView(context);
        trashPreview.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.msg_delete_solar).mutate());
        titleRow.addView(trashPreview, LayoutHelper.createLinear(22, 22));
        addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(HORIZONTAL);
        strip.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LayoutParams stripLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        stripLp.topMargin = AndroidUtilities.dp(14);
        addView(strip, stripLp);

        for (int i = 0; i < circles.length; i++) {
            final int idx = i;
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            CircleView cv = new CircleView(context, i);
            cv.setOnClickListener(v -> {
                NaConfig.INSTANCE.getDeletedTrashColor().setConfigInt(idx);
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Throwable ignore) {}
                refreshSelection();
            });
            circles[i] = cv;
            FrameLayout wrap = new FrameLayout(context);
            wrap.addView(cv, new FrameLayout.LayoutParams(AndroidUtilities.dp(30), AndroidUtilities.dp(30), Gravity.CENTER));
            col.addView(wrap, new LayoutParams(AndroidUtilities.dp(34), AndroidUtilities.dp(34)));
            TextView lab = new TextView(context);
            lab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9);
            lab.setSingleLine(true);
            lab.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            lab.setText(LocaleController.getString(labelIds[i]));
            col.addView(lab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            strip.addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }

        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setText(LocaleController.getString(R.string.meeroTrashColorHint));
        addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 0, 10, 0, 0));

        refreshSelection();
    }

    public void refreshSelection() {
        int idx = NaConfig.INSTANCE.getDeletedTrashColor().Int();
        if (idx < 0 || idx >= circles.length) {
            idx = 0;
        }
        for (int i = 0; i < circles.length; i++) {
            if (circles[i] != null) {
                circles[i].setPicked(i == idx);
            }
        }
        final int c = idx == 0 ? DEFAULT_COLOR : TimeStringHelper.MEERO_TRASH_COLORS[idx];
        if (trashPreview.getDrawable() != null) {
            trashPreview.getDrawable().setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.SRC_IN));
        }
    }

    private static final class CircleView extends View {
        private final int idx;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean picked;

        CircleView(Context context, int idx) {
            super(context);
            this.idx = idx;
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(idx == 0 ? DEFAULT_COLOR : TimeStringHelper.MEERO_TRASH_COLORS[idx]);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(AndroidUtilities.dp(2.5f));
            ring.setColor(0xFFFFFFFF);
            glyph.setColor(0xFFFFFFFF);
            glyph.setStrokeWidth(AndroidUtilities.dp(1.6f));
            glyph.setStrokeCap(Paint.Cap.ROUND);
        }

        void setPicked(boolean p) {
            if (picked != p) {
                picked = p;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final float r = Math.min(cx, cy) - AndroidUtilities.dp(1.5f);
            if (picked) {
                canvas.drawCircle(cx, cy, r, ring);
                canvas.drawCircle(cx, cy, r - AndroidUtilities.dp(3.5f), fill);
            } else {
                canvas.drawCircle(cx, cy, r, fill);
            }
            if (idx == 0) {
                // "auto" glyph - two white horizontal lines inside the gray dot
                final float w = r * 0.5f;
                canvas.drawLine(cx - w, cy - AndroidUtilities.dp(2.4f), cx + w, cy - AndroidUtilities.dp(2.4f), glyph);
                canvas.drawLine(cx - w, cy + AndroidUtilities.dp(2.4f), cx + w, cy + AndroidUtilities.dp(2.4f), glyph);
            }
        }
    }
}
