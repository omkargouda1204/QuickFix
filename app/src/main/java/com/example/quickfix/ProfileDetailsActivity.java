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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
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
 * ProfileDetailsActivity — View & Edit user profile with Supabase sync.
 * Image upload → Supabase Storage bucket "profile-images".
 */
public class ProfileDetailsActivity extends AppCompatActivity {

    private static final String TAG    = "ProfileDetails";
    private static final String BUCKET = SupabaseStorageHelper.BUCKET_PROFILE_IMAGES;

    // ── Views ──────────────────────────────────────────────────────────────
    private ImageView  ivAvatar, btnBack, btnEditSave;
    private TextView   tvAvatarInitial, tvName, tvEmail;
    private FrameLayout loadingOverlay;

    private TextInputLayout  tilName, tilPhone, tilCity, tilAddress;
    private TextInputEditText etName, etPhone, etCity, etAddress, etEmail;
    private MaterialButton   btnChangePhoto;

    // ── State ──────────────────────────────────────────────────────────────
    private boolean  isEditMode = false;
    private Uri      selectedImageUri = null;
    private String   currentImageUrl  = null;
    private String   firebaseUid;

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Image picker ───────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        Glide.with(this).load(selectedImageUri)
                                .circleCrop().into(ivAvatar);
                        tvAvatarInitial.setVisibility(View.GONE);
                    }
                }
            });

    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_details);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        firebaseUid = user.getUid();

        bindViews();
        setupClickListeners();
        setEditMode(false);
        loadProfile();
    }

    // ── Display-only rows ─────────────────────────────────────────────────
    private TextView tvDisplayPhone, tvDisplayCity, tvDisplayAddress;

    // ── Bind ───────────────────────────────────────────────────────────────
    private void bindViews() {
        ivAvatar         = findViewById(R.id.ivAvatar);
        tvAvatarInitial  = findViewById(R.id.tvAvatarInitial);
        tvName           = findViewById(R.id.tvName);
        tvEmail          = findViewById(R.id.tvEmail);
        btnBack          = findViewById(R.id.btnBack);
        btnEditSave      = findViewById(R.id.btnEditSave);
        loadingOverlay   = findViewById(R.id.loadingOverlay);
        btnChangePhoto   = findViewById(R.id.btnChangePhoto);
        tilName          = findViewById(R.id.tilName);
        tilPhone         = findViewById(R.id.tilPhone);
        tilCity          = findViewById(R.id.tilCity);
        tilAddress       = findViewById(R.id.tilAddress);
        etName           = findViewById(R.id.etName);
        etPhone          = findViewById(R.id.etPhone);
        etCity           = findViewById(R.id.etCity);
        etAddress        = findViewById(R.id.etAddress);
        etEmail          = findViewById(R.id.etEmail);
        tvDisplayPhone   = findViewById(R.id.tvDisplayPhone);
        tvDisplayCity    = findViewById(R.id.tvDisplayCity);
        tvDisplayAddress = findViewById(R.id.tvDisplayAddress);
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                saveProfile();
            } else {
                setEditMode(true);
            }
        });

        btnChangePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        ivAvatar.setOnClickListener(v -> {
            if (isEditMode) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            }
        });
    }

    // ── Toggle edit/view mode ──────────────────────────────────────────────
    private void setEditMode(boolean edit) {
        isEditMode = edit;
        int inputVis   = edit ? View.VISIBLE : View.GONE;
        int displayVis = edit ? View.GONE    : View.VISIBLE;

        tilName.setVisibility(inputVis);
        tilPhone.setVisibility(inputVis);
        tilCity.setVisibility(inputVis);
        tilAddress.setVisibility(inputVis);
        btnChangePhoto.setVisibility(inputVis);
        View tilEmail = findViewById(R.id.tilEmail);
        if (tilEmail != null) tilEmail.setVisibility(inputVis);

        tvName.setVisibility(displayVis);
        View displayRows = findViewById(R.id.profileDisplayRows);
        if (displayRows != null) displayRows.setVisibility(displayVis);

        btnEditSave.setImageResource(edit ? R.drawable.ic_check : R.drawable.ic_edit);
        btnEditSave.setContentDescription(edit ? "Save" : "Edit");
    }

    // ── Load profile from Supabase ─────────────────────────────────────────
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
                            String name     = o.optString("name", "");
                            String email    = o.optString("email", "");
                            String phone    = o.optString("phone", "");
                            String city     = o.optString("city", "");
                            String address  = o.optString("address", "");
                            String imgUrl   = o.isNull("profile_image_url") ? null
                                              : o.optString("profile_image_url", null);
                            currentImageUrl = imgUrl;
                            MAIN.post(() -> applyProfile(name, email, phone, city, address, imgUrl));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile: " + e.getMessage());
                MAIN.post(() -> Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show());
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        tvName.setText(TextUtils.isEmpty(name) ? "—" : name);
        tvEmail.setText(TextUtils.isEmpty(email) ? "—" : email);
        if (etName  != null) etName.setText(name);
        if (etEmail != null) { etEmail.setText(email); etEmail.setEnabled(false); }
        if (etPhone != null) etPhone.setText(phone);
        if (etCity  != null) etCity.setText(city);
        if (etAddress != null) etAddress.setText(address);
        // Display rows
        if (tvDisplayPhone   != null) tvDisplayPhone.setText(TextUtils.isEmpty(phone)   ? "—" : phone);
        if (tvDisplayCity    != null) tvDisplayCity.setText(TextUtils.isEmpty(city)     ? "—" : city);
        if (tvDisplayAddress != null) tvDisplayAddress.setText(TextUtils.isEmpty(address) ? "—" : address);

        // Avatar
        if (!TextUtils.isEmpty(imgUrl)) {
            Glide.with(this).load(imgUrl).circleCrop()
                    .placeholder(R.drawable.bg_profile_avatar_inner)
                    .into(ivAvatar);
            tvAvatarInitial.setVisibility(View.GONE);
        } else {
            String initial = !TextUtils.isEmpty(name) ? String.valueOf(name.charAt(0)).toUpperCase()
                    : "U";
            tvAvatarInitial.setText(initial);
            tvAvatarInitial.setVisibility(View.VISIBLE);
        }
    }

    // ── Save profile to Supabase ───────────────────────────────────────────
    private void saveProfile() {
        String name    = etName    != null && etName.getText()    != null ? etName.getText().toString().trim()    : "";
        String phone   = etPhone   != null && etPhone.getText()   != null ? etPhone.getText().toString().trim()   : "";
        String city    = etCity    != null && etCity.getText()    != null ? etCity.getText().toString().trim()    : "";
        String address = etAddress != null && etAddress.getText() != null ? etAddress.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Name is required");
            return;
        }
        tilName.setError(null);

        showLoading(true);

        if (selectedImageUri != null) {
            // Upload image first, then save
            SupabaseStorageHelper.uploadFile(this, selectedImageUri, BUCKET,
                    firebaseUid, new SupabaseStorageHelper.UploadCallback() {
                        @Override public void onSuccess(String url) {
                            currentImageUrl = url;
                            doSaveProfile(name, phone, city, address, url);
                        }
                        @Override public void onError(String msg) {
                            showLoading(false);
                            Toast.makeText(ProfileDetailsActivity.this,
                                    "Image upload failed: " + msg, Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            doSaveProfile(name, phone, city, address, currentImageUrl);
        }
    }

    private void doSaveProfile(String name, String phone, String city,
                                String address, String imageUrl) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name",              name);
                body.put("phone",             phone);
                body.put("city",              city);
                body.put("address",           address);
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
                    Log.d(TAG, "saveProfile HTTP " + code);
                    MAIN.post(() -> {
                        showLoading(false);
                        if (code == 200 || code == 204) {
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                            tvName.setText(name);
                            if (!TextUtils.isEmpty(imageUrl)) {
                                Glide.with(this).load(imageUrl).circleCrop().into(ivAvatar);
                                tvAvatarInitial.setVisibility(View.GONE);
                            } else if (!name.isEmpty()) {
                                tvAvatarInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
                            }
                            setEditMode(false);
                            selectedImageUri = null;
                        } else {
                            Toast.makeText(this, "Save failed. Please retry.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "doSaveProfile: " + e.getMessage());
                MAIN.post(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showLoading(boolean show) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}



