package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.Manifest;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.SearchResultsAdapter;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * SearchResultsActivity — full-screen provider search using a simple category query.
 * Queries Supabase provider_profiles only.
 */
public class SearchResultsActivity extends BaseActivity
        implements SearchResultsAdapter.OnResultClickListener {

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_CITY  = "city";
    public static final String EXTRA_LAT   = "lat";
    public static final String EXTRA_LNG   = "lng";

    private static final String TAG      = "SearchResults";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
    private static final int LOCATION_PERMISSION_REQ = 1014;
    private static final float NEARBY_RADIUS_PRIMARY_KM = 20f;
    private static final float NEARBY_RADIUS_SECONDARY_KM = 25f;

    // ...existing fields...
    private AppLocationManager locationManager;
    private double userLat = Double.NaN;
    private double userLng = Double.NaN;

    private EditText    etSearch;
    private ImageView   btnBack, btnClear;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private RecyclerView rvResults;
    private TextView    tvSearchLocation, tvResultCount, tvEmptyTitle, tvEmptySubtitle;
    private ChipGroup   chipGroup;

    private SearchResultsAdapter adapter;
    private List<SearchResult> allResults    = new ArrayList<>();
    private List<SearchResult> filtered      = new ArrayList<>();
    private String currentFilter = "All";

    private String queryText = "";
    private String cityFilter = "";

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // Debounce search
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);

        queryText  = getIntent().getStringExtra(EXTRA_QUERY);
        cityFilter = getIntent().getStringExtra(EXTRA_CITY);
        if (queryText  == null) queryText  = "";
        if (cityFilter == null) cityFilter = "";

        locationManager = new AppLocationManager(this);
        userLat = getIntent().getDoubleExtra(EXTRA_LAT, locationManager.getSavedLat());
        userLng = getIntent().getDoubleExtra(EXTRA_LNG, locationManager.getSavedLng());

        bindViews();
        setupRecycler();
        setupListeners();

        // Pre-fill search bar
        etSearch.setText(queryText);
        etSearch.setSelection(queryText.length());

        updateLocationLabel();
        performSearch(queryText);
    }

    private void bindViews() {
        btnBack          = findViewById(R.id.btnBack);
        etSearch         = findViewById(R.id.etSearchResults);
        btnClear         = findViewById(R.id.btnClearResultSearch);
        progressBar      = findViewById(R.id.progressBar);
        emptyState       = findViewById(R.id.emptyState);
        rvResults        = findViewById(R.id.rvResults);
        tvSearchLocation = findViewById(R.id.tvSearchLocation);
        tvResultCount    = findViewById(R.id.tvResultCount);
        tvEmptyTitle     = findViewById(R.id.tvEmptyTitle);
        tvEmptySubtitle  = findViewById(R.id.tvEmptySubtitle);
        chipGroup        = findViewById(R.id.chipGroupFilter);
    }

    private void setupRecycler() {
        adapter = new SearchResultsAdapter(this, this);
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnClear.setOnClickListener(v -> {
            etSearch.setText("");
            btnClear.setVisibility(View.GONE);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (debounceRunnable != null) debounce.removeCallbacks(debounceRunnable);
                debounceRunnable = () -> performSearch(s.toString().trim());
                debounce.postDelayed(debounceRunnable, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch(etSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipProviders) {
                currentFilter = SearchResult.TYPE_PROVIDER;
            } else {
                currentFilter = "All";
            }
            applyFilter();
        });
    }

    private void updateLocationLabel() {
        String base = cityFilter.isEmpty() ? "All locations" : "📍 " + cityFilter;
        if (!Double.isNaN(userLat) && !Double.isNaN(userLng)) {
            tvSearchLocation.setText(base + " • " + (int) NEARBY_RADIUS_PRIMARY_KM + "km radius");
        } else {
            tvSearchLocation.setText(base);
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────

    private void performSearch(String query) {
        queryText = normalizeSearchQuery(query);
        Log.d("SEARCH_QUERY", queryText);
        Log.d("SEARCH_INPUT", queryText);
        showLoading(true);
        allResults.clear();
        filtered.clear();

        if (queryText.isEmpty()) {
            showLoading(false);
            applyFilter();
            return;
        }

        executeSearch(queryText, false);
    }

    private void ensureLocationThenSearch(String query) {
        if (!Double.isNaN(userLat) && !Double.isNaN(userLng)) {
            executeSearch(query, true);
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
                @Override
                public void onSuccess(String city, String area, double lat, double lng) {
                    userLat = lat;
                    userLng = lng;
                    executeSearch(query, true);
                }

                @Override
                public void onFailure(String reason) {
                    Log.d(TAG, "Location unavailable for nearby search: " + reason);
                    executeSearch(query, false);
                }
            });
            return;
        }

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQ);
        executeSearch(query, false);
    }

    private void executeSearch(String query, boolean useNearby) {
        new Thread(() -> {
            List<SearchResult> results = new ArrayList<>();
            results.addAll(searchProviders(query, userLat, userLng, useNearby));
            MAIN.post(() -> {
                allResults = results;
                Log.d("RESULT_SIZE", String.valueOf(results.size()));
                applyFilter();
                showLoading(false);
            });
        }).start();
    }

    private boolean isNearbyQuery(String query) {
        if (query == null) return false;
        String q = query.toLowerCase(Locale.getDefault());
        return q.contains("nearby") || q.contains("near me");
    }

    private String sanitizeProviderQuery(String query) {
        if (query == null) return "";
        String cleaned = normalizeSearchQuery(query)
                .replace("nearby", "")
                .replace("near me", "")
                .trim();
        return cleaned.isEmpty() ? "service" : cleaned;
    }

    private String normalizeSearchQuery(String input) {
        String query = input == null ? "" : input.toLowerCase(Locale.US).trim();
        query = query.replaceAll("\\s+", " ");
        if (query.matches(".*\\bac\\b.*") && !query.contains("ac service")) {
            return "ac service";
        }
        if (query.contains("repairing") || query.contains("repair")) return "repair";
        if (query.contains("clean")) return "cleaning";
        return query;
    }

    private List<SearchResult> searchProviders(String query,
                                               double originLat,
                                               double originLng,
                                               boolean useNearby) {
        List<SearchResult> list = new ArrayList<>();
        if (query.isEmpty()) return list;

        try {
            String cleanedQuery = sanitizeProviderQuery(query);

            String enc = java.net.URLEncoder.encode(cleanedQuery, java.nio.charset.StandardCharsets.UTF_8.name());
            Set<String> seenIds = new HashSet<>();

            list.addAll(fetchTextProviders(enc, seenIds));
        } catch (Exception e) {
            Log.e(TAG, "searchProviders: " + e.getMessage());
        }
        return list;
    }

    private List<SearchResult> fetchNearbyFromProfiles(String encQuery,
                                                        String latKey,
                                                        String lngKey,
                                                        double originLat,
                                                        double originLng,
                                                        float radiusKm,
                                                        Set<String> seenIds) {
        List<SearchResult> nearby = new ArrayList<>();
        try {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/rest/v1/provider_profiles")
                    .append("?select=id,business_name,category,description,profile_image,business_image_url,city,area,owner_name,rating,status,availability_status,is_available,")
                    .append(latKey).append(",").append(lngKey)
                    .append("&is_approved=eq.true")
                    .append("&or=(business_name.ilike.*").append(encQuery)
                    .append("*,category.ilike.*").append(encQuery)
                    .append("*,description.ilike.*").append(encQuery)
                    .append("*,city.ilike.*").append(encQuery).append("*)")
                    .append("&limit=220");

            Request req = new Request.Builder().url(url.toString())
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) return nearby;

                JSONArray arr = new JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String id = o.optString("id", "").trim();
                    if (id.isEmpty() || seenIds.contains(id)) continue;

                    double lat = optDoubleFlexible(o, latKey);
                    double lng = optDoubleFlexible(o, lngKey);
                    if (Double.isNaN(lat) || Double.isNaN(lng)) continue;

                    float distanceKm = (float) haversineKm(originLat, originLng, lat, lng);
                    if (distanceKm > radiusKm) continue;

                    seenIds.add(id);
                    nearby.add(new SearchResult(
                            id,
                            o.optString("business_name", "-"),
                            o.optString("category", ""),
                            composeCityArea(o.optString("city", ""), o.optString("area", "")),
                            SearchResult.TYPE_PROVIDER,
                            (float) o.optDouble("rating", 0d),
                            distanceKm,
                            resolveAvailability(o)
                    ));
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "fetchNearbyFromProfiles(" + latKey + "): " + e.getMessage());
        }
        return nearby;
    }

    private List<SearchResult> fetchTextProviders(String encQuery, Set<String> seenIds) {
        List<SearchResult> list = new ArrayList<>();
        try {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/rest/v1/provider_profiles")
                    .append("?select=id,business_name,category,description,profile_image,business_image_url,city,area,owner_name,rating,status")
                    .append("&is_approved=eq.true")
                    .append("&category=ilike.*").append(encQuery).append("*")
                    .append("&limit=60");

            Request req = new Request.Builder().url(url.toString())
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String id = o.optString("id", "").trim();
                        if (id.isEmpty() || seenIds.contains(id)) continue;
                        seenIds.add(id);
                        list.add(new SearchResult(
                                id,
                                o.optString("business_name", "-"),
                                o.optString("category", ""),
                                composeCityArea(o.optString("city", ""), o.optString("area", "")),
                                SearchResult.TYPE_PROVIDER,
                                (float) o.optDouble("rating", 0d),
                                -1f,
                                resolveAvailability(o)
                        ));
                    }
                    Log.d(TAG, "Results: " + list.size());
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "fetchTextProviders: " + e.getMessage());
        }
        return list;
    }

    // Provider-only results: services lookup intentionally disabled.

    private void applyFilter() {
        filtered.clear();
        for (SearchResult r : allResults) {
            if ("All".equals(currentFilter) || currentFilter.equals(r.type)) {
                filtered.add(r);
            }
        }
        adapter.setResults(filtered);
        Log.d(TAG, "Results: " + filtered.size());

        int count = filtered.size();
        tvResultCount.setText(count + " result" + (count != 1 ? "s" : ""));

        boolean isEmpty = filtered.isEmpty() && !queryText.isEmpty();
        if (isEmpty) {
            tvEmptyTitle.setText("No providers found");
            tvEmptySubtitle.setText("Try another provider name, category, or city.");
        }

        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvResults.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            rvResults.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResultClick(SearchResult result) {
        fetchProviderAndOpenDetail(result.id);
    }

    private void fetchProviderAndOpenDetail(String providerId) {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL + "/rest/v1/provider_profiles"
                        + "?id=eq." + providerId
                        + "&select=*&limit=1";

                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            MAIN.post(() -> {
                                showLoading(false);
                                openProviderDetail(o);
                            });
                            return;
                        }
                    }
                }

                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Provider details not found", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "fetchProviderAndOpenDetail: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error loading provider details", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openProviderDetail(JSONObject o) {
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, o.optString("id"));
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID, o.optString("firebase_uid"));
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, o.optString("business_name"));
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME,
                o.optString("owner_name", o.optString("user_name", "")));
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY, o.optString("category"));
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY, o.optString("city"));
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE, o.optString("phone"));
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL, o.optString("email"));
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION, o.optString("description"));
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE,
                o.optString("experience_yrs", o.optString("experience", "")));
        String profileImage = o.optString("profile_image", "");
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = o.optString("business_image_url", "");
        }
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL, profileImage);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED, o.optBoolean("is_verified", false));
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING, (float) o.optDouble("rating", 0));
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQ) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (granted && !queryText.isEmpty()) {
                performSearch(queryText);
            } else if (!granted) {
                Toast.makeText(this,
                        "Location permission denied. Showing non-distance results.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static String composeCityArea(String city, String area) {
        String c = city == null ? "" : city.trim();
        String a = area == null ? "" : area.trim();
        if (!c.isEmpty() && !a.isEmpty()) return c + ", " + a;
        return c.isEmpty() ? a : c;
    }

    private static String resolveAvailability(JSONObject o) {
        String availability = o.optString("availability_status", "").trim();
        if (!availability.isEmpty()) return availability;

        String status = o.optString("status", "").trim();
        if (!status.isEmpty()) {
            if ("active".equalsIgnoreCase(status)) return "Available";
            return status.substring(0, 1).toUpperCase(Locale.getDefault())
                    + status.substring(1).toLowerCase(Locale.getDefault());
        }

        if (o.has("is_available") && !o.optBoolean("is_available", true)) {
            return "Busy";
        }
        return "Available";
    }

    private static double optDoubleFlexible(JSONObject obj, String key) {
        try {
            if (!obj.has(key) || obj.isNull(key)) return Double.NaN;
            Object raw = obj.get(key);
            if (raw instanceof Number) return ((Number) raw).doubleValue();
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
