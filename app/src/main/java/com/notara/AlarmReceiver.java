package com.notara;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;
import com.notara.widget.NoteWidgetProvider;
import java.util.Calendar;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            rescheduleAllAlarms(context);
            return;
        }

        int noteId = intent.getIntExtra("id", -1);
        int itemId = intent.getIntExtra("ITEM_ID", -1);
        int itemAlertType = intent.getIntExtra("ITEM_ALERT_TYPE", -1);
        if (noteId == -1) return;

        DatabaseHelper dbHelper = new DatabaseHelper(context);
        NoteRepository repository = new NoteRepositoryImpl(dbHelper);
        DatabaseHelper.Note note = repository.getNote(noteId);
        if (note == null) return;

        if (itemId != -1 && itemAlertType != -1) {
            // Per-item alarm
            if (itemAlertType == 1) {
                sendFullScreenAlarm(context, note, itemId);
            } else {
                sendNotification(context, note, itemId);
            }
            // Check recurrence on the item
            DatabaseHelper.ChecklistItem targetItem = null;
            for (DatabaseHelper.ChecklistItem ci : dbHelper.getChecklistItems(noteId)) {
                if (ci.id == itemId) { targetItem = ci; break; }
            }
            if (targetItem != null && targetItem.recurrenceType > 0) {
                scheduleNextItemAlarm(context, noteId, targetItem);
            }
        } else {
            // Note-level alarm (text notes)
            if (note.alertType == 1) {
                sendFullScreenAlarm(context, note, -1);
            } else {
                sendNotification(context, note, -1);
            }
            if (note.recurrenceType > 0) scheduleNextAlarm(context, note);
        }
    }

    private void sendFullScreenAlarm(Context context, DatabaseHelper.Note note, int itemId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = NotesApp.ALARM_CHANNEL_ID;

        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.putExtra("id", note.id);
        if (itemId != -1) alarmIntent.putExtra("ITEM_ID", itemId);
        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        int requestCode = itemId != -1 ? note.id + itemId + 1000 : note.id;
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, requestCode, 
                alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = itemId != -1 ? getItemTitle(context, note.id, itemId) : note.title;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title != null ? title : note.title)
                .setContentText(itemId != -1 ? "Alarme de item!" : "Alarme de Nota!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true);

        manager.notify(requestCode + 1000, builder.build());
    }

    private void sendNotification(Context context, DatabaseHelper.Note note, int itemId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = NotesApp.REMINDER_CHANNEL_ID;

        Class<?> activityClass = (note.type == 1) ? ChecklistActivity.class : EditActivity.class;
        Intent activityIntent = new Intent(context, activityClass);
        activityIntent.putExtra("NOTE_ID", note.id);
        if (itemId != -1) activityIntent.putExtra("ITEM_ID", itemId);
        
        int requestCode = itemId != -1 ? note.id + itemId + 2000 : note.id;
        PendingIntent pi = PendingIntent.getActivity(context, requestCode, activityIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = itemId != -1 ? getItemTitle(context, note.id, itemId) : note.title;
        String content = note.content != null && note.content.length() > 50 ? note.content.substring(0, 47) + "…" : note.content;
        if (title != null) content = title;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(note.title)
                .setContentText(content)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);

        manager.notify(requestCode, builder.build());
    }

    private String getItemTitle(Context context, int noteId, int itemId) {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            java.util.List<DatabaseHelper.ChecklistItem> items = dbHelper.getChecklistItems(noteId);
            for (DatabaseHelper.ChecklistItem item : items) {
                if (item.id == itemId) return item.name;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void rescheduleAllAlarms(Context context) {
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        NoteRepository repository = new NoteRepositoryImpl(dbHelper);
        java.util.List<DatabaseHelper.Note> notes = repository.searchNotes("", false, null);
        for (DatabaseHelper.Note note : notes) {
            if (note.type == 1) {
                // Checklist - reschedule per-item alarms
                java.util.List<DatabaseHelper.ChecklistItem> items = dbHelper.getChecklistItems(note.id);
                for (DatabaseHelper.ChecklistItem item : items) {
                    if (item.reminderTime > System.currentTimeMillis()) {
                        rescheduleItemAlarm(context, note.id, item);
                    }
                }
            } else if (note.reminderTime > System.currentTimeMillis()) {
                rescheduleAlarm(context, note);
            }
        }
    }

    public static void rescheduleAlarm(Context context, DatabaseHelper.Note note) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("id", note.id);
        
        PendingIntent pi = PendingIntent.getBroadcast(context, note.id, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (note.reminderTime <= System.currentTimeMillis()) {
            am.cancel(pi);
            return;
        }

        scheduleExact(am, note.reminderTime, pi);
    }

    public static void cancelAlarm(Context context, int noteId) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, noteId, intent, 
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    public static void scheduleNextAlarm(Context context, DatabaseHelper.Note note) {
        if (note.reminderTime <= 0) return;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(note.reminderTime);
        switch (note.recurrenceType) {
            case 1: cal.add(Calendar.DAY_OF_YEAR, 1); break;
            case 2: cal.add(Calendar.WEEK_OF_YEAR, 1); break;
            case 3: cal.add(Calendar.MONTH, 1); break;
            case 4: cal.add(Calendar.YEAR, 1); break;
            case 5:
                for (int i = 1; i <= 7; i++) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    int domToSab = cal.get(Calendar.DAY_OF_WEEK) - 1;
                    if ((note.recurrenceDays & (1 << domToSab)) != 0) break;
                }
                break;
        }
        long nextReminderTime = cal.getTimeInMillis();
        note.reminderTime = nextReminderTime;
        
        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        repository.updateNote(note);
        NoteWidgetProvider.updateAllWidgets(context);
        
        rescheduleAlarm(context, note);
    }

    // Per-item alarm helpers

    public static void rescheduleItemAlarm(Context context, int noteId, DatabaseHelper.ChecklistItem item) {
        if (item.reminderTime <= System.currentTimeMillis()) {
            cancelItemAlarm(context, noteId, item.id);
            return;
        }
        if (item.alertType == 2) {
            scheduleItemSingleAlarm(context, noteId, item.id, item.reminderTime, 0);
            scheduleItemSingleAlarm(context, noteId, item.id, item.reminderTime, 1);
        } else {
            scheduleItemSingleAlarm(context, noteId, item.id, item.reminderTime, item.alertType);
        }
    }

    public static void cancelItemAlarm(Context context, int noteId, int itemId) {
        cancelItemSingleAlarm(context, noteId, itemId, 0);
        cancelItemSingleAlarm(context, noteId, itemId, 1);
    }

    private static void scheduleItemSingleAlarm(Context context, int noteId, int itemId, long time, int alertType) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("id", noteId);
        intent.putExtra("ITEM_ID", itemId);
        intent.putExtra("ITEM_ALERT_TYPE", alertType);
        int requestCode = noteId * 1000 + itemId * 2 + alertType;
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        scheduleExact(am, time, pi);
    }

    private static void cancelItemSingleAlarm(Context context, int noteId, int itemId, int alertType) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int requestCode = noteId * 1000 + itemId * 2 + alertType;
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }

    private static void scheduleExact(AlarmManager am, long time, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        }
    }

    public static void scheduleNextItemAlarm(Context context, int noteId, DatabaseHelper.ChecklistItem item) {
        if (item.reminderTime <= 0) return;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(item.reminderTime);
        switch (item.recurrenceType) {
            case 1: cal.add(Calendar.DAY_OF_YEAR, 1); break;
            case 2: cal.add(Calendar.WEEK_OF_YEAR, 1); break;
            case 3: cal.add(Calendar.MONTH, 1); break;
            case 4: cal.add(Calendar.YEAR, 1); break;
            case 5:
                for (int i = 1; i <= 7; i++) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    int domToSab = cal.get(Calendar.DAY_OF_WEEK) - 1;
                    if ((item.recurrenceDays & (1 << domToSab)) != 0) break;
                }
                break;
        }
        item.reminderTime = cal.getTimeInMillis();
        
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        dbHelper.updateChecklistItem(item);
        
        rescheduleItemAlarm(context, noteId, item);
    }
}
