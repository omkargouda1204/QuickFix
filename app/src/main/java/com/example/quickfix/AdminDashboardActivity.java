package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * AdminDashboardActivity — Premium Material Design 3 Admin Panel.
 *
 * Features:
 *  ✦ Gradient app bar (blue → indigo) with notification + avatar
 *  ✦ Welcome banner with admin name loaded from Supabase
 *  ✦ Quick Stats: Total Users / Providers / Bookings / Pending
 *  ✦ "Open User App View" card to preview user experience
 *  ✦ 5 feature cards: Approvals, Users, Services, Content, Reports
 *  ✦ Bottom navigation bar (Dashboard, Bookings, Providers, Messages, Profile)
 *  ✦ Profile bottom sheet: name, role badge, Settings, View UI, Logout
 *  ✦ Logout ONLY accessible from bottom sheet — not on main screen
 */
public class AdminDashboardActivity extends BaseActivity {

    private static final String TAG = "AdminDashboard";
    private static final int LOCATION_PERMISSION_REQ = 2101;

    // ── Header views ─────────────────────────────────────────────────────
    private TextView  welcomeText, avatarInitial;
    private View      notificationBadge;

    // ── Quick Stats ───────────────────────────────────────────────────────
    private TextView statTotalUsers, statTotalProviders,
                     statActiveBookings, statPendingApprovals;

    // ── Feature card buttons ──────────────────────────────────────────────
    private MaterialButton btnBusinessApprovals, btnUserManagement,
                           btnServiceManagement, btnContentManagement,
                           btnViewReports;

    // ── Clickable card wrappers ───────────────────────────────────────────
    private View cardOpenUserView, cardBusinessApprovals, cardUserManagement,
                 cardServiceManagement, cardContentManagement, cardReports;

    // ── Bottom nav ────────────────────────────────────────────────────────
    private BottomNavigationView bottomNav;

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;

    // ── HTTP ──────────────────────────────────────────────────────────────
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler mainH = new Handler(Looper.getMainLooper());

    // ── State ─────────────────────────────────────────────────────────────
    private String adminName  = "Admin";
    private String adminEmail = "";

    // ── Admin location selector ───────────────────────────────────────────
    private LinearLayout adminLocationSelector;
    private TextView tvAdminSelectedLocation;
    private AppLocationManager locationManager;

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();
        locationManager = new AppLocationManager(this);

        bindViews();
        setupHeaderFromIntent();
        setupClickListeners();
        setupBottomNavigation();
        setupLocationSelector();
        loadAdminNameFromSupabase();
        loadQuickStats();

