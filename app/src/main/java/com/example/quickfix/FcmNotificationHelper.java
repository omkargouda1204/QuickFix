package com.example.quickfix;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * FcmNotificationHelper
 *
 * Sends push notifications through a Supabase Edge Function that holds
 * the Firebase Server Key securely server-side. The Android app never
 * has direct access to the Server Key.
 *
 * Architecture:
 *   Android App  →  Supabase Edge Function  →  FCM v1 API  →  Device
 *
 * Edge function URL is stored in supabase.properties as:
 *   SUPABASE_NOTIFY_FN_URL=https://<project>.supabase.co/functions/v1/send-notification
 *
 * How to deploy the Edge Function (one-time setup):
 *   1. Install Supabase CLI
 *   2. Run: supabase functions deploy send-notification
 *   3. Set secrets: supabase secrets set FIREBASE_SERVER_KEY=<your_key>
 *
 * The Edge Function source is in: supabase/functions/send-notification/index.ts
 */
public class FcmNotificationHelper {

    private static final String TAG = "FcmNotificationHelper";

    // Credentials injected from supabase.properties — never hardcoded
    private static final String SUPABASE_URL   = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY        = BuildConfig.SUPABASE_ANON_KEY;
    private static final String NOTIFY_FN_URL   = BuildConfig.SUPABASE_NOTIFY_FN_URL;

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Notify all admin users that a new provider application was submitted.
     * Called immediately after successful submitApplication().
     */
    public static void notifyAdminsNewApplication(
            String applicantName,
            String businessName,
            String category) {

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null) {
                    if (!AppSettings.isPushEnabled(appCtx) || !AppSettings.isAdminAlertsEnabled(appCtx)) {
                        Log.d(TAG, "notifyAdminsNewApplication suppressed by admin settings");
                        return;
                    }
                }

                // 1. Fetch all admin FCM tokens from Supabase
                List<String> adminTokens = fetchAdminFcmTokens();
                if (adminTokens.isEmpty()) {
                    Log.d(TAG, "notifyAdminsNewApplication: no admin tokens found");
                    return;
                }

                // 2. Send notification to each admin token
                String title = "New Provider Application";
                String body  = (applicantName != null && !applicantName.isEmpty()
                        ? applicantName : "A provider")
                        + " submitted an application"
                        + (businessName != null && !businessName.isEmpty()
                        ? " for " + businessName : "")
                        + ".";

                for (String token : adminTokens) {
                    sendViaEdgeFunction(token, title, body,
                            "provider_application", null);
                }

                // 3. Save in-app notification for all admins (legacy + admin table)
                saveInAppNotification(null, title, body, "provider_application");
                saveAdminNotification(title, body, "provider_application", null);

