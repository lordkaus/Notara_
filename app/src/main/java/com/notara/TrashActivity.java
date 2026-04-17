package com.notara;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.notara.databinding.ActivityTrashBinding;
import java.util.ArrayList;
import java.util.List;

public class TrashActivity extends AppCompatActivity {
    private ActivityTrashBinding binding;
    private DatabaseHelper db;
    private NoteAdapter adapter;
    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        db = new DatabaseHelper(this);

        // Aplica o tema
        int theme = settings.getTheme();
        if (theme == 0 || theme == 3) {
            setTheme(R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
        }

        super.onCreate(savedInstanceState);
        binding = ActivityTrashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupRecyclerView();
        loadTrash();

        binding.btnClearTrash.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Esvaziar Lixeira?")
                .setMessage("Todas as notas na lixeira serão apagadas permanentemente.")
                .setPositiveButton("Esvaziar", (d, w) -> {
                    db.clearTrash();
                    loadTrash();
                    Toast.makeText(this, "Lixeira esvaziada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.trashToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.trashToolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        int columns = settings.getGridColumns();
        boolean uniform = settings.isUniformGridEnabled();
        
        if (uniform) {
            binding.rvTrash.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, columns));
        } else {
            binding.rvTrash.setLayoutManager(new StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL));
        }
        
        adapter = new NoteAdapter(new ArrayList<>(), new NoteAdapter.NoteActionListener() {
            @Override
            public void onNoteAction() {
                loadTrash();
            }

            @Override
            public void onNoteLongClick(DatabaseHelper.Note note) {
                showTrashOptions(note);
            }
        });
        binding.rvTrash.setAdapter(adapter);
    }

    private void loadTrash() {
        List<DatabaseHelper.Note> trashedNotes = db.searchNotes(null, true, null);
        if (trashedNotes.isEmpty()) {
            binding.emptyTrashState.setVisibility(View.VISIBLE);
            binding.rvTrash.setVisibility(View.GONE);
            binding.btnClearTrash.setVisibility(View.GONE);
        } else {
            binding.emptyTrashState.setVisibility(View.GONE);
            binding.rvTrash.setVisibility(View.VISIBLE);
            binding.btnClearTrash.setVisibility(View.VISIBLE);
            adapter.setNotes(trashedNotes);
        }
    }

    private void showTrashOptions(DatabaseHelper.Note note) {
        String[] options = {"Restaurar", "Excluir Permanentemente"};
        new MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    db.restoreNote(note.id);
                    loadTrash();
                    Toast.makeText(this, "Nota restaurada", Toast.LENGTH_SHORT).show();
                } else {
                    db.deleteNoteForever(note.id);
                    loadTrash();
                    Toast.makeText(this, "Nota excluída para sempre", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
}
