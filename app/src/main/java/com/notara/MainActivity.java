package com.notara;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.notara.widget.NoteWidgetProvider;
import com.notara.widget.NoteWidgetProvider2x2;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.notara.databinding.ActivityMainBinding;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NoteViewModel viewModel;
    private NoteAdapter adapter;
    private SettingsManager settings;
    private SecurityManager securityManager;
    private SecurityDataStore securityDataStore;
    private boolean isAuthenticated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        securityManager = new SecurityManager(this);
        securityDataStore = SecurityDataStore.getInstance(this);

        settings.addObserver(() -> runOnUiThread(() -> {
            applyTheme();
            applyBgTheme();
            if (adapter != null) adapter.notifyDataSetChanged();
        }));

        super.onCreate(savedInstanceState);
        applyTheme();
        
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyBgTheme();

        // Inicializa a UI diretamente
        initApp();
    }

    private void applyBgTheme() {
        int bgTheme = settings.getBgTheme();
        if (bgTheme == 1) { // Mesh
            binding.bgView.setBackgroundResource(R.drawable.bg_mesh);
        } else if (bgTheme == 2) { // Geometric
            binding.bgView.setBackgroundResource(R.drawable.bg_geometric);
        } else { // Default
            binding.bgView.setBackground(null);
        }
    }

    private void applyTheme() {
        int theme = settings.getTheme();
        if (theme == 0) { // Apenas modo Light força ícones pretos
            setTheme(R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else { // Pantera, Dark, Material You Black
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            // Ícones brancos (padrão do sistema Android em fundos escuros)
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void requestBiometricAuth() {
        if (!securityManager.isAuthAvailable()) {
            isAuthenticated = true;
            showContent();
            return;
        }

        securityManager.authenticate(this, 
            "Notara Protegido", 
            "Acesse suas notas com segurança", 
            new SecurityManager.AuthCallback() {
                @Override
                public void onAuthenticated() {
                    isAuthenticated = true;
                    showContent();
                }

                @Override
                public void onError(String error) {
                    // Se o erro for cancelamento ou similar, fecha o app para proteger os dados
                    finish();
                }
            });
    }

    private void showContent() {
        binding.recyclerView.setVisibility(View.VISIBLE);
        binding.fab.setVisibility(View.VISIBLE);
        binding.btnSettings.setVisibility(View.VISIBLE);
        binding.btnCalendar.setVisibility(View.VISIBLE);
        if (viewModel != null) {
            viewModel.refreshNotes();
        }
    }

    private void initApp() {
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        setupRecyclerView();
        setupSearch();
        setupFab();
        requestNotificationPermission();

        viewModel.getNotes().observe(this, notes -> {
            if (notes == null || notes.isEmpty()) {
                binding.emptyState.setVisibility(View.VISIBLE);
                binding.recyclerView.setVisibility(View.GONE);
            } else {
                binding.emptyState.setVisibility(View.GONE);
                binding.recyclerView.setVisibility(View.VISIBLE);
                adapter.setNotes(notes);
            }
        });
    }

    private void setupRecyclerView() {
        int columns = settings.getGridColumns();
        if (settings.isUniformGridEnabled()) {
            binding.recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, columns));
        } else {
            binding.recyclerView.setLayoutManager(new StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL));
        }
        
        adapter = new NoteAdapter(new ArrayList<>(), new NoteAdapter.NoteActionListener() {
            @Override
            public void onNoteAction() {
                viewModel.refreshNotes();
            }

            @Override
            public void onNoteLongClick(DatabaseHelper.Note note) {
                showNoteOptions(note);
            }
        });
        binding.recyclerView.setAdapter(adapter);
    }
private void showNoteOptions(DatabaseHelper.Note note) {
    String[] options = {
        note.isLocked == 1 ? "Destrancar" : "Trancar",
        "Mover para Lixeira",
        "Excluir Permanentemente",
        "Fixar na Tela Inicial (Widget)"
    };

    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
        .setTitle(note.title)
        .setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: toggleLock(note); break;
                case 1: moveToTrash(note); break;
                case 2: authenticateAndDelete(note); break;
                case 3: pinWidget(note); break;
            }
        })
        .setNegativeButton("Cancelar", null)
        .show();
}

