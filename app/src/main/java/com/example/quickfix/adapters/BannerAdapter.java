package com.example.quickfix.adapters;

import android.content.res.ColorStateList;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.Banner;
import com.example.quickfix.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    public interface Listener {
        void onEdit(Banner banner);
        void onDelete(Banner banner);
        void onToggleActive(Banner banner, boolean active);
    }

    private final Context      ctx;
    private final List<Banner> items;
    private final Listener     listener;

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
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());

        if (h.tvTitle        != null) h.tvTitle.setText(b.title != null ? b.title : "");
        if (h.tvRedirectType != null) h.tvRedirectType.setText("Redirect: " + (b.redirectType != null ? b.redirectType : "none"));
        
        boolean expired = b.expiryDate != null && !b.expiryDate.isEmpty() && b.expiryDate.compareTo(today) < 0;
        if (h.tvExpiry != null) {
            h.tvExpiry.setText("Expires: " + (b.expiryDate != null && !b.expiryDate.isEmpty() ? b.expiryDate : "No expiry"));
            h.tvExpiry.setTextColor(expired ? ContextCompat.getColor(ctx, android.R.color.holo_red_dark) : ContextCompat.getColor(ctx, R.color.text_secondary));
        }

        if (h.ivBanner != null) {
            Glide.with(ctx).load(b.imageUrl).placeholder(R.drawable.ic_content).error(R.drawable.ic_content).centerCrop().into(h.ivBanner);
        }

        if (h.chipStatus != null) {
            boolean active = b.isActive && !expired;
            h.chipStatus.setText(expired ? "Expired" : (b.isActive ? "Active" : "Disabled"));
            int colorRes = expired ? android.R.color.holo_red_light : (active ? R.color.chip_active_bg : R.color.chip_disabled_bg);
            h.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
            h.chipStatus.setOnClickListener(v -> { if (listener != null) listener.onToggleActive(b, !b.isActive); });
        }

        if (h.btnEdit   != null) h.btnEdit.setOnClickListener(v   -> { if (listener != null) listener.onEdit(b); });
        if (h.btnDelete != null) h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(b); });
    }

    @Override
    public int getItemCount() { return items != null ? items.size() : 0; }

    public void setItems(List<Banner> newItems) {
        List<Banner> snapshot = newItems == null ? new ArrayList<>() : new ArrayList<>(newItems);
        items.clear();
        items.addAll(snapshot);
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
