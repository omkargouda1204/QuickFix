package com.example.quickfix;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MyFirebaseMessagingService
 * Responsibilities:
 *  1. onNewToken()         — save fresh FCM token to Supabase silently.
 *  2. onMessageReceived()  — show system notification when app is FOREGROUND.
 *                            When app is in background/killed, the OS handles
 *                            the notification payload automatically.
 * Notification channels:
 *  - provider_apps  : Provider application events (admin + provider)
 *  - general        : All other push messages
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "QuickFix_FCM";

    // Notification channel IDs
    public static final String CHANNEL_PROVIDER = "provider_apps";
    public static final String CHANNEL_BOOKINGS = "bookings";
    public static final String CHANNEL_GENERAL  = "general";

    // Auto-incrementing notification ID so multiple notifications stack
    private static final AtomicInteger NOTIF_ID = new AtomicInteger(1000);

    // ── Token refresh ─────────────────────────────────────────────────────

    /**
     * Called when Firebase issues a new token for this device.
     * Automatically stores the updated token in the database.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "FCM token refreshed — saving to DB silently");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            // Save fresh token to Supabase — background, no UI
            SupabaseClient.saveFcmToken(user.getUid(), token);
        } else {
            // User not logged in yet; token will be saved on next login via MainActivity
            Log.d(TAG, "onNewToken: no logged-in user, token will be saved on next login");
        }
    }

    // ── Foreground message handling ───────────────────────────────────────

    /**
     * Called when a message arrives and the app is in the FOREGROUND.
     * Background / killed app: the FCM SDK handles display automatically
     * using the notification payload (title + body) — no code needed.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "onMessageReceived from: " + remoteMessage.getFrom());

        String title = null;
        String body  = null;
        String type  = "general";

        // ── 1. Notification payload (title + body from FCM console / edge fn) ──
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            title = notification.getTitle();
            body  = notification.getBody();
            Log.d(TAG, "notification payload — title: " + title + " body: " + body);
        }

        // ── 2. Data payload (custom key-value pairs) ──────────────────────
        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            Log.d(TAG, "data payload: " + data);
            // Data payload can override or supplement notification payload
            if (title == null) title = data.get("title");
            if (body  == null) body  = data.get("body");
            if (data.containsKey("type")) type = data.get("type");
        }

        // Fallback defaults
        if (title == null || title.isEmpty()) title = getString(R.string.app_name);
        if (body  == null || body.isEmpty())  body  = "You have a new notification.";

        showSystemNotification(title, body, type, data);
    }

    // ── System notification builder ───────────────────────────────────────

    /**
     * Builds and posts a system tray notification.
     * Works on all API levels ≥ 24 (minSdk).
     */
    private void showSystemNotification(String title,
                                         String body,
                                         String type,
                                         Map<String, String> data) {
        ensureNotificationChannels();

        // Determine channel and target activity based on type
        String channelId;
        Class<?> targetActivity;

        switch (type != null ? type : "") {
            case "provider_application":
                // Admin receives this → open provider approval page
                channelId      = CHANNEL_PROVIDER;
                targetActivity = AdminProviderApprovalActivity.class;
                break;
            case "provider_approved":
            case "provider_rejected":
                // Provider receives this → open application status page
                channelId      = CHANNEL_PROVIDER;
                targetActivity = ApplicationStatusActivity.class;
                break;
            case "booking_request":
                // Provider receives this → open My Bookings page
                channelId      = CHANNEL_BOOKINGS;
                targetActivity = MyBookingsActivity.class;
                break;
            case "emergency_request":
                // Provider receives this → open emergency accept/reject screen
                channelId = CHANNEL_BOOKINGS;
                targetActivity = ProviderEmergencyRequestActivity.class;
                break;
            case "emergency_assigned":
                // User receives this → open emergency booking status
                channelId = CHANNEL_BOOKINGS;
                targetActivity = EmergencyHelpActivity.class;
                break;
            default:
                channelId      = CHANNEL_GENERAL;
                targetActivity = MainActivity.class;
                break;
        }

        // Build tap intent
        Intent intent = new Intent(this, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Pass data extras so target activity can react
        if (data != null) {
            for (Map.Entry<String, String> e : data.entrySet()) {
                intent.putExtra(e.getKey(), e.getValue());
            }
        }

        int requestCode = NOTIF_ID.incrementAndGet();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Build notification
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)          // use launcher icon as small icon
                        .setContentTitle(title)
                        .setContentText(body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)                          // dismiss on tap
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)  // sound + vibrate
                        .setColor(getResources().getColor(R.color.primary, getTheme()))
                        .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(requestCode, builder.build());
            Log.d(TAG, "showSystemNotification posted id=" + requestCode
                    + " channel=" + channelId + " title=" + title);
        }
    }

    // ── Notification channels (required for API ≥ 26) ────────────────────

    /**
     * Creates notification channels if they don't already exist.
     * Safe to call repeatedly — Android ignores duplicate creates.
     */
    public static void ensureNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Provider Applications channel — high importance, heads-up
        NotificationChannel providerChannel = new NotificationChannel(
                CHANNEL_PROVIDER,
                "Provider Applications",
                NotificationManager.IMPORTANCE_HIGH);
        providerChannel.setDescription("Notifications for provider application status updates");
        providerChannel.enableLights(true);
        providerChannel.setLightColor(Color.BLUE);
        providerChannel.enableVibration(true);
        manager.createNotificationChannel(providerChannel);

        // Bookings channel — high importance for booking requests
        NotificationChannel bookingsChannel = new NotificationChannel(
                CHANNEL_BOOKINGS,
                "Booking Requests",
                NotificationManager.IMPORTANCE_HIGH);
        bookingsChannel.setDescription("Notifications for new booking requests and updates");
        bookingsChannel.enableLights(true);
        bookingsChannel.setLightColor(Color.YELLOW);
        bookingsChannel.enableVibration(true);
        manager.createNotificationChannel(bookingsChannel);

        // General channel — default importance
        NotificationChannel generalChannel = new NotificationChannel(
                CHANNEL_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT);
        generalChannel.setDescription("General app notifications");
        generalChannel.enableLights(true);
        generalChannel.setLightColor(Color.GREEN);
        manager.createNotificationChannel(generalChannel);

        Log.d(TAG, "Notification channels ensured");
    }

    /** Instance helper — calls static version */
    private void ensureNotificationChannels() {
        ensureNotificationChannels(this);
    }
}
