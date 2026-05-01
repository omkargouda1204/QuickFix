package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EmergencyHelpActivity extends BaseActivity {

    private static final String TAG = "EmergencyHelp";
    private static final int LOCATION_PERMISSION_REQ = 2301;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final List<String> EMERGENCY_CATEGORIES = Arrays.asList(
            "Electrician", "Plumber", "AC Service", "Carpentry",
            "Cleaning", "Appliance Repair", "Painting", "General Repair"
    );

    private static final long STATUS_POLL_MS = 4000L;
    private static final double NEARBY_RADIUS_KM = 25d;
    private static final int PROVIDER_PAGE_SIZE = 500;

    public static final String EXTRA_LOCATION_CITY = "extra_location_city";
    public static final String EXTRA_LOCATION_AREA = "extra_location_area";
    public static final String EXTRA_LOCATION_LAT = "extra_location_lat";
    public static final String EXTRA_LOCATION_LNG = "extra_location_lng";
    public static final String EXTRA_REQUEST_ID = "request_id";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppLocationManager locationManager;

    private ImageView btnBack;
    private MaterialButton btnSubmit, btnSelectLocation, btnAcceptProvider, btnRejectProvider;
    private ProgressBar progress;
    private TextView tvStatus, tvName, tvPhone, tvAddress, tvLocationSummary, tvLocationHint;
    private MaterialAutoCompleteTextView actCategory;
    private TextInputEditText etDescription;
    private TextInputLayout tilCategory, tilDescription;
    private ChipGroup chipGroup;

    private View cardProviderAssigned;
    private TextView tvProviderName, tvProviderPhone, tvProviderService, tvProviderLocation;

    private String currentUserId = "", currentUserName = "", currentUserPhone = "";
    private String selectedCity = "", selectedArea = "";
    private String selectedAddress = "", selectedPincode = "";
    private double selectedLat = Double.NaN, selectedLng = Double.NaN;
    private String activeRequestId = "";
    private boolean isSubmitting = false;
    private boolean pendingOpenLocationPicker = false;

    private static final class NearbyProvider {
        final String providerId;
        final String firebaseUid;

        NearbyProvider(String providerId, String firebaseUid) {
            this.providerId = providerId == null ? "" : providerId.trim();
            this.firebaseUid = firebaseUid == null ? "" : firebaseUid.trim();
        }

        boolean isValid() {
            return !providerId.isEmpty();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_help);

        locationManager = new AppLocationManager(this);
        bindViews();
        setupCategoryInput();
        setupClicks();
        hydrateLocation();
        loadProfile();
        setupFormValidation();
        updateSubmitButtonState();
        refreshLocationState();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnSubmit = findViewById(R.id.btnEmergencyRequest);
        btnSelectLocation = findViewById(R.id.btnEmergencySelectLocation);
        btnAcceptProvider = findViewById(R.id.btnAcceptProvider);
        btnRejectProvider = findViewById(R.id.btnRejectProvider);
        progress = findViewById(R.id.progressEmergency);
        tvStatus = findViewById(R.id.tvEmergencyRequestStatus);
        tvName = findViewById(R.id.tvEmergencyUserName);
        tvPhone = findViewById(R.id.tvEmergencyPhone);
        tvAddress = findViewById(R.id.tvEmergencyAddress);
        tvLocationSummary = findViewById(R.id.tvEmergencyLocation);
        tvLocationHint = findViewById(R.id.tvEmergencyLocationHint);
        actCategory = findViewById(R.id.actEmergencyCategory);
        etDescription = findViewById(R.id.etEmergencyDescription);
        tilCategory = findViewById(R.id.tilEmergencyCategory);
        tilDescription = findViewById(R.id.tilEmergencyDescription);
        chipGroup = findViewById(R.id.chipGroupEmergencyCategories);

        cardProviderAssigned = findViewById(R.id.cardEmergencyAssignedProvider);
        tvProviderName = findViewById(R.id.tvEmergencyAssignedProviderName);
        tvProviderPhone = findViewById(R.id.tvEmergencyAssignedProviderPhone);
        tvProviderService = findViewById(R.id.tvEmergencyAssignedProviderService);
        tvProviderLocation = findViewById(R.id.tvEmergencyAssignedProviderLocation);
    }

    private void setupClicks() {
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnSelectLocation != null) btnSelectLocation.setOnClickListener(v -> openLocationPicker());
        if (btnSubmit != null) btnSubmit.setOnClickListener(v -> submitEmergencyRequest());
        if (btnAcceptProvider != null) btnAcceptProvider.setOnClickListener(v -> updateStatus("accepted"));
        if (btnRejectProvider != null) btnRejectProvider.setOnClickListener(v -> updateStatus("rejected"));
    }

    private void setupCategoryInput() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, EMERGENCY_CATEGORIES);
        actCategory.setAdapter(adapter);
        for (String cat : EMERGENCY_CATEGORIES) {
            Chip chip = new Chip(this);
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setOnCheckedChangeListener((bv, checked) -> { if(checked) actCategory.setText(cat, false); updateSubmitButtonState(); });
            chipGroup.addView(chip);
        }
    }

    private void hydrateLocation() {
        selectedCity = getIntent().getStringExtra(EXTRA_LOCATION_CITY);
        selectedArea = getIntent().getStringExtra(EXTRA_LOCATION_AREA);
        selectedLat = getIntent().getDoubleExtra(EXTRA_LOCATION_LAT, Double.NaN);
        selectedLng = getIntent().getDoubleExtra(EXTRA_LOCATION_LNG, Double.NaN);

        selectedAddress = locationManager.getSavedAddress();
        selectedPincode = locationManager.getSavedPincode();

        if (TextUtils.isEmpty(selectedCity)) {
            selectedCity = locationManager.getSavedCity();
            selectedArea = locationManager.getSavedArea();
            selectedLat = locationManager.getSavedLat();
            selectedLng = locationManager.getSavedLng();
            selectedAddress = locationManager.getSavedAddress();
            selectedPincode = locationManager.getSavedPincode();
        }
        updateLocationUi();
    }

    private void updateLocationUi() {
        String label = AppLocationManager.composeLocationLabel(selectedCity, selectedPincode);
        if (TextUtils.isEmpty(label)) {
            label = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        }
        boolean locationReady = hasValidLocation();

        tvLocationSummary.setText(label.isEmpty() ? "No location selected" : label);
        tvAddress.setText("Emergency Location: " + (label.isEmpty() ? "Not set" : label));
        if (btnSelectLocation != null) {
            btnSelectLocation.setVisibility(locationReady ? View.GONE : View.VISIBLE);
            btnSelectLocation.setText(locationReady ? "Location Ready" : "Select Location");
        }
        if (tvLocationHint != null) {
            tvLocationHint.setText(locationReady
                    ? "We will search nearby providers within 25 km."
                    : "Enable GPS so we can find nearby providers within 25 km.");
        }
        updateSubmitButtonState();
    }

    private void refreshLocationState() {
        if (!hasValidLocation() && btnSelectLocation != null) {
            btnSelectLocation.setVisibility(View.VISIBLE);
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED
                    && androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingOpenLocationPicker = true;
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_REQ);
            }
        }
    }

    private void openLocationPicker() {
        LocationSearchBottomSheet.show(this, locationManager, new LocationSearchBottomSheet.PermissionDelegate() {
            @Override public boolean hasLocationPermission() {
                return ContextCompat.checkSelfPermission(EmergencyHelpActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(EmergencyHelpActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            }
            @Override public void requestLocationPermission() {
                pendingOpenLocationPicker = true;
                ActivityCompat.requestPermissions(EmergencyHelpActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_REQ);
            }
        }, suggestion -> {
            if (suggestion == null) return;
            selectedCity = suggestion.city;
            selectedArea = suggestion.area;
            selectedLat = suggestion.lat;
            selectedLng = suggestion.lng;
            selectedAddress = locationManager.getSavedAddress();
            selectedPincode = locationManager.getSavedPincode();
            updateLocationUi();
        });
    }

    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        currentUserId = user.getUid();
        currentUserName = user.getDisplayName() == null ? "" : user.getDisplayName().trim();
        currentUserPhone = user.getPhoneNumber() == null ? "" : user.getPhoneNumber().trim();
        if (currentUserName.isEmpty()) currentUserName = "Member";

        tvName.setText("User: " + currentUserName);
        tvPhone.setText("Phone: " + (currentUserPhone.isEmpty() ? "Loading..." : currentUserPhone));

        UserRepository.fetchUserByUid(user.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onSuccess(UserModel userModel) {
                if (userModel == null) return;
                if (!TextUtils.isEmpty(userModel.name)) {
                    currentUserName = userModel.name.trim();
                    tvName.setText("User: " + currentUserName);
                }
                if (!TextUtils.isEmpty(userModel.phone)) {
                    currentUserPhone = userModel.phone.trim();
                    tvPhone.setText("Phone: " + currentUserPhone);
                }
            }

            @Override
            public void onError(String msg) {
                Log.d(TAG, "Using Firebase auth profile fallback: " + msg);
            }
        });
    }

     private void submitEmergencyRequest() {
        if (isSubmitting) return;
        if (!hasValidLocation()) {
            setStatus("Please enable location to continue.");
            openLocationPicker();
            return;
        }

        isSubmitting = true;
        setLoading(true);
        setStatus("Sending emergency request...");
        btnSubmit.setEnabled(false);
        btnSubmit.setAlpha(0.6f);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", currentUserId);
                body.put("user_name", currentUserName);
                body.put("phone", currentUserPhone);
                body.put("category", actCategory.getText().toString());
                body.put("description", etDescription.getText() != null ? etDescription.getText().toString().trim() : "");
                body.put("lat", selectedLat);
                body.put("lng", selectedLng);
                body.put("city", selectedCity);
                body.put("area", selectedArea);
                body.put("address", selectedAddress);

                // Obtain a fresh Firebase ID token (force-refreshed to avoid 401 from stale tokens)
                String idToken = AuthTokenProvider.getIdTokenBlocking(10);
                if (idToken == null || idToken.isEmpty()) {
                    throw new IllegalStateException("Not authenticated — please log out and log in again.");
                }
                Log.d(TAG, "[submitEmergency] idToken obtained: len=" + idToken.length());

                String url = BuildConfig.SUPABASE_CREATE_EMERGENCY_URL != null && !BuildConfig.SUPABASE_CREATE_EMERGENCY_URL.isEmpty()
                        ? BuildConfig.SUPABASE_CREATE_EMERGENCY_URL
                        : BuildConfig.SUPABASE_URL + "/functions/v1/create-emergency";

                Request.Builder rb = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body.toString(), JSON))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + idToken);
                Request req = rb.build();

                try (Response res = http.newCall(req).execute()) {
                    String raw = res.body() != null ? res.body().string() : "";
                    if (res.code() != 200) {
                        final String msg = "Request failed: HTTP " + res.code() + " " + raw;
                        runOnUiThread(() -> {
                            setLoading(false);
                            isSubmitting = false;
                            btnSubmit.setEnabled(true);
                            btnSubmit.setAlpha(1f);
                            setStatus("✗ " + msg);
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    JSONObject result = new JSONObject(raw);
                    boolean success = result.optBoolean("success", false);
                    if (!success) {
                        final String message = result.optString("message", "No providers available within 25km");
                        runOnUiThread(() -> {
                            setLoading(false);
                            isSubmitting = false;
                            btnSubmit.setEnabled(true);
                            btnSubmit.setAlpha(1f);
                            setStatus("✗ " + message);
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    final String requestId = result.optString("request_id", "");
                    runOnUiThread(() -> {
                        setLoading(false);
                        activeRequestId = requestId;
                        setStatus("✓ Emergency sent. Waiting for provider...");
                        Toast.makeText(this, "Emergency sent successfully!", Toast.LENGTH_LONG).show();
                    });

                    startStatusPolling();
                }
            } catch (Exception e) {
                Log.e(TAG, "submitEmergencyRequest edge call failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    isSubmitting = false;
                    btnSubmit.setEnabled(true);
                    btnSubmit.setAlpha(1f);
                    setStatus("✗ Request failed: " + e.getMessage());
                    Toast.makeText(this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
     }

    private void createPendingEmergencyBookingForProvider(String requestId, NearbyProvider provider) {
        try {
            if (provider == null || provider.providerId.isEmpty()) return;

            JSONObject booking = new JSONObject();
            booking.put("customer_id", currentUserId);
            booking.put("customer_name", currentUserName);
            booking.put("customer_phone", currentUserPhone);
            booking.put("provider_id", provider.providerId);
            booking.put("provider_name", "Awaiting Acceptance");
            booking.put("service_name", "Emergency - " + actCategory.getText().toString().trim());
            booking.put("category", actCategory.getText().toString().trim());
            booking.put("address", selectedAddress != null ? selectedAddress : selectedCity);
            booking.put("description", etDescription.getText() != null ? etDescription.getText().toString().trim() : "");
            booking.put("status", "pending");
            booking.put("type", "Emergency");
            booking.put("is_emergency", true);
            booking.put("emergency_request_id", requestId);
            booking.put("booking_date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date()));
            booking.put("booking_time", new SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()));
            booking.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new java.util.Date()));

            tryInsertBooking(booking);
        } catch (Exception e) {
            Log.w(TAG, "createPendingEmergencyBookingForProvider skipped: " + e.getMessage());
        }
    }

    private List<NearbyProvider> fetchNearbyProviders(String category) throws Exception {
        List<NearbyProvider> fromRpc = fetchNearbyProvidersFromRpc(category);
        List<NearbyProvider> fallback = fetchNearbyProvidersClientFallback(category);

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<NearbyProvider> merged = new ArrayList<>();

        for (NearbyProvider provider : fromRpc) {
            if (provider == null || !provider.isValid()) continue;
            String key = provider.providerId + "|" + provider.firebaseUid;
            if (seen.add(key)) merged.add(provider);
        }
        for (NearbyProvider provider : fallback) {
            if (provider == null || !provider.isValid()) continue;
            String key = provider.providerId + "|" + provider.firebaseUid;
            if (seen.add(key)) merged.add(provider);
        }

        Log.d(TAG, "fetchNearbyProviders rpc=" + fromRpc.size() + " fallback=" + fallback.size() + " merged=" + merged.size());
        return merged;
    }

    private List<NearbyProvider> fetchNearbyProvidersFromRpc(String category) {
        List<NearbyProvider> providers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            JSONObject body = new JSONObject();
            body.put("p_category", category == null ? "" : category.trim());
            body.put("p_lat", selectedLat);
            body.put("p_lng", selectedLng);
            body.put("p_radius_km", NEARBY_RADIUS_KM);

            // Use a fresh Firebase ID token for authenticated RPC calls
            String rpcToken = AuthTokenProvider.getIdTokenBlocking(10);
            String rpcAuth = (rpcToken != null && !rpcToken.isEmpty())
                    ? "Bearer " + rpcToken
                    : "Bearer " + BuildConfig.SUPABASE_ANON_KEY;
            Request req = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/rpc/find_emergency_nearby_providers")
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", rpcAuth)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response res = http.newCall(req).execute()) {
                String raw = res.body() != null ? res.body().string() : "[]";
                if (res.code() != 200) {
                    Log.w(TAG, "find_emergency_nearby_providers RPC failed HTTP " + res.code());
                    return providers;
                }

                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject row = arr.getJSONObject(i);
                    String providerId = row.optString("provider_id", "").trim();
                    String uid = row.optString("firebase_uid", "").trim();
                    if (providerId.isEmpty()) continue;
                    String key = providerId + "|" + uid;
                    if (!seen.add(key)) continue;
                    providers.add(new NearbyProvider(providerId, uid));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchNearbyProvidersFromRpc fallback: " + e.getMessage());
        }
        return providers;
    }

    private List<NearbyProvider> fetchNearbyProvidersClientFallback(String category) throws Exception {
        List<NearbyProvider> providers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles"
                + "?select=id,firebase_uid,business_name,category,latitude,longitude,lat,lng,status,is_approved,city,area"
                + "&limit=" + PROVIDER_PAGE_SIZE;

        Log.d(TAG, "Fallback search: category=" + category + " lat=" + selectedLat + " lng=" + selectedLng + " radius=" + NEARBY_RADIUS_KM + "km");

        String fallbackToken = AuthTokenProvider.getIdTokenBlocking(10);
        String fallbackAuth = (fallbackToken != null && !fallbackToken.isEmpty())
                ? "Bearer " + fallbackToken
                : "Bearer " + BuildConfig.SUPABASE_ANON_KEY;
        Request req = new Request.Builder().url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", fallbackAuth)
                .addHeader("Accept", "application/json")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                Log.e(TAG, "Provider fetch failed: HTTP " + res.code());
                return new ArrayList<>();
            }
            
            String body = res.body() != null ? res.body().string() : "[]";
            JSONArray arr = new JSONArray(body);
            Log.d(TAG, "Fetched " + arr.length() + " total providers from database");
            
            int skippedLocation = 0;
            int skippedCategory = 0;
            int skippedDistance = 0;
            int skippedEligibility = 0;
            
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String providerId = o.optString("id", "").trim();
                String uid = o.optString("firebase_uid", "").trim();
                String name = o.optString("business_name", "Unknown");
                
                if (providerId.isEmpty()) {
                    Log.d(TAG, "Provider at index " + i + " has no ID, skipping");
                    continue;
                }

                if (!isProviderEligible(o, category)) {
                    skippedEligibility++;
                    continue;
                }

                double pLat = readLatitude(o);
                double pLng = readLongitude(o);
                
                if (Double.isNaN(pLat) || Double.isNaN(pLng)) {
                    Log.d(TAG, "Provider " + name + " (" + providerId + ") has no valid location (lat=" + pLat + " lng=" + pLng + ")");
                    skippedLocation++;
                    continue;
                }

                double distKm = haversine(selectedLat, selectedLng, pLat, pLng);
                if (Double.isNaN(distKm) || distKm > NEARBY_RADIUS_KM) {
                    Log.d(TAG, "Provider " + name + " (" + providerId + ") too far: " + distKm + " km");
                    skippedDistance++;
                    continue;
                }

                String key = providerId + "|" + uid;
                if (!seen.add(key)) {
                    Log.d(TAG, "Duplicate provider " + providerId);
                    continue;
                }
                
                providers.add(new NearbyProvider(providerId, uid));
                Log.d(TAG, "✓ Added provider: " + name + " (" + providerId + ") - Category: " + o.optString("category", "?") 
                    + " - Distance: " + String.format("%.1f", distKm) + " km - Status: " + o.optString("status", "?"));
            }
            
            Log.d(TAG, "Provider filtering summary - Total:" + arr.length() 
                + " | Matched:" + providers.size() 
                + " | Skipped(eligibility):" + skippedEligibility 
                + " | Skipped(location):" + skippedLocation
                + " | Skipped(distance):" + skippedDistance);
        }

        return providers;
    }

    private boolean isProviderEligible(JSONObject provider, String requestedCategory) {
        if (provider == null) return false;

        String providerId = provider.optString("id", "?");
        String status = provider.optString("status", "").trim().toLowerCase(Locale.US);
        boolean approved = provider.optBoolean("is_approved", false);
        
        // Status check - provider must be active or approved
        boolean statusOk = status.isEmpty()
                || "active".equals(status)
                || "approved".equals(status)
                || "available".equals(status)
                || "online".equals(status)
                || "verified".equals(status);

        if (!statusOk && !approved) {
            Log.d(TAG, "❌ Provider " + providerId + " ineligible - status=" + status + " approved=" + approved);
            return false;
        }

        // Category check
        String providerCategory = provider.optString("category", "");
        if (!matchesCategory(requestedCategory, providerCategory)) {
            Log.d(TAG, "❌ Provider " + providerId + " category mismatch - requested=" + requestedCategory + " provider=" + providerCategory);
            return false;
        }

        Log.d(TAG, "✓ Provider " + providerId + " is eligible");
        return true;
    }

    private double readLatitude(JSONObject o) {
        if (o == null) return Double.NaN;
        if (o.has("latitude") && !o.isNull("latitude")) {
            return o.optDouble("latitude", Double.NaN);
        }
        if (o.has("lat") && !o.isNull("lat")) {
            return o.optDouble("lat", Double.NaN);
        }
        return Double.NaN;
    }

    private double readLongitude(JSONObject o) {
        if (o == null) return Double.NaN;
        if (o.has("longitude") && !o.isNull("longitude")) {
            return o.optDouble("longitude", Double.NaN);
        }
        if (o.has("lng") && !o.isNull("lng")) {
            return o.optDouble("lng", Double.NaN);
        }
        return Double.NaN;
    }

    private String createRequestInDb() throws Exception {
        JSONObject body = new JSONObject();
        body.put("category", actCategory.getText().toString());
        body.put("description", etDescription.getText() != null ? etDescription.getText().toString().trim() : "");
        body.put("user_name", currentUserName);
        body.put("phone", currentUserPhone);
        body.put("lat", selectedLat);
        body.put("lng", selectedLng);
        body.put("latitude", selectedLat);
        body.put("longitude", selectedLng);
        body.put("pincode", selectedPincode);
        body.put("city", selectedCity);
        body.put("area", selectedArea);
        body.put("address", selectedAddress);
        body.put("location_label", AppLocationManager.composeLocationLabel(selectedCity, selectedPincode));
        body.put("status", "pending");
        body.put("user_id", currentUserId);
        body.put("user_firebase_uid", currentUserId);

        String idToken = AuthTokenProvider.getIdTokenBlocking(5);
        if (idToken == null || idToken.isEmpty()) {
            throw new IllegalStateException("Failed to get Firebase ID token");
        }

        Request.Builder rb = new Request.Builder().url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?select=id")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + idToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation");
        Request req = rb.build();
        try (Response res = http.newCall(req).execute()) {
            String raw = res.body() != null ? res.body().string() : "[]";
            if (!res.isSuccessful()) {
                throw new IllegalStateException("Create request failed HTTP " + res.code() + " " + raw);
            }
            JSONArray arr = new JSONArray(raw);
            return arr.getJSONObject(0).getString("id");
        }
    }

    private boolean tryInsertBooking(JSONObject payload) {
        try {
            String bookingToken = AuthTokenProvider.getIdTokenBlocking(10);
            String bookingAuth = (bookingToken != null && !bookingToken.isEmpty())
                    ? "Bearer " + bookingToken
                    : "Bearer " + BuildConfig.SUPABASE_ANON_KEY;
            Request.Builder rb = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/bookings")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", bookingAuth)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal");
            Request insert = rb.build();
            try (Response response = http.newCall(insert).execute()) {
                return response.code() == 200 || response.code() == 201 || response.code() == 204;
            }
        } catch (Exception e) {
            Log.w(TAG, "tryInsertBooking failed: " + e.getMessage());
            return false;
        }
    }

    private void startStatusPolling() {
        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activeRequestId.isEmpty()) return;
                pollStatus();
                mainHandler.postDelayed(this, STATUS_POLL_MS);
            }
        }, STATUS_POLL_MS);
    }

    private void pollStatus() {
        new Thread(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?id=eq." + activeRequestId + "&select=*,assigned_provider:provider_profiles(*)";
                String pollToken = AuthTokenProvider.getIdTokenBlocking(10);
                String pollAuth = (pollToken != null && !pollToken.isEmpty())
                        ? "Bearer " + pollToken
                        : "Bearer " + BuildConfig.SUPABASE_ANON_KEY;
                Request.Builder rb = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", pollAuth)
                        .addHeader("Accept", "application/json");
                Request req = rb.build();
                try (Response res = http.newCall(req).execute()) {
                    JSONArray arr = new JSONArray(res.body().string());
                    if (arr.length() > 0) {
                        JSONObject r = arr.getJSONObject(0);
                        String status = r.getString("status");
                        runOnUiThread(() -> {
                            tvStatus.setText("Status: " + status);
                            if ("accepted".equals(status)) {
                                showProvider(r.optJSONObject("assigned_provider"));
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showProvider(JSONObject p) {
        if (p == null) return;
        cardProviderAssigned.setVisibility(View.VISIBLE);
        tvProviderName.setText(p.optString("business_name", "Provider"));
        tvProviderPhone.setText("Phone: " + p.optString("phone", "N/A"));
        btnAcceptProvider.setVisibility(View.VISIBLE);
        btnRejectProvider.setVisibility(View.VISIBLE);
    }

    private void updateStatus(String status) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", status);
                String updateToken = AuthTokenProvider.getIdTokenBlocking(10);
                String updateAuth = (updateToken != null && !updateToken.isEmpty())
                        ? "Bearer " + updateToken
                        : "Bearer " + BuildConfig.SUPABASE_ANON_KEY;
                Request.Builder rb = new Request.Builder().url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?id=eq." + activeRequestId)
                        .patch(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", updateAuth)
                        .addHeader("Content-Type", "application/json");
                Request req = rb.build();
                http.newCall(req).execute();
                runOnUiThread(() -> {
                    Toast.makeText(this, "Request " + status, Toast.LENGTH_SHORT).show();
                    if (!"pending".equals(status)) activeRequestId = "";
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private JSONObject buildEmergencyNotificationPayload(String requestId) throws Exception {
        JSONObject data = new JSONObject();
        data.put("request_id", requestId);
        data.put("type", "emergency_request");
        data.put("user_name", currentUserName);
        data.put("phone", currentUserPhone);
        data.put("category", actCategory.getText().toString());
        data.put("city", selectedCity);
        data.put("area", selectedArea);
        data.put("address", selectedAddress);
        data.put("pincode", selectedPincode);
        data.put("lat", selectedLat);
        data.put("lng", selectedLng);
        data.put("title", "Emergency Request");
        data.put("body", "New emergency request from " + currentUserName);
        return data;
    }

    private boolean hasValidLocation() {
        return !Double.isNaN(selectedLat)
                && !Double.isNaN(selectedLng);
    }

    /**
     * Improved category matching with flexible alias support.
     * Handles variations like "Electrician" vs "Electrical", "AC Service" vs "AC Repair", etc.
     */
    private boolean matchesCategory(String requested, String providerCategory) {
        if (requested == null || requested.trim().isEmpty()) {
            // No category specified = match all providers
            return true;
        }
        if (providerCategory == null || providerCategory.trim().isEmpty()) {
            // Provider has no category specified = skip
            Log.w(TAG, "Provider has no category, skipping");
            return false;
        }

        String req = requested.trim().toLowerCase(Locale.US);
        String prov = providerCategory.trim().toLowerCase(Locale.US);

        // Direct match
        if (req.equals(prov)) return true;

        // Contains match
        if (prov.contains(req) || req.contains(prov)) return true;

        // Category group aliases (add common service aliases)
        if (isCategoryInGroup(req, "electrical") && isCategoryInGroup(prov, "electrical")) return true;
        if (isCategoryInGroup(req, "plumbing") && isCategoryInGroup(prov, "plumbing")) return true;
        if (isCategoryInGroup(req, "ac") && isCategoryInGroup(prov, "ac")) return true;
        if (isCategoryInGroup(req, "cleaning") && isCategoryInGroup(prov, "cleaning")) return true;
        if (isCategoryInGroup(req, "painting") && isCategoryInGroup(prov, "painting")) return true;
        if (isCategoryInGroup(req, "carpentry") && isCategoryInGroup(prov, "carpentry")) return true;
        if (isCategoryInGroup(req, "repair") && isCategoryInGroup(prov, "repair")) return true;
        if (isCategoryInGroup(req, "home") && isCategoryInGroup(prov, "home")) return true;

        Log.d(TAG, "Category mismatch: requested='" + requested + "' provider='" + providerCategory + "'");
        return false;
    }

    /**
     * Check if a category string belongs to a service group
     */
    private boolean isCategoryInGroup(String category, String group) {
        if (category == null || category.isEmpty()) return false;
        String cat = category.toLowerCase(Locale.US);

        switch (group.toLowerCase(Locale.US)) {
            case "electrical":
                return cat.contains("electrician") || cat.contains("electrical") || cat.contains("electric");
            case "plumbing":
                return cat.contains("plumber") || cat.contains("plumb");
            case "ac":
                return cat.contains("ac") || cat.contains("air") || cat.contains("conditioner") 
                    || cat.contains("cooling") || cat.contains("hvac");
            case "cleaning":
                return cat.contains("clean");
            case "painting":
                return cat.contains("paint");
            case "carpentry":
                return cat.contains("carpent") || cat.contains("carpenter");
            case "repair":
                return cat.contains("repair") || cat.contains("service") || cat.contains("maintenance");
            case "home":
                return cat.contains("home") || cat.contains("appliance") || cat.contains("general");
            default:
                return false;
        }
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void setupFormValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateSubmitButtonState(); }
        };
        actCategory.addTextChangedListener(watcher);
        etDescription.addTextChangedListener(watcher);
    }

    private void updateSubmitButtonState() {
        String cat = actCategory.getText().toString();
        String desc = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        boolean locOk = !Double.isNaN(selectedLat) && !Double.isNaN(selectedLng);

        // Req 6: Validation rules
        boolean catOk = !TextUtils.isEmpty(cat);
        boolean descOk = desc.length() >= 10 && desc.length() <= 300;

        boolean valid = catOk && descOk && locOk;
        btnSubmit.setEnabled(valid);
        btnSubmit.setAlpha(valid ? 1f : 0.6f);

        // Show error messages if needed (optional, or on click)
        if (!catOk && !cat.isEmpty()) tilCategory.setError("Category required"); else tilCategory.setError(null);
        if (!descOk && !desc.isEmpty()) {
            if (desc.length() < 10) tilDescription.setError("Min 10 characters");
            else tilDescription.setError("Max 300 characters");
        } else tilDescription.setError(null);
    }

    private void setLoading(boolean l) { isSubmitting = l; progress.setVisibility(l ? View.VISIBLE : View.GONE); }
    private void setStatus(String s) { tvStatus.setText(s); }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQ) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted) {
                if (pendingOpenLocationPicker) {
                    pendingOpenLocationPicker = false;
                    openLocationPicker();
                }
            } else {
                setStatus("Location permission is required to find nearby providers.");
                Toast.makeText(this, "Location permission is required to find nearby providers.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
