package com.notara;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;

public class NoteViewModel extends AndroidViewModel {
    private final DatabaseHelper db;
    private final MutableLiveData<List<DatabaseHelper.Note>> notes = new MutableLiveData<>();
    private String currentQuery = "";
    private boolean showTrashed = false;

    public NoteViewModel(@NonNull Application application) {
        super(application);
        db = new DatabaseHelper(application);
        refreshNotes();
    }

    public LiveData<List<DatabaseHelper.Note>> getNotes() {
        return notes;
    }

    public void refreshNotes() {
        notes.setValue(db.searchNotes(currentQuery, showTrashed, null));
        com.notara.widget.NoteWidgetProvider.updateAllWidgets(getApplication());
    }

    public void setQuery(String query) {
        this.currentQuery = query;
        refreshNotes();
    }

    public void toggleTrash(boolean show) {
        this.showTrashed = show;
        refreshNotes();
    }

    public void pinNote(DatabaseHelper.Note note) {
        int newPinned = (note.isPinned == 1) ? 0 : 1;
        DatabaseHelper.Note updated = copyNote(note);
        updated.isPinned = newPinned;
        db.updateNote(updated);
        refreshNotes();
    }

    public void moveToTrash(DatabaseHelper.Note note) {
        DatabaseHelper.Note updated = copyNote(note);
        updated.isTrashed = 1;
        db.updateNote(updated);
        refreshNotes();
    }

    public void restoreNote(DatabaseHelper.Note note) {
        DatabaseHelper.Note updated = copyNote(note);
        updated.isTrashed = 0;
        db.updateNote(updated);
        refreshNotes();
    }

    public void toggleLock(DatabaseHelper.Note note) {
        int newLocked = (note.isLocked == 1) ? 0 : 1;
        DatabaseHelper.Note updated = copyNote(note);
        updated.isLocked = newLocked;

        try {
            if (newLocked == 1) {
                // Ao trancar, criptografa o conteúdo atual
                updated.content = SecurityCore.encrypt(note.content);
            } else {
                // Ao destrancar, descriptografa o conteúdo para texto simples
                updated.content = SecurityCore.decrypt(note.content);
            }
            db.updateNote(updated);
            refreshNotes();
        } catch (Exception e) {
            e.printStackTrace();
            // Em caso de erro (ex: falha no KeyStore), não altera o estado para não corromper os dados
        }
    }

    private DatabaseHelper.Note copyNote(DatabaseHelper.Note n) {
        return new DatabaseHelper.Note(n.id, n.title, n.content, n.type, n.color, n.isPinned, n.isTrashed, n.tag, n.reminderTime, n.recurrenceType, n.recurrenceDays, n.attachments, n.isLocked, n.alertType, n.lastModified, n.originalReminderTime);
    }
}
