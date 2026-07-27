package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

public class BottomPagesView extends View {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;
    private int scrollPosition;
    private int currentPage;
    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();
    private RectF rect = new RectF();
    private float animatedProgress;
    private ViewPager viewPager;
    private int pagesCount;

    private int colorKey = -1;
    private int selectedColorKey = -1;

    public BottomPagesView(Context context, ViewPager pager, int count) {
        super(context);
        viewPager = pager;
        pagesCount = count;
    }

    public void setPageOffset(int position, float offset) {
        progress = offset;
        scrollPosition = position;
        invalidate();
    }

    public void setCurrentPage(int page) {
        currentPage = page;
        invalidate();
    }

    public void setColor(int key, int selectedKey) {
        colorKey = key;
        selectedColorKey = selectedKey;
    }

    @Override
    /** MeeroX: iOS page dots never stretch; they only move. */
    private static boolean meeroIosDots() {
        try {
            return tw.nekomimi.nekogram.NekoConfig.meeroIosIntro.Bool();
        } catch (Throwable ignore) {
            return false;
        }
    }

    protected void onDraw(Canvas canvas) {
        float d = AndroidUtilities.dp(5);
        if (colorKey >= 0) {
            paint.setColor((Theme.getColor(colorKey) & 0x00ffffff) | 0xb4000000);
        } else {
            paint.setColor(Theme.getCurrentTheme().isDark() ? 0xff555555 : 0xffbbbbbb);
        }
        int x;
        currentPage = viewPager.getCurrentItem();
        for (int a = 0; a < pagesCount; a++) {
            if (a == currentPage) {
                continue;
            }
            x = a * AndroidUtilities.dp(11);
            rect.set(x, 0, x + AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(2.5f), AndroidUtilities.dp(2.5f), paint);
        }
        if (selectedColorKey >= 0) {
            paint.setColor(Theme.getColor(selectedColorKey));
        } else {
            paint.setColor(ThemeColors.TELEGRAM_COLOR);
        }
        x = currentPage * AndroidUtilities.dp(11);
        // MeeroX: iOS keeps every page dot the same circle and only moves the
        // highlight between them. Android stretches the active dot into a
        // capsule that spans towards the next page while you swipe, which is
        // the one thing that stops this control reading as a UIPageControl.
        if (progress != 0 && !meeroIosDots()) {
            if (scrollPosition >= currentPage) {
                rect.set(x, 0, x + AndroidUtilities.dp(5) + AndroidUtilities.dp(11) * progress, AndroidUtilities.dp(5));
            } else {
                rect.set(x - AndroidUtilities.dp(11) * (1.0f - progress), 0, x + AndroidUtilities.dp(5), AndroidUtilities.dp(5));
            }
        } else if (progress != 0) {
            // Slide the dot itself across instead of growing it.
            final float shift = AndroidUtilities.dp(11) * (scrollPosition >= currentPage ? progress : progress - 1f);
            rect.set(x + shift, 0, x + shift + AndroidUtilities.dp(5), AndroidUtilities.dp(5));
        } else {
            rect.set(x, 0, x + AndroidUtilities.dp(5), AndroidUtilities.dp(5));
        }
        canvas.drawRoundRect(rect, AndroidUtilities.dp(2.5f), AndroidUtilities.dp(2.5f), paint);
    }
}
