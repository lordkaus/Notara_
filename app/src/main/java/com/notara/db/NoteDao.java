package com.notara.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(NoteEntity note);

    @Update
    void update(NoteEntity note);

    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM notes WHERE is_trashed = 1")
    void deleteAllTrashed();

    @Query("DELETE FROM notes")
    void deleteAll();

    @Query("SELECT * FROM notes WHERE id = :id")
    NoteEntity getById(int id);

    @Query("SELECT * FROM notes ORDER BY last_modified DESC LIMIT 1")
    NoteEntity getLatest();

    @Query("SELECT * FROM notes WHERE is_trashed = :includeTrashed " +
            "AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') " +
            "AND (:filterTag IS NULL OR :filterTag = '' OR tag = :filterTag) " +
            "ORDER BY is_pinned DESC, last_modified DESC")
    List<NoteEntity> search(String query, boolean includeTrashed, String filterTag);

    @Query("SELECT * FROM notes WHERE is_trashed = :includeTrashed " +
            "AND (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') " +
            "AND (:filterTag IS NULL OR :filterTag = '' OR tag = :filterTag) " +
            "AND (:filterColors IS NULL OR color IN (:filterColors)) " +
            "AND (:filterTypes IS NULL OR type IN (:filterTypes)) " +
            "ORDER BY is_pinned DESC, last_modified DESC")
    List<NoteEntity> searchWithFilters(String query, boolean includeTrashed, String filterTag,
                                       List<Integer> filterColors, List<Integer> filterTypes);

    @Query("SELECT * FROM notes WHERE is_trashed = 0 " +
            "AND ((reminder_time > 0 AND reminder_time <= :endTime) OR (alarm_time > 0 AND alarm_time <= :endTime))")
    List<NoteEntity> getScheduledUpTo(long endTime);

    @Query("SELECT * FROM notes WHERE is_trashed = 0 AND (recurrence_type > 0 OR alarm_recurrence_type > 0) " +
            "ORDER BY last_modified DESC")
    List<NoteEntity> getRecurring();

    @Query("SELECT * FROM notes WHERE is_trashed = 0 " +
            "AND ((reminder_time >= :start AND reminder_time <= :end) OR (alarm_time >= :start AND alarm_time <= :end))")
    List<NoteEntity> getForDateRange(long start, long end);
}
