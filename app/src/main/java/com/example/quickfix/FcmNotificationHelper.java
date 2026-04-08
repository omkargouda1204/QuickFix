package com.example.quickfix;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
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
public final class FcmNotificationHelper {

    private static final String TAG = "FcmNotificationHelper";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String NOTIFY_FN_URL = BuildConfig.SUPABASE_NOTIFY_FN_URL;

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();

    private FcmNotificationHelper() {
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Notify all admin users that a new provider application was submitted.
     * Called immediately after successful submitApplication().
     */
    public static void notifyAdminsNewApplication(
            String applicantName,
            String businessName,
            String category) {

        String safeApplicant = trimToEmpty(applicantName);
        String safeBusiness = trimToEmpty(businessName);
        String safeCategory = trimToEmpty(category);

        String body = (safeApplicant.isEmpty() ? "A provider" : safeApplicant)
                + " submitted an application"
                + (safeBusiness.isEmpty() ? "" : " for " + safeBusiness)
                + (safeCategory.isEmpty() ? "" : " (" + safeCategory + ")")
                + ".";

        notifyAdminsEvent("New Provider Application", body, "provider_application", null);
    }

    /**
     * Notify the provider that their application was approved.
     * Called after successful approveApplication().
     *
     * @param providerFirebaseUid The firebase_uid of the approved provider.
     */
    public static void notifyProviderApproved(String providerFirebaseUid,
                                               String businessName) {
        notifyProviderEvent(
                providerFirebaseUid,
                "Application Approved",
                "Your provider application"
                        + (trimToEmpty(businessName).isEmpty() ? "" : " for " + businessName)
                        + " has been approved.",
                "provider_status",
                null
        );
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
        String reason = trimToEmpty(rejectionReason);
        notifyProviderEvent(
                providerFirebaseUid,
                "Application Rejected",
                reason.isEmpty()
                        ? "Your provider application was rejected."
                        : "Your provider application was rejected. Reason: " + reason,
                "provider_status",
                null
        );
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
        String body = (trimToEmpty(customerName).isEmpty() ? "A customer" : customerName)
                + " requested"
                + (trimToEmpty(serviceName).isEmpty() ? " a service." : " " + serviceName + ".");

        notifyProviderEvent(
                providerFirebaseUid,
                "New Booking",
                body,
                "booking",
                null
        );
    }

    /**
     * Notify multiple nearby providers that an emergency request was created.
     */
    public static void notifyEmergencyProviders(List<String> providerFirebaseUids,
                                                String title,
                                                String body,
                                                String referenceId) {
        notifyEmergencyProviders(providerFirebaseUids, title, body, referenceId, null);
    }

    public static void notifyEmergencyProviders(List<String> providerFirebaseUids,
                                                String title,
                                                String body,
                                                String referenceId,
                                                JSONObject dataPayload) {
        if (providerFirebaseUids == null || providerFirebaseUids.isEmpty()) return;

        final String safeTitle = trimToEmpty(title).isEmpty() ? "Emergency Help" : title.trim();
        final String safeBody = trimToEmpty(body).isEmpty()
                ? "A nearby customer is requesting immediate assistance."
                : body.trim();

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                List<String> tokens = new ArrayList<>();
                List<String> receiverIds = new ArrayList<>();
                List<String> receiverRoles = new ArrayList<>();
                Set<String> seenTokens = new LinkedHashSet<>();

                for (String uid : providerFirebaseUids) {
                    String safeUid = trimToEmpty(uid);
                    if (safeUid.isEmpty()) continue;

                    UserRecipient recipient = fetchUserRecipient(safeUid);
                    if (recipient == null || trimToEmpty(recipient.token).isEmpty()) continue;
                    if (!seenTokens.add(recipient.token)) continue;

                    tokens.add(recipient.token);
                    receiverIds.add(recipient.userId);
                    receiverRoles.add("provider");
                }

                if (tokens.isEmpty()) {
                    Log.d(TAG, "notifyEmergencyProviders skipped: no provider tokens available");
                    return;
                }

                Map<String, String> data = jsonToMap(dataPayload);
                sendViaEdgeFunction(tokens, receiverIds, receiverRoles,
                        safeTitle, safeBody, "emergency_request", referenceId, data);
            } catch (Exception e) {
                Log.w(TAG, "notifyEmergencyProviders failed: " + e.getMessage());
            }
        }).start();
    }

    public static void notifyEmergencyUserAssigned(String userFirebaseUid,
                                                   String title,
                                                   String body,
                                                   String requestId,
                                                   JSONObject dataPayload) {
        notifyUserEvent(userFirebaseUid, title, body, "emergency_assigned", requestId, dataPayload);
    }

    // ── Public single-token send (used by OfferManagement) ───────────────

    /**
     * Send a single FCM push notification to one device token for an offer.
     * Called from OfferManagementActivity for each user token found.
     */
    public static void sendOfferFcm(String deviceToken, String title, String body) {
        if (trimToEmpty(deviceToken).isEmpty()) return;
        sendViaEdgeFunction(
                listOf(deviceToken),
                new ArrayList<>(),
                listOf("user"),
                trimToEmpty(title),
                trimToEmpty(body),
                "offer",
                null,
                null
        );
    }

    /**
     * System alert for admin panel timeline (e.g., maintenance mode changes).
     */
    public static void notifyAdminSystemAlert(String title, String message) {
        notifyAdminsEvent(
                trimToEmpty(title).isEmpty() ? "System Alert" : title.trim(),
                trimToEmpty(message),
                "system_alert",
                null
        );
    }

    /**
     * Notify admin users of a new user registration.
     * Called after a new user signs up (and their account is active).
     *
     * @param userName The name of the new user (for notification text).
     * @param userId   The ID of the new user (for linking in admin panel).
     */
    public static void notifyAdminUserRegistration(String userName, String userId) {
        String safeName = trimToEmpty(userName);
        String body = safeName.isEmpty()
                ? "A new user created an account"
                : safeName + " created an account";
        notifyAdminsEvent("New User Registered", body, "user_registration", userId);
    }

    /**
     * Notify admin users of a newly created offer.
     * Called after an offer is successfully created.
     *
     * @param offerTitle The title of the offer (for notification text).
     * @param offerId    The ID of the offer (for linking in admin panel).
     */
    public static void notifyAdminOfferCreated(String offerTitle, String offerId) {
        String body = trimToEmpty(offerTitle).isEmpty()
                ? "A new offer is available"
                : "Offer created: " + offerTitle;
        notifyAdminsEvent("New Offer Created", body, "offer", offerId);
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private static void notifyProviderEvent(String providerFirebaseUid,
                                            String title,
                                            String body,
                                            String type,
                                            String referenceId) {
        if (trimToEmpty(providerFirebaseUid).isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipient(providerFirebaseUid);
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyProviderEvent skipped: token not found for " + providerFirebaseUid);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("provider"),
                        title,
                        body,
                        type,
                        referenceId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyProviderEvent failed: " + e.getMessage());
            }
        }).start();
    }

    private static void notifyUserEvent(String userFirebaseUid,
                                        String title,
                                        String body,
                                        String type,
                                        String referenceId,
                                        JSONObject dataPayload) {
        if (trimToEmpty(userFirebaseUid).isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipient(userFirebaseUid);
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyUserEvent skipped: token not found for " + userFirebaseUid);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("user"),
                        title,
                        body,
                        type,
                        referenceId,
                        jsonToMap(dataPayload)
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyUserEvent failed: " + e.getMessage());
            }
        }).start();
    }

    private static void notifyAdminsEvent(String title,
                                          String body,
                                          String type,
                                          String referenceId) {
        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && (!AppSettings.isPushEnabled(appCtx)
                        || !AppSettings.isAdminAlertsEnabled(appCtx))) {
                    return;
                }

                AdminRecipients recipients = fetchAdminRecipients();
                if (recipients.tokens.isEmpty()) {
                    Log.d(TAG, "notifyAdminsEvent: no admin tokens available");
                    return;
                }

                sendViaEdgeFunction(
                        recipients.tokens,
                        recipients.receiverIds,
                        recipients.receiverRoles,
                        title,
                        body,
                        type,
                        referenceId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyAdminsEvent failed: " + e.getMessage());
            }
        }).start();
    }

    private static AdminRecipients fetchAdminRecipients() {
        AdminRecipients out = new AdminRecipients();

        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?role=eq.admin"
                + "&fcm_token=not.is.null"
                + "&select=id,fcm_token"
                + "&limit=500";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (resp.code() != 200) {
                Log.w(TAG, "fetchAdminRecipients HTTP " + resp.code() + " body=" + body);
                return out;
            }

            JSONArray arr = new JSONArray(body);
            Set<String> dedupeTokens = new LinkedHashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String id = trimToEmpty(row.optString("id", ""));
                String token = trimToEmpty(row.optString("fcm_token", ""));
                if (id.isEmpty() || token.isEmpty() || dedupeTokens.contains(token)) continue;
                dedupeTokens.add(token);
                out.receiverIds.add(id);
                out.receiverRoles.add("admin");
                out.tokens.add(token);
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchAdminRecipients failed: " + e.getMessage());
        }

        return out;
    }

    private static UserRecipient fetchUserRecipient(String firebaseUid) {
        if (trimToEmpty(firebaseUid).isEmpty()) return null;

        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?firebase_uid=eq." + urlEncode(firebaseUid)
                + "&fcm_token=not.is.null"
                + "&select=id,fcm_token"
                + "&limit=1";

        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response resp = HTTP.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "[]";
            if (resp.code() != 200) {
                Log.w(TAG, "fetchUserRecipient HTTP " + resp.code() + " body=" + body);
                return null;
            }
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) return null;
            JSONObject row = arr.getJSONObject(0);
            String id = trimToEmpty(row.optString("id", ""));
            String token = trimToEmpty(row.optString("fcm_token", ""));
            if (id.isEmpty() || token.isEmpty()) return null;
            return new UserRecipient(id, token);
        } catch (Exception e) {
            Log.w(TAG, "fetchUserRecipient failed: " + e.getMessage());
            return null;
        }
    }

    private static void sendViaEdgeFunction(List<String> tokens,
                                            List<String> receiverIds,
                                            List<String> receiverRoles,
                                            String title,
                                            String body,
                                            String type,
                                            String referenceId,
                                            Map<String, String> data) {
        if (trimToEmpty(NOTIFY_FN_URL).isEmpty()) {
            Log.w(TAG, "SUPABASE_NOTIFY_FN_URL is not configured");
            return;
        }

        List<String> cleanTokens = sanitize(tokens);
        if (cleanTokens.isEmpty()) {
            Log.d(TAG, "sendViaEdgeFunction skipped: no valid tokens");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("tokens", new JSONArray(cleanTokens));
            payload.put("receiver_ids", new JSONArray(sanitize(receiverIds)));
            payload.put("receiver_roles", new JSONArray(sanitize(receiverRoles)));
            payload.put("title", trimToEmpty(title));
            payload.put("body", trimToEmpty(body));
            payload.put("type", trimToEmpty(type).isEmpty() ? "general" : type.trim());
            payload.put("reference_id", trimToEmpty(referenceId).isEmpty() ? JSONObject.NULL : referenceId.trim());
            payload.put("data", mapToJson(data));

            Request req = new Request.Builder()
                    .url(NOTIFY_FN_URL)
                    .post(RequestBody.create(payload.toString(), JSON_TYPE))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            HTTP.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.w(TAG, "sendViaEdgeFunction failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String rb = response.body() != null ? response.body().string() : "";
                    response.close();
                    if (code >= 200 && code < 300) {
                        Log.d(TAG, "sendViaEdgeFunction success HTTP " + code + " type=" + type);
                    } else {
                        Log.w(TAG, "sendViaEdgeFunction HTTP " + code + " body=" + rb);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "sendViaEdgeFunction exception: " + e.getMessage());
        }
    }

    private static Map<String, String> jsonToMap(JSONObject payload) {
        Map<String, String> out = new HashMap<>();
        if (payload == null) return out;
        JSONArray names = payload.names();
        if (names == null) return out;
        for (int i = 0; i < names.length(); i++) {
            String key = trimToEmpty(names.optString(i));
            if (key.isEmpty()) continue;
            Object value = payload.opt(key);
            if (value == null || JSONObject.NULL.equals(value)) continue;
            out.put(key, String.valueOf(value));
        }
        return out;
    }

    private static JSONObject mapToJson(Map<String, String> data) {
        JSONObject obj = new JSONObject();
        if (data == null) return obj;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            String key = trimToEmpty(entry.getKey());
            String value = trimToEmpty(entry.getValue());
            if (key.isEmpty() || value.isEmpty()) continue;
            try {
                obj.put(key, value);
            } catch (Exception ignored) {
            }
        }
        return obj;
    }

    private static List<String> sanitize(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        Set<String> dedupe = new LinkedHashSet<>();
        for (String value : values) {
            String v = trimToEmpty(value);
            if (!v.isEmpty()) dedupe.add(v);
        }
        out.addAll(dedupe);
        return out;
    }

    private static List<String> listOf(String value) {
        List<String> list = new ArrayList<>();
        if (!trimToEmpty(value).isEmpty()) list.add(value.trim());
        return list;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class UserRecipient {
        final String userId;
        final String token;

        UserRecipient(String userId, String token) {
            this.userId = userId;
            this.token = token;
        }
    }

    private static final class AdminRecipients {
        final List<String> receiverIds = new ArrayList<>();
        final List<String> receiverRoles = new ArrayList<>();
        final List<String> tokens = new ArrayList<>();
    }
}
