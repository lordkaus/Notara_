package com.notara;

import org.junit.Test;
import static org.mockito.Mockito.*;

public class NoteRepositoryImplTest {

    @Test
    public void testAddNoteDelegatesToDatabaseHelper() {
        DatabaseHelper mockDb = mock(DatabaseHelper.class);
        NoteRepositoryImpl repository = new NoteRepositoryImpl(mockDb);
        DatabaseHelper.Note note = new DatabaseHelper.Note(1, "Title", "Content", 0, 0, 0, 0, null, 0, 0, 0, null, 0, 0, 0, 0);

        repository.addNote(note);

        verify(mockDb, times(1)).addNote(note);
    }
}
