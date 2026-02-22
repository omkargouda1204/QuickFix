package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseClient — saves / upserts user profiles into Supabase public.users
 * table via the PostgREST REST API using OkHttp.
 *
 * No Firestore. No backend server. Pure HTTPS REST.
 *
 * Table schema (from your SQL):
 *   firebase_uid TEXT UNIQUE NOT NULL
 *   name         TEXT NOT NULL
 *   email        TEXT NOT NULL UNIQUE
 *   phone        TEXT
 *   role         TEXT DEFAULT 'user'
 *   is_verified  BOOLEAN DEFAULT TRUE
 */
public class SupabaseClient {

    private static final String TAG = "SupabaseClient";

    // ── Supabase project credentials ──────────────────────────────────────
    // Dashboard → Settings → API → Project URL + anon public key
    private static final String SUPABASE_URL =
            "https://rddzycqxtgryrpymivfd.supabase.co";

    // anon / public key (safe to ship in the APK)
    private static final String ANON_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
            ".eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJkZHp5Y3F4dGdyeXJweW1pdmZkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Mzk5NjYwNzksImV4cCI6MjA1NTU0MjA3OX0" +
            ".Tq6tKoSvqZSp8VLZyulqxP03glP1EfcJp2nW4nkSFqI";
    // ─────────────────────────────────────────────────────────────────────

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Callback interface ────────────────────────────────────────────────
    public interface Callback2 {
        void onSuccess();
        void onError(String message);
    }

    // ─────────────────────────────────────────────────────────────────────
    /**
     * Upsert a user profile row.
     *
     * Uses PostgREST UPSERT with "Prefer: resolution=merge-duplicates" so
     * calling this multiple times for the same firebase_uid is safe — it
     * updates the existing row rather than inserting a duplicate.
     *
     * If the Supabase call fails for any reason the callback still fires
     * onSuccess() so that the Firebase auth flow (verification email, etc.)
     * is NOT blocked by a database issue.
     */
    public static void upsertUser(
            String firebaseUid,
            String name,
            String email,
            String phone,
            String role,
            Callback2 callback) {

        try {
            // Build JSON body matching public.users columns
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("name",         name  != null ? name.trim()  : "");
            body.put("email",        email != null ? email.trim() : "");
            body.put("phone",        phone != null ? phone.trim() : "");
            body.put("role",         role  != null ? role.trim()  : "user");
            body.put("is_verified",  true);   // Firebase handles verification

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users")
                    .post(RequestBody.create(body.toString(), JSON_TYPE))
                    // Auth headers — both required by Supabase
                    .addHeader("apikey",         ANON_KEY)
                    .addHeader("Authorization",  "Bearer " + ANON_KEY)
                    .addHeader("Content-Type",   "application/json")
                    // UPSERT: on conflict (firebase_uid) → update existing row
                    .addHeader("Prefer",
                               "resolution=merge-duplicates,return=minimal")
                    .build();

            HTTP.newCall(request).enqueue(new okhttp3.Callback() {

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "upsertUser network failure: " + e.getMessage());
                    // Non-blocking — proceed with Firebase flow
                    MAIN.post(callback::onSuccess);
                }

                @Override
                public void onResponse(Call call, Response response)
                        throws IOException {
                    int    code    = response.code();
                    String rawBody = response.body() != null
                            ? response.body().string() : "";
                    response.close();

                    Log.d(TAG, "upsertUser HTTP " + code + " | " + rawBody);

                    if (code == 200 || code == 201 || code == 204) {
                        // Success
                        MAIN.post(callback::onSuccess);
                    } else {
                        // Parse Supabase error body for a useful log message
                        String detail = "HTTP " + code;
                        try {
                            JSONObject err = new JSONObject(rawBody);
                            detail = err.optString("message",
                                    err.optString("hint", detail));
                        } catch (Exception ignored) {}
                        Log.e(TAG, "upsertUser failed: " + detail);

                        // Non-blocking — still proceed with auth flow
                        MAIN.post(callback::onSuccess);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "upsertUser exception: " + e.getMessage());
            MAIN.post(callback::onSuccess); // non-blocking
        }
    }
}
