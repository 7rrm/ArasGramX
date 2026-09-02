package tw.nekomimi.nekogram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.glass.GlassTabView;

/**
 * MeeroX v209 - real Telegram-iOS attach-sheet face (owner-approved preview,
 * built straight from the official Telegram-iOS sources).
 *
 * Source of truth (TelegramMessenger/Telegram-iOS, master):
 *  - submodules/AttachmentUI/Sources/AttachmentPanel.swift -> tab strip:
 *    30pt monochrome glyph + 10pt label, horizontal scrolling row rendered
 *    as a floating "glass" capsule; unselected = secondary gray, selected =
 *    accent blue.
 *  - submodules/TelegramUI/Images.xcassets/Chat/Attach Menu -> the glyph
 *    vector PDFs themselves, rasterized here to template PNGs
 *    (res/drawable-nodpi/meero_ios_attach_*.png), tinted at runtime by
 *    GlassTabView's own color filter so theme states stay identical.
 *
 * What this class does while meeroIosAttachPanel is ON:
 *  1) Turns the stock glass tab strip into a floating capsule (side/bottom
 *     margins) instead of the edge-fused bar.
 *  2) Swaps the six main tab icons to the genuine iOS glyphs (gallery,
 *     file, location, contact, poll, music) via tabAnim(); every other tab
 *     keeps its stock animated icon.
 *  3) Hangs an iOS grabber pill at the sheet's top; tapping it dismisses
 *     the sheet (all other sheet gestures stay stock).
 *
 * Guaranteed: every tap still flows through the stock RecyclerListView
 * item-click listener, so permission / premium / restriction guards are
 * byte-identical. Switch OFF = stock face untouched, and any failure inside
 * apply()/tabAnim() silently falls back to stock.
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

    /**
     * Maps the stock animated tab animation ids to the genuine iOS glyphs.
     * Called from ChatAttachAlert's buttons adapter for the six core tabs
     * only; anything unknown (or switch OFF) returns the stock value so the
     * default face is byte-identical.
     */
    public static GlassTabView.TabAnimation tabAnim(GlassTabView.TabAnimation src) {
        try {
            if (!isOn() || src == null) {
                return src;
            }
            switch (src) {
                case GALLERY:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_GALLERY;
                case FILES:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_FILE;
                case LOCATION:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_LOCATION;
                case CONTACTS:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_CONTACT;
                case POLL:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_POLL;
                case MUSIC:
                    return GlassTabView.TabAnimation.MEERO_ATTACH_MUSIC;
                default:
                    return src;
            }
        } catch (Throwable t) {
            return src;
        }
    }

    /** iOS-style grabber pill (36x5dp), tap = dismiss sheet. */
    private static final class MeeroGrabberView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MeeroGrabberView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float pw = dp(36), ph = dp(5);
            final float left = (getWidth() - pw) / 2f;
            final float top = (getHeight() - ph) / 2f;
            canvas.drawRoundRect(left, top, left + pw, top + ph, ph / 2f, ph / 2f, paint);
        }
    }

    public static void apply(final ChatAttachAlert alert, ViewGroup containerView,
                             final FrameLayout wrapper, final RecyclerListView recycler,
                             Theme.ResourcesProvider resourcesProvider) {
        try {
            if (!isOn() || alert == null || containerView == null || wrapper == null) {
                return;
            }
            final Context ctx = containerView.getContext();

            // 1) floating iOS capsule: detach the strip from the sheet edges.
            final ViewGroup.LayoutParams lp0 = wrapper.getLayoutParams();
            if (lp0 instanceof FrameLayout.LayoutParams) {
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) lp0;
                lp.leftMargin = dp(10);
                lp.rightMargin = dp(10);
                lp.bottomMargin = dp(10);
                wrapper.setLayoutParams(lp);
                wrapper.requestLayout();
            }

            // 2) iOS grabber pill at the very top of the sheet.
            int base;
            try {
                base = resourcesProvider != null
                        ? resourcesProvider.getColor(Theme.key_dialogTextGray2)
                        : 0;
            } catch (Throwable t) {
                base = 0;
            }
            if (base == 0) {
                base = 0xFF8E8E93;
            }
            final int pillColor = (base & 0x00FFFFFF) | (92 << 24);
            final View grabber = new MeeroGrabberView(ctx, pillColor);
            grabber.setOnClickListener(v -> {
                try {
                    alert.dismiss();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            });
            // v229: tagged so ChatAttachAlert's container onLayout re-parks
            // it on the SHEET top edge every pass (it was pinned to the
            // full-window top = floated inside the chat title «خط فنص
            // الأسم»). Gravity still centers it horizontally.
            grabber.setTag("meeroGrabber");
            containerView.addView(grabber, LayoutHelper.createFrame(200, 22,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));
        } catch (Throwable t) {
            // never let a cosmetic face crash the sheet; stock stays intact
            try {
                FileLog.e(t);
            } catch (Throwable ignore) {
            }
        }
    }
}
