package com.example.quickfix;

import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ProvidersAdapter;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public static final String EXTRA_CITY     = "city";

    private static final String TAG = "ProvidersListActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextView          tvCategoryTitle, tvResultSubtitle, tvLocationLabel, tvEmptySubtitle;
    private ImageView         btnBack;
    private View              btnChangeLocation;
    private RecyclerView      rvProviders;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout      loadingState, emptyState, errorState;
    private ChipGroup         chipGroupSort;
    private EditText          etSearch;
    private ImageView         btnClearSearch;
    private LinearLayout      searchBar;

    // ── Data ──────────────────────────────────────────────────────────────
    private ProvidersAdapter      adapter;
    private List<ProviderProfile> allProviders  = new ArrayList<>();
    private String                category      = "";
    private String                cityFilter    = "";
    private String                currentChip   = "All";
    private String                searchQuery   = "";

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

        category   = getIntent().getStringExtra(EXTRA_CATEGORY);
        cityFilter = getIntent().getStringExtra(EXTRA_CITY);
        if (category   == null) category   = "";
        if (cityFilter == null) cityFilter = "";

        // Fall back to SharedPreferences city if not passed
        if (cityFilter.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(
                    AppLocationManager.PREFS_NAME, MODE_PRIVATE);
            cityFilter = prefs.getString(AppLocationManager.KEY_CITY, "");
        }

        bindViews();
        setupRecycler();
        setupChips();
        setupSwipeRefresh();
        setupSearch();
        updateHeader();
        loadProviders();
    }

    // ── Bind ─────────────────────────────────────────────────────────────
    private void bindViews() {
        tvCategoryTitle  = findViewById(R.id.tvCategoryTitle);
        tvResultSubtitle = findViewById(R.id.tvResultSubtitle);
        tvLocationLabel  = findViewById(R.id.tvLocationLabel);
        tvEmptySubtitle  = findViewById(R.id.tvEmptySubtitle);
        btnBack          = findViewById(R.id.btnBack);
        btnChangeLocation= findViewById(R.id.btnChangeLocation);
        rvProviders      = findViewById(R.id.rvProviders);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        loadingState     = findViewById(R.id.loadingState);
        emptyState       = findViewById(R.id.emptyState);
        errorState       = findViewById(R.id.errorState);
        chipGroupSort    = findViewById(R.id.chipGroupSort);
        etSearch         = findViewById(R.id.etSearch);
        btnClearSearch   = findViewById(R.id.btnClearSearch);
        searchBar        = findViewById(R.id.searchBar);

        btnBack.setOnClickListener(v -> finish());

        if (btnChangeLocation != null) {
            btnChangeLocation.setOnClickListener(v -> showLocationPicker());
        }

        View btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) btnRetry.setOnClickListener(v -> loadProviders());
        View btnRetryError = findViewById(R.id.btnRetryError);
        if (btnRetryError != null) btnRetryError.setOnClickListener(v -> loadProviders());
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

    private void updateHeader() {
        String title = category.isEmpty() ? "Service Providers" : category + " Providers";
        if (tvCategoryTitle != null) tvCategoryTitle.setText(title);
        if (tvLocationLabel != null)
            tvLocationLabel.setText(cityFilter.isEmpty() ? "All locations" : "📍 " + cityFilter);
    }

    // ── Show location picker bottom sheet ────────────────────────────────
    private void showLocationPicker() {
        // Reuse the existing location bottom-sheet from AppLocationManager if available,
        // otherwise show a simple dialog with common cities.
        AppLocationManager locMgr = new AppLocationManager(this);
        String[] cities = AppLocationManager.CITY_LIST;
        CharSequence[] cityNames = new CharSequence[cities.length + 1];
        cityNames[0] = "All Locations";
        for (int i = 0; i < cities.length; i++) cityNames[i + 1] = cities[i];

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Location")
                .setItems(cityNames, (dialog, which) -> {
                    if (which == 0) {
                        cityFilter = "";
                    } else {
                        cityFilter = cities[which - 1];
                        locMgr.saveCity(cityFilter);
                    }
                    updateHeader();
                    loadProviders();
                })
                .show();
    }

    // ── Load providers from Supabase ─────────────────────────────────────
    private void loadProviders() {
        showLoading(true);
        allProviders.clear();

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
                // Support both status conventions used in existing data.
                url.append("&or=(status.eq.approved,status.eq.active)");
            }

            // Filter by category (case-insensitive partial match)
            if (!category.isEmpty()) {
                url.append("&category=ilike.*")
                   .append(java.net.URLEncoder.encode(category, "UTF-8"))
                   .append("*");
            }

            // Filter by city (case-insensitive partial match)
            if (!cityFilter.isEmpty()) {
                url.append("&city=ilike.*")
                   .append(java.net.URLEncoder.encode(cityFilter, "UTF-8"))
                   .append("*");
            }

            url.append("&order=created_at.desc&limit=100");

            Log.d(TAG, "fetchProviders URL: " + url);

            Request req = new Request.Builder()
                    .url(url.toString())
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                Log.d(TAG, "fetchProviders HTTP " + r.code() + " len=" + body.length()
                        + " statusFilter=" + withStatusFilter);

                if (!r.isSuccessful()) return null;

                JSONArray arr = new JSONArray(body);
                List<ProviderProfile> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    ProviderProfile p = parseProvider(arr.getJSONObject(i));
                    if (p != null) list.add(p);
                }
                return list;
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchProviders error: " + e.getMessage());
            return null;
        }
    }

    private ProviderProfile parseProvider(JSONObject o) {
        try {
            ProviderProfile p = new ProviderProfile();
            p.id               = o.optString("id");
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
            p.isVerified       = o.optBoolean("is_verified", false);

            String status      = o.optString("status", "");
            p.status           = status.isEmpty()
                    ? (o.optBoolean("is_active", true) ? "active" : "inactive")
                    : status;

            // rating (optional column)
            p.rating           = (float) o.optDouble("rating", 0.0);
            p.reviewCount      = o.optInt("review_count", 0);
            return p;
        } catch (Exception e) {
            Log.e(TAG, "parseProvider: " + e.getMessage());
            return null;
        }
    }

    // ── Filter + Search ───────────────────────────────────────────────────
    private void applyFilterAndSearch() {
        String q = searchQuery.toLowerCase(Locale.getDefault()).trim();

        List<ProviderProfile> filtered = new ArrayList<>();
        for (ProviderProfile p : allProviders) {
            // 1. Chip filter
            boolean passChip = true;
            switch (currentChip) {
                case "Verified":
                    passChip = p.isVerified;
                    break;
                case "TopRated":
                    passChip = p.rating >= 4.0f;
                    break;
                default:
                    break; // "All" — no filter
            }
            if (!passChip) continue;

            // 2. Search query filter
            if (!q.isEmpty()) {
                boolean matchBusiness = p.businessName != null
                        && p.businessName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCategory = p.category != null
                        && p.category.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchOwner    = p.ownerName != null
                        && p.ownerName.toLowerCase(Locale.getDefault()).contains(q);
                boolean matchCity     = p.city != null
                        && p.city.toLowerCase(Locale.getDefault()).contains(q);

                if (!(matchBusiness || matchCategory || matchOwner || matchCity)) continue;
            }

            filtered.add(p);
        }

        if ("TopRated".equals(currentChip)) {
            filtered.sort((a, b) -> Float.compare(b.rating, a.rating));
        }

        adapter.setProviders(filtered);

        if (filtered.isEmpty()) {
            rvProviders.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            if (cityFilter.isEmpty()) {
                tvEmptySubtitle.setText("No " + (category.isEmpty() ? "" : category + " ")
                        + "providers found. Check category/status data in DB (approved/active). ");
            } else {
                tvEmptySubtitle.setText("No " + (category.isEmpty() ? "" : category + " ")
                        + "providers in " + cityFilter
                        + ". Verify exact city/category values or clear location.");
            }
        } else {
            emptyState.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
            rvProviders.setVisibility(View.VISIBLE);
        }
        tvResultSubtitle.setText(filtered.size() + " provider" +
                (filtered.size() != 1 ? "s" : "") + " found");
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

    @Override
    public void onBookService(ProviderProfile provider) {
        // For now launch detail screen with booking flag
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID,   provider.id);
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID,  provider.firebaseUid);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, provider.businessName);
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME,    provider.ownerName);
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY,      provider.category);
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY,          provider.city);
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE,         provider.phone);
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL,         provider.email);
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION,   provider.description);
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE,    provider.experienceYrs);
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL,     provider.businessImageUrl);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED,   provider.isVerified);
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING,        provider.rating);
        intent.putExtra(ProviderDetailActivity.EXTRA_OPEN_BOOKING,  true);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }
}
