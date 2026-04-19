package com.notara.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.notara.NoteRepository;
import com.notara.NoteRepositoryImpl;
import com.notara.DatabaseHelper;
import com.notara.R;

import java.util.ArrayList;
import java.util.List;

public class NoteWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new NoteRemoteViewsFactory(this.getApplicationContext(), intent);
    }

    class NoteRemoteViewsFactory implements RemoteViewsFactory {
        private final Context context;
        private final int noteId;
        private List<String> items = new ArrayList<>();
        private List<Boolean> checkedStatus = new ArrayList<>();

        public NoteRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.noteId = intent.getIntExtra("NOTE_ID", -1);
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
            if (noteId == -1) return;
            NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
            DatabaseHelper.Note note = repository.getNote(noteId);
            items.clear();
            checkedStatus.clear();
            if (note != null && note.type == 1 && note.content != null) {
                String[] lines = note.content.split("\n");
                for (String line : lines) {
                    if (line.contains("::")) {
                        String[] parts = line.split("::");
                        items.add(parts[0]);
                        checkedStatus.add(parts.length > 1 && parts[1].equals("1"));
                    } else if (!line.trim().isEmpty()) {
                        items.add(line);
                        checkedStatus.add(false);
                    }
                }
            }
        }

        @Override public void onDestroy() { items.clear(); }
        @Override public int getCount() { return items.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);
            views.setTextViewText(R.id.widget_item_text, items.get(position));
            
            boolean checked = checkedStatus.get(position);
            views.setImageViewResource(R.id.widget_item_checkbox, 
                checked ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_blank);
            
            // Intent para clique no item
            Bundle extras = new Bundle();
            extras.putInt("NOTE_ID", noteId);
            extras.putInt("ITEM_INDEX", position);
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            views.setOnClickFillInIntent(R.id.widget_item_checkbox, fillInIntent);
            views.setOnClickFillInIntent(R.id.widget_item_text, fillInIntent);

            return views;
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 1; }
        @Override public long getItemId(int position) { return position; }
        @Override public boolean hasStableIds() { return true; }
    }
}
