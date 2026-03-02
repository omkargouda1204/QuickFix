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
                body.put("name",                name.trim());
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
                String url = SUPABASE_URL + "/rest/v1/provider_applications"
                        + "?order=submitted_at.desc";
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
    //   1. PATCH provider_applications → status = approved
    //   2. INSERT provider_profiles row
    //   3. PATCH users → is_service_provider = true
    // ═══════════════════════════════════════════════════════════════════════
    public static void approveApplication(ProviderApplication app,
                                          String adminUid,
                                          Callback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new Date());

                // ── Step 1: Update provider_applications ──────────────────
                JSONObject patch = new JSONObject();
                patch.put("status",          "approved");
                patch.put("reviewed_by_uid", adminUid);
                patch.put("reviewed_at",     timestamp);

                Request step1 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_applications?id=eq." + app.id)
                        .patch(RequestBody.create(patch.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r1 = HTTP.newCall(step1).execute()) {
                    int c1 = r1.code();
                    Log.d(TAG, "approveApplication step1 HTTP " + c1);
                    if (c1 != 200 && c1 != 204) {
                        String err = r1.body() != null ? r1.body().string() : "";
                        MAIN.post(() -> callback.onError("Step1 failed HTTP " + c1 + ": " + err));
                        return;
                    }
                }

                // ── Step 2: Insert provider_profiles ─────────────────────
                JSONObject profile = new JSONObject();
                profile.put("firebase_uid",       app.firebaseUid);
                profile.put("application_id",     app.id);
                profile.put("business_name",      app.businessName);
                profile.put("owner_name",         app.name);
                profile.put("phone",              app.phone);
                profile.put("email",              app.email);
                profile.put("category",           app.category);
                profile.put("city",               app.city);
                profile.put("address",            app.address != null ? app.address : "");
                profile.put("experience",         app.experience);
                profile.put("description",        app.description);
                profile.put("is_active",          true);
                profile.put("is_approved",        true);
                profile.put("approved_at",        timestamp);
                if (app.businessImageUrl != null && !app.businessImageUrl.isEmpty())
                    profile.put("business_image_url", app.businessImageUrl);
                if (app.idProofUrl != null && !app.idProofUrl.isEmpty())
                    profile.put("id_proof_url", app.idProofUrl);

                Request step2 = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/provider_profiles")
                        .post(RequestBody.create(profile.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "resolution=merge-duplicates,return=minimal")
                        .build();

                try (Response r2 = HTTP.newCall(step2).execute()) {
                    int c2 = r2.code();
                    Log.d(TAG, "approveApplication step2 HTTP " + c2);
                    if (c2 != 200 && c2 != 201 && c2 != 204) {
                        String err = r2.body() != null ? r2.body().string() : "";
                        Log.w(TAG, "provider_profiles insert warning (continuing): " + err);
                    }
                }

                // ── Step 3: Update users role ─────────────────────────────
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
                    int c3 = r3.code();
                    Log.d(TAG, "approveApplication step3 HTTP " + c3);
                    if (c3 == 200 || c3 == 204) {
                        MAIN.post(callback::onSuccess);
                    } else {
                        String err = r3.body() != null ? r3.body().string() : "";
                        MAIN.post(() -> callback.onError("Step3 failed HTTP " + c3 + ": " + err));
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
                body.put("reviewed_by_uid",  adminUid);
                body.put("reviewed_at",      timestamp);

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
                String url = SUPABASE_URL + "/rest/v1/provider_applications"
                        + "?select=*&status=eq.pending&order=created_at.desc";
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

    // Approve/Reject methods to be added...
}
