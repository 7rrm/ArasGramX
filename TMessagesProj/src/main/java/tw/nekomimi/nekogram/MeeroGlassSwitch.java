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

    /* v187 (batch 3A): the mock ratios, easing and timings come from the
     * sealed native brain; legacy literals stay as the exact fallback.
     * Holder states: null = not probed yet, length-0 = legacy forever. */
    private static CubicBezierInterpolator sGlassEase;
    private static float[] sSwParams;

    private static float[] swParams() {
        float[] p = sSwParams;
        if (p == null) {
            float[] n = MeeroCore.glassCore() ? MeeroCore.nGlassSwitchParams() : null;
            sSwParams = (n != null && n.length == 16) ? n : new float[0];
            p = sSwParams;
        }
        return p.length == 16 ? p : null;
    }

    private static float swp(int i, float fb) {
        float[] p = swParams();
        return p != null ? p[i] : fb;
    }

    private static CubicBezierInterpolator glassEase() {
        CubicBezierInterpolator e = sGlassEase;
        if (e == null) {
            float[] p = swParams();
            e = p != null ? new CubicBezierInterpolator(p[8], p[9], p[10], p[11])
                          : new CubicBezierInterpolator(0.2, 0.9, 0.3, 1.35);
            sGlassEase = e;
        }
        return e;
    }

    /** Fallback workspace so the legacy path allocates nothing per frame. */
    private final float[] mGeo = new float[24];

    /**
     * One full mock-ratio geometry for this frame. Indices (shared with the
     * native brain, see meero_glass.h): 0..3 track rect, 4 radius, 5 cy,
     * 6 cx, 7 tx, 8 thumbR, 9 p01, 10 glowR, 11 shadowCy, 12 shadowR,
     * 13..16 shadow rect, 17..20 knob rect, 21 stretch, 22 offX, 23 onX.
     */
    private float[] glassGeometry(float w, float h) {
        final boolean rtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        if (MeeroCore.glassCore()) {
            float[] g = MeeroCore.nGlassSwitchGeom(AndroidUtilities.density, w, h,
                    glassProgress, pressProgress, rtl);
            if (g != null && g.length == 24) {
                return g;
            }
        }
        // legacy mock-parity chain (v129) - the exact fallback
        final float[] o = mGeo;
        final float trackH = Math.min(h, AndroidUtilities.dp(28));
        final float trackW = Math.min(w, trackH * (48f / 28f));
        final float left = (w - trackW) / 2f;
        final float top = (h - trackH) / 2f;
        final float right = left + trackW;
        final float bottom = top + trackH;
        final float radius = trackH / 2f;
        final float cy = top + radius;
        final float cx = (left + right) / 2f;
        final float inset = trackH * (3f / 28f);
        final float thumbR = trackH * (11f / 28f);
        final float edgeL = left + inset + thumbR;
        final float edgeR = right - inset - thumbR;
        final float offX = rtl ? edgeR : edgeL;
        final float onX = rtl ? edgeL : edgeR;
        final float p = Math.max(0f, Math.min(1f, glassProgress));
        float tx = offX + (onX - offX) * glassProgress;
        tx = Math.max(Math.min(offX, onX), Math.min(Math.max(offX, onX), tx));
        final float glowR = radius + AndroidUtilities.dp(9);
        final float stretch = thumbR * (2f / 11f) * pressProgress;
        final float side = tx >= cx ? 1f : -1f;
        final float outerEdge = tx + side * thumbR;
        final float cx2 = outerEdge - side * (thumbR + stretch);
        o[0] = left;  o[1] = top;    o[2] = right;  o[3] = bottom;
        o[4] = radius; o[5] = cy;    o[6] = cx;     o[7] = tx;
        o[8] = thumbR; o[9] = p;     o[10] = glowR;
        o[11] = cy + AndroidUtilities.dp(2);
        o[12] = thumbR + AndroidUtilities.dp(3);
        o[13] = tx - thumbR - AndroidUtilities.dp(3);
        o[14] = cy - thumbR;
        o[15] = tx + thumbR + AndroidUtilities.dp(3);
        o[16] = cy + thumbR + AndroidUtilities.dp(4);
        o[17] = cx2 - thumbR - stretch; o[18] = cy - thumbR;
        o[19] = cx2 + thumbR + stretch; o[20] = cy + thumbR;
        o[21] = stretch; o[22] = offX; o[23] = onX;
        return o;
    }

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
        final boolean changed = checked != isChecked();
        final float target = checked ? 1f : 0f;
        // super keeps listeners, haptics and icon bookkeeping identical.
        super.setChecked(checked, iconType, animated);
        if (!changed) {
            // v130 FIX (user report #2): clicking a row fires setChecked and
            // the following rebind fires it AGAIN with the same value. We
            // used to cancel + snap on that second call, killing the 280ms
            // travel two frames in - toggles looked instant. Idempotent
            // re-sets must leave a running animation alone.
            return;
        }
        cancelGlassAnimator();
        if (!glassMode() || !animated || getWindowToken() == null) {
            glassProgress = target;
            invalidate();
            return;
        }
        glassAnimator = ValueAnimator.ofFloat(glassProgress, target);
        glassAnimator.setDuration((long) swp(12, 280));    // mock: var(--dur) .28s
        glassAnimator.setInterpolator(glassEase());       // mock: cubic-bezier(.2,.9,.3,1.35)
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
        pressAnimator.setDuration((long) swp(13, 150));  // mock: width .15s
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

        // Track geometry in the mock's proportions (48x28 box) - v187: the
        // numbers come from the sealed brain; o = this frame's geometry.
        final float[] g = glassGeometry(w, h);
        final float left = g[0], top = g[1], right = g[2], bottom = g[3];
        final float radius = g[4], cy = g[5], cx = g[6];
        final float tx = g[7], thumbR = g[8], p = g[9];

        // 1) rose glow behind the track while ON (mock box-shadow 0/2/14 .4)
        if (p > 0.001f) {
            final float glowR = g[10];
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
        shadowPaint.setShader(new RadialGradient(tx, g[11],
                g[12], 0x40000000, 0x00000000,
                Shader.TileMode.CLAMP));
        glassRect.set(g[13], g[14], g[15], g[16]);
        canvas.drawRect(glassRect, shadowPaint);
        shadowPaint.setShader(null);

        // 4) the knob itself: stretches 22 -> 26dp while pressed, anchored
        // at its outer edge (mock :active::after width).
        glassRect.set(g[17], g[18], g[19], g[20]);
        thumbPaint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(glassRect, thumbR, thumbR, thumbPaint);
    }
}
