package com.example.quickfix;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ProviderReviewsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ProviderReviewsActivity extends BaseActivity {

    public static final String EXTRA_PROVIDER_ID = "provider_id";
    public static final String EXTRA_PROVIDER_UID = "provider_uid";

    private static final String TAG = "ProviderReviews";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage;

    private ProviderReviewsAdapter adapter;

    private String providerProfileId;
    private String providerUid;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_reviews);

        setupToolbar();
        bindViews();
        setupRecycler();
        setupRefresh();

        providerProfileId = getIntent().getStringExtra(EXTRA_PROVIDER_ID);
        providerUid = getIntent().getStringExtra(EXTRA_PROVIDER_UID);

        if ((providerProfileId == null || providerProfileId.trim().isEmpty())
                && (providerUid == null || providerUid.trim().isEmpty())) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                providerUid = user.getUid();
            }
        }

        loadReviews();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.provider_reviews_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        swipeRefresh = findViewById(R.id.swipeRefresh);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
    }

    private void setupRecycler() {
        adapter = new ProviderReviewsAdapter(this::confirmDelete);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadReviews);
    }

    private void loadReviews() {
        showLoading(true);

        new Thread(() -> {
            List<ProviderReviewsAdapter.ReviewItem> items = new ArrayList<>();
            try {
                String providerId = resolveProviderId();
                if (providerId.isEmpty()) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        showEmpty(true);
                    });
                    return;
                }

                String url = SUPABASE_URL + "/rest/v1/provider_reviews"
                        + "?select=id,customer_name,rating,comment,created_at"
                        + "&order=created_at.desc";

                if (providerProfileId != null && !providerProfileId.trim().isEmpty()) {
                    url += "&provider_id=eq." + Uri.encode(providerProfileId.trim());
                } else {
                    url += "&provider_id=eq." + Uri.encode(providerId);
                }

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "[]";
                    if (response.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            ProviderReviewsAdapter.ReviewItem item = new ProviderReviewsAdapter.ReviewItem();
                            item.id = o.optString("id", "");
                            item.customerName = o.optString("customer_name", "Customer");
                            item.rating = o.optInt("rating", 0);
                            item.comment = o.optString("comment", "");
                            item.createdAt = formatDate(o.optString("created_at", ""));
                            items.add(item);
                        }
                    } else {
                        Log.w(TAG, "loadReviews HTTP " + response.code() + " | " + body);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "loadReviews failed: " + e.getMessage());
            }

            runOnUiThread(() -> {
                showLoading(false);
                adapter.setItems(items);
                showEmpty(items.isEmpty());
            });
        }).start();
    }

    private String resolveProviderId() {
        String pid = providerProfileId == null ? "" : providerProfileId.trim();
        if (!pid.isEmpty()) return pid;
        String uid = providerUid == null ? "" : providerUid.trim();
        if (uid.isEmpty()) return "";
        String resolved = fetchProviderProfileIdByFirebaseUid(uid);
        if (!resolved.isEmpty()) {
            providerProfileId = resolved;
            return resolved;
        }
        return uid;
    }

    private String fetchProviderProfileIdByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) return "";
        try {
            String url = SUPABASE_URL + "/rest/v1/provider_profiles"
                    + "?firebase_uid=eq." + Uri.encode(firebaseUid.trim())
                    + "&select=id&limit=1";

            Request request = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.code() != 200) return "";
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return "";
                return arr.getJSONObject(0).optString("id", "");
            }
        } catch (Exception e) {
            return "";
        }
    }

    private void confirmDelete(ProviderReviewsAdapter.ReviewItem item, int position) {
        if (item == null || item.id == null || item.id.trim().isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.provider_reviews_delete_title))
                .setMessage(getString(R.string.provider_reviews_delete_message))
                .setPositiveButton(getString(R.string.provider_reviews_delete_confirm), (d, w) -> {
                    deleteReview(item, position);
                })
                .setNegativeButton(getString(R.string.provider_reviews_delete_cancel), null)
                .show();
    }

    private void deleteReview(ProviderReviewsAdapter.ReviewItem item, int position) {
        showLoading(true);
        new Thread(() -> {
            int code = 0;
            String errorBody = "";
            try {
                String url = SUPABASE_URL + "/rest/v1/provider_reviews?id=eq." + Uri.encode(item.id);
                Request request = new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    code = response.code();
                    errorBody = response.body() != null ? response.body().string() : "";
                }
            } catch (Exception e) {
                errorBody = e.getMessage();
            }

            int finalCode = code;
            String finalErrorBody = errorBody;
            runOnUiThread(() -> {
                showLoading(false);
                if (finalCode == 200 || finalCode == 204) {
                    adapter.removeAt(position);
                    showEmpty(adapter.getItemCount() == 0);
                    Toast.makeText(this, getString(R.string.provider_reviews_deleted), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.provider_reviews_delete_failed), Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "deleteReview failed HTTP " + finalCode + " | " + finalErrorBody);
                }
            });
        }).start();
    }

    private String formatDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String trimmed = raw.trim();
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String p : patterns) {
            try {
                SimpleDateFormat input = new SimpleDateFormat(p, Locale.US);
                if (p.contains("X")) {
                    input.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = input.parse(trimmed);
                if (date == null) continue;
                return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(date);
            } catch (Exception ignored) {
            }
        }
        return trimmed;
    }

    private void showLoading(boolean show) {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(show);
        } else if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showEmpty(boolean show) {
        if (emptyState != null) emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (tvEmptyMessage != null) tvEmptyMessage.setText(R.string.provider_reviews_empty);
    }
}

