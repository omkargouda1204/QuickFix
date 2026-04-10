package com.example.quickfix;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * BannerAdapter — Vertical RecyclerView adapter for displaying banners in admin/management screens.
 * Used in BannerManagementActivity for admin to manage banners.
 */
public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.VH> {

    public interface Listener {
        void onEdit(Banner banner);
        void onDelete(Banner banner);
        void onToggleActive(Banner banner, boolean active);
    }

    private static final String TAG = "BannerAdapter";
    private final Context ctx;
    private final List<Banner> items;
    private final Listener listener;

    public BannerAdapter(Context ctx, List<Banner> items, Listener listener) {
        this.ctx = ctx;
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_banner_management, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        try {
            Banner banner = items.get(position);
            if (banner == null) return;

            // Title
            if (holder.tvTitle != null) {
                holder.tvTitle.setText(banner.title != null ? banner.title : "Untitled Banner");
            }

            // Dates
            if (holder.tvDates != null) {
                String dateInfo = String.format("%s to %s",
                        banner.startDate != null ? banner.startDate : "No start date",
                        banner.expiryDate != null ? banner.expiryDate : "No end date");
                holder.tvDates.setText(dateInfo);
            }

            // Status badge
            if (holder.tvStatus != null) {
                boolean isActive = banner.isActive;
                holder.tvStatus.setText(isActive ? "Active" : "Inactive");
                holder.tvStatus.setTextColor(isActive ? 
                    ctx.getResources().getColor(R.color.success_green, null) :
                    ctx.getResources().getColor(R.color.text_secondary, null));
            }

            // Banner image
            if (holder.ivBannerImage != null && banner.imageUrl != null && !banner.imageUrl.isEmpty()) {
                String url = banner.imageUrl;
                if (!url.startsWith("http")) {
                    url = SupabaseStorageHelper.getPublicObjectUrl("banner-images", url);
                }
                Glide.with(ctx)
                        .load(url)
                        .placeholder(R.drawable.bg_promo_card)
                        .error(R.drawable.bg_promo_card)
                        .centerCrop()
                        .into(holder.ivBannerImage);
            }

            // Click listeners
            if (holder.btnEdit != null) {
                holder.btnEdit.setOnClickListener(v -> {
                    if (listener != null) listener.onEdit(banner);
                });
            }

            if (holder.btnDelete != null) {
                holder.btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onDelete(banner);
                });
            }

            if (holder.toggleActive != null) {
                holder.toggleActive.setChecked(banner.isActive);
                holder.toggleActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (listener != null) listener.onToggleActive(banner, isChecked);
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error binding banner: " + e.getMessage(), e);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateItem(int position, Banner banner) {
        if (position >= 0 && position < items.size()) {
            items.set(position, banner);
            notifyItemChanged(position);
        }
    }

    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void addItem(Banner banner) {
        if (banner != null) {
            items.add(0, banner);
            notifyItemInserted(0);
        }
    }

    public void setItems(List<Banner> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivBannerImage;
        TextView tvTitle, tvDates, tvStatus;
        LinearLayout btnEdit, btnDelete;
        android.widget.CheckBox toggleActive;

        VH(View v) {
            super(v);
            try {
                ivBannerImage = v.findViewById(R.id.ivBannerImage);
                tvTitle = v.findViewById(R.id.tvBannerTitle);
                tvDates = v.findViewById(R.id.tvBannerDates);
                tvStatus = v.findViewById(R.id.tvBannerStatus);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
                toggleActive = v.findViewById(R.id.toggleActive);
            } catch (Exception e) {
                Log.e("BannerAdapter.VH", "Error in ViewHolder: " + e.getMessage());
            }
        }
    }
}

