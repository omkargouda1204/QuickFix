package com.example.quickfix;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProviderOnboardingActivity extends AppCompatActivity {

    private static final String TAG = "ProviderOnboarding";

    // ── Bucket config — stored in BuildConfig (never hardcoded) ──────────
    // ID proof bucket  → BuildConfig.SUPABASE_BUCKET_ID_PROOF
    // Business image bucket → BuildConfig.SUPABASE_BUCKET_BUSINESS_IMG
    private static final String SUPABASE_URL      = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_KEY      = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BUCKET_ID_PROOF   = BuildConfig.SUPABASE_BUCKET_ID_PROOF;
    private static final String BUCKET_BIZ_IMG    = BuildConfig.SUPABASE_BUCKET_BUSINESS_IMG;

    // ── Form fields ───────────────────────────────────────────────────────
    private TextInputLayout tilBusinessName, tilOwnerName, tilPhone, tilEmail,
            tilServiceCategory, tilAddress, tilExperience, tilCity;
    private TextInputEditText etBusinessName, etOwnerName, etPhone, etEmail,
            etAddress, etExperience, etCity, etDescription;
    private AutoCompleteTextView actvServiceCategory;

    // ── Upload section — ID proof ─────────────────────────────────────────
    private LinearLayout idUploadZone, idUploadSuccess;
    private TextView tvUploadedFileName, tvChangeId, tvIdError;

    // ── Upload section — Business image ──────────────────────────────────
    private LinearLayout businessImgUploadZone, businessImgSuccess;
    private ImageView ivBusinessImgPreview;
    private TextView tvBusinessImgName, tvChangeBusinessImg;

    // ── Terms ─────────────────────────────────────────────────────────────
    private ScrollView termsScrollView;
    private TextView tvReadStatus, tvTermsNote;
    private MaterialCheckBox checkboxTerms;

    // ── Submit ────────────────────────────────────────────────────────────
    private MaterialButton btnSubmitApplication;

    // ── Screens ───────────────────────────────────────────────────────────
    private NestedScrollView onboardingScroll;
    private LinearLayout successOverlay;
    private FrameLayout loadingOverlay, successIconFrame;

    // ── State ─────────────────────────────────────────────────────────────
    private Uri idProofUri      = null;   // mandatory
    private Uri businessImgUri  = null;   // optional
    private boolean termsRead   = false;
    private ObjectAnimator pulseAnimator;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    // ── File pickers ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<String[]> idProofPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    idProofUri = uri;
                    String name = resolveFileName(uri);
                    tvUploadedFileName.setText(name);
                    idUploadZone.setVisibility(View.GONE);
                    idUploadSuccess.setVisibility(View.VISIBLE);
                    tvIdError.setVisibility(View.GONE);
                    animateFadeIn(idUploadSuccess);
                }
            });

    private final ActivityResultLauncher<String[]> businessImgPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    businessImgUri = uri;
                    String name = resolveFileName(uri);
                    tvBusinessImgName.setText(name);
                    // Load thumbnail
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        if (is != null) {
                            Bitmap bmp = BitmapFactory.decodeStream(is);
                            is.close();
                            ivBusinessImgPreview.setImageBitmap(bmp);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Thumbnail load failed: " + e.getMessage());
                    }
                    businessImgUploadZone.setVisibility(View.GONE);
                    businessImgSuccess.setVisibility(View.VISIBLE);
                    animateFadeIn(businessImgSuccess);
                }
            });

    private static final String[] CATEGORIES = {
            "Plumbing", "Electrical", "Cleaning", "Repair",
            "AC Service", "Painting", "Carpentry", "Appliance Repair",
            "Pest Control", "Interior Design", "Security", "Other"
    };

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_onboarding);

        setupToolbar();
        initViews();
        setupCategoryDropdown();
        setupUploadZones();
        setupTermsScroll();
        setupTermsCheckbox();
        setupSubmitButton();
        prefillEmail();
        animateScreenIn();
    }

    // ── Toolbar ───────────────────────────────────────────────────────────
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.onboardingToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Init Views ────────────────────────────────────────────────────────
    private void initViews() {
        // Form
        tilBusinessName    = findViewById(R.id.tilBusinessName);
        tilOwnerName       = findViewById(R.id.tilOwnerName);
        tilPhone           = findViewById(R.id.tilPhone);
        tilEmail           = findViewById(R.id.tilEmail);
        tilServiceCategory = findViewById(R.id.tilServiceCategory);
        tilAddress         = findViewById(R.id.tilAddress);
        tilExperience      = findViewById(R.id.tilExperience);
        tilCity            = findViewById(R.id.tilCity);

        etBusinessName     = findViewById(R.id.etBusinessName);
        etOwnerName        = findViewById(R.id.etOwnerName);
        etPhone            = findViewById(R.id.etPhone);
        etEmail            = findViewById(R.id.etEmail);
        etAddress          = findViewById(R.id.etAddress);
        etExperience       = findViewById(R.id.etExperience);
        etCity             = findViewById(R.id.etCity);
        etDescription      = findViewById(R.id.etDescription);
        actvServiceCategory = findViewById(R.id.actvServiceCategory);

        // ID proof upload
        idUploadZone       = findViewById(R.id.idUploadZone);
        idUploadSuccess    = findViewById(R.id.idUploadSuccess);
        tvUploadedFileName = findViewById(R.id.tvUploadedFileName);
        tvChangeId         = findViewById(R.id.tvChangeId);
        tvIdError          = findViewById(R.id.tvIdError);

        // Business image upload
        businessImgUploadZone = findViewById(R.id.businessImgUploadZone);
        businessImgSuccess    = findViewById(R.id.businessImgSuccess);
        ivBusinessImgPreview  = findViewById(R.id.ivBusinessImgPreview);
        tvBusinessImgName     = findViewById(R.id.tvBusinessImgName);
        tvChangeBusinessImg   = findViewById(R.id.tvChangeBusinessImg);

        // Terms
        termsScrollView = findViewById(R.id.termsScrollView);
        tvReadStatus    = findViewById(R.id.tvReadStatus);
        tvTermsNote     = findViewById(R.id.tvTermsNote);
        checkboxTerms   = findViewById(R.id.checkboxTerms);

        // Submit
        btnSubmitApplication = findViewById(R.id.btnSubmitApplication);

        // Screens
        onboardingScroll  = findViewById(R.id.onboardingScroll);
        successOverlay    = findViewById(R.id.successOverlay);
        loadingOverlay    = findViewById(R.id.loadingOverlay);
        successIconFrame  = findViewById(R.id.successIconFrame);
    }

    // ── Screen fade-in ────────────────────────────────────────────────────
    private void animateScreenIn() {
        onboardingScroll.setAlpha(0f);
        onboardingScroll.setTranslationY(40f);
        onboardingScroll.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420).setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ── Category Dropdown ─────────────────────────────────────────────────
    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        actvServiceCategory.setAdapter(adapter);
    }

    // ── Upload Zones ──────────────────────────────────────────────────────
    private void setupUploadZones() {
        // ID proof zone taps
        idUploadZone.setOnClickListener(v -> launchIdProofPicker());
        tvChangeId.setOnClickListener(v -> launchIdProofPicker());

        // Business image zone taps
        businessImgUploadZone.setOnClickListener(v -> launchBusinessImgPicker());
        tvChangeBusinessImg.setOnClickListener(v -> launchBusinessImgPicker());
    }

    private void launchIdProofPicker() {
        idProofPicker.launch(new String[]{"image/*", "application/pdf"});
    }

    private void launchBusinessImgPicker() {
        businessImgPicker.launch(new String[]{"image/*"});
    }

    // ── Terms Scroll — detect when user has scrolled to bottom ───────────
    private void setupTermsScroll() {
        // Checkbox disabled until terms fully scrolled
        checkboxTerms.setEnabled(false);
        termsRead = false;
        termsScrollView.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (!termsRead) {
                    View child = termsScrollView.getChildAt(0);
                    if (child != null) {
                        int visibleH = termsScrollView.getHeight();
                        int contentH = child.getHeight();
                        boolean atBottom = (termsScrollView.getScrollY() + visibleH) >= (contentH - 10);
                        if (atBottom) {
                            termsRead = true;
                            checkboxTerms.setEnabled(true);
                            tvReadStatus.setText("✅ Read");
                            tvReadStatus.setTextColor(getColor(R.color.accent_green));
                            tvTermsNote.setText("You may now agree to the terms");
                            tvTermsNote.setTextColor(getColor(R.color.accent_green));
                            animateFadeIn(checkboxTerms);
                        }
                    }
                }
            }
        });
    }

    // ── Terms Checkbox ────────────────────────────────────────────────────
    private void setupTermsCheckbox() {
        checkboxTerms.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!termsRead && isChecked) {
                checkboxTerms.setChecked(false);
                Toast.makeText(this,
                        "Please scroll and read all terms first", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSubmitApplication.setEnabled(isChecked);
            if (isChecked) startPulseAnimation();
            else stopPulseAnimation();
        });
    }

    // ── Pulse on Submit button ────────────────────────────────────────────
    private void startPulseAnimation() {
        if (pulseAnimator != null && pulseAnimator.isRunning()) return;
        pulseAnimator = ObjectAnimator.ofFloat(btnSubmitApplication, "scaleX", 1f, 1.03f);
        pulseAnimator.setDuration(600);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.start();
        ObjectAnimator pulseY = ObjectAnimator.ofFloat(btnSubmitApplication, "scaleY", 1f, 1.03f);
        pulseY.setDuration(600);
        pulseY.setRepeatMode(ObjectAnimator.REVERSE);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.start();
    }

    private void stopPulseAnimation() {
        if (pulseAnimator != null) { pulseAnimator.cancel(); pulseAnimator = null; }
        btnSubmitApplication.setScaleX(1f);
        btnSubmitApplication.setScaleY(1f);
    }

    // ── Submit Button ─────────────────────────────────────────────────────
    private void setupSubmitButton() {
        btnSubmitApplication.setOnClickListener(v -> {
            stopPulseAnimation();
            if (validateForm()) {
                submitApplication();
            } else {
                onboardingScroll.smoothScrollTo(0, 0);
                if (checkboxTerms.isChecked())
                    new Handler(Looper.getMainLooper()).postDelayed(this::startPulseAnimation, 500);
            }
        });
    }

    // ── Pre-fill email from Firebase ──────────────────────────────────────
    private void prefillEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            if (user.getEmail() != null) etEmail.setText(user.getEmail());
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                etOwnerName.setText(user.getDisplayName());
        }
    }

    // ── Validation ────────────────────────────────────────────────────────
    private boolean validateForm() {
        boolean valid = true;

        String businessName = text(etBusinessName);
        String ownerName    = text(etOwnerName);
        String phone        = text(etPhone);
        String email        = text(etEmail);
        String category     = text(actvServiceCategory);
        String experience   = text(etExperience);
        String address      = text(etAddress);
        String city         = text(etCity);

        if (TextUtils.isEmpty(businessName) || businessName.length() < 2) {
            tilBusinessName.setError(TextUtils.isEmpty(businessName)
                    ? "Business name is required" : "At least 2 characters");
            valid = false;
        } else tilBusinessName.setError(null);

        if (TextUtils.isEmpty(ownerName) || ownerName.length() < 3) {
            tilOwnerName.setError(TextUtils.isEmpty(ownerName)
                    ? "Owner name is required" : "At least 3 characters");
            valid = false;
        } else tilOwnerName.setError(null);

        if (TextUtils.isEmpty(phone) || !phone.matches("\\d{7,15}")) {
            tilPhone.setError(TextUtils.isEmpty(phone)
                    ? "Phone number is required" : "Enter a valid phone (7–15 digits)");
            valid = false;
        } else tilPhone.setError(null);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(TextUtils.isEmpty(email)
                    ? "Email is required" : "Enter a valid email address");
            valid = false;
        } else tilEmail.setError(null);

        if (TextUtils.isEmpty(category)) {
            tilServiceCategory.setError("Please select a service category");
            valid = false;
        } else tilServiceCategory.setError(null);

        if (TextUtils.isEmpty(experience)) {
            tilExperience.setError("Years of experience is required");
            valid = false;
        } else {
            int exp = safeInt(experience);
            if (exp < 0 || exp > 60) {
                tilExperience.setError("Enter a valid number (0–60)");
                valid = false;
            } else tilExperience.setError(null);
        }

        if (TextUtils.isEmpty(address)) {
            tilAddress.setError("Business address is required");
            valid = false;
        } else tilAddress.setError(null);

        if (TextUtils.isEmpty(city)) {
            tilCity.setError("City is required");
            valid = false;
        } else tilCity.setError(null);

        // ID proof is MANDATORY
        if (idProofUri == null) {
            tvIdError.setVisibility(View.VISIBLE);
            tvIdError.startAnimation(android.view.animation.AnimationUtils
                    .loadAnimation(this, android.R.anim.fade_in));
            valid = false;
        } else {
            tvIdError.setVisibility(View.GONE);
        }

        if (!checkboxTerms.isChecked()) {
            Toast.makeText(this, "Please accept Terms & Conditions to continue",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }

        return valid;
    }

    // ── Submit to Supabase ────────────────────────────────────────────────
    private void submitApplication() {
        showLoading(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String firebaseUid = user != null ? user.getUid() : "";

        // Step 1: Upload ID proof (mandatory), then submit form
        uploadIdProof(firebaseUid, idProofUri, idProofUrl -> {
            // Step 2: Upload business image if provided (optional, different bucket)
            if (businessImgUri != null) {
                uploadBusinessImage(firebaseUid, businessImgUri, bizImgUrl ->
                        submitFormData(firebaseUid, idProofUrl, bizImgUrl));
            } else {
                submitFormData(firebaseUid, idProofUrl, null);
            }
        });
    }

    // ── Upload ID proof to id-proof bucket ───────────────────────────────
    private void uploadIdProof(String uid, Uri uri, UploadCallback callback) {
        new Thread(() -> {
            try {
                byte[] bytes = readBytes(uri);
                if (bytes == null) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this, "Could not read ID file. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                String ext      = getExtension(uri);
                String path     = "id_proofs/" + uid + "_" + System.currentTimeMillis() + "." + ext;
                String mimeType = getMimeType(ext);

                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_ID_PROOF + "/" + path)
                        .post(RequestBody.create(bytes, MediaType.parse(mimeType)))
                        .addHeader("apikey",         SUPABASE_KEY)
                        .addHeader("Authorization",  "Bearer " + SUPABASE_KEY)
                        .addHeader("Content-Type",   mimeType)
                        .addHeader("x-upsert",       "true")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    if (r.isSuccessful()) {
                        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/"
                                + BUCKET_ID_PROOF + "/" + path;
                        Log.d(TAG, "ID proof uploaded: " + publicUrl);
                        runOnUiThread(() -> callback.onDone(publicUrl));
                    } else {
                        String body = r.body() != null ? r.body().string() : "";
                        Log.w(TAG, "ID upload failed HTTP " + r.code() + " " + body);
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(this,
                                    "ID upload failed. Please try again.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadIdProof exception: " + e.getMessage());
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Upload error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ── Upload business image to business-images bucket ───────────────────
    private void uploadBusinessImage(String uid, Uri uri, UploadCallback callback) {
        new Thread(() -> {
            try {
                byte[] bytes = readBytes(uri);
                if (bytes == null) {
                    // Non-fatal: continue without business image
                    runOnUiThread(() -> callback.onDone(null));
                    return;
                }
                String ext      = getExtension(uri);
                String path     = "biz_images/" + uid + "_" + System.currentTimeMillis() + "." + ext;
                String mimeType = getMimeType(ext);

                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/storage/v1/object/" + BUCKET_BIZ_IMG + "/" + path)
                        .post(RequestBody.create(bytes, MediaType.parse(mimeType)))
                        .addHeader("apikey",        SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("Content-Type",  mimeType)
                        .addHeader("x-upsert",      "true")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String publicUrl = r.isSuccessful()
                            ? SUPABASE_URL + "/storage/v1/object/public/" + BUCKET_BIZ_IMG + "/" + path
                            : null;
                    Log.d(TAG, "Biz image uploaded: " + publicUrl + " HTTP " + r.code());
                    runOnUiThread(() -> callback.onDone(publicUrl));
                }
            } catch (Exception e) {
                Log.w(TAG, "uploadBusinessImage (non-fatal): " + e.getMessage());
                runOnUiThread(() -> callback.onDone(null)); // continue without it
            }
        }).start();
    }

    // ── Submit form data to provider_applications table ───────────────────
    private void submitFormData(String firebaseUid, String idProofUrl, String bizImgUrl) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("firebase_uid",      firebaseUid);
            payload.put("business_name",     text(etBusinessName));
            payload.put("owner_name",        text(etOwnerName));
            payload.put("phone",             text(etPhone));
            payload.put("email",             text(etEmail));
            payload.put("service_category",  text(actvServiceCategory));
            payload.put("years_experience",  safeInt(text(etExperience)));
            payload.put("address",           text(etAddress));
            payload.put("city",              text(etCity));
            payload.put("description",       text(etDescription));
            payload.put("status",            "pending");
            payload.put("id_proof_url",      idProofUrl != null ? idProofUrl : "");
            if (bizImgUrl != null) payload.put("business_image_url", bizImgUrl);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/provider_applications")
                    .post(body)
                    .addHeader("apikey",        SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "return=minimal")
                    .build();

            http.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Submit failed: " + e.getMessage());
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ProviderOnboardingActivity.this,
                                "Network error. Please check connection and try again.",
                                Toast.LENGTH_LONG).show();
                        if (checkboxTerms.isChecked()) startPulseAnimation();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code();
                    String respBody = response.body() != null ? response.body().string() : "";
                    response.close();
                    Log.d(TAG, "Submit response: " + code + " body: " + respBody);
                    runOnUiThread(() -> {
                        showLoading(false);
                        if (code == 201 || code == 200) {
                            showSuccess();
                        } else {
                            Toast.makeText(ProviderOnboardingActivity.this,
                                    "Submission failed (code " + code + "). Please try again.",
                                    Toast.LENGTH_LONG).show();
                            if (checkboxTerms.isChecked()) startPulseAnimation();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "JSON build error: " + e.getMessage());
            showLoading(false);
            Toast.makeText(this, "Unexpected error. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    // ── Success Screen ────────────────────────────────────────────────────
    private void showSuccess() {
        onboardingScroll.animate()
                .alpha(0f).translationY(-30f)
                .setDuration(300).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    onboardingScroll.setVisibility(View.GONE);
                    successOverlay.setVisibility(View.VISIBLE);
                    successOverlay.setAlpha(0f);
                    successOverlay.setTranslationY(40f);
                    successOverlay.animate()
                            .alpha(1f).translationY(0f)
                            .setDuration(400).setInterpolator(new DecelerateInterpolator())
                            .start();
                    if (successIconFrame != null) {
                        successIconFrame.setScaleX(0f);
                        successIconFrame.setScaleY(0f);
                        successIconFrame.animate()
                                .scaleX(1f).scaleY(1f)
                                .setStartDelay(200).setDuration(500)
                                .setInterpolator(new OvershootInterpolator(1.5f))
                                .start();
                    }
                }).start();

        MaterialButton btnBack = findViewById(R.id.btnBackHome);
        btnBack.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSubmitApplication.setEnabled(!show && checkboxTerms.isChecked());
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private String text(AutoCompleteTextView tv) {
        return tv.getText() != null ? tv.getText().toString().trim() : "";
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String resolveFileName(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        return name != null ? name : "Selected file";
    }

    private String getExtension(Uri uri) {
        String name = resolveFileName(uri).toLowerCase();
        if (name.contains(".")) return name.substring(name.lastIndexOf('.') + 1);
        String mime = getContentResolver().getType(uri);
        if (mime != null) {
            if (mime.contains("pdf"))  return "pdf";
            if (mime.contains("png"))  return "png";
            if (mime.contains("jpeg") || mime.contains("jpg")) return "jpg";
        }
        return "jpg";
    }

    private String getMimeType(String ext) {
        switch (ext) {
            case "pdf":  return "application/pdf";
            case "png":  return "image/png";
            default:     return "image/jpeg";
        }
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[4096];
            int nRead;
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            Log.w(TAG, "readBytes failed: " + e.getMessage());
            return null;
        }
    }

    private void animateFadeIn(View v) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(300).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulseAnimation();
    }

    // ── Upload callback interface ─────────────────────────────────────────
    interface UploadCallback {
        void onDone(String url);
    }
}

