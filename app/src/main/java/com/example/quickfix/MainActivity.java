package com.example.quickfix;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.quickfix.adapters.SearchSuggestionsAdapter;
import com.example.quickfix.adapters.HomeBannerAdapter;
import com.example.quickfix.adapters.HomeOfferAdapter;
import com.example.quickfix.api.BannerApi;
import com.example.quickfix.api.OfferApi;
import com.example.quickfix.api.RetrofitProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Callback;

public class MainActivity extends BaseActivity {

    private static final String TAG                      = "MainActivity";
    private static final String MAIN_CRASH               = "MAIN_CRASH";
    private static final String MAIN_FATAL               = "MAIN_FATAL";
    private static final int    LOCATION_PERMISSION_REQ  = 1001;
    private static final int    NOTIFICATION_PERMISSION_REQ = 1002;
    private static final long   HOME_AUTO_REFRESH_MS = 60_000L;

    // ── Location ──────────────────────────────────────────────────────────
    private LinearLayout        locationSelector;
    private TextView            tvSelectedLocation;
    private ImageView           ivLocationPulse;
    private View                locationAccuracyDot;
    private AppLocationManager  locationManager;

    // ── Notification ──────────────────────────────────────────────────────
    private ImageView notificationIcon;

    // ── Search ────────────────────────────────────────────────────────────
    private EditText     etSearch;
    private ImageView    btnClearSearch;
    private LinearLayout searchSuggestionsContainer;
    private RecyclerView rvSearchSuggestions;
    private SearchSuggestionsAdapter suggestionsAdapter;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;

    // ── Header ────────────────────────────────────────────────────────────
    private TextView     welcomeMessage;
    private TextView     userEmail;

    // ── Categories ────────────────────────────────────────────────────────
    private LinearLayout catPlumbing, catElectrical, catCleaning, catRepair;
    private LinearLayout catAC, catPainting, catCarpentry, catMore;

    // ── Quick Actions ─────────────────────────────────────────────────────
    private LinearLayout quickBookService, quickEmergency, quickTrackBooking;

    // ── Featured ──────────────────────────────────────────────────────────
    private CardView featuredCard1, featuredCard2, featuredCard3;

    // ── Provider Dashboard ────────────────────────────────────────────────
    private View           providerDashboardSection;

    // ── List Your Business ────────────────────────────────────────────────
    private LinearLayout   listBusinessSection;
    private MaterialButton btnListBusinessStart;

    // ── Banners & Offers (home screen) ────────────────────────────────────
    private LinearLayout       bannersSection, offersSection;
    private View               bannerContentContainer, bannerLoadingState, bannerEmptyState, bannerErrorState;
    private TextView           tvBannerErrorMessage;
    private MaterialButton     btnRetryBannerLoad;
    private ViewPager2         vpBannersCarousel;
    private RecyclerView       rvOffersStrip;
    private TabLayout          bannerDotsLayout;
    private HomeBannerAdapter  homeBannerAdapter;
    private HomeOfferAdapter   homeOfferAdapter;
    private final List<Banner> homeBanners = new ArrayList<>();
    private final List<Offer>  homeOffers  = new ArrayList<>();
    private TabLayoutMediator  bannerDotsMediator;
    private ViewPager2.OnPageChangeCallback bannerPageCallback;
    private final Handler bannerAutoSlideHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerAutoSlideRunnable;
    private final List<ObjectAnimator> skeletonAnimators = new ArrayList<>();
    private int pendingHomeLoads = 0;

    private View               offersContentContainer, offersLoadingState, offersEmptyState, offersErrorState;
    private TextView           tvOffersErrorMessage;
    private MaterialButton     btnRetryOffersLoad;

    // ── Pull-to-Refresh ───────────────────────────────────────────────────
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout homeSwipeRefresh;

    // ── Bottom Nav ────────────────────────────────────────────────────────
    private LinearLayout navHome, navBookings, navMessages, navProfile;
    private ImageView    navHomeIcon, navBookingsIcon, navMessagesIcon, navProfileIcon;
    private TextView     navHomeText, navBookingsText, navMessagesText, navProfileText;

