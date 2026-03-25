package com.example.quickfix.adapters;

import android.content.res.ColorStateList;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Offer;
import com.example.quickfix.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OfferAdapter extends RecyclerView.Adapter<OfferAdapter.VH> {

    public interface Listener {
        void onEdit(Offer offer);
        void onDelete(Offer offer);
        void onToggleActive(Offer offer, boolean active);
    }

    private final Context     ctx;
    private final List<Offer> items;
    private final Listener    listener;

    public OfferAdapter(Context ctx, List<Offer> items, Listener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_offer_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Offer o = items.get(pos);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());

        if (h.tvTitle     != null) h.tvTitle.setText(o.title != null ? o.title : "");
        if (h.tvCode      != null) h.tvCode.setText("Code: " + (o.offerCode != null ? o.offerCode : "—"));
        if (h.tvMinAmount != null) h.tvMinAmount.setText("Min: ₹" + (int) o.minBookingAmount);

        boolean expired = o.expiryDate != null && !o.expiryDate.isEmpty() && o.expiryDate.compareTo(today) < 0;
        if (h.tvExpiry != null) {
            h.tvExpiry.setText("Expires: " + (o.expiryDate != null && !o.expiryDate.isEmpty() ? o.expiryDate : "No expiry"));
            h.tvExpiry.setTextColor(expired ? ContextCompat.getColor(ctx, android.R.color.holo_red_dark) : ContextCompat.getColor(ctx, R.color.text_secondary));
        }

        if (h.tvDiscount != null) {
            String discStr = (o.discountType != null && o.discountType.equals("percentage"))
                    ? (int) o.discountValue + "% OFF"
                    : "₹" + (int) o.discountValue + " OFF";
            h.tvDiscount.setText(discStr);
        }

        if (h.chipStatus != null) {
            boolean active = o.isActive && !expired;
            h.chipStatus.setText(expired ? "Expired" : (o.isActive ? "Active" : "Disabled"));
            int colorRes = expired ? android.R.color.holo_red_light : (active ? R.color.chip_active_bg : R.color.chip_disabled_bg);
            h.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(ctx, colorRes)));
            h.chipStatus.setOnClickListener(v -> { if (listener != null) listener.onToggleActive(o, !o.isActive); });
        }

        if (h.btnEdit   != null) h.btnEdit.setOnClickListener(v   -> { if (listener != null) listener.onEdit(o); });
        if (h.btnDelete != null) h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(o); });
    }

    @Override
    public int getItemCount() { return items != null ? items.size() : 0; }

    public void setItems(List<Offer> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView    tvTitle, tvCode, tvDiscount, tvExpiry, tvMinAmount;
        Chip        chipStatus;
        ImageButton btnEdit, btnDelete;
        VH(View v) {
            super(v);
            tvTitle     = v.findViewById(R.id.tvOfferTitle);
            tvCode      = v.findViewById(R.id.tvOfferCode);
            tvDiscount  = v.findViewById(R.id.tvOfferDiscount);
            tvExpiry    = v.findViewById(R.id.tvOfferExpiry);
            tvMinAmount = v.findViewById(R.id.tvOfferMinAmount);
            chipStatus  = v.findViewById(R.id.chipOfferStatus);
            btnEdit     = v.findViewById(R.id.btnOfferEdit);
            btnDelete   = v.findViewById(R.id.btnOfferDelete);
        }
    }
}
