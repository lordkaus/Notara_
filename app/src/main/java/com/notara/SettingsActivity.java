package com.notara;

import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private SettingsManager settings;
    private NoteViewModel viewModel;
    private SecurityManager securityManager;
    private SecurityDataStore securityDataStore;

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

        setupSecuritySettings();
        setupPlanningSettings();
        setupDataManagement();
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
