package com.example.quickfix;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;

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
    private TextView tvEmergencyDistance;
    private TextView tvEmergencyDescription;
    private TextView tvEmergencyStatus;
    private MaterialButton btnAccept;
    private MaterialButton btnReject;
    private ProgressBar progress;
    private TextView tvTechnicianDetails;

    private String requestId = "";
    private String providerProfileId = "";
    private String providerFirebaseUid = "";
    private String providerName = "Provider";
    private String providerPhone = "";
    private String providerService = "";
    private String providerCity = "";
    private String providerArea = "";
    private double providerLatitude = Double.NaN;
    private double providerLongitude = Double.NaN;
    private double requestLatitude = Double.NaN;
    private double requestLongitude = Double.NaN;
    private String quickAction = "";
    private boolean quickActionConsumed = false;
    private boolean rejectedByCurrentProvider = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_emergency_request);

        bindViews();
        bindButtons();

        requestId = safe(getIntent() != null ? getIntent().getStringExtra("booking_id") : "");
        if (TextUtils.isEmpty(requestId)) {
            requestId = safe(getIntent() != null ? getIntent().getStringExtra("request_id") : "");
        }
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
        tvEmergencyDistance = findViewById(R.id.tvEmergencyDistance);
        tvEmergencyDescription = findViewById(R.id.tvEmergencyDescription);
        tvEmergencyStatus = findViewById(R.id.tvEmergencyStatus);
        btnAccept = findViewById(R.id.btnAcceptEmergency);
        btnReject = findViewById(R.id.btnRejectEmergency);
        progress = findViewById(R.id.progressEmergencyAccept);
        tvTechnicianDetails = findViewById(R.id.tvTechnicianDetails);
    }

    private void bindButtons() {
        if (btnReject != null) {
            btnReject.setOnClickListener(v -> rejectRequest());
        }
        if (btnAccept != null) {
            btnAccept.setOnClickListener(v -> showTechnicianDialog());
        }
    }

    private void showTechnicianDialog() {
        if (isFinishing() || isDestroyed()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_technician_details, null);
        EditText etDialogName = dialogView.findViewById(R.id.etDialogServicePersonName);
        EditText etDialogPhone = dialogView.findViewById(R.id.etDialogServicePersonPhone);

        // Removed pre-fill since EditTexts are gone

        new AlertDialog.Builder(this)
                .setTitle("Service Person Details")
                .setMessage("Please enter the details of the service person visiting the user.")
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Accept Request", (dialog, which) -> {
                    String tName = etDialogName.getText().toString().trim();
                    String tPhone = etDialogPhone.getText().toString().trim();

                    if (tName.isEmpty()) {
                        showToast("Enter service person name");
                        return;
                    }
                    if (!tPhone.matches("^[0-9]{10}$")) {
                        showToast("Enter valid 10-digit mobile number");
                        return;
                    }

                    acceptRequest(tName, tPhone);
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                providerLatitude = readLatitude(provider);
                providerLongitude = readLongitude(provider);

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
                + "&select=id,business_name,phone,category,city,area,latitude,longitude,lat,lng&limit=1";

        Request.Builder rb = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json");
        Request request = rb.build();

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
        // Query provider_emergency_requests joined with emergency_bookings
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_emergency_requests"
                + "?id=eq." + urlEncode(id)
                + "&select=*,emergency_bookings(*)&limit=1";

        Request.Builder rb = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json");
        Request request = rb.build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) {
                throw new IllegalStateException("Provider request lookup failed HTTP " + response.code());
            }
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) throw new IllegalStateException("Emergency request not found");
            
            JSONObject providerReq = arr.getJSONObject(0);
            activeBookingId = providerReq.optString("booking_id");
            JSONObject booking = providerReq.optJSONObject("emergency_bookings");
            if (booking == null) throw new IllegalStateException("Booking details missing");
            
            // Mirror some provider-specific info into the booking object for bindRequest
            booking.put("provider_request_id", providerReq.optString("id"));
            booking.put("provider_request_status", providerReq.optString("status"));
            
            return booking;
        }
    }

    private void bindRequest(JSONObject request) {
        if (request == null) return;
        setText(tvEmergencyUser, "User: " + fallback(request.optString("customer_name", ""), "Customer"));
        setText(tvEmergencyPhone, "Phone: " + fallback(request.optString("customer_phone", ""), "Not available"));
        setText(tvEmergencyCategory, "Category: " + fallback(request.optString("category", ""), "General"));
        requestLatitude = readLatitude(request);
        requestLongitude = readLongitude(request);
        String rawAddress = fallback(request.optString("address", ""), fallback(request.optString("location", ""), "Not available"));
        setText(tvEmergencyAddress, "Location: " + rawAddress);
        updateDistanceLabel();
        setText(tvEmergencyDescription, "Problem: " + fallback(request.optString("description", ""), "Not specified"));

        String status = safe(request.optString("status", "pending")).toLowerCase(Locale.getDefault());
        String statusLine = "Status: " + (status.isEmpty() ? "pending" : status);
        String assignedProviderId = safe(request.optString("accepted_by", ""));
        if ("accepted".equals(status) && !TextUtils.isEmpty(assignedProviderId)) {
            if (assignedProviderId.equals(providerProfileId)) {
                statusLine += " (You accepted this request)";
            } else {
                statusLine += " (Already assigned)";
            }
            
            if (tvTechnicianDetails != null) {
                String tName = request.optString("technician_name", "");
                String tPhone = request.optString("technician_phone", "");
                if (!tName.isEmpty() || !tPhone.isEmpty()) {
                    tvTechnicianDetails.setVisibility(View.VISIBLE);
                    tvTechnicianDetails.setText("Technician: " + (tName.isEmpty() ? "-" : tName) + "\nMobile: " + (tPhone.isEmpty() ? "-" : tPhone));
                }
            }
        } else {
            if (tvTechnicianDetails != null) {
                tvTechnicianDetails.setVisibility(View.GONE);
            }
        }
        setText(tvEmergencyStatus, statusLine);
    }

    private void updateDistanceLabel() {
        if (tvEmergencyDistance == null) return;

        if (Double.isNaN(providerLatitude) || Double.isNaN(providerLongitude)
                || Double.isNaN(requestLatitude) || Double.isNaN(requestLongitude)) {
            tvEmergencyDistance.setVisibility(View.GONE);
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(requestLatitude, requestLongitude, providerLatitude, providerLongitude, results);
        float distanceKm = results[0] / 1000f;

        tvEmergencyDistance.setVisibility(View.VISIBLE);
        tvEmergencyDistance.setText(String.format(Locale.getDefault(), "Distance: %.1f km away", distanceKm));
    }

    private void updateAcceptStateFromStatus(JSONObject request) {
        String status = safe(request.optString("status", "pending")).toLowerCase(Locale.getDefault());
        String assignedProviderId = safe(request.optString("accepted_by", ""));
        boolean alreadyClosed = !"pending".equals(status);
        boolean acceptedByOther = "accepted".equals(status)
                && !TextUtils.isEmpty(assignedProviderId)
                && !assignedProviderId.equals(providerProfileId);

        boolean enableAccept = !alreadyClosed && !rejectedByCurrentProvider;
        boolean enableReject = !alreadyClosed && !rejectedByCurrentProvider;

        if (acceptedByOther) {
            showToast("Already assigned to another provider.");
        }

        if (btnAccept != null && btnReject != null) {
            if ("pending".equals(status)) {
                btnAccept.setVisibility(View.VISIBLE);
                btnReject.setVisibility(View.VISIBLE);
                btnAccept.setEnabled(enableAccept);
                btnReject.setEnabled(enableReject);
            } else {
                btnAccept.setVisibility(View.GONE);
                btnReject.setVisibility(View.GONE);
            }
        }
    }

    private void rejectRequest() {
        updateBookingStatus("cancelled", null, null);
    }

    private void acceptRequest(String technicianName, String technicianPhone) {
        updateBookingStatus("accepted", technicianName, technicianPhone);
    }

    private void updateBookingStatus(String status, String technicianName, String technicianPhone) {
        if (TextUtils.isEmpty(requestId)) return;

        setLoading(true);
        new Thread(() -> {
            try {
                if ("accepted".equals(status)) {
                    // Call final atomic RPC for acceptance
                    JSONObject body = new JSONObject();
                    body.put("p_booking_id", activeBookingId);
                    body.put("p_provider_id", providerProfileId);

                    String url = BuildConfig.SUPABASE_URL + "/rest/v1/rpc/accept_emergency_booking";

                    Request req = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(body.toString(), JSON))
                            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Content-Type", "application/json")
                            .build();

                    try (Response res = http.newCall(req).execute()) {
                        String raw = res.body() != null ? res.body().string().trim() : "false";
                        String result = raw.replace("\"", "");

                        if ("success".equalsIgnoreCase(result)) {
                            runOnUiThread(() -> {
                                setLoading(false);
                                showToast("Accepted successfully");
                                btnAccept.setEnabled(false);
                                btnAccept.setText("✓ Accepted");
                                btnAccept.setAlpha(0.7f);
                                if (btnReject != null) btnReject.setVisibility(View.GONE);
                            });
                            updateTechnicianDetails(technicianName, technicianPhone);
                        } else {
                            runOnUiThread(() -> {
                                setLoading(false);
                                showToast(result.isEmpty() ? "Already accepted by another provider" : result);
                                btnAccept.setEnabled(false);
                                btnAccept.setText("Already accepted");
                                btnAccept.setAlpha(0.5f);
                                if (btnReject != null) btnReject.setVisibility(View.GONE);
                            });
                        }
                    }
                } else {
                    performRejection(status);
                }
            } catch (Exception e) {
                Log.e(TAG, "updateBookingStatus failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    showToast("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private String activeBookingId = "";

    private void updateTechnicianDetails(String name, String phone) {
        if (TextUtils.isEmpty(name) && TextUtils.isEmpty(phone)) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("technician_name", name);
                body.put("technician_phone", phone);
                
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/emergency_bookings?id=eq." + activeBookingId;
                Request req = new Request.Builder()
                        .url(url)
                        .patch(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();
                http.newCall(req).execute();
            } catch (Exception e) {
                Log.e(TAG, "updateTechnicianDetails failed", e);
            }
        }).start();
    }

    private void performRejection(String status) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", "rejected");
            // Reject the specific provider request
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_emergency_requests?id=eq." + requestId;
            Request req = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();
            http.newCall(req).execute();
            runOnUiThread(() -> {
                setLoading(false);
                showToast("Request Declined");
                finish();
            });
        } catch (Exception e) {
            Log.e(TAG, "performRejection failed", e);
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

    private void triggerQuickActionIfNeeded() {
        if (quickActionConsumed || TextUtils.isEmpty(quickAction)) return;
        quickActionConsumed = true;

        if ("accept".equals(quickAction)) {
            showTechnicianDialog();
            return;
        }

        if ("decline".equals(quickAction) || "reject".equals(quickAction)) {
            rejectRequest();
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

    /**
     * Notify user (customer) of booking status change via Supabase Edge Function.
     * Uses the user's Supabase ID (customer_id column in bookings).
     */
    private void notifyUserBookingUpdate(String customerId, String title, String messageBody, String bookingId) {
        if (TextUtils.isEmpty(customerId)) {
            Log.w(TAG, "notifyUserBookingUpdate: customerId is empty, skipping");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id",    customerId);       // resolved against users.id
                payload.put("type",       "booking_update");
                payload.put("title",      title);
                payload.put("body",       messageBody);
                payload.put("booking_id", bookingId);
                payload.put("reference_id", bookingId);

                String url = BuildConfig.SUPABASE_URL + "/functions/v1/send-notification";
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .build();

                try (Response r = http.newCall(request).execute()) {
                    String rb = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "notifyUserBookingUpdate HTTP " + r.code() + " " + rb);
                }
            } catch (Exception e) {
                Log.e(TAG, "notifyUserBookingUpdate failed: " + e.getMessage());
            }
        }).start();
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

    private double readLatitude(JSONObject o) {
        if (o == null) return Double.NaN;
        if (o.has("latitude") && !o.isNull("latitude")) return o.optDouble("latitude", Double.NaN);
        if (o.has("lat") && !o.isNull("lat")) return o.optDouble("lat", Double.NaN);
        return Double.NaN;
    }

    private double readLongitude(JSONObject o) {
        if (o == null) return Double.NaN;
        if (o.has("longitude") && !o.isNull("longitude")) return o.optDouble("longitude", Double.NaN);
        if (o.has("lng") && !o.isNull("lng")) return o.optDouble("lng", Double.NaN);
        return Double.NaN;
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
        if (!TextUtils.isEmpty(providerProfileId)) {
            return "provider_id=eq." + profile;
        }
        return "provider_id=eq." + firebase;
    }

    private String buildProviderAndNotFilter() {
        String profile = urlEncode(providerProfileId);
        String firebase = urlEncode(providerFirebaseUid);
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

        String nextRequestId = safe(intent.getStringExtra("booking_id"));
        if (TextUtils.isEmpty(nextRequestId)) {
            nextRequestId = safe(intent.getStringExtra("request_id"));
        }
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
