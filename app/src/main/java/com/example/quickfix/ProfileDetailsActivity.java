package com.example.quickfix;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
 * ProfileDetailsActivity — View &amp; Edit user profile.
 * <p>
 * View mode  : clean card with icon rows (name, email, phone, city, address)<br>
 * Edit mode  : outlined input fields with save / cancel<br>
 * Image      : optional, uploaded to Supabase Storage "profile-images" bucket<br>
 * Data source: Supabase public.users table (keyed by firebase_uid)<br>
 * HTTP 400 prevention: optional empty fields sent as JSON null, never empty strings.
 */
public class ProfileDetailsActivity extends AppCompatActivity {

    private static final String TAG    = "ProfileDetails";
    private static final String BUCKET = SupabaseStorageHelper.BUCKET_PROFILE_IMAGES;

    // ── Header views ──────────────────────────────────────────────────────
    private ImageView  ivAvatar, btnBack, btnEditSave;
    private TextView   tvAvatarInitial, tvName, tvEmail, tvChangePhotoHint;
    private FrameLayout cameraOverlay, loadingOverlay;
    private FrameLayout avatarFrame;

    // ── View mode card ────────────────────────────────────────────────────
    private MaterialCardView cardViewMode, cardEditMode;
    private TextView tvDisplayName, tvDisplayEmail, tvDisplayPhone,
                     tvDisplayCity,  tvDisplayAddress;
    private MaterialButton btnEditProfile;

    // ── Edit mode fields ──────────────────────────────────────────────────
    private TextInputLayout   tilName, tilEmail, tilPhone, tilCity, tilAddress;
    private TextInputEditText etName, etEmail, etPhone, etCity, etAddress;
    private MaterialButton    btnSaveProfile, btnCancelEdit;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean isEditMode       = false;
    private boolean isSaving         = false;
    private Uri     selectedImageUri = null;
    private String  currentImageUrl  = null;
    private String  firebaseUid;

