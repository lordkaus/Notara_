package com.notara.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;

import com.notara.DatabaseHelper;
import com.notara.R;

import java.util.List;

public class WidgetConfigActivity extends AppCompatActivity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Se o usuário cancelar, não cria o widget
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

        // Interface simples para seleção
        DatabaseHelper db = new DatabaseHelper(this);
        List<DatabaseHelper.Note> notes = db.searchNotes("", false, null);
        
        String[] titles = new String[notes.size()];
        for (int i = 0; i < notes.size(); i++) {
            titles[i] = notes.get(i).title.isEmpty() ? "(Sem título)" : notes.get(i).title;
        }

        ListView lv = new ListView(this);
        lv.setPadding(32, 32, 32, 32);
        lv.setBackgroundColor(Color.parseColor("#121212")); // Fundo escuro para combinar com o app
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, titles) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                android.widget.TextView text = view.findViewById(android.R.id.text1);
                text.setTextColor(Color.WHITE);
                return view;
            }
        };
        
        setContentView(lv);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((parent, view, position, id) -> {
            DatabaseHelper.Note selected = notes.get(position);
            
            // Salva a preferência do widget
            getSharedPreferences("widget_prefs", MODE_PRIVATE)
                .edit()
                .putInt("note_" + appWidgetId, selected.id)
                .apply();

            // Atualiza o widget imediatamente
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            NoteWidgetProvider.updateAppWidget(this, manager, appWidgetId);

            // Retorna sucesso
            Intent resultValue = new Intent();
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, resultValue);
            finish();
        });
    }
}
