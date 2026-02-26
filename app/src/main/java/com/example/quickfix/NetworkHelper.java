package com.example.quickfix;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NetworkHelper — centralised network-aware error handling for QuickFix.
 *
 * Features:
 *  - Offline detection via ConnectivityManager (no internet at all)
 *  - ISP/DNS timeout detection (internet exists but Supabase unreachable)
 *  - Separate human-readable messages per failure category
 *  - checkAndRetry: automatic 2-second delayed retry up to a max count
 *  - All callbacks delivered on the MAIN thread
 */
public class NetworkHelper {

    private static final String TAG = "NetworkHelper";

    /** Maximum automatic retries before giving up. */
    public static final int MAX_RETRIES = 3;

    /** Delay in milliseconds between automatic retries. */
    public static final long RETRY_DELAY_MS = 2_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // OkHttpClient with aggressive timeouts for reachability checks
    private static final OkHttpClient PING_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8,    TimeUnit.SECONDS)
            .writeTimeout(8,   TimeUnit.SECONDS)
            .build();

    // ── Failure categories ────────────────────────────────────────────────

    public enum FailureType {
        /** Device has no active network at all (WiFi off, mobile data off). */
        NO_INTERNET,
        /**
         * Device is on a network but the Supabase host cannot be reached.
         * Typical cause: ISP DNS block, captive portal, or VPN needed.
         */
        ISP_OR_DNS,
        /**
         * TCP connection opened but timed out waiting for a response.
         * Typical cause: slow/throttled connection or temporary server hiccup.
         */
        TIMEOUT,
        /** Connection was actively refused (e.g. Supabase project paused). */
        REFUSED,
        /** Any other IOException we don't have a specific bucket for. */
        UNKNOWN
    }

    // ── Callback interfaces ───────────────────────────────────────────────

    public interface ReachabilityCallback {
        /** Called when Supabase is reachable (any HTTP response). */
        void onReachable();

        /**
         * Called when Supabase is NOT reachable.
         *
         * @param type    category of the failure
         * @param message human-readable message suitable for display
         */
        void onUnreachable(FailureType type, String message);
    }

    public interface RetryCallback {
        /** Called each time a retry attempt starts. */
        void onRetrying(int attemptNumber, int maxAttempts);

        /** Called when a retry attempt succeeds. */
        void onSuccess();

        /**
         * Called after all retry attempts have been exhausted.
         *
         * @param type    category of the final failure
         * @param message human-readable message suitable for display
         */
        void onFailed(FailureType type, String message);
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the device currently has an active network
     * connection (WiFi or mobile data), {@code false} otherwise.
     *
     * Uses NetworkCapabilities on API 23+ and falls back to the legacy
     * activeNetworkInfo on older devices.
     */
    @SuppressWarnings("deprecation")
    public static boolean isOnline(Context context) {
        if (context == null) return false;
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(net);
            return caps != null
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    /**
     * Classify an IOException into a {@link FailureType}.
     */
    public static FailureType classify(IOException e) {
        if (e instanceof SocketTimeoutException) return FailureType.TIMEOUT;
        if (e instanceof UnknownHostException)   return FailureType.ISP_OR_DNS;
        if (e instanceof ConnectException) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("refused")) return FailureType.REFUSED;
            return FailureType.ISP_OR_DNS;
        }
        return FailureType.UNKNOWN;
    }

    /**
     * Build a human-readable error message for a network failure.
     *
     * @param type    failure category (from {@link #classify(IOException)})
     * @param online  whether the device has internet according to ConnectivityManager
     */
    public static String buildMessage(FailureType type, boolean online) {
        if (!online) {
            return "No internet connection. Please enable WiFi or mobile data and try again.";
        }
        switch (type) {
            case ISP_OR_DNS:
                return "Unable to reach server. This may be a network provider (ISP) or DNS issue. "
                        + "Please try switching to mobile data, using a VPN, or try again later.";
            case TIMEOUT:
                return "Connection timed out. The server took too long to respond. "
                        + "Please check your connection speed and try again.";
            case REFUSED:
                return "Connection refused. The Supabase project may be paused or under maintenance. "
                        + "Please try again later.";
            default:
                return "Unable to reach server. This may be a network provider issue. "
                        + "Please try mobile data, VPN, or try again later.";
        }
    }

    /**
     * Build a message from a raw IOException and a Context (for online check).
     */
    public static String buildMessage(IOException e, Context context) {
        boolean online = isOnline(context);
        if (!online) return buildMessage(FailureType.NO_INTERNET, false);
        FailureType type = classify(e);
        return buildMessage(type, true);
    }

    /**
     * Ping Supabase once and report reachability.
     *
     * @param url      full Supabase REST root URL (e.g. https://xxx.supabase.co/rest/v1/)
     * @param apiKey   Supabase anon key
     * @param context  used for offline check before making the request
     * @param callback result on the main thread
     */
    public static void checkReachability(String url, String apiKey,
                                          Context context,
                                          ReachabilityCallback callback) {
        // Fast-fail if there's no internet at all
        if (!isOnline(context)) {
            MAIN.post(() -> callback.onUnreachable(
                    FailureType.NO_INTERNET,
                    buildMessage(FailureType.NO_INTERNET, false)));
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", "Bearer " + apiKey)
                .get()
                .build();

        Log.d(TAG, "Pinging Supabase: " + url);

        PING_CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                FailureType type = classify(e);
                String msg = buildMessage(type, true);
                Log.w(TAG, "Supabase ping FAILED type=" + type + " err=" + e.getMessage());
                MAIN.post(() -> callback.onUnreachable(type, msg));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                response.close();
                Log.d(TAG, "Supabase ping OK HTTP " + code);
                // Any HTTP response means the server is reachable
                MAIN.post(callback::onReachable);
            }
        });
    }

    /**
     * Attempt an action with automatic retries on network failure.
     *
     * Usage:
     * <pre>
     *   NetworkHelper.checkAndRetry(
     *       supabaseUrl, apiKey, context,
     *       maxRetries,
     *       new RetryCallback() { ... }
     *   );
     * </pre>
     *
     * @param url        Supabase REST root URL
     * @param apiKey     Supabase anon key
     * @param context    for online check
     * @param maxRetries max additional attempts after the first failure
     * @param callback   progress / result callback on main thread
     */
    public static void checkAndRetry(String url, String apiKey,
                                      Context context,
                                      int maxRetries,
                                      RetryCallback callback) {
        AtomicBoolean succeeded = new AtomicBoolean(false);
        retryInternal(url, apiKey, context, 1, maxRetries + 1, succeeded, callback);
    }

    // ── Internal retry loop ───────────────────────────────────────────────

    private static void retryInternal(String url, String apiKey,
                                       Context context,
                                       int attempt,
                                       int total,
                                       AtomicBoolean succeeded,
                                       RetryCallback callback) {
        if (succeeded.get()) return;

        MAIN.post(() -> callback.onRetrying(attempt, total));

        checkReachability(url, apiKey, context, new ReachabilityCallback() {
            @Override
            public void onReachable() {
                if (succeeded.compareAndSet(false, true)) {
                    Log.d(TAG, "Reachable on attempt " + attempt);
                    MAIN.post(callback::onSuccess);
                }
            }

            @Override
            public void onUnreachable(FailureType type, String message) {
                Log.w(TAG, "Attempt " + attempt + "/" + total
                        + " failed type=" + type);

                if (attempt >= total) {
                    // All retries exhausted
                    Log.e(TAG, "All " + total + " attempts failed. Giving up.");
                    MAIN.post(() -> callback.onFailed(type, message));
                } else {
                    // Schedule next retry
                    MAIN.postDelayed(() ->
                            retryInternal(url, apiKey, context,
                                    attempt + 1, total, succeeded, callback),
                            RETRY_DELAY_MS);
                }
            }
        });
    }
}