    // Cached profile data (pre-fill edit fields without re-fetch)
    private String cachedName    = "";
    private String cachedPhone   = "";
    private String cachedCity    = "";
    private String cachedAddress = "";
    private String cachedEmail   = "";

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Image picker ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        ivAvatar.setVisibility(View.VISIBLE);
                        tvAvatarInitial.setVisibility(View.GONE);
                        Glide.with(this).load(selectedImageUri).circleCrop().into(ivAvatar);
                    }
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
        setEditMode(false);   // start in view mode
        loadProfile();

        // Back press: cancel edit mode instead of closing activity
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isEditMode) {
                    setEditMode(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ── Bind all views ────────────────────────────────────────────────────
    private void bindViews() {
        // Header
        ivAvatar        = findViewById(R.id.ivAvatar);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        tvName          = findViewById(R.id.tvName);
        tvEmail         = findViewById(R.id.tvEmail);
        btnBack         = findViewById(R.id.btnBack);
        btnEditSave     = findViewById(R.id.btnEditSave);
        cameraOverlay   = findViewById(R.id.cameraOverlay);
        avatarFrame     = findViewById(R.id.avatarFrame);
        loadingOverlay  = findViewById(R.id.loadingOverlay);
        tvChangePhotoHint = findViewById(R.id.tvChangePhotoHint);

        // View mode
        cardViewMode      = findViewById(R.id.cardViewMode);
        tvDisplayName     = findViewById(R.id.tvDisplayName);
        tvDisplayEmail    = findViewById(R.id.tvDisplayEmail);
        tvDisplayPhone    = findViewById(R.id.tvDisplayPhone);
        tvDisplayCity     = findViewById(R.id.tvDisplayCity);
        tvDisplayAddress  = findViewById(R.id.tvDisplayAddress);
        btnEditProfile    = findViewById(R.id.btnEditProfile);

        // Edit mode
        cardEditMode    = findViewById(R.id.cardEditMode);
        tilName         = findViewById(R.id.tilName);
        tilEmail        = findViewById(R.id.tilEmail);
        tilPhone        = findViewById(R.id.tilPhone);
        tilCity         = findViewById(R.id.tilCity);
        tilAddress      = findViewById(R.id.tilAddress);
        etName          = findViewById(R.id.etName);
        etEmail         = findViewById(R.id.etEmail);
        etPhone         = findViewById(R.id.etPhone);
        etCity          = findViewById(R.id.etCity);
        etAddress       = findViewById(R.id.etAddress);
        btnSaveProfile  = findViewById(R.id.btnSaveProfile);
        btnCancelEdit   = findViewById(R.id.btnCancelEdit);
    }

    // ── Click listeners ───────────────────────────────────────────────────
    private void setupClickListeners() {
        // Back
        btnBack.setOnClickListener(v -> finish());

        // Header edit icon (pencil / tick toggle)
        btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                saveProfile();
            } else {
                enterEditMode();
            }
        });

        // Edit button inside view card
        if (btnEditProfile != null)
            btnEditProfile.setOnClickListener(v -> enterEditMode());

        // Cancel edit
        if (btnCancelEdit != null)
            btnCancelEdit.setOnClickListener(v -> setEditMode(false));

        // Save button inside edit card
        if (btnSaveProfile != null)
            btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Avatar click in edit mode → pick image
        if (avatarFrame != null)
            avatarFrame.setOnClickListener(v -> { if (isEditMode) pickImage(); });
    }

    // ── Switch between view / edit mode ──────────────────────────────────
    private void enterEditMode() {
        // Pre-fill edit fields from cached data
        safeSet(etName,    cachedName);
        safeSet(etPhone,   cachedPhone);
        safeSet(etCity,    cachedCity);
        safeSet(etAddress, cachedAddress);
        if (etEmail != null) {
            etEmail.setText(cachedEmail);
            etEmail.setEnabled(false);
            etEmail.setFocusable(false);
        }
        clearFieldErrors();
        setEditMode(true);
    }

    private void setEditMode(boolean edit) {
        isEditMode = edit;

        // Header icon: edit ✏ in view mode, check ✔ in edit mode
        if (btnEditSave != null)
            btnEditSave.setImageResource(edit ? R.drawable.ic_check : R.drawable.ic_edit);

        // Camera badge + hint on avatar
        if (cameraOverlay    != null) cameraOverlay.setVisibility(edit ? View.VISIBLE : View.GONE);
        if (tvChangePhotoHint != null) tvChangePhotoHint.setVisibility(edit ? View.VISIBLE : View.GONE);

        // Cards & button
        if (edit) {
            if (cardViewMode   != null) cardViewMode.setVisibility(View.GONE);
            if (btnEditProfile != null) btnEditProfile.setVisibility(View.GONE);
            if (cardEditMode   != null) {
                cardEditMode.setVisibility(View.VISIBLE);
                cardEditMode.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            }
        } else {
            if (cardEditMode   != null) cardEditMode.setVisibility(View.GONE);
            if (cardViewMode   != null) {
                cardViewMode.setVisibility(View.VISIBLE);
                cardViewMode.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            }
            if (btnEditProfile != null) btnEditProfile.setVisibility(View.VISIBLE);
            selectedImageUri = null;   // discard uncommitted image pick
        }
    }

    // ── Load profile from Supabase ────────────────────────────────────────
    private void loadProfile() {
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
                            JSONObject o = arr.getJSONObject(0);
                            String name    = nullSafe(o, "name");
                            String email   = nullSafe(o, "email");
                            String phone   = nullSafe(o, "phone");
                            String city    = nullSafe(o, "city");
                            String address = nullSafe(o, "address");
                            String imgUrl  = o.isNull("profile_image_url") ? null
                                             : o.optString("profile_image_url", "");
                            currentImageUrl = TextUtils.isEmpty(imgUrl) ? null : imgUrl;
                            MAIN.post(() -> applyProfile(name, email, phone, city, address, currentImageUrl));
                        } else {
                            // Row exists in Firebase but not yet in Supabase users table
                            FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                            String fbEmail = fbUser != null && fbUser.getEmail() != null
                                             ? fbUser.getEmail() : "";
                            String fbName  = fbUser != null && fbUser.getDisplayName() != null
                                             ? fbUser.getDisplayName() : "";
                            MAIN.post(() -> applyProfile(fbName, fbEmail, "", "", "", null));
                        }
                    } else {
                        MAIN.post(() -> showSnack("Failed to load profile (HTTP " + r.code() + ")"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile: " + e.getMessage());
                MAIN.post(() -> showSnack("Network error. Please check your connection."));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    /** Populate all view-mode display rows and cache values for edit pre-fill. */
    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        // Cache
        cachedName    = name    != null ? name    : "";
        cachedEmail   = email   != null ? email   : "";
        cachedPhone   = phone   != null ? phone   : "";
        cachedCity    = city    != null ? city    : "";
        cachedAddress = address != null ? address : "";

        // Header
        String displayName = TextUtils.isEmpty(cachedName) ? splitEmail(cachedEmail) : cachedName;
        tvName.setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        tvEmail.setText(TextUtils.isEmpty(cachedEmail) ? "" : cachedEmail);

        // Avatar
        applyAvatar(cachedName, imgUrl);

        // View-mode card rows
        setTextOrFallback(tvDisplayName,    cachedName,    "—");
        setTextOrFallback(tvDisplayEmail,   cachedEmail,   "—");
        setTextOrFallback(tvDisplayPhone,   cachedPhone,   "Not set");
        setTextOrFallback(tvDisplayCity,    cachedCity,    "Not set");
        setTextOrFallback(tvDisplayAddress, cachedAddress, "Not set");
    }

    private void applyAvatar(String name, String imgUrl) {
        if (!TextUtils.isEmpty(imgUrl)) {
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(imgUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bg_profile_avatar_inner)
                    .error(R.drawable.bg_profile_avatar_inner)
                    .into(ivAvatar);
        } else {
            ivAvatar.setVisibility(View.GONE);
            tvAvatarInitial.setVisibility(View.VISIBLE);
            String initial = !TextUtils.isEmpty(name)        ? String.valueOf(name.charAt(0)).toUpperCase()
                             : !TextUtils.isEmpty(cachedEmail) ? String.valueOf(cachedEmail.charAt(0)).toUpperCase()
                             : "U";
            tvAvatarInitial.setText(initial);
        }
    }

    // ── Save profile ──────────────────────────────────────────────────────
    private void saveProfile() {
        if (isSaving) return;   // prevent double submit

        String name    = text(etName);
        String phone   = text(etPhone);
        String city    = text(etCity);
        String address = text(etAddress);

        clearFieldErrors();

        // Validate
        if (TextUtils.isEmpty(name)) {
            setFieldError(tilName, "Full name is required");
            if (etName != null) etName.requestFocus();
            return;
        }
        if (name.length() < 2) {
            setFieldError(tilName, "Name must be at least 2 characters");
            if (etName != null) etName.requestFocus();
            return;
        }
        if (!TextUtils.isEmpty(phone) && !isValidPhone(phone)) {
            setFieldError(tilPhone, "Enter a valid phone number (10–15 digits)");
            if (etPhone != null) etPhone.requestFocus();
            return;
        }

        isSaving = true;
        showLoading(true);

        if (selectedImageUri != null) {
            SupabaseStorageHelper.uploadFile(this, selectedImageUri, BUCKET,
                    firebaseUid, new SupabaseStorageHelper.UploadCallback() {
                        @Override public void onSuccess(String url) {
                            currentImageUrl = url;
                            doSave(name, phone, city, address, url);
                        }
                        @Override public void onError(String msg) {
                            isSaving = false;
                            showLoading(false);
                            showSnack("Image upload failed: " + msg);
                        }
                    });
        } else {
            doSave(name, phone, city, address, currentImageUrl);
        }
    }

    /**
     * PATCH the users row in Supabase.
     * Optional fields (phone, city, address) are only included in the JSON body
     * when non-empty to avoid HTTP 400 schema-validation errors on strict columns.
     */
    private void doSave(String name, String phone, String city,
                         String address, String imageUrl) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);

                // Optional fields: send only if user entered something,
                // otherwise send JSON null so the DB column is cleared/kept as-is.
                body.put("phone",   TextUtils.isEmpty(phone)   ? JSONObject.NULL : phone);
                body.put("city",    TextUtils.isEmpty(city)    ? JSONObject.NULL : city);
                body.put("address", TextUtils.isEmpty(address) ? JSONObject.NULL : address);

                if (imageUrl != null && !imageUrl.isEmpty()) {
                    body.put("profile_image_url", imageUrl);
                }

                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?firebase_uid=eq." + firebaseUid;

                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(),
                                MediaType.get("application/json; charset=utf-8")))
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    int    code     = r.code();
                    String respBody = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "doSave HTTP " + code + " | " + respBody);

                    MAIN.post(() -> {
                        isSaving = false;
                        showLoading(false);
                        if (code == 200 || code == 204) {
                            applyProfile(name, cachedEmail, phone, city, address, imageUrl);
                            setEditMode(false);
                            selectedImageUri = null;
                            showSnack("Profile updated successfully ✓");
                        } else if (code == 400) {
                            showSnack("Validation error. Check your input and retry.");
                            Log.w(TAG, "HTTP 400 body: " + respBody);
                        } else if (code == 401 || code == 403) {
                            showSnack("Permission denied. Please re-login.");
                        } else {
                            showSnack("Save failed (HTTP " + code + "). Please retry.");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "doSave exception: " + e.getMessage());
                MAIN.post(() -> {
                    isSaving = false;
                    showLoading(false);
                    showSnack("Network error: " + e.getMessage());
                });
            }
        }).start();
    }

    // ── Image picker ──────────────────────────────────────────────────────
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }


    // ── Helpers ───────────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showSnack(String msg) {
        View root = findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        else Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** Set TextView text, or a fallback string if value is blank. */
    private void setTextOrFallback(TextView tv, String value, String fallback) {
        if (tv == null) return;
        tv.setText(TextUtils.isEmpty(value) ? fallback : value);
    }

    private void setFieldError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }

    private void clearFieldErrors() {
        for (TextInputLayout til : new TextInputLayout[]{ tilName, tilEmail, tilPhone, tilCity, tilAddress }) {
            if (til != null) til.setError(null);
        }
    }

    private void safeSet(TextInputEditText et, String value) {
        if (et != null) et.setText(value != null ? value : "");
    }

    /** Safely extract trimmed text from an EditText; never returns null. */
    private String text(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    /** Derive a display name from the email part before '@'. */
    private String splitEmail(String email) {
        if (TextUtils.isEmpty(email)) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** Read a JSON string field, returning "" instead of "null". */
    private String nullSafe(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    /**
     * Basic phone validation: digits, spaces, dashes, plus sign.
     * Digit count must be between 7 and 15.
     */
    private boolean isValidPhone(String phone) {
        if (TextUtils.isEmpty(phone)) return true; // optional field
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        return digitsOnly.length() >= 7 && digitsOnly.length() <= 15;
    }
}
