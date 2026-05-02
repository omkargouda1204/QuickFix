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
import com.example.quickfix.ProviderProfile;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class TopRatedAdapter extends RecyclerView.Adapter<TopRatedAdapter.VH> {

    public interface OnProviderClickListener {
        void onProviderClick(ProviderProfile provider);
    }

    private final Context ctx;
    private final List<ProviderProfile> items;
    private final OnProviderClickListener listener;
    private double userLat = Double.NaN;
    private double userLng = Double.NaN;
    private String displayMode = "TopRated"; // TopRated | TopBooked | Default

    public TopRatedAdapter(Context ctx, List<ProviderProfile> items, OnProviderClickListener listener) {
        this.ctx = ctx;
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    public void setMode(String mode) {
        this.displayMode = mode == null ? "TopRated" : mode;
        notifyDataSetChanged();
    }

    public void setUserLocation(double lat, double lng) {
        this.userLat = lat;
        this.userLng = lng;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_top_rated_provider, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ProviderProfile p = items.get(pos);

        h.tvName.setText(p.businessName != null && !p.businessName.isEmpty() ? p.businessName : p.ownerName);
        
        // Hide category as requested
        h.tvCategory.setVisibility(View.GONE);

        // Display rules:
        // - TopRated: show only rating
        // - TopBooked: show only profile (no rating/count)
        // - Default: show rating and bookings where available
        if ("TopRated".equalsIgnoreCase(displayMode)) {
            if (h.tvBookings != null) h.tvBookings.setVisibility(View.GONE);
            h.ratingContainer.setVisibility(View.VISIBLE);
            h.tvRating.setVisibility(View.VISIBLE);
            h.tvRating.setText(String.format(java.util.Locale.US, "%.1f", p.rating));
            h.tvDistance.setVisibility(View.GONE);
        } else if ("TopBooked".equalsIgnoreCase(displayMode)) {
            // Minimal profile card — hide rating (with star) and bookings
            if (h.tvBookings != null) h.tvBookings.setVisibility(View.GONE);
            h.ratingContainer.setVisibility(View.GONE);
            h.tvDistance.setVisibility(View.GONE);
        } else {
            // Default behavior
            if (h.tvBookings != null) {
                h.tvBookings.setText(ctx.getString(R.string.bookings_count_suffix, p.totalBookings));
                h.tvBookings.setVisibility(View.VISIBLE);
            }
            h.ratingContainer.setVisibility(View.VISIBLE);
            h.tvRating.setVisibility(View.VISIBLE);
            h.tvRating.setText(String.format(java.util.Locale.US, "%.1f", p.rating));
            if (!Double.isNaN(userLat) && !Double.isNaN(userLng) && !Double.isNaN(p.latitude) && !Double.isNaN(p.longitude)) {
                double dist = calculateDistance(userLat, userLng, p.latitude, p.longitude);
                h.tvDistance.setText(String.format(java.util.Locale.US, "%.1f km", dist));
                h.tvDistance.setVisibility(View.VISIBLE);
            } else {
                h.tvDistance.setVisibility(View.GONE);
            }
        }

        String imgUrl = (p.profileImageUrl != null && !p.profileImageUrl.isEmpty()) ? p.profileImageUrl : p.businessImageUrl;
        Glide.with(ctx)
                .load(imgUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(h.ivProfile);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onProviderClick(p); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivProfile;
        TextView tvName, tvCategory, tvRating, tvDistance, tvBookings;
        View ratingContainer;

        VH(View v) {
            super(v);
            ivProfile = v.findViewById(R.id.ivProviderProfile);
            tvName = v.findViewById(R.id.tvProviderName);
            tvCategory = v.findViewById(R.id.tvProviderCategory);
            tvRating = v.findViewById(R.id.tvProviderRating);
            tvDistance = v.findViewById(R.id.tvProviderDistance);
            tvBookings = v.findViewById(R.id.tvProviderBookings);
            ratingContainer = v.findViewById(R.id.ratingContainer);
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        dist = dist * 60 * 1.1515 * 1.609344;
        return dist;
    }
}
