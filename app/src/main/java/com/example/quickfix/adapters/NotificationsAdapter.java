package com.example.quickfix.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Notification;
import com.example.quickfix.R;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * NotificationsAdapter — Displays notification list items with mark-read & delete.
 */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

    private final Context context;
    private List<Notification> notifications = new ArrayList<>();
    private final NotificationActionListener listener;

    public interface NotificationActionListener {
        void onMarkRead(Notification notification);
        void onDelete(Notification notification);
        void onAcceptEmergency(Notification notification);
        void onRejectEmergency(Notification notification);
    }

    public NotificationsAdapter(Context context, NotificationActionListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    public void setNotifications(List<Notification> list) {
        notifications = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(notifications.get(position));
    }

    @Override public int getItemCount() { return notifications.size(); }

    class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView ivIcon, ivDelete;
        TextView  tvTitle, tvMessage, tvTime, tvUnreadDot;
        View layoutQuickActions;
        View btnAccept, btnReject;

        VH(@NonNull View v) {
            super(v);
            card        = v.findViewById(R.id.cardNotification);
            ivIcon      = v.findViewById(R.id.ivNotifIcon);
            ivDelete    = v.findViewById(R.id.ivDelete);
            tvTitle     = v.findViewById(R.id.tvTitle);
            tvMessage   = v.findViewById(R.id.tvMessage);
            tvTime      = v.findViewById(R.id.tvTime);
            tvUnreadDot = v.findViewById(R.id.tvUnreadDot);
            layoutQuickActions = v.findViewById(R.id.layoutQuickActions);
            btnAccept   = v.findViewById(R.id.btnAccept);
            btnReject   = v.findViewById(R.id.btnReject);
        }

        void bind(Notification n) {
            tvTitle.setText(n.title.isEmpty()   ? "Notification" : n.title);
            tvMessage.setText(n.body == null || n.body.isEmpty() ? "" : n.body);
            tvTime.setText(formatTime(n.createdAt));

            // Unread indicator (green dot) and subtle background
            tvUnreadDot.setVisibility(n.isRead ? View.GONE : View.VISIBLE);
            int bgColor = ContextCompat.getColor(context, n.isRead ? R.color.card_bg : R.color.surface_variant_alt);
            card.setCardBackgroundColor(bgColor);
            // tint unread dot to success green when visible
            if (!n.isRead) tvUnreadDot.setTextColor(ContextCompat.getColor(context, R.color.success_green));

            // Icon by type
            ivIcon.setImageResource(iconForType(n.type));

            // Show quick actions for emergency
            if ("emergency".equalsIgnoreCase(n.type) || "emergency_request".equalsIgnoreCase(n.type)) {
                if (layoutQuickActions != null) layoutQuickActions.setVisibility(View.VISIBLE);
                if (btnAccept != null) btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAcceptEmergency(n);
                });
                if (btnReject != null) btnReject.setOnClickListener(v -> {
                    if (listener != null) listener.onRejectEmergency(n);
                });
            } else {
                if (layoutQuickActions != null) layoutQuickActions.setVisibility(View.GONE);
            }

            // Mark read on click
            card.setOnClickListener(v -> {
                if (listener != null) listener.onMarkRead(n);
            });

            // Delete
            ivDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(n);
            });
        }

        private int iconForType(String type) {
            if (type == null) return R.drawable.ic_notification;
            switch (type) {
                case "booking_request":   return R.drawable.ic_bookings;
                case "booking_confirmed": return R.drawable.ic_check;
                case "provider_approval": return R.drawable.ic_approval;
                case "emergency":         return R.drawable.ic_emergency;
                default:                  return R.drawable.ic_notification;
            }
        }

        private String formatTime(String isoTime) {
            if (isoTime == null || isoTime.isEmpty()) return "";
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf.parse(isoTime.length() > 19 ? isoTime.substring(0, 19) : isoTime);
                if (d == null) return isoTime;
                return DateUtils.getRelativeTimeSpanString(d.getTime(),
                        System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
            } catch (Exception e) {
                return isoTime.length() > 10 ? isoTime.substring(0, 10) : isoTime;
            }
        }
    }
}

