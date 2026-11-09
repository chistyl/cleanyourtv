package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * A field of soft white firefly halos around one fixed screen-space anchor.
 * Only the anchor is fixed; the individual halos drift and breathe independently.
 */
public final class ParticleEffectView extends View {
    private static final int HALO_COUNT = 34;

    private static final class Halo {
        float baseX;
        float baseY;
        float travelX;
        float travelY;
        float phaseX;
        float phaseY;
        float phaseB;
        float sizeDp;
        float pulsePhase;
        float pulseSpeed;
        int driftSpeed;
        int driftSpeedY;
        float alpha;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Halo[] halos = new Halo[HALO_COUNT];
    private final float density;
    private final RadialGradient whiteHalo;
    private float centerX = -1f;
    private float centerY = -1f;
    private boolean anchorVisible = true;
    private ValueAnimator animator;
    private float animationPhase;

    public ParticleEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;

        // White-only radial light: no colored tint, hard dot, outline, or visible particle core.
        whiteHalo = new RadialGradient(
                0f, 0f, 1f,
                new int[] {
                        Color.argb(34, 255, 255, 255),
                        Color.argb(43, 255, 255, 255),
                        Color.argb(31, 255, 255, 255),
                        Color.argb(15, 255, 255, 255),
                        Color.argb(4, 255, 255, 255),
                        Color.argb(0, 255, 255, 255)
                },
                new float[] { 0f, 0.16f, 0.38f, 0.63f, 0.84f, 1f },
                Shader.TileMode.CLAMP
        );

        // A fixed seed makes the composition stable between redraws and activity recreations.
        Random random = new Random(0xF1E_F1A5L);
        for (int i = 0; i < HALO_COUNT; i++) {
            Halo halo = new Halo();
            // A wider field with a mild centre bias: the outer reach grows, while most halos
            // remain visually gathered around the main light cluster.
            float spreadX = (float) Math.pow(random.nextFloat() * 2f - 1f, 1.18);
            float spreadY = (float) Math.pow(random.nextFloat() * 2f - 1f, 1.18);
            halo.baseX = spreadX * 145f;
            halo.baseY = spreadY * 88f;
            halo.travelX = 12f + random.nextFloat() * 30f;
            halo.travelY = 9f + random.nextFloat() * 25f;
            halo.phaseX = random.nextFloat() * (float) (Math.PI * 2.0);
            halo.phaseY = random.nextFloat() * (float) (Math.PI * 2.0);
            halo.phaseB = random.nextFloat() * (float) (Math.PI * 2.0);
            // Deliberately broad size range so large and small soft halos are clearly distinguishable.
            halo.sizeDp = 8f + random.nextFloat() * 29f;
            halo.pulsePhase = random.nextFloat() * (float) (Math.PI * 2.0);
            halo.pulseSpeed = 1f + random.nextInt(4);
            halo.driftSpeed = 1 + random.nextInt(3);
            halo.driftSpeedY = 1 + random.nextInt(3);
            halo.alpha = 0.48f + random.nextFloat() * 0.52f;
            halos[i] = halo;
        }

        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        createAnimator();
    }

    private void createAnimator() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(26000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            // All motion frequencies are whole cycles, so the loop joins without a visible jump.
            animationPhase = (Float) valueAnimator.getAnimatedValue() * (float) (Math.PI * 2.0);
            invalidate();
        });
    }

    /**
     * Called by MainActivity once when the initial app layout is ready.
     * The supplied accent is intentionally ignored: every halo remains pure white.
     */
    public void setAnchor(float x, float y, int color) {
        if (centerX == x && centerY == y) return;
        centerX = x;
        centerY = y;
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

        // This translation is the sole anchor. Focus changes and halo animation never modify it.
        canvas.save();
        // Keep the anchor screen-fixed, but place the whole firefly field 450dp lower.
        canvas.translate(centerX, centerY + 450f * density);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(whiteHalo);

        for (Halo halo : halos) {
            float t = animationPhase;
            float x = halo.baseX
                    + (float) Math.sin(t * halo.driftSpeed + halo.phaseX) * halo.travelX
                    + (float) Math.sin(t * (halo.driftSpeed + 2) + halo.phaseB) * halo.travelX * 0.19f;
            float y = halo.baseY
                    + (float) Math.sin(t * halo.driftSpeedY + halo.phaseY) * halo.travelY
                    + (float) Math.cos(t * (halo.driftSpeedY + 1) + halo.phaseB) * halo.travelY * 0.22f;

            // Independent slow breathing makes the lights brighten, dim and gently change size.
            float pulse = 0.5f + 0.5f * (float) Math.sin(t * halo.pulseSpeed + halo.pulsePhase);
            float radius = halo.sizeDp * density * (0.78f + pulse * 0.34f);
            int alpha = Math.round(halo.alpha * (42f + pulse * 76f));

            canvas.save();
            canvas.translate(x * density, y * density);
            canvas.scale(radius, radius);
            paint.setAlpha(alpha);
            canvas.drawCircle(0f, 0f, 1f, paint);
            canvas.restore();
        }

        paint.setShader(null);
        canvas.restore();
    }
}
