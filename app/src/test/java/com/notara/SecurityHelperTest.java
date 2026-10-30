package com.notara;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SecurityHelperTest {

    @Test
    public void testEncryptDecryptRoundTrip() throws Exception {
        String plainText = "Esta é uma nota secreta do Notara!";
        String password = "minha_senha_segura_123";

        String encrypted = SecurityHelper.encryptForSharing(plainText, password);
        assertNotNull("Encrypted text should not be null", encrypted);
        assertNotEquals("Encrypted text should differ from plain text", plainText, encrypted);

        String decrypted = SecurityHelper.decryptFromSharing(encrypted, password);
        assertEquals("Decrypted text should match original", plainText, decrypted);
    }

    @Test
    public void testEncryptDifferentPasswordFails() throws Exception {
        String plainText = "Nota sensível";
        String password1 = "senha_correta";
        String password2 = "senha_errada";

        String encrypted = SecurityHelper.encryptForSharing(plainText, password1);

        try {
            SecurityHelper.decryptFromSharing(encrypted, password2);
            fail("Should throw exception for wrong password");
        } catch (Exception e) {
            // Expected — wrong password should fail decryption
        }
    }

    @Test
    public void testEncryptEmptyString() throws Exception {
        String plainText = "";
        String password = "senha123";

        String encrypted = SecurityHelper.encryptForSharing(plainText, password);
        String decrypted = SecurityHelper.decryptFromSharing(encrypted, password);
        assertEquals("Empty string round-trip", plainText, decrypted);
    }

    @Test
    public void testEncryptUnicodeContent() throws Exception {
        String plainText = "Acentos: áéíóú çãõ ñ ü — emojis: 🎉🔒📝";
        String password = "senha_unicode";

        String encrypted = SecurityHelper.encryptForSharing(plainText, password);
        String decrypted = SecurityHelper.decryptFromSharing(encrypted, password);
        assertEquals("Unicode round-trip", plainText, decrypted);
    }

    @Test
    public void testEncryptLongContent() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("Linha ").append(i).append(" de conteúdo repetido para testar limite.\n");
        }
        String plainText = sb.toString();
        String password = "senha_longa";

        String encrypted = SecurityHelper.encryptForSharing(plainText, password);
        String decrypted = SecurityHelper.decryptFromSharing(encrypted, password);
        assertEquals("Long content round-trip", plainText, decrypted);
    }

    @Test
    public void testInvalidCipherTextThrows() {
        try {
            SecurityHelper.decryptFromSharing("dGVzdA==", "senha");
            fail("Should throw for invalid cipher text");
        } catch (Exception e) {
            // Expected
        }
    }

    @Test
    public void testSamePasswordDifferentEncryptions() throws Exception {
        String plainText = "Mesmo texto criptografado duas vezes";
        String password = "senha";

        String enc1 = SecurityHelper.encryptForSharing(plainText, password);
        String enc2 = SecurityHelper.encryptForSharing(plainText, password);

        // Different salt/IV should produce different ciphertext
        assertNotEquals("Two encryptions should differ (random salt+IV)", enc1, enc2);

        // Both should decrypt to the same plaintext
        String dec1 = SecurityHelper.decryptFromSharing(enc1, password);
        String dec2 = SecurityHelper.decryptFromSharing(enc2, password);
        assertEquals("Both decrypt to same text", dec1, dec2);
    }

    // SecurityCore tests require AndroidKeyStore (real device only)
    // Tested via integration tests on-device via ADB
}
