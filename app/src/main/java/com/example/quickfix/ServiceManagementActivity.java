package com.example.quickfix;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ServiceManagementActivity — Manage all services (add, edit, delete).
 * Available to: super_admin, content_admin
 */
public class ServiceManagementActivity extends AppCompatActivity {

    private static final String TAG = "ServiceManagementActivity";

    private ListView servicesListView;
    private Button addServiceButton, refreshButton, editButton, deleteButton;

    private FirebaseAuth mAuth;
    private String currentAdminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_management);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        currentAdminUid = currentUser.getUid();
        initViews();
        loadAllServices();
    }

    private void initViews() {
        servicesListView = findViewById(R.id.servicesListView);
        addServiceButton = findViewById(R.id.addServiceButton);
        refreshButton = findViewById(R.id.refreshButton);
        editButton = findViewById(R.id.editButton);
        deleteButton = findViewById(R.id.deleteButton);

        addServiceButton.setOnClickListener(v -> showAddServiceDialog());
        refreshButton.setOnClickListener(v -> loadAllServices());
        editButton.setOnClickListener(v -> {
            Toast.makeText(this, "Select a service to edit", Toast.LENGTH_SHORT).show();
        });
        deleteButton.setOnClickListener(v -> {
            Toast.makeText(this, "Select a service to delete", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadAllServices() {
        // TODO: Fetch all services from Supabase
        Toast.makeText(this, "Loading services...", Toast.LENGTH_SHORT).show();
    }

    private void showAddServiceDialog() {
        // TODO: Show dialog to add new service
        Toast.makeText(this, "Add service dialog", Toast.LENGTH_SHORT).show();
    }

    /**
     * Add a new service
     */
    public void addService(String serviceName, String description,
                          String category, double basePrice) {
        // TODO: Add to service_categories table
        Toast.makeText(this, "Service added", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "service_added", "services",
                serviceName, null);
    }

    /**
     * Edit a service
     */
    public void editService(String serviceId, String serviceName,
                           String description, double basePrice) {
        // TODO: Update services table
        Toast.makeText(this, "Service updated", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "service_edited", "services",
                serviceId, null);
    }

    /**
     * Delete a service
     */
    public void deleteService(String serviceId) {
        // TODO: Delete from services table
        Toast.makeText(this, "Service deleted", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "service_deleted", "services",
                serviceId, null);
    }
}

