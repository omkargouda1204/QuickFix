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

                boolean autoApprove = false;
                try {
                    QuickFixApp app = QuickFixApp.getInstance();
                    if (app != null) {
                        autoApprove = AdminSettingsRepository.isAutoApproveProvidersEnabled(app);
                    }
                } catch (Exception ignored) {
                    autoApprove = false;
                }

                JSONObject body = new JSONObject();
                body.put("firebase_uid",        firebaseUid);
                body.put("user_name",           name != null ? name.trim() : "");
                body.put("user_email",          email != null ? email.trim() : "");
                body.put("user_phone",          phone != null ? phone.trim() : "");
                body.put("business_name",       businessName != null ? businessName.trim() : "");
                body.put("category",            category != null ? category.trim() : "");
                body.put("description",         description != null ? description.trim() : "");
                body.put("address",             address != null ? address.trim() : "");
                body.put("city",                city != null ? city.trim() : "");
                int expYrs = 0;
                try { expYrs = Integer.parseInt(experience != null ? experience.trim() : "0"); } catch (Exception ignored) {}
                body.put("experience_yrs",      expYrs);
                body.put("terms_accepted",      true);
                body.put("status",              autoApprove ? "approved" : "pending");
                body.put("submitted_at",        timestamp);
                if (autoApprove) {
                    body.put("approved_at", timestamp);
                    body.put("reviewed_at", timestamp);
                    body.put("reviewed_by_uid", "system_auto");
                }
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
                        .addHeader("Prefer",        "return=representation")
                        .build();

                try (Response r = HTTP.newCall(insertReq).execute()) {
                    int code = r.code();
                    String respBody = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "submitApplication HTTP " + code + " | " + respBody);
                    if (code == 200 || code == 201 || code == 204) {
                        if (autoApprove) {
                            // Auto-approve means role is promoted immediately.
                            patchUserAsProviderSync(firebaseUid);
                        } else {
                            String eventKey = extractInsertedApplicationId(respBody);
                            if (eventKey.trim().isEmpty()) {
                                eventKey = (firebaseUid != null ? firebaseUid.trim() : "") + ":pending";
                            }
                            FcmNotificationHelper.notifyAdminsNewApplication(
                                    name, businessName, category, eventKey);
                        }
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

    private static void patchUserAsProviderSync(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) return;
        try {
            JSONObject patch = new JSONObject();
            patch.put("is_service_provider", true);

            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + firebaseUid)
                    .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            try (Response response = HTTP.newCall(req).execute()) {
                Log.d(TAG, "patchUserAsProviderSync HTTP " + response.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "patchUserAsProviderSync non-fatal: " + e.getMessage());
        }
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
    //  APPROVE APPLICATION
    //   Strategy: use provider_applications.id as provider_profiles.id
    //   This avoids the user_id NOT NULL constraint entirely.
    //
    //   Steps:
    //     1. Fetch full application row from DB (fill any gaps in `app`)
    //     2. INSERT provider_profiles using id = app.id (no user_id needed)
    //     3. PATCH provider_applications → status = 'approved'
    //     4. PATCH users → is_service_provider = true (non-fatal)
    // ═══════════════════════════════════════════════════════════════════════
    public static void approveApplication(ProviderApplication app,
                                          String adminUid,
                                          Callback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

                // ── Step 1: Re-fetch full application row from DB ─────────
                // Ensures all fields (business_name, owner_name, email, etc.) are populated
                // even if the `app` object was built from a partial intent.
                if (app.id != null && !app.id.isEmpty()) {
                    String fetchUrl = SUPABASE_URL
                            + "/rest/v1/provider_applications"
                            + "?id=eq." + app.id
                            + "&select=*&limit=1";
                    Request fetchReq = new Request.Builder()
                            .url(fetchUrl).get()
                            .addHeader("apikey",        ANON_KEY)
                            .addHeader("Authorization", "Bearer " + ANON_KEY)
                            .addHeader("Accept",        "application/json")
                            .build();
                    try (Response fetchResp = HTTP.newCall(fetchReq).execute()) {
                        String fetchBody = fetchResp.body() != null
                                ? fetchResp.body().string() : "[]";
                        Log.d(TAG, "approveApplication prefetch HTTP "
                                + fetchResp.code() + " | " + fetchBody);
                        if (fetchResp.code() == 200) {
                            JSONArray arr = new JSONArray(fetchBody);
                            if (arr.length() > 0) {
                                // Merge DB values into app object (fill any gaps)
                                ProviderApplication dbApp =
                                        ProviderApplication.fromJson(arr.getJSONObject(0));
                                if (app.firebaseUid == null || app.firebaseUid.isEmpty())
                                    app.firebaseUid = dbApp.firebaseUid;
                                if (app.name == null || app.name.isEmpty())
                                    app.name = dbApp.name;
                                if (app.email == null || app.email.isEmpty())
                                    app.email = dbApp.email;
                                if (app.phone == null || app.phone.isEmpty())
                                    app.phone = dbApp.phone;
                                if (app.businessName == null || app.businessName.isEmpty())
                                    app.businessName = dbApp.businessName;
                                if (app.category == null || app.category.isEmpty())
                                    app.category = dbApp.category;
                                if (app.city == null || app.city.isEmpty())
                                    app.city = dbApp.city;
                                if (app.address == null || app.address.isEmpty())
                                    app.address = dbApp.address;
                                if (app.description == null || app.description.isEmpty())
                                    app.description = dbApp.description;
                                if (app.experience == null || app.experience.isEmpty())
                                    app.experience = dbApp.experience;
                                if (app.businessImageUrl == null || app.businessImageUrl.isEmpty())
                                    app.businessImageUrl = dbApp.businessImageUrl;
                                if (app.idProofUrl == null || app.idProofUrl.isEmpty())
                                    app.idProofUrl = dbApp.idProofUrl;
                            }
                        }
                    }
                }

                // ── Step 2: INSERT provider_profiles ─────────────────────
                // Use app.id as the provider_profiles.id — avoids user_id NOT NULL entirely.
                JSONObject profile = new JSONObject();
                profile.put("id", app.id);          // provider_profiles.id = application id

                // firebase_uid for cross-reference (nullable in profiles)
                putSafe(profile, "firebase_uid",  app.firebaseUid);
                putSafe(profile, "provider_uid",  app.firebaseUid); // legacy alias

                // Owner / contact info
                String ownerName = (app.name != null && !app.name.isEmpty()) ? app.name : "";
                String email     = (app.email != null && !app.email.isEmpty()) ? app.email : "";
                putSafe(profile, "owner_name",    ownerName);
                putSafe(profile, "email",         email);
                putSafe(profile, "phone",         app.phone);

                // Business info
                putSafe(profile, "business_name", app.businessName);
                putSafe(profile, "category",      app.category);
                putSafe(profile, "city",          app.city);
                putSafe(profile, "address",       app.address   != null ? app.address   : "");
                putSafe(profile, "description",   app.description != null ? app.description : "");

                // Experience
                String exp = (app.experience != null) ? app.experience.trim() : "0";
                putSafe(profile, "experience",    exp.isEmpty() ? "0" : exp);

                // Status & timestamps
                profile.put("status",       "active");
                profile.put("is_active",    true);
                profile.put("is_approved",  true);
                profile.put("approved_at",  timestamp);
                profile.put("created_at",   timestamp);

                // Media (optional)
                if (app.businessImageUrl != null && !app.businessImageUrl.isEmpty())
                    profile.put("business_image_url", app.businessImageUrl);
                if (app.idProofUrl != null && !app.idProofUrl.isEmpty())
                    profile.put("id_proof_url", app.idProofUrl);

                // Back-reference
                profile.put("application_id", app.id);

                Log.d(TAG, "approveApplication step2 INSERT payload: " + profile);

                Request insertReq = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_profiles")
                        .post(RequestBody.create(profile.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r1 = HTTP.newCall(insertReq).execute()) {
                    int c1 = r1.code();
                    String b1 = r1.body() != null ? r1.body().string() : "";
                    Log.d(TAG, "approveApplication INSERT HTTP " + c1 + " | " + b1);

                    if (c1 != 200 && c1 != 201 && c1 != 204) {
                        // ── Fallback: minimal safe insert (only guaranteed columns) ─
                        JSONObject minProfile = new JSONObject();
                        minProfile.put("id",            app.id);
                        putSafe(minProfile, "firebase_uid",  app.firebaseUid);
                        putSafe(minProfile, "business_name", app.businessName);
                        putSafe(minProfile, "owner_name",    ownerName);
                        putSafe(minProfile, "email",         email);
                        putSafe(minProfile, "category",      app.category);
                        putSafe(minProfile, "city",          app.city);
                        minProfile.put("status",      "active");
                        minProfile.put("approved_at", timestamp);
                        Log.d(TAG, "approveApplication fallback INSERT: " + minProfile);

                        Request fallbackReq = new Request.Builder()
                                .url(SUPABASE_URL + "/rest/v1/provider_profiles")
                                .post(RequestBody.create(minProfile.toString(), JSON_TYPE))
                                .addHeader("apikey",        ANON_KEY)
                                .addHeader("Authorization", "Bearer " + ANON_KEY)
                                .addHeader("Content-Type",  "application/json")
                                .addHeader("Prefer",        "return=minimal")
                                .build();

                        try (Response r1b = HTTP.newCall(fallbackReq).execute()) {
                            int c1b = r1b.code();
                            String b1b = r1b.body() != null ? r1b.body().string() : "";
                            Log.d(TAG, "approveApplication fallback HTTP " + c1b + " | " + b1b);
                            if (c1b != 200 && c1b != 201 && c1b != 204) {
                                final String err = "Could not create provider profile HTTP "
                                        + c1b + ": " + b1b;
                                MAIN.post(() -> callback.onError(err));
                                return;
                            }
                        }
                    }
                }

                // ── Step 3: Update provider_applications → approved ───────
                JSONObject statusPatch = new JSONObject();
                statusPatch.put("status",       "approved");
                statusPatch.put("approved_at",  timestamp);
                statusPatch.put("reviewed_at",  timestamp);
                if (adminUid != null && !adminUid.isEmpty())
                    statusPatch.put("reviewed_by_uid", adminUid);

                Request step3Req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + app.id)
                        .patch(RequestBody.create(statusPatch.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r2 = HTTP.newCall(step3Req).execute()) {
                    int c2 = r2.code();
                    String b2 = r2.body() != null ? r2.body().string() : "";
                    Log.d(TAG, "approveApplication status PATCH HTTP " + c2 + " | " + b2);
                    if (c2 != 200 && c2 != 204) {
                        MAIN.post(() -> callback.onError(
                                "Profile created but status update failed HTTP " + c2 + ": " + b2));
                        return;
                    }
                }

                // ── Step 4: Update users table (non-fatal) ────────────────
                if (app.firebaseUid != null && !app.firebaseUid.isEmpty()) {
                    try {
                        JSONObject userPatch = new JSONObject();
                        userPatch.put("is_service_provider", true);
                        userPatch.put("is_user",             true);
                        Request userReq = new Request.Builder()
                                .url(SUPABASE_URL + "/rest/v1/users?firebase_uid=eq."
                                        + app.firebaseUid)
                                .patch(RequestBody.create(userPatch.toString(), JSON_TYPE))
                                .addHeader("apikey",        ANON_KEY)
                                .addHeader("Authorization", "Bearer " + ANON_KEY)
                                .addHeader("Content-Type",  "application/json")
                                .addHeader("Prefer",        "return=minimal")
                                .build();
                        try (Response r3 = HTTP.newCall(userReq).execute()) {
                            Log.d(TAG, "approveApplication user PATCH HTTP " + r3.code());
                        }
                    } catch (Exception ignored) { /* non-fatal */ }
                }

                // ── Step 5: Notify provider their application was approved ─
                FcmNotificationHelper.notifyProviderApproved(app.firebaseUid, app.businessName);

                MAIN.post(callback::onSuccess);

            } catch (IOException e) {
                Log.e(TAG, "approveApplication IOException: " + e.getMessage());
                MAIN.post(() -> callback.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "approveApplication Exception: " + e.getMessage());
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

                // ── Fetch applicant firebase_uid for notification ──────────
                // We need it to look up the FCM token after rejection.
                final String[] applicantUid = {null};
                try {
                    String fetchUrl = SUPABASE_URL
                            + "/rest/v1/provider_applications"
                            + "?id=eq." + applicationId
                            + "&select=firebase_uid&limit=1";
                    try (Response fr = HTTP.newCall(new Request.Builder()
                            .url(fetchUrl).get()
                            .addHeader("apikey",        ANON_KEY)
                            .addHeader("Authorization", "Bearer " + ANON_KEY)
                            .addHeader("Accept",        "application/json")
                            .build()).execute()) {
                        String fb = fr.body() != null ? fr.body().string() : "[]";
                        if (fr.code() == 200) {
                            JSONArray arr = new JSONArray(fb);
                            if (arr.length() > 0)
                                applicantUid[0] = arr.getJSONObject(0).optString("firebase_uid", null);
                        }
                    }
                } catch (Exception ignored) { /* non-fatal */ }

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
                        // ── Notify provider of rejection ─────────────────
                        if (applicantUid[0] != null && !applicantUid[0].isEmpty()) {
                            FcmNotificationHelper.notifyProviderRejected(
                                    applicantUid[0], rejectionReason);
                        }
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

    private static String extractInsertedApplicationId(String responseBody) {
        try {
            JSONArray arr = new JSONArray(responseBody != null ? responseBody : "[]");
            if (arr.length() == 0) return "";
            JSONObject first = arr.getJSONObject(0);
            return first.optString("id", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    // Approve/Reject methods to be added...
}
