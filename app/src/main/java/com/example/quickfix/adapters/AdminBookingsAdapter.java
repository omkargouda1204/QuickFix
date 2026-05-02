package com.example.quickfix.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Booking;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AdminBookingsAdapter — Displays all platform bookings in admin panel.
 * Read-only view: shows customer, provider, service, price, status, date.
 */
public class AdminBookingsAdapter
        extends RecyclerView.Adapter<AdminBookingsAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(Booking booking);
    }

    private final Context ctx;
    private final List<Booking> items = new ArrayList<>();
    private OnItemClickListener listener;

    public AdminBookingsAdapter(Context ctx) {
        this.ctx = ctx;
    }

    public void setOnItemClickListener(OnItemClickListener l) {
        this.listener = l;
    }

    public void setData(List<Booking> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_admin_booking_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Booking b = items.get(pos);

        h.tvServiceName.setText(nonEmpty(b.serviceName, "Service"));
        h.tvCustomerName.setText("Customer: " + nonEmpty(b.customerName, "Customer"));
        h.tvCustomerPhone.setText("Phone: " + nonEmpty(b.customerPhone, "-"));
        h.tvCustomerEmail.setText("Email: " + nonEmpty(b.customerEmail, "-"));
        h.tvProviderName.setText("Provider: " + nonEmpty(b.providerName, "Provider"));

        if (h.tvTechnicianDetails != null) {
            String tName = nonEmpty(b.technicianName, "");
            String tPhone = nonEmpty(b.technicianPhone, "");
            if (!tName.isEmpty() || !tPhone.isEmpty()) {
                h.tvTechnicianDetails.setVisibility(View.VISIBLE);
                h.tvTechnicianDetails.setText("👷 Technician: " + (tName.isEmpty() ? "-" : tName) 
                        + "\n📞 Phone: " + (tPhone.isEmpty() ? "-" : tPhone));
            } else {
                h.tvTechnicianDetails.setVisibility(View.GONE);
            }
        }

        // Date + time
        String dateTime = nonEmpty(b.bookingDate, "");
        if (!b.bookingTime.isEmpty()) dateTime += "  •  " + b.bookingTime;
        h.tvDateTime.setText(dateTime.isEmpty() ? "No date" : dateTime);

        // Price
        h.tvPrice.setText(b.price > 0
                ? String.format(Locale.getDefault(), "₹%.0f", b.price) : "—");

        // Address
        h.tvAddress.setText(nonEmpty(b.address, nonEmpty(b.city, "No address")));
        h.tvCity.setText("City: " + nonEmpty(b.city, "-"));
        h.tvRating.setText(b.reviewRating > 0
                ? String.format(Locale.US, "Rating: %.1f / 5", (double) b.reviewRating)
                : "Rating: -");

        // Status badge
        applyStatus(h.tvStatus, b.status);

        // Category chip
        if (h.tvCategory != null) {
            String cat = nonEmpty(b.category, "");
            h.tvCategory.setVisibility(cat.isEmpty() ? View.GONE : View.VISIBLE);
            h.tvCategory.setText(cat);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(b);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    private void applyStatus(TextView tv, String status) {
        if (tv == null) return;
        String s = status != null ? status.toLowerCase(Locale.US) : "pending";
        tv.setText(s.toUpperCase(Locale.US));
        switch (s) {
            case "accepted":
            case "confirmed":
                tv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DBEAFE")));
                tv.setTextColor(Color.parseColor("#1D4ED8"));
                break;
            case "completed":
                tv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D1FAE5")));
                tv.setTextColor(Color.parseColor("#065F46"));
                break;
            case "cancelled":
                tv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FEE2E2")));
                tv.setTextColor(Color.parseColor("#991B1B"));
                break;
            default: // pending
                tv.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FEF3C7")));
                tv.setTextColor(Color.parseColor("#92400E"));
                break;
        }
    }

    private String nonEmpty(String v, String fallback) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : fallback;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvServiceName, tvCustomerName, tvProviderName,
                 tvDateTime, tvPrice, tvAddress, tvStatus, tvCategory,
                 tvCustomerPhone, tvCustomerEmail, tvCity, tvRating, tvTechnicianDetails;

        VH(View v) {
            super(v);
            tvServiceName  = v.findViewById(R.id.tvServiceName);
            tvCustomerName = v.findViewById(R.id.tvCustomerName);
            tvProviderName = v.findViewById(R.id.tvProviderName);
            tvCustomerPhone = v.findViewById(R.id.tvCustomerPhone);
            tvCustomerEmail = v.findViewById(R.id.tvCustomerEmail);
            tvTechnicianDetails = v.findViewById(R.id.tvTechnicianDetails);
            tvDateTime     = v.findViewById(R.id.tvDateTime);
            tvPrice        = v.findViewById(R.id.tvPrice);
            tvAddress      = v.findViewById(R.id.tvAddress);
            tvCity         = v.findViewById(R.id.tvCity);
            tvRating       = v.findViewById(R.id.tvRating);
            tvStatus       = v.findViewById(R.id.tvStatus);
            tvCategory     = v.findViewById(R.id.tvCategory);
        }
    }
}

