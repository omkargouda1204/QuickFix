package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ContentManagementActivity — Admin hub for Banner and Offer management.
 * Shows live counts from Supabase and navigates to respective management screens.
 */
public class ContentManagementActivity extends BaseActivity {

    private static final String TAG = "ContentMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private TextView tvTotalBanners, tvTotalOffers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_management);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        bindViews();
        loadCounts();
    }

    private void bindViews() {
        tvTotalBanners = findViewById(R.id.tvTotalBanners);
        tvTotalOffers  = findViewById(R.id.tvTotalOffers);

        // Back button
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Refresh button
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadCounts());

        // Banner card — whole card + button both navigate
        View cardBanner = findViewById(R.id.cardBannerMgmt);
        View btnManageBanners = findViewById(R.id.btnManageBanners);
        if (cardBanner      != null) cardBanner.setOnClickListener(v -> openBanners());
        if (btnManageBanners != null) btnManageBanners.setOnClickListener(v -> openBanners());

        // Offer card — whole card + button both navigate
        View cardOffer = findViewById(R.id.cardOfferMgmt);
        View btnManageOffers = findViewById(R.id.btnManageOffers);
        if (cardOffer      != null) cardOffer.setOnClickListener(v -> openOffers());
        if (btnManageOffers != null) btnManageOffers.setOnClickListener(v -> openOffers());
    }

    // ── Navigation ────────────────────────────────────────────────────────
    private void openBanners() {
        startActivity(new Intent(this, BannerManagementActivity.class));
    }

    private void openOffers() {
        startActivity(new Intent(this, OfferManagementActivity.class));
    }

    // ── Load Counts ───────────────────────────────────────────────────────
    private void loadCounts() {
        if (tvTotalBanners != null) tvTotalBanners.setText("...");
        if (tvTotalOffers  != null) tvTotalOffers.setText("...");

        fetchCount("banners", count -> {
            if (tvTotalBanners != null) tvTotalBanners.setText(String.valueOf(count));
        });
        fetchCount("offers", count -> {
            if (tvTotalOffers != null) tvTotalOffers.setText(String.valueOf(count));
        });
    }

    /**
     * Fetches row count by reading a lightweight id list.
     * This avoids Content-Range/head behavior differences across proxies/devices.
     */
    private void fetchCount(String table, CountCallback callback) {
        final String encodedTable = android.net.Uri.encode(table);
        final String url = SUPABASE_URL + "/rest/v1/" + encodedTable + "?select=id";
        Log.d(TAG, "fetchCount(" + table + ") URL=" + url);

        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    int code = resp.code();
                    int count = 0;
                    String body = resp.body() != null ? resp.body().string() : "[]";
                    if (code == 200 || code == 206) {
                        count = new org.json.JSONArray(body).length();
                    } else {
                        Log.e(TAG, "fetchCount(" + table + ") HTTP " + code + ": " + body);
                    }
                    final int finalCount = count;
                    main.post(() -> callback.onCount(finalCount));
                }
            } catch (Exception e) {
                Log.e(TAG, "fetchCount(" + table + ") exception: " + e.getMessage(), e);
                main.post(() -> callback.onCount(0));
            }
        }).start();
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Refresh counts whenever user returns from Banner or Offer management pages
        loadCounts();
    }

    // ── Callback interface ────────────────────────────────────────────────
    private interface CountCallback {
        void onCount(int count);
    }
}
