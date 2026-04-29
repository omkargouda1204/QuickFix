package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.TopBookedService;

import java.util.ArrayList;
import java.util.List;

public class TopBookedAdapter extends RecyclerView.Adapter<TopBookedAdapter.VH> {

    public interface OnServiceClickListener {
        void onServiceClick(TopBookedService service);
    }

    private final Context ctx;
    private final List<TopBookedService> items;
    private final OnServiceClickListener listener;

    public TopBookedAdapter(Context ctx, List<TopBookedService> items, OnServiceClickListener listener) {
        this.ctx = ctx;
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_top_booked_service, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TopBookedService s = items.get(pos);

        h.tvName.setText(s.category);
        h.tvBookings.setText(s.totalBookings + " Bookings");
        h.tvPrice.setText(String.format(java.util.Locale.US, "₹%.0f", s.startingPrice));
        h.tvRating.setText(String.format(java.util.Locale.US, "%.1f", s.rating));

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onServiceClick(s); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvBookings, tvPrice, tvRating;

        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvServiceName);
            tvBookings = v.findViewById(R.id.tvBookingCount);
            tvPrice = v.findViewById(R.id.tvStartingPrice);
            tvRating = v.findViewById(R.id.tvServiceRating);
        }
    }
}
