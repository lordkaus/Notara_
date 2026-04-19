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
import java.util.Calendar;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            // Reagendar todos os alarmes futuros quando a permissão for concedida
            rescheduleAllAlarms(context);
            return;
        }

        int noteId = intent.getIntExtra("id", -1);
        if (noteId == -1) return;

        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        DatabaseHelper.Note note = repository.getNote(noteId);
        if (note == null) return;

        if (note.alertType == 1) {
            // ALARME: Usa FullScreenIntent (Recomendado para Android 13-15)
            sendFullScreenAlarm(context, note);
        } else {
            // LEMBRETE: Notificação simples
            sendNotification(context, note);
        }

        if (note.recurrenceType > 0) scheduleNextAlarm(context, note);
    }

    private void sendFullScreenAlarm(Context context, DatabaseHelper.Note note) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = NotesApp.ALARM_CHANNEL_ID;

        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.putExtra("id", note.id);
        alarmIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, note.id, 
                alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(note.title)
                .setContentText("Alarme de Nota!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true); // Evita que seja descartado facilmente

        manager.notify(note.id + 1000, builder.build());
    }

    private void sendNotification(Context context, DatabaseHelper.Note note) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = NotesApp.REMINDER_CHANNEL_ID;

        Class<?> activityClass = (note.type == 1) ? ChecklistActivity.class : EditActivity.class;
        Intent activityIntent = new Intent(context, activityClass);
        activityIntent.putExtra("NOTE_ID", note.id);
        PendingIntent pi = PendingIntent.getActivity(context, note.id, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(note.title)
                .setContentText(note.content != null && note.content.length() > 50 ? note.content.substring(0, 47) + "..." : note.content)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);

        manager.notify(note.id, builder.build());
    }

    public static void rescheduleAllAlarms(Context context) {
        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        java.util.List<DatabaseHelper.Note> notes = repository.searchNotes("", false, null);
        for (DatabaseHelper.Note note : notes) {
            if (note.reminderTime > System.currentTimeMillis()) {
                rescheduleAlarm(context, note);
            }
        }
    }

    public static void rescheduleAlarm(Context context, DatabaseHelper.Note note) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("id", note.id);
        
        // FLAG_IMMUTABLE é obrigatória no Android 12+, mas usamos FLAG_UPDATE_CURRENT para garantir que os extras sejam atualizados
        PendingIntent pi = PendingIntent.getBroadcast(context, note.id, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (note.reminderTime <= System.currentTimeMillis()) {
            am.cancel(pi);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.reminderTime, pi);
            } else {
                // Fallback para alarme inexato mas que acorda o dispositivo
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.reminderTime, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, note.reminderTime, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, note.reminderTime, pi);
        }
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
            case 5: // Personalizado (Dias da Semana)
                // Busca o próximo dia marcado no bitmask (máximo 7 dias à frente)
                for (int i = 1; i <= 7; i++) {
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    int domToSab = cal.get(Calendar.DAY_OF_WEEK) - 1; // Dom:1-1=0, Seg:2-1=1...
                    if ((note.recurrenceDays & (1 << domToSab)) != 0) break;
                }
                break;
        }
        long nextReminderTime = cal.getTimeInMillis();
        note.reminderTime = nextReminderTime;
        
        // Persiste a mudança no banco de dados para que o alarme não se perca após reinicialização
        NoteRepository repository = new NoteRepositoryImpl(new DatabaseHelper(context));
        repository.updateNote(note);
        
        rescheduleAlarm(context, note);
    }
}
