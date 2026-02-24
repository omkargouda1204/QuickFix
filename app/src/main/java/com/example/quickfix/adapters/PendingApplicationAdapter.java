package com.example.quickfix.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.example.quickfix.R;

import org.json.JSONObject;

import java.util.List;

/**
 * PendingApplicationAdapter - Displays pending provider applications in a ListView.
 * Shows business name, applicant, service type, application date, and action buttons.
 */
public class PendingApplicationAdapter extends ArrayAdapter<JSONObject> {

    private Context context;
    private List<JSONObject> applications;
    private OnActionListener actionListener;

    public interface OnActionListener {
        void onApprove(String applicationId, String userFirebaseUid);
        void onReject(String applicationId);
    }

    public PendingApplicationAdapter(Context context, List<JSONObject> applications,
                                    OnActionListener actionListener) {
        super(context, 0, applications);
        this.context = context;
        this.applications = applications;
        this.actionListener = actionListener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_pending_application, parent, false);
        }

        try {
            JSONObject app = applications.get(position);
            final String applicationId = app.optString("id");
            final String userUid = app.optString("user_firebase_uid");
            String businessName = app.optString("business_name");
            String serviceType = app.optString("service_type");
            String applicationDate = app.optString("application_date");

            TextView businessNameView = convertView.findViewById(R.id.businessName);
            TextView serviceTypeView = convertView.findViewById(R.id.serviceType);
            TextView applicationDateView = convertView.findViewById(R.id.applicationDate);
            Button approveBtn = convertView.findViewById(R.id.approveBtn);
            Button rejectBtn = convertView.findViewById(R.id.rejectBtn);

            businessNameView.setText(businessName);
            serviceTypeView.setText("Service: " + serviceType);
            applicationDateView.setText("Applied: " + applicationDate);

            approveBtn.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onApprove(applicationId, userUid);
                }
            });

            rejectBtn.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onReject(applicationId);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        return convertView;
    }
}

