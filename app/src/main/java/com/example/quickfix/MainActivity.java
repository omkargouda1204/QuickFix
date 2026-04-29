package com.example.quickfix;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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

import com.bumptech.glide.Glide;
import com.example.quickfix.adapters.SearchSuggestionsAdapter;
import com.example.quickfix.adapters.HomeBannerAdapter;
import com.example.quickfix.adapters.HomeOfferAdapter;
import com.example.quickfix.api.BannerApi;
import com.example.quickfix.api.OfferApi;
import com.example.quickfix.api.RetrofitProvider;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import android.speech.RecognizerIntent;
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
    private static final int    VOICE_PERMISSION_REQ  = 2001;
    private static final int    VOICE_SEARCH_REQ = 1;
    private static final long   HOME_AUTO_REFRESH_MS = 60_000L;
    private static final String SEARCH_PREFS = "home_search_prefs";
    private static final String KEY_LAST_SEARCH_QUERY = "last_search_query";

    private LinearLayout        locationSelector;
    private TextView            tvSelectedLocation;
    private ImageView           ivLocationPulse;
    private View                locationAccuracyDot;
    private AppLocationManager  locationManager;

    private ImageView notificationIcon;

    private EditText     etSearch;
    private ImageView    micBtn;
    private ImageView    btnClearSearch;
    private LinearLayout searchSuggestionsContainer;
    private RecyclerView rvSearchSuggestions;
    private SearchSuggestionsAdapter suggestionsAdapter;
    private final Handler debounce = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private String lastSearchQuery = "";

    private TextView     welcomeMessage;
    private TextView     userEmail;

    private LinearLayout catPlumbing, catElectrical, catCleaning, catRepair;
    private LinearLayout catAC, catPainting, catCarpentry, catMore;

    private LinearLayout quickBookService, quickEmergency, quickTrackBooking;

    private RecyclerView rvTopRated, rvTopBooked;
    private com.example.quickfix.adapters.TopRatedAdapter topRatedAdapter;
    private com.example.quickfix.adapters.TopRatedAdapter topBookedAdapter;
    private final List<ProviderProfile> topRatedProviders = new ArrayList<>();
    private final List<ProviderProfile> topBookedProviders = new ArrayList<>();
    private View topRatedLoadingState, topBookedLoadingState;
    private TextView btnSeeAllTopRated, btnSeeAllTopBooked;

    private CardView cardImageDetection;
    private View providerDashboardSection;

    private View continueSection;
    private View continueContentContainer, continueLoadingState, continueEmptyState, continueErrorState;
    private TextView tvContinueHeading, tvContinueSubtitle, tvContinueErrorMessage;
    private MaterialButton btnContinueAction, btnRetryContinueLoad;
    private Booking continueBooking;

    private LinearLayout   listBusinessSection;
    private MaterialButton btnListBusinessStart;

    private LinearLayout       bannersSection, offersSection;
    private View               bannerContentContainer, bannerLoadingState, bannerEmptyState, bannerErrorState;
    private TextView           tvBannerErrorMessage;
    private MaterialButton     btnRetryBannerLoad;
    private ViewPager2         vpBannersCarousel;
    private ImageView          bannerImg;
    private RecyclerView       rvOffersStrip;
    private WormDotsIndicator  bannerDotsLayout;
    private HomeBannerAdapter  homeBannerAdapter;
    private HomeOfferAdapter   homeOfferAdapter;
    private BannerApi          bannerApi;
    private OfferApi           offerApi;
    private final List<Banner> homeBanners = new ArrayList<>();
    private final List<Offer>  homeOffers  = new ArrayList<>();
    private final Handler bannerAutoSlideHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerAutoSlideRunnable;
    private final List<ObjectAnimator> skeletonAnimators = new ArrayList<>();
    private int pendingHomeLoads = 0;

    private View               offersContentContainer, offersLoadingState, offersEmptyState, offersErrorState;
    private TextView           tvOffersErrorMessage;
    private MaterialButton     btnRetryOffersLoad;

    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout homeSwipeRefresh;

    private View navHome, navBookings, navMessages, navProfile;
    private ImageView navHomeIcon, navBookingsIcon, navMessagesIcon, navProfileIcon;
    private TextView navHomeText, navBookingsText, navMessagesText, navProfileText;

    private FirebaseAuth mAuth;

    private LinearLayout catPlumbing_temp, catElectrical_temp, catCleaning_temp, catRepair_temp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { redirectToLogin(); return; }

        bannerApi = RetrofitProvider.get().create(BannerApi.class);
        offerApi = RetrofitProvider.get().create(OfferApi.class);
        locationManager = new AppLocationManager(this);

        initViewsSafe();
        setupLocationSelector();
        setupSearchBar();
        setupCategoryClickListeners();
        setupQuickActionListeners();
        setupNewSections();
        setupBottomNav();
        setupPullToRefresh();
        setupListBusinessCard(user);
        loadUserData(user);
        requestNotificationPermission();
        
        // Make banner and offers sections visible during loading
        if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE);
        if (offersSection != null) offersSection.setVisibility(View.VISIBLE);
        
        // Load home content from database-backed APIs.
        reloadHomeData(false);

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                SupabaseClient.saveFcmToken(user.getUid(), task.getResult());
            }
        });
    }

    private void initViewsSafe() {
        try {
            locationSelector = findViewSafe(R.id.locationSelector);
            tvSelectedLocation = findViewSafe(R.id.tvSelectedLocation);
            ivLocationPulse = findViewSafe(R.id.ivLocationPulse);
            locationAccuracyDot = findViewSafe(R.id.locationAccuracyDot);
            notificationIcon = findViewSafe(R.id.notificationIcon);
            notificationIcon.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationsActivity.class));
            });
            etSearch = findViewSafe(R.id.etSearch);
            micBtn = findViewSafe(R.id.micBtn);
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

            rvTopRated = findViewSafe(R.id.rvTopRated);
            rvTopBooked = findViewSafe(R.id.rvTopBooked);
            topRatedLoadingState = findViewSafe(R.id.topRatedLoadingState);
            topBookedLoadingState = findViewSafe(R.id.topBookedLoadingState);
            btnSeeAllTopRated = findViewSafe(R.id.btnSeeAllTopRated);
            btnSeeAllTopBooked = findViewSafe(R.id.btnSeeAllTopBooked);

            cardImageDetection = findViewSafe(R.id.cardImageDetection);

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
            btnRetryBannerLoad.setOnClickListener(v -> loadBanners());
            vpBannersCarousel = findViewSafe(R.id.vpBannersCarousel);
            bannerImg = findViewSafe(R.id.bannerImg);
            rvOffersStrip = findViewSafe(R.id.rvOffersStrip);
            bannerDotsLayout = findViewSafe(R.id.bannerDotsLayout);

            offersContentContainer = findViewSafe(R.id.offersContentContainer);
            offersLoadingState = findViewSafe(R.id.offersLoadingState);
            offersEmptyState = findViewSafe(R.id.offersEmptyState);
            offersErrorState = findViewSafe(R.id.offersErrorState);
            tvOffersErrorMessage = findViewSafe(R.id.tvOffersErrorMessage);
            btnRetryOffersLoad = findViewSafe(R.id.btnRetryOffersLoad);
            btnRetryOffersLoad.setOnClickListener(v -> loadOffers());

            if (micBtn != null) {
                micBtn.setOnClickListener(v -> launchVoiceSearch());
            }

            bannerApi = RetrofitProvider.get().create(BannerApi.class);
            offerApi = RetrofitProvider.get().create(OfferApi.class);

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

            if (vpBannersCarousel != null && homeBannerAdapter == null) {
                homeBannerAdapter = new HomeBannerAdapter(this, homeBanners, b -> {
                    if (b == null) return;
                    String rid = b.redirectId == null ? "" : b.redirectId.trim();
                    if (rid.isEmpty()) return;
                    openProvidersList(rid);
                });
                vpBannersCarousel.setAdapter(homeBannerAdapter);
            }

            if (rvOffersStrip != null) {
                homeOfferAdapter = new HomeOfferAdapter(this, homeOffers, offer -> {
                    if (offer == null) return;
                    String code = offer.offerCode == null ? "" : offer.offerCode.trim();
                    if (!code.isEmpty()) {
                        copyCouponCode(code);
                    }
                });
                rvOffersStrip.setAdapter(homeOfferAdapter);
                LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
                rvOffersStrip.setLayoutManager(layoutManager);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views", e);
        }
    }

    private void setupLocationSelector() {
        if (locationSelector != null) {
            locationSelector.setOnClickListener(v -> {
                LocationSearchBottomSheet.show(this, locationManager,
                        new LocationSearchBottomSheet.PermissionDelegate() {
                            @Override
                            public boolean hasLocationPermission() {
                                return ContextCompat.checkSelfPermission(MainActivity.this,
                                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        || ContextCompat.checkSelfPermission(MainActivity.this,
                                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                            }

                            @Override
                            public void requestLocationPermission() {
                                ActivityCompat.requestPermissions(
                                        MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                                        LOCATION_PERMISSION_REQ
                                );
                            }
                        }, suggestion -> {
                            if (tvSelectedLocation == null) return;
                            String city = suggestion == null ? "" : (suggestion.city == null ? "" : suggestion.city.trim());
                            tvSelectedLocation.setText(city.isEmpty() ? "Select Location" : city);
                            reloadHomeData(false);
                        });
            });
        }

        if (locationManager != null && tvSelectedLocation != null) {
            String label = locationManager.getSavedLocationLabel();
            tvSelectedLocation.setText(label.isEmpty() ? "Select Location" : label);
        }
    }

    private void setupSearchBar() {
        if (etSearch != null) {
            etSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(etSearch.getText().toString());
                    return true;
                }
                return false;
            });
        }
    }

    private void setupCategoryClickListeners() {
        View.OnClickListener listener = v -> {
            String category = "";
            int id = v.getId();
            if (id == R.id.catPlumbing) category = "Plumbing";
            else if (id == R.id.catElectrical) category = "Electrical";
            else if (id == R.id.catCleaning) category = "Cleaning";
            else if (id == R.id.catRepair) category = "Repair";
            else if (id == R.id.catAC) category = "AC Service";
            else if (id == R.id.catPainting) category = "Painting";
            else if (id == R.id.catCarpentry) category = "Carpentry";
            else if (id == R.id.catMore) {
                startActivity(new Intent(this, CategoriesActivity.class));
                return;
            }

            if (!category.isEmpty()) {
                openProvidersList(category);
            }
        };

        if (catPlumbing != null) catPlumbing.setOnClickListener(listener);
        if (catElectrical != null) catElectrical.setOnClickListener(listener);
        if (catCleaning != null) catCleaning.setOnClickListener(listener);
        if (catRepair != null) catRepair.setOnClickListener(listener);
        if (catAC != null) catAC.setOnClickListener(listener);
        if (catPainting != null) catPainting.setOnClickListener(listener);
        if (catCarpentry != null) catCarpentry.setOnClickListener(listener);
        if (catMore != null) catMore.setOnClickListener(listener);
    }

    private void setupQuickActionListeners() {
        if (quickBookService != null) quickBookService.setOnClickListener(v -> openProvidersList(""));
        if (quickEmergency != null) quickEmergency.setOnClickListener(v -> {
            startActivity(new Intent(this, EmergencyHelpActivity.class));
        });
        if (quickTrackBooking != null) quickTrackBooking.setOnClickListener(v -> {
            startActivity(new Intent(this, UserBookingsActivity.class));
        });
    }

    private void setupNewSections() {
        if (rvTopRated != null) {
            topRatedAdapter = new com.example.quickfix.adapters.TopRatedAdapter(this, topRatedProviders, p -> {
                Intent intent = new Intent(this, ProviderDetailActivity.class);
                intent.putExtra("provider_id", p.id);
                startActivity(intent);
            });
            rvTopRated.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            rvTopRated.setAdapter(topRatedAdapter);
        }

        if (rvTopBooked != null) {
            topBookedAdapter = new com.example.quickfix.adapters.TopRatedAdapter(this, topBookedProviders, p -> {
                Intent intent = new Intent(this, ProviderDetailActivity.class);
                intent.putExtra("provider_id", p.id);
                startActivity(intent);
            });
            rvTopBooked.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            rvTopBooked.setAdapter(topBookedAdapter);
        }

        if (btnSeeAllTopRated != null) btnSeeAllTopRated.setOnClickListener(v -> openProvidersList(""));
        if (btnSeeAllTopBooked != null) btnSeeAllTopBooked.setOnClickListener(v -> {
            startActivity(new Intent(this, CategoriesActivity.class));
        });
        
        if (cardImageDetection != null) cardImageDetection.setOnClickListener(v -> openImageDetection());
    }

    private void setupBottomNav() {
        if (navHome != null) navHome.setOnClickListener(v -> {
            // Already on MainActivity, no action needed
        });
        if (navBookings != null) navBookings.setOnClickListener(v -> {
            startActivity(new Intent(this, UserBookingsActivity.class));
        });
        if (navMessages != null) navMessages.setOnClickListener(v -> {
            startActivity(new Intent(this, ChatThreadsActivity.class));
        });
        if (navProfile != null) navProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });
    }
    
    private void setupPullToRefresh() {
        if (homeSwipeRefresh != null) {
            homeSwipeRefresh.setColorSchemeResources(R.color.primary);
            homeSwipeRefresh.setOnRefreshListener(() -> reloadHomeData(true));
        }
    }

    private void setupListBusinessCard(FirebaseUser user) {
        // Make the list business section visible
        if (listBusinessSection != null) {
            listBusinessSection.setVisibility(View.VISIBLE);
        }
        
        if (btnListBusinessStart != null) {
            btnListBusinessStart.setOnClickListener(v -> {
                try {
                    // Always check status first
                    startActivity(new Intent(this, ApplicationStatusActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "Error opening status screen: " + e.getMessage());
                    Toast.makeText(this, "Unable to check application status", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadUserData(FirebaseUser user) {
        String fallbackName = user.getDisplayName() != null ? user.getDisplayName().trim() : "";
        if (fallbackName.isEmpty()) fallbackName = "User";
        if (welcomeMessage != null) welcomeMessage.setText("Hello, " + fallbackName);

        SupabaseClient.getUserDisplayName(user.getUid(), user.getEmail(), new SupabaseClient.StringCallback() {
            @Override
            public void onSuccess(String value) {
                String dbName = value == null ? "" : value.trim();
                if (!dbName.isEmpty() && welcomeMessage != null) {
                    welcomeMessage.setText("Hello, " + dbName);
                }
            }

            @Override
            public void onError(String message) {
                Log.d(TAG, "Using fallback display name: " + message);
            }
        });

        // Set professional greeting subtitle
        if (userEmail != null) {
            userEmail.setText("Find trusted services near you");
            userEmail.setVisibility(View.VISIBLE);
        }
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ);
            }
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) return;
        openProvidersList(query);
    }

    private void launchVoiceSearch() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a service to search");
            startActivityForResult(intent, VOICE_SEARCH_REQ);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice search is not available on this device.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "launchVoiceSearch failed", e);
            Toast.makeText(this, "Could not start voice search.", Toast.LENGTH_SHORT).show();
        }
    }

    private void openProvidersList(String category) {
        try {
            Intent intent = new Intent(this, ProvidersListActivity.class);
            if (category != null && !category.trim().isEmpty()) {
                intent.putExtra("category", category);
                intent.putExtra("query", category);
            }
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening providers list", e);
            Toast.makeText(this, "Unable to open services", Toast.LENGTH_SHORT).show();
        }
    }

    private void openImageDetection() {
        startActivity(new Intent(this, ImageDetectionActivity.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_SEARCH_REQ || resultCode != RESULT_OK || data == null) {
            return;
        }

        ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches == null || matches.isEmpty()) {
            Toast.makeText(this, "No speech recognized.", Toast.LENGTH_SHORT).show();
            return;
        }

        String spoken = matches.get(0) == null ? "" : matches.get(0).trim();
        if (spoken.isEmpty()) {
            Toast.makeText(this, "No speech recognized.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (etSearch != null) {
            etSearch.setText(spoken);
            etSearch.setSelection(spoken.length());
        }
        performSearch(spoken);
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void reloadHomeData(boolean fromPullToRefresh) {
        if (fromPullToRefresh) {
            homeBanners.clear();
            homeOffers.clear();
            topRatedProviders.clear();
            topBookedProviders.clear();
            if (homeBannerAdapter != null) homeBannerAdapter.notifyDataSetChanged();
            if (homeOfferAdapter != null) homeOfferAdapter.notifyDataSetChanged();
            if (topRatedAdapter != null) topRatedAdapter.notifyDataSetChanged();
            if (topBookedAdapter != null) topBookedAdapter.notifyDataSetChanged();
        }
        pendingHomeLoads = 0;
        loadBanners();
        loadOffers();
        loadTopRated();
        loadTopBooked();
    }

    private void beginHomeLoad() {
        pendingHomeLoads++;
    }

    private void endHomeLoad() {
        pendingHomeLoads = Math.max(0, pendingHomeLoads - 1);
        if (pendingHomeLoads == 0 && homeSwipeRefresh != null) {
            homeSwipeRefresh.setRefreshing(false);
        }
    }

    private void loadBanners() {
        Log.d(TAG, "Loading banners from DB and Bucket...");
        beginHomeLoad();
        showBannerLoading(true);

        // Task 1: Fetch from Database
        bannerApi.getBanners(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "application/json",
                "*",
                "eq.true",
                "gt." + getTodayIsoDate(),
                "priority.asc"
        ).enqueue(new retrofit2.Callback<List<Banner>>() {
            @Override
            public void onResponse(Call<List<Banner>> call, retrofit2.Response<List<Banner>> response) {
                List<Banner> dbBanners = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null) {
                    dbBanners = response.body();
                    for (Banner b : dbBanners) {
                        if (b.imageUrl != null && !b.imageUrl.startsWith("http")) {
                            b.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", b.imageUrl);
                        }
                    }
                }
                fetchBucketBanners(dbBanners);
            }

            @Override
            public void onFailure(Call<List<Banner>> call, Throwable t) {
                Log.e(TAG, "Banner DB fetch failed", t);
                fetchBucketBanners(new ArrayList<>());
            }
        });
    }

    private void fetchBucketBanners(List<Banner> dbBanners) {
        SupabaseStorageHelper.listFiles("banner-images", new SupabaseStorageHelper.ListFilesCallback() {
            @Override
            public void onSuccess(List<String> fileNames) {
                List<Banner> bucketBanners = new ArrayList<>();
                if (!fileNames.isEmpty()) {
                    for (String f : fileNames) {
                        String lower = f.toLowerCase();
                        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")) {
                            Banner b = new Banner();
                            b.id = "bucket_" + f;
                            b.title = ""; 
                            b.imageUrl = SupabaseStorageHelper.getPublicObjectUrl("banner-images", f);
                            bucketBanners.add(b);
                        }
                    }
                }
                
                // Shuffle bucket banners for variety
                Collections.shuffle(bucketBanners);
                
                // Merge lists
                List<Banner> combined = new ArrayList<>(dbBanners);
                combined.addAll(bucketBanners);

                runOnUiThread(() -> {
                    showBannerLoading(false);
                    if (!combined.isEmpty()) {
                        updateBannerCarousel(combined);
                        showBannerContent(true);
                    } else {
                        onNoBanners();
                    }
                    endHomeLoad();
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Bucket list failed: " + error);
                runOnUiThread(() -> {
                    showBannerLoading(false);
                    if (!dbBanners.isEmpty()) {
                        updateBannerCarousel(dbBanners);
                        showBannerContent(true);
                    } else {
                        onNoBanners();
                    }
                    endHomeLoad();
                });
            }
        });
    }

    private void updateBannerCarousel(List<Banner> banners) {
        homeBanners.clear();
        homeBanners.addAll(banners);

        if (homeBannerAdapter == null) {
            homeBannerAdapter = new HomeBannerAdapter(this, homeBanners, b -> {
                // Handle banner click
                if (b.redirectType != null) {
                    // Logic for redirection
                    Log.d(TAG, "Banner clicked: " + b.title + " -> " + b.redirectType);
                }
            });
            if (vpBannersCarousel != null) {
                vpBannersCarousel.setAdapter(homeBannerAdapter);
                if (bannerDotsLayout != null) {
                    bannerDotsLayout.attachTo(vpBannersCarousel);
                }
            }
        } else {
            homeBannerAdapter.notifyDataSetChanged();
        }

        // Use carousel if more than one, else static
        if (homeBanners.size() > 1) {
            if (vpBannersCarousel != null) vpBannersCarousel.setVisibility(View.VISIBLE);
            if (bannerDotsLayout != null) bannerDotsLayout.setVisibility(View.VISIBLE);
            if (bannerImg != null) bannerImg.setVisibility(View.GONE);
            startBannerAutoSlide();
        } else if (homeBanners.size() == 1) {
            if (vpBannersCarousel != null) vpBannersCarousel.setVisibility(View.GONE);
            if (bannerDotsLayout != null) bannerDotsLayout.setVisibility(View.GONE);
            if (bannerImg != null) {
                bannerImg.setVisibility(View.VISIBLE);
                Glide.with(this).load(homeBanners.get(0).imageUrl).centerCrop().into(bannerImg);
            }
        }
    }

    private void onNoBanners() {
        Log.d(TAG, "No valid banners found.");
        if (bannersSection != null) bannersSection.setVisibility(View.GONE);
    }
    
    private void loadOffers() {
        if (offerApi == null) {
            Log.e(TAG, "Offer API is null");
            endHomeLoad();
            return;
        }
        
        Log.d(TAG, "Loading offers from Supabase...");
        beginHomeLoad();
        showOffersLoading(true);
        
        offerApi.getOffers(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "application/json",
                "*",
                "eq.true",
                "gt." + getTodayIsoDate(),
                "created_at.desc"
        ).enqueue(new Callback<List<Offer>>() {
            @Override
            public void onResponse(Call<List<Offer>> call, retrofit2.Response<List<Offer>> response) {
                runOnUiThread(() -> {
                    showOffersLoading(false);
                    Log.d(TAG, "Offer API response code: " + response.code());
                    
                    if (response.isSuccessful() && response.body() != null) {
                        List<Offer> offers = response.body();
                        Log.d(TAG, "Offers loaded successfully: " + offers.size() + " items");
                        
                        // Client-side validation: filter only active and not expired offers
                        List<Offer> validOffers = new ArrayList<>();
                        String today = getTodayIsoDate();
                        
                        for (Offer offer : offers) {
                            if (offer != null && offer.isActive) {
                                // Check expiry date
                                if (offer.expiryDate != null && !offer.expiryDate.isEmpty()) {
                                    if (offer.expiryDate.compareTo(today) >= 0) {
                                        // Normalize offer code for case-insensitive matching
                                        if (offer.offerCode != null) {
                                            offer.offerCode = offer.offerCode.trim().toUpperCase();
                                        }
                                        validOffers.add(offer);
                                    }
                                }
                            }
                        }
                        
                        homeOffers.clear();
                        homeOffers.addAll(validOffers);
                        
                        if (homeOffers.isEmpty()) {
                            Log.d(TAG, "No valid offers found after validation");
                            showOffersEmpty(true);
                        } else {
                            Log.d(TAG, "Displaying " + homeOffers.size() + " validated offers");
                            showOffersContent(true);
                            if (homeOfferAdapter != null) {
                                homeOfferAdapter.notifyDataSetChanged();
                            }
                        }
                    } else {
                        String raw = "";
                        try {
                            raw = response.errorBody() != null ? response.errorBody().string() : "";
                        } catch (Exception ignored) {
                        }
                        String errorMsg = "Failed to load offers (HTTP " + response.code() + ")";
                        Log.e(TAG, errorMsg);
                        if (!raw.isEmpty()) Log.e(TAG, "Offer error body: " + raw);
                        showOffersError(true, errorMsg);
                    }
                    endHomeLoad();
                });
            }
            
            @Override
            public void onFailure(Call<List<Offer>> call, Throwable t) {
                runOnUiThread(() -> {
                    showOffersLoading(false);
                    String errorMsg = "Network error: " + t.getMessage();
                    Log.e(TAG, "Offer API network failure", t);
                    showOffersError(true, errorMsg);
                    endHomeLoad();
                });
            }
        });
    }
    
    private void showBannerLoading(boolean show) {
        if (bannersSection != null) bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (bannerLoadingState != null) {
            bannerLoadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE);
            if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE);
            if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE);
        }
    }
    
    private void showBannerEmpty(boolean show) {
        // If show is true (empty), we hide the whole section as per request
        if (bannersSection != null) bannersSection.setVisibility(show ? View.GONE : View.VISIBLE);
        if (bannerEmptyState != null) {
            bannerEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE);
            if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE);
            if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE);
        }
    }
    
    private void showBannerError(boolean show, String message) {
        boolean shouldShowError = show && homeBanners.isEmpty();

        // If it's an error and we have no banners, hide the section instead of showing error card?
        // User said "Else → hide banner section". 
        // But usually errors should be shown for retry. I'll hide it for a cleaner UI if desired.
        if (bannersSection != null) bannersSection.setVisibility(shouldShowError ? View.GONE : View.VISIBLE);

        if (bannerErrorState != null) {
            bannerErrorState.setVisibility(shouldShowError ? View.VISIBLE : View.GONE);
        }
        if (tvBannerErrorMessage != null && message != null && shouldShowError) {
            tvBannerErrorMessage.setText(message);
        }
        if (shouldShowError) {
            if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE);
            if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE);
            if (bannerContentContainer != null) bannerContentContainer.setVisibility(View.GONE);
        } else if (!homeBanners.isEmpty()) {
            showBannerContent(true);
        }
    }
    
    private void showBannerContent(boolean show) {
        if (bannersSection != null) bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (bannerContentContainer != null) {
            bannerContentContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (bannerLoadingState != null) bannerLoadingState.setVisibility(View.GONE);
            if (bannerEmptyState != null) bannerEmptyState.setVisibility(View.GONE);
            if (bannerErrorState != null) bannerErrorState.setVisibility(View.GONE);
        }
    }

    private void updateHeroBanner(String bannerUrl) {
        if (bannerImg == null) return;

        // Home uses a single hero banner; keep carousel hidden for this flow.
        if (vpBannersCarousel != null) vpBannersCarousel.setVisibility(View.GONE);
        if (bannerDotsLayout != null) bannerDotsLayout.setVisibility(View.GONE);
        bannerImg.setVisibility(View.VISIBLE);

        if (bannerUrl != null && !bannerUrl.isEmpty()) {
            Glide.with(this)
                .load(bannerUrl)
                .placeholder(R.drawable.default_banner)
                .error(R.drawable.default_banner)
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, Object model, @NonNull com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                        if (bannerImg != null) {
                            bannerImg.setVisibility(View.VISIBLE);
                            bannerImg.setImageResource(R.drawable.default_banner);
                        }
                        if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull android.graphics.drawable.Drawable resource, @NonNull Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, @NonNull com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                        if (bannerImg != null) bannerImg.setVisibility(View.VISIBLE);
                        if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(bannerImg);
        } else {
            bannerImg.setImageResource(R.drawable.default_banner);
            if (bannersSection != null) bannersSection.setVisibility(View.VISIBLE);
        }
    }
    
    private void showOffersLoading(boolean show) {
        if (offersSection != null) offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (offersLoadingState != null) {
            offersLoadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE);
            if (offersErrorState != null) offersErrorState.setVisibility(View.GONE);
            if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE);
        }
    }
    
    private void showOffersEmpty(boolean show) {
        if (offersSection != null) offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (offersEmptyState != null) {
            offersEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE);
            if (offersErrorState != null) offersErrorState.setVisibility(View.GONE);
            if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE);
        }
    }
    
    private void showOffersError(boolean show, String message) {
        boolean shouldShowError = show && homeOffers.isEmpty();

        if (offersSection != null) offersSection.setVisibility(shouldShowError ? View.VISIBLE : View.GONE);
        if (offersErrorState != null) {
            offersErrorState.setVisibility(shouldShowError ? View.VISIBLE : View.GONE);
        }
        if (tvOffersErrorMessage != null && message != null && shouldShowError) {
            tvOffersErrorMessage.setText(message);
        }
        if (shouldShowError) {
            if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE);
            if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE);
            if (offersContentContainer != null) offersContentContainer.setVisibility(View.GONE);
        } else if (!homeOffers.isEmpty()) {
            showOffersContent(true);
        }
    }
    
    private void showOffersContent(boolean show) {
        if (offersSection != null) offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (offersContentContainer != null) {
            offersContentContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (show) {
            if (offersLoadingState != null) offersLoadingState.setVisibility(View.GONE);
            if (offersEmptyState != null) offersEmptyState.setVisibility(View.GONE);
            if (offersErrorState != null) offersErrorState.setVisibility(View.GONE);
        }
    }

    private void loadTopRated() {
        beginHomeLoad();
        if (topRatedLoadingState != null) topRatedLoadingState.setVisibility(View.VISIBLE);
        
        double lat = locationManager != null ? locationManager.getSavedLat() : Double.NaN;
        double lng = locationManager != null ? locationManager.getSavedLng() : Double.NaN;
        
        if (topRatedAdapter != null) topRatedAdapter.setUserLocation(lat, lng);

        HomeRepository.fetchTopRated(lat, lng, new HomeRepository.TopRatedCallback() {
            @Override
            public void onSuccess(List<ProviderProfile> providers, boolean isNearby) {
                if (topRatedLoadingState != null) topRatedLoadingState.setVisibility(View.GONE);
                topRatedProviders.clear();
                topRatedProviders.addAll(providers);
                if (topRatedAdapter != null) topRatedAdapter.notifyDataSetChanged();
                
                if (!isNearby && !providers.isEmpty() && !Double.isNaN(lat)) {
                    // Fallback message could be shown here
                    Log.d(TAG, "No nearby providers, showing best overall");
                }
                endHomeLoad();
            }

            @Override
            public void onError(String message) {
                if (topRatedLoadingState != null) topRatedLoadingState.setVisibility(View.GONE);
                Log.e(TAG, "Top Rated error: " + message);
                endHomeLoad();
            }
        });
    }

    private void loadTopBooked() {
        beginHomeLoad();
        if (topBookedLoadingState != null) topBookedLoadingState.setVisibility(View.VISIBLE);

        double lat = locationManager != null ? locationManager.getSavedLat() : Double.NaN;
        double lng = locationManager != null ? locationManager.getSavedLng() : Double.NaN;

        if (topBookedAdapter != null) {
             topBookedAdapter.setUserLocation(lat, lng);
        }

        HomeRepository.fetchTopBookedProviders(lat, lng, new HomeRepository.TopRatedCallback() {
            @Override
            public void onSuccess(List<ProviderProfile> providers, boolean isNearby) {
                if (topBookedLoadingState != null) topBookedLoadingState.setVisibility(View.GONE);
                topBookedProviders.clear();
                topBookedProviders.addAll(providers);
                if (topBookedAdapter != null) topBookedAdapter.notifyDataSetChanged();
                endHomeLoad();
            }

            @Override
            public void onError(String message) {
                if (topBookedLoadingState != null) topBookedLoadingState.setVisibility(View.GONE);
                Log.e(TAG, "Top Booked error: " + message);
                endHomeLoad();
            }
        });
    }
    
    private void startBannerAutoSlide() {
        if (bannerAutoSlideRunnable != null) {
            bannerAutoSlideHandler.removeCallbacks(bannerAutoSlideRunnable);
        }
        
        bannerAutoSlideRunnable = new Runnable() {
            @Override
            public void run() {
                if (homeBanners.size() > 1 && vpBannersCarousel != null) {
                    int currentItem = vpBannersCarousel.getCurrentItem();
                    int nextItem = (currentItem + 1) % homeBanners.size();
                    vpBannersCarousel.setCurrentItem(nextItem, true);
                    bannerAutoSlideHandler.postDelayed(this, 3000);
                }
            }
        };
        
        bannerAutoSlideHandler.postDelayed(bannerAutoSlideRunnable, 3000);
    }
    
    private void copyCouponCode(String code) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Coupon Code", code);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Coupon code copied: " + code, Toast.LENGTH_SHORT).show();
    }

    private String getTodayIsoDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQ) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            if (!granted) {
                Toast.makeText(this, "Location permission is required to use live location", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private <T extends View> T findViewSafe(int id) {
        return findViewById(id);
    }
}
