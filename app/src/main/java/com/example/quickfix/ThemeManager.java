package com.example.quickfix;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ThemeManager — single source of truth for dark/light mode across the app.
 *
 * Flow:
 *  1. On app start  → load saved preference → apply via AppCompatDelegate
 *  2. Admin toggles → save locally → apply immediately → sync to Supabase
 *  3. User opens app → load from SharedPreferences (fast, no network)
 *  4. Optionally: fetch from Supabase to sync admin-set preference to all users
 *
 * The preference is stored in SharedPreferences under key "dark_mode_enabled"
 * in prefs file "quickfix_theme_prefs" so ALL roles share the same key.
 */
public class ThemeManager {

    private static final String TAG        = "ThemeManager";
    private static final String PREFS_NAME = "quickfix_theme_prefs";
    private static final String KEY        = "dark_mode_enabled";
    private static final String LEGACY_PREFS_NAME = "quickfix_prefs";
    private static final String LEGACY_KEY = "dark_mode_enabled";

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Persist ───────────────────────────────────────────────────────────

    /** Save dark mode preference locally */
    public static void save(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        prefs(ctx).edit().putBoolean(KEY, darkMode).apply();
        ctx.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(LEGACY_KEY, darkMode)
                .apply();
        AppSettings.set(ctx, AppSettings.KEY_DARK_MODE, darkMode);
    }

    /** Read saved preference (default: light mode) */
    public static boolean isDarkMode(Context ctx) {
        if (ctx == null) return AppSettings.DEFAULT_DARK_MODE;

        SharedPreferences themePrefs = prefs(ctx);
        if (themePrefs.contains(KEY)) {
            return themePrefs.getBoolean(KEY, AppSettings.DEFAULT_DARK_MODE);
        }

        SharedPreferences legacyPrefs = ctx.getApplicationContext()
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
        if (legacyPrefs.contains(LEGACY_KEY)) {
            boolean legacyValue = legacyPrefs.getBoolean(LEGACY_KEY, AppSettings.DEFAULT_DARK_MODE);
            save(ctx, legacyValue);
            return legacyValue;
        }

        return AppSettings.isDarkMode(ctx);
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

    /** Apply a specific mode immediately (no restart needed with AppCompatDelegate). */
    public static void apply(boolean darkMode) {
        AppCompatDelegate.setDefaultNightMode(
                darkMode
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
    }

    // ── Toggle ────────────────────────────────────────────────────────────

    /**
     * Toggle dark mode: save + apply immediately + push to Supabase.
     * This is the single method called from any settings screen.
     */
    public static void toggle(Context ctx, boolean darkMode) {
        if (ctx == null) return;
        save(ctx, darkMode);
        apply(darkMode);
        pushToSupabase(darkMode);
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

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
