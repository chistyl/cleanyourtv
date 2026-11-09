package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * A dense field of softly glowing fireflies around one immutable screen-space anchor.
 * Every particle has its own size, drift, direction and pulse, avoiding a mechanical orbit.
 */
public final class ParticleEffectView extends View {
    private static final int PARTICLE_COUNT = 54;
    private static final long RANDOM_SEED = 0x46495245464C594CL;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float[] startX = new float[PARTICLE_COUNT];
    private final float[] startY = new float[PARTICLE_COUNT];
    private final float[] driftX = new float[PARTICLE_COUNT];
    private final float[] driftY = new float[PARTICLE_COUNT];
    private final float[] phaseX = new float[PARTICLE_COUNT];
    private final float[] phaseY = new float[PARTICLE_COUNT];
    private final float[] speedX = new float[PARTICLE_COUNT];
    private final float[] speedY = new float[PARTICLE_COUNT];
    private final float[] size = new float[PARTICLE_COUNT];
    private final float[] pulsePhase = new float[PARTICLE_COUNT];
    private final float[] pulseSpeed = new float[PARTICLE_COUNT];
    private final float[] brightness = new float[PARTICLE_COUNT];

    private float centerX = -1f;
    private float centerY = -1f;
    private int fireflyColor = Color.rgb(232, 255, 194);
    private boolean anchorVisible = true;
    private ValueAnimator animator;
    private float animationTime;

    public ParticleEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        initializeParticles();
        createAnimator();
    }

    private void initializeParticles() {
        Random random = new Random(RANDOM_SEED);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            // A broad, irregular cloud rather than a ring around the anchor.
            startX[i] = (random.nextFloat() * 2f - 1f) * (28f + random.nextFloat() * 78f);
            startY[i] = (random.nextFloat() * 2f - 1f) * (18f + random.nextFloat() * 57f);
            driftX[i] = 4f + random.nextFloat() * 17f;
            driftY[i] = 3f + random.nextFloat() * 14f;
            phaseX[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            phaseY[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            speedX[i] = 0.20f + random.nextFloat() * 0.62f;
            speedY[i] = 0.16f + random.nextFloat() * 0.58f;
            pulsePhase[i] = random.nextFloat() * (float) (Math.PI * 2.0);
            pulseSpeed[i] = 0.45f + random.nextFloat() * 1.5f;
            brightness[i] = 0.55f + random.nextFloat() * 0.45f;

            // Deliberately wide size range: tiny distant sparks mixed with obvious large fireflies.
            float tier = random.nextFloat();
            if (tier < 0.58f) {
                size[i] = 0.75f + random.nextFloat() * 1.15f;
            } else if (tier < 0.88f) {
                size[i] = 2.0f + random.nextFloat() * 1.8f;
            } else {
                size[i] = 4.0f + random.nextFloat() * 2.6f;
            }
        }
    }

    private void createAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(60000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            animationTime = (Float) valueAnimator.getAnimatedValue() * 60f;
            invalidate();
        });
    }

    public void setAnchor(float x, float y, int color) {
        // The anchor is intentionally write-once at the activity level. Never derive it from focus.
        if (centerX == x && centerY == y) return;
        centerX = x;
        centerY = y;
        int r = (Color.red(color) * 35 + 232 * 65) / 100;
        int g = (Color.green(color) * 35 + 255 * 65) / 100;
        int b = (Color.blue(color) * 35 + 194 * 65) / 100;
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
        int red = Color.red(fireflyColor);
        int green = Color.green(fireflyColor);
        int blue = Color.blue(fireflyColor);

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            // Independent, slow, incommensurate waves create wandering paths without visible loops.
            float t = animationTime;
            float x = startX[i] + (float) Math.sin(t * speedX[i] + phaseX[i]) * driftX[i]
                    + (float) Math.sin(t * speedY[i] * 0.37f + phaseY[i]) * driftX[i] * 0.42f;
            float y = startY[i] + (float) Math.sin(t * speedY[i] + phaseY[i]) * driftY[i]
                    + (float) Math.cos(t * speedX[i] * 0.31f + phaseX[i]) * driftY[i] * 0.48f;
            float pulse = 0.18f + 0.82f * (0.5f + 0.5f
                    * (float) Math.sin(t * pulseSpeed[i] + pulsePhase[i]));
            float radius = size[i] * density;
            float glow = pulse * brightness[i];
            float px = x * density;
            float py = y * density;

            // Three translucent layers create a feathered edge, not a hard-edged dot or a central halo.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(10f + 17f * glow), red, green, blue));
            canvas.drawCircle(px, py, radius * 3.8f, paint);

            paint.setColor(Color.argb(Math.round(18f + 42f * glow), red, green, blue));
            canvas.drawCircle(px, py, radius * 2.15f, paint);

            paint.setColor(Color.argb(Math.round(38f + 112f * glow), red, green, blue));
            canvas.drawCircle(px, py, radius, paint);

            // A restrained luminous centre keeps even the larger fireflies soft rather than solid.
            paint.setColor(Color.argb(Math.round(45f + 105f * glow), 247, 255, 220));
            canvas.drawCircle(px, py, Math.max(0.45f * density, radius * 0.34f), paint);
        }
        canvas.restore();
    }
}
