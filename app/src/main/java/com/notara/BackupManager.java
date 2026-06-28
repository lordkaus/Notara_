package com.notara;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BackupManager {
    static final String HEADER = "NOTARA_BACKUP v1";

    public static String serialize(List<DatabaseHelper.Note> notes) throws Exception {
        JSONArray arr = new JSONArray();
        for (DatabaseHelper.Note n : notes) {
            JSONObject obj = new JSONObject();
            obj.put("title", n.title != null ? n.title : "");
            obj.put("content", n.content != null ? n.content : "");
            obj.put("type", n.type);
            obj.put("color", n.color);
            obj.put("isPinned", n.isPinned);
            obj.put("isTrashed", n.isTrashed);
            obj.put("tag", n.tag != null ? n.tag : "");
            obj.put("reminderTime", n.reminderTime);
            obj.put("recurrenceType", n.recurrenceType);
            obj.put("recurrenceDays", n.recurrenceDays);
            obj.put("attachments", n.attachments != null ? n.attachments : "");
            obj.put("isLocked", n.isLocked);
            obj.put("alertType", n.alertType);
            obj.put("lastModified", n.lastModified);
            obj.put("originalReminderTime", n.originalReminderTime);
            arr.put(obj);
        }
        return HEADER + "\n" + arr.toString(2);
    }

    public static List<DatabaseHelper.Note> deserialize(String data) throws Exception {
        int newline = data.indexOf('\n');
        if (newline == -1 || !data.substring(0, newline).startsWith("NOTARA_BACKUP"))
            throw new IllegalArgumentException("Arquivo de backup inválido");
        String json = data.substring(newline + 1);
        JSONArray arr = new JSONArray(json);
        List<DatabaseHelper.Note> notes = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            notes.add(new DatabaseHelper.Note(
                -1,
                obj.optString("title", ""),
                obj.optString("content", ""),
                obj.optInt("type", 0),
                obj.optInt("color", 0),
                obj.optInt("isPinned", 0),
                obj.optInt("isTrashed", 0),
                obj.optString("tag", null),
                obj.optLong("reminderTime", 0),
                obj.optInt("recurrenceType", 0),
                obj.optInt("recurrenceDays", 0),
                obj.optString("attachments", null),
                obj.optInt("isLocked", 0),
                obj.optInt("alertType", 0),
                obj.optLong("lastModified", 0),
                obj.optLong("originalReminderTime", 0)
            ));
        }
        return notes;
    }

    public static String encrypt(String plainText, String password) throws Exception {
        return SecurityHelper.encryptForSharing(plainText, password);
    }

    public static String decrypt(String cipherText, String password) throws Exception {
        return SecurityHelper.decryptFromSharing(cipherText, password);
    }

    public static boolean isEncrypted(String data) {
        return !data.startsWith(HEADER);
    }

    public static void write(OutputStream os, String data) throws Exception {
        os.write(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String read(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }
}
