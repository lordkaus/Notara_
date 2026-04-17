package com.notara;

import java.time.LocalDate;
import java.util.List;

public class CalendarDay {
    public final LocalDate date;
    public final boolean isCurrentMonth;
    public final boolean isSelected;
    public final boolean isToday;
    public final List<Integer> noteColors; // Lista de cores (ints processados)
    public final List<Boolean> isGhostNote; // Para aplicar transparência nos pontos

    public CalendarDay(LocalDate date, boolean isCurrentMonth, boolean isSelected, boolean isToday, List<Integer> noteColors, List<Boolean> isGhostNote) {
        this.date = date;
        this.isCurrentMonth = isCurrentMonth;
        this.isSelected = isSelected;
        this.isToday = isToday;
        this.noteColors = noteColors;
        this.isGhostNote = isGhostNote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CalendarDay that = (CalendarDay) o;
        return isCurrentMonth == that.isCurrentMonth &&
               isSelected == that.isSelected &&
               isToday == that.isToday &&
               date.equals(that.date) &&
               noteColors.equals(that.noteColors) &&
               isGhostNote.equals(that.isGhostNote);
    }

    @Override
    public int hashCode() {
        int result = date.hashCode();
        result = 31 * result + (isCurrentMonth ? 1 : 0);
        result = 31 * result + (isSelected ? 1 : 0);
        result = 31 * result + (isToday ? 1 : 0);
        result = 31 * result + noteColors.hashCode();
        result = 31 * result + isGhostNote.hashCode();
        return result;
    }
}
