package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * A gentle firefly field anchored to a fixed screen-space point above the first app icon.
 * The anchor itself never moves; individual lights drift slowly and pulse independently around it.
 */
public final class ParticleEffectView extends View {
    // Stable per-firefly paths: elliptical drift, a little secondary sway, and independent twinkle.
    private static final float[] PHASES = {
            0.15f, 0.82f, 1.47f, 2.08f, 2.76f, 3.31f, 4.02f, 4.68f, 5.24f, 5.79f
    };
    private static final float[] SPEEDS = {
            0.72f, 0.54f, 0.86f, 0.63f, 0.48f, 0.78f, 0.58f, 0.92f, 0.67f, 0.51f
    };
    private static final float[] RADII_X = {
            28f, 49f, 65f, 38f, 73f, 55f, 24f, 82f, 43f, 61f
    };
    private static final float[] RADII_Y = {
            22f, 34f, 18f, 52f, 43f, 26f, 61f, 31f, 47f, 56f
    };
    private static final float[] SIZES = {
            1.1f, 1.5f, 1.0f, 1.7f, 1.2f, 0.9f, 1.4f, 1.1f, 1.6f, 1.0f
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float centerX = -1f;
    private float centerY = -1f;
    private int fireflyColor = Color.rgb(232, 255, 194);
    private boolean anchorVisible = true;
    private final float density;
    private final float haloRadius;
    private ValueAnimator animator;
    private float animationPhase;

    public ParticleEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        haloRadius = 3.2f * density;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        createAnimator();
    }

    private void createAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(18000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            animationPhase = (Float) valueAnimator.getAnimatedValue() * (float) (Math.PI * 2.0);
            invalidate();
        });
    }

    public void setAnchor(float x, float y, int color) {
        if (centerX == x && centerY == y) return;
        centerX = x;
        centerY = y;
        // Blend the app accent with a firefly-like pale green/gold light.
        int r = (Color.red(color) * 55 + 232 * 45) / 100;
        int g = (Color.green(color) * 55 + 255 * 45) / 100;
        int b = (Color.blue(color) * 55 + 194 * 45) / 100;
        fireflyColor = Color.rgb(r, g, b);
        invalidate();
    }

    public void setAnchorVisible(boolean visible) {
        if (anchorVisible != visible) {
            anchorVisible = visible;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isStarted()) animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!anchorVisible || centerX < 0f || centerY < 0f) return;

        canvas.save();
        canvas.translate(centerX, centerY);

        // A barely visible pool of light holds the composition together without a hard central dot.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(17, Color.red(fireflyColor), Color.green(fireflyColor), Color.blue(fireflyColor)));
        canvas.drawCircle(0f, 0f, 27f * density, paint);

        for (int i = 0; i < PHASES.length; i++) {
            float angle = animationPhase * SPEEDS[i] + PHASES[i];
            float sway = 5f * density;
            float x = (float) Math.cos(angle) * RADII_X[i] * density
                    + (float) Math.cos(angle * 0.47f + PHASES[i]) * sway;
            float y = (float) Math.sin(angle * 0.83f) * RADII_Y[i] * density
                    + (float) Math.sin(angle * 0.61f + PHASES[i]) * sway;
            float twinkle = 0.5f + 0.5f * (float) Math.sin(animationPhase * (1.4f + SPEEDS[i]) + PHASES[i]);
            int alpha = 34 + Math.round(twinkle * 112f);
            float size = SIZES[i] * density;

            // Soft outer aura, brighter inner glow, then a tiny luminous core.
            paint.setColor(Color.argb(Math.max(8, alpha / 3), Color.red(fireflyColor),
                    Color.green(fireflyColor), Color.blue(fireflyColor)));
            canvas.drawCircle(x, y, haloRadius * (1.45f + twinkle * 0.55f), paint);

            paint.setColor(Color.argb(alpha, Color.red(fireflyColor),
                    Color.green(fireflyColor), Color.blue(fireflyColor)));
            canvas.drawCircle(x, y, size * 2.1f, paint);

            paint.setColor(Color.argb(Math.min(245, alpha + 82), 245, 255, 218));
            canvas.drawCircle(x, y, Math.max(0.8f * density, size * 0.72f), paint);
        }
        canvas.restore();
    }
}