                Log.d(TAG, "notifyAdminsNewApplication: sent to " + adminTokens.size() + " admins");

            } catch (Exception e) {
                Log.w(TAG, "notifyAdminsNewApplication failed (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    /**
     * Notify the provider that their application was approved.
     * Called after successful approveApplication().
     *
     * @param providerFirebaseUid The firebase_uid of the approved provider.
     */
    public static void notifyProviderApproved(String providerFirebaseUid,
                                               String businessName) {
        if (providerFirebaseUid == null || providerFirebaseUid.isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    Log.d(TAG, "notifyProviderApproved suppressed by push_notifications=false");
                    return;
                }

                String token = fetchUserFcmToken(providerFirebaseUid);
                if (token == null || token.isEmpty()) {
                    Log.d(TAG, "notifyProviderApproved: no token for uid=" + providerFirebaseUid);
                    return;
                }

                String title = "🎉 Application Approved!";
                String body  = "Congratulations! Your provider application"
                        + (businessName != null && !businessName.isEmpty()
                        ? " for " + businessName : "")
                        + " has been approved. You can now start receiving bookings.";

                sendViaEdgeFunction(token, title, body,
                        "provider_approved", providerFirebaseUid);

                // Save in-app notification for the provider
                saveInAppNotification(providerFirebaseUid, title, body, "provider_approved");

                Log.d(TAG, "notifyProviderApproved: sent to uid=" + providerFirebaseUid);

            } catch (Exception e) {
                Log.w(TAG, "notifyProviderApproved failed (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    /**
     * Notify the provider that their application was rejected.
     * Called after successful rejectApplication().
     *
     * @param providerFirebaseUid The firebase_uid of the rejected provider.
     * @param rejectionReason     The admin-provided rejection reason.
     */
    public static void notifyProviderRejected(String providerFirebaseUid,
                                               String rejectionReason) {
        if (providerFirebaseUid == null || providerFirebaseUid.isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    Log.d(TAG, "notifyProviderRejected suppressed by push_notifications=false");
                    return;
                }

                String token = fetchUserFcmToken(providerFirebaseUid);
                if (token == null || token.isEmpty()) {
                    Log.d(TAG, "notifyProviderRejected: no token for uid=" + providerFirebaseUid);
                    return;
                }

                String title = "Application Status Update";
                String body  = "Your provider application was not approved at this time."
                        + (rejectionReason != null && !rejectionReason.isEmpty()
                        ? " Reason: " + rejectionReason : "")
                        + " You may reapply after 24 hours.";

                sendViaEdgeFunction(token, title, body,
                        "provider_rejected", providerFirebaseUid);

                // Save in-app notification for the provider
                saveInAppNotification(providerFirebaseUid, title, body, "provider_rejected");

                Log.d(TAG, "notifyProviderRejected: sent to uid=" + providerFirebaseUid);

            } catch (Exception e) {
                Log.w(TAG, "notifyProviderRejected failed (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    /**
     * Notify a provider that a new booking request has been received.
     * Called after a customer successfully creates a booking.
     *
     * @param providerFirebaseUid The firebase_uid of the provider.
     * @param customerName        Name of the customer who booked.
     * @param serviceName         The service/category that was booked.
     */
    public static void notifyProviderNewBooking(String providerFirebaseUid,
                                                 String customerName,
                                                 String serviceName) {
        if (providerFirebaseUid == null || providerFirebaseUid.isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    Log.d(TAG, "notifyProviderNewBooking suppressed by push_notifications=false");
                    return;
                }

                String token = fetchUserFcmToken(providerFirebaseUid);
                if (token == null || token.isEmpty()) {
                    Log.d(TAG, "notifyProviderNewBooking: no token for uid=" + providerFirebaseUid);
                    // Still save in-app notification even if push fails
                }

                String title = "📋 New Booking Request!";
                String body  = (customerName != null && !customerName.isEmpty()
                        ? customerName : "A customer")
                        + " has requested a booking"
                        + (serviceName != null && !serviceName.isEmpty()
                        ? " for " + serviceName : "")
                        + ". Tap to review and accept.";

                // Send push notification if token available
                if (token != null && !token.isEmpty()) {
                    sendViaEdgeFunction(token, title, body,
                            "booking_request", providerFirebaseUid);
                }

                // Save in-app notification for the provider
                saveInAppNotification(providerFirebaseUid, title, body, "booking_request");
                saveAdminNotification("New Booking", body, "booking", providerFirebaseUid);

                Log.d(TAG, "notifyProviderNewBooking: sent to uid=" + providerFirebaseUid);

            } catch (Exception e) {
                Log.w(TAG, "notifyProviderNewBooking failed (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    // ── Public single-token send (used by OfferManagement) ───────────────

    /**
     * Send a single FCM push notification to one device token for an offer.
     * Called from OfferManagementActivity for each user token found.
     */
    public static void sendOfferFcm(String deviceToken, String title, String body) {
        new Thread(() -> sendViaEdgeFunction(deviceToken, title, body, "offer", null)).start();
    }

    /**
     * System alert for admin panel timeline (e.g., maintenance mode changes).
     */
    public static void notifyAdminSystemAlert(String title, String message) {
        new Thread(() -> {
            try {
                saveAdminNotification(title, message, "system_alert", null);
            } catch (Exception e) {
                Log.w(TAG, "notifyAdminSystemAlert failed: " + e.getMessage());
            }
        }).start();
    }

    // ── In-App Notification Storage ───────────────────────────────────────

    /**
     * Stores a notification record in the `notifications` table so it appears
     * in the user's in-app notification list. userId = null = broadcast to all admins.
     */
    private static void saveInAppNotification(String userId, String title,
                                               String message, String type) {
        new Thread(() -> {
            try {
                JSONObject notif = new JSONObject();
                notif.put("id",      java.util.UUID.randomUUID().toString());
                notif.put("title",   title);
                notif.put("message", message);
                notif.put("type",    type != null ? type : "general");
                notif.put("is_read", false);
                // user_id = null means broadcast (visible to admin / all users)
                if (userId != null && !userId.isEmpty()) {
                    notif.put("user_id", userId);
                }
                // created_at handled by DB default

                try (Response resp = HTTP.newCall(new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/notifications")
                        .post(RequestBody.create(notif.toString(), JSON_TYPE))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build()).execute()) {
                    Log.d(TAG, "saveInAppNotification HTTP " + resp.code()
                            + " type=" + type + " userId=" + userId);
                }
            } catch (Exception e) {
                Log.w(TAG, "saveInAppNotification failed (non-fatal): " + e.getMessage());
            }
        }).start();
    }

    private static void saveAdminNotification(String title, String message, String type) {
        saveAdminNotification(title, message, type, null);
    }

    private static void saveAdminNotification(String title,
                                              String message,
                                              String type,
                                              String referenceId) {
        try {
            JSONObject notif = new JSONObject();
            notif.put("title", title != null ? title : "Notification");
            notif.put("message", message != null ? message : "");
            notif.put("type", type != null ? type : "general");
            notif.put("is_read", false);
            if (referenceId != null && !referenceId.trim().isEmpty()) {
                notif.put("reference_id", referenceId.trim());
            }

            try (Response resp = HTTP.newCall(new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/admin_notifications")
                    .post(RequestBody.create(notif.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build()).execute()) {
                String rb = resp.body() != null ? resp.body().string() : "";
                if (!resp.isSuccessful()) {
                    Log.e(TAG, "saveAdminNotification failed HTTP " + resp.code() + ": " + rb);
                } else {
                    Log.d(TAG, "saveAdminNotification ok type=" + type + " ref=" + referenceId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "saveAdminNotification exception", e);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    /**
     * Fetch FCM tokens for all users where is_admin = true.
     * Returns an empty list if no admins or network error.
     */
    private static List<String> fetchAdminFcmTokens() throws IOException {
        java.util.List<String> tokens = new java.util.ArrayList<>();

        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?is_admin=eq.true"
                + "&fcm_token=not.is.null"
                + "&select=fcm_token";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            Log.d(TAG, "fetchAdminFcmTokens HTTP " + resp.code() + " | " + body);

            if (resp.code() == 200) {
                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    String token = arr.getJSONObject(i).optString("fcm_token", "");
                    if (!token.isEmpty()) tokens.add(token);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchAdminFcmTokens exception: " + e.getMessage());
        }

        return tokens;
    }

    /**
     * Fetch the FCM token for a specific user by firebase_uid.
     * Returns null if not found or network error.
     */
    private static String fetchUserFcmToken(String firebaseUid) {
        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?firebase_uid=eq." + firebaseUid
                + "&fcm_token=not.is.null"
                + "&select=fcm_token"
                + "&limit=1";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            Log.d(TAG, "fetchUserFcmToken HTTP " + resp.code() + " uid=" + firebaseUid);

            if (resp.code() == 200) {
                JSONArray arr = new JSONArray(body);
                if (arr.length() > 0) {
                    return arr.getJSONObject(0).optString("fcm_token", null);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchUserFcmToken exception: " + e.getMessage());
        }

        return null;
    }

    /**
     * Calls the Supabase Edge Function to send a push notification.
     *
     * The Edge Function uses the Firebase Admin SDK / FCM v1 API with
     * the server key stored securely as a Supabase secret.
     *
     * Payload sent to edge function:
     * {
     *   "token":     "<device_fcm_token>",
     *   "title":     "<notification_title>",
     *   "body":      "<notification_body>",
     *   "data": {
     *     "type":    "<notification_type>",
     *     "uid":     "<optional_uid>"
     *   }
     * }
     */
    private static void sendViaEdgeFunction(String deviceToken,
                                             String title,
                                             String body,
                                             String type,
                                             String targetUid) {
        if (NOTIFY_FN_URL == null || NOTIFY_FN_URL.isEmpty()
                || NOTIFY_FN_URL.contains("YOUR_")) {
            Log.w(TAG, "SUPABASE_NOTIFY_FN_URL not configured — skipping push notification.\n"
                    + "Add SUPABASE_NOTIFY_FN_URL to supabase.properties and deploy the edge function.");
            return;
        }

        if (deviceToken == null || deviceToken.isEmpty()) {
            Log.w(TAG, "sendViaEdgeFunction: empty device token, skipping");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("type", type != null ? type : "general");
            if (targetUid != null && !targetUid.isEmpty()) data.put("uid", targetUid);

            JSONObject payload = new JSONObject();
            payload.put("token", deviceToken);
            payload.put("title", title);
            payload.put("body",  body);
            payload.put("data",  data);

            Request req = new Request.Builder()
                    .url(NOTIFY_FN_URL)
                    .post(RequestBody.create(payload.toString(), JSON_TYPE))
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .build();

            HTTP.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "sendViaEdgeFunction onFailure: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int    code    = response.code();
                    String respBody = response.body() != null ? response.body().string() : "";
                    response.close();
                    if (code == 200 || code == 201 || code == 204) {
                        Log.d(TAG, "sendViaEdgeFunction ✅ HTTP " + code
                                + " | type=" + type);
                    } else {
                        Log.w(TAG, "sendViaEdgeFunction ❌ HTTP " + code
                                + " | body=" + respBody);
                    }
                }
            });

        } catch (Exception e) {
            Log.w(TAG, "sendViaEdgeFunction exception: " + e.getMessage());
        }
    }

    public static void notifyAdminUserRegistration(String userName, String userId) {
        new Thread(() -> {
            String title = "New User Registered";
            String body = (userName != null && !userName.trim().isEmpty())
                    ? userName + " created an account"
                    : "A new user created an account";
            saveAdminNotification(title, body, "user_registration", userId);
        }).start();
    }

    public static void notifyAdminOfferCreated(String offerTitle, String offerId) {
        new Thread(() -> {
            String title = "New Offer Created";
            String body = (offerTitle != null && !offerTitle.trim().isEmpty())
                    ? "Offer created: " + offerTitle
                    : "Admin created a new offer";
            saveAdminNotification(title, body, "offer", offerId);
        }).start();
    }
}
