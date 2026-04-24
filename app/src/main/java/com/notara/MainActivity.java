package com.notara;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.notara.databinding.ActivityMainBinding;
import com.notara.widget.NoteWidgetProvider;
import com.notara.widget.NoteWidgetProvider2x2;
import java.util.ArrayList;

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
            if (viewModel != null) {
                viewModel.refreshNotes();
            }
        }));

        super.onCreate(savedInstanceState);
        applyTheme();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyBgTheme();
        handleIncomingIntent();

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

        // Define o tema da Activity
        if (theme == 1) {
            setTheme(R.style.Theme_Notara_Pantera);
        } else {
            setTheme(R.style.Theme_Notara);
        }

        // Gerencia a cor dos ícones da barra de status usando a API do AndroidX
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Se theme == 0 (Light) ou theme == 3 (Dynamic Light), ícones pretos (true). Caso contrário, ícones brancos (false).
        controller.setAppearanceLightStatusBars(theme == 0 || theme == 3);
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
            "Fixar na Tela Inicial (Widget)",
            "Compartilhamento Seguro"
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: toggleLock(note); break;
                    case 1: moveToTrash(note); break;
                    case 2: authenticateAndDelete(note); break;
                    case 3: pinWidget(note); break;
                    case 4: shareNoteSecurely(note); break;
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
                    viewModel.deleteNoteForever(note.id);
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

    private void shareNoteSecurely(DatabaseHelper.Note note) {
        if (note.isLocked == 1) {
            securityManager.authenticate(this,
                "Confirmar Identidade",
                "Autentique-se para descriptografar e compartilhar a nota",
                new SecurityManager.AuthCallback() {
                    @Override
                    public void onAuthenticated() {
                        try {
                            String decryptedContent = SecurityCore.decrypt(note.content);
                            promptForSharingPassword(note.title, decryptedContent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "Erro ao descriptografar para compartilhamento", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, "Falha na autenticação", Toast.LENGTH_SHORT).show();
                    }
                });
        } else {
            promptForSharingPassword(note.title, note.content);
        }
    }

    private void promptForSharingPassword(String title, String content) {
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint("Senha de Compartilhamento");

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = params.rightMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        etPassword.setLayoutParams(params);
        container.addView(etPassword);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Proteja sua Nota")
            .setMessage("Crie uma senha para criptografar o pacote de compartilhamento. O destinatário precisará desta senha para abrir.")
            .setView(container)
            .setPositiveButton("Compartilhar", (d, w) -> {
                String password = etPassword.getText().toString();
                if (password.isEmpty()) {
                    Toast.makeText(this, "A senha é obrigatória para compartilhamento seguro", Toast.LENGTH_SHORT).show();
                    return;
                }
                executeSecureShare(title, content, password);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void executeSecureShare(String title, String content, String password) {
        try {
            // Prepara o texto final (Título + Conteúdo)
            String fullText = "NOTARA_SECURE_NOTE\nTITLE: " + title + "\nCONTENT: " + content;

            // Criptografa usando SecurityHelper
            String encryptedPackage = SecurityHelper.encryptForSharing(fullText, password);

            // Dispara Intent
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Nota Segura do Notara: " + title);
            intent.putExtra(Intent.EXTRA_TEXT, "Esta é uma nota segura do Notara.\nPara abri-la, instale o Notara e use a senha combinada.\n\nPACOTE:\n" + encryptedPackage);
            startActivity(Intent.createChooser(intent, "Compartilhar Nota Segura"));

        } catch (Exception e) {
            Toast.makeText(this, "Erro ao gerar pacote seguro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null && sharedText.contains("PACOTE:")) {
                    String[] parts = sharedText.split("PACOTE:\n");
                    if (parts.length > 1) {
                        String encodedPackage = parts[1].trim();
                        promptForDecryptPassword(encodedPackage);
                    }
                }
            }
        }
    }

    private void promptForDecryptPassword(String encodedPackage) {
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint("Senha de Descriptografia");

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = params.rightMargin = (int) (24 * getResources().getDisplayMetrics().density);
        params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
        etPassword.setLayoutParams(params);
        container.addView(etPassword);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Receber Nota Segura")
            .setMessage("Esta nota está protegida. Insira a senha definida pelo remetente para abrir.")
            .setView(container)
            .setPositiveButton("Abrir", (d, w) -> {
                String password = etPassword.getText().toString();
                decryptAndSaveSharedNote(encodedPackage, password);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void decryptAndSaveSharedNote(String encodedPackage, String password) {
        try {
            String decrypted = SecurityHelper.decryptFromSharing(encodedPackage, password);
            if (decrypted.startsWith("NOTARA_SECURE_NOTE")) {
                // Parse simples
                String title = "Nota Importada";
                String content = "";
                
                String[] lines = decrypted.split("\n");
                for (String line : lines) {
                    if (line.startsWith("TITLE: ")) title = line.substring(7);
                    else if (line.startsWith("CONTENT: ")) content = line.substring(9);
                }
                
                if (content.isEmpty() && lines.length > 2) {
                    // Tenta capturar conteúdo multilinhas se necessário
                    int contentIdx = decrypted.indexOf("CONTENT: ");
                    if (contentIdx != -1) content = decrypted.substring(contentIdx + 9);
                }

                // Salva no banco
                DatabaseHelper.Note newNote = new DatabaseHelper.Note(-1, title, content, 0, 1, 0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0);
                viewModel.addNote(newNote);
                Toast.makeText(this, "Nota importada com sucesso!", Toast.LENGTH_SHORT).show();
                viewModel.refreshNotes();
            }
        } catch (javax.crypto.AEADBadTagException e) {
            Toast.makeText(this, "Senha incorreta ou pacote corrompido.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao processar nota: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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
        if (viewModel != null) {
            viewModel.refreshNotes();
        }
    }
}
