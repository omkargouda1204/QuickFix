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

import com.example.quickfix.BuildConfig;
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
            "Electrician",
            "Plumber",
            "AC Service",
            "Carpentry",
            "Cleaning",
            "Appliance Repair",
            "Painting",
            "General Repair"
    );

    private static final long STATUS_POLL_MS = 4000L;
    private static final double NEARBY_RADIUS_KM = 30d;
    private static final int PROVIDER_PAGE_SIZE = 500;
    private static final int PROVIDER_MAX_PAGES = 20;

    public static final String EXTRA_LOCATION_LABEL = "extra_location_label";
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
    private MaterialButton btnSubmit;
    private MaterialButton btnSelectLocation;
    private ProgressBar progress;
    private TextView tvStatus;
    private TextView tvName;
    private TextView tvPhone;
    private TextView tvAddress;
    private TextView tvLocationSummary;
    private TextView tvLocationHint;
    private MaterialAutoCompleteTextView actCategory;
    private TextInputEditText etDescription;
    private TextInputLayout tilCategory;
    private TextInputLayout tilDescription;
    private ChipGroup chipGroup;

    private View cardProviderAssigned;
    private TextView tvProviderName;
    private TextView tvProviderPhone;
    private TextView tvProviderService;
    private TextView tvProviderLocation;

    private String currentUserId = "";
    private String currentUserName = "";
    private String currentUserPhone = "";

    private String selectedCity = "";
    private String selectedArea = "";
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;

    private String activeRequestId = "";
    private boolean providerAssignedToastShown = false;
    private JSONObject providerSnapshotFromIntent = new JSONObject();
    private boolean isSubmitting = false;

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
        hydrateRequestFromIntent();
        setupFormValidation();
        updateSubmitButtonState();
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
                updateSubmitButtonState();
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
                    updateSubmitButtonState();
                }
            });
            chipGroup.addView(chip);
        }
    }

    private void setupFormValidation() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSubmitButtonState();
            }
        };

        if (actCategory != null) {
            actCategory.addTextChangedListener(watcher);
        }
        if (etDescription != null) {
            etDescription.addTextChangedListener(watcher);
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
                    updateSubmitButtonState();
                });
            } catch (Exception e) {
                Log.w(TAG, "loadProfile failed: " + e.getMessage());
                runOnUiThread(() -> {
                    setText(tvName, "Name: " + fallback(currentUserName, "-"));
                    setText(tvPhone, "Phone: " + fallback(currentUserPhone, "-"));
                    updateSubmitButtonState();
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
            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            if (arr.size() == 0) return null;
            return new JSONObject(arr.get(0).toString());
        }
    }

    private boolean hasValidSupabaseConfig() {
        return safe(BuildConfig.SUPABASE_URL).startsWith("http")
                && !safe(BuildConfig.SUPABASE_ANON_KEY).isEmpty();
    }

    private void updateLocationUi() {
        String label = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        if (label.isEmpty()) {
            label = hasCoordinates() ? "Pinned location selected" : "Location unavailable";
        }

        setText(tvLocationSummary, label);
        setText(tvAddress, "Location: " + label);

        boolean locationReady = hasCoordinates();
        if (btnSelectLocation != null) {
            btnSelectLocation.setVisibility(locationReady ? View.GONE : View.VISIBLE);
        }
        if (tvLocationHint != null) {
            tvLocationHint.setText(locationReady
                    ? "Using selected location to find active providers within 30 km."
                    : "Accurate location is required to find active providers within 30 km.");
        }
        updateSubmitButtonState();
    }

    private void submitEmergencyRequest() {
        if (!hasValidSupabaseConfig()) {
            setStatus("Status: Request failed");
            showToast("Supabase configuration is missing. Please contact support.");
            return;
        }

        if (!NetworkHelper.isOnline(this)) {
            setStatus("Status: No internet connection");
            showToast("No internet connection. Please try again.");
            return;
        }

        if (!validateForm(true)) return;

        String category = safe(actCategory != null ? String.valueOf(actCategory.getText()) : "");
        String description = safe(etDescription != null ? String.valueOf(etDescription.getText()) : "");
        String locationLabel = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        if (locationLabel.isEmpty() && hasCoordinates()) {
            locationLabel = "Pinned location";
        }
        final String finalLocationLabel = locationLabel;

        setLoading(true);
        setStatus("Status: Searching nearby providers...");

        new Thread(() -> {
            try {
                String requestId = createEmergencyRequest(category, description, finalLocationLabel);
                mirrorEmergencyBookingOnSubmit(requestId, category, description, finalLocationLabel);
                boolean providerLookupFailed = false;
                List<String> providerFirebaseUids = new ArrayList<>();
                try {
                    providerFirebaseUids = fetchMatchingProviderFirebaseUids(
                            category,
                            selectedCity,
                            selectedLat,
                            selectedLng
                    );
                } catch (Exception providerLookupError) {
                    providerLookupFailed = true;
                    Log.w(TAG, "Provider lookup failed after request create: " + providerLookupError.getMessage());
                }

                if (!providerFirebaseUids.isEmpty()) {
                    JSONObject data = new JSONObject();
                    data.put("request_id", requestId);
                    data.put("user_name", safe(currentUserName));
                    data.put("phone", safe(currentUserPhone));
                    data.put("service_type", safe(category));
                    data.put("category", safe(category));
                    data.put("city", safe(selectedCity));
                    data.put("address", finalLocationLabel);
                    data.put("location", finalLocationLabel);
                    data.put("description", safe(description));
                    data.put("contact_number", safe(currentUserPhone));
                    if (!Double.isNaN(selectedLat)) {
                        data.put("lat", String.valueOf(selectedLat));
                    }
                    if (!Double.isNaN(selectedLng)) {
                        data.put("lng", String.valueOf(selectedLng));
                    }

                    FcmNotificationHelper.notifyEmergencyProviders(
                            providerFirebaseUids,
                            "Emergency Request",
                            safe(currentUserName) + " needs " + safe(category)
                                    + " help at " + fallback(finalLocationLabel, "shared location")
                                    + ". Contact: " + fallback(currentUserPhone, "N/A"),
                            requestId,
                            data,
                            new FcmNotificationHelper.NotificationDispatchCallback() {
                                @Override
                                public void onSuccess(int sent, int totalTokens) {
                                    if (sent <= 0) {
                                        setStatus("Status: Request submitted. Notification delivery pending.");
                                        showToast("Request saved, but notification delivery is pending.");
                                    }
                                }

                                @Override
                                public void onFailure(String message) {
                                    setStatus("Status: Request submitted. Notification retry needed.");
                                    showToast(fallback(message, "Notification failed. Please try again."));
                                }
                            }
                    );
                }

                final int notifiedProviders = providerFirebaseUids.size();
                final boolean finalProviderLookupFailed = providerLookupFailed;
                runOnUiThread(() -> {
                    setLoading(false);
                    activeRequestId = requestId;
                    providerAssignedToastShown = false;
                    if (finalProviderLookupFailed) {
                        setStatus("Status: Request submitted. Searching nearby providers...");
                    } else if (notifiedProviders == 0) {
                        setStatus("Status: No providers available within 30 km.");
                    } else {
                        setStatus("Status: Request sent to nearby providers");
                    }
                    bindAssignedProviderCard(null, category);
                    startStatusPolling();
                    if (finalProviderLookupFailed) {
                        showToast("Emergency request submitted, but provider matching is delayed due to network.");
                    } else if (notifiedProviders > 0) {
                        showToast("Request sent to nearby providers");
                    } else {
                        showToast("No providers available within 30 km right now.");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "submitEmergencyRequest failed", e);
                runOnUiThread(() -> {
                    setLoading(false);
                    setStatus("Status: Submission failed");
                    if (e instanceof IOException) {
                        showToast(NetworkHelper.buildMessage((IOException) e, EmergencyHelpActivity.this));
                    } else {
                        String detailedError = fallback(safe(e.getMessage()), "Request failed, try again");
                        showToast(detailedError);
                    }
                });
            }
        }).start();
    }

    private String createEmergencyRequest(String category, String description, String locationLabel) throws Exception {
        JSONObject payload = buildEmergencyPayload(category, description, locationLabel, true);

        try {
            return postEmergencyRequest(payload);
        } catch (IllegalStateException firstError) {
            String message = safe(firstError.getMessage()).toLowerCase(Locale.US);
            boolean schemaMismatch = message.contains("column")
                    || message.contains("schema cache")
                    || message.contains("type")
                    || message.contains("service");
            if (!schemaMismatch) {
                throw firstError;
            }

            // Retry with legacy payload for older emergency_requests schema.
            JSONObject legacyPayload = buildEmergencyPayload(category, description, locationLabel, false);
            return postEmergencyRequest(legacyPayload);
        }
    }

    private JSONObject buildEmergencyPayload(String category,
                                            String description,
                                            String locationLabel,
                                            boolean includeExtendedFields) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("user_id", safe(currentUserId));
        payload.put("user_firebase_uid", safe(currentUserId));
        payload.put("name", safe(currentUserName));
        payload.put("user_name", safe(currentUserName));
        payload.put("phone", safe(currentUserPhone));
        payload.put("service", safe(category));
        payload.put("service_type", safe(category));
        payload.put("category", safe(category));
        payload.put("issue", safe(description));
        payload.put("description", safe(description));
        payload.put("address", safe(locationLabel));
        if (!Double.isNaN(selectedLat) && !Double.isNaN(selectedLng)) {
            payload.put("location", selectedLat + "," + selectedLng);
        }
        payload.put("status", "pending");
        payload.put("city", safe(selectedCity));
        payload.put("area", safe(selectedArea));
        if (!Double.isNaN(selectedLat)) {
            payload.put("lat", selectedLat);
        }
        if (!Double.isNaN(selectedLng)) {
            payload.put("lng", selectedLng);
        }
        if (includeExtendedFields) {
            payload.put("type", "Emergency");
            payload.put("timestamp", isoNow());
        }
        payload.put("created_at", isoNow());
        return payload;
    }

    private String postEmergencyRequest(JSONObject payload) throws Exception {
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
                String apiError = extractApiErrorMessage(body, "Submit failed HTTP " + response.code());
                throw new IllegalStateException(apiError);
            }

            JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
            if (arr.size() == 0) {
                throw new IllegalStateException("Emergency request created but id missing");
            }
            String newRequestId = safe(arr.get(0).getAsJsonObject().has("id")
                    ? arr.get(0).getAsJsonObject().get("id").getAsString()
                    : "");
            if (newRequestId.isEmpty()) {
                throw new IllegalStateException("Emergency request id missing");
            }
            return newRequestId;
        }
    }

    private void mirrorEmergencyBookingOnSubmit(String requestId,
                                                String category,
                                                String description,
                                                String locationLabel) {
        try {
            JSONObject booking = new JSONObject();
            booking.put("customer_id", safe(currentUserId));
            booking.put("customer_name", safe(currentUserName));
            booking.put("customer_phone", safe(currentUserPhone));
            booking.put("provider_id", JSONObject.NULL);
            booking.put("provider_name", "");
            booking.put("service_name", fallback(category, "Emergency"));
            booking.put("category", fallback(category, "Emergency"));
            
            // Add service_id for emergency bookings
            String serviceId = fetchServiceIdForCategory(fallback(category, "Emergency"));
            if (serviceId.isEmpty()) {
                booking.put("service_id", JSONObject.NULL);
            } else {
                booking.put("service_id", serviceId);
            }
            
            booking.put("address", safe(locationLabel));
            booking.put("description", safe(description));
            booking.put("status", "pending");
            booking.put("type", "Emergency");
            booking.put("is_emergency", true);
            booking.put("emergency_request_id", safe(requestId));
            booking.put("created_at", isoNow());

            if (tryInsertBooking(booking)) {
                return;
            }

            booking.remove("is_emergency");
            if (tryInsertBooking(booking)) {
                return;
            }

            booking.remove("type");
            booking.remove("emergency_request_id");
            booking.put("booking_date", new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(new java.util.Date()));
            booking.put("booking_time", new SimpleDateFormat("HH:mm:ss", Locale.US)
                    .format(new java.util.Date()));
            try (Response fallbackResponse = postBookingInsert(booking)) {
                if (fallbackResponse.code() >= 400) {
                    Log.w(TAG, "mirrorEmergencyBookingOnSubmit fallback HTTP " + fallbackResponse.code());
                }
            }
        } catch (Exception e) {
            // Keep emergency submission successful even if booking mirror write fails.
            Log.w(TAG, "mirrorEmergencyBookingOnSubmit failed: " + e.getMessage());
        }
    }

    private boolean tryInsertBooking(JSONObject payload) {
        try (Response response = postBookingInsert(payload)) {
            return response.code() == 200 || response.code() == 201 || response.code() == 204;
        } catch (Exception e) {
            Log.w(TAG, "tryInsertBooking failed: " + e.getMessage());
            return false;
        }
    }

    private Response postBookingInsert(JSONObject payload) throws Exception {
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/bookings")
                .post(RequestBody.create(payload.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();
        return http.newCall(request).execute();
    }
    
    private String fetchServiceIdForCategory(String category) {
        String cat = safe(category);
        if (cat.isEmpty()) return "";
        try {
            String url = BuildConfig.SUPABASE_URL
                    + "/rest/v1/services?select=id&category=eq." + android.net.Uri.encode(cat)
                    + "&limit=1";
            Request request = new Request.Builder().url(url)
                    .get()
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    if (arr.length() > 0) {
                        return arr.getJSONObject(0).optString("id", "");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchServiceIdForCategory: " + e.getMessage());
            return "";
        }
        return "";
    }

    private List<String> fetchMatchingProviderFirebaseUids(String category,
                                                           String city,
                                                           double requestLat,
                                                           double requestLng) throws Exception {
        if (Double.isNaN(requestLat) || Double.isNaN(requestLng)) {
            throw new IllegalStateException("Location coordinates missing for provider matching");
        }

        List<String> providerFirebaseUids = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        for (int page = 0; page < PROVIDER_MAX_PAGES; page++) {
            int offset = page * PROVIDER_PAGE_SIZE;
            StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/provider_profiles")
                    .append("?select=firebase_uid,category,city,status,is_approved,lat,lng,latitude,longitude")
                    .append("&limit=").append(PROVIDER_PAGE_SIZE)
                    .append("&offset=").append(offset);

            Request request = new Request.Builder()
                    .url(url.toString())
                    .get()
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            JsonArray arr;
            try (Response response = http.newCall(request).execute()) {
                if (response.code() != 200) {
                    String body = response.body() != null ? response.body().string() : "";
                    throw new IllegalStateException("Provider lookup HTTP " + response.code() + " " + body);
                }

                String body = response.body() != null ? response.body().string() : "[]";
                arr = JsonParser.parseString(body).getAsJsonArray();
            }

            if (arr.size() == 0) {
                break;
            }

            for (int i = 0; i < arr.size(); i++) {
                JSONObject o = new JSONObject(arr.get(i).toString());
                String providerFirebaseUid = safe(o.optString("firebase_uid", ""));
                String providerCategory = safe(o.optString("category", ""));
                String providerStatus = safe(o.optString("status", "")).toLowerCase(Locale.US);
                String providerCity = safe(o.optString("city", ""));
                double providerLat = pickDouble(o, "latitude", "lat");
                double providerLng = pickDouble(o, "longitude", "lng");

                if (providerFirebaseUid.isEmpty()) continue;
                if (!("active".equals(providerStatus))) continue;
                if (!CategoryMatcher.matches(category, providerCategory)) continue;

                if (Double.isNaN(providerLat) || Double.isNaN(providerLng)) {
                    continue;
                }

                double distanceKm = haversineKm(requestLat, requestLng, providerLat, providerLng);
                if (distanceKm > NEARBY_RADIUS_KM) {
                    continue;
                }

                dedupe.add(providerFirebaseUid);
            }

            if (arr.size() < PROVIDER_PAGE_SIZE) {
                break;
            }
        }
        providerFirebaseUids.addAll(dedupe);
        return providerFirebaseUids;
    }

    private void hydrateRequestFromIntent() {
        String requestId = safe(getIntent() != null ? getIntent().getStringExtra(EXTRA_REQUEST_ID) : "");
        if (requestId.isEmpty()) {
            requestId = safe(getIntent() != null ? getIntent().getStringExtra("reference_id") : "");
        }
        providerSnapshotFromIntent = readProviderSnapshotFromIntent();

        if (providerSnapshotFromIntent.length() > 0) {
            bindAssignedProviderCard(providerSnapshotFromIntent, "");
        }
        if (requestId.isEmpty()) return;

        activeRequestId = requestId;
        providerAssignedToastShown = false;
        setStatus("Status: Tracking request " + requestId);
        startStatusPolling();
    }

    private void startStatusPolling() {
        if (activeRequestId.isEmpty()) return;
        mainHandler.removeCallbacks(statusPollingRunnable);
        mainHandler.post(statusPollingRunnable);
    }

    private final Runnable statusPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed() || activeRequestId.isEmpty()) {
                return;
            }

            new Thread(() -> {
                try {
                    JSONObject request = fetchEmergencyRequestById(activeRequestId);
                    String status = safe(request.optString("status", "pending")).toLowerCase(Locale.US);
                    String assignedProviderId = safe(request.optString("assigned_provider_id", ""));
                    JSONObject provider = extractProviderSnapshotFromRequest(request);

                    if ("accepted".equals(status) && provider.length() == 0 && !assignedProviderId.isEmpty()) {
                        provider = fetchProviderById(assignedProviderId);
                    }

                    if (provider.length() == 0 && providerSnapshotFromIntent.length() > 0) {
                        provider = providerSnapshotFromIntent;
                    }

                    JSONObject finalProvider = provider;
                    runOnUiThread(() -> updateRequestStatusUi(request, finalProvider));

                    if ("accepted".equals(status) || "rejected".equals(status)) {
                        mainHandler.removeCallbacks(statusPollingRunnable);
                    } else {
                        mainHandler.postDelayed(statusPollingRunnable, STATUS_POLL_MS);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "status polling failed: " + e.getMessage());
                    mainHandler.postDelayed(statusPollingRunnable, STATUS_POLL_MS);
                }
            }).start();
        }
    };

    private JSONObject fetchEmergencyRequestById(String requestId) throws Exception {
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests"
                + "?id=eq." + encode(requestId)
                + "&select=id,status,category,assigned_provider_id,accepted_at,rejected_at,"
                + "assigned_provider_name,assigned_provider_phone,assigned_provider_service,"
                + "assigned_provider_city,assigned_provider_area"
                + "&limit=1";

        JSONObject row = fetchFirstRow(url);
        if (row == null) {
            throw new IllegalStateException("Request not found");
        }
        return row;
    }

    private JSONObject fetchProviderById(String providerId) {
        try {
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles"
                    + "?id=eq." + encode(providerId)
                    + "&select=id,business_name,phone,category,city,area"
                    + "&limit=1";
            JSONObject row = fetchFirstRow(url);
            return row == null ? new JSONObject() : row;
        } catch (Exception e) {
            Log.w(TAG, "fetchProviderById failed: " + e.getMessage());
            return new JSONObject();
        }
    }

    private void updateRequestStatusUi(JSONObject request, JSONObject provider) {
        if (request == null) return;

        String status = safe(request.optString("status", "pending")).toLowerCase(Locale.US);
        String label;
        switch (status) {
            case "accepted":
                label = "Accepted";
                break;
            case "rejected":
                label = "Rejected";
                break;
            case "pending":
            default:
                label = "Pending";
                break;
        }

        setStatus("Status: " + label + " (ID: " + fallback(activeRequestId, "-") + ")");

        String category = safe(request.optString("category", ""));
        bindAssignedProviderCard("accepted".equals(status) ? provider : null, category);

        if ("accepted".equals(status) && !providerAssignedToastShown) {
            providerAssignedToastShown = true;
            showToast("Provider accepted your emergency request.");
        }
    }

    private void bindAssignedProviderCard(JSONObject provider, String fallbackService) {
        if (cardProviderAssigned == null) return;

        if (provider == null || provider.length() == 0) {
            cardProviderAssigned.setVisibility(View.GONE);
            return;
        }

        cardProviderAssigned.setVisibility(View.VISIBLE);
        setText(tvProviderName, "Name: " + fallback(firstNonBlank(
                provider.optString("business_name", ""),
                provider.optString("assigned_provider_name", ""),
                provider.optString("provider_name", "")
        ), "Provider"));

        setText(tvProviderPhone, "Contact: " + fallback(firstNonBlank(
                provider.optString("phone", ""),
                provider.optString("assigned_provider_phone", ""),
                provider.optString("provider_phone", "")
        ), "Not available"));

        String service = firstNonBlank(
                provider.optString("category", ""),
                provider.optString("assigned_provider_service", ""),
                provider.optString("provider_service", ""),
                fallbackService
        );
        setText(tvProviderService, "Service: " + fallback(service, "Emergency Support"));

        String providerCity = firstNonBlank(
                provider.optString("city", ""),
                provider.optString("assigned_provider_city", ""),
                provider.optString("provider_city", "")
        );
        String providerArea = firstNonBlank(
                provider.optString("area", ""),
                provider.optString("assigned_provider_area", ""),
                provider.optString("provider_area", "")
        );
        String providerLocation = AppLocationManager.composeLocationLabel(providerCity, providerArea);
        setText(tvProviderLocation, "Location: " + fallback(providerLocation, "Not available"));
    }

    private JSONObject extractProviderSnapshotFromRequest(JSONObject request) {
        JSONObject snapshot = new JSONObject();
        if (request == null) return snapshot;

        try {
            String name = safe(request.optString("assigned_provider_name", ""));
            String phone = safe(request.optString("assigned_provider_phone", ""));
            String service = safe(request.optString("assigned_provider_service", ""));
            String city = safe(request.optString("assigned_provider_city", ""));
            String area = safe(request.optString("assigned_provider_area", ""));
            if (name.isEmpty() && phone.isEmpty() && service.isEmpty() && city.isEmpty() && area.isEmpty()) {
                return snapshot;
            }

            snapshot.put("assigned_provider_name", name);
            snapshot.put("assigned_provider_phone", phone);
            snapshot.put("assigned_provider_service", service);
            snapshot.put("assigned_provider_city", city);
            snapshot.put("assigned_provider_area", area);
        } catch (Exception ignored) {
            return new JSONObject();
        }
        return snapshot;
    }

    private JSONObject readProviderSnapshotFromIntent() {
        JSONObject snapshot = new JSONObject();
        if (getIntent() == null) return snapshot;
        try {
            String name = safe(getIntent().getStringExtra("provider_name"));
            String phone = firstNonBlank(
                    safe(getIntent().getStringExtra("provider_phone")),
                    safe(getIntent().getStringExtra("phone"))
            );
            String service = safe(getIntent().getStringExtra("provider_service"));
            String city = safe(getIntent().getStringExtra("provider_city"));
            String area = safe(getIntent().getStringExtra("provider_area"));
            if (name.isEmpty() && phone.isEmpty() && service.isEmpty() && city.isEmpty() && area.isEmpty()) {
                return snapshot;
            }

            snapshot.put("provider_name", name);
            snapshot.put("provider_phone", phone);
            snapshot.put("provider_service", service);
            snapshot.put("provider_city", city);
            snapshot.put("provider_area", area);
        } catch (Exception ignored) {
            return new JSONObject();
        }
        return snapshot;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String cleaned = safe(value);
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private double pickDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return Double.NaN;
        for (String key : keys) {
            double value = optDoubleFlexible(obj, key);
            if (!Double.isNaN(value)) {
                return value;
            }
        }
        return Double.NaN;
    }

    private double optDoubleFlexible(JSONObject obj, String key) {
        try {
            if (obj == null || key == null || !obj.has(key) || obj.isNull(key)) {
                return Double.NaN;
            }
            Object raw = obj.get(key);
            if (raw instanceof Number) {
                return ((Number) raw).doubleValue();
            }
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 2d * r * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
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

    private boolean validateForm(boolean showErrors) {
        if (showErrors) {
            clearValidationErrors();
        }

        String category = safe(actCategory != null ? String.valueOf(actCategory.getText()) : "");
        String description = safe(etDescription != null ? String.valueOf(etDescription.getText()) : "");

        boolean valid = true;

        if (category.isEmpty()) {
            valid = false;
            if (showErrors && tilCategory != null) {
                tilCategory.setError("Please choose a service category");
            }
        } else {
            clearCategoryError();
        }

        if (description.isEmpty()) {
            valid = false;
            if (showErrors && tilDescription != null) {
                tilDescription.setError("Please describe the problem");
            }
        } else if (description.length() < 10) {
            valid = false;
            if (showErrors && tilDescription != null) {
                tilDescription.setError("Description should be at least 10 characters");
            }
        } else if (tilDescription != null) {
            tilDescription.setError(null);
            tilDescription.setErrorEnabled(false);
        }

        if (currentUserName.isEmpty()) {
            valid = false;
            if (showErrors) {
                showToast("Profile name is missing. Please update your profile.");
            }
        }

        if (!isValidPhone(currentUserPhone)) {
            valid = false;
            if (showErrors) {
                showToast(currentUserPhone.isEmpty()
                        ? "Phone number is missing. Please update your profile."
                        : "Phone number looks invalid. Please update your profile.");
            }
        }

        if (!hasValidLocation()) {
            valid = false;
            if (showErrors) {
                showToast("Please select a location first.");
            }
        }

        if (!hasCoordinates()) {
            valid = false;
            if (showErrors) {
                showToast("Please select a precise location to find providers within 30 km.");
            }
        }

        return valid;
    }

    private void updateSubmitButtonState() {
        boolean canSubmit = !isSubmitting && validateForm(false);
        if (btnSubmit != null) {
            btnSubmit.setEnabled(canSubmit);
            btnSubmit.setAlpha(canSubmit ? 1f : 0.6f);
        }
    }

    private boolean hasValidLocation() {
        String locationLabel = AppLocationManager.composeLocationLabel(selectedCity, selectedArea);
        return !locationLabel.isEmpty() || hasCoordinates();
    }

    private boolean hasCoordinates() {
        return !Double.isNaN(selectedLat) && !Double.isNaN(selectedLng);
    }

    private boolean isValidPhone(String rawPhone) {
        String phone = safe(rawPhone).replaceAll("[^0-9]", "");
        return phone.length() >= 10;
    }

    private void setLoading(boolean loading) {
        isSubmitting = loading;
        if (progress != null) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        updateSubmitButtonState();
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

    private String extractApiErrorMessage(String body, String fallbackMessage) {
        String fallback = safe(fallbackMessage);
        if (fallback.isEmpty()) {
            fallback = "Request failed";
        }
        try {
            JSONObject json = new JSONObject(body == null ? "{}" : body);
            String message = firstNonBlank(
                    safe(json.optString("message", "")),
                    safe(json.optString("error_description", "")),
                    safe(json.optString("hint", "")),
                    safe(json.optString("details", "")),
                    safe(json.optString("error", ""))
            );
            return message.isEmpty() ? fallback : message;
        } catch (JSONException ignored) {
            String compact = safe(body);
            if (compact.length() > 180) {
                compact = compact.substring(0, 180) + "...";
            }
            return compact.isEmpty() ? fallback : compact;
        }
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

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        setIntent(intent);
        hydrateRequestFromIntent();
    }
}