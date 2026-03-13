package com.example.quickfix;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AdminSettingsRepository keeps admin_settings in Supabase as the source of truth.
 *
 * It also mirrors values into AppSettings (SharedPreferences) so non-admin screens can
 * apply behavior instantly even when offline.
 */
public final class AdminSettingsRepository {

    private static final String TAG = "AdminSettingsRepo";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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

    private AdminSettingsRepository() {
    }

    public static void fetchOrCreate(Context context, SettingsCallback callback) {
        if (callback == null) return;
        if (context == null) {
            MAIN.post(() -> callback.onError("Context unavailable"));
            return;
        }
        new Thread(() -> {
            try {
                AppSettings.Snapshot snapshot = fetchOrCreateSync(context);
                applyGlobalEffects(context, snapshot);
                MAIN.post(() -> callback.onSuccess(snapshot));
            } catch (Exception e) {
                Log.e(TAG, "fetchOrCreate failed", e);
                MAIN.post(() -> callback.onError("Failed to load admin settings"));
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

    public static void updateBooleanField(Context context,
                                          String column,
                                          boolean value,
                                          UpdateCallback callback) {
        if (callback == null) return;
        if (context == null) {
            MAIN.post(() -> callback.onError("Context unavailable"));
            return;
        }
        if (column == null || column.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("Invalid column"));
            return;
        }

        new Thread(() -> {
            try {
                AppSettings.Snapshot current = ensureRowReady(context);
                JSONObject body = new JSONObject();
                body.put(column, value);

                AppSettings.Snapshot updated = patchSettingsRow(current.rowId, body);
                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                MAIN.post(() -> callback.onSuccess(updated));
            } catch (Exception e) {
                Log.e(TAG, "updateBooleanField failed", e);
                MAIN.post(() -> callback.onError("Could not update setting"));
            }
        }).start();
    }

    public static void updateMaintenance(Context context,
                                         boolean enabled,
                                         String message,
                                         String endTime,
                                         UpdateCallback callback) {
        if (callback == null) return;
        if (context == null) {
            MAIN.post(() -> callback.onError("Context unavailable"));
            return;
        }

        new Thread(() -> {
            try {
                AppSettings.Snapshot current = ensureRowReady(context);
                JSONObject body = new JSONObject();
                body.put("maintenance_mode", enabled);
                body.put("maintenance_message", sanitize(message,
                        AppSettings.DEFAULT_MAINTENANCE_MESSAGE));
                body.put("maintenance_end_time", sanitize(endTime, ""));

                AppSettings.Snapshot updated = patchSettingsRow(current.rowId, body);
                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                MAIN.post(() -> callback.onSuccess(updated));
            } catch (Exception e) {
                Log.e(TAG, "updateMaintenance failed", e);
                MAIN.post(() -> callback.onError("Could not update setting"));
            }
        }).start();
    }

    private static AppSettings.Snapshot ensureRowReady(Context context) throws Exception {
        AppSettings.Snapshot local = AppSettings.getSnapshot(context);
        if (local.rowId > 0) return local;
        return fetchOrCreateSync(context);
    }

    private static AppSettings.Snapshot fetchOrCreateSync(Context context) throws Exception {
        JSONArray rows = fetchRows();
        AppSettings.Snapshot snapshot;

        if (rows.length() == 0) {
            snapshot = createDefaultRow();
        } else {
            JSONObject first = rows.getJSONObject(0);
            snapshot = parseSnapshot(first);
            if (rows.length() > 1) {
                pruneExtraRows(rows);
            }
        }

        AppSettings.saveSnapshot(context, snapshot);
        return snapshot;
    }

    private static JSONArray fetchRows() throws Exception {
        String url = SUPABASE_URL + "/rest/v1/admin_settings"
                + "?select=id,push_notifications,email_notifications,admin_alerts,dark_mode,"
                + "auto_approve_providers,daily_email_digest,maintenance_mode,maintenance_message,maintenance_end_time"
                + "&order=id.asc&limit=10";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            if (r.code() == 200) return new JSONArray(body);
            throw new IllegalStateException("fetchRows HTTP " + r.code() + ": " + body);
        }
    }

    private static AppSettings.Snapshot createDefaultRow() throws Exception {
        JSONObject body = new JSONObject();
        body.put("push_notifications", AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        body.put("email_notifications", AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        body.put("admin_alerts", AppSettings.DEFAULT_ADMIN_ALERTS);
        body.put("dark_mode", AppSettings.DEFAULT_DARK_MODE);
        body.put("auto_approve_providers", AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        body.put("daily_email_digest", AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        body.put("maintenance_mode", AppSettings.DEFAULT_MAINTENANCE_MODE);
        body.put("maintenance_message", AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        body.put("maintenance_end_time", AppSettings.DEFAULT_MAINTENANCE_END_TIME);

        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/admin_settings")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            String resp = r.body() != null ? r.body().string() : "[]";
            if (r.code() == 200 || r.code() == 201) {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
            }
            throw new IllegalStateException("createDefaultRow HTTP " + r.code() + ": " + resp);
        }
    }

    private static void pruneExtraRows(JSONArray rows) {
        try {
            List<String> ids = new ArrayList<>();
            for (int i = 1; i < rows.length(); i++) {
                long id = rows.getJSONObject(i).optLong("id", -1L);
                if (id > 0) ids.add(String.valueOf(id));
            }
            if (ids.isEmpty()) return;

            String inFilter = String.join(",", ids);
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/admin_settings?id=in.(" + inFilter + ")")
                    .delete()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Prefer", "return=minimal")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                Log.d(TAG, "pruneExtraRows HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "pruneExtraRows skipped: " + e.getMessage());
        }
    }

    private static AppSettings.Snapshot patchSettingsRow(long rowId, JSONObject patchBody) throws Exception {
        if (rowId <= 0) throw new IllegalArgumentException("Invalid admin_settings row id");

        try {
            return patchSettingsRowInternal(rowId, patchBody);
        } catch (Exception firstError) {
            // Retry once with a fresh row id in case the cached row was deleted/changed.
            Log.w(TAG, "patchSettingsRow retrying with fresh row: " + firstError.getMessage());
            AppSettings.Snapshot fresh = fetchOrCreateSync(QuickFixApp.getInstance());
            if (fresh != null && fresh.rowId > 0 && fresh.rowId != rowId) {
                return patchSettingsRowInternal(fresh.rowId, patchBody);
            }
            throw firstError;
        }
    }

    private static AppSettings.Snapshot patchSettingsRowInternal(long rowId, JSONObject patchBody) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/admin_settings?id=eq." + rowId;
        Request req = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(patchBody.toString(), JSON_TYPE))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            String resp = r.body() != null ? r.body().string() : "[]";
            if (r.code() == 200) {
                JSONArray arr = new JSONArray(resp);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
            }
            if (r.code() == 204) {
                return fetchSingleRowById(rowId);
            }
            throw new IllegalStateException("patchSettingsRow HTTP " + r.code() + ": " + resp);
        }
    }

    private static AppSettings.Snapshot fetchSingleRowById(long rowId) throws Exception {
        String url = SUPABASE_URL + "/rest/v1/admin_settings?id=eq." + rowId
                + "&select=id,push_notifications,email_notifications,admin_alerts,dark_mode,"
                + "auto_approve_providers,daily_email_digest,maintenance_mode,maintenance_message,maintenance_end_time"
                + "&limit=1";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            if (r.code() == 200) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
            }
            throw new IllegalStateException("fetchSingleRowById HTTP " + r.code() + ": " + body);
        }
    }

