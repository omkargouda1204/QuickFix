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
            tvBookingId.setText("#" + booking.id.substring(0, Math.min(8, booking.id.length())));
            tvServiceName.setText(booking.serviceName.isEmpty() ? "Service" : booking.serviceName);
            tvCustomerName.setText(booking.customerName.isEmpty() ? "Customer" : booking.customerName);
            tvAmount.setText("₹" + String.format("%.0f", booking.price));
            tvDate.setText(booking.completedAt.isEmpty() ? booking.bookingDate : booking.completedAt.substring(0, 10));
        }
    }
}

