package com.notara.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.notara.DatabaseHelper;

@Entity(tableName = "notes")
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String content;
    public int type;
    public int color;

    @ColumnInfo(name = "is_pinned")
    public int isPinned;

    @ColumnInfo(name = "is_trashed")
    public int isTrashed;

    public String tag;

    @ColumnInfo(name = "reminder_time")
    public long reminderTime;

    @ColumnInfo(name = "recurrence_type")
    public int recurrenceType;

    @ColumnInfo(name = "recurrence_days")
    public int recurrenceDays;

    public String attachments;

    @ColumnInfo(name = "is_locked")
    public int isLocked;

    @ColumnInfo(name = "alert_type")
    public int alertType;

    @ColumnInfo(name = "last_modified")
    public long lastModified;

    @ColumnInfo(name = "original_reminder_time")
    public long originalReminderTime;

    @ColumnInfo(name = "alarm_time")
    public long alarmTime;

    @ColumnInfo(name = "alarm_recurrence_type")
    public int alarmRecurrenceType;

    @ColumnInfo(name = "alarm_recurrence_days")
    public int alarmRecurrenceDays;

    @ColumnInfo(name = "alarm_original_time")
    public long alarmOriginalReminderTime;

    /**
     * Convert from DatabaseHelper.Note to NoteEntity
     */
    public static NoteEntity fromNote(DatabaseHelper.Note note) {
        NoteEntity e = new NoteEntity();
        e.id = note.id;
        e.title = note.title;
        e.content = note.content;
        e.type = note.type;
        e.color = note.color;
        e.isPinned = note.isPinned;
        e.isTrashed = note.isTrashed;
        e.tag = note.tag;
        e.reminderTime = note.reminderTime;
        e.recurrenceType = note.recurrenceType;
        e.recurrenceDays = note.recurrenceDays;
        e.attachments = note.attachments;
        e.isLocked = note.isLocked;
        e.alertType = note.alertType;
        e.lastModified = note.lastModified;
        e.originalReminderTime = note.originalReminderTime;
        e.alarmTime = note.alarmTime;
        e.alarmRecurrenceType = note.alarmRecurrenceType;
        e.alarmRecurrenceDays = note.alarmRecurrenceDays;
        e.alarmOriginalReminderTime = note.alarmOriginalReminderTime;
        return e;
    }

    /**
     * Convert to DatabaseHelper.Note (preserving all existing code compatibility)
     */
    public DatabaseHelper.Note toNote() {
        return new DatabaseHelper.Note(
                id, title, content, type, color, isPinned, isTrashed, tag,
                reminderTime, recurrenceType, recurrenceDays, attachments,
                isLocked, alertType, lastModified, originalReminderTime,
                alarmTime, alarmRecurrenceType, alarmRecurrenceDays, alarmOriginalReminderTime
        );
    }
}
