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
import android.annotation.SuppressLint;
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
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
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
import java.util.UUID;

public class ChecklistActivity extends AppCompatActivity {
    private ActivityChecklistBinding binding;
    private NoteViewModel viewModel;
    private List<DatabaseHelper.ChecklistItem> items = new ArrayList<>();
    private CheckAdapter adapter;
    private int noteId = -1;
    private int selectedColor = 0;
    private DatabaseHelper.Note currentNote;
    private SecurityManager securityManager;

    private long lastAddTime = 0;
    private boolean saved = false;

    private SettingsManager settings;
    private boolean isUnlocked = false;
    private boolean isPreviewMode = false;
    private android.view.GestureDetector gestureDetector;

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

        binding = ActivityChecklistBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupKeyboardListener();

        isPreviewMode = getIntent().getBooleanExtra("PREVIEW_MODE", false);

        // Detector de duplo clique
        gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(android.view.MotionEvent e) {
                return true;
            }
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                enableEditMode();
                return true;
            }
        });

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        noteId = getIntent().getIntExtra("NOTE_ID", -1);

        if (noteId == -1) {
            isUnlocked = true;
            enableEditMode();
        } else {
            currentNote = viewModel.getNote(noteId);
            if (currentNote != null) {
                binding.etChecklistTitle.setText(currentNote.title);
                selectedColor = currentNote.color;
                loadItems();
                if (currentNote.isLocked == 1) {
                    lockContent();
                    requestUnlock();
                } else {
                    isUnlocked = true;
                    enablePreviewMode();
                }
            }
        }

        binding.getRoot().post(() -> {
            if (isPreviewMode) hideKeyboard();
        });

        requestPermissions();
        updateColorIndicator();
        setupDate();
        setupRecyclerView();
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
        setupListeners();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (isPreviewMode && gestureDetector.onTouchEvent(ev)) {
            return true;
        }
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
                binding.btnSaveChecklist.setVisibility(View.GONE);
                binding.btnConvertFAB.setVisibility(View.GONE);
            } else {
                if (!isPreviewMode && isUnlocked) {
                    binding.btnSaveChecklist.setVisibility(View.VISIBLE);
                    binding.btnConvertFAB.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void lockContent() {
        binding.rvChecklist.setVisibility(View.GONE);
        binding.tilNewItem.setVisibility(View.GONE);
        binding.layoutNoteSchedule.setVisibility(View.GONE);
        binding.btnConvertFAB.setVisibility(View.GONE);
        binding.btnSaveChecklist.setVisibility(View.GONE);
    }

    private void unlockContent() {
        isUnlocked = true;
        if (currentNote != null && currentNote.isLocked == 1) {
            try {
                String decrypted = SecurityCore.decrypt(currentNote.content);
                parseContent(decrypted);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao descriptografar lista.", Toast.LENGTH_SHORT).show();
            }
        }
        
        if (noteId != -1) {
            enablePreviewMode();
        } else {
            enableEditMode();
        }
    }

    private void updateUIState() {
        boolean shouldShowControls = !isPreviewMode && isUnlocked;
        
        binding.btnSaveChecklist.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        binding.btnConvertFAB.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        binding.tilNewItem.setVisibility(shouldShowControls ? View.VISIBLE : View.GONE);
        binding.layoutNoteSchedule.setVisibility(
                isUnlocked && (!isPreviewMode || (currentNote != null && currentNote.reminderTime > 0))
                ? View.VISIBLE : View.GONE);
        
        if (isUnlocked) {
            binding.rvChecklist.setVisibility(View.VISIBLE);
            binding.etChecklistTitle.setVisibility(View.VISIBLE);
            
            updateEmptyView();
        }

        binding.etChecklistTitle.setFocusable(shouldShowControls);
        binding.etChecklistTitle.setFocusableInTouchMode(shouldShowControls);
        
        if (isPreviewMode) {
            hideKeyboard();
        }

        updateColorIndicator();
        updateNoteScheduleIndicators();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void updateEmptyView() {
        if (isUnlocked && isPreviewMode) {
            binding.tvEmptyHint.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        } else {
            binding.tvEmptyHint.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
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
        binding.etChecklistTitle.requestFocus();
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

    private void setupDate() {
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
        binding.tvChecklistDate.setText(getString(R.string.edited_at, sdf.format(modified.getTime())));
        binding.tvChecklistDate.setTextColor(Color.GRAY);
    }

    private void setupRecyclerView() {
        binding.rvChecklist.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CheckAdapter();
        binding.rvChecklist.setAdapter(adapter);

        androidx.recyclerview.widget.ItemTouchHelper.Callback callback = new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN, 0) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
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
                    viewHolder.itemView.setScaleX(1.05f);
                    viewHolder.itemView.setScaleY(1.05f);

                    if (viewHolder.itemView instanceof com.google.android.material.card.MaterialCardView) {
                        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) viewHolder.itemView;
                        card.setCardBackgroundColor(Color.parseColor("#4DB6AC"));
                        card.setCardElevation(20f);
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
                    card.setCardBackgroundColor(Color.TRANSPARENT);
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
        setupFoldClickListener();
        binding.btnChecklistReminder.setOnClickListener(v -> showNoteScheduleDialog());
        binding.btnChecklistAlarm.setOnClickListener(v -> showNoteScheduleDialog());
        binding.btnConvertFAB.setOnClickListener(v -> convertToText());
    }

    private void convertToText() {
        StringBuilder sb = new StringBuilder();
        for (DatabaseHelper.ChecklistItem i : items) sb.append(i.name).append("\n");
        save();
        Intent intent = new Intent(this, EditActivity.class);
        intent.putExtra("NOTE_ID", noteId);
        intent.putExtra("CONVERT_CONTENT", sb.toString());
        startActivity(intent);
        finish();
    }

    private void addNewItem() {
        long now = System.currentTimeMillis();
        if (now - lastAddTime < 500) return;
        lastAddTime = now;
        String text = binding.etNewItem.getText().toString();
        if (!text.trim().isEmpty()) {
            DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
            item.name = text.trim();
            item.checked = false;
            item.position = items.size();
            items.add(item);
            adapter.notifyItemInserted(items.size() - 1);
            binding.rvChecklist.scrollToPosition(items.size() - 1);
            binding.etNewItem.setText("");
            updateEmptyView();
            updateUIState();
        }
    }

    private void loadItems() {
        items.clear();
        if (currentNote == null || noteId == -1) return;
        if (currentNote.isLocked == 1) {
            if (currentNote.content != null && !currentNote.content.isEmpty()) {
                try {
                    String decrypted = SecurityCore.decrypt(currentNote.content);
                    parseContent(decrypted);
                } catch (Exception ignored) {}
            }
            return;
        }
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        List<DatabaseHelper.ChecklistItem> dbItems = dbHelper.getChecklistItems(noteId);
        if (!dbItems.isEmpty()) {
            items.addAll(dbItems);
        } else if (currentNote.content != null && !currentNote.content.isEmpty()) {
            parseContent(currentNote.content);
            saveItemsToTable();
        }
        updateEmptyView();
        updateUIState();
    }

    private void saveItemsToTable() {
        if (currentNote == null || currentNote.isLocked == 1 || noteId == -1) return;
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        dbHelper.deleteChecklistItemsByNoteId(noteId);
        for (int i = 0; i < items.size(); i++) {
            DatabaseHelper.ChecklistItem item = items.get(i);
            item.noteId = noteId;
            item.position = i;
            dbHelper.insertChecklistItem(item);
        }
    }

    private void showItemScheduleDialog(DatabaseHelper.ChecklistItem item) {
        class Option {
            final int icon1, icon2;
            final String text;
            final boolean isRemove;
            Option(int icon1, int icon2, String text, boolean isRemove) {
                this.icon1 = icon1; this.icon2 = icon2;
                this.text = text; this.isRemove = isRemove;
            }
        }
        java.util.List<Option> opts = new java.util.ArrayList<>();
        opts.add(new Option(R.drawable.ic_notifications, 0, "Lembrete", false));
        opts.add(new Option(R.drawable.ic_alarm, 0, "Alarme", false));
        opts.add(new Option(R.drawable.ic_notifications, R.drawable.ic_alarm, "Ambos", false));
        if (item.reminderTime > 0) {
            opts.add(new Option(R.drawable.ic_close, 0, "Remover Agendamento", true));
        }
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return opts.size(); }
            @Override public Object getItem(int p) { return opts.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View convert, ViewGroup parent) {
                if (convert == null) {
                    convert = getLayoutInflater().inflate(R.layout.item_schedule_option, parent, false);
                }
                Option o = opts.get(p);
                android.widget.ImageView iv1 = (android.widget.ImageView) convert.findViewById(R.id.ivIcon1);
                android.widget.ImageView iv2 = (android.widget.ImageView) convert.findViewById(R.id.ivIcon2);
                iv1.setImageResource(o.icon1);
                iv1.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(
                        Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]),
                        o.isRemove ? 255 : 200));
                if (o.icon2 != 0) {
                    iv2.setVisibility(View.VISIBLE);
                    iv2.setImageResource(o.icon2);
                    iv2.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(
                            Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]), 200));
                } else {
                    iv2.setVisibility(View.GONE);
                }
                ((android.widget.TextView) convert.findViewById(R.id.tvText)).setText(o.text);
                return convert;
            }
        });
        new MaterialAlertDialogBuilder(this)
            .setTitle("Agendar: " + item.name)
            .setView(lv)
            .setPositiveButton("Cancelar", null)
            .show();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            if (pos == 0) pickDateTime(item, 0);
            else if (pos == 1) pickDateTime(item, 1);
            else if (pos == 2) pickDateTime(item, 2);
            else removeItemSchedule(item);
        });
    }

    private void pickDateTime(DatabaseHelper.ChecklistItem item, int type) {
        com.google.android.material.datepicker.MaterialDatePicker<Long> datePicker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("1. Escolha a Data")
                .setSelection(item.reminderTime > 0 ? item.reminderTime : com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(selection);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            Calendar localCal = Calendar.getInstance();
            localCal.set(year, month, day);
            int initialHour = 9, initialMinute = 0;
            if (item.reminderTime > 0) {
                Calendar current = Calendar.getInstance();
                current.setTimeInMillis(item.reminderTime);
                initialHour = current.get(Calendar.HOUR_OF_DAY);
                initialMinute = current.get(Calendar.MINUTE);
            }
            com.google.android.material.timepicker.MaterialTimePicker timePicker = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                    .setTimeFormat(settings.is24HourFormat() ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                    .setHour(initialHour)
                    .setMinute(initialMinute)
                    .setTitleText("2. Escolha o Horário")
                    .build();
            timePicker.addOnPositiveButtonClickListener(v -> {
                localCal.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                localCal.set(Calendar.MINUTE, timePicker.getMinute());
                localCal.set(Calendar.SECOND, 0);
                showRecurrenceStep(item, localCal, type);
            });
            timePicker.show(getSupportFragmentManager(), "TIME_PICKER");
        });
        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    private void showRecurrenceStep(DatabaseHelper.ChecklistItem item, Calendar cal, int type) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_step_recurrence, null);
        com.google.android.material.switchmaterial.SwitchMaterial sw = v.findViewById(R.id.switchRecurrenceToggle);
        View options = v.findViewById(R.id.layoutRecurrenceOptions);
        AutoCompleteTextView dropdown = v.findViewById(R.id.dropdownRecurrenceType);
        View customLayout = v.findViewById(R.id.layoutCustomDays);
        com.google.android.material.chip.ChipGroup chipGroup = v.findViewById(R.id.chipGroupDays);

        String[] frequencies = {"Diário", "Semanal", "Mensal", "Anual", "Personalizado"};
        android.widget.ArrayAdapter<String> adapterRec = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, frequencies);
        dropdown.setAdapter(adapterRec);

        com.google.android.material.chip.Chip[] chips = {
            v.findViewById(R.id.chipDom), v.findViewById(R.id.chipSeg), v.findViewById(R.id.chipTer),
            v.findViewById(R.id.chipQua), v.findViewById(R.id.chipQui), v.findViewById(R.id.chipSex), v.findViewById(R.id.chipSab)
        };

        if (item.recurrenceType > 0) {
            sw.setChecked(true);
            options.setVisibility(View.VISIBLE);
            dropdown.setText(frequencies[Math.min(item.recurrenceType - 1, 4)], false);
            if (item.recurrenceType == 5) {
                customLayout.setVisibility(View.VISIBLE);
                for (int i = 0; i < 7; i++) {
                    if ((item.recurrenceDays & (1 << i)) != 0) chips[i].setChecked(true);
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
                item.reminderTime = cal.getTimeInMillis();
                item.originalReminderTime = item.reminderTime;
                if (sw.isChecked()) {
                    String selected = dropdown.getText().toString();
                    if (selected.equals(frequencies[0])) item.recurrenceType = 1;
                    else if (selected.equals(frequencies[1])) item.recurrenceType = 2;
                    else if (selected.equals(frequencies[2])) item.recurrenceType = 3;
                    else if (selected.equals(frequencies[3])) item.recurrenceType = 4;
                    else if (selected.equals(frequencies[4])) {
                        item.recurrenceType = 5;
                        item.recurrenceDays = 0;
                        for (int i = 0; i < 7; i++) {
                            if (chips[i].isChecked()) item.recurrenceDays |= (1 << i);
                        }
                        if (item.recurrenceDays == 0) item.recurrenceType = 1;
                    }
                } else {
                    item.recurrenceType = 0;
                    item.recurrenceDays = 0;
                }
                item.alertType = type;
                if (adapter != null) adapter.notifyDataSetChanged();
            })
            .setNeutralButton("Remover", (d, w) -> {
                removeItemSchedule(item);
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void removeItemSchedule(DatabaseHelper.ChecklistItem item) {
        item.reminderTime = 0;
        item.originalReminderTime = 0;
        item.recurrenceType = 0;
        item.recurrenceDays = 0;
        item.alertType = 0;
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void parseContent(String content) {
        items.clear();
        if (content != null && !content.isEmpty()) {
            int pos = 0;
            for (String s : content.split("\n")) {
                DatabaseHelper.ChecklistItem item = new DatabaseHelper.ChecklistItem();
                if (s.contains("::")) {
                    String[] parts = s.split("::");
                    item.name = parts.length >= 1 ? parts[0] : "";
                    item.checked = parts.length >= 2 && "1".equals(parts[1]);
                } else if (!s.trim().isEmpty()) {
                    item.name = s.trim();
                } else {
                    continue;
                }
                item.position = pos++;
                items.add(item);
            }
        }
        updateEmptyView();
        updateUIState();
    }

    private void save() {
        String title = binding.etChecklistTitle.getText().toString();
        StringBuilder sb = new StringBuilder();
        for (DatabaseHelper.ChecklistItem i : items) sb.append(i.name).append("::").append(i.checked ? "1" : "0").append("\n");
        if (title.isEmpty() && items.isEmpty()) {
            Toast.makeText(this, "Lista vazia, nada foi salvo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (title.isEmpty()) title = DatabaseHelper.Note.extractTitle(sb.toString());

        boolean wasLocked = currentNote != null && currentNote.isLocked == 1;
        String finalContent = sb.toString();
        if (wasLocked && isUnlocked) {
            try {
                finalContent = SecurityCore.encrypt(finalContent);
            } catch (Exception e) {
                Toast.makeText(this, "Erro ao criptografar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (currentNote == null) {
            currentNote = new DatabaseHelper.Note(-1, title, finalContent, 1, selectedColor, 0, 0, null, 0, 0, 0, null, 0, 0, System.currentTimeMillis(), 0);
            noteId = (int) viewModel.addNote(currentNote); currentNote.id = noteId;
        } else {
            currentNote.title = title; currentNote.content = finalContent; currentNote.color = selectedColor;
            currentNote.type = 1;
            viewModel.updateNote(currentNote);
        }

        // Save checklist items to table and schedule alarms
        if (!wasLocked) {
            DatabaseHelper dbHelper = new DatabaseHelper(this);
            // Cancel old item alarms before deleting
            java.util.List<DatabaseHelper.ChecklistItem> oldItems = dbHelper.getChecklistItems(noteId);
            for (DatabaseHelper.ChecklistItem old : oldItems) {
                AlarmReceiver.cancelItemAlarm(this, noteId, old.id);
            }
            dbHelper.deleteChecklistItemsByNoteId(noteId);
            for (int i = 0; i < items.size(); i++) {
                DatabaseHelper.ChecklistItem item = items.get(i);
                item.noteId = noteId;
                item.position = i;
                dbHelper.insertChecklistItem(item);
                if (item.reminderTime > System.currentTimeMillis()) {
                    scheduleItemAlarm(item);
                } else if (item.reminderTime > 0) {
                    cancelItemAlarm(item);
                }
            }
        }

        // Schedule note-level alarm
        if (currentNote.reminderTime > System.currentTimeMillis()) {
            AlarmReceiver.rescheduleAlarm(this, currentNote);
        } else if (currentNote.reminderTime > 0) {
            AlarmReceiver.cancelAlarm(this, noteId);
        }

        saved = true;
        Toast.makeText(this, "Lista salva", Toast.LENGTH_SHORT).show();
    }

    private void scheduleItemAlarm(DatabaseHelper.ChecklistItem item) {
        AlarmReceiver.rescheduleItemAlarm(this, noteId, item);
    }

    private void cancelItemAlarm(DatabaseHelper.ChecklistItem item) {
        AlarmReceiver.cancelItemAlarm(this, noteId, item.id);
    }

    private void setupFoldClickListener() {
        binding.vFoldClick.setOnClickListener(v -> {
            if (!isPreviewMode && isUnlocked) showColorPicker();
        });
    }

    private void updateNoteScheduleIndicators() {
        int noteColor = Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]);
        boolean hasReminder = currentNote != null && currentNote.reminderTime > 0;
        int alert = currentNote != null ? currentNote.alertType : 0;

        if (hasReminder && (alert == 0 || alert == 2)) {
            binding.btnChecklistReminder.setColorFilter(noteColor);
            binding.btnChecklistReminder.setAlpha(1f);
        } else {
            binding.btnChecklistReminder.setColorFilter(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 60));
            binding.btnChecklistReminder.setAlpha(0.5f);
        }
        if (hasReminder && (alert == 1 || alert == 2)) {
            binding.btnChecklistAlarm.setColorFilter(noteColor);
            binding.btnChecklistAlarm.setAlpha(1f);
        } else {
            binding.btnChecklistAlarm.setColorFilter(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 60));
            binding.btnChecklistAlarm.setAlpha(0.5f);
        }
    }

    private void showNoteScheduleDialog() {
        if (currentNote == null) return;
        class Option {
            final int icon1, icon2; final String text; final int type;
            Option(int i1, int i2, String t, int ty) { icon1=i1; icon2=i2; text=t; type=ty; }
        }
        java.util.List<Option> opts = new java.util.ArrayList<>();
        opts.add(new Option(R.drawable.ic_notifications, 0, "Lembrete", 0));
        opts.add(new Option(R.drawable.ic_alarm, 0, "Alarme", 1));
        opts.add(new Option(R.drawable.ic_notifications, R.drawable.ic_alarm, "Ambos", 2));
        if (currentNote.reminderTime > 0) {
            opts.add(new Option(R.drawable.ic_close, 0, "Remover Agendamento", -1));
        }
        android.widget.ListView lv = new android.widget.ListView(this);
        lv.setAdapter(new android.widget.BaseAdapter() {
            @Override public int getCount() { return opts.size(); }
            @Override public Object getItem(int p) { return opts.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View convert, ViewGroup parent) {
                if (convert == null) convert = getLayoutInflater().inflate(R.layout.item_schedule_option, parent, false);
                Option o = opts.get(p);
                android.widget.ImageView iv1 = (android.widget.ImageView) convert.findViewById(R.id.ivIcon1);
                android.widget.ImageView iv2 = (android.widget.ImageView) convert.findViewById(R.id.ivIcon2);
                iv1.setImageResource(o.icon1);
                iv1.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(
                        Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]), 200));
                if (o.icon2 != 0) {
                    iv2.setVisibility(View.VISIBLE);
                    iv2.setImageResource(o.icon2);
                    iv2.setColorFilter(androidx.core.graphics.ColorUtils.setAlphaComponent(
                            Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]), 200));
                } else { iv2.setVisibility(View.GONE); }
                ((android.widget.TextView) convert.findViewById(R.id.tvText)).setText(o.text);
                return convert;
            }
        });
        new MaterialAlertDialogBuilder(this)
            .setTitle("Notificação da Lista")
            .setView(lv)
            .setPositiveButton("Cancelar", null)
            .show();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            Option o = opts.get(pos);
            if (o.type < 0) {
                currentNote.reminderTime = 0; currentNote.originalReminderTime = 0;
                currentNote.recurrenceType = 0; currentNote.recurrenceDays = 0;
                currentNote.alertType = 0;
                updateNoteScheduleIndicators();
            } else {
                pickNoteDateTime(o.type);
            }
        });
    }

    private void pickNoteDateTime(int type) {
        com.google.android.material.datepicker.MaterialDatePicker<Long> dp = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
                .setTitleText("1. Escolha a Data")
                .setSelection(currentNote.reminderTime > 0 ? currentNote.reminderTime : com.google.android.material.datepicker.MaterialDatePicker.todayInUtcMilliseconds())
                .build();
        dp.addOnPositiveButtonClickListener(selection -> {
            Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            cal.setTimeInMillis(selection);
            int year = cal.get(Calendar.YEAR), month = cal.get(Calendar.MONTH), day = cal.get(Calendar.DAY_OF_MONTH);
            Calendar localCal = Calendar.getInstance();
            localCal.set(year, month, day);
            int initH = 9, initM = 0;
            if (currentNote.reminderTime > 0) {
                Calendar c = Calendar.getInstance(); c.setTimeInMillis(currentNote.reminderTime);
                initH = c.get(Calendar.HOUR_OF_DAY); initM = c.get(Calendar.MINUTE);
            }
            com.google.android.material.timepicker.MaterialTimePicker tp = new com.google.android.material.timepicker.MaterialTimePicker.Builder()
                    .setTimeFormat(settings.is24HourFormat() ? com.google.android.material.timepicker.TimeFormat.CLOCK_24H : com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
                    .setHour(initH).setMinute(initM).setTitleText("2. Escolha o Horário").build();
            tp.addOnPositiveButtonClickListener(v -> {
                localCal.set(Calendar.HOUR_OF_DAY, tp.getHour());
                localCal.set(Calendar.MINUTE, tp.getMinute());
                localCal.set(Calendar.SECOND, 0);
                showNoteRecurrenceStep(localCal, type);
            });
            tp.show(getSupportFragmentManager(), "NOTE_TIME");
        });
        dp.show(getSupportFragmentManager(), "NOTE_DATE");
    }

    private void showNoteRecurrenceStep(Calendar cal, int type) {
        View v = getLayoutInflater().inflate(R.layout.dialog_step_recurrence, null);
        com.google.android.material.switchmaterial.SwitchMaterial sw = v.findViewById(R.id.switchRecurrenceToggle);
        View options = v.findViewById(R.id.layoutRecurrenceOptions);
        android.widget.AutoCompleteTextView dropdown = v.findViewById(R.id.dropdownRecurrenceType);
        View customLayout = v.findViewById(R.id.layoutCustomDays);
        com.google.android.material.chip.ChipGroup chipGroup = v.findViewById(R.id.chipGroupDays);
        String[] freqs = {"Diário", "Semanal", "Mensal", "Anual", "Personalizado"};
        android.widget.ArrayAdapter<String> aRec = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_list_item_1, freqs);
        dropdown.setAdapter(aRec);
        com.google.android.material.chip.Chip[] chips = {
            v.findViewById(R.id.chipDom), v.findViewById(R.id.chipSeg), v.findViewById(R.id.chipTer),
            v.findViewById(R.id.chipQua), v.findViewById(R.id.chipQui), v.findViewById(R.id.chipSex), v.findViewById(R.id.chipSab)
        };
        if (currentNote.recurrenceType > 0) {
            sw.setChecked(true); options.setVisibility(View.VISIBLE);
            dropdown.setText(freqs[Math.min(currentNote.recurrenceType - 1, 4)], false);
            if (currentNote.recurrenceType == 5) {
                customLayout.setVisibility(View.VISIBLE);
                for (int i = 0; i < 7; i++) if ((currentNote.recurrenceDays & (1 << i)) != 0) chips[i].setChecked(true);
            }
        }
        sw.setOnCheckedChangeListener((bv, checked) -> {
            options.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked && dropdown.getText().toString().isEmpty()) dropdown.setText(freqs[0], false);
        });
        dropdown.setOnItemClickListener((parent, view, position, id) -> customLayout.setVisibility(position == 4 ? View.VISIBLE : View.GONE));
        new MaterialAlertDialogBuilder(this)
            .setView(v)
            .setPositiveButton("Definir", (d, w) -> {
                currentNote.reminderTime = cal.getTimeInMillis();
                currentNote.originalReminderTime = currentNote.reminderTime;
                if (sw.isChecked()) {
                    String sel = dropdown.getText().toString();
                    if (sel.equals(freqs[0])) currentNote.recurrenceType = 1;
                    else if (sel.equals(freqs[1])) currentNote.recurrenceType = 2;
                    else if (sel.equals(freqs[2])) currentNote.recurrenceType = 3;
                    else if (sel.equals(freqs[3])) currentNote.recurrenceType = 4;
                    else if (sel.equals(freqs[4])) {
                        currentNote.recurrenceType = 5; currentNote.recurrenceDays = 0;
                        for (int i = 0; i < 7; i++) if (chips[i].isChecked()) currentNote.recurrenceDays |= (1 << i);
                        if (currentNote.recurrenceDays == 0) currentNote.recurrenceType = 1;
                    }
                } else { currentNote.recurrenceType = 0; currentNote.recurrenceDays = 0; }
                currentNote.alertType = type;
                updateNoteScheduleIndicators();
            })
            .setNeutralButton("Remover", (d, w) -> {
                currentNote.reminderTime = 0; currentNote.originalReminderTime = 0;
                currentNote.recurrenceType = 0; currentNote.recurrenceDays = 0;
                currentNote.alertType = 0;
                updateNoteScheduleIndicators();
            })
            .setNegativeButton("Cancelar", null)
            .show();
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
                    int pos = h.getBindingAdapterPosition();
                    if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        selectedColor = pos;
                        updateColorIndicator();
                        dialog.dismiss();
                    }
                });
            }
            @Override public int getItemCount() { return EditActivity.noteColors.length; }
        });

        dialog.show();
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data | 0xFF000000;
    }

    private void updateColorIndicator() {
        int color = Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]);
        binding.topColorIndicator.setVisibility(View.GONE);
        binding.btnSaveChecklist.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        binding.btnConvertFAB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        int fabIconColor = isColorDark(color) ? Color.WHITE : 0xFF1C1B1F;
        binding.btnSaveChecklist.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));
        binding.btnConvertFAB.setImageTintList(android.content.res.ColorStateList.valueOf(fabIconColor));

        int currentTheme = settings.getTheme();
        boolean isDarkTheme = (currentTheme == 1);

        float density = getResources().getDisplayMetrics().density;
        int tintAlpha = (int) (255 * 0.3f);
        int tintColor = Color.argb(tintAlpha, Color.red(color), Color.green(color), Color.blue(color));
        float foldPx = (isPreviewMode ? 20 : 44) * density;
        BorderWithCornerFold borderDrawable = new BorderWithCornerFold(
                12 * density, 3 * density, foldPx, tintColor, color);
        borderDrawable.setFoldOutlineVisible(!isPreviewMode);
        binding.contentContainer.setBackground(borderDrawable);
        android.view.ViewGroup.LayoutParams lp = binding.vFoldClick.getLayoutParams();
        lp.width = (int) foldPx;
        lp.height = (int) foldPx;
        binding.vFoldClick.setLayoutParams(lp);

        binding.getRoot().setBackground(null);
        int textColor = isDarkTheme ? Color.WHITE : getThemeColor(android.R.attr.textColorPrimary);
        binding.etChecklistTitle.setTextColor(textColor);
        binding.etNewItem.setTextColor(textColor);
        binding.etNewItem.setHintTextColor(isDarkTheme ? 0x80FFFFFF : getThemeColor(android.R.attr.textColorHint));

        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        shape.setColor(color);
        shape.setStroke(4, Color.WHITE);

        // Notifica o adapter para atualizar as cores dos itens se necessário
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private boolean isColorDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance <= 0.5;
    }

    @Override
    public boolean onSupportNavigateUp() {
        save();
        finish();
        return true;
    }

    @Override protected void onPause() { super.onPause(); if (!saved) save(); }

    class CheckAdapter extends RecyclerView.Adapter<CheckAdapter.VH> {
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) { return new VH(ItemChecklistBinding.inflate(LayoutInflater.from(p.getContext()), p, false)); }

        @SuppressLint("ClickableViewAccessibility")
        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            DatabaseHelper.ChecklistItem i = items.get(p);
            int noteColor = Color.parseColor(EditActivity.noteColors[selectedColor % EditActivity.noteColors.length]);

            h.binding.cbItem.setOnCheckedChangeListener(null);
            if (h.watcher != null) h.binding.etItemName.removeTextChangedListener(h.watcher);

            h.binding.cbItem.setChecked(i.checked);
            applyTextWithEffect(h, i.name, i.checked);

            int alphaColor = androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 38);
            h.binding.getRoot().setCardBackgroundColor(alphaColor);
            h.binding.getRoot().setStrokeColor(android.content.res.ColorStateList.valueOf(noteColor));

            int currentTheme = settings.getTheme();
            boolean isDarkTheme = (currentTheme == 1);
            int textColor = isDarkTheme ? Color.WHITE : getThemeColor(android.R.attr.textColorPrimary);
            h.binding.etItemName.setTextColor(textColor);
            h.binding.etItemName.setHintTextColor(isDarkTheme ? 0xFFE0E0E0 : getThemeColor(android.R.attr.textColorSecondary));

            int currentTextColor = h.binding.etItemName.getCurrentTextColor();
            int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
            };
            int[] colors = new int[] { noteColor, currentTextColor };
            h.binding.cbItem.setButtonTintList(new android.content.res.ColorStateList(states, colors));

            h.binding.etItemName.setFocusable(!isPreviewMode);
            h.binding.etItemName.setFocusableInTouchMode(!isPreviewMode);
            h.binding.btnRemoveItem.setVisibility(isPreviewMode ? View.GONE : View.VISIBLE);

            // Schedule info: time chip + icons
            boolean hasSchedule = i.reminderTime > 0;
            if (hasSchedule) {
                h.binding.tvScheduleTime.setText(formatScheduleTime(i.reminderTime));
                android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
                chipBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                chipBg.setCornerRadius(14f);
                chipBg.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 30));
                chipBg.setStroke(1, androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 120));
                h.binding.tvScheduleTime.setBackground(chipBg);
                h.binding.tvScheduleTime.setTextColor(noteColor);
            }
            if (isPreviewMode) {
                h.binding.layoutScheduleInfo.setVisibility(hasSchedule ? View.VISIBLE : View.GONE);
                h.binding.layoutScheduleIcons.setVisibility(View.GONE);
            } else {
                h.binding.layoutScheduleInfo.setVisibility(View.VISIBLE);
                h.binding.layoutScheduleIcons.setVisibility(View.VISIBLE);
                setupScheduleIcons(h, i, noteColor);
            }

            h.itemView.setOnTouchListener(null);
            h.binding.etItemName.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return isPreviewMode;
            });

            h.watcher = new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    i.name = s.toString();
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
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    items.remove(pos);
                    notifyItemRemoved(pos);
                    updateEmptyView();
                }
            });
        }

        private void setupScheduleIcons(VH h, DatabaseHelper.ChecklistItem i, int noteColor) {
            boolean hasSchedule = i.reminderTime > 0;
            h.binding.btnScheduleSecondary.setVisibility(View.GONE);
            int tintColor = hasSchedule ? noteColor : androidx.core.graphics.ColorUtils.setAlphaComponent(noteColor, 150);

            if (!hasSchedule) {
                h.binding.btnSchedulePrimary.setVisibility(View.VISIBLE);
                h.binding.btnSchedulePrimary.setImageResource(R.drawable.ic_add);
                h.binding.btnSchedulePrimary.setColorFilter(tintColor);
            } else if (i.alertType == 2) {
                h.binding.btnSchedulePrimary.setVisibility(View.VISIBLE);
                h.binding.btnScheduleSecondary.setVisibility(View.VISIBLE);
                h.binding.btnSchedulePrimary.setImageResource(R.drawable.ic_notifications);
                h.binding.btnScheduleSecondary.setImageResource(R.drawable.ic_alarm);
                h.binding.btnSchedulePrimary.setColorFilter(tintColor);
                h.binding.btnScheduleSecondary.setColorFilter(tintColor);
            } else {
                h.binding.btnSchedulePrimary.setVisibility(View.VISIBLE);
                int icon = i.alertType == 1 ? R.drawable.ic_alarm : R.drawable.ic_notifications;
                h.binding.btnSchedulePrimary.setImageResource(icon);
                h.binding.btnSchedulePrimary.setColorFilter(tintColor);
            }

            h.binding.btnSchedulePrimary.setOnClickListener(v -> showItemScheduleDialog(i));
            h.binding.btnScheduleSecondary.setOnClickListener(v -> showItemScheduleDialog(i));
        }

        private String formatScheduleTime(long timeInMillis) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(timeInMillis);
            java.util.Calendar now = java.util.Calendar.getInstance();
            String datePattern = (cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR))
                    ? "dd/MM" : "dd/MM/yyyy";
            String timePattern = settings.is24HourFormat() ? "HH:mm" : "hh:mm a";
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(timePattern + " " + datePattern, java.util.Locale.getDefault());
            return sdf.format(cal.getTime());
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
            h.binding.etItemName.removeTextChangedListener(h.watcher);
            int selectionStart = h.binding.etItemName.getSelectionStart();
            int selectionEnd = h.binding.etItemName.getSelectionEnd();

            SpannableString spannable = new SpannableString(s);
            spannable.setSpan(new StrikethroughSpan(), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            h.binding.etItemName.setText(spannable);
            h.binding.etItemName.setSelection(selectionStart, selectionEnd);

            h.binding.etItemName.addTextChangedListener(h.watcher);
        }

        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder { ItemChecklistBinding binding; android.text.TextWatcher watcher; VH(ItemChecklistBinding binding) { super(binding.getRoot()); this.binding = binding; } }
    }
}
