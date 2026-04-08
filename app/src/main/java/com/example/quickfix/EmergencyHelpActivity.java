package com.example.quickfix;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * EmergencyHelpActivity — fast assistance flow with nearby provider prioritization.
 */
public class EmergencyHelpActivity extends BaseActivity {

    private static final String TAG = "EmergencyHelp";
    private static final int EMERGENCY_NOTIFY_LIMIT = 5;
    private static final long EMERGENCY_POLL_MS = 5000L;
    private static final long EMERGENCY_POLL_TIMEOUT_MS = 2 * 60_000L;
    private static final float EMERGENCY_MAX_DISTANCE_KM = 30f;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String[] CATEGORY_LABELS = new String[]{
            "Electrician",
            "Plumber",
            "Mechanic",
            "Medical"
    };

    private static final String[][] CATEGORY_KEYWORDS = new String[][]{
            {"electrician", "electrical", "wiring", "power"},
            {"plumber", "plumbing", "pipe", "leak", "water"},
            {"mechanic", "repair", "garage", "automotive", "vehicle"},
            {"medical", "doctor", "clinic", "health", "ambulance", "hospital"}
    };

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppLocationManager locationManager;
    private FirebaseAuth auth;

    private ImageView btnBack;
    private MaterialButton btnEmergencyRequest;
    private MaterialButton btnRetry;
    private TextView tvLocation;
    private TextView tvAssigned;
    private TextView tvEmptyTitle;
    private TextView tvEmptySubtitle;
    private TextView tvUserName;
    private TextView tvPhone;
    private TextView tvAddress;
    private MaterialAutoCompleteTextView actCategory;
    private EditText etDescription;
    private View progressBar;
    private RecyclerView rvProviders;
    private View emptyState;
    private ChipGroup chipGroup;

