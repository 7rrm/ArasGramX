/*
 * Modified RadialProgressView - Dual-ring spinner
 *
 * Features:
 *  - Outer ring: TWO arcs with different solid colors
 *      Arc 1 (0°..150°):   purple 0xFF651FFF
 *      Arc 2 (180°..330°): cyan   0xFF00E5FF
 *    Each arc fades ONLY in alpha at its ends (stroke width stays constant),
 *    using a full-circle SweepGradient with a narrow visible window.
 *  - Inner ring: single 300° arc with cyan→purple gradient (unchanged colors).
 *  - Unified stroke width for both rings (2.5dp).
 *  - Glow strength: 0.5dp for BOTH outer and inner rings.
 *  - No flicker: radOffset rotates continuously without reset.
 *  - Cached inner SweepGradient for performance.
 *  - invalidate() called in all setters.
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class RadialProgressView extends View {

    // ==================== Constants ====================
    private static final float rotationTime = 700; // ms for a full 360° rotation
    private static final float risingTime = 500;    // ms used only in progress mode (not in spinner)

    // Outer ring colors
    private static final int COLOR_PURPLE = 0xFF651FFF;
    private static final int COLOR_CYAN   = 0xFF00E5FF;

    // ==================== State ====================
    private long lastUpdateTime;
    private float radOffset;
    private float currentCircleLength;
    private boolean risingCircleLength;
    private float currentProgressTime;
    private RectF cicleRect = new RectF();
    private RectF innerRect = new RectF();
    private boolean useSelfAlpha;
    private float drawingCircleLenght;

    private int progressColor;

    private DecelerateInterpolator decelerateInterpolator;
    private AccelerateInterpolator accelerateInterpolator;

    // Legacy paints (kept for compatibility with progress mode)
    private Paint progressPaint;
    private Paint glowPaint;

    // Inner ring paints
    private Paint innerPaint;
    private Paint innerGlowPaint;

    // Outer ring arc paints
    private Paint outerPurplePaint;
    private Paint outerCyanPaint;
    private Paint outerPurpleGlowPaint;
    private Paint outerCyanGlowPaint;

    private int size;

    private float currentProgress;
    private float progressAnimationStart;
    private int progressTime;
    private float animatedProgress;
    private boolean toCircle;
    private float toCircleProgress;

    private boolean noProgress = true;
    private final Theme.ResourcesProvider resourcesProvider;

    // Gradient for the inner ring
    private int[] gradientColors;
    private float[] gradientPositions;

    // Cached inner-ring gradient
    private SweepGradient cachedInnerGradient;
    private float cachedCx = -1f;
    private float cachedCy = -1f;

    // ==================== Constructors ====================

    public RadialProgressView(Context context) {
        this(context, null);
    }

    public RadialProgressView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        size = AndroidUtilities.dp(40);

        progressColor = getThemedColor(Theme.key_progressCircle);
        decelerateInterpolator = new DecelerateInterpolator();
        accelerateInterpolator = new AccelerateInterpolator();

        // Inner-ring gradient (cyan → blue → purple, then fades out)
        gradientColors = new int[]{
                0xFF00E5FF,
                0xFF2979FF,
                0xFF651FFF,
                0xCC651FFF,
                0x66651FFF,
                0x00651FFF
        };
        gradientPositions = new float[]{0f, 0.25f, 0.55f, 0.70f, 0.85f, 1.0f};

        // ===== Legacy paint (progress mode) =====
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        progressPaint.setColor(progressColor);

        // ===== Inner ring main paint =====
        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.STROKE);
        innerPaint.setStrokeCap(Paint.Cap.ROUND);
        innerPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        innerPaint.setColor(progressColor);

        // ===== Legacy glow paint (kept for compatibility, not used in dual-ring) =====
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(AndroidUtilities.dp(3f));
        glowPaint.setColor(progressColor);
        glowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(0.5f), BlurMaskFilter.Blur.NORMAL));

        // ===== Inner ring glow (0.5dp blur) =====
        innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerGlowPaint.setStyle(Paint.Style.STROKE);
        innerGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        innerGlowPaint.setStrokeWidth(AndroidUtilities.dp(3f));
        innerGlowPaint.setColor(progressColor);
        innerGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(0.5f), BlurMaskFilter.Blur.NORMAL));

        // ===== Outer ring arc paints (BUTT cap for clean alpha fade) =====
        outerPurplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPurplePaint.setStyle(Paint.Style.STROKE);
        outerPurplePaint.setStrokeCap(Paint.Cap.BUTT);
        outerPurplePaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        outerPurplePaint.setColor(COLOR_PURPLE);

        outerCyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerCyanPaint.setStyle(Paint.Style.STROKE);
        outerCyanPaint.setStrokeCap(Paint.Cap.BUTT);
        outerCyanPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        outerCyanPaint.setColor(COLOR_CYAN);

        // ===== Outer ring glow paints (0.5dp blur) =====
        outerPurpleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPurpleGlowPaint.setStyle(Paint.Style.STROKE);
        outerPurpleGlowPaint.setStrokeCap(Paint.Cap.BUTT);
        outerPurpleGlowPaint.setStrokeWidth(AndroidUtilities.dp(3f));
        outerPurpleGlowPaint.setColor(COLOR_PURPLE);
        outerPurpleGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(0.5f), BlurMaskFilter.Blur.NORMAL));

        outerCyanGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerCyanGlowPaint.setStyle(Paint.Style.STROKE);
        outerCyanGlowPaint.setStrokeCap(Paint.Cap.BUTT);
        outerCyanGlowPaint.setStrokeWidth(AndroidUtilities.dp(3f));
        outerCyanGlowPaint.setColor(COLOR_CYAN);
        outerCyanGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(0.5f), BlurMaskFilter.Blur.NORMAL));

        lastUpdateTime = System.currentTimeMillis();
    }

    // ==================== Public API ====================

    public void setUseSelfAlpha(boolean value) {
        useSelfAlpha = value;
    }

    @Keep
    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (useSelfAlpha) {
            Drawable background = getBackground();
            int a = (int) (alpha * 255);
            if (background != null) {
                background.setAlpha(a);
            }
            progressPaint.setAlpha(a);
            innerPaint.setAlpha(a);
            glowPaint.setAlpha(a);
            innerGlowPaint.setAlpha(a);
            outerPurplePaint.setAlpha(a);
            outerCyanPaint.setAlpha(a);
            outerPurpleGlowPaint.setAlpha(a);
            outerCyanGlowPaint.setAlpha(a);
        }
    }

    @Keep
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            lastUpdateTime = System.currentTimeMillis();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastUpdateTime = System.currentTimeMillis();
    }

    public void setNoProgress(boolean value) {
        noProgress = value;
        invalidate();
    }

    public void setProgress(float value) {
        currentProgress = value;
        if (animatedProgress > value) {
            animatedProgress = value;
        }
        progressAnimationStart = animatedProgress;
        progressTime = 0;
        invalidate();
    }

    public void setProgress(float value, boolean animated) {
        if (noProgress) {
            noProgress = false;
        }
        if (animated) {
            if (progressAnimationStart != value) {
                progressAnimationStart = value;
                currentProgressTime = 0;
            }
            progressTime = 0;
        } else {
            animatedProgress = value;
            progressAnimationStart = value;
            currentProgressTime = risingTime;
        }
        invalidate();
    }

    public void sync(RadialProgressView from) {
        lastUpdateTime = from.lastUpdateTime;
        radOffset = from.radOffset;
        toCircle = from.toCircle;
        toCircleProgress = from.toCircleProgress;
        noProgress = from.noProgress;
        currentCircleLength = from.currentCircleLength;
        drawingCircleLenght = from.drawingCircleLenght;
        currentProgressTime = from.currentProgressTime;
        currentProgress = from.currentProgress;
        progressTime = from.progressTime;
        animatedProgress = from.animatedProgress;
        risingCircleLength = from.risingCircleLength;
        progressAnimationStart = from.progressAnimationStart;
        invalidate();
    }

    public void setSize(int value) {
        size = value;
        cachedCx = -1f;
        cachedCy = -1f;
        invalidate();
    }

    public void setStrokeWidth(float value) {
        float dp = AndroidUtilities.dp(value);
        progressPaint.setStrokeWidth(dp);
        innerPaint.setStrokeWidth(dp);
        outerPurplePaint.setStrokeWidth(dp);
        outerCyanPaint.setStrokeWidth(dp);
        glowPaint.setStrokeWidth(dp + AndroidUtilities.dp(0.5f));
        innerGlowPaint.setStrokeWidth(dp + AndroidUtilities.dp(0.5f));
        outerPurpleGlowPaint.setStrokeWidth(dp + AndroidUtilities.dp(0.5f));
        outerCyanGlowPaint.setStrokeWidth(dp + AndroidUtilities.dp(0.5f));
        invalidate();
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
        innerPaint.setColor(progressColor);
        glowPaint.setColor(progressColor);
        innerGlowPaint.setColor(progressColor);
        // Outer arc colors are fixed (purple + cyan), so we don't override them here.
        invalidate();
    }

    public void toCircle(boolean toCircle, boolean animated) {
        this.toCircle = toCircle;
        if (!animated) {
            toCircleProgress = toCircle ? 1f : 0f;
        }
        invalidate();
    }

    public boolean isCircle() {
        return Math.abs(drawingCircleLenght) >= 360;
    }

    // ==================== Animation ====================

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        if (dt < 0) {
            dt = 16;
        }
        lastUpdateTime = newTime;
        updateAnimation(dt);
    }

    private void updateAnimation(long dt) {
        if (noProgress) {
            // Simple continuous rotation — no resets, no flicker
            radOffset += 360f * dt / rotationTime;
            if (radOffset >= 360f) {
                radOffset -= 360f * (float) Math.floor(radOffset / 360f);
            }
        } else {
            // Original progress mode
            radOffset += 360f * dt / rotationTime;
            int count = (int) (radOffset / 360);
            radOffset -= count * 360;

            if (toCircle && toCircleProgress != 1f) {
                toCircleProgress += 16 / 220f;
                if (toCircleProgress > 1f) {
                    toCircleProgress = 1f;
                }
            } else if (!toCircle && toCircleProgress != 0f) {
                toCircleProgress -= 16 / 400f;
                if (toCircleProgress < 0) {
                    toCircleProgress = 0f;
                }
            }

            if (toCircleProgress == 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= risingTime) {
                    currentProgressTime = risingTime;
                }
                if (risingCircleLength) {
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                } else {
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                }
                if (currentProgressTime == risingTime) {
                    if (risingCircleLength) {
                        radOffset += 270;
                        currentCircleLength = -266;
                    }
                    risingCircleLength = !risingCircleLength;
                    currentProgressTime = 0;
                }
            } else {
                if (risingCircleLength) {
                    float old = currentCircleLength;
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                    currentCircleLength += 360 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                } else {
                    float old = currentCircleLength;
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                    currentCircleLength -= 364 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                }
            }

            float progressDiff = currentProgress - progressAnimationStart;
            if (progressDiff > 0) {
                progressTime += dt;
                if (progressTime >= 200.0f) {
                    animatedProgress = progressAnimationStart = currentProgress;
                    progressTime = 0;
                } else {
                    animatedProgress = progressAnimationStart + progressDiff *
                        AndroidUtilities.decelerateInterpolator.getInterpolation(progressTime / 200.0f);
                }
            }
            currentCircleLength = Math.max(4, 360 * animatedProgress);
        }
    }

    // ==================== Drawing ====================

    private void ensureInnerGradient(float cx, float cy) {
        if (cachedInnerGradient == null || cachedCx != cx || cachedCy != cy) {
            cachedInnerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);
            cachedCx = cx;
            cachedCy = cy;
        }
        innerPaint.setShader(cachedInnerGradient);
        innerGlowPaint.setShader(cachedInnerGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (noProgress && toCircleProgress == 0) {
            drawDualRing(canvas, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
        } else {
            // Original progress mode
            int x = (getMeasuredWidth() - size) / 2;
            int y = (getMeasuredHeight() - size) / 2;
            cicleRect.set(x, y, x + size, y + size);
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
            canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
        }
        updateAnimation();
        invalidate();
    }

    public void draw(Canvas canvas, float cx, float cy) {
        if (noProgress && toCircleProgress == 0) {
            drawDualRing(canvas, cx, cy);
        } else {
            cicleRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
            canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
        }
        updateAnimation();
    }

    private void drawDualRing(Canvas canvas, float cx, float cy) {
        // Make the spinner big enough to fill the dialog box
        int bigSize = AndroidUtilities.dp(56);
        if (size > bigSize) {
            bigSize = size;
        }

        // ===== Unified stroke width for both rings =====
        float strokeW = AndroidUtilities.dp(2.5f);

        // Outer ring rect
        float outerHalf = bigSize / 2f;
        cicleRect.set(cx - outerHalf, cy - outerHalf,
                cx + outerHalf, cy + outerHalf);

        // Inner ring rect — slightly bigger (0.24 of size)
        float innerHalf = bigSize * 0.28f;
        innerRect.set(cx - innerHalf, cy - innerHalf,
                cx + innerHalf, cy + innerHalf);

        // ===== Build edge-fading SweepGradients for outer arcs =====
        // Full-circle gradient; the color is fully visible only inside the arc,
        // and fades to alpha=0 near both endpoints. Because the gradient is
        // circular (SweepGradient), the stroke keeps its FULL WIDTH and only
        // fades in ALPHA — no thinning at the edges.
        float fadeFrac = 0.15f;   // 15% of arc length used for the fade

        // --- Arc 1: 0°..150° (PURPLE) ---
        float arcStart1 = 0f;
        float arcEnd1   = 150f;
        float p1a = arcStart1 / 360f;
        float p1b = (arcStart1 + fadeFrac * (arcEnd1 - arcStart1)) / 360f;
        float p1c = (arcEnd1   - fadeFrac * (arcEnd1 - arcStart1)) / 360f;
        float p1d = arcEnd1 / 360f;

        SweepGradient purpleGradient = new SweepGradient(cx, cy,
                new int[]{
                        0x00651FFF,   // 0°   : transparent
                        0x00651FFF,   // 0°   : transparent (dup)
                        0xFF651FFF,   // +15% : full purple
                        0xFF651FFF,   // -15% : full purple
                        0x00651FFF,   // 150° : transparent
                        0x00651FFF    // 360° : transparent
                },
                new float[]{
                        0f,
                        p1a,
                        p1b,
                        p1c,
                        p1d,
                        1f
                });

        // --- Arc 2: 180°..330° (CYAN) ---
        float arcStart2 = 180f;
        float arcEnd2   = 330f;
        float p2a = arcStart2 / 360f;
        float p2b = (arcStart2 + fadeFrac * (arcEnd2 - arcStart2)) / 360f;
        float p2c = (arcEnd2   - fadeFrac * (arcEnd2 - arcStart2)) / 360f;
        float p2d = arcEnd2 / 360f;

        SweepGradient cyanGradient = new SweepGradient(cx, cy,
                new int[]{
                        0x0000E5FF,   // 0°   : transparent
                        0x0000E5FF,   // 180° : transparent
                        0xFF00E5FF,   // +15% : full cyan
                        0xFF00E5FF,   // -15% : full cyan
                        0x0000E5FF,   // 330° : transparent
                        0x0000E5FF    // 360° : transparent
                },
                new float[]{
                        0f,
                        p2a,
                        p2b,
                        p2c,
                        p2d,
                        1f
                });

        // Apply shaders + widths
        outerPurplePaint.setShader(purpleGradient);
        outerPurplePaint.setStrokeWidth(strokeW);

        outerCyanPaint.setShader(cyanGradient);
        outerCyanPaint.setStrokeWidth(strokeW);

        outerPurpleGlowPaint.setShader(purpleGradient);
        outerPurpleGlowPaint.setStrokeWidth(strokeW + AndroidUtilities.dp(0.5f));

        outerCyanGlowPaint.setShader(cyanGradient);
        outerCyanGlowPaint.setStrokeWidth(strokeW + AndroidUtilities.dp(0.5f));

        // ===== Draw outer ring (clockwise rotation) =====
        canvas.save();
        canvas.rotate(radOffset, cx, cy);

        // Purple arc (glow first, then solid line)
        canvas.drawArc(cicleRect, 0, 150, false, outerPurpleGlowPaint);
        canvas.drawArc(cicleRect, 0, 150, false, outerPurplePaint);

        // Cyan arc (glow first, then solid line)
        canvas.drawArc(cicleRect, 180, 150, false, outerCyanGlowPaint);
        canvas.drawArc(cicleRect, 180, 150, false, outerCyanPaint);

        canvas.restore();

        // ===== Draw inner ring (counter-clockwise) =====
        ensureInnerGradient(cx, cy);

        innerPaint.setStrokeWidth(strokeW);
        innerGlowPaint.setStrokeWidth(strokeW + AndroidUtilities.dp(0.5f));

        canvas.save();
        canvas.rotate(-radOffset, cx, cy);
        canvas.drawArc(innerRect, 0, 300, false, innerGlowPaint);
        canvas.drawArc(innerRect, 0, 300, false, innerPaint);
        canvas.restore();
    }

    // ==================== Measure ====================

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int wSize = MeasureSpec.getSize(widthMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);

        int width = (wMode == MeasureSpec.EXACTLY) ? wSize : size;
        int height = (hMode == MeasureSpec.EXACTLY) ? hSize : size;

        setMeasuredDimension(width, height);
    }

    @Override
    public void setBackgroundColor(int color) {
        progressColor = color;
        invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
