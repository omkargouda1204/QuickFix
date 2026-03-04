package com.example.quickfix.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    }

    public UserBookingsAdapter(Context context, BookingActionListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    public void setBookings(List<Booking> list) {
        bookings = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_booking_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(bookings.get(position));
    }

    @Override public int getItemCount() { return bookings.size(); }

    class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        TextView tvServiceName, tvProviderName, tvDateTime, tvAddress, tvStatus, tvPrice;
        MaterialButton   btnViewDetails, btnCancel;

        VH(@NonNull View v) {
            super(v);
            card          = v.findViewById(R.id.cardBooking);
            tvServiceName = v.findViewById(R.id.tvServiceName);
            tvProviderName= v.findViewById(R.id.tvProviderName);
            tvDateTime    = v.findViewById(R.id.tvDateTime);
            tvAddress     = v.findViewById(R.id.tvAddress);
            tvStatus      = v.findViewById(R.id.tvStatus);
            tvPrice       = v.findViewById(R.id.tvPrice);
            btnViewDetails= v.findViewById(R.id.btnViewDetails);
            btnCancel     = v.findViewById(R.id.btnCancel);
        }

        void bind(Booking b) {
            tvServiceName.setText(b.serviceName.isEmpty()  ? "Service"  : b.serviceName);
            tvProviderName.setText(b.providerName.isEmpty() ? "Provider" : "By: " + b.providerName);
            String dateTime = (b.bookingDate.isEmpty() ? "—" : b.bookingDate)
                            + (b.bookingTime.isEmpty() ? "" : " • " + b.bookingTime);
            tvDateTime.setText(dateTime);
            tvAddress.setText(b.address.isEmpty() ? "—" : b.address);
            tvPrice.setText(b.price > 0 ? "₹" + String.format("%.0f", b.price) : "");

            // Status badge
            String status = b.status == null ? "" : b.status.toLowerCase();
            tvStatus.setText(b.status == null ? "" : b.status.toUpperCase());
            switch (status) {
                case "pending":
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(Color.parseColor("#F57C00")); break;
                case "accepted": case "confirmed":
                    tvStatus.setBackgroundResource(R.drawable.badge_accepted);
                    tvStatus.setTextColor(Color.parseColor("#1976D2")); break;
                case "completed":
                    tvStatus.setBackgroundResource(R.drawable.badge_completed);
                    tvStatus.setTextColor(Color.parseColor("#388E3C")); break;
                case "cancelled":
                    tvStatus.setBackgroundResource(R.drawable.badge_cancelled);
                    tvStatus.setTextColor(Color.parseColor("#D32F2F")); break;
                default:
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(Color.parseColor("#757575")); break;
            }

            // Cancel only for pending
            boolean canCancel = "pending".equals(status);
            btnCancel.setVisibility(canCancel ? View.VISIBLE : View.GONE);

            btnViewDetails.setOnClickListener(v -> { if (listener != null) listener.onViewDetails(b); });
            btnCancel.setOnClickListener(v -> { if (listener != null) listener.onCancelBooking(b); });
        }
    }
}

