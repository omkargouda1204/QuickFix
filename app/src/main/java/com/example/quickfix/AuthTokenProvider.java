package com.example.quickfix;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper to obtain current Firebase ID token in synchronous (blocking) manner
 * suitable for background threads. Returns null on timeout or when not available.
 */
public class AuthTokenProvider {

    /**
     * Attempts to fetch the current user's Firebase ID token, waiting up to
     * {@code timeoutSeconds}. Callers should run this from a background thread.
     */
    public static String getIdTokenBlocking(long timeoutSeconds) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return null;

        final String[] tokenHolder = new String[1];
        final CountDownLatch latch = new CountDownLatch(1);

        user.getIdToken(false).addOnCompleteListener(task -> {
            try {
                if (task.isSuccessful() && task.getResult() != null) {
                    tokenHolder[0] = task.getResult().getToken();
                }
            } catch (Exception ignored) {}
            latch.countDown();
        });

        try {
            boolean ok = latch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!ok) return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return tokenHolder[0];
    }
}
