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
 * ProfileDetailsActivity — View & Edit user profile.
 * • View mode: clean card with icon rows (name, email, phone, city, address)
 * • Edit mode: outlined input fields with save/cancel
 * • Profile image: optional, uploaded to Supabase Storage "profile-images" bucket
 * • All data synced to Supabase users table via PATCH
 */
public class ProfileDetailsActivity extends AppCompatActivity {

    private static final String TAG    = "ProfileDetails";
    private static final String BUCKET = SupabaseStorageHelper.BUCKET_PROFILE_IMAGES;

    // ── Header views ──────────────────────────────────────────────────────
    private ImageView  ivAvatar, btnBack, btnEditSave;
    private TextView   tvAvatarInitial, tvName, tvEmail;
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
    private boolean isEditMode      = false;
    private Uri     selectedImageUri = null;
    private String  currentImageUrl  = null;
    private String  firebaseUid;

    // cached profile data (to pre-fill edit fields without re-fetch)
    private String cachedName = "", cachedPhone = "", cachedCity = "", cachedAddress = "", cachedEmail = "";

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
        btnEditProfile.setOnClickListener(v -> enterEditMode());

        // Cancel edit
        btnCancelEdit.setOnClickListener(v -> setEditMode(false));

        // Save button inside edit card
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Avatar click in edit mode → pick image
        avatarFrame.setOnClickListener(v -> {
            if (isEditMode) pickImage();
        });
    }

    // ── Switch between view / edit mode ──────────────────────────────────
    private void enterEditMode() {
        // Pre-fill edit fields from cached data
        if (etName    != null) etName.setText(cachedName);
        if (etEmail   != null) { etEmail.setText(cachedEmail); etEmail.setEnabled(false); }
        if (etPhone   != null) etPhone.setText(cachedPhone);
        if (etCity    != null) etCity.setText(cachedCity);
        if (etAddress != null) etAddress.setText(cachedAddress);
        setEditMode(true);
    }

    private void setEditMode(boolean edit) {
        isEditMode = edit;

        // Header icon: edit ✏ in view mode, check ✔ in edit mode
        btnEditSave.setImageResource(edit ? R.drawable.ic_check : R.drawable.ic_edit);

        // Camera badge on avatar
        if (cameraOverlay != null) cameraOverlay.setVisibility(edit ? View.VISIBLE : View.GONE);

        // Cards & button
        if (edit) {
            cardViewMode.setVisibility(View.GONE);
            btnEditProfile.setVisibility(View.GONE);
            cardEditMode.setVisibility(View.VISIBLE);
            cardEditMode.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        } else {
            cardEditMode.setVisibility(View.GONE);
            cardViewMode.setVisibility(View.VISIBLE);
            btnEditProfile.setVisibility(View.VISIBLE);
            cardViewMode.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
            selectedImageUri = null;  // discard uncommitted image
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
                    Log.d(TAG, "loadProfile HTTP " + r.code());
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            String name    = o.optString("name", "");
                            String email   = o.optString("email", "");
                            String phone   = o.optString("phone", "");
                            String city    = o.optString("city", "");
                            String address = o.optString("address", "");
                            String imgUrl  = o.isNull("profile_image_url") ? null
                                             : o.optString("profile_image_url", "");
                            currentImageUrl = TextUtils.isEmpty(imgUrl) ? null : imgUrl;
                            MAIN.post(() -> applyProfile(name, email, phone, city, address, currentImageUrl));
                        }
                    } else {
                        MAIN.post(() -> showSnack("Failed to load profile (HTTP " + r.code() + ")"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile: " + e.getMessage());
                MAIN.post(() -> showSnack("Network error: " + e.getMessage()));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    /** Populate all view-mode display rows and cache values for edit pre-fill. */
    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        // Cache
        cachedName    = name;
        cachedEmail   = email;
        cachedPhone   = phone;
        cachedCity    = city;
        cachedAddress = address;

        // Header
        String displayName = TextUtils.isEmpty(name) ? splitEmail(email) : name;
        tvName.setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        tvEmail.setText(TextUtils.isEmpty(email) ? "" : email);

        // Avatar
        applyAvatar(name, imgUrl);

        // View-mode card rows
        setText(tvDisplayName,    name,    "—");
        setText(tvDisplayEmail,   email,   "—");
        setText(tvDisplayPhone,   phone,   "Not set");
        setText(tvDisplayCity,    city,    "Not set");
        setText(tvDisplayAddress, address, "Not set");
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
            String initial = !TextUtils.isEmpty(name) ? String.valueOf(name.charAt(0)).toUpperCase()
                             : !TextUtils.isEmpty(cachedEmail) ? String.valueOf(cachedEmail.charAt(0)).toUpperCase()
                             : "U";
            tvAvatarInitial.setText(initial);
        }
    }

    // ── Save profile ──────────────────────────────────────────────────────
    private void saveProfile() {
        String name    = text(etName);
        String phone   = text(etPhone);
        String city    = text(etCity);
        String address = text(etAddress);

        // Clear any previous errors
        if (tilName    != null) tilName.setError(null);
        if (tilPhone   != null) tilPhone.setError(null);
        if (tilCity    != null) tilCity.setError(null);
        if (tilAddress != null) tilAddress.setError(null);
        if (tilEmail   != null) tilEmail.setError(null);

        // Validate required fields
        if (TextUtils.isEmpty(name)) {
            if (tilName != null) tilName.setError("Name is required");
            etName.requestFocus();
            return;
        }

        showLoading(true);

        if (selectedImageUri != null) {
            SupabaseStorageHelper.uploadFile(this, selectedImageUri, BUCKET,
                    firebaseUid, new SupabaseStorageHelper.UploadCallback() {
                        @Override public void onSuccess(String url) {
                            currentImageUrl = url;
                            doSave(name, phone, city, address, url);
                        }
                        @Override public void onError(String msg) {
                            showLoading(false);
                            showSnack("Image upload failed: " + msg);
                        }
                    });
        } else {
            doSave(name, phone, city, address, currentImageUrl);
        }
    }

    private void doSave(String name, String phone, String city,
                         String address, String imageUrl) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name",    name);
                body.put("phone",   phone);
                body.put("city",    city);
                body.put("address", address);
                if (imageUrl != null) body.put("profile_image_url", imageUrl);

                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?firebase_uid=eq." + firebaseUid;
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(),
                                MediaType.get("application/json")))
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    int code = r.code();
                    Log.d(TAG, "doSave HTTP " + code);
                    MAIN.post(() -> {
                        showLoading(false);
                        if (code == 200 || code == 204) {
                            // Refresh display without re-fetching
                            applyProfile(name, cachedEmail, phone, city, address, imageUrl);
                            setEditMode(false);
                            selectedImageUri = null;
                            showSnack("Profile updated successfully! ✓");
                        } else {
                            showSnack("Save failed (HTTP " + code + "). Please retry.");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "doSave: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    showSnack("Error: " + e.getMessage());
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

    /** Set text or fallback if blank. */
    private void setText(TextView tv, String value, String fallback) {
        if (tv == null) return;
        tv.setText(TextUtils.isEmpty(value) ? fallback : value);
    }

    /** Safely extract trimmed text from an EditText. */
    private String text(TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    /** Derive a display name from email (part before @). */
    private String splitEmail(String email) {
        if (TextUtils.isEmpty(email)) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
