package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminSettingsActivity extends BaseActivity {

    private SwitchMaterial switchPush, switchEmail, switchAdminAlerts;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchAutoApprove, switchEmailDigest;
    private SwitchMaterial switchMaintenance;
    private LinearLayout layoutMaintenanceSettings;

    private View notificationsSectionTitle;
    private View notificationsSectionCard;

    private LinearLayout settingsChangePassword, settingsViewProfile, settingsLogout;
    private TextView tvAdminEmail, tvAdminRole, tvAppVersion;

    private FirebaseAuth mAuth;
    private boolean isApplyingUiState;

    private static final String UPDATE_ERROR_MSG = "Failed to update setting. Please try again.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_settings);

        mAuth = FirebaseAuth.getInstance();
        setupToolbar();
        bindViews();
        applyRequestedVisibilityScope();
        loadSystemInfo();
        setupListeners();
        loadSettingsFromDatabase();
    }

    private void setupToolbar() {
        MaterialToolbar tb = findViewById(R.id.toolbarAdminSettings);
        if (tb == null) return;
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        tb.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        notificationsSectionTitle = findViewById(R.id.tvNotificationsSection);
        notificationsSectionCard = findViewById(R.id.cardNotificationsSection);

        switchPush = findViewById(R.id.switchAdminPushNotifications);
        switchEmail = findViewById(R.id.switchAdminEmailNotifications);
        switchAdminAlerts = findViewById(R.id.switchAdminAlerts);
        switchDarkMode = findViewById(R.id.switchAdminDarkMode);
        switchAutoApprove = findViewById(R.id.switchAutoApprove);
        switchEmailDigest = findViewById(R.id.switchEmailDigest);
        switchMaintenance = findViewById(R.id.switchMaintenanceMode);
        layoutMaintenanceSettings = findViewById(R.id.layoutMaintenanceSettings);

        settingsChangePassword = findViewById(R.id.adminSettingsChangePassword);
        settingsViewProfile = findViewById(R.id.adminSettingsViewProfile);
        settingsLogout = findViewById(R.id.adminSettingsLogout);

        tvAdminEmail = findViewById(R.id.tvAdminEmail);
        tvAdminRole = findViewById(R.id.tvAdminRole);
        tvAppVersion = findViewById(R.id.tvAppVersion);
    }

    private void loadSettingsFromDatabase() {
        setSwitchesEnabled(false);
        AdminSettingsRepository.fetchOrCreate(this, new AdminSettingsRepository.SettingsCallback() {
            @Override
            public void onSuccess(AppSettings.Snapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;
                applySnapshotToUi(snapshot);
                setSwitchesEnabled(true);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                applySnapshotToUi(AppSettings.getSnapshot(AdminSettingsActivity.this));
                setSwitchesEnabled(true);
                toast("Failed to load admin settings. Please try again.");
            }
        });
    }

    private void applySnapshotToUi(AppSettings.Snapshot s) {
        if (s == null) return;
        isApplyingUiState = true;
        setChecked(switchPush, s.pushNotifications);
        setChecked(switchEmail, s.emailNotifications);
        setChecked(switchAdminAlerts, s.adminAlerts);
        setChecked(switchDarkMode, s.darkMode);
        setChecked(switchAutoApprove, s.autoApproveProviders);
        setChecked(switchEmailDigest, s.dailyEmailDigest);
        setChecked(switchMaintenance, s.maintenanceMode);
        updateMaintenanceSettingsVisibility(s.maintenanceMode);
        isApplyingUiState = false;
    }

    private void setChecked(SwitchMaterial sw, boolean checked) {
        if (sw != null) sw.setChecked(checked);
    }

    private void setSwitchesEnabled(boolean enabled) {
        if (switchPush != null) switchPush.setEnabled(enabled);
        if (switchEmail != null) switchEmail.setEnabled(enabled);
        if (switchAdminAlerts != null) switchAdminAlerts.setEnabled(enabled);
        if (switchDarkMode != null) switchDarkMode.setEnabled(enabled);
        if (switchAutoApprove != null) switchAutoApprove.setEnabled(enabled);
        if (switchEmailDigest != null) switchEmailDigest.setEnabled(enabled);
        if (switchMaintenance != null) switchMaintenance.setEnabled(enabled);
    }

    private void loadSystemInfo() {
        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        if (tvAdminEmail != null) {
            String email = (user != null && user.getEmail() != null) ? user.getEmail() : "Not available";
            tvAdminEmail.setText(email);
        }
        if (tvAdminRole != null) tvAdminRole.setText("Administrator");
        if (tvAppVersion != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvAppVersion.setText("v" + v);
            } catch (Exception ignored) {
                tvAppVersion.setText("v1.0.0");
            }
        }
    }

    private void setupListeners() {
        if (switchPush != null) {
            switchPush.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                updateBooleanSetting("push_notifications", on, switchPush);
            });
        }

        if (switchEmail != null) {
            switchEmail.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                updateBooleanSetting("email_notifications", on, switchEmail);
            });
        }

        if (switchAdminAlerts != null) {
            switchAdminAlerts.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                updateBooleanSetting("admin_alerts", on, switchAdminAlerts);
            });
        }

        if (switchDarkMode != null) {
            switchDarkMode.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                updateBooleanSetting("dark_mode", on, switchDarkMode);
            });
        }

        if (switchAutoApprove != null) {
            switchAutoApprove.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                if (on) {
                    new AlertDialog.Builder(this)
                            .setTitle("Enable Auto-Approval?")
                            .setMessage("New provider applications will be approved automatically.")
                            .setPositiveButton("Enable", (d, w) ->
                                    updateBooleanSetting("auto_approve_providers", true, switchAutoApprove))
                            .setNegativeButton("Cancel", (d, w) -> rollbackSwitch(switchAutoApprove, false))
                            .setOnCancelListener(d -> rollbackSwitch(switchAutoApprove, false))
                            .show();
                } else {
                    updateBooleanSetting("auto_approve_providers", false, switchAutoApprove);
                }
            });
        }

        if (switchEmailDigest != null) {
            switchEmailDigest.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                updateBooleanSetting("daily_email_digest", on, switchEmailDigest);
            });
        }

        if (switchMaintenance != null) {
            switchMaintenance.setOnCheckedChangeListener((v, on) -> {
                if (isApplyingUiState) return;
                if (on) {
                    showMaintenanceConfigDialog();
                } else {
                    AppSettings.Snapshot current = AppSettings.getSnapshot(this);
                    updateMaintenanceMode(false, current.maintenanceMessage, current.maintenanceEndTime);
                }
            });
        }

        if (settingsChangePassword != null) {
            settingsChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));
        }

        if (settingsViewProfile != null) {
            settingsViewProfile.setOnClickListener(v -> finish());
        }

        if (settingsLogout != null) {
            settingsLogout.setOnClickListener(v -> confirmLogout());
        }
    }

    private void updateBooleanSetting(String column, boolean value, SwitchMaterial sourceSwitch) {
        boolean previous = sourceSwitch != null && sourceSwitch.isChecked() != value
                ? sourceSwitch.isChecked()
                : !value;
        setSwitchesEnabled(false);
        AdminSettingsRepository.updateBooleanField(this, column, value,
                new AdminSettingsRepository.UpdateCallback() {
                    @Override
                    public void onSuccess(AppSettings.Snapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        loadSettingsFromDatabase();
                        toast("Setting updated");
                    }

                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        rollbackSwitch(sourceSwitch, previous);
                        setSwitchesEnabled(true);
                        toast(UPDATE_ERROR_MSG);
                    }
                });
    }

    private void showMaintenanceConfigDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_maintenance_config, null);
        EditText etMessage = dialogView != null ? dialogView.findViewById(R.id.etMaintenanceMessage) : null;
        EditText etTime = dialogView != null ? dialogView.findViewById(R.id.etRestartTime) : null;

        AppSettings.Snapshot cached = AppSettings.getSnapshot(this);
        if (etMessage != null) etMessage.setText(cached.maintenanceMessage);
        if (etTime != null) etTime.setText(cached.maintenanceEndTime);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Enable Maintenance Mode")
                .setMessage("Users and providers will be blocked. Admin access remains available.")
                .setPositiveButton("Enable", (d, w) -> {
                    String msg = etMessage != null && etMessage.getText() != null
                            ? etMessage.getText().toString().trim() : "";
                    String end = etTime != null && etTime.getText() != null
                            ? etTime.getText().toString().trim() : "";
                    updateMaintenanceMode(true, msg, end);
                })
                .setNegativeButton("Cancel", (d, w) -> rollbackSwitch(switchMaintenance, false))
                .setOnCancelListener(d -> rollbackSwitch(switchMaintenance, false));

        if (dialogView != null) builder.setView(dialogView);
        builder.show();
    }

    private void updateMaintenanceMode(boolean enabled, String message, String endTime) {
        setSwitchesEnabled(false);
        boolean expectedRollback = !enabled;
        AdminSettingsRepository.updateMaintenance(this, enabled, message, endTime,
                new AdminSettingsRepository.UpdateCallback() {
                    @Override
                    public void onSuccess(AppSettings.Snapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        applySnapshotToUi(snapshot);
                        setSwitchesEnabled(true);

                        if (snapshot.maintenanceMode) {
                            FcmNotificationHelper.notifyAdminSystemAlert(
                                    "Maintenance Mode Enabled",
                                    snapshot.maintenanceMessage != null
                                            ? snapshot.maintenanceMessage
                                            : "Maintenance mode enabled by admin.");
                        } else {
                            FcmNotificationHelper.notifyAdminSystemAlert(
                                    "Maintenance Mode Disabled",
                                    "Maintenance mode was disabled. App access restored.");
                        }

                        loadSettingsFromDatabase();
                        toast("Setting updated");
                    }

                    @Override
                    public void onError(String error) {
                        if (isFinishing() || isDestroyed()) return;
                        rollbackSwitch(switchMaintenance, expectedRollback);
                        updateMaintenanceSettingsVisibility(expectedRollback);
                        setSwitchesEnabled(true);
                        toast(UPDATE_ERROR_MSG);
                    }
                });
    }

    private void rollbackSwitch(SwitchMaterial sw, boolean checked) {
        isApplyingUiState = true;
        if (sw != null) sw.setChecked(checked);
        isApplyingUiState = false;
    }

    private void updateMaintenanceSettingsVisibility(boolean visible) {
        if (layoutMaintenanceSettings != null) {
            layoutMaintenanceSettings.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from the admin panel?")
                .setPositiveButton("Logout", (d, w) -> {
                    if (mAuth != null) mAuth.signOut();
                    Intent i = new Intent(this, LoginActivity.class);
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

    private void applyRequestedVisibilityScope() {
        if (notificationsSectionTitle != null) notificationsSectionTitle.setVisibility(View.GONE);
        if (notificationsSectionCard != null) notificationsSectionCard.setVisibility(View.GONE);

        if (switchEmailDigest != null) {
            View row = (View) switchEmailDigest.getParent();
            if (row != null) row.setVisibility(View.GONE);
        }
        if (layoutMaintenanceSettings != null) layoutMaintenanceSettings.setVisibility(View.GONE);

        if (settingsChangePassword != null) settingsChangePassword.setVisibility(View.GONE);
        if (settingsViewProfile != null) settingsViewProfile.setVisibility(View.GONE);
    }

    @Override
    protected boolean isAdminThemeScreen() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSettingsFromDatabase();
    }
}
