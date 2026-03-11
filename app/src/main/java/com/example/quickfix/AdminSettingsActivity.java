package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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
 * AdminSettingsActivity — All toggles persist via AppSettings (SharedPreferences).
 *
 * Toggles:
 *  • Push Notifications     → saved, loaded on next open
 *  • Email Notifications    → saved, loaded on next open
 *  • Admin Alerts           → saved, loaded on next open
 *  • Dark Mode              → immediately applies + persists across restarts
 *  • Auto-approve Providers → saved with confirmation dialog
 *  • Daily Email Digest     → saved, loaded on next open
 *  • Maintenance Mode       → saved; SplashActivity reads it and blocks users
 */
public class AdminSettingsActivity extends BaseActivity {

    // Views — Notifications
    private SwitchMaterial switchPush, switchEmail, switchAdminAlerts;
    // Views — Display
    private SwitchMaterial switchDarkMode;
    // Views — Admin Preferences
    private SwitchMaterial switchAutoApprove, switchEmailDigest;
    // Views — Maintenance
    private SwitchMaterial switchMaintenance;
    private LinearLayout   layoutMaintenanceSettings;
    // Views — Account
    private LinearLayout settingsChangePassword, settingsViewProfile, settingsLogout;
    // Views — System Info
    private TextView tvAdminEmail, tvAdminRole, tvAppVersion;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_settings);

        mAuth = FirebaseAuth.getInstance();
        setupToolbar();
        bindViews();
        loadPreferences();
        loadSystemInfo();
        setupListeners();
    }

    private void setupToolbar() {
        MaterialToolbar tb = findViewById(R.id.toolbarAdminSettings);
        if (tb != null) {
            setSupportActionBar(tb);
            if (getSupportActionBar() != null)
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            tb.setNavigationOnClickListener(v -> finish());
        }
    }

    private void bindViews() {
        switchPush         = findViewById(R.id.switchAdminPushNotifications);
        switchEmail        = findViewById(R.id.switchAdminEmailNotifications);
        switchAdminAlerts  = findViewById(R.id.switchAdminAlerts);
        switchDarkMode     = findViewById(R.id.switchAdminDarkMode);
        switchAutoApprove  = findViewById(R.id.switchAutoApprove);
        switchEmailDigest  = findViewById(R.id.switchEmailDigest);
        switchMaintenance  = findViewById(R.id.switchMaintenanceMode);
        layoutMaintenanceSettings = findViewById(R.id.layoutMaintenanceSettings);

        settingsChangePassword = findViewById(R.id.adminSettingsChangePassword);
        settingsViewProfile    = findViewById(R.id.adminSettingsViewProfile);
        settingsLogout         = findViewById(R.id.adminSettingsLogout);

        tvAdminEmail  = findViewById(R.id.tvAdminEmail);
        tvAdminRole   = findViewById(R.id.tvAdminRole);
        tvAppVersion  = findViewById(R.id.tvAppVersion);
    }

    // ── Load saved preferences → set switch states ────────────────────────
    private void loadPreferences() {
        setSilently(switchPush,        AppSettings.isPushEnabled(this));
        setSilently(switchEmail,       AppSettings.isEmailNotifEnabled(this));
        setSilently(switchAdminAlerts, AppSettings.isAdminAlertsEnabled(this));
        // Use ThemeManager as single source of truth for dark mode
        setSilently(switchDarkMode,    ThemeManager.isDarkMode(this));
        setSilently(switchAutoApprove, AppSettings.isAutoApprove(this));
        setSilently(switchEmailDigest, AppSettings.isEmailDigestEnabled(this));
        setSilently(switchMaintenance, AppSettings.isMaintenanceMode(this));

        updateMaintenanceSettingsVisibility(AppSettings.isMaintenanceMode(this));
    }

    /** Set checked state WITHOUT firing the listener. */
    private void setSilently(SwitchMaterial sw, boolean checked) {
        if (sw == null) return;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
    }

    private void loadSystemInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (tvAdminEmail != null)
            tvAdminEmail.setText(user != null && user.getEmail() != null
                    ? user.getEmail() : "Not available");
        if (tvAdminRole  != null) tvAdminRole.setText("Administrator");
        if (tvAppVersion != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvAppVersion.setText("v" + v);
            } catch (Exception e) { tvAppVersion.setText("v1.0.0"); }
        }
    }

    // ── Wire up all listeners ─────────────────────────────────────────────
    private void setupListeners() {

        // ── Push Notifications ────────────────────────────────────────────
        if (switchPush != null)
            switchPush.setOnCheckedChangeListener((v, on) -> {
                AppSettings.set(this, AppSettings.KEY_PUSH_NOTIF, on);
                toast(on ? "Push notifications enabled" : "Push notifications disabled");
            });

        // ── Email Notifications ───────────────────────────────────────────
        if (switchEmail != null)
            switchEmail.setOnCheckedChangeListener((v, on) -> {
                AppSettings.set(this, AppSettings.KEY_EMAIL_NOTIF, on);
                toast(on ? "Email notifications enabled" : "Email notifications disabled");
            });

        // ── Admin Alerts ──────────────────────────────────────────────────
        if (switchAdminAlerts != null)
            switchAdminAlerts.setOnCheckedChangeListener((v, on) -> {
                AppSettings.set(this, AppSettings.KEY_ADMIN_ALERTS, on);
                toast(on ? "Admin alerts enabled" : "Admin alerts disabled");
            });

        // ── Dark Mode — applies IMMEDIATELY across entire app ─────────────
        if (switchDarkMode != null)
            switchDarkMode.setOnCheckedChangeListener((v, on) -> {
                // ThemeManager: saves locally + applies immediately + syncs to Supabase
                ThemeManager.toggle(this, on);
                // Also keep AppSettings key in sync for backwards compatibility
                AppSettings.set(this, AppSettings.KEY_DARK_MODE, on);
                toast(on ? "🌙 Dark mode enabled" : "☀️ Light mode enabled");
            });

        // ── Auto-approve Providers ────────────────────────────────────────
        if (switchAutoApprove != null)
            switchAutoApprove.setOnCheckedChangeListener((v, on) -> {
                if (on) {
                    new AlertDialog.Builder(this)
                        .setTitle("Enable Auto-Approval?")
                        .setMessage("Provider applications that meet minimum requirements "
                                + "will be automatically approved. You can review them later.")
                        .setPositiveButton("Enable", (d, w) -> {
                            AppSettings.set(this, AppSettings.KEY_AUTO_APPROVE, true);
                            toast("Auto-approval enabled");
                        })
                        .setNegativeButton("Cancel", (d, w) -> {
                            setSilently(switchAutoApprove, false);
                            AppSettings.set(this, AppSettings.KEY_AUTO_APPROVE, false);
                        })
                        .show();
                } else {
                    AppSettings.set(this, AppSettings.KEY_AUTO_APPROVE, false);
                    toast("Auto-approval disabled");
                }
            });

        // ── Daily Email Digest ────────────────────────────────────────────
        if (switchEmailDigest != null)
            switchEmailDigest.setOnCheckedChangeListener((v, on) -> {
                AppSettings.set(this, AppSettings.KEY_EMAIL_DIGEST, on);
                toast(on ? "Daily digest enabled" : "Daily digest disabled");
            });

        // ── Maintenance Mode ──────────────────────────────────────────────
        if (switchMaintenance != null)
            switchMaintenance.setOnCheckedChangeListener((v, on) -> {
                if (on) {
                    showMaintenanceConfigDialog();
                } else {
                    AppSettings.set(this, AppSettings.KEY_MAINTENANCE_MODE, false);
                    updateMaintenanceSettingsVisibility(false);
                    toast("Maintenance mode disabled — app is live");
                }
            });

        // ── Account ───────────────────────────────────────────────────────
        if (settingsChangePassword != null)
            settingsChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));

        if (settingsViewProfile != null)
            settingsViewProfile.setOnClickListener(v -> {
                toast("Opening admin profile…");
                finish();
            });

        if (settingsLogout != null)
            settingsLogout.setOnClickListener(v -> confirmLogout());
    }

    // ── Maintenance config dialog ─────────────────────────────────────────
    private void showMaintenanceConfigDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_maintenance_config, null);
        EditText etMessage = dialogView != null
                ? dialogView.findViewById(R.id.etMaintenanceMessage) : null;
        EditText etTime    = dialogView != null
                ? dialogView.findViewById(R.id.etRestartTime) : null;

        // Pre-fill with existing values
        if (etMessage != null)
            etMessage.setText(AppSettings.getMaintenanceMessage(this));
        if (etTime != null)
            etTime.setText(AppSettings.getMaintenanceRestartTime(this));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("🔧 Enable Maintenance Mode")
                .setMessage("Users and providers will see a maintenance page.\n"
                        + "Admin panel remains accessible.")
                .setPositiveButton("Enable", (d, w) -> {
                    String msg  = etMessage != null && etMessage.getText() != null
                            ? etMessage.getText().toString().trim() : "";
                    String time = etTime    != null && etTime.getText()    != null
                            ? etTime.getText().toString().trim() : "";

                    AppSettings.set(this, AppSettings.KEY_MAINTENANCE_MODE, true);
                    AppSettings.setString(this, AppSettings.KEY_MAINTENANCE_MSG,
                            msg.isEmpty() ? "We are currently performing scheduled maintenance.\nWe'll be back shortly." : msg);
                    AppSettings.setString(this, AppSettings.KEY_MAINTENANCE_TIME, time);

                    setSilently(switchMaintenance, true);
                    updateMaintenanceSettingsVisibility(true);
                    toast("⚠️ Maintenance mode ENABLED — users see maintenance page");
                })
                .setNegativeButton("Cancel", (d, w) -> {
                    // Revert switch
                    setSilently(switchMaintenance, false);
                    AppSettings.set(this, AppSettings.KEY_MAINTENANCE_MODE, false);
                });

        if (dialogView != null) builder.setView(dialogView);
        builder.show();
    }

    private void updateMaintenanceSettingsVisibility(boolean visible) {
        if (layoutMaintenanceSettings != null)
            layoutMaintenanceSettings.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    // ── Logout ────────────────────────────────────────────────────────────
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from the admin panel?")
                .setPositiveButton("Logout", (d, w) -> {
                    mAuth.signOut();
                    Intent i = new Intent(this, AdminLoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

