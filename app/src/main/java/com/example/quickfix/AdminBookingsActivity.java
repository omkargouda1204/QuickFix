package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.AdminBookingsAdapter;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AdminBookingsActivity — Displays ALL platform bookings in admin panel.
 * Fetches from the existing `bookings` table using only existing columns.
 * Features: search, status filter chips, pull-to-refresh, newest first.
 */
public class AdminBookingsActivity extends BaseActivity {

    private static final String TAG = "AdminBookings";

    // Admin should fetch all bookings columns.
    private static final String SELECT_COLS = "*";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView       rvBookings;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout       shimmerLayout, emptyStateLayout;
    private TextView           tvBookingCount;
    private TextInputEditText  etSearch;
    private Chip               chipAll, chipPending, chipAccepted,
                               chipCompleted, chipCancelled;

    // Data
    private AdminBookingsAdapter        adapter;
    private final List<Booking>         allBookings      = new ArrayList<>();
    private final List<Booking>         filteredBookings = new ArrayList<>();
    private String                      activeFilter     = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_bookings);

        bindViews();
        setupRecyclerView();
        setupSearch();
        setupFilterChips();
        setupSwipeRefresh();
        loadBookings();

        ImageButton btnBack    = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadBookings());
    }

    private void bindViews() {
        rvBookings       = findViewById(R.id.rvBookings);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        shimmerLayout    = findViewById(R.id.shimmerLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvBookingCount   = findViewById(R.id.tvBookingCount);
        etSearch         = findViewById(R.id.etSearch);
        chipAll          = findViewById(R.id.chipAll);
        chipPending      = findViewById(R.id.chipPending);
        chipAccepted     = findViewById(R.id.chipAccepted);
        chipCompleted    = findViewById(R.id.chipCompleted);
        chipCancelled    = findViewById(R.id.chipCancelled);
    }

    private void setupRecyclerView() {
        adapter = new AdminBookingsAdapter(this);
        adapter.setOnItemClickListener(booking ->
                Toast.makeText(this,
                        "Booking: " + booking.serviceName
                        + " — " + booking.status,
                        Toast.LENGTH_SHORT).show());
        rvBookings.setLayoutManager(new LinearLayoutManager(this));
        rvBookings.setAdapter(adapter);
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        View.OnClickListener chipClick = v -> {
            // Reset all chip colours to inactive
            setChipInactive(chipAll);
            setChipInactive(chipPending);
            setChipInactive(chipAccepted);
            setChipInactive(chipCompleted);
            setChipInactive(chipCancelled);

            // Activate tapped chip
            setChipActive((Chip) v);

            int id = v.getId();
            if      (id == R.id.chipAll)       activeFilter = "all";
            else if (id == R.id.chipPending)   activeFilter = "pending";
            else if (id == R.id.chipAccepted)  activeFilter = "accepted";
            else if (id == R.id.chipCompleted) activeFilter = "completed";
            else if (id == R.id.chipCancelled) activeFilter = "cancelled";
            applyFilters();
        };
        if (chipAll      != null) chipAll.setOnClickListener(chipClick);
        if (chipPending  != null) chipPending.setOnClickListener(chipClick);
        if (chipAccepted != null) chipAccepted.setOnClickListener(chipClick);
        if (chipCompleted!= null) chipCompleted.setOnClickListener(chipClick);
        if (chipCancelled!= null) chipCancelled.setOnClickListener(chipClick);
    }

    private void setChipActive(Chip chip) {
        if (chip == null) return;
        chip.setChipBackgroundColorResource(R.color.primary);
        chip.setTextColor(0xFFFFFFFF);
    }

    private void setChipInactive(Chip chip) {
        if (chip == null) return;
        chip.setChipBackgroundColorResource(android.R.color.darker_gray);
        chip.setTextColor(0xFFFFFFFF);
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadBookings);
    }

    // ── LOAD BOOKINGS ─────────────────────────────────────────────────────
    private void loadBookings() {
        showShimmer(true);
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);

        // newest first, only existing columns
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/bookings"
                + "?select=" + SELECT_COLS
                + "&order=created_at.desc";

        Request req = new Request.Builder().url(url).get()
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> {
                    showShimmer(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response)
                    throws IOException {
                int    code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                Log.d(TAG, "loadBookings HTTP " + code);
                main.post(() -> {
                    showShimmer(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    if (code == 200) {
                        parseBookings(body);
                    } else {
                        showError("Failed to load bookings (HTTP " + code + ")");
                        Log.e(TAG, "Body: " + body);
                    }
                });
            }
        });
    }

    private void parseBookings(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            allBookings.clear();
            for (int i = 0; i < arr.length(); i++) {
                Booking b = Booking.fromJson(arr.getJSONObject(i));
                if (b != null) allBookings.add(b);
            }
            applyFilters();
        } catch (Exception e) {
            Log.e(TAG, "parseBookings: " + e.getMessage());
        }
    }

    // ── FILTER ────────────────────────────────────────────────────────────
    private void applyFilters() {
        String query = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase(Locale.US) : "";

        filteredBookings.clear();
        for (Booking b : allBookings) {
            // Status filter
            boolean matchStatus = activeFilter.equals("all")
                    || (b.status != null && b.status.equalsIgnoreCase(activeFilter));

            // Search filter
            boolean matchSearch = query.isEmpty()
                    || contains(b.customerName, query)
                    || contains(b.customerPhone, query)
                    || contains(b.serviceName, query)
                    || contains(b.providerName, query)
                    || contains(b.category, query)
                    || contains(b.address, query);

            if (matchStatus && matchSearch) filteredBookings.add(b);
        }

        adapter.setData(filteredBookings);
        updateCountLabel();

        boolean empty = filteredBookings.isEmpty();
        if (rvBookings      != null) rvBookings.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyStateLayout!= null) emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private boolean contains(String field, String query) {
        return field != null && field.toLowerCase(Locale.US).contains(query);
    }

    private void updateCountLabel() {
        if (tvBookingCount == null) return;
        int total    = allBookings.size();
        int filtered = filteredBookings.size();
        if (activeFilter.equals("all") && filtered == total) {
            tvBookingCount.setText(total + " booking" + (total == 1 ? "" : "s"));
        } else {
            tvBookingCount.setText(filtered + " of " + total + " bookings");
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────
    private void showShimmer(boolean show) {
        if (shimmerLayout != null) shimmerLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvBookings    != null) rvBookings.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showError(String msg) {
        Toast.makeText(this, "❌ " + msg, Toast.LENGTH_LONG).show();
        Log.e(TAG, msg);
    }
}

