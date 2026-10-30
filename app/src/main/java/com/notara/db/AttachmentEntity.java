package com.notara.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.notara.DatabaseHelper;

@Entity(tableName = "attachments",
        foreignKeys = @ForeignKey(
                entity = NoteEntity.class,
                parentColumns = "id",
                childColumns = "note_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("note_id"))
public class AttachmentEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "note_id")
    public int noteId;

    public int type;

    @ColumnInfo(name = "file_name")
    public String fileName;

    @ColumnInfo(name = "file_path")
    public String filePath;

    @ColumnInfo(name = "mime_type")
    public String mimeType;

    @ColumnInfo(name = "file_size")
    public long fileSize;

    public static AttachmentEntity fromAttachment(DatabaseHelper.Attachment a) {
        AttachmentEntity e = new AttachmentEntity();
        e.id = a.id;
        e.noteId = a.noteId;
        e.type = a.type;
        e.fileName = a.fileName;
        e.filePath = a.filePath;
        e.mimeType = a.mimeType;
        e.fileSize = a.fileSize;
        return e;
    }

    public DatabaseHelper.Attachment toAttachment() {
        return new DatabaseHelper.Attachment(id, noteId, type, fileName, filePath, mimeType, fileSize);
    }
}
