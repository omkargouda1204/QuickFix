package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * SettingsActivity — App settings: notifications, dark mode, account preferences.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS_NAME    = "quickfix_prefs";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_PUSH_NOTIF  = "notif_push";
    private static final String KEY_EMAIL_NOTIF = "notif_email";
    private static final String KEY_BOOKING_REM = "notif_booking_reminders";

    private SwitchMaterial switchPush, switchEmail, switchBookingRem, switchDarkMode;
    private LinearLayout settingsChangePassword, settingsDeleteAccount;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbarSettings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        bindViews();
        loadPrefs();
        setupListeners();
    }

    private void bindViews() {
        switchPush           = findViewById(R.id.switchPushNotifications);
        switchEmail          = findViewById(R.id.switchEmailNotifications);
        switchBookingRem     = findViewById(R.id.switchBookingReminders);
        switchDarkMode       = findViewById(R.id.switchSettingsDarkMode);
        settingsChangePassword = findViewById(R.id.settingsChangePassword);
        settingsDeleteAccount  = findViewById(R.id.settingsDeleteAccount);
    }

    private void loadPrefs() {
        if (switchPush       != null) switchPush.setChecked(prefs.getBoolean(KEY_PUSH_NOTIF, true));
        if (switchEmail      != null) switchEmail.setChecked(prefs.getBoolean(KEY_EMAIL_NOTIF, true));
        if (switchBookingRem != null) switchBookingRem.setChecked(prefs.getBoolean(KEY_BOOKING_REM, true));
        // Load dark mode from ThemeManager (shared across all roles)
        if (switchDarkMode   != null) switchDarkMode.setChecked(ThemeManager.isDarkMode(this));
    }

    private void setupListeners() {
        if (switchPush != null) {
            switchPush.setOnCheckedChangeListener((v, checked) ->
                    prefs.edit().putBoolean(KEY_PUSH_NOTIF, checked).apply());
        }
        if (switchEmail != null) {
            switchEmail.setOnCheckedChangeListener((v, checked) ->
                    prefs.edit().putBoolean(KEY_EMAIL_NOTIF, checked).apply());
        }
        if (switchBookingRem != null) {
            switchBookingRem.setOnCheckedChangeListener((v, checked) ->
                    prefs.edit().putBoolean(KEY_BOOKING_REM, checked).apply());
        }
        if (switchDarkMode != null) {
            switchDarkMode.setEnabled(true);
            switchDarkMode.setOnCheckedChangeListener((v, checked) -> {
                ThemeManager.toggle(this, checked);
                prefs.edit().putBoolean(KEY_DARK_MODE, checked).apply();
            });
        }

        if (settingsChangePassword != null) {
            settingsChangePassword.setOnClickListener(v -> {
                Intent i = new Intent(this, ForgotPasswordActivity.class);
                startActivity(i);
            });
        }

        if (settingsDeleteAccount != null) {
            settingsDeleteAccount.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Account")
                            .setMessage("Are you sure you want to permanently delete your account? This action cannot be undone.")
                            .setPositiveButton("Delete", (d, w) ->
                                    Toast.makeText(this,
                                            "Account deletion — contact support@quickfix.app",
                                            Toast.LENGTH_LONG).show())
                            .setNegativeButton("Cancel", null)
                            .show());
        }
    }
}

