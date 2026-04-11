package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Booking;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * UserBookingsAdapter — Shows customer/user bookings with view details & cancel.
 */
public class UserBookingsAdapter extends RecyclerView.Adapter<UserBookingsAdapter.VH> {

    private final Context context;
    private List<Booking> bookings = new ArrayList<>();
    private final BookingActionListener listener;

    public interface BookingActionListener {
        void onViewDetails(Booking booking);
        void onCancelBooking(Booking booking);
        void onWriteReview(Booking booking);
        void onPayNow(Booking booking);
    }

    public UserBookingsAdapter(Context context, BookingActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setBookings(List<Booking> list) {
        bookings = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_booking_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(bookings.get(position));
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    public class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvServiceName, tvProviderName, tvDateTime, tvAddress, tvStatus, tvPrice, tvReviewSummary;
        MaterialButton btnViewDetails, btnCancel, btnReview, btnPayNow;

        public VH(@NonNull View v) {
            super(v);
            card = v.findViewById(R.id.cardBooking);
            tvServiceName = v.findViewById(R.id.tvServiceName);
            tvProviderName = v.findViewById(R.id.tvProviderName);
            tvDateTime = v.findViewById(R.id.tvDateTime);
            tvAddress = v.findViewById(R.id.tvAddress);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvPrice = v.findViewById(R.id.tvPrice);
            tvReviewSummary = v.findViewById(R.id.tvReviewSummary);
            btnViewDetails = v.findViewById(R.id.btnViewDetails);
            btnCancel = v.findViewById(R.id.btnCancel);
            btnReview = v.findViewById(R.id.btnReview);
            btnPayNow = v.findViewById(R.id.btnPayNow);
        }

        void bind(Booking b) {
            tvServiceName.setText(b.serviceName.isEmpty() ? "Service" : b.serviceName);
            tvProviderName.setText(b.providerName.isEmpty() ? "Provider" : "By: " + b.providerName);
            String dateTime = (b.bookingDate.isEmpty() ? "-" : b.bookingDate)
                    + (b.bookingTime.isEmpty() ? "" : " • " + b.bookingTime);
            tvDateTime.setText(dateTime);
            tvAddress.setText(b.address.isEmpty() ? "-" : b.address);
            tvPrice.setText(b.price > 0 ? "₹" + String.format("%.0f", b.price) : "");

            // Status badge
            String status = b.status == null ? "" : b.status.toLowerCase();
            tvStatus.setText(b.status == null ? "" : b.status.toUpperCase());
            switch (status) {
                case "pending":
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text));
                    break;
                case "accepted":
                case "confirmed":
                    tvStatus.setBackgroundResource(R.drawable.badge_accepted);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.primary));
                    break;
                case "completed":
                    tvStatus.setBackgroundResource(R.drawable.badge_completed);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_approved_text));
                    break;
                case "cancelled":
                    tvStatus.setBackgroundResource(R.drawable.badge_cancelled);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_rejected_text));
                    break;
                default:
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_neutral_text));
                    break;
            }

            // Cancel only for pending
            boolean canCancel = "pending".equals(status);
            btnCancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);

            boolean isCompleted = "completed".equals(status);
            btnReview.setVisibility(isCompleted ? View.VISIBLE : View.GONE);

            boolean paymentDone = "success".equalsIgnoreCase(b.paymentStatus) || "paid".equalsIgnoreCase(status);
            boolean canPayNow = "price_set".equals(status) && !paymentDone;
            btnPayNow.setVisibility(canPayNow ? View.VISIBLE : View.GONE);
            if (isCompleted) {
                boolean hasReview = b.reviewRating > 0;
                btnReview.setText(context.getString(
                        hasReview ? R.string.review_edit_button : R.string.review_write_button
                ));
                if (hasReview) {
                    String summary = "Your review: " + b.reviewRating + "/5"
                            + (b.reviewComment == null || b.reviewComment.trim().isEmpty()
                            ? ""
                            : " - " + b.reviewComment.trim());
                    tvReviewSummary.setText(summary);
                    tvReviewSummary.setVisibility(View.VISIBLE);
                } else {
                    tvReviewSummary.setVisibility(View.GONE);
                }
            } else {
                tvReviewSummary.setVisibility(View.GONE);
            }

            btnViewDetails.setOnClickListener(v -> {
                if (listener != null) listener.onViewDetails(b);
            });
            btnCancel.setOnClickListener(v -> {
                if (listener != null) listener.onCancelBooking(b);
            });
            btnReview.setOnClickListener(v -> {
                if (listener != null) listener.onWriteReview(b);
            });
            btnPayNow.setOnClickListener(v -> {
                if (listener != null) listener.onPayNow(b);
            });
        }
    }
}
