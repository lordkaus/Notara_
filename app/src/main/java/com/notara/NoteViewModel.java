package com.notara;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NoteViewModel extends AndroidViewModel {
    private final NoteRepository repository;
    private final MutableLiveData<List<DatabaseHelper.Note>> notes = new MutableLiveData<>();
    private String currentQuery = "";
    private boolean showTrashed = false;
    private Set<Integer> filterColors;
    private Set<Integer> filterTypes;

    public NoteViewModel(@NonNull Application application) {
        super(application);
        repository = new NoteRepositoryImpl(new DatabaseHelper(application));
        refreshNotes(false);
    }

    public LiveData<List<DatabaseHelper.Note>> getNotes() {
        return notes;
    }

    public String getCurrentQuery() {
        return currentQuery;
    }

    public void refreshNotes() {
        refreshNotes(false);
    }

    public void refreshNotes(boolean skipWidgetUpdate) {
        notes.setValue(repository.searchNotes(currentQuery, showTrashed, null, filterColors, filterTypes));
        if (!skipWidgetUpdate)
            com.notara.widget.NoteWidgetProvider.updateAllWidgets(getApplication());
    }

    public void setQuery(String query, boolean skipWidgetUpdate) {
        this.currentQuery = query;
        refreshNotes(skipWidgetUpdate);
    }

    public void setTypeFilter(Set<Integer> types) {
        this.filterTypes = types;
        refreshNotes(true);
    }

    public void setColorFilter(Set<Integer> colors) {
        this.filterColors = colors;
        refreshNotes(true);
    }

    public void clearFilters() {
        this.filterColors = null;
        this.filterTypes = null;
        refreshNotes(true);
    }

    public void toggleTrash(boolean show) {
        this.showTrashed = show;
        refreshNotes(false);
    }

    public void pinNote(DatabaseHelper.Note note) {
        int newPinned = (note.isPinned == 1) ? 0 : 1;
        DatabaseHelper.Note updated = copyNote(note);
        updated.isPinned = newPinned;
        repository.updateNote(updated);
        refreshNotes(false);
    }

    public void moveToTrash(DatabaseHelper.Note note) {
        DatabaseHelper.Note updated = copyNote(note);
        updated.isTrashed = 1;
        repository.updateNote(updated);
        refreshNotes(false);
    }

    public void restoreNote(DatabaseHelper.Note note) {
        DatabaseHelper.Note updated = copyNote(note);
        updated.isTrashed = 0;
        repository.updateNote(updated);
        refreshNotes(false);
    }

    public void deleteNoteForever(int id) {
        repository.deleteNoteForever(id);
        refreshNotes(false);
    }

    public DatabaseHelper.Note getNote(int id) {
        return repository.getNote(id);
    }

    public long addNote(DatabaseHelper.Note note) {
        long id = repository.addNote(note);
        com.notara.widget.NoteWidgetProvider.updateAllWidgets(getApplication());
        refreshNotes(false);
        return id;
    }

    public void updateNote(DatabaseHelper.Note note) {
        repository.updateNote(note);
        refreshNotes(false);
    }

    public List<DatabaseHelper.Note> getScheduledNotesUpTo(long endTime) {
        return repository.getScheduledNotesUpTo(endTime);
    }

    public List<DatabaseHelper.Note> getRecurringNotes() {
        return repository.getRecurringNotes();
    }

    public void clearTrash() {
        repository.clearTrash();
        refreshNotes(false);
    }

    public void resetAllNotes() {
        repository.resetAllNotes();
        refreshNotes(false);
    }

    public void toggleLock(DatabaseHelper.Note note) {
        int newLocked = (note.isLocked == 1) ? 0 : 1;
        DatabaseHelper.Note updated = copyNote(note);
        updated.isLocked = newLocked;

        try {
            if (newLocked == 1) {
                updated.content = SecurityCore.encrypt(note.content);
            } else {
                updated.content = SecurityCore.decrypt(note.content);
            }
            repository.updateNote(updated);
            refreshNotes(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private DatabaseHelper.Note copyNote(DatabaseHelper.Note n) {
        return new DatabaseHelper.Note(n.id, n.title, n.content, n.type, n.color, n.isPinned, n.isTrashed, n.tag, n.reminderTime, n.recurrenceType, n.recurrenceDays, n.attachments, n.isLocked, n.alertType, n.lastModified, n.originalReminderTime, n.alarmTime, n.alarmRecurrenceType, n.alarmRecurrenceDays, n.alarmOriginalReminderTime);
    }
}
