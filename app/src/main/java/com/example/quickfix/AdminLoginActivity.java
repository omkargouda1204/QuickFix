package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * AdminLoginActivity — Special login screen for admin accounts.
 *
 * Shows login form. After Firebase auth succeeds, checks if user has admin role.
 * If yes → AdminDashboardActivity
 * If no  → Shows error message, prompts to logout
 */
public class AdminLoginActivity extends AppCompatActivity {

    private static final String TAG = "AdminLoginActivity";

    private EditText emailInput, passwordInput;
    private Button loginButton;
    private TextView switchToUserLogin, statusText;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        mAuth = FirebaseAuth.getInstance();

        // If already logged in with admin role, go to dashboard
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            checkAdminAndNavigate(currentUser);
            return;
        }

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        emailInput = findViewById(R.id.adminEmailInput);
        passwordInput = findViewById(R.id.adminPasswordInput);
        loginButton = findViewById(R.id.adminLoginButton);
        switchToUserLogin = findViewById(R.id.switchToUserLogin);
        statusText = findViewById(R.id.adminStatusText);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> attemptAdminLogin());
        switchToUserLogin.setOnClickListener(v -> {
            startActivity(new Intent(AdminLoginActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void attemptAdminLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (email.isEmpty() || password.isEmpty()) {
            statusText.setText("Please enter email and password");
            statusText.setTextColor(getColor(R.color.error_red));
            return;
        }

        // Show loading state
        loginButton.setEnabled(false);
        statusText.setText("Verifying admin credentials...");
        statusText.setTextColor(getColor(R.color.text_secondary));

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        checkAdminAndNavigate(user);
                    }
                })
                .addOnFailureListener(e -> {
                    loginButton.setEnabled(true);
                    statusText.setText("Login failed: " + e.getMessage());
                    statusText.setTextColor(getColor(R.color.error_red));
                    Log.e(TAG, "Admin login failed: " + e.getMessage());
                });
    }

    /**
     * After Firebase auth succeeds, verify user has admin role
     */
    private void checkAdminAndNavigate(FirebaseUser user) {
        if (!user.isEmailVerified()) {
            statusText.setText("Email not verified. Please verify your email first.");
            statusText.setTextColor(getColor(R.color.error_red));
            loginButton.setEnabled(true);
            mAuth.signOut();
            return;
        }

        RoleManager.checkAdminRole(user.getUid(), new RoleManager.RoleCheckCallback() {
            @Override
            public void onAdminVerified(String adminRoleId, String roleName) {
                Log.d(TAG, "Admin verified: " + roleName);
                statusText.setText("Welcome, " + roleName + "!");
                statusText.setTextColor(getColor(R.color.primary));

                // Navigate to admin dashboard
                Intent intent = new Intent(AdminLoginActivity.this, AdminDashboardActivity.class);
                intent.putExtra("admin_role", roleName);
                intent.putExtra("user_uid", user.getUid());
                startActivity(intent);
                finish();
            }

            @Override
            public void onNotAdmin() {
                Log.w(TAG, "Non-admin tried to access admin login");
                statusText.setText("Access denied. You do not have admin privileges.");
                statusText.setTextColor(getColor(R.color.error_red));
                loginButton.setEnabled(true);

                // Logout the user
                mAuth.signOut();
                Toast.makeText(AdminLoginActivity.this,
                        "This account is not authorized as an admin",
                        Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error checking admin role: " + message);
                statusText.setText("Error: " + message);
                statusText.setTextColor(getColor(R.color.error_red));
                loginButton.setEnabled(true);
                mAuth.signOut();
            }
        });
    }
}