        // Handle back press with AndroidX dispatcher (replaces deprecated onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new AlertDialog.Builder(AdminDashboardActivity.this)
                        .setTitle("Exit Admin Panel?")
                        .setMessage("Press the profile icon to logout, or stay in admin dashboard.")
                        .setPositiveButton("Logout", (d, w) -> confirmLogout())
                        .setNegativeButton("Stay", null)
                        .show();
            }
        });
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        welcomeText          = findViewById(R.id.welcomeText);
        avatarInitial        = findViewById(R.id.avatarInitial);
        notificationBadge    = findViewById(R.id.notificationBadge);

        statTotalUsers       = findViewById(R.id.statTotalUsers);
        statTotalProviders   = findViewById(R.id.statTotalProviders);
        statActiveBookings   = findViewById(R.id.statActiveBookings);
        statPendingApprovals = findViewById(R.id.statPendingApprovals);

        cardOpenUserView       = findViewById(R.id.cardOpenUserView);
        cardBusinessApprovals  = findViewById(R.id.cardBusinessApprovals);
        cardUserManagement     = findViewById(R.id.cardUserManagement);
        cardServiceManagement  = findViewById(R.id.cardServiceManagement);
        cardContentManagement  = findViewById(R.id.cardContentManagement);
        cardReports            = findViewById(R.id.cardReports);

        btnBusinessApprovals   = findViewById(R.id.btnBusinessApprovals);
        btnUserManagement      = findViewById(R.id.btnUserManagement);
        btnServiceManagement   = findViewById(R.id.btnServiceManagement);
        btnContentManagement   = findViewById(R.id.btnContentManagement);
        btnViewReports         = findViewById(R.id.btnViewReports);

        bottomNav = findViewById(R.id.adminBottomNav);

        adminLocationSelector = findViewById(R.id.adminLocationSelector);
        tvAdminSelectedLocation = findViewById(R.id.tvAdminSelectedLocation);
    }

    private void setupLocationSelector() {
        if (tvAdminSelectedLocation != null) {
            String saved = locationManager.getSavedCity();
            tvAdminSelectedLocation.setText(saved == null || saved.isEmpty() ? "All Cities" : saved);
        }
        if (adminLocationSelector != null) {
            adminLocationSelector.setOnClickListener(v -> LocationSearchBottomSheet.show(
                    this,
                    locationManager,
                    new LocationSearchBottomSheet.PermissionDelegate() {
                        @Override
                        public boolean hasLocationPermission() {
                            return ContextCompat.checkSelfPermission(AdminDashboardActivity.this,
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED;
                        }

                        @Override
                        public void requestLocationPermission() {
                            ActivityCompat.requestPermissions(AdminDashboardActivity.this,
                                    new String[]{
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                    },
                                    LOCATION_PERMISSION_REQ);
                        }
                    },
                    suggestion -> {
                        if (tvAdminSelectedLocation == null) {
                            return;
                        }
                        if (suggestion == null || suggestion.city == null || suggestion.city.trim().isEmpty()) {
                            tvAdminSelectedLocation.setText("All Cities");
                            Toast.makeText(this, "Admin filter cleared", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        tvAdminSelectedLocation.setText(suggestion.displayTitle());
                        Toast.makeText(this, "Admin filter: " + suggestion.displayTitle(),
                                Toast.LENGTH_SHORT).show();
                    }));
        }
    }

    // ── Populate header immediately from Intent extras ───────────────────

    private void setupHeaderFromIntent() {
        FirebaseUser user = mAuth.getCurrentUser();

        // Prefer displayName from Firebase (set during signup)
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                adminName = user.getDisplayName();
            } else if (user.getEmail() != null) {
                adminEmail = user.getEmail();
                adminName  = user.getEmail().split("@")[0]; // fallback: part before @
            }
        }

        // Allow override via Intent extra (e.g. passed from LoginActivity)
        String intentName = getIntent().getStringExtra("admin_name");
        if (intentName != null && !intentName.isEmpty()) {
            adminName = intentName;
        }

        applyAdminName(adminName);
    }

    private void applyAdminName(String name) {
        String first = firstName(name);
        if (welcomeText  != null) welcomeText.setText("Welcome back, " + first + " 👋");
        if (avatarInitial != null) {
            String initial = (name != null && !name.isEmpty())
                    ? String.valueOf(name.charAt(0)).toUpperCase() : "A";
            avatarInitial.setText(initial);
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────

    private void setupClickListeners() {
        // Header: notifications → open AdminNotificationsActivity
        View btnNotif = findViewById(R.id.btnNotifications);
        if (btnNotif != null) btnNotif.setOnClickListener(v ->
                startActivity(new Intent(this, AdminNotificationsActivity.class)));

        // Header: avatar → bottom sheet
        View btnAvatar = findViewById(R.id.btnAdminAvatar);
        if (btnAvatar != null) btnAvatar.setOnClickListener(v -> showProfileBottomSheet());

        // Open User App card
        if (cardOpenUserView != null) cardOpenUserView.setOnClickListener(v -> openUserView());

        // Feature cards (whole card is clickable)
        if (cardBusinessApprovals != null)
            cardBusinessApprovals.setOnClickListener(v -> goTo(BusinessApprovalActivity.class));
        if (cardUserManagement != null)
            cardUserManagement.setOnClickListener(v -> goTo(UserManagementActivity.class));
        if (cardServiceManagement != null)
            cardServiceManagement.setOnClickListener(v -> goTo(ServiceManagementActivity.class));
        if (cardContentManagement != null)
            cardContentManagement.setOnClickListener(v -> goTo(ContentManagementActivity.class));
        if (cardReports != null)
            cardReports.setOnClickListener(v -> goTo(ReportsAnalyticsActivity.class));

        // Feature card buttons (individual buttons also work)
        if (btnBusinessApprovals != null)
            btnBusinessApprovals.setOnClickListener(v -> goTo(BusinessApprovalActivity.class));
        if (btnUserManagement != null)
            btnUserManagement.setOnClickListener(v -> goTo(UserManagementActivity.class));
        if (btnServiceManagement != null)
            btnServiceManagement.setOnClickListener(v -> goTo(ServiceManagementActivity.class));
        if (btnContentManagement != null)
            btnContentManagement.setOnClickListener(v -> goTo(ContentManagementActivity.class));
        if (btnViewReports != null)
            btnViewReports.setOnClickListener(v -> goTo(ReportsAnalyticsActivity.class));
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────

    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        bottomNav.setSelectedItemId(R.id.nav_dashboard);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                // Already here
                return true;
            } else if (id == R.id.nav_bookings) {
                goTo(AdminBookingsActivity.class);
                return true;
            } else if (id == R.id.nav_providers) {
                goTo(BusinessApprovalActivity.class);
                return true;
            } else if (id == R.id.nav_messages) {
                Toast.makeText(this, "Messages — coming soon", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                showProfileBottomSheet();
                return true;
            }
            return false;
        });
    }

    // ── Profile Bottom Sheet ──────────────────────────────────────────────

    private void showProfileBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.Theme_QuickFix_BottomSheet);

        View sheetView = LayoutInflater.from(this)
                .inflate(R.layout.bottom_sheet_admin_profile, null);

        // Set name + initial in bottom sheet
        TextView bsName    = sheetView.findViewById(R.id.bsAdminName);
        TextView bsInitial = sheetView.findViewById(R.id.bsAvatarInitial);
        if (bsName    != null) bsName.setText(adminName);
        if (bsInitial != null) {
            String initial = (!adminName.isEmpty())
                    ? String.valueOf(adminName.charAt(0)).toUpperCase() : "A";
            bsInitial.setText(initial);
        }

        // Settings
        LinearLayout bsSettings = sheetView.findViewById(R.id.bsSettingsOption);
        if (bsSettings != null) bsSettings.setOnClickListener(v -> {
            sheet.dismiss();
            Intent intent = new Intent(this, AdminSettingsActivity.class);
            startActivity(intent);
        });

        // View User Interface
        LinearLayout bsUserView = sheetView.findViewById(R.id.bsViewUserOption);
        if (bsUserView != null) bsUserView.setOnClickListener(v -> {
            sheet.dismiss();
            openUserView();
        });

        // Logout
        LinearLayout bsLogout = sheetView.findViewById(R.id.bsLogoutOption);
        if (bsLogout != null) bsLogout.setOnClickListener(v -> {
            sheet.dismiss();
            confirmLogout();
        });

        sheet.setContentView(sheetView);
        sheet.show();
    }

    // ── Open User App View ────────────────────────────────────────────────

    private void openUserView() {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("from_admin", true);
        startActivity(i);
    }

    // ── Load admin name from Supabase ─────────────────────────────────────

    private void loadAdminNameFromSupabase() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String uid   = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";

        SupabaseClient.getUserRoleWithEmail(uid, email,
                new SupabaseClient.RoleInfoCallback() {
                    @Override
                    public void onSuccess(SupabaseClient.RoleInfo info) {
                        // Role info loaded — now fetch name via ApiClient
                        ApiClient.getUserByEmail(email, new ApiClient.ApiCallback() {
                            @Override
                            public void onSuccess(org.json.JSONObject response) {
                                String name = response.optString("name", "").trim();
                                if (!name.isEmpty()) {
                                    adminName = name;
                                    applyAdminName(adminName);
                                    Log.d(TAG, "Admin name loaded: " + adminName);
                                }
                            }
                            @Override
                            public void onError(String errorMessage) {
                                Log.w(TAG, "Could not load admin name: " + errorMessage);
                                // Keep the name set from Firebase / Intent
                            }
                        });
                    }
                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "Role check: " + message);
                    }
                });
    }

    // ── Load quick stats from Supabase ────────────────────────────────────

    private void loadQuickStats() {
        // Load total users count
        loadStatCount("/rest/v1/users?select=id&is_user=eq.true",
                statTotalUsers);

        // Load service providers count
        loadStatCount("/rest/v1/users?select=id&is_service_provider=eq.true",
                statTotalProviders);

        // Load pending approvals count — from provider_applications
        loadStatCount("/rest/v1/provider_applications?select=id&status=eq.pending",
                statPendingApprovals);

        // Active bookings — set placeholder (bookings table depends on schema)
        if (statActiveBookings != null) statActiveBookings.setText("—");
    }

    /**
     * Generic stat counter: queries a Supabase endpoint with
     * "Prefer: count=exact" and reads the Content-Range header.
     */
    private void loadStatCount(String path, TextView targetView) {
        if (targetView == null) return;

        String url = BuildConfig.SUPABASE_URL + path + "&limit=1";

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept",        "application/json")
                .addHeader("Prefer",        "count=exact")
                .build();

        new okhttp3.OkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "loadStatCount failed: " + e.getMessage());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response)
                    throws java.io.IOException {
                // PostgREST returns count in Content-Range: 0-0/COUNT
                String range = response.header("Content-Range");
                response.close();
                if (range != null && range.contains("/")) {
                    String countStr = range.split("/")[1].trim();
                    runOnUiThread(() -> {
                        try {
                            int count = Integer.parseInt(countStr);
                            targetView.setText(String.valueOf(count));
                        } catch (NumberFormatException ex) {
                            targetView.setText("—");
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQuickStats();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted. Tap location again.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Navigation helper ─────────────────────────────────────────────────

    private void goTo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    // ── Logout ────────────────────────────────────────────────────────────

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout from the admin panel?")
                .setPositiveButton("Logout", (d, w) -> {
                    mAuth.signOut();
                    Intent i = new Intent(this, LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String firstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "Admin";
        return fullName.trim().split("\\s+")[0];
    }
}
