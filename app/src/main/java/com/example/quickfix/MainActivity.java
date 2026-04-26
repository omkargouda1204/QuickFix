package com.example.quickfix;

import android.Manifest;
import android.animation.ObjectAnimator;
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
import java.util.List;
import java.util.Locale;
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
    private View fabAiChat;

    private CardView featuredCard1, featuredCard2, featuredCard3;
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
    private RecyclerView       rvOffersStrip;
    private WormDotsIndicator  bannerDotsLayout;
    private HomeBannerAdapter  homeBannerAdapter;
    private HomeOfferAdapter   homeOfferAdapter;
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
    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    private BannerApi bannerApi;
    private OfferApi offerApi;

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
        setupFeaturedCardListeners();
        setupBottomNav();
        setupListBusinessCard(user);
        loadUserData(user);
        requestNotificationPermission();
        
        // Load banners and offers
        loadBanners();
        loadOffers();

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
            fabAiChat = findViewSafe(R.id.fabAiChat);

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
            btnRetryBannerLoad.setOnClickListener(v -> loadBanners());
            vpBannersCarousel = findViewSafe(R.id.vpBannersCarousel);
            rvOffersStrip = findViewSafe(R.id.rvOffersStrip);
            bannerDotsLayout = findViewSafe(R.id.bannerDotsLayout);

            offersContentContainer = findViewSafe(R.id.offersContentContainer);
            offersLoadingState = findViewSafe(R.id.offersLoadingState);
            offersEmptyState = findViewSafe(R.id.offersEmptyState);
            offersErrorState = findViewSafe(R.id.offersErrorState);
            tvOffersErrorMessage = findViewSafe(R.id.tvOffersErrorMessage);
            btnRetryOffersLoad = findViewSafe(R.id.btnRetryOffersLoad);
            btnRetryOffersLoad.setOnClickListener(v -> loadOffers());

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
                // TODO: Open location picker
            });
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
            else if (id == R.id.catMore) category = "More";

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
            startActivity(new Intent(this, MyBookingsActivity.class));
        });
    }

    private void setupFeaturedCardListeners() {
        if (featuredCard1 != null) featuredCard1.setOnClickListener(v -> openProvidersList("Cleaning"));
        if (featuredCard2 != null) featuredCard2.setOnClickListener(v -> openProvidersList("Electrical"));
        if (featuredCard3 != null) featuredCard3.setOnClickListener(v -> openProvidersList("Plumbing"));
    }

    private void setupBottomNav() {
        if (navHome != null) navHome.setOnClickListener(v -> {
            // Already on MainActivity, no action needed
        });
        if (navBookings != null) navBookings.setOnClickListener(v -> {
            startActivity(new Intent(this, MyBookingsActivity.class));
        });
        if (navMessages != null) navMessages.setOnClickListener(v -> {
            startActivity(new Intent(this, MessagesActivity.class));
        });
        if (navProfile != null) navProfile.setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });
    }

    private void setupListBusinessCard(FirebaseUser user) {
        if (btnListBusinessStart != null) {
            btnListBusinessStart.setOnClickListener(v -> {
                // startActivity(new Intent(this, ProviderRegistrationActivity.class));
            });
        }
    }

    private void loadUserData(FirebaseUser user) {
        if (welcomeMessage != null) welcomeMessage.setText("Hello, " + user.getDisplayName());
        if (userEmail != null) userEmail.setText(user.getEmail());
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

    private void openProvidersList(String category) {
        try {
            Intent intent = new Intent(this, ProviderListActivity.class);
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

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void loadBanners() {
        if (bannerApi == null) {
            Log.e(TAG, "Banner API is null");
            return;
        }
        
        Log.d(TAG, "Loading banners from Supabase...");
        showBannerLoading(true);
        
        bannerApi.getBanners(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "application/json",
                "*",
                "eq.true",
                "sort_order.asc"
        ).enqueue(new Callback<List<Banner>>() {
            @Override
            public void onResponse(Call<List<Banner>> call, retrofit2.Response<List<Banner>> response) {
                runOnUiThread(() -> {
                    showBannerLoading(false);
                    Log.d(TAG, "Banner API response code: " + response.code());
                    
                    if (response.isSuccessful() && response.body() != null) {
                        List<Banner> banners = response.body();
                        Log.d(TAG, "Banners loaded successfully: " + banners.size() + " items");
                        
                        homeBanners.clear();
                        homeBanners.addAll(banners);
                        
                        if (homeBanners.isEmpty()) {
                            Log.d(TAG, "No active banners found");
                            showBannerEmpty(true);
                        } else {
                            Log.d(TAG, "Displaying " + homeBanners.size() + " banners");
                            showBannerContent(true);
                            if (homeBannerAdapter != null) {
                                homeBannerAdapter.notifyDataSetChanged();
                                startBannerAutoSlide();
                            }
                        }
                    } else {
                        String errorMsg = "Failed to load banners (HTTP " + response.code() + ")";
                        Log.e(TAG, errorMsg);
                        showBannerError(true, errorMsg);
                    }
                });
            }
            
            @Override
            public void onFailure(Call<List<Banner>> call, Throwable t) {
                runOnUiThread(() -> {
                    showBannerLoading(false);
                    String errorMsg = "Network error: " + t.getMessage();
                    Log.e(TAG, "Banner API network failure", t);
                    showBannerError(true, errorMsg);
                });
            }
        });
    }
    
    private void loadOffers() {
        if (offerApi == null) {
            Log.e(TAG, "Offer API is null");
            return;
        }
        
        Log.d(TAG, "Loading offers from Supabase...");
        showOffersLoading(true);
        
        offerApi.getOffers(
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer " + BuildConfig.SUPABASE_ANON_KEY,
                "application/json",
                "*",
                "eq.true",
                "sort_order.asc"
        ).enqueue(new Callback<List<Offer>>() {
            @Override
            public void onResponse(Call<List<Offer>> call, retrofit2.Response<List<Offer>> response) {
                runOnUiThread(() -> {
                    showOffersLoading(false);
                    Log.d(TAG, "Offer API response code: " + response.code());
                    
                    if (response.isSuccessful() && response.body() != null) {
                        List<Offer> offers = response.body();
                        Log.d(TAG, "Offers loaded successfully: " + offers.size() + " items");
                        
                        homeOffers.clear();
                        homeOffers.addAll(offers);
                        
                        if (homeOffers.isEmpty()) {
                            Log.d(TAG, "No active offers found");
                            showOffersEmpty(true);
                        } else {
                            Log.d(TAG, "Displaying " + homeOffers.size() + " offers");
                            showOffersContent(true);
                            if (homeOfferAdapter != null) {
                                homeOfferAdapter.notifyDataSetChanged();
                            }
                        }
                    } else {
                        String errorMsg = "Failed to load offers (HTTP " + response.code() + ")";
                        Log.e(TAG, errorMsg);
                        showOffersError(true, errorMsg);
                    }
                });
            }
            
            @Override
            public void onFailure(Call<List<Offer>> call, Throwable t) {
                runOnUiThread(() -> {
                    showOffersLoading(false);
                    String errorMsg = "Network error: " + t.getMessage();
                    Log.e(TAG, "Offer API network failure", t);
                    showOffersError(true, errorMsg);
                });
            }
        });
    }
    
    private void showBannerLoading(boolean show) {
        if (bannersSection != null) {
            bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bannerLoadingState != null) {
            bannerLoadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private void showBannerEmpty(boolean show) {
        if (bannersSection != null) {
            bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bannerEmptyState != null) {
            bannerEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private void showBannerError(boolean show, String message) {
        if (bannersSection != null) {
            bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bannerErrorState != null) {
            bannerErrorState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (tvBannerErrorMessage != null && message != null) {
            tvBannerErrorMessage.setText(message);
        }
    }
    
    private void showBannerContent(boolean show) {
        if (bannersSection != null) {
            bannersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (bannerContentContainer != null) {
            bannerContentContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private void showOffersLoading(boolean show) {
        if (offersSection != null) {
            offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (offersLoadingState != null) {
            offersLoadingState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private void showOffersEmpty(boolean show) {
        if (offersSection != null) {
            offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (offersEmptyState != null) {
            offersEmptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }
    
    private void showOffersError(boolean show, String message) {
        if (offersSection != null) {
            offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (offersErrorState != null) {
            offersErrorState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (tvOffersErrorMessage != null && message != null) {
            tvOffersErrorMessage.setText(message);
        }
    }
    
    private void showOffersContent(boolean show) {
        if (offersSection != null) {
            offersSection.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (offersContentContainer != null) {
            offersContentContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        }
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

    private <T extends View> T findViewSafe(int id) {
        return findViewById(id);
    }
}
