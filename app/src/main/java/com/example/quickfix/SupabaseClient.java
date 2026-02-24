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
 * table via the PostgREST REST API using OkHttp.
 *
 * No Firestore. No backend server. Pure HTTPS REST.
 *
 * Table schema (from your SQL):
 *   firebase_uid TEXT UNIQUE NOT NULL
 *   name         TEXT NOT NULL
 *   email        TEXT NOT NULL UNIQUE
 *   phone        TEXT
 *   role         TEXT DEFAULT 'user'
 *   is_verified  BOOLEAN DEFAULT TRUE
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";

    // ── Supabase project credentials ──────────────────────────────────────
    // Dashboard → Settings → API → Project URL + anon public key
    private static final String SUPABASE_URL =
            "https://rddzycqxtgryrpymivfd.supabase.co";

    // anon / public key (safe to ship in the APK)
    private static final String ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkZHp5Y3F4dGdyeXJweW1pdmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzk5NjYwNzksImV4cCI6MjA1NTU0MjA3OX0" +
            ".Tq6tKoSvqZSp8VLZyulqxP03glP1EfcJp2nW4nkSFqI";
    // ─────────────────────────────────────────────────────────────────────

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Callback interface ────────────────────────────────────────────────
    public interface Callback2 {
        void onSuccess();
        void onError(String message);
    }

    // ─────────────────────────────────────────────────────────────────────
    /**
     * Upsert a user profile row.
     *
     * Uses PostgREST UPSERT with "Prefer: resolution=merge-duplicates" so
     * calling this multiple times for the same firebase_uid is safe — it
     * updates the existing row rather than inserting a duplicate.
     *
     * If the Supabase call fails for any reason the callback still fires
     * onSuccess() so that the Firebase auth flow (verification email, etc.)
     * is NOT blocked by a database issue.
     */
    public static void upsertUser(
            String firebaseUid,
            String name,
            String email,
            String phone,
            String role,
            Callback2 callback) {

        try {
            // Build JSON body matching public.users columns
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("name",         name  != null ? name.trim()  : "");
            body.put("email",        email != null ? email.trim() : "");
            body.put("phone",        phone != null ? phone.trim() : "");
            body.put("role",         role  != null ? role.trim()  : "user");
            body.put("is_verified",  true);   // Firebase handles verification

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users")
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    // Auth headers — both required by Supabase
                    .addHeader("apikey",         ANON_KEY)
                    .addHeader("Authorization",  "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",   "application/json")
                    // UPSERT: on conflict (firebase_uid) → update existing row
                    .addHeader("Prefer",
                               "resolution=merge-duplicates,return=minimal")
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "upsertUser network failure: " + e.getMessage());
                    // Non-blocking — proceed with Firebase flow
                    MAIN.post(callback::onSuccess);
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {
                    int    code    = response.code();
                    String rawBody = response.body() != null
                            ? response.body().string() : "";
                    response.close();

                    Log.d(TAG, "upsertUser HTTP " + code + " | " + rawBody);

                    if (code == 200 || code == 201 || code == 204) {
                        // Success
                        MAIN.post(callback::onSuccess);
                    } else {
                        // Parse Supabase error body for a useful log message
                        String detail = "HTTP " + code;
                        try {
                            JSONObject err = new JSONObject(rawBody);
                            detail = err.optString("message",
                                    err.optString("hint", detail));
                        } catch (Exception ignored) {}
                        Log.e(TAG, "upsertUser failed: " + detail);

                        // Non-blocking — still proceed with auth flow
                        MAIN.post(callback::onSuccess);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "upsertUser exception: " + e.getMessage());
            MAIN.post(callback::onSuccess); // non-blocking
        }
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // ADMIN PANEL METHODS
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Callback interface for SELECT/READ queries
     */
    public interface QueryCallback {
        void onSuccess(org.json.JSONArray result);
        void onError(String message);
    }

    /**
     * Generic SELECT query via PostgREST REST API
     * Used for: admin queries, role checks, permission validation, data listing
     */
    public static void querySupabase(String sql, QueryCallback callback) {
        // Note: SQL queries are NOT directly supported by PostgREST for security.
        // Use PostgREST endpoints with filters instead (see specific methods below)
        // This is a placeholder for demonstrating the pattern
        Log.w(TAG, "querySupabase: Use specific methods instead of raw SQL");
        callback.onError("Direct SQL not supported. Use specific query methods.");
    }

    /**
     * Fetch all pending provider applications for admin review
     * Endpoint: GET /rest/v1/pending_approvals?status=eq.pending
     */
    public static void fetchPendingApprovals(QueryCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/pending_approvals" +
                "?status=eq.pending" +
                "&order=application_date.desc";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        HTTP.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchPendingApprovals failed: " + e.getMessage());
                MAIN.post(() -> callback.onError(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String body = response.body() != null ? response.body().string() : "[]";
                    response.close();

                    if (response.code() == 200) {
                        org.json.JSONArray result = new org.json.JSONArray(body);
                        MAIN.post(() -> callback.onSuccess(result));
                    } else {
                        Log.e(TAG, "fetchPendingApprovals HTTP " + response.code());
                        MAIN.post(() -> callback.onError("HTTP " + response.code()));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "fetchPendingApprovals parse error: " + e.getMessage());
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }

    /**
     * Approve a provider application
     * Updates: pending_approvals → status='approved'
     * Updates: users → role='provider'
     */
    public static void approveProvider(String applicationId, String userFirebaseUid,
                                       String adminUid, Callback2 callback) {
        try {
            // Update pending_approvals
            JSONObject updateApprovalBody = new JSONObject();
            updateApprovalBody.put("status", "approved");
            updateApprovalBody.put("reviewed_by_uid", adminUid);
            updateApprovalBody.put("reviewed_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));

            String approvalUrl = SUPABASE_URL + "/rest/v1/pending_approvals" +
                    "?id=eq." + applicationId;

            Request approvalRequest = new Request.Builder()
                    .url(approvalUrl)
                    .patch(RequestBody.create(updateApprovalBody.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(approvalRequest).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "approveProvider failed: " + e.getMessage());
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    if (code == 200 || code == 204) {
                        // Now update user role to 'provider'
                        updateUserRole(userFirebaseUid, "provider", callback);
                        logAdminAction(adminUid, "provider_approved", "pending_approvals",
                                applicationId, null);
                    } else {
                        Log.e(TAG, "approveProvider HTTP " + code);
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "approveProvider exception: " + e.getMessage());
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    /**
     * Reject a provider application
     */
    public static void rejectProvider(String applicationId, String rejectionReason,
                                      String adminUid, Callback2 callback) {
        try {
            JSONObject updateBody = new JSONObject();
            updateBody.put("status", "rejected");
            updateBody.put("rejection_reason", rejectionReason);
            updateBody.put("reviewed_by_uid", adminUid);
            updateBody.put("reviewed_at", new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss").format(new java.util.Date()));

            String url = SUPABASE_URL + "/rest/v1/pending_approvals" +
                    "?id=eq." + applicationId;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(updateBody.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "rejectProvider failed: " + e.getMessage());
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    if (code == 200 || code == 204) {
                        MAIN.post(callback::onSuccess);
                        logAdminAction(adminUid, "provider_rejected", "pending_approvals",
                                applicationId, rejectionReason);
                    } else {
                        Log.e(TAG, "rejectProvider HTTP " + code);
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "rejectProvider exception: " + e.getMessage());
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    /**
     * Update user role (used internally for provider approval, etc.)
     */
    private static void updateUserRole(String firebaseUid, String newRole, Callback2 callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("role", newRole);

            String url = SUPABASE_URL + "/rest/v1/users" +
                    "?firebase_uid=eq." + firebaseUid;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "updateUserRole failed: " + e.getMessage());
                    MAIN.post(() -> callback.onError(e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    if (code == 200 || code == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        Log.e(TAG, "updateUserRole HTTP " + code);
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "updateUserRole exception: " + e.getMessage());
            MAIN.post(() -> callback.onError(e.getMessage()));
        }
    }

    /**
     * Log admin actions for audit trail
     * Non-blocking: doesn't affect main operations
     */
    public static void logAdminAction(String adminUid, String actionType,
                                      String targetType, String targetId, String details) {
        try {
            JSONObject logBody = new JSONObject();
            logBody.put("admin_uid", adminUid);
            logBody.put("action_type", actionType);
            logBody.put("target_type", targetType);
            logBody.put("target_id", targetId);
            if (details != null) {
                logBody.put("details", new JSONObject(details));
            }

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/admin_logs")
                    .post(RequestBody.create(logBody.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "logAdminAction failed (non-blocking): " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();
                    if (code != 201) {
                        Log.w(TAG, "logAdminAction HTTP " + code + " (non-blocking)");
                    }
                }
            });

        } catch (Exception e) {
            Log.w(TAG, "logAdminAction exception (non-blocking): " + e.getMessage());
        }
    }
}
