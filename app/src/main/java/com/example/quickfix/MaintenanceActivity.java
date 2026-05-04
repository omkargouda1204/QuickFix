package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * MaintenanceActivity — shown after SplashActivity when admin has enabled
 * Maintenance Mode. Blocks users/providers from accessing the app.
 * Admin panel is NOT affected — admins bypass this screen.
 *
 * When "Check Again" is tapped it re-reads SharedPreferences; if maintenance
 * mode has been turned off by admin the user proceeds normally.
 */
public class MaintenanceActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maintenance);

        // Disable back navigation
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Do nothing to block going back
            }
        });

        bindAndFill();
        setupRefreshButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If maintenance was turned off while this screen was open → navigate away
        if (!AppSettings.isMaintenanceMode(this)) {
            navigateToLogin();
        }
    }

    private void bindAndFill() {
        // Message
        TextView tvMessage = findViewById(R.id.tvMaintenanceMessage);
        if (tvMessage != null) {
            String msg = AppSettings.getMaintenanceMessage(this);
            tvMessage.setText(msg.isEmpty()
                    ? "We are currently performing scheduled maintenance.\nWe'll be back shortly."
                    : msg);
        }

        // Restart time
        String restartTime = AppSettings.getMaintenanceRestartTime(this);
        LinearLayout layoutRestart = findViewById(R.id.layoutRestartTime);
        TextView tvRestart = findViewById(R.id.tvRestartTime);
        if (layoutRestart != null && tvRestart != null) {
            if (!restartTime.isEmpty()) {
                layoutRestart.setVisibility(View.VISIBLE);
                tvRestart.setText(restartTime);
            } else {
                layoutRestart.setVisibility(View.GONE);
            }
        }
    }

    private void setupRefreshButton() {
        MaterialButton btnRefresh = findViewById(R.id.btnRefresh);
        ProgressBar    progress   = findViewById(R.id.progressBar);

        if (btnRefresh == null) return;
        btnRefresh.setOnClickListener(v -> {
            // Show spinner briefly then re-check
            if (progress != null) progress.setVisibility(View.VISIBLE);
            btnRefresh.setEnabled(false);

            v.postDelayed(() -> {
                if (progress != null) progress.setVisibility(View.GONE);
                btnRefresh.setEnabled(true);

                if (AppSettings.isMaintenanceMode(this)) {
                    // Still in maintenance — refresh the message in case it changed
                    bindAndFill();
                } else {
                    // Maintenance lifted → go to login
                    navigateToLogin();
                }
            }, 1500);
        });
    }

    private void navigateToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}

