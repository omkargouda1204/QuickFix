package com.example.quickfix.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.AdminService;
import com.example.quickfix.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AdminServiceAdapter — displays services from the `services` table.
 * Uses actual DB columns: title, category, price, duration_mins, is_active.
 */
public class AdminServiceAdapter extends RecyclerView.Adapter<AdminServiceAdapter.VH> {

    public interface Listener {
        void onEdit(AdminService service);
        void onDelete(AdminService service);
        void onToggleStatus(AdminService service, boolean newStatus);
    }

    private final Context ctx;
    private final List<AdminService> items;
    private final Listener listener;

    public AdminServiceAdapter(Context ctx, List<AdminService> items, Listener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_admin_service_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AdminService svc = items.get(pos);

        // ── title (DB column) ────────────────────────────────────────────
        h.tvName.setText(svc.title != null ? svc.title : "");

        // ── category (plain string, no FK) ───────────────────────────────
        h.tvCategory.setText(svc.category != null && !svc.category.isEmpty()
                ? svc.category : "Uncategorized");

        // ── price (DB column) ────────────────────────────────────────────
        String priceLabel = String.format(Locale.getDefault(), "₹%.0f", svc.price);
        if (svc.priceUnit != null && !svc.priceUnit.isEmpty())
            priceLabel += " / " + svc.priceUnit;
        h.tvPrice.setText(priceLabel);

        // ── description ──────────────────────────────────────────────────
        h.tvDescription.setText(svc.description != null && !svc.description.isEmpty()
                ? svc.description : "No description");

        // ── duration (if available) ───────────────────────────────────────
        if (h.tvDuration != null) {
            h.tvDuration.setVisibility(svc.durationMins > 0 ? View.VISIBLE : View.GONE);
            if (svc.durationMins > 0)
                h.tvDuration.setText(svc.durationMins + " mins");
        }

        // ── status chip ──────────────────────────────────────────────────
        if (svc.isActive) {
            h.chipStatus.setText("Active");
            h.chipStatus.setChipBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#C8E6C9")));
            h.chipStatus.setTextColor(Color.parseColor("#1B5E20"));
        } else {
            h.chipStatus.setText("Disabled");
            h.chipStatus.setChipBackgroundColor(
                    ColorStateList.valueOf(Color.parseColor("#FFCDD2")));
            h.chipStatus.setTextColor(Color.parseColor("#B71C1C"));
        }

        // ── default icon (no icon_url column in DB) ───────────────────────
        if (h.ivIcon != null) h.ivIcon.setImageResource(R.drawable.ic_services);

        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(svc); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(svc); });
        h.chipStatus.setOnClickListener(v -> {
            if (listener != null) listener.onToggleStatus(svc, !svc.isActive);
        });
    }

    @Override public int getItemCount() { return items.size(); }

    public void setItems(List<AdminService> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView   ivIcon;
        TextView    tvName, tvCategory, tvPrice, tvDescription, tvDuration;
        Chip        chipStatus;
        ImageButton btnEdit, btnDelete;

        VH(View v) {
            super(v);
            ivIcon        = v.findViewById(R.id.ivServiceIcon);
            tvName        = v.findViewById(R.id.tvServiceName);
            tvCategory    = v.findViewById(R.id.tvCategory);
            tvPrice       = v.findViewById(R.id.tvPrice);
            tvDescription = v.findViewById(R.id.tvDescription);
            tvDuration    = v.findViewById(R.id.tvDuration);   // optional view
            chipStatus    = v.findViewById(R.id.chipStatus);
            btnEdit       = v.findViewById(R.id.btnEdit);
            btnDelete     = v.findViewById(R.id.btnDelete);
        }
    }
}
