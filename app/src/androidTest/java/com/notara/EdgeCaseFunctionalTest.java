package com.notara;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * B17 — Edge cases (on-device).
 *
 * Covers: reminder+alarm double recurrence advancing independently, item
 * alertType=2 double-dispatch advancing only once, custom weekday recurrence
 * for an alarm-only note in the calendar, encrypted backup roundtrip/tamper,
 * and a real v12 database file upgraded by SQLiteOpenHelper to v13.
 */
@RunWith(AndroidJUnit4.class)
public class EdgeCaseFunctionalTest {

    private Context ctx;
    private NoteRepository repo;
    private DatabaseHelper db;
    private final List<Integer> createdNotes = new ArrayList<>();

    @Before
    public void setup() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = new NoteRepositoryImpl(ctx);
        db = new DatabaseHelper(ctx);
    }

    @After
    public void teardown() {
        for (int id : createdNotes) {
            try {
                AlarmReceiver.cancelAlarm(ctx, id);
                AlarmReceiver.cancelNoteAlarm(ctx, id);
                for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
                    AlarmReceiver.cancelItemAlarm(ctx, id, it.id);
                }
                repo.deleteNoteForever(id);
            } catch (Exception ignored) {}
        }
        createdNotes.clear();
    }

    // ---------- helpers ----------

    private DatabaseHelper.Note note(String title, String content, int type,
                                     long reminderTime, int reminderRecurrence,
                                     long alarmTime, int alarmRecurrence, int alarmDays) {
        DatabaseHelper.Note n = new DatabaseHelper.Note(-1, title, content, type, 0,
                0, 0, null, reminderTime, reminderRecurrence, 0, null, 0, 0,
                System.currentTimeMillis(), 0, alarmTime, alarmRecurrence, alarmDays, alarmTime);
        int id = (int) repo.addNote(n);
        createdNotes.add(id);
        return repo.getNote(id);
    }

    private void fire(int noteId, int itemId, int itemAlertType, int noteAlertType) {
        Intent i = new Intent(ctx, AlarmReceiver.class)
                .putExtra("id", noteId)
                .putExtra("NOTE_ALERT_TYPE", noteAlertType);
        if (itemId != -1) {
            i.putExtra("ITEM_ID", itemId).putExtra("ITEM_ALERT_TYPE", itemAlertType);
        }
        new AlarmReceiver().onReceive(ctx, i);
    }

    private static LocalDate dayOf(long millis) {
        return java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static long millisAt(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static boolean matchesSeries(LocalDate grid, LocalDate event, int type, int days) {
        return CalendarActivity.matchesSeries(grid, event, type, days);
    }

    private static boolean shouldShowOnDay(DatabaseHelper.Note note, LocalDate grid) {
        return CalendarActivity.shouldShowOnDay(grid, note);
    }

    // ---------- E01: reminder + alarm double recurrence ----------

    @Test
    public void e01_doubleRecurrenceAdvancesIndependently() {
        LocalDate baseDay = LocalDate.now().minusDays(3);
        long base = millisAt(baseDay, 9);
        DatabaseHelper.Note n = note("E01 dupla", "corpo", 0,
                base, 1,            // daily reminder
                base, 1, 0);        // daily alarm

        // Reminder fires: only the reminder series advances.
        fire(n.id, -1, -1, 0);
        DatabaseHelper.Note afterReminder = repo.getNote(n.id);
        assertEquals("reminder did not advance exactly one day",
                baseDay.plusDays(1), dayOf(afterReminder.reminderTime));
        assertEquals("alarm advanced when the reminder fired",
                baseDay, dayOf(afterReminder.alarmTime));

        // Alarm fires: only the alarm series advances.
        fire(n.id, -1, -1, 1);
        DatabaseHelper.Note afterAlarm = repo.getNote(n.id);
        assertEquals("reminder advanced twice / moved when the alarm fired",
                baseDay.plusDays(1), dayOf(afterAlarm.reminderTime));
        assertEquals("alarm did not advance exactly one day",
                baseDay.plusDays(1), dayOf(afterAlarm.alarmTime));
    }

    // ---------- E02: item alertType=2 double dispatch ----------

    @Test
    public void e02_itemBothAlertTypesAdvanceRecurrenceOnlyOnce() {
        // Just a few minutes ago: the first dispatch advances to tomorrow
        // (future), which is what blocks the second dispatch from advancing
        // again. A multi-day backlog legitimately advances more than once.
        long base = System.currentTimeMillis() - 5 * 60 * 1000L;
        LocalDate baseDay = dayOf(base);
        DatabaseHelper.Note n = note("E02 item", "Item::0", 1, 0, 0, 0, 0, 0);

        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = n.id;
        item.name = "Item";
        item.position = 0;
        item.reminderTime = base;
        item.alertType = 2;
        item.recurrenceType = 1; // daily
        db.insertChecklistItem(item);

        fire(n.id, item.id, 0, -1);  // notification dispatch
        fire(n.id, item.id, 1, -1);  // alarm dispatch, same instant

        DatabaseHelper.ChecklistItem after = null;
        for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(n.id)) {
            if (it.id == item.id) after = it;
        }
        assertNotNull(after);
        assertEquals("alertType=2 must not advance the item by two days",
                baseDay.plusDays(1), dayOf(after.reminderTime));
        assertTrue("item notification alarm was not rescheduled",
                hasPending(n.id * 1000 + item.id * 2));
        assertTrue("item full-screen alarm was not rescheduled",
                hasPending(n.id * 1000 + item.id * 2 + 1));
    }

    private boolean hasPending(int requestCode) {
        Intent probe = new Intent(ctx, AlarmReceiver.class);
        return android.app.PendingIntent.getBroadcast(ctx, requestCode, probe,
                android.app.PendingIntent.FLAG_NO_CREATE | android.app.PendingIntent.FLAG_IMMUTABLE) != null;
    }

    // ---------- E03: custom weekday recurrence, alarm-only ----------

    @Test
    public void e03_customWeekdayAlarmOnlySeriesInCalendar() throws Exception {
        LocalDate anchor = LocalDate.of(2026, 6, 11); // Thursday
        int monday = 1 << 1;
        int wednesday = 1 << 3;
        int days = monday | wednesday;

        DatabaseHelper.Note n = note("E03 custom", "corpo", 0,
                0, 0,
                millisAt(anchor, 10), 5, days);

        assertFalse("day before the anchor must not match", shouldShowOnDay(n, anchor.minusDays(1)));
        assertTrue("anchor day must match", shouldShowOnDay(n, anchor));
        assertFalse("Friday is not in the custom set", shouldShowOnDay(n, LocalDate.of(2026, 6, 12)));
        assertTrue("Monday must match", shouldShowOnDay(n, LocalDate.of(2026, 6, 15)));
        assertFalse("Tuesday is not in the custom set", shouldShowOnDay(n, LocalDate.of(2026, 6, 16)));
        assertTrue("Wednesday must match", shouldShowOnDay(n, LocalDate.of(2026, 6, 17)));
        assertFalse("Thursday after the anchor is not in the custom set",
                shouldShowOnDay(n, LocalDate.of(2026, 6, 18)));

        // Same helper with the reminder-empty side: an alarm-only note must not
        // light up days through the (absent) reminder series.
        assertTrue(matchesSeries(anchor, anchor, 0, 0));
        assertFalse(matchesSeries(anchor.plusDays(1), anchor, 0, 0));
    }

    // ---------- E04: encrypted backup ----------

    @Test
    public void e04_encryptedBackupRoundtripAndTamper() throws Exception {
        DatabaseHelper.Note original = note("E04 backup", "conteúdo çãõ 🔒", 0,
                1_700_000_000_000L, 2,
                1_700_003_600_000L, 3, 0);
        List<DatabaseHelper.Note> notes = new ArrayList<>();
        notes.add(original);

        String json = BackupManager.serialize(notes);
        assertTrue(json.startsWith(BackupManager.HEADER));
        assertFalse(BackupManager.isEncrypted(json));

        String password = "senha-forte-123";
        String encrypted = BackupManager.encrypt(json, password);
        assertTrue(BackupManager.isEncrypted(encrypted));
        assertFalse("encrypted backup must not contain plaintext", encrypted.contains("E04 backup"));

        // File roundtrip (same bytes path used by SAF).
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BackupManager.write(out, encrypted);
        String readBack = BackupManager.read(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(encrypted, readBack);

        String decrypted = BackupManager.decrypt(readBack, password);
        List<DatabaseHelper.Note> restored = BackupManager.deserialize(decrypted);
        assertEquals(1, restored.size());
        DatabaseHelper.Note r = restored.get(0);
        assertEquals(original.title, r.title);
        assertEquals(original.content, r.content);
        assertEquals(original.reminderTime, r.reminderTime);
        assertEquals(original.recurrenceType, r.recurrenceType);
        assertEquals(original.alarmTime, r.alarmTime);
        assertEquals(original.alarmRecurrenceType, r.alarmRecurrenceType);
        assertEquals(original.alarmRecurrenceDays, r.alarmRecurrenceDays);
        assertEquals(original.alarmOriginalReminderTime, r.alarmOriginalReminderTime);

        try {
            BackupManager.decrypt(encrypted, "senha-errada");
            fail("wrong password decrypted the backup");
        } catch (Exception expected) { /* good */ }

        // Flip one payload byte (still valid Base64) -> GCM tag must reject it.
        char[] chars = encrypted.toCharArray();
        int idx = chars.length / 2;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        try {
            BackupManager.decrypt(new String(chars), password);
            fail("tampered backup was accepted");
        } catch (Exception expected) { /* good */ }

        try {
            BackupManager.deserialize("NOT_A_BACKUP\n{}");
            fail("invalid header accepted");
        } catch (Exception expected) { /* good */ }
    }

    // ---------- E05: v12 -> v13 database upgrade ----------

    @Test
    public void e05_realV12DatabaseUpgradesToV13PreservingRows() throws Exception {
        File file = new File(ctx.getCacheDir(), "b17_upgrade_" + System.nanoTime() + ".db");
        assertTrue(file.delete() || !file.exists());

        SQLiteOpenHelper v12 = new SQLiteOpenHelper(ctx, file.getAbsolutePath(), null, 12) {
            @Override public void onCreate(SQLiteDatabase d) { new DatabaseHelper(ctx).onCreate(d); }
            @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {
                new DatabaseHelper(ctx).onUpgrade(d, o, n);
            }
        };

        SQLiteDatabase d = v12.getWritableDatabase();
        assertEquals(12, d.getVersion());
        ContentValues note = new ContentValues();
        note.put("title", "E05 v12");
        note.put("content", "sobrevive ao upgrade");
        note.put("reminder_time", 123456789L);
        note.put("alarm_time", 987654321L);
        note.put("is_locked", 0);
        long noteId = d.insertOrThrow("notes", null, note);
        ContentValues item = new ContentValues();
        item.put("note_id", noteId);
        item.put("name", "Item v12");
        item.put("reminder_time", 555L);
        d.insertOrThrow("checklist_items", null, item);
        v12.close();

        SQLiteOpenHelper v13 = new SQLiteOpenHelper(ctx, file.getAbsolutePath(), null, 13) {
            @Override public void onCreate(SQLiteDatabase d) { new DatabaseHelper(ctx).onCreate(d); }
            @Override public void onUpgrade(SQLiteDatabase d, int o, int n) {
                new DatabaseHelper(ctx).onUpgrade(d, o, n);
            }
        };

        SQLiteDatabase upgraded = v13.getWritableDatabase();
        assertEquals("user_version must be bumped to 13", 13, upgraded.getVersion());

        Cursor c = upgraded.rawQuery("SELECT title, content, reminder_time, alarm_time FROM notes WHERE id=?",
                new String[]{String.valueOf(noteId)});
        assertTrue(c.moveToFirst());
        assertEquals("E05 v12", c.getString(0));
        assertEquals("sobrevive ao upgrade", c.getString(1));
        assertEquals(123456789L, c.getLong(2));
        assertEquals(987654321L, c.getLong(3));
        c.close();

        Cursor ci = upgraded.rawQuery("SELECT name, reminder_time FROM checklist_items WHERE note_id=?",
                new String[]{String.valueOf(noteId)});
        assertTrue(ci.moveToFirst());
        assertEquals("Item v12", ci.getString(0));
        assertEquals(555L, ci.getLong(1));
        ci.close();

        v13.close();
        assertTrue(file.delete() || !file.exists());
    }
}
