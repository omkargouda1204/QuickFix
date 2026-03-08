package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.Banner;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class HomeBannerAdapter extends RecyclerView.Adapter<HomeBannerAdapter.VH> {

    public interface OnBannerClickListener {
        void onBannerClick(Banner banner);
    }

    private final Context            ctx;
    private final List<Banner>       items;
    private final OnBannerClickListener listener;

    public HomeBannerAdapter(Context ctx, List<Banner> items, OnBannerClickListener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_home_banner, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Banner b = items.get(pos);

        h.tvTitle.setText(b.title != null ? b.title : "");

        if (b.imageUrl != null && !b.imageUrl.isEmpty()) {
            h.ivImage.setVisibility(View.VISIBLE);
            h.gradientBg.setVisibility(View.GONE);
            Glide.with(ctx)
                    .load(b.imageUrl)
                    .placeholder(R.drawable.bg_promo_card)
                    .error(R.drawable.bg_promo_card)
                    .centerCrop()
                    .into(h.ivImage);
        } else {
            h.ivImage.setVisibility(View.GONE);
            h.gradientBg.setVisibility(View.VISIBLE);
        }

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onBannerClick(b); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<Banner> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivImage;
        View      gradientBg;
        TextView  tvTitle;

        VH(View v) {
            super(v);
            ivImage    = v.findViewById(R.id.ivBannerImage);
            gradientBg = v.findViewById(R.id.bannerGradientBg);
            tvTitle    = v.findViewById(R.id.tvBannerTitle);
        }
    }
}

