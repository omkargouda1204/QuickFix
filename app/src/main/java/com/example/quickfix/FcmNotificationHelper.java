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
                                                String serviceName,
                                                String bookingId) {
        String body = (trimToEmpty(customerName).isEmpty() ? "A customer" : customerName)
                + " requested"
                + (trimToEmpty(serviceName).isEmpty() ? " a service." : " " + serviceName + ".");

        notifyProviderEvent(
                providerFirebaseUid,
                "New Booking Request",
                body,
                "booking_request",
                trimToEmpty(bookingId).isEmpty() ? null : bookingId
        );
    }

    /** Backward-compatible overload used by older call sites. */
    public static void notifyProviderNewBooking(String providerFirebaseUid,
                                                String customerName,
                                                String serviceName) {
        notifyProviderNewBooking(providerFirebaseUid, customerName, serviceName, null);
    }

    /**
     * Notify the customer that their booking status has been updated.
     * Called after a provider accepts, rejects, or completes a booking.
     *
     * @param customerUserId The user ID of the customer (for notification).
     * @param providerName   The name of the provider (for notification text).
     * @param serviceName    The service/category of the booking (for notification text).
     * @param newStatus      The new status of the booking (accepted, cancelled, completed, etc.).
     * @param bookingId      The ID of the booking (for linking in admin panel, if needed).
     */
    public static void notifyCustomerBookingStatus(String customerUserId,
                                                   String providerName,
                                                   String serviceName,
                                                   String newStatus,
                                                   String bookingId) {
        if (trimToEmpty(customerUserId).isEmpty()) return;

        String normalized = trimToEmpty(newStatus).toLowerCase();
        String title;
        String type;
        switch (normalized) {
            case "accepted":
                title = "Booking Accepted";
                type = "booking_accepted";
                break;
            case "cancelled":
                title = "Booking Rejected";
                type = "booking_rejected";
                break;
            case "completed":
                title = "Service Completed";
                type = "booking_completed";
                break;
            default:
                title = "Booking Updated";
                type = "booking_update";
                break;
        }

        String safeProvider = trimToEmpty(providerName);
        String safeService = trimToEmpty(serviceName);
        String body = (safeProvider.isEmpty() ? "Your provider" : safeProvider)
                + " updated"
                + (safeService.isEmpty() ? " your booking." : " your " + safeService + " booking.");

        new Thread(() -> {
            try {
                Context appCtx = QuickFixApp.getInstance();
                if (appCtx != null && !AppSettings.isPushEnabled(appCtx)) {
                    return;
                }

                UserRecipient recipient = fetchUserRecipientByUserId(customerUserId);
                if (recipient == null || trimToEmpty(recipient.token).isEmpty()) {
                    // Fallback for older rows where customer_id may still hold firebase_uid text.
                    recipient = fetchUserRecipient(customerUserId);
                }
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
                        type,
                        trimToEmpty(bookingId).isEmpty() ? null : bookingId
                );
            } catch (Exception e) {
                Log.w(TAG, "notifyCustomerBookingStatus failed: " + e.getMessage());
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
                        referenceId
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
                        referenceId
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

    private static UserRecipient fetchUserRecipientByUserId(String userId) {
        if (trimToEmpty(userId).isEmpty()) return null;

        String url = SUPABASE_URL
                + "/rest/v1/users"
                + "?id=eq." + urlEncode(userId)
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
                Log.w(TAG, "fetchUserRecipientByUserId HTTP " + resp.code() + " body=" + body);
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
            Log.w(TAG, "fetchUserRecipientByUserId failed: " + e.getMessage());
            return null;
        }
    }

    private static void sendViaEdgeFunction(List<String> tokens,
                                            List<String> receiverIds,
                                            List<String> receiverRoles,
                                            String title,
                                            String body,
                                            String type,
                                            String referenceId) {
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
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always available, but return a safe fallback for compiler/runtime safety.
            Log.w(TAG, "urlEncode fallback: " + e.getMessage());
            return value;
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
