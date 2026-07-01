package com.notara;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import com.notara.databinding.ItemAttachmentAudioBinding;
import java.io.File;
import java.io.IOException;

public class AudioPlayerView extends FrameLayout {

    private ItemAttachmentAudioBinding binding;
    private MediaPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                binding.seekBar.setProgress((int) ((float) player.getCurrentPosition() / player.getDuration() * 1000));
                binding.tvDuration.setText(formatTime(player.getCurrentPosition()) + " / " + formatTime(player.getDuration()));
                handler.postDelayed(this, 200);
            }
        }
    };
    private boolean prepared = false;
    private boolean isPlaying = false;

    public AudioPlayerView(Context context) { this(context, null); }
    public AudioPlayerView(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public AudioPlayerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        binding = ItemAttachmentAudioBinding.inflate(LayoutInflater.from(context), this, true);

        binding.btnPlayPause.setOnClickListener(v -> togglePlayPause());
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && player != null && prepared) {
                    int pos = (int) ((float) progress / 1000 * player.getDuration());
                    player.seekTo(pos);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    public void setAudioFile(File file) {
        release();
        player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                prepared = true;
                binding.tvDuration.setText("0:00 / " + formatTime(player.getDuration()));
                binding.seekBar.setProgress(0);
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            });
            player.setOnCompletionListener(mp -> {
                isPlaying = false;
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                binding.seekBar.setProgress(1000);
                binding.tvDuration.setText(formatTime(player.getDuration()) + " / " + formatTime(player.getDuration()));
                handler.removeCallbacks(updater);
            });
            player.prepareAsync();
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
        } catch (IOException e) {
            binding.tvDuration.setText("Erro");
        }
    }

    public void setDeleteListener(OnClickListener listener) {
        binding.btnDeleteAttachment.setOnClickListener(listener);
    }

    private void togglePlayPause() {
        if (!prepared || player == null) return;
        if (isPlaying) {
            player.pause();
            isPlaying = false;
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            handler.removeCallbacks(updater);
        } else {
            player.start();
            isPlaying = true;
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            handler.post(updater);
        }
    }

    private void release() {
        if (player != null) {
            if (player.isPlaying()) player.stop();
            player.release();
            player = null;
        }
        prepared = false;
        isPlaying = false;
        handler.removeCallbacks(updater);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    private static String formatTime(int ms) {
        int s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }
}
