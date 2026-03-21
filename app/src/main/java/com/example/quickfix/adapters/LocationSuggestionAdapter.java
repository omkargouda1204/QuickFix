package com.example.quickfix.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.LocationSuggestion;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocationSuggestionAdapter extends RecyclerView.Adapter<LocationSuggestionAdapter.VH> {

    public interface Listener {
        void onPick(LocationSuggestion suggestion);
    }

    private final List<LocationSuggestion> items = new ArrayList<>();
    private final Listener listener;

    public LocationSuggestionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<LocationSuggestion> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LocationSuggestion item = items.get(position);
        h.tvCity.setText(item.displayTitle());
        h.tvState.setText(item.displaySubtitle());

        if (item.servicesCount > 0) {
            h.tvServicesCount.setVisibility(View.VISIBLE);
            h.tvServicesCount.setText(item.servicesCount + (item.servicesCount == 1
                    ? " provider"
                    : " providers"));
        } else {
            h.tvServicesCount.setVisibility(View.GONE);
        }

        h.tvNearby.setVisibility(item.nearby ? View.VISIBLE : View.GONE);
        if (item.nearby && item.distanceKm > 0f) {
            h.tvDistance.setVisibility(View.VISIBLE);
            h.tvDistance.setText(String.format(Locale.getDefault(), "%.1f km", item.distanceKm));
        } else {
            h.tvDistance.setVisibility(View.GONE);
        }
        h.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvCity;
        TextView tvState;
        TextView tvServicesCount;
        TextView tvNearby;
        TextView tvDistance;

        VH(@NonNull View itemView) {
            super(itemView);
            tvCity = itemView.findViewById(R.id.tvRowCity);
            tvState = itemView.findViewById(R.id.tvRowState);
            tvServicesCount = itemView.findViewById(R.id.tvRowServicesCount);
            tvNearby = itemView.findViewById(R.id.tvNearbyBadge);
            tvDistance = itemView.findViewById(R.id.tvRowDistance);
        }
    }
}
