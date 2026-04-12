package com.example.quickfix;

import android.content.Intent;
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
import com.example.quickfix.api.BookingApi;
import com.example.quickfix.api.RetrofitProvider;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * MyBookingsActivity — Provider's bookings list with filters and actions
 */
public class MyBookingsActivity extends BaseActivity implements BookingsAdapter.BookingActionListener {

    private static final String TAG = "MyBookingsActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

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
    private BookingApi bookingApi;

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
        bookingApi = RetrofitProvider.get().create(BookingApi.class);
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
        String uid = encodeValue(providerFirebaseUid);
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
        String encodedProviderId = encodeValue(providerId);
        String encodedUid = encodeValue(providerFirebaseUid);

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
            String status = b.status == null ? "" : b.status.toLowerCase(Locale.US);
            boolean matchesCancelledGroup = "cancelled".equals(currentFilter)
                    && ("cancelled".equals(status) || "rejected".equals(status));
            if ("all".equals(currentFilter) || currentFilter.equals(status) || matchesCancelledGroup) {
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
                .setPositiveButton("Accept", (d, w) -> updateBookingStatus(booking, "accepted"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onReject(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Reject Booking")
                .setMessage("Reject booking from " + booking.customerName + "?")
                .setPositiveButton("Reject", (d, w) -> updateBookingStatus(booking, "cancelled"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onMarkCompleted(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Mark as Completed")
                .setMessage("Mark this job as completed?")
                .setPositiveButton("Complete", (d, w) -> updateBookingStatus(booking, "completed"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onSetPrice(Booking booking, double amount) {
        if (booking == null) return;
        if (!"accepted".equalsIgnoreCase(booking.status == null ? "" : booking.status.trim())) {
            Toast.makeText(this, "Price can be set only for accepted bookings", Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, "Enter valid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Set Price")
                .setMessage("Set booking amount to \u20b9" + String.format(Locale.US, "%.0f", amount) + "?")
                .setPositiveButton("Set", (d, w) -> updateBookingPrice(booking, amount))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onMessageBooking(Booking booking) {
        if (booking == null) return;
        Intent i = new Intent(this, MessagesActivity.class);
        i.putExtra(MessagesActivity.EXTRA_BOOKING_ID, booking.id == null ? "" : booking.id.trim());
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ID, booking.customerId == null ? "" : booking.customerId.trim());
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ROLE, "user");
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_NAME, booking.customerName == null ? "" : booking.customerName.trim());
        i.putExtra(MessagesActivity.EXTRA_USER_UID, providerFirebaseUid == null ? "" : providerFirebaseUid.trim());
        startActivity(i);
    }

    private void updateBookingStatus(Booking booking, String newStatus) {
        try {
            String bookingId = booking == null ? "" : (booking.id == null ? "" : booking.id.trim());
            if (bookingId == null || bookingId.trim().isEmpty()) {
                Toast.makeText(this, "Invalid booking id", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!("accepted".equalsIgnoreCase(newStatus)
                    || "cancelled".equalsIgnoreCase(newStatus)
                    || "completed".equalsIgnoreCase(newStatus))) {
                Toast.makeText(this, "Invalid booking status", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("status", newStatus.toLowerCase());
            if ("accepted".equalsIgnoreCase(newStatus)) {
                if (providerId != null && !providerId.trim().isEmpty()) {
                    body.put("provider_id", providerId.trim());
                } else if (providerFirebaseUid != null && !providerFirebaseUid.trim().isEmpty()) {
                    body.put("provider_id", providerFirebaseUid.trim());
                }
            }

            bookingApi.updateBookingStatus(
                            ANON_KEY,
                            "Bearer " + ANON_KEY,
                            "eq." + bookingId.trim(),
                            body
                    )
                    .enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    mainHandler.post(() ->
                            Toast.makeText(MyBookingsActivity.this,
                                    "Update failed: " + (t.getMessage() == null ? "network error" : t.getMessage()),
                                    Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                    int code = response.code();
                    String responseBody = "";
                    try {
                        if (response.errorBody() != null) {
                            responseBody = response.errorBody().string();
                        }
                    } catch (Exception ignored) {
                    }
                    Log.d("BOOKING_UPDATE", "HTTP " + code + " body=" + responseBody);
                    final String errorBodyForUi = responseBody;

                    mainHandler.post(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(MyBookingsActivity.this,
                                    "Updated",
                                    Toast.LENGTH_SHORT).show();
                            notifyCustomerForStatus(booking, newStatus, 0d);
                            loadBookings();
                        } else {
                            Log.e("ERROR", errorBodyForUi == null ? "" : errorBodyForUi);
                            Toast.makeText(MyBookingsActivity.this,
                                    "Update failed",
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

    private void updateBookingPrice(Booking booking, double amount) {
        String bookingId = booking == null ? "" : (booking.id == null ? "" : booking.id.trim());
        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Invalid booking id", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", "price_set");
        body.put("payment_amount", amount);
        body.put("price", amount);

        bookingApi.updateBookingStatus(
                        ANON_KEY,
                        "Bearer " + ANON_KEY,
                        "eq." + bookingId,
                        body
                )
                .enqueue(new retrofit2.Callback<>() {
                    @Override
                    public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                        mainHandler.post(() -> Toast.makeText(
                                MyBookingsActivity.this,
                                "Failed to set price",
                                Toast.LENGTH_SHORT
                        ).show());
                    }

                    @Override
                    public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                        mainHandler.post(() -> {
                            if (response.isSuccessful()) {
                                Toast.makeText(MyBookingsActivity.this,
                                        "Price set. Waiting for user payment",
                                        Toast.LENGTH_SHORT).show();
                                notifyCustomerForStatus(booking, "price_set", amount);
                                loadBookings();
                            } else {
                                Toast.makeText(MyBookingsActivity.this,
                                        "Failed to set price",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
    }

    private void notifyCustomerForStatus(Booking booking, String status, double amount) {
        if (booking == null) return;
        if ("price_set".equalsIgnoreCase(status)) {
            FcmNotificationHelper.notifyCustomerPriceSet(
                    booking.customerId,
                    amount,
                    booking.id
            );
            return;
        }
        FcmNotificationHelper.notifyCustomerBookingStatus(
                booking.customerId,
                booking.providerName,
                booking.serviceName,
                status,
                booking.id
        );
    }

    private String encodeValue(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }
}
