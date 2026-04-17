package com.notara;

import android.content.Context;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Persistência para configurações de segurança usando Jetpack DataStore.
 */
public class SecurityDataStore {
    private static final String DATASTORE_NAME = "security_settings";
    private static final Preferences.Key<Boolean> KEY_SECURITY_ENABLED = PreferencesKeys.booleanKey("security_enabled");

    private final RxDataStore<Preferences> dataStore;
    private static SecurityDataStore instance;

    private SecurityDataStore(Context context) {
        dataStore = new RxPreferenceDataStoreBuilder(context.getApplicationContext(), DATASTORE_NAME).build();
    }

    public static synchronized SecurityDataStore getInstance(Context context) {
        if (instance == null) {
            instance = new SecurityDataStore(context);
        }
        return instance;
    }

    /**
     * Salva o estado da segurança.
     */
    public void setSecurityEnabled(boolean enabled) {
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(KEY_SECURITY_ENABLED, enabled);
            return Single.just(mutablePreferences);
        })
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .subscribe(
                        () -> { },
                        throwable -> android.util.Log.e("SecurityDataStore", "setSecurityEnabled failed", throwable)
                );
    }

    /**
     * Limpa todas as configurações de segurança.
     */
    public void clearAll() {
        dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.clear();
            return Single.just(mutablePreferences);
        });
    }

    /**
     * Retorna um Flowable com o estado da segurança.
     */
    public Flowable<Boolean> isSecurityEnabled() {
        return dataStore.data().map(prefs -> {
            Boolean enabled = prefs.get(KEY_SECURITY_ENABLED);
            return enabled != null ? enabled : false;
        });
    }

    /**
     * Retorna o estado atual de forma síncrona (bloqueante - usar com cautela).
     */
    public boolean isSecurityEnabledSync() {
        try {
            return isSecurityEnabled().firstOrError().blockingGet();
        } catch (Exception e) {
            return false;
        }
    }
}
