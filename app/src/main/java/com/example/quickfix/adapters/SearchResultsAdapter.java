package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.VH> {

    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    private final Context ctx;
    private List<SearchResult> items = new ArrayList<>();
    private OnResultClickListener listener;

    public SearchResultsAdapter(Context ctx, OnResultClickListener listener) {
        this.ctx      = ctx;
        this.listener = listener;
    }

    public void setResults(List<SearchResult> list) {
        items = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_search_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchResult r = items.get(pos);
        h.tvName.setText(r.name);
        h.tvSubtitle.setText(r.subtitle);
        h.tvCity.setText(r.city != null && !r.city.isEmpty() ? r.city : "-");
        h.tvType.setText(r.type);

        if (SearchResult.TYPE_PROVIDER.equals(r.type)) {
            h.tvType.setBackgroundResource(R.drawable.bg_role_badge_provider);
            h.tvType.setTextColor(0xFF2E7D32);
            h.ivIcon.setImageResource(R.drawable.ic_business);
            h.tvAvailability.setVisibility(View.VISIBLE);
            h.tvDistance.setVisibility(View.VISIBLE);
            h.tvRating.setVisibility(r.rating > 0f ? View.VISIBLE : View.GONE);
            h.tvRating.setText(String.format(Locale.getDefault(), "%.1f", r.rating));
            h.tvDistance.setText(r.distanceKm >= 0f
                    ? String.format(Locale.getDefault(), "%.1f km", r.distanceKm)
                    : "-");
            h.tvAvailability.setText((r.availabilityStatus == null || r.availabilityStatus.isEmpty())
                    ? "Available"
                    : r.availabilityStatus);
        } else {
            h.tvType.setBackgroundResource(R.drawable.bg_role_badge_user);
            h.tvType.setTextColor(0xFF1565C0);
            h.ivIcon.setImageResource(R.drawable.ic_book_service);
            h.tvRating.setVisibility(View.GONE);
            h.tvDistance.setVisibility(View.GONE);
            h.tvAvailability.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onResultClick(r); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
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
}