    // ── Firebase / HTTP / API ─────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private BannerApi bannerApi;
    private OfferApi offerApi;
    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());
    private Runnable homeAutoRefreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Global crash handler
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            Log.e("GLOBAL_CRASH", "Uncaught: " + e.toString(), e);
            e.printStackTrace();
        });

        try {
            setContentView(R.layout.activity_main);
            Log.d(TAG, "Layout inflated successfully");

            // Safe Firebase initialization
            try {
                mAuth = FirebaseAuth.getInstance();
                FirebaseUser currentUser = mAuth.getCurrentUser();
                if (currentUser == null) {
                    Log.w(TAG, "No current user, redirecting to login");
                    redirectToLogin();
                    return;
                }
                Log.d(TAG, "User authenticated: " + currentUser.getUid());
            } catch (Exception e) {
                Log.e(TAG, "Firebase init failed: " + e.getMessage(), e);
                redirectToLogin();
                return;
            }

            // Safe API initialization
            try {
                bannerApi = RetrofitProvider.get().create(BannerApi.class);
                offerApi = RetrofitProvider.get().create(OfferApi.class);
                locationManager = new AppLocationManager(this);
                Log.d(TAG, "APIs initialized");
            } catch (Exception e) {
                Log.e(TAG, "API init failed: " + e.getMessage(), e);
                showSafeUI("Failed to initialize APIs");
                return;
            }

            // Safe view initialization
            try {
                initViewsSafe();
                Log.d(TAG, "Views initialized");
            } catch (Exception e) {
                Log.e(TAG, "View init failed: " + e.getMessage(), e);
                showSafeUI("Failed to load interface");
                return;
            }

            // Safe user data loading
            try {
                loadUserData(mAuth.getCurrentUser());
                Log.d(TAG, "User data loaded");
            } catch (Exception e) {
                Log.e(TAG, "User data load failed: " + e.getMessage(), e);
            }

            // Safe location setup
            try {
                setupLocationSelector();
                Log.d(TAG, "Location selector ready");
            } catch (Exception e) {
                Log.e(TAG, "Location setup failed: " + e.getMessage(), e);
            }

            // Safe search setup
            try {
                setupSearchBar();
                Log.d(TAG, "Search bar ready");
            } catch (Exception e) {
                Log.e(TAG, "Search setup failed: " + e.getMessage(), e);
            }

            // Safe category setup
            try {
                setupCategoryClickListeners();
                Log.d(TAG, "Categories ready");
            } catch (Exception e) {
                Log.e(TAG, "Category setup failed: " + e.getMessage(), e);
            }

            // Safe quick actions
            try {
                setupQuickActionListeners();
                setupFeaturedCardListeners();
                Log.d(TAG, "Quick actions ready");
            } catch (Exception e) {
                Log.e(TAG, "Quick actions setup failed: " + e.getMessage(), e);
            }

            // Safe bottom nav setup
            try {
                setupBottomNav();
                Log.d(TAG, "Bottom navigation ready");
            } catch (Exception e) {
                Log.e(TAG, "Bottom nav setup failed: " + e.getMessage(), e);
            }

            // Safe provider check
            try {
                setupListBusinessCard(mAuth.getCurrentUser());
                Log.d(TAG, "Provider check done");
            } catch (Exception e) {
                Log.e(TAG, "Provider check failed: " + e.getMessage(), e);
            }

            // Safe home content refresh
            try {
                refreshHomeContent(false);
                Log.d(TAG, "Home content refresh started");
            } catch (Exception e) {
                Log.e(TAG, "Home refresh failed: " + e.getMessage(), e);
            }

            // Safe theme sync
            try {
                ThemeManager.fetchAndSync(this);
                if (providerDashboardSection != null) {
                    providerDashboardSection.setVisibility(View.GONE);
                }
                Log.d(TAG, "Theme synced");
            } catch (Exception e) {
                Log.e(TAG, "Theme sync failed: " + e.getMessage(), e);
            }

            // Safe FCM token
            try {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                        try {
                            if (task.isSuccessful() && task.getResult() != null) {
                                SupabaseClient.saveFcmToken(user.getUid(), task.getResult());
                                Log.d(TAG, "FCM token saved");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "FCM save failed: " + e.getMessage(), e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "FCM init failed: " + e.getMessage(), e);
            }

            // Safe notification permission
            try {
                requestNotificationPermission();
                Log.d(TAG, "Notification permission requested");
            } catch (Exception e) {
                Log.e(TAG, "Notification permission failed: " + e.getMessage(), e);
            }

            Log.d(TAG, "onCreate completed successfully");

        } catch (Exception e) {
            Log.e(MAIN_FATAL, "Unexpected error in onCreate: " + e.toString(), e);
            e.printStackTrace();
            showSafeUI("Application startup error");
        }
    }

    /**
     * Safe view initialization with null checks
     */
    private void initViewsSafe() {
        try {
            locationSelector = findViewSafe(R.id.locationSelector);
            tvSelectedLocation = findViewSafe(R.id.tvSelectedLocation);
            ivLocationPulse = findViewSafe(R.id.ivLocationPulse);
            locationAccuracyDot = findViewSafe(R.id.locationAccuracyDot);
            notificationIcon = findViewSafe(R.id.notificationIcon);
            etSearch = findViewSafe(R.id.etSearch);
            btnClearSearch = findViewSafe(R.id.btnClearSearch);
            searchSuggestionsContainer = findViewSafe(R.id.searchSuggestionsContainer);
            rvSearchSuggestions = findViewSafe(R.id.rvSearchSuggestions);
            welcomeMessage = findViewSafe(R.id.welcomeMessage);
            userEmail = findViewSafe(R.id.userEmail);

            catPlumbing = findViewSafe(R.id.catPlumbing);
            catElectrical = findViewSafe(R.id.catElectrical);
            catCleaning = findViewSafe(R.id.catCleaning);
            catRepair = findViewSafe(R.id.catRepair);
            catAC = findViewSafe(R.id.catAC);
            catPainting = findViewSafe(R.id.catPainting);
            catCarpentry = findViewSafe(R.id.catCarpentry);
            catMore = findViewSafe(R.id.catMore);

            quickBookService = findViewSafe(R.id.quickBookService);
            quickEmergency = findViewSafe(R.id.quickEmergency);
            quickTrackBooking = findViewSafe(R.id.quickTrackBooking);

            featuredCard1 = findViewSafe(R.id.featuredCard1);
            featuredCard2 = findViewSafe(R.id.featuredCard2);
            featuredCard3 = findViewSafe(R.id.featuredCard3);

            providerDashboardSection = findViewSafe(R.id.providerDashboardSection);
            listBusinessSection = findViewSafe(R.id.listBusinessSection);
            btnListBusinessStart = findViewSafe(R.id.btnListBusinessStart);

            bannersSection = findViewSafe(R.id.bannersSection);
            offersSection = findViewSafe(R.id.offersSection);
            bannerContentContainer = findViewSafe(R.id.bannerContentContainer);
            bannerLoadingState = findViewSafe(R.id.bannerLoadingState);
            bannerEmptyState = findViewSafe(R.id.bannerEmptyState);
            bannerErrorState = findViewSafe(R.id.bannerErrorState);
            tvBannerErrorMessage = findViewSafe(R.id.tvBannerErrorMessage);
            btnRetryBannerLoad = findViewSafe(R.id.btnRetryBannerLoad);
            vpBannersCarousel = findViewSafe(R.id.vpBannersCarousel);
            rvOffersStrip = findViewSafe(R.id.rvOffersStrip);
            bannerDotsLayout = findViewSafe(R.id.bannerDotsLayout);

            offersContentContainer = findViewSafe(R.id.offersContentContainer);
            offersLoadingState = findViewSafe(R.id.offersLoadingState);
            offersEmptyState = findViewSafe(R.id.offersEmptyState);
            offersErrorState = findViewSafe(R.id.offersErrorState);
            tvOffersErrorMessage = findViewSafe(R.id.tvOffersErrorMessage);
            btnRetryOffersLoad = findViewSafe(R.id.btnRetryOffersLoad);

            navHome = findViewSafe(R.id.navHome);
            navBookings = findViewSafe(R.id.navBookings);
            navMessages = findViewSafe(R.id.navMessages);
            navProfile = findViewSafe(R.id.navProfile);
            navHomeIcon = findViewSafe(R.id.navHomeIcon);
            navBookingsIcon = findViewSafe(R.id.navBookingsIcon);
            navMessagesIcon = findViewSafe(R.id.navMessagesIcon);
            navProfileIcon = findViewSafe(R.id.navProfileIcon);
            navHomeText = findViewSafe(R.id.navHomeText);
            navBookingsText = findViewSafe(R.id.navBookingsText);
            navMessagesText = findViewSafe(R.id.navMessagesText);
            navProfileText = findViewSafe(R.id.navProfileText);

            homeSwipeRefresh = findViewSafe(R.id.homeSwipeRefresh);

            // Initialize adapters once with the shared source lists.
            if (vpBannersCarousel != null && homeBannerAdapter == null) {
                homeBannerAdapter = new HomeBannerAdapter(this, homeBanners, b -> {
                    try {
                        if (b == null) return;
                        String type = b.redirectType == null ? "" : b.redirectType.trim().toLowerCase(Locale.US);
                        String rid = b.redirectId == null ? "" : b.redirectId.trim();
                        if (rid.isEmpty()) return;
                        if ("service".equals(type) || "category".equals(type)) {
                            openProvidersList(rid);
                        } else if ("external".equals(type) || rid.startsWith("http")) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(rid)));
                            } catch (Exception ignored) {
                                Log.w(TAG, "Could not open banner URL: " + rid);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling banner click: " + e.getMessage());
                    }
                });
                vpBannersCarousel.setAdapter(homeBannerAdapter);
            }

            if (rvOffersStrip != null) {
                rvOffersStrip.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                if (homeOfferAdapter == null) {
                    homeOfferAdapter = new HomeOfferAdapter(this, homeOffers, offer -> {
                        try {
                            if (offer == null || offer.offerCode == null) return;
                            String message = "Use code: " + offer.offerCode + " for " +
                                    ("percentage".equalsIgnoreCase(offer.discountType)
                                            ? (int) offer.discountValue + "% OFF"
                                            : "₹" + (int) offer.discountValue + " OFF");
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Log.e(TAG, "Error handling offer click: " + e.getMessage());
                        }
                    });
                }
                rvOffersStrip.setAdapter(homeOfferAdapter);
            }

            // Setup retry listeners safely
            if (btnRetryBannerLoad != null) {
                btnRetryBannerLoad.setOnClickListener(v -> {
                    try {
                        setBannerLoadingState();
                        loadHomeBanners();
                    } catch (Exception e) {
                        Log.e(TAG, "Retry banner click failed: " + e.getMessage());
                    }
                });
            }

            if (btnRetryOffersLoad != null) {
                btnRetryOffersLoad.setOnClickListener(v -> {
                    try {
                        setOffersLoadingState();
                        loadHomeOffers();
                    } catch (Exception e) {
                        Log.e(TAG, "Retry offers click failed: " + e.getMessage());
                    }
                });
            }

            // Setup swipe refresh safely
            if (homeSwipeRefresh != null) {
                try {
                    homeSwipeRefresh.setColorSchemeResources(R.color.primary);
                    homeSwipeRefresh.setOnRefreshListener(() -> {
                        try {
                            refreshHomeContent(true);
                        } catch (Exception e) {
                            Log.e(TAG, "Swipe refresh failed: " + e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Swipe refresh setup failed: " + e.getMessage());
                }
            }

            // Start skeleton animations
            try {
                startSkeletonPulse(bannerLoadingState);
                startSkeletonPulse(offersLoadingState);
            } catch (Exception e) {
                Log.e(TAG, "Skeleton animation failed: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "initViewsSafe failed: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Safe findViewById with null handling
     */
    @SuppressWarnings("unchecked")
    private <T extends View> T findViewSafe(int id) {
        try {
            return (T) findViewById(id);
        } catch (Exception e) {
            Log.w(TAG, "View not found: " + id);
            return null;
        }
    }

    /**
     * Show safe fallback UI if errors occur
     */
    private void showSafeUI(String message) {
        try {
            if (message != null && !message.trim().isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
            // Log error but don't crash
            Log.e(TAG, "Fallback UI: " + (message != null ? message : "Unknown error"));
        } catch (Exception e) {
            Log.e(TAG, "Fallback UI failed: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            refreshHomeContent(false);
            startBannerAutoSlide();
            startHomeAutoRefresh();
        } catch (Exception e) {
            Log.e(TAG, "onResume: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        try {
            stopBannerAutoSlide();
            stopHomeAutoRefresh();
        } catch (Exception e) {
            Log.e(TAG, "onPause: " + e.getMessage());
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            for (ObjectAnimator animator : skeletonAnimators) {
                if (animator != null) animator.cancel();
            }
            skeletonAnimators.clear();
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: " + e.getMessage());
        }
        super.onDestroy();
    }



    private void loadUserData(FirebaseUser user) {
        if (welcomeMessage != null) welcomeMessage.setText("Hello!");
        if (userEmail != null) userEmail.setText(getString(R.string.find_services));
        if (user == null) return;
        String email = user.getEmail() != null ? user.getEmail().trim() : "";
        String uid = user.getUid();
        SupabaseClient.getUserDisplayName(uid, email, new SupabaseClient.StringCallback() {
            @Override public void onSuccess(String value) { runOnUiThread(() -> { if (!isFinishing() && welcomeMessage != null) welcomeMessage.setText("Hello, " + firstName(value) + "!"); }); }
            @Override public void onError(String message) { runOnUiThread(() -> { if (!isFinishing() && welcomeMessage != null) welcomeMessage.setText("Hello!"); }); }
        });
        if (notificationIcon != null) notificationIcon.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "User";
        return fullName.trim().split("\\s+")[0];
    }

    private void setupLocationSelector() {
        if (locationManager == null) return;
        String savedLabel = locationManager.getSavedLocationLabel();
        updateLocationDisplay(savedLabel != null && !savedLabel.isEmpty() ? savedLabel : "Select City", savedLabel != null && !savedLabel.isEmpty());
        if (locationSelector != null) locationSelector.setOnClickListener(v -> LocationSearchBottomSheet.show(this, locationManager, new LocationSearchBottomSheet.PermissionDelegate() {
            @Override public boolean hasLocationPermission() { return ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
            @Override public void requestLocationPermission() { ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQ); }
        }, suggestion -> {
            if (suggestion == null || suggestion.city == null || suggestion.city.trim().isEmpty()) { updateLocationDisplay("Select City", false); return; }
            String label = AppLocationManager.composeLocationLabel(suggestion.city, suggestion.area);
            updateLocationDisplay(label.isEmpty() ? suggestion.city : label, suggestion.nearby);
        }));
    }

    private void updateLocationDisplay(String city, boolean isDetected) {
        if (tvSelectedLocation != null) tvSelectedLocation.setText((city == null || city.isEmpty()) ? "Select City" : city);
        if (locationAccuracyDot != null) locationAccuracyDot.setVisibility(isDetected ? View.VISIBLE : View.GONE);
        if (ivLocationPulse != null) { if (isDetected) { ivLocationPulse.startAnimation(AnimationUtils.loadAnimation(this, R.anim.location_pulse)); } else { ivLocationPulse.clearAnimation(); } }
    }

    private void setupSearchBar() {
        if (etSearch == null) return;
        if (rvSearchSuggestions != null) {
            suggestionsAdapter = new SearchSuggestionsAdapter(this, suggestion -> { hideSuggestions(); openSearchResults(suggestion.name); });
            rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
            rvSearchSuggestions.setAdapter(suggestionsAdapter);
        }
        if (btnClearSearch != null) btnClearSearch.setOnClickListener(v -> { etSearch.setText(""); hideSuggestions(); btnClearSearch.setVisibility(View.GONE); hideKeyboard(etSearch); });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s.toString().trim();
                if (btnClearSearch != null) btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (debounceRunnable != null) debounce.removeCallbacks(debounceRunnable);
                if (q.isEmpty()) { hideSuggestions(); return; }
                debounceRunnable = () -> fetchSuggestions(q);
                debounce.postDelayed(debounceRunnable, 350);
            }
        });
        etSearch.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { String q = etSearch.getText().toString().trim(); if (!q.isEmpty()) openSearchResults(q); hideKeyboard(etSearch); return true; } return false; });
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            try {
                List<SearchResult> list = new ArrayList<>();
                String enc = java.net.URLEncoder.encode(query, "UTF-8");
                String city = locationManager != null ? locationManager.getSavedCity() : "";
                StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL).append("/rest/v1/provider_profiles?status=eq.active&or=(business_name.ilike.*").append(enc).append("*,category.ilike.*").append(enc).append("*,owner_name.ilike.*").append(enc).append("*)&select=id,business_name,category,city&limit=6");
                if (!city.isEmpty()) url.append("&city=ilike.*").append(java.net.URLEncoder.encode(city, "UTF-8")).append("*");
                Request req = new Request.Builder().url(url.toString()).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY).addHeader("Accept", "application/json").build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (r.code() == 200 && r.body() != null) {
                        JSONArray arr = new JSONArray(r.body().string());
                        for (int i = 0; i < arr.length(); i++) { JSONObject o = arr.getJSONObject(i); list.add(new SearchResult(o.optString("id"), o.optString("business_name", ""), o.optString("category", ""), o.optString("city", ""), SearchResult.TYPE_PROVIDER)); }
                    }
                }
                MAIN.post(() -> { if (suggestionsAdapter != null) { suggestionsAdapter.setSuggestions(list); if (searchSuggestionsContainer != null) searchSuggestionsContainer.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE); } });
            } catch (Exception e) { Log.e(TAG, "fetchSuggestions: " + e.getMessage()); }
        }).start();
    }

    private void hideSuggestions() { if (searchSuggestionsContainer != null) searchSuggestionsContainer.setVisibility(View.GONE); }
    private void openSearchResults(String query) { hideSuggestions(); Intent intent = new Intent(this, SearchResultsActivity.class); intent.putExtra(SearchResultsActivity.EXTRA_QUERY, query == null ? "" : query.trim()); intent.putExtra(SearchResultsActivity.EXTRA_CITY, locationManager != null ? locationManager.getSavedCity() : ""); if (locationManager != null) { intent.putExtra(SearchResultsActivity.EXTRA_LAT, locationManager.getSavedLat()); intent.putExtra(SearchResultsActivity.EXTRA_LNG, locationManager.getSavedLng()); } startActivity(intent); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); }
    private void hideKeyboard(View v) { if (v == null) return; InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0); }

    private void setupCategoryClickListeners() {
        View.OnClickListener providerClick = v -> { String cat = ""; int id = v.getId(); if (id == R.id.catPlumbing) cat = "Plumbing"; else if (id == R.id.catElectrical) cat = "Electrical"; else if (id == R.id.catCleaning) cat = "Cleaning"; else if (id == R.id.catRepair) cat = "Repair"; else if (id == R.id.catAC) cat = "AC Service"; else if (id == R.id.catPainting) cat = "Painting"; else if (id == R.id.catCarpentry) cat = "Carpentry"; if (!cat.isEmpty()) openProvidersList(cat); };
        if (catPlumbing != null) catPlumbing.setOnClickListener(providerClick); if (catElectrical != null) catElectrical.setOnClickListener(providerClick); if (catCleaning != null) catCleaning.setOnClickListener(providerClick); if (catRepair != null) catRepair.setOnClickListener(providerClick); if (catAC != null) catAC.setOnClickListener(providerClick); if (catPainting != null) catPainting.setOnClickListener(providerClick); if (catCarpentry != null) catCarpentry.setOnClickListener(providerClick);
        if (catMore != null) catMore.setOnClickListener(v -> { startActivity(new Intent(this, CategoriesActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
    }

    private void openProvidersList(String category) { Intent intent = new Intent(this, ProviderListActivity.class); intent.putExtra(ProviderListActivity.EXTRA_CATEGORY, category); startActivity(intent); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); }

    private void setupQuickActionListeners() {
        if (quickBookService != null) quickBookService.setOnClickListener(v -> { startActivity(new Intent(this, CategoriesActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
        if (quickEmergency != null) quickEmergency.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("🚨 Emergency Help").setMessage("Do you need immediate assistance?\n\nOur team is available 24/7.").setPositiveButton("Call Now", (d, w) -> Toast.makeText(this, "Calling emergency support…", Toast.LENGTH_SHORT).show()).setNegativeButton("Cancel", null).show());
        if (quickTrackBooking != null) quickTrackBooking.setOnClickListener(v -> { startActivity(new Intent(this, UserBookingsActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
    }

    private void setupFeaturedCardListeners() {
        if (featuredCard1 != null) featuredCard1.setOnClickListener(v -> openProvidersList("Cleaning"));
        if (featuredCard2 != null) featuredCard2.setOnClickListener(v -> openProvidersList("AC Service"));
        if (featuredCard3 != null) featuredCard3.setOnClickListener(v -> openProvidersList("Plumbing"));
    }

    private void setupBottomNav() {
        setNavSelected(0);
        if (navHome != null) navHome.setOnClickListener(v -> setNavSelected(0));
        if (navBookings != null) navBookings.setOnClickListener(v -> { setNavSelected(1); startActivity(new Intent(this, UserBookingsActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
        if (navMessages != null) navMessages.setOnClickListener(v -> { setNavSelected(2); startActivity(new Intent(this, MessagesActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
        if (navProfile != null) navProfile.setOnClickListener(v -> { setNavSelected(3); startActivity(new Intent(this, ProfileActivity.class)); overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); });
    }

    private void setNavSelected(int index) {
        int active = ContextCompat.getColor(this, R.color.primary); int inactive = ContextCompat.getColor(this, R.color.text_secondary);
        if (navHomeIcon != null) navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        if (navBookingsIcon != null) navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        if (navMessagesIcon != null) navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        if (navProfileIcon != null) navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        if (navHomeText != null) navHomeText.setTextColor(inactive); if (navBookingsText != null) navBookingsText.setTextColor(inactive); if (navMessagesText != null) navMessagesText.setTextColor(inactive); if (navProfileText != null) navProfileText.setTextColor(inactive);
        switch (index) {
            case 0: if (navHomeIcon != null) navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); if (navHomeText != null) navHomeText.setTextColor(active); break;
            case 1: if (navBookingsIcon != null) navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); if (navBookingsText != null) navBookingsText.setTextColor(active); break;
            case 2: if (navMessagesIcon != null) navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); if (navMessagesText != null) navMessagesText.setTextColor(active); break;
            case 3: if (navProfileIcon != null) navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); if (navProfileText != null) navProfileText.setTextColor(active); break;
        }
    }

    private void setupListBusinessCard(FirebaseUser user) {
        if (listBusinessSection == null || btnListBusinessStart == null || user == null) return;
        listBusinessSection.setVisibility(View.GONE);
        String uid = user.getUid(); String email = user.getEmail() != null ? user.getEmail() : "";
        RoleManager.checkAdminRole(uid, new RoleManager.RoleCheckCallback() {
            @Override public void onAdminVerified(String adminRoleId, String roleName) { runOnUiThread(() -> { if (listBusinessSection != null) listBusinessSection.setVisibility(View.GONE); }); }
            @Override public void onNotAdmin() { checkProviderProfile(uid, email); }
            @Override public void onError(String message) { checkProviderProfile(uid, email); }
        });
    }

    private void checkProviderProfile(String uid, String email) { ProviderApplicationRepository.checkProviderByEmail(email, new ProviderApplicationRepository.ProviderExistsCallback() { @Override public void onExists() { runOnUiThread(() -> { if (listBusinessSection != null) listBusinessSection.setVisibility(View.GONE); }); } @Override public void onNotExists() { checkExistingApplication(uid); } @Override public void onError(String msg) { checkExistingApplication(uid); } }); }
    private void checkExistingApplication(String uid) { ProviderApplicationRepository.checkApplicationStatus(uid, new ProviderApplicationRepository.StatusCallback() { @Override public void onFound(String status) { runOnUiThread(() -> { if (status == null) { showListBusinessCard(false); return; } switch (status.toLowerCase(Locale.US)) { case "approved": if (listBusinessSection != null) listBusinessSection.setVisibility(View.GONE); break; case "pending": case "rejected": showListBusinessCard(true); break; default: showListBusinessCard(false); break; } }); } @Override public void onNotFound() { runOnUiThread(() -> showListBusinessCard(false)); } @Override public void onError(String message) { runOnUiThread(() -> showListBusinessCard(false)); } }); }
    private void showListBusinessCard(boolean hasExistingApplication) { if (listBusinessSection == null) return; listBusinessSection.setVisibility(View.VISIBLE); listBusinessSection.setAlpha(0f); listBusinessSection.animate().alpha(1f).setDuration(400).start(); if (btnListBusinessStart != null) { btnListBusinessStart.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse)); btnListBusinessStart.setOnClickListener(v -> { if (hasExistingApplication) { startActivity(new Intent(this, ApplicationStatusActivity.class)); } else { startActivity(new Intent(this, ProviderOnboardingActivity.class)); } overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out); }); if (listBusinessSection.getChildCount() > 0) { listBusinessSection.getChildAt(0).setOnClickListener(v -> btnListBusinessStart.performClick()); } } }

    private void refreshHomeContent(boolean fromSwipe) {
        try {
            pendingHomeLoads = 2;
            setBannerLoadingState();
            setOffersLoadingState();
            if (fromSwipe && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(true);
            loadHomeBanners();
            loadHomeOffers();
        } catch (Exception e) {
            Log.e(MAIN_CRASH, "refreshHomeContent: " + e.toString());
        }
    }

    private void loadHomeBanners() {
        if (bannerApi == null) {
            Log.e(TAG, "Banner API is null");
            showBannerErrorState("Service unavailable");
            return;
        }

        try {
            bannerApi.getBanners(
                            BuildConfig.SUPABASE_ANON_KEY,
                            "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                            "*",
                            "eq.true",
                            null,
                            "priority.asc"
                    )
                    .enqueue(new Callback<List<Banner>>() {
                        @Override
                        public void onResponse(Call<List<Banner>> call, retrofit2.Response<List<Banner>> response) {
                            try {
                                runOnUiThread(() -> {
                                    try {
                                        if (response == null || !response.isSuccessful()) {
                                            Log.e(TAG, "Banner API error: " + (response != null ? response.code() + " " + response.message() : "null response"));
                                            showBannerErrorState("Failed to load banners");
                                            markHomeSectionLoaded();
                                            return;
                                        }

                                        List<Banner> body = response.body();
                                        homeBanners.clear();
                                        if (body != null) {
                                            for (Banner b : body) {
                                                if (b == null || !b.isActive || !hasStarted(b.startDate) || isExpired(b.expiryDate)) continue;
                                                if (b.imageUrl != null && !b.imageUrl.startsWith("http")) {
                                                    b.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", b.imageUrl);
                                                }
                                                homeBanners.add(b);
                                            }
                                        }

                                        Log.d("BANNER_SIZE", String.valueOf(homeBanners.size()));
                                        showBannersOnHome();
                                        Log.d(TAG, "Loaded " + homeBanners.size() + " banners");
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing banners: " + e.getMessage(), e);
                                        showBannerErrorState("Processing error");
                                    }
                                    markHomeSectionLoaded();
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error in banner response runOnUiThread: " + e.getMessage(), e);
                                markHomeSectionLoaded();
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Banner>> call, Throwable t) {
                            try {
                                Log.e(TAG, "Banner API failure: " + t.getMessage(), t);
                                runOnUiThread(() -> {
                                    try {
                                        showBannerErrorState("Failed to load banners");
                                        markHomeSectionLoaded();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error showing banner error state: " + e.getMessage());
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error in banner failure handler: " + e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadHomeBanners: " + e.getMessage(), e);
            showBannerErrorState("Error loading banners");
            markHomeSectionLoaded();
        }
    }

    private void loadHomeOffers() {
        if (offerApi == null) {
            Log.e(TAG, "Offer API is null");
            showOffersErrorState("Service unavailable");
            return;
        }

        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String offerExpiryFilter = "(expiry_date.is.null,expiry_date.gte." + today + ")";

            offerApi.getOffers(
                            BuildConfig.SUPABASE_ANON_KEY,
                            "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                            "*",
                            "eq.true",
                            offerExpiryFilter
                    )
                    .enqueue(new Callback<List<Offer>>() {
                        @Override
                        public void onResponse(Call<List<Offer>> call, retrofit2.Response<List<Offer>> response) {
                            try {
                                runOnUiThread(() -> {
                                    try {
                                        if (response == null || !response.isSuccessful()) {
                                            Log.e(TAG, "Offer API error: " + (response != null ? response.code() + " " + response.message() : "null response"));
                                            showOffersErrorState("Failed to load offers");
                                            markHomeSectionLoaded();
                                            return;
                                        }

                                        List<Offer> body = response.body();
                                        homeOffers.clear();
                                        if (body != null) {
                                            for (Offer o : body) {
                                                if (o == null || !o.isActive || isExpired(o.expiryDate)) continue;
                                                homeOffers.add(o);
                                            }
                                        }

                                        Log.d("OFFER_SIZE", String.valueOf(homeOffers.size()));
                                        showOffersOnHome();
                                        Log.d(TAG, "Loaded " + homeOffers.size() + " offers");
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error processing offers: " + e.getMessage(), e);
                                        showOffersErrorState("Processing error");
                                    }
                                    markHomeSectionLoaded();
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error in offer response runOnUiThread: " + e.getMessage(), e);
                                markHomeSectionLoaded();
                            }
                        }

                        @Override
                        public void onFailure(Call<List<Offer>> call, Throwable t) {
                            try {
                                Log.e(TAG, "Offer API failure: " + t.getMessage(), t);
                                runOnUiThread(() -> {
                                    try {
                                        showOffersErrorState("Failed to load offers");
                                        markHomeSectionLoaded();
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error showing offer error state: " + e.getMessage());
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Error in offer failure handler: " + e.getMessage());
                            }
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Exception in loadHomeOffers: " + e.getMessage(), e);
            showOffersErrorState("Error loading offers");
            markHomeSectionLoaded();
        }
    }

    private void markHomeSectionLoaded() {
        pendingHomeLoads = Math.max(0, pendingHomeLoads - 1);
        if (pendingHomeLoads == 0 && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(false);
    }

    private void showBannersOnHome() {
        try {
            if (bannersSection == null || vpBannersCarousel == null || bannerDotsLayout == null) {
                Log.w(TAG, "Banner views not initialized");
                return;
            }

            if (homeBanners.isEmpty()) {
                Log.d(TAG, "No banners to display");
                bannersSection.setVisibility(View.VISIBLE);
                if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE);
                if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE);
                if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE);
                if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.VISIBLE);
                stopBannerAutoSlide();
                if (bannerDotsMediator != null) {
                    try {
                        bannerDotsMediator.detach();
                    } catch (Exception e) {
                        Log.e(TAG, "Error detaching dots mediator: " + e.getMessage());
                    }
                    bannerDotsMediator = null;
                }
                return;
            }

            bannersSection.setVisibility(View.VISIBLE);
            if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE);
            if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE);
            if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE);
            if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.VISIBLE);
            vpBannersCarousel.setVisibility(View.VISIBLE);

            if (homeBannerAdapter != null) {
                homeBannerAdapter.notifyDataSetChanged();
            }

            if (bannerPageCallback == null) {
                bannerPageCallback = new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        try {
                            if (bannerDotsLayout.getTabCount() == 0) return;
                            TabLayout.Tab tab = bannerDotsLayout.getTabAt(position);
                            if (tab != null) bannerDotsLayout.selectTab(tab);
                        } catch (Exception e) {
                            Log.e(TAG, "Error updating dot indicator: " + e.getMessage());
                        }
                    }
                };
                vpBannersCarousel.registerOnPageChangeCallback(bannerPageCallback);
            }

            if (bannerDotsMediator != null) {
                try {
                    bannerDotsMediator.detach();
                } catch (Exception e) {
                    Log.e(TAG, "Error detaching old mediator: " + e.getMessage());
                }
            }
            bannerDotsMediator = new TabLayoutMediator(bannerDotsLayout, vpBannersCarousel, (tab, position) -> {});
            bannerDotsMediator.attach();

            startBannerAutoSlide();
            Log.d(TAG, "Banners displayed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error in showBannersOnHome: " + e.getMessage(), e);
            showBannerErrorState("Failed to display banners");
        }
    }

    private void showOffersOnHome() {
        try {
            if (offersSection == null || rvOffersStrip == null) {
                Log.w(TAG, "Offers views not initialized");
                return;
            }

            if (homeOffers.isEmpty()) {
                Log.d(TAG, "No offers to display");
                offersSection.setVisibility(View.VISIBLE);
                if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE);
                if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE);
                if (offersErrorState != null) offersErrorState.setVisibility(View.GONE);
                if (offersEmptyState != null) offersEmptyState.setVisibility(View.VISIBLE);
                return;
            }

            offersSection.setVisibility(View.VISIBLE);
            if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE);
            if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE);
            if (offersErrorState != null) offersErrorState.setVisibility(View.GONE);
            if (offersContentContainer != null) offersContentContainer.setVisibility(View.VISIBLE);

            if (homeOfferAdapter != null) {
                homeOfferAdapter.notifyDataSetChanged();
            }

            Log.d(TAG, "Offers displayed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error in showOffersOnHome: " + e.getMessage(), e);
            showOffersErrorState("Failed to display offers");
        }
    }

    private boolean hasStarted(String date) {
        if (date == null || date.trim().isEmpty()) return true;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setLenient(false);
            Date start = sdf.parse(date.trim());
            if (start == null) return true;
            java.util.Calendar cStart = java.util.Calendar.getInstance();
            cStart.setTime(start);
            cStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cStart.set(java.util.Calendar.MINUTE, 0);
            cStart.set(java.util.Calendar.SECOND, 0);
            cStart.set(java.util.Calendar.MILLISECOND, 0);

            java.util.Calendar cNow = java.util.Calendar.getInstance();
            cNow.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cNow.set(java.util.Calendar.MINUTE, 0);
            cNow.set(java.util.Calendar.SECOND, 0);
            cNow.set(java.util.Calendar.MILLISECOND, 0);

            return !cStart.after(cNow);
        } catch (Exception e) {
            Log.w(TAG, "hasStarted parse failed for date=" + date + ": " + e.getMessage());
            return true;
        }
    }

    private boolean isExpired(String date) {
        if (date == null || date.trim().isEmpty()) return false;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            sdf.setLenient(false);
            Date expiry = sdf.parse(date.trim());
            return expiry != null && expiry.before(new Date());
        } catch (Exception e) {
            Log.w(TAG, "isExpired parse failed for date=" + date + ": " + e.getMessage());
            return false;
        }
    }

    private void requestNotificationPermission() { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ); } } }

    private void setBannerLoadingState() { if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.VISIBLE); }
    private void showBannerErrorState(String message) { if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE); if (tvBannerErrorMessage != null) tvBannerErrorMessage.setText(message == null || message.trim().isEmpty() ? getString(R.string.home_banner_error_message) : message); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.VISIBLE); stopBannerAutoSlide(); }
    private void setOffersLoadingState() { if (offersSection != null) offersSection.setVisibility(View.VISIBLE); if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.GONE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.VISIBLE); }
    private void showOffersErrorState(String message) { if (offersSection != null) offersSection.setVisibility(View.VISIBLE); if (tvOffersErrorMessage != null) tvOffersErrorMessage.setText(message == null || message.trim().isEmpty() ? getString(R.string.home_offers_error_message) : message); if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.VISIBLE); }

    private void startBannerAutoSlide() {
        try {
            stopBannerAutoSlide();
            if (vpBannersCarousel == null || homeBanners.size() <= 1) {
                Log.d(TAG, "Banner auto-slide not started (not enough banners)");
                return;
            }

            bannerAutoSlideRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (homeBanners.isEmpty() || vpBannersCarousel == null) return;
                        int next = (vpBannersCarousel.getCurrentItem() + 1) % homeBanners.size();
                        vpBannersCarousel.setCurrentItem(next, true);
                        bannerAutoSlideHandler.postDelayed(this, 3000);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in banner auto-slide: " + e.getMessage());
                    }
                }
            };
            bannerAutoSlideHandler.postDelayed(bannerAutoSlideRunnable, 3000);
            Log.d(TAG, "Banner auto-slide started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting banner auto-slide: " + e.getMessage());
        }
    }

    private void stopBannerAutoSlide() {
        try {
            if (bannerAutoSlideRunnable != null) {
                bannerAutoSlideHandler.removeCallbacks(bannerAutoSlideRunnable);
                bannerAutoSlideRunnable = null;
                Log.d(TAG, "Banner auto-slide stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping banner auto-slide: " + e.getMessage());
        }
    }

    private void startSkeletonPulse(View target) { if (target == null) return; ObjectAnimator animator = ObjectAnimator.ofFloat(target, View.ALPHA, 0.55f, 1f); animator.setDuration(700); animator.setRepeatMode(ObjectAnimator.REVERSE); animator.setRepeatCount(ObjectAnimator.INFINITE); animator.start(); skeletonAnimators.add(animator); }

    private void startHomeAutoRefresh() { stopHomeAutoRefresh(); homeAutoRefreshRunnable = new Runnable() { @Override public void run() { refreshHomeContent(false); MAIN.postDelayed(this, HOME_AUTO_REFRESH_MS); } }; MAIN.postDelayed(homeAutoRefreshRunnable, HOME_AUTO_REFRESH_MS); }
    private void stopHomeAutoRefresh() { if (homeAutoRefreshRunnable != null) { MAIN.removeCallbacks(homeAutoRefreshRunnable); homeAutoRefreshRunnable = null; } }

    private void redirectToLogin() { Intent i = new Intent(this, LoginActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); }
}
