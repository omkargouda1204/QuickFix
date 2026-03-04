package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.ServiceCategory;

import java.util.List;

public class CategoriesAdapter extends RecyclerView.Adapter<CategoriesAdapter.VH> {

    public interface OnCategoryClickListener {
        void onCategoryClick(ServiceCategory category);
    }

    private final Context ctx;
    private final List<ServiceCategory> items;
    private final OnCategoryClickListener listener;

    public CategoriesAdapter(Context ctx, List<ServiceCategory> items,
                             OnCategoryClickListener listener) {
        this.ctx      = ctx;
        this.items    = items;
        this.listener = listener;
    }

    public void updateCount(String categoryName, String count) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).name.equals(categoryName)) {
                items.get(i).providerCount = count;
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_category_grid, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ServiceCategory cat = items.get(pos);
        h.tvName.setText(cat.name);
        h.ivIcon.setImageResource(cat.iconRes);
        h.iconBg.setBackgroundResource(cat.bgRes);
        h.tvCount.setText(cat.providerCount.isEmpty() ? "Loading..." : cat.providerCount);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onCategoryClick(cat); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        FrameLayout iconBg;
        ImageView   ivIcon;
        TextView    tvName, tvCount;
        VH(View v) {
            super(v);
            iconBg  = v.findViewById(R.id.categoryIconBg);
            ivIcon  = v.findViewById(R.id.ivCategoryIcon);
            tvName  = v.findViewById(R.id.tvCategoryName);
            tvCount = v.findViewById(R.id.tvProviderCount);
        }
    }
}

