package com.notara;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.notara.databinding.ItemAttachmentImageBinding;
import java.io.File;
import java.util.List;

public class AttachmentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_IMAGE = 0;
    private static final int TYPE_AUDIO = 1;
    private final Context context;
    private List<DatabaseHelper.Attachment> items;
    private final OnDeleteListener deleteListener;

    public interface OnDeleteListener {
        void onDelete(DatabaseHelper.Attachment attachment);
    }

    public AttachmentAdapter(Context context, List<DatabaseHelper.Attachment> items, OnDeleteListener deleteListener) {
        this.context = context;
        this.items = items;
        this.deleteListener = deleteListener;
    }

    public void setItems(List<DatabaseHelper.Attachment> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_IMAGE) {
            ItemAttachmentImageBinding b = ItemAttachmentImageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ImageVH(b);
        } else {
            AudioPlayerView apv = new AudioPlayerView(parent.getContext());
            apv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new AudioVH(apv);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DatabaseHelper.Attachment a = items.get(position);
        if (holder instanceof ImageVH) {
            ImageVH ih = (ImageVH) holder;
            File f = AttachmentManager.getAttachmentFile(context, a);
            if (f.exists()) {
                Bitmap bmp = AttachmentManager.generateThumbnail(f.getAbsolutePath(), 200);
                if (bmp != null) ih.binding.ivThumbnail.setImageBitmap(bmp);
            }
            ih.binding.ivThumbnail.setOnClickListener(v -> {
                Intent intent = new Intent(context, ImageViewerActivity.class);
                intent.putExtra("file_path", f.getAbsolutePath());
                context.startActivity(intent);
            });
            ih.binding.btnDeleteAttachment.setOnClickListener(v -> deleteListener.onDelete(a));
        } else if (holder instanceof AudioVH) {
            AudioVH ah = (AudioVH) holder;
            File f = AttachmentManager.getAttachmentFile(context, a);
            if (f.exists()) ah.playerView.setAudioFile(f);
            ah.playerView.setDeleteListener(v -> deleteListener.onDelete(a));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ImageVH extends RecyclerView.ViewHolder {
        final ItemAttachmentImageBinding binding;
        ImageVH(ItemAttachmentImageBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }

    static class AudioVH extends RecyclerView.ViewHolder {
        final AudioPlayerView playerView;
        AudioVH(AudioPlayerView apv) { super(apv); this.playerView = apv; }
    }
}
