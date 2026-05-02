package com.example.quickfix.adapters;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Booking;
 import com.example.quickfix.ProviderEmergencyRequestActivity;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * BookingsAdapter — Shows provider's bookings with action buttons
 */
public class BookingsAdapter extends RecyclerView.Adapter<BookingsAdapter.BookingViewHolder> {

    private Context context;
    private List<Booking> bookings = new ArrayList<>();
    private BookingActionListener listener;

    public interface BookingActionListener {
        void onAccept(Booking booking);
        void onReject(Booking booking);
        void onMarkCompleted(Booking booking);
        void onSetPrice(Booking booking, double amount);
        void onMessageBooking(Booking booking);
    }

    public BookingsAdapter(Context context, BookingActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setBookings(List<Booking> bookings) {
        this.bookings = bookings != null ? bookings : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_booking_card, parent, false);
        return new BookingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
        Booking booking = bookings.get(position);
        holder.bind(booking);
    }

    @Override
    public int getItemCount() {
        return bookings.size();
    }

    class BookingViewHolder extends RecyclerView.ViewHolder {
        TextView tvCustomerName, tvServiceName, tvDateTime, tvAddress, tvPrice, tvStatus, tvTechnicianDetails;
        MaterialButton btnAccept, btnReject, btnMarkCompleted, btnCall, btnMessage;
        MaterialButton btnSetPrice;
        EditText etSetAmount;
        LinearLayout layoutPriceSetter;
        TextView tvWaitingForPayment;
        CardView cardBooking;

        BookingViewHolder(@NonNull View itemView) {
            super(itemView);
            cardBooking = itemView.findViewById(R.id.cardBooking);
            tvCustomerName = itemView.findViewById(R.id.tvCustomerName);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTechnicianDetails = itemView.findViewById(R.id.tvTechnicianDetails);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnMarkCompleted = itemView.findViewById(R.id.btnMarkCompleted);
            btnCall = itemView.findViewById(R.id.btnCall);
            btnMessage = itemView.findViewById(R.id.btnMessage);
            btnSetPrice = itemView.findViewById(R.id.btnSetPrice);
            etSetAmount = itemView.findViewById(R.id.etSetAmount);
            layoutPriceSetter = itemView.findViewById(R.id.layoutPriceSetter);
            tvWaitingForPayment = itemView.findViewById(R.id.tvWaitingForPayment);
        }

        void bind(Booking booking) {
            tvCustomerName.setText(booking.customerName.isEmpty() ? "Customer" : booking.customerName);
            String serviceTitle = booking.serviceName.isEmpty() ? "Service" : booking.serviceName;
            if (booking.isEmergency) {
                serviceTitle = "EMERGENCY - " + serviceTitle;
            }
            tvServiceName.setText(serviceTitle);
            tvDateTime.setText(booking.bookingDate + " • " + booking.bookingTime);
            tvAddress.setText(booking.address.isEmpty() ? "No address" : cleanLocation(booking.address));
            double shownAmount = booking.paymentAmount > 0 ? booking.paymentAmount : booking.price;
            tvPrice.setText("Amount: ₹" + String.format(Locale.US, "%.0f", shownAmount));
            String status = booking.status == null ? "" : booking.status.trim().toLowerCase(Locale.US);
            tvStatus.setText(formatStatus(status));

            // Service Person Details
            if (("accepted".equals(status) || "price_set".equals(status) || "paid".equals(status) || "completed".equals(status)) 
                    && (booking.technicianName != null && !booking.technicianName.isEmpty() || booking.technicianPhone != null && !booking.technicianPhone.isEmpty())) {
                if (tvTechnicianDetails != null) {
                    tvTechnicianDetails.setVisibility(View.VISIBLE);
                    String tName = (booking.technicianName == null || booking.technicianName.isEmpty()) ? "-" : booking.technicianName;
                    String tPhone = (booking.technicianPhone == null || booking.technicianPhone.isEmpty()) ? "-" : booking.technicianPhone;
                    tvTechnicianDetails.setText("Technician: " + tName + "\nMobile: " + tPhone);
                }
            } else {
                if (tvTechnicianDetails != null) tvTechnicianDetails.setVisibility(View.GONE);
            }

            // Status badge color
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
                case "price_set":
                case "paid":
                    tvStatus.setBackgroundResource(R.drawable.badge_accepted);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.primary));
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

