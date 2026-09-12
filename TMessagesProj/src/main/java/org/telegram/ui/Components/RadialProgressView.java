/*
 * Modified RadialProgressView - Dual-ring spinner with gradient + glow + fade
 * Keeps ALL original methods intact for compatibility.
 * Only overrides onDraw for the new dual-ring design (in noProgress mode).
 *
 * Fixes applied:
 *  - No flicker: radOffset rotates continuously without reset
 *  - Bigger spinner: fills the dialog box (56dp default)
 *  - Reuses SweepGradient instead of recreating every frame (performance)
 *  - invalidate() called in all setters
 *  - setAlpha updates all paints
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
    private Paint progressPaint;
    private Paint innerPaint;
    private Paint glowPaint;
    private Paint innerGlowPaint;

    private static final float rotationTime = 1200; // ms per full rotation
    private static final float risingTime = 500;
    private int size;

    private float currentProgress;
    private float progressAnimationStart;
    private int progressTime;
    private float animatedProgress;
    private boolean toCircle;
    private float toCircleProgress;

    private boolean noProgress = true;
    private final Theme.ResourcesProvider resourcesProvider;

    private int[] gradientColors;
    private float[] gradientPositions;

    // Cached gradients (recreated only when center changes)
    private SweepGradient cachedOuterGradient;
    private SweepGradient cachedInnerGradient;
    private float cachedCx = -1f;
    private float cachedCy = -1f;

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

        // Colors: vivid cyan -> blue -> purple with smooth alpha fade
        gradientColors = new int[]{
                0xFF00E5FF,
                0xFF2979FF,
                0xFF651FFF,
                0xCC651FFF,
                0x66651FFF,
                0x00651FFF
        };
        gradientPositions = new float[]{0f, 0.25f, 0.55f, 0.70f, 0.85f, 1.0f};

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        progressPaint.setColor(progressColor);

        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.STROKE);
        innerPaint.setStrokeCap(Paint.Cap.ROUND);
        innerPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        innerPaint.setColor(progressColor);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(AndroidUtilities.dp(5));
        glowPaint.setColor(progressColor);
        glowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(2f), BlurMaskFilter.Blur.NORMAL));

        innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerGlowPaint.setStyle(Paint.Style.STROKE);
        innerGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        innerGlowPaint.setStrokeWidth(AndroidUtilities.dp(4f));
        innerGlowPaint.setColor(progressColor);
        innerGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(1.5f), BlurMaskFilter.Blur.NORMAL));

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
        progressPaint.setStrokeWidth(AndroidUtilities.dp(value));
        invalidate();
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
        innerPaint.setColor(progressColor);
        glowPaint.setColor(progressColor);
        innerGlowPaint.setColor(progressColor);
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

    private void ensureGradients(float cx, float cy) {
        if (cachedOuterGradient == null || cachedCx != cx || cachedCy != cy) {
            cachedOuterGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);
            cachedInnerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);
            cachedCx = cx;
            cachedCy = cy;
        }
        progressPaint.setShader(cachedOuterGradient);
        glowPaint.setShader(cachedOuterGradient);
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
            progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
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
        // Make the spinner much bigger to fill the dialog box
        int bigSize = AndroidUtilities.dp(56);
        if (size > bigSize) {
            bigSize = size;
        }

        float strokeWidth = AndroidUtilities.dp(3.5f);
        float innerStrokeWidth = AndroidUtilities.dp(3f);

        // Outer ring rect — fills most of the view
        float outerHalf = bigSize / 2f;
        cicleRect.set(cx - outerHalf, cy - outerHalf,
                cx + outerHalf, cy + outerHalf);

        // Inner ring rect — 32% radius (64% diameter) with clear gap
        float innerHalf = bigSize * 0.28f;
        innerRect.set(cx - innerHalf, cy - innerHalf,
                cx + innerHalf, cy + innerHalf);

        // Set gradients (cached)
        ensureGradients(cx, cy);

        progressPaint.setStrokeWidth(strokeWidth);
        innerPaint.setStrokeWidth(innerStrokeWidth);
        glowPaint.setStrokeWidth(strokeWidth + AndroidUtilities.dp(2));
        innerGlowPaint.setStrokeWidth(innerStrokeWidth + AndroidUtilities.dp(1.5f));

        // ---- Outer ring: 2 gaps, clockwise ----
        // Arc 1: 0°..150°   | Gap 1: 150°..180°
        // Arc 2: 180°..330° | Gap 2: 330°..360°
        canvas.save();
        canvas.rotate(radOffset, cx, cy);
        canvas.drawArc(cicleRect, 0, 150, false, glowPaint);
        canvas.drawArc(cicleRect, 180, 150, false, glowPaint);
        canvas.drawArc(cicleRect, 0, 150, false, progressPaint);
        canvas.drawArc(cicleRect, 180, 150, false, progressPaint);
        canvas.restore();

        // ---- Inner ring: 1 gap, counter-clockwise ----
        // Arc: 0°..300° | Gap: 300°..360°
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

        // If parent gives us an exact size, use it — otherwise use `size`
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
