package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.quickfix.BuildConfig;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProviderEmergencyRequestActivity extends BaseActivity {

    private static final String TAG = "ProviderEmergency";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long STATUS_POLL_MS = 4000L;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView tvEmergencyUser;
    private TextView tvEmergencyPhone;
    private TextView tvEmergencyCategory;
    private TextView tvEmergencyAddress;
    private TextView tvEmergencyDescription;
    private TextView tvEmergencyStatus;
    private MaterialButton btnAccept;
    private MaterialButton btnReject;
    private ProgressBar progress;

    private String requestId = "";
    private String providerProfileId = "";
    private String providerFirebaseUid = "";
    private String providerName = "Provider";
    private String providerPhone = "";
    private String providerService = "";
    private String providerCity = "";
    private String providerArea = "";
    private String quickAction = "";
    private boolean quickActionConsumed = false;
    private boolean rejectedByCurrentProvider = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_emergency_request);

        bindViews();
        bindButtons();

        requestId = safe(getIntent() != null ? getIntent().getStringExtra("request_id") : "");
        if (TextUtils.isEmpty(requestId)) {
            requestId = safe(getIntent() != null ? getIntent().getStringExtra("reference_id") : "");
        }
        quickAction = safe(getIntent() != null ? getIntent().getStringExtra("quick_action") : "").toLowerCase(Locale.US);
        if (TextUtils.isEmpty(requestId)) {
            showToast("Emergency request id is missing.");
            finish();
            return;
        }

        fetchProviderProfileAndRequest();
    }

    private void bindViews() {
        tvEmergencyUser = findViewById(R.id.tvEmergencyUser);
        tvEmergencyPhone = findViewById(R.id.tvEmergencyPhone);
        tvEmergencyCategory = findViewById(R.id.tvEmergencyCategory);
        tvEmergencyAddress = findViewById(R.id.tvEmergencyAddress);
        tvEmergencyDescription = findViewById(R.id.tvEmergencyDescription);
        tvEmergencyStatus = findViewById(R.id.tvEmergencyStatus);
        btnAccept = findViewById(R.id.btnAcceptEmergency);
        btnReject = findViewById(R.id.btnRejectEmergency);
        progress = findViewById(R.id.progressEmergencyAccept);
    }

    private void bindButtons() {
        if (btnReject != null) {
            btnReject.setOnClickListener(v -> rejectRequest());
        }
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> acceptRequest());
        }
    }

    private void fetchProviderProfileAndRequest() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            showToast("Please login again.");
            finish();
            return;
        }
        providerFirebaseUid = safe(user.getUid());

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject provider = fetchProviderByFirebaseUid(user.getUid());
                providerProfileId = safe(provider.optString("id", ""));
                providerName = safe(provider.optString("business_name", "Provider"));
                providerPhone = safe(provider.optString("phone", ""));
                providerService = safe(provider.optString("category", ""));
                providerCity = safe(provider.optString("city", ""));
                providerArea = safe(provider.optString("area", ""));

                JSONObject request = fetchRequestById(requestId);

                runOnUiThread(() -> {
                    setLoading(false);
                    bindRequest(request);
                    updateAcceptStateFromStatus(request);
                    startStatusPolling();
                    triggerQuickActionIfNeeded();
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchProviderProfileAndRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("Could not load emergency request.");
                    finish();
                });
            }
        }).start();
    }

    private JSONObject fetchProviderByFirebaseUid(String firebaseUid) throws Exception {
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles"
                + "?firebase_uid=eq." + urlEncode(firebaseUid)
                + "&select=id,business_name,phone,category,city,area&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) {
                throw new IllegalStateException("Provider lookup failed HTTP " + response.code() + " " + body);
            }
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) throw new IllegalStateException("Provider profile not found");
            return arr.getJSONObject(0);
        }
    }

    private JSONObject fetchRequestById(String id) throws Exception {
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests"
                + "?id=eq." + urlEncode(id)
                + "&select=id,status,user_id,user_firebase_uid,user_name,phone,category,description,address,type,assigned_provider_id,accepted_at"
                + "&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) {
                throw new IllegalStateException("Request lookup failed HTTP " + response.code());
            }
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) throw new IllegalStateException("Emergency request not found");
            return arr.getJSONObject(0);
        }
    }

    private void bindRequest(JSONObject request) {
        if (request == null) return;
        setText(tvEmergencyUser, "User: " + fallback(request.optString("user_name", ""), "Customer"));
        setText(tvEmergencyPhone, "Phone: " + fallback(request.optString("phone", ""), "Not available"));
        setText(tvEmergencyCategory, "Category: " + fallback(request.optString("category", ""), "General"));
        setText(tvEmergencyAddress, "Location: " + fallback(request.optString("address", ""), "Not available"));
        setText(tvEmergencyDescription, "Problem: " + fallback(request.optString("description", ""), "Not specified"));

        String status = safe(request.optString("status", "pending")).toLowerCase(Locale.getDefault());
        String statusLine = "Status: " + (status.isEmpty() ? "pending" : status);
        String assignedProviderId = safe(request.optString("assigned_provider_id", ""));
        if ("accepted".equals(status) && !TextUtils.isEmpty(assignedProviderId)) {
            if (assignedProviderId.equals(providerProfileId)) {
                statusLine += " (You accepted this request)";
            } else {
                statusLine += " (Already assigned)";
            }
        }
        setText(tvEmergencyStatus, statusLine);
    }

    private void updateAcceptStateFromStatus(JSONObject request) {
        String status = safe(request.optString("status", "pending")).toLowerCase(Locale.getDefault());
        String assignedProviderId = safe(request.optString("assigned_provider_id", ""));
        boolean alreadyClosed = !"pending".equals(status);
        boolean acceptedByOther = "accepted".equals(status)
                && !TextUtils.isEmpty(assignedProviderId)
                && !assignedProviderId.equals(providerProfileId);

        boolean enableAccept = !alreadyClosed && !rejectedByCurrentProvider;
        boolean enableReject = !alreadyClosed && !rejectedByCurrentProvider;

        if (acceptedByOther) {
            showToast("Already assigned to another provider.");
        }

        if (btnAccept != null) {
            btnAccept.setEnabled(enableAccept);
            btnAccept.setText(alreadyClosed ? "Already Assigned" : (rejectedByCurrentProvider ? "Rejected" : "Accept"));
            btnAccept.setAlpha(enableAccept ? 1f : 0.65f);
        }
        if (btnReject != null) {
            btnReject.setEnabled(enableReject);
            btnReject.setText(rejectedByCurrentProvider ? "Rejected" : "Reject");
            btnReject.setAlpha(enableReject ? 1f : 0.65f);
        }
    }

    private void rejectRequest() {
        if (TextUtils.isEmpty(providerProfileId) && TextUtils.isEmpty(providerFirebaseUid)) {
            showToast("Provider profile is missing.");
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                updateMyEmergencyBookingStatus(requestId, "rejected");
                upsertProviderResponse("rejected");
                rejectedByCurrentProvider = true;

                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("Request rejected.");
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "rejectRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("Could not reject request.");
                });
            }
        }).start();
    }

        private void updateMyEmergencyBookingStatus(String emergencyId, String status) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", status);

            String providerFilter = buildProviderOrFilter();
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/bookings"
                    + "?emergency_request_id=eq." + urlEncode(emergencyId)
                + "&" + providerFilter
                    + "&status=eq.pending";

            Request request = new Request.Builder()
                    .url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                    .build();

            http.newCall(request).execute().close();
        } catch (Exception ignored) {}
    }

    private void acceptRequest() {
        if (TextUtils.isEmpty(providerProfileId)) {
            showToast("Provider profile is missing.");
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject updated;
                boolean rpcAccepted = acceptEmergencyWithRpc();
                if (rpcAccepted) {
                    updated = fetchRequestById(requestId);
                } else {
                    JSONArray updatedRows = acceptPendingRequestWithSnapshotFallback();
                    if (updatedRows.length() == 0) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showToast("Already accepted by another provider.");
                        });
                        refreshStateAfterConflict();
                        return;
                    }

                    updated = updatedRows.getJSONObject(0);
                    // Update THIS provider's booking record to 'accepted'
                    updateMyBookingToAccepted(requestId);
                    // Auto reject other providers' pending bookings
                    cleanupOtherEmergencyBookings(requestId);
                }

                rejectedByCurrentProvider = false;
                
                upsertProviderResponse("accepted");
                notifyUserAssigned(updated);
                // mirrorEmergencyBooking(updated); // Not needed anymore as we create them beforehand

                runOnUiThread(() -> {
                    setLoading(false);
                    bindRequest(updated);
                    updateAcceptStateFromStatus(updated);
                    showToast("Emergency request accepted!");
                });
            } catch (Exception e) {
                Log.e(TAG, "acceptRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast(extractErrorMessage(e, "Could not accept request. Please try again."));
                });
            }
        }).start();
    }

    private boolean acceptEmergencyWithRpc() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("p_request_id", requestId);
            payload.put("p_provider_id", safe(providerProfileId));
            payload.put("p_provider_firebase_uid", safe(providerFirebaseUid));
            payload.put("p_provider_name", fallback(providerName, "Provider"));
            payload.put("p_provider_phone", fallback(providerPhone, ""));
            payload.put("p_provider_service", fallback(providerService, ""));
            payload.put("p_provider_city", fallback(providerCity, ""));
            payload.put("p_provider_area", fallback(providerArea, ""));

            Request request = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/accept_emergency_request_atomic")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.code() != 200) {
                    Log.w(TAG, "accept_emergency_request_atomic RPC HTTP " + response.code() + " " + body);
                    return false;
                }

                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return false;
                JSONObject first = arr.getJSONObject(0);
                return first.optBoolean("accepted", false);
            }
        } catch (Exception e) {
            Log.w(TAG, "acceptEmergencyWithRpc fallback: " + e.getMessage());
            return false;
        }
    }

    private void updateMyBookingToAccepted(String emergencyId) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "accepted");
            body.put("provider_name", providerName != null ? providerName : "Provider");
            body.put("provider_phone", providerPhone != null ? providerPhone : "");
            if (!TextUtils.isEmpty(providerProfileId)) {
                body.put("provider_id", providerProfileId);
            }

            String providerFilter = buildProviderOrFilter();
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/bookings"
                    + "?emergency_request_id=eq." + urlEncode(emergencyId)
                    + "&" + providerFilter;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "updateMyBookingToAccepted failed: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "updateMyBookingToAccepted error: " + e.getMessage());
        }
    }

    private void cleanupOtherEmergencyBookings(String emergencyId) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "rejected");

            String exclusion = buildProviderAndNotFilter();
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/bookings"
                    + "?emergency_request_id=eq." + urlEncode(emergencyId)
                + "&" + exclusion
                + "&status=eq.pending";

            Request request = new Request.Builder()
                    .url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "cleanupOtherEmergencyBookings failed: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "cleanupOtherEmergencyBookings error: " + e.getMessage());
        }
    }

    private void triggerQuickActionIfNeeded() {
        if (quickActionConsumed || TextUtils.isEmpty(quickAction)) return;
        quickActionConsumed = true;

        if ("accept".equals(quickAction)) {
            acceptRequest();
            return;
        }

        if ("decline".equals(quickAction) || "reject".equals(quickAction)) {
            rejectRequest();
        }
    }

    private JSONArray acceptPendingRequestWithSnapshotFallback() throws Exception {
        JSONObject withSnapshot = new JSONObject();
        withSnapshot.put("status", "accepted");
        withSnapshot.put("assigned_provider_id", providerProfileId);
        withSnapshot.put("accepted_at", isoNow());
        withSnapshot.put("assigned_provider_name", fallback(providerName, "Provider"));
        withSnapshot.put("assigned_provider_phone", fallback(providerPhone, ""));
        withSnapshot.put("assigned_provider_service", fallback(providerService, ""));
        withSnapshot.put("assigned_provider_city", fallback(providerCity, ""));
        withSnapshot.put("assigned_provider_area", fallback(providerArea, ""));

        try {
            return patchPendingRequest(withSnapshot);
        } catch (IllegalStateException e) {
            String message = safe(e.getMessage()).toLowerCase(Locale.US);
            boolean snapshotColumnMissing = message.contains("assigned_provider_")
                    || message.contains("column")
                    || message.contains("schema cache");
            if (!snapshotColumnMissing) {
                throw e;
            }

            JSONObject legacy = new JSONObject();
            legacy.put("status", "accepted");
            legacy.put("assigned_provider_id", providerProfileId);
            legacy.put("accepted_at", isoNow());
            return patchPendingRequest(legacy);
        }
    }

    private JSONArray patchPendingRequest(JSONObject body) throws Exception {
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests"
                + "?id=eq." + urlEncode(requestId)
                + "&status=eq.pending";

        Request request = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) {
                throw new IllegalStateException("Accept failed HTTP " + response.code() + " " + responseBody);
            }
            return new JSONArray(responseBody);
        }
    }

    private void refreshStateAfterConflict() {
        try {
            JSONObject latest = fetchRequestById(requestId);
            runOnUiThread(() -> {
                bindRequest(latest);
                updateAcceptStateFromStatus(latest);
            });
        } catch (Exception e) {
            Log.w(TAG, "refreshStateAfterConflict failed: " + e.getMessage());
        }
    }

    private void startStatusPolling() {
        mainHandler.removeCallbacks(statusPollingRunnable);
        mainHandler.postDelayed(statusPollingRunnable, STATUS_POLL_MS);
    }

    private final Runnable statusPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) return;
            new Thread(() -> {
                try {
                    JSONObject latest = fetchRequestById(requestId);
                    runOnUiThread(() -> {
                        bindRequest(latest);
                        updateAcceptStateFromStatus(latest);
                    });
                } catch (Exception e) {
                    Log.w(TAG, "status polling failed: " + e.getMessage());
                } finally {
                    mainHandler.postDelayed(this, STATUS_POLL_MS);
                }
            }).start();
        }
    };

    private void upsertProviderResponse(String responseStatus) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("request_id", requestId);
            payload.put("provider_id", providerProfileId);
            payload.put("provider_firebase_uid", FirebaseAuth.getInstance().getCurrentUser() != null
                    ? safe(FirebaseAuth.getInstance().getCurrentUser().getUid()) : "");
            payload.put("response", safe(responseStatus));
            payload.put("responded_at", isoNow());

            Request request = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_request_responses")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (response.code() == 404) {
                    Log.w(TAG, "emergency_request_responses table missing; skipping response upsert");
                    return;
                }
                if (response.code() >= 400) {
                    String body = response.body() != null ? response.body().string() : "";
                    Log.w(TAG, "upsertProviderResponse HTTP " + response.code() + " " + body);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "upsertProviderResponse failed: " + e.getMessage());
        }
    }

    private void notifyUserAssigned(JSONObject request) {
        String userRef = safe(request.optString("user_firebase_uid", ""));
        if (TextUtils.isEmpty(userRef)) {
            userRef = safe(request.optString("user_id", ""));
        }
        if (TextUtils.isEmpty(userRef)) return;

        try {
            JSONObject data = new JSONObject();
            data.put("request_id", requestId);
            data.put("provider_name", fallback(providerName, "Provider"));
            data.put("provider_phone", fallback(providerPhone, ""));
            data.put("provider_service", fallback(providerService, ""));
            data.put("provider_city", fallback(providerCity, ""));
            data.put("provider_area", fallback(providerArea, ""));
            data.put("phone", fallback(providerPhone, ""));
            data.put("eta", "Arriving in 10 mins");

            FcmNotificationHelper.notifyEmergencyUserAssigned(
                    userRef,
                    "Provider Assigned",
                    fallback(providerName, "Provider") + " is on the way.",
                    requestId,
                    data
            );
        } catch (Exception e) {
            Log.w(TAG, "notifyUserAssigned failed: " + e.getMessage());
        }
    }

    private String extractErrorMessage(Throwable throwable, String fallback) {
        String msg = safe(throwable != null ? throwable.getMessage() : "");
        return TextUtils.isEmpty(msg) ? fallback : msg;
    }

    private void setLoading(boolean loading) {
        runOnUiThread(() -> {
            if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (btnAccept != null) btnAccept.setEnabled(!loading);
            if (btnReject != null) btnReject.setEnabled(!loading);
        });
    }

    private void setText(TextView view, String value) {
        if (view == null) return;
        view.setText(value);
    }

    private String fallback(String value, String fallback) {
        String safe = safe(value);
        return safe.isEmpty() ? fallback : safe;
    }

    private String isoNow() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(new java.util.Date());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }


    private void showToast(String message) {
        if (TextUtils.isEmpty(message)) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private String buildProviderOrFilter() {
        String profile = urlEncode(providerProfileId);
        String firebase = urlEncode(providerFirebaseUid);
        if (!TextUtils.isEmpty(providerProfileId) && !TextUtils.isEmpty(providerFirebaseUid)
                && !providerProfileId.equals(providerFirebaseUid)) {
            return "or=(provider_id.eq." + profile + ",provider_id.eq." + firebase + ")";
        }
        if (!TextUtils.isEmpty(providerProfileId)) {
            return "provider_id=eq." + profile;
        }
        return "provider_id=eq." + firebase;
    }

    private String buildProviderAndNotFilter() {
        String profile = urlEncode(providerProfileId);
        String firebase = urlEncode(providerFirebaseUid);
        if (!TextUtils.isEmpty(providerProfileId) && !TextUtils.isEmpty(providerFirebaseUid)
                && !providerProfileId.equals(providerFirebaseUid)) {
            return "and=(provider_id.neq." + profile + ",provider_id.neq." + firebase + ")";
        }
        if (!TextUtils.isEmpty(providerProfileId)) {
            return "provider_id=neq." + profile;
        }
        return "provider_id=neq." + firebase;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(@androidx.annotation.NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        setIntent(intent);

        String nextRequestId = safe(intent.getStringExtra("request_id"));
        if (TextUtils.isEmpty(nextRequestId)) {
            nextRequestId = safe(intent.getStringExtra("reference_id"));
        }
        String nextQuickAction = safe(intent.getStringExtra("quick_action")).toLowerCase(Locale.US);

        if (!TextUtils.isEmpty(nextRequestId) && !nextRequestId.equals(requestId)) {
            requestId = nextRequestId;
            quickAction = nextQuickAction;
            quickActionConsumed = false;
            rejectedByCurrentProvider = false;
            fetchProviderProfileAndRequest();
            return;
        }

        if (!TextUtils.isEmpty(nextQuickAction)) {
            quickAction = nextQuickAction;
            quickActionConsumed = false;
            triggerQuickActionIfNeeded();
        }
    }
}
