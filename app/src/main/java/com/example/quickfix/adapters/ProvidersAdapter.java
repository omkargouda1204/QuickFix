package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.quickfix.ProviderProfile;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProvidersAdapter extends RecyclerView.Adapter<ProvidersAdapter.VH> {

    public interface OnProviderActionListener {
        void onViewProfile(ProviderProfile provider);
        void onBookService(ProviderProfile provider);
    }

    private final Context ctx;
    private List<ProviderProfile> items = new ArrayList<>();
    private OnProviderActionListener listener;

    public ProvidersAdapter(Context ctx, OnProviderActionListener listener) {
        this.ctx      = ctx;
        this.listener = listener;
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
        h.tvProviderName.setText("by " + notEmpty(p.ownerName, "Provider"));
        h.tvCity.setText(notEmpty(p.city, "—"));
        h.tvCategoryBadge.setText(notEmpty(p.category, "Service"));

        // Experience
        int expYears = parseExperienceYears(p.experienceYrs);
        if (expYears > 0) {
            h.tvExperience.setText(expYears + " yrs exp");
            h.tvExperience.setVisibility(View.VISIBLE);
        } else {
            h.tvExperience.setVisibility(View.GONE);
        }

        // Description
        // Keep cards compact; full details are shown on ProviderDetailActivity.
        h.tvDescription.setVisibility(View.GONE);

        // Verified badge
        h.layoutVerifiedBadge.setVisibility(p.isVerified ? View.VISIBLE : View.GONE);

        // Rating
        if (p.rating > 0) {
            h.layoutRating.setVisibility(View.VISIBLE);
            h.tvRating.setText(String.format(Locale.US, "%.1f", p.rating));
        } else {
            h.layoutRating.setVisibility(View.GONE);
        }

        // Buttons
        h.btnViewProfile.setOnClickListener(v -> { if (listener != null) listener.onViewProfile(p); });
        h.btnBookService.setOnClickListener(v -> { if (listener != null) listener.onBookService(p); });

        // Whole card click → view profile
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onViewProfile(p); });
    }

    private String notEmpty(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    private int parseExperienceYears(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.US)
                .replace("years", "")
                .replace("year", "")
                .replace("yrs", "")
                .replace("yr", "")
                .trim();
        if (s.isEmpty()) return 0;
        try {
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Math.max(0, Integer.parseInt(digits));
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView      ivBusinessImage;
        TextView       tvBusinessName, tvProviderName, tvCity, tvCategoryBadge,
                       tvExperience, tvDescription, tvRating;
        LinearLayout   layoutVerifiedBadge, layoutRating;
        MaterialButton btnViewProfile, btnBookService;

        VH(View v) {
            super(v);
            ivBusinessImage    = v.findViewById(R.id.ivBusinessImage);
            tvBusinessName     = v.findViewById(R.id.tvBusinessName);
            tvProviderName     = v.findViewById(R.id.tvProviderName);
            tvCity             = v.findViewById(R.id.tvCity);
            tvCategoryBadge    = v.findViewById(R.id.tvCategoryBadge);
            tvExperience       = v.findViewById(R.id.tvExperience);
            tvDescription      = v.findViewById(R.id.tvDescription);
            tvRating           = v.findViewById(R.id.tvRating);
            layoutVerifiedBadge = v.findViewById(R.id.layoutVerifiedBadge);
            layoutRating       = v.findViewById(R.id.layoutRating);
            btnViewProfile     = v.findViewById(R.id.btnViewProfile);
            btnBookService     = v.findViewById(R.id.btnBookService);
        }
    }
}
