package com.example.quickfix;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.AdminProviderManagementAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Admin provider management based on provider_profiles table.
 */
public class AdminProviderManagementActivity extends BaseActivity
        implements AdminProviderManagementAdapter.Listener {

    private static final String TAG = "AdminProviders";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(16, TimeUnit.SECONDS)
            .build();

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvProviders;
    private LinearLayout emptyStateLayout;
    private View progressBar;

    private TextView tvTotalProviders;
    private TextView tvAvailableProviders;
    private TextView tvApprovedProviders;

    private final List<ProviderProfile> providers = new ArrayList<>();
    private AdminProviderManagementAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_provider_management);

        bindViews();
        setupRecycler();
        setupRefresh();
        loadProviders();
    }

    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        rvProviders = findViewById(R.id.rvProviders);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        progressBar = findViewById(R.id.progressBar);

        tvTotalProviders = findViewById(R.id.tvTotalProviders);
        tvAvailableProviders = findViewById(R.id.tvAvailableProviders);
        tvApprovedProviders = findViewById(R.id.tvApprovedProviders);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadProviders());
    }

    private void setupRecycler() {
        adapter = new AdminProviderManagementAdapter(this, providers, this);
        rvProviders.setLayoutManager(new LinearLayoutManager(this));
        rvProviders.setAdapter(adapter);
    }

    private void setupRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadProviders);
    }

    private void loadProviders() {
        showLoading(true);
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles"
                + "?select=id,firebase_uid,business_name,owner_name,category,city,address,phone,email,description,experience,rating,is_available,is_approved,is_verified,created_at"
                + "&order=created_at.desc";

        Request req = baseGet(url);
        http.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showError("Failed to load providers: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                if (!response.isSuccessful()) {
                    Log.e(TAG, "loadProviders HTTP " + response.code() + " body=" + body);
                    runOnUiThread(() -> {
                        showLoading(false);
                        showError("Failed to load providers (" + response.code() + ")");
                    });
                    return;
                }

                List<ProviderProfile> parsed = parseProviders(body);
                runOnUiThread(() -> {
                    showLoading(false);
                    providers.clear();
                    providers.addAll(parsed);
                    adapter.notifyDataSetChanged();
                    updateStats();
                    updateListState();
                });
            }
        });
    }

    private List<ProviderProfile> parseProviders(String json) {
        List<ProviderProfile> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                ProviderProfile p = new ProviderProfile();
                p.id = o.optString("id", "");
                p.firebaseUid = o.optString("firebase_uid", "");
                p.businessName = o.optString("business_name", "");
                p.ownerName = o.optString("owner_name", "");
                p.category = o.optString("category", "");
                p.city = o.optString("city", "");
                p.address = o.optString("address", "");
                p.phone = o.optString("phone", "");
                p.email = o.optString("email", "");
                p.description = o.optString("description", "");
                p.experienceYrs = o.optString("experience", o.optString("experience_yrs", ""));
                p.rating = (float) o.optDouble("rating", 0d);
                p.isAvailable = o.optBoolean("is_available", true);
                p.isApproved = o.optBoolean("is_approved", false);
                p.isVerified = o.optBoolean("is_verified", false);
                p.createdAt = o.optString("created_at", "");
                list.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseProviders error", e);
        }
        return list;
    }

    private void updateStats() {
        int total = providers.size();
        int available = 0;
        int approved = 0;
        for (ProviderProfile p : providers) {
            if (p.isAvailable) available++;
            if (p.isApproved) approved++;
        }
        tvTotalProviders.setText(String.valueOf(total));
        tvAvailableProviders.setText(String.valueOf(available));
        tvApprovedProviders.setText(String.valueOf(approved));
    }

    private void updateListState() {
        boolean empty = providers.isEmpty();
        rvProviders.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private void showLoading(boolean loading) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            rvProviders.setVisibility(View.GONE);
            if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewDetails(@NonNull ProviderProfile provider) {
        loadProviderMetricsAndShowDialog(provider);
    }

    @Override
    public void onToggleBlock(@NonNull ProviderProfile provider) {
        boolean newValue = !provider.isAvailable;
        String title = newValue ? "Unblock Provider" : "Block Provider";
        String msg = newValue
                ? "Allow this provider to receive bookings again?"
                : "Block this provider from receiving bookings?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Yes", (d, w) -> updateProviderFlags(provider, newValue, null, null))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleVerify(@NonNull ProviderProfile provider) {
        boolean newValue = !provider.isVerified;
        String title = newValue ? "Verify Provider" : "Remove Verification";
        String msg = newValue
                ? "Show verified badge for this provider?"
                : "Remove verified badge from this provider?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Confirm", (d, w) -> updateProviderFlags(provider, null, null, newValue))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateProviderFlags(@NonNull ProviderProfile provider, Boolean isAvailable, Boolean isApproved, Boolean isVerified) {
        try {
            JSONObject body = new JSONObject();
            if (isAvailable != null) body.put("is_available", isAvailable);
            if (isApproved != null && isApproved) {
                body.put("is_approved", true);
                String nowIso = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                        .format(new java.util.Date());
                body.put("approved_at", nowIso);
            }
            if (isVerified != null) body.put("is_verified", isVerified);

            String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?id=eq."
                    + URLEncoder.encode(provider.id, StandardCharsets.UTF_8.name());

            Request req = basePatch(url, body.toString());
            http.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> showError("Update failed: " + e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "updateProviderFlags HTTP " + response.code() + " body=" + responseBody);
                        runOnUiThread(() -> showError("Update failed (" + response.code() + ")"));
                        return;
                    }
                    runOnUiThread(() -> {
                        if (isAvailable != null) provider.isAvailable = isAvailable;
                        if (isApproved != null && isApproved) provider.isApproved = true;
                        if (isVerified != null) provider.isVerified = isVerified;
                        adapter.notifyDataSetChanged();
                        updateStats();
                        Toast.makeText(AdminProviderManagementActivity.this, "Provider updated", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            showError("Update failed: " + e.getMessage());
        }
    }

    private void loadProviderMetricsAndShowDialog(@NonNull ProviderProfile provider) {
        new Thread(() -> {
            int totalBookings = fetchCount("bookings", "provider_id", provider.id);
            double totalEarnings = fetchSumPrice("bookings", "provider_id", provider.id, "status=in.(completed,done)");
            int totalReviews = fetchCount("reviews", "provider_id", provider.id);

            runOnUiThread(() -> showProviderDetailsDialog(provider, totalBookings, totalEarnings, totalReviews));
        }).start();
    }

    private int fetchCount(String table, String field, String value) {
        try {
            String url = BuildConfig.SUPABASE_URL + "/rest/v1/" + table
                    + "?select=id&" + field + "=eq." + URLEncoder.encode(value, StandardCharsets.UTF_8.name())
                    + "&limit=1";

            Request req = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .addHeader("Prefer", "count=exact")
                    .build();

            try (Response response = http.newCall(req).execute()) {
                if (!response.isSuccessful()) return 0;
                String range = response.header("Content-Range", "");
                if (!TextUtils.isEmpty(range) && range.contains("/")) {
                    return Integer.parseInt(range.substring(range.lastIndexOf('/') + 1).trim());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchCount(" + table + ") failed: " + e.getMessage());
        }
        return 0;
    }

    private double fetchSumPrice(String table, String field, String value, String extraQuery) {
        try {
            StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/").append(table)
                    .append("?select=price")
                    .append("&").append(field).append("=eq.")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
            if (!TextUtils.isEmpty(extraQuery)) url.append("&").append(extraQuery);

            Request req = baseGet(url.toString());
            try (Response response = http.newCall(req).execute()) {
                if (!response.isSuccessful() || response.body() == null) return 0d;
                String body = response.body().string();
                JSONArray arr = new JSONArray(body);
                double sum = 0d;
                for (int i = 0; i < arr.length(); i++) {
                    sum += arr.getJSONObject(i).optDouble("price", 0d);
                }
                return sum;
            }
        } catch (Exception e) {
            Log.w(TAG, "fetchSumPrice failed: " + e.getMessage());
        }
        return 0d;
    }

    private void showProviderDetailsDialog(@NonNull ProviderProfile p,
                                           int totalBookings,
                                           double totalEarnings,
                                           int totalReviews) {
        String message = "Business: " + safe(p.businessName)
                + "\nOwner: " + safe(p.ownerName)
                + "\nCategory: " + safe(p.category)
                + "\nCity: " + safe(p.city)
                + "\nPhone: " + safe(p.phone)
                + "\nEmail: " + safe(p.email)
                + "\nAvailable: " + (p.isAvailable ? "Yes" : "No")
                + "\nApproved: " + (p.isApproved ? "Yes" : "No")
                + "\nVerified: " + (p.isVerified ? "Yes" : "No")
                + "\nRating: " + String.format(Locale.US, "%.1f", p.rating)
                + "\n\nTotal Bookings: " + totalBookings
                + "\nTotal Earnings: " + String.format(Locale.US, "Rs %.2f", totalEarnings)
                + "\nReviews: " + totalReviews
                + "\n\nAddress: " + safe(p.address)
                + "\nExperience: " + safe(p.experienceYrs)
                + "\nDescription: " + safe(p.description);

        new AlertDialog.Builder(this)
                .setTitle("Provider Details")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private Request baseGet(String url) {
        return new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build();
    }

    private Request basePatch(String url, String jsonBody) {
        return new Request.Builder()
                .url(url)
                .patch(RequestBody.create(jsonBody, JSON))
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
