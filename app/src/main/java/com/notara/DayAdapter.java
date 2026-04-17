package com.notara;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DayAdapter extends RecyclerView.Adapter<DayAdapter.DayViewHolder> {
    private List<CalendarDay> days = new ArrayList<>();
    private final OnDayClickListener listener;

    public interface OnDayClickListener {
        void onDayClick(CalendarDay day);
        void onDayLongClick(CalendarDay day);
    }

    public DayAdapter(OnDayClickListener listener) {
        this.listener = listener;
    }

    public void updateDays(List<CalendarDay> newDays) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return days.size(); }
            @Override public int getNewListSize() { return newDays.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return days.get(oldPos).date.equals(newDays.get(newPos).date);
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                return days.get(oldPos).equals(newDays.get(newPos));
            }
        });
        this.days = new ArrayList<>(newDays);
        result.dispatchUpdatesTo(this);
    }

    @NonNull @Override public DayViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new DayViewHolder(LayoutInflater.from(p.getContext()).inflate(R.layout.item_calendar_day, p, false));
    }

    @Override public void onBindViewHolder(@NonNull DayViewHolder h, int p) {
        h.bind(days.get(p), listener);
    }

    @Override public int getItemCount() { return days.size(); }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay; View selectedIndicator; LinearLayout dotContainer;

        DayViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tvDay);
            selectedIndicator = v.findViewById(R.id.selectedIndicator);
            dotContainer = v.findViewById(R.id.dotContainer);
        }

        void bind(CalendarDay day, OnDayClickListener listener) {
            tvDay.setText(String.valueOf(day.date.getDayOfMonth()));
            
            // Opacidade para dias fora do mês
            tvDay.setAlpha(day.isCurrentMonth ? 1.0f : 0.3f);

            // Indicador de Seleção e Hoje
            selectedIndicator.setVisibility(day.isSelected ? View.VISIBLE : View.GONE);
            if (day.isSelected) {
                tvDay.setTextColor(Color.WHITE);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(Color.parseColor("#4DB6AC")); // Seria melhor usar ContextCompat.getColor(v.getContext(), R.color.primary)
                selectedIndicator.setBackground(shape);
            } else {
                if (day.isToday) {
                    tvDay.setTextColor(Color.parseColor("#4DB6AC"));
                } else {
                    tvDay.setTextColor(Color.GRAY);
                }
            }

            // Renderização dos Pontos (Dots)
            dotContainer.removeAllViews();
            for (int i = 0; i < day.noteColors.size(); i++) {
                View dot = new View(itemView.getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(12, 12);
                params.setMargins(2, 0, 2, 0);
                dot.setLayoutParams(params);
                
                GradientDrawable dotShape = new GradientDrawable();
                dotShape.setShape(GradientDrawable.OVAL);
                dotShape.setColor(day.noteColors.get(i));
                dot.setBackground(dotShape);
                
                // Transparência para instâncias recorrentes (fantasmas)
                if (day.isGhostNote.get(i)) {
                    dot.setAlpha(0.4f);
                }
                
                dotContainer.addView(dot);
            }

            itemView.setOnClickListener(v -> listener.onDayClick(day));
            itemView.setOnLongClickListener(v -> {
                listener.onDayLongClick(day);
                return true;
            });
        }
    }
}
