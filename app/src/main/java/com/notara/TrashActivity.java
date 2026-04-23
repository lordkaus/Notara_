package com.notara;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.notara.databinding.ActivityTrashBinding;
import java.util.ArrayList;

public class TrashActivity extends AppCompatActivity {
    private ActivityTrashBinding binding;
    private NoteViewModel viewModel;
    private NoteAdapter adapter;
    private SettingsManager settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);

        // Aplica o tema
        applyTheme();

        super.onCreate(savedInstanceState);
        binding = ActivityTrashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        viewModel.toggleTrash(true);

        setupToolbar();
        setupRecyclerView();

        viewModel.getNotes().observe(this, notes -> {
            if (notes.isEmpty()) {
                binding.emptyTrashState.setVisibility(View.VISIBLE);
                binding.rvTrash.setVisibility(View.GONE);
                binding.btnClearTrash.setVisibility(View.GONE);
            } else {
                binding.emptyTrashState.setVisibility(View.GONE);
                binding.rvTrash.setVisibility(View.VISIBLE);
                binding.btnClearTrash.setVisibility(View.VISIBLE);
                adapter.setNotes(notes);
            }
        });

        binding.btnClearTrash.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Esvaziar Lixeira?")
                .setMessage("Todas as notas na lixeira serão apagadas permanentemente.")
                .setPositiveButton("Esvaziar", (d, w) -> {
                    viewModel.clearTrash();
                    Toast.makeText(this, "Lixeira esvaziada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });
    }

    private void applyTheme() {
        int theme = settings.getTheme();
        WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        if (theme == 0 || theme == 3) {
            setTheme(R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(true);
        } else {
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(false);
        }
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
                viewModel.refreshNotes();
            }

            @Override
            public void onNoteLongClick(DatabaseHelper.Note note) {
                showTrashOptions(note);
            }
        });
        binding.rvTrash.setAdapter(adapter);
    }

    private void showTrashOptions(DatabaseHelper.Note note) {
        String[] options = {"Restaurar", "Excluir Permanentemente"};
        new MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    viewModel.restoreNote(note);
                    Toast.makeText(this, "Nota restaurada", Toast.LENGTH_SHORT).show();
                } else {
                    viewModel.deleteNoteForever(note.id);
                    Toast.makeText(this, "Nota excluída para sempre", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }
}
