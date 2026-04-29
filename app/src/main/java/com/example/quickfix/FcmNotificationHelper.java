package com.example.quickfix;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.quickfix.BuildConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private FcmNotificationHelper() {
    }

    public interface NotificationDispatchCallback {
        void onSuccess(int sent, int totalTokens);

        void onFailure(String message);
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
     * Notify multiple providers about a new emergency request.
     */
    public static void notifyEmergencyProviders(List<String> providerFirebaseUids,
                                                String title,
                                                String body,
                                                String requestId,
                                                JSONObject data) {
        notifyEmergencyProviders(providerFirebaseUids, title, body, requestId, data, null);
    }

    public static void notifyEmergencyProviders(List<String> providerFirebaseUids,
                                                String title,
                                                String body,
                                                String requestId,
                                                JSONObject data,
                                                NotificationDispatchCallback callback) {
        List<String> targets = sanitize(providerFirebaseUids);
        if (targets.isEmpty()) {
            postFailure(callback, "No providers available for notification.");
            return;
        }

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    postFailure(callback, "Push notifications are disabled.");
                    return;
                }

                List<String> tokens = new ArrayList<>();
                Set<String> dedupeTokens = new LinkedHashSet<>();
                for (String firebaseUid : targets) {
                    UserRecipient recipient = fetchUserRecipient(firebaseUid);
                    if (recipient == null || trimToEmpty(recipient.token).isEmpty()) continue;
                    if (dedupeTokens.add(recipient.token)) {
                        tokens.add(recipient.token);
                    }
                }

                if (tokens.isEmpty()) {
                    Log.d(TAG, "notifyEmergencyProviders skipped: no provider tokens found");
                    postFailure(callback, "No valid provider device tokens found.");
                    return;
                }

                sendViaEdgeFunction(
                        tokens,
                        new ArrayList<>(),
                        listOf("provider"),
                        title,
                        body,
                        "emergency_request",
                        requestId,
                        data,
                        callback
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyEmergencyProviders failed: " + e.getMessage());
                postFailure(callback, "Notification failed: " + trimToEmpty(e.getMessage()));
            }
        }).start();
    }

    /**
     * Notify a user that an emergency provider has been assigned.
     */
    public static void notifyEmergencyUserAssigned(String userFirebaseUid,
                                                   String title,
                                                   String body,
                                                   String requestId,
                                                   JSONObject data) {
        if (trimToEmpty(userFirebaseUid).isEmpty()) return;

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipient(userFirebaseUid);
                if (recipient == null) {
                    recipient = fetchUserRecipientById(userFirebaseUid);
                }
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyEmergencyUserAssigned skipped: token not found for " + userFirebaseUid);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("user"),
                        title,
                        body,
                        "emergency_assigned",
                        requestId,
                        data
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyEmergencyUserAssigned failed: " + e.getMessage());
            }
        }).start();
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

    /**
     * Notify customer when provider updates booking status.
     */
    public static void notifyCustomerBookingStatus(String customerUserId,
                                                   String providerName,
                                                   String serviceName,
                                                   String newStatus,
                                                   String bookingId) {
        if (trimToEmpty(customerUserId).isEmpty()) return;

        String status = trimToEmpty(newStatus).toLowerCase();
        String title;
        String body;

        switch (status) {
            case "accepted":
                title = "Booking Accepted";
                body = (trimToEmpty(providerName).isEmpty() ? "Your provider" : providerName)
                        + " accepted your booking"
                        + (trimToEmpty(serviceName).isEmpty() ? "." : " for " + serviceName + ".");
                break;
            case "completed":
                title = "Booking Completed";
                body = "Your booking"
                        + (trimToEmpty(serviceName).isEmpty() ? "" : " for " + serviceName)
                        + " was marked completed.";
                break;
            case "cancelled":
                title = "Booking Cancelled";
                body = "Your booking"
                        + (trimToEmpty(serviceName).isEmpty() ? "" : " for " + serviceName)
                        + " was cancelled by the provider.";
                break;
            case "price_set":
                title = "Payment Requested";
                body = "Your provider set the booking amount. Please complete payment.";
                break;
            default:
                title = "Booking Updated";
                body = "Your booking status changed"
                        + (status.isEmpty() ? "." : " to " + status + ".");
                break;
        }

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipientById(customerUserId);
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyCustomerBookingStatus skipped: token not found for " + customerUserId);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("user"),
                        title,
                        body,
                        "booking",
                        bookingId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyCustomerBookingStatus failed: " + e.getMessage());
            }
        }).start();
    }

    public static void notifyCustomerPriceSet(String customerUserId,
                                              double amount,
                                              String bookingId) {
        if (trimToEmpty(customerUserId).isEmpty()) return;
        String amountLabel = amount > 0 ? "\u20b9" + Math.round(amount) : "the final amount";

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipientById(customerUserId);
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyCustomerPriceSet skipped: token not found for " + customerUserId);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("user"),
                        "Payment Requested",
                        "Provider set " + amountLabel + ". Please pay.",
                        "booking",
                        bookingId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyCustomerPriceSet failed: " + e.getMessage());
            }
        }).start();
    }

    public static void notifyProviderPaymentCompleted(String providerFirebaseUid,
                                                      String serviceName,
                                                      String bookingId) {
        String providerRef = trimToEmpty(providerFirebaseUid);
        if (providerRef.isEmpty()) return;
        String body = "User completed payment"
                + (trimToEmpty(serviceName).isEmpty() ? "." : " for " + serviceName + ".");

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipient(providerRef);
                if (recipient == null) {
                    recipient = fetchUserRecipientById(providerRef);
                }
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyProviderPaymentCompleted skipped: token not found for " + providerRef);
                    return;
                }

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf("provider"),
                        "Payment Completed",
                        body,
                        "booking",
                        bookingId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyProviderPaymentCompleted failed: " + e.getMessage());
            }
        }).start();
    }

    public static void notifyChatMessage(String receiverId,
                                         String receiverRole,
                                         String senderName,
                                         String messagePreview,
                                         String bookingId) {
        String target = trimToEmpty(receiverId);
        if (target.isEmpty()) return;
        String role = trimToEmpty(receiverRole).isEmpty() ? "user" : receiverRole.trim().toLowerCase();

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipientById(target);
                if (recipient == null) {
                    recipient = fetchUserRecipient(target);
                }
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    Log.d(TAG, "notifyChatMessage skipped: token not found for " + target);
                    return;
                }

                String title = "New message from " + (trimToEmpty(senderName).isEmpty() ? "someone" : trimToEmpty(senderName));
                String body = trimToEmpty(messagePreview).isEmpty() ? "You received a message" : trimToEmpty(messagePreview);

                sendViaEdgeFunction(
                        listOf(recipient.token),
                        listOf(recipient.userId),
                        listOf(role),
                        title,
                        body,
                        "chat_message",
                        trimToEmpty(bookingId).isEmpty() ? null : bookingId,
                        null
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyChatMessage failed: " + e.getMessage());
            }
        }).start();
    }

    public static void saveNotificationToDb(String receiverUid, String title, String message, String requestId, String type) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("receiver_id", receiverUid); // provider_id
                body.put("provider_id", receiverUid); // as requested
                body.put("title", title);
                body.put("message", message);
                body.put("request_id", requestId);
                body.put("type", type != null ? type : "emergency");
                body.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new java.util.Date()));

                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/notifications")
                        .post(RequestBody.create(body.toString(), JSON_TYPE))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    if (!r.isSuccessful()) {
                        Log.w(TAG, "saveNotificationToDb failed: " + r.code());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "saveNotificationToDb error: " + e.getMessage());
            }
        }).start();
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
        return fetchUserRecipientByColumn("firebase_uid", firebaseUid);
    }

    private static UserRecipient fetchUserRecipientById(String userId) {
        if (trimToEmpty(userId).isEmpty()) return null;
        return fetchUserRecipientByColumn("id", userId);
    }

    private static UserRecipient fetchUserRecipientByColumn(String column, String value) {
        String safeColumn = trimToEmpty(column);
        if (safeColumn.isEmpty() || trimToEmpty(value).isEmpty()) return null;

        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?" + safeColumn + "=eq." + urlEncode(value)
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
                Log.w(TAG, "fetchUserRecipientByColumn HTTP " + resp.code() + " body=" + body);
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
            Log.w(TAG, "fetchUserRecipientByColumn failed: " + e.getMessage());
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
                                            JSONObject data) {
        sendViaEdgeFunction(tokens, receiverIds, receiverRoles, title, body, type, referenceId, data, null);
    }

    private static void sendViaEdgeFunction(List<String> tokens,
                                            List<String> receiverIds,
                                            List<String> receiverRoles,
                                            String title,
                                            String body,
                                            String type,
                                            String referenceId,
                                            JSONObject data,
                                            NotificationDispatchCallback callback) {
        if (trimToEmpty(NOTIFY_FN_URL).isEmpty()) {
            Log.w(TAG, "SUPABASE_NOTIFY_FN_URL is not configured");
            postFailure(callback, "Notification service is not configured.");
            return;
        }

        List<String> cleanTokens = sanitize(tokens);
        if (cleanTokens.isEmpty()) {
            Log.d(TAG, "sendViaEdgeFunction skipped: no valid tokens");
            postFailure(callback, "No valid notification tokens available.");
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
            JSONObject dataPayload = toStringData(data);
            if (dataPayload.length() > 0) {
                payload.put("data", dataPayload);
            }

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
                    postFailure(callback, "Notification request failed: " + trimToEmpty(e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    String rb = response.body() != null ? response.body().string() : "";
                    response.close();
                    if (code >= 200 && code < 300) {
                        Log.d(TAG, "sendViaEdgeFunction success HTTP " + code + " type=" + type);
                        int sent = 0;
                        int totalTokens = cleanTokens.size();
                        try {
                            if (!trimToEmpty(rb).isEmpty()) {
                                JsonObject root = JsonParser.parseString(rb).getAsJsonObject();
                                if (root.has("sent")) {
                                    sent = root.get("sent").getAsInt();
                                }
                                if (root.has("totalTokens")) {
                                    totalTokens = root.get("totalTokens").getAsInt();
                                }
                            }
                        } catch (Exception parseError) {
                            Log.w(TAG, "sendViaEdgeFunction response parse failed: " + parseError.getMessage());
                            sent = cleanTokens.size();
                        }
                        postSuccess(callback, sent, totalTokens);
                    } else {
                        Log.w(TAG, "sendViaEdgeFunction HTTP " + code + " body=" + rb);
                        postFailure(callback, "Notification API failed (" + code + ").");
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "sendViaEdgeFunction exception: " + e.getMessage());
            postFailure(callback, "Notification exception: " + trimToEmpty(e.getMessage()));
        }
    }

    private static void postSuccess(NotificationDispatchCallback callback, int sent, int totalTokens) {
        if (callback == null) return;
        MAIN.post(() -> callback.onSuccess(Math.max(0, sent), Math.max(0, totalTokens)));
    }

    private static void postFailure(NotificationDispatchCallback callback, String message) {
        if (callback == null) return;
        String safeMessage = trimToEmpty(message).isEmpty() ? "Notification failed." : message.trim();
        MAIN.post(() -> callback.onFailure(safeMessage));
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

    private static JSONObject toStringData(JSONObject data) {
        JSONObject out = new JSONObject();
        if (data == null) return out;

        try {
            Iterator<String> keys = data.keys();
            while (keys.hasNext()) {
                String key = trimToEmpty(keys.next());
                if (key.isEmpty()) continue;
                Object value = data.opt(key);
                if (value == null || value == JSONObject.NULL) continue;
                out.put(key, String.valueOf(value));
            }
        } catch (Exception e) {
            Log.w(TAG, "toStringData failed: " + e.getMessage());
        }
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
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
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
