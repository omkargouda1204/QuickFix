package com.example.quickfix.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.ProviderService;
import com.example.quickfix.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * ProviderServicesAdapter — Shows provider's services with edit/delete options
 */
public class ProviderServicesAdapter extends RecyclerView.Adapter<ProviderServicesAdapter.ServiceViewHolder> {

    private Context context;
    private List<ProviderService> services = new ArrayList<>();
    private ServiceActionListener listener;

    public interface ServiceActionListener {
        void onEdit(ProviderService service);
        void onDelete(ProviderService service);
        void onToggleAvailability(ProviderService service, boolean isAvailable);
    }

    public ProviderServicesAdapter(Context context, ServiceActionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setServices(List<ProviderService> services) {
        this.services = services != null ? services : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ServiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_provider_service, parent, false);
        return new ServiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ServiceViewHolder holder, int position) {
        ProviderService service = services.get(position);
        holder.bind(service);
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    class ServiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvServiceName, tvCategory, tvPrice, tvDescription;
        SwitchCompat switchAvailability;
        MaterialButton btnEdit, btnDelete;
        CardView cardService;

        ServiceViewHolder(@NonNull View itemView) {
            super(itemView);
            cardService = itemView.findViewById(R.id.cardService);
            tvServiceName = itemView.findViewById(R.id.tvServiceName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            switchAvailability = itemView.findViewById(R.id.switchAvailability);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(ProviderService service) {
            tvServiceName.setText(service.serviceName.isEmpty() ? "Service" : service.serviceName);
            tvCategory.setText(service.category.isEmpty() ? "Category" : service.category);
            tvPrice.setText("₹" + String.format("%.0f", service.price));
            tvDescription.setText(service.description.isEmpty() ? "No description" : service.description);

            switchAvailability.setChecked(service.isAvailable);
            switchAvailability.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onToggleAvailability(service, isChecked);
                }
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(service);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(service);
            });

            // Visual indication of availability
            if (service.isAvailable) {
                cardService.setCardBackgroundColor(Color.WHITE);
                tvServiceName.setAlpha(1.0f);
            } else {
                cardService.setCardBackgroundColor(Color.parseColor("#F5F5F5"));
                tvServiceName.setAlpha(0.6f);
            }
        }
    }
}

