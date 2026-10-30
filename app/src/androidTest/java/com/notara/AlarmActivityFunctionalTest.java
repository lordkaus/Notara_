package com.notara;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * B15 — Full-screen alarm screen (on-device).
 *
 * Covers: normal rendering, locked-note masking (no title/ciphertext/item
 * names), checklist rendering + toggle persistence, dismiss button opening
 * the editor, and invalid ids.
 *
 * Not covered here (manual): real alarm sound/vibration, screen wake while
 * locked, and the full-screen intent permission flow.
 */
@RunWith(AndroidJUnit4.class)
public class AlarmActivityFunctionalTest {

    private Context ctx;
    private NoteRepository repo;
    private DatabaseHelper db;
    private AudioManager audio;
    private int oldAlarmVolume;
    private final List<Integer> createdNotes = new ArrayList<>();

    @Before
    public void setup() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = new NoteRepositoryImpl(ctx);
        db = new DatabaseHelper(ctx);
        // Silence the alarm stream for the duration of the test (the screen
        // plays the real ringtone otherwise).
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        oldAlarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);
        audio.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
    }

    @After
    public void teardown() {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, oldAlarmVolume, 0);
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

    private int insert(String title, String content, int type) {
        int id = (int) repo.addNote(new DatabaseHelper.Note(-1, title, content, type, 0,
                0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0));
        createdNotes.add(id);
        return id;
    }

    private void lock(int id) {
        DatabaseHelper.Note note = repo.getNote(id);
        try {
            note.content = SecurityCore.encrypt(note.content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        note.isLocked = 1;
        repo.updateNote(note);
    }

    private ActivityScenario<AlarmActivity> launch(int noteId) {
        return ActivityScenario.launch(new Intent(ctx, AlarmActivity.class)
                .putExtra("id", noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static String text(Activity a, int id) {
        TextView tv = a.findViewById(id);
        assertNotNull("missing view " + id, tv);
        return tv.getText().toString();
    }

    @SuppressWarnings("unchecked")
    private static List<AlarmActivity.CheckItem> checkItems(Activity a) throws Exception {
        Field f = AlarmActivity.class.getDeclaredField("checkItems");
        f.setAccessible(true);
        return (List<AlarmActivity.CheckItem>) f.get(a);
    }

    private static void callSaveChecklist(Activity a) throws Exception {
        Method m = AlarmActivity.class.getDeclaredMethod("saveChecklist");
        m.setAccessible(true);
        m.invoke(a);
    }

    // ---------- T01/T02: text rendering + locked masking ----------

    @Test
    public void t01_textNoteShowsRealTitleAndContent() {
        int id = insert("B15 Título", "B15 conteúdo do alarme", 0);
        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                assertEquals("B15 Título", text(a, R.id.tvAlarmTitle));
                assertEquals("B15 conteúdo do alarme", text(a, R.id.tvAlarmContent));
                RecyclerView rv = a.findViewById(R.id.rvAlarmChecklist);
                assertTrue(rv == null || rv.getVisibility() == View.GONE);
            });
        }
    }

    @Test
    public void t02_lockedNoteMasksEverythingAndStaysDismissible() {
        int id = insert("B15 SEGREDO", "B15 corpo secreto", 0);
        lock(id);
        String cipher = repo.getNote(id).content;

        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                String title = text(a, R.id.tvAlarmTitle);
                String body = text(a, R.id.tvAlarmContent);
                assertFalse("locked title leaked on the alarm screen", title.contains("B15 SEGREDO"));
                assertFalse("locked content leaked on the alarm screen", body.contains("B15 corpo secreto"));
                assertFalse("ciphertext shown on the alarm screen",
                        body.contains(cipher.substring(0, Math.min(12, cipher.length()))));

                Button stop = a.findViewById(R.id.btnStopAlarm);
                assertNotNull(stop);
                assertTrue("alarm screen must remain dismissible", stop.hasOnClickListeners());
            });
        }
    }

    // ---------- T03/T04: checklist rendering + locked editing ----------

    @Test
    public void t03_checklistNoteShowsItems() throws Exception {
        int id = insert("B15 checklist", "Item A::0\nItem B::1", 1);
        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                List<AlarmActivity.CheckItem> items;
                try {
                    items = checkItems(a);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                assertEquals(2, items.size());
                assertEquals("Item A", items.get(0).name);
                assertFalse(items.get(0).checked);
                assertEquals("Item B", items.get(1).name);
                assertTrue(items.get(1).checked);
                RecyclerView rv = a.findViewById(R.id.rvAlarmChecklist);
                assertNotNull(rv);
                assertEquals(View.VISIBLE, rv.getVisibility());
                assertEquals(2, rv.getAdapter().getItemCount());
            });
        }
    }

    @Test
    public void t04_lockedChecklistCannotBeReadOrEditedIntoPlaintext() throws Exception {
        int id = insert("B15 checklist segredo", "Item secreto::0", 1);
        lock(id);
        String cipher = repo.getNote(id).content;

        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                String body = text(a, R.id.tvAlarmContent);
                assertFalse("ciphertext shown for a locked checklist",
                        body.contains(cipher.substring(0, Math.min(12, cipher.length()))));
                RecyclerView rv = a.findViewById(R.id.rvAlarmChecklist);
                assertTrue("locked checklist items must stay hidden", rv == null || rv.getVisibility() == View.GONE);

                try {
                    List<AlarmActivity.CheckItem> items = checkItems(a);
                    if (!items.isEmpty()) items.get(0).checked = true;
                    callSaveChecklist(a);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertEquals("alarm screen rewrote a locked note", cipher, repo.getNote(id).content);
        assertEquals(1, repo.getNote(id).isLocked);
    }

    @Test
    public void t05_checklistTogglePersistsAndKeepsItemSchedule() throws Exception {
        long future = System.currentTimeMillis() + 3_600_000;
        int id = insert("B15 agenda", "Item::0", 1);
        DatabaseHelper.ChecklistItem row = new DatabaseHelper.ChecklistItem();
        row.noteId = id;
        row.name = "Item";
        row.position = 0;
        row.reminderTime = future;
        row.alertType = 1;
        db.insertChecklistItem(row);

        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                try {
                    List<AlarmActivity.CheckItem> items = checkItems(a);
                    assertEquals(1, items.size());
                    items.get(0).checked = true;
                    callSaveChecklist(a);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        DatabaseHelper.Note note = repo.getNote(id);
        assertTrue("alarm toggle did not update note.content", note.content.startsWith("Item::1"));
        List<DatabaseHelper.ChecklistItem> rows = db.getChecklistItems(id);
        assertEquals(1, rows.size());
        assertTrue("alarm toggle did not update checklist_items (widget/editor stays stale)", rows.get(0).checked);
        assertEquals("item id changed -> alarm orphaned", row.id, rows.get(0).id);
        assertEquals("item schedule wiped by alarm toggle", future, rows.get(0).reminderTime);
        assertEquals(1, rows.get(0).alertType);
    }

    // ---------- T06/T07: dismiss + invalid id ----------

    @Test
    public void t06_stopOpensTheNoteEditor() {
        int id = insert("B15 abrir", "corpo", 0);
        Instrumentation instr = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(EditActivity.class.getName(), null, false);
        instr.addMonitor(monitor);
        try (ActivityScenario<AlarmActivity> sc = launch(id)) {
            sc.onActivity(a -> {
                Button stop = a.findViewById(R.id.btnStopAlarm);
                assertNotNull(stop);
                stop.performClick();
            });
            Activity edit = instr.waitForMonitorWithTimeout(monitor, 10000);
            assertNotNull("stop did not open the note editor", edit);
            assertEquals(id, edit.getIntent().getIntExtra("NOTE_ID", -1));
            instr.runOnMainSync(edit::finish);
        } finally {
            instr.removeMonitor(monitor);
        }
    }

    @Test
    public void t07_missingNoteDoesNotCrash() {
        try (ActivityScenario<AlarmActivity> sc = launch(-1)) {
            sc.onActivity(a -> {
                assertNotNull(a.findViewById(R.id.tvAlarmTitle));
                Button stop = a.findViewById(R.id.btnStopAlarm);
                assertNotNull(stop);
                assertTrue(stop.hasOnClickListeners());
            });
        }
    }
}
