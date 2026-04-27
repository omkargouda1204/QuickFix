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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

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
        TextView tvBookingId, tvServiceName, tvProviderName, tvAmount, tvDate, tvTxnId, tvStatus;

        EarningViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingId = itemView.findViewById(R.id.tvBookingId);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvProviderName = itemView.findViewById(R.id.tvProviderName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTxnId = itemView.findViewById(R.id.tvTxnId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(Booking booking) {
            String bid = booking.id != null ? booking.id.trim() : "";
            tvBookingId.setText(bid.length() > 8 ? "#" + bid.substring(0, 8) : "#" + bid);
            
            tvServiceName.setText(booking.serviceName != null ? booking.serviceName : "Service");
            String providerName = booking.providerName != null ? booking.providerName.trim() : "";
            tvProviderName.setText("Provider: " + (providerName.isEmpty() ? "Not assigned" : providerName));

            double amount = booking.paymentAmount > 0 ? booking.paymentAmount : booking.price;
            tvAmount.setText("₹" + String.format(Locale.US, "%.0f", amount));

            tvDate.setText(formatDateTime(booking));

            String txnId = booking.paymentId != null ? booking.paymentId.trim() : "";
            if (txnId.isEmpty()) {
                txnId = booking.id != null ? booking.id.trim() : "";
            }
            tvTxnId.setText(txnId.isEmpty() ? "N/A" : txnId);

            String status = booking.paymentStatus != null ? booking.paymentStatus.trim() : "";
            if (status.isEmpty()) {
                status = booking.status != null ? booking.status : "pending";
            }
            status = status.toUpperCase(Locale.US);
            tvStatus.setText(status);
            
            if ("COMPLETED".equals(status) || "PAID".equals(status) || "SUCCESS".equals(status)) {
                tvStatus.setBackgroundResource(R.drawable.badge_completed);
            } else {
                tvStatus.setBackgroundResource(R.drawable.badge_pending);
            }
        }

        private String formatDateTime(Booking booking) {
            String raw = pickDateSource(booking);
            if (raw == null || raw.trim().isEmpty()) {
                return "--";
            }
            String trimmed = raw.trim();
            String parsed = formatIsoDate(trimmed);
            if (!parsed.isEmpty()) {
                return parsed;
            }
            if (booking.bookingDate != null && !booking.bookingDate.trim().isEmpty()
                    && booking.bookingTime != null && !booking.bookingTime.trim().isEmpty()) {
                return booking.bookingDate.trim() + " " + booking.bookingTime.trim();
            }
            return trimmed;
        }

        private String pickDateSource(Booking booking) {
            if (booking.completedAt != null && !booking.completedAt.trim().isEmpty()) return booking.completedAt;
            if (booking.createdAt != null && !booking.createdAt.trim().isEmpty()) return booking.createdAt;
            if (booking.bookingDate != null && !booking.bookingDate.trim().isEmpty()) {
                if (booking.bookingTime != null && !booking.bookingTime.trim().isEmpty()) {
                    return booking.bookingDate.trim() + " " + booking.bookingTime.trim();
                }
                return booking.bookingDate;
            }
            return "";
        }

        private String formatIsoDate(String raw) {
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                    "yyyy-MM-dd'T'HH:mm:ssX",
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd"
            };
            for (String p : patterns) {
                try {
                    SimpleDateFormat input = new SimpleDateFormat(p, Locale.US);
                    if (p.contains("X")) {
                        input.setTimeZone(TimeZone.getTimeZone("UTC"));
                    }
                    Date date = input.parse(raw);
                    if (date == null) continue;
                    return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(date);
                } catch (Exception ignored) {
                }
            }
            return "";
        }
    }
}
