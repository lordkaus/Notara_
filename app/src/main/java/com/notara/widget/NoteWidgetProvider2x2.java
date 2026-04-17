package com.notara.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

public class NoteWidgetProvider2x2 extends NoteWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
