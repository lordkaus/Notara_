package com.notara;

import android.content.Context;
import androidx.annotation.OptIn;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlinx.coroutines.ExperimentalCoroutinesApi;

/**
 * Persistência para configurações de segurança usando Jetpack DataStore.
 */
public class SecurityDataStore {
    private static final String DATASTORE_NAME = "security_settings";
    private static final Preferences.Key<Boolean> KEY_SECURITY_ENABLED = PreferencesKeys.booleanKey("security_enabled");

    private final RxDataStore<Preferences> dataStore;
    private static SecurityDataStore instance;

    // 1. A "Sacola" para guardar as operações e evitar o erro de CheckResult
    private final CompositeDisposable disposables = new CompositeDisposable();

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
    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    public void setSecurityEnabled(boolean enabled) {
        // Armazenamos o resultado do subscribe no CompositeDisposable
        disposables.add(
            dataStore.updateDataAsync(prefsIn -> {
                MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
                mutablePreferences.set(KEY_SECURITY_ENABLED, enabled);
                return Single.just(mutablePreferences);
            })
            .subscribeOn(Schedulers.io())
            .subscribe(
                prefs -> { /* Sucesso silencioso */ },
                throwable -> android.util.Log.e("SecurityDataStore", "setSecurityEnabled failed", throwable)
            )
        );
    }

    /**
     * Limpa todas as configurações de segurança.
     */
    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
    public void clearAll() {
        // CORREÇÃO: Adicionado o subscribe (sem ele, a operação nunca era disparada)
        disposables.add(
            dataStore.updateDataAsync(prefsIn -> {
                MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
                mutablePreferences.clear();
                return Single.just(mutablePreferences);
            })
            .subscribeOn(Schedulers.io())
            .subscribe(
                prefs -> { /* Sucesso */ },
                throwable -> android.util.Log.e("SecurityDataStore", "clearAll failed", throwable)
            )
        );
    }

    /**
     * Retorna um Flowable com o estado da segurança.
     */
    @OptIn(markerClass = ExperimentalCoroutinesApi.class)
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
