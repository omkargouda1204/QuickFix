package com.example.quickfix;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * AppSettings stores the locally cached admin_settings row so global behavior
 * can be enforced even when a fresh network fetch is not immediately available.
 */
public class AppSettings {

    private static final String PREFS_NAME = "admin_settings_prefs";

    public static final String KEY_ROW_ID = "admin_settings_row_id";
    public static final String KEY_PUSH_NOTIF = "push_notifications";
    public static final String KEY_EMAIL_NOTIF = "email_notifications";
    public static final String KEY_ADMIN_ALERTS = "admin_alerts";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_AUTO_APPROVE = "auto_approve_providers";
    public static final String KEY_EMAIL_DIGEST = "daily_email_digest";
    public static final String KEY_MAINTENANCE_MODE = "maintenance_mode";
    public static final String KEY_MAINTENANCE_MSG = "maintenance_message";
    public static final String KEY_MAINTENANCE_TIME = "maintenance_end_time";
    public static final String KEY_LAST_ROLE_ADMIN = "last_role_is_admin";

    public static final boolean DEFAULT_PUSH_NOTIFICATIONS = true;
    public static final boolean DEFAULT_EMAIL_NOTIFICATIONS = true;
    public static final boolean DEFAULT_ADMIN_ALERTS = true;
    public static final boolean DEFAULT_DARK_MODE = false;
    public static final boolean DEFAULT_AUTO_APPROVE_PROVIDERS = false;
    public static final boolean DEFAULT_DAILY_EMAIL_DIGEST = true;
    public static final boolean DEFAULT_MAINTENANCE_MODE = false;
    public static final String DEFAULT_MAINTENANCE_MESSAGE =
            "We are currently performing scheduled maintenance.\nWe'll be back shortly.";
    public static final String DEFAULT_MAINTENANCE_END_TIME = "";

    public static class Snapshot {
        public long rowId = -1L;
        public boolean pushNotifications = DEFAULT_PUSH_NOTIFICATIONS;
        public boolean emailNotifications = DEFAULT_EMAIL_NOTIFICATIONS;
        public boolean adminAlerts = DEFAULT_ADMIN_ALERTS;
        public boolean darkMode = DEFAULT_DARK_MODE;
        public boolean autoApproveProviders = DEFAULT_AUTO_APPROVE_PROVIDERS;
        public boolean dailyEmailDigest = DEFAULT_DAILY_EMAIL_DIGEST;
        public boolean maintenanceMode = DEFAULT_MAINTENANCE_MODE;
        public String maintenanceMessage = DEFAULT_MAINTENANCE_MESSAGE;
        public String maintenanceEndTime = DEFAULT_MAINTENANCE_END_TIME;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSnapshot(Context ctx, Snapshot snapshot) {
        if (ctx == null || snapshot == null) return;
        prefs(ctx).edit()
                .putLong(KEY_ROW_ID, snapshot.rowId)
                .putBoolean(KEY_PUSH_NOTIF, snapshot.pushNotifications)
                .putBoolean(KEY_EMAIL_NOTIF, snapshot.emailNotifications)
                .putBoolean(KEY_ADMIN_ALERTS, snapshot.adminAlerts)
                .putBoolean(KEY_DARK_MODE, snapshot.darkMode)
                .putBoolean(KEY_AUTO_APPROVE, snapshot.autoApproveProviders)
                .putBoolean(KEY_EMAIL_DIGEST, snapshot.dailyEmailDigest)
                .putBoolean(KEY_MAINTENANCE_MODE, snapshot.maintenanceMode)
                .putString(KEY_MAINTENANCE_MSG, safe(snapshot.maintenanceMessage, DEFAULT_MAINTENANCE_MESSAGE))
                .putString(KEY_MAINTENANCE_TIME, safe(snapshot.maintenanceEndTime, DEFAULT_MAINTENANCE_END_TIME))
                .apply();
    }

