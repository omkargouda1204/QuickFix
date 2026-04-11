package com.example.quickfix;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

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
    private String providerName = "Provider";
    private String providerPhone = "";

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
            btnReject.setOnClickListener(v -> {
                showToast("Request dismissed.");
                finish();
            });
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

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject provider = fetchProviderByFirebaseUid(user.getUid());
                providerProfileId = safe(provider.optString("id", ""));
                providerName = safe(provider.optString("business_name", "Provider"));
                providerPhone = safe(provider.optString("phone", ""));

                JSONObject request = fetchRequestById(requestId);

                runOnUiThread(() -> {
                    setLoading(false);
                    bindRequest(request);
                    updateAcceptStateFromStatus(request);
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
                + "&select=id,business_name,phone&limit=1";

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
                + "&select=id,status,user_id,user_name,phone,category,description,address,assigned_provider_id,accepted_at"
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

        boolean enableAccept = !alreadyClosed;
        boolean enableReject = !alreadyClosed;

        if (acceptedByOther) {
            showToast("Already assigned to another provider.");
        }

        if (btnAccept != null) {
            btnAccept.setEnabled(enableAccept);
            btnAccept.setText(alreadyClosed ? "Already Assigned" : "Accept");
            btnAccept.setAlpha(enableAccept ? 1f : 0.65f);
        }
        if (btnReject != null) {
            btnReject.setEnabled(enableReject);
            btnReject.setAlpha(enableReject ? 1f : 0.65f);
        }
    }

    private void acceptRequest() {
        if (TextUtils.isEmpty(providerProfileId)) {
            showToast("Provider profile is missing.");
            return;
        }

        setLoading(true);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "accepted");
                body.put("assigned_provider_id", providerProfileId);
                body.put("accepted_at", isoNow());

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

                    JSONArray updatedRows = new JSONArray(responseBody);
                    if (updatedRows.length() == 0) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showToast("Already accepted by another provider.");
                        });
                        refreshStateAfterConflict();
                        return;
                    }

                    JSONObject updated = updatedRows.getJSONObject(0);
                    notifyUserAssigned(updated);
                    mirrorEmergencyBooking(updated);

                    runOnUiThread(() -> {
                        setLoading(false);
                        bindRequest(updated);
                        updateAcceptStateFromStatus(updated);
                        showToast("Emergency request accepted.");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "acceptRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("Could not accept request. Please try again.");
                });
            }
        }).start();
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

    private void notifyUserAssigned(JSONObject request) {
        String userFirebaseUid = safe(request.optString("user_id", ""));
        if (TextUtils.isEmpty(userFirebaseUid)) return;

        try {
            JSONObject data = new JSONObject();
            data.put("request_id", requestId);
            data.put("provider_name", fallback(providerName, "Provider"));
            data.put("phone", fallback(providerPhone, ""));
            data.put("eta", "Arriving in 10 mins");

            FcmNotificationHelper.notifyEmergencyUserAssigned(
                    userFirebaseUid,
                    "Provider Assigned",
                    fallback(providerName, "Provider") + " is on the way.",
                    requestId,
                    data
            );
        } catch (Exception e) {
            Log.w(TAG, "notifyUserAssigned failed: " + e.getMessage());
        }
    }

    private void mirrorEmergencyBooking(JSONObject request) {
        try {
             String customerId = safe(request.optString("user_id", ""));
            String providerId = safe(providerProfileId);
            if (TextUtils.isEmpty(customerId) || TextUtils.isEmpty(providerId)) return;

            JSONObject booking = new JSONObject();
            booking.put("customer_id", customerId);
            booking.put("provider_id", providerId);
            booking.put("customer_name", fallback(request.optString("user_name", ""), "Customer"));
            booking.put("customer_phone", safe(request.optString("phone", "")));
            booking.put("provider_name", fallback(providerName, "Provider"));
            booking.put("service_name", fallback(request.optString("category", ""), "Emergency"));
            booking.put("category", fallback(request.optString("category", ""), "Emergency"));
            booking.put("address", safe(request.optString("address", "")));
            booking.put("description", safe(request.optString("description", "")));
            booking.put("status", "accepted");
            booking.put("is_emergency", true);

            Request insert = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/bookings")
                    .post(RequestBody.create(booking.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            try (Response response = http.newCall(insert).execute()) {
                if (response.code() == 200 || response.code() == 201 || response.code() == 204) return;
            }

            booking.remove("is_emergency");
            booking.put("booking_date", new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(new java.util.Date()));
            booking.put("booking_time", new java.text.SimpleDateFormat("HH:mm", Locale.US)
                    .format(new java.util.Date()));

            Request fallbackInsert = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/bookings")
                    .post(RequestBody.create(booking.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();
            try (Response fallbackResponse = http.newCall(fallbackInsert).execute()) {
                // Keep acceptance successful even if booking mirror fails.
                if (fallbackResponse.code() >= 400) {
                    Log.w(TAG, "mirrorEmergencyBooking fallback HTTP " + fallbackResponse.code());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "mirrorEmergencyBooking failed: " + e.getMessage());
        }
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
            return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }
}
