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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.BookingsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MyBookingsActivity — Provider's bookings list with filters and actions
 */
public class MyBookingsActivity extends BaseActivity implements BookingsAdapter.BookingActionListener {

    private static final String TAG = "MyBookingsActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private RecyclerView recyclerView;
    private BookingsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage;
    private ChipGroup chipGroupFilters;
    private Chip chipAll, chipPending, chipAccepted, chipCompleted, chipCancelled;

    private FirebaseAuth mAuth;
    private String providerId;
    private String providerFirebaseUid;
    private String currentFilter = "all";
    private List<Booking> allBookings = new ArrayList<>();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_bookings);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        providerFirebaseUid = user.getUid();
        providerId = providerFirebaseUid;
        Log.d("UID", providerFirebaseUid);

        setupToolbar();
        initViews();
        setupRecyclerView();
        setupFilters();
        loadBookings();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Bookings");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        chipAll = findViewById(R.id.chipAll);
        chipPending = findViewById(R.id.chipPending);
        chipAccepted = findViewById(R.id.chipAccepted);
        chipCompleted = findViewById(R.id.chipCompleted);
        chipCancelled = findViewById(R.id.chipCancelled);

        swipeRefresh.setOnRefreshListener(this::loadBookings);
    }

    private void setupRecyclerView() {
        adapter = new BookingsAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipAll) currentFilter = "all";
            else if (checkedId == R.id.chipPending) currentFilter = "pending";
            else if (checkedId == R.id.chipAccepted) currentFilter = "accepted";
            else if (checkedId == R.id.chipCompleted) currentFilter = "completed";
            else if (checkedId == R.id.chipCancelled) currentFilter = "cancelled";

            filterBookings();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBookings();
    }

    private void loadBookings() {
        showLoading(true);

        if (providerFirebaseUid == null || providerFirebaseUid.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Provider ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        resolveProviderIdAndLoadBookings();
    }

    private void resolveProviderIdAndLoadBookings() {
        String uid = java.net.URLEncoder.encode(providerFirebaseUid, java.nio.charset.StandardCharsets.UTF_8);
        String url = SUPABASE_URL + "/rest/v1/provider_profiles"
                + "?firebase_uid=eq." + uid
                + "&select=id,firebase_uid"
                + "&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "resolveProviderId failed, using firebase uid directly: " + e.getMessage());
                providerId = providerFirebaseUid;
                loadBookingsWithProviderId();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                if (code == 200) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            String profileId = arr.getJSONObject(0).optString("id", "").trim();
                            if (!profileId.isEmpty()) {
                                providerId = profileId;
                            } else {
                                providerId = providerFirebaseUid;
                            }
                        } else {
                            providerId = providerFirebaseUid;
                        }
                    } catch (Exception e) {
                        providerId = providerFirebaseUid;
                    }
                } else {
                    providerId = providerFirebaseUid;
                }

                loadBookingsWithProviderId();
            }
        });
    }

    private void loadBookingsWithProviderId() {
        String filter;
        String encodedProviderId = java.net.URLEncoder.encode(providerId, java.nio.charset.StandardCharsets.UTF_8);
        String encodedUid = java.net.URLEncoder.encode(providerFirebaseUid, java.nio.charset.StandardCharsets.UTF_8);

        if (providerId != null && !providerId.isEmpty() && !providerId.equals(providerFirebaseUid)) {
            filter = "or=(provider_id.eq." + encodedProviderId + ",provider_id.eq." + encodedUid + ")";
        } else {
            filter = "provider_id=eq." + encodedUid;
        }

        String url = SUPABASE_URL + "/rest/v1/bookings"
                + "?" + filter
                + "&order=created_at.desc"
                + "&select=*";

        Log.d(TAG, "loadBookingsWithProviderId URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(MyBookingsActivity.this,
                            "Failed to load bookings: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                Log.d(TAG, "loadBookingsWithProviderId response: HTTP " + code + " | Body: " + body);

                mainHandler.post(() -> {
                    showLoading(false);
                    if (code == 200) {
                        parseBookings(body);
                    } else {
                        Toast.makeText(MyBookingsActivity.this,
                                "Error: HTTP " + code + " | " + body,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Legacy fallback retained for compatibility; now routed to provider_id query.
    private void loadBookingsWithProviderName(String providerName) {
        loadBookingsWithProviderId();
    }

    // Legacy fallback retained for compatibility; now routed to provider_id query.
    private void loadBookingsWithFirebaseUid() {
        loadBookingsWithProviderId();
    }

    private void parseBookings(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            allBookings.clear();

            for (int i = 0; i < arr.length(); i++) {
                Booking booking = Booking.fromJson(arr.getJSONObject(i));
                if (booking != null) {
                    allBookings.add(booking);
                }
            }

            filterBookings();
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            Toast.makeText(this, "Error parsing bookings", Toast.LENGTH_SHORT).show();
        }
    }

    private void filterBookings() {
        List<Booking> filtered = new ArrayList<>();

        for (Booking b : allBookings) {
            if ("all".equals(currentFilter) || currentFilter.equals(b.status)) {
                filtered.add(b);
            }
        }

        adapter.setBookings(filtered);

        if (filtered.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvEmptyMessage.setText("No " + currentFilter + " bookings found");
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean show) {
        if (swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(show);
        } else {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // BookingActionListener implementations
    @Override
    public void onAccept(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Accept Booking")
                .setMessage("Accept booking from " + booking.customerName + "?")
                .setPositiveButton("Accept", (d, w) -> updateBookingStatus(booking.id, "accepted"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onReject(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Reject Booking")
                .setMessage("Reject booking from " + booking.customerName + "?")
                .setPositiveButton("Reject", (d, w) -> updateBookingStatus(booking.id, "cancelled"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onMarkCompleted(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Mark as Completed")
                .setMessage("Mark this job as completed?")
                .setPositiveButton("Complete", (d, w) -> updateBookingStatus(booking.id, "completed"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateBookingStatus(String bookingId, String newStatus) {
        try {
            JSONObject body = new JSONObject();
            body.put("status", newStatus);
            if ("accepted".equalsIgnoreCase(newStatus)) {
                if (providerId != null && !providerId.trim().isEmpty()) {
                    body.put("provider_id", providerId.trim());
                } else if (providerFirebaseUid != null && !providerFirebaseUid.trim().isEmpty()) {
                    body.put("provider_id", providerFirebaseUid.trim());
                }
            }

            String url = SUPABASE_URL + "/rest/v1/bookings?id=eq." + bookingId;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() ->
                            Toast.makeText(MyBookingsActivity.this,
                                    "Update failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    mainHandler.post(() -> {
                        if (code == 200 || code == 204) {
                            Toast.makeText(MyBookingsActivity.this,
                                    "Status updated successfully",
                                    Toast.LENGTH_SHORT).show();
                            loadBookings();
                        } else {
                            Toast.makeText(MyBookingsActivity.this,
                                    "Update failed: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Update error: " + e.getMessage());
            Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show();
        }
    }
}
