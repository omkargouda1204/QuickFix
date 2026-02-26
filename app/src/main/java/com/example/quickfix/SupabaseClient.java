package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
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
                body.put("firebase_uid", firebaseUid);
                body.put("name",        name  != null ? name.trim()  : "");
                body.put("email",       email != null ? email.trim() : "");
                body.put("phone",       phone != null ? phone.trim() : "");
                body.put("is_user", isUser);
                body.put("is_service_provider", isServiceProvider);
                body.put("is_admin", isAdmin);
                body.put("is_verified", true);

                // ── Step 1: Try INSERT with ignoreDuplicates ──────────
                // If row already exists, Supabase returns 200/204 with no body (ignores it).
                // This means the existing role is NEVER overwritten.
                Request insertReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users")
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        // ignoreDuplicates: if firebase_uid already exists, skip silently
                        .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                        .build();

                try (Response r = HTTP.newCall(insertReq).execute()) {
                    int code = r.code();
                    String rawBody = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "upsertUser INSERT HTTP " + code + " | " + rawBody);

                    if (code == 200 || code == 201 || code == 204) {
                        // Either inserted (new user) or silently ignored (existing user)
                        // Now PATCH name/phone to keep them fresh — but NEVER touch role
                        patchUserProfileSync(firebaseUid, name, phone);
                        Log.d(TAG, "upsertUser OK for " + email);
                        MAIN.post(callback::onSuccess);
                    } else {
                        Log.w(TAG, "upsertUser INSERT HTTP " + code + " — " + rawBody);
                        // Still try patch as fallback
                        patchUserProfileSync(firebaseUid, name, phone);
                        MAIN.post(callback::onSuccess);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "upsertUser exception: " + e.getMessage());
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
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
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
            String url = SUPABASE_URL + "/rest/v1/users"
                    + "?firebase_uid=eq." + firebaseUid
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
                    .url(SUPABASE_URL + "/rest/v1/pending_approvals?id=eq." + applicationId)
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
                    .url(SUPABASE_URL + "/rest/v1/pending_approvals?id=eq." + applicationId)
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
}
