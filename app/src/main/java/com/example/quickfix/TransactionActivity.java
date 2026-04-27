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

import java.util.ArrayList;
import java.util.List;
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
                        JSONArray arr = new JSONArray(r.body().string());
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
        adapter.setTransactions(new ArrayList<>());
        new Thread(() -> {
            try {
                // Requirement: Use logged-in user UID (userId), not email.
                String uid = userId == null ? "" : userId.trim();

                // Fetch bookings where customer_id matches the UID and payment_status is 'success'
                String url = BASE_URL + "/rest/v1/bookings"
                        + "?select=*"
                        + "&customer_id=eq." + android.net.Uri.encode(uid)
                        + "&payment_status=eq.success"
                        + "&order=created_at.desc";

                Log.d(TAG, "Transaction Fetch -> UID used: [" + uid + "]");
                Log.d(TAG, "Transaction Fetch -> URL: " + url);

                Request req = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "Transaction Fetch -> Code: " + r.code());
                    Log.d(TAG, "Transaction Fetch -> Response Body: " + body);
                    
                    if (r.isSuccessful()) {
                        JSONArray arr = new JSONArray(body);
                        List<Booking> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            Booking booking = Booking.fromJson(arr.getJSONObject(i));
                            if (booking != null) {
                                list.add(booking);
                            }
                        }
                        
                        MAIN.post(() -> {
                            adapter.setTransactions(list);
                            showEmptyState(list.isEmpty());
                            showLoading(false);
                        });
                    } else {
                        Log.e(TAG, "Failed to load transactions: " + r.code());
                        MAIN.post(() -> {
                            showEmptyState(true);
                            showLoading(false);
                            Toast.makeText(this, "Failed to load transactions", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadTransactions: " + e.getMessage(), e);
                MAIN.post(() -> {
                    showEmptyState(true);
                    showLoading(false);
                    Toast.makeText(this, "Unable to load transactions", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private boolean isValidTransaction(Booking booking) {
        return booking != null && "success".equals(booking.getPaymentStatus());
    }

    private void showEmptyState(boolean show) {
        if (emptyState != null) {
            emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (tvEmptyMessage != null) tvEmptyMessage.setText(R.string.no_transactions_yet);
    }

    private void showLoading(boolean show) {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            if (!show) swipeRefresh.setRefreshing(false);
            return;
        }
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}

