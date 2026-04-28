package com.example.quickfix.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ProviderApplication;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProviderApplicationAdapter
        extends RecyclerView.Adapter<ProviderApplicationAdapter.ViewHolder> {

    public interface OnActionListener {
        void onApprove(ProviderApplication application);
        void onReject(ProviderApplication application);
        void onViewDetails(ProviderApplication application);
    }

    private final Context context;
    private final List<ProviderApplication> items = new ArrayList<>();
    private OnActionListener listener;

    public ProviderApplicationAdapter(Context context) {
        this.context = context;
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<ProviderApplication> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void updateItem(ProviderApplication updated) {
        if (updated == null || updated.id == null || updated.id.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            ProviderApplication item = items.get(i);
            if (item != null && updated.id.equals(item.id)) {
                items.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_provider_application_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ProviderApplication app = items.get(position);
        if (app == null) return;
        h.bindData(app, listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {

        final MaterialCardView card;
        final TextView         tvBusinessName, tvOwnerName, tvCategory,
                               tvCity, tvExperience, tvDate, tvStatus,
                               tvEmail, tvPhone;
        final MaterialButton   btnApprove, btnReject, btnDetails;

        ViewHolder(@NonNull View v) {
            super(v);
            card           = v.findViewById(R.id.cardRoot);
            tvBusinessName = v.findViewById(R.id.tvBusinessName);
            tvOwnerName    = v.findViewById(R.id.tvOwnerName);
            tvCategory     = v.findViewById(R.id.tvCategory);
            tvCity         = v.findViewById(R.id.tvCity);
            tvExperience   = v.findViewById(R.id.tvExperience);
            tvDate         = v.findViewById(R.id.tvDate);
            tvStatus       = v.findViewById(R.id.tvStatus);
            tvEmail        = v.findViewById(R.id.tvEmail);
            tvPhone        = v.findViewById(R.id.tvPhone);
            btnApprove     = v.findViewById(R.id.btnApprove);
            btnReject      = v.findViewById(R.id.btnReject);
            btnDetails     = v.findViewById(R.id.btnDetails);
        }

        void bindData(@NonNull ProviderApplication app, OnActionListener listener) {
            set(tvBusinessName, app.businessName, "-");
            set(tvOwnerName,    "Owner: " + str(app.name));
            set(tvEmail,        str(app.email).isEmpty() ? "-" : str(app.email));
            set(tvPhone,        str(app.phone).isEmpty() ? "-" : str(app.phone));
            set(tvCategory,     str(app.category));
            
            // Req 5: show city + pincode on card
            String loc = str(app.city);
            if (!str(app.pincode).isEmpty()) loc += " - " + app.pincode;
            set(tvCity,         loc);

            set(tvExperience,   expText(app.experience));
            set(tvDate,         "Applied: " + fmtDate(app.submittedAt));

            String status = (app.status != null) ? app.status.toLowerCase(Locale.US) : "pending";
            if (tvStatus != null) {
                switch (status) {
                    case "approved":
                        tvStatus.setText("Approved");
                        tvStatus.setBackgroundResource(R.drawable.bg_status_approved);
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.status_approved_text));
                        break;
                    case "rejected":
                        tvStatus.setText("Rejected");
                        tvStatus.setBackgroundResource(R.drawable.bg_status_rejected);
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.status_rejected_text));
                        break;
                    default:
                        tvStatus.setText("Pending");
                        tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                        tvStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.status_pending_text));
                        break;
                }
            }

            boolean isPending = "pending".equals(status);
            btnEnabled(btnApprove, isPending);
            btnEnabled(btnReject,  isPending);
            if (btnDetails != null) { btnDetails.setEnabled(true); btnDetails.setAlpha(1f); }

            if (btnApprove != null) btnApprove.setOnClickListener(
                    v -> { if (listener != null) listener.onApprove(app); });
            if (btnReject  != null) btnReject.setOnClickListener(
                    v -> { if (listener != null) listener.onReject(app); });
            if (btnDetails != null) btnDetails.setOnClickListener(
                    v -> { if (listener != null) listener.onViewDetails(app); });
        }

        private static String str(String v)  { return v != null ? v : ""; }

        private static String expText(String exp) {
            return (exp != null && !exp.trim().isEmpty()) ? exp.trim() + " yrs exp" : "-";
        }

        private static void set(TextView tv, String text, String fallback) {
            if (tv == null) return;
            tv.setText((text != null && !text.isEmpty()) ? text : fallback);
        }

        private static void set(TextView tv, String text) {
            if (tv == null) return;
            tv.setText(text != null ? text : "-");
        }

        private static void btnEnabled(MaterialButton btn, boolean enabled) {
            if (btn == null) return;
            btn.setEnabled(enabled);
            btn.setAlpha(enabled ? 1f : 0.4f);
        }

        private static String fmtDate(String iso) {
            if (iso == null || iso.isEmpty()) return "-";
            try {
                String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                java.util.Date d = sdf.parse(s);
                if (d == null) return iso.length() >= 10 ? iso.substring(0, 10) : iso;
                return new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
            } catch (Exception e) {
                return iso.length() >= 10 ? iso.substring(0, 10) : iso;
            }
        }
    }
}
