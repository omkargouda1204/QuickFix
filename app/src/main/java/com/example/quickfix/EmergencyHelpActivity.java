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
    private double selectedLat = Double.NaN, selectedLng = Double.NaN;
    private String activeRequestId = "";
    private boolean isSubmitting = false;

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

        if (TextUtils.isEmpty(selectedCity)) {
            selectedCity = locationManager.getSavedCity();
            selectedArea = locationManager.getSavedArea();
            selectedLat = locationManager.getSavedLat();
            selectedLng = locationManager.getSavedLng();
        }
        updateLocationUi();
    }

    private void updateLocationUi() {
        String label = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        tvLocationSummary.setText(label.isEmpty() ? "No location selected" : label);
        tvAddress.setText("Emergency Location: " + (label.isEmpty() ? "Not set" : label));
        updateSubmitButtonState();
    }

    private void openLocationPicker() {
        LocationSearchBottomSheet.show(this, locationManager, new LocationSearchBottomSheet.PermissionDelegate() {
            @Override public boolean hasLocationPermission() { return ContextCompat.checkSelfPermission(EmergencyHelpActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
            @Override public void requestLocationPermission() { ActivityCompat.requestPermissions(EmergencyHelpActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQ); }
        }, suggestion -> {
            if (suggestion == null) return;
            selectedCity = suggestion.city;
            selectedArea = suggestion.area;
            selectedLat = suggestion.lat;
            selectedLng = suggestion.lng;
            updateLocationUi();
        });
    }

    private void loadProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        currentUserId = user.getUid();
        currentUserName = user.getDisplayName();
        // Fetch phone from DB or user if needed
        tvName.setText("User: " + (currentUserName == null ? "Member" : currentUserName));
    }

    private void submitEmergencyRequest() {
        if (isSubmitting) return;
        setLoading(true);
        setStatus("Searching for nearby providers...");

        new Thread(() -> {
            try {
                // 1. Fetch nearby providers
                List<String> providers = fetchNearbyProviders(actCategory.getText().toString());
                
                // 2. Create request in DB
                String rid = createRequestInDb();
                activeRequestId = rid;

                // 3. Notify providers
                if (!providers.isEmpty()) {
                    FcmNotificationHelper.notifyEmergencyProviders(providers, "Emergency!", "New request from " + currentUserName, rid, new JSONObject(), null);
                    runOnUiThread(() -> setStatus("Request sent to " + providers.size() + " nearby providers."));
                } else {
                    runOnUiThread(() -> setStatus("No providers found within 25km. Try a different category."));
                }
                
                startStatusPolling();
            } catch (Exception e) {
                runOnUiThread(() -> { setLoading(false); setStatus("Failed to submit request."); });
            }
        }).start();
    }

    private List<String> fetchNearbyProviders(String category) throws Exception {
        List<String> uids = new ArrayList<>();
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?select=firebase_uid,latitude,longitude,area,city,status&status=eq.active";
        
        Request req = new Request.Builder().url(url).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) return uids;
            JSONArray arr = new JSONArray(res.body().string());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                double pLat = o.optDouble("latitude", o.optDouble("lat", Double.NaN));
                double pLng = o.optDouble("longitude", o.optDouble("lng", Double.NaN));
                String pArea = o.optString("area", "");
                String pCity = o.optString("city", "");

                boolean match = false;
                if (!Double.isNaN(selectedLat) && !Double.isNaN(pLat)) {
                    double dist = haversine(selectedLat, selectedLng, pLat, pLng);
                    if (dist <= NEARBY_RADIUS_KM) match = true;
                } else {
                    // Fallback to area matching
                    if (!TextUtils.isEmpty(selectedArea) && selectedArea.equalsIgnoreCase(pArea)) match = true;
                    else if (!TextUtils.isEmpty(selectedCity) && selectedCity.equalsIgnoreCase(pCity)) match = true;
                }

                if (match) uids.add(o.getString("firebase_uid"));
            }
        }
        return uids;
    }

    private String createRequestInDb() throws Exception {
        JSONObject body = new JSONObject();
        body.put("user_id", currentUserId);
        body.put("category", actCategory.getText().toString());
        body.put("description", etDescription.getText() != null ? etDescription.getText().toString().trim() : "");
        body.put("status", "pending");
        body.put("latitude", selectedLat);
        body.put("longitude", selectedLng);
        body.put("area", selectedArea);
        body.put("city", selectedCity);
        body.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new java.util.Date()));

        Request req = new Request.Builder().url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?select=id")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Prefer", "return=representation").build();
        try (Response res = http.newCall(req).execute()) {
            JSONArray arr = new JSONArray(res.body().string());
            return arr.getJSONObject(0).getString("id");
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
        boolean locOk = !Double.isNaN(selectedLat) && !TextUtils.isEmpty(selectedCity);

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
}
