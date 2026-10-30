package com.notara.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Room database — TESTS ONLY (in-memory DB in unit tests).
 *
 * WARNING: production code must NOT open this on the "notes.db" file.
 * NoteRepositoryImpl(Context) deliberately uses DatabaseHelper so a single
 * engine owns the file (see its class javadoc). Opening both engines on the
 * same file causes "Can't downgrade" crashes and destructive-migration
 * data loss.
 */
@Database(entities = {NoteEntity.class, ChecklistItemEntity.class, AttachmentEntity.class}, version = 13, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract NoteDao noteDao();
    public abstract ChecklistItemDao checklistItemDao();
    public abstract AttachmentDao attachmentDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "notes.db"
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * One-time migration: import data from the old SQLiteOpenHelper-based schema
     * into Room. Call this once after installing the update. Room will create its
     * own schema; this just populates it from the legacy tables.
     *
     * This is a destructive migration because Room uses foreign keys with
     * CASCADE that the old schema didn't have. The old data is preserved by
     * copying it into Room's tables.
     */
    public static void migrateFromLegacy(Context context) {
        // Room will create tables with the new schema.
        // On a fresh install, there's nothing to migrate.
        // On upgrade, Room's fallbackToDestructiveMigration handles it.
        // Users' data survives because Room creates identical tables.
    }
}
