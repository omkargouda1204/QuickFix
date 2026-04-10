package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.quickfix.adapters.HomeBannerAdapter;
import com.example.quickfix.adapters.HomeOfferAdapter;
import com.example.quickfix.api.BannerApi;
import com.example.quickfix.api.OfferApi;
import com.example.quickfix.api.RetrofitProvider;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
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
    private static final long ADMIN_BANNER_AUTO_SCROLL_MS = 3500L;

    // ── Header views ─────────────────────────────────────────────────────
    private TextView  welcomeText, avatarInitial;
    private View      notificationBadge;
    private View      providerSectionBadge;
    private View      userSectionBadge;

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

    // ── Admin content preview (banners + offers) ─────────────────────────
    private BannerApi bannerApi;
    private OfferApi offerApi;
    private ViewPager2 vpAdminBanners;
    private WormDotsIndicator adminBannerDots;
    private RecyclerView rvAdminOffers;
    private ProgressBar progressAdminBanners;
    private ProgressBar progressAdminOffers;
    private TextView tvAdminBannersEmpty;
    private TextView tvAdminOffersEmpty;
    private TextView tvAdminBannersError;
    private TextView tvAdminOffersError;
    private View layoutAdminBannersError;
    private View layoutAdminOffersError;
    private MaterialButton btnRetryAdminBanners;
    private MaterialButton btnRetryAdminOffers;
    private HomeBannerAdapter adminBannerAdapter;
    private HomeOfferAdapter adminOfferAdapter;
    private final List<Banner> adminBanners = new ArrayList<>();
    private final List<Offer> adminOffers = new ArrayList<>();
    private Runnable adminBannerAutoSlideRunnable;

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ADMIN", "Opened");
        setContentView(R.layout.activity_admin_dashboard);

        mAuth = FirebaseAuth.getInstance();
        locationManager = new AppLocationManager(this);
        bannerApi = RetrofitProvider.get().create(BannerApi.class);
        offerApi = RetrofitProvider.get().create(OfferApi.class);

        bindViews();
        setupHeaderFromIntent();
        setupClickListeners();
        setupBottomNavigation();
        setupLocationSelector();
        setupAdminHomeContent();
        loadAdminNameFromSupabase();
        loadQuickStats();
        refreshAdminHomeContent();

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
        providerSectionBadge = findViewById(R.id.providerSectionBadge);
        userSectionBadge     = findViewById(R.id.userSectionBadge);

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

        vpAdminBanners = findViewById(R.id.vpAdminBanners);
        adminBannerDots = findViewById(R.id.adminBannerDots);
        rvAdminOffers = findViewById(R.id.rvAdminOffers);
        progressAdminBanners = findViewById(R.id.progressAdminBanners);
        progressAdminOffers = findViewById(R.id.progressAdminOffers);
        tvAdminBannersEmpty = findViewById(R.id.tvAdminBannersEmpty);
        tvAdminOffersEmpty = findViewById(R.id.tvAdminOffersEmpty);
        tvAdminBannersError = findViewById(R.id.tvAdminBannersError);
        tvAdminOffersError = findViewById(R.id.tvAdminOffersError);
        layoutAdminBannersError = findViewById(R.id.layoutAdminBannersError);
        layoutAdminOffersError = findViewById(R.id.layoutAdminOffersError);
        btnRetryAdminBanners = findViewById(R.id.btnRetryAdminBanners);
        btnRetryAdminOffers = findViewById(R.id.btnRetryAdminOffers);

        // Keep badges hidden until unread counts load.
        if (notificationBadge != null) notificationBadge.setVisibility(View.GONE);
        if (providerSectionBadge != null) providerSectionBadge.setVisibility(View.GONE);
        if (userSectionBadge != null) userSectionBadge.setVisibility(View.GONE);
    }

    private void setupAdminHomeContent() {
        adminBannerAdapter = new HomeBannerAdapter(this, adminBanners, banner -> {
            // Dashboard preview only; management actions remain in content management screens.
        });
        if (vpAdminBanners != null) {
            vpAdminBanners.setAdapter(adminBannerAdapter);
        }

        adminOfferAdapter = new HomeOfferAdapter(this, adminOffers, offer -> {
            // Dashboard preview only.
        });
        if (rvAdminOffers != null) {
            rvAdminOffers.setLayoutManager(new LinearLayoutManager(this));
            rvAdminOffers.setAdapter(adminOfferAdapter);
        }

        if (btnRetryAdminBanners != null) {
            btnRetryAdminBanners.setOnClickListener(v -> loadAdminBanners());
        }
        if (btnRetryAdminOffers != null) {
            btnRetryAdminOffers.setOnClickListener(v -> loadAdminOffers());
        }
    }

    private void refreshAdminHomeContent() {
        loadAdminBanners();
        loadAdminOffers();
    }

    private void loadAdminBanners() {
        if (bannerApi == null) return;
        setAdminBannersLoading(true);

        bannerApi.getAllBanners(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "*",
                "created_at.desc"
        ).enqueue(new Callback<List<Banner>>() {
            @Override
            public void onResponse(Call<List<Banner>> call, Response<List<Banner>> response) {
                if (!response.isSuccessful()) {
                    showAdminBannersError("Failed to load banners");
                    return;
                }

                List<Banner> payload = response.body() == null ? new ArrayList<>() : response.body();
                List<Banner> filtered = filterAdminBanners(payload);

                adminBanners.clear();
                adminBanners.addAll(filtered);
                if (adminBannerAdapter != null) adminBannerAdapter.setItems(adminBanners);

                if (adminBanners.isEmpty()) {
                    showAdminBannersEmpty();
                } else {
                    showAdminBannersContent();
                }
            }

            @Override
            public void onFailure(Call<List<Banner>> call, Throwable t) {
                Log.w(TAG, "loadAdminBanners failed", t);
                showAdminBannersError("Network error while loading banners");
            }
        });
    }

    private void loadAdminOffers() {
        if (offerApi == null) return;
        setAdminOffersLoading(true);

        offerApi.getAllOffers(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "*",
                "created_at.desc"
        ).enqueue(new Callback<List<Offer>>() {
            @Override
            public void onResponse(Call<List<Offer>> call, Response<List<Offer>> response) {
                if (!response.isSuccessful()) {
                    showAdminOffersError("Failed to load offers");
                    return;
                }

                List<Offer> payload = response.body() == null ? new ArrayList<>() : response.body();
                List<Offer> filtered = filterAdminOffers(payload);

                adminOffers.clear();
                adminOffers.addAll(filtered);
                if (adminOfferAdapter != null) adminOfferAdapter.setItems(adminOffers);

                if (adminOffers.isEmpty()) {
                    showAdminOffersEmpty();
                } else {
                    showAdminOffersContent();
                }
            }

            @Override
            public void onFailure(Call<List<Offer>> call, Throwable t) {
                Log.w(TAG, "loadAdminOffers failed", t);
                showAdminOffersError("Network error while loading offers");
            }
        });
    }

    private List<Banner> filterAdminBanners(List<Banner> source) {
        List<Banner> out = new ArrayList<>();
        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        String uid = user != null ? safe(user.getUid()) : "";
        String email = user != null ? safe(user.getEmail()) : "";

        for (Banner banner : source) {
            if (!isBannerVisible(banner)) continue;
            if (!isCreatedByCurrentAdmin(
                    banner != null ? banner.createdBy : "",
                    banner != null ? banner.createdByUid : "",
                    banner != null ? banner.createdByEmail : "",
                    uid,
                    email
            )) continue;
            normalizeBannerImage(banner);
            out.add(banner);
        }
        return out;
    }

    private List<Offer> filterAdminOffers(List<Offer> source) {
        List<Offer> out = new ArrayList<>();
        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        String uid = user != null ? safe(user.getUid()) : "";
        String email = user != null ? safe(user.getEmail()) : "";

        for (Offer offer : source) {
            if (!isOfferVisible(offer)) continue;
            if (!isCreatedByCurrentAdmin(
                    offer != null ? offer.createdBy : "",
                    offer != null ? offer.createdByUid : "",
                    offer != null ? offer.createdByEmail : "",
                    uid,
                    email
            )) continue;
            out.add(offer);
        }
        return out;
    }

    private boolean isCreatedByCurrentAdmin(String createdBy,
                                            String createdByUid,
                                            String createdByEmail,
                                            String currentUid,
                                            String currentEmail) {
        String by = safe(createdBy);
        String byUid = safe(createdByUid);
        String byEmail = safe(createdByEmail);

        // Backward compatibility: older rows may not carry creator metadata.
        if (by.isEmpty() && byUid.isEmpty() && byEmail.isEmpty()) {
            return true;
        }

        if (!currentUid.isEmpty() && (currentUid.equalsIgnoreCase(byUid) || currentUid.equalsIgnoreCase(by))) {
            return true;
        }
        if (!currentEmail.isEmpty() && (currentEmail.equalsIgnoreCase(byEmail) || currentEmail.equalsIgnoreCase(by))) {
            return true;
        }
        return false;
    }

    private boolean hasStarted(String date) {
        if (TextUtils.isEmpty(date)) return true;
        String normalized = date.trim().length() >= 10 ? date.trim().substring(0, 10) : date.trim();
        try {
            Date start = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(normalized);
            if (start == null) return true;

            java.util.Calendar startDay = java.util.Calendar.getInstance();
            startDay.setTime(start);
            startDay.set(java.util.Calendar.HOUR_OF_DAY, 0);
            startDay.set(java.util.Calendar.MINUTE, 0);
            startDay.set(java.util.Calendar.SECOND, 0);
            startDay.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar today = java.util.Calendar.getInstance();
            today.set(java.util.Calendar.HOUR_OF_DAY, 0);
            today.set(java.util.Calendar.MINUTE, 0);
            today.set(java.util.Calendar.SECOND, 0);
            today.set(java.util.Calendar.MILLISECOND, 0);

            return !startDay.after(today);
        } catch (Exception ignore) {
            return true;
        }
    }

    private boolean isExpired(String date) {
        if (TextUtils.isEmpty(date)) return false;
        String normalized = date.trim().length() >= 10 ? date.trim().substring(0, 10) : date.trim();
        try {
            Date expiry = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(normalized);
            if (expiry == null) return false;

            java.util.Calendar expiryDay = java.util.Calendar.getInstance();
            expiryDay.setTime(expiry);
            expiryDay.set(java.util.Calendar.HOUR_OF_DAY, 23);
            expiryDay.set(java.util.Calendar.MINUTE, 59);
            expiryDay.set(java.util.Calendar.SECOND, 59);
            expiryDay.set(java.util.Calendar.MILLISECOND, 999);
            return expiryDay.getTimeInMillis() < System.currentTimeMillis();
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean isBannerVisible(Banner banner) {
        return banner != null && banner.isActive && hasStarted(banner.startDate) && !isExpired(banner.expiryDate);
    }

    private boolean isOfferVisible(Offer offer) {
        return offer != null && offer.isActive && !isExpired(offer.expiryDate);
    }

    private void normalizeBannerImage(Banner banner) {
        if (banner == null || TextUtils.isEmpty(banner.imageUrl)) return;
        if (!banner.imageUrl.startsWith("http")) {
            banner.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", banner.imageUrl);
        }
    }

    private void setAdminBannersLoading(boolean loading) {
        if (progressAdminBanners != null) progressAdminBanners.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (vpAdminBanners != null) vpAdminBanners.setVisibility(loading ? View.GONE : vpAdminBanners.getVisibility());
        if (tvAdminBannersEmpty != null && loading) tvAdminBannersEmpty.setVisibility(View.GONE);
        if (layoutAdminBannersError != null && loading) layoutAdminBannersError.setVisibility(View.GONE);
        if (loading) stopAdminBannerAutoSlide();
    }

    private void setAdminOffersLoading(boolean loading) {
        if (progressAdminOffers != null) progressAdminOffers.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvAdminOffers != null && loading) rvAdminOffers.setVisibility(View.GONE);
        if (tvAdminOffersEmpty != null && loading) tvAdminOffersEmpty.setVisibility(View.GONE);
        if (layoutAdminOffersError != null && loading) layoutAdminOffersError.setVisibility(View.GONE);
    }

    private void showAdminBannersContent() {
        if (progressAdminBanners != null) progressAdminBanners.setVisibility(View.GONE);
        if (layoutAdminBannersError != null) layoutAdminBannersError.setVisibility(View.GONE);
        if (tvAdminBannersEmpty != null) tvAdminBannersEmpty.setVisibility(View.GONE);
        if (vpAdminBanners != null) vpAdminBanners.setVisibility(View.VISIBLE);

        if (adminBannerDots != null) {
            if (adminBanners.size() > 1 && vpAdminBanners != null) {
                adminBannerDots.setVisibility(View.VISIBLE);
                try {
                    adminBannerDots.setViewPager2(vpAdminBanners);
                } catch (Exception e) {
                    Log.w(TAG, "adminBannerDots bind failed: " + e.getMessage());
                }
            } else {
                adminBannerDots.setVisibility(View.GONE);
            }
        }

        startAdminBannerAutoSlide();
    }

    private void showAdminBannersEmpty() {
        if (progressAdminBanners != null) progressAdminBanners.setVisibility(View.GONE);
        if (vpAdminBanners != null) vpAdminBanners.setVisibility(View.GONE);
        if (adminBannerDots != null) adminBannerDots.setVisibility(View.GONE);
        if (layoutAdminBannersError != null) layoutAdminBannersError.setVisibility(View.GONE);
        if (tvAdminBannersEmpty != null) tvAdminBannersEmpty.setVisibility(View.VISIBLE);
        stopAdminBannerAutoSlide();
    }

    private void showAdminBannersError(String message) {
        if (progressAdminBanners != null) progressAdminBanners.setVisibility(View.GONE);
        if (vpAdminBanners != null) vpAdminBanners.setVisibility(View.GONE);
        if (adminBannerDots != null) adminBannerDots.setVisibility(View.GONE);
        if (tvAdminBannersEmpty != null) tvAdminBannersEmpty.setVisibility(View.GONE);
        if (tvAdminBannersError != null) tvAdminBannersError.setText(TextUtils.isEmpty(message) ? "Failed to load banners" : message);
        if (layoutAdminBannersError != null) layoutAdminBannersError.setVisibility(View.VISIBLE);
        stopAdminBannerAutoSlide();
    }

    private void showAdminOffersContent() {
        if (progressAdminOffers != null) progressAdminOffers.setVisibility(View.GONE);
        if (layoutAdminOffersError != null) layoutAdminOffersError.setVisibility(View.GONE);
        if (tvAdminOffersEmpty != null) tvAdminOffersEmpty.setVisibility(View.GONE);
        if (rvAdminOffers != null) rvAdminOffers.setVisibility(View.VISIBLE);
    }

    private void showAdminOffersEmpty() {
        if (progressAdminOffers != null) progressAdminOffers.setVisibility(View.GONE);
        if (rvAdminOffers != null) rvAdminOffers.setVisibility(View.GONE);
        if (layoutAdminOffersError != null) layoutAdminOffersError.setVisibility(View.GONE);
        if (tvAdminOffersEmpty != null) tvAdminOffersEmpty.setVisibility(View.VISIBLE);
    }

    private void showAdminOffersError(String message) {
        if (progressAdminOffers != null) progressAdminOffers.setVisibility(View.GONE);
        if (rvAdminOffers != null) rvAdminOffers.setVisibility(View.GONE);
        if (tvAdminOffersEmpty != null) tvAdminOffersEmpty.setVisibility(View.GONE);
        if (tvAdminOffersError != null) tvAdminOffersError.setText(TextUtils.isEmpty(message) ? "Failed to load offers" : message);
        if (layoutAdminOffersError != null) layoutAdminOffersError.setVisibility(View.VISIBLE);
    }

    private void startAdminBannerAutoSlide() {
        stopAdminBannerAutoSlide();
        if (vpAdminBanners == null || adminBanners.size() < 2) return;

        adminBannerAutoSlideRunnable = new Runnable() {
            @Override
            public void run() {
                if (vpAdminBanners == null || adminBanners.isEmpty()) return;
                int next = (vpAdminBanners.getCurrentItem() + 1) % adminBanners.size();
                vpAdminBanners.setCurrentItem(next, true);
                mainH.postDelayed(this, ADMIN_BANNER_AUTO_SCROLL_MS);
            }
        };
        mainH.postDelayed(adminBannerAutoSlideRunnable, ADMIN_BANNER_AUTO_SCROLL_MS);
    }

    private void stopAdminBannerAutoSlide() {
        if (adminBannerAutoSlideRunnable != null) {
            mainH.removeCallbacks(adminBannerAutoSlideRunnable);
            adminBannerAutoSlideRunnable = null;
        }
    }

    private void setupLocationSelector() {
        if (tvAdminSelectedLocation != null) {
            String savedLabel = locationManager.getSavedLocationLabel();
            tvAdminSelectedLocation.setText(savedLabel.isEmpty() ? "All Cities" : savedLabel);
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
                        String label = AppLocationManager.composeLocationLabel(suggestion.city, suggestion.area);
                        tvAdminSelectedLocation.setText(label);
                        Toast.makeText(this, "Admin filter: " + label,
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
                goTo(AdminProviderManagementActivity.class);
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

        // Provider count from provider_profiles table.
        loadStatCount("/rest/v1/provider_profiles?select=id",
                statTotalProviders);

        // Load pending approvals count — from provider_applications
        loadStatCount("/rest/v1/provider_applications?select=id&status=eq.pending",
                statPendingApprovals);

        // Active bookings count
        loadStatCount("/rest/v1/bookings?select=id",
                statActiveBookings);
    }

    private interface CountCallback {
        void onResult(int count);
    }

    private void loadNotificationBadges() {
        // Red dot on bell icon
        loadCountAsync("/rest/v1/admin_notifications?select=id&is_read=eq.false", count -> {
            if (notificationBadge != null) {
                notificationBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Red dot for provider section
        loadCountAsync("/rest/v1/admin_notifications?select=id&is_read=eq.false&type=in.(provider,provider_application)", count -> {
            if (providerSectionBadge != null) {
                providerSectionBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }
        });

        // Red dot for user section
        loadCountAsync("/rest/v1/admin_notifications?select=id&is_read=eq.false&type=in.(user,user_registration,register,welcome)", count -> {
            if (userSectionBadge != null) {
                userSectionBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void loadCountAsync(String path, CountCallback callback) {
        if (callback == null) return;
        String url = BuildConfig.SUPABASE_URL + path + "&limit=1";

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept",        "application/json")
                .addHeader("Prefer",        "count=exact")
                .build();

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "loadCountAsync failed: " + e.getMessage());
                runOnUiThread(() -> callback.onResult(0));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                int count = extractCountFromResponse(response);
                runOnUiThread(() -> callback.onResult(Math.max(0, count)));
            }
        });
    }

    private int extractCountFromResponse(okhttp3.Response response) {
        try {
            String body = response.body() != null ? response.body().string() : "[]";
            String range = response.header("Content-Range", "");
            if (range.contains("/")) {
                String countStr = range.substring(range.lastIndexOf('/') + 1).trim();
                return Integer.parseInt(countStr);
            }
            // Fallback for proxies/configs that omit Content-Range.
            return new org.json.JSONArray(body).length();
        } catch (Exception ignored) {
        } finally {
            response.close();
        }
        return 0;
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

        http.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                Log.w(TAG, "loadStatCount failed: " + e.getMessage());
                runOnUiThread(() -> targetView.setText("0"));
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) {
                int count = extractCountFromResponse(response);
                runOnUiThread(() -> targetView.setText(String.valueOf(Math.max(0, count))));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQuickStats();
        loadNotificationBadges();
        refreshAdminHomeContent();
        if (tvAdminSelectedLocation != null) {
            String savedLabel = locationManager.getSavedLocationLabel();
            tvAdminSelectedLocation.setText(savedLabel.isEmpty() ? "All Cities" : savedLabel);
        }
    }

    @Override
    protected void onPause() {
        stopAdminBannerAutoSlide();
        super.onPause();
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
