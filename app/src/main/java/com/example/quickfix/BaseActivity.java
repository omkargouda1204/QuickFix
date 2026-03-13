package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * BaseActivity — all QuickFix Activities extend this instead of AppCompatActivity directly.
 *
 * <p>Sole responsibility: wrap the base context with the user's saved locale so every
 * Activity automatically renders in the correct language without any per-Activity code.
 *
 * <p>The locale is applied in {@link #attachBaseContext(Context)} which is called by the
 * framework <em>before</em> {@code onCreate()}, ensuring {@code getResources()} and
 * {@code getString()} return strings in the right language from the very first call.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private boolean maintenanceCheckInFlight;
    private boolean appliedDarkMode;

    @Override
    protected void attachBaseContext(Context base) {
        // Wrap the context with the persisted locale before the Activity inflates
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyFromPrefs(this);
        appliedDarkMode = ThemeManager.isDarkMode(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean currentDarkMode = ThemeManager.isDarkMode(this);
        if (currentDarkMode != appliedDarkMode) {
            appliedDarkMode = currentDarkMode;
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
            return;
        }
        enforceMaintenanceAccessIfNeeded();
    }

    private void enforceMaintenanceAccessIfNeeded() {
        if (!AppSettings.isMaintenanceMode(this)) return;
        if (shouldBypassMaintenanceCheck()) return;
        if (maintenanceCheckInFlight) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            redirectToMaintenance();
            return;
        }

        if (AppSettings.isLastKnownAdminRole(this)) return;

        maintenanceCheckInFlight = true;
        String email = user.getEmail() != null ? user.getEmail() : "";
        SupabaseClient.getUserRoleWithEmail(user.getUid(), email,
                new SupabaseClient.RoleInfoCallback() {
                    @Override
                    public void onSuccess(SupabaseClient.RoleInfo info) {
                        maintenanceCheckInFlight = false;
                        if (isFinishing() || isDestroyed()) return;

                        boolean isAdmin = info != null && info.isAdmin;
                        AppSettings.setLastKnownAdminRole(BaseActivity.this, isAdmin);
                        if (!isAdmin) redirectToMaintenance();
                    }

                    @Override
                    public void onError(String message) {
                        maintenanceCheckInFlight = false;
                        if (isFinishing() || isDestroyed()) return;
                        // Fail-safe: if we cannot verify admin role during maintenance, block access.
                        redirectToMaintenance();
                    }
                });
    }

    private boolean shouldBypassMaintenanceCheck() {
        if (this instanceof SplashActivity
                || this instanceof MaintenanceActivity
                || this instanceof LoginActivity
                || this instanceof AdminLoginActivity) {
            return true;
        }
        return false;
    }

    private void redirectToMaintenance() {
        if (this instanceof MaintenanceActivity) return;
        Intent i = new Intent(this, MaintenanceActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
