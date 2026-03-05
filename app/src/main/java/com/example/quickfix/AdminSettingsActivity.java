package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * AdminSettingsActivity — Admin-specific settings panel.
 * 
 * Features:
 *  • Notification preferences (push, email, admin alerts)
 *  • Display settings (dark mode)
 *  • Admin preferences (auto-approval thresholds, email digests)
 *  • Account management (change password, logout)
 *  • System info display
 */
public class AdminSettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "admin_settings_prefs";
    
    // Notification keys
    private static final String KEY_PUSH_NOTIF = "admin_notif_push";
    private static final String KEY_EMAIL_NOTIF = "admin_notif_email";
    private static final String KEY_ADMIN_ALERTS = "admin_alerts";
    
    // Display keys
    private static final String KEY_DARK_MODE = "admin_dark_mode";
    
    // Admin preference keys
    private static final String KEY_AUTO_APPROVE = "admin_auto_approve";
    private static final String KEY_EMAIL_DIGEST = "admin_email_digest";
    
    // Views - Notifications
    private SwitchMaterial switchPush, switchEmail, switchAdminAlerts;
    
    // Views - Display
    private SwitchMaterial switchDarkMode;
    
    // Views - Admin Preferences
    private SwitchMaterial switchAutoApprove, switchEmailDigest;
    
    // Views - Account
    private LinearLayout settingsChangePassword, settingsViewProfile, settingsLogout;
    
    // Views - System Info
    private TextView tvAdminEmail, tvAdminRole, tvAppVersion;
    
    private SharedPreferences prefs;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_settings);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        setupToolbar();
        bindViews();
        loadPreferences();
        loadSystemInfo();
        setupListeners();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarAdminSettings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void bindViews() {
        // Notifications
        switchPush = findViewById(R.id.switchAdminPushNotifications);
        switchEmail = findViewById(R.id.switchAdminEmailNotifications);
        switchAdminAlerts = findViewById(R.id.switchAdminAlerts);
        
        // Display
        switchDarkMode = findViewById(R.id.switchAdminDarkMode);
        
        // Admin Preferences
        switchAutoApprove = findViewById(R.id.switchAutoApprove);
        switchEmailDigest = findViewById(R.id.switchEmailDigest);
        
        // Account
        settingsChangePassword = findViewById(R.id.adminSettingsChangePassword);
        settingsViewProfile = findViewById(R.id.adminSettingsViewProfile);
        settingsLogout = findViewById(R.id.adminSettingsLogout);
        
        // System Info
        tvAdminEmail = findViewById(R.id.tvAdminEmail);
        tvAdminRole = findViewById(R.id.tvAdminRole);
        tvAppVersion = findViewById(R.id.tvAppVersion);
    }

    private void loadPreferences() {
        // Notifications
        if (switchPush != null) {
            switchPush.setChecked(prefs.getBoolean(KEY_PUSH_NOTIF, true));
        }
        if (switchEmail != null) {
            switchEmail.setChecked(prefs.getBoolean(KEY_EMAIL_NOTIF, true));
        }
        if (switchAdminAlerts != null) {
            switchAdminAlerts.setChecked(prefs.getBoolean(KEY_ADMIN_ALERTS, true));
        }
        
        // Display
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        }
        
        // Admin Preferences
        if (switchAutoApprove != null) {
            switchAutoApprove.setChecked(prefs.getBoolean(KEY_AUTO_APPROVE, false));
        }
        if (switchEmailDigest != null) {
            switchEmailDigest.setChecked(prefs.getBoolean(KEY_EMAIL_DIGEST, true));
        }
    }

    private void loadSystemInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        
        // Admin Email
        if (tvAdminEmail != null && user != null) {
            tvAdminEmail.setText(user.getEmail() != null ? user.getEmail() : "Not available");
        }
        
        // Admin Role - default for now, could be fetched from Supabase
        if (tvAdminRole != null) {
            tvAdminRole.setText("Administrator");
        }
        
        // App Version
        if (tvAppVersion != null) {
            try {
                String version = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                tvAppVersion.setText("v" + version);
            } catch (Exception e) {
                tvAppVersion.setText("v1.0.0");
            }
        }
    }

    private void setupListeners() {
        // Notification Switches
        if (switchPush != null) {
            switchPush.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_PUSH_NOTIF, checked).apply();
                showToast(checked ? "Push notifications enabled" : "Push notifications disabled");
            });
        }
        
        if (switchEmail != null) {
            switchEmail.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_EMAIL_NOTIF, checked).apply();
                showToast(checked ? "Email notifications enabled" : "Email notifications disabled");
            });
        }
        
        if (switchAdminAlerts != null) {
            switchAdminAlerts.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_ADMIN_ALERTS, checked).apply();
                showToast(checked ? "Admin alerts enabled" : "Admin alerts disabled");
            });
        }
        
        // Display Switch
        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_DARK_MODE, checked).apply();
                AppCompatDelegate.setDefaultNightMode(
                        checked ? AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);
                showToast(checked ? "Dark mode enabled" : "Dark mode disabled");
            });
        }
        
        // Admin Preference Switches
        if (switchAutoApprove != null) {
            switchAutoApprove.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_AUTO_APPROVE, checked).apply();
                if (checked) {
                    showConfirmDialog(
                        "Auto-Approval Enabled",
                        "Provider applications meeting minimum requirements will be automatically approved. You can review them later.",
                        () -> showToast("Auto-approval enabled")
                    );
                } else {
                    showToast("Auto-approval disabled");
                }
            });
        }
        
        if (switchEmailDigest != null) {
            switchEmailDigest.setOnCheckedChangeListener((v, checked) -> {
                prefs.edit().putBoolean(KEY_EMAIL_DIGEST, checked).apply();
                showToast(checked ? "Daily email digest enabled" : "Daily email digest disabled");
            });
        }
        
        // Account Management
        if (settingsChangePassword != null) {
            settingsChangePassword.setOnClickListener(v -> {
                Intent intent = new Intent(this, ForgotPasswordActivity.class);
                startActivity(intent);
            });
        }
        
        if (settingsViewProfile != null) {
            settingsViewProfile.setOnClickListener(v -> {
                showToast("Opening admin profile...");
                // Could navigate to a dedicated admin profile page
                finish();
            });
        }
        
        if (settingsLogout != null) {
            settingsLogout.setOnClickListener(v -> confirmLogout());
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from the admin panel?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    clearPreferences();
                    Intent intent = new Intent(this, AdminLoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearPreferences() {
        // Clear admin preferences on logout
        prefs.edit().clear().apply();
    }

    private void showConfirmDialog(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> {
                    if (onConfirm != null) onConfirm.run();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Revert the switch if cancelled
                    if (switchAutoApprove != null) {
                        switchAutoApprove.setChecked(false);
                    }
                })
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}

