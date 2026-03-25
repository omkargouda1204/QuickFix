package com.example.quickfix;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.quickfix.api.AdminSettingsApi;
import com.example.quickfix.api.RetrofitProvider;
import com.example.quickfix.api.model.AdminSettingsDto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import retrofit2.Call;

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
    private static volatile AdminSettingsApi adminSettingsApi;

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
                MAIN.post(() -> callback.onError("Failed to load admin settings. Please try again."));
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

                AppSettings.Snapshot updated = patchBooleanWithAliasFallback(context, current.rowId, column, value, body);

                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                final AppSettings.Snapshot finalUpdated = updated;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(finalUpdated);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "updateBooleanField failed: " + e.getMessage(), e);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Failed to update setting. Please try again.");
                    }
                });
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
                body.put("maintenance_end_time", nullableText(endTime));
                AppSettings.Snapshot updated = patchSettingsRow(context, current.rowId, body);
                AppSettings.saveSnapshot(context, updated);
                applyGlobalEffects(context, updated);
                final AppSettings.Snapshot finalUpdated = updated;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(finalUpdated);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "updateMaintenance failed: " + e.getMessage(), e);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Failed to update setting. Please try again.");
                    }
                });
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
            Log.d(TAG, "fetchOrCreateSync: no rows found, creating default singleton row");
            snapshot = createDefaultRow();
        } else {
            snapshot = parseSnapshot(rows.getJSONObject(0));
            Log.d(TAG, "fetchOrCreateSync: loaded rowId=" + snapshot.rowId);
        }
        AppSettings.saveSnapshot(context, snapshot);
        return snapshot;
    }

    /** Always fetch the canonical singleton row. */
    private static JSONArray fetchRows() throws Exception {
        try {
            Call<List<AdminSettingsDto>> call = getApi().fetchSingleRow(
                    ANON_KEY,
                    "Bearer " + ANON_KEY,
                    "application/json",
                    "*",
                    1
            );
            retrofit2.Response<List<AdminSettingsDto>> r = call.execute();
            if (r.code() == 200) {
                JSONArray arr = new JSONArray();
                List<AdminSettingsDto> body = r.body();
                if (body != null && !body.isEmpty()) {
                    arr.put(dtoToJson(body.get(0)));
                }
                Log.d(TAG, "fetchRows Retrofit HTTP 200 rows=" + (body == null ? 0 : body.size()));
                return arr;
            }
            String err = r.errorBody() != null ? r.errorBody().string() : "";
            throw new IllegalStateException("fetchRows HTTP " + r.code() + ": " + err);
        } catch (Exception e) {
            Log.e(TAG, "fetchRows Retrofit failed: " + e.getMessage());
            throw e;
        }
    }

    private static AppSettings.Snapshot createDefaultRow() throws Exception {
        // Prefer minimal row creation so DB defaults populate all columns.
        JSONObject minimalDefaults = new JSONObject();
        minimalDefaults.put("id", java.util.UUID.randomUUID().toString());
        minimalDefaults.put("maintenance_end_time", JSONObject.NULL);

        JSONObject primary = new JSONObject();
        primary.put("id", java.util.UUID.randomUUID().toString());
        primary.put("push_notifications",     AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        primary.put("email_notifications",    AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        primary.put("admin_alerts",           AppSettings.DEFAULT_ADMIN_ALERTS);
        primary.put("dark_mode",              AppSettings.DEFAULT_DARK_MODE);
        primary.put("auto_approve_providers", AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        primary.put("daily_email_digest",     AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        primary.put("maintenance_mode",       AppSettings.DEFAULT_MAINTENANCE_MODE);
        primary.put("maintenance_message",    AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        primary.put("maintenance_end_time",   JSONObject.NULL);

        JSONObject legacy = new JSONObject();
        legacy.put("id", java.util.UUID.randomUUID().toString());
        legacy.put("push_notifications",     AppSettings.DEFAULT_PUSH_NOTIFICATIONS);
        legacy.put("email_notifications",    AppSettings.DEFAULT_EMAIL_NOTIFICATIONS);
        legacy.put("admin_alerts",           AppSettings.DEFAULT_ADMIN_ALERTS);
        legacy.put("dark_mode",              AppSettings.DEFAULT_DARK_MODE);
        legacy.put("auto_approve",           AppSettings.DEFAULT_AUTO_APPROVE_PROVIDERS);
        legacy.put("daily_digest",           AppSettings.DEFAULT_DAILY_EMAIL_DIGEST);
        legacy.put("maintenance_mode",       AppSettings.DEFAULT_MAINTENANCE_MODE);
        legacy.put("maintenance_message",    AppSettings.DEFAULT_MAINTENANCE_MESSAGE);
        legacy.put("maintenance_end_time",   JSONObject.NULL);

        JSONObject[] payloads = new JSONObject[]{ minimalDefaults, primary, legacy };

        for (JSONObject body : payloads) {
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
                Log.d(TAG, "createDefaultRow HTTP " + r.code() + " payload=" + body + " resp=" + resp);
                if (r.code() == 200 || r.code() == 201) {
                    JSONArray arr = new JSONArray(resp);
                    if (arr.length() > 0) return parseSnapshot(arr.getJSONObject(0));
                }
                // If row already exists, just fetch and continue.
                if (r.code() == 409) {
                    JSONArray retry = fetchRows();
                    if (retry.length() > 0) return parseSnapshot(retry.getJSONObject(0));
                }
            }
        }

        JSONArray retry = fetchRows();
        if (retry.length() > 0) return parseSnapshot(retry.getJSONObject(0));
        throw new IllegalStateException("createDefaultRow HTTP 400");
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
        try {
            Map<String, Object> patchMap = jsonToMap(patchBody);
            Call<List<AdminSettingsDto>> call = getApi().updateById(
                    ANON_KEY,
                    "Bearer " + ANON_KEY,
                    "application/json",
                    "return=representation",
                    "eq." + rowId,
                    patchMap
            );
            retrofit2.Response<List<AdminSettingsDto>> r = call.execute();
            if (r.code() == 200) {
                List<AdminSettingsDto> body = r.body();
                if (body != null && !body.isEmpty()) {
                    return parseSnapshot(dtoToJson(body.get(0)));
                }
                return fetchSingleRowById(rowId);
            }
            if (r.code() == 204) {
                return fetchSingleRowById(rowId);
            }
            String err = r.errorBody() != null ? r.errorBody().string() : "";
            throw new IllegalStateException("patchSettingsRow HTTP " + r.code() + ": " + err);
        } catch (Exception e) {
            Log.e(TAG, "patchSettingsRowInternal Retrofit failed: " + e.getMessage());
            throw e;
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

        s.pushNotifications    = optBoolNullFalse(obj, "push_notifications");
        s.emailNotifications   = optBoolNullFalse(obj, "email_notifications");
        s.adminAlerts          = optBoolNullFalse(obj, "admin_alerts");
        s.darkMode             = optBoolNullFalse(obj, "dark_mode");
        s.autoApproveProviders = optBoolAnyNullFalse(obj,
                new String[]{"auto_approve_providers", "auto_approve"});
        s.dailyEmailDigest     = optBoolAnyNullFalse(obj,
                new String[]{"daily_email_digest", "daily_digest"});
        s.maintenanceMode      = optBoolNullFalse(obj, "maintenance_mode");
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

    private static boolean optBoolAny(JSONObject obj, String[] keys, boolean def) {
        if (keys == null) return def;
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optBoolean(key, def);
            }
        }
        return def;
    }

    private static boolean optBoolNullFalse(JSONObject obj, String key) {
        if (obj.has(key) && !obj.isNull(key)) return obj.optBoolean(key, false);
        return false;
    }

    private static boolean optBoolAnyNullFalse(JSONObject obj, String[] keys) {
        if (keys == null) return false;
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.optBoolean(key, false);
            }
        }
        return false;
    }

    private static String aliasColumn(String column) {
        if (column == null) return "";
        switch (column) {
            case "auto_approve_providers": return "auto_approve";
            case "daily_email_digest": return "daily_digest";
            case "auto_approve": return "auto_approve_providers";
            case "daily_digest": return "daily_email_digest";
            default: return column;
        }
    }

    private static AppSettings.Snapshot patchBooleanWithAliasFallback(Context context,
                                                                      String rowId,
                                                                      String column,
                                                                      boolean value,
                                                                      JSONObject originalBody) throws Exception {
        try {
            return patchSettingsRow(context, rowId, originalBody);
        } catch (Exception first) {
            String fallbackColumn = aliasColumn(column);
            if (!fallbackColumn.equals(column)) {
                Log.w(TAG, "Retrying updateBooleanField with alias column=" + fallbackColumn);
                JSONObject fallbackBody = new JSONObject();
                fallbackBody.put(fallbackColumn, value);
                return patchSettingsRow(context, rowId, fallbackBody);
            }
            throw first;
        }
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

    private static Object nullableText(String value) {
        if (value == null || value.trim().isEmpty()) return JSONObject.NULL;
        return value.trim();
    }

    private static AdminSettingsApi getApi() {
        if (adminSettingsApi == null) {
            synchronized (AdminSettingsRepository.class) {
                if (adminSettingsApi == null) {
                    adminSettingsApi = RetrofitProvider.get().create(AdminSettingsApi.class);
                }
            }
        }
        return adminSettingsApi;
    }

    private static JSONObject dtoToJson(AdminSettingsDto d) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", d.id);
        if (d.pushNotifications != null) o.put("push_notifications", d.pushNotifications);
        if (d.emailNotifications != null) o.put("email_notifications", d.emailNotifications);
        if (d.adminAlerts != null) o.put("admin_alerts", d.adminAlerts);
        if (d.darkMode != null) o.put("dark_mode", d.darkMode);
        if (d.autoApproveProviders != null) o.put("auto_approve_providers", d.autoApproveProviders);
        if (d.autoApproveLegacy != null) o.put("auto_approve", d.autoApproveLegacy);
        if (d.dailyEmailDigest != null) o.put("daily_email_digest", d.dailyEmailDigest);
        if (d.dailyDigestLegacy != null) o.put("daily_digest", d.dailyDigestLegacy);
        if (d.maintenanceMode != null) o.put("maintenance_mode", d.maintenanceMode);
        if (d.maintenanceMessage != null) o.put("maintenance_message", d.maintenanceMessage);
        if (d.maintenanceEndTime != null) o.put("maintenance_end_time", d.maintenanceEndTime);
        return o;
    }

    private static Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            Object v = json.opt(k);
            if (v == JSONObject.NULL) {
                map.put(k, null);
            } else {
                map.put(k, v);
            }
        }
        return map;
    }
}
