package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.Offer;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class HomeOfferAdapter extends RecyclerView.Adapter<HomeOfferAdapter.VH> {

    public interface OnOfferClickListener {
        void onOfferClick(Offer offer);
    }

    private final Context   ctx;
    private final List<Offer> items;
    private final OnOfferClickListener listener;

    public HomeOfferAdapter(Context ctx, List<Offer> items, OnOfferClickListener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_home_offer, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Offer o = items.get(pos);

        String discountStr = o.discountType != null && o.discountType.equals("percentage")
                ? (int) o.discountValue + "% OFF"
                : "₹" + (int) o.discountValue + " OFF";
        h.tvDiscount.setText(discountStr);
        h.tvTitle.setText(o.title != null ? o.title : "");
        h.tvCode.setText(o.offerCode != null ? o.offerCode : "");

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onOfferClick(o); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<Offer> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDiscount, tvTitle, tvCode;

        VH(View v) {
            super(v);
            tvDiscount = v.findViewById(R.id.tvHomeOfferDiscount);
            tvTitle    = v.findViewById(R.id.tvHomeOfferTitle);
            tvCode     = v.findViewById(R.id.tvHomeOfferCode);
        }
    }
}

