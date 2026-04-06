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
import android.widget.ImageView;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * EmergencyHelpActivity — fast assistance flow with nearby provider prioritization.
 */
public class EmergencyHelpActivity extends BaseActivity {

    private static final String TAG = "EmergencyHelp";
    private static final int EMERGENCY_NOTIFY_LIMIT = 5;

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
    private double currentLat = Double.NaN;
    private double currentLng = Double.NaN;
    private String currentLocationLabel = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_help);

        auth = FirebaseAuth.getInstance();
        locationManager = new AppLocationManager(this);

        bindViews();
        setupRecycler();
        setupCategoryChips();
        setupListeners();

        updateLocationLabel(locationManager != null ? locationManager.getSavedLocationLabel() : "");
        loadProviders(false);
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEmergencyRequest = findViewById(R.id.btnEmergencyRequest);
        btnRetry = findViewById(R.id.btnRetryEmergency);
        tvLocation = findViewById(R.id.tvEmergencyLocation);
        tvAssigned = findViewById(R.id.tvAssignedProvider);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle);
        progressBar = findViewById(R.id.progressEmergency);
        rvProviders = findViewById(R.id.rvEmergencyProviders);
        emptyState = findViewById(R.id.layoutEmptyState);
        chipGroup = findViewById(R.id.chipGroupEmergencyCategories);
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
                for (int c = 0; c < chipGroup.getChildCount(); c++) {
                    View child = chipGroup.getChildAt(c);
                    if (child instanceof Chip) {
                        ((Chip) child).setChecked(c == index);
                    }
                }
                loadProviders(false);
            });
            chipGroup.addView(chip);
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

    private void requestEmergencyHelp() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showToast("Please log in again to request emergency help.");
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

        captureCurrentLocation(new LocationResult() {
            @Override
            public void onSuccess(String locationLabel, double lat, double lng) {
                currentLocationLabel = locationLabel;
                currentLat = lat;
                currentLng = lng;
                updateLocationLabel(locationLabel);
                fetchProviders(triggerEmergencyFlow, lat, lng, locationLabel);
            }

            @Override
            public void onFailure(String reason) {
                currentLat = Double.NaN;
                currentLng = Double.NaN;
                currentLocationLabel = locationManager != null ? locationManager.getSavedLocationLabel() : "";
                updateLocationLabel(currentLocationLabel);
                Log.d(TAG, "Location fallback: " + reason);
                fetchProviders(triggerEmergencyFlow, Double.NaN, Double.NaN, currentLocationLabel);
            }
        });
    }

    private void captureCurrentLocation(LocationResult callback) {
        if (locationManager == null) {
            if (callback != null) callback.onFailure("Location manager unavailable");
            return;
        }

        locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
            @Override
            public void onSuccess(String city, String area, double lat, double lng) {
                String label = AppLocationManager.composeLocationLabel(city, area);
                if (TextUtils.isEmpty(label)) {
                    label = city != null ? city : "";
                }
                if (callback != null) callback.onSuccess(label, lat, lng);
            }

            @Override
            public void onFailure(String reason) {
                if (callback != null) callback.onFailure(reason);
            }
        });
    }

    private void fetchProviders(boolean triggerEmergencyFlow, double lat, double lng, String locationLabel) {
        new Thread(() -> {
            try {
                List<EmergencyProviderItem> fetched = queryProviders(selectedCategoryLabel, lat, lng, locationLabel);
                if (fetched.isEmpty()) {
                    fetched = queryProviders("", lat, lng, locationLabel);
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
                                                       String locationLabel) throws Exception {
        StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                .append("/rest/v1/provider_profiles?select=id,firebase_uid,business_name,owner_name,category,city,address,phone,email,description,experience_yrs,rating,is_available,availability_status,is_verified,status,is_approved,approved,lat,lng,latitude,longitude,business_image_url,review_count")
                .append("&or=(is_approved.eq.true,approved.eq.true,status.eq.active,status.eq.approved,is_active.eq.true)")
                .append("&limit=180")
                .append("&order=rating.desc");

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
            conditions.add("city.ilike.*" + safe + "*");
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

        EmergencyProviderItem assigned = null;
        for (EmergencyProviderItem item : list) {
            if (item != null && item.isEmergencyReady()) {
                assigned = item;
                break;
            }
        }
        if (assigned == null) {
            assigned = list.get(0);
        }

        assignedProviderId = assigned.provider.id;
        assignedProviderUid = assigned.provider.firebaseUid;
        updateAssignedProvider(assigned);

        List<String> firebaseUids = new ArrayList<>();
        for (EmergencyProviderItem item : notifyTargets) {
            firebaseUids.add(item.provider.firebaseUid);
        }

        String location = TextUtils.isEmpty(currentLocationLabel)
                ? (locationManager != null ? locationManager.getSavedLocationLabel() : "")
                : currentLocationLabel;
        String title = selectedCategoryLabel + " Emergency Help";
        String body = "A nearby customer needs immediate " + selectedCategoryLabel.toLowerCase(Locale.getDefault())
                + (TextUtils.isEmpty(location) ? " assistance." : " help near " + location + ".");

        FcmNotificationHelper.notifyEmergencyProviders(firebaseUids, title, body, assignedProviderId);

        showToast("Emergency request sent. Assigning " + safe(assigned.provider.businessName) + ".");
        openAssignedProvider(assigned);
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
        tvAssigned.setVisibility(View.VISIBLE);
        tvAssigned.setText("Assigned: " + safe(assigned.provider.businessName) + " • " + distance);
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

    private interface LocationResult {
        void onSuccess(String locationLabel, double lat, double lng);
        void onFailure(String reason);
    }
}

