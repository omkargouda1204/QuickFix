package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.EarningsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
 * TransactionActivity — Customer transaction history based on paid/completed bookings.
 */
public class TransactionActivity extends BaseActivity {

    private static final String TAG = "TransactionActivity";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;

    private RecyclerView recyclerView;
    private EarningsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage;

    private String userId;
    private String userDbId;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earnings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Transaction History");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        userId = user.getUid();

        bindViews();
        setupRecyclerView();
        setupRefresh();
        resolveUserDbIdAndLoad();
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        hideProviderSummaryCards();
    }

    private void hideProviderSummaryCards() {
        View summaryCards = findViewById(R.id.summaryCardsContainer);
        if (summaryCards != null) {
            summaryCards.setVisibility(View.GONE);
        }
    }

    private void setupRecyclerView() {
        adapter = new EarningsAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary);
            swipeRefresh.setOnRefreshListener(this::loadTransactions);
        }
    }

    private void resolveUserDbIdAndLoad() {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL + "/rest/v1/users"
                        + "?firebase_uid=eq." + android.net.Uri.encode(userId)
                        + "&select=id&limit=1";
                Request req = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        String responseBody = r.body().string();
                        JSONArray arr = new JSONArray(responseBody);
                        if (arr.length() > 0) {
                            userDbId = arr.getJSONObject(0).optString("id", "");
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "resolveUserDbId: " + e.getMessage());
            }

            MAIN.post(() -> {
                if (userDbId == null || userDbId.trim().isEmpty()) {
                    userDbId = userId;
                }
                loadTransactions();
            });
        }).start();
    }

    private void loadTransactions() {
        showLoading(true);
        new Thread(() -> {
            try {
                String uid = userId == null ? "" : userId.trim();
                String dbId = userDbId == null ? "" : userDbId.trim();

                Log.d(TAG, "Loading transactions for uid=" + uid + " dbId=" + dbId);

                List<Booking> list = new ArrayList<>();
                
                // Load regular bookings
                List<Booking> bookings = fetchTransactionRows(
                        BASE_URL + "/rest/v1/bookings"
                                + "?select=*"
                                + "&or=(customer_id.eq." + android.net.Uri.encode(uid)
                                + ",customer_id.eq." + android.net.Uri.encode(dbId)
                                + ",customer_firebase_uid.eq." + android.net.Uri.encode(uid)
                                + ",user_firebase_uid.eq." + android.net.Uri.encode(uid)
                                + ",user_id.eq." + android.net.Uri.encode(uid) + ")"
                                + "&order=created_at.desc",
                        "bookings");
                list.addAll(bookings);
                Log.d(TAG, "Loaded " + bookings.size() + " bookings");

                // Load emergency requests
                List<Booking> emergencies = fetchTransactionRows(
                        BASE_URL + "/rest/v1/emergency_requests"
                                + "?select=*"
                                + "&or=(user_firebase_uid.eq." + android.net.Uri.encode(uid)
                                + ",user_id.eq." + android.net.Uri.encode(dbId)
                                + ",user_id.eq." + android.net.Uri.encode(uid) + ")"
                                + "&order=created_at.desc",
                        "emergency_requests");
                list.addAll(emergencies);
                Log.d(TAG, "Loaded " + emergencies.size() + " emergency requests");

                // Filter to transactions only
                List<Booking> filtered = new ArrayList<>();
                for (Booking booking : list) {
                    if (booking != null && isTransaction(booking)) {
                        filtered.add(booking);
                    }
                }

                // Resolve missing provider names for display
                try {
                    resolveProviderNamesForTransactions(filtered);
                } catch (Exception e) {
                    Log.w(TAG, "resolveProviderNamesForTransactions failed: " + e.getMessage());
                }

                Log.d(TAG, "Final filtered transactions: " + filtered.size() + " (from " + list.size() + " total)");

                MAIN.post(() -> {
                    adapter.setTransactions(filtered);
                    showEmptyState(filtered.isEmpty());
                    showLoading(false);
                    if (!filtered.isEmpty()) {
                        Toast.makeText(TransactionActivity.this, "✓ Loaded " + filtered.size() + " transactions", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "loadTransactions error: " + e.getMessage(), e);
                MAIN.post(() -> {
                    showEmptyState(true, "Failed to load transactions. Pull to retry.");
                    showLoading(false);
                    Toast.makeText(TransactionActivity.this, "Unable to load transactions: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void resolveProviderNamesForTransactions(List<Booking> bookings) throws Exception {
        if (bookings == null || bookings.isEmpty()) return;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Booking b : bookings) {
            if (b == null) continue;
            String pid = b.providerId == null ? "" : b.providerId.trim();
            String pname = b.providerName == null ? "" : b.providerName.trim();
            if (pid.isEmpty()) continue;
            if (pname.isEmpty() || "null".equalsIgnoreCase(pname) || "Awaiting Acceptance".equalsIgnoreCase(pname)) ids.add(pid);
        }
        if (ids.isEmpty()) return;

        String joined = String.join(",", ids);
        String url = BASE_URL + "/rest/v1/provider_profiles?select=id,business_name&limit=" + ids.size()
            + "&id=in.(" + joined + ")";

        Request req = new Request.Builder().url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (r.code() != 200) return;
            String body = r.body() != null ? r.body().string() : "[]";
            JSONArray arr = new JSONArray(body);
            java.util.Map<String, String> map = new java.util.HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                String name = o.optString("business_name", "");
                if (id != null && !id.isEmpty() && name != null && !name.isEmpty()) map.put(id, name);
            }

            for (Booking b : bookings) {
                if (b == null) continue;
                String pid = b.providerId == null ? "" : b.providerId.trim();
                if (!pid.isEmpty() && (b.providerName == null || b.providerName.trim().isEmpty() || "null".equalsIgnoreCase(b.providerName))) {
                    String resolved = map.get(pid);
                    if (resolved != null && !resolved.trim().isEmpty()) b.providerName = resolved;
                }
            }
        }
    }

    private List<Booking> fetchTransactionRows(String url, String label) throws Exception {
        Log.d(TAG, "Fetching " + label + " from: " + url.substring(0, Math.min(120, url.length())) + "...");

        Request req = new Request.Builder().url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        List<Booking> list = new ArrayList<>();
        try (Response r = HTTP.newCall(req).execute()) {
            String body = r.body() != null ? r.body().string() : "[]";
            Log.d(TAG, label + " response: HTTP " + r.code() + " | Body length: " + body.length());
            
            if (!r.isSuccessful()) {
                Log.w(TAG, label + " returned HTTP " + r.code() + ": " + body.substring(0, Math.min(200, body.length())));
                return list;
            }
            
            if (!body.startsWith("[")) {
                Log.w(TAG, label + " response is not a JSON array: " + body.substring(0, Math.min(100, body.length())));
                return list;
            }

            JSONArray arr = new JSONArray(body);
            Log.d(TAG, "Parsed " + arr.length() + " items from " + label);
            
            for (int i = 0; i < arr.length(); i++) {
                try {
                    Booking booking = Booking.fromJson(arr.getJSONObject(i));
                    if (booking != null) {
                        list.add(booking);
                    }
                } catch (Exception e) {
                    Log.w(TAG, label + " item " + i + " parse error: " + e.getMessage());
                }
            }
            
            Log.d(TAG, "Successfully parsed " + list.size() + " items from " + label);
        }
        return list;
    }

    /**
     * Define what constitutes a "transaction" in the history.
     * We broaden this to include anything that has been accepted or paid.
     * Emergency requests in any state are included (they're important to users).
     */
    private boolean isTransaction(Booking booking) {
        if (booking == null) return false;
        
        String status = normalize(booking.status);
        String payment = normalize(booking.paymentStatus);
        
        // Emergency requests: always show them
        if (booking.isEmergency) {
            return true;
        }
        
        // Regular bookings: show if paid or completed
        return "paid".equals(status)
                || "completed".equals(status)
                || "done".equals(status)
                || "success".equals(status)
                || "accepted".equals(status)
                || "processing".equals(status)
                || "settled".equals(status)
                || "price_set".equals(status)
                || "paid".equals(payment)
                || "success".equals(payment);
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.US);
    }
    
    private void showEmptyState(boolean show) {
        showEmptyState(show, R.string.no_transactions_yet);
    }

    private void showEmptyState(boolean show, String message) {
        if (emptyState != null) {
            emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (tvEmptyMessage != null) tvEmptyMessage.setText(message);
    }

    private void showEmptyState(boolean show, int messageRes) {
        if (emptyState != null) {
            emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (tvEmptyMessage != null) tvEmptyMessage.setText(messageRes);
    }

    private void showLoading(boolean show) {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            if (!show) swipeRefresh.setRefreshing(false);
            return;
        }
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
