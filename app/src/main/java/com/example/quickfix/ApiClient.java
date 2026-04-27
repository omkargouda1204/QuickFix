package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
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
    private static final String USER_SELECT = "firebase_uid,name,email,phone,is_user,is_service_provider,is_admin";

    // Use centralized HTTP client for optimized connection pooling
    private static final OkHttpClient client = HttpClientProvider.getInstance();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static boolean isConfigured() {
        return SUPABASE_URL != null
                && !SUPABASE_URL.trim().isEmpty()
                && !SUPABASE_URL.contains("YOUR_")
                && ANON_KEY != null
                && !ANON_KEY.trim().isEmpty()
                && !ANON_KEY.contains("YOUR_");
    }

    private static boolean ensureConfigured(ApiCallback callback) {
        if (isConfigured()) return true;
        Log.e(TAG, "Supabase config is missing or invalid.");
        if (callback != null) {
            mainHandler.post(() -> callback.onError("App configuration error. Please contact support."));
        }
        return false;
    }

    private static boolean ensureConfigured(ArrayCallback callback) {
        if (isConfigured()) return true;
        Log.e(TAG, "Supabase config is missing or invalid.");
        if (callback != null) {
            mainHandler.post(() -> callback.onError("App configuration error. Please contact support."));
        }
        return false;
    }

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
        if (callback == null) return;
        if (!ensureConfigured(callback)) return;

        if (email == null || email.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Email must not be empty."));
            return;
        }

        String encoded;
        try {
            encoded = URLEncoder.encode(email.trim(), "UTF-8");
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Invalid email format."));
            return;
        }

        fetchSingleUserWithFallback("email=eq." + encoded, "getUserByEmail", callback);
    }

    // ── GET a user row by Firebase UID ───────────────────────────────────

    /**
     * Fetch user record from Supabase "users" table by Firebase UID.
     */
    public static void getUserByUid(String firebaseUid, ApiCallback callback) {
        if (callback == null) return;
        if (!ensureConfigured(callback)) return;

        if (firebaseUid == null || firebaseUid.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Firebase UID must not be empty."));
            return;
        }

        final String encodedUid;
        try {
            encodedUid = URLEncoder.encode(firebaseUid.trim(), "UTF-8");
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError("Invalid user id."));
            return;
        }

        fetchSingleUserWithFallback("firebase_uid=eq." + encodedUid, "getUserByUid", callback);
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
        if (callback == null) return;
        if (!ensureConfigured(callback)) return;
        if (body == null) {
            mainHandler.post(() -> callback.onError("Request body is required."));
            return;
        }

        String firebaseUid = body.optString("firebase_uid", "").trim();
        if (firebaseUid.isEmpty()) {
            mainHandler.post(() -> callback.onError("firebase_uid is required."));
            return;
        }

        if (!body.has("email")) {
            body.remove("email");
        }

        RequestBody reqBody = RequestBody.create(body.toString(), JSON);

        Request request = supabaseBuilder("/rest/v1/users")
                .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                .post(reqBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                NetworkHelper.FailureType type = NetworkHelper.classify(e);
                String msg = NetworkHelper.buildMessage(type, true);
                Log.e(TAG, "upsertUser failed type=" + type + " uid=" + firebaseUid + ": " + e.getMessage());
                mainHandler.post(() -> callback.onError(msg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String bodyStr = response.body() != null ? response.body().string() : "{}";
                response.close();
                Log.d(TAG, "upsertUser HTTP " + code + " uid=" + firebaseUid + " body=" + bodyStr);
                if (code == 200 || code == 201 || code == 204) {
                    mainHandler.post(() -> {
                        try { callback.onSuccess(new JSONObject("{\"success\":true}")); }
                        catch (Exception e) { callback.onSuccess(new JSONObject()); }
                    });
                } else {
                    String parsed = parseSupabaseError(bodyStr);
                    String message = buildHttpErrorMessage("upsert user", code, parsed);
                    mainHandler.post(() -> callback.onError(message));
                }
            }
        });
    }

    private static void fetchSingleUserWithFallback(String filter, String tag, ApiCallback callback) {
        String strictPath = "/rest/v1/users?" + filter + "&select=" + USER_SELECT + "&limit=1";
        Request strictRequest = supabaseBuilder(strictPath).get().build();

        executeArrayRequest(strictRequest, tag, result -> {
            if (result.length() > 0) {
                callback.onSuccess(result.optJSONObject(0));
                return;
            }
            callback.onError("USER_NOT_FOUND");
        }, error -> {
            if (!error.toLowerCase().contains("bad request")) {
                callback.onError(error);
                return;
            }

            String fallbackPath = "/rest/v1/users?" + filter + "&select=*&limit=1";
            Request fallbackRequest = supabaseBuilder(fallbackPath).get().build();
            executeArrayRequest(fallbackRequest, tag + "_fallback", fallbackResult -> {
                if (fallbackResult.length() > 0) {
                    JSONObject first = fallbackResult.optJSONObject(0);
                    callback.onSuccess(first != null ? first : new JSONObject());
                    return;
                }
                callback.onError("USER_NOT_FOUND");
            }, callback::onError);
        });
    }

    // ── Check Supabase reachability (call before login if needed) ────────

    /**
     * Ping Supabase to confirm the project is reachable.
     * Use before critical operations to give the user an early network error.
     */
    public static void checkReachability(Runnable onReachable, Runnable onUnreachable) {
        if (!isConfigured()) {
            mainHandler.post(onUnreachable);
            return;
        }
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
                    String parsed = parseSupabaseError(bodyStr);
                    String msg = buildHttpErrorMessage(tag, code, parsed);
                    mainHandler.post(() -> onError.accept(msg));
                }
            }
        });
    }

    private static String buildHttpErrorMessage(String operation, int code, String parsedBody) {
        if (code == 400) {
            return "Bad request while trying to " + operation + ". " + parsedBody;
        }
        if (code == 422) {
            return "Validation failed while trying to " + operation + ". " + parsedBody;
        }
        return "Supabase error (" + code + "): " + parsedBody;
    }

    private static String parseSupabaseError(String raw) {
        String safeRaw = raw != null ? raw.trim() : "";
        if (safeRaw.isEmpty()) return "Empty response body.";
        try {
            JSONObject obj = new JSONObject(safeRaw);
            String msg = obj.optString("message", "").trim();
            if (msg.isEmpty()) msg = obj.optString("error", "").trim();
            String details = obj.optString("details", "").trim();
            String hint = obj.optString("hint", "").trim();
            StringBuilder sb = new StringBuilder();
            if (!msg.isEmpty()) sb.append(msg);
            if (!details.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("Details: ").append(details);
            }
            if (!hint.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append("Hint: ").append(hint);
            }
            return sb.length() > 0 ? sb.toString() : safeRaw;
        } catch (Exception ignored) {
            return safeRaw;
        }
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
