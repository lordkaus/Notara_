package com.notara;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.search.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.junit.Assert.*;

/**
 * B16 — Search and filters (on-device, no Espresso).
 *
 * Covers: query on title/content, Unicode case folding (Portuguese accents),
 * literal %/_ handling, type/color filters, combined filters, trash mode,
 * empty results, locked-note content, and the MainActivity search UI wiring.
 */
@RunWith(AndroidJUnit4.class)
public class SearchFilterFunctionalTest {

    private Context ctx;
    private NoteRepository repo;
    private DatabaseHelper db;
    private NoteViewModel vm;
    private final List<Integer> createdNotes = new ArrayList<>();

    @Before
    public void setup() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = new NoteRepositoryImpl(ctx);
        db = new DatabaseHelper(ctx);
        final NoteViewModel[] holder = new NoteViewModel[1];
        onMain(() -> holder[0] = new NoteViewModel((android.app.Application) ctx.getApplicationContext()));
        vm = holder[0];
    }

    @After
    public void teardown() {
        for (int id : createdNotes) {
            try {
                AlarmReceiver.cancelAlarm(ctx, id);
                AlarmReceiver.cancelNoteAlarm(ctx, id);
                for (DatabaseHelper.ChecklistItem it : db.getChecklistItems(id)) {
                    AlarmReceiver.cancelItemAlarm(ctx, id, it.id);
                }
                repo.deleteNoteForever(id);
            } catch (Exception ignored) {}
        }
        createdNotes.clear();
    }

    // ---------- helpers ----------

    private static void onMain(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    private int note(String title, String content, int type, int color) {
        int id = (int) repo.addNote(new DatabaseHelper.Note(-1, title, content, type, color,
                0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0));
        createdNotes.add(id);
        return id;
    }

    private void trash(int id) {
        DatabaseHelper.Note n = repo.getNote(id);
        n.isTrashed = 1;
        repo.updateNote(n);
    }

    private List<DatabaseHelper.Note> query(String q, Set<Integer> types, Set<Integer> colors) {
        final List<DatabaseHelper.Note>[] out = new List[1];
        onMain(() -> {
            vm.setQuery(q, true);
            vm.setTypeFilter(types);
            vm.setColorFilter(colors);
            out[0] = vm.getNotes().getValue();
        });
        assertNotNull("ViewModel returned null notes", out[0]);
        return out[0];
    }

    private static List<String> titles(List<DatabaseHelper.Note> notes) {
        List<String> out = new ArrayList<>();
        for (DatabaseHelper.Note n : notes) out.add(n.title);
        return out;
    }

    private static Set<Integer> set(Integer... v) {
        return new HashSet<>(Arrays.asList(v));
    }

    private void waitUntil(String what, long timeoutMs, Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.call()) return;
            Thread.sleep(100);
        }
        fail("timeout waiting for " + what);
    }

    // ---------- B16-01/02/03: text matching ----------

    @Test
    public void b16_01_matchesTitleAndContent() {
        int a = note("Relatório mensal", "nada aqui", 0, 0);
        int b = note("Outra nota", "contém o ALVO escondido", 0, 1);

        List<String> byTitle = titles(query("Relatório", null, null));
        assertTrue(byTitle.contains("Relatório mensal"));
        assertFalse(byTitle.contains("Outra nota"));

        List<String> byContent = titles(query("alvo", null, null));
        assertTrue("ASCII search must be case-insensitive", byContent.contains("Outra nota"));
        assertFalse(byContent.contains("Relatório mensal"));
        assertTrue(createdNotes.contains(a) && createdNotes.contains(b));
    }

    @Test
    public void b16_02_matchesAccentedCaseInsensitively() {
        note("AÇÃO Urgente", "corpo", 0, 0);
        note("Café da manhã", "pão", 0, 1);
        note("Sem acento", "nada", 0, 2);

        assertTrue("accented query must match regardless of case",
                titles(query("ação", null, null)).contains("AÇÃO Urgente"));
        assertTrue(titles(query("CAFÉ", null, null)).contains("Café da manhã"));
        assertTrue("mixed-case accented content", titles(query("PÃO", null, null)).contains("Café da manhã"));
    }

    @Test
    public void b16_03_wildcardsAreLiteralCharacters() {
        note("Desconto de 100%", "válido", 0, 0);
        note("Sem percentual", "nada", 0, 1);
        note("Nome com a_b", "underscore", 0, 2);
        note("Nome com axb", "outro", 0, 3);

        List<String> percent = titles(query("%", null, null));
        assertEquals("a literal % must match only notes containing %", 1, percent.size());
        assertEquals("Desconto de 100%", percent.get(0));

        List<String> underscore = titles(query("a_b", null, null));
        assertEquals("_ must not act as a single-char wildcard", 1, underscore.size());
        assertEquals("Nome com a_b", underscore.get(0));
    }

    // ---------- B16-04/05/06: filters ----------

    @Test
    public void b16_04_typeFilter() {
        note("Texto", "x", 0, 0);
        note("Lista", "Item::0", 1, 0);

        List<String> textOnly = titles(query("", set(0), null));
        assertEquals(1, textOnly.size());
        assertEquals("Texto", textOnly.get(0));

        List<String> listOnly = titles(query("", set(1), null));
        assertEquals(1, listOnly.size());
        assertEquals("Lista", listOnly.get(0));

        assertEquals(2, query("", set(0, 1), null).size());
        assertTrue("null type filter means all", query("", null, null).size() >= 2);
    }

    @Test
    public void b16_05_colorFilterSingleAndMultiple() {
        note("Cor dois", "x", 0, 2);
        note("Cor quatro", "x", 0, 4);
        note("Cor seis", "x", 0, 6);

        assertEquals(1, query("", null, set(2)).size());
        assertEquals("Cor dois", titles(query("", null, set(2))).get(0));

        Set<String> multi = new HashSet<>(titles(query("", null, set(2, 6))));
        assertEquals(2, multi.size());
        assertTrue(multi.contains("Cor dois"));
        assertTrue(multi.contains("Cor seis"));
        assertFalse(multi.contains("Cor quatro"));
    }

    @Test
    public void b16_06_combinedQueryTypeAndColor() {
        note("Alvo texto", "corpo", 0, 3);
        note("Alvo lista", "Alvo::0", 1, 3);
        note("Alvo outra cor", "corpo", 0, 5);
        note("Fora", "corpo", 0, 3);

        List<String> found = titles(query("Alvo", set(0), set(3)));
        assertEquals(1, found.size());
        assertEquals("Alvo texto", found.get(0));
    }

    @Test
    public void b16_07_trashModeIsExclusive() {
        int active = note("Ativa", "corpo", 0, 0);
        int trashed = note("Na lixeira", "corpo", 0, 0);
        trash(trashed);

        onMain(() -> vm.toggleTrash(false));
        assertTrue("active view must show only active notes", titles(query("", null, null)).contains("Ativa"));

        onMain(() -> vm.toggleTrash(true));
        List<String> trashList = titles(query("", null, null));
        assertTrue("trash view must list the trashed note", trashList.contains("Na lixeira"));
        assertFalse("trash view must not list active notes", trashList.contains("Ativa"));

        onMain(() -> vm.toggleTrash(false));
        assertTrue(createdNotes.contains(active));
    }

    @Test
    public void b16_08_noResultsAndLockedContentIsNotSearchable() {
        note("Visível", "palavra procurável", 0, 0);
        int locked = note("Segredo", "conteúdo confidencial", 0, 1);
        DatabaseHelper.Note n = repo.getNote(locked);
        try {
            n.content = SecurityCore.encrypt(n.content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        n.isLocked = 1;
        repo.updateNote(n);

        assertTrue("no results expected", query("zzz-nao-existe", null, null).isEmpty());
        assertTrue("locked note content must not be searchable while encrypted",
                query("confidencial", null, null).isEmpty());
        assertFalse("locked title is still searchable (masked in UI)",
                query("Segredo", null, null).isEmpty());
    }

    // ---------- B16-09/10: MainActivity UI wiring ----------

    @Test
    public void b16_09_searchUiShowsResultsAndEmptyState() throws Exception {
        note("UI Alvo", "corpo", 0, 0);
        note("UI Outro", "corpo", 0, 1);

        try (ActivityScenario<MainActivity> sc = ActivityScenario.launch(MainActivity.class)) {
            sc.onActivity(a -> {
                try {
                    Field f = MainActivity.class.getDeclaredField("isSearchActive");
                    f.setAccessible(true);
                    f.setBoolean(a, true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                SearchView sv = a.findViewById(R.id.searchView);
                assertNotNull(sv);
                sv.getEditText().setText("UI Alvo");
            });

            RecyclerView results = null;
            long end = System.currentTimeMillis() + 10000;
            while (System.currentTimeMillis() < end) {
                final RecyclerView[] holder = new RecyclerView[1];
                sc.onActivity(a -> holder[0] = a.findViewById(R.id.searchResults));
                results = holder[0];
                if (results != null && results.getVisibility() == View.VISIBLE
                        && results.getAdapter() != null && results.getAdapter().getItemCount() == 1) break;
                Thread.sleep(100);
            }
            assertNotNull(results);
            assertEquals(View.VISIBLE, results.getVisibility());
            assertNotNull("search results adapter missing", results.getAdapter());
            assertEquals("UI search did not narrow to the matching note", 1, results.getAdapter().getItemCount());

            sc.onActivity(a -> {
                SearchView sv = a.findViewById(R.id.searchView);
                sv.getEditText().setText("zzz-nao-existe-ui");
            });

            end = System.currentTimeMillis() + 10000;
            boolean emptyShown = false;
            while (System.currentTimeMillis() < end) {
                final boolean[] shown = new boolean[1];
                final String[] msg = new String[1];
                sc.onActivity(a -> {
                    TextView empty = a.findViewById(R.id.searchEmptyText);
                    shown[0] = empty != null && empty.getVisibility() == View.VISIBLE;
                    msg[0] = empty == null ? "" : empty.getText().toString();
                });
                if (shown[0] && msg[0].contains("zzz-nao-existe-ui")) { emptyShown = true; break; }
                Thread.sleep(100);
            }
            assertTrue("empty-state message not shown for a query without results", emptyShown);
        }
    }
}
