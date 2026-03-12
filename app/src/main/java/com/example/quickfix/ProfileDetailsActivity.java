package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ProfileDetailsActivity — View + Edit profile.
 *
 * <p>Load order (fastest → authoritative):
 * <ol>
 *   <li>Intent extras — shown instantly, zero latency.</li>
 *   <li>Firebase metadata — fills gaps left by missing extras.</li>
 *   <li>Supabase {@code public.users} row — authoritative; overwrites on arrival.</li>
 *   <li>Auto-seed — if no row yet, one is created from Firebase + extras.</li>
 * </ol>
 *
 * <p>Edit: Name, Phone, City, Address. Email is read-only.
 * Save PATCH → Supabase users table by firebase_uid.
 * If no row exists yet, upsert (INSERT OR UPDATE) is used instead.
 */
public class ProfileDetailsActivity extends BaseActivity {

    private static final String TAG = "ProfileDetails";

    // ── Intent extras (passed by ProfileActivity for instant pre-fill) ────
    public static final String EXTRA_NAME  = "extra_profile_name";
    public static final String EXTRA_EMAIL = "extra_profile_email";
    public static final String EXTRA_PHONE = "extra_profile_phone";

    // ── Header views ──────────────────────────────────────────────────────
    private ImageView   ivAvatar;
    private ImageView   btnBack;
    private TextView    tvAvatarInitial;
    private TextView    tvName;
    private TextView    tvEmail;
    private FrameLayout loadingOverlay;

    // ── View-mode card ────────────────────────────────────────────────────
    private MaterialCardView cardViewMode;
    private TextView tvDisplayName;
    private TextView tvDisplayEmail;
    private TextView tvDisplayPhone;
    private TextView tvDisplayCity;
    private TextView tvDisplayAddress;
    private MaterialButton btnEditProfile;

    // ── Edit-mode card ────────────────────────────────────────────────────
    private MaterialCardView    cardEditMode;
    private TextInputLayout     tilEditName, tilEditPhone, tilEditCity, tilEditAddress;
    private TextInputEditText   etEditName, etEditPhone, etEditCity, etEditAddress;
    private TextView            tvEditEmailReadonly;
    private MaterialButton      btnSaveChanges, btnCancelEdit;

    // ── Scroll view (to scroll to top on mode switch) ─────────────────────
    private androidx.core.widget.NestedScrollView profileScrollView;

