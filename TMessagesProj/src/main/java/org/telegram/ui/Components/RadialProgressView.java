/*
 * Modified RadialProgressView - Dual-ring spinner with gradient + glow + fade
 * Keeps ALL original methods intact for compatibility.
 * Only overrides onDraw for the new dual-ring design (in noProgress mode).
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
    private static final float rotationTime = 1200; // Speed: 1200ms per rotation (faster)
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

        // Colors: vivid cyan → blue → purple with smooth alpha fade
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
    }

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
        }
    }

    public void setNoProgress(boolean value) {
        noProgress = value;
    }

    public void setProgress(float value) {
        currentProgress = value;
        if (animatedProgress > value) {
            animatedProgress = value;
        }
        progressAnimationStart = animatedProgress;
        progressTime = 0;
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
        updateAnimation(17 * 5);
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        updateAnimation(dt);
    }

    private void updateAnimation(long dt) {
        radOffset += 360 * dt / rotationTime;
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

        if (noProgress) {
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
        } else {
            float progressDiff = currentProgress - progressAnimationStart;
            if (progressDiff > 0) {
                progressTime += dt;
                if (progressTime >= 200.0f) {
                    animatedProgress = progressAnimationStart = currentProgress;
                    progressTime = 0;
                } else {
                    animatedProgress = progressAnimationStart + progressDiff * AndroidUtilities.decelerateInterpolator.getInterpolation(progressTime / 200.0f);
                }
            }
            currentCircleLength = Math.max(4, 360 * animatedProgress);
        }
        invalidate();
    }

    public void setSize(int value) {
        size = value;
        invalidate();
    }

    public void setStrokeWidth(float value) {
        progressPaint.setStrokeWidth(AndroidUtilities.dp(value));
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
        innerPaint.setColor(progressColor);
    }

    public void toCircle(boolean toCircle, boolean animated) {
        this.toCircle = toCircle;
        if (!animated) {
            toCircleProgress = toCircle ? 1f : 0f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (noProgress && toCircleProgress == 0) {
            // === Dual-ring spinner with gradient + glow + fade ===
            int viewSize = size;
            if (viewSize == 0) {
                viewSize = AndroidUtilities.dp(40);
            }

            int x = (getMeasuredWidth() - viewSize) / 2;
            int y = (getMeasuredHeight() - viewSize) / 2;

            float strokeWidth = AndroidUtilities.dp(3);
            float innerStrokeWidth = AndroidUtilities.dp(2.5f);

            // Outer ring rect - make it fill most of the view
            float outerPadding = strokeWidth / 2 + AndroidUtilities.dp(1);
            cicleRect.set(x + outerPadding, y + outerPadding,
                    x + viewSize - outerPadding, y + viewSize - outerPadding);

            // Inner ring rect (~65% of outer - bigger to fill more space)
            float innerOffset = viewSize * 0.175f;
            innerRect.set(x + innerOffset, y + innerOffset,
                    x + viewSize - innerOffset, y + viewSize - innerOffset);

            float cx = x + viewSize / 2f;
            float cy = y + viewSize / 2f;

            // Create sweep gradients
            SweepGradient outerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);
            SweepGradient innerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);

            // Set shaders
            progressPaint.setShader(outerGradient);
            progressPaint.setStrokeWidth(strokeWidth);

            innerPaint.setShader(innerGradient);
            innerPaint.setStrokeWidth(innerStrokeWidth);

            glowPaint.setShader(new SweepGradient(cx, cy, gradientColors, gradientPositions));
            glowPaint.setStrokeWidth(strokeWidth + AndroidUtilities.dp(2));

            innerGlowPaint.setShader(new SweepGradient(cx, cy, gradientColors, gradientPositions));
            innerGlowPaint.setStrokeWidth(innerStrokeWidth + AndroidUtilities.dp(1.5f));

            // Draw outer ring glow + main together (no clip issues)
            canvas.save();
            canvas.rotate(radOffset, cx, cy);
            canvas.drawArc(cicleRect, 0, 270, false, glowPaint);
            canvas.drawArc(cicleRect, 0, 270, false, progressPaint);
            canvas.restore();

            // Draw inner ring glow + main together
            canvas.save();
            canvas.rotate(-radOffset, cx, cy);
            canvas.drawArc(innerRect, 0, 135, false, innerGlowPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerGlowPaint);
            canvas.drawArc(innerRect, 0, 135, false, innerPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerPaint);
            canvas.restore();

            // Update animation
            updateAnimation();
        } else {
            // Original onDraw for progress mode
            int x = (getMeasuredWidth() - size) / 2;
            int y = (getMeasuredHeight() - size) / 2;
            cicleRect.set(x, y, x + size, y + size);
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
            canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
            updateAnimation();
        }
    }

    public void draw(Canvas canvas, float cx, float cy) {
        if (noProgress && toCircleProgress == 0) {
            // Use custom draw for spinner mode
            int viewSize = size;
            if (viewSize == 0) {
                viewSize = AndroidUtilities.dp(40);
            }

            float strokeWidth = AndroidUtilities.dp(3);
            float innerStrokeWidth = AndroidUtilities.dp(2.5f);

            float outerPadding = strokeWidth / 2;
            cicleRect.set(cx - viewSize / 2f + outerPadding, cy - viewSize / 2f + outerPadding,
                    cx + viewSize / 2f - outerPadding, cy + viewSize / 2f - outerPadding);

            float innerOffset = viewSize * 0.20f;
            innerRect.set(cx - viewSize / 2f + innerOffset, cy - viewSize / 2f + innerOffset,
                    cx + viewSize / 2f - innerOffset, cy + viewSize / 2f - innerOffset);

            SweepGradient outerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);
            SweepGradient innerGradient = new SweepGradient(cx, cy, gradientColors, gradientPositions);

            progressPaint.setShader(outerGradient);
            progressPaint.setStrokeWidth(strokeWidth);
            innerPaint.setShader(innerGradient);
            innerPaint.setStrokeWidth(innerStrokeWidth);
            glowPaint.setShader(new SweepGradient(cx, cy, gradientColors, gradientPositions));
            glowPaint.setStrokeWidth(strokeWidth + AndroidUtilities.dp(2));
            innerGlowPaint.setShader(new SweepGradient(cx, cy, gradientColors, gradientPositions));
            innerGlowPaint.setStrokeWidth(innerStrokeWidth + AndroidUtilities.dp(1.5f));

            canvas.save();
            canvas.rotate(radOffset, cx, cy);
            canvas.drawArc(cicleRect, 0, 270, false, glowPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(radOffset, cx, cy);
            canvas.drawArc(cicleRect, 0, 270, false, progressPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(-radOffset, cx, cy);
            canvas.drawArc(innerRect, 0, 135, false, innerGlowPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerGlowPaint);
            canvas.restore();

            canvas.save();
            canvas.rotate(-radOffset, cx, cy);
            canvas.drawArc(innerRect, 0, 135, false, innerPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerPaint);
            canvas.restore();
        } else {
            cicleRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
            canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
        }
        updateAnimation();
    }

    public boolean isCircle() {
        return Math.abs(drawingCircleLenght) >= 360;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
