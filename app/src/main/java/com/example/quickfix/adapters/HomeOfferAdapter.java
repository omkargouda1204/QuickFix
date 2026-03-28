package com.example.quickfix.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.quickfix.Offer;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.Nullable;

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

        String discountStr = o.discountType != null && o.discountType.equalsIgnoreCase("percentage")
                ? (int) o.discountValue + "% OFF"
                : "₹" + (int) o.discountValue + " OFF";
        h.tvDiscount.setText(discountStr);

        String title = o.getTitle() != null ? o.getTitle().trim() : "";
        if (title.isEmpty()) {
            h.tvTitle.setVisibility(View.GONE);
        } else {
            h.tvTitle.setVisibility(View.VISIBLE);
            h.tvTitle.setText(title);
        }

        String description = o.getDescription() != null ? o.getDescription().trim() : "";
        if (description.isEmpty()) {
            h.tvDescription.setVisibility(View.GONE);
        } else {
            h.tvDescription.setVisibility(View.VISIBLE);
            h.tvDescription.setText(description);
        }

        String normalizedCode = o.getOfferCode() != null ? o.getOfferCode().trim().toUpperCase(Locale.US) : "";
        if (normalizedCode.isEmpty()) {
            h.tvCode.setVisibility(View.GONE);
            h.copyBtn.setVisibility(View.GONE);
        } else {
            h.tvCode.setVisibility(View.VISIBLE);
            h.copyBtn.setVisibility(View.VISIBLE);
            h.tvCode.setText(h.itemView.getContext().getString(R.string.home_offer_code_format, normalizedCode));
        }

        String imageUrl = o.getImageUrl() != null ? o.getImageUrl().trim() : "";
        if (!imageUrl.isEmpty()) {
            h.ivImage.setVisibility(View.VISIBLE);
            h.ivImage.setPadding(0, 0, 0, 0);
            h.ivImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(ctx)
                    .load(imageUrl)
                    .placeholder(R.drawable.placeholder)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            h.ivImage.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            h.ivImage.setVisibility(View.VISIBLE);
                            return false;
                        }
                    })
                    .error(R.drawable.placeholder)
                    .into(h.ivImage);
        } else {
            h.ivImage.setVisibility(View.GONE);
        }

        h.copyBtn.setOnClickListener(v -> {
            String offerCode = o.getOfferCode() != null ? o.getOfferCode().trim() : "";
            if (offerCode.isEmpty()) {
                Toast.makeText(ctx, R.string.home_offer_no_code, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Offer Code", offerCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ctx, R.string.offer_code_copied, Toast.LENGTH_SHORT).show();
            }
        });

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onOfferClick(o); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<Offer> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvDiscount, tvTitle, tvDescription, tvCode;
        MaterialButton copyBtn;

        VH(View v) {
            super(v);
            ivImage = v.findViewById(R.id.ivHomeOfferImage);
            tvDiscount = v.findViewById(R.id.tvHomeOfferDiscount);
            tvTitle    = v.findViewById(R.id.tvHomeOfferTitle);
            tvDescription = v.findViewById(R.id.tvHomeOfferDescription);
            tvCode     = v.findViewById(R.id.tvHomeOfferCode);
            copyBtn    = v.findViewById(R.id.copyBtn);
        }
    }
}
