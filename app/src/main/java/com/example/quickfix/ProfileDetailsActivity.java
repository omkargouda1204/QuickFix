package com.example.quickfix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

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
    private static final int AVATAR_SIGNED_URL_TTL_SEC = 3600;

    // ── Intent extras (passed by ProfileActivity for instant pre-fill) ────
    public static final String EXTRA_NAME  = "extra_profile_name";
    public static final String EXTRA_EMAIL = "extra_profile_email";
    public static final String EXTRA_PHONE = "extra_profile_phone";
    public static final String EXTRA_IMAGE = "extra_profile_image";

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
    private MaterialButton      btnUploadProfileImage;

    // ── Scroll view (to scroll to top on mode switch) ─────────────────────
    private androidx.core.widget.NestedScrollView profileScrollView;

    // ── State ─────────────────────────────────────────────────────────────
    private String firebaseUid   = "";
    private String cachedName    = "";
    private String cachedEmail   = "";
    private String cachedPhone   = "";
    private String cachedCity    = "";
    private String cachedAddress = "";
    private String cachedProfileImagePath = "";
    private boolean isEditMode   = false;
    private boolean isSaving     = false;
    private Uri pendingAvatarUri = null;
    private Uri pendingCameraUri = null;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final class ProfileFetchResult {
        JSONObject row;
        int httpCode;
        String body;
    }

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                onPhotoSelected(uri);
            });

    private final ActivityResultLauncher<Uri> cameraCaptureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                    onPhotoSelected(pendingCameraUri);
                } else if (pendingCameraUri != null) {
                    showSnack(getString(R.string.pd_camera_capture_cancelled));
                }
                pendingCameraUri = null;
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    openCameraCapture();
                } else {
                    showSnack(getString(R.string.pd_camera_permission_denied));
                }
            });

    private final ActivityResultLauncher<String> mediaPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    imagePickerLauncher.launch("image/*");
                } else {
                    showSnack(getString(R.string.pd_gallery_permission_denied));
                }
            });

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
        String extraImage = getIntent().getStringExtra(EXTRA_IMAGE);

        // ── Step 2: fill any gaps from Firebase metadata ─────────────────
        if (TextUtils.isEmpty(extraName)  && user.getDisplayName() != null)
            extraName  = user.getDisplayName().trim();
        if (TextUtils.isEmpty(extraEmail) && user.getEmail() != null)
            extraEmail = user.getEmail().trim();
        if (TextUtils.isEmpty(extraPhone) && user.getPhoneNumber() != null)
            extraPhone = user.getPhoneNumber().trim();

        if (!TextUtils.isEmpty(extraName) || !TextUtils.isEmpty(extraEmail)) {
            applyProfile(safeStr(extraName), safeStr(extraEmail),
                         safeStr(extraPhone), "", "", safeStr(extraImage));
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
        btnUploadProfileImage = findViewById(R.id.btnUploadProfileImage);

        showEditMode(false);
    }

    // ── Click listeners ───────────────────────────────────────────────────
    private void setupClickListeners() {
        if (btnBack != null) btnBack.setOnClickListener(v -> handleBackPress());
        if (btnEditProfile  != null) btnEditProfile.setOnClickListener(v -> enterEditMode());
        if (btnCancelEdit   != null) btnCancelEdit.setOnClickListener(v -> handleCancelEdit());
        if (btnSaveChanges  != null) btnSaveChanges.setOnClickListener(v -> validateAndSave());
        if (btnUploadProfileImage != null) {
            btnUploadProfileImage.setOnClickListener(v -> {
                if (!isEditMode) {
                    showSnack(getString(R.string.pd_edit_before_photo));
                    return;
                }
                showPhotoSourceDialog();
            });
        }

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
        boolean fieldsChanged = !getText(etEditName).trim().equals(cachedName)
            || !getText(etEditPhone).trim().equals(cachedPhone)
            || !getText(etEditCity).trim().equals(cachedCity)
            || !getText(etEditAddress).trim().equals(cachedAddress);
        return fieldsChanged || pendingAvatarUri != null;
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
        pendingAvatarUri = null;
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
        if (profileScrollView != null) profileScrollView.scrollTo(0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SAVE CHANGES
    // ─────────────────────────────────────────────────────────────────────

    private void validateAndSave() {
        if (isSaving) return;
        clearFieldErrors();

        String name  = getText(etEditName).trim();
        String phone = getText(etEditPhone).trim();
        String city  = getText(etEditCity).trim();
        String addr  = getText(etEditAddress).trim();

        boolean ok = true;
        if (TextUtils.isEmpty(name)) { setError(tilEditName, "Name is required"); ok = false; }
        if (!ok) return;

        saveProfile(name, phone, city, addr);
    }

    private void saveProfile(String name, String phone, String city, String addr) {
        isSaving = true;
        showLoading(true);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            showSnack("Session expired. Please login again.");
            isSaving = false;
            showLoading(false);
            return;
        }

        if (pendingAvatarUri != null) {
            showSnack(getString(R.string.pd_uploading_photo));
            SupabaseStorageHelper.uploadProfileImage(
                    this,
                    pendingAvatarUri,
                    currentUser.getUid(),
                    new SupabaseStorageHelper.UploadCallback() {
                        @Override
                        public void onSuccess(String publicUrl) {
                            persistProfileUpdate(name, phone, city, addr, publicUrl, true);
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() -> {
                                showSnack(TextUtils.isEmpty(message)
                                        ? "Profile image upload failed."
                                        : message);
                                isSaving = false;
                                showLoading(false);
                            });
                        }
                    }
            );
            return;
        }

        persistProfileUpdate(name, phone, city, addr, cachedProfileImagePath, false);
    }

    private void persistProfileUpdate(String name, String phone, String city, String addr,
                                      String uploadedImagePath, boolean avatarUpdated) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("phone", phone);
                body.put("city", city);
                body.put("address", addr);
                body.put("profile_image", uploadedImagePath);

                Request req = new Request.Builder()
                        .url(BuildConfig.SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + firebaseUid)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json; charset=utf-8")))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    final int code = r.code();
                    if (code == 200 || code == 204) {
                        final String finalImg = uploadedImagePath;
                        MAIN.post(() -> {
                            applyProfile(name, cachedEmail, phone, city, addr, finalImg);
                            pendingAvatarUri = null;
                            showEditMode(false);
                            showSnack(getString(avatarUpdated ? R.string.pd_saved_with_photo_ok
                                    : R.string.pd_saved_ok));
                        });
                    } else {
                        MAIN.post(() -> showSnack("Save failed: " + code));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Save error", e);
                MAIN.post(() -> showSnack("Error: " + e.getMessage()));
            } finally {
                MAIN.post(() -> {
                    isSaving = false;
                    showLoading(false);
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────

    private void loadProfile(String fallbackName, String fallbackEmail, String fallbackPhone) {
        showLoading(true);
        new Thread(() -> {
            try {
                ProfileFetchResult fetch = fetchProfileByUid("name,email,phone,city,address,profile_image", false);
                if (fetch.httpCode == 400) {
                    // Backward compatibility when DB still has legacy profile_image_url column.
                    fetch = fetchProfileByUid("name,email,phone,city,address,profile_image_url", true);
                }

                Log.d(TAG, "loadProfile HTTP " + fetch.httpCode + " | " + fetch.body);

                final int responseCode = fetch.httpCode;

                if (responseCode == 200 && fetch.row != null) {
                    JSONObject o   = fetch.row;
                    String name    = nullSafe(o, "name");
                    String email   = nullSafe(o, "email");
                    String phone   = nullSafe(o, "phone");
                    String city    = nullSafe(o, "city");
                    String address = nullSafe(o, "address");
                    String imagePath = nullSafe(o, "profile_image");
                    if (TextUtils.isEmpty(imagePath)) imagePath = nullSafe(o, "profile_image_url");

                    if (TextUtils.isEmpty(name))  name  = fallbackName;
                    if (TextUtils.isEmpty(email)) email = fallbackEmail;
                    if (TextUtils.isEmpty(phone)) phone = fallbackPhone;

                    final String fName = name, fEmail = email, fPhone = phone,
                            fCity = city, fAddr = address, fImg = imagePath;
                    MAIN.post(() -> applyProfile(fName, fEmail, fPhone, fCity, fAddr, fImg));
                } else if (responseCode == 200) {
                    Log.i(TAG, "No Supabase row — seeding.");
                    seedSupabaseRow(fallbackName, fallbackEmail, fallbackPhone, "", "", "");
                    MAIN.post(() -> applyProfile(fallbackName, fallbackEmail, fallbackPhone, "", "", ""));
                } else {
                    MAIN.post(() -> showSnack(getString(R.string.pd_load_http_error, responseCode)));
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile exception: " + e.getMessage(), e);
                MAIN.post(() -> showSnack(getString(R.string.pd_network_showing_cached)));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    private ProfileFetchResult fetchProfileByUid(String selectColumns, boolean legacyImageKey) {
        ProfileFetchResult out = new ProfileFetchResult();
        out.httpCode = 0;
        out.body = "[]";
        try {
            String encodedUid = java.net.URLEncoder.encode(firebaseUid, java.nio.charset.StandardCharsets.UTF_8.name());
            String url = BuildConfig.SUPABASE_URL
                    + "/rest/v1/users?firebase_uid=eq." + encodedUid
                    + "&select=" + selectColumns + "&limit=1";

            Request req = new Request.Builder().url(url)
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Accept",        "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                out.httpCode = r.code();
                out.body = r.body() != null ? r.body().string() : "[]";
                if (r.code() == 200) {
                    JSONArray arr = new JSONArray(out.body);
                    if (arr.length() > 0) {
                        JSONObject row = arr.getJSONObject(0);
                        if (legacyImageKey && !row.has("profile_image") && row.has("profile_image_url")) {
                            row.put("profile_image", nullSafe(row, "profile_image_url"));
                        }
                        out.row = row;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchProfileByUid exception: " + e.getMessage(), e);
        }
        return out;
    }

    /** Non-blocking seed — creates the row in background; failure is non-fatal. */
    private void seedSupabaseRow(String name, String email, String phone,
                                  String city, String address, String profileImagePath) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid",        firebaseUid);
            body.put("name",                TextUtils.isEmpty(name)    ? "" : name.trim());
            body.put("email",               TextUtils.isEmpty(email)   ? "" : email.trim());
            body.put("phone",               TextUtils.isEmpty(phone)   ? "" : phone.trim());
            body.put("city",                TextUtils.isEmpty(city)    ? "" : city.trim());
            body.put("address",             TextUtils.isEmpty(address) ? "" : address.trim());
            body.put("profile_image",       TextUtils.isEmpty(profileImagePath) ? "" : profileImagePath.trim());
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
                               String city, String address, String imagePath) {
        cachedName    = safeStr(name);
        cachedEmail   = safeStr(email);
        cachedPhone   = safeStr(phone);
        cachedCity    = safeStr(city);
        cachedAddress = safeStr(address);
        cachedProfileImagePath = safeStr(imagePath);

        // Header
        String displayName = TextUtils.isEmpty(cachedName)
                ? splitEmail(cachedEmail) : cachedName;
        if (tvName  != null) tvName .setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        if (tvEmail != null) tvEmail.setText(cachedEmail);

        applyAvatar(cachedName, cachedProfileImagePath);

        // Card rows
        setTextOrFallback(tvDisplayName,    cachedName,    "—");
        setTextOrFallback(tvDisplayEmail,   cachedEmail,   "—");
        setTextOrFallback(tvDisplayPhone,   cachedPhone,   "Not set");
        setTextOrFallback(tvDisplayCity,    cachedCity,    "Not set");
        setTextOrFallback(tvDisplayAddress, cachedAddress, "Not set");
    }

    private void applyAvatar(String name, String imagePath) {
        if (ivAvatar == null || tvAvatarInitial == null) return;
        if (!TextUtils.isEmpty(imagePath)) {
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                Glide.with(this).load(imagePath).circleCrop()
                        .placeholder(R.drawable.bg_profile_avatar_inner)
                        .error(R.drawable.bg_profile_avatar_inner)
                        .into(ivAvatar);
                return;
            }
            SupabaseStorageHelper.getSignedUrl(
                    SupabaseStorageHelper.BUCKET_PROFILE_IMAGES,
                    imagePath,
                    AVATAR_SIGNED_URL_TTL_SEC,
                    new SupabaseStorageHelper.SignedUrlCallback() {
                        @Override
                        public void onSuccess(String signedUrl) {
                            if (isFinishing() || isDestroyed()) return;
                            Glide.with(ProfileDetailsActivity.this)
                                    .load(signedUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.bg_profile_avatar_inner)
                                    .error(R.drawable.bg_profile_avatar_inner)
                                    .into(ivAvatar);
                        }

                        @Override
                        public void onError(String message) {
                            Log.w(TAG, "Signed URL failed: " + message);
                            showInitialAvatar(name);
                        }
                    }
            );
        } else {
            showInitialAvatar(name);
        }
    }

    private void showInitialAvatar(String name) {
        if (ivAvatar != null) ivAvatar.setVisibility(View.GONE);
        if (tvAvatarInitial != null) {
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

    private void showPhotoSourceDialog() {
        String[] items = {getString(R.string.pd_photo_source_gallery), getString(R.string.pd_photo_source_camera)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.pd_photo_source_title)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        requestGalleryPermissionAndOpenPicker();
                    } else {
                        requestCameraPermissionAndCapture();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }


    private void requestCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        openCameraCapture();
    }

    private void requestGalleryPermissionAndOpenPicker() {
        String mediaPermission = requiredGalleryPermission();
        if (mediaPermission == null) {
            imagePickerLauncher.launch("image/*");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, mediaPermission) != PackageManager.PERMISSION_GRANTED) {
            mediaPermissionLauncher.launch(mediaPermission);
            return;
        }

        imagePickerLauncher.launch("image/*");
    }

    private String requiredGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return null;
    }

    private void openCameraCapture() {
        try {
            java.io.File cameraDir = new java.io.File(getCacheDir(), "camera");
            if (!cameraDir.exists() && !cameraDir.mkdirs()) {
                showSnack(getString(R.string.pd_camera_unavailable));
                return;
            }
            java.io.File photoFile = java.io.File.createTempFile("profile_", ".jpg", cameraDir);
            String authority = getPackageName() + ".fileprovider";
            pendingCameraUri = FileProvider.getUriForFile(this, authority, photoFile);
            cameraCaptureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Log.e(TAG, "openCameraCapture", e);
            showSnack(getString(R.string.pd_camera_unavailable));
            pendingCameraUri = null;
        }
    }

    private void onPhotoSelected(Uri uri) {
        pendingAvatarUri = uri;
        if (ivAvatar == null || tvAvatarInitial == null) return;
        ivAvatar.setVisibility(View.VISIBLE);
        tvAvatarInitial.setVisibility(View.GONE);
        Glide.with(this)
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.bg_profile_avatar_inner)
                .error(R.drawable.bg_profile_avatar_inner)
                .into(ivAvatar);
        showSnack(getString(R.string.pd_photo_selected_save_hint));
    }
}
