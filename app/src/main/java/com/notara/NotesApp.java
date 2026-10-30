/*
 * Copyright (c) 1996 lordkaus
 * This file is part of Notara_.
 *
 * Notara_ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Notara_ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Notara_. If not, see <https://www.gnu.org/licenses/>.
 */
package com.notara;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import com.google.android.material.color.DynamicColors;

public class NotesApp extends Application {
    public static final String ALARM_CHANNEL_ID = "alarm_channel_v2";
    public static final String REMINDER_CHANNEL_ID = "notes_reminders_v2";
    public static final String OLD_ALARM_CHANNEL_ID = "alarm_channel";
    public static final String OLD_REMINDER_CHANNEL_ID = "notes_reminders";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Notara_", "Sempre pensei que eu era uma pessoa sem razão para viver, mas suponho que estava errado. Mesmo alguém como eu pode ter um propósito na vida.-LK");
        Log.d("Notara_", "I always thought I was a person with no reason to live, but I suppose I was wrong. Even someone like me can have a purpose in life. -LK");

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

            // Canal de Alarmes (Importância Máxima + Som + FullScreen)
            NotificationChannel alarmChannel = new NotificationChannel(
                    ALARM_CHANNEL_ID,
                    "Alarmes de Notas",
                    NotificationManager.IMPORTANCE_MAX
            );
            alarmChannel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            alarmChannel.setBypassDnd(true);
            alarmChannel.setDescription("Usado para alarmes em tela cheia");
            Uri alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) alarmSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
            alarmChannel.setSound(alarmSound, new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            alarmChannel.enableVibration(true);
            alarmChannel.setVibrationPattern(new long[]{0, 500, 300, 500});

            // Canal de Lembretes (Notificação com Som)
            NotificationChannel reminderChannel = new NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    "Lembretes",
                    NotificationManager.IMPORTANCE_HIGH
            );
            reminderChannel.setDescription("Usado para lembretes de notas");
            reminderChannel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
            reminderChannel.enableVibration(true);

            manager.deleteNotificationChannel(OLD_ALARM_CHANNEL_ID);
            manager.deleteNotificationChannel(OLD_REMINDER_CHANNEL_ID);

            // Reseta os canais 1x por versão (guarda em prefs) para reverter silenciamentos/overrides antigos
            int currentVersion = 0;
            try {
                currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            } catch (Exception e) {
                currentVersion = 1;
            }
            SettingsManager sm = new SettingsManager(this);
            int lastResetVersion = sm.getLastChannelResetVersion();
            if (lastResetVersion != currentVersion) {
                manager.deleteNotificationChannel(ALARM_CHANNEL_ID);
                manager.deleteNotificationChannel(REMINDER_CHANNEL_ID);
                sm.setLastChannelResetVersion(currentVersion);
            }

            manager.createNotificationChannel(alarmChannel);
            manager.createNotificationChannel(reminderChannel);
        }
    }
}
