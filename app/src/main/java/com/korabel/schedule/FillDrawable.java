package com.korabel.schedule;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Translucent lesson-card backdrop: gray above, a colored band below. The
 * band is anchored to the bottom and its top edge drains downward as the
 * lesson (or the break before it) runs out — frac is the colored share of
 * the card: 1 for a future lesson, 0 once it is over.
 */
public final class FillDrawable extends Drawable {

    private final Paint paint = new Paint();
    private int base, gray, color;
    private float frac = 1f;

    void set(int baseColor, int grayColor, int bandColor, float f) {
        base = baseColor;
        gray = grayColor;
        color = bandColor;
        frac = Math.max(0f, Math.min(1f, f));
        invalidateSelf();
    }

    @Override public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        if (w <= 0 || h <= 0) return;
        paint.setColor(base);
        c.drawRect(0, 0, w, h, paint);
        float split = h * (1f - frac);
        if (split > 0) {
            paint.setColor(gray);
            c.drawRect(0, 0, w, split, paint);
        }
        if (frac > 0) {
            paint.setColor(color);
            c.drawRect(0, split, w, h, paint);
        }
    }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter f) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
