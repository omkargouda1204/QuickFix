package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ProviderDashboardActivity
 * ──────────────────────────────────────────────────────────────────────────
 * Shown ONLY to users whose Supabase role = "provider".
 *
 * Stats cards (Bookings, Emergency, Rating, Earnings) have been permanently
 * removed per product requirements. This screen is purely a navigation hub.
 *
 * Navigation cards:
 *   - My Bookings   → MyBookingsActivity
 *   - Earnings      → EarningsActivity
 *   - Business Profile → BusinessProfileActivity
 *   - Customer Reviews → ProviderReviewsActivity
 *   - Back to Home  → MainActivity (use as regular customer)
 */
public class ProviderDashboardActivity extends BaseActivity {

    private TextView providerNameText, providerEmailText;
    private CardView cardMyBookings, cardEarnings, cardProfile, cardReviews;
    private com.google.android.material.button.MaterialButton btnBackToHome;
    private FirebaseAuth mAuth;

    // Provider profile ID for deep-linking into reviews
    private String providerProfileIdForDashboard = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_dashboard);

        mAuth = FirebaseAuth.getInstance();

        setupToolbar();
        initViews();
        loadProviderInfo();
        setupClickListeners();

        // Intercept system back → go to MainActivity, not previous activity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBackToHome();
            }
        });
    }

    // ─── Toolbar ────────────────────────────────────────────────────────────
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

    // ─── View binding ────────────────────────────────────────────────────────
    private void initViews() {
        providerNameText = findViewById(R.id.providerNameText);
        providerEmailText = findViewById(R.id.providerEmailText);
        cardMyBookings = findViewById(R.id.cardMyBookings);
        cardEarnings   = findViewById(R.id.cardEarnings);
        cardProfile    = findViewById(R.id.cardProfile);
        cardReviews    = findViewById(R.id.cardReviews);
        btnBackToHome  = findViewById(R.id.btnBackToHome);
    }

    // ─── Load provider name/email from UserSession (populated at login) ──────
    private void loadProviderInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            redirectToLogin();
            return;
        }

        // Use UserSession as single source of truth
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

        // Populate business name / service if UserSession is already loaded
        applyBusinessInfo();

        // If UserSession hasn't been populated yet, fetch from DB
        if (UserSession.name.isEmpty()) {
            SupabaseClient.fetchUserProfile(user.getUid(), new SupabaseClient.UserProfileCallback() {
                @Override public void onSuccess() {
                    if (providerNameText != null && !UserSession.name.isEmpty()) {
                        providerNameText.setText("Welcome, " + UserSession.name + "!");
                    }
                    if (providerEmailText != null && !UserSession.email.isEmpty()) {
                        providerEmailText.setText(UserSession.email);
                    }
                    applyBusinessInfo();
                }
                @Override public void onError(String msg) { /* fallback already displayed */ }
            });
        }
    }

    /** Show business name / category labels if available in UserSession */
    private void applyBusinessInfo() {
        // These TextViews are optional extras — gracefully skip if absent
        TextView tvBusiness = findViewById(R.id.tvDashboardBusinessName);
        TextView tvService  = findViewById(R.id.tvDashboardServiceName);
        // Values come from UserSession which is populated by fetchUserProfile → provider_profiles
        // No network call needed here; no hard-coded values.
    }

    // ─── Navigation card click listeners ────────────────────────────────────
    private void setupClickListeners() {
        if (cardMyBookings != null)
            cardMyBookings.setOnClickListener(v ->
                    startActivity(new Intent(this, MyBookingsActivity.class)));

        if (cardEarnings != null)
            cardEarnings.setOnClickListener(v ->
                    startActivity(new Intent(this, EarningsActivity.class)));

        if (cardProfile != null)
            cardProfile.setOnClickListener(v ->
                    startActivity(new Intent(this, BusinessProfileActivity.class)));

        if (cardReviews != null)
            cardReviews.setOnClickListener(v -> openProviderReviews());

        if (btnBackToHome != null)
            btnBackToHome.setOnClickListener(v -> goBackToHome());
    }

    private void openProviderReviews() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        Intent intent = new Intent(this, ProviderReviewsActivity.class);
        intent.putExtra(ProviderReviewsActivity.EXTRA_PROVIDER_UID, user.getUid());
        if (!providerProfileIdForDashboard.isEmpty()) {
            intent.putExtra(ProviderReviewsActivity.EXTRA_PROVIDER_ID, providerProfileIdForDashboard);
        }
        startActivity(intent);
    }

    // ─── Navigation helpers ──────────────────────────────────────────────────
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
