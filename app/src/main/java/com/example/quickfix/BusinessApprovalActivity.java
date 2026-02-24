package com.example.quickfix;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;

/**
 * BusinessApprovalActivity — Displays pending provider applications.
 * Admin can approve or reject with reason.
 */
public class BusinessApprovalActivity extends AppCompatActivity {

    private static final String TAG = "BusinessApprovalActivity";

    private ListView pendingApplicationsList;
    private Button refreshButton;

    private FirebaseAuth mAuth;
    private String currentAdminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_approval);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        currentAdminUid = currentUser.getUid();

        initViews();
        loadPendingApplications();
    }

    private void initViews() {
        pendingApplicationsList = findViewById(R.id.pendingApplicationsList);
        refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> loadPendingApplications());
    }

    private void loadPendingApplications() {
        refreshButton.setEnabled(false);
        SupabaseClient.fetchPendingApprovals(new SupabaseClient.QueryCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                Log.d(TAG, "Loaded " + result.length() + " pending applications");
                refreshButton.setEnabled(true);
                // TODO: Create adapter and display in ListView
                Toast.makeText(BusinessApprovalActivity.this,
                        "Loaded " + result.length() + " pending applications",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading applications: " + message);
                refreshButton.setEnabled(true);
                Toast.makeText(BusinessApprovalActivity.this,
                        "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Approve a provider application
     */
    public void approveApplication(String applicationId, String userFirebaseUid) {
        SupabaseClient.approveProvider(applicationId, userFirebaseUid, currentAdminUid,
                new SupabaseClient.Callback2() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(BusinessApprovalActivity.this,
                                "Provider approved!", Toast.LENGTH_SHORT).show();
                        loadPendingApplications();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(BusinessApprovalActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Reject a provider application
     */
    public void rejectApplication(String applicationId, String rejectionReason) {
        SupabaseClient.rejectProvider(applicationId, rejectionReason, currentAdminUid,
                new SupabaseClient.Callback2() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(BusinessApprovalActivity.this,
                                "Provider rejected", Toast.LENGTH_SHORT).show();
                        loadPendingApplications();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(BusinessApprovalActivity.this,
                                "Error: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

