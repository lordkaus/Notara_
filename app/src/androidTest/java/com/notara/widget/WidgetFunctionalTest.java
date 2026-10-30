package com.notara.widget;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.notara.AlarmReceiver;
import com.notara.ChecklistActivity;
import com.notara.DatabaseHelper;
import com.notara.EditActivity;
import com.notara.MainActivity;
import com.notara.NoteRepository;
import com.notara.NoteRepositoryImpl;
import com.notara.NoteViewModel;
import com.notara.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * B12 — Widget battery (on-device).
 *
 * Covers: provider update paths, both RemoteViews factories, config activities
 * (note picker + 1x2 color picker), widget click dispatch, checklist toggle
 * sync, and CRUD -> updateAllWidgets.
 *
 * Not covered here (needs a real bound widget / launcher): 1x2 and 2x2 layout
 * selection, and the visual look on the home screen.
 */
@RunWith(AndroidJUnit4.class)
public class WidgetFunctionalTest {

    private static final int FAKE_WIDGET_ID = 424242;

    private Context ctx;
    private NoteRepository repo;
    private DatabaseHelper db;
    private AppWidgetManager manager;
    private final List<Integer> createdNotes = new ArrayList<>();

    @Before
    public void setup() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = new NoteRepositoryImpl(ctx);
        db = new DatabaseHelper(ctx);
        manager = AppWidgetManager.getInstance(ctx);
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
            } catch (Exception ignored) {}
            try { repo.deleteNoteForever(id); } catch (Exception ignored) {}
        }
        createdNotes.clear();
        ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                .remove("note_" + FAKE_WIDGET_ID)
                .remove("color_" + FAKE_WIDGET_ID)
                .commit();
    }

    // ---------- helpers ----------

    private DatabaseHelper.Note note(String title, String content, int type, int color) {
        return new DatabaseHelper.Note(-1, title, content, type, color, 0, 0, null,
                0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0);
    }

    private int insert(DatabaseHelper.Note n) {
        int id = (int) repo.addNote(n);
        createdNotes.add(id);
        return id;
    }

    private DatabaseHelper.Note reload(int id) {
        return repo.getNote(id);
    }

    private void setWidgetNote(int noteId) {
        ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE).edit()
                .putInt("note_" + FAKE_WIDGET_ID, noteId).commit();
    }

    private void waitUntil(String what, long timeoutMs, Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.call()) return;
            Thread.sleep(100);
        }
        fail("timeout waiting for " + what);
    }

    private static int[] renderPixels(Drawable d, int size) {
        d.setBounds(0, 0, size, size);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        d.draw(new Canvas(bmp));
        int[] pixels = new int[size * size];
        bmp.getPixels(pixels, 0, size, 0, 0, size, size);
        return pixels;
    }

    private static void assertDrawableSame(Drawable actual, int expectedRes, Context context, String msg) {
        Drawable expected = context.getDrawable(expectedRes);
        assertNotNull(msg + " (actual null)", actual);
        assertNotNull(msg + " (expected null)", expected);
        assertArrayEquals(msg, renderPixels(expected, 24), renderPixels(actual, 24));
    }

    private static <T extends View> T findChild(View root, Class<T> type) {
        if (root == null) return null;
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                T found = findChild(g.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void sendWidgetClick(int noteId, int itemIndex) {
        Intent i = new Intent(ctx, NoteWidgetProvider1x1.class);
        i.setAction(NoteWidgetProvider.ACTION_WIDGET_CLICK);
        i.putExtra("NOTE_ID", noteId);
        if (itemIndex != -1) i.putExtra("ITEM_INDEX", itemIndex);
        ctx.sendBroadcast(i);
    }

    private boolean hasPendingAlarm(int requestCode) {
        Intent probe = new Intent(ctx, AlarmReceiver.class);
        return PendingIntent.getBroadcast(ctx, requestCode, probe,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null;
    }

    // ---------- W01: providers do not crash on update ----------

    @Test
    public void w01_providerUpdateSmoke() {
        int noteId = insert(note("W01 nota", "conteudo W01", 0, 0));
        setWidgetNote(noteId);

        int[] ids = new int[]{FAKE_WIDGET_ID};
        new NoteWidgetProvider1x1().onUpdate(ctx, manager, ids);
        new NoteWidgetProvider1x2().onUpdate(ctx, manager, ids);
        new NoteWidgetProvider2x2().onUpdate(ctx, manager, ids);
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
        NoteWidgetProvider.updateAllWidgets(ctx);
    }

    @Test
    public void w02_providerHandlesEveryNoteKindWithoutCrash() {
        int textId = insert(note("W02 texto", "corpo do texto", 0, 2));
        int checkId = insert(note("W02 checklist", "A::0\nB::1", 1, 3));
        int lockedId = insert(note("W02 trancada", "segredo", 0, 4));
        DatabaseHelper.Note locked = reload(lockedId);
        locked.isLocked = 1;
        repo.updateNote(locked);

        for (int id : new int[]{textId, checkId, lockedId, 999999}) {
            setWidgetNote(id);
            NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
        }
        setWidgetNote(-1);
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
    }

    /**
     * EditActivity.noteColors[note.color] is indexed without clamping in the
     * single-note path (the all-notes path clamps). A note restored from a
     * backup/newer palette can carry an out-of-range color.
     */
    @Test
    public void w03_outOfRangeColorDoesNotCrashWidgetUpdate() {
        int id = insert(note("W03 cor invalida", "corpo", 0, 99));
        setWidgetNote(id);
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
    }

    // ---------- W04/W05: all-notes RemoteViews factory ----------

    @Test
    public void w04_allNotesFactoryListsAndRendersNotes() {
        String title = "W04 lista " + System.currentTimeMillis();
        insert(note(title, "conteudo", 0, 1));

        AllNotesWidgetService svc = new AllNotesWidgetService();
        AllNotesWidgetService.AllNotesRemoteViewsFactory f =
                svc.new AllNotesRemoteViewsFactory(ctx.getApplicationContext());
        f.onCreate();

        int expected = repo.searchNotes("", false, null).size();
        assertEquals("factory count must match repository list", expected, f.getCount());
        assertEquals(1, f.getViewTypeCount());
        assertNull(f.getLoadingView());

        int position = -1;
        for (int i = 0; i < f.getCount(); i++) {
            RemoteViews rv = f.getViewAt(i);
            View v = rv.apply(ctx, null);
            TextView tv = v.findViewById(android.R.id.text1);
            if (title.equals(String.valueOf(tv.getText()))) { position = i; break; }
        }
        assertTrue("note not listed by the widget", position >= 0);

        RemoteViews rv = f.getViewAt(position);
        assertNotNull(rv);
        View v = rv.apply(ctx, null);
        assertEquals(title, ((TextView) v.findViewById(android.R.id.text1)).getText().toString());

        ImageView divider = v.findViewById(R.id.divider);
        assertNotNull("divider missing", divider);
        Drawable d = divider.getDrawable();
        assertTrue("divider must be a bitmap", d instanceof BitmapDrawable);
        int color = android.graphics.Color.parseColor(EditActivity.noteColors[1]);
        assertEquals(color, ((BitmapDrawable) d).getBitmap().getPixel(0, 0));

        assertNull(f.getViewAt(-1));
        assertNull(f.getViewAt(f.getCount()));

        int before = f.getCount();
        insert(note("W04 nova " + System.currentTimeMillis(), "", 0, 0));
        f.onDataSetChanged();
        assertEquals(before + 1, f.getCount());
    }

    @Test
    public void w05_allNotesFactoryMasksLockedNotes() {
        int id = insert(note("W05 segredo", "conteudo", 0, 0));
        DatabaseHelper.Note n = reload(id);
        n.isLocked = 1;
        repo.updateNote(n);

        AllNotesWidgetService svc = new AllNotesWidgetService();
        AllNotesWidgetService.AllNotesRemoteViewsFactory f =
                svc.new AllNotesRemoteViewsFactory(ctx.getApplicationContext());
        f.onCreate();
        for (int i = 0; i < f.getCount(); i++) {
            View v = f.getViewAt(i).apply(ctx, null);
            String t = String.valueOf(((TextView) v.findViewById(android.R.id.text1)).getText());
            assertFalse("locked note title leaked", t.equals("W05 segredo"));
            if (t.equals("* Nota Protegida")) return;
        }
        fail("locked note not listed at all (expected masked entry)");
    }

    // ---------- W06/W07: checklist RemoteViews factory ----------

    @Test
    public void w06_checklistFactoryRendersItemsAndStates() {
        int id = insert(note("W06 checklist", "Item A::0\nItem B::1\nlinha livre", 1, 0));

        NoteWidgetService svc = new NoteWidgetService();
        Intent i = new Intent().putExtra("NOTE_ID", id);
        NoteWidgetService.NoteRemoteViewsFactory f =
                svc.new NoteRemoteViewsFactory(ctx.getApplicationContext(), i);
        f.onCreate();

        assertEquals(3, f.getCount());
        View a = f.getViewAt(0).apply(ctx, null);
        View b = f.getViewAt(1).apply(ctx, null);
        View free = f.getViewAt(2).apply(ctx, null);

        assertEquals("Item A", ((TextView) a.findViewById(R.id.widget_item_text)).getText().toString());
        assertEquals("Item B", ((TextView) b.findViewById(R.id.widget_item_text)).getText().toString());
        assertEquals("linha livre", ((TextView) free.findViewById(R.id.widget_item_text)).getText().toString());

        assertDrawableSame(((ImageView) a.findViewById(R.id.widget_item_checkbox)).getDrawable(),
                R.drawable.ic_checkbox_blank, ctx, "unchecked item must use blank checkbox");
        assertDrawableSame(((ImageView) b.findViewById(R.id.widget_item_checkbox)).getDrawable(),
                R.drawable.ic_checkbox_checked, ctx, "checked item must use checked checkbox");
        assertDrawableSame(((ImageView) free.findViewById(R.id.widget_item_checkbox)).getDrawable(),
                R.drawable.ic_checkbox_blank, ctx, "free line must use blank checkbox");

        assertEquals(1, f.getViewTypeCount());
        f.onDataSetChanged();
        assertEquals(3, f.getCount());
    }

    @Test
    public void w07_checklistFactoryMissingNoteIsEmpty() {
        NoteWidgetService svc = new NoteWidgetService();
        NoteWidgetService.NoteRemoteViewsFactory f =
                svc.new NoteRemoteViewsFactory(ctx.getApplicationContext(),
                        new Intent().putExtra("NOTE_ID", -1));
        f.onCreate();
        assertEquals(0, f.getCount());
        assertNull(f.getViewAt(0));
    }

    // ---------- W08/W09: widget click dispatch ----------

    @Test
    public void w08_widgetClickTogglesChecklistItem() throws Exception {
        int id = insert(note("W08 toggle", "Primeiro::0\nSegundo::0", 1, 0));

        sendWidgetClick(id, 0);
        waitUntil("first item checked", 10000, () -> {
            DatabaseHelper.Note n = reload(id);
            return n != null && n.content.startsWith("Primeiro::1");
        });

        int checks = 0;
        for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
            if (it.checked) checks++;
        }
        assertEquals("checklist_items table must mirror the content toggle", 1, checks);

        sendWidgetClick(id, 1);
        waitUntil("second item checked", 10000, () -> {
            DatabaseHelper.Note n = reload(id);
            return n != null && n.content.contains("Segundo::1");
        });

        sendWidgetClick(id, 0);
        waitUntil("first item unchecked", 10000, () -> {
            DatabaseHelper.Note n = reload(id);
            return n != null && n.content.startsWith("Primeiro::0");
        });

        int remaining = 0;
        for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
            if (it.checked) remaining++;
        }
        assertEquals("only the second item should stay checked", 1, remaining);
    }

    @Test
    public void w09_widgetClickWithBadIdsDoesNotCrash() throws Exception {
        sendWidgetClick(987654321, -1);
        sendWidgetClick(-1, -1);
        int textNote = insert(note("W09 texto", "conteudo", 0, 0));
        sendWidgetClick(textNote, 0);       // item index on a text note
        sendWidgetClick(textNote, 9999);    // out of range
        Thread.sleep(1500);
        assertNotNull(reload(textNote));
    }

    /**
     * Regression: toggling a checklist item from a widget used to rebuild the
     * whole checklist_items table from the note content ("name::checked"),
     * wiping reminder_time / alert_type / recurrence_* and assigning new
     * AUTOINCREMENT ids, which also orphaned already scheduled item alarms
     * (request code = noteId*1000 + itemId*2 + alertType).
     */
    @Test
    public void w10_widgetToggleMustPreserveItemSchedules() throws Exception {
        int id = insert(note("W10 agenda", "Agendado::0\nOutro::0", 1, 0));

        long future = System.currentTimeMillis() + 3_600_000;
        DatabaseHelper.ChecklistItem a = new DatabaseHelper.ChecklistItem();
        a.noteId = id; a.name = "Agendado"; a.position = 0; a.checked = false;
        a.reminderTime = future; a.alertType = 2; a.recurrenceType = 1;
        db.insertChecklistItem(a);
        DatabaseHelper.ChecklistItem b = new DatabaseHelper.ChecklistItem();
        b.noteId = id; b.name = "Outro"; b.position = 1; b.checked = false;
        b.reminderTime = future + 1000; b.alertType = 1; b.recurrenceType = 0;
        db.insertChecklistItem(b);

        int originalId = a.id;
        int otherId = b.id;
        AlarmReceiver.rescheduleItemAlarm(ctx, id, a);
        AlarmReceiver.rescheduleItemAlarm(ctx, id, b);
        assertTrue(hasPendingAlarm(id * 1000 + a.id * 2));
        assertTrue(hasPendingAlarm(id * 1000 + b.id * 2 + 1));

        sendWidgetClick(id, 0);
        try {
            waitUntil("toggle applied", 10000, () -> {
                DatabaseHelper.Note n = reload(id);
                return n != null && n.content.startsWith("Agendado::1");
            });

            DatabaseHelper.ChecklistItem reloadedA = null, reloadedB = null;
            for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
                if (it.name.equals("Agendado")) reloadedA = it;
                if (it.name.equals("Outro")) reloadedB = it;
            }
            assertNotNull(reloadedA);
            assertNotNull(reloadedB);

            assertEquals("item id changed -> old alarms orphaned", originalId, reloadedA.id);
            assertEquals("other item id changed -> old alarms orphaned", otherId, reloadedB.id);
            assertEquals("reminderTime wiped by widget toggle", future, reloadedA.reminderTime);
            assertEquals("alertType wiped by widget toggle", 2, reloadedA.alertType);
            assertEquals("recurrenceType wiped by widget toggle", 1, reloadedA.recurrenceType);
            assertEquals("second item schedule wiped by widget toggle", future + 1000, reloadedB.reminderTime);
            assertEquals("second item alertType wiped by widget toggle", 1, reloadedB.alertType);
        } finally {
            for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
                AlarmReceiver.cancelItemAlarm(ctx, id, it.id);
            }
            AlarmReceiver.cancelItemAlarm(ctx, id, originalId);
            AlarmReceiver.cancelItemAlarm(ctx, id, otherId);
        }
    }

    // ---------- W11/W12: config activities ----------

    @Test
    public void w11_notePickerConfigSavesSelectedNote() {
        String title = "W11 escolha " + System.currentTimeMillis();
        int id = insert(note(title, "conteudo", 0, 0));

        Intent i = new Intent(ctx, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, FAKE_WIDGET_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<WidgetConfigActivity> sc = ActivityScenario.launchActivityForResult(i)) {
            sc.onActivity(activity -> {
                ListView list = findChild(activity.getWindow().getDecorView(), ListView.class);
                assertNotNull("note picker list not found", list);
                ArrayAdapter<?> adapter = (ArrayAdapter<?>) list.getAdapter();
                int pos = -1;
                for (int p = 0; p < adapter.getCount(); p++) {
                    if (String.valueOf(adapter.getItem(p)).equals(title)) { pos = p; break; }
                }
                assertTrue("note not offered by picker", pos >= 0);
                list.performItemClick(null, pos, adapter.getItemId(pos));
            });
            assertEquals(Activity.RESULT_OK, sc.getResult().getResultCode());
        }

        assertEquals("selected note must be persisted for the widget id", id,
                ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                        .getInt("note_" + FAKE_WIDGET_ID, -1));
    }

    @Test
    public void w12_colorPickerConfigSavesColor() {
        Intent i = new Intent(ctx, WidgetConfigActivity1x2.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, FAKE_WIDGET_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<WidgetConfigActivity1x2> sc = ActivityScenario.launchActivityForResult(i)) {
            sc.onActivity(activity -> {
                GridView grid = activity.findViewById(R.id.colorGrid);
                assertNotNull(grid);
                assertEquals(EditActivity.noteColors.length, grid.getAdapter().getCount());
                grid.performItemClick(null, 2, 2);
            });
            assertEquals(Activity.RESULT_OK, sc.getResult().getResultCode());
        }
        assertEquals(2, ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                .getInt("color_" + FAKE_WIDGET_ID, -1));
    }

    // ---------- W13: click opens the right screen ----------

    @Test
    public void w13_widgetClickOpensNoteScreen() {
        int textId = insert(note("W13 texto", "corpo", 0, 0));
        int checkId = insert(note("W13 check", "A::0", 1, 0));

        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            main.onActivity(a -> assertNotNull(a));

            Instrumentation instr = InstrumentationRegistry.getInstrumentation();

            Instrumentation.ActivityMonitor editMonitor =
                    new Instrumentation.ActivityMonitor(EditActivity.class.getName(), null, false);
            instr.addMonitor(editMonitor);
            sendWidgetClick(textId, -1);
            Activity edit = instr.waitForMonitorWithTimeout(editMonitor, 10000);
            instr.removeMonitor(editMonitor);
            assertNotNull("widget click did not open EditActivity", edit);
            assertEquals(textId, edit.getIntent().getIntExtra("NOTE_ID", -1));
            instr.runOnMainSync(edit::finish);

            Instrumentation.ActivityMonitor checkMonitor =
                    new Instrumentation.ActivityMonitor(ChecklistActivity.class.getName(), null, false);
            instr.addMonitor(checkMonitor);
            sendWidgetClick(checkId, -1);
            Activity check = instr.waitForMonitorWithTimeout(checkMonitor, 10000);
            instr.removeMonitor(checkMonitor);
            assertNotNull("widget click did not open ChecklistActivity", check);
            assertEquals(checkId, check.getIntent().getIntExtra("NOTE_ID", -1));
            instr.runOnMainSync(check::finish);
        }
    }

    // ---------- W14: CRUD propagation ----------

    @Test
    public void w14_crudPropagationToWidgetsDoesNotCrash() {
        int id = insert(note("W14 nota", "v1", 0, 0));
        setWidgetNote(id);
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
        NoteWidgetProvider.updateAllWidgets(ctx);

        DatabaseHelper.Note n = reload(id);
        n.title = "W14 editada";
        n.content = "v2";
        repo.updateNote(n);
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);

        final NoteViewModel[] holder = new NoteViewModel[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> holder[0] =
                new NoteViewModel((android.app.Application) ctx.getApplicationContext()));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0].deleteNoteForever(id));
        NoteWidgetProvider.updateAppWidget(ctx, manager, FAKE_WIDGET_ID);
    }
}
