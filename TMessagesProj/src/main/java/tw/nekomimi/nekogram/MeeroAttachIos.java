package tw.nekomimi.nekogram;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

/**
 * MeeroX v208 - iOS face for the attach sheet (owner-approved preview A).
 *
 * What it does while meeroIosAttachPanel is ON: hides the stock glass tab
 * strip, hangs an iOS grabber pill at the sheet's top, and pins a grouped
 * iOS action card (colored squircle glyph + localized label + chevron +
 * hairlines) where the strip used to be. Every row dispatches through the
 * sheet's own stock listener via ChatAttachAlert.meeroProxyForAttachTag(),
 * so permissions / premium / restriction guards stay byte-identical. The
 * deep surfaces (full gallery grid, multi-select, photo editor, camera,
 * files/location tabs) are NOT touched. Switch OFF = stock, and any
 * failure inside apply() returns silently with stock intact.
 */
public final class MeeroAttachIos {

    private MeeroAttachIos() {
    }

    public static boolean isOn() {
        try {
            return NekoConfig.meeroIosAttachPanel.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    public static void apply(final ChatAttachAlert alert, ViewGroup containerView,
                             final FrameLayout wrapper, final RecyclerListView recycler,
                             Theme.ResourcesProvider resourcesProvider) {
        try {
            if (!isOn() || alert == null || containerView == null || wrapper == null || recycler == null) {
                return;
            }
            final Context ctx = wrapper.getContext();
            final boolean dark = Theme.isCurrentThemeDark();

            // 1) Dissolve the stock glass pill strip; my card inherits the
            //    wrapper's bottom anchor + the sheet's show/hide state
            //    machine for free.
            wrapper.setBackground(null);
            ViewGroup.LayoutParams lp = wrapper.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                wrapper.setLayoutParams(lp);
            }
            recycler.setVisibility(View.GONE);

            // 2) iOS grabber pill (36x5) at the sheet's top.
            View grabber = new View(ctx);
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(dark ? 0x59FFFFFF : 0x59000000);
            gd.setCornerRadius(dp(2.5f));
            grabber.setBackground(gd);
            containerView.addView(grabber, LayoutHelper.createFrame(36, 5, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 7, 0, 0));

            // 3) The grouped action card.
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(dark ? 0xF226262C : 0xFFF9F9FC);
            cardBg.setCornerRadius(dp(14));
            card.setBackground(cardBg);

            // tag, squircle color, glyph, label - iOS order: gallery, file,
            // location, poll, article, music, contact.
            final int[] tags = {1, 4, 6, 9, ChatAttachAlert.LAYOUT_TYPE_RICH, 3, 5};
            final int[] colors = {0xFFBF5AF2, 0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFF64D2FF, 0xFFFF375F, 0xFF98989D};
            final String[] glyphs = {"🖼", "📄", "📍", "📊", "🅰", "🎵", "👤"};
            final int[] labels = {R.string.ChatGallery, R.string.ChatDocument, R.string.ChatLocation, R.string.Poll, R.string.AttachArticle, R.string.AttachMusic, R.string.AttachContact};

            final int labelColor = Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider);
            for (int i = 0; i < tags.length; i++) {
                final int tag = tags[i];
                FrameLayout rowLayout = new FrameLayout(ctx);

                GradientDrawable sq = new GradientDrawable();
                sq.setColor(colors[i]);
                sq.setCornerRadius(dp(8));
                TextView glyph = new TextView(ctx);
                glyph.setText(glyphs[i]);
                glyph.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                glyph.setGravity(Gravity.CENTER);
                glyph.setBackground(sq);
                rowLayout.addView(glyph, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL | Gravity.START, 14, 0, 0, 0));

                TextView label = new TextView(ctx);
                label.setText(LocaleController.getString(labels[i]));
                label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                label.setTextColor(labelColor);
                label.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                label.setSingleLine(true);
                FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                llp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
                llp.setMarginStart(dp(58));
                label.setLayoutParams(llp);
                rowLayout.addView(label);

                TextView chevron = new TextView(ctx);
                chevron.setText("‹");
                chevron.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                chevron.setTextColor(dark ? 0xFF636366 : 0xFFC7C7CC);
                chevron.setGravity(Gravity.CENTER);
                rowLayout.addView(chevron, LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 12, 0));

                if (i + 1 < tags.length) {
                    View hair = new View(ctx);
                    hair.setBackgroundColor(dark ? 0x2EFFFFFF : 0x1F000000);
                    FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    hlp.gravity = Gravity.BOTTOM;
                    hlp.setMarginStart(dp(58));
                    hair.setLayoutParams(hlp);
                    rowLayout.addView(hair);
                }

                rowLayout.setBackground(Theme.getSelectorDrawable(false));
                rowLayout.setOnClickListener(v -> {
                    try {
                        RecyclerListView.OnItemClickListener listener = recycler.getOnItemClickListener();
                        View proxy = alert.meeroProxyForAttachTag(tag);
                        if (listener != null && proxy != null) {
                            listener.onItemClick(proxy, -1);
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                });
                card.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));
            }

            FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.leftMargin = dp(10);
            cardLp.rightMargin = dp(10);
            cardLp.bottomMargin = dp(2);
            card.setLayoutParams(cardLp);
            wrapper.addView(card);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
