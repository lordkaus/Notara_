package com.notara;

import android.util.Base64;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utilitário de segurança para compartilhamento de notas usando AES-GCM (AEAD).
 * Utiliza PBKDF2 para derivação de chave baseada em senha fornecida pelo usuário.
 */
public class SecurityHelper {
    private static final String AES_MODE = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA1";
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 10000;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * Criptografa o texto usando uma senha.
     * Resultado: Base64([SALT] + [IV] + [CIPHERTEXT])
     */
    public static String encryptForSharing(String plainText, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        
        // Gera Salt e IV aleatórios
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        // Deriva a chave da senha
        SecretKey key = deriveKey(password, salt);

        // Configura Cifra
        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

        // Empacota: [SALT] + [IV] + [CIPHERTEXT]
        byte[] packageData = new byte[SALT_LENGTH + IV_LENGTH + cipherText.length];
        System.arraycopy(salt, 0, packageData, 0, SALT_LENGTH);
        System.arraycopy(iv, 0, packageData, SALT_LENGTH, IV_LENGTH);
        System.arraycopy(cipherText, 0, packageData, SALT_LENGTH + IV_LENGTH, cipherText.length);

        return Base64.encodeToString(packageData, Base64.NO_WRAP);
    }

    /**
     * Decriptografa o pacote usando a senha.
     */
    public static String decryptFromSharing(String encodedPackage, String password) throws Exception {
        byte[] packageData = Base64.decode(encodedPackage, Base64.NO_WRAP);

        if (packageData.length < SALT_LENGTH + IV_LENGTH + (GCM_TAG_LENGTH / 8)) {
            throw new IllegalArgumentException("Dados de compartilhamento corrompidos ou inválidos");
        }

        // Extrai Salt e IV
        byte[] salt = new byte[SALT_LENGTH];
        System.arraycopy(packageData, 0, salt, 0, SALT_LENGTH);
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(packageData, SALT_LENGTH, iv, 0, IV_LENGTH);

        // Deriva a chave
        SecretKey key = deriveKey(password, salt);

        // Configura Cifra
        Cipher cipher = Cipher.getInstance(AES_MODE);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        // Decriptografa o restante
        int cipherTextOffset = SALT_LENGTH + IV_LENGTH;
        int cipherTextLength = packageData.length - cipherTextOffset;
        byte[] plainText = cipher.doFinal(packageData, cipherTextOffset, cipherTextLength);

        return new String(plainText, "UTF-8");
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}
