package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

/**
 * AdminDashboardActivity — Main hub for admin panel.
 *
 * Provides navigation to:
 *  - Business Approvals
 *  - User Management
 *  - Service Management
 *  - Content Management
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private TextView adminWelcomeText;
    private MaterialButton btnBusinessApprovals, btnUserManagement,
            btnServiceManagement, btnContentManagement, btnViewLogs, btnLogout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupWelcomeMessage();
        setupClickListeners();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.adminToolbar);
        setSupportActionBar(toolbar);

        adminWelcomeText     = findViewById(R.id.adminWelcomeText);
        btnBusinessApprovals = findViewById(R.id.btnBusinessApprovals);
        btnUserManagement    = findViewById(R.id.btnUserManagement);
        btnServiceManagement = findViewById(R.id.btnServiceManagement);
        btnContentManagement = findViewById(R.id.btnContentManagement);
        btnViewLogs          = findViewById(R.id.btnViewLogs);
        btnLogout            = findViewById(R.id.btnLogout);
    }

    private void setupWelcomeMessage() {
        String roleName = getIntent().getStringExtra("admin_role");
        if (roleName != null && !roleName.isEmpty()) {
            adminWelcomeText.setText("Welcome, " + roleName);
        } else if (mAuth.getCurrentUser() != null) {
            String email = mAuth.getCurrentUser().getEmail();
            adminWelcomeText.setText("Welcome, " + (email != null ? email : "Admin"));
        }
    }

    private void setupClickListeners() {
        btnBusinessApprovals.setOnClickListener(v ->
                startActivity(new Intent(this, BusinessApprovalActivity.class)));

        btnUserManagement.setOnClickListener(v ->
                startActivity(new Intent(this, UserManagementActivity.class)));

        btnServiceManagement.setOnClickListener(v ->
                startActivity(new Intent(this, ServiceManagementActivity.class)));

        btnContentManagement.setOnClickListener(v ->
                startActivity(new Intent(this, ContentManagementActivity.class)));

        btnViewLogs.setOnClickListener(v ->
                Toast.makeText(this, "Admin Logs — coming soon", Toast.LENGTH_SHORT).show());

        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from the admin panel?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        // Prevent accidental back-press from admin dashboard
        new AlertDialog.Builder(this)
                .setTitle("Exit Admin Panel?")
                .setMessage("Do you want to go back to the main app?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(AdminDashboardActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Stay", null)
                .show();
    }
}

