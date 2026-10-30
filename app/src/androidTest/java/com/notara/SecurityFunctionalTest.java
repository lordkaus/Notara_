package com.notara;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * B14 — Security battery (on-device).
 *
 * Covers: AES-GCM roundtrip + tamper detection, lock/unlock of text and
 * checklist notes, masking in notifications and full-screen alarms, alarm
 * cancellation on reset/trash, and the internal password.
 *
 * Not covered here (manual): biometric prompt, device credential prompt,
 * real notification/alarm sound + full screen wake, widget visual masking
 * (widget battery already asserts the masked title).
 */
@RunWith(AndroidJUnit4.class)
public class SecurityFunctionalTest {

    private static final int NOTE_ALARM_OFFSET = 50000;

    private Context ctx;
    private NotificationManager nm;
    private NoteRepository repo;
    private DatabaseHelper db;
    private final List<Integer> createdNotes = new ArrayList<>();

    @BeforeClass
    public static void grantNotificationPermission() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "pm grant " + ctx.getPackageName() + " android.permission.POST_NOTIFICATIONS");
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set " + ctx.getPackageName() + " POST_NOTIFICATION allow");
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
        nm.cancelAll();
    }

    // ---------- helpers ----------

    private int insertText(String title, String content) {
        int id = (int) repo.addNote(new DatabaseHelper.Note(-1, title, content, 0, 0,
                0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0));
        createdNotes.add(id);
        return id;
    }

    private int insertChecklist(String title, String content) {
        int id = (int) repo.addNote(new DatabaseHelper.Note(-1, title, content, 1, 0,
                0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0));
        createdNotes.add(id);
        return id;
    }

    private DatabaseHelper.Note reload(int id) {
        return repo.getNote(id);
    }

    private static String decrypt(String cipher) {
        try {
            return SecurityCore.decrypt(cipher);
        } catch (Exception e) {
            fail("decryption failed: " + e);
            return null;
        }
    }

    private NoteViewModel vmOnMain() {
        final NoteViewModel[] holder = new NoteViewModel[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0] = new NoteViewModel((android.app.Application) ctx.getApplicationContext()));
        return holder[0];
    }

    private void onMain(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    private boolean hasPendingAlarm(int requestCode) {
        Intent probe = new Intent(ctx, AlarmReceiver.class);
        return PendingIntent.getBroadcast(ctx, requestCode, probe,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
    }

    private static String notifTitle(StatusBarNotification sbn) {
        return sbn.getNotification().extras.getString(Notification.EXTRA_TITLE, "");
    }

    private static String notifText(StatusBarNotification sbn) {
        CharSequence cs = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        return cs == null ? "" : cs.toString();
    }

    private void invokeSendNotification(DatabaseHelper.Note note, int itemId) throws Exception {
        Method m = AlarmReceiver.class.getDeclaredMethod(
                "sendNotification", Context.class, DatabaseHelper.Note.class, int.class);
        m.setAccessible(true);
        m.invoke(new AlarmReceiver(), ctx, note, itemId);
    }

    private void invokeSendFullScreenAlarm(DatabaseHelper.Note note, int itemId) throws Exception {
        Method m = AlarmReceiver.class.getDeclaredMethod(
                "sendFullScreenAlarm", Context.class, DatabaseHelper.Note.class, int.class);
        m.setAccessible(true);
        m.invoke(new AlarmReceiver(), ctx, note, itemId);
    }

    private void waitForAnyNotification(long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (nm.getActiveNotifications().length > 0) return;
            Thread.sleep(100);
        }
        fail("no notification was posted");
    }

    // ---------- S01: crypto core ----------

    @Test
    public void s01_cryptoRoundtripAndTamperDetection() throws Exception {
        String[] samples = {
                "simples",
                "",
                "acentuação çãõé — emoji 🔒📝 e unicode 中文",
                repeat("A", 100_000)
        };
        for (String plain : samples) {
            String enc = SecurityCore.encrypt(plain);
            assertNotNull(enc);
            if (!plain.isEmpty()) assertFalse("ciphertext must not equal plaintext", enc.equals(plain));
            assertEquals("roundtrip failed for length " + plain.length(), plain, SecurityCore.decrypt(enc));
        }

        String enc = SecurityCore.encrypt("segredo original");
        assertNotEquals("two encryptions of the same text must differ (random IV)",
                enc, SecurityCore.encrypt("segredo original"));

        // Tamper with the payload -> GCM authentication must reject it.
        String tampered = enc.substring(0, enc.length() - 2)
                + (enc.endsWith("A") ? "B" : "A") + enc.substring(enc.length() - 1);
        try {
            SecurityCore.decrypt(tampered);
            fail("tampered ciphertext was accepted");
        } catch (Exception expected) { /* good */ }

        try {
            SecurityCore.decrypt("not-a-valid-payload");
            fail("garbage payload was accepted");
        } catch (Exception expected) { /* good */ }
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // ---------- S02/S03: lock roundtrip ----------

    @Test
    public void s02_lockUnlockTextNoteRoundtrip() {
        String title = "S02 Título confidencial";
        String content = "conteúdo secreto do usuário";
        int id = insertText(title, content);
        NoteViewModel vm = vmOnMain();

        DatabaseHelper.Note note = reload(id);
        onMain(() -> vm.toggleLock(note));

        DatabaseHelper.Note locked = reload(id);
        assertEquals(1, locked.isLocked);
        assertNotEquals("content must be encrypted at rest", content, locked.content);
        assertEquals("title stays as-is (used by the list mask)", title, locked.title);
        assertEquals(content, decrypt(locked.content));

        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note unlocked = reload(id);
        assertEquals(0, unlocked.isLocked);
        assertEquals("unlock must restore the exact plaintext", content, unlocked.content);
    }

    @Test
    public void s03_lockUnlockChecklistNoteRoundtrip() {
        String title = "S03 Checklist trancada";
        String content = "Item um::0\nItem dois::1";
        int id = insertChecklist(title, content);
        NoteViewModel vm = vmOnMain();

        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note locked = reload(id);
        assertEquals(1, locked.isLocked);
        assertFalse("checklist serialized content must be encrypted",
                locked.content.contains("Item um"));
        assertEquals(content, decrypt(locked.content));

        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note unlocked = reload(id);
        assertEquals(0, unlocked.isLocked);
        assertEquals(content, unlocked.content);
    }

    /** Lock must survive the trash roundtrip without ever exposing plaintext. */
    @Test
    public void s04_trashRestoreKeepsLockedNoteEncrypted() {
        String content = "segredo que não pode vazar na lixeira";
        int id = insertText("S04 lixeira", content);
        NoteViewModel vm = vmOnMain();

        onMain(() -> vm.toggleLock(reload(id)));
        String cipher = reload(id).content;

        onMain(() -> vm.moveToTrash(reload(id)));
        DatabaseHelper.Note trashed = reload(id);
        assertEquals(1, trashed.isTrashed);
        assertEquals("trash must not decrypt the note", cipher, trashed.content);

        onMain(() -> vm.restoreNote(reload(id)));
        DatabaseHelper.Note restored = reload(id);
        assertEquals(0, restored.isTrashed);
        assertEquals("restore must keep the note encrypted", cipher, restored.content);
        assertEquals(content, decrypt(restored.content));
    }

    // ---------- S05/S06/S07: masking in notifications/alarms ----------

    @Test
    public void s05_lockedNoteNotificationDoesNotLeakTitleOrContent() throws Exception {
        String title = "S05 TÍTULO SECRETO";
        String content = "S05 corpo ultra secreto";
        int id = insertText(title, content);
        NoteViewModel vm = vmOnMain();
        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note locked = reload(id);
        invokeSendNotification(locked, -1);
        waitForAnyNotification(10000);

        StatusBarNotification sbn = null;
        for (StatusBarNotification s : nm.getActiveNotifications()) sbn = s;
        assertNotNull(sbn);
        String shownTitle = notifTitle(sbn);
        String shownText = notifText(sbn);

        assertFalse("locked note title leaked into the notification title: " + shownTitle,
                shownTitle.contains(title));
        assertFalse("locked note title leaked into the notification body: " + shownText,
                shownText.contains(title));
        assertFalse("locked note content leaked into the notification body: " + shownText,
                shownText.contains(content));
        assertFalse("ciphertext must not be shown to the user: " + shownText,
                shownText.contains(locked.content.substring(0, Math.min(12, locked.content.length()))));
    }

    @Test
    public void s06_lockedChecklistItemNotificationDoesNotLeakItemName() throws Exception {
        String itemName = "S06 ITEM CONFIDENCIAL";
        int id = insertChecklist("S06 checklist", itemName + "::0");
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = id;
        item.name = itemName;
        item.position = 0;
        item.checked = false;
        db.insertChecklistItem(item);

        NoteViewModel vm = vmOnMain();
        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note locked = reload(id);
        invokeSendNotification(locked, item.id);
        waitForAnyNotification(10000);

        StatusBarNotification sbn = null;
        for (StatusBarNotification s : nm.getActiveNotifications()) sbn = s;
        assertNotNull(sbn);
        assertFalse("locked checklist item name leaked (title)",
                notifTitle(sbn).contains(itemName));
        assertFalse("locked checklist item name leaked (body)",
                notifText(sbn).contains(itemName));
    }

    @Test
    public void s07_lockedNoteFullScreenAlarmDoesNotLeakTitle() throws Exception {
        String title = "S07 ALARME SECRETO";
        int id = insertText(title, "corpo");
        NoteViewModel vm = vmOnMain();
        onMain(() -> vm.toggleLock(reload(id)));

        invokeSendFullScreenAlarm(reload(id), -1);
        waitForAnyNotification(10000);

        StatusBarNotification sbn = null;
        for (StatusBarNotification s : nm.getActiveNotifications()) sbn = s;
        assertNotNull(sbn);
        assertFalse("locked note title leaked into the alarm notification",
                notifTitle(sbn).contains(title));
        assertFalse("locked note title leaked into the alarm body",
                notifText(sbn).contains(title));
    }

    // ---------- S08/S09: alarm cancellation on reset/trash ----------

    @Test
    public void s08_resetAllNotesCancelsNoteAndItemAlarms() throws Exception {
        long future = System.currentTimeMillis() + 3_600_000;
        int id = insertChecklist("S08 reset", "Item::0");
        DatabaseHelper.Note note = reload(id);
        note.reminderTime = future;
        note.alarmTime = future + 1000;
        repo.updateNote(note);

        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = id;
        item.name = "Item";
        item.position = 0;
        item.reminderTime = future + 2000;
        item.alertType = 2;
        db.insertChecklistItem(item);

        AlarmReceiver.rescheduleAlarm(ctx, reload(id));
        AlarmReceiver.scheduleNoteAlarm(ctx, reload(id));
        AlarmReceiver.rescheduleItemAlarm(ctx, id, item);

        assertTrue("note notification not scheduled", hasPendingAlarm(id));
        assertTrue("note alarm not scheduled", hasPendingAlarm(id + NOTE_ALARM_OFFSET));
        assertTrue("item notification not scheduled", hasPendingAlarm(id * 1000 + item.id * 2));
        assertTrue("item alarm not scheduled", hasPendingAlarm(id * 1000 + item.id * 2 + 1));

        NoteViewModel vm = vmOnMain();
        onMain(vm::resetAllNotes);

        assertFalse("note notification survived resetAllNotes", hasPendingAlarm(id));
        assertFalse("note alarm survived resetAllNotes", hasPendingAlarm(id + NOTE_ALARM_OFFSET));
        assertFalse("item notification survived resetAllNotes", hasPendingAlarm(id * 1000 + item.id * 2));
        assertFalse("item alarm survived resetAllNotes", hasPendingAlarm(id * 1000 + item.id * 2 + 1));
    }

    @Test
    public void s09_trashCancelsAndRestoreReschedulesItemAlarms() throws Exception {
        long future = System.currentTimeMillis() + 3_600_000;
        int id = insertChecklist("S09 lixeira itens", "Item::0");
        DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
        item.noteId = id;
        item.name = "Item";
        item.position = 0;
        item.reminderTime = future;
        item.alertType = 0;
        db.insertChecklistItem(item);
        AlarmReceiver.rescheduleItemAlarm(ctx, id, item);
        int requestCode = id * 1000 + item.id * 2;
        assertTrue("item alarm not scheduled before trashing", hasPendingAlarm(requestCode));

        NoteViewModel vm = vmOnMain();
        onMain(() -> vm.moveToTrash(reload(id)));

        assertFalse("moving a checklist to trash must cancel its item alarms",
                hasPendingAlarm(requestCode));

        onMain(() -> vm.restoreNote(reload(id)));

        assertTrue("restoring a checklist must reschedule future item alarms",
                hasPendingAlarm(requestCode));
    }

    // ---------- S10: internal password ----------

    @Test
    public void s10_internalPasswordRoundtrip() throws Exception {
        SecurityManager sm = new SecurityManager(ctx);
        Method save = SecurityManager.class.getDeclaredMethod("saveInternalPassword", String.class);
        save.setAccessible(true);
        Method verify = SecurityManager.class.getDeclaredMethod("verifyInternalPassword", String.class);
        verify.setAccessible(true);

        String password = "S3nh@-测试-🔐";
        assertFalse("no password should be set at test start", sm.isInternalPasswordSet());
        save.invoke(sm, password);

        assertTrue(sm.isInternalPasswordSet());
        assertTrue("correct password rejected", (Boolean) verify.invoke(sm, password));
        assertFalse("wrong password accepted", (Boolean) verify.invoke(sm, "errada"));
        assertFalse("empty password accepted", (Boolean) verify.invoke(sm, ""));

        sm.clearAll();
        assertFalse("clearAll must remove the internal password", sm.isInternalPasswordSet());
    }

    // ---------- S11: locked checklist editing ----------

    /**
     * A locked checklist is edited inside the decrypted view. Saving used to
     * skip the checklist_items table (because wasLocked==true), so unlocking
     * later loaded the stale rows and discarded the edits. It also must keep
     * the item id/schedule while doing so, since the id is part of the alarm
     * request code.
     */
    @Test
    public void s11_lockedChecklistEditsSurviveUnlockAndKeepSchedules() throws Exception {
        long future = System.currentTimeMillis() + 3_600_000;
        int id = insertChecklist("S11 checklist", "Antigo::0\nOutro::0");

        DatabaseHelper.ChecklistItem scheduled = new DatabaseHelper.ChecklistItem();
        scheduled.noteId = id;
        scheduled.name = "Antigo";
        scheduled.position = 0;
        scheduled.reminderTime = future;
        scheduled.alertType = 1;
        db.insertChecklistItem(scheduled);
        int scheduledId = scheduled.id;
        AlarmReceiver.rescheduleItemAlarm(ctx, id, scheduled);
        assertTrue(hasPendingAlarm(id * 1000 + scheduledId * 2 + 1));

        NoteViewModel vm = vmOnMain();
        onMain(() -> vm.toggleLock(reload(id)));
        assertEquals(1, reload(id).isLocked);

        // What the editor does while unlocked: rename + re-encrypt the content.
        DatabaseHelper.Note locked = reload(id);
        locked.content = SecurityCore.encrypt("Editado::0\nOutro::0");
        repo.updateNote(locked);

        List<DatabaseHelper.ChecklistItem> edited = new ArrayList<>();
        DatabaseHelper.ChecklistItem e1 = new DatabaseHelper.ChecklistItem();
        e1.name = "Editado";
        e1.position = 0;
        DatabaseHelper.ChecklistItem e2 = new DatabaseHelper.ChecklistItem();
        e2.name = "Outro";
        e2.position = 1;
        edited.add(e1);
        edited.add(e2);
        ChecklistActivity.syncChecklistItemsTable(ctx, id, edited);

        onMain(() -> vm.toggleLock(reload(id)));

        DatabaseHelper.Note unlocked = reload(id);
        assertEquals(0, unlocked.isLocked);
        assertEquals("Editado::0\nOutro::0", unlocked.content);

        List<DatabaseHelper.ChecklistItem> rows = db.getChecklistItems(id);
        assertEquals(2, rows.size());
        DatabaseHelper.ChecklistItem editedRow = null;
        for (DatabaseHelper.ChecklistItem row : rows) {
            assertNotEquals("stale row survived unlock", "Antigo", row.name);
            if (row.name.equals("Editado")) editedRow = row;
        }
        assertNotNull("edited item was lost when unlocking", editedRow);
        assertEquals("item id changed -> its alarm is orphaned", scheduledId, editedRow.id);
        assertEquals("item schedule lost while editing a locked note", future, editedRow.reminderTime);
        assertEquals(1, editedRow.alertType);
        assertTrue("item alarm was not rescheduled after the edit",
                hasPendingAlarm(id * 1000 + scheduledId * 2 + 1));
    }
}
