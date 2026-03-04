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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.SearchResultsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * SearchResultsActivity — full-screen search results filtered by query + city.
 * Queries Supabase provider_applications (approved) + services tables.
 */
public class SearchResultsActivity extends AppCompatActivity
        implements SearchResultsAdapter.OnResultClickListener {

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_CITY  = "city";

    private static final String TAG      = "SearchResults";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;

    private EditText    etSearch;
    private ImageView   btnBack, btnClear;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private RecyclerView rvResults;
    private TextView    tvSearchLocation, tvResultCount;
    private ChipGroup   chipGroup;
    private Chip        chipAll, chipServices, chipProviders;

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
        chipGroup        = findViewById(R.id.chipGroupFilter);
        chipAll          = findViewById(R.id.chipAll);
        chipServices     = findViewById(R.id.chipServices);
        chipProviders    = findViewById(R.id.chipProviders);
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
            if      (id == R.id.chipAll)       currentFilter = "All";
            else if (id == R.id.chipServices)  currentFilter = SearchResult.TYPE_SERVICE;
            else if (id == R.id.chipProviders) currentFilter = SearchResult.TYPE_PROVIDER;
            applyFilter();
        });
    }

    private void updateLocationLabel() {
        if (cityFilter.isEmpty()) {
            tvSearchLocation.setText("All locations");
        } else {
            tvSearchLocation.setText("📍 " + cityFilter);
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────

    private void performSearch(String query) {
        queryText = query;
        showLoading(true);
        allResults.clear();
        filtered.clear();

        new Thread(() -> {
            List<SearchResult> results = new ArrayList<>();
            results.addAll(searchProviders(query));
            results.addAll(searchServices(query));
            MAIN.post(() -> {
                allResults = results;
                applyFilter();
                showLoading(false);
            });
        }).start();
    }

    /** Query provider_applications (approved) filtered by business_name / category + city. */
    private List<SearchResult> searchProviders(String query) {
        List<SearchResult> list = new ArrayList<>();
        if (query.isEmpty()) return list;
        try {
            String enc = java.net.URLEncoder.encode(query, "UTF-8");
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/rest/v1/provider_applications")
                    .append("?status=eq.approved")
                    .append("&or=(business_name.ilike.*").append(enc).append("*,category.ilike.*").append(enc).append("*,owner_name.ilike.*").append(enc).append("*)")
                    .append("&select=id,business_name,category,city,owner_name")
                    .append("&limit=30");

            if (!cityFilter.isEmpty()) {
                url.append("&city=ilike.*")
                   .append(java.net.URLEncoder.encode(cityFilter, "UTF-8")).append("*");
            }

            Request req = new Request.Builder().url(url.toString())
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new SearchResult(
                            o.optString("id"),
                            o.optString("business_name", "—"),
                            o.optString("category", "") + " • " + o.optString("owner_name", ""),
                            o.optString("city", ""),
                            SearchResult.TYPE_PROVIDER
                        ));
                    }
                }
            }
        } catch (Exception e) { Log.e(TAG, "searchProviders: " + e.getMessage()); }
        return list;
    }

    /** Query services table if it exists (gracefully skipped if 404). */
    private List<SearchResult> searchServices(String query) {
        List<SearchResult> list = new ArrayList<>();
        if (query.isEmpty()) return list;
        try {
            String enc = java.net.URLEncoder.encode(query, "UTF-8");
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/rest/v1/services")
                    .append("?or=(name.ilike.*").append(enc).append("*,category.ilike.*").append(enc).append("*)")
                    .append("&select=id,name,category,city")
                    .append("&limit=30");

            if (!cityFilter.isEmpty()) {
                url.append("&city=ilike.*")
                   .append(java.net.URLEncoder.encode(cityFilter, "UTF-8")).append("*");
            }

            Request req = new Request.Builder().url(url.toString())
                    .addHeader("apikey",        ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                if (r.code() == 404 || r.code() == 400) return list; // table doesn't exist yet
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        list.add(new SearchResult(
                            o.optString("id"),
                            o.optString("name", "—"),
                            o.optString("category", ""),
                            o.optString("city", ""),
                            SearchResult.TYPE_SERVICE
                        ));
                    }
                }
            }
        } catch (IOException e) { Log.e(TAG, "searchServices: " + e.getMessage()); }
        catch (Exception ignored) {}
        return list;
    }

    private void applyFilter() {
        filtered.clear();
        for (SearchResult r : allResults) {
            if ("All".equals(currentFilter) || currentFilter.equals(r.type)) {
                filtered.add(r);
            }
        }
        adapter.setResults(filtered);

        int count = filtered.size();
        tvResultCount.setText(count + " result" + (count != 1 ? "s" : ""));

        boolean isEmpty = filtered.isEmpty() && !queryText.isEmpty();
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
        Toast.makeText(this, result.name + " — details coming soon", Toast.LENGTH_SHORT).show();
    }
}

