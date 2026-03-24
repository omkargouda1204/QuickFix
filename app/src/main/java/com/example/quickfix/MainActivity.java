package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends BaseActivity {

    private static final String TAG                      = "MainActivity";
    private static final String CRASH_TAG                = "CRASH_ERROR";
    private static final int    LOCATION_PERMISSION_REQ  = 1001;
    private static final int    NOTIFICATION_PERMISSION_REQ = 1002;

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
    private CardView       providerDashboardCard;
    private MaterialButton providerDashboardBtn;

    // ── List Your Business ────────────────────────────────────────────────
    private LinearLayout   listBusinessSection;
    private MaterialButton btnListBusinessStart;

    // ── Banners & Offers (home screen) ────────────────────────────────────
    private LinearLayout       bannersSection, offersSection;
    private ViewPager2         vpBannersCarousel;
    private RecyclerView       rvOffersStrip;
    private TabLayout          bannerDotsLayout;
    private HomeBannerAdapter  homeBannerAdapter;
    private HomeOfferAdapter   homeOfferAdapter;
    private final List<Banner> homeBanners = new java.util.ArrayList<>();
    private final List<Offer>  homeOffers  = new java.util.ArrayList<>();
    private TabLayoutMediator  bannerDotsMediator;
    private ViewPager2.OnPageChangeCallback bannerPageCallback;
    private final Handler bannerAutoSlideHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerAutoSlideRunnable;
    private int pendingHomeLoads = 0;


    // ── Pull-to-Refresh ───────────────────────────────────────────────────
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout homeSwipeRefresh;

    // ── Bottom Nav ────────────────────────────────────────────────────────
    private LinearLayout navHome, navBookings, navMessages, navProfile;
    private ImageView    navHomeIcon, navBookingsIcon, navMessagesIcon, navProfileIcon;
    private TextView     navHomeText, navBookingsText, navMessagesText, navProfileText;

    // ── Firebase / HTTP ───────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) { redirectToLogin(); return; }

        locationManager = new AppLocationManager(this);

        initViews();
        loadUserData(currentUser);
        setupLocationSelector();
        setupSearchBar();
        setupCategoryClickListeners();
        setupQuickActionListeners();
        setupFeaturedCardListeners();
        setupBottomNav();
        setupListBusinessCard(currentUser);

        try {
            refreshHomeContent(false);
        } catch (Exception e) {
            Log.e(CRASH_TAG, "Home content init failed", e);
            Log.e(TAG, "Home content init failed", e);
        }

        // Sync dark mode setting from Supabase (admin may have changed it)
        ThemeManager.fetchAndSync(this);

        if (providerDashboardSection != null)
            providerDashboardSection.setVisibility(View.GONE);

        // FCM token — stored in DB silently
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();
            Log.d("FCM_TOKEN", "Token retrieved (stored in DB, not shown to user)");
            SupabaseClient.saveFcmToken(currentUser.getUid(), token);
        });

        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            loadHomeBanners();
            loadHomeOffers();
        } catch (Exception e) {
            Log.e(CRASH_TAG, "Home content refresh failed", e);
            Log.e(TAG, "Home content refresh failed", e);
        }
        startBannerAutoSlide();
    }

    @Override
    protected void onPause() {
        stopBannerAutoSlide();
        super.onPause();
    }

    // ── Init Views ────────────────────────────────────────────────────────
    private void initViews() {
        // Location
        locationSelector    = findViewById(R.id.locationSelector);
        tvSelectedLocation  = findViewById(R.id.tvSelectedLocation);
        ivLocationPulse     = findViewById(R.id.ivLocationPulse);
        locationAccuracyDot = findViewById(R.id.locationAccuracyDot);

        // Notification
        notificationIcon = findViewById(R.id.notificationIcon);

        // Search
        etSearch                   = findViewById(R.id.etSearch);
        btnClearSearch             = findViewById(R.id.btnClearSearch);
        searchSuggestionsContainer = findViewById(R.id.searchSuggestionsContainer);
        rvSearchSuggestions        = findViewById(R.id.rvSearchSuggestions);

        // Header
        welcomeMessage = findViewById(R.id.welcomeMessage);
        userEmail      = findViewById(R.id.userEmail);

        // Categories
        catPlumbing  = findViewById(R.id.catPlumbing);
        catElectrical= findViewById(R.id.catElectrical);
        catCleaning  = findViewById(R.id.catCleaning);
        catRepair    = findViewById(R.id.catRepair);
        catAC        = findViewById(R.id.catAC);
        catPainting  = findViewById(R.id.catPainting);
        catCarpentry = findViewById(R.id.catCarpentry);
        catMore      = findViewById(R.id.catMore);

        // Quick actions
        quickBookService  = findViewById(R.id.quickBookService);
        quickEmergency    = findViewById(R.id.quickEmergency);
        quickTrackBooking = findViewById(R.id.quickTrackBooking);

        // Featured
        featuredCard1 = findViewById(R.id.featuredCard1);
        featuredCard2 = findViewById(R.id.featuredCard2);
        featuredCard3 = findViewById(R.id.featuredCard3);

        // Provider dashboard
        providerDashboardSection = findViewById(R.id.providerDashboardSection);
        providerDashboardCard    = findViewById(R.id.providerDashboardCard);
        providerDashboardBtn     = findViewById(R.id.providerDashboardBtn);

        // List business
        listBusinessSection  = findViewById(R.id.listBusinessSection);
        btnListBusinessStart = findViewById(R.id.btnListBusinessStart);

        // Banners & Offers
        bannersSection    = findViewById(R.id.bannersSection);
        offersSection     = findViewById(R.id.offersSection);
        vpBannersCarousel = findViewById(R.id.vpBannersCarousel);
        rvOffersStrip     = findViewById(R.id.rvOffersStrip);
        bannerDotsLayout  = findViewById(R.id.bannerDotsLayout);

        View tvSeeAllOffers = findViewById(R.id.tvSeeAllOffers);
        if (tvSeeAllOffers != null) tvSeeAllOffers.setOnClickListener(v ->
                Toast.makeText(this, "All offers shown below", Toast.LENGTH_SHORT).show());

        // Bottom nav
        navHome     = findViewById(R.id.navHome);
        navBookings = findViewById(R.id.navBookings);
        navMessages = findViewById(R.id.navMessages);
        navProfile  = findViewById(R.id.navProfile);

        navHomeIcon     = findViewById(R.id.navHomeIcon);
        navBookingsIcon = findViewById(R.id.navBookingsIcon);
        navMessagesIcon = findViewById(R.id.navMessagesIcon);
        navProfileIcon  = findViewById(R.id.navProfileIcon);

        navHomeText     = findViewById(R.id.navHomeText);
        navBookingsText = findViewById(R.id.navBookingsText);
        navMessagesText = findViewById(R.id.navMessagesText);
        navProfileText  = findViewById(R.id.navProfileText);

        // Pull-to-refresh (silent background refresh — no visible spinner shown)
        homeSwipeRefresh = findViewById(R.id.homeSwipeRefresh);
        if (homeSwipeRefresh != null) {
            homeSwipeRefresh.setColorSchemeResources(R.color.primary);
            homeSwipeRefresh.setProgressBackgroundColorSchemeColor(
                    android.graphics.Color.TRANSPARENT);
            homeSwipeRefresh.setProgressViewOffset(false, 0, 0);
            homeSwipeRefresh.setOnRefreshListener(() -> refreshHomeContent(true));
        }
    }

    // ── Load User Data ────────────────────────────────────────────────────
    private void loadUserData(FirebaseUser user) {
        if (welcomeMessage != null) {
            welcomeMessage.setText("Hello!");
        }
        if (userEmail != null) userEmail.setText(getString(R.string.find_services));

        String email = user != null && user.getEmail() != null ? user.getEmail().trim() : "";
        String uid = user != null ? user.getUid() : "";

        SupabaseClient.getUserDisplayName(uid, email, new SupabaseClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                if (isFinishing()) return;
                if (welcomeMessage != null) {
                    welcomeMessage.setText("Hello, " + firstName(value) + "!");
                }
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                if (welcomeMessage != null) {
                    welcomeMessage.setText("Hello!");
                }
            }
        });

        if (notificationIcon != null) {
            notificationIcon.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationsActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            });
        }
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "User";
        return fullName.trim().split("\\s+")[0];
    }

    // ── Location Selector ─────────────────────────────────────────────────
    private void setupLocationSelector() {
        String savedLabel = locationManager.getSavedLocationLabel();
        if (savedLabel != null && !savedLabel.isEmpty()) {
            updateLocationDisplay(savedLabel, true);
        } else {
            updateLocationDisplay("Select City", false);
        }

        if (locationSelector != null) {
            locationSelector.setOnClickListener(v -> LocationSearchBottomSheet.show(
                    this,
                    locationManager,
                    new LocationSearchBottomSheet.PermissionDelegate() {
                        @Override
                        public boolean hasLocationPermission() {
                            return ContextCompat.checkSelfPermission(MainActivity.this,
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                                    == PackageManager.PERMISSION_GRANTED;
                        }

                        @Override
                        public void requestLocationPermission() {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                    },
                                    LOCATION_PERMISSION_REQ);
                        }
                    },
                    suggestion -> {
                        if (suggestion == null || suggestion.city == null || suggestion.city.trim().isEmpty()) {
                            updateLocationDisplay("Select City", false);
                            return;
                        }
                        String label = AppLocationManager.composeLocationLabel(suggestion.city, suggestion.area);
                        updateLocationDisplay(label.isEmpty() ? suggestion.city : label, suggestion.nearby);
                    }
            ));
        }
    }

    private void updateLocationDisplay(String city, boolean isDetected) {
        if (tvSelectedLocation != null)
            tvSelectedLocation.setText((city == null || city.isEmpty()) ? "Select City" : city);
        if (locationAccuracyDot != null)
            locationAccuracyDot.setVisibility(isDetected ? View.VISIBLE : View.GONE);
        if (ivLocationPulse != null) {
            if (isDetected) {
                Animation pulse = AnimationUtils.loadAnimation(this, R.anim.location_pulse);
                ivLocationPulse.startAnimation(pulse);
            } else {
                ivLocationPulse.clearAnimation();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, permissions, results);
        if (req == LOCATION_PERMISSION_REQ) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (req == NOTIFICATION_PERMISSION_REQ) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "POST_NOTIFICATIONS permission " + (granted ? "GRANTED" : "DENIED"));
        }
    }

    // ── Search Bar ────────────────────────────────────────────────────────
    private void setupSearchBar() {
        if (etSearch == null) return;

        if (rvSearchSuggestions != null) {
            suggestionsAdapter = new SearchSuggestionsAdapter(this, suggestion -> {
                hideSuggestions();
                openSearchResults(suggestion.name);
            });
            rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
            rvSearchSuggestions.setAdapter(suggestionsAdapter);
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                etSearch.setText("");
                hideSuggestions();
                btnClearSearch.setVisibility(View.GONE);
                hideKeyboard(etSearch);
            });
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                String q = s.toString().trim();
                if (btnClearSearch != null)
                    btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                if (debounceRunnable != null) debounce.removeCallbacks(debounceRunnable);
                if (q.isEmpty()) { hideSuggestions(); return; }
                debounceRunnable = () -> fetchSuggestions(q);
                debounce.postDelayed(debounceRunnable, 350);
            }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = etSearch.getText().toString().trim();
                if (!q.isEmpty()) openSearchResults(q);
                hideKeyboard(etSearch);
                return true;
            }
            return false;
        });
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            List<SearchResult> list = new ArrayList<>();
            try {
                String enc  = java.net.URLEncoder.encode(query, "UTF-8");
                String city = locationManager.getSavedCity();

                // ── 1. Query provider_profiles (approved providers) ──
                StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/provider_profiles")
                    .append("?status=eq.active")
                    .append("&or=(business_name.ilike.*").append(enc)
                    .append("*,category.ilike.*").append(enc)
                    .append("*,owner_name.ilike.*").append(enc).append("*)")
                    .append("&select=id,business_name,category,city&limit=6");
                // Only add city filter if a location is selected
                if (!city.isEmpty())
                    url.append("&city=ilike.*")
                       .append(java.net.URLEncoder.encode(city, "UTF-8")).append("*");

                Request req = new Request.Builder().url(url.toString())
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (r.code() == 200) {
                        String body = r.body() != null ? r.body().string() : "[]";
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new SearchResult(
                                o.optString("id"),
                                o.optString("business_name", ""),
                                o.optString("category", ""),
                                o.optString("city", ""),
                                SearchResult.TYPE_PROVIDER));
                        }
                    }
                }

                // ── 2. If no results from profiles, try provider_applications ──
                if (list.isEmpty()) {
                    StringBuilder url2 = new StringBuilder(BuildConfig.SUPABASE_URL)
                        .append("/rest/v1/provider_applications?status=eq.approved")
                        .append("&or=(business_name.ilike.*").append(enc)
                        .append("*,category.ilike.*").append(enc)
                        .append("*,user_name.ilike.*").append(enc).append("*)")
                        .append("&select=id,business_name,category,city&limit=6");
                    if (!city.isEmpty())
                        url2.append("&city=ilike.*")
                            .append(java.net.URLEncoder.encode(city, "UTF-8")).append("*");

                    Request req2 = new Request.Builder().url(url2.toString())
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                    try (Response r2 = HTTP.newCall(req2).execute()) {
                        if (r2.code() == 200) {
                            String body2 = r2.body() != null ? r2.body().string() : "[]";
                            JSONArray arr2 = new JSONArray(body2);
                            for (int i = 0; i < arr2.length(); i++) {
                                JSONObject o = arr2.getJSONObject(i);
                                list.add(new SearchResult(
                                    o.optString("id"),
                                    o.optString("business_name", ""),
                                    o.optString("category", ""),
                                    o.optString("city", ""),
                                    SearchResult.TYPE_PROVIDER));
                            }
                        }
                    }
                }

            } catch (Exception e) { Log.e(TAG, "fetchSuggestions: " + e.getMessage()); }

            MAIN.post(() -> {
                if (suggestionsAdapter != null) {
                    suggestionsAdapter.setSuggestions(list);
                    if (searchSuggestionsContainer != null)
                        searchSuggestionsContainer.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                }
            });
        }).start();
    }

    private void hideSuggestions() {
        if (searchSuggestionsContainer != null)
            searchSuggestionsContainer.setVisibility(View.GONE);
    }

    private void openSearchResults(String query) {
        hideSuggestions();
        Intent intent = new Intent(this, ProviderListActivity.class);
        intent.putExtra(ProviderListActivity.EXTRA_QUERY, query);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ── Category Click Listeners ──────────────────────────────────────────
    private void setupCategoryClickListeners() {
        // Regular categories → ProvidersListActivity filtered by category
        View.OnClickListener providerClick = v -> {
            String cat = ""; int id = v.getId();
            if      (id == R.id.catPlumbing)   cat = "Plumbing";
            else if (id == R.id.catElectrical) cat = "Electrical";
            else if (id == R.id.catCleaning)   cat = "Cleaning";
            else if (id == R.id.catRepair)     cat = "Repair";
            else if (id == R.id.catAC)         cat = "AC Service";
            else if (id == R.id.catPainting)   cat = "Painting";
            else if (id == R.id.catCarpentry)  cat = "Carpentry";
            if (!cat.isEmpty()) openProvidersList(cat);
        };
        catPlumbing.setOnClickListener(providerClick);
        catElectrical.setOnClickListener(providerClick);
        catCleaning.setOnClickListener(providerClick);
        catRepair.setOnClickListener(providerClick);
        catAC.setOnClickListener(providerClick);
        catPainting.setOnClickListener(providerClick);
        catCarpentry.setOnClickListener(providerClick);

        // More → full categories grid
        catMore.setOnClickListener(v -> {
            startActivity(new Intent(this, CategoriesActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
    }

    private void openProvidersList(String category) {
        Intent intent = new Intent(this, ProviderListActivity.class);
        intent.putExtra(ProviderListActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
    }

    // ── Quick Action Listeners ────────────────────────────────────────────
    private void setupQuickActionListeners() {
        quickBookService.setOnClickListener(v -> {
            startActivity(new Intent(this, CategoriesActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        quickEmergency.setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle("🚨 Emergency Help")
                .setMessage("Do you need immediate assistance?\n\nOur team is available 24/7.")
                .setPositiveButton("Call Now", (d, w) ->
                    Toast.makeText(this, "Calling emergency support…", Toast.LENGTH_SHORT).show())
                .setNegativeButton("Cancel", null)
                .show());

        quickTrackBooking.setOnClickListener(v -> {
            startActivity(new Intent(this, UserBookingsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
    }

    // ── Featured Card Listeners ───────────────────────────────────────────
    private void setupFeaturedCardListeners() {
        featuredCard1.setOnClickListener(v -> openProvidersList("Cleaning"));
        featuredCard2.setOnClickListener(v -> openProvidersList("AC Service"));
        featuredCard3.setOnClickListener(v -> openProvidersList("Plumbing"));
    }

    // ── Bottom Navigation ─────────────────────────────────────────────────
    private void setupBottomNav() {
        setNavSelected(0);
        navHome.setOnClickListener(v -> setNavSelected(0));
        navBookings.setOnClickListener(v -> {
            setNavSelected(1);
            startActivity(new Intent(this, UserBookingsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
        navMessages.setOnClickListener(v -> {
            setNavSelected(2);
            startActivity(new Intent(this, MessagesActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
        navProfile.setOnClickListener(v -> {
            setNavSelected(3);
            startActivity(new Intent(this, ProfileActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
    }

    private void setNavSelected(int index) {
        int active   = ContextCompat.getColor(this, R.color.primary);
        int inactive = ContextCompat.getColor(this, R.color.text_secondary);
        navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(inactive));
        navHomeText.setTextColor(inactive);
        navBookingsText.setTextColor(inactive);
        navMessagesText.setTextColor(inactive);
        navProfileText.setTextColor(inactive);
        switch (index) {
            case 0: navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); navHomeText.setTextColor(active); break;
            case 1: navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); navBookingsText.setTextColor(active); break;
            case 2: navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); navMessagesText.setTextColor(active); break;
            case 3: navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(active)); navProfileText.setTextColor(active); break;
        }
    }

    // ── List Your Business Card ───────────────────────────────────────────
    private void setupListBusinessCard(FirebaseUser user) {
        if (listBusinessSection == null || btnListBusinessStart == null) return;
        listBusinessSection.setVisibility(View.GONE);
        String uid   = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";

        // 1. Check if user is admin — hide card for admins
        RoleManager.checkAdminRole(uid, new RoleManager.RoleCheckCallback() {
            @Override public void onAdminVerified(String adminRoleId, String roleName) {
                listBusinessSection.setVisibility(View.GONE);
            }
            @Override public void onNotAdmin() {
                // 2. Check if email already has an approved provider profile
                checkProviderProfile(uid, email);
            }
            @Override public void onError(String message) {
                // On role check error — still show for normal users
                checkProviderProfile(uid, email);
            }
        });
    }

    /**
     * Checks if the user already has an approved provider profile (email uniqueness).
     * If yes → hide card.
     * If no → check application status for the Start button routing.
     */
    private void checkProviderProfile(String uid, String email) {
        ProviderApplicationRepository.checkProviderByEmail(email,
                new ProviderApplicationRepository.ProviderExistsCallback() {
                    @Override public void onExists() {
                        // Already an approved provider — hide the card
                        runOnUiThread(() -> {
                            if (listBusinessSection != null)
                                listBusinessSection.setVisibility(View.GONE);
                        });
                    }
                    @Override public void onNotExists() {
                        // Check for any existing application (pending/rejected)
                        checkExistingApplication(uid);
                    }
                    @Override public void onError(String msg) {
                        checkExistingApplication(uid);
                    }
                });
    }

    /**
     * Checks existing application status.
     * - No application → show card, Start → open ProviderOnboardingActivity
     * - Pending or Rejected → show card, Start → open ApplicationStatusActivity
     * - Approved → hide card (already a provider)
     */
    private void checkExistingApplication(String uid) {
        ProviderApplicationRepository.checkApplicationStatus(uid,
                new ProviderApplicationRepository.StatusCallback() {
                    @Override public void onFound(String status) {
                        runOnUiThread(() -> {
                            switch (status.toLowerCase(Locale.US)) {
                                case "approved":
                                    // Already provider — hide card
                                    if (listBusinessSection != null)
                                        listBusinessSection.setVisibility(View.GONE);
                                    break;
                                case "pending":
                                case "rejected":
                                    // Show card — Start routes to status page
                                    showListBusinessCard(true);
                                    break;
                                default:
                                    showListBusinessCard(false);
                                    break;
                            }
                        });
                    }
                    @Override public void onNotFound() {
                        runOnUiThread(() -> showListBusinessCard(false));
                    }
                    @Override public void onError(String message) {
                        runOnUiThread(() -> showListBusinessCard(false));
                    }
                });
    }

    /**
     * @param hasExistingApplication true = route Start → ApplicationStatusActivity
     *                               false = route Start → ProviderOnboardingActivity
     */
    private void showListBusinessCard(boolean hasExistingApplication) {
        if (listBusinessSection == null) return;
        listBusinessSection.setVisibility(View.VISIBLE);
        listBusinessSection.setAlpha(0f);
        listBusinessSection.animate().alpha(1f).setDuration(400).start();
        if (btnListBusinessStart != null) {
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            btnListBusinessStart.startAnimation(pulse);
            btnListBusinessStart.setOnClickListener(v -> {
                if (hasExistingApplication) {
                    startActivity(new Intent(this, ApplicationStatusActivity.class));
                } else {
                    startActivity(new Intent(this, ProviderOnboardingActivity.class));
                }
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            });
            if (listBusinessSection.getChildCount() > 0) {
                listBusinessSection.getChildAt(0).setOnClickListener(
                        v -> btnListBusinessStart.performClick());
            }
        }
    }

    // ── Load Banners & Offers from Supabase ───────────────────────────────
    // DB schema:
    //   banners: id, title, image_url, redirect_type, redirect_id, start_date,
    //            expiry_date, is_active(boolean), priority, created_at
    //   offers:  id, title, description, offer_code, discount_type,
    //            discount_value(numeric), min_booking_amount, expiry_date,
    //            is_active(boolean), created_at
    private void refreshHomeContent(boolean fromSwipe) {
        try {
            if (fromSwipe && homeSwipeRefresh != null) {
                homeSwipeRefresh.setRefreshing(true);
                pendingHomeLoads = 2;
            }
            loadHomeBanners();
            loadHomeOffers();
        } catch (Exception e) {
            Log.e(CRASH_TAG, "refreshHomeContent failed", e);
            Log.e(TAG, "refreshHomeContent failed", e);
            if (homeSwipeRefresh != null) {
                homeSwipeRefresh.setRefreshing(false);
            }
        }
    }

    private void markHomeSectionLoaded() {
        pendingHomeLoads = Math.max(0, pendingHomeLoads - 1);
        if (pendingHomeLoads == 0 && homeSwipeRefresh != null) {
            homeSwipeRefresh.setRefreshing(false);
        }
    }

    private void loadHomeBanners() {
        loadBanners();
    }

    private void loadHomeOffers() {
        loadOffers();
    }

    private void loadBanners() {
        final String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                Locale.getDefault()).format(new java.util.Date());

        new Thread(() -> {
            try {
                String[] urls = new String[] {
                        BuildConfig.SUPABASE_URL + "/rest/v1/banners?select=*&is_active=eq.true&or=(expiry_date.is.null,expiry_date.gte.now())&order=priority.asc,created_at.desc",
                        BuildConfig.SUPABASE_URL + "/rest/v1/banners?select=*&is_active=eq.true&or=(expiry_date.is.null,expiry_date.gte." + today + ")&order=priority.asc,created_at.desc"
                };

                String body = "[]";
                int code = 0;
                boolean ok = false;
                for (String url : urls) {
                    Request req = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Accept", "application/json")
                            .build();
                    try (Response resp = HTTP.newCall(req).execute()) {
                        code = resp.code();
                        body = resp.body() != null ? resp.body().string() : "[]";
                        if (resp.isSuccessful()) {
                            ok = true;
                            break;
                        }
                    }
                }

                Log.e("RESPONSE", body);
                if (ok) {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    List<Banner> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        Banner b = new Banner();
                        b.id = o.optString("id");
                        b.title = o.optString("title");
                        b.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", o.optString("image_url"));
                        b.redirectType = o.optString("redirect_type");
                        b.redirectId = o.optString("redirect_id");
                        b.expiryDate = o.optString("expiry_date");
                        b.priority = o.optInt("priority", 1);
                        b.isActive = o.optBoolean("is_active", true);
                        b.status = b.isActive ? "active" : "inactive";

                        boolean notExpired = b.expiryDate.isEmpty() || b.expiryDate.compareTo(today) >= 0;
                        if (b.isActive && notExpired) {
                            list.add(b);
                        }
                    }
                    MAIN.post(() -> showBannersOnHome(list));
                } else {
                    Log.e(CRASH_TAG, "loadBanners failed HTTP " + code);
                    Log.e("ERROR", body);
                    MAIN.post(() -> showBannersOnHome(new ArrayList<>()));
                }
            } catch (Exception e) {
                Log.e(CRASH_TAG, "loadBanners exception", e);
                MAIN.post(() -> showBannersOnHome(new ArrayList<>()));
            } finally {
                MAIN.post(this::markHomeSectionLoaded);
            }
        }).start();
    }

    private void loadOffers() {
        final String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                Locale.getDefault()).format(new java.util.Date());

        new Thread(() -> {
            try {
                String[] urls = new String[] {
                        BuildConfig.SUPABASE_URL + "/rest/v1/offers?select=*&is_active=eq.true&or=(expiry_date.is.null,expiry_date.gte.now())&order=created_at.desc",
                        BuildConfig.SUPABASE_URL + "/rest/v1/offers?select=*&is_active=eq.true&or=(expiry_date.is.null,expiry_date.gte." + today + ")&order=created_at.desc"
                };

                String body = "[]";
                int code = 0;
                boolean ok = false;
                for (String url : urls) {
                    Request req = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                            .addHeader("Accept", "application/json")
                            .build();
                    try (Response resp = HTTP.newCall(req).execute()) {
                        code = resp.code();
                        body = resp.body() != null ? resp.body().string() : "[]";
                        if (resp.isSuccessful()) {
                            ok = true;
                            break;
                        }
                    }
                }

                Log.e("RESPONSE", body);
                if (ok) {
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    List<Offer> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        Offer offer = new Offer();
                        offer.id = o.optString("id");
                        offer.title = o.optString("title");
                        offer.description = o.optString("description", "");
                        offer.offerCode = o.optString("offer_code", "");
                        offer.discountType = o.optString("discount_type", "percentage");
                        offer.discountValue = o.optDouble("discount_value", 0);
                        offer.minBookingAmount = o.optDouble("min_booking_amount", 0);
                        offer.expiryDate = o.optString("expiry_date", "");
                        offer.isActive = o.optBoolean("is_active", true);
                        offer.status = offer.isActive ? "active" : "inactive";

                        boolean notExpired = offer.expiryDate.isEmpty() || offer.expiryDate.compareTo(today) >= 0;
                        if (offer.isActive && notExpired) {
                            list.add(offer);
                        }
                    }
                    MAIN.post(() -> showOffersOnHome(list));
                } else {
                    Log.e(CRASH_TAG, "loadOffers failed HTTP " + code);
                    Log.e("ERROR", body);
                    MAIN.post(() -> showOffersOnHome(new ArrayList<>()));
                }
            } catch (Exception e) {
                Log.e(CRASH_TAG, "loadOffers exception", e);
                MAIN.post(() -> showOffersOnHome(new ArrayList<>()));
            } finally {
                MAIN.post(this::markHomeSectionLoaded);
            }
        }).start();
    }

    private void showBannersOnHome(List<Banner> list) {
        if (bannersSection == null || vpBannersCarousel == null || bannerDotsLayout == null) {
            Log.e(CRASH_TAG, "Banner views are null; skipping render");
            return;
        }

        if (list != null && list.size() > 0) {
            homeBanners.clear();
            homeBanners.addAll(list);
        } else {
            Log.e("DATA", "Empty or null list: banners");
            homeBanners.clear();
        }

        if (homeBanners.isEmpty()) {
            bannersSection.setVisibility(View.GONE);
            stopBannerAutoSlide();
            if (bannerDotsMediator != null) {
                bannerDotsMediator.detach();
                bannerDotsMediator = null;
            }
            return;
        }

        bannersSection.setVisibility(View.VISIBLE);

        if (homeBannerAdapter == null) {
            homeBannerAdapter = new HomeBannerAdapter(this, homeBanners, banner -> {
                if ("category".equals(banner.redirectType) && banner.redirectId != null) {
                    openProvidersList(banner.redirectId);
                } else if ("service".equals(banner.redirectType) && banner.redirectId != null) {
                    openProvidersList(banner.redirectId);
                }
            });
        }

        if (homeBanners != null && homeBanners.size() > 0) {
            if (vpBannersCarousel.getAdapter() == null) {
                vpBannersCarousel.setAdapter(homeBannerAdapter);
            }
            homeBannerAdapter.setItems(homeBanners);
            homeBannerAdapter.notifyDataSetChanged();
        } else {
            vpBannersCarousel.setVisibility(View.GONE);
            Log.e("DATA", "Empty or null list: banner adapter source");
            return;
        }

        if (bannerPageCallback == null) {
            bannerPageCallback = new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    if (bannerDotsLayout.getTabCount() == 0) return;
                    TabLayout.Tab tab = bannerDotsLayout.getTabAt(position);
                    if (tab != null) {
                        bannerDotsLayout.selectTab(tab);
                    }
                }
            };
            vpBannersCarousel.registerOnPageChangeCallback(bannerPageCallback);
        }

        if (bannerDotsMediator != null) {
            bannerDotsMediator.detach();
        }
        bannerDotsMediator = new TabLayoutMediator(bannerDotsLayout, vpBannersCarousel,
                (tab, position) -> { });
        bannerDotsMediator.attach();

        startBannerAutoSlide();
    }

    private void startBannerAutoSlide() {
        stopBannerAutoSlide();
        if (vpBannersCarousel == null || homeBanners.size() <= 1) return;

        bannerAutoSlideRunnable = new Runnable() {
            @Override
            public void run() {
                if (homeBanners.isEmpty()) return;
                int next = (vpBannersCarousel.getCurrentItem() + 1) % homeBanners.size();
                vpBannersCarousel.setCurrentItem(next, true);
                bannerAutoSlideHandler.postDelayed(this, 3000);
            }
        };
        bannerAutoSlideHandler.postDelayed(bannerAutoSlideRunnable, 3000);
    }

    private void stopBannerAutoSlide() {
        if (bannerAutoSlideRunnable != null) {
            bannerAutoSlideHandler.removeCallbacks(bannerAutoSlideRunnable);
            bannerAutoSlideRunnable = null;
        }
    }

    private void showOffersOnHome(List<Offer> list) {
        if (offersSection == null || rvOffersStrip == null) {
            Log.e(CRASH_TAG, "Offer views are null; skipping render");
            return;
        }

        if (list != null && list.size() > 0) {
            homeOffers.clear();
            homeOffers.addAll(list);
        } else {
            Log.e("DATA", "Empty or null list: offers");
            homeOffers.clear();
        }

        if (homeOffers.isEmpty()) {
            offersSection.setVisibility(View.GONE);
            if (homeOfferAdapter != null) {
                homeOfferAdapter.notifyDataSetChanged();
            }
            return;
        }

        offersSection.setVisibility(View.VISIBLE);

        if (homeOfferAdapter == null) {
            homeOfferAdapter = new HomeOfferAdapter(this, homeOffers, offer ->
                    Toast.makeText(this,
                            "Use code: " + offer.offerCode + " for "
                                    + (offer.discountType.equals("percentage")
                                    ? (int) offer.discountValue + "% OFF"
                                    : "₹" + (int) offer.discountValue + " OFF"),
                            Toast.LENGTH_LONG).show()
            );
            LinearLayoutManager lm = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
            rvOffersStrip.setLayoutManager(lm);
            rvOffersStrip.setAdapter(homeOfferAdapter);
        } else {
            homeOfferAdapter.setItems(homeOffers);
        }

        if (homeOffers != null && homeOffers.size() > 0) {
            homeOfferAdapter.notifyDataSetChanged();
        } else {
            Log.e("DATA", "Empty or null list: offer adapter source");
        }
    }

    // ── Notification permission (Android 13+) ────────────────────────────
    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQ);
            }
        }
    }


    // ── Navigation ────────────────────────────────────────────────────────
    private void redirectToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        // Keep activity alive to avoid sudden app close perception on startup errors.
    }
}
