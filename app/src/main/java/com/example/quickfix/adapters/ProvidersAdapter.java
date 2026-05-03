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

        // Profile image — Glide with circleCrop
        String img = (p.profileImageUrl != null && !p.profileImageUrl.isEmpty()) ? p.profileImageUrl : p.businessImageUrl;
        Glide.with(ctx)
            .load(img)
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .circleCrop()
            .into(h.imgProvider);

        // Names & Category
        h.tvBusinessName.setText(notEmpty(p.businessName, "Business"));
        h.tvCategory.setText(notEmpty(p.category, "General"));
        
        // Location
        h.tvLocation.setText(cleanLocation(p.city));

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

    private String cleanLocation(String text) {
        if (text == null) return "";
        String cleaned = text.replaceAll("[A-Z0-9+]{6,}", "").replace("-", "").trim();
        return cleaned.replaceAll("\\s*\\d{5,6}$", "").trim();
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView      imgProvider;
        TextView       tvBusinessName;
        TextView       tvCategory;
        TextView       tvLocation;
        TextView       tvRating;

        VH(View v) {
            super(v);
            imgProvider        = v.findViewById(R.id.imgProvider);
            tvBusinessName     = v.findViewById(R.id.tvBusinessName);
            tvCategory         = v.findViewById(R.id.tvProviderCategory);
            tvLocation         = v.findViewById(R.id.tvLocation);
            tvRating           = v.findViewById(R.id.tvProviderRating);
        }
    }
}
