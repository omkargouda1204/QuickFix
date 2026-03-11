package com.example.quickfix;

import android.content.Intent;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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

    // ── Header views ─────────────────────────────────────────────────────
    private TextView  welcomeText, avatarInitial;
    private View      notificationBadge;

    // ── Quick Stats ───────────────────────────────────────────────────────
    private TextView statTotalUsers, statTotalProviders,
                     statActiveBookings, statPendingApprovals;

    // ── Promotions overview ───────────────────────────────────────────────
    private TextView dashTotalBanners, dashTotalOffers;

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

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupHeaderFromIntent();
        setupClickListeners();
        setupBottomNavigation();
        loadAdminNameFromSupabase();
        loadQuickStats();
        loadBannerOfferCounts();

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

        // Promotions overview
        dashTotalBanners = findViewById(R.id.dashTotalBanners);
        dashTotalOffers  = findViewById(R.id.dashTotalOffers);

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

        // Promotions overview — Banner card
        View cardDashBanners    = findViewById(R.id.cardDashBanners);
        View btnDashManageBanners = findViewById(R.id.btnDashManageBanners);
        if (cardDashBanners     != null) cardDashBanners.setOnClickListener(v     -> goTo(BannerManagementActivity.class));
        if (btnDashManageBanners!= null) btnDashManageBanners.setOnClickListener(v-> goTo(BannerManagementActivity.class));

        // Promotions overview — Offer card
        View cardDashOffers    = findViewById(R.id.cardDashOffers);
        View btnDashManageOffers = findViewById(R.id.btnDashManageOffers);
        if (cardDashOffers     != null) cardDashOffers.setOnClickListener(v      -> goTo(OfferManagementActivity.class));
        if (btnDashManageOffers!= null) btnDashManageOffers.setOnClickListener(v -> goTo(OfferManagementActivity.class));
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

    // ── Load Banner + Offer counts for dashboard overview ────────────────

    private void loadBannerOfferCounts() {
        loadRowCount("banners", dashTotalBanners);
        loadRowCount("offers",  dashTotalOffers);
    }

    /**
     * Fetches all rows from `table` using Thread-based OkHttp (same pattern as MainActivity).
     * Parses JSON array length and posts result to UI thread.
     */
    private void loadRowCount(String table, TextView target) {
        if (target == null) return;
        target.setText("…");
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/" + table + "?select=id";
        Log.d(TAG, "loadRowCount(" + table + ") → " + url);

        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    int    code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "[]";
                    Log.d(TAG, "loadRowCount(" + table + ") HTTP " + code
                            + " body_len=" + body.length());
                    int count = 0;
                    if (code == 200) {
                        try { count = new JSONArray(body).length(); }
                        catch (Exception ex) { Log.e(TAG, "parse error: " + ex.getMessage()); }
                    } else {
                        Log.e(TAG, "loadRowCount(" + table + ") FAILED HTTP " + code + ": " + body);
                    }
                    final int finalCount = count;
                    mainH.post(() -> {
                        if (target != null) target.setText(String.valueOf(finalCount));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "loadRowCount(" + table + ") exception: " + e.getMessage(), e);
                mainH.post(() -> { if (target != null) target.setText("—"); });
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh banner/offer counts every time admin returns to dashboard
        loadBannerOfferCounts();
        loadQuickStats();
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