            // Show/hide action buttons based on status
            if ("pending".equals(status)) {
                if (booking.isEmergency) {
                    btnAccept.setVisibility(View.GONE);
                    btnReject.setVisibility(View.GONE);
                } else {
                    btnAccept.setVisibility(View.VISIBLE);
                    btnReject.setVisibility(View.VISIBLE);
                }
                btnMarkCompleted.setVisibility(View.GONE);
                layoutPriceSetter.setVisibility(View.GONE);
                tvWaitingForPayment.setVisibility(View.GONE);
            } else if ("accepted".equals(status)) {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.GONE);
                layoutPriceSetter.setVisibility(View.VISIBLE);
                tvWaitingForPayment.setVisibility(View.GONE);
            } else if ("price_set".equals(status)) {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.GONE);
                layoutPriceSetter.setVisibility(View.GONE);
                tvWaitingForPayment.setVisibility(View.VISIBLE);
            } else if ("paid".equals(status)) {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.VISIBLE);
                layoutPriceSetter.setVisibility(View.GONE);
                tvWaitingForPayment.setVisibility(View.GONE);
            } else {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.GONE);
                layoutPriceSetter.setVisibility(View.GONE);
                tvWaitingForPayment.setVisibility(View.GONE);
            }

            btnMessage.setVisibility(View.VISIBLE);
            btnMessage.setEnabled(!"completed".equals(status));
            btnMessage.setAlpha(btnMessage.isEnabled() ? 1f : 0.5f);
            btnCall.setVisibility(View.GONE);

            if (etSetAmount != null) {
                etSetAmount.setText(booking.paymentAmount > 0
                        ? String.format(Locale.US, "%.0f", booking.paymentAmount)
                        : "");
            }

            // Click listeners
            btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(booking);
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(booking);
            });

            btnMarkCompleted.setOnClickListener(v -> {
                if (listener != null) listener.onMarkCompleted(booking);
            });

            btnSetPrice.setOnClickListener(v -> {
                if (listener == null || etSetAmount == null) return;
                String raw = etSetAmount.getText() == null ? "" : etSetAmount.getText().toString().trim();
                if (raw.isEmpty()) {
                    etSetAmount.setError("Enter amount");
                    return;
                }
                try {
                    double amount = Double.parseDouble(raw);
                    if (amount <= 0) {
                        etSetAmount.setError("Enter valid amount");
                        return;
                    }
                    etSetAmount.setError(null);
                    listener.onSetPrice(booking, amount);
                } catch (Exception ignored) {
                    etSetAmount.setError("Enter valid amount");
                }
            });

            btnCall.setOnClickListener(v -> {
                if (!booking.customerPhone.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + booking.customerPhone));
                    context.startActivity(intent);
                }
            });

            btnMessage.setOnClickListener(v -> {
                if (listener != null && btnMessage.isEnabled()) listener.onMessageBooking(booking);
            });

            cardBooking.setOnClickListener(v -> {
                if (booking.isEmergency) {
                    Intent intent = new Intent(context, ProviderEmergencyRequestActivity.class);
                    intent.putExtra("booking_id", booking.id);
                    context.startActivity(intent);
                }
            });
        }

        private String cleanLocation(String address) {
            if (address == null) return "";
            return address.replaceAll("[A-Z0-9\\+]{6,}", "").trim();
        }

        private String formatStatus(String status) {
            if ("price_set".equals(status) || "paid".equals(status) || "confirmed".equals(status)) {
                return "ACCEPTED";
            }
            if ("rejected".equals(status)) return "CANCELLED";
            return status.toUpperCase(Locale.US);
        }
    }
}