    private EmergencyProviderAdapter adapter;
    private final List<EmergencyProviderItem> providers = new ArrayList<>();
    private String selectedCategoryLabel = CATEGORY_LABELS[0];
    private int selectedCategoryIndex = 0;
    private boolean isLoading = false;
    private String assignedProviderId = "";
    private String assignedProviderUid = "";
    private String activeEmergencyRequestId = "";
    private long pollingStartedAtMs = 0L;
    private double currentLat = Double.NaN;
    private double currentLng = Double.NaN;
    private String currentLocationLabel = "";
    private String currentCity = "";
    private String currentUserName = "";
    private String currentUserPhone = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_help);

        auth = FirebaseAuth.getInstance();
        locationManager = new AppLocationManager(this);

        bindViews();
        setupRecycler();
        setupCategoryChips();
        setupCategoryDropdown();
        setupListeners();

        updateLocationLabel(locationManager != null ? locationManager.getSavedLocationLabel() : "");
        bindUserContactPreview();
        loadCurrentUserContact();
        restoreRequestFromIntent();
        loadProviders(false);
    }

    private void restoreRequestFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return;
        String requestId = safe(intent.getStringExtra("request_id"));
        if (TextUtils.isEmpty(requestId)) {
            requestId = safe(intent.getStringExtra("reference_id"));
        }
        if (TextUtils.isEmpty(requestId)) return;

        activeEmergencyRequestId = requestId;
        pollingStartedAtMs = System.currentTimeMillis();
        if (tvAssigned != null) {
            tvAssigned.setVisibility(View.VISIBLE);
            tvAssigned.setText("Checking emergency assignment status...");
        }
        pollEmergencyStatusOnce(requestId);
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEmergencyRequest = findViewById(R.id.btnEmergencyRequest);
        btnRetry = findViewById(R.id.btnRetryEmergency);
        tvLocation = findViewById(R.id.tvEmergencyLocation);
        tvAssigned = findViewById(R.id.tvAssignedProvider);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        tvUserName = findViewById(R.id.tvEmergencyUserName);
        tvPhone = findViewById(R.id.tvEmergencyPhone);
        tvAddress = findViewById(R.id.tvEmergencyAddress);
        actCategory = findViewById(R.id.actEmergencyCategory);
        etDescription = findViewById(R.id.etEmergencyDescription);
        progressBar = findViewById(R.id.progressEmergency);
        rvProviders = findViewById(R.id.rvEmergencyProviders);
        emptyState = findViewById(R.id.layoutEmptyState);
        chipGroup = findViewById(R.id.chipGroupEmergencyCategories);
    }

    private void setupCategoryDropdown() {
        if (actCategory == null) return;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                CATEGORY_LABELS
        );
        actCategory.setAdapter(adapter);
        actCategory.setText(selectedCategoryLabel, false);
        actCategory.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= CATEGORY_LABELS.length) return;
            selectedCategoryIndex = position;
            selectedCategoryLabel = CATEGORY_LABELS[position];
            syncCategoryChips();
            loadProviders(false);
        });
    }

    private void setupRecycler() {
        adapter = new EmergencyProviderAdapter();
        rvProviders.setLayoutManager(new LinearLayoutManager(this));
        rvProviders.setAdapter(adapter);
    }

    private void setupCategoryChips() {
        if (chipGroup == null) return;
        chipGroup.removeAllViews();
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            final int index = i;
            Chip chip = new Chip(this);
            chip.setText(CATEGORY_LABELS[i]);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChipIconResource(iconForCategory(i));
            chip.setChipIconVisible(true);
            chip.setCheckedIconVisible(false);
            chip.setCloseIconVisible(false);
            chip.setChecked(i == selectedCategoryIndex);
            chip.setOnClickListener(v -> {
                if (selectedCategoryIndex == index) return;
                selectedCategoryIndex = index;
                selectedCategoryLabel = CATEGORY_LABELS[index];
                syncCategoryChips();
                if (actCategory != null) {
                    actCategory.setText(selectedCategoryLabel, false);
                }
                loadProviders(false);
            });
            chipGroup.addView(chip);
        }
    }

    private void syncCategoryChips() {
        if (chipGroup == null) return;
        for (int c = 0; c < chipGroup.getChildCount(); c++) {
            View child = chipGroup.getChildAt(c);
            if (child instanceof Chip) {
                ((Chip) child).setChecked(c == selectedCategoryIndex);
            }
        }
    }

    private void bindUserContactPreview() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (TextUtils.isEmpty(currentUserName) && user != null) {
            currentUserName = safe(user.getDisplayName());
        }
        if (tvUserName != null) {
            tvUserName.setText("Name: " + (TextUtils.isEmpty(currentUserName) ? "-" : currentUserName));
        }
        if (tvPhone != null) {
            tvPhone.setText("Phone: " + (TextUtils.isEmpty(currentUserPhone) ? "-" : currentUserPhone));
        }
        if (tvAddress != null) {
            String location = TextUtils.isEmpty(currentLocationLabel)
                    ? (locationManager != null ? locationManager.getSavedLocationLabel() : "")
                    : currentLocationLabel;
            tvAddress.setText("Location: " + (TextUtils.isEmpty(location) ? "-" : location));
        }
    }

    private void setupListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        if (btnEmergencyRequest != null) {
            btnEmergencyRequest.setOnClickListener(v -> requestEmergencyHelp());
        }

        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> loadProviders(false));
        }
    }

    private void loadCurrentUserContact() {
        FirebaseUser user = auth != null ? auth.getCurrentUser() : null;
        if (user == null) return;

        if (TextUtils.isEmpty(currentUserName)) {
            currentUserName = safe(user.getDisplayName());
        }
        bindUserContactPreview();

        new Thread(() -> {
            try {
                String uid = urlEncode(user.getUid());
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/users"
                        + "?firebase_uid=eq." + uid
                        + "&select=name,phone&limit=1";

                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response response = http.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "[]";
                    if (response.code() != 200) return;
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() == 0) return;
                    JSONObject row = arr.getJSONObject(0);
                    currentUserName = safe(row.optString("name", currentUserName));
                    currentUserPhone = safe(row.optString("phone", currentUserPhone));
                    mainHandler.post(this::bindUserContactPreview);
                }
            } catch (Exception e) {
                Log.w(TAG, "loadCurrentUserContact failed: " + e.getMessage());
            }
        }).start();
    }

    private void requestEmergencyHelp() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showToast("Please log in again to request emergency help.");
            return;
        }

        if (!TextUtils.isEmpty(activeEmergencyRequestId)) {
            showToast("An emergency request is already active. Please wait for assignment.");
            return;
        }

        String selectedFromInput = actCategory != null ? safe(actCategory.getText() != null
                ? actCategory.getText().toString() : "") : "";
        if (!TextUtils.isEmpty(selectedFromInput)) {
            selectedCategoryLabel = selectedFromInput;
            for (int i = 0; i < CATEGORY_LABELS.length; i++) {
                if (CATEGORY_LABELS[i].equalsIgnoreCase(selectedCategoryLabel)) {
                    selectedCategoryIndex = i;
                    break;
                }
            }
            syncCategoryChips();
        }

        if (TextUtils.isEmpty(selectedCategoryLabel)) {
            showToast("Please select an emergency category.");
            return;
        }

        if (providers.isEmpty()) {
            loadProviders(true);
            return;
        }
        handleEmergencyRequest(new ArrayList<>(providers));
    }

    private void loadProviders(boolean triggerEmergencyFlow) {
        if (isLoading) return;
        isLoading = true;
        showLoading(true);
        currentLocationLabel = locationManager != null ? locationManager.getSavedLocationLabel() : "";
        currentLat = locationManager != null ? locationManager.getSavedLat() : Double.NaN;
        currentLng = locationManager != null ? locationManager.getSavedLng() : Double.NaN;
        currentCity = extractCity(currentLocationLabel);
        updateLocationLabel(currentLocationLabel);
        fetchProviders(triggerEmergencyFlow, currentLat, currentLng, currentLocationLabel, currentCity);
    }

    private void fetchProviders(boolean triggerEmergencyFlow,
                                double lat,
                                double lng,
                                String locationLabel,
                                String city) {
        new Thread(() -> {
            try {
                List<EmergencyProviderItem> fetched = queryProviders(selectedCategoryLabel, lat, lng, locationLabel, city);
                if (fetched.isEmpty()) {
                    fetched = queryProviders("", lat, lng, locationLabel, city);
                }

                List<EmergencyProviderItem> finalFetched = fetched;
                mainHandler.post(() -> {
                    isLoading = false;
                    showLoading(false);
                    providers.clear();
                    providers.addAll(finalFetched);
                    adapter.setItems(finalFetched);
                    updateEmptyState(finalFetched.isEmpty());

                    if (triggerEmergencyFlow) {
                        handleEmergencyRequest(finalFetched);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchProviders error", e);
                mainHandler.post(() -> {
                    isLoading = false;
                    showLoading(false);
                    providers.clear();
                    adapter.setItems(new ArrayList<>());
                    updateEmptyState(true);
                    showToast("Could not load emergency providers. Please try again.");
                });
            }
        }).start();
    }

    private List<EmergencyProviderItem> queryProviders(String categoryLabel,
                                                       double lat,
                                                       double lng,
                                                       String locationLabel,
                                                       String city) throws Exception {
        StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                .append("/rest/v1/provider_profiles?select=id,firebase_uid,business_name,owner_name,category,city,address,phone,email,description,experience_yrs,rating,is_available,availability_status,is_verified,status,is_approved,approved,lat,lng,latitude,longitude,business_image_url,review_count")
                .append("&or=(is_approved.eq.true,approved.eq.true,status.eq.active,status.eq.approved,is_active.eq.true)")
                .append("&is_available=eq.true")
                .append("&limit=180")
                .append("&order=rating.desc");

        if (!TextUtils.isEmpty(city)) {
            url.append("&city=ilike.*").append(urlEncode(city)).append("*");
        }

        String queryFilter = buildEmergencyFilter(categoryLabel);
        if (!TextUtils.isEmpty(queryFilter)) {
            url.append("&or=").append(queryFilter);
        }

        Request req = new Request.Builder()
                .url(url.toString())
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        List<EmergencyProviderItem> out = new ArrayList<>();
        try (Response r = http.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            if (r.code() != 200) {
                Log.w(TAG, "queryProviders HTTP " + r.code() + " body=" + body);
                return out;
            }

            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ProviderProfile profile = parseProvider(o);
                if (profile == null) continue;

                float distance = computeDistanceKm(lat, lng, profile.lat, profile.lng);
                if (Double.isNaN(lat) || Double.isNaN(lng)) {
                    distance = -1f;
                }
                if (distance >= 0f && distance > EMERGENCY_MAX_DISTANCE_KM) {
                    continue;
                }

                EmergencyProviderItem item = new EmergencyProviderItem(profile, distance);
                out.add(item);
            }
        }

        sortEmergencyProviders(out);
        return out;
    }

    private String buildEmergencyFilter(String categoryLabel) {
        String[] terms = termsForCategory(categoryLabel);
        if (terms == null || terms.length == 0) return "";

        List<String> conditions = new ArrayList<>();
        for (String term : terms) {
            String safe = urlEncode(term);
            conditions.add("business_name.ilike.*" + safe + "*");
            conditions.add("category.ilike.*" + safe + "*");
            conditions.add("description.ilike.*" + safe + "*");
            conditions.add("owner_name.ilike.*" + safe + "*");
        }
        return "(" + String.join(",", conditions) + ")";
    }

    private String[] termsForCategory(String categoryLabel) {
        if (TextUtils.isEmpty(categoryLabel)) return new String[0];
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            if (CATEGORY_LABELS[i].equalsIgnoreCase(categoryLabel.trim())) {
                return CATEGORY_KEYWORDS[i];
            }
        }
        return new String[]{categoryLabel.trim().toLowerCase(Locale.getDefault())};
    }

    private ProviderProfile parseProvider(JSONObject o) {
        try {
            ProviderProfile p = new ProviderProfile();
            p.id = o.optString("id", "");
            p.firebaseUid = o.optString("firebase_uid", "");
            p.businessName = o.optString("business_name", "");
            p.ownerName = o.optString("owner_name", "");
            p.category = o.optString("category", "");
            p.city = o.optString("city", "");
            p.address = o.optString("address", "");
            p.phone = o.optString("phone", "");
            p.email = o.optString("email", "");
            p.description = o.optString("description", "");
            p.experienceYrs = o.optString("experience_yrs", o.optString("experience", ""));
            p.businessImageUrl = o.optString("business_image_url", "");
            p.isVerified = o.optBoolean("is_verified", false);
            p.isApproved = o.optBoolean("is_approved", false) || o.optBoolean("approved", false);
            p.isAvailable = o.optBoolean("is_available", true);
            p.rating = (float) o.optDouble("rating", 0d);
            p.reviewCount = o.optInt("review_count", 0);
            p.status = o.optString("status", "");
            p.lat = pickDouble(o, "lat", "latitude");
            p.lng = pickDouble(o, "lng", "longitude");
            return p;
        } catch (Exception e) {
            Log.w(TAG, "parseProvider error: " + e.getMessage());
            return null;
        }
    }

    private double pickDouble(JSONObject obj, String... keys) {
        if (obj == null || keys == null) return Double.NaN;
        for (String key : keys) {
            if (key == null) continue;
            try {
                if (!obj.has(key) || obj.isNull(key)) continue;
                Object value = obj.get(key);
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                String s = String.valueOf(value).trim();
                if (!s.isEmpty()) return Double.parseDouble(s);
            } catch (Exception ignored) {
            }
        }
        return Double.NaN;
    }

    private float computeDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        if (Double.isNaN(lat1) || Double.isNaN(lng1) || Double.isNaN(lat2) || Double.isNaN(lng2)) {
            return -1f;
        }
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (float) (R * c);
    }

    private void sortEmergencyProviders(List<EmergencyProviderItem> list) {
        if (list == null) return;
        list.sort(new Comparator<EmergencyProviderItem>() {
            @Override
            public int compare(EmergencyProviderItem a, EmergencyProviderItem b) {
                int availableDiff = Boolean.compare(!a.isEmergencyReady(), !b.isEmergencyReady());
                if (availableDiff != 0) return availableDiff;

                boolean aHasDistance = a.distanceKm >= 0f;
                boolean bHasDistance = b.distanceKm >= 0f;
                if (aHasDistance && bHasDistance) {
                    int distanceDiff = Float.compare(a.distanceKm, b.distanceKm);
                    if (distanceDiff != 0) return distanceDiff;
                } else if (aHasDistance != bHasDistance) {
                    return aHasDistance ? -1 : 1;
                }

                int ratingDiff = Float.compare(b.provider.rating, a.provider.rating);
                if (ratingDiff != 0) return ratingDiff;
                return safe(a.provider.businessName).compareToIgnoreCase(safe(b.provider.businessName));
            }
        });
    }

    private void handleEmergencyRequest(List<EmergencyProviderItem> list) {
        if (list == null || list.isEmpty()) {
            showToast("No emergency providers were found. Please try a different category or location.");
            return;
        }

        List<EmergencyProviderItem> notifyTargets = new ArrayList<>();
        for (EmergencyProviderItem item : list) {
            if (item == null || item.provider == null) continue;
            if (TextUtils.isEmpty(item.provider.firebaseUid)) continue;
            notifyTargets.add(item);
            if (notifyTargets.size() >= EMERGENCY_NOTIFY_LIMIT) break;
        }

        if (notifyTargets.isEmpty()) {
            showToast("Nearby providers were found, but none can be notified right now.");
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showToast("Session expired. Please log in again.");
            return;
        }

        String description = etDescription != null && etDescription.getText() != null
                ? etDescription.getText().toString().trim()
                : "";
        String location = TextUtils.isEmpty(currentLocationLabel)
                ? (locationManager != null ? locationManager.getSavedLocationLabel() : "")
                : currentLocationLabel;
        final String phone = TextUtils.isEmpty(currentUserPhone) ? "" : currentUserPhone;
        final String name = TextUtils.isEmpty(currentUserName)
                ? safe(user.getDisplayName())
                : currentUserName;

        showLoading(true);
        new Thread(() -> {
            try {
                String requestId = insertEmergencyRequest(
                        user.getUid(),
                        name,
                        phone,
                        selectedCategoryLabel,
                        description,
                        location,
                        currentLat,
                        currentLng
                );

                List<String> firebaseUids = new ArrayList<>();
                for (EmergencyProviderItem item : notifyTargets) {
                    firebaseUids.add(item.provider.firebaseUid);
                }

                String title = "Emergency Request";
                String body = "New urgent " + selectedCategoryLabel + " job near "
                        + (TextUtils.isEmpty(location) ? "your area" : location);

                JSONObject data = new JSONObject();
                data.put("request_id", requestId);
                data.put("user_name", TextUtils.isEmpty(name) ? "Customer" : name);
                data.put("phone", phone);
                data.put("address", TextUtils.isEmpty(location) ? "N/A" : location);
                data.put("category", selectedCategoryLabel);
                data.put("description", TextUtils.isEmpty(description) ? "Not specified" : description);

                FcmNotificationHelper.notifyEmergencyProviders(firebaseUids, title, body, requestId, data);

                mainHandler.post(() -> {
                    activeEmergencyRequestId = requestId;
                    pollingStartedAtMs = System.currentTimeMillis();
                    showLoading(false);
                    tvAssigned.setVisibility(View.VISIBLE);
                    tvAssigned.setText("Emergency request sent. Waiting for provider acceptance...");
                    showToast("Emergency request sent to nearby providers.");
                    startEmergencyStatusPolling(requestId);
                });
            } catch (Exception e) {
                Log.e(TAG, "handleEmergencyRequest failed", e);
                mainHandler.post(() -> {
                    showLoading(false);
                    showToast("Could not send emergency request. Please try again.");
                });
            }
        }).start();
    }

    private String insertEmergencyRequest(String userUid,
                                          String userName,
                                          String phone,
                                          String category,
                                          String description,
                                          String address,
                                          double lat,
                                          double lng) throws Exception {
        JSONObject body = new JSONObject();
        body.put("user_id", safe(userUid));
        body.put("user_name", safe(userName));
        body.put("phone", safe(phone));
        body.put("category", safe(category));
        body.put("description", safe(description));
        body.put("address", safe(address));
        body.put("latitude", Double.isNaN(lat) ? JSONObject.NULL : lat);
        body.put("longitude", Double.isNaN(lng) ? JSONObject.NULL : lng);
        body.put("status", "pending");

        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests")
                .post(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200 && response.code() != 201) {
                throw new IllegalStateException("Emergency request failed: HTTP " + response.code() + " " + resp);
            }
            JSONArray arr = new JSONArray(resp);
            if (arr.length() == 0) throw new IllegalStateException("Emergency request id missing");
            String id = arr.getJSONObject(0).optString("id", "");
            if (TextUtils.isEmpty(id)) throw new IllegalStateException("Emergency request id missing");
            return id;
        }
    }

    private void startEmergencyStatusPolling(String requestId) {
        if (TextUtils.isEmpty(requestId)) return;
        mainHandler.postDelayed(() -> pollEmergencyStatusOnce(requestId), EMERGENCY_POLL_MS);
    }

    private void pollEmergencyStatusOnce(String requestId) {
        if (!requestId.equals(activeEmergencyRequestId)) return;

        new Thread(() -> {
            try {
                JSONObject request = fetchEmergencyRequestById(requestId);
                String status = safe(request.optString("status", "pending")).toLowerCase(Locale.getDefault());
                if ("accepted".equals(status)) {
                    String providerId = safe(request.optString("assigned_provider_id", ""));
                    JSONObject provider = fetchProviderById(providerId);
                    EmergencyProviderItem assigned = providerToEmergencyItem(provider);

                    mainHandler.post(() -> {
                        activeEmergencyRequestId = "";
                        assignedProviderId = providerId;
                        assignedProviderUid = safe(provider.optString("firebase_uid", ""));
                        updateAssignedProvider(assigned);
                        showToast("Provider assigned successfully.");
                        openAssignedProvider(assigned);
                    });
                    return;
                }

                if ("cancelled".equals(status) || "expired".equals(status) || "rejected".equals(status)) {
                    mainHandler.post(() -> {
                        activeEmergencyRequestId = "";
                        showToast("Emergency request ended. Please try again.");
                        updateAssignedProvider(null);
                    });
                    return;
                }

                long elapsed = System.currentTimeMillis() - pollingStartedAtMs;
                if (elapsed > EMERGENCY_POLL_TIMEOUT_MS) {
                    mainHandler.post(() -> {
                        activeEmergencyRequestId = "";
                        showToast("No provider accepted yet. Please retry emergency request.");
                    });
                    return;
                }

                mainHandler.postDelayed(() -> pollEmergencyStatusOnce(requestId), EMERGENCY_POLL_MS);
            } catch (Exception e) {
                Log.w(TAG, "pollEmergencyStatusOnce: " + e.getMessage());
                mainHandler.postDelayed(() -> pollEmergencyStatusOnce(requestId), EMERGENCY_POLL_MS);
            }
        }).start();
    }

    private JSONObject fetchEmergencyRequestById(String requestId) throws Exception {
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/emergency_requests?id=eq." + urlEncode(requestId)
                        + "&select=id,status,assigned_provider_id,accepted_at,user_id,user_name,phone,category,address,description&limit=1")
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) {
                throw new IllegalStateException("fetchEmergencyRequestById HTTP " + response.code());
            }
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) throw new IllegalStateException("Emergency request not found");
            return arr.getJSONObject(0);
        }
    }

    private JSONObject fetchProviderById(String providerId) throws Exception {
        if (TextUtils.isEmpty(providerId)) return new JSONObject();
        Request request = new Request.Builder()
                .url(BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?id=eq." + urlEncode(providerId)
                        + "&select=id,firebase_uid,business_name,owner_name,category,city,address,phone,email,description,experience_yrs,rating,is_available,status,is_verified,business_image_url,lat,lng,latitude,longitude&limit=1")
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "[]";
            if (response.code() != 200) return new JSONObject();
            JSONArray arr = new JSONArray(body);
            return arr.length() > 0 ? arr.getJSONObject(0) : new JSONObject();
        }
    }

    private EmergencyProviderItem providerToEmergencyItem(JSONObject providerRow) {
        ProviderProfile profile = parseProvider(providerRow != null ? providerRow : new JSONObject());
        if (profile == null) {
            profile = new ProviderProfile();
            profile.businessName = "Provider";
        }
        float distance = computeDistanceKm(currentLat, currentLng, profile.lat, profile.lng);
        return new EmergencyProviderItem(profile, distance);
    }

    private String extractCity(String locationLabel) {
        String normalized = safe(locationLabel);
        if (TextUtils.isEmpty(normalized)) {
            return locationManager != null ? safe(locationManager.getSavedCity()) : "";
        }
        int comma = normalized.indexOf(',');
        if (comma <= 0) return normalized;
        return normalized.substring(0, comma).trim();
    }

    private void updateAssignedProvider(EmergencyProviderItem assigned) {
        if (tvAssigned == null) return;
        if (assigned == null || assigned.provider == null) {
            tvAssigned.setText("");
            tvAssigned.setVisibility(View.GONE);
            return;
        }
        String distance = assigned.distanceKm >= 0f
                ? String.format(Locale.getDefault(), "%.1f km away", assigned.distanceKm)
                : "Distance unavailable";
        String eta = assigned.distanceKm >= 0f
                ? String.format(Locale.getDefault(), "ETA %d mins", Math.max(5, Math.round(assigned.distanceKm * 4)))
                : "ETA updating";
        tvAssigned.setVisibility(View.VISIBLE);
        tvAssigned.setText("Assigned: " + safe(assigned.provider.businessName)
                + "\nPhone: " + safe(assigned.provider.phone)
                + "\n" + distance + " • " + eta);
        adapter.setAssignedProviderId(assigned.provider.id);
    }

    private void openAssignedProvider(EmergencyProviderItem assigned) {
        if (assigned == null || assigned.provider == null) return;
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, assigned.provider.id);
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID, assigned.provider.firebaseUid);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, assigned.provider.businessName);
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME, assigned.provider.ownerName);
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY, assigned.provider.category);
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY, assigned.provider.city);
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE, assigned.provider.phone);
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL, assigned.provider.email);
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION, assigned.provider.description);
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE, assigned.provider.experienceYrs);
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL, assigned.provider.businessImageUrl);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED, assigned.provider.isVerified);
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING, assigned.provider.rating);
        intent.putExtra(ProviderDetailActivity.EXTRA_OPEN_BOOKING, true);
        if (auth.getCurrentUser() != null) {
            intent.putExtra(ProviderDetailActivity.EXTRA_CUSTOMER_ID, auth.getCurrentUser().getUid());
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    private void openDialer(EmergencyProviderItem item) {
        if (item == null || item.provider == null || TextUtils.isEmpty(item.provider.phone)) {
            showToast("Phone number unavailable for this provider.");
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.provider.phone.trim())));
        } catch (Exception e) {
            showToast("Unable to open dialer.");
        }
    }

    private void openBookNow(EmergencyProviderItem item) {
        if (item == null || item.provider == null) return;
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, item.provider.id);
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID, item.provider.firebaseUid);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, item.provider.businessName);
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME, item.provider.ownerName);
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY, item.provider.category);
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY, item.provider.city);
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE, item.provider.phone);
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL, item.provider.email);
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION, item.provider.description);
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE, item.provider.experienceYrs);
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL, item.provider.businessImageUrl);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED, item.provider.isVerified);
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING, item.provider.rating);
        intent.putExtra(ProviderDetailActivity.EXTRA_OPEN_BOOKING, true);
        if (auth.getCurrentUser() != null) {
            intent.putExtra(ProviderDetailActivity.EXTRA_CUSTOMER_ID, auth.getCurrentUser().getUid());
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    private void openChat(EmergencyProviderItem item) {
        if (item == null || item.provider == null) {
            showToast("Provider not available for chat.");
            return;
        }
        Intent intent = new Intent(this, MessagesActivity.class);
        intent.putExtra(MessagesActivity.EXTRA_CONTEXT_TITLE, safe(item.provider.businessName));
        intent.putExtra(MessagesActivity.EXTRA_CONTEXT_SUBTITLE,
                safe(item.provider.category) + (TextUtils.isEmpty(item.provider.city) ? "" : " • " + item.provider.city));
        intent.putExtra(MessagesActivity.EXTRA_CONTEXT_BODY,
                "Chat support for emergency assistance is coming soon. You can call or book now for faster help.");
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    private void updateLocationLabel(String label) {
        if (tvLocation == null) return;
        String base = TextUtils.isEmpty(label) ? "Location unavailable" : label;
        tvLocation.setText(base + " • " + selectedCategoryLabel + " focus");
        bindUserContactPreview();
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvProviders != null) rvProviders.setVisibility(show ? View.GONE : View.VISIBLE);
        if (emptyState != null && show) emptyState.setVisibility(View.GONE);
    }

    private void updateEmptyState(boolean empty) {
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (tvEmptyTitle != null) {
            tvEmptyTitle.setText(empty
                    ? "No emergency-ready providers found"
                    : "");
        }
        if (tvEmptySubtitle != null) {
            tvEmptySubtitle.setText(empty
                    ? "Try another category or widen your search area. We’ll still show the fastest available options when they appear."
                    : "");
        }
    }

    private void showToast(String message) {
        if (TextUtils.isEmpty(message)) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static float distanceOrFallback(float distanceKm) {
        return distanceKm < 0f ? -1f : distanceKm;
    }

    private static String formatDistance(float distanceKm) {
        if (distanceKm < 0f) return "Distance n/a";
        return String.format(Locale.getDefault(), "%.1f km", distanceKm);
    }

    private static int iconForCategory(int index) {
        switch (index) {
            case 0: return R.drawable.ic_electrical;
            case 1: return R.drawable.ic_plumbing;
            case 2: return R.drawable.ic_repair;
            case 3: return R.drawable.ic_emergency;
            default: return R.drawable.ic_emergency;
        }
    }

    @Override
    protected void onDestroy() {
        activeEmergencyRequestId = "";
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final class EmergencyProviderAdapter extends RecyclerView.Adapter<EmergencyProviderAdapter.VH> {
        private final List<EmergencyProviderItem> items = new ArrayList<>();
        private String assignedProviderId = "";

        void setItems(List<EmergencyProviderItem> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }

        void setAssignedProviderId(String id) {
            assignedProviderId = safe(id);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_emergency_provider_card, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            EmergencyProviderItem item = items.get(position);
            ProviderProfile p = item.provider;

            h.card.setStrokeWidth(safe(p.id).equals(assignedProviderId) ? 3 : 0);
            h.card.setStrokeColor(ContextCompat.getColor(EmergencyHelpActivity.this, R.color.primary));

            h.tvBusinessName.setText(safe(p.businessName).isEmpty() ? "Provider" : p.businessName);
            h.tvOwnerName.setText(TextUtils.isEmpty(p.ownerName) ? "" : "by " + p.ownerName);
            h.tvCategory.setText(TextUtils.isEmpty(p.category) ? selectedCategoryLabel : p.category);
            h.tvCity.setText(TextUtils.isEmpty(p.city) ? "Location unavailable" : p.city);
            h.tvDescription.setText(TextUtils.isEmpty(p.description)
                    ? "Available for urgent assistance."
                    : p.description);
            h.tvRating.setText(String.format(Locale.getDefault(), "%.1f ★", p.rating <= 0 ? 0f : p.rating));
            h.tvDistance.setText(formatDistance(item.distanceKm));

            if (item.isEmergencyReady()) {
                h.tvAvailability.setText("Emergency ready");
                h.tvAvailability.setBackgroundResource(R.drawable.bg_category_green);
                h.tvAvailability.setTextColor(ContextCompat.getColor(EmergencyHelpActivity.this, android.R.color.white));
            } else {
                String status = TextUtils.isEmpty(p.status) ? (p.isAvailable ? "Available" : "Busy") : p.status;
                h.tvAvailability.setText(status);
                h.tvAvailability.setBackgroundResource(R.drawable.bg_category_grey);
                h.tvAvailability.setTextColor(ContextCompat.getColor(EmergencyHelpActivity.this, R.color.text_primary));
            }

            if (!TextUtils.isEmpty(p.businessImageUrl) && h.ivImage != null) {
                Glide.with(EmergencyHelpActivity.this)
                        .load(p.businessImageUrl)
                        .centerCrop()
                        .placeholder(R.drawable.bg_profile_header_gradient)
                        .error(R.drawable.bg_profile_header_gradient)
                        .into(h.ivImage);
            } else if (h.ivImage != null) {
                h.ivImage.setImageResource(R.drawable.bg_profile_header_gradient);
            }

            h.btnCall.setOnClickListener(v -> openDialer(item));
            h.btnBook.setOnClickListener(v -> openBookNow(item));
            h.btnChat.setOnClickListener(v -> openChat(item));

            h.btnCall.setEnabled(!TextUtils.isEmpty(p.phone));
            h.btnCall.setAlpha(TextUtils.isEmpty(p.phone) ? 0.5f : 1f);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView ivImage;
            final TextView tvBusinessName;
            final TextView tvOwnerName;
            final TextView tvCategory;
            final TextView tvCity;
            final TextView tvDescription;
            final TextView tvDistance;
            final TextView tvAvailability;
            final TextView tvRating;
            final MaterialButton btnCall;
            final MaterialButton btnBook;
            final MaterialButton btnChat;

            VH(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardEmergencyProvider);
                ivImage = itemView.findViewById(R.id.ivEmergencyBusinessImage);
                tvBusinessName = itemView.findViewById(R.id.tvEmergencyBusinessName);
                tvOwnerName = itemView.findViewById(R.id.tvEmergencyOwnerName);
                tvCategory = itemView.findViewById(R.id.tvEmergencyCategory);
                tvCity = itemView.findViewById(R.id.tvEmergencyCity);
                tvDescription = itemView.findViewById(R.id.tvEmergencyDescription);
                tvDistance = itemView.findViewById(R.id.tvEmergencyDistance);
                tvAvailability = itemView.findViewById(R.id.tvEmergencyAvailability);
                tvRating = itemView.findViewById(R.id.tvEmergencyRating);
                btnCall = itemView.findViewById(R.id.btnEmergencyCall);
                btnBook = itemView.findViewById(R.id.btnEmergencyBook);
                btnChat = itemView.findViewById(R.id.btnEmergencyChat);
            }
        }
    }

    private static final class EmergencyProviderItem {
        final ProviderProfile provider;
        final float distanceKm;

        EmergencyProviderItem(ProviderProfile provider, float distanceKm) {
            this.provider = provider;
            this.distanceKm = distanceOrFallback(distanceKm);
        }

        boolean isEmergencyReady() {
            if (provider == null) return false;
            if (provider.isAvailable) return true;
            String status = provider.status == null ? "" : provider.status.trim().toLowerCase(Locale.getDefault());
            return status.contains("active") || status.contains("available") || status.contains("ready");
        }
    }

}

