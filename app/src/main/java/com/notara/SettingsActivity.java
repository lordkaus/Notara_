package com.notara;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Consumer;
import android.widget.LinearLayout;

public class SettingsActivity extends AppCompatActivity {
    private SettingsManager settings;
    private NoteViewModel viewModel;
    private SecurityManager securityManager;
    private SecurityDataStore securityDataStore;
    private String pendingPassword;
    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        securityManager = new SecurityManager(this);
        securityDataStore = SecurityDataStore.getInstance(this);

        int theme = settings.getTheme();
        WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        if (theme == 0) {
            setTheme(R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(true);
        } else {
            setTheme(R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(false);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupToolbar();
        setupGridSettings();
        setupToquesSettings();
        setupThemeSettings();

        setupVersionInfo();
        setupSecuritySettings();
        setupPlanningSettings();
        setupDataManagement();
        setupBackupSettings();

        handleBackupIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBackupIntent(intent);
    }

    private void setupBackupSettings() {
        exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                try {
                    NoteRepository repo = new NoteRepositoryImpl(new DatabaseHelper(this));
                    List<DatabaseHelper.Note> notes = repo.searchNotes("", false, null);
                    if (notes.isEmpty()) {
                        Toast.makeText(this, R.string.backup_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String data = BackupManager.serialize(notes);
                    if (pendingPassword != null) {
                        data = BackupManager.encrypt(data, pendingPassword);
                        pendingPassword = null;
                    }
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        BackupManager.write(os, data);
                    }
                    Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.backup_read_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        );

        importLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                String data;
                try {
                    data = readUriContent(uri);
                } catch (Exception e) {
                    Toast.makeText(this, getString(R.string.backup_read_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
                boolean encrypted = BackupManager.isEncrypted(data);
                if (encrypted) {
                    promptPassword((password) -> doRestore(data, password));
                } else {
                    doRestore(data, null);
                }
            }
        );

        findViewById(R.id.btnBackup).setOnClickListener(v -> {
            pendingPassword = null;
            launchExport();
        });

        findViewById(R.id.btnBackupEncrypted).setOnClickListener(v -> {
            promptPassword((password) -> {
                pendingPassword = password;
                launchExport();
            });
        });

        findViewById(R.id.btnRestoreBackup).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            importLauncher.launch(intent);
        });
    }

    private void launchExport() {
        NoteRepository repo = new NoteRepositoryImpl(new DatabaseHelper(this));
        List<DatabaseHelper.Note> notes = repo.searchNotes("", false, null);
        if (notes.isEmpty()) {
            Toast.makeText(this, R.string.backup_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "Notara_Backup.nrb");
        exportLauncher.launch(intent);
    }

    private String readUriContent(Uri uri) throws IOException {
        Exception lastEx = null;
        if (!"content".equals(uri.getScheme())) {
            try (InputStream is = new FileInputStream(uri.getPath())) {
                return safeRead(is);
            } catch (Exception e) { lastEx = e; }
        }
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is != null) return safeRead(is);
        } catch (Exception e) {
            lastEx = e;
        }
        String path = extractPathFromUri(uri);
        if (path != null) {
            try (InputStream is = new FileInputStream(path)) {
                return safeRead(is);
            } catch (Exception ignored) {}
        }
        if (lastEx != null) throw new IOException(lastEx);
        throw new IOException("Não foi possível ler o arquivo");
    }

    private String safeRead(InputStream is) throws IOException {
        try {
            return BackupManager.read(is);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private String extractPathFromUri(Uri uri) {
        String docId = null;
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            try {
                docId = DocumentsContract.getDocumentId(uri);
            } catch (Exception ignored) {}
        }
        if (docId == null) {
            String path = uri.getPath();
            if (path != null) {
                int idx = path.lastIndexOf("document/");
                if (idx >= 0) docId = path.substring(idx + "document/".length());
            }
        }
        if (docId == null) return null;
        docId = Uri.decode(docId);
        String[] parts = docId.split(":");
        String storage;
        if (parts.length >= 2) {
            if ("primary".equalsIgnoreCase(parts[0])) {
                storage = Environment.getExternalStorageDirectory().getAbsolutePath();
            } else {
                storage = "/storage/" + parts[0];
            }
            return storage + "/" + parts[1];
        }
        return parts[0];
    }

    private void handleBackupIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action)) return;
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri.class);
        }
        if (uri == null) return;
        String data;
        try {
            data = readUriContent(uri);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.backup_read_error) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        boolean encrypted = BackupManager.isEncrypted(data);
        if (encrypted) {
            promptPasswordForRestore(data);
        } else {
            doRestore(data, null);
        }
    }

    private void promptPasswordForRestore(String data) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.backup_password_hint);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_encrypted)
            .setView(input)
            .setPositiveButton("OK", (d, w) -> {
                String pwd = input.getText().toString();
                if (pwd.isEmpty()) {
                    Toast.makeText(this, "A senha não pode estar vazia", Toast.LENGTH_SHORT).show();
                    return;
                }
                doRestore(data, pwd);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void doRestore(String data, String password) {
        try {
            if (password != null) data = BackupManager.decrypt(data, password);
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_wrong_password, Toast.LENGTH_SHORT).show();
            return;
        }
        List<DatabaseHelper.Note> notes;
        try {
            notes = BackupManager.deserialize(data);
        } catch (Exception e) {
            Toast.makeText(this, R.string.backup_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        int count = 0;
        for (DatabaseHelper.Note note : notes) {
            note.id = -1;
            viewModel.addNote(note);
            count++;
        }
        Toast.makeText(this, getString(R.string.backup_restored, count), Toast.LENGTH_SHORT).show();
    }

    private void promptPassword(java.util.function.Consumer<String> callback) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.backup_password_hint);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);

        EditText confirmInput = new EditText(this);
        confirmInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmInput.setHint(R.string.backup_password_confirm);
        confirmInput.setPadding(padding, padding, padding, padding);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(input);
        layout.addView(confirmInput);

        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_encrypted)
            .setView(layout)
            .setPositiveButton("OK", (d, w) -> {
                String pwd = input.getText().toString();
                String confirm = confirmInput.getText().toString();
                if (!pwd.equals(confirm)) {
                    Toast.makeText(this, R.string.backup_password_mismatch, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (pwd.isEmpty()) {
                    Toast.makeText(this, "A senha não pode estar vazia", Toast.LENGTH_SHORT).show();
                    return;
                }
                callback.accept(pwd);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void setupPlanningSettings() {
        findViewById(R.id.btnRecurringNotes).setOnClickListener(v -> {
            List<DatabaseHelper.Note> recurringNotes = viewModel.getRecurringNotes();
            if (recurringNotes.isEmpty()) {
                Toast.makeText(this, "Nenhuma nota recorrente encontrada", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] titles = new String[recurringNotes.size()];
            for (int i = 0; i < recurringNotes.size(); i++) {
                DatabaseHelper.Note note = recurringNotes.get(i);
                String prefix = note.type == 1 ? "[Lista] " : "[Nota] ";
                String rec = "";
                switch (note.recurrenceType) {
                    case 1: rec = " (Diária)"; break;
                    case 2: rec = " (Semanal)"; break;
                    case 3: rec = " (Mensal)"; break;
                    case 4: rec = " (Anual)"; break;
                    case 5: rec = " (Personalizada)"; break;
                }
                titles[i] = prefix + (note.title.isEmpty() ? DatabaseHelper.Note.extractTitle(note.content) : note.title) + rec;
            }

            new MaterialAlertDialogBuilder(this)
                .setTitle("Notas e Listas Recorrentes")
                .setItems(titles, (dialog, which) -> {
                    DatabaseHelper.Note selectedNote = recurringNotes.get(which);
                    Intent intent = new Intent(this, selectedNote.type == 1 ? ChecklistActivity.class : EditActivity.class);
                    intent.putExtra("NOTE_ID", selectedNote.id);
                    startActivity(intent);
                })
                .setNegativeButton("Fechar", null)
                .show();
        });
    }



    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupGridSettings() {
        Slider slider = findViewById(R.id.sliderGrid);
        android.widget.TextView tvValue = findViewById(R.id.tvGridValue);

        int currentCols = settings.getGridColumns();
        slider.setValue(currentCols);
        tvValue.setText(String.valueOf(currentCols));

        slider.addOnChangeListener((s, value, fromUser) -> {
            int val = (int) value;
            settings.setGridColumns(val);
            tvValue.setText(String.valueOf(val));
        });

        SwitchMaterial swUniform = findViewById(R.id.switchUniformGrid);
        swUniform.setChecked(settings.isUniformGridEnabled());
        swUniform.setOnCheckedChangeListener((v, checked) -> settings.setUniformGridEnabled(checked));

        SwitchMaterial sw24h = findViewById(R.id.switch24h);
        sw24h.setChecked(settings.is24HourFormat());
        sw24h.setOnCheckedChangeListener((v, checked) -> settings.set24HourFormat(checked));
    }

    private void setupVersionInfo() {
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText("v" + BuildConfig.VERSION_NAME + " (Build " + BuildConfig.VERSION_CODE + ")");
    }

    private void setupToquesSettings() {
        SwitchMaterial swNotify = findViewById(R.id.switchNotifyColor);
        SwitchMaterial swAlarm = findViewById(R.id.switchAlarmColor);
        swNotify.setChecked(settings.isNotificationColorSync());
        swAlarm.setChecked(settings.isAlarmColorSync());
        swNotify.setOnCheckedChangeListener((v, checked) -> settings.setNotificationColorSync(checked));
        swAlarm.setOnCheckedChangeListener((v, checked) -> settings.setAlarmColorSync(checked));
    }

    private void setupThemeSettings() {
        Slider sliderTrans = findViewById(R.id.sliderTransparency);
        android.widget.TextView tvTransValue = findViewById(R.id.tvTransparencyValue);
        int currentTrans = settings.getTransparency();
        sliderTrans.setValue(currentTrans);
        tvTransValue.setText(getString(R.string.transparency_value, currentTrans));

        sliderTrans.addOnChangeListener((s, value, fromUser) -> {
            int val = (int) value;
            settings.setTransparency(val);
            tvTransValue.setText(getString(R.string.transparency_value, val));
            com.notara.widget.NoteWidgetProvider.updateAllWidgets(this);
        });
    }



    private void setupSecuritySettings() {
        SwitchMaterial swBio = findViewById(R.id.switchBiometric);
        swBio.setChecked(securityDataStore.isSecurityEnabledSync());
        swBio.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !securityManager.isAuthAvailable() && !securityManager.isInternalPasswordSet()) {
                v.setChecked(false);
                securityManager.promptSecuritySetup(this);
            } else {
                securityDataStore.setSecurityEnabled(checked);
                settings.setBiometricEnabled(checked);
            }
        });
        findViewById(R.id.btnSetAppPassword).setVisibility(android.view.View.VISIBLE);
        findViewById(R.id.btnSetAppPassword).setOnClickListener(v -> {
            securityManager.promptSecuritySetup(this);
        });
    }

    private void setupDataManagement() {
        findViewById(R.id.btnTrash).setOnClickListener(v -> {
            startActivity(new Intent(this, TrashActivity.class));
        });

        findViewById(R.id.btnResetAll).setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Apagar Tudo?")
                .setMessage("Esta ação removerá todas as suas notas permanentemente. Confirme sua identidade.")
                .setPositiveButton("Redefinir", (d, w) -> {
                    securityManager.authenticate(this,
                        "Confirmar Exclusão",
                        "Autentique-se para apagar todos os dados",
                        new SecurityManager.AuthCallback() {
                            @Override
                            public void onAuthenticated() {
                                performFullReset();
                            }
                            @Override
                            public void onError(String error) {
                                Toast.makeText(SettingsActivity.this, "Erro na autenticação: " + error, Toast.LENGTH_SHORT).show();
                            }
                        });
                })
                .setNegativeButton("Cancelar", null)
                .show();
        });
    }

    private void performFullReset() {
        viewModel.resetAllNotes();
        settings.clearAll();
        securityDataStore.clearAll();
        securityManager.clearAll();
        com.notara.widget.NoteWidgetProvider.updateAllWidgets(this);
        Toast.makeText(this, "Todos os dados e configurações foram redefinidos.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
