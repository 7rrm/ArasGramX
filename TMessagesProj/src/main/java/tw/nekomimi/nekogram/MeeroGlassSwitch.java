package tw.nekomimi.nekogram;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.TimeInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Switch;

/**
 * MeeroX v129: the switch the preview mock shows, drawn by us behind the
 * stock {@link Switch} API so every existing cell keeps working untouched.
 *
 * Mock parity (settings-mock-a-glass.html, element ".sw"):
 *  - track 48x28dp pill; OFF = rgba(255,255,255,.18) night / #D9D9E0 day
 *    (exactly MeeroGlassTheme.trackOff()).
 *  - ON   = rose #FF4E8A -> violet #7B5CFF at 135deg + a soft rose glow
 *           (0/2/14 rgba(255,78,138,.4), radial-gradient equivalent).
 *  - thumb: white circle 22dp, inset 3dp, travel 20dp, shadow 0/2/6 25%.
 *  - press: the knob stretches 22 -> 26dp anchored at its outer edge and
 *           settles back in 150ms (":active::after { width:26px }").
 *  - motion: 280ms cubic-bezier(.2,.9,.3,1.35) - the mock's springy ease.
 *
 * Everything scales from the view's actual height by the mock's ratios, so
 * a stock-sized 38x22dp layout still keeps the same proportions.
 *
 * When the glass design (or the dedicated switches toggle) is off, onDraw
 * defers to super and the widget looks and behaves byte-identical to the
 * stock Switch - same colors, same timing.
 */
public class MeeroGlassSwitch extends Switch {

    private static final TimeInterpolator GLASS_EASE =
            new CubicBezierInterpolator(0.2, 0.9, 0.3, 1.35);

    private float glassProgress;          // 0..1 travel of the knob
    private ValueAnimator glassAnimator;
    private float pressProgress;          // 0..1 knob squash
    private ValueAnimator pressAnimator;

    private final Paint offPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF glassRect = new RectF();

    private int cachedW = -1, cachedH = -1;

    public MeeroGlassSwitch(Context context) {
        // The cells() provider doubles as the stock-fallback palette: glass
        // off -> it resolves through the theme, i.e. exact stock colors.
        super(context, MeeroGlassTheme.cells());
        setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        // Mirrors the row's pressed state (the cell owns the touch), so the
        // knob squash works even though the cell - not the switch - is
        // the clickable view.
        setDuplicateParentStateEnabled(true);
        glassProgress = isChecked() ? 1f : 0f;
    }

    private boolean glassMode() {
        return MeeroGlassTheme.switchesEnabled();
    }

