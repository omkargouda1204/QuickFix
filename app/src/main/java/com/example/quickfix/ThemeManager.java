package com.example.quickfix;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ThemeManager — role-aware source of truth for dark/light mode across the app.
 *
 * Flow:
 *  1. On app start  → resolve role-specific preference → apply via AppCompatDelegate
 *  2. Admin toggles → save locally + in admin_settings → apply immediately → sync to Supabase
 *  3. User toggles  → save in user-only prefs → apply immediately
 *  4. Legacy shared preferences are migrated on first read per role
 */
public class ThemeManager {

    private static final String TAG        = "ThemeManager";
    private static final String USER_PREFS_NAME  = "quickfix_theme_user_prefs";
    private static final String ADMIN_PREFS_NAME = "quickfix_theme_admin_prefs";
    private static final String KEY              = "dark_mode_enabled";
    private static final String LEGACY_USER_PREFS_NAME  = "quickfix_prefs";
    private static final String LEGACY_SHARED_PREFS_NAME = "quickfix_theme_prefs";
    private static final String LEGACY_ADMIN_PREFS_NAME = "quickfix_theme_prefs";
    private static final String LEGACY_KEY = "dark_mode_enabled";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();


    // ── Persist ───────────────────────────────────────────────────────────

    /** Save the user-facing dark mode preference locally. */
    public static void saveUser(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        userPrefs(ctx).edit().putBoolean(KEY, darkMode).apply();
        ctx.getApplicationContext()
                .getSharedPreferences(LEGACY_USER_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LEGACY_KEY, darkMode)
                .apply();
    }

    /** Save the admin-facing dark mode preference locally and in the admin cache. */
    public static void saveAdmin(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        adminPrefs(ctx).edit().putBoolean(KEY, darkMode).apply();
        ctx.getApplicationContext()
                .getSharedPreferences(LEGACY_ADMIN_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LEGACY_KEY, darkMode)
                .apply();
        AppSettings.set(ctx, AppSettings.KEY_DARK_MODE, darkMode);
    }

    /** Read the user dark mode preference (default: light mode). */
    public static boolean isUserDarkMode(Context ctx) {
        if (ctx == null) return AppSettings.DEFAULT_DARK_MODE;
        return readRoleMode(ctx, userPrefs(ctx), LEGACY_USER_PREFS_NAME, AppSettings.DEFAULT_DARK_MODE,
                false);
    }

    /** Read the admin dark mode preference (default: light mode). */
    public static boolean isAdminDarkMode(Context ctx) {
        if (ctx == null) return AppSettings.DEFAULT_DARK_MODE;
        return readRoleMode(ctx, adminPrefs(ctx), LEGACY_ADMIN_PREFS_NAME, AppSettings.DEFAULT_DARK_MODE,
                true);
    }

    /** Backward-compatible role-aware lookup used by legacy call sites. */
    public static boolean isDarkMode(Context ctx) {
        return isDarkMode(ctx, AppSettings.isLastKnownAdminRole(ctx));
    }

    /** Role-aware lookup for explicit admin/user screens. */
    public static boolean isDarkMode(Context ctx, boolean isAdmin) {
        return isAdmin ? isAdminDarkMode(ctx) : isUserDarkMode(ctx);
    }

    // ── Apply ─────────────────────────────────────────────────────────────

    /**
     * Apply the saved theme. Must be called before setContentView().
     * In Application.onCreate() this sets the mode for the entire session.
     */
    public static void applyFromPrefs(Context ctx) {
        if (ctx == null) return;
        apply(isDarkMode(ctx));
    }

    /** Apply the theme for an explicit role without relying on cached role state. */
    public static void applyFromPrefs(Context ctx, boolean isAdmin) {
        if (ctx == null) return;
        apply(isDarkMode(ctx, isAdmin));
    }

