package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProfileActivity — Complete Profile / Account screen.
 * Gradient header · avatar · role badge · sectioned menu cards
 * Supabase user fetch · language picker · dark mode · logout
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String TAG           = "ProfileActivity";
    private static final String PREFS_NAME    = "quickfix_prefs";
    private static final String KEY_LANGUAGE  = "selected_language";
    private static final String KEY_DARK_MODE = "dark_mode_enabled";
    private static final String KEY_NOTIF     = "notifications_enabled";

    // ── Header ────────────────────────────────────────────────────────────
    private TextView  tvAvatarInitial, tvUserName, tvUserEmail, tvRoleBadge;
    private ImageView btnBack;

    // ── Menu rows ─────────────────────────────────────────────────────────
    private LinearLayout menuMyProfile, menuSettings, menuListBusiness,
            menuMyBookings, menuNotifications, menuLanguage,
            menuDarkMode, menuHelpSupport, menuPrivacyPolicy,
            menuTerms, menuAboutApp, menuLogout,
            menuAdminDashboard, menuProviderDashboard;
    private androidx.cardview.widget.CardView cardRoleDashboard;

    // ── Toggles ───────────────────────────────────────────────────────────
    private SwitchMaterial switchNotifications, switchDarkMode;
    private TextView       tvCurrentLanguage;
    private FrameLayout    profileLoadingOverlay;

    // ── State (volatile — written on background thread, read on main) ─────
    private volatile boolean isAdmin    = false;
    private volatile boolean isProvider = false;
    private volatile boolean hasApplied = false;
    private volatile String  applicationStatus = "";

    // ── Cached profile data (populated after Supabase fetch) ──────────────
    private volatile String cachedProfileName  = null;
    private volatile String cachedProfileEmail = null;
    private volatile String cachedProfilePhone = null;

    // ── Firebase + HTTP ───────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        applyDarkModeFromPrefs();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { redirectToLogin(); return; }

        bindViews();
        applyLanguageFromPrefs();
        prefillFromFirebase(user);
        setupTogglesFromPrefs();
        setupClickListeners();
        loadUserDataFromSupabase(user);
    }

    // ── Bind ──────────────────────────────────────────────────────────────
    private void bindViews() {
        tvAvatarInitial       = findViewById(R.id.tvAvatarInitial);
        tvUserName            = findViewById(R.id.tvUserName);
        tvUserEmail           = findViewById(R.id.tvUserEmail);
        tvRoleBadge           = findViewById(R.id.tvRoleBadge);
        btnBack               = findViewById(R.id.btnBack);
        menuMyProfile         = findViewById(R.id.menuMyProfile);
        menuSettings          = findViewById(R.id.menuSettings);
        menuListBusiness      = findViewById(R.id.menuListBusiness);
        menuMyBookings        = findViewById(R.id.menuMyBookings);
        menuNotifications     = findViewById(R.id.menuNotifications);
        menuLanguage          = findViewById(R.id.menuLanguage);
        menuDarkMode          = findViewById(R.id.menuDarkMode);
        menuHelpSupport       = findViewById(R.id.menuHelpSupport);
        menuPrivacyPolicy     = findViewById(R.id.menuPrivacyPolicy);
        menuTerms             = findViewById(R.id.menuTerms);
        menuAboutApp          = findViewById(R.id.menuAboutApp);
        menuLogout            = findViewById(R.id.menuLogout);
        menuAdminDashboard    = findViewById(R.id.menuAdminDashboard);
        menuProviderDashboard = findViewById(R.id.menuProviderDashboard);
        cardRoleDashboard     = findViewById(R.id.cardRoleDashboard);
        switchNotifications   = findViewById(R.id.switchNotifications);
        switchDarkMode        = findViewById(R.id.switchDarkMode);
        tvCurrentLanguage     = findViewById(R.id.tvCurrentLanguage);
        profileLoadingOverlay = findViewById(R.id.profileLoadingOverlay);
    }

    // ── Pre-fill from Firebase (instant, no network) ──────────────────────
    private void prefillFromFirebase(FirebaseUser user) {
        String name  = user.getDisplayName();
        String email = user.getEmail();
        if (email != null) tvUserEmail.setText(email);
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(name);
            tvAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
        } else if (email != null && !email.isEmpty()) {
            tvUserName.setText(email.split("@")[0]);
            tvAvatarInitial.setText(String.valueOf(email.charAt(0)).toUpperCase());
        } else {
            tvUserName.setText(R.string.app_name);
            tvAvatarInitial.setText("U");
        }
        applyRoleBadge("User");
    }

    // ── Load user data from Supabase ──────────────────────────────────────
    private void loadUserDataFromSupabase(FirebaseUser user) {
        showLoading(true);
        final String uid   = user.getUid();
        final String email = user.getEmail() != null ? user.getEmail() : "";

        new Thread(() -> {
            UserModel model = fetchUserRow(uid, email);
            MAIN.post(() -> { showLoading(false); if (model != null) applyUserModel(model); });
            checkProviderApplication(uid);
        }).start();
    }

    private UserModel fetchUserRow(String uid, String email) {
        try {
            // Try by UID first
            String url = BuildConfig.SUPABASE_URL
                    + "/rest/v1/users?firebase_uid=eq." + uid
                    + "&select=name,email,phone,is_user,is_service_provider,is_admin&limit=1";
            try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) return parseUser(arr.getJSONObject(0));
                }
            }
            // Fallback by email
            if (!email.isEmpty()) {
                String enc = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8.name());
                String urlE = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?email=eq." + enc
                        + "&select=name,email,phone,is_user,is_service_provider,is_admin&limit=1";
                try (Response r2 = HTTP.newCall(buildGet(urlE)).execute()) {
                    String body2 = r2.body() != null ? r2.body().string() : "[]";
                    if (r2.code() == 200) {
                        JSONArray arr2 = new JSONArray(body2);
                        if (arr2.length() > 0) return parseUser(arr2.getJSONObject(0));
                    }
                }
            }
        } catch (Exception e) { Log.e(TAG, "fetchUserRow: " + e.getMessage()); }
        return null;
    }

    private UserModel parseUser(JSONObject o) {
        UserModel m = new UserModel();
        m.name              = o.optString("name", "");
        m.email             = o.optString("email", "");
        m.phone             = o.optString("phone", "");
        m.isAdmin           = o.optBoolean("is_admin", false);
        m.isServiceProvider = o.optBoolean("is_service_provider", false);
        m.isUser            = o.optBoolean("is_user", true);
        return m;
    }

    private void applyUserModel(UserModel m) {
        // Cache for later use when opening ProfileDetailsActivity
        if (m.name  != null && !m.name.isEmpty())  cachedProfileName  = m.name;
        if (m.email != null && !m.email.isEmpty())  cachedProfileEmail = m.email;
        if (m.phone != null && !m.phone.isEmpty())  cachedProfilePhone = m.phone;

        if (m.name  != null && !m.name.isEmpty())  { tvUserName.setText(m.name);  tvAvatarInitial.setText(String.valueOf(m.name.charAt(0)).toUpperCase()); }
        if (m.email != null && !m.email.isEmpty())   tvUserEmail.setText(m.email);
        isAdmin    = m.isAdmin;
        isProvider = m.isServiceProvider;
        applyRoleBadge(isAdmin ? "Admin" : isProvider ? "Provider" : "User");
        if (isAdmin || isProvider) {
            cardRoleDashboard.setVisibility(View.VISIBLE);
            cardRoleDashboard.animate().alpha(1f).setDuration(300).start();
        }
        if (isAdmin)    menuAdminDashboard.setVisibility(View.VISIBLE);
        if (isProvider) menuProviderDashboard.setVisibility(View.VISIBLE);
    }

    private void applyRoleBadge(String role) {
        tvRoleBadge.setText(role);
        switch (role) {
            case "Admin":
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_admin);
                tvRoleBadge.setTextColor(0xFF6A1B9A); break;
            case "Provider":
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_provider);
                tvRoleBadge.setTextColor(0xFF2E7D32); break;
            default:
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_user);
                tvRoleBadge.setTextColor(0xFF1565C0); break;
        }
    }

    private void checkProviderApplication(String uid) {
        try {
            String url = BuildConfig.SUPABASE_URL
                    + "/rest/v1/provider_applications?firebase_uid=eq." + uid
                    + "&select=status&order=submitted_at.desc&limit=1";
            try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        hasApplied        = true;
                        applicationStatus = arr.getJSONObject(0).optString("status", "pending");
                    }
                }
            }
        } catch (Exception e) { Log.e(TAG, "checkProviderApplication: " + e.getMessage()); }
    }

    // ── Click Listeners ───────────────────────────────────────────────────
    private void setupClickListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        menuMyProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileDetailsActivity.class);
            // Pass cached data so ProfileDetailsActivity can show it instantly (no waiting)
            FirebaseUser cur = mAuth.getCurrentUser();
            if (cur != null) {
                intent.putExtra(ProfileDetailsActivity.EXTRA_NAME,
                        cachedProfileName  != null ? cachedProfileName  : safeStr(cur.getDisplayName()));
                intent.putExtra(ProfileDetailsActivity.EXTRA_EMAIL,
                        cachedProfileEmail != null ? cachedProfileEmail : safeStr(cur.getEmail()));
                intent.putExtra(ProfileDetailsActivity.EXTRA_PHONE,
                        cachedProfilePhone != null ? cachedProfilePhone : "");
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        menuSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        menuListBusiness.setOnClickListener(v -> handleListBusinessClick());


        menuNotifications.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });
        switchNotifications.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(KEY_NOTIF, checked).apply();
            Toast.makeText(this, checked ? "Notifications enabled" : "Notifications disabled",
                    Toast.LENGTH_SHORT).show();
        });

        menuLanguage.setOnClickListener(v -> showLanguageBottomSheet());

        menuDarkMode.setOnClickListener(v -> {
            if (switchDarkMode != null) switchDarkMode.toggle();
        });
        // Load current dark mode state from ThemeManager (shared across all roles)
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeManager.isDarkMode(this));
            switchDarkMode.setEnabled(true);
            switchDarkMode.setOnCheckedChangeListener((btn, checked) -> {
                ThemeManager.toggle(this, checked);
                // Sync with AppSettings key for backwards compat
                prefs.edit().putBoolean(KEY_DARK_MODE, checked).apply();
                Toast.makeText(this, checked ? "🌙 Dark mode enabled" : "☀️ Light mode enabled",
                        Toast.LENGTH_SHORT).show();
            });
        }

        menuHelpSupport.setOnClickListener(v ->
                Toast.makeText(this, "Help & Support — coming soon!", Toast.LENGTH_SHORT).show());

        menuPrivacyPolicy.setOnClickListener(v -> showInfoDialog(
                getString(R.string.profile_privacy_policy),
                "Your privacy is important to us. We collect minimal data necessary to provide the QuickFix service.\n\nFull policy: quickfix.app/privacy\nSupport: support@quickfix.app"));

        menuTerms.setOnClickListener(v -> showInfoDialog(
                getString(R.string.profile_terms),
                getString(R.string.onboarding_terms_body)));

        menuAboutApp.setOnClickListener(v -> showInfoDialog(
                getString(R.string.profile_about_app),
                "QuickFix v1.0\n\nYour trusted home services platform.\n\nConnect with skilled professionals for plumbing, electrical, cleaning and more.\n\n© 2026 QuickFix. All rights reserved."));

        menuAdminDashboard.setOnClickListener(v ->
                startActivity(new Intent(this, AdminDashboardActivity.class)));

        menuProviderDashboard.setOnClickListener(v ->
                startActivity(new Intent(this, ProviderDashboardActivity.class)));

        menuLogout.setOnClickListener(v -> showLogoutDialog());
    }

    // ── List Your Business ────────────────────────────────────────────────
    private void handleListBusinessClick() {
        if (!hasApplied) {
            startActivity(new Intent(this, ProviderOnboardingActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            return;
        }
        switch (applicationStatus) {
            case "approved":
                startActivity(new Intent(this, ProviderDashboardActivity.class));
                break;
            case "rejected":
                new AlertDialog.Builder(this)
                        .setTitle("Re-apply?")
                        .setMessage("Your previous application was rejected. Submit a new one?")
                        .setPositiveButton("Apply Again", (d, w) ->
                                startActivity(new Intent(this, ProviderOnboardingActivity.class)))
                        .setNegativeButton("Cancel", null).show();
                break;
            default:
                new AlertDialog.Builder(this)
                        .setTitle("⏳ Application Pending")
                        .setMessage("Your application is under review.\nWe'll get back to you within 24–48 hours.")
                        .setPositiveButton("OK", null).show();
                break;
        }
    }

    // ── Language Bottom Sheet ─────────────────────────────────────────────
    private void showLanguageBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this)
                .inflate(R.layout.bottom_sheet_language, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        String current = prefs.getString(KEY_LANGUAGE, "English");

        // Hide all checks, then show only the current one
        int[] checkIds = {R.id.checkEnglish, R.id.checkKannada, R.id.checkHindi,
                R.id.checkTamil, R.id.checkTelugu, R.id.checkMalayalam};
        String[] names = {"English", "Kannada", "Hindi", "Tamil", "Telugu", "Malayalam"};
        for (int i = 0; i < checkIds.length; i++) {
            View chk = view.findViewById(checkIds[i]);
            if (chk != null) chk.setVisibility(names[i].equals(current) ? View.VISIBLE : View.GONE);
        }

        view.findViewById(R.id.optionEnglish) .setOnClickListener(v -> { applyLanguage("English",   "en"); dialog.dismiss(); });
        view.findViewById(R.id.optionKannada) .setOnClickListener(v -> { applyLanguage("Kannada",   "kn"); dialog.dismiss(); });
        view.findViewById(R.id.optionHindi)   .setOnClickListener(v -> { applyLanguage("Hindi",     "hi"); dialog.dismiss(); });
        view.findViewById(R.id.optionTamil)   .setOnClickListener(v -> { applyLanguage("Tamil",     "ta"); dialog.dismiss(); });
        view.findViewById(R.id.optionTelugu)  .setOnClickListener(v -> { applyLanguage("Telugu",    "te"); dialog.dismiss(); });
        view.findViewById(R.id.optionMalayalam).setOnClickListener(v -> { applyLanguage("Malayalam", "ml"); dialog.dismiss(); });
        dialog.show();
    }

    private void applyLanguage(String displayName, String localeCode) {
        prefs.edit().putString(KEY_LANGUAGE, displayName).apply();
        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(displayName);
        Locale locale = new Locale(localeCode);
        Locale.setDefault(locale);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        Toast.makeText(this, "Language: " + displayName, Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void applyLanguageFromPrefs() {
        String saved = prefs.getString(KEY_LANGUAGE, "English");
        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(saved);
        String code = localeCodeForLanguage(saved);
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(getResources().getConfiguration());
        config.setLocale(locale);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    /** Map language display name → BCP-47 locale code */
    private static String localeCodeForLanguage(String name) {
        if (name == null) return "en";
        switch (name) {
            case "Kannada":   return "kn";
            case "Hindi":     return "hi";
            case "Tamil":     return "ta";
            case "Telugu":    return "te";
            case "Malayalam": return "ml";
            default:          return "en";
        }
    }

    // ── Dark Mode ─────────────────────────────────────────────────────────
    private void applyDarkModeFromPrefs() {
        // Use ThemeManager — shared preference for all roles
        ThemeManager.applyFromPrefs(this);
    }

    private void setupTogglesFromPrefs() {
        switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIF, true));
        // Load dark mode from ThemeManager
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeManager.isDarkMode(this));
        }
        String lang = prefs.getString(KEY_LANGUAGE, "English");
        tvCurrentLanguage.setText(lang);
    }

    // ── Logout ────────────────────────────────────────────────────────────
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_logout_confirm_title)
                .setMessage(R.string.profile_logout_confirm_msg)
                .setPositiveButton(R.string.profile_logout, (d, w) -> performLogout())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void performLogout() {
        showLoading(true);
        mAuth.signOut();
        new Handler(Looper.getMainLooper()).postDelayed(this::redirectToLogin, 400);
    }

    private void redirectToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void showLoading(boolean show) {
        if (profileLoadingOverlay != null)
            profileLoadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private static Request buildGet(String url) {
        return new Request.Builder().url(url).get()
                .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();
    }

    /** Returns the string as-is, or "" if it is null. */
    private static String safeStr(String s) { return s != null ? s : ""; }
}

