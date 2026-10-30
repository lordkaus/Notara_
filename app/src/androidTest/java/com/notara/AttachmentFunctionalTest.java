package com.notara;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * B13 — Attachments / media / drawing (on-device).
 *
 * Covers: attachment copy + DB metadata, per-attachment delete vs whole-note
 * delete, cascade on permanent delete / clear trash / reset, image viewer
 * annotation save, thumbnail generation, corrupt files.
 *
 * Not covered here: picking from the real gallery/camera, audio recording and
 * playback (manual).
 */
@RunWith(AndroidJUnit4.class)
public class AttachmentFunctionalTest {

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
                AttachmentManager.deleteNoteAttachmentDir(ctx, id);
                AlarmReceiver.cancelAlarm(ctx, id);
                AlarmReceiver.cancelNoteAlarm(ctx, id);
                repo.deleteNoteForever(id);
            } catch (Exception ignored) {}
        }
        createdNotes.clear();
    }

    // ---------- helpers ----------

    private int insertNote(String title) {
        int id = (int) repo.addNote(new DatabaseHelper.Note(-1, title, "conteudo", 0, 0, 0, 0,
                "B13TEST", 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0));
        createdNotes.add(id);
        return id;
    }

    private File png(int w, int h) throws Exception {
        File f = new File(ctx.getCacheDir(), "b13_" + System.nanoTime() + ".png");
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(Color.WHITE);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        bmp.recycle();
        return f;
    }

    private File fakeAudio() throws Exception {
        File f = new File(ctx.getCacheDir(), "b13_" + System.nanoTime() + ".mp3");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(new byte[4096]);
        }
        return f;
    }

    private DatabaseHelper.Attachment attach(int noteId, File src, int type) throws Exception {
        DatabaseHelper.Attachment a = AttachmentManager.saveAttachmentFromUri(ctx, android.net.Uri.fromFile(src), noteId, type);
        db.insertAttachment(a);
        return a;
    }

    private File dirOf(int noteId) {
        return new File(ctx.getFilesDir(), "attachments/" + noteId);
    }

    private NoteViewModel vmOnMain() {
        final NoteViewModel[] holder = new NoteViewModel[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> holder[0] = new NoteViewModel((android.app.Application) ctx.getApplicationContext()));
        return holder[0];
    }

    // ---------- A01/A02: attachment copy + metadata ----------

    @Test
    public void a01_imageAttachmentIsCopiedAndRegistered() throws Exception {
        int noteId = insertNote("A01 imagem");
        File src = png(64, 64);

        DatabaseHelper.Attachment a = attach(noteId, src, 0);
        assertTrue("attachment id not assigned", a.id > 0);
        assertEquals(noteId, a.noteId);
        assertEquals(0, a.type);
        assertEquals(src.length(), a.fileSize);
        assertNotNull(a.filePath);

        File stored = AttachmentManager.getAttachmentFile(ctx, a);
        assertTrue("copied file missing", stored.exists());
        assertEquals("copied file must keep the source size", src.length(), stored.length());
        assertTrue("file must live under the note directory", stored.getParentFile().equals(dirOf(noteId)));

        List<DatabaseHelper.Attachment> list = db.getAttachments(noteId);
        assertEquals(1, list.size());
        assertEquals(a.fileName, list.get(0).fileName);
    }

    @Test
    public void a02_audioAttachmentIsCopiedAndRegistered() throws Exception {
        int noteId = insertNote("A02 audio");
        File src = fakeAudio();

        DatabaseHelper.Attachment a = attach(noteId, src, 1);
        assertEquals(1, a.type);
        assertEquals(src.length(), a.fileSize);
        assertTrue(AttachmentManager.getAttachmentFile(ctx, a).exists());
        assertEquals(1, db.getAttachments(noteId).size());
    }

    @Test
    public void a03_thumbnailHandlesValidCorruptAndMissingFiles() throws Exception {
        File valid = png(500, 500);
        Bitmap thumb = AttachmentManager.generateThumbnail(valid.getAbsolutePath(), 200);
        assertNotNull(thumb);
        assertTrue(thumb.getWidth() > 0 && thumb.getHeight() > 0);
        thumb.recycle();

        File corrupt = new File(ctx.getCacheDir(), "b13_corrupt_" + System.nanoTime() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(corrupt)) {
            fos.write(new byte[]{1, 2, 3, 4, 5});
        }
        assertNull(AttachmentManager.generateThumbnail(corrupt.getAbsolutePath(), 200));

        File missing = new File(ctx.getCacheDir(), "b13_missing_" + System.nanoTime() + ".png");
        assertNull(AttachmentManager.generateThumbnail(missing.getAbsolutePath(), 200));
    }

    // ---------- A04: deleting one attachment keeps the others ----------

    @Test
    public void a04_deleteOneAttachmentKeepsOtherFiles() throws Exception {
        int noteId = insertNote("A04 dois anexos");
        DatabaseHelper.Attachment a = attach(noteId, png(32, 32), 0);
        DatabaseHelper.Attachment b = attach(noteId, png(48, 48), 0);
        File fa = AttachmentManager.getAttachmentFile(ctx, a);
        File fb = AttachmentManager.getAttachmentFile(ctx, b);
        assertTrue(fa.exists());
        assertTrue(fb.exists());

        Intent i = new Intent(ctx, EditActivity.class)
                .putExtra("NOTE_ID", noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<EditActivity> sc = ActivityScenario.launch(i)) {
            sc.onActivity(activity -> {
                try {
                    Field af = EditActivity.class.getDeclaredField("attachmentAdapter");
                    af.setAccessible(true);
                    AttachmentAdapter adapter = (AttachmentAdapter) af.get(activity);
                    Field lf = AttachmentAdapter.class.getDeclaredField("deleteListener");
                    lf.setAccessible(true);
                    AttachmentAdapter.OnDeleteListener listener =
                            (AttachmentAdapter.OnDeleteListener) lf.get(adapter);
                    listener.onDelete(a);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertFalse("removed attachment file must be gone", fa.exists());
        assertTrue("deleting one attachment must not delete the other's file", fb.exists());
        assertEquals(1, db.getAttachments(noteId).size());
    }

    // ---------- A05/A06/A07: cascade deletes ----------

    @Test
    public void a05_deleteNoteForeverRemovesAttachmentDir() throws Exception {
        int noteId = insertNote("A05 excluir");
        attach(noteId, png(24, 24), 0);
        File dir = dirOf(noteId);
        assertTrue(dir.exists());

        NoteViewModel vm = vmOnMain();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> vm.deleteNoteForever(noteId));

        assertFalse("attachment files leaked after permanent note delete", dir.exists());
        assertEquals(0, db.getAttachments(noteId).size());
        assertNull(repo.getNote(noteId));
    }

    @Test
    public void a06_clearTrashRemovesOnlyTrashedAttachmentDirs() throws Exception {
        Assume.assumeTrue("device already has trashed notes; skipping to protect user data",
                countTrashed() == 0);

        int active = insertNote("A06 ativa");
        attach(active, png(24, 24), 0);
        int trashed = insertNote("A06 lixeira");
        attach(trashed, png(24, 24), 0);

        NoteViewModel vm = vmOnMain();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> vm.moveToTrash(repo.getNote(trashed)));
        assertFalse("note must be trashed before clearTrash", repo.getNote(trashed).isTrashed == 0);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(vm::clearTrash);

        assertFalse("trashed note attachments must be deleted", dirOf(trashed).exists());
        assertTrue("active note attachments must survive clearTrash", dirOf(active).exists());
        assertEquals(0, db.getAttachments(trashed).size());
        assertEquals(1, db.getAttachments(active).size());
    }

    @Test
    public void a07_resetAllNotesRemovesAttachmentDirs() throws Exception {
        boolean empty = repo.searchNotes("", false, null).isEmpty()
                && repo.searchNotes("", true, null).isEmpty();
        Assume.assumeTrue("device has notes; skipping destructive reset test", empty);

        int noteId = insertNote("A07 reset");
        attach(noteId, png(24, 24), 0);
        File dir = dirOf(noteId);
        assertTrue(dir.exists());

        NoteViewModel vm = vmOnMain();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(vm::resetAllNotes);

        assertFalse("attachment files leaked after full reset", dir.exists());
        assertTrue(repo.searchNotes("", false, null).isEmpty());
        assertTrue(repo.searchNotes("", true, null).isEmpty());
    }

    private int countTrashed() {
        int n = 0;
        for (DatabaseHelper.Note note : repo.searchNotes("", true, null)) {
            if (note.isTrashed == 1) n++;
        }
        return n;
    }

    // ---------- A08/A09: image viewer + drawing ----------

    @Test
    public void a08_annotationIsSavedAtTheRightImagePosition() throws Exception {
        File img = png(2000, 2000);
        try (ActivityScenario<ImageViewerActivity> sc = ActivityScenario.launch(
                new Intent(ctx, ImageViewerActivity.class)
                        .putExtra("file_path", img.getAbsolutePath())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            sc.onActivity(activity -> {
                ImageView iv = activity.findViewById(R.id.ivFullImage);
                DrawingView dv = activity.findViewById(R.id.drawingView);
                dv.setVisibility(View.VISIBLE);
                dv.setDrawingMode(true);
                dv.setCurrentColor(Color.RED);
                dv.setStrokeWidth(24f);

                float[] center = {1000f, 1000f}; // center in bitmap coordinates
                Matrix m = new Matrix();
                iv.getImageMatrix().mapPoints(center); // -> view coordinates

                long t = SystemClock.uptimeMillis();
                dispatch(dv, t, MotionEvent.ACTION_DOWN, center[0], center[1]);
                dispatch(dv, t, MotionEvent.ACTION_MOVE, center[0] + 10, center[1] + 10);
                dispatch(dv, t, MotionEvent.ACTION_MOVE, center[0] + 20, center[1] + 20);
                dispatch(dv, t, MotionEvent.ACTION_UP, center[0] + 20, center[1] + 20);

                try {
                    Method save = ImageViewerActivity.class.getDeclaredMethod("saveAnnotation");
                    save.setAccessible(true);
                    save.invoke(activity);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Bitmap saved = BitmapFactory.decodeFile(img.getAbsolutePath());
        assertNotNull(saved);
        assertEquals(2000, saved.getWidth());
        assertEquals(2000, saved.getHeight());

        int red = 0;
        for (int x = 930; x <= 1070; x++) {
            for (int y = 930; y <= 1070; y++) {
                int p = saved.getPixel(x, y);
                if (Color.red(p) > 150 && Color.green(p) < 100 && Color.blue(p) < 100) red++;
            }
        }
        saved.recycle();
        assertTrue("annotation was not saved at the image center "
                        + "(drawing coordinates are view-space, not bitmap-space)", red > 0);
    }

    private static void dispatch(View v, long downTime, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(downTime, downTime + 10, action, x, y, 0);
        v.dispatchTouchEvent(e);
        e.recycle();
    }

    @Test
    public void a09_viewerHandlesMissingFileWithoutCrash() {
        File missing = new File(ctx.getCacheDir(), "b13_nope_" + System.nanoTime() + ".png");
        try (ActivityScenario<ImageViewerActivity> sc = ActivityScenario.launch(
                new Intent(ctx, ImageViewerActivity.class)
                        .putExtra("file_path", missing.getAbsolutePath())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            sc.onActivity(activity -> {
                DrawingView dv = activity.findViewById(R.id.drawingView);
                dv.setDrawingMode(true);
                try {
                    Method save = ImageViewerActivity.class.getDeclaredMethod("saveAnnotation");
                    save.setAccessible(true);
                    save.invoke(activity);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertFalse(missing.exists());
    }
}