    // ── State ─────────────────────────────────────────────────────────────
    private String firebaseUid   = "";
    private String cachedName    = "";
    private String cachedEmail   = "";
    private String cachedPhone   = "";
    private String cachedCity    = "";
    private String cachedAddress = "";
    private String cachedImgUrl  = null;
    private boolean isEditMode   = false;
    private boolean isSaving     = false;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_details);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        firebaseUid = user.getUid();

        bindViews();
        setupClickListeners();

        // ── Step 1: instant pre-fill from intent extras ──────────────────
        String extraName  = getIntent().getStringExtra(EXTRA_NAME);
        String extraEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        String extraPhone = getIntent().getStringExtra(EXTRA_PHONE);

        // ── Step 2: fill any gaps from Firebase metadata ─────────────────
        if (TextUtils.isEmpty(extraName)  && user.getDisplayName() != null)
            extraName  = user.getDisplayName().trim();
        if (TextUtils.isEmpty(extraEmail) && user.getEmail() != null)
            extraEmail = user.getEmail().trim();
        if (TextUtils.isEmpty(extraPhone) && user.getPhoneNumber() != null)
            extraPhone = user.getPhoneNumber().trim();

        if (!TextUtils.isEmpty(extraName) || !TextUtils.isEmpty(extraEmail)) {
            applyProfile(safeStr(extraName), safeStr(extraEmail),
                         safeStr(extraPhone), "", "", null);
        }

        // ── Step 3: authoritative load from Supabase ─────────────────────
        loadProfile(safeStr(extraName), safeStr(extraEmail), safeStr(extraPhone));
    }

    // ── Bind all views ────────────────────────────────────────────────────
    private void bindViews() {
        profileScrollView = findViewById(R.id.profileScrollView);
        ivAvatar          = findViewById(R.id.ivAvatar);
        tvAvatarInitial   = findViewById(R.id.tvAvatarInitial);
        tvName            = findViewById(R.id.tvName);
        tvEmail           = findViewById(R.id.tvEmail);
        btnBack           = findViewById(R.id.btnBack);
        loadingOverlay    = findViewById(R.id.loadingOverlay);

        // View mode
        cardViewMode     = findViewById(R.id.cardViewMode);
        tvDisplayName    = findViewById(R.id.tvDisplayName);
        tvDisplayEmail   = findViewById(R.id.tvDisplayEmail);
        tvDisplayPhone   = findViewById(R.id.tvDisplayPhone);
        tvDisplayCity    = findViewById(R.id.tvDisplayCity);
        tvDisplayAddress = findViewById(R.id.tvDisplayAddress);
        btnEditProfile   = findViewById(R.id.btnEditProfile);

        // Edit mode
        cardEditMode        = findViewById(R.id.cardEditMode);
        tilEditName         = findViewById(R.id.tilEditName);
        tilEditPhone        = findViewById(R.id.tilEditPhone);
        tilEditCity         = findViewById(R.id.tilEditCity);
        tilEditAddress      = findViewById(R.id.tilEditAddress);
        etEditName          = findViewById(R.id.etEditName);
        etEditPhone         = findViewById(R.id.etEditPhone);
        etEditCity          = findViewById(R.id.etEditCity);
        etEditAddress       = findViewById(R.id.etEditAddress);
        tvEditEmailReadonly = findViewById(R.id.tvEditEmailReadonly);
        btnSaveChanges      = findViewById(R.id.btnSaveChanges);
        btnCancelEdit       = findViewById(R.id.btnCancelEdit);

        showEditMode(false);
    }

    // ── Click listeners ───────────────────────────────────────────────────
    private void setupClickListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> handleBackPress());
        if (btnEditProfile  != null) btnEditProfile.setOnClickListener(v -> enterEditMode());
        if (btnCancelEdit   != null) btnCancelEdit.setOnClickListener(v -> handleCancelEdit());
        if (btnSaveChanges  != null) btnSaveChanges.setOnClickListener(v -> validateAndSave());

        // ── Back-press: prompt to discard changes if in edit mode ─────────
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() { handleBackPress(); }
                });
    }

    private void handleBackPress() {
        if (isEditMode) {
            if (hasUnsavedChanges()) {
                showDiscardDialog();
            } else {
                showEditMode(false);
            }
        } else {
            finish();
        }
    }

    /** Returns true if any edit field differs from the cached (saved) value. */
    private boolean hasUnsavedChanges() {
        return !getText(etEditName).trim().equals(cachedName)
            || !getText(etEditPhone).trim().equals(cachedPhone)
            || !getText(etEditCity).trim().equals(cachedCity)
            || !getText(etEditAddress).trim().equals(cachedAddress);
    }

    private void showDiscardDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Discard changes?")
            .setMessage("You have unsaved changes. Do you want to discard them?")
            .setPositiveButton("Discard", (d, w) -> showEditMode(false))
            .setNegativeButton("Keep editing", null)
            .show();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  EDIT MODE SWITCH
    // ─────────────────────────────────────────────────────────────────────

    private void enterEditMode() {
        setText(etEditName,    cachedName);
        setText(etEditPhone,   cachedPhone);
        setText(etEditCity,    cachedCity);
        setText(etEditAddress, cachedAddress);
        if (tvEditEmailReadonly != null)
            tvEditEmailReadonly.setText(TextUtils.isEmpty(cachedEmail) ? "—" : cachedEmail);
        clearFieldErrors();
        showEditMode(true);
    }

    private void handleCancelEdit() {
        if (hasUnsavedChanges()) {
            showDiscardDialog();
        } else {
            showEditMode(false);
        }
    }

    private void showEditMode(boolean edit) {
        isEditMode = edit;
        if (cardViewMode != null) cardViewMode.setVisibility(edit ? View.GONE  : View.VISIBLE);
        if (cardEditMode != null) cardEditMode.setVisibility(edit ? View.VISIBLE : View.GONE);
        // Scroll back to top so the header + card are visible
        if (profileScrollView != null) profileScrollView.post(() -> profileScrollView.smoothScrollTo(0, 0));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  VALIDATION + SAVE
    // ─────────────────────────────────────────────────────────────────────

    private void validateAndSave() {
        if (isSaving) return;
        if (!validateFields()) return;

        String newName    = getText(etEditName).trim();
        String newPhone   = getText(etEditPhone).trim();
        String newCity    = getText(etEditCity).trim();
        String newAddress = getText(etEditAddress).trim();

        saveToSupabase(newName, newPhone, newCity, newAddress);
    }

    private boolean validateFields() {
        clearFieldErrors();
        boolean valid = true;

        String name  = getText(etEditName).trim();
        String phone = getText(etEditPhone).trim();
        String city  = getText(etEditCity).trim();

        // Name — required, min 2 chars
        if (TextUtils.isEmpty(name)) {
            setError(tilEditName, getString(R.string.error_fullname_required));
            valid = false;
        } else if (name.length() < 2) {
            setError(tilEditName, "Name must be at least 2 characters");
            valid = false;
        } else if (name.length() > 60) {
            setError(tilEditName, "Name is too long (max 60 characters)");
            valid = false;
        }

        // Phone — optional, but must be valid if provided
        if (!TextUtils.isEmpty(phone) && !phone.matches("^[+]?[0-9]{7,15}$")) {
            setError(tilEditPhone, getString(R.string.error_phone_invalid));
            valid = false;
        }

        // City — optional, length guard
        if (!TextUtils.isEmpty(city) && city.length() > 80) {
            setError(tilEditCity, "City name is too long (max 80 characters)");
            valid = false;
        }

        return valid;
    }

    private void saveToSupabase(String name, String phone, String city, String address) {
        isSaving = true;
        showLoading(true);
        setSaveButtonState(true, "Saving…");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name",    name);
                body.put("phone",   phone);
                body.put("city",    city);
                body.put("address", address);
                if (!TextUtils.isEmpty(cachedEmail)) body.put("email", cachedEmail);

                // ── PATCH existing row ────────────────────────────────────
                String patchUrl = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?firebase_uid=eq." + firebaseUid;

                Request patchReq = new Request.Builder()
                        .url(patchUrl)
                        .patch(RequestBody.create(
                                body.toString(),
                                MediaType.get("application/json; charset=utf-8")))
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(patchReq).execute()) {
                    int code = r.code();
                    Log.d(TAG, "PATCH /users HTTP " + code);

                    if (code == 200 || code == 204) {
                        // Successful update
                        MAIN.post(() -> onSaveSuccess(name, phone, city, address, "Profile updated ✅"));
                    } else if (code == 406) {
                        // 406 = "Not Acceptable" — can happen when Prefer header is mismatched;
                        // treat as success if body is empty (Supabase PATCH with return=minimal)
                        String errBody = r.body() != null ? r.body().string() : "";
                        if (errBody.isEmpty() || errBody.equals("[]") || errBody.equals("{}")) {
                            MAIN.post(() -> onSaveSuccess(name, phone, city, address, "Profile updated ✅"));
                        } else {
                            Log.w(TAG, "PATCH 406: " + errBody);
                            // Row may not exist — fall through to upsert
                            upsertSupabaseRow(name, phone, city, address);
                        }
                    } else {
                        // Any other non-success code — attempt upsert as fallback
                        String errBody = r.body() != null ? r.body().string() : "";
                        Log.w(TAG, "PATCH failed HTTP " + code + " | " + errBody);
                        // If 0 rows matched (empty array body) try upsert
                        if (errBody.equals("[]") || errBody.isEmpty()) {
                            upsertSupabaseRow(name, phone, city, address);
                        } else {
                            MAIN.post(() -> onSaveError("Save failed (HTTP " + code + "). Try again."));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "saveToSupabase exception: " + e.getMessage(), e);
                MAIN.post(() -> onSaveError("Network error. Check your connection and try again."));
            }
        }).start();
    }

    /** Upsert (INSERT or UPDATE) — used when PATCH found 0 matching rows. */
    private void upsertSupabaseRow(String name, String phone, String city, String address) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid",        firebaseUid);
            body.put("name",                name);
            body.put("email",               TextUtils.isEmpty(cachedEmail) ? "" : cachedEmail);
            body.put("phone",               phone);
            body.put("city",                city);
            body.put("address",             address);
            body.put("is_user",             true);
            body.put("is_service_provider", false);
            body.put("is_admin",            false);

            Request req = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/users")
                    .post(RequestBody.create(
                            body.toString(),
                            MediaType.get("application/json; charset=utf-8")))
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "resolution=merge-duplicates,return=minimal")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                int code = r.code();
                Log.d(TAG, "upsertSupabaseRow HTTP " + code);
                if (code == 200 || code == 201 || code == 204) {
                    MAIN.post(() -> onSaveSuccess(name, phone, city, address, "Profile saved ✅"));
                } else {
                    String errBody = r.body() != null ? r.body().string() : "";
                    Log.w(TAG, "upsert error HTTP " + code + " | " + errBody);
                    MAIN.post(() -> onSaveError("Could not save profile (HTTP " + code + "). Try again."));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "upsertSupabaseRow exception: " + e.getMessage(), e);
            MAIN.post(() -> onSaveError("Network error. Check your connection and try again."));
        }
    }

    private void onSaveSuccess(String name, String phone, String city, String address, String msg) {
        isSaving = false;
        showLoading(false);
        setSaveButtonState(false, getString(R.string.pd_btn_save));
        showEditMode(false);
        applyProfile(name, cachedEmail, phone, city, address, cachedImgUrl);
        showSnack(msg);
    }

    private void onSaveError(String msg) {
        isSaving = false;
        showLoading(false);
        setSaveButtonState(false, getString(R.string.pd_btn_save));
        showSnack(msg);
    }

    private void setSaveButtonState(boolean loading, String text) {
        if (btnSaveChanges == null) return;
        btnSaveChanges.setEnabled(!loading);
        btnSaveChanges.setText(text);
        if (btnCancelEdit != null) btnCancelEdit.setEnabled(!loading);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOAD FROM SUPABASE
    // ─────────────────────────────────────────────────────────────────────

    private void loadProfile(String fallbackName, String fallbackEmail, String fallbackPhone) {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?firebase_uid=eq." + firebaseUid
                        + "&select=name,email,phone,city,address,profile_image_url&limit=1";

                Request req = new Request.Builder().url(url)
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "loadProfile HTTP " + r.code() + " | " + body);

                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o   = arr.getJSONObject(0);
                            String name    = nullSafe(o, "name");
                            String email   = nullSafe(o, "email");
                            String phone   = nullSafe(o, "phone");
                            String city    = nullSafe(o, "city");
                            String address = nullSafe(o, "address");
                            String rawImg  = o.isNull("profile_image_url") ? null
                                            : o.optString("profile_image_url", "");
                            String imgUrl  = TextUtils.isEmpty(rawImg) ? null : rawImg;

                            // Merge: prefer DB fields; fall back to Firebase/extras
                            if (TextUtils.isEmpty(name))  name  = fallbackName;
                            if (TextUtils.isEmpty(email)) email = fallbackEmail;
                            if (TextUtils.isEmpty(phone)) phone = fallbackPhone;

                            final String fName = name, fEmail = email, fPhone = phone,
                                         fCity = city, fAddr = address, fImg = imgUrl;
                            MAIN.post(() -> applyProfile(fName, fEmail, fPhone, fCity, fAddr, fImg));
                        } else {
                            // No row yet — seed one from Firebase data
                            Log.i(TAG, "No Supabase row — seeding.");
                            seedSupabaseRow(fallbackName, fallbackEmail, fallbackPhone, "", "");
                            MAIN.post(() -> applyProfile(
                                    fallbackName, fallbackEmail, fallbackPhone, "", "", null));
                        }
                    } else {
                        Log.w(TAG, "loadProfile HTTP " + r.code());
                        MAIN.post(() -> showSnack("Could not load profile (HTTP " + r.code() + ")"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile exception: " + e.getMessage(), e);
                MAIN.post(() -> showSnack("Network error. Showing cached data."));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    /** Non-blocking seed — creates the row in background; failure is non-fatal. */
    private void seedSupabaseRow(String name, String email, String phone,
                                  String city, String address) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid",        firebaseUid);
            body.put("name",                TextUtils.isEmpty(name)    ? "" : name.trim());
            body.put("email",               TextUtils.isEmpty(email)   ? "" : email.trim());
            body.put("phone",               TextUtils.isEmpty(phone)   ? "" : phone.trim());
            body.put("city",                TextUtils.isEmpty(city)    ? "" : city.trim());
            body.put("address",             TextUtils.isEmpty(address) ? "" : address.trim());
            body.put("is_user",             true);
            body.put("is_service_provider", false);
            body.put("is_admin",            false);

            Request req = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/users")
                    .post(RequestBody.create(body.toString(),
                            MediaType.get("application/json; charset=utf-8")))
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "resolution=ignore-duplicates,return=minimal")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                Log.d(TAG, "seedSupabaseRow HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "seedSupabaseRow (non-fatal): " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  APPLY PROFILE TO UI
    // ─────────────────────────────────────────────────────────────────────

    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        cachedName    = safeStr(name);
        cachedEmail   = safeStr(email);
        cachedPhone   = safeStr(phone);
        cachedCity    = safeStr(city);
        cachedAddress = safeStr(address);
        cachedImgUrl  = imgUrl;

        // Header
        String displayName = TextUtils.isEmpty(cachedName)
                ? splitEmail(cachedEmail) : cachedName;
        if (tvName  != null) tvName .setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        if (tvEmail != null) tvEmail.setText(cachedEmail);

        applyAvatar(cachedName, imgUrl);

        // Card rows
        setTextOrFallback(tvDisplayName,    cachedName,    "—");
        setTextOrFallback(tvDisplayEmail,   cachedEmail,   "—");
        setTextOrFallback(tvDisplayPhone,   cachedPhone,   "Not set");
        setTextOrFallback(tvDisplayCity,    cachedCity,    "Not set");
        setTextOrFallback(tvDisplayAddress, cachedAddress, "Not set");
    }

    private void applyAvatar(String name, String imgUrl) {
        if (ivAvatar == null || tvAvatarInitial == null) return;
        if (!TextUtils.isEmpty(imgUrl)) {
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            Glide.with(this).load(imgUrl).circleCrop()
                    .placeholder(R.drawable.bg_profile_avatar_inner)
                    .error(R.drawable.bg_profile_avatar_inner)
                    .into(ivAvatar);
        } else {
            ivAvatar.setVisibility(View.GONE);
            tvAvatarInitial.setVisibility(View.VISIBLE);
            String initial = !TextUtils.isEmpty(name)
                    ? String.valueOf(name.charAt(0)).toUpperCase()
                    : !TextUtils.isEmpty(cachedEmail)
                      ? String.valueOf(cachedEmail.charAt(0)).toUpperCase()
                      : "U";
            tvAvatarInitial.setText(initial);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void showLoading(boolean show) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showSnack(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        View root = findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        else Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void setTextOrFallback(TextView tv, String value, String fallback) {
        if (tv == null) return;
        tv.setText(TextUtils.isEmpty(value) ? fallback : value);
    }

    private void setError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }

    private void clearFieldErrors() {
        if (tilEditName    != null) tilEditName   .setError(null);
        if (tilEditPhone   != null) tilEditPhone  .setError(null);
        if (tilEditCity    != null) tilEditCity   .setError(null);
        if (tilEditAddress != null) tilEditAddress.setError(null);
    }

    private String getText(TextInputEditText et) {
        return (et != null && et.getText() != null) ? et.getText().toString() : "";
    }

    private void setText(TextInputEditText et, String val) {
        if (et != null) et.setText(val != null ? val : "");
    }

    private static String splitEmail(String email) {
        if (TextUtils.isEmpty(email)) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String nullSafe(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    private static String safeStr(String s) { return s != null ? s.trim() : ""; }
}
