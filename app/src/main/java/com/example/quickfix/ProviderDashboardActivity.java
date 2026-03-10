package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

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
public class ProviderDashboardActivity extends AppCompatActivity {

    private TextView  providerNameText, providerEmailText;
    private CardView  cardMyBookings, cardEarnings, cardProfile;
    private com.google.android.material.button.MaterialButton btnBackToHome;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_dashboard);

        mAuth = FirebaseAuth.getInstance();

        setupToolbar();
        initViews();
        loadProviderInfo();
        setupClickListeners();

        // Handle system back consistently with toolbar back: return to MainActivity.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBackToHome();
            }
        });
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
        providerNameText  = findViewById(R.id.providerNameText);
        providerEmailText = findViewById(R.id.providerEmailText);
        cardMyBookings    = findViewById(R.id.cardMyBookings);
        cardEarnings      = findViewById(R.id.cardEarnings);
        cardProfile       = findViewById(R.id.cardProfile);
        btnBackToHome     = findViewById(R.id.btnBackToHome);
    }

    private void loadProviderInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { redirectToLogin(); return; }

        String name  = user.getDisplayName();
        String email = user.getEmail();

        if (providerNameText != null)
            providerNameText.setText(
                    (name != null && !name.isEmpty()) ? "Welcome, " + name + "!" : "Welcome, Provider!");
        if (providerEmailText != null)
            providerEmailText.setText(email != null ? email : "");
    }

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

        if (btnBackToHome != null)
            btnBackToHome.setOnClickListener(v -> {
                // Provider is also a user — go back to home screen
                Intent i = new Intent(this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
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
