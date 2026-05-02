package com.example.quickfix;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProviderDashboardActivity
 * ──────────────────────────────────────────────────────────────────────────
 * Shown ONLY to users whose Supabase role = "provider".
 * A provider is also a regular user — they can still use the home screen
 * for booking other services.
 *
 * Features:
 *   - My Bookings    (incoming service requests)
 *   - Earnings       (revenue summary)
 *   - Profile        (business profile)
 *   - Back to Home   (still use the app as a regular user)
 */
public class ProviderDashboardActivity extends BaseActivity {

    private TextView providerNameText, providerEmailText;
    private TextView tvStatsBookings, tvStatsRating, tvStatsEarnings;
    private CardView cardMyBookings, cardEarnings, cardProfile, cardReviews;
    private CardView cardRating;
    private com.google.android.material.button.MaterialButton btnBackToHome;
    private FirebaseAuth mAuth;

    private String providerProfileIdForDashboard = "";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_dashboard);

        mAuth = FirebaseAuth.getInstance();

        setupToolbar();
        initViews();
        loadProviderInfo();
        setupClickListeners();
        loadDashboardStats();

        // Handle system back consistently with toolbar back: return to MainActivity.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBackToHome();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardStats();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.providerToolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> goBackToHome());
        }
    }

    private void initViews() {
        providerNameText = findViewById(R.id.providerNameText);
        providerEmailText = findViewById(R.id.providerEmailText);
        tvStatsBookings = findViewById(R.id.tvStatsBookings);
        tvStatsRating = findViewById(R.id.tvStatsRating);
        tvStatsEarnings = findViewById(R.id.tvStatsEarnings);
        cardMyBookings = findViewById(R.id.cardMyBookings);
        cardEarnings = findViewById(R.id.cardEarnings);
        cardProfile = findViewById(R.id.cardProfile);
        cardReviews = findViewById(R.id.cardReviews);
        cardRating = findViewById(R.id.cardRating);
        btnBackToHome = findViewById(R.id.btnBackToHome);
    }

    private void loadProviderInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            redirectToLogin();
            return;
        }

        // Use UserSession (single source of truth) — fallback to Firebase name
        String name  = !UserSession.name.isEmpty()  ? UserSession.name  : user.getDisplayName();
        String email = !UserSession.email.isEmpty() ? UserSession.email : user.getEmail();

        if (providerNameText != null) {
            providerNameText.setText((name != null && !name.isEmpty())
                    ? "Welcome, " + name + "!"
                    : "Welcome, Provider!");
        }
        if (providerEmailText != null) {
            providerEmailText.setText(email != null ? email : "");
        }

        // If UserSession hasn't been populated yet, fetch it
        if (UserSession.name.isEmpty()) {
            SupabaseClient.fetchUserProfile(user.getUid(), new SupabaseClient.UserProfileCallback() {
                @Override public void onSuccess() {
                    if (providerNameText != null && !UserSession.name.isEmpty()) {
                        providerNameText.setText("Welcome, " + UserSession.name + "!");
                    }
                    if (providerEmailText != null && !UserSession.email.isEmpty()) {
                        providerEmailText.setText(UserSession.email);
                    }
                }
                @Override public void onError(String msg) { /* use fallback already set */ }
            });
        }
    }

    private void loadDashboardStats() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        final String uid = user.getUid();
        new Thread(() -> {
            double totalEarnings = 0d;
            int totalBookingsCount = 0;
            int emergencyCount = 0;
            float ratingValue = 0f;
            String businessName = "";
            String serviceName = "";

            try {
                String providerProfileId = "";

                JSONArray profileArr = getJsonArray(BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles"
                        + "?firebase_uid=eq." + Uri.encode(uid)
                        + "&select=id,total_bookings,rating,business_name,category"
                        + "&limit=1");

                if (profileArr.length() > 0) {
                    JSONObject p = profileArr.getJSONObject(0);
                    providerProfileId = p.optString("id", "").trim();
                    totalBookingsCount = p.optInt("total_bookings", 0);
                    ratingValue = (float) p.optDouble("rating", 0d);
                    businessName = p.optString("business_name", "");
                    serviceName = p.optString("category", "");
                }

                providerProfileIdForDashboard = providerProfileId;

                JSONArray bookingArr = getProviderBookings(providerProfileId, uid);
                // If total_bookings column in profile is 0, fallback to current active bookings count
                if (totalBookingsCount <= 0) {
                    totalBookingsCount = bookingArr.length();
                }
                
                for (int i = 0; i < bookingArr.length(); i++) {
                    JSONObject b = bookingArr.getJSONObject(i);
                    String status = b.optString("status", "").trim().toLowerCase(Locale.US);
                    
                    if (b.optBoolean("is_emergency", false) || "Emergency".equalsIgnoreCase(b.optString("type"))) {
                        emergencyCount++;
                    }

                    if ("paid".equals(status) || "completed".equals(status)) {
                        double amount = parseDoubleSafe(b.opt("price"));
                        if (amount <= 0d) {
                            amount = parseDoubleSafe(b.opt("payment_amount"));
                        }
                        totalEarnings += amount;
                    }
                }

                if (ratingValue <= 0f) {
                    ratingValue = loadRatingFallback(providerProfileId, uid);
                }

            } catch (Exception e) {
                android.util.Log.e("ProviderDashboard", "Error loading stats", e);
            }

            final double finalEarnings = totalEarnings;
            final int finalTotal = Math.max(0, totalBookingsCount);
            final int finalEmergency = emergencyCount;
            final float finalRating = Math.max(0f, ratingValue);
            
            final String finalBusiness = businessName;
            final String finalService = serviceName;
            
            runOnUiThread(() -> {
                if (tvStatsBookings != null) {
                    tvStatsBookings.setText(String.valueOf(finalTotal));
                }
                
                TextView tvEmergency = findViewById(R.id.tvStatsEmergency);
                if (tvEmergency != null) {
                    tvEmergency.setText(String.valueOf(finalEmergency));
                }

                TextView tvBusiness = findViewById(R.id.tvDashboardBusinessName);
                if (tvBusiness != null && !finalBusiness.isEmpty()) {
                    tvBusiness.setVisibility(android.view.View.VISIBLE);
                    tvBusiness.setText("Business: " + finalBusiness);
                }
                
                TextView tvService = findViewById(R.id.tvDashboardServiceName);
                if (tvService != null && !finalService.isEmpty()) {
                    tvService.setVisibility(android.view.View.VISIBLE);
                    tvService.setText("Service: " + finalService);
                }

                if (tvStatsRating != null) {
                    tvStatsRating.setText(String.format(Locale.US, "⭐ %.1f", finalRating));
                }
                if (tvStatsEarnings != null) {
                    tvStatsEarnings.setText("₹" + formatCurrency(finalEarnings));
                }
            });
        }).start();
    }

    private JSONArray getProviderBookings(String providerProfileId, String uid) {
        String base = BuildConfig.SUPABASE_URL + "/rest/v1/bookings?select=status,price,payment_amount,is_emergency,type";

        String url;
        if (providerProfileId != null && !providerProfileId.trim().isEmpty()) {
            url = base + "&provider_id=eq." + Uri.encode(providerProfileId.trim());
        } else {
            url = base + "&provider_id=eq." + Uri.encode(uid);
        }

        return getJsonArray(url);
    }

    private float loadRatingFallback(String providerProfileId, String uid) {
        try {
            JSONArray reviews;
            if (providerProfileId != null && !providerProfileId.trim().isEmpty()) {
                reviews = getJsonArray(BuildConfig.SUPABASE_URL + "/rest/v1/provider_reviews"
                        + "?select=rating"
                        + "&provider_id=eq." + Uri.encode(providerProfileId.trim()));
            } else {
                reviews = getJsonArray(BuildConfig.SUPABASE_URL + "/rest/v1/provider_reviews"
                        + "?select=rating"
                        + "&provider_id=eq." + Uri.encode(uid));
            }

            if (reviews.length() == 0) return 0f;
            float sum = 0f;
            for (int i = 0; i < reviews.length(); i++) {
                sum += (float) reviews.getJSONObject(i).optDouble("rating", 0d);
            }
            return sum / reviews.length();
        } catch (Exception e) {
            android.util.Log.e("ProviderDashboard", "Error in fallback rating", e);
            return 0f;
        }
    }

    private JSONArray getJsonArray(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = http.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (!r.isSuccessful()) return new JSONArray();
                return new JSONArray(body);
            }
        } catch (Exception e) {
            android.util.Log.e("ProviderDashboard", "Error fetching JSON array from " + url, e);
            return new JSONArray();
        }
    }

    private double parseDoubleSafe(Object value) {
        if (value == null) return 0d;
        try {
            if (value instanceof Number) return ((Number) value).doubleValue();
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private String formatCurrency(double value) {
        DecimalFormat df = new DecimalFormat("#,##0.##");
        return df.format(Math.max(0d, value));
    }

    private void setupClickListeners() {
        if (cardMyBookings != null)
            cardMyBookings.setOnClickListener(v -> startActivity(new Intent(this, MyBookingsActivity.class)));

        if (cardEarnings != null)
            cardEarnings.setOnClickListener(v -> startActivity(new Intent(this, EarningsActivity.class)));

        if (cardProfile != null)
            cardProfile.setOnClickListener(v -> startActivity(new Intent(this, BusinessProfileActivity.class)));

        if (cardRating != null)
            cardRating.setOnClickListener(v -> openProviderReviews());

        if (cardReviews != null)
            cardReviews.setOnClickListener(v -> openProviderReviews());

        if (btnBackToHome != null)
            btnBackToHome.setOnClickListener(v -> {
                // Provider is also a user — go back to home screen
                Intent i = new Intent(this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
    }

    private void openProviderReviews() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        Intent intent = new Intent(this, ProviderReviewsActivity.class);
        intent.putExtra(ProviderReviewsActivity.EXTRA_PROVIDER_UID, user.getUid());
        if (providerProfileIdForDashboard != null && !providerProfileIdForDashboard.isEmpty()) {
            intent.putExtra(ProviderReviewsActivity.EXTRA_PROVIDER_ID, providerProfileIdForDashboard);
        }
        startActivity(intent);
    }

    private void goBackToHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void redirectToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
