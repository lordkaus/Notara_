package com.notara;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(Context context) { super(context, "notes.db", null, 9); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT, type INTEGER DEFAULT 0, color INTEGER DEFAULT 0, is_pinned INTEGER DEFAULT 0, is_trashed INTEGER DEFAULT 0, tag TEXT, reminder_time LONG DEFAULT 0, recurrence_type INTEGER DEFAULT 0, recurrence_days INTEGER DEFAULT 0, attachments TEXT, is_locked INTEGER DEFAULT 0, alert_type INTEGER DEFAULT 0, last_modified LONG DEFAULT 0, original_reminder_time LONG DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 7) {
            try { db.execSQL("ALTER TABLE notes ADD COLUMN last_modified LONG DEFAULT 0"); } catch (Exception e) {}
        }
        if (oldV < 8) {
            try { db.execSQL("ALTER TABLE notes ADD COLUMN original_reminder_time LONG DEFAULT 0"); } catch (Exception e) {}
        }
        if (oldV < 9) {
            try { db.execSQL("ALTER TABLE notes ADD COLUMN recurrence_days INTEGER DEFAULT 0"); } catch (Exception e) {}
        }
    }

    public long addNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("title", note.title); v.put("content", note.content); v.put("type", note.type);
        v.put("color", note.color); v.put("is_pinned", note.isPinned); v.put("is_trashed", note.isTrashed);
        v.put("tag", note.tag); v.put("reminder_time", note.reminderTime); 
        v.put("recurrence_type", note.recurrenceType); v.put("recurrence_days", note.recurrenceDays);
        v.put("attachments", note.attachments); v.put("is_locked", note.isLocked); v.put("alert_type", note.alertType);
        v.put("last_modified", System.currentTimeMillis()); v.put("original_reminder_time", note.originalReminderTime);
        return db.insert("notes", null, v);
    }

    public void updateNote(Note note) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("title", note.title); v.put("content", note.content); v.put("type", note.type);
        v.put("color", note.color); v.put("is_pinned", note.isPinned); v.put("is_trashed", note.isTrashed);
        v.put("tag", note.tag); v.put("reminder_time", note.reminderTime); 
        v.put("recurrence_type", note.recurrenceType); v.put("recurrence_days", note.recurrenceDays);
        v.put("attachments", note.attachments); v.put("is_locked", note.isLocked); v.put("alert_type", note.alertType);
        v.put("last_modified", System.currentTimeMillis()); v.put("original_reminder_time", note.originalReminderTime);
        db.update("notes", v, "id=?", new String[]{String.valueOf(note.id)});
    }

    private Note cursorToNote(Cursor c) {
        return new Note(
            c.getInt(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("title")),
            c.getString(c.getColumnIndexOrThrow("content")), c.getInt(c.getColumnIndexOrThrow("type")),
            c.getInt(c.getColumnIndexOrThrow("color")), c.getInt(c.getColumnIndexOrThrow("is_pinned")),
            c.getInt(c.getColumnIndexOrThrow("is_trashed")), c.getString(c.getColumnIndexOrThrow("tag")),
            c.getLong(c.getColumnIndexOrThrow("reminder_time")), 
            c.getInt(c.getColumnIndexOrThrow("recurrence_type")),
            c.getInt(c.getColumnIndexOrThrow("recurrence_days")),
            c.getString(c.getColumnIndexOrThrow("attachments")), c.getInt(c.getColumnIndexOrThrow("is_locked")),
            c.getInt(c.getColumnIndexOrThrow("alert_type")), c.getLong(c.getColumnIndexOrThrow("last_modified")),
            c.getLong(c.getColumnIndexOrThrow("original_reminder_time"))
        );
    }

    public List<Note> searchNotes(String query, boolean includeTrashed, String filterTag) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder sb = new StringBuilder("SELECT * FROM notes WHERE ");
        sb.append("is_trashed = ").append(includeTrashed ? "1" : "0");
        if (filterTag != null) sb.append(" AND tag = '").append(filterTag).append("'");
        if (query != null && !query.isEmpty()) sb.append(" AND (title LIKE '%").append(query).append("%' OR content LIKE '%").append(query).append("%')");
        sb.append(" ORDER BY is_pinned DESC, id DESC");
        
        Cursor c = db.rawQuery(sb.toString(), null);
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    public Note getNote(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query("notes", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        Note note = null;
        if (c != null && c.moveToFirst()) { note = cursorToNote(c); c.close(); }
        return note;
    }

    public List<Note> getScheduledNotesUpTo(long endTime) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM notes WHERE is_trashed = 0 AND reminder_time > 0 AND reminder_time <= ? ORDER BY reminder_time ASC";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(endTime)});
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    public void deleteNoteForever(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public void clearTrash() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("notes", "is_trashed=1", null);
    }

    public void resetAllNotes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("notes", null, null);
    }

    public List<Note> getRecurringNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM notes WHERE is_trashed = 0 AND recurrence_type > 0 ORDER BY last_modified DESC";
        Cursor c = db.rawQuery(query, null);
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    public Note getLatestNote() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM notes WHERE is_trashed = 0 ORDER BY last_modified DESC LIMIT 1";
        Cursor c = db.rawQuery(query, null);
        Note note = null;
        if (c != null && c.moveToFirst()) {
            note = cursorToNote(c);
            c.close();
        }
        return note;
    }

    public void restoreNote(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("is_trashed", 0);
        db.update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Note> getNotesForDateRange(long start, long end) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM notes WHERE is_trashed = 0 AND reminder_time >= ? AND reminder_time <= ? ORDER BY reminder_time ASC";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(start), String.valueOf(end)});
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    public static class Note {
        public int id, type, color, isPinned, isTrashed, isLocked, recurrenceType, recurrenceDays, alertType;
        public String title, content, tag, attachments;
        public long reminderTime, lastModified, originalReminderTime;
        public boolean isGhost = false;

        public Note(int id, String title, String content, int type, int color, int isPinned, int isTrashed, String tag, long reminderTime, int recurrenceType, int recurrenceDays, String attachments, int isLocked, int alertType, long lastModified, long originalReminderTime) {
            this.id = id; this.title = title; this.content = content; this.type = type; this.color = color;
            this.isPinned = isPinned; this.isTrashed = isTrashed; this.tag = tag; this.reminderTime = reminderTime;
            this.recurrenceType = recurrenceType; this.recurrenceDays = recurrenceDays;
            this.attachments = attachments; this.isLocked = isLocked;
            this.alertType = alertType; this.lastModified = lastModified; this.originalReminderTime = originalReminderTime;
        }

        public Note asGhost() {
            Note ghost = new Note(id, title, content, type, color, isPinned, isTrashed, tag, reminderTime, recurrenceType, recurrenceDays, attachments, isLocked, alertType, lastModified, originalReminderTime);
            ghost.isGhost = true;
            return ghost;
        }

        public java.time.LocalDate getLocalDate() {
            long time = originalReminderTime > 0 ? originalReminderTime : reminderTime;
            // Usa o fuso horário do sistema para garantir que o "dia" seja o mesmo que o usuário vê
            return java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
    }
}
