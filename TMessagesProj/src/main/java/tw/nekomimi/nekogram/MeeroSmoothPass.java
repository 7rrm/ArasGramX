package tw.nekomimi.nekogram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;

/**
 * MeeroX v164 (his two approved picks from preview-ideas-v164 - this is
 * the "🚀 حزمة السلاسة" smooth-start pack).
 *
 * One-shot pre-warm, run once per process shortly after the dialogs screen
 * first resumes, so three first-use moments start warm instead of paying
 * cold init at first touch:
 *
 *   1. First popup menu  - an off-screen ActionBarPopupWindowLayout with
 *      two real sub-items is measured and drawn into a throwaway bitmap, so
 *      the first real popup skips view construction, ripple creation and
 *      font warm-up, and starts straight on the 180 ms iOS animation.
 *   2. First chat open   - one off-screen ChatMessageCell is built and laid
 *      out, warming the cell class and its shared paint/path caches while
 *      the user is still on the dialogs screen.
 *   3. First dialogs swipe - {@link #warmListScroll} nudges each page's
 *      list one pixel down and back, forcing the extra-row fill and the
 *      blur invalidation pass to happen before the first real fling.
 *
 * The whole pack is gated on {@link NekoConfig#meeroSmoothPass} (default
 * ON). When OFF, {@link #warmOnce} returns before ANY work is scheduled,
 * so start-up stays byte-identical to v163. Every step is independently
 * try/caught: any failure degrades silently into the v163 cold path and
 * can never crash the launch.
 */
public final class MeeroSmoothPass {

    private MeeroSmoothPass() {
    }

    /** True once the pack has run (or been skipped for lacking a context). */
    private static boolean warmedOnce;

    /* v189 (batch 3C): the warm-up policy numbers (delay, off-screen sizes)
     * come from the sealed motion table (dom 'C'); literals = fallback. */
    private static volatile float[] pol;

    private static float sp(int i, float legacy) {
        float[] p = pol;
        if (p == null && MeeroCore.motionCore()) {
            p = MeeroCore.nSmoothPolicy();
            if (p != null && p.length == 5) pol = p; else p = null;
        }
        return p != null ? p[i] : legacy;
    }

    /**
     * Called from DialogsActivity.onResume. Returns true only on the first
     * call per process with the switch ON and a usable context; the caller
     * then posts {@link #warmListScroll} for its pages. Never throws.
     */
    public static boolean warmOnce(Context context) {
        if (warmedOnce || context == null) {
            return false;
        }
        final boolean on;
        try {
            on = NekoConfig.meeroSmoothPass.Bool();
        } catch (Throwable t) {
            return false;
        }
        if (!on) {
            return false;
        }
        warmedOnce = true;
        // Small delay: the home screen's own first frames come first, then
        // the warm-up runs while the user is still just looking at the list.
        AndroidUtilities.runOnUIThread(() -> {
            try {
                warmPopup(context);
            } catch (Throwable ignore) {
                // cold path, exactly as v163
            }
            try {
                warmChatCell(context);
            } catch (Throwable ignore) {
                // cold path, exactly as v163
            }
        }, (long) sp(0, 900f));
        return true;
    }

    /**
     * First-swipe warm-up: a 1px scroll down and back forces the layout of
     * one extra row plus the blur invalidation pass, so the first real fling
     * does not pay them. Invisible (1px net-zero), never throws.
     */
    public static void warmListScroll(RecyclerListView listView) {
        if (listView == null) {
            return;
        }
        try {
            listView.scrollBy(0, 1);
            listView.scrollBy(0, -1);
        } catch (Throwable ignore) {
            // cold path, exactly as v163
        }
    }

    /**
     * Off-screen popup frame with two genuine sub-items, fully measured and
     * drawn once into a discardable bitmap. This is the exact view family
     * the real menu inflates, so view construction, ripple creation, font
     * measuring and the nine-patch background decode are all paid here
     * instead of at first open.
     */
    private static void warmPopup(Context context) {
        final ActionBarPopupWindow.ActionBarPopupWindowLayout layout =
                new ActionBarPopupWindow.ActionBarPopupWindowLayout(context);
        final ActionBarMenuSubItem top = new ActionBarMenuSubItem(context, true, false);
        top.setTextAndIcon("MeeroX", R.drawable.msg_copy);
        layout.addView(top);
        final ActionBarMenuSubItem bottom = new ActionBarMenuSubItem(context, false, true);
        bottom.setTextAndIcon("MeeroX", R.drawable.msg_copy);
        layout.addView(bottom);

        final int w = AndroidUtilities.dp(sp(1, 220f));
        layout.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(sp(2, 148f)), View.MeasureSpec.AT_MOST));
        layout.layout(0, 0, layout.getMeasuredWidth(), layout.getMeasuredHeight());

        final Bitmap off = Bitmap.createBitmap(
                Math.max(1, layout.getMeasuredWidth()),
                Math.max(1, layout.getMeasuredHeight()),
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(off);
        layout.draw(canvas);
        canvas.setBitmap(null);
        off.recycle();
    }

    /**
     * Off-screen message cell: loads the cell class, its static/shared
     * resources and the measure-layout path used the moment a chat opens.
     * No message object is set - the body's own null guards keep this a
     * pure warm-up, and the outer try/catch is the final safety net.
     */
    private static void warmChatCell(Context context) {
        final ChatMessageCell cell = new ChatMessageCell(context, UserConfig.selectedAccount);
        final int w = AndroidUtilities.dp(sp(3, 360f));
        cell.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(sp(4, 120f)), View.MeasureSpec.EXACTLY));
        cell.layout(0, 0, w, AndroidUtilities.dp(sp(4, 120f)));
    }
}
