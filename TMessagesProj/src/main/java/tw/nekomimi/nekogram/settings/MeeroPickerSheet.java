package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import tw.nekomimi.nekogram.MeeroBubbleStyles;
import tw.nekomimi.nekogram.MeeroGlassTheme;
import tw.nekomimi.nekogram.MeeroTickStyles;
import tw.nekomimi.nekogram.NekoConfig;

/**
 * MeeroX v124 - the modern selector sheet shared by the bubble-shape row and
 * the read-mark row. A real Telegram bottom sheet with a grip, a segmented
 * «Bubbles | Read marks» tab pill, a big LIVE preview and a
 * sideways-scrolling card rail. One tap applies the style instantly - no OK
 * button, the hero is always WYSIWYG.
 *
 * v131 rebuild (the user's request #2): no more drawn stripes pretending to
 * be text. The hero is now a real three-message conversation laid out with
 * StaticLayout, wearing the actual chat text/time colors, with the read
 * ticks sitting in the meta row exactly like a chat bubble, and the card
 * thumbnails are single real mini messages instead of empty outlines. The
 * bubbles themselves still route through MeeroBubbleStyles.drawPreview(), so
 * what you pick is what the chat draws.
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

    private static GradientDrawable pill(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    /** Tick pair tinted for the outgoing bubble's meta row (the chat colors). */
    private static Drawable[] metaTicks(Context context, int style, int tint) {
        if (style < 0 || style >= MeeroTickStyles.COUNT) {
            style = 0;
        }
        Drawable single = context.getResources().getDrawable(MeeroTickStyles.SINGLES[style]).mutate();
        single.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        Drawable second = context.getResources().getDrawable(MeeroTickStyles.SECONDS[style]).mutate();
        second.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        return new Drawable[]{single, second};
    }

    public static void open(final Context context, final int firstTab, final Runnable onApply) {
        if (context == null) {
            return;
        }

        final BottomSheet sheet = new BottomSheet(context, false);
        final int[] tab = {firstTab};

        // v128: with the glass skin on, the sheet wears the fixed MeeroX
        // palette like the settings screen (theme-proof, day/night only).
        // The hero bubbles stay on chat colors on purpose - they preview
        // what the chat itself looks like.
        final boolean glass = MeeroGlassTheme.enabled();
        final int colSheet = glass ? MeeroGlassTheme.sheetBg() : Theme.getColor(Theme.key_dialogBackground);
        final int colInk = glass ? MeeroGlassTheme.ink() : Theme.getColor(Theme.key_dialogTextBlack);
        final int colSub = glass ? MeeroGlassTheme.sub() : Theme.getColor(Theme.key_dialogTextGray3);
        final int colAccent = glass ? MeeroGlassTheme.ACC1 : Theme.getColor(Theme.key_dialogTextBlue);
        final int colSegTrack = glass ? MeeroGlassTheme.segTrack() : Theme.getColor(Theme.key_windowBackgroundGray);

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

        // ---- hero live conversation preview ----
        final HeroView hero = new HeroView(context, colSheet, colAccent);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(196));
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
                cards.addView(makeCard(context, bubbles, i, i == sel, colSheet, colInk, colSub, colAccent, v -> {
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

    /** One rail card: real mini message + name (+ desc for bubbles) + check badge. */
    private static View makeCard(final Context context, final boolean bubbles, final int style, final boolean selected,
                                 int colSheet, int colInk, int colSub, int colAccent, final CardTap tap) {
        final FrameLayout wrap = new FrameLayout(context);

        final LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        inner.setPadding(dp(8), dp(8), dp(8), dp(8));

        final GradientDrawable bg = pill(colSheet, 16);
        bg.setStroke(dp(selected ? 2f : 1.2f), selected ? colAccent : blend(colSheet, colSub, 0.25f));
        inner.setBackground(bg);

        if (bubbles) {
            // v131: a real single outgoing mini message in the card's style,
            // carrying its short greeting, time and the current read ticks.
            final MiniMessageView mini = new MiniMessageView(context, style);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(76), dp(48));
            ip.bottomMargin = dp(4);
            inner.addView(mini, ip);
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
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(76), dp(48));
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

        FrameLayout.LayoutParams ip2 = new FrameLayout.LayoutParams(dp(100), FrameLayout.LayoutParams.WRAP_CONTENT);
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
     * v131: the card thumbnail - ONE real outgoing mini message (text, time,
     * current read ticks) drawn inside the candidate bubble style, so the
     * rail reads like snippets of an actual chat instead of empty outlines.
     */
    private static class MiniMessageView extends View {
        private final int style;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final int colOut, colTime;
        private final String text;
        private final String time = "10:24";
        private Drawable tick1, tick2;

        MiniMessageView(Context context, int style) {
            super(context);
            this.style = style;
            this.text = getString(R.string.MeeroPickerCardMsg);
            this.colOut = Theme.getColor(Theme.key_chat_outBubble);
            this.colTime = Theme.getColor(Theme.key_chat_outTimeText);
            textPaint.setColor(Theme.getColor(Theme.key_chat_messageTextOut));
            textPaint.setTextSize(dp(9.5f));
            metaPaint.setColor(colTime);
            metaPaint.setTextSize(dp(6.5f));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (tick1 == null || tick2 == null) {
                Drawable[] t = metaTicks(getContext(), NekoConfig.meeroTickStyle.Int(), colTime);
                tick1 = t[0];
                tick2 = t[1];
            }
            final float w = getWidth(), h = getHeight();
            MeeroBubbleStyles.drawPreview(canvas, style, false, 0, dp(1.5f), w, h - dp(1.5f), colOut);

            // drawPreview keeps a 7dp tail allowance on the right; the text
            // block sits centered inside the body, above a tiny meta row.
            final float bodyR = w - dp(7);
            final float metaH = dp(7.5f);
            final float textBase = (h - metaH) / 2f + textPaint.getTextSize() * 0.38f;
            final float textW = textPaint.measureText(text);
            canvas.drawText(text, Math.max(dp(4), (bodyR - textW) / 2f - dp(2)), textBase, textPaint);

            final float timeW = metaPaint.measureText(time);
            final float tick = dp(6f);
            final float metaR = bodyR - dp(5);
            final float base = h - dp(6.5f);
            tick2.setBounds((int) (metaR - tick - dp(3) - tick), (int) (base - tick), (int) (metaR - tick - dp(3)), (int) base);
            tick2.draw(canvas);
            tick1.setBounds((int) (metaR - tick), (int) (base - tick), (int) metaR, (int) base);
            tick1.draw(canvas);
            canvas.drawText(time, metaR - tick - dp(3) - tick - dp(2.5f) - timeW, base - dp(1), metaPaint);
        }
    }

    /**
     * The big live preview: a REAL three-message conversation (text laid out
     * with StaticLayout, the chat's own message/time colors, the read ticks
     * in the meta row) playing a short pop-in stagger whenever a style is
     * tapped. In bubbles mode all three wear the candidate style; in ticks
     * mode the outgoing message carries the candidate tick pair.
     */
    private static class HeroView extends View {
        private static final long ANIM_DUR = 420L;
        private static final long STAGGER = 130L;

        private final Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final int colOut, colIn, colMsgOut, colMsgIn, colTimeOut, colTimeIn;
        private final Context ctx;
        private int mode = TAB_BUBBLES;
        private int style = MeeroBubbleStyles.IOS_OFFICIAL;
        private int tickStyle = 0;
        private Drawable tick1, tick2;
        private final Msg[] msgs = new Msg[3];
        private boolean layoutDirty = true;
        private long animStart = -1L;

        private static final class Msg {
            boolean outgoing;
            CharSequence text;
            String time;
            StaticLayout layout;
            float bw, bh, x, y;
        }

        HeroView(Context context, int colSheet, int colAccent) {
            super(context);
            this.ctx = context;
            this.colOut = Theme.getColor(Theme.key_chat_outBubble);
            this.colIn = Theme.getColor(Theme.key_chat_inBubble);
            this.colMsgOut = Theme.getColor(Theme.key_chat_messageTextOut);
            this.colMsgIn = Theme.getColor(Theme.key_chat_messageTextIn);
            this.colTimeOut = Theme.getColor(Theme.key_chat_outTimeText);
            this.colTimeIn = Theme.getColor(Theme.key_chat_inTimeText);
            panel.setColor(blend(colSheet, colAccent, 0.13f));
            panel.setStyle(Paint.Style.FILL);
            textPaint.setTextSize(dp(13.5f));
            metaPaint.setTextSize(dp(10.5f));

            // A small conversation that reads naturally in both locales:
            // greeting in, longer answer out, reaction back in.
            for (int i = 0; i < 3; i++) {
                msgs[i] = new Msg();
            }
            msgs[0].outgoing = false; msgs[0].text = getString(R.string.MeeroHeroMsg1); msgs[0].time = "10:24";
            msgs[1].outgoing = true;  msgs[1].text = getString(R.string.MeeroHeroMsg2); msgs[1].time = "10:25";
            msgs[2].outgoing = false; msgs[2].text = getString(R.string.MeeroHeroMsg3); msgs[2].time = "10:26";
        }

        void refresh(int newMode, int sel) {
            mode = newMode;
            if (mode == TAB_BUBBLES) {
                style = sel;
                // back on the bubbles tab the hero wears the CURRENT tick
                // config again; a stale candidate pair must not linger.
                tick1 = tick2 = null;
            } else {
                tickStyle = sel;
                Drawable[] t = metaTicks(ctx, tickStyle, colTimeOut);
                tick1 = t[0];
                tick2 = t[1];
            }
            animStart = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            layoutDirty = true;
        }

        private void rebuildLayout() {
            layoutDirty = false;
            final float w = getWidth(), h = getHeight();
            final int maxTextW = (int) (w * 0.58f);
            final float pad = dp(12);
            float cy = pad;
            final boolean rtl = LocaleController.isRTL;
            for (Msg m : msgs) {
                textPaint.setColor(m.outgoing ? colMsgOut : colMsgIn);
                m.layout = StaticLayout.Builder
                        .obtain(m.text, 0, m.text.length(), textPaint, maxTextW)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .build();
                float lineW = 0;
                for (int l = 0; l < m.layout.getLineCount(); l++) {
                    lineW = Math.max(lineW, m.layout.getLineWidth(l));
                }
                // Bubble width: text + side padding + the 7dp tail allowance
                // drawPreview() reserves on the outing side.
                m.bw = (float) Math.ceil(lineW) + dp(18) + dp(7);
                m.bh = m.layout.getHeight() + dp(14) + dp(13);
                final boolean rightSide = m.outgoing != rtl;
                m.x = rightSide ? w - pad - m.bw : pad;
                m.y = cy;
                cy += m.bh + dp(7);
            }
        }

        private static float easeOutBack(float t) {
            final float c1 = 1.70158f, c3 = c1 + 1f;
            return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth(), h = getHeight();
            rf.set(0, 0, w, h);
            canvas.drawRoundRect(rf, dp(18), dp(18), panel);
            if (layoutDirty) {
                rebuildLayout();
            }

            final long now = SystemClock.uptimeMillis();
            boolean animating = false;
            final int bubbleStyle = mode == TAB_BUBBLES ? style : NekoConfig.meeroBubbleStyle.Int();
            for (int i = 0; i < msgs.length; i++) {
                final Msg m = msgs[i];
                float p = 1f;
                if (animStart >= 0) {
                    p = Math.min(1f, Math.max(0f, (now - animStart - i * STAGGER) / (float) ANIM_DUR));
                    if (p < 1f) {
                        animating = true;
                    }
                }
                final float ease = easeOutBack(p);
                final boolean transformed = p < 1f;
                if (transformed) {
                    canvas.save();
                    final float cx = m.x + m.bw / 2f, cyy = m.y + m.bh / 2f;
                    final float s = 0.85f + 0.15f * ease;
                    canvas.scale(s, s, cx, cyy);
                }

                final int bubbleColor = m.outgoing ? colOut : colIn;
                if (transformed) {
                    // simple two-pass alpha: the whole message (bubble + text)
                    // fades in together by drawing into the same save-layer-free
                    // block - colors are pre-multiplied by drawAlpha here.
                    canvas.saveLayerAlpha(m.x - dp(8), m.y - dp(8), m.x + m.bw + dp(8), m.y + m.bh + dp(8),
                            (int) (255 * p));
                }
                MeeroBubbleStyles.drawPreview(canvas, bubbleStyle, !m.outgoing, m.x, m.y, m.x + m.bw, m.y + m.bh, bubbleColor);
                drawMessageContent(canvas, m);
                if (transformed) {
                    canvas.restore();
                    canvas.restore();
                }
            }
            if (animating) {
                postInvalidateOnAnimation();
            }
        }

        /** Text block + meta row (time, and ticks on outgoing) inside one bubble. */
        private void drawMessageContent(Canvas canvas, Msg m) {
            final float dpu = AndroidUtilities.density;
            // drawPreview body: incoming starts 7dp in; outgoing ends 7dp early.
            final float bodyL = m.outgoing ? m.x : m.x + 7f * dpu;
            final float bodyR = m.outgoing ? m.x + m.bw - 7f * dpu : m.x + m.bw;

            textPaint.setColor(m.outgoing ? colMsgOut : colMsgIn);
            metaPaint.setColor(m.outgoing ? colTimeOut : colTimeIn);

            final float textTop = m.y + (m.bh - dp(13) - m.layout.getHeight()) / 2f + dp(1);
            canvas.save();
            canvas.translate(bodyL + dp(9), textTop);
            m.layout.draw(canvas);
            canvas.restore();

            // meta row, hugging the bubble's inner bottom-end corner.
            final float timeW = metaPaint.measureText(m.time);
            final float tick = m.outgoing ? dp(11) : 0f;
            final float tickGap = m.outgoing ? dp(3) : 0f;
            // the two tick drawables overlap by a third, like the chat screen.
            final float ticksW = m.outgoing ? tick + dp(5) + tick : 0f;
            float metaR = bodyR - dp(9);
            final float baseline = m.y + m.bh - dp(7);
            if (m.outgoing && tick1 != null && tick2 != null) {
                tick2.setBounds((int) (metaR - ticksW), (int) (baseline - tick), (int) (metaR - ticksW + tick), (int) baseline);
                tick2.draw(canvas);
                tick1.setBounds((int) (metaR - tick), (int) (baseline - tick), (int) metaR, (int) baseline);
                tick1.draw(canvas);
            } else if (m.outgoing) {
                Drawable[] t = metaTicks(ctx, NekoConfig.meeroTickStyle.Int(), colTimeOut);
                tick1 = t[0];
                tick2 = t[1];
                tick2.setBounds((int) (metaR - ticksW), (int) (baseline - tick), (int) (metaR - ticksW + tick), (int) baseline);
                tick2.draw(canvas);
                tick1.setBounds((int) (metaR - tick), (int) (baseline - tick), (int) metaR, (int) baseline);
                tick1.draw(canvas);
            }
            canvas.drawText(m.time, metaR - ticksW - tickGap - timeW, baseline - dp(1.5f), metaPaint);
        }
    }
}
