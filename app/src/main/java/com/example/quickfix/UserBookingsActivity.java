package com.example.quickfix;

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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public class UserBookingsActivity extends BaseActivity
        implements UserBookingsAdapter.BookingActionListener, PaymentResultListener {

    private static final String TAG = "UserBookings";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
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

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());
    private Booking pendingPaymentBooking;
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
                String dbId = android.net.Uri.encode(userDbId);
                String url = BASE_URL
                        + "/rest/v1/bookings?or=(customer_id.eq." + uid + ",customer_id.eq." + dbId + ")"
                        + "&select=*&order=created_at.desc";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
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
                    } else {
                        MAIN.post(() -> {
                            showLoading(false);
                            Toast.makeText(this, "Failed to load bookings", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadBookings: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
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

        Request req = new Request.Builder().url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

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
        if (keyId.isEmpty()) {
            Toast.makeText(this, "Payment key missing. Add RAZORPAY_KEY_ID.", Toast.LENGTH_LONG).show();
            return;
        }

        double amountInr = booking.paymentAmount;
        if (amountInr <= 0) {
            Toast.makeText(this, "Invalid payment amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            pendingPaymentBooking = booking;
            Checkout checkout = new Checkout();
            checkout.setKeyID(keyId);

            JSONObject options = new JSONObject();
            options.put("name", "Home Service App");
            options.put("description", "Service Payment");
            options.put("currency", "INR");
            options.put("amount", (int) Math.round(amountInr * 100.0));

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String email = user != null && user.getEmail() != null ? user.getEmail() : "";
            options.put("prefill.email", email);
            options.put("prefill.contact", clean(booking.customerPhone));

            checkout.open(this, options);
        } catch (Exception e) {
            Log.e(TAG, "Razorpay open error: " + e.getMessage(), e);
            Toast.makeText(this, "Unable to start payment", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPaymentSuccess(String razorpayPaymentID) {
        Booking booking = pendingPaymentBooking;
        pendingPaymentBooking = null;
        if (booking == null || clean(booking.id).isEmpty()) {
            Toast.makeText(this, "Payment received, but booking not found", Toast.LENGTH_SHORT).show();
            return;
        }
        updateBookingPaymentStatus(booking, razorpayPaymentID);
    }

    @Override
    public void onPaymentError(int code, String response) {
        pendingPaymentBooking = null;
        Log.e("RAZORPAY_ERROR", "code=" + code + " response=" + clean(response));
        Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show();
    }

    private void updateBookingPaymentStatus(Booking booking, String razorpayPaymentID) {
        showLoading(true);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "paid");
                body.put("payment_status", "success");
                body.put("payment_id", clean(razorpayPaymentID));

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
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("status", "cancelled");
                String uid = userId != null ? userId.trim() : "";
                String dbId = userDbId != null ? userDbId.trim() : "";
                String bookingId = clean(booking.id);
                String url = BASE_URL + "/rest/v1/bookings?id=eq." + android.net.Uri.encode(bookingId);
                if (!uid.isEmpty() || !dbId.isEmpty()) {
                    url += "&or=(";
                    boolean hasPrev = false;
                    if (!uid.isEmpty()) {
                        url += "customer_id.eq." + android.net.Uri.encode(uid);
                        hasPrev = true;
                    }
                    if (!dbId.isEmpty()) {
                        if (hasPrev) url += ",";
                        url += "customer_id.eq." + android.net.Uri.encode(dbId);
                    }
                    url += ")";
                }
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
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
        String reviewLine = (b.reviewRating > 0)
                ? ("\nReview: " + b.reviewRating + "/5"
                + (clean(b.reviewComment).isEmpty() ? "" : " - " + b.reviewComment))
                : "";

        String msg = "Service: " + (b.serviceName.isEmpty() ? "-" : b.serviceName) + "\n"
                + "Provider: " + (b.providerName.isEmpty() ? "-" : b.providerName) + "\n"
                + "Date: " + (b.bookingDate.isEmpty() ? "-" : b.bookingDate) + "\n"
                + "Time: " + (b.bookingTime.isEmpty() ? "-" : b.bookingTime) + "\n"
                + "Address: " + (b.address.isEmpty() ? "-" : b.address) + "\n"
                + "Status: " + b.status.toUpperCase() + "\n"
                + "Price: ₹" + String.format("%.0f", b.price)
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
            return merged.isEmpty() ? "Unknown Supabase error" : merged;
        } catch (Exception ignore) {
            return clean(responseBody);
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
