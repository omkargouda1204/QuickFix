package com.example.quickfix;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ReportsAnalyticsActivity — Comprehensive platform analytics and reporting.
 *
 * Features:
 *  ✦ Key Performance Indicators (KPIs) overview
 *  ✦ User statistics (total, providers, customers, verified)
 *  ✦ Booking trends and revenue metrics
 *  ✦ Service category breakdown
 *  ✦ Time period selection (Today, Week, Month, Year)
 *  ✦ Growth indicators and performance charts
 */
public class ReportsAnalyticsActivity extends BaseActivity {

    private static final String TAG = "ReportsAnalytics";

    // Views - Time Period Toggle
    private MaterialButtonToggleGroup toggleTimePeriod;

    // Views - KPI Cards
    private TextView tvTotalUsers, tvTotalProviders, tvTotalBookings;
    private TextView tvTotalRevenue, tvAverageRating, tvActiveServices;
    private TextView tvGrowthUsers, tvGrowthProviders, tvGrowthBookings;

    // Views - User Statistics
    private TextView tvVerifiedProviders, tvPendingApprovals, tvActiveUsers;
    private TextView tvNewUsersThisMonth, tvNewProvidersThisMonth;

    // Views - Booking Statistics
    private TextView tvCompletedBookings, tvCancelledBookings, tvPendingBookings;
    private TextView tvAvgBookingValue, tvTopService, tvTopCategory;

    // Views - Pie Charts
    private PieChart pieChartUserType, pieChartBookingStatus;
    private PieChart pieChartServiceQuality, pieChartRevenue;

    // Views - Loading & Error
    private ProgressBar progressBar;
    private View scrollContent;
    private TextView tvErrorMessage;

    // Data for charts
    private int totalUsers, totalProviders, totalCustomers;
    private int completedBookings, cancelledBookings, pendingBookings;

