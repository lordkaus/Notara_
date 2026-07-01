package com.notara;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class AttachmentManager {

    public static DatabaseHelper.Attachment saveAttachmentFromUri(Context context, Uri uri, int noteId, int type) throws Exception {
        String fileName = getFileName(context, uri, type);
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : (type == 0 ? ".jpg" : ".mp3");
        String uuid = UUID.randomUUID().toString();
        File dir = getAttachmentsDir(context, noteId);
        dir.mkdirs();
        File dest = new File(dir, uuid + ext);

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            long size = 0;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
                size += len;
            }
            DatabaseHelper.Attachment a = new DatabaseHelper.Attachment();
            a.noteId = noteId;
            a.type = type;
            a.fileName = fileName;
            a.filePath = noteId + "/" + uuid + ext;
            a.mimeType = context.getContentResolver().getType(uri);
            a.fileSize = size;
            return a;
        }
    }

    public static File getAttachmentFile(Context context, DatabaseHelper.Attachment a) {
        return new File(context.getFilesDir(), "attachments/" + a.filePath);
    }

    public static void deleteNoteAttachmentDir(Context context, int noteId) {
        File dir = getAttachmentsDir(context, noteId);
        if (dir.exists()) deleteRecursive(dir);
    }

    public static Bitmap generateThumbnail(String filePath, int maxSize) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, opts);
        int scale = Math.max(opts.outWidth / maxSize, opts.outHeight / maxSize);
        if (scale < 1) scale = 1;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = scale;
        return BitmapFactory.decodeFile(filePath, opts);
    }

    private static File getAttachmentsDir(Context context, int noteId) {
        return new File(context.getFilesDir(), "attachments/" + noteId);
    }

    private static String getFileName(Context context, Uri uri, int type) {
        String name = "unknown";
        android.database.Cursor c = null;
        try {
            c = context.getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception e) { /* fallback */ } finally {
            if (c != null) c.close();
        }
        if (name == null || name.isEmpty()) name = type == 0 ? "image.jpg" : "audio.mp3";
        return name;
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
