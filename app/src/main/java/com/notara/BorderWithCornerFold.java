package com.notara;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class BorderWithCornerFold extends Drawable {
    private final Paint fillPaint;
    private final Paint strokePaint;
    private final Paint foldPaint;
    private Paint foldOutlinePaint;
    private boolean showFoldOutline;
    private final Path path;
    private final Path foldPath;
    private final Path foldOutlinePath;
    private final float cornerRadius;
    private final float strokeWidth;
    private float foldSize;

    public BorderWithCornerFold(float cornerRadius, float strokeWidth, float foldSize,
                                 int fillColor, int strokeColor) {
        this.cornerRadius = cornerRadius;
        this.strokeWidth = strokeWidth;
        this.foldSize = foldSize;

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(fillColor);
        fillPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setColor(strokeColor);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        foldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        foldPaint.setColor(strokeColor);
        foldPaint.setStyle(Paint.Style.FILL);

        showFoldOutline = false;
        path = new Path();
        foldPath = new Path();
        foldOutlinePath = new Path();
    }

    public void setColors(int fillColor, int strokeColor) {
        fillPaint.setColor(fillColor);
        strokePaint.setColor(strokeColor);
        foldPaint.setColor(strokeColor);
        invalidateSelf();
    }

    public void setFoldSize(float newFoldSize) {
        if (this.foldSize != newFoldSize) {
            this.foldSize = newFoldSize;
            buildPaths(getBounds());
            invalidateSelf();
        }
    }

    public void setFoldOutlineVisible(boolean visible) {
        this.showFoldOutline = visible;
        if (visible && foldOutlinePaint == null) {
            foldOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            foldOutlinePaint.setStyle(Paint.Style.STROKE);
            foldOutlinePaint.setStrokeWidth(4f);
            foldOutlinePaint.setColor(android.graphics.Color.WHITE);
            foldOutlinePaint.setStrokeJoin(Paint.Join.ROUND);
            foldOutlinePaint.setStrokeCap(Paint.Cap.ROUND);
        }
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        buildPaths(bounds);
    }

    private void buildPaths(Rect bounds) {
        float w = bounds.width();
        float h = bounds.height();

        path.reset();
        foldPath.reset();

        if (w <= 0 || h <= 0) return;

        float r = cornerRadius;
        float f = Math.min(foldSize, Math.min(w / 2f, h / 2f));

        path.moveTo(r, 0);
        path.lineTo(w - f, 0);
        path.lineTo(w, f);
        path.lineTo(w, h - r);
        path.quadTo(w, h, w - r, h);
        path.lineTo(r, h);
        path.quadTo(0, h, 0, h - r);
        path.lineTo(0, r);
        path.quadTo(0, 0, r, 0);
        path.close();

        foldPath.moveTo(w - f, 0);
        foldPath.lineTo(w, 0);
        foldPath.lineTo(w, f);
        foldPath.close();

        foldOutlinePath.reset();
        foldOutlinePath.moveTo(w - f, 0);
        foldOutlinePath.lineTo(w, 0);
        foldOutlinePath.lineTo(w, f);
        foldOutlinePath.close();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, fillPaint);
        canvas.drawPath(foldPath, foldPaint);
        canvas.drawPath(path, strokePaint);
        if (showFoldOutline && foldOutlinePaint != null) {
            canvas.drawPath(foldOutlinePath, foldOutlinePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        foldPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
