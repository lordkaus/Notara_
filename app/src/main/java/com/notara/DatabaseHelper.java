package com.notara;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(Context context) { super(context, "notes.db", null, 12); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT, type INTEGER DEFAULT 0, color INTEGER DEFAULT 0, is_pinned INTEGER DEFAULT 0, is_trashed INTEGER DEFAULT 0, tag TEXT, reminder_time LONG DEFAULT 0, recurrence_type INTEGER DEFAULT 0, recurrence_days INTEGER DEFAULT 0, attachments TEXT, is_locked INTEGER DEFAULT 0, alert_type INTEGER DEFAULT 0, last_modified LONG DEFAULT 0, original_reminder_time LONG DEFAULT 0, alarm_time LONG DEFAULT 0, alarm_recurrence_type INTEGER DEFAULT 0, alarm_recurrence_days INTEGER DEFAULT 0, alarm_original_time LONG DEFAULT 0)");
        db.execSQL("CREATE TABLE checklist_items (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', checked INTEGER DEFAULT 0, position INTEGER DEFAULT 0, reminder_time LONG DEFAULT 0, alert_type INTEGER DEFAULT 0, recurrence_type INTEGER DEFAULT 0, recurrence_days INTEGER DEFAULT 0, original_reminder_time LONG DEFAULT 0)");
        db.execSQL("CREATE TABLE attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, type INTEGER DEFAULT 0, file_name TEXT, file_path TEXT, mime_type TEXT, file_size LONG DEFAULT 0)");    }

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
        if (oldV < 10) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS checklist_items (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, name TEXT NOT NULL DEFAULT '', checked INTEGER DEFAULT 0, position INTEGER DEFAULT 0, reminder_time LONG DEFAULT 0, alert_type INTEGER DEFAULT 0, recurrence_type INTEGER DEFAULT 0, recurrence_days INTEGER DEFAULT 0, original_reminder_time LONG DEFAULT 0)");
                Cursor c = db.query("notes", new String[]{"id", "content", "reminder_time", "alert_type", "recurrence_type", "recurrence_days", "original_reminder_time"}, "type=1", null, null, null, null);
                if (c.moveToFirst()) do {
                    int noteId = c.getInt(0);
                    String content = c.getString(1);
                    long noteReminder = c.getLong(2);
                    int noteAlertType = c.getInt(3);
                    int noteRecurType = c.getInt(4);
                    int noteRecurDays = c.getInt(5);
                    long noteOrigReminder = c.getLong(6);
                    int pos = 0;
                    boolean migratedNoteReminder = false;
                    if (content != null && !content.isEmpty()) {
                        for (String line : content.split("\n")) {
                            if (line.contains("::")) {
                                String[] parts = line.split("::");
                                String itemName = parts.length >= 1 ? parts[0] : "";
                                boolean checked = parts.length >= 2 && "1".equals(parts[1]);
                                ContentValues v = new ContentValues();
                                v.put("note_id", noteId);
                                v.put("name", itemName);
                                v.put("checked", checked ? 1 : 0);
                                v.put("position", pos);
                                if (!migratedNoteReminder && pos == 0 && noteReminder > 0) {
                                    v.put("reminder_time", noteReminder);
                                    v.put("alert_type", noteAlertType);
                                    v.put("recurrence_type", noteRecurType);
                                    v.put("recurrence_days", noteRecurDays);
                                    v.put("original_reminder_time", noteOrigReminder);
                                    migratedNoteReminder = true;
                                }
                                db.insert("checklist_items", null, v);
                                pos++;
                            }
                        }
                    }
                } while (c.moveToNext());
                c.close();
            } catch (Exception e) { e.printStackTrace(); }
        }
        if (oldV < 11) {
            try { db.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, note_id INTEGER NOT NULL, type INTEGER DEFAULT 0, file_name TEXT, file_path TEXT, mime_type TEXT, file_size LONG DEFAULT 0)"); } catch (Exception e) {}
        }
        if (oldV < 12) {
            try { db.execSQL("ALTER TABLE notes ADD COLUMN alarm_time LONG DEFAULT 0"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE notes ADD COLUMN alarm_recurrence_type INTEGER DEFAULT 0"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE notes ADD COLUMN alarm_recurrence_days INTEGER DEFAULT 0"); } catch (Exception e) {}
            try { db.execSQL("ALTER TABLE notes ADD COLUMN alarm_original_time LONG DEFAULT 0"); } catch (Exception e) {}
            try {
                // Migrate existing data: alert_type=1 (alarm-only) → move reminder_time to alarm_time
                // alert_type=2 (both) → copy reminder_time to alarm_time
                db.execSQL("UPDATE notes SET alarm_time = reminder_time, alarm_original_time = original_reminder_time, alarm_recurrence_type = recurrence_type, alarm_recurrence_days = recurrence_days WHERE alert_type = 2");
                db.execSQL("UPDATE notes SET alarm_time = reminder_time, alarm_original_time = original_reminder_time WHERE alert_type = 1");
                db.execSQL("UPDATE notes SET reminder_time = 0, original_reminder_time = 0, recurrence_type = 0, recurrence_days = 0 WHERE alert_type = 1");
            } catch (Exception e) { e.printStackTrace(); }
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
        v.put("alarm_time", note.alarmTime); v.put("alarm_recurrence_type", note.alarmRecurrenceType);
        v.put("alarm_recurrence_days", note.alarmRecurrenceDays); v.put("alarm_original_time", note.alarmOriginalReminderTime);
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
        v.put("alarm_time", note.alarmTime); v.put("alarm_recurrence_type", note.alarmRecurrenceType);
        v.put("alarm_recurrence_days", note.alarmRecurrenceDays); v.put("alarm_original_time", note.alarmOriginalReminderTime);
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
            c.getLong(c.getColumnIndexOrThrow("original_reminder_time")),
            c.getLong(c.getColumnIndexOrThrow("alarm_time")),
            c.getInt(c.getColumnIndexOrThrow("alarm_recurrence_type")),
            c.getInt(c.getColumnIndexOrThrow("alarm_recurrence_days")),
            c.getLong(c.getColumnIndexOrThrow("alarm_original_time"))
        );
    }

    public List<Note> searchNotes(String query, boolean includeTrashed, String filterTag) {
        return searchNotes(query, includeTrashed, filterTag, null, null);
    }

    public List<Note> searchNotes(String query, boolean includeTrashed, String filterTag, Set<Integer> filterColors, Set<Integer> filterTypes) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder sb = new StringBuilder("SELECT * FROM notes WHERE is_trashed = ?");
        List<String> args = new ArrayList<>();
        args.add(includeTrashed ? "1" : "0");
        if (filterTag != null) { sb.append(" AND tag = ?"); args.add(filterTag); }
        if (query != null && !query.isEmpty()) {
            sb.append(" AND (title LIKE ? OR content LIKE ?)");
            String like = "%" + query + "%";
            args.add(like);
            args.add(like);
        }
        if (filterColors != null && !filterColors.isEmpty()) {
            sb.append(" AND color IN (");
            boolean first = true;
            for (int c : filterColors) {
                if (!first) sb.append(",");
                sb.append("?");
                args.add(String.valueOf(c));
                first = false;
            }
            sb.append(")");
        }
        if (filterTypes != null && !filterTypes.isEmpty()) {
            sb.append(" AND type IN (");
            boolean first = true;
            for (int t : filterTypes) {
                if (!first) sb.append(",");
                sb.append("?");
                args.add(String.valueOf(t));
                first = false;
            }
            sb.append(")");
        }
        sb.append(" ORDER BY is_pinned DESC, last_modified DESC");

        Cursor c = db.rawQuery(sb.toString(), args.toArray(new String[0]));
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
        String query = "SELECT DISTINCT n.* FROM notes n WHERE n.is_trashed = 0 AND ((n.reminder_time > 0 AND n.reminder_time <= ?) OR (n.alarm_time > 0 AND n.alarm_time <= ?))";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(endTime), String.valueOf(endTime)});
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    public void deleteNoteForever(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("checklist_items", "note_id=?", new String[]{String.valueOf(id)});
        db.delete("attachments", "note_id=?", new String[]{String.valueOf(id)});
        db.delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public void clearTrash() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM checklist_items WHERE note_id IN (SELECT id FROM notes WHERE is_trashed=1)");
        db.execSQL("DELETE FROM attachments WHERE note_id IN (SELECT id FROM notes WHERE is_trashed=1)");
        db.delete("notes", "is_trashed=1", null);
    }

    public void resetAllNotes() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("checklist_items", null, null);
        db.delete("attachments", null, null);
        db.delete("notes", null, null);
    }

    public List<Note> getRecurringNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM notes WHERE is_trashed = 0 AND (recurrence_type > 0 OR alarm_recurrence_type > 0) ORDER BY last_modified DESC";
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
        String query = "SELECT DISTINCT n.* FROM notes n WHERE n.is_trashed = 0 AND ((n.reminder_time >= ? AND n.reminder_time <= ?) OR (n.alarm_time >= ? AND n.alarm_time <= ?))";
        Cursor c = db.rawQuery(query, new String[]{String.valueOf(start), String.valueOf(end), String.valueOf(start), String.valueOf(end)});
        if (c.moveToFirst()) { do { notes.add(cursorToNote(c)); } while (c.moveToNext()); }
        c.close();
        return notes;
    }

    // --- ChecklistItem CRUD ---
    public List<ChecklistItem> getChecklistItems(int noteId) {
        List<ChecklistItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query("checklist_items", null, "note_id=?", new String[]{String.valueOf(noteId)}, null, null, "position ASC");
        if (c.moveToFirst()) do { list.add(cursorToItem(c)); } while (c.moveToNext());
        c.close();
        return list;
    }

    public long insertChecklistItem(ChecklistItem item) {
        SQLiteDatabase db = this.getWritableDatabase();
        long id = db.insert("checklist_items", null, itemToValues(item, true));
        if (id != -1) item.id = (int) id;
        return id;
    }

    public void updateChecklistItem(ChecklistItem item) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.update("checklist_items", itemToValues(item, false), "id=?", new String[]{String.valueOf(item.id)});
    }

    public void deleteChecklistItem(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("checklist_items", "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteChecklistItemsByNoteId(int noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("checklist_items", "note_id=?", new String[]{String.valueOf(noteId)});
    }

    private ChecklistItem cursorToItem(Cursor c) {
        return new ChecklistItem(
            c.getInt(c.getColumnIndexOrThrow("id")),
            c.getInt(c.getColumnIndexOrThrow("note_id")),
            c.getString(c.getColumnIndexOrThrow("name")),
            c.getInt(c.getColumnIndexOrThrow("checked")) == 1,
            c.getInt(c.getColumnIndexOrThrow("position")),
            c.getLong(c.getColumnIndexOrThrow("reminder_time")),
            c.getInt(c.getColumnIndexOrThrow("alert_type")),
            c.getInt(c.getColumnIndexOrThrow("recurrence_type")),
            c.getInt(c.getColumnIndexOrThrow("recurrence_days")),
            c.getLong(c.getColumnIndexOrThrow("original_reminder_time"))
        );
    }

    private ContentValues itemToValues(ChecklistItem item, boolean withNoteId) {
        ContentValues v = new ContentValues();
        if (withNoteId) v.put("note_id", item.noteId);
        v.put("name", item.name);
        v.put("checked", item.checked ? 1 : 0);
        v.put("position", item.position);
        v.put("reminder_time", item.reminderTime);
        v.put("alert_type", item.alertType);
        v.put("recurrence_type", item.recurrenceType);
        v.put("recurrence_days", item.recurrenceDays);
        v.put("original_reminder_time", item.originalReminderTime);
        return v;
    }

    public static class ChecklistItem {
        public int id = -1;
        public int noteId;
        public String name;
        public boolean checked;
        public int position;
        public long reminderTime;
        public int alertType;
        public int recurrenceType;
        public int recurrenceDays;
        public long originalReminderTime;

        public ChecklistItem(int id, int noteId, String name, boolean checked, int position, long reminderTime, int alertType, int recurrenceType, int recurrenceDays, long originalReminderTime) {
            this.id = id; this.noteId = noteId; this.name = name; this.checked = checked;
            this.position = position; this.reminderTime = reminderTime; this.alertType = alertType;
            this.recurrenceType = recurrenceType; this.recurrenceDays = recurrenceDays;
            this.originalReminderTime = originalReminderTime;
        }

        public ChecklistItem() {}
    }

    public static class Attachment {
        public int id = -1;
        public int noteId;
        public int type; // 0=image, 1=audio
        public String fileName;
        public String filePath;
        public String mimeType;
        public long fileSize;

        public Attachment(int id, int noteId, int type, String fileName, String filePath, String mimeType, long fileSize) {
            this.id = id; this.noteId = noteId; this.type = type;
            this.fileName = fileName; this.filePath = filePath; this.mimeType = mimeType; this.fileSize = fileSize;
        }

        public Attachment() {}
    }

    // --- Attachment CRUD ---
    public List<Attachment> getAttachments(int noteId) {
        List<Attachment> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query("attachments", null, "note_id=?", new String[]{String.valueOf(noteId)}, null, null, "id ASC");
        if (c.moveToFirst()) do { list.add(cursorToAttachment(c)); } while (c.moveToNext());
        c.close();
        return list;
    }

    public long insertAttachment(Attachment a) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("note_id", a.noteId);
        v.put("type", a.type);
        v.put("file_name", a.fileName);
        v.put("file_path", a.filePath);
        v.put("mime_type", a.mimeType);
        v.put("file_size", a.fileSize);
        long id = db.insert("attachments", null, v);
        if (id != -1) a.id = (int) id;
        return id;
    }

    public void deleteAttachment(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("attachments", "id=?", new String[]{String.valueOf(id)});
    }

    public void deleteAttachmentsByNoteId(int noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("attachments", "note_id=?", new String[]{String.valueOf(noteId)});
    }

    private Attachment cursorToAttachment(Cursor c) {
        return new Attachment(
            c.getInt(c.getColumnIndexOrThrow("id")),
            c.getInt(c.getColumnIndexOrThrow("note_id")),
            c.getInt(c.getColumnIndexOrThrow("type")),
            c.getString(c.getColumnIndexOrThrow("file_name")),
            c.getString(c.getColumnIndexOrThrow("file_path")),
            c.getString(c.getColumnIndexOrThrow("mime_type")),
            c.getLong(c.getColumnIndexOrThrow("file_size"))
        );
    }

    public static class Note {
        public int id, type, color, isPinned, isTrashed, isLocked, recurrenceType, recurrenceDays, alertType, alarmRecurrenceType, alarmRecurrenceDays;
        public String title, content, tag, attachments;
        public long reminderTime, lastModified, originalReminderTime, alarmTime, alarmOriginalReminderTime;
        public boolean isGhost = false;

        public Note(int id, String title, String content, int type, int color, int isPinned, int isTrashed, String tag, long reminderTime, int recurrenceType, int recurrenceDays, String attachments, int isLocked, int alertType, long lastModified, long originalReminderTime, long alarmTime, int alarmRecurrenceType, int alarmRecurrenceDays, long alarmOriginalReminderTime) {
            this.id = id; this.title = title; this.content = content; this.type = type; this.color = color;
            this.isPinned = isPinned; this.isTrashed = isTrashed; this.tag = tag; this.reminderTime = reminderTime;
            this.recurrenceType = recurrenceType; this.recurrenceDays = recurrenceDays;
            this.attachments = attachments; this.isLocked = isLocked;
            this.alertType = alertType; this.lastModified = lastModified; this.originalReminderTime = originalReminderTime;
            this.alarmTime = alarmTime; this.alarmRecurrenceType = alarmRecurrenceType;
            this.alarmRecurrenceDays = alarmRecurrenceDays; this.alarmOriginalReminderTime = alarmOriginalReminderTime;
        }

        public Note asGhost() {
            Note ghost = new Note(id, title, content, type, color, isPinned, isTrashed, tag, reminderTime, recurrenceType, recurrenceDays, attachments, isLocked, alertType, lastModified, originalReminderTime, alarmTime, alarmRecurrenceType, alarmRecurrenceDays, alarmOriginalReminderTime);
            ghost.isGhost = true;
            return ghost;
        }

        public static String extractTitle(String content) {
            if (content == null || content.trim().isEmpty()) return "Sem título";
            for (String line : content.split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    if (line.length() > 50) line = line.substring(0, 50) + "…";
                    return line;
                }
            }
            String[] words = content.trim().split("\\s+");
            int limit = Math.min(3, words.length);
            return String.join(" ", java.util.Arrays.copyOf(words, limit));
        }

        public java.time.LocalDate getLocalDate() {
            long time = originalReminderTime > 0 ? originalReminderTime : reminderTime;
            if (time <= 0) time = alarmOriginalReminderTime > 0 ? alarmOriginalReminderTime : alarmTime;
            if (time <= 0) return null;
            return java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }

        public java.time.LocalDate getAlarmLocalDate() {
            long time = alarmOriginalReminderTime > 0 ? alarmOriginalReminderTime : alarmTime;
            if (time <= 0) return null;
            return java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
    }
}
