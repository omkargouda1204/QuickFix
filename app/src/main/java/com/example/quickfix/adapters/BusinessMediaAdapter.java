package com.example.quickfix.adapters;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.BusinessMediaItem;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class BusinessMediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_UPLOADED = 1;
    private static final int TYPE_LOCAL = 2;

    private final Context context;
    private final List<Object> mediaItems = new ArrayList<>();
    private final MediaActionListener listener;
    private boolean isEditMode = false;

    public interface MediaActionListener {
        void onDelete(Object item, int position);
        void onView(Object item);
    }

    public BusinessMediaAdapter(Context context, MediaActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setUploadedMedia(List<BusinessMediaItem> items) {
        mediaItems.clear();
        if (items != null) {
            mediaItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void addLocalMedia(Uri uri) {
        mediaItems.add(uri);
        notifyItemInserted(mediaItems.size() - 1);
    }

    public void removeLocalMedia(Uri uri) {
        int pos = mediaItems.indexOf(uri);
        if (pos >= 0) {
            mediaItems.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public List<Uri> getPendingUploads() {
        List<Uri> uris = new ArrayList<>();
        for (Object item : mediaItems) {
            if (item instanceof Uri) {
                uris.add((Uri) item);
            }
        }
        return uris;
    }

    public void setEditMode(boolean editMode) {
        isEditMode = editMode;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mediaItems.get(position) instanceof BusinessMediaItem ? TYPE_UPLOADED : TYPE_LOCAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_business_media, parent, false);
        return new MediaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ((MediaViewHolder) holder).bind(mediaItems.get(position), position);
    }

    @Override
    public int getItemCount() {
        return mediaItems.size();
    }

    class MediaViewHolder extends RecyclerView.ViewHolder {
        ImageView ivMedia, ivPlayIcon;
        TextView tvMediaType;
        ImageButton btnDelete;

        MediaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMedia = itemView.findViewById(R.id.ivMedia);
            ivPlayIcon = itemView.findViewById(R.id.ivPlayIcon);
            tvMediaType = itemView.findViewById(R.id.tvMediaType);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(Object item, int position) {
            if (item instanceof BusinessMediaItem) {
                BusinessMediaItem media = (BusinessMediaItem) item;
                String url = media.isVideo() && media.thumbnailUrl != null && !media.thumbnailUrl.isEmpty()
                        ? media.thumbnailUrl : media.mediaUrl;

                Glide.with(context)
                        .load(url)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .centerCrop()
                        .into(ivMedia);

                ivPlayIcon.setVisibility(media.isVideo() ? View.VISIBLE : View.GONE);
                tvMediaType.setText(media.isVideo() ? "Video" : "Image");

            } else if (item instanceof Uri) {
                Uri uri = (Uri) item;
                Glide.with(context)
                        .load(uri)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .centerCrop()
                        .into(ivMedia);

                ivPlayIcon.setVisibility(View.GONE);
                tvMediaType.setText("Pending");
            }

            btnDelete.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
            btnDelete.setEnabled(isEditMode);
            btnDelete.setOnClickListener(v -> {
                if (!isEditMode) {
                    return;
                }
                if (listener != null) {
                    listener.onDelete(item, position);
                }
            });

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onView(item);
                }
            });
        }
    }
}
