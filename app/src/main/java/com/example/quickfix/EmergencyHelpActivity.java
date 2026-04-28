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
         setStatus("Submitting emergency request...");
         btnSubmit.setEnabled(false);
         btnSubmit.setAlpha(0.6f);

         new Thread(() -> {
             try {
                 String rid = createRequestInDb();
                 activeRequestId = rid;

                 List<String> providers = fetchNearbyProviders(actCategory.getText().toString());

                 if (!providers.isEmpty()) {
                     JSONObject payload = buildEmergencyNotificationPayload(rid);
                     
                     for (String providerUid : providers) {
                         // 1. Insert into notifications table (DB storage)
                         FcmNotificationHelper.saveNotificationToDb(
                                 providerUid,
                                 "Emergency Request",
                                 "New emergency request for " + actCategory.getText().toString() + " near " + selectedCity,
                                 rid,
                                 "emergency"
                         );
                         
                         // 2. Insert into bookings table (status = pending)
                         createPendingEmergencyBookingForProvider(rid, providerUid);
                     }

                     // 3. Send FCM push notification
                     FcmNotificationHelper.notifyEmergencyProviders(
                             providers,
                             "Emergency Request",
                             "New emergency request from " + currentUserName,
                             rid,
                             payload,
                             null
                     );
                     
                     runOnUiThread(() -> {
                         setLoading(false);
                         setStatus("✓ Request submitted successfully! Waiting for acceptance...");
                         Toast.makeText(this, "Emergency request submitted successfully!", Toast.LENGTH_LONG).show();
                     });
                 } else {
                     runOnUiThread(() -> {
                         setLoading(false);
                         setStatus("✓ Request submitted. No providers within 25km found yet.");
                         Toast.makeText(this, "Request submitted but no nearby providers available.", Toast.LENGTH_LONG).show();
                     });
                 }

                 startStatusPolling();
             } catch (Exception e) {
                 Log.e(TAG, "submitEmergencyRequest failed", e);
                 runOnUiThread(() -> { 
                     setLoading(false);
                     isSubmitting = false;
                     btnSubmit.setEnabled(true);
                     btnSubmit.setAlpha(1f);
                     setStatus("✗ Failed to submit request: " + e.getMessage());
                     Toast.makeText(EmergencyHelpActivity.this, "Failed to submit request", Toast.LENGTH_SHORT).show();
                 });
             }
         }).start();
     }

    private void createPendingEmergencyBookingForProvider(String requestId, String providerUid) {
        try {
            JSONObject booking = new JSONObject();
            booking.put("customer_id", currentUserId);
            booking.put("customer_name", currentUserName);
            booking.put("customer_phone", currentUserPhone);
            booking.put("provider_id", providerUid);
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

    private List<String> fetchNearbyProviders(String category) throws Exception {
        List<String> uids = new ArrayList<>();
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?select=firebase_uid,category,latitude,longitude,area,city,status&status=eq.active&is_approved=eq.true";

        Request req = new Request.Builder().url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) return uids;
            JSONArray arr = new JSONArray(res.body().string());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                double pLat = o.optDouble("latitude", Double.NaN);
                double pLng = o.optDouble("longitude", Double.NaN);
                String pArea = o.optString("area", "");
                String pCity = o.optString("city", "");
                String pCategory = o.optString("category", "");

                boolean match = false;
                if (!Double.isNaN(selectedLat) && !Double.isNaN(pLat)) {
                    double dist = haversine(selectedLat, selectedLng, pLat, pLng);
                    if (dist <= NEARBY_RADIUS_KM) match = true;
                } else {
                    // Fallback to area matching if GPS missing
                    if (!TextUtils.isEmpty(selectedArea) && selectedArea.equalsIgnoreCase(pArea)) match = true;
                    else if (!TextUtils.isEmpty(selectedCity) && selectedCity.equalsIgnoreCase(pCity)) match = true;
                }

                if (match && !TextUtils.isEmpty(category)) {
                    match = matchesCategory(category, pCategory);
                }

                if (match) uids.add(o.getString("firebase_uid"));
            }
        }
        return uids;
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
        body.put("location_label", AppLocationManager.composeLocationLabel(selectedCity, selectedPincode));
        body.put("status", "pending");
        body.put("user_id", currentUserId);

        Request req = new Request.Builder().url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?select=id")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation").build();
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
            Request insert = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/bookings")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();
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
                Request req = new Request.Builder().url(url).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).build();
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
                Request req = new Request.Builder().url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?id=eq." + activeRequestId)
                        .patch(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).build();
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
                && !Double.isNaN(selectedLng)
                && !TextUtils.isEmpty(selectedCity);
    }

    private boolean matchesCategory(String requested, String providerCategory) {
        String a = requested == null ? "" : requested.trim().toLowerCase(Locale.getDefault());
        String b = providerCategory == null ? "" : providerCategory.trim().toLowerCase(Locale.getDefault());
        if (a.isEmpty() || b.isEmpty()) return true;
        return a.equals(b) || a.contains(b) || b.contains(a);
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
        boolean locOk = !Double.isNaN(selectedLat) && !Double.isNaN(selectedLng) && !TextUtils.isEmpty(selectedCity);

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