private void pinWidget(DatabaseHelper.Note note) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        ComponentName component = new ComponentName(this, NoteWidgetProvider2x2.class);

        if (manager.isRequestPinAppWidgetSupported()) {
            Intent pinnedWidgetCallbackIntent = new Intent(this, NoteWidgetProvider.class);
            // Aqui poderíamos passar o noteId para o callback se precisássemos, 
            // mas a API do Android não vincula automaticamente o ID do widget novo antes do usuário aceitar.
            // Em vez disso, salvaremos uma nota temporária se o usuário confirmar
            // Mas a melhor forma é o usuário adicionar e escolher.
            // Para simplificar, vou apenas solicitar a adição.
            manager.requestPinAppWidget(component, null, null);
            Toast.makeText(this, "Confirme no sistema para adicionar o widget", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Seu launcher não suporta fixar widgets diretamente", Toast.LENGTH_SHORT).show();
        }
    } else {
        Toast.makeText(this, "Opção disponível apenas no Android 8.0+", Toast.LENGTH_SHORT).show();
    }
}


    private void toggleLock(DatabaseHelper.Note note) {
        if (note.isLocked == 1) {
            authenticateAndUnlock(note);
        } else {
            viewModel.toggleLock(note);
            Toast.makeText(this, "Nota trancada", Toast.LENGTH_SHORT).show();
        }
    }

    private void authenticateAndUnlock(DatabaseHelper.Note note) {
        securityManager.authenticate(this, 
            "Destrancar Nota", 
            "Autentique-se para destrancar na grade", 
            new SecurityManager.AuthCallback() {
                @Override
                public void onAuthenticated() {
                    viewModel.toggleLock(note);
                    Toast.makeText(MainActivity.this, "Nota destrancada", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Falha na autenticação: " + error, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void authenticateAndDelete(DatabaseHelper.Note note) {
        securityManager.authenticate(this, 
            "Confirmar Exclusão", 
            "Autentique-se para excluir permanentemente", 
            new SecurityManager.AuthCallback() {
                @Override
                public void onAuthenticated() {
                    new DatabaseHelper(MainActivity.this).deleteNoteForever(note.id);
                    viewModel.refreshNotes();
                    Toast.makeText(MainActivity.this, "Nota excluída permanentemente", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Falha na autenticação: " + error, Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void moveToTrash(DatabaseHelper.Note note) {
        viewModel.moveToTrash(note);
        Toast.makeText(this, "Nota movida para a lixeira", Toast.LENGTH_SHORT).show();
    }

    private void setupSearch() {
        binding.searchView.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setQuery(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFab() {
        binding.fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditActivity.class);
            startActivity(intent);
        });
        
        binding.btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        binding.btnCalendar.setOnClickListener(v -> {
            Intent intent = new Intent(this, CalendarActivity.class);
            startActivity(intent);
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings != null && binding != null) {
            applyBgTheme();
            
            // Força a atualização do adaptador para refletir mudanças de estilo/transparência
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            int columns = settings.getGridColumns();
            boolean uniform = settings.isUniformGridEnabled();
            
            if (uniform) {
                if (!(binding.recyclerView.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager)) {
                    binding.recyclerView.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, columns));
                } else {
                    ((androidx.recyclerview.widget.GridLayoutManager) binding.recyclerView.getLayoutManager()).setSpanCount(columns);
                }
            } else {
                if (!(binding.recyclerView.getLayoutManager() instanceof StaggeredGridLayoutManager)) {
                    binding.recyclerView.setLayoutManager(new StaggeredGridLayoutManager(columns, StaggeredGridLayoutManager.VERTICAL));
                } else {
                    ((StaggeredGridLayoutManager) binding.recyclerView.getLayoutManager()).setSpanCount(columns);
                }
            }
        }
        // Sempre atualiza as notas para garantir a privacidade (exibir * e esconder conteúdo)
        if (viewModel != null) {
            viewModel.refreshNotes();
        }
    }
}
