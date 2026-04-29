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
 * 
 * Logic:
 *  - Use user GPS as center point.
 *  - Fetch providers within 25km radius using lat/lng.
 *  - Fallback to area/city matching if location unavailable.
 */
public class ProvidersListActivity extends BaseActivity
        implements ProvidersAdapter.OnProviderActionListener {

    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_CITY = "city";
    public static final String EXTRA_QUERY = "query";

    private static final String TAG = "ProvidersListActivity";
    private static final int LOCATION_PERMISSION_REQ = 4301;
    private static final float NEARBY_RADIUS_KM = 25.0f;
    private static final int   PROVIDERS_PAGE_SIZE = 10;

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
    private String                areaFilter    = "";
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
        
        cityFilter = getIntent().getStringExtra(EXTRA_CITY);
        if (cityFilter == null || cityFilter.trim().isEmpty()) {
            cityFilter = locationManager.getSavedCity();
        }
        
        areaFilter = locationManager.getSavedArea();
        selectedLat = locationManager.getSavedLat();
        selectedLng = locationManager.getSavedLng();

        searchQuery = getIntent().getStringExtra(EXTRA_QUERY);
        if (searchQuery == null) searchQuery = "";

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

        if (btnSearchFilter != null) {
            btnSearchFilter.setOnClickListener(v -> openLocationFilterSheet());
        }

        View btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) btnRetry.setOnClickListener(v -> loadProviders());
        
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
                applyFilterAndSearch();
            });
        }
    }

    private void openLocationFilterSheet() {
        LocationSearchBottomSheet.show(this, locationManager,
                new LocationSearchBottomSheet.PermissionDelegate() {
                    @Override public boolean hasLocationPermission() {
                        return ContextCompat.checkSelfPermission(ProvidersListActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    }
                    @Override public void requestLocationPermission() {
                        ActivityCompat.requestPermissions(ProvidersListActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQ);
                    }
                }, suggestion -> {
                    if (suggestion != null) {
                        cityFilter = safeText(suggestion.city);
                        areaFilter = safeText(suggestion.area);
                        if (suggestion.nearby) {
                            selectedLat = suggestion.lat;
                            selectedLng = suggestion.lng;
                        } else {
                            selectedLat = Double.NaN;
                            selectedLng = Double.NaN;
                        }
                    } else {
                        cityFilter = "";
                        areaFilter = "";
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
            String label = AppLocationManager.composeLocationLabel(cityFilter, areaFilter);
            tvResultSubtitle.setText("Location: " + (label.isEmpty() ? "All" : label));
        }
    }

    private void loadProviders() {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?select=*&status=in.(approved,active)&order=created_at.desc";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.isSuccessful()) {
                        JSONArray arr = new JSONArray(body);
                        List<ProviderProfile> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            ProviderProfile p = parseProvider(arr.getJSONObject(i));
                            if (p != null) list.add(p);
                        }
                        allProviders = list;
                        MAIN.post(() -> {
                            showLoading(false);
                            applyFilterAndSearch();
                        });
                    } else {
                        MAIN.post(() -> { showLoading(false); showError(); });
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> { showLoading(false); showError(); });
            }
        }).start();
    }

    private ProviderProfile parseProvider(JSONObject o) {
        try {
            ProviderProfile p = new ProviderProfile();
            p.id = o.optString("id");
            p.firebaseUid = o.optString("firebase_uid");
            p.businessName = o.optString("business_name");
            p.ownerName = o.optString("owner_name");
            p.category = o.optString("category");
            p.city = o.optString("city");
            p.area = o.optString("area");
            p.phone = o.optString("phone");
            p.email = o.optString("email");
            p.description = o.optString("description");
            p.experienceYrs = o.optString("experience_yrs");
            p.businessImageUrl = o.optString("business_image_url");
            p.status = o.optString("status");
            p.latitude = o.optDouble("latitude", o.optDouble("lat", Double.NaN));
            p.longitude = o.optDouble("longitude", o.optDouble("lng", Double.NaN));
            p.rating = (float) o.optDouble("rating", 0.0);
            p.isVerified = o.optBoolean("is_verified", false);
            return p;
        } catch (Exception e) { return null; }
    }

    private void applyFilterAndSearch() {
        List<ProviderProfile> filtered = new ArrayList<>();
        String query = searchQuery.toLowerCase(Locale.US);

        for (ProviderProfile p : allProviders) {
            if (!CategoryMatcher.matches(category, p.category)) continue;

            if ("Verified".equals(currentChip) && !p.isVerified) continue;
            
            boolean matchesSearch = query.isEmpty() 
                    || (p.businessName != null && p.businessName.toLowerCase(Locale.US).contains(query))
                    || (p.category != null && p.category.toLowerCase(Locale.US).contains(query));
            if (!matchesSearch) continue;

            // Location Filtering
            if (!Double.isNaN(selectedLat) && !Double.isNaN(selectedLng)) {
                // GPS radius check (25km)
                double dist = haversineDistance(selectedLat, selectedLng, p.latitude, p.longitude);
                if (Double.isNaN(dist) || dist > NEARBY_RADIUS_KM) {
                    // Fallback to area matching within GPS mode
                    if (!matchesAreaOrCity(p)) continue;
                }
            } else if (!cityFilter.isEmpty() || !areaFilter.isEmpty()) {
                // Manual/Saved location mode - Area matching
                if (!matchesAreaOrCity(p)) continue;
            }

            filtered.add(p);
        }

        if ("TopRated".equals(currentChip)) {
            filtered.sort((a, b) -> Float.compare(b.rating, a.rating));
        }

        latestFilteredProviders = filtered;
        renderProviderList(filtered, true);
    }

    private boolean matchesAreaOrCity(ProviderProfile p) {
        String pArea = safeText(p.area).toLowerCase(Locale.US);
        String pCity = safeText(p.city).toLowerCase(Locale.US);
        String fArea = areaFilter.toLowerCase(Locale.US);
        String fCity = cityFilter.toLowerCase(Locale.US);

        if (!fArea.isEmpty() && (pArea.contains(fArea) || fArea.contains(pArea))) return true;
        if (!fCity.isEmpty() && (pCity.contains(fCity) || fCity.contains(pCity))) return true;
        return fArea.isEmpty() && fCity.isEmpty();
    }

    private double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        if (Double.isNaN(lat1) || Double.isNaN(lat2)) return Double.NaN;
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void renderProviderList(List<ProviderProfile> filtered, boolean reset) {
        if (reset) visibleProvidersCount = PROVIDERS_PAGE_SIZE;
        int end = Math.min(filtered.size(), visibleProvidersCount);
        adapter.setProviders(filtered.subList(0, end));

        if (filtered.isEmpty()) {
            rvProviders.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvProviders.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
        
        if (btnLoadMoreProviders != null) {
            btnLoadMoreProviders.setVisibility(end < filtered.size() ? View.VISIBLE : View.GONE);
        }
    }

    private void showLoading(boolean show) {
        loadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            rvProviders.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
        }
        swipeRefresh.setRefreshing(false);
    }

    private void showError() {
        errorState.setVisibility(View.VISIBLE);
        loadingState.setVisibility(View.GONE);
    }

    @Override
    public void onViewProfile(ProviderProfile provider) {
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, provider.id);
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID, provider.firebaseUid);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, provider.businessName);
        startActivity(intent);
    }
}
