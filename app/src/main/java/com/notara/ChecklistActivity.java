package com.notara;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.DatePicker;
import android.widget.GridView;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.notara.databinding.ActivityChecklistBinding;
import com.notara.databinding.ItemChecklistBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChecklistActivity extends AppCompatActivity {
    private ActivityChecklistBinding binding;
    private NoteViewModel viewModel;
    private List<CheckItem> items = new ArrayList<>();
    private CheckAdapter adapter;
    private int noteId = -1;
    private int selectedColor = 0;
    private long reminderTime = 0;
    private long originalReminderTime = 0;
    private int recurrenceType = 0;
    private int alertType = 0;
    private DatabaseHelper.Note currentNote;
    private SecurityManager securityManager;

    private SettingsManager settings;
    private boolean isUnlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        securityManager = new SecurityManager(this);
        super.onCreate(savedInstanceState);
        // Aplica o tema
        int theme = settings.getTheme();
        if (theme == 0) { // Apenas Light força ícones pretos
            setTheme(R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
        }

        binding = ActivityChecklistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        noteId = getIntent().getIntExtra("NOTE_ID", -1);
        
        // Se for uma nova checklist vinda do calendário, pega o tempo sugerido
        if (noteId == -1) {
            reminderTime = getIntent().getLongExtra("INITIAL_REMINDER_TIME", 0);
        }

        if (noteId != -1) {
            currentNote = viewModel.getNote(noteId);
            if (currentNote != null) {
                binding.etChecklistTitle.setText(currentNote.title);
                selectedColor = currentNote.color;
                reminderTime = currentNote.reminderTime;
                originalReminderTime = currentNote.originalReminderTime;
                recurrenceType = currentNote.recurrenceType;
                alertType = currentNote.alertType;
                parseContent(currentNote.content);
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
        setupDate();
        setupRecyclerView();
        setupListeners();
    }

    private void lockContent() {
        binding.rvChecklist.setVisibility(View.GONE);
        binding.tilNewItem.setVisibility(View.GONE);
        binding.btnChecklistColorPicker.setVisibility(View.GONE);
        binding.btnChecklistReminder.setVisibility(View.GONE);
        binding.btnChecklistAlarm.setVisibility(View.GONE);
        binding.btnConvertToText.setVisibility(View.GONE);
        binding.btnSaveChecklist.setVisibility(View.GONE);
    }

    private void unlockContent() {
        isUnlocked = true;
        if (currentNote != null && currentNote.isLocked == 1) {
            try {
                String decrypted = SecurityCore.decrypt(currentNote.content);
                parseContent(decrypted);
                adapter.notifyDataSetChanged();
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao descriptografar lista.", Toast.LENGTH_SHORT).show();
            }
        }
        binding.rvChecklist.setVisibility(View.VISIBLE);
        binding.tilNewItem.setVisibility(View.VISIBLE);
        binding.btnChecklistColorPicker.setVisibility(View.VISIBLE);
        binding.btnChecklistReminder.setVisibility(View.VISIBLE);
        binding.btnChecklistAlarm.setVisibility(View.VISIBLE);
        binding.btnConvertToText.setVisibility(View.VISIBLE);
        binding.btnSaveChecklist.setVisibility(View.VISIBLE);
    }

    private void requestUnlock() {
        securityManager.authenticate(this, 
            "Lista Trancada", 
            "Autentique-se para ver o conteúdo", 
            new SecurityManager.AuthCallback() {
                @Override
                public void onAuthenticated() {
                    unlockContent();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(ChecklistActivity.this, "Acesso negado: " + error, Toast.LENGTH_SHORT).show();
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

    private void setupDate() {
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
            
            binding.tvChecklistDate.setText(sb.toString());
            binding.tvChecklistDate.setTextColor(alertType == 1 ? Color.parseColor("#F44336") : Color.parseColor("#4DB6AC"));
        } else {
            // Apenas Última Modificação
            String pattern = (modified.get(Calendar.YEAR) == now.get(Calendar.YEAR)) ? "dd/MM" : "dd/MM/yyyy";
            String timeFormat = settings.is24HourFormat() ? "HH:mm" : "hh:mm a";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern + " 'às' " + timeFormat, Locale.getDefault());
            binding.tvChecklistDate.setText("Editado em " + sdf.format(modified.getTime()));
            binding.tvChecklistDate.setTextColor(Color.GRAY);
        }
    }

    private void setupRecyclerView() {
        binding.rvChecklist.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CheckAdapter();
        binding.rvChecklist.setAdapter(adapter);

        // Reintegração do Drag & Drop (Segurar e Soltar para organizar)
        androidx.recyclerview.widget.ItemTouchHelper.Callback callback = new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {
            
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                    java.util.Collections.swap(items, fromPos, toPos);
                    adapter.notifyItemMoved(fromPos, toPos);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG) {
                    // Feedback visual agressivo: destaca o item "levitando" ele
                    viewHolder.itemView.setScaleX(1.05f);
                    viewHolder.itemView.setScaleY(1.05f);
                    
                    if (viewHolder.itemView instanceof com.google.android.material.card.MaterialCardView) {
                        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) viewHolder.itemView;
                        card.setCardBackgroundColor(Color.parseColor("#4DB6AC")); // Cor primária como destaque
                        card.setCardElevation(20f);
                        // Muda a cor dos textos internos para contraste
                        // (Opcional, mas melhora muito o feedback)
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setScaleX(1.0f);
                viewHolder.itemView.setScaleY(1.0f);
                
                if (viewHolder.itemView instanceof com.google.android.material.card.MaterialCardView) {
                    com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) viewHolder.itemView;
                    card.setCardBackgroundColor(Color.TRANSPARENT); // Volta ao normal
                    card.setCardElevation(0f);
                }
            }
        };

        androidx.recyclerview.widget.ItemTouchHelper touchHelper = new androidx.recyclerview.widget.ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(binding.rvChecklist);
    }

    private void setupListeners() {
        binding.tilNewItem.setEndIconOnClickListener(v -> addNewItem());
        binding.etNewItem.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { addNewItem(); return true; }
            return false;
        });
        binding.btnSaveChecklist.setOnClickListener(v -> { save(); finish(); });
        binding.btnChecklistColorPicker.setOnClickListener(v -> showColorPicker());
        binding.btnChecklistAlarm.setOnClickListener(v -> showReminderDialog(1));
        binding.btnChecklistReminder.setOnClickListener(v -> showReminderDialog(0));
        binding.btnConvertToText.setOnClickListener(v -> convertToText());
    }

    private void convertToText() {
        StringBuilder sb = new StringBuilder();
        for (CheckItem i : items) sb.append(i.name).append("\n");
        save();
        Intent intent = new Intent(this, EditActivity.class);
        intent.putExtra("NOTE_ID", noteId);
        intent.putExtra("CONVERT_CONTENT", sb.toString());
        startActivity(intent);
        finish();
    }

    private void showReminderDialog(int type) {
        if (!PermissionUtils.hasNotificationPermission(this)) {
            PermissionUtils.openNotificationSettings(this);
            return;
        }

        // PASSO 1: Data
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

        // PASSO 2: Hora
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
                setupDate();
            })
            .setNeutralButton("Remover", (d, w) -> {
                if (noteId != -1) AlarmReceiver.cancelAlarm(this, noteId);
                reminderTime = 0; originalReminderTime = 0; recurrenceType = 0; recurrenceDays = 0; setupDate();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void addNewItem() {
        String text = binding.etNewItem.getText().toString();
        if (!text.trim().isEmpty()) {
            items.add(new CheckItem(text.trim(), false));
            adapter.notifyItemInserted(items.size() - 1);
            binding.rvChecklist.scrollToPosition(items.size() - 1);
            binding.etNewItem.setText("");
        }
    }

    private void parseContent(String content) {
        items.clear();
        if (content != null && !content.isEmpty()) {
            for (String s : content.split("\n")) {
                if (s.contains("::")) {
                    String[] parts = s.split("::");
                    if (parts.length >= 2) items.add(new CheckItem(parts[0], parts[1].equals("1")));
                } else if (!s.trim().isEmpty()) items.add(new CheckItem(s.trim(), false));
            }
        }
    }

    private void save() {
        String title = binding.etChecklistTitle.getText().toString();
        StringBuilder sb = new StringBuilder();
        for (CheckItem i : items) sb.append(i.name).append("::").append(i.checked ? "1" : "0").append("\n");
        if (title.isEmpty() && items.isEmpty()) return;
        if (title.isEmpty()) title = "Sem título";

        String finalContent = sb.toString();
        if (currentNote != null && currentNote.isLocked == 1 && isUnlocked) {
            try {
                finalContent = SecurityCore.encrypt(finalContent);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao criptografar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, finalContent, 1, selectedColor, 0, 0, null, reminderTime, recurrenceType, recurrenceDays, null, 0, alertType, System.currentTimeMillis(), originalReminderTime);
            noteId = (int) viewModel.addNote(currentNote); currentNote.id = noteId;
        } else {
            currentNote.title = title; currentNote.content = finalContent; currentNote.color = selectedColor;
            currentNote.reminderTime = reminderTime; currentNote.originalReminderTime = originalReminderTime;
            currentNote.recurrenceType = recurrenceType; currentNote.recurrenceDays = recurrenceDays;
            currentNote.alertType = alertType; currentNote.type = 1;
            viewModel.updateNote(currentNote);
        }
        if (reminderTime > System.currentTimeMillis()) AlarmReceiver.rescheduleAlarm(this, currentNote);
        else if (reminderTime == 0 && noteId != -1) AlarmReceiver.cancelAlarm(this, noteId);
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
                int color = Color.parseColor(EditActivity.noteColors[p]);
                
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                shape.setColor(color);
                
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
            @Override public int getItemCount() { return EditActivity.noteColors.length; }
        });
        
        dialog.show();
    }

    private void updateColorIndicator() {
        int color = Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]);
        binding.topColorIndicator.setBackgroundColor(color);
        binding.btnSaveChecklist.setBackgroundColor(color);
        
        // Atualiza o pequeno círculo de preview na barra inferior com borda sofisticada
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setStroke(4, Color.WHITE);
        binding.viewSelectedColor.setBackground(shape);
    }

    @Override
    public boolean onSupportNavigateUp() {
        save();
        finish();
        return true;
    }

    @Override protected void onPause() { super.onPause(); save(); }

    static class CheckItem { String name; boolean checked; CheckItem(String n, boolean c) { name = n; checked = c; } }

    class CheckAdapter extends RecyclerView.Adapter<CheckAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) { return new VH(ItemChecklistBinding.inflate(LayoutInflater.from(p.getContext()), p, false)); }
        
        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            CheckItem i = items.get(p);
            
            h.binding.cbItem.setOnCheckedChangeListener(null);
            if (h.watcher != null) h.binding.etItemName.removeTextChangedListener(h.watcher);

            h.binding.cbItem.setChecked(i.checked);
            
            // Define o texto e aplica o efeito logo no início
            applyTextWithEffect(h, i.name, i.checked);

            h.watcher = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { 
                    i.name = s.toString();
                    // Re-aplica o efeito se necessário enquanto digita, sem disparar recursão
                    if (i.checked) {
                        applyStrikethroughWhileTyping(h, s);
                    }
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            };
            h.binding.etItemName.addTextChangedListener(h.watcher);

            h.binding.cbItem.setOnCheckedChangeListener((bv, checked) -> {
                i.checked = checked;
                applyTextWithEffect(h, i.name, checked);
            });
            
            h.binding.btnRemoveItem.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    items.remove(pos);
                    notifyItemRemoved(pos);
                }
            });
        }

        private void applyTextWithEffect(VH h, String text, boolean checked) {
            if (h.watcher != null) h.binding.etItemName.removeTextChangedListener(h.watcher);
            
            if (checked && text != null && !text.isEmpty()) {
                SpannableString spannable = new SpannableString(text);
                spannable.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                h.binding.etItemName.setText(spannable);
                h.binding.etItemName.setAlpha(0.5f);
                h.binding.etItemName.setPaintFlags(h.binding.etItemName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            } else {
                h.binding.etItemName.setText(text);
                h.binding.etItemName.setAlpha(1.0f);
                h.binding.etItemName.setPaintFlags(h.binding.etItemName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            }
            
            if (h.watcher != null) h.binding.etItemName.addTextChangedListener(h.watcher);
        }

        private void applyStrikethroughWhileTyping(VH h, CharSequence s) {
            // Evita disparar o watcher novamente
            h.binding.etItemName.removeTextChangedListener(h.watcher);
            
            int selectionStart = h.binding.etItemName.getSelectionStart();
            int selectionEnd = h.binding.etItemName.getSelectionEnd();
            
            SpannableString spannable = new SpannableString(s);
            spannable.setSpan(new StrikethroughSpan(), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.binding.etItemName.setText(spannable);
            
            // Restaura a posição do cursor
            h.binding.etItemName.setSelection(selectionStart, selectionEnd);
            
            h.binding.etItemName.addTextChangedListener(h.watcher);
        }

        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder { ItemChecklistBinding binding; android.text.TextWatcher watcher; VH(ItemChecklistBinding binding) { super(binding.getRoot()); this.binding = binding; } }
    }
}
