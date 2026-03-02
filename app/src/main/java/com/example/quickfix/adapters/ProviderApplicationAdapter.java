package com.example.quickfix.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ProviderApplication;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * ProviderApplicationAdapter — RecyclerView adapter for the Admin Provider
 * Approval screen.  Displays each application as a MaterialCardView with
 * colour-coded status badge and Approve / Reject action buttons.
 */
public class ProviderApplicationAdapter
        extends RecyclerView.Adapter<ProviderApplicationAdapter.ViewHolder> {

    public interface OnActionListener {
        void onApprove(ProviderApplication application);
        void onReject(ProviderApplication application);
        void onViewDetails(ProviderApplication application);
    }

    private final Context context;
    private final List<ProviderApplication> items;
    private OnActionListener listener;

    public ProviderApplicationAdapter(Context context) {
        this.context = context;
        this.items   = new ArrayList<>();
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    /** Replace the whole dataset and refresh */
    public void setData(List<ProviderApplication> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    /** Update a single item in-place after approve/reject */
    public void updateItem(ProviderApplication updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(updated.id)) {
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
        h.bind(app);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolder ─────────────────────────────────────────────────────────

    class ViewHolder extends RecyclerView.ViewHolder {

        final TextView tvBusinessName, tvOwnerName, tvCategory,
                tvCity, tvExperience, tvDate, tvStatus;
        final MaterialButton btnApprove, btnReject, btnDetails;
        final MaterialCardView card;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card          = itemView.findViewById(R.id.cardRoot);
            tvBusinessName = itemView.findViewById(R.id.tvBusinessName);
            tvOwnerName    = itemView.findViewById(R.id.tvOwnerName);
            tvCategory     = itemView.findViewById(R.id.tvCategory);
            tvCity         = itemView.findViewById(R.id.tvCity);
            tvExperience   = itemView.findViewById(R.id.tvExperience);
            tvDate         = itemView.findViewById(R.id.tvDate);
            tvStatus       = itemView.findViewById(R.id.tvStatus);
            btnApprove     = itemView.findViewById(R.id.btnApprove);
            btnReject      = itemView.findViewById(R.id.btnReject);
            btnDetails     = itemView.findViewById(R.id.btnDetails);
        }

        void bind(ProviderApplication app) {
            tvBusinessName.setText(app.businessName);
            tvOwnerName.setText("Owner: " + app.name);
            tvCategory.setText(app.category);
            tvCity.setText("📍 " + app.city);
            tvExperience.setText(app.experience + " yrs exp");

            // Format submitted date (ISO → dd MMM yyyy)
            tvDate.setText("Applied: " + formatDate(app.submittedAt));

            // Status badge
            String status = app.status != null ? app.status.toLowerCase() : "pending";
            switch (status) {
                case "approved":
                    tvStatus.setText("✅ Approved");
                    tvStatus.setBackgroundResource(R.drawable.bg_status_approved);
                    tvStatus.setTextColor(Color.parseColor("#1B5E20"));
                    break;
                case "rejected":
                    tvStatus.setText("❌ Rejected");
                    tvStatus.setBackgroundResource(R.drawable.bg_status_rejected);
                    tvStatus.setTextColor(Color.parseColor("#B71C1C"));
                    break;
                default:
                    tvStatus.setText("⏳ Pending");
                    tvStatus.setBackgroundResource(R.drawable.bg_status_pending);
                    tvStatus.setTextColor(Color.parseColor("#E65100"));
                    break;
            }

            // Buttons — disable if already processed
            boolean isPending = "pending".equals(status);
            btnApprove.setEnabled(isPending);
            btnReject.setEnabled(isPending);
            btnDetails.setEnabled(!isPending);
            btnApprove.setAlpha(isPending ? 1f : 0.4f);
            btnReject.setAlpha(isPending ? 1f : 0.4f);
            btnDetails.setAlpha(!isPending ? 1f : 0.4f);

            btnApprove.setOnClickListener(v -> {
                if (listener != null) listener.onApprove(app);
            });
            btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onReject(app);
            });
            btnDetails.setOnClickListener(v -> {
                if (listener != null) listener.onViewDetails(app);
            });
        }

        private String formatDate(String iso) {
            if (iso == null || iso.isEmpty()) return "—";
            try {
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
                                java.util.Locale.US);
                java.util.Date d = sdf.parse(iso.length() > 19 ? iso.substring(0, 19) : iso);
                java.text.SimpleDateFormat out =
                        new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US);
                return out.format(d);
            } catch (Exception e) {
                return iso.length() >= 10 ? iso.substring(0, 10) : iso;
            }
        }
    }
}

