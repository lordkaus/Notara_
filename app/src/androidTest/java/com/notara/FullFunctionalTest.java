package com.notara;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Full on-device functional test: reminders, alarms, recurrence,
 * calendar series, boot reschedule, trash/restore, backup, activity smoke.
 *
 * Run: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class FullFunctionalTest {

    private static final String NOTE_ALERT_TYPE = "NOTE_ALERT_TYPE";
    private static final int NOTE_ALARM_OFFSET = 50000;
    private static final long POLL_MS = 500;

    private Context ctx;
    private NotificationManager nm;
    private NoteRepository repo;
    private DatabaseHelper db;
    private final List<Integer> createdNotes = new ArrayList<>();

    @BeforeClass
    public static void grantPermissions() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // NOTE: exact permission name is POST_NOTIFICATIONS (plural).
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "pm grant " + ctx.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        // Belt and braces: AppOps gate behind areNotificationsEnabled().
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set " + ctx.getPackageName() + " POST_NOTIFICATION allow");
        // Grants via shell apply asynchronously: wait until effective, or
        // every notification-observation test fails with a misleading timeout.
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        long end = System.currentTimeMillis() + 20000;
        while (!nm.areNotificationsEnabled() && System.currentTimeMillis() < end) {
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        assertTrue("POST_NOTIFICATIONS grant did not take effect", nm.areNotificationsEnabled());
    }

    @Before
    public void setup() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancelAll();
        repo = new NoteRepositoryImpl(ctx);
        db = new DatabaseHelper(ctx);
    }

    @After
    public void teardown() {
        for (int id : createdNotes) {
            try { AlarmReceiver.cancelAlarm(ctx, id); } catch (Exception ignored) {}
            try { AlarmReceiver.cancelNoteAlarm(ctx, id); } catch (Exception ignored) {}
            try { AlarmReceiver.cancelItemAlarm(ctx, id, 0); } catch (Exception ignored) {}
            try { AlarmReceiver.cancelItemAlarm(ctx, id, 1); } catch (Exception ignored) {}
            try { repo.deleteNoteForever(id); } catch (Exception ignored) {}
        }
        createdNotes.clear();
        try { nm.cancelAll(); } catch (Exception ignored) {}
    }

    // ---------- helpers ----------

    private DatabaseHelper.Note newNote(String title, long reminderTime, int recType, int recDays,
                                        long alarmTime, int alarmRecType, int alarmRecDays) {
        return new DatabaseHelper.Note(-1, title, "conteudo " + title, 0, 0, 0, 0, "FULLTEST",
                reminderTime, recType, recDays, null, 0, 0, System.currentTimeMillis(), reminderTime,
                alarmTime, alarmRecType, alarmRecDays, alarmTime);
    }

    private int insert(DatabaseHelper.Note n) {
        int id = (int) repo.addNote(n);
        createdNotes.add(id);
        return id;
    }

    private boolean hasNoteAlarm(int requestCode) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        return pi != null;
    }

    private StatusBarNotification findNotification(String channelId, String titlePart, String textPart) {
        for (StatusBarNotification sbn : nm.getActiveNotifications()) {
            if (!ctx.getPackageName().equals(sbn.getPackageName())) continue;
            Bundle ex = sbn.getNotification().extras;
            String title = String.valueOf(ex.getCharSequence("android.title"));
            String text = String.valueOf(ex.getCharSequence("android.text"));
            boolean chOk = channelId == null || channelId.equals(sbn.getNotification().getChannelId());
            if (chOk && title.contains(titlePart) && (textPart == null || text.contains(textPart))) return sbn;
        }
        return null;
    }

    private StatusBarNotification waitNotification(String channelId, String titlePart, String textPart, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        StatusBarNotification found;
        do {
            found = findNotification(channelId, titlePart, textPart);
            if (found != null) return found;
            try { Thread.sleep(POLL_MS); } catch (InterruptedException e) { break; }
        } while (System.currentTimeMillis() < end);
        return null;
    }

    private static Calendar cal(long time) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(time);
        return c;
    }

    // ---------- T01: DB CRUD + queries ----------

    @Test
    public void t01_dbCrudAndQueries() {
        long now = System.currentTimeMillis();
        long rem = now + 3600000;
        long alm = now + 7200000;
        int id = insert(newNote("T01", rem, 1, 0, alm, 2, 0));

        DatabaseHelper.Note n = repo.getNote(id);
        assertNotNull(n);
        assertEquals(rem, n.reminderTime);
        assertEquals(alm, n.alarmTime);
        assertEquals(1, n.recurrenceType);
        assertEquals(2, n.alarmRecurrenceType);

        assertTrue(containsId(repo.getScheduledNotesUpTo(now + 8000000), id));
        assertFalse(containsId(repo.getScheduledNotesUpTo(now + 1000), id));

        assertTrue(containsId(repo.getNotesForDateRange(rem - 1000, rem + 1000), id)); // reminder only
        assertTrue(containsId(repo.getNotesForDateRange(alm - 1000, alm + 1000), id)); // alarm only
        assertFalse(containsId(repo.getNotesForDateRange(now + 8000000, now + 9000000), id));

        assertTrue(containsId(repo.getRecurringNotes(), id)); // recurring via reminder

        int id2 = insert(newNote("T01-alarmrec", 0, 0, 0, alm, 1, 0));
        assertTrue(containsId(repo.getRecurringNotes(), id2)); // recurring via alarm only
        assertTrue(containsId(repo.getScheduledNotesUpTo(now + 8000000), id2));

        int id3 = insert(newNote("T01-plain", 0, 0, 0, 0, 0, 0));
        assertFalse(containsId(repo.getRecurringNotes(), id3));
        assertFalse(containsId(repo.getScheduledNotesUpTo(now + 8000000), id3));

        n.isTrashed = 1;
        repo.updateNote(n);
        assertFalse(containsId(repo.getRecurringNotes(), id)); // trashed excluded
    }

    private static boolean containsId(List<DatabaseHelper.Note> list, int id) {
        for (DatabaseHelper.Note n : list) if (n.id == id) return true;
        return false;
    }

    // ---------- T02: schedule / cancel independence ----------

    @Test
    public void t02_scheduleCancelIndependence() {
        long future = System.currentTimeMillis() + 3600000;
        int id = insert(newNote("T02", future, 0, 0, future, 0, 0));
        DatabaseHelper.Note n = repo.getNote(id);

        AlarmReceiver.rescheduleAlarm(ctx, n);
        assertTrue(hasNoteAlarm(id));
        AlarmReceiver.scheduleNoteAlarm(ctx, n);
        assertTrue(hasNoteAlarm(id + NOTE_ALARM_OFFSET));

        AlarmReceiver.cancelAlarm(ctx, id); // cancel notification only
        assertFalse(hasNoteAlarm(id));
        assertTrue(hasNoteAlarm(id + NOTE_ALARM_OFFSET)); // alarm survives

        AlarmReceiver.rescheduleAlarm(ctx, n);
        AlarmReceiver.cancelNoteAlarm(ctx, id); // cancel alarm only
        assertTrue(hasNoteAlarm(id));
        assertFalse(hasNoteAlarm(id + NOTE_ALARM_OFFSET));

        DatabaseHelper.Note past = repo.getNote(id);
        past.reminderTime = System.currentTimeMillis() - 60000;
        past.alarmTime = System.currentTimeMillis() - 60000;
        AlarmReceiver.rescheduleAlarm(ctx, past);
        AlarmReceiver.scheduleNoteAlarm(ctx, past);
        assertFalse(hasNoteAlarm(id)); // past times schedule nothing
        assertFalse(hasNoteAlarm(id + NOTE_ALARM_OFFSET));
    }

    // ---------- T03: recurrence advancement ----------

    @Test
    public void t03_recurrenceAdvancement() {
        // daily reminder
        DatabaseHelper.Note d = repo.getNote(insert(newNote("T03-d", fixed(2026, 5, 10, 9, 0), 1, 0, 0, 0, 0)));
        AlarmReceiver.cancelAlarm(ctx, d.id);
        AlarmReceiver.scheduleNextAlarm(ctx, d);
        DatabaseHelper.Note d2 = repo.getNote(d.id);
        assertEquals(11, cal(d2.reminderTime).get(Calendar.DAY_OF_MONTH));
        assertEquals(0, d2.alarmTime); // alarm untouched

        // weekly reminder (base must be a real Monday: Jun 8 2026)
        DatabaseHelper.Note w = repo.getNote(insert(newNote("T03-w", fixed(2026, 5, 8, 9, 0), 2, 0, 0, 0, 0)));
        AlarmReceiver.cancelAlarm(ctx, w.id);
        AlarmReceiver.scheduleNextAlarm(ctx, w);
        Calendar wc = cal(repo.getNote(w.id).reminderTime);
        assertEquals(15, wc.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.MONDAY, wc.get(Calendar.DAY_OF_WEEK));

        // monthly edge: Jan 31 -> Feb 28 (2026 not leap)
        DatabaseHelper.Note m = repo.getNote(insert(newNote("T03-m", fixed(2026, 0, 31, 9, 0), 3, 0, 0, 0, 0)));
        AlarmReceiver.cancelAlarm(ctx, m.id);
        AlarmReceiver.scheduleNextAlarm(ctx, m);
        Calendar mc = cal(repo.getNote(m.id).reminderTime);
        assertEquals(Calendar.FEBRUARY, mc.get(Calendar.MONTH));
        assertEquals(28, mc.get(Calendar.DAY_OF_MONTH));

        // yearly edge: Feb 29 2024 -> Feb 28 2025
        DatabaseHelper.Note y = repo.getNote(insert(newNote("T03-y", fixed(2024, 1, 29, 9, 0), 4, 0, 0, 0, 0)));
        AlarmReceiver.cancelAlarm(ctx, y.id);
        AlarmReceiver.scheduleNextAlarm(ctx, y);
        Calendar yc = cal(repo.getNote(y.id).reminderTime);
        assertEquals(2025, yc.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, yc.get(Calendar.MONTH));
        assertEquals(28, yc.get(Calendar.DAY_OF_MONTH));

        // custom Mon+Wed from Monday -> Wednesday
        int monWed = (1 << 1) | (1 << 3);
        DatabaseHelper.Note c = repo.getNote(insert(newNote("T03-c", fixed(2026, 5, 8, 9, 0), 5, monWed, 0, 0, 0))); // Mon Jun 8 2026
        AlarmReceiver.cancelAlarm(ctx, c.id);
        AlarmReceiver.scheduleNextAlarm(ctx, c);
        Calendar cc = cal(repo.getNote(c.id).reminderTime);
        assertEquals(Calendar.WEDNESDAY, cc.get(Calendar.DAY_OF_WEEK));
        assertEquals(10, cc.get(Calendar.DAY_OF_MONTH));

        // alarm series daily advances independently
        long remT = fixed(2026, 5, 10, 9, 0);
        DatabaseHelper.Note a = repo.getNote(insert(newNote("T03-a", remT, 0, 0, fixed(2026, 5, 10, 20, 0), 1, 0)));
        AlarmReceiver.cancelNoteAlarm(ctx, a.id);
        AlarmReceiver.scheduleNextNoteAlarm(ctx, a);
        DatabaseHelper.Note a2 = repo.getNote(a.id);
        assertEquals(11, cal(a2.alarmTime).get(Calendar.DAY_OF_MONTH));
        assertEquals(remT, a2.reminderTime); // reminder untouched

        // item series daily
        int noteId = insert(checklistNote("T03-item"));
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = noteId; item.name = "item"; item.position = 0;
        item.reminderTime = fixed(2026, 5, 10, 9, 0); item.alertType = 0; item.recurrenceType = 1;
        db.insertChecklistItem(item);
        AlarmReceiver.cancelItemAlarm(ctx, noteId, item.id);
        AlarmReceiver.scheduleNextItemAlarm(ctx, noteId, item);
        DatabaseHelper.ChecklistItem reloaded = null;
        for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(noteId)) if (it.id == item.id) reloaded = it;
        assertNotNull(reloaded);
        assertEquals(11, cal(reloaded.reminderTime).get(Calendar.DAY_OF_MONTH));
    }

    private static long fixed(int year, int month0, int day, int hour, int min) {
        Calendar c = Calendar.getInstance();
        c.set(year, month0, day, hour, min, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private DatabaseHelper.Note checklistNote(String title) {
        DatabaseHelper.Note n = newNote(title, 0, 0, 0, 0, 0, 0);
        n.type = 1;
        return n;
    }

    // ---------- T04: item alarms schedule + fire ----------

    @Test
    public void t04_itemAlarmFires_notification() {
        int noteId = insert(checklistNote("T04-" + System.currentTimeMillis()));
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = noteId; item.name = "Comprar leite T04"; item.position = 0;
        item.reminderTime = System.currentTimeMillis() + 4000; item.alertType = 0;
        db.insertChecklistItem(item);

        AlarmReceiver.rescheduleItemAlarm(ctx, noteId, item);
        assertTrue(hasNoteAlarm(noteId * 1000 + item.id * 2));

        StatusBarNotification sbn = waitNotification(NotesApp.REMINDER_CHANNEL_ID,
                "T04-", "Comprar leite T04", 30000);
        assertNotNull("item notification (alertType=0) never fired", sbn);
    }

    @Test
    public void t04b_itemAlarmFires_alarmChannel() {
        long tag = System.currentTimeMillis();
        int noteId = insert(checklistNote("T04b-" + tag));
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = noteId; item.name = "Acordar T04b"; item.position = 0;
        item.reminderTime = System.currentTimeMillis() + 4000; item.alertType = 1;
        db.insertChecklistItem(item);

        AlarmReceiver.rescheduleItemAlarm(ctx, noteId, item);
        StatusBarNotification sbn = waitNotification(NotesApp.ALARM_CHANNEL_ID,
                "Acordar T04b", "Alarme de item!", 30000);
        assertNotNull("item alarm (alertType=1) never fired", sbn);
    }

    @Test
    public void t04c_itemAlarmCancelled_neverFires() throws Exception {
        long tag = System.currentTimeMillis();
        int noteId = insert(checklistNote("T04c-" + tag));
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = noteId; item.name = "Nunca T04c"; item.position = 0;
        item.reminderTime = System.currentTimeMillis() + 4000; item.alertType = 0;
        db.insertChecklistItem(item);

        AlarmReceiver.rescheduleItemAlarm(ctx, noteId, item);
        assertTrue(hasNoteAlarm(noteId * 1000 + item.id * 2));
        AlarmReceiver.cancelItemAlarm(ctx, noteId, item.id);
        assertFalse(hasNoteAlarm(noteId * 1000 + item.id * 2));

        Thread.sleep(7000);
        assertNull(findNotification(null, "T04c-", "Nunca T04c"));
    }

    // ---------- T05: end-to-end reminder + alarm ----------

    @Test
    public void t05_endToEndReminderAndAlarm() {
        long tag = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        int id = insert(newNote("T05-" + tag, now + 5000, 0, 0, now + 9000, 0, 0));
        DatabaseHelper.Note n = repo.getNote(id);
        AlarmReceiver.rescheduleAlarm(ctx, n);
        AlarmReceiver.scheduleNoteAlarm(ctx, n);

        StatusBarNotification rem = waitNotification(NotesApp.REMINDER_CHANNEL_ID,
                "T05-" + tag, "conteudo T05-", 30000);
        assertNotNull("reminder never fired", rem);

        StatusBarNotification alm = waitNotification(NotesApp.ALARM_CHANNEL_ID,
                "T05-" + tag, "Alarme de Nota!", 30000);
        assertNotNull("alarm never fired", alm);
    }

    @Test
    public void t05b_recurringReminderAdvancesAfterFire() {
        long tag = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        int id = insert(newNote("T05b-" + tag, now + 4000, 1, 0, 0, 0, 0));
        AlarmReceiver.rescheduleAlarm(ctx, repo.getNote(id));

        StatusBarNotification rem = waitNotification(NotesApp.REMINDER_CHANNEL_ID,
                "T05b-" + tag, null, 30000);
        assertNotNull("recurring reminder never fired", rem);

        long end = System.currentTimeMillis() + 10000;
        DatabaseHelper.Note after = null;
        while (System.currentTimeMillis() < end) {
            after = repo.getNote(id);
            if (after != null && after.reminderTime > now + 3600000) break;
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        assertNotNull(after);
        assertTrue("reminder was not advanced by ~1 day, got " + (after.reminderTime - now),
                after.reminderTime > now + 3600000);
    }

    // ---------- T06: direct onReceive ----------

    @Test
    public void t06_onReceiveDirect() {
        long tag = System.currentTimeMillis();
        int id = insert(newNote("T06-" + tag, 0, 0, 0, 0, 0, 0));

        ctx.sendBroadcast(new Intent(ctx, AlarmReceiver.class).putExtra("id", id));
        assertNotNull(waitNotification(NotesApp.REMINDER_CHANNEL_ID, "T06-" + tag, null, 10000));

        ctx.sendBroadcast(new Intent(ctx, AlarmReceiver.class)
                .putExtra("id", id).putExtra(NOTE_ALERT_TYPE, 1));
        assertNotNull(waitNotification(NotesApp.ALARM_CHANNEL_ID, "T06-" + tag, "Alarme de Nota!", 10000));

        // unknown id must not crash nor notify
        ctx.sendBroadcast(new Intent(ctx, AlarmReceiver.class).putExtra("id", 987654321));
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    // ---------- T07: boot reschedule ----------

    @Test
    public void t07_bootReschedulesAlarms() {
        long future = System.currentTimeMillis() + 3600000;
        int id = insert(newNote("T07", future, 0, 0, future, 0, 0));
        DatabaseHelper.Note n = repo.getNote(id);
        AlarmReceiver.rescheduleAlarm(ctx, n);
        AlarmReceiver.scheduleNoteAlarm(ctx, n);
        assertTrue(hasNoteAlarm(id));
        assertTrue(hasNoteAlarm(id + NOTE_ALARM_OFFSET));

        // simulate reboot: all PendingIntents lost
        AlarmReceiver.cancelAlarm(ctx, id);
        AlarmReceiver.cancelNoteAlarm(ctx, id);
        assertFalse(hasNoteAlarm(id));

        // NOTE: BOOT_COMPLETED is a protected broadcast, tests cannot send it.
        // Invoke the receiver directly, exactly as the system would.
        new BootReceiver().onReceive(ctx, new Intent("android.intent.action.BOOT_COMPLETED"));
        long end = System.currentTimeMillis() + 15000;
        boolean ok = false;
        while (System.currentTimeMillis() < end) {
            if (hasNoteAlarm(id) && hasNoteAlarm(id + NOTE_ALARM_OFFSET)) { ok = true; break; }
            try { Thread.sleep(500); } catch (InterruptedException e) { break; }
        }
        assertTrue("BootReceiver did not reschedule both alarms", ok);
    }

    // ---------- T08: trash cancels, restore reschedules ----------

    @Test
    public void t08_trashRestoreAlarms() {
        long future = System.currentTimeMillis() + 3600000;
        int id = insert(newNote("T08", future, 0, 0, future, 0, 0));
        // NOTE: NoteViewModel touches LiveData.setValue, so it must run on the main thread.
        final NoteViewModel[] holder = new NoteViewModel[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0] = new NoteViewModel((Application) ctx.getApplicationContext()));
        final NoteViewModel vm = holder[0];
        DatabaseHelper.Note n = repo.getNote(id);
        AlarmReceiver.rescheduleAlarm(ctx, n);
        AlarmReceiver.scheduleNoteAlarm(ctx, n);
        assertTrue(hasNoteAlarm(id));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.moveToTrash(repo.getNote(id)));
        assertFalse(hasNoteAlarm(id));
        assertFalse(hasNoteAlarm(id + NOTE_ALARM_OFFSET));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.restoreNote(repo.getNote(id)));
        assertTrue(hasNoteAlarm(id));
        assertTrue(hasNoteAlarm(id + NOTE_ALARM_OFFSET));
    }

    // ---------- T09: backup roundtrip ----------

    @Test
    public void t09_backupRoundtripKeepsAlarmFields() throws Exception {
        long now = System.currentTimeMillis();
        DatabaseHelper.Note orig = newNote("T09", now + 1000, 2, 5, now + 2000, 3, 7);
        orig.alertType = 2; orig.originalReminderTime = now - 1000; orig.alarmOriginalReminderTime = now - 2000;
        java.util.List<DatabaseHelper.Note> list = new java.util.ArrayList<>();
        list.add(orig);

        String data = BackupManager.serialize(list);
        assertTrue(BackupManager.isEncrypted("garbage") || !BackupManager.isEncrypted(data));
        List<DatabaseHelper.Note> back = BackupManager.deserialize(data);
        assertEquals(1, back.size());
        DatabaseHelper.Note r = back.get(0);
        assertEquals(orig.reminderTime, r.reminderTime);
        assertEquals(orig.recurrenceType, r.recurrenceType);
        assertEquals(orig.recurrenceDays, r.recurrenceDays);
        assertEquals(orig.originalReminderTime, r.originalReminderTime);
        assertEquals(orig.alarmTime, r.alarmTime);
        assertEquals(orig.alarmRecurrenceType, r.alarmRecurrenceType);
        assertEquals(orig.alarmRecurrenceDays, r.alarmRecurrenceDays);
        assertEquals(orig.alarmOriginalReminderTime, r.alarmOriginalReminderTime);
        assertEquals(orig.alertType, r.alertType);
    }

    // ---------- T10: calendar series ----------

    @Test
    public void t10_calendarSeries() {
        try (ActivityScenario<CalendarActivity> sc = ActivityScenario.launch(CalendarActivity.class)) {
            sc.onActivity(a -> {
                try {
                    Method m = CalendarActivity.class.getDeclaredMethod("shouldShowOnDay",
                            java.time.LocalDate.class, DatabaseHelper.Note.class);
                    m.setAccessible(true);

                    java.time.LocalDate jun10 = java.time.LocalDate.of(2026, 6, 10); // Wednesday
                    long jun10ms = jun10.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                    // non-recurring reminder: anchor only
                    DatabaseHelper.Note plain = newNote("c", jun10ms, 0, 0, 0, 0, 0);
                    assertTrue((boolean) m.invoke(a, jun10, plain));
                    assertFalse((boolean) m.invoke(a, jun10.plusDays(1), plain));
                    assertFalse((boolean) m.invoke(a, jun10.minusDays(1), plain));

                    // daily reminder
                    DatabaseHelper.Note daily = newNote("c", jun10ms, 1, 0, 0, 0, 0);
                    assertTrue((boolean) m.invoke(a, jun10.plusDays(5), daily));
                    assertFalse((boolean) m.invoke(a, jun10.minusDays(1), daily));

                    // weekly reminder (Wednesday)
                    DatabaseHelper.Note weekly = newNote("c", jun10ms, 2, 0, 0, 0, 0);
                    assertTrue((boolean) m.invoke(a, jun10.plusWeeks(1), weekly));
                    assertFalse((boolean) m.invoke(a, jun10.plusDays(1), weekly));

                    // monthly edge Jan 31 -> Feb 28 2026
                    long jan31 = java.time.LocalDate.of(2026, 1, 31)
                            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    DatabaseHelper.Note monthly = newNote("c", jan31, 3, 0, 0, 0, 0);
                    assertTrue((boolean) m.invoke(a, java.time.LocalDate.of(2026, 2, 28), monthly));
                    assertFalse((boolean) m.invoke(a, java.time.LocalDate.of(2026, 2, 27), monthly));
                    assertTrue((boolean) m.invoke(a, java.time.LocalDate.of(2026, 3, 31), monthly));

                    // custom Mon+Wed from Mon Jun 8
                    long jun8 = java.time.LocalDate.of(2026, 6, 8)
                            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    int monWed = (1 << 1) | (1 << 3);
                    DatabaseHelper.Note custom = newNote("c", jun8, 5, monWed, 0, 0, 0);
                    assertTrue((boolean) m.invoke(a, java.time.LocalDate.of(2026, 6, 10), custom));
                    assertFalse((boolean) m.invoke(a, java.time.LocalDate.of(2026, 6, 9), custom));

                    // alarm-only daily recurring (Session-9 fix)
                    DatabaseHelper.Note almDaily = newNote("c", 0, 0, 0, jun10ms, 1, 0);
                    assertTrue((boolean) m.invoke(a, jun10, almDaily));
                    assertTrue((boolean) m.invoke(a, jun10.plusDays(3), almDaily));
                    assertFalse((boolean) m.invoke(a, jun10.minusDays(1), almDaily));

                    // alarm-only non-recurring
                    DatabaseHelper.Note almOnce = newNote("c", 0, 0, 0, jun10ms, 0, 0);
                    assertTrue((boolean) m.invoke(a, jun10, almOnce));
                    assertFalse((boolean) m.invoke(a, jun10.plusDays(1), almOnce));

                    // reminder + alarm on different days
                    long jun12 = java.time.LocalDate.of(2026, 6, 12)
                            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    DatabaseHelper.Note both = newNote("c", jun10ms, 0, 0, jun12, 0, 0);
                    assertTrue((boolean) m.invoke(a, jun10, both));
                    assertTrue((boolean) m.invoke(a, java.time.LocalDate.of(2026, 6, 12), both));
                    assertFalse((boolean) m.invoke(a, jun10.plusDays(1), both));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    // ---------- T11: activity smoke ----------

    @Test
    public void t11_activitiesLaunch() {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            s.onActivity(Activity::getTitle);
        }

        try (ActivityScenario<EditActivity> s = ActivityScenario.launch(EditActivity.class)) {
            s.onActivity(Activity::getTitle);
        }
        try (ActivityScenario<ChecklistActivity> s = ActivityScenario.launch(ChecklistActivity.class)) {
            s.onActivity(Activity::getTitle);
        }
        int id = insert(newNote("T11", 0, 0, 0, 0, 0, 0));
        Intent edit = new Intent(ctx, EditActivity.class).putExtra("NOTE_ID", id);
        try (ActivityScenario<EditActivity> s = ActivityScenario.launch(edit)) {
            s.onActivity(Activity::getTitle);
        }
        int cid = insert(checklistNote("T11-list"));
        Intent check = new Intent(ctx, ChecklistActivity.class).putExtra("NOTE_ID", cid);
        try (ActivityScenario<ChecklistActivity> s = ActivityScenario.launch(check)) {
            s.onActivity(Activity::getTitle);
        }
        try (ActivityScenario<CalendarActivity> s = ActivityScenario.launch(CalendarActivity.class)) {
            s.onActivity(Activity::getTitle);
        }
        try (ActivityScenario<SettingsActivity> s = ActivityScenario.launch(SettingsActivity.class)) {
            s.onActivity(Activity::getTitle);
        }
        try (ActivityScenario<TrashActivity> s = ActivityScenario.launch(TrashActivity.class)) {
            s.onActivity(Activity::getTitle);
        }
        // AlarmActivity intentionally NOT launched (plays sound/vibration).
    }
}
