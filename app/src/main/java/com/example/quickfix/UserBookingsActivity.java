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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.UserBookingsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * UserBookingsActivity — Displays bookings for a regular (customer) user.
 * Filters: All | Upcoming | Completed | Cancelled
 */
public class UserBookingsActivity extends AppCompatActivity
        implements UserBookingsAdapter.BookingActionListener {

    private static final String TAG      = "UserBookings";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;

    private RecyclerView         recyclerView;
    private UserBookingsAdapter  adapter;
    private SwipeRefreshLayout   swipeRefresh;
    private ProgressBar          progressBar;
    private LinearLayout         emptyState;
    private TextView             tvEmptyMessage, tvBookingCount;
    private ChipGroup            chipGroupFilters;

    private String currentFilter = "all";
    private List<Booking> allBookings = new ArrayList<>();
    private String userId;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_bookings);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        userId = user.getUid();

        bindViews();
        setupRecyclerView();
        setupFilters();
        setupSwipeRefresh();
        loadBookings();
    }

    private void bindViews() {
        recyclerView    = findViewById(R.id.recyclerView);
        swipeRefresh    = findViewById(R.id.swipeRefresh);
        progressBar     = findViewById(R.id.progressBar);
        emptyState      = findViewById(R.id.emptyState);
        tvEmptyMessage  = findViewById(R.id.tvEmptyMessage);
        tvBookingCount  = findViewById(R.id.tvBookingCount);
        chipGroupFilters= findViewById(R.id.chipGroupFilters);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new UserBookingsAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        if (chipGroupFilters == null) return;
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if      (id == R.id.chipAll)       currentFilter = "all";
            else if (id == R.id.chipUpcoming)  currentFilter = "upcoming";
            else if (id == R.id.chipCompleted) currentFilter = "completed";
            else if (id == R.id.chipCancelled) currentFilter = "cancelled";
            applyFilter();
        });
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary);
            swipeRefresh.setOnRefreshListener(this::loadBookings);
        }
    }

    private void loadBookings() {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BASE_URL
                        + "/rest/v1/bookings?customer_id=eq." + userId
                        + "&select=*&order=created_at.desc";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "loadBookings HTTP " + r.code());
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        List<Booking> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            Booking b = Booking.fromJson(arr.getJSONObject(i));
                            if (b != null) list.add(b);
                        }
                        allBookings = list;
                        MAIN.post(() -> { showLoading(false); applyFilter(); });
                    } else {
                        MAIN.post(() -> { showLoading(false);
                            Toast.makeText(this, "Failed to load bookings", Toast.LENGTH_SHORT).show(); });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadBookings: " + e.getMessage());
                MAIN.post(() -> { showLoading(false);
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show(); });
            }
        }).start();
    }

    private void applyFilter() {
        List<Booking> filtered;
        if ("all".equals(currentFilter)) {
            filtered = new ArrayList<>(allBookings);
        } else if ("upcoming".equals(currentFilter)) {
            filtered = allBookings.stream()
                    .filter(b -> "pending".equalsIgnoreCase(b.status)
                              || "accepted".equalsIgnoreCase(b.status)
                              || "confirmed".equalsIgnoreCase(b.status))
                    .collect(Collectors.toList());
        } else {
            filtered = allBookings.stream()
                    .filter(b -> currentFilter.equalsIgnoreCase(b.status))
                    .collect(Collectors.toList());
        }
        adapter.setBookings(filtered);
        if (tvBookingCount != null)
            tvBookingCount.setText(filtered.size() + " booking" + (filtered.size() != 1 ? "s" : ""));
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onViewDetails(Booking booking) {
        showBookingDetails(booking);
    }

    @Override
    public void onCancelBooking(Booking booking) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Booking")
                .setMessage("Are you sure you want to cancel this booking?")
                .setPositiveButton("Yes, Cancel", (d, w) -> cancelBooking(booking))
                .setNegativeButton("No", null)
                .show();
    }

    private void cancelBooking(Booking booking) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "cancelled");
                String url = BASE_URL + "/rest/v1/bookings?id=eq." + booking.id;
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    MAIN.post(() -> {
                        if (r.code() == 200 || r.code() == 204) {
                            booking.status = "cancelled";
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "Booking cancelled", Toast.LENGTH_SHORT).show();
                            loadBookings();
                        } else {
                            Toast.makeText(this, "Failed to cancel. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "cancelBooking: " + e.getMessage());
                MAIN.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showBookingDetails(Booking b) {
        String msg = "Service: "  + (b.serviceName.isEmpty()  ? "—" : b.serviceName)  + "\n"
                   + "Provider: " + (b.providerName.isEmpty() ? "—" : b.providerName) + "\n"
                   + "Date: "     + (b.bookingDate.isEmpty()  ? "—" : b.bookingDate)  + "\n"
                   + "Time: "     + (b.bookingTime.isEmpty()  ? "—" : b.bookingTime)  + "\n"
                   + "Address: "  + (b.address.isEmpty()      ? "—" : b.address)      + "\n"
                   + "Status: "   + b.status.toUpperCase()                             + "\n"
                   + "Price: ₹"  + String.format("%.0f", b.price);
        new AlertDialog.Builder(this)
                .setTitle("Booking Details")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private void showLoading(boolean show) {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            if (!show) swipeRefresh.setRefreshing(false);
            return;
        }
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}