    public static Snapshot getSnapshot(Context ctx) {
        Snapshot s = new Snapshot();
        if (ctx == null) return s;
        SharedPreferences p = prefs(ctx);
        s.rowId = p.getLong(KEY_ROW_ID, -1L);
        s.pushNotifications = p.getBoolean(KEY_PUSH_NOTIF, DEFAULT_PUSH_NOTIFICATIONS);
        s.emailNotifications = p.getBoolean(KEY_EMAIL_NOTIF, DEFAULT_EMAIL_NOTIFICATIONS);
        s.adminAlerts = p.getBoolean(KEY_ADMIN_ALERTS, DEFAULT_ADMIN_ALERTS);
        s.darkMode = p.getBoolean(KEY_DARK_MODE, DEFAULT_DARK_MODE);
        s.autoApproveProviders = p.getBoolean(KEY_AUTO_APPROVE, DEFAULT_AUTO_APPROVE_PROVIDERS);
        s.dailyEmailDigest = p.getBoolean(KEY_EMAIL_DIGEST, DEFAULT_DAILY_EMAIL_DIGEST);
        s.maintenanceMode = p.getBoolean(KEY_MAINTENANCE_MODE, DEFAULT_MAINTENANCE_MODE);
        s.maintenanceMessage = safe(p.getString(KEY_MAINTENANCE_MSG, DEFAULT_MAINTENANCE_MESSAGE),
                DEFAULT_MAINTENANCE_MESSAGE);
        s.maintenanceEndTime = safe(p.getString(KEY_MAINTENANCE_TIME, DEFAULT_MAINTENANCE_END_TIME),
                DEFAULT_MAINTENANCE_END_TIME);
        return s;
    }

    public static void setLastKnownAdminRole(Context ctx, boolean isAdmin) {
        if (ctx == null) return;
        prefs(ctx).edit().putBoolean(KEY_LAST_ROLE_ADMIN, isAdmin).apply();
    }

    public static boolean isLastKnownAdminRole(Context ctx) {
        return ctx != null && prefs(ctx).getBoolean(KEY_LAST_ROLE_ADMIN, false);
    }

    public static boolean get(Context ctx, String key, boolean def) {
        return ctx != null && prefs(ctx).getBoolean(key, def);
    }

    public static void set(Context ctx, String key, boolean value) {
        if (ctx == null) return;
        prefs(ctx).edit().putBoolean(key, value).apply();
    }

    public static String getString(Context ctx, String key, String def) {
        return ctx != null ? prefs(ctx).getString(key, def) : def;
    }

    public static void setString(Context ctx, String key, String value) {
        if (ctx == null) return;
        prefs(ctx).edit().putString(key, value).apply();
    }

    public static long getAdminSettingsRowId(Context ctx) {
        return ctx != null ? prefs(ctx).getLong(KEY_ROW_ID, -1L) : -1L;
    }

    public static boolean isPushEnabled(Context ctx) {
        return get(ctx, KEY_PUSH_NOTIF, DEFAULT_PUSH_NOTIFICATIONS);
    }

    public static boolean isEmailNotifEnabled(Context ctx) {
        return get(ctx, KEY_EMAIL_NOTIF, DEFAULT_EMAIL_NOTIFICATIONS);
    }

    public static boolean isAdminAlertsEnabled(Context ctx) {
        return get(ctx, KEY_ADMIN_ALERTS, DEFAULT_ADMIN_ALERTS);
    }

    public static boolean isDarkMode(Context ctx) {
        return get(ctx, KEY_DARK_MODE, DEFAULT_DARK_MODE);
    }

    public static boolean isAutoApprove(Context ctx) {
        return get(ctx, KEY_AUTO_APPROVE, DEFAULT_AUTO_APPROVE_PROVIDERS);
    }

    public static boolean isEmailDigestEnabled(Context ctx) {
        return get(ctx, KEY_EMAIL_DIGEST, DEFAULT_DAILY_EMAIL_DIGEST);
    }

    public static boolean isMaintenanceMode(Context ctx) {
        return get(ctx, KEY_MAINTENANCE_MODE, DEFAULT_MAINTENANCE_MODE);
    }

    public static String getMaintenanceMessage(Context ctx) {
        return getString(ctx, KEY_MAINTENANCE_MSG, DEFAULT_MAINTENANCE_MESSAGE);
    }

    public static String getMaintenanceRestartTime(Context ctx) {
        return getString(ctx, KEY_MAINTENANCE_TIME, DEFAULT_MAINTENANCE_END_TIME);
    }

    public static void applyDarkMode(Context ctx) {
        ThemeManager.applyFromPrefs(ctx);
    }

    private static String safe(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
