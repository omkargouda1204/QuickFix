package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.quickfix.AdminCategory;
import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class AdminCategoryAdapter extends RecyclerView.Adapter<AdminCategoryAdapter.VH> {

    public interface Listener {
        void onEdit(AdminCategory category);
        void onDelete(AdminCategory category);
    }

    private final Context   ctx;
    private final List<AdminCategory> items;
    private final Listener  listener;

    public AdminCategoryAdapter(Context ctx, List<AdminCategory> items, Listener listener) {
        this.ctx      = ctx;
        this.items    = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_admin_category_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AdminCategory cat = items.get(pos);

        h.tvName.setText(cat.name != null ? cat.name : "");
        h.tvDescription.setText(cat.description != null && !cat.description.isEmpty()
                ? cat.description : "No description");
        h.tvProviderCount.setText(cat.providerCount + " providers");

        // Load icon image if available, else show placeholder
        if (cat.iconUrl != null && !cat.iconUrl.isEmpty()) {
            Glide.with(ctx)
                    .load(cat.iconUrl)
                    .placeholder(R.drawable.ic_services)
                    .error(R.drawable.ic_services)
                    .centerCrop()
                    .into(h.ivIcon);
        } else {
            h.ivIcon.setImageResource(R.drawable.ic_services);
        }

        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(cat); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(cat); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void setItems(List<AdminCategory> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView   ivIcon;
        TextView    tvName, tvDescription, tvProviderCount;
        ImageButton btnEdit, btnDelete;

        VH(View v) {
            super(v);
            ivIcon         = v.findViewById(R.id.ivCategoryIcon);
            tvName         = v.findViewById(R.id.tvCategoryName);
            tvDescription  = v.findViewById(R.id.tvCategoryDescription);
            tvProviderCount = v.findViewById(R.id.tvProviderCount);
            btnEdit        = v.findViewById(R.id.btnEdit);
            btnDelete      = v.findViewById(R.id.btnDelete);
        }
    }
}

