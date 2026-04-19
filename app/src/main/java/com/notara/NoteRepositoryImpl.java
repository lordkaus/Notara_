package com.notara;

import java.util.List;

/**
 * Implementação do repositório utilizando DatabaseHelper como fonte de dados.
 */
public class NoteRepositoryImpl implements NoteRepository {
    private final DatabaseHelper db;

    public NoteRepositoryImpl(DatabaseHelper db) {
        this.db = db;
    }

    @Override
    public long addNote(DatabaseHelper.Note note) { return db.addNote(note); }

    @Override
    public void updateNote(DatabaseHelper.Note note) { db.updateNote(note); }

    @Override
    public void deleteNoteForever(int id) { db.deleteNoteForever(id); }

    @Override
    public void restoreNote(int id) { db.restoreNote(id); }

    @Override
    public void clearTrash() { db.clearTrash(); }

    @Override
    public void resetAllNotes() { db.resetAllNotes(); }

    @Override
    public DatabaseHelper.Note getNote(int id) { return db.getNote(id); }

    @Override
    public DatabaseHelper.Note getLatestNote() { return db.getLatestNote(); }

    @Override
    public List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag) {
        return db.searchNotes(query, includeTrashed, filterTag);
    }

    @Override
    public List<DatabaseHelper.Note> getScheduledNotesUpTo(long endTime) {
        return db.getScheduledNotesUpTo(endTime);
    }

    @Override
    public List<DatabaseHelper.Note> getRecurringNotes() {
        return db.getRecurringNotes();
    }

    @Override
    public List<DatabaseHelper.Note> getNotesForDateRange(long start, long end) {
        return db.getNotesForDateRange(start, end);
    }
}
