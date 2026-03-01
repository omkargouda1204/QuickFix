package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    // ── Toolbar ───────────────────────────────────────────────────────────
    @SuppressWarnings("unused") // reserved for location feature
    private TextView toolbarLocation;
    private ImageView notificationIcon;
    private FrameLayout profileAvatar;
    private TextView profileInitial;

    // ── Header ────────────────────────────────────────────────────────────
    private TextView welcomeMessage;
    private TextView userEmail;
    private LinearLayout searchBar;

    // ── Categories ────────────────────────────────────────────────────────
    private LinearLayout catPlumbing, catElectrical, catCleaning, catRepair;
    private LinearLayout catAC, catPainting, catCarpentry, catMore;

    // ── Quick Actions ─────────────────────────────────────────────────────
    private LinearLayout quickBookService, quickEmergency, quickTrackBooking;

    // ── Featured ──────────────────────────────────────────────────────────
    private CardView featuredCard1, featuredCard2, featuredCard3;

    // ── Provider Dashboard ────────────────────────────────────────────────
    private android.view.View providerDashboardSection;
    @SuppressWarnings("unused") // reserved for provider dashboard expansion
    private CardView providerDashboardCard;
    @SuppressWarnings("unused") // reserved for provider dashboard expansion
    private com.google.android.material.button.MaterialButton providerDashboardBtn;

    // ── List Your Business Card ───────────────────────────────────────────
    private LinearLayout listBusinessSection;
    private MaterialButton btnListBusinessStart;

    // ── Bottom Nav ────────────────────────────────────────────────────────
    private LinearLayout navHome, navBookings, navMessages, navProfile;
    private ImageView navHomeIcon, navBookingsIcon, navMessagesIcon, navProfileIcon;
    private TextView navHomeText, navBookingsText, navMessagesText, navProfileText;

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        // Guard: must be logged in.
        // NOTE: We do NOT block on isEmailVerified() here because Google Sign-In users
        // are verified by Google and may have isEmailVerified()=false in cached token.
        // LoginActivity already gates on verification before routing here.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        setupToolbar();
        initViews();
        loadUserData(currentUser);
        setupCategoryClickListeners();
        setupQuickActionListeners();
        setupFeaturedCardListeners();
        setupBottomNav();
        setupSearchBar();
        setupListBusinessCard(currentUser);
        // Hide provider dashboard section — only providers/admins are routed elsewhere
        if (providerDashboardSection != null)
            providerDashboardSection.setVisibility(View.GONE);

        // ── FCM Token — store silently in DB, never shown in UI ──
        FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w("FCM_TOKEN", "Fetching token failed", task.getException());
                    return;
                }
                String token = task.getResult();
                Log.d("FCM_TOKEN", "Token retrieved (stored in DB, not shown to user)");
                // Save to Supabase users table silently
                SupabaseClient.saveFcmToken(currentUser.getUid(), token);
            });
    }

    // ── Setup Toolbar ─────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    // ── Init Views ────────────────────────────────────────────────────────

    private void initViews() {
        toolbarLocation  = findViewById(R.id.toolbarLocation);
        notificationIcon = findViewById(R.id.notificationIcon);
        profileAvatar    = findViewById(R.id.profileAvatar);
        profileInitial   = findViewById(R.id.profileInitial);

        welcomeMessage = findViewById(R.id.welcomeMessage);
        userEmail      = findViewById(R.id.userEmail);
        searchBar      = findViewById(R.id.searchBar);

        catPlumbing   = findViewById(R.id.catPlumbing);
        catElectrical = findViewById(R.id.catElectrical);
        catCleaning   = findViewById(R.id.catCleaning);
        catRepair     = findViewById(R.id.catRepair);
        catAC         = findViewById(R.id.catAC);
        catPainting   = findViewById(R.id.catPainting);
        catCarpentry  = findViewById(R.id.catCarpentry);
        catMore       = findViewById(R.id.catMore);

        quickBookService   = findViewById(R.id.quickBookService);
        quickEmergency     = findViewById(R.id.quickEmergency);
        quickTrackBooking  = findViewById(R.id.quickTrackBooking);

        featuredCard1  = findViewById(R.id.featuredCard1);
        featuredCard2  = findViewById(R.id.featuredCard2);
        featuredCard3  = findViewById(R.id.featuredCard3);

        providerDashboardSection = findViewById(R.id.providerDashboardSection);
        providerDashboardCard    = findViewById(R.id.providerDashboardCard);
        providerDashboardBtn     = findViewById(R.id.providerDashboardBtn);

        listBusinessSection  = findViewById(R.id.listBusinessSection);
        btnListBusinessStart = findViewById(R.id.btnListBusinessStart);

        navHome      = findViewById(R.id.navHome);
        navBookings  = findViewById(R.id.navBookings);
        navMessages  = findViewById(R.id.navMessages);
        navProfile   = findViewById(R.id.navProfile);

        navHomeIcon      = findViewById(R.id.navHomeIcon);
        navBookingsIcon  = findViewById(R.id.navBookingsIcon);
        navMessagesIcon  = findViewById(R.id.navMessagesIcon);
        navProfileIcon   = findViewById(R.id.navProfileIcon);

        navHomeText      = findViewById(R.id.navHomeText);
        navBookingsText  = findViewById(R.id.navBookingsText);
        navMessagesText  = findViewById(R.id.navMessagesText);
        navProfileText   = findViewById(R.id.navProfileText);
    }

    // ── Load User Data ────────────────────────────────────────────────────

    private void loadUserData(FirebaseUser user) {
        String displayName = user.getDisplayName();
        String email       = user.getEmail();

        // Greeting
        if (displayName != null && !displayName.isEmpty()) {
            welcomeMessage.setText("Hello, " + firstName(displayName) + "! 👋");
            // Profile initial
            profileInitial.setText(String.valueOf(displayName.charAt(0)).toUpperCase());
        } else {
            welcomeMessage.setText("Hello! 👋");
            profileInitial.setText(email != null && !email.isEmpty()
                    ? String.valueOf(email.charAt(0)).toUpperCase() : "U");
        }

        if (userEmail != null) {
            userEmail.setText(getString(R.string.find_services));
        }

        // Profile avatar click → show logout dialog
        if (profileAvatar != null) {
            profileAvatar.setOnClickListener(v -> showProfileMenu(displayName, email));
        }

        // Notification
        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v ->
                    Toast.makeText(this, "No new notifications", Toast.LENGTH_SHORT).show());
        }
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "User";
        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }

    // ── Category Click Listeners ──────────────────────────────────────────

    private void setupCategoryClickListeners() {
        View.OnClickListener categoryClick = v -> {
            String name = "";
            int id = v.getId();
            if      (id == R.id.catPlumbing)   name = "Plumbing";
            else if (id == R.id.catElectrical) name = "Electrical";
            else if (id == R.id.catCleaning)   name = "Cleaning";
            else if (id == R.id.catRepair)     name = "Repair";
            else if (id == R.id.catAC)         name = "AC Service";
            else if (id == R.id.catPainting)   name = "Painting";
            else if (id == R.id.catCarpentry)  name = "Carpentry";
            else if (id == R.id.catMore)       name = "All Services";
            Toast.makeText(this, name + " — coming soon!", Toast.LENGTH_SHORT).show();
        };

        catPlumbing.setOnClickListener(categoryClick);
        catElectrical.setOnClickListener(categoryClick);
        catCleaning.setOnClickListener(categoryClick);
        catRepair.setOnClickListener(categoryClick);
        catAC.setOnClickListener(categoryClick);
        catPainting.setOnClickListener(categoryClick);
        catCarpentry.setOnClickListener(categoryClick);
        catMore.setOnClickListener(categoryClick);
    }

    // ── Quick Action Listeners ────────────────────────────────────────────

    private void setupQuickActionListeners() {
        quickBookService.setOnClickListener(v ->
                Toast.makeText(this, "Book a Service — coming soon!", Toast.LENGTH_SHORT).show());

        quickEmergency.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("🚨 Emergency Help")
                        .setMessage("Do you need immediate assistance?\n\nOur team is available 24/7.")
                        .setPositiveButton("Call Now", (d, w) ->
                                Toast.makeText(this, "Calling emergency support…", Toast.LENGTH_SHORT).show())
                        .setNegativeButton("Cancel", null)
                        .show());

        quickTrackBooking.setOnClickListener(v ->
                Toast.makeText(this, "Track Booking — coming soon!", Toast.LENGTH_SHORT).show());
    }

    // ── Featured Card Listeners ───────────────────────────────────────────

    private void setupFeaturedCardListeners() {
        View.OnClickListener featuredClick = v -> {
            String msg = "";
            int id = v.getId();
            if      (id == R.id.featuredCard1) msg = "Home Deep Cleaning";
            else if (id == R.id.featuredCard2) msg = "AC Installation";
            else if (id == R.id.featuredCard3) msg = "Plumbing Fix";
            Toast.makeText(this, msg + " — coming soon!", Toast.LENGTH_SHORT).show();
        };

        featuredCard1.setOnClickListener(featuredClick);
        featuredCard2.setOnClickListener(featuredClick);
        featuredCard3.setOnClickListener(featuredClick);
    }

    // ── Search Bar ────────────────────────────────────────────────────────

    private void setupSearchBar() {
        if (searchBar != null) {
            searchBar.setOnClickListener(v ->
                    Toast.makeText(this, "Search — coming soon!", Toast.LENGTH_SHORT).show());
        }
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────

    private void setupBottomNav() {
        setNavSelected(0); // Home selected by default

        navHome.setOnClickListener(v -> setNavSelected(0));

        navBookings.setOnClickListener(v -> {
            setNavSelected(1);
            Toast.makeText(this, "Bookings — coming soon!", Toast.LENGTH_SHORT).show();
        });

        navMessages.setOnClickListener(v -> {
            setNavSelected(2);
            Toast.makeText(this, "Messages — coming soon!", Toast.LENGTH_SHORT).show();
        });

        navProfile.setOnClickListener(v -> {
            setNavSelected(3);
            showProfileMenu(
                    mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getDisplayName() : null,
                    mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null
            );
        });
    }

    private void setNavSelected(int index) {
        int activeColor   = ContextCompat.getColor(this, R.color.primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);

        // Reset all
        navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactiveColor));
        navHomeText.setTextColor(inactiveColor);
        navBookingsText.setTextColor(inactiveColor);
        navMessagesText.setTextColor(inactiveColor);
        navProfileText.setTextColor(inactiveColor);

        // Activate selected
        switch (index) {
            case 0:
                navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                navHomeText.setTextColor(activeColor);
                break;
            case 1:
                navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                navBookingsText.setTextColor(activeColor);
                break;
            case 2:
                navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                navMessagesText.setTextColor(activeColor);
                break;
            case 3:
                navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(activeColor));
                navProfileText.setTextColor(activeColor);
                break;
        }
    }

    // ── Profile Menu ──────────────────────────────────────────────────────

    private void showProfileMenu(String name, String email) {
        String title = (name != null && !name.isEmpty()) ? name : "Profile";
        String msg   = (email != null) ? email : "";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    redirectToLogin();
                })
                .setNegativeButton("Close", null)
                .show();
    }


    // ── List Your Business Card ───────────────────────────────────────────

    private void setupListBusinessCard(FirebaseUser user) {
        if (listBusinessSection == null || btnListBusinessStart == null) return;

        // Default: hide until role is confirmed as normal user
        listBusinessSection.setVisibility(View.GONE);

        String uid = user.getUid();

        // Step 1: Check if admin
        RoleManager.checkAdminRole(uid, new RoleManager.RoleCheckCallback() {
            @Override
            public void onAdminVerified(String adminRoleId, String roleName) {
                // Admin → keep card hidden
                listBusinessSection.setVisibility(View.GONE);
            }

            @Override
            public void onNotAdmin() {
                // Step 2: Check if already a provider
                checkIfProvider(uid);
            }

            @Override
            public void onError(String message) {
                // On error, default to showing the card (safe fallback)
                showListBusinessCard();
            }
        });
    }

    private void checkIfProvider(String uid) {
        // Query provider_applications table for approved providers
        String SUPABASE_URL = BuildConfig.SUPABASE_URL;
        String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

        okhttp3.OkHttpClient http = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String path = SUPABASE_URL + "/rest/v1/provider_applications"
                + "?firebase_uid=eq." + uid
                + "&status=eq.approved"
                + "&select=id"
                + "&limit=1";

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(path)
                .get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                // Network error: show the card as fallback
                runOnUiThread(() -> showListBusinessCard());
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                runOnUiThread(() -> {
                    try {
                        org.json.JSONArray arr = new org.json.JSONArray(body);
                        if (code == 200 && arr.length() > 0) {
                            // Already an approved provider → hide card
                            listBusinessSection.setVisibility(View.GONE);
                        } else {
                            // Normal user (or pending) → show card
                            showListBusinessCard();
                        }
                    } catch (Exception e) {
                        showListBusinessCard();
                    }
                });
            }
        });
    }

    private void showListBusinessCard() {
        if (listBusinessSection == null) return;
        listBusinessSection.setVisibility(View.VISIBLE);

        // Fade-in animation on the card
        listBusinessSection.setAlpha(0f);
        listBusinessSection.animate().alpha(1f).setDuration(400).start();

        // Pulse animation on the Start button
        if (btnListBusinessStart != null) {
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            btnListBusinessStart.startAnimation(pulse);

            // Navigate to ProviderOnboardingActivity on click
            btnListBusinessStart.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProviderOnboardingActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            });

            // Also make the whole card clickable
            if (listBusinessSection.getChildCount() > 0) {
                View card = listBusinessSection.getChildAt(0);
                card.setOnClickListener(v -> btnListBusinessStart.performClick());
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
