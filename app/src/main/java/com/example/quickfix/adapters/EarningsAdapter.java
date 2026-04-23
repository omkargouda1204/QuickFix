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
import java.util.Locale;

/**
 * EarningsAdapter — Shows bookings as transaction history
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
        TextView tvBookingId, tvServiceName, tvAmount, tvDate, tvTxnId, tvStatus;

        EarningViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingId = itemView.findViewById(R.id.tvBookingId);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTxnId = itemView.findViewById(R.id.tvTxnId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(Booking booking) {
            String bid = booking.id != null ? booking.id.trim() : "";
            tvBookingId.setText(bid.length() > 8 ? "#" + bid.substring(0, 8) : "#" + bid);
            
            tvServiceName.setText(booking.serviceName != null ? booking.serviceName : "Service");
            
            double amount = booking.paymentAmount > 0 ? booking.paymentAmount : booking.price;
            tvAmount.setText("₹" + String.format(Locale.US, "%.0f", amount));

            String displayDate = booking.completedAt != null && !booking.completedAt.isEmpty() 
                    ? booking.completedAt : (booking.bookingDate != null ? booking.bookingDate : "");
            if (displayDate.length() >= 10) displayDate = displayDate.substring(0, 10);
            tvDate.setText(displayDate);

            String txnId = booking.paymentId != null ? booking.paymentId : "";
            tvTxnId.setText(txnId.isEmpty() ? "N/A" : txnId);

            String status = booking.status != null ? booking.status.toUpperCase(Locale.US) : "COMPLETED";
            tvStatus.setText(status);
            
            if ("COMPLETED".equals(status) || "PAID".equals(status)) {
                tvStatus.setBackgroundResource(R.drawable.badge_completed);
            } else {
                tvStatus.setBackgroundResource(R.drawable.badge_pending);
            }
        }
    }
}
