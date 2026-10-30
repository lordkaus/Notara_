package com.notara;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NoteShareManagerTest {

    @Test
    public void testGetSanitizedBaseName_simple() {
        assertEquals("Minha_Nota", NoteShareManager.getSanitizedBaseName("Minha Nota"));
    }

    @Test
    public void testGetSanitizedBaseName_removesTxtExtension() {
        assertEquals("Minha_Nota", NoteShareManager.getSanitizedBaseName("Minha Nota.txt"));
    }

    @Test
    public void testGetSanitizedBaseName_removesSavageExtension() {
        assertEquals("Minha_Nota", NoteShareManager.getSanitizedBaseName("Minha Nota.savage"));
    }

    @Test
    public void testGetSanitizedBaseName_specialCharacters() {
        assertEquals("nota_com_acentos___", NoteShareManager.getSanitizedBaseName("nota com acentos çã"));
    }

    @Test
    public void testFormatNoteForExport_textNote() {
        DatabaseHelper.Note note = new DatabaseHelper.Note(1, "Título", "Conteúdo",
                0, 0, 0, 0, null, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0);
        String result = NoteShareManager.formatNoteForExport(note);
        assertEquals("Título\n\nConteúdo", result);
    }

    @Test
    public void testFormatNoteForExport_checklistNote() {
        DatabaseHelper.Note note = new DatabaseHelper.Note(1, "Lista", "Item 1::1\nItem 2::0",
                1, 0, 0, 0, null, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0);
        String result = NoteShareManager.formatNoteForExport(note);
        assertTrue(result.contains("[x] Item 1"));
        assertTrue(result.contains("[ ] Item 2"));
        assertTrue(result.startsWith("Lista"));
    }

    @Test
    public void testImportTextNote() {
        DatabaseHelper.Note note = NoteShareManager.importTextNote("Conteúdo importado");
        assertEquals("Nota Importada", note.title);
        assertEquals("Conteúdo importado", note.content);
        assertEquals(0, note.type);
        assertEquals(0, note.isLocked);
    }

    @Test
    public void testCreateTextExportIntent() {
        DatabaseHelper.Note note = new DatabaseHelper.Note(1, "Teste", "Conteúdo",
                0, 0, 0, 0, null, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0);
        Intent intent = NoteShareManager.createTextExportIntent(note);
        assertNotNull(intent);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.getAction());
        assertEquals("text/plain", intent.getType());
    }

    @Test
    public void testCreateSecureExportIntent() {
        DatabaseHelper.Note note = new DatabaseHelper.Note(1, "Secreto", "Segredo",
                0, 0, 0, 0, null, 0, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0);
        Intent intent = NoteShareManager.createSecureExportIntent(note);
        assertNotNull(intent);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.getAction());
        assertEquals("application/octet-stream", intent.getType());
    }

    @Test
    public void testCreateSecureShareText() throws Exception {
        String result = NoteShareManager.createSecureShareText("Title", "Content", "pwd");
        assertTrue(result.startsWith("Esta é uma nota segura do Notara."));
        assertTrue(result.contains("PACOTE:"));
        // The encrypted package should be different from the plaintext
        assertFalse(result.contains("Content"));
    }

    @Test
    public void testDecryptSharedNote_roundTrip() throws Exception {
        String title = "Nota Teste";
        String content = "Conteúdo secreto";
        String password = "senha123";

        String shareText = NoteShareManager.createSecureShareText(title, content, password);
        // Extract the PACOTE portion
        String[] parts = shareText.split("PACOTE:\n");
        String package64 = parts[1].trim();

        DatabaseHelper.Note decrypted = NoteShareManager.decryptSharedNote(package64, password);
        assertNotNull(decrypted);
        assertEquals(title, decrypted.title);
        assertEquals(content, decrypted.content);
        assertEquals(1, decrypted.isLocked);
    }
}
