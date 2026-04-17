package com.example.quickfix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

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
            "Electrician",
            "Plumber",
            "AC Service",
            "Carpentry",
            "Cleaning",
            "Appliance Repair",
            "Painting",
            "General Repair"
    );

    public static final String EXTRA_LOCATION_LABEL = "extra_location_label";
    public static final String EXTRA_LOCATION_CITY = "extra_location_city";
    public static final String EXTRA_LOCATION_AREA = "extra_location_area";
    public static final String EXTRA_LOCATION_LAT = "extra_location_lat";
    public static final String EXTRA_LOCATION_LNG = "extra_location_lng";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private AppLocationManager locationManager;

    private ImageView btnBack;
    private MaterialButton btnSubmit;
    private MaterialButton btnSelectLocation;
    private ProgressBar progress;
    private TextView tvStatus;
    private TextView tvName;
    private TextView tvPhone;
    private TextView tvAddress;
    private TextView tvLocationSummary;
    private MaterialAutoCompleteTextView actCategory;
    private TextInputEditText etDescription;
    private TextInputLayout tilCategory;
    private TextInputLayout tilDescription;
    private ChipGroup chipGroup;

    private String currentUserId = "";
    private String currentUserName = "";
    private String currentUserPhone = "";

    private String selectedCity = "";
    private String selectedArea = "";
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_help);

        locationManager = new AppLocationManager(this);

        bindViews();
        setupCategoryInput();
        setupClicks();
        hydrateLocationFromHomeOrSaved();
        loadProfile();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnSubmit = findViewById(R.id.btnEmergencyRequest);
        btnSelectLocation = findViewById(R.id.btnEmergencySelectLocation);
        progress = findViewById(R.id.progressEmergency);
        tvStatus = findViewById(R.id.tvEmergencyRequestStatus);
        tvName = findViewById(R.id.tvEmergencyUserName);
        tvPhone = findViewById(R.id.tvEmergencyPhone);
        tvAddress = findViewById(R.id.tvEmergencyAddress);
        tvLocationSummary = findViewById(R.id.tvEmergencyLocation);
        actCategory = findViewById(R.id.actEmergencyCategory);
        etDescription = findViewById(R.id.etEmergencyDescription);
        tilCategory = findViewById(R.id.tilEmergencyCategory);
        tilDescription = findViewById(R.id.tilEmergencyDescription);
        chipGroup = findViewById(R.id.chipGroupEmergencyCategories);
    }

    private void setupClicks() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
        if (btnSelectLocation != null) {
            btnSelectLocation.setOnClickListener(v -> openLocationPicker());
        }
        if (btnSubmit != null) {
            btnSubmit.setOnClickListener(v -> submitEmergencyRequest());
        }
    }

    private void setupCategoryInput() {
        if (actCategory != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_list_item_1,
                    new ArrayList<>(EMERGENCY_CATEGORIES)
            );
            actCategory.setAdapter(adapter);
            actCategory.setOnItemClickListener((parent, view, position, id) -> {
                clearCategoryError();
                if (chipGroup != null) {
                    chipGroup.clearCheck();
                    Chip selectedChip = (Chip) chipGroup.getChildAt(position);
                    if (selectedChip != null) {
                        selectedChip.setChecked(true);
                    }
                }
            });
        }

        if (chipGroup == null) return;
        chipGroup.removeAllViews();
        for (String category : EMERGENCY_CATEGORIES) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && actCategory != null) {
                    actCategory.setText(category, false);
                    clearCategoryError();
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void hydrateLocationFromHomeOrSaved() {
        String homeLabel = safe(getIntent() != null ? getIntent().getStringExtra(EXTRA_LOCATION_LABEL) : "");
        String homeCity = safe(getIntent() != null ? getIntent().getStringExtra(EXTRA_LOCATION_CITY) : "");
        String homeArea = safe(getIntent() != null ? getIntent().getStringExtra(EXTRA_LOCATION_AREA) : "");

        double homeLat = getIntent() != null ? getIntent().getDoubleExtra(EXTRA_LOCATION_LAT, Double.NaN) : Double.NaN;
        double homeLng = getIntent() != null ? getIntent().getDoubleExtra(EXTRA_LOCATION_LNG, Double.NaN) : Double.NaN;

        if (!homeCity.isEmpty() || !homeArea.isEmpty() || !homeLabel.isEmpty()) {
            selectedCity = homeCity;
            selectedArea = homeArea;
            selectedLat = homeLat;
            selectedLng = homeLng;
            updateLocationUi();
            return;
        }

        selectedCity = safe(locationManager.getSavedCity());
        selectedArea = safe(locationManager.getSavedArea());
        selectedLat = locationManager.getSavedLat();
        selectedLng = locationManager.getSavedLng();
        updateLocationUi();
    }

    private void openLocationPicker() {
        LocationSearchBottomSheet.show(
                this,
                locationManager,
                new LocationSearchBottomSheet.PermissionDelegate() {
                    @Override
                    public boolean hasLocationPermission() {
                        return ContextCompat.checkSelfPermission(
                                EmergencyHelpActivity.this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                                EmergencyHelpActivity.this,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public void requestLocationPermission() {
                        ActivityCompat.requestPermissions(
                                EmergencyHelpActivity.this,
                                new String[]{
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                },
                                LOCATION_PERMISSION_REQ
                        );
                    }
                },
                suggestion -> {
                    if (suggestion == null) return;
                    selectedCity = safe(suggestion.city);
                    selectedArea = safe(suggestion.area);
                    if (suggestion.nearby) {
                        selectedLat = suggestion.lat;
                        selectedLng = suggestion.lng;
                    } else {
                        selectedLat = Double.NaN;
                        selectedLng = Double.NaN;
                    }
                    updateLocationUi();
                }
        );
    }

    private void loadProfile() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            showToast("Please login to continue.");
            finish();
            return;
        }
        currentUserId = safe(firebaseUser.getUid());

        setText(tvName, "Name: Loading...");
        setText(tvPhone, "Phone: Loading...");

        new Thread(() -> {
            try {
                JSONObject user = fetchUserProfile(firebaseUser);
                currentUserName = safe(user.optString("name", ""));
                currentUserPhone = safe(user.optString("phone", ""));

                if (currentUserName.isEmpty()) {
                    currentUserName = safe(firebaseUser.getDisplayName());
                }
                if (currentUserName.isEmpty()) {
                    String email = safe(firebaseUser.getEmail());
                    currentUserName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "User";
                }

                runOnUiThread(() -> {
                    setText(tvName, "Name: " + fallback(currentUserName, "-"));
                    setText(tvPhone, "Phone: " + fallback(currentUserPhone, "-"));
                });
            } catch (Exception e) {
                Log.w(TAG, "loadProfile failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setText(tvName, "Name: " + fallback(currentUserName, "-"));
                    setText(tvPhone, "Phone: " + fallback(currentUserPhone, "-"));
                });
            }
        }).start();
    }

    private JSONObject fetchUserProfile(FirebaseUser firebaseUser) throws Exception {
        String uid = safe(firebaseUser.getUid());
        String encodedUid = encode(uid);

        String url = BuildConfig.SUPABASE_URL
                + "/rest/v1/users?firebase_uid=eq." + encodedUid
                + "&select=id,name,phone,email&limit=1";

        JSONObject user = fetchFirstRow(url);
        if (user != null) return user;

        String email = safe(firebaseUser.getEmail());
        if (!email.isEmpty()) {
            String byEmail = BuildConfig.SUPABASE_URL
                    + "/rest/v1/users?email=eq." + encode(email)
                    + "&select=id,name,phone,email&limit=1";
            user = fetchFirstRow(byEmail);
            if (user != null) return user;
        }

        return new JSONObject();
    }

    private JSONObject fetchFirstRow(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            if (response.code() != 200) {
                return null;
            }
            String body = response.body() != null ? response.body().string() : "[]";
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) return null;
            return arr.getJSONObject(0);
        }
    }

    private void updateLocationUi() {
        String label = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        if (label.isEmpty()) {
            label = "Location unavailable";
        }

        setText(tvLocationSummary, label);
        setText(tvAddress, "Location: " + label);
    }

    private void submitEmergencyRequest() {
        clearValidationErrors();

        String category = safe(actCategory != null ? String.valueOf(actCategory.getText()) : "");
        String description = safe(etDescription != null ? String.valueOf(etDescription.getText()) : "");
        String locationLabel = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);

        boolean hasError = false;
        if (category.isEmpty()) {
            hasError = true;
            if (tilCategory != null) {
                tilCategory.setError("Please choose a service category");
            }
        }

        if (description.isEmpty()) {
            hasError = true;
            if (tilDescription != null) {
                tilDescription.setError("Please describe the problem");
            }
        }

        if (currentUserName.isEmpty()) {
            hasError = true;
            showToast("Profile name is missing. Please update your profile.");
        }

        if (currentUserPhone.isEmpty()) {
            hasError = true;
            showToast("Phone number is missing. Please update your profile.");
        }

        if (locationLabel.isEmpty()) {
            hasError = true;
            showToast("Please select a location first.");
        }

        if (hasError) return;

        setLoading(true);
        setStatus("Status: Submitting request...");

        new Thread(() -> {
            try {
                String requestId = createEmergencyRequest(category, description, locationLabel);
                List<String> providerFirebaseUids = fetchMatchingProviderFirebaseUids(category, selectedCity);

                if (!providerFirebaseUids.isEmpty()) {
                    JSONObject data = new JSONObject();
                    data.put("request_id", requestId);
                    data.put("category", category);
                    data.put("city", safe(selectedCity));
                    data.put("address", locationLabel);

                    FcmNotificationHelper.notifyEmergencyProviders(
                            providerFirebaseUids,
                            "Emergency Request",
                            currentUserName + " needs urgent " + category + " support.",
                            requestId,
                            data
                    );
                }

                final int notifiedProviders = providerFirebaseUids.size();
                runOnUiThread(() -> {
                    setLoading(false);
                    setStatus("Status: Request submitted (ID: " + requestId + ")");
                    showToast(notifiedProviders > 0
                            ? "Emergency request submitted. " + notifiedProviders + " provider(s) notified."
                            : "Emergency request submitted. Providers will be matched shortly.");
                });
            } catch (Exception e) {
                Log.e(TAG, "submitEmergencyRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    setStatus("Status: Submission failed");
                    showToast("Could not submit emergency request. Please try again.");
                });
            }
        }).start();
    }

    private String createEmergencyRequest(String category, String description, String locationLabel) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("user_id", safe(currentUserId));
        payload.put("user_name", safe(currentUserName));
        payload.put("phone", safe(currentUserPhone));
        payload.put("category", safe(category));
        payload.put("description", safe(description));
        payload.put("address", safe(locationLabel));
        payload.put("status", "pending");
        payload.put("city", safe(selectedCity));
        payload.put("area", safe(selectedArea));
        if (!Double.isNaN(selectedLat)) {
            payload.put("lat", selectedLat);
        }
        if (!Double.isNaN(selectedLng)) {
            payload.put("lng", selectedLng);
        }
        payload.put("created_at", isoNow());

        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?select=id")
                .post(RequestBody.create(payload.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 201 && response.code() != 200) {
                throw new IllegalStateException("Submit failed HTTP " + response.code() + " " + body);
            }

            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) {
                throw new IllegalStateException("Emergency request created but id missing");
            }
            String requestId = safe(arr.getJSONObject(0).optString("id", ""));
            if (requestId.isEmpty()) {
                throw new IllegalStateException("Emergency request id missing");
            }
            return requestId;
        }
    }

    private List<String> fetchMatchingProviderFirebaseUids(String category, String city) {
        List<String> providerFirebaseUids = new ArrayList<>();
        try {
            StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/provider_profiles")
                    .append("?select=firebase_uid,category,city,status,is_approved")
                    .append("&is_approved=eq.true")
                    .append("&limit=200");

            if (!safe(city).isEmpty()) {
                url.append("&city=ilike.*").append(encode(city)).append("*");
            }

            Request request = new Request.Builder()
                    .url(url.toString())
                    .get()
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (response.code() != 200) {
                    return providerFirebaseUids;
                }

                String body = response.body() != null ? response.body().string() : "[]";
                JSONArray arr = new JSONArray(body);
                Set<String> dedupe = new LinkedHashSet<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String providerFirebaseUid = safe(o.optString("firebase_uid", ""));
                    String providerCategory = safe(o.optString("category", ""));
                    String providerStatus = safe(o.optString("status", "approved")).toLowerCase(Locale.US);
                    String providerCity = safe(o.optString("city", ""));

                    if (providerFirebaseUid.isEmpty()) continue;
                    if (!providerStatus.isEmpty() && !("approved".equals(providerStatus) || "active".equals(providerStatus))) {
                        continue;
                    }
                    if (!CategoryMatcher.matches(category, providerCategory)) continue;

                    if (!safe(city).isEmpty() && !providerCity.isEmpty() && !providerCity.equalsIgnoreCase(city)) {
                        continue;
                    }

                    dedupe.add(providerFirebaseUid);
                    if (dedupe.size() >= 30) break;
                }

                providerFirebaseUids.addAll(dedupe);
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchMatchingProviderFirebaseUids failed: " + e.getMessage());
        }
        return providerFirebaseUids;
    }

    private void clearValidationErrors() {
        if (tilCategory != null) {
            tilCategory.setError(null);
            tilCategory.setErrorEnabled(false);
        }
        if (tilDescription != null) {
            tilDescription.setError(null);
            tilDescription.setErrorEnabled(false);
        }
    }

    private void clearCategoryError() {
        if (tilCategory != null) {
            tilCategory.setError(null);
            tilCategory.setErrorEnabled(false);
        }
    }

    private void setLoading(boolean loading) {
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnSubmit != null) {
            btnSubmit.setEnabled(!loading);
            btnSubmit.setAlpha(loading ? 0.7f : 1f);
        }
    }

    private void setStatus(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }

    private String isoNow() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(new java.util.Date());
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString());
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private void setText(TextView view, String text) {
        if (view != null) {
            view.setText(text);
        }
    }

    private String fallback(String value, String fallback) {
        String clean = safe(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showToast(String text) {
        if (TextUtils.isEmpty(text)) return;
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQ) return;

        boolean granted = false;
        if (grantResults != null) {
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
        }
        if (granted) {
            openLocationPicker();
        } else {
            showToast("Location permission denied. Please select city manually.");
        }
    }
}