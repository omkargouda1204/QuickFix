package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ProviderApplicationRepository — All Supabase REST calls for the
 * Provider Application flow.
 *
 * Tables involved:
 *   • provider_applications  — submission, status updates
 *   • provider_profiles       — created when admin approves
 *   • users                   — is_service_provider patched on approve
 *
 * SQL to add columns (run once in Supabase SQL editor):
 *   ALTER TABLE provider_applications
 *     ADD COLUMN IF NOT EXISTS address TEXT,
 *     ADD COLUMN IF NOT EXISTS business_image_url TEXT,
 *     ADD COLUMN IF NOT EXISTS id_proof_url TEXT;
 *
 *   ALTER TABLE provider_profiles
 *     ADD COLUMN IF NOT EXISTS address TEXT,
 *     ADD COLUMN IF NOT EXISTS business_image_url TEXT,
 *     ADD COLUMN IF NOT EXISTS id_proof_url TEXT;
 */
public class ProviderApplicationRepository {

    private static final String TAG = "ProvAppRepo";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Callbacks ─────────────────────────────────────────────────────────

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public interface ApplicationListCallback {
        void onSuccess(List<ProviderApplication> applications);
        void onError(String message);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SUBMIT APPLICATION
    // ═══════════════════════════════════════════════════════════════════════
    /**
     * Insert a new row in provider_applications.
     * Checks for duplicate (pending/approved) first.
     * Includes address, businessImageUrl, idProofUrl.
     */
    public static void submitApplication(
            String firebaseUid,
            String name,
            String email,
            String phone,
            String businessName,
            String category,
            String description,
            String address,
            String city,
            String experience,
            String businessImageUrl,
            String idProofUrl,
            Callback callback) {

        new Thread(() -> {
            try {
                // ── 1. Duplicate check ────────────────────────────────────
                String checkUrl = SUPABASE_URL
                        + "/rest/v1/provider_applications"
                        + "?firebase_uid=eq." + firebaseUid
                        + "&status=in.(pending,approved)"
                        + "&select=id&limit=1";
                Request checkReq = new Request.Builder()
                        .url(checkUrl).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response checkResp = HTTP.newCall(checkReq).execute()) {
                    String checkBody = checkResp.body() != null
                            ? checkResp.body().string() : "[]";
                    if (checkResp.code() == 200) {
                        JSONArray existing = new JSONArray(checkBody);
                        if (existing.length() > 0) {
                            MAIN.post(() -> callback.onError("DUPLICATE"));
                            return;
                        }
                    }
                }

                // ── 2. Insert new application ─────────────────────────────
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

                JSONObject body = new JSONObject();
                body.put("firebase_uid",        firebaseUid);
                body.put("owner_name",          name.trim());   // DB column: owner_name
                body.put("email",               email.trim());
                body.put("phone",               phone.trim());
                body.put("business_name",       businessName.trim());
                body.put("category",            category.trim());
                body.put("description",         description.trim());
                body.put("address",             address != null ? address.trim() : "");
                body.put("city",                city.trim());
                body.put("experience",          experience.trim());
                body.put("status",              "pending");
                body.put("submitted_at",        timestamp);
                if (businessImageUrl != null && !businessImageUrl.isEmpty())
                    body.put("business_image_url", businessImageUrl);
                if (idProofUrl != null && !idProofUrl.isEmpty())
                    body.put("id_proof_url", idProofUrl);

                Request insertReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_applications")
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(insertReq).execute()) {
                    int code = r.code();
                    String respBody = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "submitApplication HTTP " + code + " | " + respBody);
                    if (code == 200 || code == 201 || code == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code + ": " + respBody));
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "submitApplication IOException: " + e.getMessage());
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                Log.e(TAG, "submitApplication Exception: " + e.getMessage());
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FETCH ALL APPLICATIONS (admin)  — filter by status
    // ═══════════════════════════════════════════════════════════════════════
    public static void fetchApplications(String statusFilter, ApplicationListCallback callback) {
        new Thread(() -> {
            try {
                // Use select=* to avoid HTTP 400 from missing/renamed columns
                String url = SUPABASE_URL + "/rest/v1/provider_applications"
                        + "?select=*"
                        + "&order=submitted_at.desc";
                if (statusFilter != null && !statusFilter.isEmpty()
                        && !statusFilter.equalsIgnoreCase("all")) {
                    url += "&status=eq." + statusFilter;
                }

                Request req = new Request.Builder()
                        .url(url).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    int code = r.code();
                    Log.d(TAG, "fetchApplications HTTP " + code);

                    if (code == 200) {
                        JSONArray arr = new JSONArray(body);
                        List<ProviderApplication> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            list.add(ProviderApplication.fromJson(arr.getJSONObject(i)));
                        }
                        MAIN.post(() -> callback.onSuccess(list));
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  APPROVE APPLICATION  (3-step chain)
    //   1. INSERT provider_profiles row (upsert on firebase_uid)
    //   2. PATCH provider_applications → status = approved
    //   3. PATCH users → is_service_provider = true
    // ═══════════════════════════════════════════════════════════════════════
    public static void approveApplication(ProviderApplication app,
                                          String adminUid,
                                          Callback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

                // ── Step 1: Insert/upsert provider_profiles ───────────────
                // Only include columns that definitely exist in provider_profiles.
                // Handle both naming conventions (user_name / owner_name).
                JSONObject profile = new JSONObject();

                // firebase_uid is the unique key for upsert
                putSafe(profile, "firebase_uid",   app.firebaseUid);

                // Support both naming conventions
                String ownerName = app.name != null && !app.name.isEmpty() ? app.name : "";
                putSafe(profile, "owner_name",     ownerName);
                putSafe(profile, "user_name",      ownerName);   // legacy column alias

                String email = app.email != null && !app.email.isEmpty() ? app.email : "";
                putSafe(profile, "email",          email);
                putSafe(profile, "user_email",     email);       // legacy column alias

                putSafe(profile, "business_name",  app.businessName);
                putSafe(profile, "category",       app.category);
                putSafe(profile, "phone",          app.phone);
                putSafe(profile, "city",           app.city);

                String address = app.address != null ? app.address : "";
                putSafe(profile, "address",        address);

                // experience may be stored as integer (experience_yrs) or string
                String exp = app.experience != null ? app.experience.trim() : "0";
                putSafe(profile, "experience",     exp);
                try { profile.put("experience_yrs", Integer.parseInt(exp)); }
                catch (Exception ignored) { profile.put("experience_yrs", 0); }

                putSafe(profile, "description",    app.description != null ? app.description : "");
                profile.put("status",              "active");
                profile.put("is_active",           true);
                profile.put("is_approved",         true);
                profile.put("approved_at",         timestamp);
                profile.put("created_at",          timestamp);

                if (app.businessImageUrl != null && !app.businessImageUrl.isEmpty())
                    profile.put("business_image_url", app.businessImageUrl);
                if (app.idProofUrl != null && !app.idProofUrl.isEmpty())
                    profile.put("id_proof_url", app.idProofUrl);
                if (app.id != null && !app.id.isEmpty())
                    profile.put("application_id",  app.id);

                Log.d(TAG, "approveApplication step1 profile payload: " + profile);

                Request step1 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_profiles")
                        .post(RequestBody.create(profile.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "resolution=merge-duplicates,return=representation")
                        .build();

                try (Response r1 = HTTP.newCall(step1).execute()) {
                    int c1 = r1.code();
                    String body1 = r1.body() != null ? r1.body().string() : "";
                    Log.d(TAG, "approveApplication step1 HTTP " + c1 + " | " + body1);

                    if (c1 != 200 && c1 != 201 && c1 != 204) {
                        // Attempt minimal insert with only guaranteed-safe columns
                        JSONObject minProfile = new JSONObject();
                        putSafe(minProfile, "firebase_uid",  app.firebaseUid);
                        putSafe(minProfile, "business_name", app.businessName);
                        putSafe(minProfile, "email",         email);
                        putSafe(minProfile, "category",      app.category);
                        putSafe(minProfile, "city",          app.city);
                        minProfile.put("status",             "active");
                        minProfile.put("is_active",          true);
                        minProfile.put("approved_at",        timestamp);

                        Log.d(TAG, "approveApplication step1 retry minimal: " + minProfile);

                        Request step1b = new Request.Builder()
                                .url(SUPABASE_URL + "/rest/v1/provider_profiles")
                                .post(RequestBody.create(minProfile.toString(), JSON_TYPE))
                                .addHeader("apikey",        ANON_KEY)
                                .addHeader("Authorization", "Bearer " + ANON_KEY)
                                .addHeader("Content-Type",  "application/json")
                                .addHeader("Prefer",        "resolution=merge-duplicates,return=minimal")
                                .build();

                        try (Response r1b = HTTP.newCall(step1b).execute()) {
                            int c1b = r1b.code();
                            String body1b = r1b.body() != null ? r1b.body().string() : "";
                            Log.d(TAG, "approveApplication step1 retry HTTP " + c1b + " | " + body1b);
                            if (c1b != 200 && c1b != 201 && c1b != 204) {
                                final String errMsg = "Could not create provider profile HTTP " + c1b + " " + body1b;
                                MAIN.post(() -> callback.onError(errMsg));
                                return;
                            }
                        }
                    }
                }

                // ── Step 2: Update provider_applications status ───────────
                JSONObject patch = new JSONObject();
                patch.put("status",          "approved");
                patch.put("reviewed_at",     timestamp);
                if (adminUid != null && !adminUid.isEmpty())
                    patch.put("reviewed_by_uid", adminUid);

                Request step2 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + app.id)
                        .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r2 = HTTP.newCall(step2).execute()) {
                    int c2 = r2.code();
                    String body2 = r2.body() != null ? r2.body().string() : "";
                    Log.d(TAG, "approveApplication step2 HTTP " + c2 + " | " + body2);
                    if (c2 != 200 && c2 != 204) {
                        MAIN.post(() -> callback.onError("Status update failed HTTP " + c2 + ": " + body2));
                        return;
                    }
                }

                // ── Step 3: Update users role ─────────────────────────────
                if (app.firebaseUid != null && !app.firebaseUid.isEmpty()) {
                    JSONObject userPatch = new JSONObject();
                    userPatch.put("is_service_provider", true);
                    userPatch.put("is_user",             true);

                    Request step3 = new Request.Builder()
                            .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + app.firebaseUid)
                            .patch(RequestBody.create(userPatch.toString(), JSON_TYPE))
                            .addHeader("apikey",        ANON_KEY)
                            .addHeader("Authorization", "Bearer " + ANON_KEY)
                            .addHeader("Content-Type",  "application/json")
                            .addHeader("Prefer",        "return=minimal")
                            .build();

                    try (Response r3 = HTTP.newCall(step3).execute()) {
                        Log.d(TAG, "approveApplication step3 HTTP " + r3.code());
                        // Non-fatal — approval succeeded even if user role patch fails
                    }
                }

                MAIN.post(callback::onSuccess);

            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR: " + e.getMessage()));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    /** Safely puts a non-null, non-empty string into a JSONObject */
    private static void putSafe(JSONObject obj, String key, String value) {
        try {
            if (value != null && !value.isEmpty()) {
                obj.put(key, value);
            }
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REJECT APPLICATION
    // ═══════════════════════════════════════════════════════════════════════
    public static void rejectApplication(String applicationId,
                                         String rejectionReason,
                                         String adminUid,
                                         Callback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

                JSONObject body = new JSONObject();
                body.put("status",           "rejected");
                body.put("rejection_reason", rejectionReason);
                body.put("reviewed_at",      timestamp);
                // Store rejected_at for 24-hour reapply countdown
                body.put("rejected_at",      timestamp);
                if (adminUid != null && !adminUid.isEmpty())
                    body.put("reviewed_by_uid",  adminUid);

                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + applicationId)
                        .patch(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    int code = r.code();
                    Log.d(TAG, "rejectApplication HTTP " + code);
                    if (code == 200 || code == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        String err = r.body() != null ? r.body().string() : "";
                        MAIN.post(() -> callback.onError("HTTP " + code + ": " + err));
                    }
                }

            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CHECK EXISTING APPLICATION STATUS (for user)
    //  Returns the latest application row for the user (all fields).
    // ═══════════════════════════════════════════════════════════════════════
    public interface StatusCallback {
        void onFound(String status);
        void onNotFound();
        void onError(String message);
    }

    public interface ApplicationCallback {
        void onFound(ProviderApplication application);
        void onNotFound();
        void onError(String message);
    }

    public static void checkApplicationStatus(String firebaseUid, StatusCallback callback) {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL
                        + "/rest/v1/provider_applications"
                        + "?firebase_uid=eq." + firebaseUid
                        + "&order=submitted_at.desc"
                        + "&select=status,submitted_at"
                        + "&limit=1";

                Request req = new Request.Builder()
                        .url(url).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    int code = r.code();
                    if (code == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            String status = arr.getJSONObject(0).optString("status", "pending");
                            MAIN.post(() -> callback.onFound(status));
                        } else {
                            MAIN.post(callback::onNotFound);
                        }
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    /** Fetch the full latest application row for a user (for status screen) */
    public static void fetchLatestApplication(String firebaseUid, ApplicationCallback callback) {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL
                        + "/rest/v1/provider_applications"
                        + "?firebase_uid=eq." + firebaseUid
                        + "&order=submitted_at.desc"
                        + "&limit=1";

                Request req = new Request.Builder()
                        .url(url).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    int code = r.code();
                    Log.d(TAG, "fetchLatestApplication HTTP " + code);
                    if (code == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            ProviderApplication app = ProviderApplication.fromJson(arr.getJSONObject(0));
                            MAIN.post(() -> callback.onFound(app));
                        } else {
                            MAIN.post(callback::onNotFound);
                        }
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FETCH PENDING APPLICATIONS (admin)
    // ═══════════════════════════════════════════════════════════════════════
    public static void fetchPendingApplications(ApplicationListCallback cb) {
        new Thread(() -> {
            try {
                // Use select=* and put filter params correctly
                String url = SUPABASE_URL + "/rest/v1/provider_applications"
                        + "?select=*&status=eq.pending&order=submitted_at.desc";
                try (Response r = HTTP.newCall(new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build()).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        List<ProviderApplication> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++)
                            list.add(ProviderApplication.fromJson(arr.getJSONObject(i)));
                        MAIN.post(() -> cb.onSuccess(list));
                    } else {
                        MAIN.post(() -> cb.onError("HTTP " + r.code() + ": " + body));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CHECK IF EMAIL ALREADY EXISTS IN provider_profiles (one-provider rule)
    // ═══════════════════════════════════════════════════════════════════════
    public interface ProviderExistsCallback {
        void onExists();        // email already has an approved provider profile
        void onNotExists();     // email is free to apply
        void onError(String msg);
    }

    public static void checkProviderByEmail(String email, ProviderExistsCallback callback) {
        new Thread(() -> {
            try {
                String enc = java.net.URLEncoder.encode(email, "UTF-8");
                // Check both email columns for compatibility
                String url = SUPABASE_URL + "/rest/v1/provider_profiles"
                        + "?or=(email.eq." + enc + ",user_email.eq." + enc + ")"
                        + "&select=id&limit=1";
                Request req = new Request.Builder().url(url).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    String b = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(b);
                        if (arr.length() > 0) MAIN.post(callback::onExists);
                        else                  MAIN.post(callback::onNotExists);
                    } else {
                        MAIN.post(callback::onNotExists); // non-fatal: allow form to open
                    }
                }
            } catch (Exception e) {
                MAIN.post(callback::onNotExists); // non-fatal
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FETCH FULL LATEST APPLICATION (for status page — all fields + dates)
    // ═══════════════════════════════════════════════════════════════════════
    public static void fetchApplicationFull(String firebaseUid, ApplicationCallback callback) {
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/provider_applications"
                        + "?firebase_uid=eq." + firebaseUid
                        + "&select=*&order=submitted_at.desc&limit=1";
                Request req = new Request.Builder().url(url).get()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    int code = r.code();
                    Log.d(TAG, "fetchApplicationFull HTTP " + code);
                    if (code == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            ProviderApplication app = ProviderApplication.fromJson(arr.getJSONObject(0));
                            MAIN.post(() -> callback.onFound(app));
                        } else {
                            MAIN.post(callback::onNotFound);
                        }
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code));
                    }
                }
            } catch (IOException e) {
                MAIN.post(() -> callback.onError("NETWORK_ERROR"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // Approve/Reject methods to be added...
}
