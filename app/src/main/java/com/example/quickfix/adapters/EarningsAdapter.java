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
        TextView tvBookingId, tvServiceName, tvProviderName, tvAmount, tvDate, tvTxnId, tvOrderId, tvStatus, tvServiceId, tvPaymentMode, tvCouponInfo;

        EarningViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBookingId = itemView.findViewById(R.id.tvBookingId);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvProviderName = itemView.findViewById(R.id.tvProviderName);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTxnId = itemView.findViewById(R.id.tvTxnId);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvServiceId = itemView.findViewById(R.id.tvServiceId);
            tvPaymentMode = itemView.findViewById(R.id.tvPaymentMode);
            tvCouponInfo = itemView.findViewById(R.id.tvCouponInfo);
        }

        void bind(Booking booking) {
            String bid = booking.id != null ? booking.id.trim() : "";
            tvBookingId.setText(bid.isEmpty() ? "-" : bid);

            tvServiceName.setText(booking.serviceName != null ? booking.serviceName : "Service");
            String providerName = booking.providerName != null ? booking.providerName.trim() : "";
            tvProviderName.setText("Provider: " + (providerName.isEmpty() ? "Not assigned" : providerName));
            String sid = booking.serviceId != null ? booking.serviceId.trim() : "";
            tvServiceId.setText(sid.isEmpty() ? "-" : sid);

            double amount = booking.finalAmount > 0 ? booking.finalAmount : (booking.paymentAmount > 0 ? booking.paymentAmount : booking.price);
            tvAmount.setText("₹" + String.format(Locale.US, "%.2f", amount));

            if (tvCouponInfo != null) {
                if (booking.couponCode != null && !booking.couponCode.isEmpty()) {
                    tvCouponInfo.setVisibility(View.VISIBLE);
                    tvCouponInfo.setText("Coupon: " + booking.couponCode + " (-₹" + String.format(Locale.US, "%.2f", booking.couponDiscount) + ")");
                } else {
                    tvCouponInfo.setVisibility(View.GONE);
                }
            }

            tvDate.setText(formatDateTime(booking));

            String txnId = booking.paymentId != null ? booking.paymentId.trim() : "";
            if (txnId.isEmpty()) {
                txnId = booking.id != null ? booking.id.trim() : "";
            }
            tvTxnId.setText(txnId.isEmpty() ? "N/A" : txnId);
            String orderId = booking.paymentOrderId == null ? "" : booking.paymentOrderId.trim();
            tvOrderId.setText(orderId.isEmpty() ? "Order: -" : "Order: " + orderId);

            String paymentMode = normalizePaymentMode(booking.paymentMode, booking.status, booking.paymentStatus);
            tvPaymentMode.setText(paymentMode);

            String status = booking.paymentStatus != null ? booking.paymentStatus.trim() : "";
            if (status.isEmpty()) {
                status = booking.status != null ? booking.status : "pending";
            }
            String normalizedStatus = normalizeStatus(status);
            tvStatus.setText(normalizedStatus);

            if ("SUCCESS".equals(normalizedStatus)) {
                tvStatus.setBackgroundResource(R.drawable.badge_completed);
            } else if ("FAILED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
                tvStatus.setBackgroundResource(R.drawable.badge_cancelled);
            } else {
                tvStatus.setBackgroundResource(R.drawable.badge_pending);
            }
        }

        private String normalizeStatus(String rawStatus) {
            String s = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.US);
            if ("paid".equals(s) || "success".equals(s) || "completed".equals(s)) return "SUCCESS";
            if ("failed".equals(s) || "failure".equals(s) || "cancelled".equals(s)) return "FAILED";
            if (s.isEmpty()) return "PENDING";
            return s.toUpperCase(Locale.US);
        }

        private String normalizePaymentMode(String rawMode, String bookingStatus, String paymentStatus) {
            String mode = rawMode == null ? "" : rawMode.trim();
            if (!mode.isEmpty()) {
                String lower = mode.toLowerCase(Locale.US);
                if (lower.contains("upi")) return "UPI";
                if (lower.contains("card")) return "Card";
                if (lower.contains("cash")) return "Cash";
                return Character.toUpperCase(mode.charAt(0)) + mode.substring(1).toLowerCase(Locale.US);
            }
            String pay = paymentStatus == null ? "" : paymentStatus.trim().toLowerCase(Locale.US);
            String book = bookingStatus == null ? "" : bookingStatus.trim().toLowerCase(Locale.US);
            if ("success".equals(pay) || "paid".equals(book)) return "Online";
            return "Pending";
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
