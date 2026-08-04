package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import tw.nekomimi.nekogram.MeeroBubbleStyles;
import tw.nekomimi.nekogram.MeeroTickStyles;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v124 - the modern selector sheet shared by the bubble-shape row and
 * the read-mark row. A real Telegram bottom sheet with a grip, a segmented
 * «Bubbles | Read marks» tab pill, a big live preview (incoming + outgoing
 * mini conversation drawn through the same drawPreview()/tick drawables the
 * chat screen uses) and a sideways-scrolling card rail. One tap applies the
 * style instantly - no OK button, the hero is always WYSIWYG.
 */
public final class MeeroPickerSheet {

    public static final int TAB_BUBBLES = 0;
    public static final int TAB_TICKS = 1;

    private MeeroPickerSheet() {
    }

    private static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    private static int blend(int base, int tint, float t) {
        float r = Color.red(base) + (Color.red(tint) - Color.red(base)) * t;
        float g = Color.green(base) + (Color.green(tint) - Color.green(base)) * t;
        float b = Color.blue(base) + (Color.blue(tint) - Color.blue(base)) * t;
        return Color.rgb((int) r, (int) g, (int) b);
    }

    private static int darken(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }

    private static GradientDrawable pill(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    public static void open(final Context context, final int firstTab, final Runnable onApply) {
        if (context == null) {
            return;
        }

        final BottomSheet sheet = new BottomSheet(context, false);
        final int[] tab = {firstTab};

        final int colSheet = Theme.getColor(Theme.key_dialogBackground);
        final int colInk = Theme.getColor(Theme.key_dialogTextBlack);
        final int colSub = Theme.getColor(Theme.key_dialogTextGray3);
        final int colAccent = Theme.getColor(Theme.key_dialogTextBlue);
        final int colSegTrack = Theme.getColor(Theme.key_windowBackgroundGray);
        final int colOut = Theme.getColor(Theme.key_chat_outBubble);
        final int colIn = Theme.getColor(Theme.key_chat_inBubble);

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), 0, dp(14), dp(14));

