package com.notara;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;

public class ImageViewerActivity extends AppCompatActivity {

    private ImageView imageView;
    private DrawingView drawingView;
    private View container;
    private LinearLayout toolbar;
    private Bitmap originalBitmap;
    private String filePath;
    private boolean isDrawingMode = false;

    private float scale = 1f;
    private ScaleGestureDetector scaleDetector;
    private float lastX, lastY;
    private float transX, transY;

    private static final int[] COLORS = {Color.BLACK, Color.WHITE, Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW};
    private ImageButton[] colorButtons;
    private int selectedColorIndex = 2;

    private View selectedStrokeView;
    private Button btnEraser;
    private ImageButton btnUndo, btnClear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        imageView = findViewById(R.id.ivFullImage);
        drawingView = findViewById(R.id.drawingView);
        container = findViewById(R.id.container);
        toolbar = findViewById(R.id.toolbar);

        filePath = getIntent().getStringExtra("file_path");
        if (filePath != null) {
            originalBitmap = BitmapFactory.decodeFile(filePath);
            if (originalBitmap != null) imageView.setImageBitmap(originalBitmap);
        }

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                scale *= detector.getScaleFactor();
                scale = Math.max(0.5f, Math.min(scale, 5f));
                applyTransform();
                return true;
            }
        });

        buildToolbar();
        toolbar.setVisibility(View.VISIBLE);
    }

    private void applyTransform() {
        container.setScaleX(scale);
        container.setScaleY(scale);
        container.setTranslationX(transX);
        container.setTranslationY(transY);
    }

    private void buildToolbar() {
        toolbar.removeAllViews();

        ImageButton btnToggle = createToolbarButton();
        btnToggle.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_notes));
        btnToggle.setColorFilter(Color.WHITE);
        btnToggle.setOnClickListener(v -> toggleDrawingMode());
        toolbar.addView(btnToggle);
        toolbar.addView(createDivider());

        colorButtons = new ImageButton[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            ImageButton btn = createColorCircle(COLORS[i]);
            btn.setOnClickListener(v -> selectColor(index));
            colorButtons[i] = btn;
            toolbar.addView(btn);
            if (i < COLORS.length - 1) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(4, 0));
                toolbar.addView(spacer);
            }
        }
        
        toolbar.addView(createDivider());

        selectedStrokeView = null;
        for (int w : new int[]{3, 8, 16}) {
            final int width = w;
            View strokeView = new View(this);
            int size = 28;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(3, 0, 3, 0);
            strokeView.setLayoutParams(lp);
            strokeView.setBackground(createCircleDrawable(Color.WHITE, false, 1f));
            strokeView.setTag("stroke_" + w);
            strokeView.setOnClickListener(v -> {
                drawingView.setStrokeWidth(width);
                highlightStrokeButton(strokeView);
            });
            toolbar.addView(strokeView);
            if (w == 8) {
                highlightStrokeButton(strokeView);
            }
        }

        toolbar.addView(createDivider());

        btnEraser = new Button(this, null, android.R.attr.borderlessButtonStyle);
        {
            int size = 40;
            btnEraser.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            btnEraser.setText("E");
            btnEraser.setTextColor(Color.WHITE);
            btnEraser.setTextSize(14);
            btnEraser.setGravity(Gravity.CENTER);
            btnEraser.setPadding(0, 0, 0, 0);
        }
        btnEraser.setOnClickListener(v -> toggleEraser());
        toolbar.addView(btnEraser);
        selectColor(selectedColorIndex);

        btnUndo = createToolbarButton();
        btnUndo.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_revert));
        btnUndo.setColorFilter(Color.WHITE);
        btnUndo.setOnClickListener(v -> { drawingView.undo(); });
        toolbar.addView(btnUndo);

        btnClear = createToolbarButton();
        btnClear.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_close));
        btnClear.setColorFilter(Color.WHITE);
        btnClear.setOnClickListener(v -> { drawingView.clearAll(); });
        toolbar.addView(btnClear);

        ImageButton btnSave = createToolbarButton();
        btnSave.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_save));
        btnSave.setColorFilter(Color.WHITE);
        btnSave.setOnClickListener(v -> saveAnnotation());
        toolbar.addView(btnSave);
    }

    private ImageButton createToolbarButton() {
        ImageButton btn = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        int size = 40;
        btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        btn.setPadding(6, 6, 6, 6);
        return btn;
    }

    private ImageButton createColorCircle(int color) {
        ImageButton btn = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        int size = 32;
        btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        btn.setBackground(createCircleDrawable(color, color == Color.WHITE, 2f));
        btn.setPadding(0, 0, 0, 0);
        btn.setScaleType(ImageView.ScaleType.FIT_CENTER);
        return btn;
    }

    private GradientDrawable createCircleDrawable(int color, boolean border, float strokeWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        if (border) d.setStroke((int) strokeWidthPx, Color.GRAY);
        return d;
    }

    private View createDivider() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 32));
        v.setBackgroundColor(0x55FFFFFF);
        return v;
    }

    private void selectColor(int index) {
        selectedColorIndex = index;
        drawingView.setCurrentColor(COLORS[index]);
        drawingView.setEraser(false);
        btnEraser.setTextColor(Color.WHITE);
        btnEraser.setSelected(false);
        for (int i = 0; i < colorButtons.length; i++) {
            colorButtons[i].setAlpha(i == index ? 1f : 0.4f);
        }
    }

    private void highlightStrokeButton(View v) {
        if (selectedStrokeView != null) {
            selectedStrokeView.setAlpha(0.4f);
        }
        selectedStrokeView = v;
        v.setAlpha(1f);
    }

    private void toggleEraser() {
        boolean erasing = !drawingView.isEraser();
        drawingView.setEraser(erasing);
        btnEraser.setTextColor(erasing ? Color.YELLOW : Color.WHITE);
        btnEraser.setSelected(erasing);
    }

    private void toggleDrawingMode() {
        isDrawingMode = !isDrawingMode;
        drawingView.setDrawingMode(isDrawingMode);
        drawingView.setVisibility(isDrawingMode ? View.VISIBLE : View.GONE);
        if (!isDrawingMode) {
            resetViewTransform();
        }
    }

    private void resetViewTransform() {
        scale = 1f;
        transX = 0;
        transY = 0;
        applyTransform();
    }

    private void saveAnnotation() {
        if (originalBitmap == null || filePath == null) return;

        int w = originalBitmap.getWidth();
        int h = originalBitmap.getHeight();

        Bitmap combined = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(combined);
        c.drawBitmap(originalBitmap, 0, 0, null);
        Bitmap drawingBmp = drawingView.getDrawingBitmap(w, h);
        c.drawBitmap(drawingBmp, 0, 0, null);
        drawingBmp.recycle();

        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            combined.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            Toast.makeText(this, "Anotação salva", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        combined.recycle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDrawingMode) return drawingView.onTouchEvent(event) || super.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(); lastY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    transX += dx; transY += dy;
                    applyTransform();
                    lastX = event.getX(); lastY = event.getY();
                }
                break;
        }
        return true;
    }
}
