package com.notara;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.DatePicker;
import android.widget.GridView;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.notara.databinding.ActivityEditBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class EditActivity extends AppCompatActivity {
    private ActivityEditBinding binding;
    private NoteViewModel viewModel;
    private int noteId = -1;
    private DatabaseHelper.Note currentNote;
    private int selectedColor = 0;
    private long reminderTime = 0;
    private long originalReminderTime = 0;
    private int recurrenceType = 0;
    private int alertType = 0;
    private SecurityManager securityManager;

    public static final String[] noteColors = {
        "#FFEB3B", "#4DB6AC", "#FF9800", "#8BC34A", "#F44336", "#9C27B0", "#2196F3", "#E91E63"
    };

    private SettingsManager settings;
    private boolean isUnlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        securityManager = new SecurityManager(this);
        super.onCreate(savedInstanceState);
        
        // Aplica o tema
        int theme = settings.getTheme();
        if (theme == 0 || theme == 3) {
            setTheme(R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
        }

        binding = ActivityEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        noteId = getIntent().getIntExtra("NOTE_ID", -1);
        
        // Se for uma nova nota vinda do calendário, pega o tempo sugerido
        if (noteId == -1) {
            reminderTime = getIntent().getLongExtra("INITIAL_REMINDER_TIME", 0);
        }

        if (noteId != -1) {
            currentNote = viewModel.getNote(noteId);
            if (currentNote != null) {
                // Título original sem o '*' de visualização da grade
                binding.etTitle.setText(currentNote.title);
                
                // Se viermos de uma conversão de checklist, usamos o conteúdo convertido
                String convertContent = getIntent().getStringExtra("CONVERT_CONTENT");
                if (convertContent != null) {
                    binding.editNoteText.setText(convertContent);
                } else {
                    binding.editNoteText.setText(currentNote.content);
                }
                
                selectedColor = currentNote.color;
                reminderTime = currentNote.reminderTime;
                originalReminderTime = currentNote.originalReminderTime;
                recurrenceType = currentNote.recurrenceType;
                alertType = currentNote.alertType;

                // Bloqueia o conteúdo se for uma nota trancada
                if (currentNote.isLocked == 1) {
                    lockContent();
                    requestUnlock();
                } else {
                    isUnlocked = true;
                }
            }
        }
        
        requestPermissions();
        updateColorIndicator();
        updateDate();
        setupListeners();
    }

    private void lockContent() {
        binding.editNoteText.setVisibility(View.GONE);
        // Esconde todos os itens da barra inferior
        binding.btnColorPicker.setVisibility(View.GONE);
        binding.btnReminder.setVisibility(View.GONE);
        binding.btnAlarm.setVisibility(View.GONE);
        binding.btnConvertToChecklist.setVisibility(View.GONE);
        binding.btnSave.setVisibility(View.GONE);
    }

    private void unlockContent() {
        isUnlocked = true;
        if (currentNote != null && currentNote.isLocked == 1) {
            try {
                String decrypted = SecurityCore.decrypt(currentNote.content);
                binding.editNoteText.setText(decrypted);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao descriptografar nota.", Toast.LENGTH_SHORT).show();
            }
        }
        binding.editNoteText.setVisibility(View.VISIBLE);
        binding.btnColorPicker.setVisibility(View.VISIBLE);
        binding.btnReminder.setVisibility(View.VISIBLE);
        binding.btnAlarm.setVisibility(View.VISIBLE);
        binding.btnConvertToChecklist.setVisibility(View.VISIBLE);
        binding.btnSave.setVisibility(View.VISIBLE);
    }

    private void requestUnlock() {
        securityManager.authenticate(this, 
            "Nota Trancada", 
            "Autentique-se para ver o conteúdo", 
            new SecurityManager.AuthCallback() {
                @Override
                public void onAuthenticated() {
                    unlockContent();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(EditActivity.this, "Acesso negado: " + error, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
    }

    private void requestPermissions() {
        if (!PermissionUtils.hasNotificationPermission(this)) {
            PermissionUtils.requestNotificationPermission(this, 101);
        }
    }

    private int recurrenceDays = 0;

    private void updateDate() {
        StringBuilder sb = new StringBuilder();
        Calendar now = Calendar.getInstance();
        Calendar modified = Calendar.getInstance();
        if (currentNote != null && currentNote.lastModified > 0) {
            modified.setTimeInMillis(currentNote.lastModified);
        } else {
            modified.setTimeInMillis(System.currentTimeMillis());
        }

        // Informação de Agendamento (Se houver)
        if (reminderTime > 0) {
            Calendar reminder = Calendar.getInstance();
            reminder.setTimeInMillis(reminderTime);
            
            String type = (alertType == 1) ? "⏰ Alarme" : "🔔 Lembrete";
            sb.append(type);
            
            // Recorrência
            String[] recurrences = {"", " (Diário)", " (Semanal)", " (Mensal)", " (Anual)", " (Pers.)"};
            if (recurrenceType > 0 && recurrenceType < recurrences.length) {
                sb.append(recurrences[recurrenceType]);
            }
            
            sb.append(": ");
            
            // Formata Data (Ano opcional)
            String pattern = (reminder.get(Calendar.YEAR) == now.get(Calendar.YEAR)) ? "dd/MM" : "dd/MM/yyyy";
            String timeFormat = settings.is24HourFormat() ? "HH:mm" : "hh:mm a";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern + " 'às' " + timeFormat, Locale.getDefault());
            sb.append(sdf.format(reminder.getTime()));
            
            binding.tvDate.setText(sb.toString());
            binding.tvDate.setTextColor(alertType == 1 ? Color.parseColor("#F44336") : Color.parseColor("#4DB6AC"));
        } else {
            // Apenas Última Modificação
            String pattern = (modified.get(Calendar.YEAR) == now.get(Calendar.YEAR)) ? "dd/MM" : "dd/MM/yyyy";
            String timeFormat = settings.is24HourFormat() ? "HH:mm" : "hh:mm a";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern + " 'às' " + timeFormat, Locale.getDefault());
            binding.tvDate.setText("Editado em " + sdf.format(modified.getTime()));
            binding.tvDate.setTextColor(Color.GRAY);
        }
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> { saveNote(); finish(); });
        binding.btnColorPicker.setOnClickListener(v -> showColorPicker());
        binding.btnConvertToChecklist.setOnClickListener(v -> convertToChecklist());
        binding.btnReminder.setOnClickListener(v -> showReminderDialog(0));
        binding.btnAlarm.setOnClickListener(v -> showReminderDialog(1));
    }

    private void showColorPicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_color_picker, null);
        androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.colorRecyclerView);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 4));
        
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Escolher Cor")
            .setView(dialogView)
            .create();

        rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(getLayoutInflater().inflate(R.layout.item_color_picker, p, false)) {};
            }
            @Override public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder h, int p) {
                View colorView = h.itemView.findViewById(R.id.colorView);
                int color = Color.parseColor(noteColors[p]);
                
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                shape.setColor(color);
                
                // Se for a cor selecionada, adiciona uma borda de destaque
                if (selectedColor == p) {
                    shape.setStroke(6, Color.WHITE);
                }
                
                colorView.setBackground(shape);
                colorView.setOnClickListener(v -> {
                    selectedColor = p;
                    updateColorIndicator();
                    dialog.dismiss();
                });
            }
            @Override public int getItemCount() { return noteColors.length; }
        });
        
        dialog.show();
    }

    private void updateColorIndicator() {
        int color = Color.parseColor(noteColors[selectedColor % noteColors.length]);
        binding.topColorIndicator.setBackgroundColor(color);
        binding.btnSave.setBackgroundColor(color);
        binding.editNoteText.setLineColor(color);

        // Atualiza o pequeno círculo de preview na barra inferior
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setStroke(4, Color.WHITE); // Adiciona uma borda branca sofisticada
        binding.viewSelectedColor.setBackground(shape);
    }

    private void showReminderDialog(int type) {
        if (!PermissionUtils.hasNotificationPermission(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("Permissão Necessária").setMessage("Ative as notificações para receber lembretes.").setPositiveButton("Configurações", (d, w) -> PermissionUtils.openNotificationSettings(this)).setNegativeButton("Agora não", null).show();
            return;
        }

        if (!PermissionUtils.canScheduleExactAlarms(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("Alarme Exato Necessário").setMessage("O Android 15 exige permissão especial para Alarmes Exatos.").setPositiveButton("Configurar", (d, w) -> PermissionUtils.openExactAlarmSettings(this)).setNegativeButton("Cancelar", null).show();
            return;
        }

        // PASSO 1: Selecionar Data (MaterialDatePicker)
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("1. Escolha a Data")
                .setSelection(reminderTime > 0 ? reminderTime : com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // Converte a seleção UTC para LocalDate pura para evitar o deslocamento do fuso
            java.time.LocalDate pickedDate = java.time.Instant.ofEpochMilli(selection)
                    .atZone(java.time.ZoneId.of("UTC"))
                    .toLocalDate();
            
            Calendar cal = Calendar.getInstance();
            cal.set(pickedDate.getYear(), pickedDate.getMonthValue() - 1, pickedDate.getDayOfMonth());
            
            // PASSO 2: Selecionar Hora (MaterialTimePicker)
            int initialHour = 9, initialMinute = 0;
            if (reminderTime > 0) {
                Calendar current = Calendar.getInstance(); current.setTimeInMillis(reminderTime);
                initialHour = current.get(Calendar.HOUR_OF_DAY); initialMinute = current.get(Calendar.MINUTE);
            }

            com.google.android.material.timepicker.MaterialTimePicker timePicker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                    .setTimeFormat(settings.is24HourFormat() ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                    .setHour(initialHour)
                    .setMinute(initialMinute)
                    .setTitleText("2. Escolha o Horário")
                    .build();

            timePicker.addOnPositiveButtonClickListener(v -> {
                cal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                cal.set(Calendar.MINUTE, timePicker.getMinute());
                cal.set(Calendar.SECOND, 0);

                // PASSO 3: Repetição
                showRecurrenceStep(cal, type);
            });

            timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showRecurrenceStep(Calendar cal, int type) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_step_recurrence, null);
        com.google.android.material.switchmaterial.SwitchMaterial sw = v.findViewById(R.id.switchRecurrenceToggle);
        View options = v.findViewById(R.id.layoutRecurrenceOptions);
        AutoCompleteTextView dropdown = v.findViewById(R.id.dropdownRecurrenceType);
        View customLayout = v.findViewById(R.id.layoutCustomDays);
        com.google.android.material.chip.ChipGroup chipGroup = v.findViewById(R.id.chipGroupDays);
        
        String[] frequencies = {"Diário", "Semanal", "Mensal", "Anual", "Personalizado"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, frequencies);
        dropdown.setAdapter(adapter);

        com.google.android.material.chip.Chip[] chips = {
            v.findViewById(R.id.chipDom), v.findViewById(R.id.chipSeg), v.findViewById(R.id.chipTer),
            v.findViewById(R.id.chipQua), v.findViewById(R.id.chipQui), v.findViewById(R.id.chipSex), v.findViewById(R.id.chipSab)
        };

        if (recurrenceType > 0) {
            sw.setChecked(true);
            options.setVisibility(View.VISIBLE);
            dropdown.setText(frequencies[Math.min(recurrenceType - 1, 4)], false);
            if (recurrenceType == 5) {
                customLayout.setVisibility(View.VISIBLE);
                for (int i = 0; i < 7; i++) {
                    if ((recurrenceDays & (1 << i)) != 0) chips[i].setChecked(true);
                }
            }
        }

        sw.setOnCheckedChangeListener((bv, checked) -> {
            options.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && dropdown.getText().toString().isEmpty()) {
                dropdown.setText(frequencies[0], false);
            }
        });

        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            customLayout.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
        });

        new MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton("Definir", (d, w) -> {
                reminderTime = cal.getTimeInMillis();
                originalReminderTime = reminderTime;
                if (sw.isChecked()) {
                    String selected = dropdown.getText().toString();
                    if (selected.equals(frequencies[0])) recurrenceType = 1;
                    else if (selected.equals(frequencies[1])) recurrenceType = 2;
                    else if (selected.equals(frequencies[2])) recurrenceType = 3;
                    else if (selected.equals(frequencies[3])) recurrenceType = 4;
                    else if (selected.equals(frequencies[4])) {
                        recurrenceType = 5;
                        recurrenceDays = 0;
                        for (int i = 0; i < 7; i++) {
                            if (chips[i].isChecked()) recurrenceDays |= (1 << i);
                        }
                        if (recurrenceDays == 0) recurrenceType = 1;
                    }
                } else {
                    recurrenceType = 0;
                    recurrenceDays = 0;
                }
                
                alertType = type;
                updateDate();
                Toast.makeText(this, "Agendado!", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Remover", (d, w) -> {
                if (noteId != -1) AlarmReceiver.cancelAlarm(this, noteId);
                reminderTime = 0; originalReminderTime = 0; recurrenceType = 0; recurrenceDays = 0; updateDate();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void convertToChecklist() {
        String content = binding.editNoteText.getText().toString();
        if (content.trim().isEmpty()) { 
            Toast.makeText(this, "Escreva algo para converter", Toast.LENGTH_SHORT).show(); 
            return; 
        }

        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) { 
            if (!line.trim().isEmpty()) sb.append(line.trim()).append("::0\n"); 
        }

        String title = binding.etTitle.getText().toString(); 
        if (title.isEmpty()) title = "Sem título";

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, sb.toString(), 1, selectedColor, 0, 0, null, reminderTime, recurrenceType, recurrenceDays, null, 0, alertType, System.currentTimeMillis(), originalReminderTime);
            noteId = (int) viewModel.addNote(currentNote); 
            currentNote.id = noteId;
        } else {
            currentNote.title = title; 
            currentNote.content = sb.toString(); 
            currentNote.type = 1; 
            currentNote.alertType = alertType;
            viewModel.updateNote(currentNote);
        }

        Intent intent = new Intent(this, ChecklistActivity.class); 
        intent.putExtra("NOTE_ID", currentNote.id);
        startActivity(intent); 
        finish();
    }

    private void saveNote() {
        String title = binding.etTitle.getText().toString();
        String content = binding.editNoteText.getText().toString();
        if (title.isEmpty() && content.isEmpty()) return;
        if (title.isEmpty()) title = "Sem título";
        
        String finalContent = content;
        if (currentNote != null && currentNote.isLocked == 1 && isUnlocked) {
            try {
                finalContent = SecurityCore.encrypt(content);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao criptografar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, finalContent, 0, selectedColor, 0, 0, null, reminderTime, recurrenceType, recurrenceDays, null, 0, alertType, System.currentTimeMillis(), originalReminderTime);
            noteId = (int) viewModel.addNote(currentNote); currentNote.id = noteId;
        } else {
            currentNote.title = title; currentNote.content = finalContent; currentNote.color = selectedColor;
            currentNote.reminderTime = reminderTime; currentNote.originalReminderTime = originalReminderTime;
            currentNote.recurrenceType = recurrenceType; currentNote.recurrenceDays = recurrenceDays;
            currentNote.alertType = alertType;
            viewModel.updateNote(currentNote);
        }
        
        if (reminderTime > System.currentTimeMillis()) {
            AlarmReceiver.rescheduleAlarm(this, currentNote);
        } else if (reminderTime == 0 && noteId != -1) {
            AlarmReceiver.cancelAlarm(this, noteId);
        }
    }

    @Override protected void onPause() { super.onPause(); saveNote(); }
}
