package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProfileActivity — Complete Profile / Account screen.
 * Gradient header · avatar · role badge · sectioned menu cards
 * Supabase user fetch · language picker · dark mode · logout
 */
public class ProfileActivity extends BaseActivity {

    private static final String TAG           = "ProfileActivity";
    private static final String PREFS_NAME    = "quickfix_prefs";
    private static final String KEY_NOTIF     = "notifications_enabled";

    // ── Header ────────────────────────────────────────────────────────────
    private TextView  tvAvatarInitial, tvUserName, tvUserEmail, tvRoleBadge;
    private ImageView btnBack;
    private ImageView ivHeaderAvatar;

    // ── Menu rows ─────────────────────────────────────────────────────────
    private LinearLayout menuMyProfile, menuSettings, menuListBusiness,
            menuMyBookings, menuNotifications, menuLanguage,
            menuDarkMode, menuHelpSupport, menuPrivacyPolicy,
            menuTerms, menuAboutApp, menuLogout,
            menuTransactionHistory,
            menuAdminDashboard, menuProviderDashboard;
    private androidx.cardview.widget.CardView cardRoleDashboard;

    // ── Toggles ─────���─────────────────────────────────────────────────────
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
    private volatile String cachedProfileImage = null;

    // ── Firebase + HTTP ───────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private final ActivityResultLauncher<Intent> profileDetailsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result != null && result.getResultCode() == RESULT_OK) {
                    FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
                    if (user != null) loadUserDataFromSupabase(user);
                }
            });

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
        ivHeaderAvatar        = findViewById(R.id.ivHeaderAvatar);
        menuMyProfile         = findViewById(R.id.menuMyProfile);
        menuSettings          = findViewById(R.id.menuSettings);
        menuListBusiness      = findViewById(R.id.menuListBusiness);
        menuMyBookings        = findViewById(R.id.menuMyBookings);
        menuTransactionHistory = findViewById(R.id.menuTransactionHistory);
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
        applyAvatar(name, email, null);
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
            String encodedUid = android.net.Uri.encode(uid);
            String url = BuildConfig.SUPABASE_URL
                    + "/rest/v1/users?firebase_uid=eq." + encodedUid
                    + "&select=id,firebase_uid,name,email,phone,profile_image,is_user,is_service_provider,is_admin,dark_mode,language&limit=1";
            try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) return parseUser(arr.getJSONObject(0));
                } else if (r.code() == 400) {
                    // Backward compatibility for legacy column profile_image_url.
                    String legacyUrl = BuildConfig.SUPABASE_URL
                            + "/rest/v1/users?firebase_uid=eq." + encodedUid
                            + "&select=id,firebase_uid,name,email,phone,profile_image_url,is_user,is_service_provider,is_admin,dark_mode,language&limit=1";
                    try (Response rLegacy = HTTP.newCall(buildGet(legacyUrl)).execute()) {
                        String legacyBody = rLegacy.body() != null ? rLegacy.body().string() : "[]";
                        if (rLegacy.code() == 200) {
                            JSONArray arrLegacy = new JSONArray(legacyBody);
                            if (arrLegacy.length() > 0) {
                                JSONObject row = arrLegacy.getJSONObject(0);
                                if (!row.has("profile_image") && row.has("profile_image_url")) {
                                    row.put("profile_image", row.optString("profile_image_url", ""));
                                }
                                return parseUser(row);
                            }
                        }
                    }
                }
            }
            if (!email.isEmpty()) {
                String enc = android.net.Uri.encode(email);
                String urlE = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?email=eq." + enc
                        + "&select=id,firebase_uid,name,email,phone,profile_image,is_user,is_service_provider,is_admin,dark_mode,language&limit=1";
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
        m.id                = o.optString("id", "");
        m.firebaseUid       = o.optString("firebase_uid", "");
        m.name              = o.optString("name", "");
        m.email             = o.optString("email", "");
        m.phone             = o.optString("phone", "");
        m.profileImage      = o.optString("profile_image", "");
        m.isAdmin           = o.optBoolean("is_admin", false);
        m.isServiceProvider = o.optBoolean("is_service_provider", false);
        m.isUser            = o.optBoolean("is_user", true);
        m.darkMode          = o.optBoolean("dark_mode", false);
        m.language          = o.optString("language", "en");
        return m;
    }

    private void applyUserModel(UserModel m) {
        // Cache for later use when opening ProfileDetailsActivity
        if (m.name  != null && !m.name.isEmpty())  cachedProfileName  = m.name;
        if (m.email != null && !m.email.isEmpty())  cachedProfileEmail = m.email;
        if (m.phone != null && !m.phone.isEmpty())  cachedProfilePhone = m.phone;
        cachedProfileImage = safeStr(m.profileImage);

        if (m.name  != null && !m.name.isEmpty())  { tvUserName.setText(m.name);  tvAvatarInitial.setText(String.valueOf(m.name.charAt(0)).toUpperCase()); }
        if (m.email != null && !m.email.isEmpty())   tvUserEmail.setText(m.email);
        applyAvatar(m.name, m.email, m.profileImage);
        isAdmin    = m.isAdmin;
        isProvider = m.isServiceProvider;
        applyRoleBadge(isAdmin ? "Admin" : isProvider ? "Provider" : "User");
        if (isAdmin || isProvider) {
            cardRoleDashboard.setVisibility(View.VISIBLE);
            cardRoleDashboard.animate().alpha(1f).setDuration(300).start();
        }
        if (isAdmin)    menuAdminDashboard.setVisibility(View.VISIBLE);
        if (isProvider) menuProviderDashboard.setVisibility(View.VISIBLE);

        FirebaseUser current = mAuth != null ? mAuth.getCurrentUser() : null;
        if (current != null) {
            UserPreferenceManager.applyFromServer(
                    this,
                    current.getUid(),
                    m.darkMode,
                    m.language
            );
        }
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(UserPreferenceManager.isDarkMode(this));
        }
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(LocaleHelper.codeToDisplayName(UserPreferenceManager.getLanguageCode(this)));
        }
    }

    private void applyRoleBadge(String role) {
        tvRoleBadge.setText(role);
        switch (role) {
            case "Admin":
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_admin);
                tvRoleBadge.setTextColor(getColor(R.color.accent_purple)); break;
            case "Provider":
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_provider);
                tvRoleBadge.setTextColor(getColor(R.color.accent_green)); break;
            default:
                tvRoleBadge.setBackgroundResource(R.drawable.bg_role_badge_user);
                tvRoleBadge.setTextColor(getColor(R.color.primary)); break;
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
                intent.putExtra(ProfileDetailsActivity.EXTRA_IMAGE,
                        cachedProfileImage != null ? cachedProfileImage : "");
            }
            profileDetailsLauncher.launch(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        menuSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        menuListBusiness.setOnClickListener(v -> handleListBusinessClick());

        menuMyBookings.setOnClickListener(v -> {
            startActivity(new Intent(this, UserBookingsActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

        if (menuTransactionHistory != null) {
            menuTransactionHistory.setOnClickListener(v -> {
                startActivity(new Intent(this, TransactionActivity.class));
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
            });
        }


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
        // Load current dark mode state from the user-only theme preference.
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(UserPreferenceManager.isDarkMode(this));
            switchDarkMode.setEnabled(true);
            switchDarkMode.setOnCheckedChangeListener((btn, checked) -> {
                UserPreferenceManager.setDarkMode(this, checked);
                FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
                if (user != null) {
                    SupabaseClient.updateUserSettings(
                            user.getUid(),
                            user.getEmail(),
                            checked,
                            UserPreferenceManager.getLanguageCode(this),
                            new SupabaseClient.Callback2() {
                                @Override public void onSuccess() { }
                                @Override public void onError(String message) {
                                    Log.w(TAG, "Failed to sync dark mode: " + message);
                                }
                            });
                }
            });
        }

        menuHelpSupport.setOnClickListener(v -> {
            startActivity(new Intent(this, HelpSupportActivity.class));
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out);
        });

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

        // Mark the currently active language with a checkmark
        String currentDisplay = LocaleHelper.getSavedDisplayName(this);
        int[] checkIds = {R.id.checkEnglish, R.id.checkKannada, R.id.checkHindi,
                          R.id.checkTamil,   R.id.checkTelugu,  R.id.checkMalayalam};
        String[] names  = {"English", "Kannada", "Hindi", "Tamil", "Telugu", "Malayalam"};
        for (int i = 0; i < checkIds.length; i++) {
            View chk = view.findViewById(checkIds[i]);
            if (chk != null) chk.setVisibility(names[i].equals(currentDisplay) ? View.VISIBLE : View.GONE);
        }

        view.findViewById(R.id.optionEnglish)  .setOnClickListener(v -> { applyLanguage("en"); dialog.dismiss(); });
        view.findViewById(R.id.optionKannada)  .setOnClickListener(v -> { applyLanguage("kn"); dialog.dismiss(); });
        view.findViewById(R.id.optionHindi)    .setOnClickListener(v -> { applyLanguage("hi"); dialog.dismiss(); });
        view.findViewById(R.id.optionTamil)    .setOnClickListener(v -> { applyLanguage("ta"); dialog.dismiss(); });
        view.findViewById(R.id.optionTelugu)   .setOnClickListener(v -> { applyLanguage("te"); dialog.dismiss(); });
        view.findViewById(R.id.optionMalayalam).setOnClickListener(v -> { applyLanguage("ml"); dialog.dismiss(); });
        dialog.show();
    }

    /**
     * Persist the chosen locale via {@link LocaleHelper}, update the label, and recreate.
     *
     * @param localeCode BCP-47 code: "en", "kn", "hi", "ta", "te", "ml"
     */
    private void applyLanguage(String localeCode) {
        UserPreferenceManager.setLanguage(this, localeCode);
        String displayName = LocaleHelper.codeToDisplayName(localeCode);
        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(displayName);

        FirebaseUser user = mAuth != null ? mAuth.getCurrentUser() : null;
        if (user != null) {
            SupabaseClient.updateUserSettings(
                    user.getUid(),
                    user.getEmail(),
                    UserPreferenceManager.isDarkMode(this),
                    localeCode,
                    new SupabaseClient.Callback2() {
                        @Override public void onSuccess() { }
                        @Override public void onError(String message) {
                            Log.w(TAG, "Failed to sync language: " + message);
                        }
                    });
        }

        Toast.makeText(this, getString(R.string.language_changed_toast, displayName),
                Toast.LENGTH_SHORT).show();
    }

    /** Update the language label from the persisted preference (called in onCreate). */
    private void applyLanguageFromPrefs() {
        String display = LocaleHelper.getSavedDisplayName(this);
        if (tvCurrentLanguage != null) tvCurrentLanguage.setText(display);
    }

    // ── Dark Mode ─────────────────────────────────────────────────────────

    private void setupTogglesFromPrefs() {
        switchNotifications.setChecked(prefs.getBoolean(KEY_NOTIF, true));
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(UserPreferenceManager.isDarkMode(this));
        }
        if (tvCurrentLanguage != null) {
            tvCurrentLanguage.setText(LocaleHelper.codeToDisplayName(UserPreferenceManager.getLanguageCode(this)));
        }
    }

    @Override
    protected boolean isAdminThemeScreen() {
        return false;
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

    private void applyAvatar(String name, String email, String imagePath) {
        if (ivHeaderAvatar == null || tvAvatarInitial == null) return;

        if (!TextUtils.isEmpty(imagePath)) {
            ivHeaderAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            String loadUrl = imagePath.startsWith("http://") || imagePath.startsWith("https://")
                    ? imagePath
                    : SupabaseStorageHelper.getPublicObjectUrl(
                    SupabaseStorageHelper.BUCKET_PROFILE_IMAGES,
                    imagePath
            );
            Glide.with(this)
                    .load(loadUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bg_profile_avatar_inner)
                    .error(R.drawable.bg_profile_avatar_inner)
                    .into(ivHeaderAvatar);
            return;
        }

        showInitial(name, email);
    }

    private void showInitial(String name, String email) {
        if (ivHeaderAvatar != null) ivHeaderAvatar.setVisibility(View.GONE);
        if (tvAvatarInitial != null) {
            tvAvatarInitial.setVisibility(View.VISIBLE);
            String source = !TextUtils.isEmpty(name) ? name : email;
            String initial = !TextUtils.isEmpty(source)
                    ? String.valueOf(source.charAt(0)).toUpperCase()
                    : "U";
            tvAvatarInitial.setText(initial);
        }
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

