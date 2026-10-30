package com.notara;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * Gerencia lógica de compartilhamento, importação e exportação de notas.
 * Extraído do MainActivity para melhor separação de responsabilidades.
 */
public class NoteShareManager {

    private NoteShareManager() {}

    public static String formatNoteForExport(DatabaseHelper.Note note) {
        String content = note.content;
        if (note.isLocked == 1) {
            try { content = SecurityCore.decrypt(note.content); } catch (Exception e) { return "Erro ao descriptografar."; }
        }

        if (note.type == 1) {
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\n")) {
                if (line.contains("::")) {
                    String[] parts = line.split("::");
                    sb.append(parts.length > 1 && parts[1].equals("1") ? "[x] " : "[ ] ").append(parts[0]).append("\n");
                }
            }
            return note.title + "\n\n" + sb.toString();
        }
        return note.title + "\n\n" + content;
    }

    public static String getSanitizedBaseName(String title) {
        String name = title.replaceAll("(?i)\\.(txt|savage|md|json)$", "");
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    public static Intent createTextExportIntent(DatabaseHelper.Note note) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, getSanitizedBaseName(note.title) + ".txt");
        return intent;
    }

    public static Intent createSecureExportIntent(DatabaseHelper.Note note) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, getSanitizedBaseName(note.title) + ".savage");
        return intent;
    }

    public static void writeTextExport(OutputStream os, DatabaseHelper.Note note) throws Exception {
        String content = formatNoteForExport(note);
        os.write(content.getBytes());
        os.close();
    }

    public static void writeSecureExport(OutputStream os, DatabaseHelper.Note note, String password) throws Exception {
        String content = formatNoteForExport(note);
        String encrypted = SecurityHelper.encryptForSharing(
                "NOTARA_SECURE_NOTE\nTITLE: " + note.title + "\nCONTENT: " + content, password);
        os.write(encrypted.getBytes());
        os.close();
    }

    public static String readUriContent(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) content.append(line).append('\n');
        inputStream.close();
        return content.toString();
    }

    public static DatabaseHelper.Note importTextNote(String content) {
        return new DatabaseHelper.Note(-1, "Nota Importada", content, 0, 1, 0, 0, null,
                0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0);
    }

    public static DatabaseHelper.Note decryptSharedNote(String encodedPackage, String password) throws Exception {
        String decrypted = SecurityHelper.decryptFromSharing(encodedPackage, password);
        if (!decrypted.startsWith("NOTARA_SECURE_NOTE")) return null;

        String title = "Nota Importada";
        String content = "";

        String[] lines = decrypted.split("\n");
        for (String line : lines) {
            if (line.startsWith("TITLE: ")) title = line.substring(7);
            else if (line.startsWith("CONTENT: ")) content = line.substring(9);
        }

        if (content.isEmpty() && lines.length > 2) {
            int contentIdx = decrypted.indexOf("CONTENT: ");
            if (contentIdx != -1) content = decrypted.substring(contentIdx + 9);
        }

        return new DatabaseHelper.Note(-1, title, content, 0, 1, 0, 0, null,
                0, 0, 0, null, 1, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0);
    }

    public static String createSecureShareText(String title, String content, String password) throws Exception {
        String fullText = "NOTARA_SECURE_NOTE\nTITLE: " + title + "\nCONTENT: " + content;
        String encryptedPackage = SecurityHelper.encryptForSharing(fullText, password);
        return "Esta é uma nota segura do Notara.\n"
                + "Para abri-la, instale o Notara e use a senha combinada.\n\n"
                + "PACOTE:\n" + encryptedPackage;
    }
}
