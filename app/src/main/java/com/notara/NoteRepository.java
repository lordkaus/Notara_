package com.notara;

import java.util.List;

/**
 * Interface de repositório para acesso aos dados de Notas.
 */
public interface NoteRepository {
    long addNote(DatabaseHelper.Note note);
    void updateNote(DatabaseHelper.Note note);
    void clearTrash();
    void deleteNoteForever(int id);
    void restoreNote(int id);
    void resetAllNotes();
    // ...
    
    DatabaseHelper.Note getNote(int id);
    DatabaseHelper.Note getLatestNote();
    List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag);
    List<DatabaseHelper.Note> getScheduledNotesUpTo(long endTime);
    List<DatabaseHelper.Note> getRecurringNotes();
    List<DatabaseHelper.Note> getNotesForDateRange(long start, long end);
}
