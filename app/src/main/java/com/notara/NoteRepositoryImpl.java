package com.notara;

import android.content.Context;

import com.notara.db.AppDatabase;
import com.notara.db.AttachmentDao;
import com.notara.db.AttachmentEntity;
import com.notara.db.ChecklistItemDao;
import com.notara.db.ChecklistItemEntity;
import com.notara.db.NoteDao;
import com.notara.db.NoteEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * NoteRepository implementation.
 *
 * Single-backend policy: production ALWAYS uses the legacy DatabaseHelper,
 * which is the only engine allowed to open the "notes.db" file. Room
 * (AppDatabase/DAOs/entities) is kept for unit tests only (in-memory DB).
 * Opening the same file with both engines causes version conflicts
 * (Room bumps user_version; legacy then crashes with "Can't downgrade")
 * and destructive-migration data loss, so the Context constructor below
 * deliberately delegates to DatabaseHelper instead of Room.
 */
public class NoteRepositoryImpl implements NoteRepository {
    private final NoteDao noteDao;
    private final ChecklistItemDao checklistItemDao;
    private final AttachmentDao attachmentDao;

    /** Legacy constructor — still works for callers that pass DatabaseHelper */
    public NoteRepositoryImpl(DatabaseHelper db) {
        // Fallback: use the legacy DatabaseHelper directly
        this.noteDao = null;
        this.checklistItemDao = null;
        this.attachmentDao = null;
        this.legacyDb = db;
    }

    /** Room constructor — DEPRECATED for production: delegates to the
     * legacy DatabaseHelper so a single engine owns "notes.db".
     * (See class javadoc. Room stays available for unit tests.) */
    public NoteRepositoryImpl(Context context) {
        this(new DatabaseHelper(context.getApplicationContext()));
    }

    // Legacy fallback field
    private final DatabaseHelper legacyDb;

    private boolean isRoom() { return noteDao != null; }

    // --- Note operations ---

    @Override
    public long addNote(DatabaseHelper.Note note) {
        if (isRoom()) {
            NoteEntity entity = NoteEntity.fromNote(note);
            entity.id = 0; // let Room auto-generate
            return noteDao.insert(entity);
        }
        return legacyDb.addNote(note);
    }

    @Override
    public void updateNote(DatabaseHelper.Note note) {
        if (isRoom()) {
            noteDao.update(NoteEntity.fromNote(note));
        } else {
            legacyDb.updateNote(note);
        }
    }

    @Override
    public void deleteNoteForever(int id) {
        if (isRoom()) {
            noteDao.deleteById(id);
        } else {
            legacyDb.deleteNoteForever(id);
        }
    }

    @Override
    public void restoreNote(int id) {
        if (isRoom()) {
            NoteEntity e = noteDao.getById(id);
            if (e != null) { e.isTrashed = 0; noteDao.update(e); }
        } else {
            legacyDb.restoreNote(id);
        }
    }

    @Override
    public void clearTrash() {
        if (isRoom()) {
            noteDao.deleteAllTrashed();
        } else {
            legacyDb.clearTrash();
        }
    }

    @Override
    public void resetAllNotes() {
        if (isRoom()) {
            noteDao.deleteAll();
        } else {
            legacyDb.resetAllNotes();
        }
    }

    @Override
    public DatabaseHelper.Note getNote(int id) {
        if (isRoom()) {
            NoteEntity e = noteDao.getById(id);
            return e != null ? e.toNote() : null;
        }
        return legacyDb.getNote(id);
    }

    @Override
    public DatabaseHelper.Note getLatestNote() {
        if (isRoom()) {
            NoteEntity e = noteDao.getLatest();
            return e != null ? e.toNote() : null;
        }
        return legacyDb.getLatestNote();
    }

    @Override
    public List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag) {
        if (isRoom()) {
            return toNotes(noteDao.search(query, includeTrashed, filterTag));
        }
        return legacyDb.searchNotes(query, includeTrashed, filterTag);
    }

    @Override
    public List<DatabaseHelper.Note> searchNotes(String query, boolean includeTrashed, String filterTag,
                                                  Set<Integer> filterColors, Set<Integer> filterTypes) {
        if (isRoom()) {
            List<Integer> colors = filterColors != null ? new ArrayList<>(filterColors) : null;
            List<Integer> types = filterTypes != null ? new ArrayList<>(filterTypes) : null;
            return toNotes(noteDao.searchWithFilters(query, includeTrashed, filterTag, colors, types));
        }
        return legacyDb.searchNotes(query, includeTrashed, filterTag, filterColors, filterTypes);
    }

    @Override
    public List<DatabaseHelper.Note> getScheduledNotesUpTo(long endTime) {
        if (isRoom()) {
            return toNotes(noteDao.getScheduledUpTo(endTime));
        }
        return legacyDb.getScheduledNotesUpTo(endTime);
    }

    @Override
    public List<DatabaseHelper.Note> getRecurringNotes() {
        if (isRoom()) {
            return toNotes(noteDao.getRecurring());
        }
        return legacyDb.getRecurringNotes();
    }

    @Override
    public List<DatabaseHelper.Note> getNotesForDateRange(long start, long end) {
        if (isRoom()) {
            return toNotes(noteDao.getForDateRange(start, end));
        }
        return legacyDb.getNotesForDateRange(start, end);
    }

    // --- Helper ---

    private List<DatabaseHelper.Note> toNotes(List<NoteEntity> entities) {
        List<DatabaseHelper.Note> notes = new ArrayList<>();
        for (NoteEntity e : entities) notes.add(e.toNote());
        return notes;
    }
}
