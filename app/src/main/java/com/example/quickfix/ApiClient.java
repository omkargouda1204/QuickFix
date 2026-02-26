package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ApiClient — Supabase REST API client using OkHttp.
 *
 * Connects directly to Supabase over HTTPS only. No backend server needed.
 * Credentials are loaded at build time from supabase.properties via BuildConfig.
 * Credentials are NEVER hardcoded.
 *
 * Headers used on every request:
 *   apikey:        SUPABASE_ANON_KEY
 *   Authorization: Bearer SUPABASE_ANON_KEY
 *   Content-Type:  application/json
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    // ── Supabase connection — values come from BuildConfig (supabase.properties) ──
    private static final String SUPABASE_URL  = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY      = BuildConfig.SUPABASE_ANON_KEY;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Timeouts: short enough that the user never waits more than ~10 s
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,    TimeUnit.SECONDS)
            .writeTimeout(15,   TimeUnit.SECONDS)
            .build();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Callback interface ────────────────────────────────────────────────

    public interface ApiCallback {
        /** Called on the main thread with the parsed JSON response object. */
        void onSuccess(JSONObject response);
        /** Called on the main thread with a human-readable error message. */
        void onError(String errorMessage);
    }

    public interface ArrayCallback {
        /** Called on the main thread with a JSON array result. */
        void onSuccess(JSONArray result);
        /** Called on the main thread with a human-readable error message. */
        void onError(String errorMessage);
    }

    /** Single-method functional interface used internally for array results. */
    @FunctionalInterface
    private interface ArraySuccessCallback {
        void onSuccess(JSONArray result);
    }

    // ── Supabase request builder helper ──────────────────────────────────

    private static Request.Builder supabaseBuilder(String path) {
        return new Request.Builder()
                .url(SUPABASE_URL + path)
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Accept",        "application/json");
    }

    // ── GET a user row by email ──────────────────────────────────────────

    /**
     * Fetch user record from Supabase "users" table by email.
     *
     * @param email    the user's email address
     * @param callback result callback (always on the main thread)
     */
    public static void getUserByEmail(String email, ApiCallback callback) {
        if (email == null || email.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Email must not be empty."));
            return;
        }

        String encoded;
        try {
            encoded = java.net.URLEncoder.encode(email.trim(), "UTF-8");
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Invalid email format."));
            return;
        }

        String path = "/rest/v1/users"
                + "?email=eq." + encoded
                + "&select=firebase_uid,name,email,phone,is_user,is_service_provider,is_admin"
                + "&limit=1";

        Request request = supabaseBuilder(path).get().build();

        executeArrayRequest(request, "getUserByEmail", result -> {
            try {
                if (result.length() > 0) {
                    callback.onSuccess(result.getJSONObject(0));
                } else {
                    callback.onError("USER_NOT_FOUND");
                }
            } catch (Exception e) {
                callback.onError("Parse error: " + e.getMessage());
            }
        }, callback::onError);
    }

    // ── GET a user row by Firebase UID ───────────────────────────────────

    /**
     * Fetch user record from Supabase "users" table by Firebase UID.
     */
    public static void getUserByUid(String firebaseUid, ApiCallback callback) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Firebase UID must not be empty."));
            return;
        }

        String path = "/rest/v1/users"
                + "?firebase_uid=eq." + firebaseUid
                + "&select=firebase_uid,name,email,phone,is_user,is_service_provider,is_admin"
                + "&limit=1";

        Request request = supabaseBuilder(path).get().build();

        executeArrayRequest(request, "getUserByUid", result -> {
            try {
                if (result.length() > 0) {
                    callback.onSuccess(result.getJSONObject(0));
                } else {
                    callback.onError("USER_NOT_FOUND");
                }
            } catch (Exception e) {
                callback.onError("Parse error: " + e.getMessage());
            }
        }, callback::onError);
    }

    // ── UPSERT (INSERT or UPDATE) user profile ───────────────────────────

    /**
     * Insert or update a user row in Supabase "users" table.
     * Uses "Prefer: resolution=ignore-duplicates" to preserve existing roles.
     *
     * @param body     JSONObject with firebase_uid, name, email, phone, etc.
     * @param callback result callback
     */
    public static void upsertUser(JSONObject body, ApiCallback callback) {
        RequestBody reqBody = RequestBody.create(body.toString(), JSON);

        Request request = supabaseBuilder("/rest/v1/users")
                .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String msg = buildNetworkErrorMessage(e);
                Log.e(TAG, "upsertUser failed: " + e.getMessage());
                mainHandler.post(() -> callback.onError(msg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String bodyStr = response.body() != null ? response.body().string() : "{}";
                response.close();
                Log.d(TAG, "upsertUser HTTP " + code);
                if (code == 200 || code == 201 || code == 204) {
                    mainHandler.post(() -> {
                        try { callback.onSuccess(new JSONObject("{\"success\":true}")); }
                        catch (Exception e) { callback.onSuccess(new JSONObject()); }
                    });
                } else {
                    mainHandler.post(() -> callback.onError(
                            "Supabase error (" + code + "): " + bodyStr));
                }
            }
        });
    }

    // ── Check Supabase reachability (call before login if needed) ────────

    /**
     * Ping Supabase to confirm the project is reachable.
     * Use before critical operations to give the user an early network error.
     */
    public static void checkReachability(Runnable onReachable, Runnable onUnreachable) {
        Request request = supabaseBuilder("/rest/v1/").get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Supabase unreachable: " + e.getMessage());
                mainHandler.post(onUnreachable);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                response.close();
                // Any HTTP response means the server is reachable
                mainHandler.post(onReachable);
            }
        });
    }

    // ── Internal helper: execute a GET request and parse as JSONArray ────

    private static void executeArrayRequest(Request request, String tag,
                                             ArraySuccessCallback onSuccess,
                                             java.util.function.Consumer<String> onError) {
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                String msg = buildNetworkErrorMessage(e);
                Log.e(TAG, tag + " failed: " + e.getMessage());
                mainHandler.post(() -> onError.accept(msg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String bodyStr = response.body() != null ? response.body().string() : "[]";
                response.close();
                Log.d(TAG, tag + " HTTP " + code + " | " + bodyStr);

                if (code == 200) {
                    try {
                        JSONArray arr = new JSONArray(bodyStr);
                        mainHandler.post(() -> onSuccess.onSuccess(arr));
                    } catch (Exception e) {
                        mainHandler.post(() -> onError.accept("Response parse error: " + e.getMessage()));
                    }
                } else if (code == 401 || code == 403) {
                    mainHandler.post(() -> onError.accept(
                            "Unauthorized. Check your Supabase API key."));
                } else if (code == 404) {
                    mainHandler.post(() -> onError.accept(
                            "Table not found. Check your Supabase schema."));
                } else {
                    mainHandler.post(() -> onError.accept(
                            "Supabase error (" + code + "): " + bodyStr));
                }
            }
        });
    }

    // ── Network error message builder ─────────────────────────────────────

    private static String buildNetworkErrorMessage(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return "Connection timed out. Check your internet and try again.";
        }
        if (e instanceof UnknownHostException) {
            return "Cannot reach Supabase. Check your internet connection.";
        }
        String msg = e.getMessage();
        if (msg != null && (msg.contains("CLEARTEXT") || msg.contains("cleartext"))) {
            return "Insecure connection blocked. Only HTTPS is allowed.";
        }
        if (msg != null && msg.contains("refused")) {
            return "Connection refused. Supabase project may be paused.";
        }
        return "Network error: " + (msg != null ? msg : "Unknown error");
    }
}
