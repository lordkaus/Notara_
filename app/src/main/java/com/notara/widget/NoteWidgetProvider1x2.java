package com.notara.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.widget.RemoteViews;
import com.notara.R;

public class NoteWidgetProvider1x2 extends NoteWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            // Poderíamos passar um layout específico aqui se quiséssemos
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
