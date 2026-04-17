package com.notara;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AlarmActivity extends AppCompatActivity {
    private Ringtone ringtone;
    private DatabaseHelper db;
    private DatabaseHelper.Note currentNote;
    private List<CheckItem> checkItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                                     WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                                     WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                                     WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | 
                                     WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | 
                                     WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                                     WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) { e.printStackTrace(); }

        setContentView(R.layout.activity_alarm);

        db = new DatabaseHelper(this);
        int noteId = getIntent().getIntExtra("id", -1);
        currentNote = db.getNote(noteId);

        setupUI();
        playAlarm();
    }

    private void setupUI() {
        View rootLayout = findViewById(R.id.alarmRoot);
        TextView tvTitle = findViewById(R.id.tvAlarmTitle);
        TextView tvContent = findViewById(R.id.tvAlarmContent);
        RecyclerView rvChecklist = findViewById(R.id.rvAlarmChecklist);
        Button btnStop = findViewById(R.id.btnStopAlarm);

        if (currentNote != null) {
            tvTitle.setText(currentNote.title != null ? currentNote.title : "Notara_");
            
            try {
                int colorIndex = currentNote.color;
                if (colorIndex < 0 || colorIndex >= EditActivity.noteColors.length) colorIndex = 0;
                
                int noteColor = Color.parseColor(EditActivity.noteColors[colorIndex]);
                if (rootLayout != null) rootLayout.setBackgroundColor(noteColor);
                
                int textColor = getContrastColor(noteColor);
                if (tvTitle != null) tvTitle.setTextColor(textColor);
                if (tvContent != null) tvContent.setTextColor(textColor);
                if (btnStop != null) {
                    btnStop.setTextColor(textColor);
                    btnStop.setBackgroundColor(adjustAlpha(textColor, 0.2f));
                }

                if (currentNote.type == 1) { // Checklist
                    if (tvContent != null) tvContent.setVisibility(View.GONE);
                    if (rvChecklist != null) {
                        rvChecklist.setVisibility(View.VISIBLE);
                        parseContent(currentNote.content);
                        rvChecklist.setLayoutManager(new LinearLayoutManager(this));
                        rvChecklist.setAdapter(new AlarmCheckAdapter(textColor));
                    }
                } else { // Texto
                    if (tvContent != null) {
                        tvContent.setVisibility(View.VISIBLE);
                        tvContent.setText(currentNote.content != null ? currentNote.content : "");
                    }
                    if (rvChecklist != null) rvChecklist.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                if (rootLayout != null) rootLayout.setBackgroundColor(Color.parseColor("#121212"));
                e.printStackTrace();
            }
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                stopAlarmPermanently();
                if (currentNote != null) {
                    Intent intent = new Intent(this, currentNote.type == 1 ? ChecklistActivity.class : EditActivity.class);
                    intent.putExtra("NOTE_ID", currentNote.id);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
                finish();
            });
        }
    }

    private int getContrastColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void playAlarm() {
        try {
            if (ringtone != null && ringtone.isPlaying()) return;
            
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alert == null) alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
                ringtone.play();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopAlarmPermanently() {
        try {
            if (ringtone != null && ringtone.isPlaying()) {
                ringtone.stop();
                ringtone = null;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void parseContent(String content) {
        checkItems.clear();
        if (content != null && !content.isEmpty()) {
            for (String s : content.split("\n")) {
                if (s.contains("::")) {
                    String[] parts = s.split("::");
                    if (parts.length >= 2) checkItems.add(new CheckItem(parts[0], parts[1].equals("1")));
                } else if (!s.trim().isEmpty()) checkItems.add(new CheckItem(s.trim(), false));
            }
        }
    }

    private void saveChecklist() {
        if (currentNote != null) {
            StringBuilder sb = new StringBuilder();
            for (CheckItem i : checkItems) sb.append(i.name).append("::").append(i.checked ? "1" : "0").append("\n");
            currentNote.content = sb.toString();
            db.updateNote(currentNote);
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            stopAlarmPermanently();
        }
    }

    static class CheckItem { String name; boolean checked; CheckItem(String n, boolean c) { name = n; checked = c; } }

    private class AlarmCheckAdapter extends RecyclerView.Adapter<AlarmCheckAdapter.VH> {
        private int textColor;
        AlarmCheckAdapter(int textColor) { this.textColor = textColor; }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            // Usamos o layout padrão que contém um CheckedTextView
            View v = LayoutInflater.from(p.getContext()).inflate(android.R.layout.simple_list_item_multiple_choice, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            CheckItem item = checkItems.get(p);
            h.ctv.setText(item.name);
            h.ctv.setTextColor(textColor);
            h.ctv.setChecked(item.checked);
            updateTextEffect(h.ctv, item.checked);
            
            h.ctv.setOnClickListener(v -> {
                h.ctv.toggle();
                item.checked = h.ctv.isChecked();
                updateTextEffect(h.ctv, item.checked);
                saveChecklist();
            });
        }

        private void updateTextEffect(CheckedTextView ctv, boolean checked) {
            if (checked) {
                ctv.setPaintFlags(ctv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                ctv.setAlpha(0.6f);
            } else {
                ctv.setPaintFlags(ctv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                ctv.setAlpha(1.0f);
            }
        }

        @Override public int getItemCount() { return checkItems.size(); }
        class VH extends RecyclerView.ViewHolder {
            CheckedTextView ctv;
            VH(View v) { 
                super(v); 
                // No layout simple_list_item_multiple_choice, o ID do CheckedTextView é text1
                ctv = v.findViewById(android.R.id.text1); 
            }
        }
    }
}
