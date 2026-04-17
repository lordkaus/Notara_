package com.notara.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.notara.DatabaseHelper;
import com.notara.R;

import java.util.ArrayList;
import java.util.List;

public class AllNotesWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new AllNotesRemoteViewsFactory(this.getApplicationContext());
    }

    class AllNotesRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private List<DatabaseHelper.Note> notes = new ArrayList<>();

        public AllNotesRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        private void loadData() {
            DatabaseHelper db = new DatabaseHelper(context);
            // Busca todas as notas (não excluídas), ordenadas por modificação
            notes = db.searchNotes("", false, null);
        }

        @Override public void onDestroy() { notes.clear(); }
        @Override public int getCount() { return notes.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= notes.size()) return null;
            
            DatabaseHelper.Note note = notes.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
            
            String title = note.isLocked == 1 ? "* Nota Protegida" : (note.title.isEmpty() ? "(Sem título)" : note.title);
            views.setTextViewText(android.R.id.text1, title);
            views.setTextColor(android.R.id.text1, 0xFFFFFFFF);
            views.setTextViewTextSize(android.R.id.text1, android.util.TypedValue.COMPLEX_UNIT_SP, 13);

            // Intent para abrir a nota ao clicar
            Bundle extras = new Bundle();
            extras.putInt("NOTE_ID", note.id);
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            views.setOnClickFillInIntent(android.R.id.text1, fillInIntent);

            return views;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
    }
}