    public static boolean isAutoApproveProvidersEnabled(Context context) {
        if (context == null) return false;
        return AppSettings.isAutoApprove(context);
    }

    private static AppSettings.Snapshot parseSnapshot(JSONObject obj) {
        AppSettings.Snapshot s = new AppSettings.Snapshot();
        s.rowId = obj.optLong("id", -1L);
        s.pushNotifications = obj.optBoolean("push_notifications", AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        s.emailNotifications = obj.optBoolean("email_notifications", AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        s.adminAlerts = obj.optBoolean("admin_alerts", AppSettings.DEFAULT_ADMIN_ALERTS);
        s.darkMode = obj.optBoolean("dark_mode", AppSettings.DEFAULT_DARK_MODE);
        s.autoApproveProviders = obj.optBoolean("auto_approve_providers", AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        s.dailyEmailDigest = obj.optBoolean("daily_email_digest", AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        s.maintenanceMode = obj.optBoolean("maintenance_mode", AppSettings.DEFAULT_MAINTENANCE_MODE);
        s.maintenanceMessage = sanitize(obj.optString("maintenance_message", ""),
                AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        s.maintenanceEndTime = sanitize(obj.optString("maintenance_end_time", ""), "");
        return s;
    }

    private static void applyGlobalEffects(Context context, AppSettings.Snapshot snapshot) {
        if (context == null || snapshot == null) return;
        ThemeManager.save(context, snapshot.darkMode);
        ThemeManager.apply(snapshot.darkMode);
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
