package com.notara;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawingView extends View {

    private static final int DEFAULT_COLOR = Color.RED;
    private static final float DEFAULT_WIDTH = 8f;

    private final List<Path> paths = new ArrayList<>();
    private final List<Paint> paints = new ArrayList<>();
    private Path currentPath;
    private Paint currentPaint;
    private boolean drawingMode = false;
    private boolean eraserMode = false;
    private int currentColor = DEFAULT_COLOR;
    private float strokeWidth = DEFAULT_WIDTH;

    public DrawingView(Context context) {
        super(context);
        init();
    }

    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        currentPaint = new Paint();
        currentPaint.setAntiAlias(true);
        currentPaint.setDither(true);
        currentPaint.setStyle(Paint.Style.STROKE);
        currentPaint.setStrokeJoin(Paint.Join.ROUND);
        currentPaint.setStrokeCap(Paint.Cap.ROUND);
        currentPaint.setColor(currentColor);
        currentPaint.setStrokeWidth(strokeWidth);
    }

    public void setDrawingMode(boolean enabled) {
        this.drawingMode = enabled;
    }

    public boolean isDrawingMode() {
        return drawingMode;
    }

    public void setEraser(boolean eraser) {
        this.eraserMode = eraser;
        if (eraser) {
            currentPaint.setColor(0xFF000000);
            currentPaint.setAlpha(0);
            currentPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
        } else {
            currentPaint.setColor(currentColor);
            currentPaint.setAlpha(255);
            currentPaint.setXfermode(null);
        }
        currentPaint.setStrokeWidth(strokeWidth);
    }

    public boolean isEraser() {
        return eraserMode;
    }

    public void setCurrentColor(int color) {
        this.currentColor = color;
        if (!eraserMode) {
            currentPaint.setColor(color);
            currentPaint.setAlpha(255);
        }
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public void setStrokeWidth(float width) {
        this.strokeWidth = width;
        currentPaint.setStrokeWidth(width);
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void undo() {
        if (!paths.isEmpty()) {
            paths.remove(paths.size() - 1);
            paints.remove(paints.size() - 1);
            invalidate();
        }
    }

    public void clearAll() {
        paths.clear();
        paints.clear();
        currentPath = null;
        invalidate();
    }

    public Bitmap getDrawingBitmap(int width, int height) {
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        for (int i = 0; i < paths.size(); i++) {
            c.drawPath(paths.get(i), paints.get(i));
        }
        return bmp;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!drawingMode) return false;

        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                Paint p = new Paint(currentPaint);
                if (eraserMode) {
                    p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR));
                }
                paths.add(currentPath);
                paints.add(p);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (currentPath != null) {
                    currentPath.lineTo(x, y);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                currentPath = null;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
        for (int i = 0; i < paths.size(); i++) {
            canvas.drawPath(paths.get(i), paints.get(i));
        }
    }
}
