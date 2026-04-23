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
    private RecyclerView rvServiceChips;
    private HomeServiceChipsAdapter serviceChipsAdapter;

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
                FcmNotificationHelper.updateTokenInSupabase(user.getUid(), task.getResult());
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
            rvServiceChips = findViewSafe(R.id.rvServiceChips);

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
                rvOffersStrip.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                if (homeOfferAdapter == null) {
                    homeOfferAdapter = new HomeOfferAdapter(this, homeOffers, offer -> {
                        if (offer == null || offer.offerCode == null) return;
                        Toast.makeText(MainActivity.this, "Code: " + offer.offerCode, Toast.LENGTH_SHORT).show();
                    });
                }
                rvOffersStrip.setAdapter(homeOfferAdapter);
            }

            if (rvServiceChips != null) {
                rvServiceChips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                if (serviceChipsAdapter == null) {
                    serviceChipsAdapter = new HomeServiceChipsAdapter();
                }
                rvServiceChips.setAdapter(serviceChipsAdapter);
                serviceChipsAdapter.setItems(buildServiceChipItems());
            }

            if (homeSwipeRefresh != null) {
                homeSwipeRefresh.setColorSchemeResources(R.color.primary);
                homeSwipeRefresh.setOnRefreshListener(() -> refreshHomeContent(true));
            }

            startSkeletonPulse(bannerLoadingState);
            startSkeletonPulse(offersLoadingState);

        } catch (Exception e) {
            Log.e(TAG, "initViewsSafe failed: " + e.getMessage());
        }
    }

    private <T extends View> T findViewSafe(int id) {
        try {
            return findViewById(id);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (etSearch != null) {
                etSearch.setText(""); // Clear search on reopen
            }
            hideSuggestions();
            refreshHomeContent(false);
            startBannerAutoSlide();
            startHomeAutoRefresh();
        } catch (Exception e) {
            Log.e(TAG, "onResume: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        stopBannerAutoSlide();
        stopHomeAutoRefresh();
        super.onPause();
    }

    private void loadUserData(FirebaseUser user) {
        if (welcomeMessage != null) welcomeMessage.setText("Hello!");
        if (user == null) return;
        String email = user.getEmail() != null ? user.getEmail().trim() : "";
        SupabaseClient.getUserDisplayName(user.getUid(), email, new SupabaseClient.StringCallback() {
            @Override public void onSuccess(String value) { runOnUiThread(() -> { if (welcomeMessage != null) welcomeMessage.setText("Hello, " + firstName(value) + "!"); }); }
            @Override public void onError(String message) { runOnUiThread(() -> { if (welcomeMessage != null) welcomeMessage.setText("Hello!"); }); }
        });
        if (notificationIcon != null) notificationIcon.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });
    }

    private String firstName(String name) {
        if (name == null || name.isEmpty()) return "User";
        return name.trim().split("\\s+")[0];
    }

    private void setupLocationSelector() {
        if (locationManager == null) return;
        String savedLabel = locationManager.getSavedLocationLabel();
        updateLocationDisplay(savedLabel != null && !savedLabel.isEmpty() ? savedLabel : "Select City", savedLabel != null && !savedLabel.isEmpty());
        if (locationSelector != null) locationSelector.setOnClickListener(v -> LocationSearchBottomSheet.show(this, locationManager, new LocationSearchBottomSheet.PermissionDelegate() {
            @Override public boolean hasLocationPermission() { return ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
            @Override public void requestLocationPermission() { ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQ); }
        }, suggestion -> {
            if (suggestion == null) return;
            String label = AppLocationManager.composeLocationLabel(suggestion.city, suggestion.area);
            updateLocationDisplay(label.isEmpty() ? suggestion.city : label, true);
        }));
    }

    private void updateLocationDisplay(String city, boolean isDetected) {
        if (tvSelectedLocation != null) tvSelectedLocation.setText(city);
        if (locationAccuracyDot != null) locationAccuracyDot.setVisibility(isDetected ? View.VISIBLE : View.GONE);
    }

    private void setupSearchBar() {
        if (etSearch == null) return;
        if (rvSearchSuggestions != null) {
            suggestionsAdapter = new SearchSuggestionsAdapter(this, suggestion -> {
                hideSuggestions();
                if (suggestion != null && suggestion.id != null && !suggestion.id.trim().isEmpty()) {
                    openProviderDetailFromSuggestion(suggestion);
                } else {
                    openSearchResults(suggestion != null ? suggestion.name : "");
                }
            });
            rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
            rvSearchSuggestions.setAdapter(suggestionsAdapter);
        }
        if (btnClearSearch != null) btnClearSearch.setOnClickListener(v -> { etSearch.setText(""); hideSuggestions(); });
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
        etSearch.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { String q = etSearch.getText().toString().trim(); if (!q.isEmpty()) searchServices(q); return true; } return false; });
        if (micBtn != null) micBtn.setOnClickListener(v -> startVoiceSearch());
    }

    private void startVoiceSearch() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, VOICE_PERMISSION_REQ);
                return;
            }
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            startActivityForResult(intent, VOICE_SEARCH_REQ);
        } catch (Exception e) {
            Toast.makeText(this, "Voice search not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_SEARCH_REQ && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) {
                etSearch.setText(res.get(0));
                searchServices(res.get(0));
            }
        }
    }

    private void fetchSuggestions(String query) {
        new Thread(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_profiles?select=id,business_name,category,city&is_approved=eq.true&or=(business_name.ilike.*" + query + "*,category.ilike.*" + query + "*,city.ilike.*" + query + "*)&limit=8";
                Request req = new Request.Builder().url(url).addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY).addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY).build();
                try (Response r = HTTP.newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONArray arr = new JSONArray(r.body().string());
                        List<SearchResult> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            list.add(new SearchResult(o.optString("id"), o.optString("business_name"), o.optString("category"), o.optString("city"), SearchResult.TYPE_PROVIDER));
                        }
                        MAIN.post(() -> {
                            if (suggestionsAdapter != null) {
                                suggestionsAdapter.setSuggestions(list);
                                searchSuggestionsContainer.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void searchServices(String input) {
        hideSuggestions();
        hideKeyboard(etSearch);
        openSearchResults(input.trim());
    }

    private void hideSuggestions() { if (searchSuggestionsContainer != null) searchSuggestionsContainer.setVisibility(View.GONE); }

    private void openSearchResults(String query) {
        Intent intent = new Intent(this, SearchResultsActivity.class);
        intent.putExtra(SearchResultsActivity.EXTRA_QUERY, query);
        intent.putExtra(SearchResultsActivity.EXTRA_CITY, locationManager.getSavedCity());
        intent.putExtra(SearchResultsActivity.EXTRA_LAT, locationManager.getSavedLat());
        intent.putExtra(SearchResultsActivity.EXTRA_LNG, locationManager.getSavedLng());
        startActivity(intent);
    }

    private void openProviderDetailFromSuggestion(SearchResult s) {
        Intent intent = new Intent(this, ProviderDetailActivity.class);
        intent.putExtra(ProviderDetailActivity.EXTRA_PROVIDER_ID, s.id);
        intent.putExtra(ProviderDetailActivity.EXTRA_BUSINESS_NAME, s.name);
        startActivity(intent);
    }

    private void hideKeyboard(View v) { if (v != null) { InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0); } }

    private void setupCategoryClickListeners() {
        View.OnClickListener listener = v -> {
            String cat = "";
            int id = v.getId();
            if (id == R.id.catPlumbing) cat = "Plumbing";
            else if (id == R.id.catElectrical) cat = "Electrical";
            else if (id == R.id.catCleaning) cat = "Cleaning";
            else if (id == R.id.catRepair) cat = "Repair";
            else if (id == R.id.catAC) cat = "AC Service";
            else if (id == R.id.catPainting) cat = "Painting";
            else if (id == R.id.catCarpentry) cat = "Carpentry";
            if (!cat.isEmpty()) openProvidersList(cat);
        };
        catPlumbing.setOnClickListener(listener); catElectrical.setOnClickListener(listener); catCleaning.setOnClickListener(listener);
        catRepair.setOnClickListener(listener); catAC.setOnClickListener(listener); catPainting.setOnClickListener(listener);
        catCarpentry.setOnClickListener(listener);
        catMore.setOnClickListener(v -> startActivity(new Intent(this, CategoriesActivity.class)));
    }

    private void openProvidersList(String cat) {
        Intent i = new Intent(this, ProviderListActivity.class);
        i.putExtra(ProviderListActivity.EXTRA_CATEGORY, cat);
        i.putExtra(ProviderListActivity.EXTRA_CITY, locationManager.getSavedCity());
        startActivity(i);
    }

    private void setupQuickActionListeners() {
        quickBookService.setOnClickListener(v -> startActivity(new Intent(this, CategoriesActivity.class)));
        quickEmergency.setOnClickListener(v -> startActivity(new Intent(this, EmergencyHelpActivity.class)));
        quickTrackBooking.setOnClickListener(v -> startActivity(new Intent(this, UserBookingsActivity.class)));
        if (fabAiChat != null) fabAiChat.setOnClickListener(v -> startActivity(new Intent(this, AiChatActivity.class)));
    }

    private void setupFeaturedCardListeners() {
        featuredCard1.setOnClickListener(v -> openProvidersList("Cleaning"));
        featuredCard2.setOnClickListener(v -> openProvidersList("AC Service"));
        featuredCard3.setOnClickListener(v -> openProvidersList("Plumbing"));
    }

    private void setupBottomNav() {
        navHome.setOnClickListener(v -> setNavSelected(0));
        navBookings.setOnClickListener(v -> { startActivity(new Intent(this, UserBookingsActivity.class)); setNavSelected(1); });
        navMessages.setOnClickListener(v -> { startActivity(new Intent(this, ChatThreadsActivity.class)); setNavSelected(2); });
        navProfile.setOnClickListener(v -> { startActivity(new Intent(this, ProfileActivity.class)); setNavSelected(3); });
        setNavSelected(0);
    }

    private void setNavSelected(int index) {
        int active = ContextCompat.getColor(this, R.color.primary);
        int inactive = ContextCompat.getColor(this, R.color.text_secondary);
        navHomeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(index == 0 ? active : inactive));
        navHomeText.setTextColor(index == 0 ? active : inactive);
        navBookingsIcon.setImageTintList(android.content.res.ColorStateList.valueOf(index == 1 ? active : inactive));
        navBookingsText.setTextColor(index == 1 ? active : inactive);
        navMessagesIcon.setImageTintList(android.content.res.ColorStateList.valueOf(index == 2 ? active : inactive));
        navMessagesText.setTextColor(index == 2 ? active : inactive);
        navProfileIcon.setImageTintList(android.content.res.ColorStateList.valueOf(index == 3 ? active : inactive));
        navProfileText.setTextColor(index == 3 ? active : inactive);
    }

    private void setupListBusinessCard(FirebaseUser user) {
        if (user == null) return;
        listBusinessSection.setVisibility(View.VISIBLE);
        btnListBusinessStart.setOnClickListener(v -> startActivity(new Intent(this, ProviderOnboardingActivity.class)));
    }

    private void refreshHomeContent(boolean fromSwipe) {
        pendingHomeLoads = 2;
        if (fromSwipe && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(true);
        fetchBanners();
        fetchOffers();
    }

    private void fetchBanners() {
        bannerApi.getBanners(BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, "*", "eq.true", "created_at.desc").enqueue(new Callback<List<Banner>>() {
            @Override public void onResponse(Call<List<Banner>> call, retrofit2.Response<List<Banner>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    homeBanners.clear(); homeBanners.addAll(response.body());
                    if (homeBannerAdapter != null) homeBannerAdapter.notifyDataSetChanged();
                    bannersSection.setVisibility(homeBanners.isEmpty() ? View.GONE : View.VISIBLE);
                    bannerLoadingState.setVisibility(View.GONE);
                    bannerContentContainer.setVisibility(homeBanners.isEmpty() ? View.GONE : View.VISIBLE);
                }
                markHomeSectionLoaded();
            }
            @Override public void onFailure(Call<List<Banner>> call, Throwable t) { markHomeSectionLoaded(); }
        });
    }

    private void fetchOffers() {
        offerApi.getOffers(BuildConfig.SUPABASE_ANON_KEY, "Bearer " + BuildConfig.SUPABASE_ANON_KEY, "*", "eq.true", "created_at.desc").enqueue(new Callback<List<Offer>>() {
            @Override public void onResponse(Call<List<Offer>> call, retrofit2.Response<List<Offer>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    homeOffers.clear(); homeOffers.addAll(response.body());
                    if (homeOfferAdapter != null) homeOfferAdapter.notifyDataSetChanged();
                    offersSection.setVisibility(homeOffers.isEmpty() ? View.GONE : View.VISIBLE);
                    offersLoadingState.setVisibility(View.GONE);
                    offersContentContainer.setVisibility(homeOffers.isEmpty() ? View.GONE : View.VISIBLE);
                }
                markHomeSectionLoaded();
            }
            @Override public void onFailure(Call<List<Offer>> call, Throwable t) { markHomeSectionLoaded(); }
        });
    }

    private void markHomeSectionLoaded() {
        pendingHomeLoads = Math.max(0, pendingHomeLoads - 1);
        if (pendingHomeLoads == 0 && homeSwipeRefresh != null) homeSwipeRefresh.setRefreshing(false);
    }

    private List<ServiceChipItem> buildServiceChipItems() {
        List<ServiceChipItem> items = new ArrayList<>();
        items.add(new ServiceChipItem("Plumbing", "Plumbing"));
        items.add(new ServiceChipItem("Electrical", "Electrical"));
        items.add(new ServiceChipItem("Cleaning", "Cleaning"));
        items.add(new ServiceChipItem("Repair", "Repair"));
        items.add(new ServiceChipItem("AC Service", "AC Service"));
        items.add(new ServiceChipItem("More", "more"));
        return items;
    }

    private static class ServiceChipItem {
        final String title, category;
        ServiceChipItem(String t, String c) { this.title = t; this.category = c; }
    }

    private class HomeServiceChipsAdapter extends RecyclerView.Adapter<HomeServiceChipsAdapter.ChipViewHolder> {
        private final List<ServiceChipItem> items = new ArrayList<>();
        void setItems(List<ServiceChipItem> d) { items.clear(); if (d != null) items.addAll(d); notifyDataSetChanged(); }
        @NonNull @Override public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            View v = getLayoutInflater().inflate(R.layout.item_service_chip, p, false);
            return new ChipViewHolder(v);
        }
        @Override public void onBindViewHolder(@NonNull ChipViewHolder h, int pos) {
            ServiceChipItem it = items.get(pos);
            h.btn.setText(it.title);
            h.btn.setOnClickListener(v -> { if ("more".equals(it.category)) startActivity(new Intent(MainActivity.this, CategoriesActivity.class)); else openProvidersList(it.category); });
        }
        @Override public int getItemCount() { return items.size(); }
        class ChipViewHolder extends RecyclerView.ViewHolder {
            final MaterialButton btn;
            ChipViewHolder(View v) { super(v); btn = v.findViewById(R.id.btnServiceChip); }
        }
    }

    private void startBannerAutoSlide() {
        stopBannerAutoSlide();
        if (vpBannersCarousel == null || homeBanners.size() <= 1) return;
        bannerAutoSlideRunnable = new Runnable() {
            @Override public void run() {
                if (homeBanners.isEmpty() || vpBannersCarousel == null) return;
                int next = (vpBannersCarousel.getCurrentItem() + 1) % homeBanners.size();
                vpBannersCarousel.setCurrentItem(next, true);
                bannerAutoSlideHandler.postDelayed(this, 4000);
            }
        };
        bannerAutoSlideHandler.postDelayed(bannerAutoSlideRunnable, 4000);
    }

    private void stopBannerAutoSlide() { if (bannerAutoSlideRunnable != null) { bannerAutoSlideHandler.removeCallbacks(bannerAutoSlideRunnable); bannerAutoSlideRunnable = null; } }

    private void startSkeletonPulse(View t) { if (t == null) return; ObjectAnimator anim = ObjectAnimator.ofFloat(t, View.ALPHA, 0.4f, 1f); anim.setDuration(800); anim.setRepeatMode(ObjectAnimator.REVERSE); anim.setRepeatCount(ObjectAnimator.INFINITE); anim.start(); skeletonAnimators.add(anim); }

    private void startHomeAutoRefresh() {}
    private void stopHomeAutoRefresh() {}
    private void redirectToLogin() { Intent i = new Intent(this, LoginActivity.class); i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(i); }
    private void requestNotificationPermission() { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) { if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQ); } } }
}
