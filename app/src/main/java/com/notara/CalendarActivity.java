package com.notara;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.notara.databinding.ActivityCalendarBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CalendarActivity extends AppCompatActivity {
    private ActivityCalendarBinding binding;
    private Calendar currentCalendar;
    private Calendar selectedDate;
    private NoteViewModel viewModel;
    private DayAdapter dayAdapter;
    private NoteAdapter noteAdapter;
    private List<DatabaseHelper.Note> allScheduledNotes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SettingsManager settings = new SettingsManager(this);
        int theme = settings.getTheme();

        // Define o tema antes do super.onCreate
        if (theme == 1) {
            setTheme(R.style.Theme_Notara_Pantera);
        } else {
            setTheme(R.style.Theme_Notara);
        }

        // Gerencia a cor dos ícones da barra de status usando a API do AndroidX
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        if (theme == 0 || theme == 3) {
            controller.setAppearanceLightStatusBars(true);
        } else {
            controller.setAppearanceLightStatusBars(false);
        }

        super.onCreate(savedInstanceState);
        binding = ActivityCalendarBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(NoteViewModel.class);
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
        List<DatabaseHelper.Note> recurringNotes = viewModel.getRecurringNotes();

        RecyclerView recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        NoteAdapter adapter = new NoteAdapter(recurringNotes, new NoteAdapter.NoteActionListener() {
            @Override public void onNoteAction() {}
            @Override public void onNoteLongClick(DatabaseHelper.Note note) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(CalendarActivity.this)
                    .setTitle(R.string.delete_cycle_title)
                    .setMessage(R.string.delete_cycle_msg)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        note.isTrashed = 1;
                        viewModel.updateNote(note);
                        showRecurringNotesDialog();
                        updateUI();
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
        binding.rvNotes.setLayoutManager(new LinearLayoutManager(this));
        noteAdapter = new NoteAdapter(new ArrayList<>(), new NoteAdapter.NoteActionListener() {
            @Override public void onNoteAction() { updateUI(); }
            @Override public void onNoteLongClick(DatabaseHelper.Note note) {}
        });
        binding.rvNotes.setAdapter(noteAdapter);
    }

    private void updateUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        binding.tvMonthYear.setText(sdf.format(currentCalendar.getTime()));

        Calendar rangeEndCal = (Calendar) currentCalendar.clone();
        rangeEndCal.set(Calendar.DAY_OF_MONTH, 1);
        rangeEndCal.add(Calendar.DAY_OF_MONTH, 42);

        allScheduledNotes = viewModel.getScheduledNotesUpTo(rangeEndCal.getTimeInMillis());

        dayAdapter.updateDays(prepareCalendarDays());
        updateNoteList();
    }

    private List<CalendarDay> prepareCalendarDays() {
        List<CalendarDay> days = new ArrayList<>();
        java.time.LocalDate firstOfMonth = java.time.LocalDate.of(
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH) + 1,
            1
        );

        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue();
        int daysBefore = (dayOfWeek % 7);
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
        binding.tvSelectedDate.setText(getString(R.string.notes_on_date, sdf.format(selectedDate.getTime())));
    }

    private boolean shouldShowOnDay(java.time.LocalDate gridDate, DatabaseHelper.Note note) {
        java.time.LocalDate eventDate = note.getLocalDate();
        if (gridDate.isBefore(eventDate)) return false;
        if (gridDate.isEqual(eventDate)) return true;
        if (note.recurrenceType == 0) return false;

        switch (note.recurrenceType) {
            case 1: return true;
            case 2: return gridDate.getDayOfWeek() == eventDate.getDayOfWeek();
            case 3:
                int targetDay = eventDate.getDayOfMonth();
                int lastDayOfMonth = gridDate.lengthOfMonth();
                return gridDate.getDayOfMonth() == Math.min(targetDay, lastDayOfMonth);
            case 4:
                if (gridDate.getMonth() != eventDate.getMonth()) return false;
                int targetDayYearly = eventDate.getDayOfMonth();
                int lastDayOfMonthYearly = gridDate.lengthOfMonth();
                return gridDate.getDayOfMonth() == Math.min(targetDayYearly, lastDayOfMonthYearly);
            case 5:
                int dayOfWeekValue = gridDate.getDayOfWeek().getValue();
                int bitShift = dayOfWeekValue % 7;
                return (note.recurrenceDays & (1 << bitShift)) != 0;
        }
        return false;
    }

    private boolean isSameDay(java.time.LocalDate d1, java.time.LocalDate d2) {
        return d1 != null && d2 != null && d1.isEqual(d2);
    }
}
