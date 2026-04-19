package com.notara;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

/**
 * Núcleo de criptografia AES-GCM usando Android KeyStore.
 * Garante que as chaves sejam persistentes e únicas para o app.
 */
public class SecurityCore {
    private static final String KEY_ALIAS = "com.notara.crypto_v1"; // Alias persistente e único
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // Padrão GCM recomendado

    /**
     * Obtém a chave existente ou gera uma nova se necessário.
     */
    private static synchronized SecretKey getOrGenerateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setUserAuthenticationRequired(false) // Permite persistência após reinicialização sem exigir bio imediata
                        .build());
                return keyGenerator.generateKey();
            } else {
                throw new UnsupportedOperationException("Android M+ required for this security feature");
            }
        }
        
        KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        if (entry == null) {
            // Caso raro onde a entrada existe mas está corrompida
            keyStore.deleteEntry(KEY_ALIAS);
            return getOrGenerateKey();
        }
        return entry.getSecretKey();
    }

    /**
     * Criptografa dados usando AES-GCM.
     */
    public static String encrypt(String data) throws Exception {
        if (data == null) return null;
        
        SecretKey secretKey = getOrGenerateKey();
        Cipher cipher = Cipher.getInstance(AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(data.getBytes("UTF-8"));
        
        // Combina IV + Dados Criptografados para armazenamento único
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /**
     * Decriptografa dados usando AES-GCM.
     */
    public static String decrypt(String encryptedData) throws Exception {
        if (encryptedData == null) return null;
        
        byte[] combined = Base64.decode(encryptedData, Base64.NO_WRAP);
        if (combined.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Dados criptografados inválidos ou corrompidos");
        }

        SecretKey secretKey = getOrGenerateKey();
        Cipher cipher = Cipher.getInstance(AES_MODE);
        
        GCMParameterSpec spec = new GCMParameterSpec(128, combined, 0, IV_LENGTH);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        
        byte[] decrypted = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
        return new String(decrypted, "UTF-8");
    }

    /**
     * Verifica se a chave de criptografia está pronta para uso.
     */
    public static void validateSecurityState() throws Exception {
        getOrGenerateKey();
    }
}
