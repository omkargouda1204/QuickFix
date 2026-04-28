package com.example.quickfix.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;

import java.util.ArrayList;
import java.util.List;

public class ProviderReviewsAdapter extends RecyclerView.Adapter<ProviderReviewsAdapter.VH> {

    public interface Listener {
        void onDelete(ReviewItem item, int position);
    }

    public static class ReviewItem {
        public String id;
        public String customerName;
        public int rating;
        public String comment;
        public String createdAt;
    }

    private final List<ReviewItem> items = new ArrayList<>();
    private final Listener listener;

    public ProviderReviewsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ReviewItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) return;
        items.remove(position);
        notifyItemRemoved(position);
    }

    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider_review, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ReviewItem item = items.get(position);
        holder.bind(item, listener, position);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvReviewerName;
        TextView tvRating;
        TextView tvReviewDate;
        TextView tvReviewComment;
        View btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvReviewDate = itemView.findViewById(R.id.tvReviewDate);
            tvReviewComment = itemView.findViewById(R.id.tvReviewComment);
            btnDelete = itemView.findViewById(R.id.btnDeleteReview);
        }

        void bind(ReviewItem item, Listener listener, int position) {
            String name = item.customerName == null || item.customerName.trim().isEmpty()
                    ? "Customer"
                    : item.customerName.trim();
            tvReviewerName.setText(name);
            tvRating.setText(String.valueOf(item.rating));
            tvReviewDate.setText(item.createdAt == null ? "" : item.createdAt);
            tvReviewComment.setText(item.comment == null ? "" : item.comment);

            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> {
                    if (listener != null) listener.onDelete(item, position);
                });
            }
        }
    }
}

