package com.notara;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.provider.Settings;
import com.google.android.material.color.DynamicColors;

public class NotesApp extends Application {
    public static final String ALARM_CHANNEL_ID = "alarm_channel";
    public static final String REMINDER_CHANNEL_ID = "notes_reminders";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            SecurityCore.validateSecurityState();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SettingsManager sm = new SettingsManager(this);
        int t = sm.getTheme();
        
        // Aplica cores dinâmicas para os modos Dynamic
        if (t == 2 || t == 3) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
        
        // Define o modo noturno global
        if (t == 1 || t == 2) { // Pantera ou Dynamic Dark
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }
        
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            
            // Canal de Alarmes (Alta Importância + FullScreen)
            NotificationChannel alarmChannel = new NotificationChannel(
                    ALARM_CHANNEL_ID, 
                    "Alarmes de Notas", 
                    NotificationManager.IMPORTANCE_HIGH
            );
            alarmChannel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            alarmChannel.setBypassDnd(true);
            alarmChannel.setDescription("Usado para alarmes em tela cheia");
            alarmChannel.setSound(null, null); // Som gerenciado pela Activity
            alarmChannel.enableVibration(true);
            
            // Canal de Lembretes (Notificação Simples)
            NotificationChannel reminderChannel = new NotificationChannel(
                    REMINDER_CHANNEL_ID, 
                    "Lembretes", 
                    NotificationManager.IMPORTANCE_HIGH
            );
            reminderChannel.setDescription("Usado para lembretes de notas");
            reminderChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
            reminderChannel.enableVibration(true);

            manager.createNotificationChannel(alarmChannel);
            manager.createNotificationChannel(reminderChannel);
        }
    }
}
