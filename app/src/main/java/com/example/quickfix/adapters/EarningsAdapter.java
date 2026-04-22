package com.example.quickfix.adapters;

import android.content.Context;
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

/**
 * EarningsAdapter — Shows completed bookings as transaction history
 */
public class EarningsAdapter extends RecyclerView.Adapter<EarningsAdapter.EarningViewHolder> {

    private Context context;
    private List<Booking> transactions = new ArrayList<>();

    public EarningsAdapter(Context context) {
        this.context = context;
    }

    public void setTransactions(List<Booking> transactions) {
        this.transactions = transactions != null ? transactions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EarningViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_earning, parent, false);
        return new EarningViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EarningViewHolder holder, int position) {
        Booking booking = transactions.get(position);
        holder.bind(booking);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class EarningViewHolder extends RecyclerView.ViewHolder {
        TextView tvBookingId, tvServiceName, tvCustomerName, tvAmount, tvDate;

        EarningViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingId = itemView.findViewById(R.id.tvBookingId);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
        }

        void bind(Booking booking) {
            String bookingId = booking != null && booking.id != null ? booking.id.trim() : "";
            if (bookingId.isEmpty()) {
                tvBookingId.setText("#--------");
            } else {
                tvBookingId.setText("#" + bookingId.substring(0, Math.min(8, bookingId.length())));
            }

            String serviceName = booking != null && booking.serviceName != null ? booking.serviceName.trim() : "";
            String customerName = booking != null && booking.customerName != null ? booking.customerName.trim() : "";
            double amount = booking != null ? Math.max(0d, booking.price) : 0d;
            String completedAt = booking != null && booking.completedAt != null ? booking.completedAt.trim() : "";
            String bookingDate = booking != null && booking.bookingDate != null ? booking.bookingDate.trim() : "";

            tvServiceName.setText(serviceName.isEmpty() ? "Service" : serviceName);
            tvCustomerName.setText(customerName.isEmpty() ? "Customer" : customerName);
            tvAmount.setText("₹" + String.format(java.util.Locale.getDefault(), "%.0f", amount));

            String displayDate = !completedAt.isEmpty() ? completedAt : bookingDate;
            if (displayDate.length() >= 10) {
                displayDate = displayDate.substring(0, 10);
            }
            tvDate.setText(displayDate.isEmpty() ? "-" : displayDate);
        }
    }
}
