package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ContentManagementActivity — Admin hub for Banner and Offer management.
 * Shows live counts from Supabase and navigates to respective management screens.
 */
public class ContentManagementActivity extends AppCompatActivity {

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
        if (tvTotalBanners != null) tvTotalBanners.setText("…");
        if (tvTotalOffers  != null) tvTotalOffers.setText("…");

        fetchCount("banners", count -> {
            if (tvTotalBanners != null) tvTotalBanners.setText(String.valueOf(count));
        });
        fetchCount("offers", count -> {
            if (tvTotalOffers != null) tvTotalOffers.setText(String.valueOf(count));
        });
    }

    /**
     * Fetch all rows from `table` and return the row count on the main thread.
     */
    private void fetchCount(String table, CountCallback callback) {
        // Use select=id to minimise payload; HEAD count would need RPC, this is simpler.
        String url = SUPABASE_URL + "/rest/v1/" + table + "?select=id";

        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .get()
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "fetchCount(" + table + ") failed: " + e.getMessage());
                main.post(() -> callback.onCount(0));
            }

            @Override public void onResponse(Call call, Response resp) throws IOException {
                int    code = resp.code();
                String body = resp.body() != null ? resp.body().string() : "[]";
                resp.close();
                int count = 0;
                if (code == 200) {
                    try { count = new JSONArray(body).length(); }
                    catch (Exception ex) { Log.e(TAG, "parse error: " + ex.getMessage()); }
                } else {
                    Log.e(TAG, "fetchCount(" + table + ") HTTP " + code + ": " + body);
                    main.post(() ->
                            Toast.makeText(ContentManagementActivity.this,
                                    "Failed to load " + table + " count (HTTP " + code + ")",
                                    Toast.LENGTH_SHORT).show());
                }
                final int finalCount = count;
                main.post(() -> callback.onCount(finalCount));
            }
        });
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
