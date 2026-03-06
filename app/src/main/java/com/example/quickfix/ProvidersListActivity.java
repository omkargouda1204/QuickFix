package com.example.quickfix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ProvidersAdapter;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProvidersListActivity — shows approved providers filtered by category + city.
 * Launched from home screen category icons or CategoriesActivity.
 */
public class ProvidersListActivity extends AppCompatActivity
        implements ProvidersAdapter.OnProviderActionListener {

    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_CITY     = "city";

    private static final String TAG = "ProvidersListActivity";

    // Views
    private TextView         tvCategoryTitle, tvResultSubtitle, tvLocationLabel, tvEmptySubtitle;
    private ImageView        btnBack;
    private RecyclerView     rvProviders;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout     loadingState, emptyState, errorState;
    private ChipGroup        chipGroupSort;

    // Data
    private ProvidersAdapter         adapter;
    private List<ProviderProfile>    allProviders  = new ArrayList<>();
    private String                   category      = "";
    private String                   cityFilter    = "";
    private String                   currentFilter = "All";

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ═══════════════════════════════════════════════════════════════════
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
        rvProviders      = findViewById(R.id.rvProviders);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        loadingState     = findViewById(R.id.loadingState);
        emptyState       = findViewById(R.id.emptyState);
        errorState       = findViewById(R.id.errorState);
        chipGroupSort    = findViewById(R.id.chipGroupSort);

        btnBack.setOnClickListener(v -> finish());

        if (findViewById(R.id.btnRetry) != null)
            findViewById(R.id.btnRetry).setOnClickListener(v -> loadProviders());
        if (findViewById(R.id.btnRetryError) != null)
            findViewById(R.id.btnRetryError).setOnClickListener(v -> loadProviders());
    }

    private void setupRecycler() {
        adapter = new ProvidersAdapter(this, this);
        rvProviders.setLayoutManager(new LinearLayoutManager(this));
        rvProviders.setAdapter(adapter);
    }

    private void setupChips() {
        chipGroupSort.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chipAll)      currentFilter = "All";
            else if (id == R.id.chipVerified) currentFilter = "Verified";
            else if (id == R.id.chipTopRated) currentFilter = "TopRated";
            applyFilter();
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadProviders);
    }

    private void updateHeader() {
        String title = category.isEmpty() ? "Service Providers" : category + " Providers";
        tvCategoryTitle.setText(title);
        tvLocationLabel.setText(cityFilter.isEmpty() ? "All locations" : "📍 " + cityFilter);
    }

    // ── Load providers from Supabase ─────────────────────────────────────
    private void loadProviders() {
        showLoading(true);
        allProviders.clear();

        new Thread(() -> {
            try {
                // ── Query provider_profiles (approved/active providers only) ──
                StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                        .append("/rest/v1/provider_profiles")
                        .append("?is_approved=eq.true")
                        .append("&is_active=eq.true");

                // Filter by category
                if (!category.isEmpty()) {
                    url.append("&category=ilike.*")
                       .append(java.net.URLEncoder.encode(category, "UTF-8"))
                       .append("*");
                }

                // Filter by city
                if (!cityFilter.isEmpty()) {
                    url.append("&city=ilike.*")
                       .append(java.net.URLEncoder.encode(cityFilter, "UTF-8"))
                       .append("*");
                }

                url.append("&select=id,firebase_uid,business_name,owner_name,category,")
                   .append("city,address,phone,email,description,experience,")
                   .append("business_image_url,id_proof_url,is_approved,is_active")
                   .append("&order=approved_at.desc")
                   .append("&limit=50");

                Request req = new Request.Builder()
                        .url(url.toString())
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "HTTP " + r.code() + " body len=" + body.length());

                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        List<ProviderProfile> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            ProviderProfile p = parseProvider(arr.getJSONObject(i));
                            if (p != null) list.add(p);
                        }
                        allProviders = list;
                        MAIN.post(() -> {
                            showLoading(false);
                            applyFilter();
                            tvResultSubtitle.setText(list.size() + " provider" +
                                    (list.size() != 1 ? "s" : "") + " found");
                        });
                    } else {
                        Log.e(TAG, "Error " + r.code() + ": " + body);
                        MAIN.post(() -> { showLoading(false); showError(); });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProviders: " + e.getMessage());
                MAIN.post(() -> { showLoading(false); showError(); });
            }
        }).start();
    }

    private ProviderProfile parseProvider(JSONObject o) {
        try {
            ProviderProfile p = new ProviderProfile();
            p.id               = o.optString("id", "");
            p.firebaseUid      = o.optString("firebase_uid", "");
            p.businessName     = o.optString("business_name", "");
            p.ownerName        = o.optString("owner_name", "");
            p.category         = o.optString("category", "");
            p.city             = o.optString("city", "");
            p.address          = o.optString("address", "");
            p.phone            = o.optString("phone", "");
            p.email            = o.optString("email", "");
            p.description      = o.optString("description", "");
            // 'experience' column in provider_profiles (copied from provider_applications)
            p.experienceYrs    = o.optString("experience", "");
            p.businessImageUrl = o.optString("business_image_url", "");
            p.idProofUrl       = o.optString("id_proof_url", "");
            p.isVerified       = o.optBoolean("is_approved", false);
            p.status           = o.optBoolean("is_active", false) ? "active" : "inactive";
            // rating not yet in table — default 0
            p.rating           = (float) o.optDouble("rating", 0.0);
            return p;
        } catch (Exception e) {
            Log.e(TAG, "parseProvider: " + e.getMessage());
            return null;
        }
    }

    // ── Filter ────────────────────────────────────────────────────────────
    private void applyFilter() {
        List<ProviderProfile> filtered = new ArrayList<>();
        for (ProviderProfile p : allProviders) {
            switch (currentFilter) {
                case "Verified":
                    if (p.isVerified) filtered.add(p);
                    break;
                case "TopRated":
                    if (p.rating >= 4.0f) filtered.add(p);
                    break;
                default:
                    filtered.add(p);
            }
        }
        adapter.setProviders(filtered);

        if (filtered.isEmpty()) {
            rvProviders.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            // Helpful empty message
            if (cityFilter.isEmpty()) {
                tvEmptySubtitle.setText("No " + (category.isEmpty() ? "" : category + " ")
                        + "providers available yet. Check back soon!");
            } else {
                tvEmptySubtitle.setText("No " + (category.isEmpty() ? "" : category + " ")
                        + "providers found in " + cityFilter
                        + ".\nTry clearing the location filter.");
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

