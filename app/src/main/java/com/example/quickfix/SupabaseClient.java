package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseClient — saves / upserts user profiles into Supabase public.users
 * via the PostgREST REST API using OkHttp.
 *
 * KEY DESIGN DECISIONS:
 *  - Timeouts are SHORT (8 s connect, 10 s read) so the app never hangs.
 *  - upsertUser uses "Prefer: resolution=merge-duplicates" — a true atomic
 *    UPSERT that works regardless of RLS returning 409 or not.
 *  - getUserRole queries by firebase_uid first; if the row is not found it
 *    falls back to query by email so admins are never accidentally demoted.
 *  - Every network call fires callback on the MAIN thread.
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";

    // Keys are injected at build time from supabase.properties — never hardcoded
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    // SHORT timeouts — user should never wait more than 10 s
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8,  TimeUnit.SECONDS)
            .readTimeout(10,    TimeUnit.SECONDS)
            .writeTimeout(10,   TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // Static initializer to validate credentials
    static {
        validateCredentials();
    }

    /**
     * Validates that Supabase credentials are properly configured.
     * Logs detailed errors if missing, which helps diagnose the issue.
     */
    private static void validateCredentials() {
        boolean urlMissing = SUPABASE_URL == null || SUPABASE_URL.isEmpty() || SUPABASE_URL.contains("YOUR_");
        boolean keyMissing = ANON_KEY == null || ANON_KEY.isEmpty() || ANON_KEY.contains("YOUR_");

        if (urlMissing || keyMissing) {
            String msg = "❌ SUPABASE CREDENTIALS MISSING!\n" +
                    "📁 File needed: supabase.properties (in project root)\n" +
                    "🔧 URL configured: " + (urlMissing ? "❌ NO" : "✅ YES") + "\n" +
                    "🔑 API Key configured: " + (keyMissing ? "❌ NO" : "✅ YES") + "\n" +
                    "📝 Instructions:\n" +
                    "1. Get credentials from: https://supabase.com/dashboard/project/_/settings/api\n" +
                    "2. Create supabase.properties in project root with:\n" +
                    "   SUPABASE_URL=https://YOUR_PROJECT.supabase.co\n" +
                    "   SUPABASE_ANON_KEY=YOUR_ANON_KEY\n" +
                    "3. Rebuild the project";
            Log.e(TAG, msg);
        } else {
            Log.i(TAG, "✅ Supabase credentials configured: " + SUPABASE_URL);
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────
    public interface Callback2 {
        void onSuccess();
        void onError(String message);
    }

    public interface StringCallback {
        void onSuccess(String value);
        void onError(String message);
    }

    public interface QueryCallback {
        void onSuccess(org.json.JSONArray result);
        void onError(String message);
    }

    // RoleInfo: holds user role booleans
    public static class RoleInfo {
        public boolean isUser;
        public boolean isServiceProvider;
        public boolean isAdmin;
        public RoleInfo(boolean isUser, boolean isServiceProvider, boolean isAdmin) {
            this.isUser = isUser;
            this.isServiceProvider = isServiceProvider;
            this.isAdmin = isAdmin;
        }
    }

    public interface RoleInfoCallback {
        void onSuccess(RoleInfo info);
        void onError(String message);
    }

    // Account lifecycle
    private static final long COOL_DOWN_MS_24H = 24L * 60L * 60L * 1000L;

    public interface AccountStatusCallback {
        void onSuccess(UserAccountStatus status);
        void onError(String message);
    }

    public static class UserAccountStatus {
        public boolean exists;
        public String firebaseUid = "";
        public String name = "";
        public String email = "";
        public String profileImagePath = "";
        public String createdAtRaw = "";
        public String deletedAtRaw = "";
        public long createdAtMs;
        public long deletedAtMs;

        public boolean isDeleted() {
            return deletedAtMs > 0L;
        }

        public boolean isDeletionCooldownActive() {
            if (deletedAtMs <= 0L) return false;
            return System.currentTimeMillis() - deletedAtMs < COOL_DOWN_MS_24H;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UPSERT USER  — true atomic upsert, never overwrites role
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Inserts or updates a user row.
     *
     * Uses PostgREST UPSERT (Prefer: resolution=merge-duplicates) on the
     * firebase_uid unique key.  The role column is intentionally excluded
     * from the ON CONFLICT update so existing admin / provider roles are
     * never overwritten by a re-login.
     *
     * If the HTTP call fails for any reason (network, RLS, etc.) the callback
     * still fires onSuccess so the Firebase auth flow is never blocked.
     */
    public static void upsertUser(
            String firebaseUid,
            String name,
            String email,
            String phone,
            boolean isUser,
            boolean isServiceProvider,
            boolean isAdmin,
            Callback2 callback) {

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("firebase_uid",         firebaseUid);
                body.put("name",                 name  != null ? name.trim()  : "");
                body.put("email",                email != null ? email.trim() : "");
                body.put("phone",                phone != null ? phone.trim() : "");
                body.put("is_user",              isUser);
                body.put("is_service_provider",  isServiceProvider);
                body.put("is_admin",             isAdmin);

                // ── INSERT — ignore if already exists (preserve existing role) ──
                Request insertReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users")
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "resolution=ignore-duplicates,return=minimal")
                        .build();

                try (Response r = HTTP.newCall(insertReq).execute()) {
                    int code = r.code();
                    String rawBody = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "upsertUser INSERT HTTP " + code + " | body=" + rawBody);

                    if (code == 200 || code == 201 || code == 204) {
                        Log.d(TAG, "upsertUser INSERT OK for uid=" + firebaseUid);
                        patchUserProfileSync(firebaseUid, name, phone);
                        MAIN.post(callback::onSuccess);
                    } else {
                        // Log full details so the developer can diagnose RLS / schema issues
                        Log.e(TAG, "upsertUser INSERT failed HTTP " + code
                                + " | url=" + SUPABASE_URL
                                + " | body=" + rawBody);
                        // Non-fatal: patch in case row already exists
                        patchUserProfileSync(firebaseUid, name, phone);
                        MAIN.post(callback::onSuccess);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "upsertUser exception: " + e.getMessage(), e);
                MAIN.post(callback::onSuccess); // never block auth flow
            }
        }).start();
    }

    /** Synchronous PATCH of name/phone only — called from background thread */
    private static void patchUserProfileSync(String firebaseUid, String name, String phone) {
        try {
            JSONObject patch = new JSONObject();
            if (name  != null && !name.trim().isEmpty())  patch.put("name",  name.trim());
            if (phone != null && !phone.trim().isEmpty()) patch.put("phone", phone.trim());
            if (patch.length() == 0) return;

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + firebaseUid)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();

            try (Response r = HTTP.newCall(request).execute()) {
                Log.d(TAG, "patchUserProfile HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "patchUserProfile exception (non-fatal): " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GET USER ROLE  — email-first strategy, hard error on missing record
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Fetches is_admin / is_service_provider / is_user from Supabase.
     *
     * Strategy (runs in a single background thread):
     *   1. Query by EMAIL  — most reliable; admins set manually in DB are
     *      always found here even before their first login (firebase_uid
     *      may not be stored yet).
     *   2. If no row by email → query by firebase_uid  (fallback for rows
     *      whose email field is blank or was stored differently).
     *   3. If still no row → callback.onError("USER_NOT_FOUND") so the
     *      caller can show an error instead of silently opening home.
     *   4. If a network/parse exception occurs → callback.onError(message).
     *
     * IMPORTANT: onError is NEVER silently swallowed. The caller in
     * LoginActivity decides what to show / do when the record is missing.
     */
    public static void getUserRole(String firebaseUid, RoleInfoCallback callback) {
        getUserRoleWithEmail(firebaseUid, null, callback);
    }

    public static void getUserRoleWithEmail(String firebaseUid,
                                             String email,
                                             RoleInfoCallback callback) {
        new Thread(() -> {
            try {
                // ── 1. Email-first lookup (catches manually-set admins) ──
                if (email != null && !email.trim().isEmpty()) {
                    RoleInfo byEmail = queryRoleByEmail(email.trim());
                    if (byEmail != null) {
                        Log.d(TAG, "getUserRole: found by email=" + email);
                        // Also back-fill firebase_uid so future UID lookups work
                        if (firebaseUid != null && !firebaseUid.isEmpty()) {
                            backfillFirebaseUid(email.trim(), firebaseUid);
                        }
                        MAIN.post(() -> callback.onSuccess(byEmail));
                        return;
                    }
                }

                // ── 2. UID fallback ──────────────────────────────────────
                if (firebaseUid != null && !firebaseUid.isEmpty()) {
                    RoleInfo byUid = queryRoleByUid(firebaseUid);
                    if (byUid != null) {
                        Log.d(TAG, "getUserRole: found by uid=" + firebaseUid);
                        MAIN.post(() -> callback.onSuccess(byUid));
                        return;
                    }
                }

                // ── 3. No row found — hard error, NOT a silent default ───
                Log.w(TAG, "getUserRole: no row found for email=" + email
                        + " uid=" + firebaseUid);
                MAIN.post(() -> callback.onError("USER_NOT_FOUND"));

            } catch (Exception e) {
                Log.e(TAG, "getUserRole exception: " + e.getMessage());
                // Unwrap RuntimeException wrapping from queryRoleBy* helpers
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String errorMsg;
                if (cause instanceof IOException) {
                    NetworkHelper.FailureType type = NetworkHelper.classify((IOException) cause);
                    errorMsg = NetworkHelper.buildMessage(type, true);
                    Log.e(TAG, "getUserRole network type=" + type + " msg=" + cause.getMessage());
                } else {
                    errorMsg = "NETWORK_ERROR: " + e.getMessage();
                }
                MAIN.post(() -> callback.onError(errorMsg));
            }
        }).start();
    }

    /** Queries by email; returns null on empty result or HTTP error */
    private static RoleInfo queryRoleByEmail(String email) {
        try {
            String encoded = java.net.URLEncoder.encode(email, "UTF-8");
            String url = SUPABASE_URL + "/rest/v1/users"
                    + "?email=eq." + encoded
                    + "&select=is_user,is_service_provider,is_admin,firebase_uid"
                    + "&limit=1";
            Request req = new Request.Builder()
                    .url(url).get()
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();
            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                Log.d(TAG, "queryRoleByEmail HTTP " + r.code() + " body=" + body);
                if (r.code() == 200) {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    if (arr.length() > 0) {
                        JSONObject o = arr.getJSONObject(0);
                        return new RoleInfo(
                                o.optBoolean("is_user",             true),
                                o.optBoolean("is_service_provider", false),
                                o.optBoolean("is_admin",            false)
                        );
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "queryRoleByEmail exception: " + e.getMessage());
            throw new RuntimeException(e); // bubble up to getUserRoleWithEmail
        }
        return null;
    }

    /** Queries by firebase_uid; returns null on empty result or HTTP error */
    private static RoleInfo queryRoleByUid(String firebaseUid) {
        try {
            String encodedUid = java.net.URLEncoder.encode(firebaseUid, "UTF-8");
            String url = SUPABASE_URL + "/rest/v1/users"
                    + "?or=(firebase_uid.eq." + encodedUid + ",id.eq." + encodedUid + ")"
                    + "&select=is_user,is_service_provider,is_admin"
                    + "&limit=1";
            Request req = new Request.Builder()
                    .url(url).get()
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();
            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                Log.d(TAG, "queryRoleByUid HTTP " + r.code() + " body=" + body);
                if (r.code() == 200) {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    if (arr.length() > 0) {
                        JSONObject o = arr.getJSONObject(0);
                        return new RoleInfo(
                                o.optBoolean("is_user",             true),
                                o.optBoolean("is_service_provider", false),
                                o.optBoolean("is_admin",            false)
                        );
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "queryRoleByUid exception: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Silently back-fills firebase_uid on a row that was found by email.
     * Called after a successful email-lookup so the UID path works on next login.
     * Runs on the background thread that called getUserRoleWithEmail — fire and forget.
     */
    private static void backfillFirebaseUid(String email, String firebaseUid) {
        try {
            String encoded = java.net.URLEncoder.encode(email, "UTF-8");
            JSONObject patch = new JSONObject();
            patch.put("firebase_uid", firebaseUid);
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?email=eq." + encoded)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();
            try (Response r = HTTP.newCall(req).execute()) {
                Log.d(TAG, "backfillFirebaseUid HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "backfillFirebaseUid (non-fatal): " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADMIN PANEL METHODS
    // ══════════════════════════════════════════════════════════════════════

    public static void querySupabase(String sql, QueryCallback callback) {
        Log.w(TAG, "querySupabase: Use specific methods instead of raw SQL");
        callback.onError("Direct SQL not supported. Use specific query methods.");
    }

    public static void fetchPendingApprovals(QueryCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/pending_approvals"
                + "?status=eq.pending&order=application_date.desc";

        Request request = new Request.Builder()
                .url(url).get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        HTTP.newCall(request).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(Call call, IOException e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();
                try {
                    if (code == 200) {
                        MAIN.post(() -> {
                            try { callback.onSuccess(new org.json.JSONArray(body)); }
                            catch (Exception ex) { callback.onError(ex.getMessage()); }
                        });
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                } catch (Exception e) {
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    public static void approveProvider(String applicationId, String userFirebaseUid,
                                       String adminUid, Callback2 callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "approved");
            body.put("reviewed_by_uid", adminUid);
            body.put("reviewed_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + applicationId)
                    .patch(RequestBody.create(body.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code(); response.close();
                    if (code == 200 || code == 204) {
                        updateUserRoleAsync(userFirebaseUid, "provider", callback);
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    public static void rejectProvider(String applicationId, String rejectionReason,
                                      String adminUid, Callback2 callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "rejected");
            body.put("rejection_reason", rejectionReason);
            body.put("reviewed_by_uid", adminUid);
            body.put("reviewed_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + applicationId)
                    .patch(RequestBody.create(body.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code(); response.close();
                    if (code == 200 || code == 204) MAIN.post(callback::onSuccess);
                    else MAIN.post(() -> callback.onError("HTTP " + code));
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    private static void updateUserRoleAsync(String firebaseUid, String newRole,
                                             Callback2 callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("role", newRole);
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + firebaseUid)
                    .patch(RequestBody.create(body.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            HTTP.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code(); response.close();
                    if (code == 200 || code == 204) MAIN.post(callback::onSuccess);
                    else MAIN.post(() -> callback.onError("HTTP " + code));
                }
            });
        } catch (Exception e) {
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SAVE FCM TOKEN  — silently patch fcm_token on the users row
    // ══════════════════════════════════════════════════════════════════════
    /**
     * Stores the latest FCM token in user_fcm_tokens and mirrors it to users.fcm_token.
     * This method is safe to call on every login and token refresh.
     */
    public static void saveFcmToken(String firebaseUid, String fcmToken) {
        if (firebaseUid == null || firebaseUid.isEmpty()) return;
        if (fcmToken    == null || fcmToken.isEmpty())    return;

        new Thread(() -> {
            try {
                upsertFcmTokenRow(firebaseUid, fcmToken);
                mirrorFcmTokenToUsers(firebaseUid, fcmToken);
            } catch (Exception e) {
                Log.w(TAG, "saveFcmToken (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    private static void upsertFcmTokenRow(String firebaseUid, String fcmToken) {
        try {
            JSONObject row = new JSONObject();
            row.put("user_id", firebaseUid);
            row.put("fcm_token", fcmToken);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/user_fcm_tokens?on_conflict=user_id")
                    .post(RequestBody.create("[" + row.toString() + "]", JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "resolution=merge-duplicates,return=minimal")
                    .build();

            try (Response r = HTTP.newCall(request).execute()) {
                Log.d(TAG, "upsertFcmTokenRow HTTP " + r.code() + " uid=" + firebaseUid);
            }
        } catch (Exception e) {
            Log.w(TAG, "upsertFcmTokenRow failed (non-fatal): " + e.getMessage());
        }
    }

    private static void mirrorFcmTokenToUsers(String firebaseUid, String fcmToken) {
        try {
            JSONObject patch = new JSONObject();
            patch.put("fcm_token", fcmToken);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + firebaseUid)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();

            try (Response r = HTTP.newCall(request).execute()) {
                Log.d(TAG, "mirrorFcmTokenToUsers HTTP " + r.code() + " uid=" + firebaseUid);
            }
        } catch (Exception e) {
            Log.w(TAG, "mirrorFcmTokenToUsers failed (non-fatal): " + e.getMessage());
        }
    }

    public static void logAdminAction(String adminUid, String actionType,
                                      String targetType, String targetId, String details) {
        try {
            JSONObject logBody = new JSONObject();
            logBody.put("admin_uid",   adminUid);
            logBody.put("action_type", actionType);
            logBody.put("target_type", targetType);
            logBody.put("target_id",   targetId);
            if (details != null) logBody.put("details", details);

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/admin_logs")
                    .post(RequestBody.create(logBody.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            HTTP.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "logAdminAction failed: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    response.close();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "logAdminAction exception: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ACCOUNT LIFECYCLE METHODS
    // ══════════════════════════════════════════════════════════════════════

    public static void getAccountStatusByEmail(String email, AccountStatusCallback callback) {
        if (callback == null) return;
        if (email == null || email.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("Email is required"));
            return;
        }

        new Thread(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(email.trim(), "UTF-8");
                JSONObject row = fetchAccountRow("email=eq." + encoded + "&order=created_at.desc&limit=1");
                UserAccountStatus status = new UserAccountStatus();
                if (row != null) populateAccountStatus(status, row);
                MAIN.post(() -> callback.onSuccess(status));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
            }
        }).start();
    }

    public static void getAccountStatusByUid(String firebaseUid, AccountStatusCallback callback) {
        if (callback == null) return;
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("User uid is required"));
            return;
        }

        new Thread(() -> {
            try {
                String encodedUid = java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");
                JSONObject row = fetchAccountRow("or=(firebase_uid.eq." + encodedUid + ",id.eq." + encodedUid + ")&limit=1");
                UserAccountStatus status = new UserAccountStatus();
                if (row != null) populateAccountStatus(status, row);
                MAIN.post(() -> callback.onSuccess(status));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
            }
        }).start();
    }

    public static void getUserDisplayName(String firebaseUid, String email, StringCallback callback) {
        if (callback == null) return;

        getAccountStatusByUid(firebaseUid, new AccountStatusCallback() {
            @Override
            public void onSuccess(UserAccountStatus status) {
                if (status != null && status.exists && status.name != null && !status.name.trim().isEmpty()) {
                    callback.onSuccess(status.name.trim());
                    return;
                }
                if (email == null || email.trim().isEmpty()) {
                    callback.onError("Name not found");
                    return;
                }
                getAccountStatusByEmail(email.trim(), new AccountStatusCallback() {
                    @Override
                    public void onSuccess(UserAccountStatus statusByEmail) {
                        if (statusByEmail != null && statusByEmail.exists
                                && statusByEmail.name != null
                                && !statusByEmail.name.trim().isEmpty()) {
                            callback.onSuccess(statusByEmail.name.trim());
                        } else {
                            callback.onError("Name not found");
                        }
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public static void insertUserAfterAuth(String authUid,
                                           String fullName,
                                           String email,
                                           String phone,
                                           Callback2 callback) {
        if (callback == null) return;
        new Thread(() -> {
            try {
                JSONObject modern = new JSONObject();
                modern.put("firebase_uid", authUid);
                modern.put("name", fullName != null ? fullName.trim() : "");
                modern.put("email", email != null ? email.trim() : "");
                modern.put("phone", phone != null ? phone.trim() : "");
                modern.put("is_user", true);
                modern.put("is_service_provider", false);
                modern.put("is_admin", false);

                Response modernResp = postUsersInsert(modern);
                int modernCode = modernResp.code();
                String modernBody = modernResp.body() != null ? modernResp.body().string() : "";
                modernResp.close();

                if (modernCode == 200 || modernCode == 201 || modernCode == 204 || modernCode == 409) {
                    MAIN.post(callback::onSuccess);
                    return;
                }

                JSONObject legacy = new JSONObject();
                legacy.put("id", authUid);
                legacy.put("full_name", fullName != null ? fullName.trim() : "");
                legacy.put("email", email != null ? email.trim() : "");
                legacy.put("phone", phone != null ? phone.trim() : "");
                legacy.put("created_at", isoNowUtc());

                Response legacyResp = postUsersInsert(legacy);
                int legacyCode = legacyResp.code();
                String legacyBody = legacyResp.body() != null ? legacyResp.body().string() : "";
                legacyResp.close();

                if (legacyCode == 200 || legacyCode == 201 || legacyCode == 204 || legacyCode == 409) {
                    MAIN.post(callback::onSuccess);
                } else {
                    MAIN.post(() -> callback.onError("HTTP " + legacyCode + " " + legacyBody));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    private static Response postUsersInsert(JSONObject body) throws IOException {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/users")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .build();
        return HTTP.newCall(req).execute();
    }

    public static void restoreDeletedAccountByEmail(String email,
                                                    String firebaseUid,
                                                    String name,
                                                    String phone,
                                                    Callback2 callback) {
        if (callback == null) return;
        new Thread(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(email.trim(), "UTF-8");

                JSONObject modern = new JSONObject();
                modern.put("firebase_uid", firebaseUid);
                modern.put("name", name != null ? name.trim() : "");
                modern.put("phone", phone != null ? phone.trim() : "");
                modern.put("deleted_at", JSONObject.NULL);
                modern.put("is_user", true);
                modern.put("is_service_provider", false);
                modern.put("is_admin", false);

                Request modernReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users?email=eq." + encoded)
                        .patch(RequestBody.create(modern.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(modernReq).execute()) {
                    if (r.code() == 200 || r.code() == 204) {
                        MAIN.post(callback::onSuccess);
                        return;
                    }
                }

                JSONObject legacy = new JSONObject();
                legacy.put("id", firebaseUid);
                legacy.put("full_name", name != null ? name.trim() : "");
                legacy.put("phone", phone != null ? phone.trim() : "");
                legacy.put("deleted_at", JSONObject.NULL);

                Request legacyReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users?email=eq." + encoded)
                        .patch(RequestBody.create(legacy.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r2 = HTTP.newCall(legacyReq).execute()) {
                    if (r2.code() == 200 || r2.code() == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + r2.code()));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    public static void hardDeleteUserData(String firebaseUid, Callback2 callback) {
        if (callback == null) return;
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("User uid is required"));
            return;
        }

        new Thread(() -> {
            List<String> failures = new ArrayList<>();
            try {
                String encUid = java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");

                // Dependents first, then the user row.
                deleteByFilter("business_media",
                        "or=(provider_id.eq." + encUid + ",firebase_uid.eq." + encUid + ")",
                        true,
                        failures);
                deleteByFilter("bookings",
                        "or=(customer_id.eq." + encUid + ",provider_id.eq." + encUid + ")",
                        true,
                        failures);
                deleteByFilter("notifications",
                        "or=(receiver_id.eq." + encUid + ",user_id.eq." + encUid + ",firebase_uid.eq." + encUid + ")",
                        true,
                        failures);
                deleteByFilter("provider_applications",
                        "or=(firebase_uid.eq." + encUid + ",id.eq." + encUid + ")",
                        true,
                        failures);
                deleteByFilter("services",
                        "or=(provider_id.eq." + encUid + ",firebase_uid.eq." + encUid + ")",
                        true,
                        failures);
                deleteByFilter("user_fcm_tokens",
                        "or=(user_id.eq." + encUid + ",firebase_uid.eq." + encUid + ")",
                        true,
                        failures);

                boolean providerProfileDeleted = deleteByFilter(
                        "provider_profiles",
                        "or=(firebase_uid.eq." + encUid + ",id.eq." + encUid + ")",
                        true,
                        failures
                );
                if (!providerProfileDeleted) {
                    deleteByFilter("provider_profiles", "id=eq." + encUid, true, failures);
                }

                boolean usersDeleted = deleteByFilter(
                        "users",
                        "or=(firebase_uid.eq." + encUid + ",id.eq." + encUid + ")",
                        false,
                        failures
                );
                if (!usersDeleted) {
                    deleteByFilter("users", "id=eq." + encUid, false, failures);
                }

                if (failures.isEmpty()) {
                    MAIN.post(callback::onSuccess);
                } else {
                    MAIN.post(() -> callback.onError("Failed to remove data from: " + joinFailures(failures)));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // Backward-compatible name used by older call sites.
    public static void softDeleteUser(String firebaseUid, Callback2 callback) {
        hardDeleteUserData(firebaseUid, callback);
    }

    private static boolean deleteByFilter(String table,
                                          String filter,
                                          boolean optional,
                                          List<String> failures) {
        String url = SUPABASE_URL + "/rest/v1/" + table + "?" + filter;
        Request req = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Prefer", "return=minimal")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            int code = r.code();
            if (code == 200 || code == 204) {
                return true;
            }
            // Optional tables/filters may legitimately fail in older schemas.
            if (optional && (code == 400 || code == 404)) {
                Log.w(TAG, "deleteByFilter optional skip: table=" + table + " code=" + code);
                return false;
            }

            String body = r.body() != null ? r.body().string() : "";
            Log.e(TAG, "deleteByFilter failed table=" + table + " code=" + code + " body=" + body);
            failures.add(table + "(HTTP " + code + ")");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "deleteByFilter exception table=" + table + " msg=" + e.getMessage());
            failures.add(table);
            return false;
        }
    }

    private static String joinFailures(List<String> failures) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(failures.get(i));
        }
        return sb.toString();
    }

    private static JSONObject fetchAccountRow(String filterQuery) throws Exception {
        String[] selects = new String[] {
                "firebase_uid,name,email,created_at,deleted_at,profile_image,profile_image_url",
                "id,full_name,email,created_at,deleted_at,profile_image,profile_image_url",
                "*"
        };

        for (String select : selects) {
            String url = SUPABASE_URL + "/rest/v1/users?" + filterQuery
                    + "&select=" + java.net.URLEncoder.encode(select, "UTF-8");

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
                    if (arr.length() > 0) return arr.getJSONObject(0);
                    return null;
                }
                if (r.code() != 400) {
                    Log.w(TAG, "fetchAccountRow HTTP " + r.code() + " body=" + body);
                }
            }
        }
        return null;
    }

    private static void populateAccountStatus(UserAccountStatus status, JSONObject row) {
        if (status == null || row == null) return;
        status.exists = true;

        String uid = row.optString("firebase_uid", "");
        if (uid == null || uid.trim().isEmpty()) uid = row.optString("id", "");
        status.firebaseUid = uid != null ? uid : "";

        String name = row.optString("name", "");
        if (name == null || name.trim().isEmpty()) name = row.optString("full_name", "");
        status.name = name != null ? name : "";

        status.email = row.optString("email", "");
        status.createdAtRaw = row.optString("created_at", "");
        status.deletedAtRaw = row.optString("deleted_at", "");

        String p = row.optString("profile_image", "");
        if (p == null || p.trim().isEmpty()) {
            p = row.optString("profile_image_url", "");
        }
        status.profileImagePath = p != null ? p : "";
        status.createdAtMs = parseIsoToMillis(status.createdAtRaw);
        status.deletedAtMs = parseIsoToMillis(status.deletedAtRaw);
    }

    private static long parseIsoToMillis(String iso) {
        if (iso == null || iso.trim().isEmpty() || "null".equalsIgnoreCase(iso.trim())) {
            return 0L;
        }

        String normalized = iso.trim();
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+0000";
        }

        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                if ("yyyy-MM-dd HH:mm:ss".equals(pattern)) {
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = sdf.parse(normalized);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) {
                // try next pattern
            }
        }
        return 0L;
    }

    private static String isoNowUtc() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