        // ---- grip ----
        final View grip = new View(context);
        grip.setBackground(pill(blend(colSheet, colSub, 0.35f), 3));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(38), dp(4.5f));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.topMargin = dp(8);
        glp.bottomMargin = dp(12);
        root.addView(grip, glp);

        // ---- segmented tab pill ----
        final TextView segL = new TextView(context);
        final TextView segR = new TextView(context);
        final LinearLayout seg = new LinearLayout(context);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(pill(colSegTrack, 13));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (TextView tv : new TextView[]{segL, segR}) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(10), dp(7), dp(10), dp(7));
            seg.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        segL.setText(getString(R.string.MeeroPickerTabBubbles));
        segR.setText(getString(R.string.MeeroPickerTabTicks));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(230), LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.bottomMargin = dp(10);
        root.addView(seg, slp);

        // ---- title + subtitle ----
        final TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTextColor(colInk);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        subtitle.setTextColor(colSub);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(getString(R.string.MeeroPickerLiveHint));
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subp.topMargin = dp(2);
        subp.bottomMargin = dp(12);
        root.addView(subtitle, subp);

        // ---- hero live preview ----
        final HeroView hero = new HeroView(context, colSheet, colAccent, colOut, colIn, colInk);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(118));
        hlp.bottomMargin = dp(12);
        root.addView(hero, hlp);

        // ---- cards rail ----
        final LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        final HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        // v125: force a plain left-to-right order so the styles read exactly
        // like the agreed mock (newest at the far right, always reachable),
        // and a fade edge advertises that more cards exist.
        scroller.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        cards.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        scroller.setHorizontalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(dp(24));
        scroller.addView(cards);
        root.addView(scroller, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- bottom hint ----
        final TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        hint.setTextColor(colSub);
        hint.setGravity(Gravity.CENTER);
        hint.setText(getString(R.string.MeeroPickerSwipeHint));
        LinearLayout.LayoutParams hil = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hil.topMargin = dp(8);
        root.addView(hint, hil);

        final Runnable refreshSeg = () -> {
            final boolean bubbles = tab[0] == TAB_BUBBLES;
            segL.setBackground(pill(bubbles ? colSheet : Color.TRANSPARENT, 10));
            segR.setBackground(pill(!bubbles ? colSheet : Color.TRANSPARENT, 10));
            segL.setTextColor(bubbles ? colInk : colSub);
            segR.setTextColor(!bubbles ? colInk : colSub);
            segL.getPaint().setFakeBoldText(bubbles);
            segR.getPaint().setFakeBoldText(!bubbles);
            title.setText(getString(bubbles ? R.string.MeeroBubbleStyle : R.string.MeeroTickStyle));
        };

        // Single-element holder: the card-tap lambda calls back into this
        // runnable, which javac disallows inside its own initializer.
        final Runnable[] rebuildCards = new Runnable[1];
        rebuildCards[0] = () -> {
            cards.removeAllViews();
            final boolean bubbles = tab[0] == TAB_BUBBLES;
            final int count = bubbles ? MeeroBubbleStyles.COUNT : MeeroTickStyles.COUNT;
            final int sel = bubbles ? NekoConfig.meeroBubbleStyle.Int() : NekoConfig.meeroTickStyle.Int();
            for (int i = 0; i < count; i++) {
                cards.addView(makeCard(context, bubbles, i, i == sel, colSheet, colInk, colSub, colAccent, colOut, v -> {
                    if (tab[0] == TAB_BUBBLES) {
                        NekoConfig.meeroBubbleStyle.setConfigInt(v);
                    } else {
                        NekoConfig.meeroTickStyle.setConfigInt(v);
                    }
                    if (onApply != null) {
                        onApply.run();
                    }
                    rebuildCards[0].run();
                    hero.refresh(tab[0], bubbles ? NekoConfig.meeroBubbleStyle.Int() : NekoConfig.meeroTickStyle.Int());
                }));
            }
            hero.refresh(tab[0], bubbles ? NekoConfig.meeroBubbleStyle.Int() : NekoConfig.meeroTickStyle.Int());
        };

        segL.setOnClickListener(v -> { tab[0] = TAB_BUBBLES; refreshSeg.run(); rebuildCards[0].run(); });
        segR.setOnClickListener(v -> { tab[0] = TAB_TICKS; refreshSeg.run(); rebuildCards[0].run(); });

        refreshSeg.run();
        rebuildCards[0].run();

        sheet.setCustomView(root);
        sheet.setBackgroundColor(colSheet);
        sheet.fixNavigationBar(colSheet);
        sheet.show();
    }

    private interface CardTap {
        void onTap(int style);
    }

    /** One rail card: live thumbnail + name (+ desc for bubbles) + check badge. */
    private static View makeCard(final Context context, final boolean bubbles, final int style, final boolean selected,
                                 int colSheet, int colInk, int colSub, int colAccent, int colOut, final CardTap tap) {
        final FrameLayout wrap = new FrameLayout(context);

        final LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        inner.setPadding(dp(8), dp(8), dp(8), dp(8));

        final GradientDrawable bg = pill(colSheet, 16);
        bg.setStroke(dp(selected ? 2f : 1.2f), selected ? colAccent : blend(colSheet, colSub, 0.25f));
        inner.setBackground(bg);

        if (bubbles) {
            final ImageView iv = new ImageView(context);
            iv.setImageDrawable(MeeroBubbleStyles.previewDrawable(style, colOut));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(74), dp(36));
            ip.bottomMargin = dp(4);
            inner.addView(iv, ip);
        } else {
            final FrameLayout icons = new FrameLayout(context);
            final ImageView single = new ImageView(context);
            single.setImageDrawable(MeeroSettingsActivity.tickStyleIcon(context, style, false));
            final ImageView second = new ImageView(context);
            second.setImageDrawable(MeeroSettingsActivity.tickStyleIcon(context, style, true));
            FrameLayout.LayoutParams l1 = new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.START | Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams l2 = new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.START | Gravity.CENTER_VERTICAL);
            l2.setMarginStart(dp(9));
            icons.addView(single, l1);
            icons.addView(second, l2);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(74), dp(36));
            ip.bottomMargin = dp(4);
            inner.addView(icons, ip);
        }

        final TextView name = new TextView(context);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        name.setTextColor(selected ? colAccent : colInk);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setText(bubbles ? MeeroSettingsActivity.bubbleStyleName(style) : MeeroSettingsActivity.tickStyleName(style));
        inner.addView(name, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));

        if (bubbles) {
            final TextView desc = new TextView(context);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9.5f);
            desc.setTextColor(colSub);
            desc.setGravity(Gravity.CENTER);
            desc.setMaxLines(2);
            desc.setText(MeeroSettingsActivity.bubbleStyleDesc(style));
            inner.addView(desc, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        final TextView badge = new TextView(context);
        badge.setText("✓");
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        badge.setTextColor(Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        final GradientDrawable bd = new GradientDrawable();
        bd.setShape(GradientDrawable.OVAL);
        bd.setColor(colAccent);
        badge.setBackground(bd);
        badge.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

        FrameLayout.LayoutParams ip2 = new FrameLayout.LayoutParams(dp(98), FrameLayout.LayoutParams.WRAP_CONTENT);
        ip2.setMargins(0, dp(7), 0, 0);
        wrap.addView(inner, ip2);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.START);
        bp.setMarginStart(0);
        wrap.addView(badge, bp);

        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.setMargins(dp(3), 0, dp(3), 0);
        wrap.setLayoutParams(wp);

        inner.setOnClickListener(v -> tap.onTap(style));
        return wrap;
    }

    /**
     * The big live preview: a tinted panel showing an incoming and an
     * outgoing mini message. In bubbles mode both use the candidate style; in
     * ticks mode the outgoing message wears the current bubble and carries
     * the candidate tick pair.
     */
    private static class HeroView extends View {
        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stripe = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final int colOut, colIn, colInk;
        private final Context ctx;
        private int mode = TAB_BUBBLES;
        private int style = MeeroBubbleStyles.IOS_OFFICIAL;
        private int tickStyle = 0;
        private Drawable tick1, tick2;

        HeroView(Context context, int colSheet, int colAccent, int colOut, int colIn, int colInk) {
            super(context);
            this.ctx = context;
            this.colOut = colOut;
            this.colIn = colIn;
            this.colInk = colInk;
            panel.setColor(blend(colSheet, colAccent, 0.13f));
            panel.setStyle(Paint.Style.FILL);
            stripe.setStyle(Paint.Style.FILL);
        }

        void refresh(int newMode, int sel) {
            mode = newMode;
            if (mode == TAB_BUBBLES) {
                style = sel;
            } else {
                tickStyle = sel;
                tick1 = MeeroSettingsActivity.tickStyleIcon(ctx, tickStyle, false);
                tick2 = MeeroSettingsActivity.tickStyleIcon(ctx, tickStyle, true);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth(), h = getHeight();
            rf.set(0, 0, w, h);
            canvas.drawRoundRect(rf, dp(18), dp(18), panel);

            // v125: more real - the pair is offset and sized like a live
            // conversation instead of two identical stadiums.
            final float bh = dp(50);
            final float inL = dp(14), inT = (h - bh) / 2f - dp(4);
            final float outR = w - dp(14);

            // incoming message
            final int bubbleStyle = mode == TAB_BUBBLES ? style : NekoConfig.meeroBubbleStyle.Int();
            final float inW = dp(92);
            MeeroBubbleStyles.drawPreview(canvas, bubbleStyle, true, inL, inT, inL + inW, inT + bh, colIn);
            stripes(canvas, inL + dp(15), inT, colIn);

            // outgoing message
            final float outW = dp(116);
            final float outL = outR - outW;
            MeeroBubbleStyles.drawPreview(canvas, bubbleStyle, false, outL, inT + dp(8), outR, inT + dp(8) + bh, colOut);
            stripes(canvas, outL + dp(14), inT + dp(8), colOut);

            if (mode == TAB_TICKS && tick1 != null && tick2 != null) {
                // the candidate tick pair, worn at the bubble's inner bottom
                final int s = dp(14);
                final int left = (int) (outR - dp(34));
                final int top = (int) (inT + dp(8) + bh - dp(20));
                tick2.setBounds(left, top, left + s, top + s);
                tick2.draw(canvas);
                tick1.setBounds(left + dp(8), top, left + dp(8) + s, top + s);
                tick1.draw(canvas);
            }
        }

        private void stripes(Canvas canvas, float l, float top, int fill) {
            stripe.setColor(darken(fill, 0.88f));
            rf.set(l, top + dp(11), l + dp(50), top + dp(15.5f));
            canvas.drawRoundRect(rf, dp(2.5f), dp(2.5f), stripe);
            rf.set(l, top + dp(21), l + dp(33), top + dp(25.5f));
            canvas.drawRoundRect(rf, dp(2.5f), dp(2.5f), stripe);
        }
    }
}
