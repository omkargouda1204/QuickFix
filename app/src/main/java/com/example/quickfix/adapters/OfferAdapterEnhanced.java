package com.example.quickfix.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.Offer;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

/**
 * OfferAdapterEnhanced — Vertical RecyclerView adapter for displaying offers in admin/management screens.
 * Used in OfferManagementActivity for admin to manage discount offers.
 */
public class OfferAdapterEnhanced extends RecyclerView.Adapter<OfferAdapterEnhanced.VH> {

    public interface Listener {
        void onEdit(Offer offer);
        void onDelete(Offer offer);
        void onToggleActive(Offer offer, boolean active);
    }

    private static final String TAG = "OfferAdapterEnhanced";
    private final Context ctx;
    private final List<Offer> items;
    private final Listener listener;

    public OfferAdapterEnhanced(Context ctx, List<Offer> items, Listener listener) {
        this.ctx = ctx;
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_offer_management, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        try {
            Offer offer = items.get(position);
            if (offer == null) return;

            // Title
            if (holder.tvTitle != null) {
                String title = offer.title != null ? offer.title.trim() : "Untitled Offer";
                holder.tvTitle.setText(title.isEmpty() ? "Untitled Offer" : title);
            }

            // Discount
            if (holder.tvDiscount != null) {
                String discountStr = offer.discountType != null && offer.discountType.equalsIgnoreCase("percentage")
                        ? (int) offer.discountValue + "% OFF"
                        : "₹" + (int) offer.discountValue + " OFF";
                holder.tvDiscount.setText(discountStr);
            }

            // Offer code
            if (holder.tvCode != null) {
                String code = offer.offerCode != null ? offer.offerCode.trim().toUpperCase() : "";
                if (code.isEmpty()) {
                    holder.tvCode.setVisibility(View.GONE);
                } else {
                    holder.tvCode.setVisibility(View.VISIBLE);
                    holder.tvCode.setText("Code: " + code);
                }
            }

            // Expiry date
            if (holder.tvExpiry != null) {
                String expiry = offer.expiryDate != null ? offer.expiryDate : "No expiry";
                holder.tvExpiry.setText("Expires: " + expiry);
            }

            // Status
            if (holder.tvStatus != null) {
                boolean isActive = offer.isActive;
                holder.tvStatus.setText(isActive ? "Active" : "Inactive");
                holder.tvStatus.setTextColor(isActive ? 
                    ctx.getResources().getColor(R.color.success_green, null) :
                    ctx.getResources().getColor(R.color.text_secondary, null));
            }

            // Offer image
            if (holder.ivOfferImage != null) {
                String imageUrl = offer.imageUrl != null ? offer.imageUrl.trim() : "";
                if (!imageUrl.isEmpty()) {
                    holder.ivOfferImage.setVisibility(View.VISIBLE);
                    Glide.with(ctx)
                            .load(imageUrl)
                            .placeholder(R.drawable.bg_promo_card)
                            .error(R.drawable.bg_promo_card)
                            .centerCrop()
                            .into(holder.ivOfferImage);
                } else {
                    holder.ivOfferImage.setVisibility(View.GONE);
                }
            }

            // Click listeners
            if (holder.btnEdit != null) {
                holder.btnEdit.setOnClickListener(v -> {
                    if (listener != null) listener.onEdit(offer);
                });
            }

            if (holder.btnDelete != null) {
                holder.btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onDelete(offer);
                });
            }

            if (holder.toggleActive != null) {
                holder.toggleActive.setChecked(offer.isActive);
                holder.toggleActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (listener != null) listener.onToggleActive(offer, isChecked);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error binding offer: " + e.getMessage(), e);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItem(int position, Offer offer) {
        if (position >= 0 && position < items.size()) {
            items.set(position, offer);
            notifyItemChanged(position);
        }
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void addItem(Offer offer) {
        if (offer != null) {
            items.add(0, offer);
            notifyItemInserted(0);
        }
    }

    public void setItems(List<Offer> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivOfferImage;
        TextView tvTitle, tvDiscount, tvCode, tvExpiry, tvStatus;
        LinearLayout btnEdit, btnDelete;
        android.widget.CheckBox toggleActive;

        VH(View v) {
            super(v);
            try {
                ivOfferImage = v.findViewById(R.id.ivOfferImage);
                tvTitle = v.findViewById(R.id.tvOfferTitle);
                tvDiscount = v.findViewById(R.id.tvDiscount);
                tvCode = v.findViewById(R.id.tvOfferCode);
                tvExpiry = v.findViewById(R.id.tvOfferExpiry);
                tvStatus = v.findViewById(R.id.tvOfferStatus);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                toggleActive = v.findViewById(R.id.toggleActive);
            } catch (Exception e) {
                Log.e("OfferAdapterEnhanced.VH", "Error in ViewHolder: " + e.getMessage());
            }
        }
    }
}