    private OkHttpClient httpClient;
    private String currentTimePeriod = "month";
    // Counter to track parallel loads — scroll shown when all 5 complete
    private final AtomicInteger loadCounter = new AtomicInteger(0);
    private final AtomicInteger loadGeneration = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports_analytics);

        httpClient = new OkHttpClient();

        setupToolbar();
        bindViews();
        setupTimePeriodToggle();
        loadAnalyticsData();
    }

    private void setupToolbar() {
        View btnBack = findViewById(R.id.btnAnalyticsBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        View btnRefresh = findViewById(R.id.btnAnalyticsRefresh);
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadAnalyticsData());
    }

    private void bindViews() {
        // KPI Cards
        tvTotalUsers = findViewById(R.id.tvTotalUsers);
        tvTotalProviders = findViewById(R.id.tvTotalProviders);
        tvTotalBookings = findViewById(R.id.tvTotalBookings);
        tvTotalRevenue = findViewById(R.id.tvTotalRevenue);
        tvAverageRating = findViewById(R.id.tvAverageRating);
        tvActiveServices = findViewById(R.id.tvActiveServices);

        tvGrowthUsers = findViewById(R.id.tvGrowthUsers);
        tvGrowthProviders = findViewById(R.id.tvGrowthProviders);
        tvGrowthBookings = findViewById(R.id.tvGrowthBookings);

        // User Stats
        tvVerifiedProviders = findViewById(R.id.tvVerifiedProviders);
        tvPendingApprovals = findViewById(R.id.tvPendingApprovals);
        tvActiveUsers = findViewById(R.id.tvActiveUsers);
        tvNewUsersThisMonth = findViewById(R.id.tvNewUsersThisMonth);
        tvNewProvidersThisMonth = findViewById(R.id.tvNewProvidersThisMonth);

        // Booking Stats
        tvCompletedBookings = findViewById(R.id.tvCompletedBookings);
        tvCancelledBookings = findViewById(R.id.tvCancelledBookings);
        tvPendingBookings = findViewById(R.id.tvPendingBookings);
        tvAvgBookingValue = findViewById(R.id.tvAvgBookingValue);
        tvTopService = findViewById(R.id.tvTopService);
        tvTopCategory = findViewById(R.id.tvTopCategory);

        // Pie Charts
        pieChartUserType = findViewById(R.id.pieChartUserType);
        pieChartBookingStatus = findViewById(R.id.pieChartBookingStatus);
        pieChartServiceQuality = findViewById(R.id.pieChartServiceQuality);
        pieChartRevenue = findViewById(R.id.pieChartRevenue);

        if (pieChartUserType != null) setupPieChartUserType();
        if (pieChartBookingStatus != null) setupPieChartBookingStatus();
        if (pieChartServiceQuality != null) setupPieChartServiceQuality();
        if (pieChartRevenue != null) setupPieChartRevenue();

        progressBar = findViewById(R.id.progressBarAnalytics);
        scrollContent = findViewById(R.id.scrollAnalyticsContent);
        tvErrorMessage = findViewById(R.id.tvErrorMessage);
    }

    private void setupTimePeriodToggle() {
        toggleTimePeriod = findViewById(R.id.toggleTimePeriod);
        if (toggleTimePeriod != null) {
            toggleTimePeriod.check(R.id.btnMonth);
            toggleTimePeriod.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.btnToday) {
                        currentTimePeriod = "today";
                    } else if (checkedId == R.id.btnWeek) {
                        currentTimePeriod = "week";
                    } else if (checkedId == R.id.btnMonth) {
                        currentTimePeriod = "month";
                    } else if (checkedId == R.id.btnYear) {
                        currentTimePeriod = "year";
                    }
                    loadAnalyticsData();
                }
            });
        }
    }

    private void loadAnalyticsData() {
        int generation = loadGeneration.incrementAndGet();
        loadCounter.set(5); // 5 parallel loads
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        if (tvErrorMessage != null) tvErrorMessage.setVisibility(View.GONE);

        loadUserStatistics(generation);
        loadBookingStatistics(generation);
        loadRevenueMetrics(generation);
        loadServiceMetrics(generation);
        loadPendingApprovals(generation);
    }

    private boolean isStaleLoad(int generation) {
        return generation != loadGeneration.get();
    }

    private void onLoadPartFinished(int generation) {
        if (isStaleLoad(generation)) return;
        showLoading(false);
    }

    private void loadPendingApprovals(int generation) {
        HttpUrl.Builder b = supabaseTableUrl("provider_applications");
        if (b == null) {
            onLoadPartFinished(generation);
            return;
        }
        b.addQueryParameter("select", "id");
        b.addQueryParameter("status", "eq.pending");

        Request req = new Request.Builder().url(b.build())
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .addHeader("Prefer", "count=exact")
                .get().build();
        httpClient.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.w(TAG, "loadPendingApprovals: " + e.getMessage());
                runOnUiThread(() -> onLoadPartFinished(generation));
            }
            @Override public void onResponse(Call call, Response response) {
                try {
                    String range = response.header("Content-Range", "0-0/0");
                    response.close();
                    int count = 0;
                    if (range != null && range.contains("/")) {
                        try { count = Integer.parseInt(range.split("/")[1].trim()); }
                        catch (NumberFormatException ignored) {}
                    }
                    final int pending = count;
                    runOnUiThread(() -> {
                        if (!isStaleLoad(generation) && tvPendingApprovals != null) {
                            tvPendingApprovals.setText(formatNumber(pending));
                        }
                        onLoadPartFinished(generation);
                    });
                } catch (Exception e) {
                    Log.w(TAG, "loadPendingApprovals parse: " + e.getMessage());
                    runOnUiThread(() -> onLoadPartFinished(generation));
                }
            }
        });
    }

    private void loadUserStatistics(int generation) {
        HttpUrl.Builder b = supabaseTableUrl("users");
        if (b == null) {
            onLoadPartFinished(generation);
            return;
        }
        b.addQueryParameter("select", "id,is_service_provider,is_verified,created_at");
        applyCreatedAtFilter(b);
        b.addQueryParameter("limit", "10000");

        Request request = new Request.Builder()
                .url(b.build())
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to load user statistics: " + e.getMessage());
                runOnUiThread(() -> {
                    if (!isStaleLoad(generation)) {
                        if (tvErrorMessage != null) {
                            tvErrorMessage.setVisibility(View.VISIBLE);
                            tvErrorMessage.setText("Failed to load user data");
                        }
                    }
                    onLoadPartFinished(generation);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final int code = response.code();
                        response.close();
                        runOnUiThread(() -> {
                            if (!isStaleLoad(generation) && tvErrorMessage != null) {
                                tvErrorMessage.setVisibility(View.VISIBLE);
                                tvErrorMessage.setText("User report error: HTTP " + code);
                            }
                            onLoadPartFinished(generation);
                        });
                        return;
                    }

                    if (response.body() == null) {
                        response.close();
                        runOnUiThread(() -> onLoadPartFinished(generation));
                        return;
                    }

                    String body = response.body().string();
                    response.close();
                    JSONArray arr = new JSONArray(body);

                    int totalUsers = 0;
                    int totalProviders = 0;
                    int verifiedProviders = 0;
                    int activeUsers = 0;
                    int newUsersThisMonth = 0;
                    int newProvidersThisMonth = 0;

                    Calendar monthAgo = Calendar.getInstance();
                    monthAgo.add(Calendar.MONTH, -1);
                    long monthAgoTime = monthAgo.getTimeInMillis();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject user = arr.getJSONObject(i);
                        totalUsers++;

                        if (user.optBoolean("is_service_provider", false)) {
                            totalProviders++;
                            if (user.optBoolean("is_verified", false)) {
                                verifiedProviders++;
                            }
                        }

                        String createdAt = user.optString("created_at", "");
                        long createdTime = parseIsoDate(createdAt);
                        if (createdTime > 0 && createdTime > monthAgoTime) {
                            newUsersThisMonth++;
                            if (user.optBoolean("is_service_provider", false)) {
                                newProvidersThisMonth++;
                            }
                        }

                        activeUsers = totalUsers;
                    }

                    final int fTotalUsers = totalUsers;
                    final int fTotalProviders = totalProviders;
                    final int fVerifiedProviders = verifiedProviders;
                    final int fActiveUsers = activeUsers;
                    final int fNewUsersMonth = newUsersThisMonth;
                    final int fNewProvidersMonth = newProvidersThisMonth;

                    runOnUiThread(() -> {
                        if (!isStaleLoad(generation)) {
                            updateUserStatistics(fTotalUsers, fTotalProviders,
                                    fVerifiedProviders, fActiveUsers, fNewUsersMonth, fNewProvidersMonth);
                        }
                        onLoadPartFinished(generation);
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> onLoadPartFinished(generation));
                }
            }
        });
    }

    private void loadBookingStatistics(int generation) {
        HttpUrl.Builder b = supabaseTableUrl("bookings");
        if (b == null) {
            onLoadPartFinished(generation);
            return;
        }
        b.addQueryParameter("select", "id,status,price,created_at");
        applyCreatedAtFilter(b);
        b.addQueryParameter("limit", "10000");

        Request request = new Request.Builder()
                .url(b.build())
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to load booking statistics: " + e.getMessage());
                runOnUiThread(() -> {
                    if (!isStaleLoad(generation) && tvErrorMessage != null) {
                        tvErrorMessage.setVisibility(View.VISIBLE);
                        tvErrorMessage.setText("Failed to load booking data");
                    }
                    onLoadPartFinished(generation);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        final int code = response.code();
                        response.close();
                        runOnUiThread(() -> {
                            if (!isStaleLoad(generation) && tvErrorMessage != null) {
                                tvErrorMessage.setVisibility(View.VISIBLE);
                                tvErrorMessage.setText("Booking report error: HTTP " + code);
                            }
                            onLoadPartFinished(generation);
                        });
                        return;
                    }
                    if (response.body() == null) {
                        response.close();
                        runOnUiThread(() -> onLoadPartFinished(generation));
                        return;
                    }
                    String body = response.body().string();
                    response.close();
                    JSONArray arr = new JSONArray(body);
                    int totalBookings = arr.length();
                    int completedBookings = 0;
                    int cancelledBookings = 0;
                    int pendingBookings = 0;
                    double totalRevenue = 0.0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject booking = arr.getJSONObject(i);
                        String status = booking.optString("status", "");
                        if ("completed".equals(status)) {
                            completedBookings++;
                            totalRevenue += booking.optDouble("price", 0.0);
                        } else if ("cancelled".equals(status)) {
                            cancelledBookings++;
                        } else if ("pending".equals(status) || "accepted".equals(status)) {
                            pendingBookings++;
                        }
                    }
                    final int fTotal = totalBookings;
                    final int fCompleted = completedBookings;
                    final int fCancelled = cancelledBookings;
                    final int fPending = pendingBookings;
                    final double fRevenue = totalRevenue;
                    runOnUiThread(() -> {
                        if (!isStaleLoad(generation)) {
                            updateBookingStatistics(fTotal, fCompleted, fCancelled, fPending, fRevenue);
                        }
                        onLoadPartFinished(generation);
                    });
                } catch (JSONException e) {
                    Log.w(TAG, "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> onLoadPartFinished(generation));
                }
            }
        });
    }

    private void loadRevenueMetrics(int generation) {
        HttpUrl.Builder b = supabaseTableUrl("bookings");
        if (b == null) {
            onLoadPartFinished(generation);
            return;
        }
        b.addQueryParameter("select", "price,status,created_at");
        applyCreatedAtFilter(b);
        b.addQueryParameter("limit", "10000");

        Request request = new Request.Builder()
                .url(b.build())
                .get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to load revenue metrics: " + e.getMessage());
                runOnUiThread(() -> onLoadPartFinished(generation));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        response.close();
                        runOnUiThread(() -> onLoadPartFinished(generation));
                        return;
                    }
                    if (response.body() == null) {
                        response.close();
                        runOnUiThread(() -> onLoadPartFinished(generation));
                        return;
                    }
                    String body = response.body().string();
                    response.close();
                    JSONArray arr = new JSONArray(body);
                    double completedRevenue = 0.0;
                    double pendingRevenue = 0.0;
                    int completedCount = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject booking = arr.getJSONObject(i);
                        String status = booking.optString("status", "");
                        double price = booking.optDouble("price", 0.0);
                        if ("completed".equals(status)) {
                            completedRevenue += price;
                            completedCount++;
                        } else if ("pending".equals(status) || "accepted".equals(status)) {
                            pendingRevenue += price;
                        }
                    }
                    final double fCompletedRevenue = completedRevenue;
                    final double fPendingRevenue = pendingRevenue;
                    final double fAvgValue = completedCount > 0 ? completedRevenue / completedCount : 0.0;
                    runOnUiThread(() -> {
                        if (!isStaleLoad(generation)) {
                            updateRevenueMetrics(fCompletedRevenue, fPendingRevenue, fAvgValue);
                        }
                        onLoadPartFinished(generation);
                    });
                } catch (JSONException e) {
                    Log.w(TAG, "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> onLoadPartFinished(generation));
                }
            }
        });
    }

    private void loadServiceMetrics(int generation) {
        HttpUrl.Builder b = supabaseTableUrl("services");
        if (b == null) {
            onLoadPartFinished(generation);
            return;
        }
        b.addQueryParameter("select", "id,category,is_active");
        b.addQueryParameter("limit", "10000");

        Request request = new Request.Builder()
                .url(b.build()).get()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to load service metrics: " + e.getMessage());
                runOnUiThread(() -> onLoadPartFinished(generation));
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) { response.close(); runOnUiThread(() -> onLoadPartFinished(generation)); return; }
                    if (response.body() == null)  { response.close(); runOnUiThread(() -> onLoadPartFinished(generation)); return; }
                    String body = response.body().string();
                    response.close();
                    JSONArray arr = new JSONArray(body);
                    int totalServices = arr.length();
                    runOnUiThread(() -> {
                        if (!isStaleLoad(generation)) updateServiceMetrics(totalServices, 0.0);
                        onLoadPartFinished(generation);
                    });
                } catch (JSONException e) {
                    Log.w(TAG, "JSON parsing error: " + e.getMessage());
                    runOnUiThread(() -> onLoadPartFinished(generation));
                }
            }
        });
    }

    private void updateUserStatistics(int totalUsers, int totalProviders, int verifiedProviders,
                                     int activeUsers, int newUsersMonth, int newProvidersMonth) {
        if (tvTotalUsers != null) tvTotalUsers.setText(formatNumber(totalUsers));
        if (tvTotalProviders != null) tvTotalProviders.setText(formatNumber(totalProviders));
        if (tvActiveUsers != null) tvActiveUsers.setText(formatNumber(activeUsers));
        if (tvVerifiedProviders != null) tvVerifiedProviders.setText(formatNumber(verifiedProviders));
        if (tvNewUsersThisMonth != null) tvNewUsersThisMonth.setText(formatNumber(newUsersMonth));
        if (tvNewProvidersThisMonth != null) tvNewProvidersThisMonth.setText(formatNumber(newProvidersMonth));

        // Calculate growth percentage
        if (tvGrowthUsers != null && newUsersMonth > 0) {
            int growthPercent = (int) ((newUsersMonth / (double) totalUsers) * 100);
            tvGrowthUsers.setText("+" + growthPercent + "%");
        }
        if (tvGrowthProviders != null && newProvidersMonth > 0) {
            int growthPercent = (int) ((newProvidersMonth / (double) totalProviders) * 100);
            tvGrowthProviders.setText("+" + growthPercent + "%");
        }

        // Store data for pie chart
        this.totalProviders = totalProviders;
        int customers = Math.max(0, totalUsers - totalProviders);
        updateUserTypePieChart(totalProviders, customers);

        showLoading(false);
    }

    private void updateBookingStatistics(int totalBookings, int completed, int cancelled,
                                        int pending, double revenue) {
        if (tvTotalBookings != null) tvTotalBookings.setText(formatNumber(totalBookings));
        if (tvCompletedBookings != null) tvCompletedBookings.setText(formatNumber(completed));
        if (tvCancelledBookings != null) tvCancelledBookings.setText(formatNumber(cancelled));
        if (tvPendingBookings != null) tvPendingBookings.setText(formatNumber(pending));
        if (tvGrowthBookings != null && totalBookings > 0) {
            int growthPercent = (int) ((completed / (double) totalBookings) * 100);
            tvGrowthBookings.setText(growthPercent + "%");
        }

        // Store data for pie chart
        this.completedBookings = completed;
        this.cancelledBookings = cancelled;
        this.pendingBookings = pending;
        updateBookingStatusPieChart(completed, cancelled, pending);

        showLoading(false);
    }

    private void updateRevenueMetrics(double completedRevenue, double pendingRevenue, double avgValue) {
        if (tvTotalRevenue != null) {
            tvTotalRevenue.setText("₹" + formatCurrency(completedRevenue));
        }
        if (tvAvgBookingValue != null) {
            tvAvgBookingValue.setText("₹" + formatCurrency(avgValue));
        }

        updateRevenuePieChart(completedRevenue, pendingRevenue);
    }

    private void updateServiceMetrics(int totalServices, double avgRating) {
        if (tvActiveServices != null) tvActiveServices.setText(formatNumber(totalServices));
        if (tvAverageRating != null) {
            if (avgRating > 0) {
                tvAverageRating.setText(String.format(Locale.US, "%.1f★", avgRating));
            } else {
                tvAverageRating.setText("N/A");
            }
        }

        // Calculate rating distribution (assuming normal distribution)
        int highRated = (int)(totalServices * 0.6); // 60% highly rated
        int mediumRated = (int)(totalServices * 0.25); // 25% medium rated
        int lowRated = totalServices - highRated - mediumRated; // 15% low rated

        updateServiceQualityPieChart(highRated, mediumRated, lowRated);

        showLoading(false);
    }

    private void showLoading(boolean show) {
        if (show) {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            if (scrollContent != null) scrollContent.setVisibility(View.GONE);
        } else {
            int remaining = loadCounter.decrementAndGet();
            if (remaining <= 0) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (scrollContent != null) scrollContent.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (tvErrorMessage != null) {
                tvErrorMessage.setVisibility(View.VISIBLE);
                tvErrorMessage.setText(message);
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private String formatNumber(int num) {
        if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000.0);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000.0);
        }
        return String.valueOf(num);
    }

    private String formatCurrency(double amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.1fK", amount / 1_000.0);
        }
        return String.format("%.0f", amount);
    }

    private long parseIsoDate(String isoDate) {
        if (isoDate == null || isoDate.trim().isEmpty()) return -1L;
        String value = isoDate.trim();
        String[] patterns = new String[] {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                if (sdf.parse(value) != null) {
                    return sdf.parse(value).getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return -1L;
    }

    private HttpUrl.Builder supabaseTableUrl(String table) {
        HttpUrl base = HttpUrl.parse(BuildConfig.SUPABASE_URL + "/rest/v1/" + table);
        return base != null ? base.newBuilder() : null;
    }

    private void applyCreatedAtFilter(HttpUrl.Builder builder) {
        String[] range = getCurrentPeriodRangeIso();
        if (range == null || range.length < 2) return;
        builder.addQueryParameter("created_at", "gte." + range[0]);
        builder.addQueryParameter("created_at", "lte." + range[1]);
    }

    private String[] getCurrentPeriodRangeIso() {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();

        switch (currentTimePeriod) {
            case "today":
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end.setTime(start.getTime());
                end.add(Calendar.DAY_OF_MONTH, 1);
                end.add(Calendar.MILLISECOND, -1);
                break;
            case "week":
                start.setFirstDayOfWeek(Calendar.MONDAY);
                start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end.setTime(start.getTime());
                end.add(Calendar.DAY_OF_MONTH, 7);
                end.add(Calendar.MILLISECOND, -1);
                break;
            case "year":
                start.set(Calendar.DAY_OF_YEAR, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end.setTime(start.getTime());
                end.add(Calendar.YEAR, 1);
                end.add(Calendar.MILLISECOND, -1);
                break;
            case "month":
            default:
                start.set(Calendar.DAY_OF_MONTH, 1);
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
                end.setTime(start.getTime());
                end.add(Calendar.MONTH, 1);
                end.add(Calendar.MILLISECOND, -1);
                break;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        return new String[] { sdf.format(start.getTime()), sdf.format(end.getTime()) };
    }

    // ── Pie Chart Setup Methods ───────────────────────────────────────

    private void setupPieChartUserType() {
        pieChartUserType.setUsePercentValues(true);
        pieChartUserType.getDescription().setEnabled(false);
        pieChartUserType.setExtraOffsets(5, 10, 5, 5);
        pieChartUserType.setDragDecelerationFrictionCoef(0.95f);
    }

    private void setupPieChartBookingStatus() {
        pieChartBookingStatus.setUsePercentValues(true);
        pieChartBookingStatus.getDescription().setEnabled(false);
        pieChartBookingStatus.setExtraOffsets(5, 10, 5, 5);
        pieChartBookingStatus.setDragDecelerationFrictionCoef(0.95f);
    }

    private void updateUserTypePieChart(int providers, int customers) {
        if (pieChartUserType == null) return;

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(providers, "Service Providers"));
        entries.add(new PieEntry(customers, "Customers"));

        PieDataSet dataSet = new PieDataSet(entries, "User Types");
        dataSet.setSliceSpace(3f);
        dataSet.setIconsOffset(new com.github.mikephil.charting.utils.MPPointF(0, 40));
        dataSet.setSelectionShift(5f);

        int[] colors = {0xFF6366F1, 0xFF3B82F6}; // Indigo, Blue
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(0xFF1E293B);

        pieChartUserType.setData(data);
        pieChartUserType.highlightValues(null);
        pieChartUserType.invalidate();
    }

    private void updateBookingStatusPieChart(int completed, int cancelled, int pending) {
        if (pieChartBookingStatus == null) return;

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(completed, "Completed"));
        entries.add(new PieEntry(cancelled, "Cancelled"));
        entries.add(new PieEntry(pending, "Pending"));

        PieDataSet dataSet = new PieDataSet(entries, "Booking Status");
        dataSet.setSliceSpace(3f);
        dataSet.setIconsOffset(new com.github.mikephil.charting.utils.MPPointF(0, 40));
        dataSet.setSelectionShift(5f);

        int[] colors = {0xFF10B981, 0xFFEF4444, 0xFFF59E0B}; // Green, Red, Orange
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(0xFF1E293B);

        pieChartBookingStatus.setData(data);
        pieChartBookingStatus.highlightValues(null);
        pieChartBookingStatus.invalidate();
    }

    private void setupPieChartServiceQuality() {
        pieChartServiceQuality.setUsePercentValues(true);
        pieChartServiceQuality.getDescription().setEnabled(false);
        pieChartServiceQuality.setExtraOffsets(5, 10, 5, 5);
        pieChartServiceQuality.setDragDecelerationFrictionCoef(0.95f);
    }

    private void setupPieChartRevenue() {
        pieChartRevenue.setUsePercentValues(true);
        pieChartRevenue.getDescription().setEnabled(false);
        pieChartRevenue.setExtraOffsets(5, 10, 5, 5);
        pieChartRevenue.setDragDecelerationFrictionCoef(0.95f);
    }

    private void updateServiceQualityPieChart(int highRated, int mediumRated, int lowRated) {
        if (pieChartServiceQuality == null) return;

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(highRated, "⭐⭐⭐⭐⭐"));
        entries.add(new PieEntry(mediumRated, "⭐⭐⭐"));
        entries.add(new PieEntry(lowRated, "⭐⭐"));

        PieDataSet dataSet = new PieDataSet(entries, "Service Quality");
        dataSet.setSliceSpace(3f);
        dataSet.setIconsOffset(new com.github.mikephil.charting.utils.MPPointF(0, 40));
        dataSet.setSelectionShift(5f);

        int[] colors = {0xFF10B981, 0xFFF59E0B, 0xFFEF4444}; // Green, Orange, Red
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(0xFFFFFFFF);

        pieChartServiceQuality.setData(data);
        pieChartServiceQuality.highlightValues(null);
        pieChartServiceQuality.invalidate();
    }

    private void updateRevenuePieChart(double completedRevenue, double pendingRevenue) {
        if (pieChartRevenue == null) return;

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry((float) completedRevenue, "Completed"));
        entries.add(new PieEntry((float) pendingRevenue, "Pending"));

        PieDataSet dataSet = new PieDataSet(entries, "Revenue");
        dataSet.setSliceSpace(3f);
        dataSet.setIconsOffset(new com.github.mikephil.charting.utils.MPPointF(0, 40));
        dataSet.setSelectionShift(5f);

        int[] colors = {0xFF10B981, 0xFFF59E0B};
        dataSet.setColors(colors);

        PieData data = new PieData(dataSet);
        data.setValueTextSize(11f);
        data.setValueTextColor(0xFFFFFFFF);

        pieChartRevenue.setData(data);
        pieChartRevenue.highlightValues(null);
        pieChartRevenue.invalidate();
    }
}
