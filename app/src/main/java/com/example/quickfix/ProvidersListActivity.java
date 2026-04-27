package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ProvidersAdapter;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProvidersListActivity — shows approved providers filtered by category + city.
 * Launched from home screen category icons or CategoriesActivity.
 *
 * Fixes:
 *  - Queries both is_approved filter AND status=active fallback (avoids Connection Error).
 *  - Live search bar filters by business name, category, owner name.
 *  - All / Verified / Top Rated chip filters work correctly.
 *  - Location change button reloads with new city.
 */
public class ProvidersListActivity extends BaseActivity
        implements ProvidersAdapter.OnProviderActionListener {

    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_CITY = "city";
    public static final String EXTRA_QUERY = "query";

    private static final String TAG = "ProvidersListActivity";
    private static final int LOCATION_PERMISSION_REQ = 4301;
    private static final float NEARBY_RADIUS_PRIMARY_METERS = 20000f;
    private static final float NEARBY_RADIUS_SECONDARY_METERS = 25000f;
    private static final int PROVIDERS_PAGE_SIZE = 10;

    // ── Views ─────────────────────────────────────────────────────────────
    private TextView          tvCategoryTitle, tvResultSubtitle, tvLocationLabel, tvEmptySubtitle;
    private ImageView         btnBack;
    private RecyclerView      rvProviders;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout      loadingState, emptyState, errorState;
    private ChipGroup         chipGroupSort;
    private EditText          etSearch;
    private ImageView         btnClearSearch;
    private ImageView         btnSearchFilter;
    private LinearLayout      searchBar;
    private com.google.android.material.button.MaterialButton btnLoadMoreProviders;

    // ── Data ──────────────────────────────────────────────────────────────
    private ProvidersAdapter      adapter;
    private List<ProviderProfile> allProviders  = new ArrayList<>();
    private List<ProviderProfile> latestFilteredProviders = new ArrayList<>();
    private int visibleProvidersCount = PROVIDERS_PAGE_SIZE;
    private String                category      = "";
    private String                cityFilter    = "";
    private String                currentChip   = "All";
    private String                searchQuery   = "";
    private AppLocationManager    locationManager;
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_providers_list);

        category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (category == null) category = "";
        category = category.trim();

        locationManager = new AppLocationManager(this);
        SharedPreferences prefs = getSharedPreferences(AppLocationManager.PREFS_NAME, MODE_PRIVATE);
        String incomingCity = getIntent().getStringExtra(EXTRA_CITY);
        String savedCity = prefs.getString(AppLocationManager.KEY_CITY_LEGACY,
                prefs.getString(AppLocationManager.KEY_CITY, ""));
        cityFilter = incomingCity != null && !incomingCity.trim().isEmpty()
                ? incomingCity.trim()
                : (savedCity != null ? savedCity.trim() : "");

        searchQuery = getIntent().getStringExtra(EXTRA_QUERY);
        if (searchQuery == null) searchQuery = "";

        selectedLat = locationManager.getSavedLat();
        selectedLng = locationManager.getSavedLng();

        bindViews();
        setupRecycler();
        setupChips();
        setupSwipeRefresh();
        setupSearch();
        updateHeader();
        loadProviders();

        if (!searchQuery.isEmpty() && etSearch != null) {
            etSearch.setText(searchQuery);
            etSearch.setSelection(searchQuery.length());
        }
    }

    // ── Bind ─────────────────────────────────────────────────────────────
    private void bindViews() {
        tvCategoryTitle  = findViewById(R.id.tvCategoryTitle);
        tvResultSubtitle = findViewById(R.id.tvResultSubtitle);
        tvLocationLabel  = findViewById(R.id.tvLocationLabel);
        tvEmptySubtitle  = findViewById(R.id.tvEmptySubtitle);
        btnBack          = findViewById(R.id.btnBack);
        rvProviders      = findViewById(R.id.rvProviders);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        loadingState     = findViewById(R.id.loadingState);
        emptyState       = findViewById(R.id.emptyState);
        errorState       = findViewById(R.id.errorState);
        chipGroupSort    = findViewById(R.id.chipGroupSort);
        etSearch         = findViewById(R.id.etSearch);
        btnClearSearch   = findViewById(R.id.btnClearSearch);
        btnSearchFilter  = findViewById(R.id.btnSearchFilter);
        searchBar        = findViewById(R.id.searchBar);
        btnLoadMoreProviders = findViewById(R.id.btnLoadMoreProviders);

        btnBack.setOnClickListener(v -> finish());

        if (tvLocationLabel != null) {
            tvLocationLabel.setVisibility(View.GONE);
        }
        View btnChangeLocation = findViewById(R.id.btnChangeLocation);
        if (btnChangeLocation != null) {
            btnChangeLocation.setVisibility(View.GONE);
        }

        if (btnSearchFilter != null) {
            btnSearchFilter.setOnClickListener(v -> openLocationFilterSheet());
        }

        View btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) btnRetry.setOnClickListener(v -> loadProviders());
        View btnRetryError = findViewById(R.id.btnRetryError);
        if (btnRetryError != null) btnRetryError.setOnClickListener(v -> loadProviders());

        if (btnLoadMoreProviders != null) {
            btnLoadMoreProviders.setOnClickListener(v -> {
                visibleProvidersCount += PROVIDERS_PAGE_SIZE;
                renderProviderList(latestFilteredProviders, false);
            });
        }
    }

    private void setupRecycler() {
        adapter = new ProvidersAdapter(this, this);
        rvProviders.setLayoutManager(new LinearLayoutManager(this));
        rvProviders.setAdapter(adapter);
    }

    private void setupChips() {
        if (chipGroupSort == null) return;
        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chipAll)      currentChip = "All";
            else if (id == R.id.chipVerified) currentChip = "Verified";
            else if (id == R.id.chipTopRated) currentChip = "TopRated";
            applyFilterAndSearch();
        });
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadProviders);
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int count) {
                searchQuery = s.toString().trim();
                if (btnClearSearch != null)
                    btnClearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilterAndSearch();
            }
        });
        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearch.setText("");
                searchQuery = "";
                btnClearSearch.setVisibility(View.GONE);
                applyFilterAndSearch();
            });
        }
    }

    private void openLocationFilterSheet() {
        if (locationManager == null) return;

        LocationSearchBottomSheet.show(this, locationManager,
                new LocationSearchBottomSheet.PermissionDelegate() {
                    @Override
                    public boolean hasLocationPermission() {
                        return ContextCompat.checkSelfPermission(ProvidersListActivity.this,
                                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                || ContextCompat.checkSelfPermission(ProvidersListActivity.this,
                                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public void requestLocationPermission() {
                        ActivityCompat.requestPermissions(
                                ProvidersListActivity.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                LOCATION_PERMISSION_REQ
                        );
                    }
                }, suggestion -> {
                    cityFilter = suggestion == null ? "" : safeText(suggestion.city);
                    if (suggestion != null && suggestion.nearby && !Double.isNaN(suggestion.lat) && !Double.isNaN(suggestion.lng)) {
                        selectedLat = suggestion.lat;
                        selectedLng = suggestion.lng;
                    } else if (cityFilter.isEmpty()) {
                        selectedLat = Double.NaN;
                        selectedLng = Double.NaN;
                    }
                    updateHeader();
                    applyFilterAndSearch();
                });
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void updateHeader() {
        String title = category.isEmpty() ? "Service Providers" : category + " Providers";
        if (tvCategoryTitle != null) tvCategoryTitle.setText(title);
        if (tvResultSubtitle != null) {
            String cityLabel = cityFilter.isEmpty() ? "All cities" : cityFilter;
            tvResultSubtitle.setText("City: " + cityLabel);
        }
    }

    // ── Load providers from Supabase ─────────────────────────────────────
    private void loadProviders() {
        showLoading(true);
        allProviders.clear();
        latestFilteredProviders.clear();
        visibleProvidersCount = PROVIDERS_PAGE_SIZE;

        new Thread(() -> {
            try {
                // Strategy: fetch approved/active profiles first; fallback to broad query if needed.
                List<ProviderProfile> list = fetchProviders(true);

                if (list == null) {
                    // HTTP error — try without any status filter
                    list = fetchProviders(false);
                }

                if (list == null) {
                    // Still failed — show error state
                    MAIN.post(() -> { showLoading(false); showError(); });
                    return;
                }

                final List<ProviderProfile> finalList = list;
                allProviders = new ArrayList<>(finalList);

                // Enrich provider images from users table
                enrichProviderImagesFromUsers(allProviders);

                MAIN.post(() -> {
                    showLoading(false);
                    applyFilterAndSearch();
                });

            } catch (Exception e) {
                Log.e(TAG, "loadProviders exception: " + e.getMessage(), e);
                MAIN.post(() -> { showLoading(false); showError(); });
            }
        }).start();
    }

    /**
     * Fetch providers from Supabase.
     * @param withStatusFilter  true = filter status=eq.active; false = no status filter (fallback)
     * @return list of providers or null on HTTP error
     */
    private List<ProviderProfile> fetchProviders(boolean withStatusFilter) {
        try {
            StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/provider_profiles?select=*");

            if (withStatusFilter) {
                url.append("&or=(is_approved.eq.true,status.eq.approved,status.eq.active)");
            }

            url.append("&order=created_at.desc");

            Log.d(TAG, "fetchProviders URL: " + url);

            Request req = new Request.Builder()
                    .url(url.toString())
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (!r.isSuccessful()) {
                    Log.w(TAG, "fetchProviders HTTP " + r.code() + " body=" + body);
                    return null;
                }

                JSONArray arr = new JSONArray(body);
                List<ProviderProfile> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    ProviderProfile p = parseProvider(arr.getJSONObject(i));
                    if (p == null) continue;
                    if (!isProviderEligible(p)) continue;
                    if (!matchesCategory(category, p.category)) continue;
                    list.add(p);
                }
                return list;
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchProviders error: " + e.getMessage());
            return null;
        }
    }

    private boolean isProviderEligible(ProviderProfile p) {
        if (p == null || p.status == null) return false;
        String status = p.status.trim().toLowerCase(Locale.getDefault());
        return "approved".equals(status) || "active".equals(status);
    }

    private boolean matchesCategory(String selectedCategory, String providerCategory) {
        return CategoryMatcher.matches(selectedCategory, providerCategory);
    }

    private boolean matchesCity(String selectedCity, String providerCity) {
        String selected = safeText(selectedCity).toLowerCase(Locale.getDefault());
        if (selected.isEmpty()) return true;

        String provider = safeText(providerCity).toLowerCase(Locale.getDefault());
        if (provider.isEmpty()) return false;

        // Keep location filtering tolerant for values like "Bangalore Urban" vs "Bangalore".
        return provider.equals(selected) || provider.contains(selected) || selected.contains(provider);
    }

    private ProviderProfile parseProvider(JSONObject o) {
        try {
            ProviderProfile p = new ProviderProfile();
            p.id               = o.optString("id");
            p.serviceId        = o.optString("service_id");
            p.firebaseUid      = o.optString("firebase_uid");
            p.businessName     = o.optString("business_name");
            p.ownerName        = o.optString("owner_name");
            p.category         = o.optString("category");
            p.city             = o.optString("city");
            p.address          = o.optString("address");
            p.phone            = o.optString("phone");
            p.email            = o.optString("email");
            p.description      = o.optString("description");
            p.experienceYrs    = o.optString("experience_yrs");
            p.businessImageUrl = o.optString("business_image_url");
            p.profileImageUrl = o.optString("profile_image");
            if ((p.profileImageUrl == null || p.profileImageUrl.trim().isEmpty())
                    && p.businessImageUrl != null && !p.businessImageUrl.trim().isEmpty()) {
                p.profileImageUrl = p.businessImageUrl;
            }

            String status      = o.optString("status", "");
            if (status.isEmpty() && o.optBoolean("is_approved", false)) {
                status = "approved";
            }
            p.status = status;

            // rating (optional column)
            p.rating = (float) o.optDouble("rating", 0.0);
            p.reviewCount = o.optInt("review_count", 0);

            // Support either lat/lng or latitude/longitude columns.
            p.lat = optDoubleFlexible(o, "lat");
            if (Double.isNaN(p.lat)) p.lat = optDoubleFlexible(o, "latitude");
            p.lng = optDoubleFlexible(o, "lng");
            if (Double.isNaN(p.lng)) p.lng = optDoubleFlexible(o, "longitude");
             p.isVerified       = o.optBoolean("is_verified", o.optBoolean("verified", false));
            return p;
        } catch (Exception e) {
            Log.e(TAG, "parseProvider: " + e.getMessage());
            return null;
        }
    }

    private double optDoubleFlexible(JSONObject obj, String key) {
        try {
            if (!obj.has(key) || obj.isNull(key)) return Double.NaN;
            Object raw = obj.get(key);
            if (raw instanceof Number) return ((Number) raw).doubleValue();
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private void enrichProviderImagesFromUsers(List<ProviderProfile> providers) {
        if (providers == null || providers.isEmpty()) return;

        try {
            Set<String> uidSet = new LinkedHashSet<>();
            for (ProviderProfile p : providers) {
                if (p == null || p.firebaseUid == null) continue;
                String uid = p.firebaseUid.trim();
                if (!uid.isEmpty()) uidSet.add(uid);
            }
            if (uidSet.isEmpty()) return;

            StringBuilder in = new StringBuilder();
            for (String uid : uidSet) {
                if (in.length() > 0) in.append(",");
                in.append(URLEncoder.encode(uid, StandardCharsets.UTF_8.name()));
            }

            String url = BuildConfig.SUPABASE_URL + "/rest/v1/users"
                    + "?select=firebase_uid,profile_image"
                    + "&firebase_uid=in.(" + in + ")";

            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (!r.isSuccessful()) {
                    Log.w(TAG, "Image enrichment HTTP " + r.code());
                    return;
                }

                JSONArray arr = new JSONArray(body);
                Map<String, String> imageByUid = new HashMap<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject u = arr.getJSONObject(i);
                    String uid = u.optString("firebase_uid", "").trim();
                    String image = u.optString("profile_image", "").trim();
                    if (!uid.isEmpty() && !image.isEmpty()) {
                        imageByUid.put(uid, SupabaseStorageHelper.getPublicObjectUrl(
                                SupabaseStorageHelper.BUCKET_PROFILE_IMAGES, image));
                    }
                }

                for (ProviderProfile p : providers) {
                    if (p == null || p.firebaseUid == null) continue;
                    String image = imageByUid.get(p.firebaseUid.trim());
                    if (image != null && !image.isEmpty()) {
                        p.profileImageUrl = image;
                        p.businessImageUrl = image;
                    } else {
                        String providerProfileImage = p.profileImageUrl == null ? "" : p.profileImageUrl.trim();
                        if (!providerProfileImage.isEmpty()) {
                            String resolved = providerProfileImage.startsWith("http")
                                    ? providerProfileImage
                                    : SupabaseStorageHelper.getPublicObjectUrl(SupabaseStorageHelper.BUCKET_PROFILE_IMAGES, providerProfileImage);
                            p.profileImageUrl = resolved;
                            p.businessImageUrl = resolved;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to enrich provider images: " + e.getMessage());
        }
    }

    // ── Filter + Search ───────────────────────────────────────────────────
    private void applyFilterAndSearch() {
        String q = normalizeSearchQuery(searchQuery);
        Log.d("SEARCH_QUERY", q);

        if (q.contains("nearby")) {
            applyNearbyFilterFromGps(q.replace("nearby", "").replace("near me", "").trim());
            return;
        }

        List<ProviderProfile> filtered = new ArrayList<>();
        boolean filterByDistance = !Double.isNaN(selectedLat) && !Double.isNaN(selectedLng);
        for (ProviderProfile p : allProviders) {
            if (filterByDistance) {
                if (Double.isNaN(p.lat) || Double.isNaN(p.lng)) continue;
                float[] distance = new float[1];
                Location.distanceBetween(selectedLat, selectedLng, p.lat, p.lng, distance);
                if (distance[0] > NEARBY_RADIUS_SECONDARY_METERS) continue;
            }
            if (!matchesCity(cityFilter, p.city)) continue;

            boolean passChip = true;
            switch (currentChip) {
                case "Verified":
                    passChip = p.isVerified;
                    break;
                case "TopRated":
                    passChip = p.rating >= 4.0f;
                    break;
                default:
                    break;
            }
            if (!passChip) continue;

            if (!q.isEmpty()) {
                boolean matchBusiness = p.businessName != null
                        && p.businessName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCategory = p.category != null
                        && p.category.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchOwner = p.ownerName != null
                        && p.ownerName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCity = p.city != null
                        && p.city.toLowerCase(Locale.getDefault()).contains(q);
                if (!(matchBusiness || matchCategory || matchOwner || matchCity)) continue;
            }
            filtered.add(p);
        }

        if ("TopRated".equals(currentChip)) {
            filtered.sort((a, b) -> Float.compare(b.rating, a.rating));
        }

        visibleProvidersCount = PROVIDERS_PAGE_SIZE;
        renderProviderList(filtered, true);
    }

    private String normalizeSearchQuery(String input) {
        String query = input == null ? "" : input.toLowerCase(Locale.US).trim();
        query = query.replaceAll("\\s+", " ");
        if (query.contains("repairing")) query = query.replace("repairing", "repair");
        if (query.contains("clean")) query = query.replace("clean", "cleaning");
        if (query.matches(".*\\bac\\b.*") && !query.contains("ac service")) {
            query = query.replaceAll("\\bac\\b", "ac service");
        }
        return query;
    }

    private void applyNearbyFilterFromGps(String cleanedQuery) {
        if (locationManager == null) {
            renderProviderList(new ArrayList<>(), true);
            return;
        }

        locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
            @Override
            public void onSuccess(String city, String area, double lat, double lng) {
                List<ProviderProfile> within20Km = filterByDistanceAndQuery(cleanedQuery, lat, lng, NEARBY_RADIUS_PRIMARY_METERS);
                if (!within20Km.isEmpty()) {
                    runOnUiThread(() -> {
                        visibleProvidersCount = PROVIDERS_PAGE_SIZE;
                        renderProviderList(within20Km, true);
                    });
                    return;
                }

                List<ProviderProfile> within25Km = filterByDistanceAndQuery(cleanedQuery, lat, lng, NEARBY_RADIUS_SECONDARY_METERS);
                if (!within25Km.isEmpty()) {
                    runOnUiThread(() -> {
                        visibleProvidersCount = PROVIDERS_PAGE_SIZE;
                        renderProviderList(within25Km, true);
                    });
                    return;
                }

                // Final fallback: use city/category results instead of empty nearby list.
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersListActivity.this,
                            "No providers in 25km. Showing city results.",
                            Toast.LENGTH_SHORT).show();
                    searchQuery = cleanedQuery;
                    applyFilterAndSearch();
                });
            }

            @Override
            public void onFailure(String reason) {
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersListActivity.this,
                            "Unable to get current location. Showing city results.",
                            Toast.LENGTH_SHORT).show();
                    searchQuery = cleanedQuery;
                    applyFilterAndSearch();
                });
            }
        });
    }

    private List<ProviderProfile> filterByDistanceAndQuery(String cleanedQuery, double lat, double lng, float radiusMeters) {
        List<ProviderProfile> filtered = new ArrayList<>();
        String q = cleanedQuery == null ? "" : cleanedQuery.toLowerCase(Locale.getDefault()).trim();

        for (ProviderProfile p : allProviders) {
            if (Double.isNaN(p.lat) || Double.isNaN(p.lng)) continue;
            float[] result = new float[1];
            Location.distanceBetween(lat, lng, p.lat, p.lng, result);
            if (result[0] > radiusMeters) continue;

            if (!q.isEmpty()) {
                boolean matchBusiness = p.businessName != null
                        && p.businessName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCategory = p.category != null
                        && p.category.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchOwner = p.ownerName != null
                        && p.ownerName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCity = p.city != null
                        && p.city.toLowerCase(Locale.getDefault()).contains(q);
                if (!(matchBusiness || matchCategory || matchOwner || matchCity)) continue;
            }
            filtered.add(p);
        }
        return filtered;
    }

    private void renderProviderList(List<ProviderProfile> filtered, boolean resetPagingWindow) {
        latestFilteredProviders = filtered == null ? new ArrayList<>() : new ArrayList<>(filtered);

        if (resetPagingWindow) {
            visibleProvidersCount = PROVIDERS_PAGE_SIZE;
        }

        int total = latestFilteredProviders.size();
        int end = Math.min(total, Math.max(PROVIDERS_PAGE_SIZE, visibleProvidersCount));
        List<ProviderProfile> visible = latestFilteredProviders.subList(0, end);

        Log.d("RESULT_SIZE", String.valueOf(total));
        adapter.setProviders(visible);

        if (total == 0) {
            rvProviders.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            tvEmptySubtitle.setText("No providers found for selected category/city.");
            if (btnLoadMoreProviders != null) btnLoadMoreProviders.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
            rvProviders.setVisibility(View.VISIBLE);
            if (btnLoadMoreProviders != null) {
                btnLoadMoreProviders.setVisibility(end < total ? View.VISIBLE : View.GONE);
                btnLoadMoreProviders.setText("Show more providers");
            }
        }

        String cityLabel = cityFilter == null || cityFilter.trim().isEmpty() ? "All cities" : cityFilter;
        tvResultSubtitle.setText("Showing " + end + " of " + total + " provider"
                + (total != 1 ? "s" : "") + " in " + cityLabel);
    }

    // ── State helpers ─────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        swipeRefresh.setRefreshing(false);
        if (show) {
            loadingState.setVisibility(View.VISIBLE);
            rvProviders.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
        } else {
            loadingState.setVisibility(View.GONE);
        }
    }

    private void showError() {
        loadingState.setVisibility(View.GONE);
        rvProviders.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
    }

    // ── Provider action callbacks ─────────────────────────────────────────
    @Override
    public void onViewProfile(ProviderProfile provider) {
        // Open provider detail screen (pass data as extras)
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID,        provider.id);
        intent.putExtra("service_id",                                    provider.serviceId);
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID,       provider.firebaseUid);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME,      provider.businessName);
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME,         provider.ownerName);
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY,           provider.category);
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY,               provider.city);
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE,              provider.phone);
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL,              provider.email);
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION,        provider.description);
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE,         provider.experienceYrs);
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL,          provider.businessImageUrl);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED,        provider.isVerified);
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING,             provider.rating);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }
}
