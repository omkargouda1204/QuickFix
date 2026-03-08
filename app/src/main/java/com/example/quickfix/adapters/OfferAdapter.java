package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Offer;
import com.example.quickfix.R;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class OfferAdapter extends RecyclerView.Adapter<OfferAdapter.VH> {

    public interface Listener {
        void onEdit(Offer offer);
        void onDelete(Offer offer);
        void onToggleActive(Offer offer, boolean active);
    }

    private final Context      ctx;
    private final List<Offer>  items;
    private final Listener     listener;

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

        h.tvTitle.setText(o.title != null ? o.title : "");
        h.tvCode.setText("Code: " + (o.offerCode != null ? o.offerCode : "—"));

        String discountStr = o.discountType != null && o.discountType.equals("percentage")
                ? (int) o.discountValue + "% OFF"
                : "₹" + (int) o.discountValue + " OFF";
        h.tvDiscount.setText(discountStr);
        h.tvExpiry.setText("Expires: " + (o.expiryDate != null ? o.expiryDate : "No expiry"));
        h.tvMinAmount.setText("Min: ₹" + (int) o.minBookingAmount);

        if (o.isActive) {
            h.chipStatus.setText("Active");
            h.chipStatus.setChipBackgroundColorResource(R.color.chip_active_bg);
        } else {
            h.chipStatus.setText("Disabled");
            h.chipStatus.setChipBackgroundColorResource(R.color.chip_disabled_bg);
        }

        h.btnEdit.setOnClickListener(v   -> { if (listener != null) listener.onEdit(o); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(o); });
        h.chipStatus.setOnClickListener(v -> {
            if (listener != null) listener.onToggleActive(o, !o.isActive);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

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

