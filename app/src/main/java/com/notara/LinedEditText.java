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

    public LinedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        mRect = new Rect();
        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(2.5f); // Espessura ligeiramente maior e uniforme
        mPaint.setAntiAlias(true);
        // Amarelo mais visível por padrão (opacidade 0x73 = ~45%)
        mPaint.setColor(0x73FFEB3B); 
    }

    public void setLineColor(int color) {
        // Aplica a cor com 45% de opacidade (0x73) para ser "discreto mas visível"
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
        
        // Pega a posição base da primeira linha
        int baseline = getLineBounds(0, r);

        // Desenha as linhas com precisão uniforme
        for (int i = 0; i < numberOfLines; i++) {
            // Desenha a linha exatamente na mesma posição relativa ao texto
            canvas.drawLine(r.left, baseline + 12, r.right, baseline + 12, paint);
            baseline += lineHeight;
        }

        super.onDraw(canvas);
    }
}
