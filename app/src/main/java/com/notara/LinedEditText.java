package com.notara;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;

public class LinedEditText extends AppCompatEditText {
    private Rect mRect;
    private Paint mPaint;
    private Paint mMarginPaint;
    private float density;

    public LinedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRect = new Rect();
        density = context.getResources().getDisplayMetrics().density;

        // Linhas Horizontais
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(Math.max(1f, 1.0f * density)); 
        mPaint.setAntiAlias(true);
        mPaint.setColor(0x66CCCCCC); // cinza mais visível (40% opaco)

        // Linha de Margem Vertical (invisível)
        mMarginPaint = new Paint();
        mMarginPaint.setStyle(Paint.Style.STROKE);
        mMarginPaint.setStrokeWidth(Math.max(1f, 0.8f * density));
        mMarginPaint.setAntiAlias(true);
        mMarginPaint.setColor(0x00FF5252); // completamente transparente 
    }

    public void setLineColor(int color) {
        mPaint.setColor((color & 0x00FFFFFF) | 0x73000000);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int height = getHeight();
        int lineHeight = getLineHeight();
        int numberOfLines = height / lineHeight;
        
        if (getLineCount() > numberOfLines) {
            numberOfLines = getLineCount();
        }

        Rect r = mRect;
        Paint paint = mPaint;
        
        // Margem Vertical removida

        int baseline = getLineBounds(0, r);
        float offset = (getLineHeight() - getTextSize()) / 2 + 2 * density;

        for (int i = 0; i < numberOfLines; i++) {
            canvas.drawLine(r.left, baseline + offset, r.right, baseline + offset, paint);
            baseline += lineHeight;
        }

        super.onDraw(canvas);
    }
}
