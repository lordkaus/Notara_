package com.notara.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ChecklistItemDao {

    @Insert
    long insert(ChecklistItemEntity item);

    @Update
    void update(ChecklistItemEntity item);

    @Query("DELETE FROM checklist_items WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM checklist_items WHERE note_id = :noteId")
    void deleteByNoteId(int noteId);

    @Query("SELECT * FROM checklist_items WHERE note_id = :noteId ORDER BY position ASC")
    List<ChecklistItemEntity> getByNoteId(int noteId);

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    ChecklistItemEntity getById(int id);
}
