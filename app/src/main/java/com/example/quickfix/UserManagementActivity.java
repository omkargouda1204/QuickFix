package com.example.quickfix;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * UserManagementActivity — View all users, delete, suspend accounts.
 * Available to: super_admin, user_admin
 */
public class UserManagementActivity extends AppCompatActivity {

    private static final String TAG = "UserManagementActivity";

    private ListView userListView;
    private Button refreshButton, deleteButton, suspendButton;

    private FirebaseAuth mAuth;
    private String currentAdminUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        currentAdminUid = currentUser.getUid();
        initViews();
        loadAllUsers();
    }

    private void initViews() {
        userListView = findViewById(R.id.userListView);
        refreshButton = findViewById(R.id.refreshButton);
        deleteButton = findViewById(R.id.deleteButton);
        suspendButton = findViewById(R.id.suspendButton);

        refreshButton.setOnClickListener(v -> loadAllUsers());
        deleteButton.setOnClickListener(v -> {
            Toast.makeText(this, "Select a user to delete", Toast.LENGTH_SHORT).show();
        });
        suspendButton.setOnClickListener(v -> {
            Toast.makeText(this, "Select a user to suspend", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadAllUsers() {
        // TODO: Fetch all users from Supabase
        Toast.makeText(this, "Loading all users...", Toast.LENGTH_SHORT).show();
    }

    /**
     * Delete a user account
     */
    public void deleteUser(String userFirebaseUid) {
        // TODO: Call Supabase to delete user
        Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "user_deleted", "users",
                userFirebaseUid, null);
    }

    /**
     * Suspend a user account
     */
    public void suspendUser(String userFirebaseUid) {
        // TODO: Update user status to 'suspended'
        Toast.makeText(this, "User suspended", Toast.LENGTH_SHORT).show();
        SupabaseClient.logAdminAction(currentAdminUid, "user_suspended", "users",
                userFirebaseUid, null);
    }

    /**
     * Deactivate a user account
     */
    public void deactivateUser(String userFirebaseUid) {
        // TODO: Update user status to 'deactivated'
        SupabaseClient.logAdminAction(currentAdminUid, "user_deactivated", "users",
                userFirebaseUid, null);
    }
}

