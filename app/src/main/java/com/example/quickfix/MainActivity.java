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
    private static final String MAIN_ERROR               = "MAIN_ERROR";
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
        try {
            setContentView(R.layout.activity_main);

            mAuth = FirebaseAuth.getInstance();
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) { redirectToLogin(); return; }

            bannerApi = RetrofitProvider.get().create(BannerApi.class);
            offerApi = RetrofitProvider.get().create(OfferApi.class);
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

            refreshHomeContent(false);

            ThemeManager.fetchAndSync(this);
            if (providerDashboardSection != null) providerDashboardSection.setVisibility(View.GONE);

            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    SupabaseClient.saveFcmToken(currentUser.getUid(), task.getResult());
                }
            });

            requestNotificationPermission();
        } catch (Exception e) {
            Log.e(MAIN_ERROR, "onCreate: " + e.getMessage(), e);
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
            Log.e(MAIN_ERROR, "onResume: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onPause() {
        try {
            stopBannerAutoSlide();
            stopHomeAutoRefresh();
        } catch (Exception e) {
            Log.e(MAIN_ERROR, "onPause: " + e.getMessage(), e);
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
            Log.e(MAIN_ERROR, "onDestroy: " + e.getMessage(), e);
        }
        super.onDestroy();
    }

    private void initViews() {
        locationSelector    = findViewById(R.id.locationSelector);
        tvSelectedLocation  = findViewById(R.id.tvSelectedLocation);
        ivLocationPulse     = findViewById(R.id.ivLocationPulse);
        locationAccuracyDot = findViewById(R.id.locationAccuracyDot);
        notificationIcon = findViewById(R.id.notificationIcon);
        etSearch                   = findViewById(R.id.etSearch);
        btnClearSearch             = findViewById(R.id.btnClearSearch);
        searchSuggestionsContainer = findViewById(R.id.searchSuggestionsContainer);
        rvSearchSuggestions        = findViewById(R.id.rvSearchSuggestions);
        welcomeMessage = findViewById(R.id.welcomeMessage);
        userEmail      = findViewById(R.id.userEmail);
        catPlumbing  = findViewById(R.id.catPlumbing);
        catElectrical= findViewById(R.id.catElectrical);
        catCleaning  = findViewById(R.id.catCleaning);
        catRepair    = findViewById(R.id.catRepair);
        catAC        = findViewById(R.id.catAC);
        catPainting  = findViewById(R.id.catPainting);
        catCarpentry = findViewById(R.id.catCarpentry);
        catMore      = findViewById(R.id.catMore);
        quickBookService  = findViewById(R.id.quickBookService);
        quickEmergency    = findViewById(R.id.quickEmergency);
        quickTrackBooking = findViewById(R.id.quickTrackBooking);
        featuredCard1 = findViewById(R.id.featuredCard1);
        featuredCard2 = findViewById(R.id.featuredCard2);
        featuredCard3 = findViewById(R.id.featuredCard3);
        providerDashboardSection = findViewById(R.id.providerDashboardSection);
        listBusinessSection  = findViewById(R.id.listBusinessSection);
        btnListBusinessStart = findViewById(R.id.btnListBusinessStart);
        bannersSection    = findViewById(R.id.bannersSection);
        offersSection     = findViewById(R.id.offersSection);
        bannerContentContainer = findViewById(R.id.bannerContentContainer);
        bannerLoadingState = findViewById(R.id.bannerLoadingState);
        bannerEmptyState = findViewById(R.id.bannerEmptyState);
        bannerErrorState = findViewById(R.id.bannerErrorState);
        tvBannerErrorMessage = findViewById(R.id.tvBannerErrorMessage);
        btnRetryBannerLoad = findViewById(R.id.btnRetryBannerLoad);
        vpBannersCarousel = findViewById(R.id.vpBannersCarousel);
        rvOffersStrip     = findViewById(R.id.rvOffersStrip);
        bannerDotsLayout  = findViewById(R.id.bannerDotsLayout);
        offersContentContainer = findViewById(R.id.offersContentContainer);
        offersLoadingState = findViewById(R.id.offersLoadingState);
        offersEmptyState = findViewById(R.id.offersEmptyState);
        offersErrorState = findViewById(R.id.offersErrorState);
        tvOffersErrorMessage = findViewById(R.id.tvOffersErrorMessage);
        btnRetryOffersLoad = findViewById(R.id.btnRetryOffersLoad);

        if (btnRetryBannerLoad != null) btnRetryBannerLoad.setOnClickListener(v -> { setBannerLoadingState(); loadHomeBanners(); });
        if (btnRetryOffersLoad != null) btnRetryOffersLoad.setOnClickListener(v -> { setOffersLoadingState(); loadHomeOffers(); });

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

        homeSwipeRefresh = findViewById(R.id.homeSwipeRefresh);
        if (homeSwipeRefresh != null) {
            homeSwipeRefresh.setColorSchemeResources(R.color.primary);
            homeSwipeRefresh.setOnRefreshListener(() -> refreshHomeContent(true));
        }

        startSkeletonPulse(bannerLoadingState);
        startSkeletonPulse(offersLoadingState);
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
        pendingHomeLoads = 2;
        setBannerLoadingState();
        setOffersLoadingState();
        if (fromSwipe && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(true);
        loadHomeBanners();
        loadHomeOffers();
    }

    private void loadHomeBanners() {
        if (bannerApi == null) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
        bannerApi.getBanners(BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, "*", "eq.true", "(expiry_date.is.null,expiry_date.gte." + today + ")", "priority.asc,created_at.desc")
                .enqueue(new Callback<List<Banner>>() {
                    @Override public void onResponse(Call<List<Banner>> call, retrofit2.Response<List<Banner>> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful() && response.body() != null) {
                                List<Banner> list = response.body();
                                for (Banner b : list) if (b != null && b.imageUrl != null && !b.imageUrl.startsWith("http")) b.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", b.imageUrl);
                                showBannersOnHome(list);
                            } else { showBannerErrorState(getString(R.string.home_banner_error_message)); }
                            markHomeSectionLoaded();
                        });
                    }
                    @Override public void onFailure(Call<List<Banner>> call, Throwable t) { runOnUiThread(() -> { showBannerErrorState(getString(R.string.home_banner_error_message)); markHomeSectionLoaded(); }); }
                });
    }

    private void loadHomeOffers() {
        if (offerApi == null) return;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
        offerApi.getOffers(BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, "*", "eq.true", "(expiry_date.is.null,expiry_date.gte." + today + ")")
                .enqueue(new Callback<List<Offer>>() {
                    @Override public void onResponse(Call<List<Offer>> call, retrofit2.Response<List<Offer>> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful() && response.body() != null) { showOffersOnHome(response.body()); }
                            else { showOffersErrorState(getString(R.string.home_offers_error_message)); }
                            markHomeSectionLoaded();
                        });
                    }
                    @Override public void onFailure(Call<List<Offer>> call, Throwable t) { runOnUiThread(() -> { showOffersErrorState(getString(R.string.home_offers_error_message)); markHomeSectionLoaded(); }); }
                });
    }

    private void markHomeSectionLoaded() {
        pendingHomeLoads = Math.max(0, pendingHomeLoads - 1);
        if (pendingHomeLoads == 0 && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(false);
    }

    private void showBannersOnHome(List<Banner> list) {
        if (bannersSection == null || vpBannersCarousel == null || bannerDotsLayout == null) return;
        homeBanners.clear();
        if (list != null) {
            for (Banner b : list) if (b != null) homeBanners.add(b);
        }
        if (homeBanners.isEmpty()) { bannersSection.setVisibility(View.VISIBLE); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.VISIBLE); stopBannerAutoSlide(); if (bannerDotsMediator != null) { bannerDotsMediator.detach(); bannerDotsMediator = null; } return; }
        bannersSection.setVisibility(View.VISIBLE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.VISIBLE); vpBannersCarousel.setVisibility(View.VISIBLE);
        if (homeBannerAdapter == null) { homeBannerAdapter = new HomeBannerAdapter(this, homeBanners, b -> { if (b == null) return; String type = b.redirectType == null ? "" : b.redirectType.trim().toLowerCase(Locale.US); String rid = b.redirectId == null ? "" : b.redirectId.trim(); if (rid.isEmpty()) return; if ("service".equals(type) || "category".equals(type)) { openProvidersList(rid); } else if ("external".equals(type) || rid.startsWith("http")) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(rid))); } catch (Exception ignored) {} } }); vpBannersCarousel.setAdapter(homeBannerAdapter); }
        else { homeBannerAdapter.setItems(homeBanners); homeBannerAdapter.notifyDataSetChanged(); }
        if (bannerPageCallback == null) { bannerPageCallback = new ViewPager2.OnPageChangeCallback() { @Override public void onPageSelected(int position) { super.onPageSelected(position); if (bannerDotsLayout.getTabCount() == 0) return; TabLayout.Tab tab = bannerDotsLayout.getTabAt(position); if (tab != null) bannerDotsLayout.selectTab(tab); } }; vpBannersCarousel.registerOnPageChangeCallback(bannerPageCallback); }
        if (bannerDotsMediator != null) bannerDotsMediator.detach();
        bannerDotsMediator = new TabLayoutMediator(bannerDotsLayout, vpBannersCarousel, (tab, position) -> { }); bannerDotsMediator.attach();
        startBannerAutoSlide();
    }

    private void startBannerAutoSlide() {
        stopBannerAutoSlide(); if (vpBannersCarousel == null || homeBanners.size() <= 1) return;
        bannerAutoSlideRunnable = new Runnable() { @Override public void run() { if (homeBanners.isEmpty()) return; int next = (vpBannersCarousel.getCurrentItem() + 1) % homeBanners.size(); vpBannersCarousel.setCurrentItem(next, true); bannerAutoSlideHandler.postDelayed(this, 3000); } };
        bannerAutoSlideHandler.postDelayed(bannerAutoSlideRunnable, 3000);
    }

    private void stopBannerAutoSlide() { if (bannerAutoSlideRunnable != null) { bannerAutoSlideHandler.removeCallbacks(bannerAutoSlideRunnable); bannerAutoSlideRunnable = null; } }

    private void showOffersOnHome(List<Offer> list) {
        if (offersSection == null || rvOffersStrip == null) return;
        homeOffers.clear(); if (list != null) { for (Offer o : list) if (o != null) homeOffers.add(o); }
        if (homeOffers.isEmpty()) { offersSection.setVisibility(View.VISIBLE); if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.VISIBLE); return; }
        offersSection.setVisibility(View.VISIBLE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.GONE); if (offersContentContainer != null) offersContentContainer.setVisibility(View.VISIBLE);
        if (homeOfferAdapter == null) { homeOfferAdapter = new HomeOfferAdapter(this, homeOffers, offer -> { if (offer == null) return; Toast.makeText(this, "Use code: " + offer.offerCode + " for " + ("percentage".equalsIgnoreCase(offer.discountType) ? (int) offer.discountValue + "% OFF" : "₹" + (int) offer.discountValue + " OFF"), Toast.LENGTH_LONG).show(); }); rvOffersStrip.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)); rvOffersStrip.setAdapter(homeOfferAdapter); }
        else { homeOfferAdapter.setItems(homeOffers); homeOfferAdapter.notifyDataSetChanged(); }
    }

    private void requestNotificationPermission() { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ); } } }

    private void setBannerLoadingState() { if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.VISIBLE); }
    private void showBannerErrorState(String message) { if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE); if (tvBannerErrorMessage != null) tvBannerErrorMessage.setText(message == null || message.trim().isEmpty() ? getString(R.string.home_banner_error_message) : message); if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE); if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE); if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE); if (bannerErrorState != null) bannerErrorState.setVisibility(View.VISIBLE); stopBannerAutoSlide(); }
    private void setOffersLoadingState() { if (offersSection != null) offersSection.setVisibility(View.VISIBLE); if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.GONE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.VISIBLE); }
    private void showOffersErrorState(String message) { if (offersSection != null) offersSection.setVisibility(View.VISIBLE); if (tvOffersErrorMessage != null) tvOffersErrorMessage.setText(message == null || message.trim().isEmpty() ? getString(R.string.home_offers_error_message) : message); if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE); if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE); if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE); if (offersErrorState != null) offersErrorState.setVisibility(View.VISIBLE); }

    private void startSkeletonPulse(View target) { if (target == null) return; ObjectAnimator animator = ObjectAnimator.ofFloat(target, View.ALPHA, 0.55f, 1f); animator.setDuration(700); animator.setRepeatMode(ObjectAnimator.REVERSE); animator.setRepeatCount(ObjectAnimator.INFINITE); animator.start(); skeletonAnimators.add(animator); }

    private void startHomeAutoRefresh() { stopHomeAutoRefresh(); homeAutoRefreshRunnable = new Runnable() { @Override public void run() { refreshHomeContent(false); MAIN.postDelayed(this, HOME_AUTO_REFRESH_MS); } }; MAIN.postDelayed(homeAutoRefreshRunnable, HOME_AUTO_REFRESH_MS); }
    private void stopHomeAutoRefresh() { if (homeAutoRefreshRunnable != null) { MAIN.removeCallbacks(homeAutoRefreshRunnable); homeAutoRefreshRunnable = null; } }

    private void redirectToLogin() { Intent i = new Intent(this, LoginActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); }
}
