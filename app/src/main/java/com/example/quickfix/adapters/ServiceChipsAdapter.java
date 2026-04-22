package com.example.quickfix.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ServiceChipsAdapter extends RecyclerView.Adapter<ServiceChipsAdapter.ChipViewHolder> {

    public interface OnChipClickListener {
        void onChipClick(String category);
    }

    public static class ServiceChipItem {
        public final String title;
        public final String category;

        public ServiceChipItem(String title, String category) {
            this.title = title == null ? "" : title;
            this.category = category == null ? "" : category;
        }
    }

    private final List<ServiceChipItem> items = new ArrayList<>();
    private final OnChipClickListener listener;

    public ServiceChipsAdapter(OnChipClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ServiceChipItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service_chip, parent, false);
        return new ChipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChipViewHolder holder, int position) {
        ServiceChipItem item = items.get(position);
        holder.button.setText(item.title);
        holder.button.setOnClickListener(v -> {
            if (listener != null) listener.onChipClick(item.category);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ChipViewHolder extends RecyclerView.ViewHolder {
        final MaterialButton button;

        ChipViewHolder(@NonNull View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.btnServiceChip);
        }
    }
}

