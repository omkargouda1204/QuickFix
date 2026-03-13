package com.example.quickfix;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AdminSettingsRepository — keeps admin_settings in Supabase as the source of truth.
 * admin_settings.id is a UUID (text), not an integer.
 *
 * Uses select=* on fetch to avoid HTTP 400 from missing column name mismatches.
 */
public final class AdminSettingsRepository {

    private static final String TAG          = "AdminSettingsRepo";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface SettingsCallback {
        void onSuccess(AppSettings.Snapshot snapshot);
        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess(AppSettings.Snapshot snapshot);
        void onError(String message);
    }

    private AdminSettingsRepository() {}

    // ── PUBLIC API ────────────────────────────────────────────────────────

    public static void fetchOrCreate(Context context, SettingsCallback callback) {
        if (callback == null) return;
        if (context == null) { MAIN.post(() -> callback.onError("Context unavailable")); return; }
        new Thread(() -> {
            try {
                AppSettings.Snapshot snapshot = fetchOrCreateSync(context);
                applyGlobalEffects(context, snapshot);
                MAIN.post(() -> callback.onSuccess(snapshot));
            } catch (Exception e) {
                Log.e(TAG, "fetchOrCreate failed: " + e.getMessage(), e);
                MAIN.post(() -> callback.onError("Failed to load admin settings: " + e.getMessage()));
            }
        }).start();
    }

    public static void syncInBackground(Context context) {
        if (context == null) return;
        new Thread(() -> {
            try {
                AppSettings.Snapshot snapshot = fetchOrCreateSync(context);
                applyGlobalEffects(context, snapshot);
            } catch (Exception e) {
                Log.w(TAG, "syncInBackground ignored: " + e.getMessage());
            }
        }).start();
    }

