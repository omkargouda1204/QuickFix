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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.EarningsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * EarningsActivity — Provider's earnings and transaction history
 */
public class EarningsActivity extends BaseActivity {

    private static final String TAG = "EarningsActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private TextView tvTotalEarnings, tvMonthEarnings, tvCompletedJobs;
    private RecyclerView recyclerView;
    private EarningsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState;

    private FirebaseAuth mAuth;
    private String providerFirebaseUid;
    private String providerProfileId;
    private List<Booking> completedBookings = new ArrayList<>();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_earnings);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        providerFirebaseUid = user.getUid();

        setupToolbar();
        initViews();
        setupRecyclerView();
        loadEarnings();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Earnings");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tvTotalEarnings = findViewById(R.id.tvTotalEarnings);
        tvMonthEarnings = findViewById(R.id.tvMonthEarnings);
        tvCompletedJobs = findViewById(R.id.tvCompletedJobs);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);

        swipeRefresh.setOnRefreshListener(this::loadEarnings);
    }

    private void setupRecyclerView() {
        adapter = new EarningsAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadEarnings() {
        showLoading(true);

        if (providerFirebaseUid == null || providerFirebaseUid.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Provider ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        resolveProviderIdAndLoadEarnings();
    }

    private void resolveProviderIdAndLoadEarnings() {
        String url = SUPABASE_URL + "/rest/v1/provider_profiles"
                + "?firebase_uid=eq." + encodeValue(providerFirebaseUid)
                + "&select=id"
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
                Log.w(TAG, "resolveProviderId failed, falling back to firebase uid: " + e.getMessage());
                providerProfileId = "";
                loadEarningsByProviderId();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                providerProfileId = "";
                if (code == 200) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            providerProfileId = arr.getJSONObject(0).optString("id", "").trim();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to parse provider profile id: " + e.getMessage());
                    }
                }

                loadEarningsByProviderId();
            }
        });
    }

    private void loadEarningsByProviderId() {
        String encodedUid = encodeValue(providerFirebaseUid);
        
        // Use select=* or ensure all columns are correct to avoid HTTP 400 (Invalid Column)
        String url = SUPABASE_URL + "/rest/v1/bookings"
                + "?select=*"
                + "&order=created_at.desc";

        if (providerProfileId != null && !providerProfileId.isEmpty()) {
            String encodedProfileId = encodeValue(providerProfileId);
            url += "&provider_id=eq." + encodedProfileId;
        } else {
            url += "&provider_id=eq." + encodedUid;
        }

        Log.d(TAG, "loadEarningsByProviderId URL: " + url);

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
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(EarningsActivity.this,
                            "Failed to load earnings: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                Log.d(TAG, "loadEarningsByProviderId response: HTTP " + code + " | Body: " + body);

                mainHandler.post(() -> {
                    showLoading(false);
                    if (code == 200) {
                        parseEarnings(body);
                    } else {
                        Toast.makeText(EarningsActivity.this,
                                "Error loading earnings (HTTP " + code + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void parseEarnings(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            completedBookings.clear();

            double totalEarnings = 0;
            double monthEarnings = 0;
            int completedCount = 0;

            Calendar calendar = Calendar.getInstance();
            int currentMonth = calendar.get(Calendar.MONTH);
            int currentYear = calendar.get(Calendar.YEAR);

            for (int i = 0; i < arr.length(); i++) {
                Booking booking = Booking.fromJson(arr.getJSONObject(i));
                if (booking == null) continue;

                if (isCancelled(booking)) {
                    continue;
                }

                double amount = booking.finalAmount > 0 ? booking.finalAmount
                        : (booking.paymentAmount > 0 ? booking.paymentAmount : booking.price);
                booking.price = amount;

                completedBookings.add(booking);

                if (isSuccessful(booking)) {
                    totalEarnings += amount;
                    completedCount++;

                    String dateToCheck = booking.completedAt;
                    if (dateToCheck == null || dateToCheck.isEmpty()) {
                        dateToCheck = booking.createdAt;
                    }

                    if (dateToCheck != null && !dateToCheck.isEmpty() && dateToCheck.length() >= 10) {
                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            Calendar bookingCal = Calendar.getInstance();
                            bookingCal.setTime(sdf.parse(dateToCheck.substring(0, 10)));

                            if (bookingCal.get(Calendar.MONTH) == currentMonth
                                    && bookingCal.get(Calendar.YEAR) == currentYear) {
                                monthEarnings += amount;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Date parse error: " + e.getMessage());
                        }
                    }
                }
            }

            tvTotalEarnings.setText("₹" + String.format(Locale.getDefault(), "%.0f", totalEarnings));
            tvMonthEarnings.setText("₹" + String.format(Locale.getDefault(), "%.0f", monthEarnings));
            tvCompletedJobs.setText(String.valueOf(completedCount));

            adapter.setTransactions(completedBookings);

            if (completedBookings.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            Toast.makeText(this, "Error parsing earnings data", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isSuccessful(Booking booking) {
        String status = normalize(booking.status);
        String payment = normalize(booking.paymentStatus);
        return "paid".equals(status)
                || "completed".equals(status)
                || "done".equals(status)
                || "success".equals(status)
                || "paid".equals(payment)
                || "success".equals(payment);
    }

    private boolean isCancelled(Booking booking) {
        String status = normalize(booking.status);
        String payment = normalize(booking.paymentStatus);
        return "cancelled".equals(status)
                || "canceled".equals(status)
                || "failed".equals(status)
                || "cancelled".equals(payment)
                || "canceled".equals(payment)
                || "failed".equals(payment);
    }

    private boolean hasAmount(Booking booking) {
        if (booking == null) return false;
        return booking.finalAmount > 0 || booking.paymentAmount > 0 || booking.price > 0;
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.US);
    }

    private String encodeValue(String raw) {
        if (raw == null) return "";
        return android.net.Uri.encode(raw.trim());
    }

    private void showLoading(boolean show) {
        if (swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(show);
        } else {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
}
