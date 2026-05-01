package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.UserBookingsAdapter;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.util.Date;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * UserBookingsActivity — Displays bookings for a regular (customer) user.
 * Filters: All | Upcoming | Completed | Cancelled
 */
public class UserBookingsActivity extends BaseActivity
        implements UserBookingsAdapter.BookingActionListener, PaymentResultListener {

    private static final String TAG = "UserBookings";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
    private static final String CREATE_ORDER_FN_URL = BuildConfig.SUPABASE_CREATE_ORDER_FN_URL;
    private static final String OFFER_VALIDATION_FN_URL = BuildConfig.SUPABASE_OFFER_VALIDATION_FN_URL;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final int REVIEW_MAX_CHARS = 500;

    private RecyclerView recyclerView;
    private UserBookingsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage;
    private TextView tvBookingCount;
    private ChipGroup chipGroupFilters;

    private String currentFilter = "all";
    private List<Booking> allBookings = new ArrayList<>();
    private String userId;
    private String userDbId;

    // Use centralized HTTP client for optimized connection pooling
    private final OkHttpClient HTTP = HttpClientProvider.getInstance();
    private final Handler MAIN = new Handler(Looper.getMainLooper());
    private Booking pendingPaymentBooking;
    private String pendingOrderId;
    private String pendingCouponCode = "";
    private double pendingCouponDiscount = 0d;
    private double pendingFinalAmountInr = 0d;
    private boolean shouldReloadOnResume = false;
    private boolean skipNextResumeReload = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_bookings);
        Checkout.preload(getApplicationContext());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        userId = user.getUid();

        bindViews();
        setupRecyclerView();
        setupFilters();
        setupSwipeRefresh();
        loadBookings();
        shouldReloadOnResume = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (skipNextResumeReload) {
            skipNextResumeReload = false;
            return;
        }
        if (shouldReloadOnResume) {
            loadBookings();
        }
    }

    private void bindViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        tvBookingCount = findViewById(R.id.tvBookingCount);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
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
            if (id == R.id.chipAll) currentFilter = "all";
            else if (id == R.id.chipUpcoming) currentFilter = "upcoming";
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

        if (userDbId == null || userDbId.trim().isEmpty()) {
            resolveUserDbIdAndLoad();
            return;
        }

        new Thread(() -> {
            try {
                String uid = android.net.Uri.encode(userId);
                List<Booking> list = new ArrayList<>();

                // Build an OR query to find bookings by multiple possible ids (db id or firebase uid)
                String orClause = "or=(customer_id.eq." + android.net.Uri.encode(userDbId)
                        + ",customer_id.eq." + android.net.Uri.encode(userId) + ")";

                String url = BASE_URL + "/rest/v1/bookings?select=*&" + orClause + "&order=created_at.desc";
                Request.Builder rb = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json");
                
                try (Response r = HTTP.newCall(rb.build()).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "bookings response: HTTP " + r.code() + " | Body length: " + body.length());
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            Booking b = Booking.fromJson(arr.getJSONObject(i));
                            if (b != null) list.add(b);
                        }
                    }
                }

                // Load emergency requests and convert to bookings for unified display
                String emergencyUrl = BASE_URL
                    + "/rest/v1/emergency_requests?user_id=eq." + uid
                    + "&select=*&order=created_at.desc";
                
                Request.Builder rbEmerg = new Request.Builder().url(emergencyUrl)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json");
                
                try (Response rEmerg = HTTP.newCall(rbEmerg.build()).execute()) {
                    String bodyEmerg = rEmerg.body() != null ? rEmerg.body().string() : "[]";
                    Log.d(TAG, "emergency_requests response: HTTP " + rEmerg.code() + " | Body length: " + bodyEmerg.length());
                    if (rEmerg.code() == 200) {
                        JSONArray arrEmerg = new JSONArray(bodyEmerg);
                        for (int i = 0; i < arrEmerg.length(); i++) {
                            JSONObject o = arrEmerg.getJSONObject(i);
                            Booking b = convertEmergencyToBooking(o);
                            if (b != null) list.add(b);
                        }
                    }
                }

                // Ensure provider names are resolved for any bookings missing providerName
                try {
                    resolveMissingProviderNames(list);
                } catch (Exception e) {
                    Log.w(TAG, "resolveMissingProviderNames failed: " + e.getMessage());
                }

                try {
                    attachReviewData(list);
                } catch (Exception reviewErr) {
                    Log.w(TAG, "attachReviewData skipped: " + reviewErr.getMessage());
                }

                allBookings = list;
                MAIN.post(() -> {
                    showLoading(false);
                    applyFilter();
                });
            } catch (Exception e) {
                Log.e(TAG, "loadBookings: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private Booking convertEmergencyToBooking(JSONObject o) {
        try {
            Booking b = new Booking();
            b.id = o.optString("id", "");
            b.customerId = o.optString("user_firebase_uid", "");
            b.providerName = o.optString("assigned_provider_name", "Awaiting Provider");
            b.serviceCategory = o.optString("category", "Emergency");
            b.serviceName = "🚨 " + o.optString("category", "Emergency Service");
            b.status = o.optString("status", "pending");
            b.price = 0;
            b.location = o.optString("location_label", "");
            b.description = o.optString("description", "Emergency request");
            b.createdAt = o.optString("created_at", "");
            b.isEmergency = true;
            b.emergencyRequestId = b.id;
            return b;
        } catch (Exception e) {
            Log.e(TAG, "convertEmergencyToBooking: " + e.getMessage());
            return null;
        }
    }

    private void attachReviewData(List<Booking> bookings) throws Exception {
        if (bookings == null || bookings.isEmpty()) return;

        String uid = userId != null ? userId.trim() : "";
        String dbId = userDbId != null ? userDbId.trim() : "";

        String url = BASE_URL + "/rest/v1/provider_reviews"
                + "?select=id,booking_id,customer_id,rating,comment,updated_at,created_at"
                + "&order=created_at.desc";

        if (!uid.isEmpty() && !dbId.isEmpty() && !uid.equals(dbId)) {
            url += "&or=(customer_id.eq." + android.net.Uri.encode(uid)
                    + ",customer_id.eq." + android.net.Uri.encode(dbId) + ")";
        } else if (!dbId.isEmpty()) {
            url += "&customer_id=eq." + android.net.Uri.encode(dbId);
        } else if (!uid.isEmpty()) {
            url += "&customer_id=eq." + android.net.Uri.encode(uid);
        }

        Request.Builder rb = new Request.Builder().url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json");
        Request req = rb.build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (r.code() != 200) return;
            String body = r.body() != null ? r.body().string() : "[]";
            JSONArray arr = new JSONArray(body);
            Map<String, JSONObject> byBookingId = new HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                String bookingId = clean(row.optString("booking_id", ""));
                if (bookingId.isEmpty() || byBookingId.containsKey(bookingId)) continue;
                byBookingId.put(bookingId, row);
            }

            for (Booking booking : bookings) {
                JSONObject row = byBookingId.get(clean(booking.id));
                if (row == null) continue;
                booking.reviewId = clean(row.optString("id", ""));
                booking.reviewRating = row.optInt("rating", 0);
                booking.reviewComment = clean(row.optString("comment", ""));
                booking.reviewUpdatedAt = clean(row.optString("updated_at", ""));
                if (booking.reviewUpdatedAt.isEmpty()) {
                    booking.reviewUpdatedAt = clean(row.optString("created_at", ""));
                }
            }
        }
    }

    private void resolveUserDbIdAndLoad() {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/rest/v1/users"
                        + "?firebase_uid=eq." + android.net.Uri.encode(userId)
                        + "&select=id&limit=1";
                Request.Builder rb = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json");
                
                try (Response r = HTTP.newCall(rb.build()).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "resolveUserDbId response: HTTP " + r.code() + " | Body length: " + body.length());
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            userDbId = arr.getJSONObject(0).optString("id", "");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "resolveUserDbId: " + e.getMessage());
            }

            MAIN.post(() -> {
                if (userDbId == null || userDbId.trim().isEmpty()) {
                    // Fallback to Firebase UID-only read.
                    userDbId = userId;
                }
                loadBookings();
            });
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
                            || "confirmed".equalsIgnoreCase(b.status)
                            || "price_set".equalsIgnoreCase(b.status)
                            || "paid".equalsIgnoreCase(b.status))
                    .collect(Collectors.toList());
        } else {
            filtered = allBookings.stream()
                    .filter(b -> currentFilter.equalsIgnoreCase(b.status))
                    .collect(Collectors.toList());
        }
        adapter.setBookings(filtered);
        if (tvBookingCount != null) {
            int size = filtered.size();
            tvBookingCount.setText(getString(
                    size == 1 ? R.string.booking_count_singular : R.string.booking_count_plural,
                    size
            ));
        }
        if (tvEmptyMessage != null) {
            if (filtered.isEmpty() && !"all".equals(currentFilter)) {
                tvEmptyMessage.setText("No " + currentFilter + " bookings found");
            } else {
                tvEmptyMessage.setText("No bookings found");
            }
        }
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // Fetch provider profiles in batch to fill missing provider names
    private void resolveMissingProviderNames(List<Booking> bookings) throws Exception {
        if (bookings == null || bookings.isEmpty()) return;
        // Collect provider ids which are non-empty and where providerName is missing or placeholder
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Booking b : bookings) {
            if (b == null) continue;
            String pid = b.providerId == null ? "" : b.providerId.trim();
            String pname = b.providerName == null ? "" : b.providerName.trim();
            if (pid.isEmpty()) continue;
            if (pname.isEmpty() || "Awaiting Acceptance".equalsIgnoreCase(pname) || "null".equalsIgnoreCase(pname)) {
                ids.add(pid);
            }
        }
        if (ids.isEmpty()) return;

        // Build id.in clause
        StringBuilder inClause = new StringBuilder();
        inClause.append("id=in.({ids})");
        // Use comma separated and url-encode
        String joined = String.join(",", ids);
        String url = BASE_URL + "/rest/v1/provider_profiles?select=id,business_name,firebase_uid&limit=" + ids.size()
            + "&id=in.(" + joined + ")";

        Request.Builder rb = new Request.Builder().url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json");

        try (Response r = HTTP.newCall(rb.build()).execute()) {
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
                if (!pid.isEmpty() && (b.providerName == null || b.providerName.trim().isEmpty() || "Awaiting Acceptance".equalsIgnoreCase(b.providerName) || "null".equalsIgnoreCase(b.providerName))) {
                    String resolved = map.get(pid);
                    if (resolved != null && !resolved.trim().isEmpty()) {
                        b.providerName = resolved;
                    }
                }
            }
        }
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

    @Override
    public void onWriteReview(Booking booking) {
        if (booking == null) return;
        if (!"completed".equalsIgnoreCase(clean(booking.status))) {
            Toast.makeText(this, getString(R.string.review_only_completed), Toast.LENGTH_SHORT).show();
            return;
        }
        showReviewDialog(booking);
    }

    @Override
    public void onPayNow(Booking booking) {
        if (booking == null) return;
        String bookingStatus = clean(booking.status).toLowerCase();
        if (!"price_set".equals(bookingStatus)) {
            Toast.makeText(this, "Payment not available for this booking", Toast.LENGTH_SHORT).show();
            return;
        }

        String keyId = BuildConfig.RAZORPAY_KEY_ID.trim();
        if (keyId.trim().isEmpty()) {
            Toast.makeText(this, "Payment key missing. Add RAZORPAY_KEY_ID.", Toast.LENGTH_LONG).show();
            return;
        }

        double amountInr = booking.paymentAmount;
        if (amountInr <= 0) {
            Toast.makeText(this, "Invalid payment amount", Toast.LENGTH_SHORT).show();
            return;
        }

        showCouponDialog(booking, amountInr, keyId);
    }

    private void showCouponDialog(Booking booking, double amountInr, String keyId) {
        TextInputLayout couponInputLayout = new TextInputLayout(this);
        couponInputLayout.setHint("Enter coupon code");
        TextInputEditText couponInput = new TextInputEditText(this);
        couponInput.setSingleLine(true);
        couponInputLayout.addView(couponInput);

        TextView tvSummary = new TextView(this);
        tvSummary.setText("Original amount: ₹" + String.format(java.util.Locale.US, "%.2f", amountInr));
        tvSummary.setPadding(8, 16, 8, 0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, 0);
        content.addView(couponInputLayout);
        content.addView(tvSummary);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Apply Coupon")
                .setView(content)
                .setNegativeButton("Skip", null)
                .setPositiveButton("Apply & Pay", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = clean(couponInput.getText() == null ? "" : couponInput.getText().toString());
                if (code.isEmpty()) {
                    pendingCouponCode = "";
                    pendingCouponDiscount = 0d;
                    pendingFinalAmountInr = amountInr;
                    dialog.dismiss();
                    createOrderAndLaunchCheckout(booking, amountInr, keyId);
                    return;
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                validateCouponAndProceed(booking, amountInr, keyId, code, dialog, tvSummary);
            });

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                pendingCouponCode = "";
                pendingCouponDiscount = 0d;
                pendingFinalAmountInr = amountInr;
                dialog.dismiss();
                createOrderAndLaunchCheckout(booking, amountInr, keyId);
            });
        });

        dialog.show();
    }

    private void validateCouponAndProceed(Booking booking,
                                          double amountInr,
                                          String keyId,
                                          String code,
                                          AlertDialog dialog,
                                          TextView tvSummary) {
        new Thread(() -> {
            try {
                String endpoint = clean(OFFER_VALIDATION_FN_URL);
                if (endpoint.isEmpty()) {
                    throw new IllegalStateException("Offer validation service is unavailable");
                }

                String cleanCode = code.trim().toUpperCase(Locale.US);
                JSONObject payload = new JSONObject();
                payload.put("code", cleanCode);
                payload.put("amount", amountInr);
                payload.put("booking_id", clean(booking.id));
                payload.put("user_id", clean(userId));

                Log.d(TAG, "Coupon Request: URL=" + endpoint + ", Payload=" + payload.toString());

                Request req = new Request.Builder().url(endpoint)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "{}";
                    Log.d(TAG, "Coupon Response: Code=" + r.code() + ", Body=" + body);

                    if (!r.isSuccessful()) {
                        String parsedError = parseSupabaseError(body);
                        if (parsedError.toLowerCase(Locale.US).contains("unknown")) {
                            CouponValidationResult fallback = validateCouponFromOffersTable(code, amountInr);
                            applyCouponValidationResult(fallback, amountInr, code, dialog, tvSummary, booking, keyId);
                            return;
                        }
                        throw new IllegalStateException("Coupon validation failed: " + parsedError);
                    }

                    JSONObject result = new JSONObject(body);
                    boolean isValid = result.optBoolean("valid", false);
                    boolean isActive = result.optBoolean("is_active", true); // Default to true if not present

                    if (!isValid || !isActive) {
                        String msg = clean(result.optString("message", ""));
                        throw new IllegalStateException(msg.isEmpty() ? "Invalid or inactive coupon" : msg);
                    }

                    // Check expiry date if returned by function
                    String expiry = result.optString("expiry_date", "");
                    if (!expiry.isEmpty()) {
                        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                        if (expiry.compareTo(today) < 0) {
                            throw new IllegalStateException("Coupon has expired");
                        }
                    }

                    double discount = optDoubleAny(result,
                            "discount_amount", "discount", "coupon_discount", "applied_discount");
                    double finalAmount = optDoubleAny(result,
                            "final_amount", "amount_after_discount", "payable_amount");

                    double computedFinalAmount = finalAmount;
                    double computedDiscount = discount;
                    if (computedFinalAmount <= 0d) {
                        computedFinalAmount = Math.max(0.01d, amountInr - Math.max(0d, computedDiscount));
                    }
                    if (computedDiscount < 0d) {
                        computedDiscount = Math.max(0d, amountInr - computedFinalAmount);
                    }

                    final double appliedDiscount = computedDiscount;
                    final double payableAmount = computedFinalAmount;
                    applyCouponValidationResult(
                            new CouponValidationResult(true, "", appliedDiscount, payableAmount),
                            amountInr,
                            code,
                            dialog,
                            tvSummary,
                            booking,
                            keyId
                    );
                }
            } catch (Exception e) {
                Log.e(TAG, "validateCouponAndProceed: " + e.getMessage(), e);
                MAIN.post(() -> {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private double optDoubleAny(JSONObject source, String... keys) {
        if (source == null || keys == null) return -1d;
        for (String key : keys) {
            if (!source.has(key) || source.isNull(key)) continue;
            try {
                Object raw = source.get(key);
                if (raw instanceof Number) return ((Number) raw).doubleValue();
                String text = clean(String.valueOf(raw));
                if (text.isEmpty()) continue;
                return Double.parseDouble(text);
            } catch (Exception ignore) {
            }
        }
        return -1d;
    }

    private CouponValidationResult validateCouponFromOffersTable(String code, double amountInr) {
        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String encodedCode = android.net.Uri.encode(code);
            String[] codeColumns = new String[]{"offer_code", "code"};

            for (String codeColumn : codeColumns) {
                String url = BASE_URL + "/rest/v1/offers?select=*"
                        + "&" + codeColumn + "=eq." + encodedCode
                        + "&is_active=eq.true"
                        + "&expiry_date=gte." + today
                        + "&limit=1";

                Request req = new Request.Builder().url(url)
                        .get()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (!r.isSuccessful()) {
                        continue;
                }

                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) {
                        continue;
                }

                JSONObject offer = arr.getJSONObject(0);
                int usageLimit = offer.optInt("usage_limit", -1);
                int usedCount = offer.optInt("used_count", offer.optInt("usage_count", 0));
                if (usageLimit >= 0 && usedCount >= usageLimit) {
                    return new CouponValidationResult(false, "Invalid or expired coupon", 0d, amountInr);
                }

                double minAmount = offer.optDouble("min_booking_amount", 0d);
                if (amountInr < minAmount) {
                    return new CouponValidationResult(false, "Minimum booking amount not met", 0d, amountInr);
                }

                String discountType = clean(offer.optString("discount_type", ""));
                double discountValue = offer.optDouble("discount_value", offer.optDouble("discount", 0d));
                double discount = 0d;
                if ("percentage".equalsIgnoreCase(discountType)) {
                    discount = amountInr * (Math.max(0d, discountValue) / 100d);
                } else {
                    discount = Math.max(0d, discountValue);
                }
                discount = Math.min(discount, Math.max(0d, amountInr - 0.01d));
                double finalAmount = Math.max(0.01d, amountInr - discount);
                return new CouponValidationResult(true, "", discount, finalAmount);
                }
            }
            return new CouponValidationResult(false, "Invalid or expired coupon", 0d, amountInr);
        } catch (Exception e) {
            Log.w(TAG, "validateCouponFromOffersTable failed: " + e.getMessage());
            return new CouponValidationResult(false, "Invalid or expired coupon", 0d, amountInr);
        }
    }

    private void applyCouponValidationResult(CouponValidationResult result,
                                             double amountInr,
                                             String code,
                                             AlertDialog dialog,
                                             TextView tvSummary,
                                             Booking booking,
                                             String keyId) {
        if (result == null || !result.valid) {
            String message = result == null ? "Invalid or expired coupon" : clean(result.message);
            throw new IllegalStateException(message.isEmpty() ? "Invalid or expired coupon" : message);
        }

        final double appliedDiscount = result.discountAmount;
        final double payableAmount = result.finalAmount > 0d ? result.finalAmount : Math.max(0.01d, amountInr - appliedDiscount);
        pendingCouponCode = code;
        pendingCouponDiscount = Math.max(0d, appliedDiscount);
        pendingFinalAmountInr = payableAmount;

        MAIN.post(() -> {
            tvSummary.setText("Discount: ₹" + String.format(java.util.Locale.US, "%.2f", pendingCouponDiscount)
                    + "\nFinal amount: ₹" + String.format(java.util.Locale.US, "%.2f", pendingFinalAmountInr));
            dialog.dismiss();
            Toast.makeText(this, "Coupon applied", Toast.LENGTH_SHORT).show();
            createOrderAndLaunchCheckout(booking, pendingFinalAmountInr, keyId);
        });
    }

    private static final class CouponValidationResult {
        final boolean valid;
        final String message;
        final double discountAmount;
        final double finalAmount;

        CouponValidationResult(boolean valid, String message, double discountAmount, double finalAmount) {
            this.valid = valid;
            this.message = message;
            this.discountAmount = discountAmount;
            this.finalAmount = finalAmount;
        }
    }

    @Override
    public void onMessageBooking(Booking booking) {
        if (booking == null) return;
        Intent i = new Intent(this, MessagesActivity.class);
        i.putExtra(MessagesActivity.EXTRA_BOOKING_ID, clean(booking.id));
        i.putExtra(MessagesActivity.EXTRA_PROVIDER_ID, clean(booking.providerId));
        i.putExtra(MessagesActivity.EXTRA_PROVIDER_NAME, clean(booking.providerName));
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ID, clean(booking.providerId));
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ROLE, "provider");
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_NAME, clean(booking.providerName));
        i.putExtra(MessagesActivity.EXTRA_USER_UID, clean(userId));
        startActivity(i);
    }

    @Override
    public void onProviderClick(Booking booking) {
        if (booking == null || clean(booking.providerId).isEmpty()) return;
        showLoading(true);
        new Thread(() -> {
            try {
                String pid = clean(booking.providerId);
                String url = BASE_URL + "/rest/v1/provider_profiles"
                        + "?or=(id.eq." + android.net.Uri.encode(pid) 
                        + ",firebase_uid.eq." + android.net.Uri.encode(pid) + ")"
                        + "&select=*&limit=1";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            MAIN.post(() -> {
                                showLoading(false);
                                openProviderDetail(o);
                            });
                            return;
                        }
                    }
                }
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Provider profile not found", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "onProviderClick: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error opening profile", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openProviderDetail(JSONObject o) {
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, o.optString("id"));
        intent.putExtra(ProviderDetailActivity.EXTRA_FIREBASE_UID, o.optString("firebase_uid"));
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, o.optString("business_name"));
        intent.putExtra(ProviderDetailActivity.EXTRA_OWNER_NAME, o.optString("owner_name", o.optString("user_name", "")));
        intent.putExtra(ProviderDetailActivity.EXTRA_CATEGORY, o.optString("category"));
        intent.putExtra(ProviderDetailActivity.EXTRA_CITY, o.optString("city"));
        intent.putExtra(ProviderDetailActivity.EXTRA_PHONE, o.optString("phone"));
        intent.putExtra(ProviderDetailActivity.EXTRA_EMAIL, o.optString("email"));
        intent.putExtra(ProviderDetailActivity.EXTRA_DESCRIPTION, o.optString("description"));
        intent.putExtra(ProviderDetailActivity.EXTRA_EXPERIENCE, o.optString("experience_yrs", o.optString("experience", "")));
        String profileImage = o.optString("profile_image", "");
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = o.optString("business_image_url", "");
        }
        intent.putExtra(ProviderDetailActivity.EXTRA_IMAGE_URL, profileImage);
        intent.putExtra(ProviderDetailActivity.EXTRA_IS_VERIFIED, o.optBoolean("is_verified", false));
        intent.putExtra(ProviderDetailActivity.EXTRA_RATING, (float) o.optDouble("rating", 0));
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    private void createOrderAndLaunchCheckout(Booking booking, double amountInr, String keyId) {
        if (clean(CREATE_ORDER_FN_URL).isEmpty()) {
            Toast.makeText(this, "Payment backend URL missing", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("amount", amountInr);
                payload.put("booking_id", clean(booking.id));

                Request req = new Request.Builder()
                        .url(CREATE_ORDER_FN_URL)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "{}";
                    int code = r.code();
                    if (code >= 200 && code < 300) {
                        JSONObject order = new JSONObject(body);
                        String orderId = clean(order.optString("id", ""));
                        int amountPaise = order.optInt("amount", 0);
                        if (orderId.isEmpty() || amountPaise <= 0) {
                            throw new IllegalStateException("Invalid order response");
                        }
                        MAIN.post(() -> {
                            showLoading(false);
                            openRazorpayCheckout(booking, keyId, orderId, amountPaise);
                        });
                    } else {
                        String err = parseSupabaseError(body);
                        MAIN.post(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Order failed: " + err, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "createOrderAndLaunchCheckout: " + e.getMessage(), e);
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Unable to start payment", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openRazorpayCheckout(Booking booking, String keyId, String orderId, int amountPaise) {
        try {
            pendingPaymentBooking = booking;
            pendingOrderId = orderId;
            Checkout checkout = new Checkout();
            checkout.setKeyID(keyId);

            JSONObject options = new JSONObject();
            options.put("name", "Home Service App");
            options.put("description", "Service Payment");
            options.put("currency", "INR");
            options.put("order_id", orderId);
            options.put("amount", amountPaise);
            options.put("key", keyId);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String email = user != null && user.getEmail() != null ? user.getEmail() : "";
            options.put("prefill.email", email);
            options.put("prefill.contact", clean(booking.customerPhone));

            checkout.open(this, options);
        } catch (Exception e) {
            pendingPaymentBooking = null;
            pendingOrderId = null;
            Log.e(TAG, "Razorpay open error: " + e.getMessage(), e);
            Toast.makeText(this, "Unable to start payment", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentID) {
        Booking booking = pendingPaymentBooking;
        String orderId = clean(pendingOrderId);
        pendingPaymentBooking = null;
        pendingOrderId = null;
        if (booking == null || clean(booking.id).isEmpty()) {
            Toast.makeText(this, "Payment received, but booking not found", Toast.LENGTH_SHORT).show();
            return;
        }
        updateBookingPaymentStatus(booking, razorpayPaymentID, orderId);
    }

    @Override
    public void onPaymentError(int code, String response) {
        pendingPaymentBooking = null;
        pendingOrderId = null;
        Log.e("RAZORPAY_ERROR", "code=" + code + " response=" + clean(response));
        Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show();
    }

    private void updateBookingPaymentStatus(Booking booking, String razorpayPaymentID, String orderId) {
        showLoading(true);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "paid");
                body.put("payment_status", "success");
                body.put("payment_id", clean(razorpayPaymentID));
                body.put("payment_amount", pendingFinalAmountInr > 0 ? pendingFinalAmountInr : booking.paymentAmount);
                if (!clean(pendingCouponCode).isEmpty()) {
                    body.put("coupon_code", clean(pendingCouponCode));
                    body.put("coupon_discount", pendingCouponDiscount);
                    body.put("final_amount", pendingFinalAmountInr > 0 ? pendingFinalAmountInr : booking.paymentAmount);
                }
                if (!clean(orderId).isEmpty()) {
                    body.put("payment_order_id", clean(orderId));
                }

                String url = BASE_URL + "/rest/v1/bookings?id=eq." + android.net.Uri.encode(clean(booking.id));
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String err = r.body() != null ? r.body().string() : "";
                    int code = r.code();
                    MAIN.post(() -> {
                        showLoading(false);
                        if (code == 200 || code == 204) {
                            Toast.makeText(this, "Payment Successful", Toast.LENGTH_SHORT).show();
                            FcmNotificationHelper.notifyProviderPaymentCompleted(
                                    clean(booking.providerId),
                                    clean(booking.serviceName),
                                    clean(booking.id)
                            );
                            loadBookings();
                        } else {
                            Log.e("ERROR", err);
                            Toast.makeText(this, "Payment update failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "updateBookingPaymentStatus: " + e.getMessage(), e);
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Payment update failed", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showReviewDialog(Booking booking) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_provider_review, null, false);
        TextView tvReviewTarget = content.findViewById(R.id.tvReviewTarget);
        RatingBar ratingBar = content.findViewById(R.id.ratingBar);
        TextInputEditText etReviewComment = content.findViewById(R.id.etReviewComment);

        String providerName = clean(booking.providerName).isEmpty() ? "provider" : booking.providerName.trim();
        tvReviewTarget.setText("How was your service with " + providerName + "?");

        if (booking.reviewRating > 0) ratingBar.setRating(booking.reviewRating);
        if (!clean(booking.reviewComment).isEmpty()) {
            etReviewComment.setText(booking.reviewComment);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(booking.reviewRating > 0 ? getString(R.string.review_edit_button) : getString(R.string.review_write_button))
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int rating = Math.round(ratingBar.getRating());
            String comment = clean(etReviewComment.getText() == null ? "" : etReviewComment.getText().toString());

            if (rating < 1 || rating > 5) {
                Toast.makeText(this, getString(R.string.review_rating_required), Toast.LENGTH_SHORT).show();
                return;
            }
            if (comment.length() > REVIEW_MAX_CHARS) {
                Toast.makeText(this, getString(R.string.review_too_long), Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            submitReview(booking, rating, comment);
        }));

        dialog.show();
    }

    private void submitReview(Booking booking, int rating, String comment) {
        showLoading(true);
        new Thread(() -> {
            try {
                String activeCustomerId = clean(userDbId);
                if (activeCustomerId.isEmpty()) activeCustomerId = clean(userId);
                if (activeCustomerId.isEmpty()) {
                    throw new IllegalStateException("Missing customer identity");
                }

                JSONObject body = new JSONObject();
                body.put("booking_id", clean(booking.id));
                body.put("provider_id", clean(booking.providerId));
                body.put("customer_id", activeCustomerId);
                body.put("customer_name", clean(booking.customerName));
                body.put("rating", rating);
                body.put("comment", comment);

                boolean updateExisting = !clean(booking.reviewId).isEmpty();
                String url;
                Request.Builder requestBuilder;
                if (updateExisting) {
                    url = BASE_URL + "/rest/v1/provider_reviews?id=eq." + android.net.Uri.encode(booking.reviewId);
                    requestBuilder = new Request.Builder().url(url)
                            .patch(RequestBody.create(body.toString(), JSON));
                } else {
                    url = BASE_URL + "/rest/v1/provider_reviews";
                    requestBuilder = new Request.Builder().url(url)
                            .post(RequestBody.create(body.toString(), JSON));
                }

                Request request = requestBuilder
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("Prefer", "return=representation")
                        .build();

                try (Response response = HTTP.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "[]";
                    int code = response.code();
                    if (code == 200 || code == 201) {
                        JSONArray arr = new JSONArray(responseBody);
                        if (arr.length() > 0) {
                            JSONObject row = arr.getJSONObject(0);
                            booking.reviewId = clean(row.optString("id", booking.reviewId));
                            booking.reviewRating = row.optInt("rating", rating);
                            booking.reviewComment = clean(row.optString("comment", comment));
                            booking.reviewUpdatedAt = clean(row.optString("updated_at", ""));
                            if (booking.reviewUpdatedAt.isEmpty()) {
                                booking.reviewUpdatedAt = clean(row.optString("created_at", ""));
                            }
                        } else {
                            booking.reviewRating = rating;
                            booking.reviewComment = comment;
                        }

                        MAIN.post(() -> {
                            showLoading(false);
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, getString(R.string.review_saved), Toast.LENGTH_SHORT).show();
                            loadBookings();
                        });
                    } else {
                        String apiError = parseSupabaseError(responseBody);
                        Log.w(TAG, "submitReview failed HTTP " + code + " | " + apiError + " | raw=" + responseBody);
                        String toastMsg = (code == 401 || code == 403)
                                ? "Review permission denied. Check provider_reviews RLS/policies."
                                : "Failed to save review (HTTP " + code + ")";
                        MAIN.post(() -> {
                            showLoading(false);
                            Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "submitReview: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.review_failed_save), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void cancelBooking(Booking booking) {
        if (booking == null) return;
        if (!"pending".equals(booking.status)) {
            Toast.makeText(this, "Cannot cancel after accepted", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "cancelled");

                // Target bookings table directly for both emergency and normal
                String url = BASE_URL + "/rest/v1/bookings"
                        + "?id=eq." + android.net.Uri.encode(clean(booking.id))
                        + "&status=eq.pending";

                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    MAIN.post(() -> {
                        if (r.isSuccessful()) {
                            booking.status = "cancelled";
                            adapter.notifyDataSetChanged();
                            Toast.makeText(this, "Cancelled successfully", Toast.LENGTH_SHORT).show();
                            loadBookings();

                            // Notify provider if someone was assigned (even if pending)
                            if (!clean(booking.providerId).isEmpty()) {
                                notifyProviderOfCancellation(booking);
                            }
                        } else {
                            Toast.makeText(this, "Failed to cancel. Request may already be accepted.", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "cancelBooking: " + e.getMessage());
                MAIN.post(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void notifyProviderOfCancellation(Booking booking) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", clean(booking.providerId));
                payload.put("type", "booking_cancelled");
                payload.put("title", "Booking Cancelled");
                payload.put("body", "The customer has cancelled their request: " + clean(booking.serviceName));
                
                JSONObject data = new JSONObject();
                data.put("booking_id", clean(booking.id));
                payload.put("data", data);

                Request req = new Request.Builder()
                        .url(clean(BASE_URL) + "/functions/v1/send-notification")
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                HTTP.newCall(req).execute().close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to notify provider of cancellation: " + e.getMessage());
            }
        }).start();
    }

    private boolean cancelEmergencyRequestAtomic(Booking booking) {
        try {
            String requestId = clean(booking.emergencyRequestId);
            if (requestId.isEmpty()) {
                requestId = clean(booking.id);
            }
            if (requestId.isEmpty()) return false;

            JSONObject payload = new JSONObject();
            payload.put("p_request_id", requestId);

            Request rpcRequest = new Request.Builder()
                    .url(BASE_URL + "/rest/v1/rpc/cancel_emergency_request_atomic")
                    .post(RequestBody.create(payload.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response rpcResponse = HTTP.newCall(rpcRequest).execute()) {
                String body = rpcResponse.body() != null ? rpcResponse.body().string() : "[]";
                if (rpcResponse.code() != 200) {
                    return false;
                }

                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return false;
                return arr.getJSONObject(0).optBoolean("cancelled", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "cancelEmergencyRequestAtomic: " + e.getMessage());
            return false;
        }
    }

    private void cancelRelatedEmergencyBookings(String emergencyRequestId) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "cancelled");
                String url = BASE_URL + "/rest/v1/bookings?emergency_request_id=eq." + emergencyRequestId + "&status=eq.pending";
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();
                HTTP.newCall(req).execute().close();
            } catch (Exception ignored) {}
        }).start();
    }

    private void showBookingDetails(Booking b) {
        if (b == null) return;
        
        String reviewLine = (b.reviewRating > 0)
                ? ("\nReview: " + b.reviewRating + "/5"
                + (clean(b.reviewComment).isEmpty() ? "" : " - " + clean(b.reviewComment)))
                : "";

        String msg = "Service: " + (clean(b.serviceName).isEmpty() ? "-" : b.serviceName) + "\n"
                + "Provider: " + (clean(b.providerName).isEmpty() ? "-" : b.providerName) + "\n"
                + "Date: " + (clean(b.bookingDate).isEmpty() ? "-" : b.bookingDate) + "\n"
                + "Time: " + (clean(b.bookingTime).isEmpty() ? "" : " " + b.bookingTime) + "\n"
                + "Address: " + (clean(b.address).isEmpty() ? "-" : b.address) + "\n"
                + "Status: " + (clean(b.status).isEmpty() ? "" : b.status.toUpperCase(Locale.US)) + "\n"
                + "Price: ₹" + String.format(Locale.US, "%.0f", b.price)
                + reviewLine;
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

    private String parseSupabaseError(String responseBody) {
        try {
            JSONObject o = new JSONObject(responseBody == null ? "{}" : responseBody);
            String message = clean(o.optString("message", ""));
            String details = clean(o.optString("details", ""));
            String hint = clean(o.optString("hint", ""));
            String merged = message;
            if (!details.isEmpty()) merged += (merged.isEmpty() ? "" : " | ") + details;
            if (!hint.isEmpty()) merged += (merged.isEmpty() ? "" : " | ") + hint;
            return merged.isEmpty() ? "Invalid or expired coupon" : merged;
        } catch (Exception ignore) {
            return clean(responseBody);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
