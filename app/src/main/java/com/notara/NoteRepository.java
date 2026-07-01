package com.notara;

import java.util.List;
import java.util.Set;

public interface NoteRepository {
    long addNote(DatabaseHelper.Note note);
    void updateNote(DatabaseHelper.Note note);
    void clearTrash();
    void deleteNoteForever(int id);
    void restoreNote(int id);
    void resetAllNotes();
    
    DatabaseHelper.Note getNote(int id);
    DatabaseHelper.Note getLatestNote();
    List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag);
    List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag, Set<Integer> filterColors, Set<Integer> filterTypes);
    List<DatabaseHelper.Note> getScheduledNotesUpTo(long endTime);
    List<DatabaseHelper.Note> getRecurringNotes();
    List<DatabaseHelper.Note> getNotesForDateRange(long start, long end);
}
