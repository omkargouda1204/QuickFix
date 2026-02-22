package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    // ── Toolbar ───────────────────────────────────────────────────────────
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
    private TextView seeAllServices;

    // ── Quick Actions ─────────────────────────────────────────────────────
    private LinearLayout quickBookService, quickEmergency, quickTrackBooking;

    // ── Featured ──────────────────────────────────────────────────────────
    private CardView featuredCard1, featuredCard2, featuredCard3;
    private TextView seeAllFeatured;

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

        // Guard: must be logged in + email verified
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || !currentUser.isEmailVerified()) {
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
        seeAllServices = findViewById(R.id.seeAllServices);

        quickBookService   = findViewById(R.id.quickBookService);
        quickEmergency     = findViewById(R.id.quickEmergency);
        quickTrackBooking  = findViewById(R.id.quickTrackBooking);

        featuredCard1  = findViewById(R.id.featuredCard1);
        featuredCard2  = findViewById(R.id.featuredCard2);
        featuredCard3  = findViewById(R.id.featuredCard3);
        seeAllFeatured = findViewById(R.id.seeAllFeatured);

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
        seeAllServices.setOnClickListener(v ->
                Toast.makeText(this, "All Services — coming soon!", Toast.LENGTH_SHORT).show());
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
        seeAllFeatured.setOnClickListener(v ->
                Toast.makeText(this, "All Featured Services — coming soon!", Toast.LENGTH_SHORT).show());
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

    // ── Navigation ────────────────────────────────────────────────────────

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
