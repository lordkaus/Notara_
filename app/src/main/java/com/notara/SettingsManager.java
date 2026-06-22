package com.notara;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "notara_settings";
    private static final String KEY_GRID_COLUMNS = "grid_columns";
    private static final String KEY_UNIFORM_GRID = "uniform_grid";
    private static final String KEY_THEME = "app_theme";
    private static final String KEY_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String KEY_APP_PASSWORD = "app_password";
    private static final String KEY_NOTIFICATION_COLOR_SYNC = "notification_color_sync";
    private static final String KEY_ALARM_COLOR_SYNC = "alarm_color_sync";
    private static final String KEY_CLOCK_FORMAT = "clock_format_24h";
    private static final String KEY_TRANSPARENCY = "note_transparency";

    private final SharedPreferences prefs;
    private final java.util.List<Runnable> observers = new java.util.ArrayList<>();

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void addObserver(Runnable observer) { observers.add(observer); }
    public void removeObserver(Runnable observer) { observers.remove(observer); }
    private void notifyObservers() { for (Runnable r : observers) r.run(); }

    public int getTransparency() { return prefs.getInt(KEY_TRANSPARENCY, 100); }
    public void setTransparency(int val) { prefs.edit().putInt(KEY_TRANSPARENCY, val).apply(); notifyObservers(); }

    public boolean is24HourFormat() { return prefs.getBoolean(KEY_CLOCK_FORMAT, true); }
    public void set24HourFormat(boolean is24h) { prefs.edit().putBoolean(KEY_CLOCK_FORMAT, is24h).apply(); notifyObservers(); }

    public int getGridColumns() { return prefs.getInt(KEY_GRID_COLUMNS, 2); }
    public void setGridColumns(int count) { prefs.edit().putInt(KEY_GRID_COLUMNS, count).apply(); notifyObservers(); }

    public boolean isUniformGridEnabled() { return prefs.getBoolean(KEY_UNIFORM_GRID, true); }
    public void setUniformGridEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_UNIFORM_GRID, enabled).apply(); notifyObservers(); }

    public int getTheme() {
        int stored = prefs.getInt(KEY_THEME, 0);
        // Remap old values: Light(0)->Branco(0), Pantera(1)->Preto(1), DynamicBlack(2)->Preto(1), DynamicLight(3)->Branco(0)
        if (stored >= 2) return stored == 3 ? 0 : 1;
        return stored;
    }
    public void setTheme(int theme) { prefs.edit().putInt(KEY_THEME, theme).apply(); notifyObservers(); }

    public boolean isBiometricEnabled() { return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false); }
    public void setBiometricEnabled(boolean enabled) { prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply(); notifyObservers(); }

    public String getAppPassword() { return prefs.getString(KEY_APP_PASSWORD, ""); }
    public void setAppPassword(String password) { prefs.edit().putString(KEY_APP_PASSWORD, password).apply(); notifyObservers(); }

    public boolean isNotificationColorSync() { return prefs.getBoolean(KEY_NOTIFICATION_COLOR_SYNC, true); }
    public void setNotificationColorSync(boolean enabled) { prefs.edit().putBoolean(KEY_NOTIFICATION_COLOR_SYNC, enabled).apply(); notifyObservers(); }

    public boolean isAlarmColorSync() { return prefs.getBoolean(KEY_ALARM_COLOR_SYNC, true); }
    public void setAlarmColorSync(boolean enabled) { prefs.edit().putBoolean(KEY_ALARM_COLOR_SYNC, enabled).apply(); notifyObservers(); }

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
