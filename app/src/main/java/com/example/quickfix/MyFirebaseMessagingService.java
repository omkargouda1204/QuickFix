package com.example.quickfix;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Handles FCM token refresh events and incoming push messages.
 *
 * IMPORTANT: onNewToken() is called by Firebase whenever the device token is
 * rotated (app reinstall, cache clear, etc.). We save the fresh token to
 * Supabase so push notifications always reach the right device.
 *
 * The token is NEVER displayed in the UI — it is stored silently in the
 * public.users table under the fcm_token column.
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "QuickFix_FCM";

    /**
     * Called when Firebase issues a new token for this device.
     * Automatically stores the updated token in the database.
     */
    @Override
    public void onNewToken(String token) {
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

    /**
     * Called when a FCM data/notification message is received while the app
     * is in the foreground. Extend this to show in-app notifications.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        // Notification payload — Firebase auto-shows it when app is in background.
        // Handle foreground notification here if needed.
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Notification title: "
                    + remoteMessage.getNotification().getTitle());
        }

        // Data payload
        if (!remoteMessage.getData().isEmpty()) {
            Log.d(TAG, "Data payload: " + remoteMessage.getData());
        }
    }
}

