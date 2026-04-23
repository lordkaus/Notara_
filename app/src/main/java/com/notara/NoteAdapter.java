package com.notara;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import com.notara.databinding.ItemNoteBinding;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {
    private List<DatabaseHelper.Note> notes;
    private final NoteActionListener actionListener;
    private SettingsManager settings;

    public interface NoteActionListener {
        void onNoteAction();
        void onNoteLongClick(DatabaseHelper.Note note);
    }

    public NoteAdapter(List<DatabaseHelper.Note> notes, NoteActionListener actionListener) {
        this.notes = notes;
        this.actionListener = actionListener;
    }

    public void setNotes(List<DatabaseHelper.Note> newNotes) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return notes.size(); }
            @Override public int getNewListSize() { return newNotes.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return notes.get(oldPos).id == newNotes.get(newPos).id;
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                DatabaseHelper.Note oldNote = notes.get(oldPos);
                DatabaseHelper.Note newNote = newNotes.get(newPos);
                return oldNote.title.equals(newNote.title) && 
                       oldNote.content.equals(newNote.content) && 
                       oldNote.color == newNote.color &&
                       oldNote.isPinned == newNote.isPinned &&
                       oldNote.isLocked == newNote.isLocked &&
                       oldNote.type == newNote.type &&
                       oldNote.isGhost == newNote.isGhost;
            }
        });
        this.notes = newNotes;
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        if (settings == null) settings = new SettingsManager(holder.itemView.getContext());
        boolean uniform = settings.isUniformGridEnabled();
        
        DatabaseHelper.Note note = notes.get(position);
        
        // Feedback visual para instâncias fantasmas (recorrência)
        holder.itemView.setAlpha(note.isGhost ? 0.5f : 1.0f);
        
        // Ajuste para Tamanho Uniforme
        if (uniform) {
            holder.binding.tvTitle.setLines(2);
            holder.binding.tvContent.setLines(4);
        } else {
            holder.binding.tvTitle.setMaxLines(2);
            holder.binding.tvTitle.setMinLines(0);
            holder.binding.tvContent.setMaxLines(8);
            holder.binding.tvContent.setMinLines(0);
        }

        // Regra de Privacidade: Título com '*' e conteúdo oculto se trancado
        if (note.isLocked == 1) {
            holder.binding.tvTitle.setText(holder.itemView.getContext().getString(R.string.locked_note_title, note.title));
            holder.binding.tvContent.setText(holder.itemView.getContext().getString(R.string.locked_note_content));
            holder.binding.tvContent.setAlpha(0.4f);
        } else {
            holder.binding.tvTitle.setText(note.title);
            holder.binding.tvContent.setAlpha(1.0f);
            if (note.type == 1) {
                holder.binding.tvContent.setText(formatChecklistPreview(note.content));
            } else {
                holder.binding.tvContent.setText(note.content);
            }
        }

        holder.binding.ivPin.setVisibility(note.isPinned == 1 ? View.VISIBLE : View.GONE);
        
        // Usa a paleta sincronizada
        int color = getNoteColor(note.color);
        int cardStyle = settings.getCardStyle();
        int transparency = settings.getTransparency();
        int alpha = (int) (transparency * 2.55); // 0-255

        if (cardStyle == 1) { // Pastel
            holder.binding.colorBar.setVisibility(View.GONE);
            float[] hsl = new float[3];
            androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
            hsl[1] *= 0.4f; // Reduz saturação
            hsl[2] = 0.9f; // Alta luminosidade
            int baseColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl);
            int pastelWithAlpha = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor));
            
            holder.binding.cardNote.setCardBackgroundColor(pastelWithAlpha);
            holder.binding.tvTitle.setTextColor(0xFF333333);
            holder.binding.tvContent.setTextColor(0xFF555555);
            holder.binding.cardNote.setStrokeWidth(0);
        } else if (cardStyle == 2) { // Solid
            holder.binding.colorBar.setVisibility(View.GONE);
            int solidWithAlpha = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
            
            holder.binding.cardNote.setCardBackgroundColor(solidWithAlpha);
            holder.binding.tvTitle.setTextColor(Color.BLACK); // Letras pretas conforme solicitado
            holder.binding.tvContent.setTextColor(0xFF222222);
            holder.binding.cardNote.setStrokeWidth(0);
        } else { // Label (Padrão)
            holder.binding.colorBar.setVisibility(View.VISIBLE);
            holder.binding.colorBar.setBackgroundColor(color);
            
            // Fundo do card com transparência do usuário, mas texto opaco
            int surfaceColor = getThemeColor(holder.itemView.getContext(), android.R.attr.colorBackground);
            int labelBgWithAlpha = Color.argb(alpha, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor));
            
            holder.binding.cardNote.setCardBackgroundColor(labelBgWithAlpha);
            
            // Verifica luminosidade do fundo para definir cor do texto
            double luminance = androidx.core.graphics.ColorUtils.calculateLuminance(surfaceColor);
            int textColor = (luminance > 0.5) ? Color.BLACK : Color.WHITE;
            int subColor = (luminance > 0.5) ? Color.DKGRAY : Color.LTGRAY;
            
            holder.binding.tvTitle.setTextColor(textColor | 0xFF000000);
            holder.binding.tvContent.setTextColor(subColor | 0xFF000000);
            holder.binding.cardNote.setStrokeWidth(1);
        }

        holder.itemView.setOnClickListener(v -> {
            DatabaseHelper.Note noteData = notes.get(holder.getBindingAdapterPosition());
            Intent intent = new Intent(v.getContext(), noteData.type == 1 ? ChecklistActivity.class : EditActivity.class);
            intent.putExtra("NOTE_ID", noteData.id);
            if (noteData.id != -1) {
                intent.putExtra("PREVIEW_MODE", true);
            }
            v.getContext().startActivity(intent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            actionListener.onNoteLongClick(note);
            return true;
        });
    }

    private CharSequence formatChecklistPreview(String content) {
        if (content == null || content.isEmpty()) return "";
        SpannableStringBuilder builder = new SpannableStringBuilder();
        String[] lines = content.split("\n");
        int limit = Math.min(lines.length, 5);
        for (int i = 0; i < limit; i++) {
            String line = lines[i];
            int start = builder.length();
            if (line.contains("::")) {
                String[] parts = line.split("::");
                String name = parts[0];
                boolean checked = parts.length > 1 && parts[1].equals("1");
                builder.append(checked ? "✓ " : "☐ ").append(name);
                if (checked) {
                    builder.setSpan(new StrikethroughSpan(), start + 2, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                builder.append("\n");
            } else if (!line.trim().isEmpty()) {
                builder.append("☐ ").append(line).append("\n");
            }
        }
        if (lines.length > 5) builder.append("…");
        return builder.length() > 0 && builder.charAt(builder.length()-1) == '\n' ? builder.subSequence(0, builder.length()-1) : builder;
    }

    @Override public int getItemCount() { return notes.size(); }

    private int getThemeColor(android.content.Context context, int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attr, tv, true);
        // Garante opacidade total (Alpha = FF) para o texto
        return tv.data | 0xFF000000;
    }

    private int getNoteColor(int index) {
        // Sincronizado com EditActivity.noteColors
        String[] colors = EditActivity.noteColors;
        if (index >= 0 && index < colors.length) {
            return Color.parseColor(colors[index]);
        }
        return Color.parseColor(colors[0]); // Amarelo padrão
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        ItemNoteBinding binding;
        NoteViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
