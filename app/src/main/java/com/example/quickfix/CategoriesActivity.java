package com.example.quickfix;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.CategoriesAdapter;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * CategoriesActivity — full-page grid of all service categories.
 * Tapping a category opens ProvidersListActivity filtered by that category.
 */
public class CategoriesActivity extends BaseActivity {

    private static final String TAG = "CategoriesActivity";

    private RecyclerView     rvCategories;
    private CategoriesAdapter adapter;
    private List<ServiceCategory> categories;
    private String cityFilter = "";

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        // Get saved city from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(
                AppLocationManager.PREFS_NAME, MODE_PRIVATE);
        cityFilter = prefs.getString(AppLocationManager.KEY_CITY, "");

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        rvCategories = findViewById(R.id.rvCategories);
        buildCategoryList();
        setupGrid();
        loadProviderCounts();
    }

    private void buildCategoryList() {
        categories = new ArrayList<>();
        categories.add(new ServiceCategory("Plumbing",        R.drawable.ic_plumbing,    R.drawable.bg_category_blue));
        categories.add(new ServiceCategory("Electrical",      R.drawable.ic_electrical,  R.drawable.bg_category_yellow));
        categories.add(new ServiceCategory("Cleaning",        R.drawable.ic_cleaning,    R.drawable.bg_category_green));
        categories.add(new ServiceCategory("Repair",          R.drawable.ic_repair,      R.drawable.bg_category_red));
        categories.add(new ServiceCategory("AC Service",      R.drawable.ic_ac,          R.drawable.bg_category_teal));
        categories.add(new ServiceCategory("Painting",        R.drawable.ic_painting,    R.drawable.bg_category_purple));
        categories.add(new ServiceCategory("Carpentry",       R.drawable.ic_carpentry,   R.drawable.bg_category_brown));
        categories.add(new ServiceCategory("Pest Control",    R.drawable.ic_more,        R.drawable.bg_category_grey));
        categories.add(new ServiceCategory("Appliance Repair",R.drawable.ic_repair,      R.drawable.bg_category_red));
        categories.add(new ServiceCategory("Landscaping",     R.drawable.ic_more,        R.drawable.bg_category_green));
        categories.add(new ServiceCategory("Security",        R.drawable.ic_more,        R.drawable.bg_category_blue));
        categories.add(new ServiceCategory("Laundry",         R.drawable.ic_cleaning,    R.drawable.bg_category_teal));
    }

    private void setupGrid() {
        adapter = new CategoriesAdapter(this, categories, cat -> openProviders(cat.name));
        rvCategories.setLayoutManager(new GridLayoutManager(this, 3));
        rvCategories.setAdapter(adapter);
    }

    private void openProviders(String category) {
        Intent intent = new Intent(this, ProviderListActivity.class);
        intent.putExtra(ProviderListActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    /**
     * Fetch count of approved providers per category from Supabase.
     * Updates each category card asynchronously.
     */
    private void loadProviderCounts() {
        for (ServiceCategory cat : categories) {
            final String catName = cat.name;
            new Thread(() -> {
                try {
                    StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                            .append("/rest/v1/provider_applications")
                            .append("?status=eq.approved")
                            .append("&category=ilike.*")
                            .append(java.net.URLEncoder.encode(catName, "UTF-8"))
                            .append("*")
                            .append("&select=id");

                    if (!cityFilter.isEmpty()) {
                        url.append("&city=ilike.*")
                           .append(java.net.URLEncoder.encode(cityFilter, "UTF-8"))
                           .append("*");
                    }

                    Request req = new Request.Builder().url(url.toString())
                            .addHeader("apikey",         BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization",  "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Accept",         "application/json")
                            .build();

                    try (Response r = HTTP.newCall(req).execute()) {
                        if (r.code() == 200) {
                            String body = r.body() != null ? r.body().string() : "[]";
                            int count = new JSONArray(body).length();
                            String label = count == 0 ? "No providers"
                                    : count + " provider" + (count != 1 ? "s" : "");
                            MAIN.post(() -> adapter.updateCount(catName, label));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "loadCount " + catName + ": " + e.getMessage());
                    MAIN.post(() -> adapter.updateCount(catName, "—"));
                }
            }).start();
        }
    }
}

