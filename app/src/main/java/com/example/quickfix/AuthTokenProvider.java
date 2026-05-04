package com.example.quickfix;

import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Helper to obtain the Supabase REST bearer token used by this app.
 *
 * The project currently does not include a Supabase Auth session SDK, so the
 * anonymous Supabase JWT is used as the stable bearer token for public table
 * access. Callers should still run this from a background thread.
 */
public class AuthTokenProvider {

    public static String getIdTokenBlocking(long timeoutSeconds) {
        // Keep the signature for existing call sites, but return the stable
        // Supabase JWT used by the app's REST requests.
        if (timeoutSeconds > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String token = BuildConfig.SUPABASE_ANON_KEY;
        if (token.trim().isEmpty()) {
            Log.e("AuthTokenProvider", "Supabase anon key is missing; cannot build Authorization header");
            return null;
        }

        Log.d("TOKEN", token);
        return token.trim();
    }
}
