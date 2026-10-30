package com.notara.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.notara.DatabaseHelper;

@Entity(tableName = "checklist_items",
        foreignKeys = @ForeignKey(
                entity = NoteEntity.class,
                parentColumns = "id",
                childColumns = "note_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("note_id"))
public class ChecklistItemEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "note_id")
    public int noteId;

    public String name;
    public boolean checked;
    public int position;

    @ColumnInfo(name = "reminder_time")
    public long reminderTime;

    @ColumnInfo(name = "alert_type")
    public int alertType;

    @ColumnInfo(name = "recurrence_type")
    public int recurrenceType;

    @ColumnInfo(name = "recurrence_days")
    public int recurrenceDays;

    @ColumnInfo(name = "original_reminder_time")
    public long originalReminderTime;

    public static ChecklistItemEntity fromItem(DatabaseHelper.ChecklistItem item) {
        ChecklistItemEntity e = new ChecklistItemEntity();
        e.id = item.id;
        e.noteId = item.noteId;
        e.name = item.name;
        e.checked = item.checked;
        e.position = item.position;
        e.reminderTime = item.reminderTime;
        e.alertType = item.alertType;
        e.recurrenceType = item.recurrenceType;
        e.recurrenceDays = item.recurrenceDays;
        e.originalReminderTime = item.originalReminderTime;
        return e;
    }

    public DatabaseHelper.ChecklistItem toItem() {
        return new DatabaseHelper.ChecklistItem(
                id, noteId, name, checked, position,
                reminderTime, alertType, recurrenceType, recurrenceDays, originalReminderTime
        );
    }
}
