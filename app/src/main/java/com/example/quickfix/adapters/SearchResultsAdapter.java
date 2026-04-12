package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.SearchResult;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.VH> {

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    private final Context ctx;
    private List<SearchResult> items = new ArrayList<>();
    private final OnResultClickListener listener;

    public SearchResultsAdapter(Context ctx, OnResultClickListener listener) {
        this.ctx      = ctx;
        this.listener = listener;
    }

    public void setResults(List<SearchResult> list) {
        List<SearchResult> newItems = list == null ? new ArrayList<>() : new ArrayList<>(list);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return items.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                SearchResult oldItem = items.get(oldItemPosition);
                SearchResult newItem = newItems.get(newItemPosition);
                if (oldItem.id != null && newItem.id != null) {
                    return oldItem.id.equals(newItem.id);
                }
                return oldItem == newItem;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SearchResult oldItem = items.get(oldItemPosition);
                SearchResult newItem = newItems.get(newItemPosition);
                return eq(oldItem.name, newItem.name)
                        && eq(oldItem.subtitle, newItem.subtitle)
                        && eq(oldItem.city, newItem.city)
                        && eq(oldItem.type, newItem.type)
                        && oldItem.rating == newItem.rating
                        && oldItem.distanceKm == newItem.distanceKm
                        && eq(oldItem.availabilityStatus, newItem.availabilityStatus)
                        && eq(oldItem.profileImage, newItem.profileImage)
                        && oldItem.isVerified == newItem.isVerified;
            }
        });

        items = newItems;
        diff.dispatchUpdatesTo(this);
    }

    public void clear() {
        int previousSize = items.size();
        if (previousSize == 0) {
            return;
        }
        items = new ArrayList<>();
        notifyItemRangeRemoved(0, previousSize);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_search_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchResult r = items.get(pos);

        h.tvName.setText(r.name == null ? "" : r.name.trim());

        String subtitle = r.subtitle == null ? "" : r.subtitle.trim();
        h.tvSubtitle.setText(subtitle);

        String city = r.city == null ? "" : r.city.trim();
        h.tvCity.setText(city);
        h.tvCity.setVisibility(city.isEmpty() ? View.GONE : View.VISIBLE);

        if (r.profileImage != null && !r.profileImage.trim().isEmpty()) {
            Glide.with(ctx)
                    .load(r.profileImage)
                    .placeholder(R.drawable.ic_business)
                    .error(R.drawable.ic_business)
                    .into(h.ivIcon);
        } else {
            h.ivIcon.setImageResource(R.drawable.ic_business);
        }

        h.tvType.setBackgroundResource(R.drawable.bg_role_badge_provider);
        h.tvType.setTextColor(0xFF2E7D32);
        String typeLabel = SearchResult.TYPE_SERVICE.equals(r.type) ? "Service" : "Provider";
        h.tvType.setText(r.isVerified ? "✔ " + typeLabel : typeLabel);

        h.tvAvailability.setVisibility(View.VISIBLE);

        boolean hasRating = r.rating > 0f;
        h.tvRating.setVisibility(r.rating > 0f ? View.VISIBLE : View.GONE);
        h.tvRating.setText(hasRating ? String.format(Locale.getDefault(), "%.1f", r.rating) : "");

        boolean hasDistance = r.distanceKm >= 0f;
        h.tvDistance.setVisibility(hasDistance ? View.VISIBLE : View.GONE);
        h.tvDistance.setText(hasDistance
                ? String.format(Locale.getDefault(), "%.1f km", r.distanceKm)
                : "");

        h.tvAvailability.setText((r.availabilityStatus == null || r.availabilityStatus.isEmpty())
                ? "Available"
                : r.availabilityStatus);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onResultClick(r); });
    }

    @Override public int getItemCount() { return items.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvName, tvSubtitle, tvCity, tvType, tvRating, tvDistance, tvAvailability;
        VH(View v) {
            super(v);
            ivIcon         = v.findViewById(R.id.ivResultIcon);
            tvName         = v.findViewById(R.id.tvResultName);
            tvSubtitle     = v.findViewById(R.id.tvResultSubtitle);
            tvCity         = v.findViewById(R.id.tvResultCity);
            tvType         = v.findViewById(R.id.tvResultType);
            tvRating       = v.findViewById(R.id.tvResultRating);
            tvDistance     = v.findViewById(R.id.tvResultDistance);
            tvAvailability = v.findViewById(R.id.tvResultAvailability);
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
