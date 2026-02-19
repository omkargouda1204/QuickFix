package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ApiClient — centralised HTTP client for all backend calls.
 *
 * ──────────────────────────────────────────────────────────────
 *  BASE_URL SETUP:
 *
 *  If running on a physical Android device on the same Wi-Fi
 *  network as your PC:
 *    BASE_URL = "http://YOUR_PC_LOCAL_IP:5000"   (e.g. 192.168.1.5)
 *
 *  If running on Android Emulator on the same PC:
 *    BASE_URL = "http://10.0.2.2:5000"
 *
 *  Find your PC IP:  run  ipconfig  in CMD → look for IPv4 Address
 * ──────────────────────────────────────────────────────────────
 */
public class ApiClient {

    // ── CHANGE THIS to your PC's local IP if testing on a real device ──
    public static final String BASE_URL = "http://10.249.219.177:5000";
    // ───────────────────────────────────────────────────────────────────

    private static final String TAG      = "ApiClient";
    private static final MediaType JSON  = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ── Callback interface ────────────────────────────────────────────
    public interface ApiCallback {
        /** Called on the main thread with the parsed JSON response. */
        void onSuccess(JSONObject response);
        /** Called on the main thread with a human-readable error message. */
        void onError(String errorMessage);
    }

    // ── POST helper ───────────────────────────────────────────────────

    /**
     * Make a POST request to the backend.
     *
     * @param endpoint  e.g. "/api/auth/signup"
     * @param body      JSON body as JSONObject
     * @param callback  result callback (always delivered on the main thread)
     */
    public static void post(String endpoint, JSONObject body, ApiCallback callback) {
        String url = BASE_URL + endpoint;
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept",       "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "POST " + endpoint + " failed: " + e.getMessage());
                String msg = buildNetworkErrorMessage(e.getMessage());
                mainHandler.post(() -> callback.onError(msg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "{}";
                Log.d(TAG, "POST " + endpoint + " → " + response.code() + " | " + bodyStr);

                try {
                    JSONObject json = new JSONObject(bodyStr);
                    if (response.isSuccessful()) {
                        mainHandler.post(() -> callback.onSuccess(json));
                    } else {
                        // Extract message from error response
                        String msg = json.optString("message", "Request failed (" + response.code() + ")");
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } catch (Exception parseEx) {
                    Log.e(TAG, "JSON parse error: " + parseEx.getMessage());
                    mainHandler.post(() -> callback.onError("Unexpected server response. Please try again."));
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────

    private static String buildNetworkErrorMessage(String rawMsg) {
        if (rawMsg == null) return "Network error. Check your connection.";
        if (rawMsg.contains("Failed to connect") || rawMsg.contains("Connection refused")) {
            return "Cannot connect to server. Make sure the backend is running and BASE_URL is correct.";
        }
        if (rawMsg.contains("timeout") || rawMsg.contains("timed out")) {
            return "Connection timed out. Please try again.";
        }
        return "Network error: " + rawMsg;
    }
}

