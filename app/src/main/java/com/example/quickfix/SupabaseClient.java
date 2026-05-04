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
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private static final String SUPABASE_URL = (BuildConfig.SUPABASE_URL == null || BuildConfig.SUPABASE_URL.isEmpty())
            ? "https://example.supabase.co" : BuildConfig.SUPABASE_URL;
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
        public String role = "user";

        public RoleInfo(boolean isUser, boolean isServiceProvider, boolean isAdmin) {
            this.isUser = isUser;
            this.isServiceProvider = isServiceProvider;
            this.isAdmin = isAdmin;
            // Infer role string if not provided
            if (isAdmin) this.role = "admin";
            else if (isServiceProvider) this.role = "provider";
            else this.role = "user";
        }

        public RoleInfo(String role) {
            this.role = (role != null) ? role : "user";
            this.isAdmin = "admin".equalsIgnoreCase(this.role);
            this.isServiceProvider = "provider".equalsIgnoreCase(this.role);
            this.isUser = "user".equalsIgnoreCase(this.role);
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

    public interface UserProfileCallback {
        void onSuccess();
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
                body.put("full_name",            name  != null ? name.trim()  : "");
                body.put("email",                email != null ? email.trim() : "");
                body.put("phone",                phone != null ? phone.trim() : "");
                
                String role = "user";
                if (isAdmin) role = "admin";
                else if (isServiceProvider) role = "provider";
                body.put("role", role);

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
            if (name  != null && !name.trim().isEmpty()) {
                patch.put("full_name", name.trim());
                patch.put("name", name.trim()); // Keep legacy 'name' in sync
            }
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
    //  FETCH USER PROFILE (GLOBAL STATE)
    // ══════════════════════════════════════════════════════════════════════
    public static void fetchUserProfile(String firebaseUid, UserProfileCallback callback) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            if (callback != null) MAIN.post(() -> callback.onError("User ID is empty"));
            return;
        }

        new Thread(() -> {
            try {
                String encodedUid = java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");

                // Step 1: Try user_profiles directly by id (works if user_profiles.id == firebase_uid or UUID)
                // First try querying users table to get the row with full_name/role
                String usersUrl = SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + encodedUid
                        + "&select=id,full_name,name,email,phone,profile_image,role,is_admin,is_service_provider&limit=1";

                Request usersReq = new Request.Builder()
                        .url(usersUrl).get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(usersReq).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        org.json.JSONArray arr = new org.json.JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            UserSession.id = o.optString("id", firebaseUid);
                            String fullName = o.optString("full_name", "");
                            if (fullName.isEmpty()) fullName = o.optString("name", "");
                            UserSession.name = fullName;
                            UserSession.email = o.optString("email", "");
                            UserSession.phone = o.optString("phone", "");
                            UserSession.image = o.optString("profile_image", "");
                            String role = o.optString("role", "");
                            if (role.isEmpty()) {
                                if (o.optBoolean("is_admin", false)) role = "admin";
                                else if (o.optBoolean("is_service_provider", false)) role = "provider";
                                else role = "user";
                            }
                            UserSession.role = role;
                            if (callback != null) MAIN.post(callback::onSuccess);
                            return;
                        }
                    }
                }

                // Step 2: If not found in users, try user_profiles by id=firebaseUid
                String profileUrl = SUPABASE_URL + "/rest/v1/user_profiles?id=eq." + encodedUid + "&limit=1";
                Request profileReq = new Request.Builder()
                        .url(profileUrl).get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(profileReq).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        org.json.JSONArray arr = new org.json.JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            UserSession.id = o.optString("id", firebaseUid);
                            UserSession.name = o.optString("full_name", "");
                            UserSession.email = o.optString("email", "");
                            UserSession.phone = o.optString("phone", "");
                            UserSession.image = o.optString("profile_image", "");
                            UserSession.role = o.optString("role", "user");
                            if (callback != null) MAIN.post(callback::onSuccess);
                            return;
                        }
                    }
                }

                if (callback != null) MAIN.post(() -> callback.onError("Profile not found"));
            } catch (Exception e) {
                Log.e(TAG, "fetchUserProfile exception: " + e.getMessage());
                if (callback != null) MAIN.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Save profile image URL to both users and user_profiles tables.
     */
    public static void updateProfileImage(String firebaseUid, String imageUrl, Callback2 callback) {
        new Thread(() -> {
            try {
                JSONObject patch = new JSONObject();
                patch.put("profile_image", imageUrl);

                String encodedUid = java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");

                // Update users table
                Request req1 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + encodedUid)
                        .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();
                try (Response r = HTTP.newCall(req1).execute()) {
                    Log.d(TAG, "updateProfileImage users HTTP " + r.code());
                }

                // Also update user_profiles table
                String userId = UserSession.id.isEmpty() ? firebaseUid : UserSession.id;
                Request req2 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/user_profiles?id=eq." + java.net.URLEncoder.encode(userId, "UTF-8"))
                        .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();
                try (Response r = HTTP.newCall(req2).execute()) {
                    Log.d(TAG, "updateProfileImage user_profiles HTTP " + r.code());
                }

                UserSession.image = imageUrl;
                if (callback != null) MAIN.post(callback::onSuccess);
            } catch (Exception e) {
                Log.w(TAG, "updateProfileImage exception: " + e.getMessage());
                if (callback != null) MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
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
                    + "&select=role,is_user,is_service_provider,is_admin,firebase_uid"
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
                        if (o.has("role") && !o.isNull("role")) {
                            return new RoleInfo(o.optString("role", "user"));
                        }
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
            String filter = "firebase_uid=eq." + encodedUid;
            if (looksLikeUuid(firebaseUid)) {
                filter = "or=(firebase_uid.eq." + encodedUid + ",id.eq." + encodedUid + ")";
            }
            String url = SUPABASE_URL + "/rest/v1/users"
                    + "?" + filter
                    + "&select=role,is_user,is_service_provider,is_admin"
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
                        if (o.has("role") && !o.isNull("role")) {
                            return new RoleInfo(o.optString("role", "user"));
                        }
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

    /**
     * Updates a user's role via the admin_set_user_role RPC function (SECURITY DEFINER).
     * This bypasses RLS so the admin panel can promote users without hitting 403.
     * Also syncs the boolean flags (is_admin, is_service_provider, is_user).
     */
    private static void updateUserRoleAsync(String firebaseUid, String newRole,
                                             Callback2 callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("p_firebase_uid", firebaseUid);
            body.put("p_new_role", newRole);

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/rpc/admin_set_user_role")
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            HTTP.newCall(req).enqueue(new okhttp3.Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "updateUserRoleAsync RPC failed: " + e.getMessage());
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String responseBody = response.body() != null ? response.body().string() : "";
                    response.close();
                    Log.d(TAG, "updateUserRoleAsync RPC HTTP " + code + " uid=" + firebaseUid + " role=" + newRole);
                    if (code == 200 || code == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        Log.e(TAG, "updateUserRoleAsync RPC error: " + responseBody);
                        MAIN.post(() -> callback.onError("HTTP " + code + ": " + responseBody));
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "updateUserRoleAsync exception: " + e.getMessage());
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
                mirrorFcmTokenToProviderProfiles(firebaseUid, fcmToken);
                mirrorFcmTokenToUserProfiles(firebaseUid, fcmToken); // ✅ ensures Edge Function can find token
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

    private static void mirrorFcmTokenToProviderProfiles(String firebaseUid, String fcmToken) {
        try {
            JSONObject patch = new JSONObject();
            patch.put("fcm_token", fcmToken);

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/provider_profiles?firebase_uid=eq." + firebaseUid)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();

            try (Response r = HTTP.newCall(request).execute()) {
                Log.d(TAG, "mirrorFcmTokenToProviderProfiles HTTP " + r.code() + " uid=" + firebaseUid);
            }
        } catch (Exception e) {
            Log.w(TAG, "mirrorFcmTokenToProviderProfiles failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Mirror the FCM token to user_profiles so the Edge Function can find it
     * by looking up users.id (Supabase UUID) → user_profiles.id.
     */
    private static void mirrorFcmTokenToUserProfiles(String firebaseUid, String fcmToken) {
        try {
            // First resolve Supabase UUID from firebase_uid
            String url = SUPABASE_URL + "/rest/v1/users"
                    + "?firebase_uid=eq." + firebaseUid
                    + "&select=id&limit=1";
            Request resolveReq = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            String supabaseUuid = "";
            try (Response r = HTTP.newCall(resolveReq).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    if (arr.length() > 0) supabaseUuid = arr.getJSONObject(0).optString("id", "");
                }
            }

            if (supabaseUuid.isEmpty()) {
                Log.d(TAG, "mirrorFcmTokenToUserProfiles: supabase UUID not found for " + firebaseUid);
                return;
            }

            JSONObject patch = new JSONObject();
            patch.put("fcm_token", fcmToken);

            Request patchReq = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/user_profiles?id=eq." + supabaseUuid)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();

            try (Response r = HTTP.newCall(patchReq).execute()) {
                Log.d(TAG, "mirrorFcmTokenToUserProfiles HTTP " + r.code() + " uuid=" + supabaseUuid);
            }
        } catch (Exception e) {
            Log.w(TAG, "mirrorFcmTokenToUserProfiles failed (non-fatal): " + e.getMessage());
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
                String normalizedUid = firebaseUid.trim();
                String encodedUid = java.net.URLEncoder.encode(normalizedUid, "UTF-8");
                String filter = "firebase_uid=eq." + encodedUid + "&limit=1";
                if (looksLikeUuid(normalizedUid)) {
                    filter = "or=(firebase_uid.eq." + encodedUid + ",id.eq." + encodedUid + ")&limit=1";
                }
                JSONObject row = fetchAccountRow(filter);
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
        if (authUid == null || authUid.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("firebase_uid is required."));
            return;
        }
        new Thread(() -> {
            try {
                String normalizedUid = authUid.trim();
                JSONObject modern = new JSONObject();
                modern.put("firebase_uid", normalizedUid);
                modern.put("full_name", fullName != null ? fullName.trim() : "");
                modern.put("name", fullName != null ? fullName.trim() : "");
                modern.put("email", email != null ? email.trim() : "");
                modern.put("phone", phone != null ? phone.trim() : "");
                modern.put("role", "user");

                Response modernResp = postUsersInsert(modern);
                int modernCode = modernResp.code();
                String modernBody = modernResp.body() != null ? modernResp.body().string() : "";
                modernResp.close();

                if (modernCode == 200 || modernCode == 201 || modernCode == 204 || modernCode == 409) {
                    MAIN.post(callback::onSuccess);
                    return;
                }

                JSONObject legacy = new JSONObject();
                legacy.put("id", normalizedUid);
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
                    Log.e(TAG, "insertUserAfterAuth failed modern=" + modernCode + " legacy=" + legacyCode
                            + " modernBody=" + modernBody + " legacyBody=" + legacyBody);
                    String message = buildSupabaseHttpError("insert user", legacyCode, legacyBody);
                    MAIN.post(() -> callback.onError(message));
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
        if (email == null || email.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("Email is required."));
            return;
        }
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            MAIN.post(() -> callback.onError("firebase_uid is required."));
            return;
        }
        new Thread(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(email.trim(), "UTF-8");

                JSONObject modern = new JSONObject();
                modern.put("firebase_uid", firebaseUid);
                modern.put("full_name", name != null ? name.trim() : "");
                modern.put("name", name != null ? name.trim() : "");
                modern.put("phone", phone != null ? phone.trim() : "");
                modern.put("deleted_at", JSONObject.NULL);
                modern.put("role", "user");

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
                        String body = r2.body() != null ? r2.body().string() : "";
                        String message = buildSupabaseHttpError("restore account", r2.code(), body);
                        MAIN.post(() -> callback.onError(message));
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

        final String normalizedUid = firebaseUid.trim();
        if (normalizedUid.contains(" ")) {
            MAIN.post(() -> callback.onError("Invalid user uid"));
            return;
        }

        new Thread(() -> {
            List<String> failures = new ArrayList<>();
            try {
                Log.d(TAG, "Starting account deletion for uid: " + normalizedUid);
                
                String encUid = java.net.URLEncoder.encode(normalizedUid, "UTF-8");
                JSONObject accountRow = fetchAccountRow("firebase_uid=eq." + encUid + "&limit=1");
                String userRowId = accountRow != null ? safeJson(accountRow.optString("id", "")) : "";
                
                Log.d(TAG, "Account lookup result - firebase_uid found: " + (accountRow != null) 
                    + ", user_row_id: " + (userRowId.isEmpty() ? "NOT_FOUND" : "FOUND"));

                List<String> identityIds = collectIdentityIds(normalizedUid, userRowId);
                List<String> encodedIds = encodeValues(identityIds);
                
                Log.d(TAG, "Identity IDs to delete: " + identityIds.size() + " entries");

                // Dependents first, then the user row.
                Log.d(TAG, "Deleting dependent records...");
                
                deleteWithFallbackFilters("business_media",
                        buildIdentityFilters(encodedIds, "provider_id", "firebase_uid", "user_id"),
                        true, failures);

                deleteWithFallbackFilters("bookings",
                        buildIdentityFilters(encodedIds, "customer_id", "provider_id", "firebase_uid"),
                        true, failures);

                deleteWithFallbackFilters("notifications",
                        buildIdentityFilters(encodedIds, "receiver_id", "user_id", "firebase_uid"),
                        true, failures);

                deleteWithFallbackFilters("emergency_bookings",
                        buildIdentityFilters(encodedIds, "user_id", "assigned_provider_id"),
                        true, failures);

                deleteWithFallbackFilters("provider_applications",
                        buildIdentityFilters(encodedIds, "firebase_uid", "id"),
                        true, failures);

                deleteWithFallbackFilters("services",
                        buildIdentityFilters(encodedIds, "provider_id", "firebase_uid"),
                        true, failures);

                deleteWithFallbackFilters("user_fcm_tokens",
                        buildIdentityFilters(encodedIds, "user_id", "firebase_uid"),
                        true, failures);

                deleteWithFallbackFilters("provider_profiles",
                        buildIdentityFilters(encodedIds, "firebase_uid", "id"),
                        true, failures);

                Log.d(TAG, "Deleting user record...");
                deleteWithFallbackFilters("users",
                        buildIdentityFilters(encodedIds, "firebase_uid", "id"),
                        false, failures);

                // Verify deletion
                Log.d(TAG, "Verifying account deletion...");
                boolean stillExists = fetchAccountRow("firebase_uid=eq." + encUid + "&limit=1") != null;
                if (!stillExists && !userRowId.isEmpty()) {
                    String encRowId = java.net.URLEncoder.encode(userRowId, "UTF-8");
                    stillExists = fetchAccountRow("id=eq." + encRowId + "&limit=1") != null;
                }

                if (stillExists) {
                    Log.w(TAG, "User account still exists after deletion attempt");
                    failures.add("users(VERIFY_EXISTS)");
                } else {
                    Log.d(TAG, "User account successfully verified as deleted");
                }

                if (failures.isEmpty()) {
                    Log.i(TAG, "Account deletion completed successfully for uid: " + normalizedUid);
                    MAIN.post(callback::onSuccess);
                } else {
                    String failureMsg = "Failed to remove data from: " + joinFailures(failures);
                    Log.e(TAG, "Account deletion incomplete: " + failureMsg);
                    MAIN.post(() -> callback.onError(failureMsg));
                }
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                Log.e(TAG, "Account deletion exception: " + errorMsg, e);
                MAIN.post(() -> callback.onError(errorMsg));
            }
        }).start();
    }

    private static List<String> collectIdentityIds(String firebaseUid, String userRowId) {
        Set<String> dedupe = new LinkedHashSet<>();
        if (firebaseUid != null && !firebaseUid.trim().isEmpty()) {
            dedupe.add(firebaseUid.trim());
        }
        if (userRowId != null && !userRowId.trim().isEmpty()) {
            dedupe.add(userRowId.trim());
        }
        return new ArrayList<>(dedupe);
    }

    private static List<String> encodeValues(List<String> values) throws Exception {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            out.add(java.net.URLEncoder.encode(value.trim(), "UTF-8"));
        }
        return out;
    }

    private static List<String> buildIdentityFilters(List<String> encodedIds, String... columns) {
        List<String> filters = new ArrayList<>();
        if (encodedIds == null || encodedIds.isEmpty() || columns == null || columns.length == 0) {
            return filters;
        }
        for (String column : columns) {
            if (column == null || column.trim().isEmpty()) continue;
            for (String encId : encodedIds) {
                if (encId == null || encId.trim().isEmpty()) continue;
                filters.add(column.trim() + "=eq." + encId.trim());
            }
        }
        return filters;
    }

    private static void deleteWithFallbackFilters(String table,
                                                  List<String> filters,
                                                  boolean optional,
                                                  List<String> failures) {
        if (filters == null || filters.isEmpty()) {
            failures.add(table + "(NO_FILTER)");
            return;
        }

        boolean success = false;
        boolean hadHardFailure = false;
        for (String filter : filters) {
            if (filter == null || filter.trim().isEmpty()) continue;
            DeleteAttemptResult result = deleteByFilter(table, filter.trim(), optional, failures);
            if (result == DeleteAttemptResult.SUCCESS) {
                success = true;
                break;
            } else if (result == DeleteAttemptResult.HARD_FAILURE) {
                hadHardFailure = true;
            }
        }

        if (!success && !optional && !hadHardFailure) {
            failures.add(table + "(NO_MATCH)");
        }
    }

    // Backward-compatible name used by older call sites.
    public static void softDeleteUser(String firebaseUid, Callback2 callback) {
        hardDeleteUserData(firebaseUid, callback);
    }

    private enum DeleteAttemptResult {
        SUCCESS,
        SOFT_SKIP,
        HARD_FAILURE
    }

    private static DeleteAttemptResult deleteByFilter(String table,
                                                      String filter,
                                                      boolean optional,
                                                      List<String> failures) {
        if (table == null || table.trim().isEmpty() || filter == null || filter.trim().isEmpty()) {
            failures.add((table != null ? table : "unknown") + "(INVALID_FILTER)");
            return DeleteAttemptResult.HARD_FAILURE;
        }

        String url = SUPABASE_URL + "/rest/v1/" + table + "?" + filter;
        Request req = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            int code = r.code();
            if (code == 200 || code == 204) {
                Log.d(TAG, "deleteByFilter SUCCESS: table=" + table + " filter=" + filter + " code=" + code);
                return DeleteAttemptResult.SUCCESS;
            }
            // Optional tables/filters may legitimately fail in older schemas.
            if (optional && (code == 400 || code == 404)) {
                Log.w(TAG, "deleteByFilter optional skip: table=" + table + " code=" + code);
                return DeleteAttemptResult.SOFT_SKIP;
            }

            String body = r.body() != null ? r.body().string() : "";
            String parsed = parseSupabaseError(body);
            Log.e(TAG, "deleteByFilter failed table=" + table + " filter=" + filter
                    + " code=" + code + " error=" + parsed);
            String failureTag = table + "(HTTP " + code + ")";
            if (!failures.contains(failureTag)) failures.add(failureTag);
            return DeleteAttemptResult.HARD_FAILURE;
        } catch (Exception e) {
            Log.e(TAG, "deleteByFilter exception table=" + table + " msg=" + e.getMessage());
            if (!failures.contains(table)) failures.add(table);
            return DeleteAttemptResult.HARD_FAILURE;
        }
    }

    private static boolean looksLikeUuid(String value) {
        if (value == null) return false;
        return value.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    }

    private static String joinFailures(List<String> failures) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failures.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(failures.get(i));
        }
        return sb.toString();
    }

    private static String safeJson(String value) {
        return value == null ? "" : value.trim();
    }

    private static JSONObject fetchAccountRow(String filterQuery) throws Exception {
        String[] selects = new String[] {
                "firebase_uid,full_name,name,email,created_at,deleted_at,profile_image,profile_image_url,role",
                "id,full_name,email,created_at,deleted_at,profile_image,profile_image_url,role",
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

        String name = row.optString("full_name", "");
        if (name == null || name.trim().isEmpty()) name = row.optString("name", "");
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

    private static String buildSupabaseHttpError(String operation, int code, String body) {
        String parsed = parseSupabaseError(body);
        if (code == 400) {
            return "Bad request while trying to " + operation + ". " + parsed;
        }
        if (code == 404) {
            return "Data not found while trying to " + operation + ".";
        }
        return "Supabase HTTP " + code + ": " + parsed;
    }

    private static String parseSupabaseError(String raw) {
        String safeRaw = raw != null ? raw.trim() : "";
        if (safeRaw.isEmpty()) return "No details provided.";

        try {
            JSONObject obj = new JSONObject(safeRaw);
            String message = obj.optString("message", "").trim();
            if (message.isEmpty()) message = obj.optString("error", "").trim();
            String details = obj.optString("details", "").trim();
            String hint = obj.optString("hint", "").trim();

            StringBuilder sb = new StringBuilder();
            if (!message.isEmpty()) sb.append(message);
            if (!details.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("Details: ").append(details);
            }
            if (!hint.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("Hint: ").append(hint);
            }

            return sb.length() > 0 ? sb.toString() : safeRaw;
        } catch (Exception ignored) {
            return safeRaw;
        }
    }

    private static String isoNowUtc() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    // ── User Settings ──────────────────────────────────────────────────────
    public interface UserSettingsCallback {
        void onSuccess(UserSettings settings);
        void onError(String message);
    }

    public static class UserSettings {
        public String userId = "";
        public boolean darkMode = false;
        public String language = "en";
    }

    public static void fetchUserSettings(String firebaseUid, String email, UserSettingsCallback callback) {
        if (callback == null) return;
        if ((firebaseUid == null || firebaseUid.trim().isEmpty())
                && (email == null || email.trim().isEmpty())) {
            MAIN.post(() -> callback.onError("User identity is required"));
            return;
        }

        new Thread(() -> {
            try {
                JSONObject row = null;
                if (firebaseUid != null && !firebaseUid.trim().isEmpty()) {
                    String encodedUid = java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");
                    row = fetchFirstUserByFilter("firebase_uid=eq." + encodedUid);
                }

                if (row == null && email != null && !email.trim().isEmpty()) {
                    String encodedEmail = java.net.URLEncoder.encode(email.trim(), "UTF-8");
                    row = fetchFirstUserByFilter("email=eq." + encodedEmail);
                }

                if (row == null) {
                    MAIN.post(() -> callback.onError("User settings not found"));
                    return;
                }

                UserSettings settings = new UserSettings();
                settings.userId = row.optString("id", "");
                settings.darkMode = row.optBoolean("dark_mode", false);
                settings.language = row.optString("language", "en");
                if (settings.language == null || settings.language.trim().isEmpty()) {
                    settings.language = "en";
                }

                MAIN.post(() -> callback.onSuccess(settings));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
            }
        }).start();
    }

    public static void updateUserSettings(String firebaseUid,
                                          String email,
                                          boolean darkMode,
                                          String language,
                                          Callback2 callback) {
        if (callback == null) return;
        String normalizedLanguage = (language == null || language.trim().isEmpty()) ? "en" : language.trim();

        if ((firebaseUid == null || firebaseUid.trim().isEmpty())
                && (email == null || email.trim().isEmpty())) {
            MAIN.post(() -> callback.onError("User identity is required"));
            return;
        }

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("dark_mode", darkMode);
                payload.put("language", normalizedLanguage);

                String filter;
                if (firebaseUid != null && !firebaseUid.trim().isEmpty()) {
                    filter = "firebase_uid=eq." + java.net.URLEncoder.encode(firebaseUid.trim(), "UTF-8");
                } else {
                    filter = "email=eq." + java.net.URLEncoder.encode(email.trim(), "UTF-8");
                }

                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/users?" + filter)
                        .patch(RequestBody.create(payload.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    String body = response.body() != null ? response.body().string() : "";
                    if (code == 200 || code == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        String error = buildSupabaseHttpError("update user settings", code, body);
                        MAIN.post(() -> callback.onError(error));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
            }
        }).start();
    }

    private static JSONObject fetchFirstUserByFilter(String filter) {
        if (filter == null || filter.trim().isEmpty()) return null;
        try {
            String select = java.net.URLEncoder.encode("id,dark_mode,language", "UTF-8");
            String url = SUPABASE_URL + "/rest/v1/users?" + filter + "&select=" + select + "&limit=1";
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = HTTP.newCall(request).execute()) {
                if (response.code() != 200) {
                    return null;
                }
                String body = response.body() != null ? response.body().string() : "[]";
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return null;
                return arr.getJSONObject(0);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
