package com.notara;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.notara.databinding.ActivityCalendarBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarActivity extends AppCompatActivity {
    private ActivityCalendarBinding binding;
    private Calendar currentCalendar;
    private Calendar selectedDate;
    private DatabaseHelper db;
    private DayAdapter dayAdapter;
    private NoteAdapter noteAdapter;
    private List<DatabaseHelper.Note> allScheduledNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager settings = new SettingsManager(this);
        int theme = settings.getTheme();
        if (theme == 0 || theme == 3) {
            setTheme(R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            setTheme(theme == 1 ? R.style.Theme_Notara_Pantera : R.style.Theme_Notara);
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_VISIBLE);
        }
        
        super.onCreate(savedInstanceState);
        binding = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = new DatabaseHelper(this);
        currentCalendar = Calendar.getInstance();
        selectedDate = Calendar.getInstance();
        
        setupCalendar();
        setupNoteList();
        updateUI();

        binding.btnPrev.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateUI();
        });

        binding.btnNext.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateUI();
        });

        binding.btnRecurring.setOnClickListener(v -> showRecurringNotesDialog());
    }

    private void showRecurringNotesDialog() {
        List<DatabaseHelper.Note> recurringNotes = db.getRecurringNotes();

        // Create a RecyclerView to display the notes
        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        NoteAdapter adapter = new NoteAdapter(recurringNotes, new NoteAdapter.NoteActionListener() {
            @Override
            public void onNoteAction() {
                // No-op for this dialog, as notes are managed via long click
            }

            @Override
            public void onNoteLongClick(DatabaseHelper.Note note) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(CalendarActivity.this)
                    .setTitle(R.string.delete_cycle_title)
                    .setMessage(R.string.delete_cycle_msg)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        note.isTrashed = 1; // Move to trash
                        db.updateNote(note);
                        showRecurringNotesDialog(); // Refresh the dialog
                        updateUI(); // Refresh main calendar UI
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            }
        });
        recyclerView.setAdapter(adapter);

        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recurring_cycles)
            .setView(recyclerView);

        if (recurringNotes.isEmpty()) {
            builder.setMessage(R.string.no_cycles_found);
        }

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void setupCalendar() {
        binding.rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        dayAdapter = new DayAdapter(new DayAdapter.OnDayClickListener() {
            @Override
            public void onDayClick(CalendarDay day) {
                selectedDate.set(day.date.getYear(), day.date.getMonthValue() - 1, day.date.getDayOfMonth());
                updateUI();
            }

            @Override
            public void onDayLongClick(CalendarDay day) {
                getWindow().getDecorView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                Intent intent = new Intent(CalendarActivity.this, EditActivity.class);
                Calendar calToCreate = Calendar.getInstance();
                calToCreate.set(day.date.getYear(), day.date.getMonthValue() - 1, day.date.getDayOfMonth(), 9, 0);
                intent.putExtra("INITIAL_REMINDER_TIME", calToCreate.getTimeInMillis());
                startActivity(intent);
            }
        });
        binding.rvCalendar.setAdapter(dayAdapter);
    }

    private void setupNoteList() {
        binding.rvNotes.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        noteAdapter = new NoteAdapter(new ArrayList<>(), new NoteAdapter.NoteActionListener() {
            @Override public void onNoteAction() { updateUI(); }
            @Override public void onNoteLongClick(DatabaseHelper.Note note) {}
        });
        binding.rvNotes.setAdapter(noteAdapter);
    }

    private void updateUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        binding.tvMonthYear.setText(sdf.format(currentCalendar.getTime()));
        
        // Calcula o fim do período visível (aprox. 42 dias a partir do início da grade)
        Calendar rangeEndCal = (Calendar) currentCalendar.clone();
        rangeEndCal.set(Calendar.DAY_OF_MONTH, 1);
        rangeEndCal.add(Calendar.DAY_OF_MONTH, 42); 
        
        allScheduledNotes = db.getScheduledNotesUpTo(rangeEndCal.getTimeInMillis());
        
        // Prepara os dados para o adapter de forma desacoplada
        dayAdapter.updateDays(prepareCalendarDays());
        updateNoteList();
    }

    private List<CalendarDay> prepareCalendarDays() {
        List<CalendarDay> days = new ArrayList<>();
        
        // Data base do mês atual
        java.time.LocalDate firstOfMonth = java.time.LocalDate.of(
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH) + 1,
            1
        );
        
        // Calcula o início da grade (primeiro dia visível)
        //getDayOfWeek() retorna 1 (segunda) a 7 (domingo)
        // No calendário tradicional, domingo é 1. Vamos ajustar para o domingo ser o início.
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue(); // 1=seg, ..., 7=dom
        int daysBefore = (dayOfWeek % 7); // Se for domingo(7), daysBefore=0. Se for segunda(1), daysBefore=1.
        
        java.time.LocalDate startDate = firstOfMonth.minusDays(daysBefore);
        
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate selLocalDate = selectedDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        int currentMonthValue = firstOfMonth.getMonthValue();

        for (int i = 0; i < 42; i++) {
            java.time.LocalDate date = startDate.plusDays(i);
            
            List<Integer> colors = new ArrayList<>();
            List<Boolean> ghosts = new ArrayList<>();
            
            int count = 0;
            for (DatabaseHelper.Note n : allScheduledNotes) {
                if (shouldShowOnDay(date, n)) {
                    int colorIndex = n.color;
                    String colorHex = EditActivity.noteColors[colorIndex % EditActivity.noteColors.length];
                    colors.add(Color.parseColor(colorHex));
                    ghosts.add(!isSameDay(date, n.getLocalDate()));
                    
                    count++;
                    if (count >= 3) break;
                }
            }

            days.add(new CalendarDay(
                date,
                date.getMonthValue() == currentMonthValue,
                isSameDay(date, selLocalDate),
                isSameDay(date, today),
                colors,
                ghosts
            ));
        }
        return days;
    }

    private void updateNoteList() {
        List<DatabaseHelper.Note> dayNotes = new ArrayList<>();
        java.time.LocalDate selDate = selectedDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        
        for (DatabaseHelper.Note n : allScheduledNotes) {
            if (shouldShowOnDay(selDate, n)) {
                java.time.LocalDate nDate = n.getLocalDate();
                if (!isSameDay(selDate, nDate)) {
                    dayNotes.add(n.asGhost());
                } else {
                    n.isGhost = false;
                    dayNotes.add(n);
                }
            }
        }
        noteAdapter.setNotes(dayNotes);
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'de' MMMM", Locale.getDefault());
        binding.tvSelectedDate.setText("Notas de " + sdf.format(selectedDate.getTime()));
    }

    private boolean shouldShowOnDay(java.time.LocalDate gridDate, DatabaseHelper.Note note) {
        java.time.LocalDate eventDate = note.getLocalDate();
        
        // Se a data da grade for antes da data de início, não mostra
        if (gridDate.isBefore(eventDate)) return false;
        
        // Lógica de Recorrência
        if (gridDate.isEqual(eventDate)) return true;
        if (note.recurrenceType == 0) return false; // Sem repetição
        
        switch (note.recurrenceType) {
            case 1: // Diário
                return true;
            case 2: // Semanal
                return gridDate.getDayOfWeek() == eventDate.getDayOfWeek();
            case 3: // Mensal
                // Verifica se o dia do mês coincide. 
                // Se o evento foi criado no dia 31 e o mês atual só tem 30, mostra no dia 30.
                int targetDay = eventDate.getDayOfMonth();
                int lastDayOfMonth = gridDate.lengthOfMonth();
                return gridDate.getDayOfMonth() == Math.min(targetDay, lastDayOfMonth);
            case 4: // Anual
                // Verifica se o mês coincide
                if (gridDate.getMonth() != eventDate.getMonth()) return false;
                // Verifica o dia (ajustando para 29 de fevereiro em anos não bissextos)
                int targetDayYearly = eventDate.getDayOfMonth();
                int lastDayOfMonthYearly = gridDate.lengthOfMonth();
                return gridDate.getDayOfMonth() == Math.min(targetDayYearly, lastDayOfMonthYearly);
            case 5: // Personalizado (Dias da Semana)
                // LocalDate DayOfWeek: 1 (seg) a 7 (dom). 
                // Nosso bitmask: Dom=0, Seg=1, Ter=2, Qua=3, Qui=4, Sex=5, Sab=6
                int dayOfWeekValue = gridDate.getDayOfWeek().getValue(); // 1-7
                int bitShift = dayOfWeekValue % 7; // Seg:1%7=1, Ter:2%7=2... Dom:7%7=0
                return (note.recurrenceDays & (1 << bitShift)) != 0;
        }
        return false;
    }

    private boolean isSameDay(java.time.LocalDate d1, java.time.LocalDate d2) {
        return d1 != null && d2 != null && d1.isEqual(d2);
    }
}
