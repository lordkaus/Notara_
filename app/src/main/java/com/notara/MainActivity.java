/*
 * Copyright (c) 1996 lordkaus
 * This file is part of Notara_.
 *
 * Notara_ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Notara_ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Notara_. If not, see <https://www.gnu.org/licenses/>.
 */
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
            "Compartilhar..."
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(note.title)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: toggleLock(note); break;
                    case 1: moveToTrash(note); break;
                    case 2: authenticateAndDelete(note); break;
                    case 3: pinWidget(note); break;
                    case 4: showShareOptionsDialog(note); break;
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void showShareOptionsDialog(DatabaseHelper.Note note) {
        String[] options = {
            "Compartilhar Texto (Copia e Cola)",
            "Salvar Arquivo em .txt",
            "Exportar Seguro .savage (Com Cripto)"
        };

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Compartilhar: " + note.title)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: shareNoteNormally(note); break;
                    case 1: exportNoteAsFile(note); break;
                    case 2: exportSecureNoteAsFile(note); break;
                }
            })
            .setNegativeButton("Voltar", null)
            .show();
    }

    private void exportSecureNoteAsFile(DatabaseHelper.Note note) {
        android.widget.EditText etPassword = new android.widget.EditText(this);
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setHint("Senha de Exportação");

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Criptografar Arquivo")
            .setMessage("Defina uma senha para proteger este arquivo .savage.")
            .setView(etPassword)
            .setPositiveButton("Próximo", (d, w) -> {
                String pass = etPassword.getText().toString();
                if (pass.isEmpty()) { Toast.makeText(this, "Senha necessária", Toast.LENGTH_SHORT).show(); return; }
                
                if (note.isLocked == 1) {
                    authenticateForAction(note, n -> {
                        noteToExport = n;
                        secureExportPassword = pass;
                        launchSecureFilePicker();
                    });
                } else {
                    noteToExport = note;
                    secureExportPassword = pass;
                    launchSecureFilePicker();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private String secureExportPassword;
    private void launchSecureFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // ALTERAÇÃO: application/octet-stream impede que o Android tente "corrigir" a extensão
        intent.setType("application/octet-stream"); 
        intent.putExtra(Intent.EXTRA_TITLE, getSanitizedBaseName(noteToExport.title) + ".savage");
        startActivityForResult(intent, REQUEST_CODE_CREATE_FILE + 1);
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

    private DatabaseHelper.Note noteToExport;
    private static final int REQUEST_CODE_CREATE_FILE = 1002;

    private void shareNoteNormally(DatabaseHelper.Note note) {
        if (note.isLocked == 1) {
            authenticateForAction(note, this::doShareNormally);
        } else {
            doShareNormally(note);
        }
    }

    private void doShareNormally(DatabaseHelper.Note note) {
        String content = formatNoteForExport(note);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, note.title);
        intent.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(intent, "Compartilhar nota"));
    }

    private void exportNoteAsFile(DatabaseHelper.Note note) {
        if (note.isLocked == 1) {
            authenticateForAction(note, n -> {
                noteToExport = n;
                launchTextFilePicker();
            });
        } else {
            noteToExport = note;
            launchTextFilePicker();
        }
    }

    private String getSanitizedBaseName(String title) {
        String name = title.replaceAll("(?i)\\.(txt|savage|md|json)$", "");
        return name.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    private void launchTextFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, getSanitizedBaseName(noteToExport.title) + ".txt");
        startActivityForResult(intent, REQUEST_CODE_CREATE_FILE);
    }

    private String formatNoteForExport(DatabaseHelper.Note note) {
        String content = note.content;
        if (note.isLocked == 1) {
            try { content = SecurityCore.decrypt(note.content); } catch (Exception e) { return "Erro ao descriptografar."; }
        }

        if (note.type == 1) { // Checklist
            StringBuilder sb = new StringBuilder();
            for (String line : content.split("\n")) {
                if (line.contains("::")) {
                    String[] parts = line.split("::");
                    sb.append(parts.length > 1 && parts[1].equals("1") ? "[x] " : "[ ] ").append(parts[0]).append("\n");
                }
            }
            return note.title + "\n\n" + sb.toString();
        }
        return note.title + "\n\n" + content;
    }

    private void authenticateForAction(DatabaseHelper.Note note, java.util.function.Consumer<DatabaseHelper.Note> action) {
        securityManager.authenticate(this, "Autenticação", "Autentique-se para continuar", new SecurityManager.AuthCallback() {
            @Override public void onAuthenticated() { action.accept(note); }
            @Override public void onError(String error) { Toast.makeText(MainActivity.this, "Erro: " + error, Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == REQUEST_CODE_CREATE_FILE) {
                try {
                    String content = formatNoteForExport(noteToExport);
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                    os.write(content.getBytes());
                    os.close();
                    Toast.makeText(this, "Arquivo salvo com sucesso!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Erro ao salvar arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQUEST_CODE_CREATE_FILE + 1) {
                try {
                    String content = formatNoteForExport(noteToExport);
                    String encrypted = SecurityHelper.encryptForSharing("NOTARA_SECURE_NOTE\nTITLE: " + noteToExport.title + "\nCONTENT: " + content, secureExportPassword);
                    java.io.OutputStream os = getContentResolver().openOutputStream(data.getData());
                    os.write(encrypted.getBytes());
                    os.close();
                    Toast.makeText(this, "Arquivo .savage protegido salvo!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Erro na criptografia: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent();
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        
        String action = intent.getAction();
        
        // Trata Compartilhamento (SEND)
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null && sharedText.contains("PACOTE:")) {
                String[] parts = sharedText.split("PACOTE:\n");
                if (parts.length > 1) promptForDecryptPassword(parts[1].trim());
            }
        } 
        // Trata Abrir Arquivo (VIEW)
        else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            handleIncomingFile(intent.getData());
        }
    }

    private void handleIncomingFile(android.net.Uri fileUri) {
        try {
            java.io.InputStream inputStream = getContentResolver().openInputStream(fileUri);
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) content.append(line).append('\n');
            inputStream.close();
            String finalData = content.toString();

            // Se for .savage, tenta descriptografar
            if (fileUri.toString().endsWith(".savage")) {
                promptForDecryptPassword(finalData.trim());
            } else {
                // Importação simples .txt
                DatabaseHelper.Note newNote = new DatabaseHelper.Note(-1, "Nota Importada", finalData, 0, 1, 0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0);
                viewModel.addNote(newNote);
                Toast.makeText(this, "Arquivo .txt importado!", Toast.LENGTH_SHORT).show();
                viewModel.refreshNotes();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao processar arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                String title = "Nota Importada";
                String content = "";
                
                String[] lines = decrypted.split("\n");
                for (String line : lines) {
                    if (line.startsWith("TITLE: ")) title = line.substring(7);
                    else if (line.startsWith("CONTENT: ")) content = line.substring(9);
                }
                
                if (content.isEmpty() && lines.length > 2) {
                    int contentIdx = decrypted.indexOf("CONTENT: ");
                    if (contentIdx != -1) content = decrypted.substring(contentIdx + 9);
                }

                // Cria uma referência temporária para confirmar com o usuário
                final String finalTitle = title;
                final String finalContent = content;

                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Importar Nota")
                    .setMessage("Deseja importar a nota '" + finalTitle + "' e trancá-la com segurança?")
                    .setPositiveButton("Sim, importar e trancar", (d, w) -> {
                        // isLocked = 1 para trancar
                        DatabaseHelper.Note newNote = new DatabaseHelper.Note(-1, finalTitle, finalContent, 0, 1, 0, 0, null, 0, 0, 0, null, 1, 0, System.currentTimeMillis(), 0);
                        viewModel.addNote(newNote);
                        Toast.makeText(this, "Nota importada e trancada!", Toast.LENGTH_SHORT).show();
                        viewModel.refreshNotes();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
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