    /** Apply a specific mode immediately (no restart needed with AppCompatDelegate). */
    public static void apply(boolean darkMode) {
        AppCompatDelegate.setDefaultNightMode(
                darkMode
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void applyUserMode(Context ctx) {
        apply(isUserDarkMode(ctx));
    }

    public static void applyAdminMode(Context ctx) {
        apply(isAdminDarkMode(ctx));
    }

    // ── Toggle ────────────────────────────────────────────────────────────

    /**
     * Toggle dark mode: save + apply immediately + push to Supabase.
     * This is the single method called from any settings screen.
     */
    public static void toggleUser(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        saveUser(ctx, darkMode);
        apply(darkMode);
    }

    public static void toggleAdmin(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        saveAdmin(ctx, darkMode);
        apply(darkMode);
        pushToSupabase(darkMode);
    }

    /** Backward-compatible toggle routed by the cached role. */
    public static void toggle(Context ctx, boolean darkMode) {
        if (AppSettings.isLastKnownAdminRole(ctx)) toggleAdmin(ctx, darkMode);
        else toggleUser(ctx, darkMode);
    }

    // ── Supabase sync ─────────────────────────────────────────────────────

    /**
     * Push dark mode setting to the app_settings table in Supabase.
     * Table: app_settings(key TEXT PK, value TEXT, updated_at TIMESTAMPTZ)
     * Row: key = 'dark_mode', value = 'true' or 'false'
     *
     * Creates or updates the row using UPSERT.
     * If table doesn't exist yet, the error is silently logged.
     */
    public static void pushToSupabase(boolean darkMode) {
        new Thread(() -> {
            try {
                String url   = BuildConfig.SUPABASE_URL + "/rest/v1/app_settings";
                String anonKey = BuildConfig.SUPABASE_ANON_KEY;

                JSONObject body = new JSONObject();
                body.put("key",   "dark_mode");
                body.put("value", String.valueOf(darkMode));

                Request req = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey",        anonKey)
                        .addHeader("Authorization", "Bearer " + anonKey)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "resolution=merge-duplicates,return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    int code = r.code();
                    if (code == 200 || code == 201 || code == 204) {
                        Log.d(TAG, "Dark mode synced to Supabase: " + darkMode);
                    } else {
                        String rb = r.body() != null ? r.body().string() : "";
                        Log.w(TAG, "pushToSupabase HTTP " + code + " | " + rb);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "pushToSupabase error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Sync dark mode from admin_settings via AdminSettingsRepository.
     * This keeps theme behavior aligned with the Admin Settings source of truth.
     */
    public static void fetchAndSync(Context ctx) {
        if (ctx == null) return;
        AdminSettingsRepository.syncInBackground(ctx);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static boolean readRoleMode(Context ctx,
                                        SharedPreferences rolePrefs,
                                        String legacyPrefsName,
                                        boolean def,
                                        boolean alsoUseAdminSettingsFallback) {
        if (rolePrefs.contains(KEY)) {
            return rolePrefs.getBoolean(KEY, def);
        }

        SharedPreferences legacyPrefs = ctx.getApplicationContext()
                .getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE);
        if (legacyPrefs.contains(LEGACY_KEY)) {
            boolean legacyValue = legacyPrefs.getBoolean(LEGACY_KEY, def);
            rolePrefs.edit().putBoolean(KEY, legacyValue).apply();
            return legacyValue;
        }

        if (!LEGACY_SHARED_PREFS_NAME.equals(legacyPrefsName)) {
            SharedPreferences sharedPrefs = ctx.getApplicationContext()
                    .getSharedPreferences(LEGACY_SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            if (sharedPrefs.contains(LEGACY_KEY)) {
                boolean legacyValue = sharedPrefs.getBoolean(LEGACY_KEY, def);
                rolePrefs.edit().putBoolean(KEY, legacyValue).apply();
                return legacyValue;
            }
        }

        if (alsoUseAdminSettingsFallback) {
            boolean cached = AppSettings.isDarkMode(ctx);
            rolePrefs.edit().putBoolean(KEY, cached).apply();
            return cached;
        }

        return def;
    }

    private static SharedPreferences userPrefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(USER_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences adminPrefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(ADMIN_PREFS_NAME, Context.MODE_PRIVATE);
    }
}
