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
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.notara.databinding.ActivityEditBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditActivity extends AppCompatActivity {
    private ActivityEditBinding binding;
    private NoteViewModel viewModel;
    private int noteId = -1;
    private DatabaseHelper.Note currentNote;
    private int selectedColor = 0;
    private long alarmTime = 0;
    private long alarmOriginalReminderTime = 0;
    private int alarmRecurrenceType = 0;
    private int alarmRecurrenceDays = 0;
    private long reminderTime = 0;
    private long originalReminderTime = 0;
    private int recurrenceType = 0;
    private int recurrenceDays = 0;
    private SecurityManager securityManager;
    private boolean saved = false;

    public static final String[] noteColors = {
        "#FFEB3B", "#4DB6AC", "#FF9800", "#8BC34A", "#F44336", "#9C27B0", "#2196F3", "#E91E63"
    };

    private SettingsManager settings;
    private boolean isUnlocked = false;

    private boolean isPreviewMode = false;
    private android.view.GestureDetector gestureDetector;

    private List<DatabaseHelper.Attachment> attachmentList = new ArrayList<>();
    private AttachmentAdapter attachmentAdapter;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<String> audioPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new SettingsManager(this);
        securityManager = new SecurityManager(this);
        super.onCreate(savedInstanceState);

        int theme = settings.getTheme();
        WindowInsetsControllerCompat windowInsetsController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        if (theme == 0) {
            setTheme(R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(true);
        } else {
            setTheme(R.style.Theme_Notara);
            windowInsetsController.setAppearanceLightStatusBars(false);
        }
        
        isPreviewMode = getIntent().getBooleanExtra("PREVIEW_MODE", false);
        binding = ActivityEditBinding.inflate(getLayoutInflater());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isPreviewMode && isUnlocked) {
                    enablePreviewMode();
                } else {
                    finish();
                }
            }
        });

        setContentView(binding.getRoot());

        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) handleAttachmentPicked(uri, 0);
        });
        audioPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) handleAttachmentPicked(uri, 1);
        });

        // Configuração de modo preview
        if (isPreviewMode) {
            enablePreviewMode();
        }

        updateUIState();

        // Detector de duplo clique para editar
        gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                if (isPreviewMode) {
                    enableEditMode();
                    return true;
                }
                return false;
            }
        });

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        noteId = getIntent().getIntExtra("NOTE_ID", -1);

        if (noteId == -1) {
            reminderTime = getIntent().getLongExtra("INITIAL_REMINDER_TIME", 0);
            isUnlocked = true;
            enableEditMode();
        }

        if (noteId != -1) {
            currentNote = viewModel.getNote(noteId);
            if (currentNote != null) {
                binding.etTitle.setText(currentNote.title);

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
                recurrenceDays = currentNote.recurrenceDays;
                alarmTime = currentNote.alarmTime;
                alarmOriginalReminderTime = currentNote.alarmOriginalReminderTime;
                alarmRecurrenceType = currentNote.alarmRecurrenceType;
                alarmRecurrenceDays = currentNote.alarmRecurrenceDays;

                if (currentNote.isLocked == 1) {
                    lockContent();
                    requestUnlock();
                } else {
                    isUnlocked = true;
                }
                updateUIState();
            }
        }

        requestPermissions();
        updateColorIndicator();
        updateDate();
        setupAttachments();
        setupListeners();
        setupKeyboardListener();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        gestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void setupKeyboardListener() {
        View root = binding.getRoot();
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (isPreviewMode || !isUnlocked) return;
            android.graphics.Rect r = new android.graphics.Rect();
            root.getWindowVisibleDisplayFrame(r);
            int heightDiff = root.getRootView().getHeight() - (r.bottom - r.top);
            int threshold = (int) (200 * getResources().getDisplayMetrics().density);
            if (heightDiff > threshold) {
                binding.btnSave.setVisibility(View.GONE);
                binding.btnConvertFAB.setVisibility(View.GONE);
                binding.btnAttachFAB.setVisibility(View.GONE);
            } else {
                if (!isPreviewMode && isUnlocked) {
                    binding.btnSave.setVisibility(View.VISIBLE);
                    binding.btnConvertFAB.setVisibility(View.VISIBLE);
                    binding.btnAttachFAB.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void lockContent() {
        binding.etTitle.setVisibility(View.GONE);
        binding.tvDate.setVisibility(View.GONE);
        binding.editNoteText.setVisibility(View.GONE);
        binding.btnSave.setVisibility(View.GONE);
        binding.btnConvertFAB.setVisibility(View.GONE);
        binding.btnAttachFAB.setVisibility(View.GONE);
        binding.rvAttachments.setVisibility(View.GONE);
        binding.layoutNoteSchedule.setVisibility(View.GONE);
        binding.tvScheduleInfo.setVisibility(View.GONE);
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
        
        // Sempre exibe os campos após desbloqueio
        binding.etTitle.setVisibility(View.VISIBLE);
        binding.tvDate.setVisibility(View.VISIBLE);
        binding.editNoteText.setVisibility(View.VISIBLE);
        
        // Se for uma nota existente aberta da lista, entra em modo preview primeiro
        if (noteId != -1) {
            enablePreviewMode();
        } else {
            enableEditMode();
        }
    }

    private void updateUIState() {
        boolean shouldShowControls = !isPreviewMode && isUnlocked;
        
        binding.btnSave.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        binding.btnConvertFAB.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        binding.btnAttachFAB.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        boolean scheduleVisible = isUnlocked && (!isPreviewMode || reminderTime > 0 || alarmTime > 0);
        binding.layoutNoteSchedule.setVisibility(scheduleVisible ? View.VISIBLE : View.GONE);
        if (!scheduleVisible) binding.tvScheduleInfo.setVisibility(View.GONE);
        
        // Ensure text views are visible if unlocked
        if (isUnlocked) {
            binding.etTitle.setVisibility(View.VISIBLE);
            binding.tvDate.setVisibility(View.VISIBLE);
            binding.editNoteText.setVisibility(View.VISIBLE);
        }

        // Toggle focusability
        binding.etTitle.setFocusable(shouldShowControls);
        binding.etTitle.setFocusableInTouchMode(shouldShowControls);
        binding.editNoteText.setFocusable(shouldShowControls);
        binding.editNoteText.setFocusableInTouchMode(shouldShowControls);
        
        if (isPreviewMode) {
            hideKeyboard();
        }
        updateColorIndicator();
        updateNoteScheduleIndicators();
    }

    private void hideKeyboard() {
        android.view.View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void enablePreviewMode() {
        isPreviewMode = true;
        updateUIState();
    }

    private void enableEditMode() {
        isPreviewMode = false;
        updateUIState();
        binding.etTitle.requestFocus();
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

    private String getPeriodText(int hour) {
        if (hour >= 6 && hour < 12) return getString(R.string.period_morning);
        if (hour >= 12 && hour < 18) return getString(R.string.period_afternoon);
        if (hour >= 18) return getString(R.string.period_night);
        return getString(R.string.period_dawn);
    }

    private void updateDate() {
        Calendar now = Calendar.getInstance();
        Calendar modified = Calendar.getInstance();
        if (currentNote != null && currentNote.lastModified > 0) {
            modified.setTimeInMillis(currentNote.lastModified);
        } else {
            modified.setTimeInMillis(System.currentTimeMillis());
        }

        String pattern = (modified.get(Calendar.YEAR) == now.get(Calendar.YEAR)) ? "dd/MM" : "dd/MM/yyyy";
        String timeFormat = settings.is24HourFormat() ? "HH:mm" : "hh:mm a";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern + " 'às' " + timeFormat, Locale.getDefault());
        binding.tvDate.setText(getString(R.string.edited_at, sdf.format(modified.getTime())));
        binding.tvDate.setTextColor(Color.GRAY);
        binding.tvDate.setVisibility(View.VISIBLE);

        StringBuilder scheduleSb = new StringBuilder();
        if (reminderTime > 0) {
            Calendar reminder = Calendar.getInstance();
            reminder.setTimeInMillis(reminderTime);
            scheduleSb.append("🔔 Lembrete");
            String[] recurrences = {"", " (Diário)", " (Semanal)", " (Mensal)", " (Anual)", " (Pers.)"};
            if (recurrenceType > 0 && recurrenceType < recurrences.length) {
                scheduleSb.append(recurrences[recurrenceType]);
            }
            scheduleSb.append(": ");
            appendFormattedTime(scheduleSb, reminder, now);
        }

        if (alarmTime > 0) {
            if (scheduleSb.length() > 0) scheduleSb.append("\n");
            Calendar alarm = Calendar.getInstance();
            alarm.setTimeInMillis(alarmTime);
            scheduleSb.append("⏰ Alarme");
            String[] recurrences = {"", " (Diário)", " (Semanal)", " (Mensal)", " (Anual)", " (Pers.)"};
            if (alarmRecurrenceType > 0 && alarmRecurrenceType < recurrences.length) {
                scheduleSb.append(recurrences[alarmRecurrenceType]);
            }
            scheduleSb.append(": ");
            appendFormattedTime(scheduleSb, alarm, now);
        }

        if (scheduleSb.length() > 0) {
            binding.tvScheduleInfo.setText(scheduleSb.toString());
            binding.tvScheduleInfo.setTextColor(Color.parseColor("#4DB6AC"));
            binding.tvScheduleInfo.setVisibility(View.VISIBLE);
        } else {
            binding.tvScheduleInfo.setVisibility(View.GONE);
        }
    }

    private void appendFormattedTime(StringBuilder sb, Calendar cal, Calendar now) {
        String pattern = (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)) ? "dd/MM" : "dd/MM/yyyy";
        SimpleDateFormat dateSdf = new SimpleDateFormat(pattern, Locale.getDefault());
        if (settings.is24HourFormat()) {
            SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            sb.append(dateSdf.format(cal.getTime()));
            sb.append(" às ");
            sb.append(timeSdf.format(cal.getTime()));
        } else {
            int h12 = cal.get(Calendar.HOUR_OF_DAY) % 12;
            if (h12 == 0) h12 = 12;
            sb.append(dateSdf.format(cal.getTime()));
            sb.append(" às ");
            sb.append(String.format(Locale.getDefault(), "%d:%02d", h12, cal.get(Calendar.MINUTE)));
            sb.append(" ");
            sb.append(getPeriodText(cal.get(Calendar.HOUR_OF_DAY)));
        }
    }

    private void setupAttachments() {
        binding.rvAttachments.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        attachmentAdapter = new AttachmentAdapter(this, attachmentList, attachment -> {
            if (attachment.id > 0) {
                DatabaseHelper db = new DatabaseHelper(this);
                db.deleteAttachment(attachment.id);
            }
            AttachmentManager.deleteNoteAttachmentDir(this, noteId);
            attachmentList.remove(attachment);
            attachmentAdapter.setItems(attachmentList);
            updateAttachmentVisibility();
        });
        binding.rvAttachments.setAdapter(attachmentAdapter);

        if (noteId != -1) {
            DatabaseHelper db = new DatabaseHelper(this);
            attachmentList = db.getAttachments(noteId);
            attachmentAdapter.setItems(attachmentList);
        }
        updateAttachmentVisibility();
    }

    private void handleAttachmentPicked(Uri uri, int type) {
        try {
            if (noteId == -1) {
                currentNote = new DatabaseHelper.Note(-1, "", "", 0, selectedColor, 0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, 0);
                noteId = (int) viewModel.addNote(currentNote);
                currentNote.id = noteId;
            }
            DatabaseHelper.Attachment a = AttachmentManager.saveAttachmentFromUri(this, uri, noteId, type);
            DatabaseHelper db = new DatabaseHelper(this);
            db.insertAttachment(a);
            attachmentList = db.getAttachments(noteId);
            attachmentAdapter.setItems(attachmentList);
            updateAttachmentVisibility();
            Toast.makeText(this, "Anexo adicionado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao adicionar anexo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateAttachmentVisibility() {
        binding.rvAttachments.setVisibility(
                attachmentList != null && !attachmentList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupListeners() {
        binding.btnSave.setOnClickListener(v -> { saveNote(); finish(); });
        binding.btnConvertFAB.setOnClickListener(v -> convertToChecklist());
        binding.btnAttachFAB.setOnClickListener(v -> showAttachmentPicker());
        binding.btnReminder.setOnClickListener(v -> showReminderDialog());
        binding.btnAlarm.setOnClickListener(v -> showAlarmDialog());
        setupFoldClickListener();
    }

    private void showAttachmentPicker() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Adicionar Anexo")
            .setItems(new String[]{"Galeria", "Áudio"}, (d, w) -> {
                if (w == 0) imagePickerLauncher.launch("image/*");
                else audioPickerLauncher.launch("audio/*");
            })
            .show();
    }

    private void setupFoldClickListener() {
        binding.vFoldClick.setOnClickListener(v -> showColorPicker());
    }

    private void updateNoteScheduleIndicators() {
        int noteColor = Color.parseColor(noteColors[selectedColor % noteColors.length]);
        boolean reminderActive = reminderTime > 0;
        boolean alarmActive = alarmTime > 0;

        if (isPreviewMode) {
            binding.btnReminder.setVisibility(reminderActive ? View.VISIBLE : View.GONE);
            binding.btnAlarm.setVisibility(alarmActive ? View.VISIBLE : View.GONE);
        } else {
            binding.btnReminder.setVisibility(View.VISIBLE);
            binding.btnAlarm.setVisibility(View.VISIBLE);
        }

        binding.btnReminder.setImageResource(reminderActive ? R.drawable.ic_notifications_filled : R.drawable.ic_notifications);
        if (reminderActive) {
            binding.btnReminder.setColorFilter(noteColor);
            binding.btnReminder.setAlpha(1f);
        } else if (!isPreviewMode) {
            binding.btnReminder.setColorFilter(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 60));
            binding.btnReminder.setAlpha(0.5f);
        }

        binding.btnAlarm.setImageResource(alarmActive ? R.drawable.ic_alarm_filled : R.drawable.ic_alarm);
        if (alarmActive) {
            binding.btnAlarm.setColorFilter(noteColor);
            binding.btnAlarm.setAlpha(1f);
        } else if (!isPreviewMode) {
            binding.btnAlarm.setColorFilter(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 60));
            binding.btnAlarm.setAlpha(0.5f);
        }
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

                if (selectedColor == p) {
                    shape.setStroke(6, Color.WHITE);
                }

                colorView.setBackground(shape);
                colorView.setOnClickListener(v -> {
                    int pos = h.getBindingAdapterPosition();
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        selectedColor = pos;
                        updateColorIndicator();
                        dialog.dismiss();
                    }
                });
            }
            @Override public int getItemCount() { return noteColors.length; }
        });

        dialog.show();
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data | 0xFF000000;
    }

    private void updateColorIndicator() {
        int color = Color.parseColor(noteColors[selectedColor % noteColors.length]);
        binding.topColorIndicator.setVisibility(View.GONE);
        binding.btnSave.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        binding.btnConvertFAB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        binding.btnAttachFAB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        int fabIconColor = isColorDark(color) ? Color.WHITE : 0xFF1C1B1F;
        binding.btnSave.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));
        binding.btnConvertFAB.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));
        binding.btnAttachFAB.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));

        int currentTheme = settings.getTheme();
        boolean isDarkTheme = (currentTheme == 1);

        float density = getResources().getDisplayMetrics().density;
        int tintAlpha = (int) (255 * 0.3f);
        int tintColor = Color.argb(tintAlpha, Color.red(color), Color.green(color), Color.blue(color));
        float foldPx = (isPreviewMode ? 20 : 44) * density;
        BorderWithCornerFold borderDrawable = new BorderWithCornerFold(
                12 * density, 3 * density, foldPx, tintColor, color);
        borderDrawable.setFoldOutlineVisible(!isPreviewMode);
        binding.nestedScrollView.setBackground(borderDrawable);
        android.view.ViewGroup.LayoutParams lp = binding.vFoldClick.getLayoutParams();
        lp.width = (int) foldPx;
        lp.height = (int) foldPx;
        binding.vFoldClick.setLayoutParams(lp);

        binding.getRoot().setBackground(null);
        int textColor = isDarkTheme ? Color.WHITE : getThemeColor(android.R.attr.textColorPrimary);
        int subColor = isDarkTheme ? 0xFFE0E0E0 : getThemeColor(android.R.attr.textColorSecondary);
        binding.etTitle.setTextColor(textColor);
        binding.editNoteText.setTextColor(subColor);
        binding.editNoteText.setLineColor(color);

    }

    private boolean isColorDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance <= 0.5;
    }

    private void showReminderDialog() {
        if (!PermissionUtils.hasNotificationPermission(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("Permissão Necessária").setMessage("Ative as notificações para receber lembretes.").setPositiveButton("Configurações", (d, w) -> PermissionUtils.openNotificationSettings(this)).setNegativeButton("Agora não", null).show();
            return;
        }

        long defaultReminderTime = reminderTime > 0 ? reminderTime : (alarmTime > 0 ? alarmTime : 0);
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("1. Escolha a Data")
                .setSelection(defaultReminderTime > 0 ? defaultReminderTime : com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            java.time.LocalDate pickedDate = java.time.Instant.ofEpochMilli(selection)
                    .atZone(java.time.ZoneId.of("UTC"))
                    .toLocalDate();

            Calendar cal = Calendar.getInstance();
            cal.set(pickedDate.getYear(), pickedDate.getMonthValue() - 1, pickedDate.getDayOfMonth());

            int initialHour = 9, initialMinute = 0;
            if (defaultReminderTime > 0) {
                Calendar current = Calendar.getInstance(); current.setTimeInMillis(defaultReminderTime);
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

                showRecurrenceStep(cal, false);
            });

            timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showAlarmDialog() {
        if (!PermissionUtils.hasNotificationPermission(this)) {
            new MaterialAlertDialogBuilder(this).setTitle("Permissão Necessária").setMessage("Ative as notificações para receber alarmes.").setPositiveButton("Configurações", (d, w) -> PermissionUtils.openNotificationSettings(this)).setNegativeButton("Agora não", null).show();
            return;
        }

        long defaultAlarmTime = alarmTime > 0 ? alarmTime : (reminderTime > 0 ? reminderTime : 0);
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("1. Escolha a Data")
                .setSelection(defaultAlarmTime > 0 ? defaultAlarmTime : com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            java.time.LocalDate pickedDate = java.time.Instant.ofEpochMilli(selection)
                    .atZone(java.time.ZoneId.of("UTC"))
                    .toLocalDate();

            Calendar cal = Calendar.getInstance();
            cal.set(pickedDate.getYear(), pickedDate.getMonthValue() - 1, pickedDate.getDayOfMonth());

            int initialHour = 9, initialMinute = 0;
            if (defaultAlarmTime > 0) {
                Calendar current = Calendar.getInstance(); current.setTimeInMillis(defaultAlarmTime);
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

                showRecurrenceStep(cal, true);
            });

            timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showRecurrenceStep(Calendar cal, boolean isAlarm) {
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

        int existingRecurrenceType = isAlarm ? alarmRecurrenceType : recurrenceType;
        int existingRecurrenceDays = isAlarm ? alarmRecurrenceDays : recurrenceDays;

        if (existingRecurrenceType > 0) {
            sw.setChecked(true);
            options.setVisibility(View.VISIBLE);
            dropdown.setText(frequencies[Math.min(existingRecurrenceType - 1, 4)], false);
            if (existingRecurrenceType == 5) {
                customLayout.setVisibility(View.VISIBLE);
                for (int i = 0; i < 7; i++) {
                    if ((existingRecurrenceDays & (1 << i)) != 0) chips[i].setChecked(true);
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
                long time = cal.getTimeInMillis();
                if (isAlarm) {
                    alarmTime = time;
                    alarmOriginalReminderTime = time;
                    if (sw.isChecked()) {
                        String selected = dropdown.getText().toString();
                        if (selected.equals(frequencies[0])) alarmRecurrenceType = 1;
                        else if (selected.equals(frequencies[1])) alarmRecurrenceType = 2;
                        else if (selected.equals(frequencies[2])) alarmRecurrenceType = 3;
                        else if (selected.equals(frequencies[3])) alarmRecurrenceType = 4;
                        else if (selected.equals(frequencies[4])) {
                            alarmRecurrenceType = 5;
                            alarmRecurrenceDays = 0;
                            for (int i = 0; i < 7; i++) {
                                if (chips[i].isChecked()) alarmRecurrenceDays |= (1 << i);
                            }
                            if (alarmRecurrenceDays == 0) alarmRecurrenceType = 1;
                        }
                    } else {
                        alarmRecurrenceType = 0;
                        alarmRecurrenceDays = 0;
                    }
                } else {
                    reminderTime = time;
                    originalReminderTime = time;
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
                }

                updateDate();
                updateNoteScheduleIndicators();
                Toast.makeText(this, "Agendado!", Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Remover", (d, w) -> {
                if (noteId != -1) {
                    if (isAlarm) {
                        AlarmReceiver.cancelNoteAlarm(this, noteId);
                    } else {
                        AlarmReceiver.cancelAlarm(this, noteId);
                    }
                }
                if (isAlarm) {
                    alarmTime = 0; alarmOriginalReminderTime = 0; alarmRecurrenceType = 0; alarmRecurrenceDays = 0;
                } else {
                    reminderTime = 0; originalReminderTime = 0; recurrenceType = 0; recurrenceDays = 0;
                }
                updateDate();
                updateNoteScheduleIndicators();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void convertToChecklist() {
        String content = binding.editNoteText.getText().toString();

        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            if (!line.trim().isEmpty()) sb.append(line.trim()).append("::0\n");
        }

        String title = binding.etTitle.getText().toString();
        if (title.isEmpty()) title = DatabaseHelper.Note.extractTitle(sb.toString());

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, sb.toString(), 1, selectedColor, 0, 0, null, reminderTime, recurrenceType, recurrenceDays, null, 0, 0, System.currentTimeMillis(), originalReminderTime, alarmTime, alarmRecurrenceType, alarmRecurrenceDays, alarmOriginalReminderTime);
            noteId = (int) viewModel.addNote(currentNote);
            currentNote.id = noteId;
        } else {
            currentNote.title = title;
            currentNote.content = sb.toString();
            currentNote.type = 1;
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
        if (title.isEmpty() && content.isEmpty() && (attachmentList == null || attachmentList.isEmpty())) {
            Toast.makeText(this, "Nota vazia, nada foi salvo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) title = DatabaseHelper.Note.extractTitle(content);

        String finalContent = content;
        if (currentNote != null && currentNote.isLocked == 1 && isUnlocked) {
            try {
                finalContent = SecurityCore.encrypt(content);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao criptografar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, finalContent, 0, selectedColor, 0, 0, null, reminderTime, recurrenceType, recurrenceDays, null, 0, 0, System.currentTimeMillis(), originalReminderTime, alarmTime, alarmRecurrenceType, alarmRecurrenceDays, alarmOriginalReminderTime);
            noteId = (int) viewModel.addNote(currentNote); currentNote.id = noteId;
        } else {
            currentNote.title = title; currentNote.content = finalContent; currentNote.color = selectedColor;
            currentNote.reminderTime = reminderTime; currentNote.originalReminderTime = originalReminderTime;
            currentNote.recurrenceType = recurrenceType; currentNote.recurrenceDays = recurrenceDays;
            currentNote.type = 0;
            currentNote.alarmTime = alarmTime; currentNote.alarmOriginalReminderTime = alarmOriginalReminderTime;
            currentNote.alarmRecurrenceType = alarmRecurrenceType; currentNote.alarmRecurrenceDays = alarmRecurrenceDays;
            viewModel.updateNote(currentNote);
        }

        if (reminderTime > System.currentTimeMillis()) {
            AlarmReceiver.rescheduleAlarm(this, currentNote);
        } else if (noteId != -1) {
            AlarmReceiver.cancelAlarm(this, noteId);
        }

        if (alarmTime > System.currentTimeMillis()) {
            AlarmReceiver.scheduleNoteAlarm(this, currentNote);
        } else if (noteId != -1) {
            AlarmReceiver.cancelNoteAlarm(this, noteId);
        }

        saved = true;
        Toast.makeText(this, "Nota salva", Toast.LENGTH_SHORT).show();
    }

    @Override protected void onPause() { super.onPause(); if (!saved) saveNote(); }
}
