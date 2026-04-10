package com.example.quickfix.adapters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Booking;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

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
        TextView tvCustomerName, tvServiceName, tvDateTime, tvAddress, tvPrice, tvStatus;
        MaterialButton btnAccept, btnReject, btnMarkCompleted, btnCall;
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
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnReject = itemView.findViewById(R.id.btnReject);
            btnMarkCompleted = itemView.findViewById(R.id.btnMarkCompleted);
            btnCall = itemView.findViewById(R.id.btnCall);
        }

        void bind(Booking booking) {
            tvCustomerName.setText(booking.customerName.isEmpty() ? "Customer" : booking.customerName);
            tvServiceName.setText(booking.serviceName.isEmpty() ? "Service" : booking.serviceName);
            tvDateTime.setText(booking.bookingDate + " • " + booking.bookingTime);
            tvAddress.setText(booking.address.isEmpty() ? "No address" : booking.address);
            tvPrice.setText("₹" + String.format("%.0f", booking.price));
            tvStatus.setText(booking.status.toUpperCase());

            // Status badge color
            switch (booking.status.toLowerCase()) {
                case "pending":
                    tvStatus.setBackgroundResource(R.drawable.badge_pending);
                    tvStatus.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text));
                    break;
                case "accepted":
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
            }

            // Show/hide action buttons based on status
            if ("pending".equals(booking.status)) {
                btnAccept.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.VISIBLE);
                btnMarkCompleted.setVisibility(View.GONE);
            } else if ("accepted".equals(booking.status)) {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.VISIBLE);
            } else {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
                btnMarkCompleted.setVisibility(View.GONE);
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

            btnCall.setOnClickListener(v -> {
                if (!booking.customerPhone.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_DIAL);
                    intent.setData(Uri.parse("tel:" + booking.customerPhone));
                    context.startActivity(intent);
                }
            });
        }
    }
}

