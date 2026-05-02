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
import java.util.Locale;

/**
 * UserBookingsAdapter — Shows customer/user bookings with details, cancel, review & provider profile link.
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
        void onMessageBooking(Booking booking);
        void onProviderClick(Booking booking);
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
        TextView tvServiceName, tvProviderName, tvDateTime, tvAddress, tvStatus, tvPrice, tvReviewSummary, tvTechnicianDetails;
        MaterialButton btnViewDetails, btnCancel, btnReview, btnPayNow, btnMessage;

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
            tvTechnicianDetails = v.findViewById(R.id.tvTechnicianDetails);
            btnViewDetails = v.findViewById(R.id.btnViewDetails);
            btnCancel = v.findViewById(R.id.btnCancel);
            btnReview = v.findViewById(R.id.btnReview);
            btnPayNow = v.findViewById(R.id.btnPayNow);
            btnMessage = v.findViewById(R.id.btnMessage);
        }

        void bind(Booking b) {
            String serviceTitle = b.serviceName == null || b.serviceName.isEmpty() ? "Service" : b.serviceName;
            if (b.isEmergency) {
                serviceTitle = "EMERGENCY - " + serviceTitle;
            }
            tvServiceName.setText(serviceTitle);
            
            String pName = b.providerName;
            if (pName == null || pName.isEmpty() || "null".equalsIgnoreCase(pName) || "Awaiting Acceptance".equalsIgnoreCase(pName)) {
                pName = "Waiting for provider...";
            }
            tvProviderName.setText("By: " + pName);
            tvProviderName.setOnClickListener(v -> {
                if (listener != null) listener.onProviderClick(b);
            });

            String dateTime = (b.bookingDate == null || b.bookingDate.isEmpty() ? "-" : b.bookingDate)
                    + (b.bookingTime == null || b.bookingTime.isEmpty() ? "" : " • " + b.bookingTime);
            tvDateTime.setText(dateTime);
            tvAddress.setText(b.address == null || b.address.isEmpty() ? "-" : cleanLocation(b.address));

            // Status badge
            String status = b.status == null ? "" : b.status.toLowerCase(Locale.US);
            tvStatus.setText(formatStatus(status));
            
            // Service Person Details
            if (("accepted".equals(status) || "price_set".equals(status) || "paid".equals(status) || "completed".equals(status)) 
                    && (b.technicianName != null && !b.technicianName.isEmpty() || b.technicianPhone != null && !b.technicianPhone.isEmpty())) {
                tvTechnicianDetails.setVisibility(View.VISIBLE);
                String tName = (b.technicianName == null || b.technicianName.isEmpty()) ? "-" : b.technicianName;
                String tPhone = (b.technicianPhone == null || b.technicianPhone.isEmpty()) ? "-" : b.technicianPhone;
                tvTechnicianDetails.setText("Technician: " + tName + "\nMobile: " + tPhone);
            } else {
                tvTechnicianDetails.setVisibility(View.GONE);
            }
            
            double shownAmount = b.paymentAmount > 0 ? b.paymentAmount : b.price;
            tvPrice.setText("₹" + String.format(Locale.US, "%.0f", shownAmount));

            applyStatusStyle(status);

            // Logic: Cancel only for pending
            btnCancel.setVisibility("pending".equals(status) ? View.VISIBLE : View.GONE);

            // Logic: Review ONLY if COMPLETED
            boolean isCompleted = "completed".equals(status);
            btnReview.setVisibility(isCompleted ? View.VISIBLE : View.GONE);

            // Logic: Pay Now for price_set and not already paid
            boolean paymentDone = "success".equalsIgnoreCase(b.paymentStatus) || "paid".equalsIgnoreCase(status);
            boolean canPayNow = "price_set".equals(status) && !paymentDone;
            btnPayNow.setVisibility(canPayNow ? View.VISIBLE : View.GONE);
            
            btnMessage.setVisibility(View.VISIBLE);
            btnMessage.setEnabled(!isCompleted);
            btnMessage.setAlpha(isCompleted ? 0.5f : 1f);

            if (isCompleted && b.reviewRating > 0) {
                String summary = "Your review: " + b.reviewRating + "/5"
                        + (b.reviewComment == null || b.reviewComment.trim().isEmpty()
                        ? "" : " - " + b.reviewComment.trim());
                tvReviewSummary.setText(summary);
                tvReviewSummary.setVisibility(View.VISIBLE);
                btnReview.setText("Edit Review");
            } else {
                tvReviewSummary.setVisibility(View.GONE);
                btnReview.setText("Write Review");
            }

            btnViewDetails.setOnClickListener(v -> { if (listener != null) listener.onViewDetails(b); });
            btnCancel.setOnClickListener(v -> { if (listener != null) listener.onCancelBooking(b); });
            btnReview.setOnClickListener(v -> { if (listener != null) listener.onWriteReview(b); });
            btnPayNow.setOnClickListener(v -> { if (listener != null) listener.onPayNow(b); });
            btnMessage.setOnClickListener(v -> { if (listener != null && btnMessage.isEnabled()) listener.onMessageBooking(b); });
        }

        private String cleanLocation(String address) {
            if (address == null) return "";
            return address.replaceAll("[A-Z0-9\\+]{6,}", "").trim();
        }

        private void applyStatusStyle(String status) {
            switch (status) {
                case "pending":
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text));
                    break;
                case "accepted":
                case "confirmed":
                case "price_set":
                case "paid":
                    tvStatus.setBackgroundResource(R.drawable.badge_accepted);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.primary));
                    break;
                case "completed":
                    tvStatus.setBackgroundResource(R.drawable.badge_completed);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_approved_text));
                    break;
                case "cancelled":
                case "rejected":
                    tvStatus.setBackgroundResource(R.drawable.badge_cancelled);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_rejected_text));
                    break;
                default:
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_neutral_text));
                    break;
            }
        }

        private String formatStatus(String status) {
            if ("price_set".equals(status)) return "ACCEPTED";
            if ("paid".equals(status)) return "ACCEPTED";
            if ("rejected".equals(status)) return "CANCELLED";
            return status == null ? "" : status.toUpperCase(Locale.US);
        }
    }
}
