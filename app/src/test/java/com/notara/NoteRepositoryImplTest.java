package com.notara;

import android.content.Context;

import androidx.room.Room;

import com.notara.db.AppDatabase;
import com.notara.db.NoteDao;
import com.notara.db.NoteEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NoteRepositoryImplTest {

    private AppDatabase db;
    private NoteDao noteDao;

    @Before
    public void setup() {
        Context context = RuntimeEnvironment.getApplication();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        noteDao = db.noteDao();
    }

    @After
    public void teardown() {
        db.close();
    }

    @Test
    public void testInsertAndReadNote() {
        NoteEntity note = new NoteEntity();
        note.title = "Teste";
        note.content = "Conteúdo";
        note.lastModified = System.currentTimeMillis();

        long id = noteDao.insert(note);
        assertTrue("Should return valid ID", id > 0);

        NoteEntity retrieved = noteDao.getById((int) id);
        assertNotNull(retrieved);
        assertEquals("Teste", retrieved.title);
        assertEquals("Conteúdo", retrieved.content);
    }

    @Test
    public void testUpdateNote() {
        NoteEntity note = new NoteEntity();
        note.title = "Original";
        note.content = "V1";
        long id = noteDao.insert(note);

        NoteEntity retrieved = noteDao.getById((int) id);
        retrieved.title = "Atualizado";
        retrieved.content = "V2";
        noteDao.update(retrieved);

        NoteEntity updated = noteDao.getById((int) id);
        assertEquals("Atualizado", updated.title);
        assertEquals("V2", updated.content);
    }

    @Test
    public void testDeleteNote() {
        NoteEntity note = new NoteEntity();
        note.title = "Para deletar";
        long id = noteDao.insert(note);
        assertNotNull(noteDao.getById((int) id));

        noteDao.deleteById((int) id);
        assertNull(noteDao.getById((int) id));
    }

    @Test
    public void testSearchNotes() {
        NoteEntity n1 = new NoteEntity(); n1.title = "Nota Android"; n1.content = "Java"; n1.lastModified = 1;
        NoteEntity n2 = new NoteEntity(); n2.title = "Nota iOS"; n2.content = "Swift"; n2.lastModified = 2;
        NoteEntity n3 = new NoteEntity(); n3.title = "Lista Compras"; n3.content = "Leite"; n3.lastModified = 3;
        noteDao.insert(n1); noteDao.insert(n2); noteDao.insert(n3);

        // Search by query
        java.util.List<NoteEntity> results = noteDao.search("Android", false, null);
        assertEquals(1, results.size());
        assertEquals("Nota Android", results.get(0).title);

        // Search all
        java.util.List<NoteEntity> all = noteDao.search("", false, null);
        assertEquals(3, all.size());
    }

    @Test
    public void testSearchWithColorFilter() {
        NoteEntity n1 = new NoteEntity(); n1.title = "Amarela"; n1.color = 0; n1.lastModified = 1;
        NoteEntity n2 = new NoteEntity(); n2.title = "Azul"; n2.color = 6; n2.lastModified = 2;
        noteDao.insert(n1); noteDao.insert(n2);

        java.util.List<NoteEntity> results = noteDao.searchWithFilters("", false, null, java.util.Arrays.asList(6), null);
        assertEquals(1, results.size());
        assertEquals("Azul", results.get(0).title);
    }

    @Test
    public void testTrashOperations() {
        NoteEntity n1 = new NoteEntity(); n1.title = "Ativa"; n1.lastModified = 1;
        NoteEntity n2 = new NoteEntity(); n2.title = "Lixeira"; n2.isTrashed = 1; n2.lastModified = 2;
        noteDao.insert(n1); noteDao.insert(n2);

        // Search non-trashed
        java.util.List<NoteEntity> active = noteDao.search("", false, null);
        assertEquals(1, active.size());
        assertEquals("Ativa", active.get(0).title);

        // Search trashed
        java.util.List<NoteEntity> trashed = noteDao.search("", true, null);
        assertEquals(1, trashed.size());
        assertEquals("Lixeira", trashed.get(0).title);

        // Delete all trashed
        noteDao.deleteAllTrashed();
        java.util.List<NoteEntity> afterClear = noteDao.search("", true, null);
        assertEquals(0, afterClear.size());
    }

    @Test
    public void testNoteEntityConversion() {
        DatabaseHelper.Note original = new DatabaseHelper.Note(
                42, "Title", "Content", 1, 3, 1, 0, "tag",
                1700000000000L, 2, 7, "att.json", 1, 2,
                1700000000000L, 1699900000000L, 1700100000000L, 3, 15, 1700000000000L);

        NoteEntity entity = NoteEntity.fromNote(original);
        DatabaseHelper.Note restored = entity.toNote();

        assertEquals(original.id, restored.id);
        assertEquals(original.title, restored.title);
        assertEquals(original.content, restored.content);
        assertEquals(original.type, restored.type);
        assertEquals(original.color, restored.color);
        assertEquals(original.isPinned, restored.isPinned);
        assertEquals(original.isTrashed, restored.isTrashed);
        assertEquals(original.tag, restored.tag);
        assertEquals(original.reminderTime, restored.reminderTime);
        assertEquals(original.recurrenceType, restored.recurrenceType);
        assertEquals(original.recurrenceDays, restored.recurrenceDays);
        assertEquals(original.isLocked, restored.isLocked);
        assertEquals(original.alertType, restored.alertType);
        assertEquals(original.alarmTime, restored.alarmTime);
        assertEquals(original.alarmRecurrenceType, restored.alarmRecurrenceType);
        assertEquals(original.alarmRecurrenceDays, restored.alarmRecurrenceDays);
    }

    @Test
    public void testExtractTitle_emptyContent() {
        assertEquals("Sem título", DatabaseHelper.Note.extractTitle(null));
        assertEquals("Sem título", DatabaseHelper.Note.extractTitle(""));
        assertEquals("Sem título", DatabaseHelper.Note.extractTitle("   "));
    }

    @Test
    public void testExtractTitle_shortContent() {
        assertEquals("Primeira linha", DatabaseHelper.Note.extractTitle("Primeira linha\nSegunda linha"));
    }

    @Test
    public void testExtractTitle_longContent() {
        String longLine = "Esta é uma linha muito longa que deveria ser cortada em algum ponto para não ficar enorme";
        String result = DatabaseHelper.Note.extractTitle(longLine);
        assertTrue("Should be truncated at 50 chars", result.length() <= 53);
        assertTrue("Should end with ellipsis", result.endsWith("…"));
    }

    @Test
    public void testExtractTitle_multiline_skipsEmptyLines() {
        String content = "\n\n\nLinha real";
        assertEquals("Linha real", DatabaseHelper.Note.extractTitle(content));
    }

    @Test
    public void testChecklistItemEntityConversion() {
        DatabaseHelper.ChecklistItem original = new DatabaseHelper.ChecklistItem(
                1, 10, "Buy milk", true, 0,
                1700000000000L, 1, 2, 3, 1699900000000L);

        com.notara.db.ChecklistItemEntity entity = com.notara.db.ChecklistItemEntity.fromItem(original);
        DatabaseHelper.ChecklistItem restored = entity.toItem();

        assertEquals(original.id, restored.id);
        assertEquals(original.noteId, restored.noteId);
        assertEquals(original.name, restored.name);
        assertEquals(original.checked, restored.checked);
        assertEquals(original.position, restored.position);
        assertEquals(original.reminderTime, restored.reminderTime);
        assertEquals(original.alertType, restored.alertType);
    }

    private static DatabaseHelper.Note newNoteWithSchedule(long reminderTime, long originalReminderTime,
                                                           long alarmTime, long alarmOriginalTime) {
        return new DatabaseHelper.Note(-1, "T", "C", 0, 0, 0, 0, null,
                reminderTime, 0, 0, null, 0, 0, 0, originalReminderTime,
                alarmTime, 0, 0, alarmOriginalTime);
    }

    @Test
    public void testGetLocalDate_reminderOnly() {
        long reminder = 1700000000000L;
        DatabaseHelper.Note n = newNoteWithSchedule(reminder, 0, 0, 0);
        java.time.LocalDate expected = java.time.Instant.ofEpochMilli(reminder)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        assertEquals(expected, n.getLocalDate());
        assertNull(n.getAlarmLocalDate());
    }

    @Test
    public void testGetLocalDate_fallsBackToAlarm() {
        long alarm = 1700100000000L;
        DatabaseHelper.Note n = newNoteWithSchedule(0, 0, alarm, 0);
        java.time.LocalDate expected = java.time.Instant.ofEpochMilli(alarm)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        assertEquals(expected, n.getLocalDate());
        assertEquals(expected, n.getAlarmLocalDate());
    }

    @Test
    public void testGetLocalDate_prefersOriginalTimes() {
        DatabaseHelper.Note n = newNoteWithSchedule(1700000000000L, 1699900000000L, 1700100000000L, 1700050000000L);
        java.time.LocalDate expectedReminder = java.time.Instant.ofEpochMilli(1699900000000L)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        java.time.LocalDate expectedAlarm = java.time.Instant.ofEpochMilli(1700050000000L)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        assertEquals(expectedReminder, n.getLocalDate());
        assertEquals(expectedAlarm, n.getAlarmLocalDate());
    }

    @Test
    public void testGetLocalDate_noSchedule() {
        DatabaseHelper.Note n = newNoteWithSchedule(0, 0, 0, 0);
        assertNull(n.getLocalDate());
        assertNull(n.getAlarmLocalDate());
    }

    @Test
    public void testRepositoryContextConstructor_usesWorkingBackend() {
        Context context = RuntimeEnvironment.getApplication();
        NoteRepository repo = new NoteRepositoryImpl(context);
        DatabaseHelper.Note note = newNoteWithSchedule(System.currentTimeMillis() + 3600000, 0,
                System.currentTimeMillis() + 7200000, 0);
        note.title = "Backend unificado";
        long id = repo.addNote(note);
        assertTrue(id > 0);
        DatabaseHelper.Note loaded = repo.getNote((int) id);
        assertNotNull(loaded);
        assertEquals("Backend unificado", loaded.title);
        assertEquals(note.reminderTime, loaded.reminderTime);
        assertEquals(note.alarmTime, loaded.alarmTime);
        repo.deleteNoteForever((int) id);
        assertNull(repo.getNote((int) id));
    }
}
