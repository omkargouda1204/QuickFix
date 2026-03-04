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
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.SearchSuggestionsAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG                      = "MainActivity";
    private static final int    LOCATION_PERMISSION_REQ  = 1001;

    // ── Location ──────────────────────────────────────────────────────────
    private LinearLayout        locationSelector;
    private TextView            tvSelectedLocation;
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

        if (providerDashboardSection != null)
            providerDashboardSection.setVisibility(View.GONE);

        // FCM token — stored in DB silently
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) return;
            String token = task.getResult();
            Log.d("FCM_TOKEN", "Token retrieved (stored in DB, not shown to user)");
            SupabaseClient.saveFcmToken(currentUser.getUid(), token);
        });
    }

    // ── Init Views ────────────────────────────────────────────────────────
    private void initViews() {
        // Location
        locationSelector   = findViewById(R.id.locationSelector);
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation);

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
    }

    // ── Load User Data ────────────────────────────────────────────────────
    private void loadUserData(FirebaseUser user) {
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            welcomeMessage.setText("Hello, " + firstName(displayName) + "! 👋");
        } else {
            welcomeMessage.setText("Hello! 👋");
        }
        if (userEmail != null) userEmail.setText(getString(R.string.find_services));

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
        String saved = locationManager.getSavedCity();
        updateLocationDisplay(saved.isEmpty() ? "Select Location" : saved);
        if (locationSelector != null)
            locationSelector.setOnClickListener(v -> showLocationDialog());
    }

    private void updateLocationDisplay(String city) {
        if (tvSelectedLocation != null)
            tvSelectedLocation.setText(city.isEmpty() ? "Select Location" : city);
    }

    private void showLocationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Select Location")
            .setItems(new String[]{"📍 Use My Live Location", "🏙️ Choose Location Manually", "✕ Clear Location"},
                (dialog, which) -> {
                    if      (which == 0) requestLiveLocation();
                    else if (which == 1) showManualLocationPicker();
                    else { locationManager.clearCity(); updateLocationDisplay("Select Location"); }
                })
            .show();
    }

    private void requestLiveLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                             Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQ);
            return;
        }
        fetchLiveLocationNow();
    }

    private void fetchLiveLocationNow() {
        updateLocationDisplay("Fetching…");
        locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
            @Override public void onSuccess(String city, double lat, double lng) {
                String d = city.isEmpty() ? "Location detected" : city;
                updateLocationDisplay(d);
                Toast.makeText(MainActivity.this, "📍 " + d, Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(String reason) {
                updateLocationDisplay("Select Location");
                Toast.makeText(MainActivity.this, "Could not get location: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showManualLocationPicker() {
        List<String> sorted = new ArrayList<>(Arrays.asList(AppLocationManager.CITY_LIST));
        java.util.Collections.sort(sorted);

        EditText etFilter = new EditText(this);
        etFilter.setHint("Search city...");
        etFilter.setPadding(40, 20, 40, 20);

        android.widget.ListView lv = new android.widget.ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>(sorted));
        lv.setAdapter(adapter);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.addView(etFilter);
        container.addView(lv);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Select Your City")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .create();

        etFilter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                adapter.getFilter().filter(s);
            }
        });

        lv.setOnItemClickListener((parent, view, pos, id) -> {
            String selected = (String) parent.getItemAtPosition(pos);
            locationManager.saveCity(selected);
            updateLocationDisplay(selected);
            Toast.makeText(this, "📍 " + selected, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, permissions, results);
        if (req == LOCATION_PERMISSION_REQ) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                fetchLiveLocationNow();
            else
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
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
                StringBuilder url = new StringBuilder(BuildConfig.SUPABASE_URL)
                    .append("/rest/v1/provider_applications?status=eq.approved")
                    .append("&or=(business_name.ilike.*").append(enc).append("*,category.ilike.*").append(enc).append("*)")
                    .append("&select=id,business_name,category,city&limit=6");
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
        Intent intent = new Intent(this, SearchResultsActivity.class);
        intent.putExtra(SearchResultsActivity.EXTRA_QUERY, query);
        intent.putExtra(SearchResultsActivity.EXTRA_CITY, locationManager.getSavedCity());
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
        Intent intent = new Intent(this, ProvidersListActivity.class);
        intent.putExtra(ProvidersListActivity.EXTRA_CATEGORY, category);
        intent.putExtra(ProvidersListActivity.EXTRA_CITY, locationManager.getSavedCity());
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
        String uid = user.getUid();
        RoleManager.checkAdminRole(uid, new RoleManager.RoleCheckCallback() {
            @Override public void onAdminVerified(String adminRoleId, String roleName) {
                listBusinessSection.setVisibility(View.GONE);
            }
            @Override public void onNotAdmin() { checkIfProvider(uid); }
            @Override public void onError(String message) { showListBusinessCard(); }
        });
    }

    private void checkIfProvider(String uid) {
        String url = BuildConfig.SUPABASE_URL + "/rest/v1/provider_applications"
                + "?firebase_uid=eq." + uid + "&status=eq.approved&select=id&limit=1";
        Request req = new Request.Builder().url(url)
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();
        HTTP.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> showListBusinessCard());
            }
            @Override public void onResponse(@NonNull okhttp3.Call call, @NonNull Response response) throws java.io.IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                runOnUiThread(() -> {
                    try {
                        if (code == 200 && new JSONArray(body).length() > 0)
                            listBusinessSection.setVisibility(View.GONE);
                        else showListBusinessCard();
                    } catch (Exception e) { showListBusinessCard(); }
                });
            }
        });
    }

    private void showListBusinessCard() {
        if (listBusinessSection == null) return;
        listBusinessSection.setVisibility(View.VISIBLE);
        listBusinessSection.setAlpha(0f);
        listBusinessSection.animate().alpha(1f).setDuration(400).start();
        if (btnListBusinessStart != null) {
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            btnListBusinessStart.startAnimation(pulse);
            btnListBusinessStart.setOnClickListener(v -> {
                startActivity(new Intent(this, ProviderOnboardingActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            });
            if (listBusinessSection.getChildCount() > 0) {
                listBusinessSection.getChildAt(0).setOnClickListener(v -> btnListBusinessStart.performClick());
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────
    private void redirectToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
