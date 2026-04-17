package com.notara;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.util.concurrent.Executor;

/**
 * Gerenciador de segurança responsável pela validação de identidade do usuário.
 * Integra Biometria do Sistema e Senha Interna (fallback).
 */
public class SecurityManager {

    public interface AuthCallback {
        void onAuthenticated();
        void onError(String error);
    }

    private final Context context;

    public SecurityManager(Context context) {
        this.context = context;
    }

    /**
     * Verifica se a autenticação biométrica ou por credenciais do sistema está disponível.
     */
    public boolean isAuthAvailable() {
        BiometricManager biometricManager = BiometricManager.from(context);
        int canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK | 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Fluxo principal de autenticação. Tenta biometria, se falhar ou não disponível,
     * tenta a senha interna se configurada.
     */
    public void authenticate(@NonNull FragmentActivity activity, String title, String subtitle, @NonNull AuthCallback callback) {
        if (isAuthAvailable()) {
            authenticateWithBiometrics(activity, title, subtitle, callback);
        } else if (isInternalPasswordSet()) {
            showInternalPasswordDialog(activity, callback);
        } else {
            // Caso o dispositivo use 'Deslizar' ou 'Gesto' simples, o Android diz que não pode autenticar.
            // Oferecemos configurar a Senha Interna.
            new AlertDialog.Builder(activity)
                .setTitle("Segurança do Notara")
                .setMessage("Seu dispositivo usa um bloqueio simples (Gestos/Deslizar). Para proteger esta nota, configure uma Senha Interna.")
                .setPositiveButton("Configurar Senha", (d, w) -> showSecurityWarning(activity))
                .setNegativeButton("Cancelar", (d, w) -> callback.onError("Segurança não configurada"))
                .show();
        }
    }

    private void authenticateWithBiometrics(@NonNull FragmentActivity activity, String title, String subtitle, @NonNull AuthCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(context);
        
        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                // Fallback para Senha Interna se o usuário cancelar ou o sistema falhar (comum em dispositivos legados)
                if (isInternalPasswordSet()) {
                    showInternalPasswordDialog(activity, callback);
                } else {
                    callback.onError(errString.toString());
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                callback.onAuthenticated();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | 
                                         BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Exibe o diálogo inicial para configurar a segurança do app.
     */
    public void promptSecuritySetup(@NonNull FragmentActivity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("Segurança do Notara")
            .setMessage("Para proteger suas notas, escolha como deseja bloquear o acesso.")
            .setPositiveButton("Bloqueio do Sistema (Recomendado)", (d, w) -> {
                context.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
                Toast.makeText(context, "Configure um PIN, Padrão ou Biometria no Android", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Senha Interna", (d, w) -> {
                showSecurityWarning(activity);
            })
            .setNeutralButton("Cancelar", null)
            .show();
    }

    /**
     * ALERTA DE SEGURANÇA: Exibido ao optar por Senha Interna.
     * Segue a regra de ouro da skill notara-dev.
     */
    private void showSecurityWarning(@NonNull FragmentActivity activity) {
        new AlertDialog.Builder(activity)
            .setTitle("⚠️ Aviso de Segurança")
            .setMessage("Senhas internas são menos seguras que o bloqueio do sistema (PIN/Biometria). \n\n" +
                     "• Se esquecer esta senha, não há recuperação. \n" +
                     "• As notas trancadas ficarão inacessíveis para sempre.\n\n" +
                     "Deseja continuar com a segurança reduzida?")
            .setPositiveButton("Sim, prosseguir", (d, w) -> showInternalPasswordSetup(activity))
            .setNegativeButton("Voltar", (d, w) -> promptSecuritySetup(activity))
            .show();
    }

    private void showInternalPasswordSetup(@NonNull FragmentActivity activity) {
        EditText et = new EditText(activity);
        et.setHint("Digite a nova senha");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        new AlertDialog.Builder(activity)
            .setTitle("Definir Senha Interna")
            .setView(et)
            .setPositiveButton("Salvar", (d, w) -> {
                String password = et.getText().toString();
                if (password.length() < 4) {
                    Toast.makeText(context, "A senha deve ter pelo menos 4 caracteres", Toast.LENGTH_SHORT).show();
                    showInternalPasswordSetup(activity);
                } else {
                    saveInternalPassword(password);
                    SecurityDataStore.getInstance(context).setSecurityEnabled(true);
                    Toast.makeText(context, "Senha interna configurada com sucesso", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showInternalPasswordDialog(@NonNull FragmentActivity activity, @NonNull AuthCallback callback) {
        EditText et = new EditText(activity);
        et.setHint("Senha do Notara");
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(activity)
            .setTitle("Cofre Trancado")
            .setMessage("Insira sua senha interna para acessar.")
            .setView(et)
            .setPositiveButton("Desbloquear", (d, w) -> {
                if (verifyInternalPassword(et.getText().toString())) {
                    callback.onAuthenticated();
                } else {
                    callback.onError("Senha incorreta.");
                }
            })
            .setNegativeButton("Cancelar", (d, w) -> callback.onError("Cancelado pelo usuário"))
            .show();
    }

    private void saveInternalPassword(String password) {
        try {
            SharedPreferences prefs = getEncryptedPrefs();
            prefs.edit().putString("internal_password", password).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean verifyInternalPassword(String input) {
        try {
            SharedPreferences prefs = getEncryptedPrefs();
            String saved = prefs.getString("internal_password", "");
            return !saved.isEmpty() && saved.equals(input);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isInternalPasswordSet() {
        try {
            SharedPreferences prefs = getEncryptedPrefs();
            return prefs.contains("internal_password");
        } catch (Exception e) {
            return false;
        }
    }

    public void clearAll() {
        try {
            getEncryptedPrefs().edit().clear().apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SharedPreferences getEncryptedPrefs() throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        return EncryptedSharedPreferences.create(
                context,
                "notara_secure_storage",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
