package com.example.quickfix;

import android.content.Context;
import android.content.SharedPreferences;


/**
 * AppSettings — Centralised SharedPreferences manager.
 *
 * Used by:
 *  • AdminSettingsActivity  — to save all toggle states
 *  • QuickFixApp            — to apply dark-mode on startup
 *  • SplashActivity         — to check maintenance mode
 *  • AdminDashboardActivity — to read auto-approve flag
 */
public class AppSettings {

    private static final String PREFS_NAME = "admin_settings_prefs";

    // ── Keys ──────────────────────────────────────────────────────────────
    public static final String KEY_PUSH_NOTIF       = "admin_notif_push";
    public static final String KEY_EMAIL_NOTIF      = "admin_notif_email";
    public static final String KEY_ADMIN_ALERTS     = "admin_alerts";
    public static final String KEY_DARK_MODE        = "admin_dark_mode";
    public static final String KEY_AUTO_APPROVE     = "admin_auto_approve";
    public static final String KEY_EMAIL_DIGEST     = "admin_email_digest";
    public static final String KEY_MAINTENANCE_MODE = "maintenance_mode";
    public static final String KEY_MAINTENANCE_MSG  = "maintenance_message";
    public static final String KEY_MAINTENANCE_TIME = "maintenance_restart_time";

    // ── Defaults ──────────────────────────────────────────────────────────
    private static final boolean DEF_PUSH_NOTIF       = true;
    private static final boolean DEF_EMAIL_NOTIF      = true;
    private static final boolean DEF_ADMIN_ALERTS     = true;
    private static final boolean DEF_DARK_MODE        = false;
    private static final boolean DEF_AUTO_APPROVE     = false;
    private static final boolean DEF_EMAIL_DIGEST     = true;
    private static final boolean DEF_MAINTENANCE_MODE = false;
    private static final String  DEF_MAINTENANCE_MSG  =
            "We are currently performing scheduled maintenance.\nWe'll be back shortly.";
    private static final String  DEF_MAINTENANCE_TIME = "";

    // ─────────────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Boolean helpers ───────────────────────────────────────────────────

    public static boolean get(Context ctx, String key, boolean def) {
        return prefs(ctx).getBoolean(key, def);
    }

    public static void set(Context ctx, String key, boolean value) {
        prefs(ctx).edit().putBoolean(key, value).apply();
    }

    public static String getString(Context ctx, String key, String def) {
        return prefs(ctx).getString(key, def);
    }

    public static void setString(Context ctx, String key, String value) {
        prefs(ctx).edit().putString(key, value).apply();
    }

    // ── Convenience getters ───────────────────────────────────────────────

    public static boolean isPushEnabled(Context ctx) {
        return get(ctx, KEY_PUSH_NOTIF, DEF_PUSH_NOTIF);
    }

    public static boolean isEmailNotifEnabled(Context ctx) {
        return get(ctx, KEY_EMAIL_NOTIF, DEF_EMAIL_NOTIF);
    }

    public static boolean isAdminAlertsEnabled(Context ctx) {
        return get(ctx, KEY_ADMIN_ALERTS, DEF_ADMIN_ALERTS);
    }

    public static boolean isDarkMode(Context ctx) {
        return get(ctx, KEY_DARK_MODE, DEF_DARK_MODE);
    }

    public static boolean isAutoApprove(Context ctx) {
        return get(ctx, KEY_AUTO_APPROVE, DEF_AUTO_APPROVE);
    }

    public static boolean isEmailDigestEnabled(Context ctx) {
        return get(ctx, KEY_EMAIL_DIGEST, DEF_EMAIL_DIGEST);
    }

    public static boolean isMaintenanceMode(Context ctx) {
        return get(ctx, KEY_MAINTENANCE_MODE, DEF_MAINTENANCE_MODE);
    }

    public static String getMaintenanceMessage(Context ctx) {
        return getString(ctx, KEY_MAINTENANCE_MSG, DEF_MAINTENANCE_MSG);
    }

    public static String getMaintenanceRestartTime(Context ctx) {
        return getString(ctx, KEY_MAINTENANCE_TIME, DEF_MAINTENANCE_TIME);
    }

    // ── Apply dark / light mode system-wide ──────────────────────────────

    public static void applyDarkMode(Context ctx) {
        // Delegate to ThemeManager (shared across admin, provider, and user)
        ThemeManager.applyFromPrefs(ctx);
    }
}