    @Override
    public void setChecked(boolean checked, int iconType, boolean animated) {
        final float target = checked ? 1f : 0f;
        // super keeps listeners, haptics and icon bookkeeping identical.
        super.setChecked(checked, iconType, animated);
        cancelGlassAnimator();
        if (!glassMode() || !animated || getWindowToken() == null) {
            glassProgress = target;
            invalidate();
            return;
        }
        glassAnimator = ValueAnimator.ofFloat(glassProgress, target);
        glassAnimator.setDuration(280);                  // mock: var(--dur) .28s
        glassAnimator.setInterpolator(GLASS_EASE);       // mock: cubic-bezier(.2,.9,.3,1.35)
        glassAnimator.addUpdateListener(a -> {
            glassProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        glassAnimator.start();
    }

    private void cancelGlassAnimator() {
        if (glassAnimator != null) {
            glassAnimator.cancel();
            glassAnimator = null;
        }
    }

    private boolean lastPressed;

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (pressed == lastPressed) {
            return;
        }
        lastPressed = pressed;
        cancelPressAnimator();
        final float target = pressed ? 1f : 0f;
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target);
        pressAnimator.setDuration(150);                  // mock: width .15s
        pressAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        pressAnimator.addUpdateListener(a -> {
            pressProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    private void cancelPressAnimator() {
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!glassMode()) {
            // exact stock rendering path, colors included
            super.onDraw(canvas);
            return;
        }
        // Self-heal after the feature toggle flipped while rows were visible:
        // with no animation in flight the knob must sit at the checked side.
        if (glassAnimator == null) {
            final float target = isChecked() ? 1f : 0f;
            if (Math.abs(glassProgress - target) > 0.001f) {
                glassProgress = target;
            }
        }

        final float w = getWidth();
        final float h = getHeight();
        if (w != cachedW || h != cachedH) {
            cachedW = (int) w;
            cachedH = (int) h;
        }

        // Track geometry in the mock's proportions (48x28 box).
        final float trackH = Math.min(h, AndroidUtilities.dp(28));
        final float trackW = Math.min(w, trackH * (48f / 28f));
        final float left = (w - trackW) / 2f;
        final float top = (h - trackH) / 2f;
        final float right = left + trackW;
        final float bottom = top + trackH;
        final float radius = trackH / 2f;
        final float cy = top + radius;

        final float inset = trackH * (3f / 28f);
        final float thumbR = trackH * (11f / 28f);           // 22dp knob
        final float edgeL = left + inset + thumbR;           // OFF center (LTR)
        final float edgeR = right - inset - thumbR;          // ON center (LTR)
        final boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final float offX = rtl ? edgeR : edgeL;
        final float onX = rtl ? edgeL : edgeR;

        final float p = Math.max(0f, Math.min(1f, glassProgress));
        float tx = offX + (onX - offX) * glassProgress;      // overshoot allowed
        tx = Math.max(Math.min(offX, onX), Math.min(Math.max(offX, onX), tx));

        final float cx = (left + right) / 2f;

        // 1) rose glow behind the track while ON (mock box-shadow 0/2/14 .4)
        if (p > 0.001f) {
            final float glowR = radius + AndroidUtilities.dp(9);
            glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                    MeeroGlassTheme.ACC1 & 0x00FFFFFF | 0x66000000,
                    MeeroGlassTheme.ACC1 & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            glowPaint.setAlpha((int) (255 * p));
            glassRect.set(cx - glowR, cy - glowR, cx + glowR, cy + glowR);
            canvas.drawRect(glassRect, glowPaint);
            glowPaint.setShader(null);
        }

        // 2) track: OFF fill cross-fading into the 135deg gradient
        glassRect.set(left, top, right, bottom);
        if (p < 0.999f) {
            offPaint.setColor(MeeroGlassTheme.trackOff());
            offPaint.setAlpha((int) (255 * (1f - p)));
            canvas.drawRoundRect(glassRect, radius, radius, offPaint);
        }
        if (p > 0.001f) {
            // CSS linear-gradient(135deg) runs top-left -> bottom-right,
            // physical in both LTR and RTL.
            gradPaint.setShader(new LinearGradient(left, top, right, bottom,
                    MeeroGlassTheme.ACC1, MeeroGlassTheme.ACC2, Shader.TileMode.CLAMP));
            gradPaint.setAlpha((int) (255 * p));
            canvas.drawRoundRect(glassRect, radius, radius, gradPaint);
            gradPaint.setShader(null);
        }

        // 3) knob shadow (mock 0/2/6 rgba(0,0,0,.25))
        shadowPaint.setShader(new RadialGradient(tx, cy + AndroidUtilities.dp(2),
                thumbR + AndroidUtilities.dp(3), 0x40000000, 0x00000000,
                Shader.TileMode.CLAMP));
        glassRect.set(tx - thumbR - AndroidUtilities.dp(3), cy - thumbR,
                tx + thumbR + AndroidUtilities.dp(3), cy + thumbR + AndroidUtilities.dp(4));
        canvas.drawRect(glassRect, shadowPaint);
        shadowPaint.setShader(null);

        // 4) the knob itself: stretches 22 -> 26dp while pressed, anchored
        // at its outer edge (mock :active::after width).
        final float stretch = thumbR * (2f / 11f) * pressProgress;  // +2dp half width
        final float side = tx >= cx ? 1f : -1f;
        final float outerEdge = tx + side * thumbR;
        final float cx2 = outerEdge - side * (thumbR + stretch);
        glassRect.set(cx2 - thumbR - stretch, cy - thumbR, cx2 + thumbR + stretch, cy + thumbR);
        thumbPaint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(glassRect, thumbR, thumbR, thumbPaint);
    }
}
