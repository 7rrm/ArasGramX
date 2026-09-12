/*
 * Modified RadialProgressView - Dual-ring spinner with gradient + glow + fade
 * Outer ring: 1 gap (270° arc), rotates clockwise
 * Inner ring: 2 opposite gaps (two 135° arcs), rotates counter-clockwise
 * Same speed, cyan-to-purple sweep gradient with smooth fade and neon glow
 */
package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
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
    private static final float rotationTime = 1100;
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
        // The alpha fades from 255 (opaque) at start to 0 (transparent) at end
        // This creates the soft tail effect before the gap
        // Vivid neon colors: #00E5FF (cyan), #2979FF (blue), #651FFF (deep purple)
        int[] colors = {
                0xFF00E5FF,    // Bright cyan (leading tip) - alpha 255
                0xFF2979FF,    // Electric blue - alpha 255
                0xFF651FFF,    // Deep purple - alpha 255
                0xCC651FFF,    // Purple fading - alpha ~80%
                0x66651FFF,   // Purple more faded - alpha ~40%
                0x00651FFF      // Fully transparent (gap starts) - alpha 0
        };
        float[] positions = {0f, 0.25f, 0.55f, 0.70f, 0.85f, 1.0f};

        // Outer ring paint
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        progressPaint.setColor(progressColor);

        // Inner ring paint
        innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.STROKE);
        innerPaint.setStrokeCap(Paint.Cap.ROUND);
        innerPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        innerPaint.setColor(progressColor);

        // Glow paint (wider, blurred for neon effect)
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeWidth(AndroidUtilities.dp(5));
        glowPaint.setColor(progressColor);
        glowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(2f), BlurMaskFilter.Blur.NORMAL));

        // Inner glow paint
        innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerGlowPaint.setStyle(Paint.Style.STROKE);
        innerGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        innerGlowPaint.setStrokeWidth(AndroidUtilities.dp(4f));
        innerGlowPaint.setColor(progressColor);
        innerGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(1.5f), BlurMaskFilter.Blur.NORMAL));

        // Store colors for gradient creation
        gradientColors = colors;
        gradientPositions = positions;
    }

    private int[] gradientColors;
    private float[] gradientPositions;

    public void setUseSelfAlpha(boolean value) {
        useSelfAlpha = value;
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (noProgress) {
            // === Dual-ring spinner with gradient + glow + fade ===
            int viewSize = size;
            if (viewSize == 0) {
                viewSize = AndroidUtilities.dp(40);
            }

            float strokeWidth = AndroidUtilities.dp(3);
            float innerStrokeWidth = AndroidUtilities.dp(2.5f);

            // Outer ring rect
            float outerPadding = strokeWidth / 2 + AndroidUtilities.dp(2);
            cicleRect.set(outerPadding, outerPadding,
                    viewSize - outerPadding, viewSize - outerPadding);

            // Inner ring rect (~60% of outer)
            float innerSize = viewSize * 0.30f;
            innerRect.set(innerSize, innerSize,
                    viewSize - innerSize, viewSize - innerSize);

            // Create sweep gradients
            float cx = viewSize / 2f;
            float cy = viewSize / 2f;

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

            // Draw outer ring glow first (behind the main ring)
            canvas.save();
            canvas.rotate(radOffset, cx, cy);
            canvas.drawArc(cicleRect, 0, 270, false, glowPaint);
            canvas.restore();

            // Draw outer ring main (on top of glow)
            canvas.save();
            canvas.rotate(radOffset, cx, cy);
            canvas.drawArc(cicleRect, 0, 270, false, progressPaint);
            canvas.restore();

            // Draw inner ring glow
            canvas.save();
            canvas.rotate(-radOffset, cx, cy);
            canvas.drawArc(innerRect, 0, 135, false, innerGlowPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerGlowPaint);
            canvas.restore();

            // Draw inner ring main
            canvas.save();
            canvas.rotate(-radOffset, cx, cy);
            canvas.drawArc(innerRect, 0, 135, false, innerPaint);
            canvas.drawArc(innerRect, 180, 135, false, innerPaint);
            canvas.restore();

            // Update rotation - same speed for both
            long newTime = System.currentTimeMillis();
            long delta = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            if (delta > 18) {
                delta = 16;
            }
            radOffset += 360 * delta / rotationTime;
            invalidate();
        } else {
            // Progress mode (original behavior)
            int viewSize = size;
            if (viewSize == 0) {
                viewSize = AndroidUtilities.dp(40);
            }

            float radius = viewSize / 2f - AndroidUtilities.dp(2);
            float strokeWidth = AndroidUtilities.dp(3);

            cicleRect.set(
                    viewSize / 2f - radius,
                    viewSize / 2f - radius,
                    viewSize / 2f + radius,
                    viewSize / 2f + radius
            );

            progressPaint.setShader(null);
            progressPaint.setColor(progressColor);
            progressPaint.setStrokeWidth(strokeWidth);

            canvas.save();
            canvas.rotate(radOffset - 90, viewSize / 2f, viewSize / 2f);
            canvas.drawArc(cicleRect, 0, Math.max(4, currentCircleLength), false, progressPaint);
            canvas.restore();

            long newTime = System.currentTimeMillis();
            long delta = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            if (delta > 18) {
                delta = 16;
            }
            radOffset += 360 * delta / rotationTime;
            invalidate();
        }
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

    public void setSize(int value) {
        size = value;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY));
    }

    @Override
    public void setBackgroundColor(int color) {
        progressColor = color;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