    public static void updateBooleanField(Context context, String column, boolean value,
                                          UpdateCallback callback) {
        if (callback == null) return;
        if (context == null) { MAIN.post(() -> callback.onError("Context unavailable")); return; }
        if (column == null || column.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("Invalid column")); return;
        }
        new Thread(() -> {
            try {
                AppSettings.Snapshot current = ensureRowReady(context);
                Log.d(TAG, "updateBooleanField: col=" + column + " val=" + value
                        + " rowId=" + current.rowId);
                JSONObject body = new JSONObject();
                body.put(column, value);
                AppSettings.Snapshot updated = patchSettingsRow(context, current.rowId, body);
                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                MAIN.post(() -> callback.onSuccess(updated));
            } catch (Exception e) {
                Log.e(TAG, "updateBooleanField failed: " + e.getMessage(), e);
                MAIN.post(() -> callback.onError("Could not update setting: " + e.getMessage()));
            }
        }).start();
    }

    public static void updateMaintenance(Context context, boolean enabled, String message,
                                         String endTime, UpdateCallback callback) {
        if (callback == null) return;
        if (context == null) { MAIN.post(() -> callback.onError("Context unavailable")); return; }
        new Thread(() -> {
            try {
                AppSettings.Snapshot current = ensureRowReady(context);
                JSONObject body = new JSONObject();
                body.put("maintenance_mode",     enabled);
                body.put("maintenance_message",  sanitize(message, AppSettings.DEFAULT_MAINTENANCE_MESSAGE));
                body.put("maintenance_end_time", sanitize(endTime, ""));
                AppSettings.Snapshot updated = patchSettingsRow(context, current.rowId, body);
                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                MAIN.post(() -> callback.onSuccess(updated));
            } catch (Exception e) {
                Log.e(TAG, "updateMaintenance failed: " + e.getMessage(), e);
                MAIN.post(() -> callback.onError("Could not update setting: " + e.getMessage()));
            }
        }).start();
    }

    public static boolean isAutoApproveProvidersEnabled(Context context) {
        return context != null && AppSettings.isAutoApprove(context);
    }

    // ── INTERNAL ──────────────────────────────────────────────────────────

    private static AppSettings.Snapshot ensureRowReady(Context context) throws Exception {
        AppSettings.Snapshot local = AppSettings.getSnapshot(context);
        if (local.hasValidRowId()) return local;
        Log.d(TAG, "ensureRowReady: no cached rowId, fetching from DB");
        return fetchOrCreateSync(context);
    }

    private static AppSettings.Snapshot fetchOrCreateSync(Context context) throws Exception {
        JSONArray rows = fetchRows();
        AppSettings.Snapshot snapshot;
        if (rows.length() == 0) {
            Log.d(TAG, "fetchOrCreateSync: no rows found, creating default");
            snapshot = createDefaultRow();
        } else {
            Log.d(TAG, "fetchOrCreateSync: found " + rows.length() + " row(s)");
            snapshot = parseSnapshot(rows.getJSONObject(0));
            if (rows.length() > 1) pruneExtraRows(rows);
        }
        AppSettings.saveSnapshot(context, snapshot);
        return snapshot;
    }

    /** Uses select=* so it works regardless of which optional columns exist. */
    private static JSONArray fetchRows() throws Exception {
        // Use select=* to avoid HTTP 400 from column name mismatches
        String url = SUPABASE_URL + "/rest/v1/admin_settings?select=*&limit=10";
        Request req = new Request.Builder()
                .url(url).get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            Log.d(TAG, "fetchRows HTTP " + r.code() + " body=" + body);
            if (r.code() == 200) return new JSONArray(body);
            Log.e(TAG, "fetchRows FAILED HTTP " + r.code() + ": " + body);
            throw new IllegalStateException("fetchRows HTTP " + r.code() + ": " + body);
        }
    }

    private static AppSettings.Snapshot createDefaultRow() throws Exception {
        // Insert only the columns that definitely exist
        JSONObject body = new JSONObject();
        body.put("push_notifications",     AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        body.put("email_notifications",    AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        body.put("admin_alerts",           AppSettings.DEFAULT_ADMIN_ALERTS);
        body.put("dark_mode",              AppSettings.DEFAULT_DARK_MODE);
        body.put("auto_approve_providers", AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        body.put("daily_email_digest",     AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        body.put("maintenance_mode",       AppSettings.DEFAULT_MAINTENANCE_MODE);
        body.put("maintenance_message",    AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        body.put("maintenance_end_time",   AppSettings.DEFAULT_MAINTENANCE_END_TIME);

        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/admin_settings")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Prefer",        "return=representation")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            String resp = r.body() != null ? r.body().string() : "[]";
            Log.d(TAG, "createDefaultRow HTTP " + r.code() + ": " + resp);
            if (r.code() == 200 || r.code() == 201) {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
            }
            // If insert also fails (e.g. RLS), try fetching again in case row exists
            Log.w(TAG, "createDefaultRow failed HTTP " + r.code() + " trying re-fetch");
            JSONArray retry = fetchRows();
            if (retry.length() > 0) return parseSnapshot(retry.getJSONObject(0));
            throw new IllegalStateException("createDefaultRow HTTP " + r.code() + ": " + resp);
        }
    }

    /** PATCH admin_settings row by UUID. Retries with a fresh rowId on failure. */
    private static AppSettings.Snapshot patchSettingsRow(Context context, String rowId,
                                                          JSONObject patchBody) throws Exception {
        if (rowId == null || rowId.trim().isEmpty()) {
            throw new IllegalArgumentException("admin_settings rowId is null — cannot PATCH");
        }
        try {
            return patchSettingsRowInternal(rowId, patchBody);
        } catch (Exception firstErr) {
            Log.w(TAG, "patchSettingsRow first attempt failed, retrying: " + firstErr.getMessage());
            // Fetch fresh rowId and retry once
            AppSettings.Snapshot fresh = fetchOrCreateSync(QuickFixApp.getInstance());
            if (fresh != null && fresh.hasValidRowId() && !fresh.rowId.equals(rowId)) {
                Log.d(TAG, "patchSettingsRow retrying with fresh rowId=" + fresh.rowId);
                return patchSettingsRowInternal(fresh.rowId, patchBody);
            }
            throw firstErr;
        }
    }

    private static AppSettings.Snapshot patchSettingsRowInternal(String rowId,
                                                                   JSONObject patchBody) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/admin_settings?id=eq." + rowId;
        Log.d(TAG, "PATCH " + url + " body=" + patchBody);
        Request req = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(patchBody.toString(), JSON_TYPE))
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Prefer",        "return=representation")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            String resp = r.body() != null ? r.body().string() : "[]";
            Log.d(TAG, "patchSettingsRowInternal HTTP " + r.code() + " rowId=" + rowId + " resp=" + resp);
            if (r.code() == 200) {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
                return fetchSingleRowById(rowId);
            }
            if (r.code() == 204) return fetchSingleRowById(rowId);
            Log.e(TAG, "PATCH failed HTTP " + r.code() + ": " + resp);
            throw new IllegalStateException("patchSettingsRow HTTP " + r.code() + ": " + resp);
        }
    }

    private static AppSettings.Snapshot fetchSingleRowById(String rowId) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/admin_settings?select=*&id=eq." + rowId + "&limit=1";
        Request req = new Request.Builder()
                .url(url).get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();
        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            Log.d(TAG, "fetchSingleRowById HTTP " + r.code() + " rowId=" + rowId);
            if (r.code() == 200) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
            }
            throw new IllegalStateException("fetchSingleRowById HTTP " + r.code() + ": " + body);
        }
    }

    private static void pruneExtraRows(JSONArray rows) {
        try {
            StringBuilder ids = new StringBuilder();
            for (int i = 1; i < rows.length(); i++) {
                String id = rows.getJSONObject(i).optString("id", "");
                if (!id.isEmpty()) {
                    if (ids.length() > 0) ids.append(",");
                    ids.append(id);
                }
            }
            if (ids.length() == 0) return;
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/admin_settings?id=in.(" + ids + ")")
                    .delete()
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Prefer",        "return=minimal")
                    .build();
            try (Response r = HTTP.newCall(req).execute()) {
                Log.d(TAG, "pruneExtraRows HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneExtraRows skipped: " + e.getMessage());
        }
    }

    /**
     * Parse admin_settings JSON row — handles both potential column name variants
     * so we don't break if the DB schema differs slightly.
     */
    private static AppSettings.Snapshot parseSnapshot(JSONObject obj) {
        AppSettings.Snapshot s = new AppSettings.Snapshot();
        String rawId = obj.optString("id", "");
        s.rowId = rawId.isEmpty() ? null : rawId;

        s.pushNotifications    = optBool(obj, "push_notifications",     AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        s.emailNotifications   = optBool(obj, "email_notifications",    AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        s.adminAlerts          = optBool(obj, "admin_alerts",           AppSettings.DEFAULT_ADMIN_ALERTS);
        s.darkMode             = optBool(obj, "dark_mode",              AppSettings.DEFAULT_DARK_MODE);
        s.autoApproveProviders = optBool(obj, "auto_approve_providers", AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        s.dailyEmailDigest     = optBool(obj, "daily_email_digest",     AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        s.maintenanceMode      = optBool(obj, "maintenance_mode",       AppSettings.DEFAULT_MAINTENANCE_MODE);
        s.maintenanceMessage   = sanitize(obj.optString("maintenance_message", ""),
                AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        s.maintenanceEndTime   = sanitize(obj.optString("maintenance_end_time", ""), "");

        Log.d(TAG, "parseSnapshot rowId=" + s.rowId + " darkMode=" + s.darkMode
                + " maintenance=" + s.maintenanceMode);
        return s;
    }

    private static boolean optBool(JSONObject obj, String key, boolean def) {
        if (obj.has(key) && !obj.isNull(key)) return obj.optBoolean(key, def);
        return def;
    }

    private static void applyGlobalEffects(Context context, AppSettings.Snapshot snapshot) {
        if (context == null || snapshot == null) return;
        ThemeManager.save(context, snapshot.darkMode);
        ThemeManager.apply(snapshot.darkMode);
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) return fallback;
        String t = value.trim();
        return t.isEmpty() ? fallback : t;
    }
}
