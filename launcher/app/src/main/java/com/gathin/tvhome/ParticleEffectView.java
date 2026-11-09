package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A restrained point-light accent drawn above the ambient glow. Its centre is supplied in root
 * coordinates by MainActivity and follows the first app tile as the grid is laid out or scrolled.
 */
public final class ParticleEffectView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float centerX = -1f;
    private float centerY = -1f;
    private int color = Color.WHITE;
    private boolean anchorVisible = true;
    private float density;

    public ParticleEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
    }

    public void setAnchor(float x, float y, int color) {
        this.centerX = x;
        this.centerY = y;
        this.color = 0xFF000000 | (color & 0x00FFFFFF);
        invalidate();
    }

    public void setAnchorVisible(boolean visible) {
        if (anchorVisible != visible) {
            anchorVisible = visible;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!anchorVisible || centerX < 0f || centerY < 0f) return;

        float radius = 76f * density;
        paint.setShader(new RadialGradient(centerX, centerY, radius,
                new int[]{0x55FFFFFF, 0x20FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.28f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setShader(null);

        int tint = color;
        paint.setColor((tint & 0x00FFFFFF) | 0x99000000);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, 2.2f * density, paint);

        // Fixed, quiet pinpoints keep the effect stable and avoid a continuously invalidated animation.
        float[][] points = {
                {-28f, -13f, 1.3f}, {24f, -22f, 1.1f}, {38f, 9f, 1.5f},
                {-34f, 25f, 1.0f}, {7f, 37f, 1.2f}, {-8f, -42f, 0.9f},
                {51f, -34f, 0.8f}, {-54f, 2f, 0.8f}
        };
        for (float[] point : points) {
            paint.setColor((tint & 0x00FFFFFF) | 0x66000000);
            canvas.drawCircle(centerX + point[0] * density, centerY + point[1] * density,
                    point[2] * density, paint);
        }
    }
}
