package com.notara.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AttachmentDao {

    @Insert
    long insert(AttachmentEntity attachment);

    @Query("DELETE FROM attachments WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM attachments WHERE note_id = :noteId")
    void deleteByNoteId(int noteId);

    @Query("SELECT * FROM attachments WHERE note_id = :noteId ORDER BY id ASC")
    List<AttachmentEntity> getByNoteId(int noteId);
}
