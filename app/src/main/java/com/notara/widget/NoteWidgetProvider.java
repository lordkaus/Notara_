package com.notara.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.notara.ChecklistActivity;
import com.notara.DatabaseHelper;
import com.notara.EditActivity;
import com.notara.MainActivity;
import com.notara.NoteRepository;
import com.notara.NoteRepositoryImpl;
import com.notara.R;
import com.notara.SettingsManager;

public class NoteWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_CLICK = "com.notara.ACTION_WIDGET_CLICK";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_WIDGET_CLICK.equals(intent.getAction())) {
            int noteId = intent.getIntExtra("NOTE_ID", -1);
            int itemIndex = intent.getIntExtra("ITEM_INDEX", -1);

            if (noteId != -1) {
                if (itemIndex != -1) {
                    // Clique em um item do checklist
                    toggleChecklistItem(context, noteId, itemIndex);
                } else {
                    // Clique geral no widget - abre a nota
                    NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
                    DatabaseHelper.Note note = repository.getNote(noteId);
                    if (note != null) {
                        Intent openIntent = new Intent(context, note.type == 1 ? ChecklistActivity.class : EditActivity.class);
                        openIntent.putExtra("NOTE_ID", note.id);
                        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(openIntent);
                    }
                }
            }
        }
    }

    private void toggleChecklistItem(Context context, int noteId, int index) {
        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        DatabaseHelper.Note note = repository.getNote(noteId);
        if (note != null && note.type == 1) {
            String[] lines = note.content.split("\n");
            if (index >= 0 && index < lines.length) {
                String line = lines[index];
                if (line.contains("::")) {
                    String[] parts = line.split("::");
                    boolean checked = parts.length > 1 && parts[1].equals("1");
                    lines[index] = parts[0] + "::" + (checked ? "0" : "1");
                    
                    StringBuilder newContent = new StringBuilder();
                    for (int i = 0; i < lines.length; i++) {
                        newContent.append(lines[i]);
                        if (i < lines.length - 1) newContent.append("\n");
                    }
                    note.content = newContent.toString();
                    repository.updateNote(note);
                    
                    // Notifica atualização
                    updateAllWidgets(context);
                }
            }
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids1x1 = manager.getAppWidgetIds(new ComponentName(context, NoteWidgetProvider.class));
        int[] ids1x2 = manager.getAppWidgetIds(new ComponentName(context, NoteWidgetProvider1x2.class));
        int[] ids2x2 = manager.getAppWidgetIds(new ComponentName(context, NoteWidgetProvider2x2.class));
        
        for (int id : ids1x1) updateAppWidget(context, manager, id);
        for (int id : ids1x2) updateAppWidget(context, manager, id);
        for (int id : ids2x2) updateAppWidget(context, manager, id);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SettingsManager settings = new SettingsManager(context);
        int noteId = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).getInt("note_" + appWidgetId, -1);
        
        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        DatabaseHelper.Note note = (noteId == -1) ? repository.getLatestNote() : repository.getNote(noteId);

        // Detecta o layout baseado no tamanho do widget
        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        
        int layoutId;
        boolean isAllNotesList = false;
        if (minHeight < 100) {
            layoutId = R.layout.widget_memo_small; // 1x1
        } else if (minHeight < 200) {
            layoutId = R.layout.widget_all_notes; // 1x2 Dinâmico (Lista de todas as notas)
            isAllNotesList = true;
        } else {
            layoutId = R.layout.widget_note_2x2; // 2x2 Fixo
        }
        
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutId);

        // Cor de Fundo e Transparência (Comum a todos)
        int alpha = (int) (settings.getTransparency() * 2.55); // 0-100 to 0-255
        int cardStyle = settings.getCardStyle();

        if (isAllNotesList) {
            // Widget 1x2: Lista de Todas as Notas
            views.setInt(R.id.widget_root, "setBackgroundColor", Color.argb(alpha, 18, 18, 18));
            views.setInt(R.id.widget_color_bar, "setBackgroundColor", Color.parseColor("#4DB6AC")); // Cor Notara_
            
            Intent serviceIntent = new Intent(context, AllNotesWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
            views.setRemoteAdapter(R.id.widget_all_notes_list, serviceIntent);
            
            // Template para abrir notas da lista
            Intent clickIntent = new Intent(context, NoteWidgetProvider.class);
            clickIntent.setAction(ACTION_WIDGET_CLICK);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 2000, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.widget_all_notes_list, pendingIntent);
            
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_all_notes_list);

        } else if (note != null) {
            // Widgets 1x1 e 2x2 (Fixos em uma nota)
            views.setTextViewText(R.id.widget_title, note.isLocked == 1 ? "* Nota Protegida" : note.title);
            
            int noteColor = Color.parseColor(EditActivity.noteColors[note.color]);
            
            if (cardStyle == 1) { // Pastel
                views.setViewVisibility(R.id.widget_color_bar, View.GONE);
                float[] hsl = new float[3];
                androidx.core.graphics.ColorUtils.colorToHSL(noteColor, hsl);
                hsl[1] *= 0.4f;
                hsl[2] = 0.9f;
                int pastelColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl);
                int finalColor = Color.argb(alpha, Color.red(pastelColor), Color.green(pastelColor), Color.blue(pastelColor));
                views.setInt(R.id.widget_root, "setBackgroundColor", finalColor);
                views.setTextColor(R.id.widget_title, 0xFF333333);
                views.setTextColor(R.id.widget_content, 0xFF555555);
            } else if (cardStyle == 2) { // Solid
                views.setViewVisibility(R.id.widget_color_bar, View.GONE);
                int finalColor = Color.argb(alpha, Color.red(noteColor), Color.green(noteColor), Color.blue(noteColor));
                views.setInt(R.id.widget_root, "setBackgroundColor", finalColor);
                views.setTextColor(R.id.widget_title, Color.BLACK); // Letras pretas para Solid
                views.setTextColor(R.id.widget_content, 0xFF222222);
            } else { // Label (Padrão)
                views.setViewVisibility(R.id.widget_color_bar, View.VISIBLE);
                // No widget, o fundo padrão é escuro
                int finalColor = Color.argb(alpha, 18, 18, 18);
                views.setInt(R.id.widget_root, "setBackgroundColor", finalColor);
                views.setInt(R.id.widget_color_bar, "setBackgroundColor", noteColor);
                views.setTextColor(R.id.widget_title, Color.WHITE);
                views.setTextColor(R.id.widget_content, 0xFFBBBBBB);
            }

            if (note.isLocked == 1) {
                views.setTextViewText(R.id.widget_content, "Autentique-se no app para ver");
                views.setViewVisibility(R.id.widget_list, View.GONE);
                views.setViewVisibility(R.id.widget_content, View.VISIBLE);
            } else if (note.type == 1) {
                // Lista/Checklist
                views.setViewVisibility(R.id.widget_content, View.GONE);
                views.setViewVisibility(R.id.widget_list, View.VISIBLE);
                
                Intent serviceIntent = new Intent(context, NoteWidgetService.class);
                serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                serviceIntent.putExtra("NOTE_ID", note.id);
                serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
                views.setRemoteAdapter(R.id.widget_list, serviceIntent);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);
            } else {
                // Nota de texto
                views.setViewVisibility(R.id.widget_list, View.GONE);
                views.setViewVisibility(R.id.widget_content, View.VISIBLE);
                views.setTextViewText(R.id.widget_content, note.content);
            }

            // Click Intent para o widget todo
            Intent clickIntent = new Intent(context, NoteWidgetProvider.class);
            clickIntent.setAction(ACTION_WIDGET_CLICK);
            clickIntent.putExtra("NOTE_ID", note.id);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
            
            // Template para cliques na lista (para os checkboxes)
            Intent listClickIntent = new Intent(context, NoteWidgetProvider.class);
            listClickIntent.setAction(ACTION_WIDGET_CLICK);
            listClickIntent.putExtra("NOTE_ID", note.id);
            PendingIntent listPendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 1000, listClickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            views.setPendingIntentTemplate(R.id.widget_list, listPendingIntent);

        } else {
            views.setTextViewText(R.id.widget_title, "Notara_");
            views.setTextViewText(R.id.widget_content, "Nenhuma nota encontrada.");
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
