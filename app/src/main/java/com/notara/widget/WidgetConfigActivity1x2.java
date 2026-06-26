package com.notara.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.notara.EditActivity;
import com.notara.R;

public class WidgetConfigActivity1x2 extends AppCompatActivity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_widget_config_1x2);

        TextView title = findViewById(R.id.tvTitle);
        title.setTextColor(Color.WHITE);

        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#121212"));

        GridView gridView = findViewById(R.id.colorGrid);
        gridView.setAdapter(new ColorAdapter());

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            getSharedPreferences("widget_prefs", MODE_PRIVATE)
                .edit()
                .putInt("color_" + appWidgetId, position)
                .commit();

            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            NoteWidgetProvider.updateAppWidget(this, manager, appWidgetId);

            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }

    private class ColorAdapter extends BaseAdapter {
        @Override
        public int getCount() { return EditActivity.noteColors.length; }

        @Override
        public Object getItem(int position) { return EditActivity.noteColors[position]; }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = new View(WidgetConfigActivity1x2.this);
                int size = (int) (56 * getResources().getDisplayMetrics().density);
                view.setLayoutParams(new GridView.LayoutParams(size, size));
            }

            int color = Color.parseColor(EditActivity.noteColors[position]);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            shape.setStroke(3, Color.WHITE);
            view.setBackground(shape);
            view.setElevation(4 * getResources().getDisplayMetrics().density);

            return view;
        }
    }
}
