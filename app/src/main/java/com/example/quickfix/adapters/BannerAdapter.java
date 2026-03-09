package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.Banner;
import com.example.quickfix.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    public interface Listener {
        void onEdit(Banner banner);
        void onDelete(Banner banner);
        void onToggleActive(Banner banner, boolean active);
    }

    private final Context       ctx;
    private final List<Banner>  items;
    private final Listener      listener;

    public BannerAdapter(Context ctx, List<Banner> items, Listener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_banner_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Banner b = items.get(pos);

        h.tvTitle.setText(b.title != null ? b.title : "");
        h.tvRedirectType.setText("Redirect: " + (b.redirectType != null ? b.redirectType : "none"));
        h.tvExpiry.setText("Expires: " + (b.expiryDate != null ? b.expiryDate : "No expiry"));

        // Status chip
        if (b.isEffectivelyActive()) {
            h.chipStatus.setText("Active");
            h.chipStatus.setChipBackgroundColorResource(R.color.chip_active_bg);
        } else {
            h.chipStatus.setText("Disabled");
            h.chipStatus.setChipBackgroundColorResource(R.color.chip_disabled_bg);
        }

        // ...existing code...

        h.chipStatus.setOnClickListener(v -> {
            if (listener != null) listener.onToggleActive(b, !b.isEffectivelyActive());
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<Banner> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView   ivBanner;
        TextView    tvTitle, tvRedirectType, tvExpiry;
        Chip        chipStatus;
        ImageButton btnEdit, btnDelete;

        VH(View v) {
            super(v);
            ivBanner       = v.findViewById(R.id.ivBannerPreview);
            tvTitle        = v.findViewById(R.id.tvBannerTitle);
            tvRedirectType = v.findViewById(R.id.tvBannerRedirect);
            tvExpiry       = v.findViewById(R.id.tvBannerExpiry);
            chipStatus     = v.findViewById(R.id.chipBannerStatus);
            btnEdit        = v.findViewById(R.id.btnBannerEdit);
            btnDelete      = v.findViewById(R.id.btnBannerDelete);
        }
    }
}

