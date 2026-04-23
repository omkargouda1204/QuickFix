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
                // ...
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
             // TODO: Open emergency service
        });
        if (quickTrackBooking != null) quickTrackBooking.setOnClickListener(v -> {
             // TODO: Open bookings
        });
    }

    private void setupFeaturedCardListeners() {
        if (featuredCard1 != null) featuredCard1.setOnClickListener(v -> openProvidersList("Cleaning"));
        if (featuredCard2 != null) featuredCard2.setOnClickListener(v -> openProvidersList("Electrical"));
        if (featuredCard3 != null) featuredCard3.setOnClickListener(v -> openProvidersList("Plumbing"));
    }

    private void setupBottomNav() {
        if (navHome != null) navHome.setOnClickListener(v -> {});
        if (navBookings != null) navBookings.setOnClickListener(v -> {
            // startActivity(new Intent(this, BookingsActivity.class));
        });
        if (navMessages != null) navMessages.setOnClickListener(v -> {
            // startActivity(new Intent(this, MessagesActivity.class));
        });
        if (navProfile != null) navProfile.setOnClickListener(v -> {
            // startActivity(new Intent(this, ProfileActivity.class));
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
        // Intent intent = new Intent(this, ProvidersActivity.class);
        // intent.putExtra("category", category);
        // startActivity(intent);
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private <T extends View> T findViewSafe(int id) {
        return findViewById(id);
    }
}
