package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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
 *   - My Services    (services the provider offers)
 *   - Earnings       (revenue summary)
 *   - Profile        (business profile)
 *   - Back to Home   (still use the app as a regular user)
 */
public class ProviderDashboardActivity extends AppCompatActivity {

    private static final String TAG = "ProviderDashboard";

    private TextView  providerNameText, providerEmailText;
    private CardView  cardMyBookings, cardMyServices, cardEarnings, cardProfile;
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
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.providerToolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void initViews() {
        providerNameText  = findViewById(R.id.providerNameText);
        providerEmailText = findViewById(R.id.providerEmailText);
        cardMyBookings    = findViewById(R.id.cardMyBookings);
        cardMyServices    = findViewById(R.id.cardMyServices);
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
                    Toast.makeText(this, "My Bookings — coming soon!", Toast.LENGTH_SHORT).show());

        if (cardMyServices != null)
            cardMyServices.setOnClickListener(v ->
                    Toast.makeText(this, "My Services — coming soon!", Toast.LENGTH_SHORT).show());

        if (cardEarnings != null)
            cardEarnings.setOnClickListener(v ->
                    Toast.makeText(this, "Earnings — coming soon!", Toast.LENGTH_SHORT).show());

        if (cardProfile != null)
            cardProfile.setOnClickListener(v ->
                    Toast.makeText(this, "Business Profile — coming soon!", Toast.LENGTH_SHORT).show());

        if (btnBackToHome != null)
            btnBackToHome.setOnClickListener(v -> {
                // Provider is also a user — go back to home screen
                Intent i = new Intent(this, MainActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
    }

    @Override
    public void onBackPressed() {
        // Go back to main home screen (provider is also a regular user)
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

