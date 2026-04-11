package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ProviderProfile;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Locale;

public class AdminProviderManagementAdapter extends RecyclerView.Adapter<AdminProviderManagementAdapter.VH> {

    public interface Listener {
        void onViewDetails(@NonNull ProviderProfile provider);
        void onToggleBlock(@NonNull ProviderProfile provider);
        void onToggleVerify(@NonNull ProviderProfile provider);
    }

    private final Context context;
    private final List<ProviderProfile> items;
    private final Listener listener;

    public AdminProviderManagementAdapter(Context context,
                                          List<ProviderProfile> items,
                                          Listener listener) {
        this.context = context;
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_admin_provider_profile, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ProviderProfile p = items.get(position);

        h.tvBusinessName.setText(valueOrDash(p.businessName));
        h.tvOwnerName.setText("Owner: " + valueOrDash(p.ownerName));
        h.tvCategoryCity.setText(valueOrDash(p.category) + "  |  " + valueOrDash(p.city));
        h.tvPhone.setText("Phone: " + valueOrDash(p.phone));
        h.tvRating.setText(String.format(Locale.US, "Rating: %.1f", p.rating));
        h.tvAvailable.setText(p.isAvailable ? "Available" : "Blocked");
        h.tvApproved.setText(p.isVerified ? "Verified" : "Not verified");

        h.btnDetails.setOnClickListener(v -> {
            if (listener != null) listener.onViewDetails(p);
        });

        h.btnBlockUnblock.setText(p.isAvailable ? "Block" : "Unblock");
        h.btnBlockUnblock.setOnClickListener(v -> {
            if (listener != null) listener.onToggleBlock(p);
        });

        h.btnVerifyBadge.setText(p.isVerified ? "Remove Badge" : "Verify Badge");
        h.btnVerifyBadge.setOnClickListener(v -> {
            if (listener != null) listener.onToggleVerify(p);
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    private String valueOrDash(String value) {
        if (value == null) return "-";
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvBusinessName;
        TextView tvOwnerName;
        TextView tvCategoryCity;
        TextView tvPhone;
        TextView tvRating;
        TextView tvAvailable;
        TextView tvApproved;
        MaterialButton btnDetails;
        MaterialButton btnBlockUnblock;
        MaterialButton btnVerifyBadge;

        VH(@NonNull View itemView) {
            super(itemView);
            tvBusinessName = itemView.findViewById(R.id.tvBusinessName);
            tvOwnerName = itemView.findViewById(R.id.tvOwnerName);
            tvCategoryCity = itemView.findViewById(R.id.tvCategoryCity);
            tvPhone = itemView.findViewById(R.id.tvPhone);
            tvRating = itemView.findViewById(R.id.tvRating);
            tvAvailable = itemView.findViewById(R.id.tvAvailable);
            tvApproved = itemView.findViewById(R.id.tvApproved);
            btnDetails = itemView.findViewById(R.id.btnDetails);
            btnBlockUnblock = itemView.findViewById(R.id.btnBlockUnblock);
            btnVerifyBadge = itemView.findViewById(R.id.btnVerifyBadge);
        }
    }
}
