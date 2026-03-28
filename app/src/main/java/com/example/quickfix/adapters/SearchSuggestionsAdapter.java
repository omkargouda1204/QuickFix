package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.R;
import com.example.quickfix.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class SearchSuggestionsAdapter extends RecyclerView.Adapter<SearchSuggestionsAdapter.VH> {

    public interface OnSuggestionClickListener {
        void onSuggestionClick(SearchResult suggestion);
    }

    private final Context ctx;
    private List<SearchResult> items = new ArrayList<>();
    private OnSuggestionClickListener listener;

    public SearchSuggestionsAdapter(Context ctx, OnSuggestionClickListener listener) {
        this.ctx      = ctx;
        this.listener = listener;
    }

    public void setSuggestions(List<SearchResult> list) {
        items = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_search_suggestion, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SearchResult r = items.get(pos);
        h.tvTitle.setText(r.name);

        String category = r.subtitle != null ? r.subtitle.trim() : "";
        String city = r.city != null ? r.city.trim() : "";
        String sub;
        if (!category.isEmpty() && !city.isEmpty()) {
            sub = category + " • " + city;
        } else {
            sub = !category.isEmpty() ? category : city;
        }
        h.tvSub.setText(sub);
        h.ivIcon.setImageResource(R.drawable.ic_business);

        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onSuggestionClick(r); });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvSub;
        VH(View v) {
            super(v);
            ivIcon  = v.findViewById(R.id.ivSuggestIcon);
            tvTitle = v.findViewById(R.id.tvSuggestTitle);
            tvSub   = v.findViewById(R.id.tvSuggestSub);
        }
    }
}
