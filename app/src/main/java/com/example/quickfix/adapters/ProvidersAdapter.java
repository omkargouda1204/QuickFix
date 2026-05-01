package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.quickfix.ProviderProfile;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class ProvidersAdapter extends RecyclerView.Adapter<ProvidersAdapter.VH> {

    public interface OnProviderActionListener {
        void onViewProfile(ProviderProfile provider);
    }

    private final Context ctx;
    private List<ProviderProfile> items = new ArrayList<>();
    private final OnProviderActionListener listener;
    private String displayMode = "All"; // All | TopRated | TopBooked

    public ProvidersAdapter(Context ctx, OnProviderActionListener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setMode(String mode) {
        this.displayMode = mode == null ? "All" : mode;
    }

    public void setProviders(List<ProviderProfile> list) {
        items = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_provider_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ProviderProfile p = items.get(pos);

        // Business image — Glide with fallback gradient
        if (p.businessImageUrl != null && !p.businessImageUrl.isEmpty()) {
            Glide.with(ctx)
                .load(p.businessImageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_profile_header_gradient)
                .error(R.drawable.bg_profile_header_gradient)
                .centerCrop()
                .into(h.ivBusinessImage);
        } else {
            // Set a colored background based on category
            h.ivBusinessImage.setImageResource(R.drawable.bg_profile_header_gradient);
        }

        // Names
        h.tvBusinessName.setText(notEmpty(p.businessName, "Business"));
        h.tvCategory.setText(notEmpty(p.category, "General"));

        // Rating: only visible in TopRated mode and when rating > 0
        if ("TopRated".equalsIgnoreCase(displayMode) && p.rating > 0) {
            h.tvRating.setVisibility(View.VISIBLE);
            h.tvRating.setText("⭐ " + String.format(java.util.Locale.US, "%.1f", p.rating));
        } else {
            h.tvRating.setVisibility(View.GONE);
        }

        // Whole card click → view profile
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onViewProfile(p); });
    }

    private String notEmpty(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView      ivBusinessImage;
        TextView       tvBusinessName;
        TextView       tvCategory;
        TextView       tvRating;

        VH(View v) {
            super(v);
            ivBusinessImage    = v.findViewById(R.id.ivBusinessImage);
            tvBusinessName     = v.findViewById(R.id.tvBusinessName);
            tvCategory         = v.findViewById(R.id.tvProviderCategory);
            tvRating           = v.findViewById(R.id.tvProviderRating);
        }
    }
}
