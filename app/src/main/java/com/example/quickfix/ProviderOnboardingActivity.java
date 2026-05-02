package com.example.quickfix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProviderOnboardingActivity extends BaseActivity {

    private static final String TAG = "ProviderOnboarding";

    private static final String SUPABASE_URL    = BuildConfig.SUPABASE_URL;
    private static final String SUPABASE_KEY    = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BUCKET_ID_PROOF = BuildConfig.SUPABASE_BUCKET_ID_PROOF;
    private static final String BUCKET_BIZ_IMG  = BuildConfig.SUPABASE_BUCKET_BUSINESS_IMG;

    // ── Form fields ───────────────────────────────────────────────────────
    private TextInputLayout tilBusinessName, tilOwnerName, tilPhone, tilEmail,
            tilServiceCategory, tilAddress, tilExperience, tilCity, tilPincode;
    private TextInputEditText etBusinessName, etOwnerName, etPhone, etEmail,
            etAddress, etExperience, etCity, etDescription, etPincode;
    private AutoCompleteTextView actvServiceCategory;

    // ── Location ──────────────────────────────────────────────────────────
    private MaterialButton btnUseCurrentLocation;
    private LinearLayout layoutLocationDisplay;
    private TextView tvLocationLabel;
    private double selectedLat = Double.NaN;
    private double selectedLng = Double.NaN;
    private AppLocationManager locationManager;

    // ── Upload — ID proof ─────────────────────────────────────────────────
    private LinearLayout idUploadZone, idUploadSuccess;
    private TextView tvUploadedFileName, tvChangeId, tvIdError;

    // ── Upload — Business image ───────────────────────────────────────────
    private LinearLayout businessImgUploadZone, businessImgSuccess;
    private ImageView ivBusinessImgPreview;
    private TextView tvBusinessImgName, tvChangeBusinessImg;

    // ── Terms ─────────────────────────────────────────────────────────────
    private NestedScrollView termsScrollContainer;
    private TextView tvReadStatus, tvTermsNote;
    private MaterialCheckBox checkboxTerms;

    // ── Submit ────────────────────────────────────────────────────────────
    private MaterialButton btnSubmitApplication;

    // ── Screens ───────────────────────────────────────────────────────────
    private NestedScrollView onboardingScroll;
    private LinearLayout successOverlay;
    private FrameLayout loadingOverlay, successIconFrame;

    // ── State ─────────────────────────────────────────────────────────────
    private Uri idProofUri     = null;
    private Uri businessImgUri = null;
    private boolean termsRead  = false;
    private ObjectAnimator pulseAnimator;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    // ── File pickers ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<String[]> idProofPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    idProofUri = uri;
                    tvUploadedFileName.setText(resolveFileName(uri));
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
                    tvBusinessImgName.setText(resolveFileName(uri));
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
        locationManager = new AppLocationManager(this);
        setupToolbar();
        initViews();
        setupCategoryDropdown();
        setupUploadZones();
        setupPhoneValidation();
        setupTermsScroll();
        setupTermsCheckbox();
        setupSubmitButton();
        setupLocationLogic();
        prefillEmail();
        animateScreenIn();
        // Check if user already has an active provider profile or pending application
        checkAlreadyProvider();
    }

    /** Redirect to status page if user already has a pending/approved application */
    private void checkAlreadyProvider() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String email = user.getEmail() != null ? user.getEmail() : "";
        String uid   = user.getUid();

        // First check provider_profiles (approved)
        ProviderApplicationRepository.checkProviderByEmail(email,
                new ProviderApplicationRepository.ProviderExistsCallback() {
                    @Override public void onExists() {
                        // Already approved provider
                        startActivity(new Intent(ProviderOnboardingActivity.this,
                                ApplicationStatusActivity.class));
                        finish();
                    }
                    @Override public void onNotExists() {
                        // Check for pending/rejected application
                        ProviderApplicationRepository.checkApplicationStatus(uid,
                                new ProviderApplicationRepository.StatusCallback() {
                                    @Override public void onFound(String status) {
                                        String s = status.toLowerCase(java.util.Locale.US);
                                        if ("approved".equals(s) || "pending".equals(s)) {
                                            startActivity(new Intent(ProviderOnboardingActivity.this,
                                                    ApplicationStatusActivity.class));
                                            finish();
                                        }
                                        // If rejected and 24h passed (or no restriction) — let them apply
                                    }
                                    @Override public void onNotFound() { /* show form */ }
                                    @Override public void onError(String msg) { /* show form */ }
                                });
                    }
                    @Override public void onError(String msg) { /* show form */ }
                });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.onboardingToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tilBusinessName    = findViewById(R.id.tilBusinessName);
        tilOwnerName       = findViewById(R.id.tilOwnerName);
        tilPhone           = findViewById(R.id.tilPhone);
        tilEmail           = findViewById(R.id.tilEmail);
        tilServiceCategory = findViewById(R.id.tilServiceCategory);
        tilAddress         = findViewById(R.id.tilAddress);
        tilExperience      = findViewById(R.id.tilExperience);
        tilCity            = findViewById(R.id.tilCity);
        tilPincode         = findViewById(R.id.tilPincode);

        etBusinessName     = findViewById(R.id.etBusinessName);
        etOwnerName        = findViewById(R.id.etOwnerName);
        etPhone            = findViewById(R.id.etPhone);
        etEmail            = findViewById(R.id.etEmail);
        etAddress          = findViewById(R.id.etAddress);
        etExperience       = findViewById(R.id.etExperience);
        etCity             = findViewById(R.id.etCity);
        etPincode          = findViewById(R.id.etPincode);
        etDescription      = findViewById(R.id.etDescription);
        actvServiceCategory = findViewById(R.id.actvServiceCategory);

        btnUseCurrentLocation = findViewById(R.id.btnUseCurrentLocation);
        layoutLocationDisplay = findViewById(R.id.layoutLocationDisplay);
        tvLocationLabel       = findViewById(R.id.tvLocationLabel);

        idUploadZone       = findViewById(R.id.idUploadZone);
        idUploadSuccess    = findViewById(R.id.idUploadSuccess);
        tvUploadedFileName = findViewById(R.id.tvUploadedFileName);
        tvChangeId         = findViewById(R.id.tvChangeId);
        tvIdError          = findViewById(R.id.tvIdError);

        businessImgUploadZone = findViewById(R.id.businessImgUploadZone);
        businessImgSuccess    = findViewById(R.id.businessImgSuccess);
        ivBusinessImgPreview  = findViewById(R.id.ivBusinessImgPreview);
        tvBusinessImgName     = findViewById(R.id.tvBusinessImgName);
        tvChangeBusinessImg   = findViewById(R.id.tvChangeBusinessImg);

        termsScrollContainer = findViewById(R.id.termsScrollContainer);
        tvReadStatus         = findViewById(R.id.tvReadStatus);
        tvTermsNote          = findViewById(R.id.tvTermsNote);
        checkboxTerms        = findViewById(R.id.checkboxTerms);

        btnSubmitApplication = findViewById(R.id.btnSubmitApplication);
        onboardingScroll     = findViewById(R.id.onboardingScroll);
        successOverlay       = findViewById(R.id.successOverlay);
        loadingOverlay       = findViewById(R.id.loadingOverlay);
        successIconFrame     = findViewById(R.id.successIconFrame);

        // Locked by default — must scroll terms first
        checkboxTerms.setEnabled(false);
        btnSubmitApplication.setEnabled(false);
    }

    private void animateScreenIn() {
        onboardingScroll.setAlpha(0f);
        onboardingScroll.setTranslationY(40f);
        onboardingScroll.animate()
                .alpha(1f).translationY(0f)
                .setDuration(420).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void setupCategoryDropdown() {
        actvServiceCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORIES));
    }

    /** Real-time phone validation — clears error as user types, shows on loss of focus */
    private void setupPhoneValidation() {
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String phone = s != null ? s.toString().trim() : "";
                if (phone.length() == 10 && phone.matches("\\d{10}")) {
                    tilPhone.setError(null);
                    tilPhone.setHelperText("✓ Valid phone number");
                } else if (!phone.isEmpty() && phone.length() < 10) {
                    tilPhone.setHelperText("Enter " + (10 - phone.length()) + " more digit(s)");
                    tilPhone.setError(null);
                } else if (phone.length() == 10 && !phone.matches("\\d{10}")) {
                    tilPhone.setError("Only digits allowed");
                } else {
                    tilPhone.setHelperText("Enter 10 digit mobile number");
                    tilPhone.setError(null);
                }
            }
        });
    }

    private void setupUploadZones() {
        idUploadZone.setOnClickListener(v -> launchIdProofPicker());
        tvChangeId.setOnClickListener(v -> launchIdProofPicker());
        businessImgUploadZone.setOnClickListener(v -> launchBusinessImgPicker());
        tvChangeBusinessImg.setOnClickListener(v -> launchBusinessImgPicker());
    }

    private void launchIdProofPicker() {
        idProofPicker.launch(new String[]{"image/*", "application/pdf"});
    }

    private void launchBusinessImgPicker() {
        businessImgPicker.launch(new String[]{"image/*"});
    }

    // ── Terms Scroll — detect when user scrolls to the very bottom ────────
    private void setupTermsScroll() {
        termsRead = false;
        checkboxTerms.setEnabled(false);

        termsScrollContainer.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (!termsRead) {
                        View child = termsScrollContainer.getChildAt(0);
                        if (child != null) {
                            int contentHeight    = child.getMeasuredHeight();
                            int scrollViewHeight = termsScrollContainer.getMeasuredHeight();
                            // 30px tolerance so it works on all screen sizes
                            boolean reachedBottom = (scrollY + scrollViewHeight) >= (contentHeight - 30);
                            if (reachedBottom) {
                                termsRead = true;
                                checkboxTerms.setEnabled(true);

                                tvReadStatus.setText("✅ Read");
                                tvReadStatus.setTextColor(getColor(R.color.accent_green));

                                tvTermsNote.setText("✅ You may now agree to the terms");
                                tvTermsNote.setTextColor(getColor(R.color.accent_green));

                                animateFadeIn(checkboxTerms);
                                Toast.makeText(this,
                                        "You have read all terms! Tick the checkbox to agree.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    // ── Terms Checkbox ────────────────────────────────────────────────────
    private void setupTermsCheckbox() {
        checkboxTerms.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked && !termsRead) {
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

    private void setupLocationLogic() {
        if (btnUseCurrentLocation != null) {
            btnUseCurrentLocation.setOnClickListener(v -> {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
                    return;
                }
                fetchLocation();
            });
        }
    }

    private void fetchLocation() {
        showLoading(true);
        locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
            @Override
            public void onSuccess(String city, String area, double lat, double lng) {
                runOnUiThread(() -> {
                    showLoading(false);
                    selectedLat = lat;
                    selectedLng = lng;

                    // Fetch detailed address components manually for better control
                    try {
                        Geocoder geocoder = new Geocoder(ProviderOnboardingActivity.this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            Address addr = addresses.get(0);

                            // 1. Auto-fill Pincode
                            String pincode = addr.getPostalCode();
                            if (pincode != null && pincode.length() == 6) {
                                etPincode.setText(pincode);
                                tilPincode.setError(null);
                            }

                            // 2. Auto-fill City
                            if (city != null && !city.isEmpty()) {
                                etCity.setText(city);
                                tilCity.setError(null);
                            }

                            // 3. Auto-fill Business Address (Clean)
                            // We construct a clean address without Plus Codes (alphanumeric mixed codes like 7JFC+VX)
                            StringBuilder cleanAddress = new StringBuilder();
                            
                            // Get individual address lines instead of using addr.getFeatureName() which often contains the Plus Code
                            int maxLines = addr.getMaxAddressLineIndex();
                            for (int i = 0; i <= maxLines; i++) {
                                String line = addr.getAddressLine(i);
                                if (line == null) continue;
                                
                                // Regex to detect Plus Codes: e.g., 7JFC+VX Bangalore, Karnataka
                                // Plus codes usually have a '+' at the 4th or 8th position and contain mixed alphanumeric
                                if (line.contains("+") && line.split(" ").length > 0) {
                                    // Try to strip the plus code part if it's at the start
                                    String firstPart = line.split(" ")[0];
                                    if (firstPart.contains("+") && firstPart.length() >= 7) {
                                        line = line.substring(firstPart.length()).trim();
                                    }
                                }
                                
                                // Skip if the line still looks like a standalone plus code
                                if (line.matches("^[A-Z0-9]{4}\\+[A-Z0-9]{2,}.*") || line.matches("^[A-Z0-9]{8}\\+.*")) {
                                    continue; 
                                }

                                if (line.length() > 0) {
                                    if (cleanAddress.length() > 0) cleanAddress.append(", ");
                                    cleanAddress.append(line);
                                }
                            }

                            String finalAddress = cleanAddress.toString();
                            
                            // Final safety check: remove any leading/trailing commas or leftover fragments
                            finalAddress = finalAddress.replaceAll("^[ ,]+|[ ,]+$", "");
                            
                            if (finalAddress.isEmpty()) finalAddress = (area != null && !area.isEmpty()) ? area : city;

                            etAddress.setText(finalAddress);
                            tilAddress.setError(null);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Reverse geocode failed", e);
                        if (!city.isEmpty()) etCity.setText(city);
                        if (!area.isEmpty()) etAddress.setText(area);
                    }

                    if (layoutLocationDisplay != null) {
                        layoutLocationDisplay.setVisibility(View.VISIBLE);
                        // Display ONLY the place name (Area if available, otherwise City)
                        String placeName = (area != null && !area.isEmpty()) ? area : city;
                        tvLocationLabel.setText("Detected: " + (placeName.isEmpty() ? "Location" : placeName));
                        animateFadeIn(layoutLocationDisplay);
                    }
                    Toast.makeText(ProviderOnboardingActivity.this, "Location detected!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(String reason) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(ProviderOnboardingActivity.this, "Location failed: " + reason, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        }
    }

    private void prefillEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        // 1. Email is always from Firebase and read-only
        if (user.getEmail() != null) etEmail.setText(user.getEmail());
        etEmail.setFocusable(false);
        etEmail.setFocusableInTouchMode(false);
        etEmail.setClickable(false);
        etEmail.setCursorVisible(false);
        if (tilEmail != null) tilEmail.setFocusable(false);

        // 2. Fetch Name from 'users' table in Supabase (Priority)
        String uid = user.getUid();
        SupabaseClient.fetchUserProfile(uid, new SupabaseClient.UserProfileCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    // UserSession is updated by SupabaseClient.fetchUserProfile
                    String dbName = UserSession.name;
                    if (dbName != null && !dbName.trim().isEmpty()) {
                        etOwnerName.setText(dbName);
                        Log.d(TAG, "Prefilled name from Supabase: " + dbName);
                    } else {
                        fallbackFirebaseName(user);
                    }
                });
            }

            @Override
            public void onError(String msg) {
                Log.w(TAG, "Failed to fetch name from Supabase: " + msg);
                runOnUiThread(() -> fallbackFirebaseName(user));
            }
        });
    }

    private void fallbackFirebaseName(FirebaseUser user) {
        if (etOwnerName.getText().toString().isEmpty()) {
            String fbName = user.getDisplayName();
            if (fbName != null && !fbName.isEmpty()) {
                etOwnerName.setText(fbName);
                Log.d(TAG, "Prefilled name from Firebase fallback: " + fbName);
            }
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
        String pincode      = text(etPincode);

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

        if (TextUtils.isEmpty(phone) || !phone.matches("\\d{10}")) {
            tilPhone.setError(TextUtils.isEmpty(phone)
                    ? "Phone number is required" : "Enter a valid 10 digit mobile number");
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
            tilAddress.setError("Business address is required"); valid = false;
        } else tilAddress.setError(null);

        if (TextUtils.isEmpty(city)) {
            tilCity.setError("City is required"); valid = false;
        } else tilCity.setError(null);

        if (TextUtils.isEmpty(pincode) || pincode.length() < 6) {
            tilPincode.setError("Enter 6 digit pincode"); valid = false;
        } else tilPincode.setError(null);

        if (idProofUri == null) {
            tvIdError.setVisibility(View.VISIBLE);
            tvIdError.startAnimation(android.view.animation.AnimationUtils
                    .loadAnimation(this, android.R.anim.fade_in));
            valid = false;
        } else tvIdError.setVisibility(View.GONE);

        if (!termsRead) {
            Toast.makeText(this,
                    "Please scroll and read all Terms & Conditions first",
                    Toast.LENGTH_LONG).show();
            valid = false;
        } else if (!checkboxTerms.isChecked()) {
            Toast.makeText(this,
                    "Please accept Terms & Conditions to continue",
                    Toast.LENGTH_SHORT).show();
            valid = false;
        }

        return valid;
    }

    // ── Submit ────────────────────────────────────────────────────────────
    private void submitApplication() {
        // Guard: Firebase user must be signed in
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || TextUtils.isEmpty(user.getUid())) {
            Toast.makeText(this,
                    "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        final String firebaseUid = user.getUid();

        uploadIdProof(firebaseUid, idProofUri, idProofUrl -> {
            if (businessImgUri != null) {
                uploadBusinessImage(firebaseUid, businessImgUri, bizImgUrl ->
                        submitFormData(firebaseUid, idProofUrl, bizImgUrl));
            } else {
                submitFormData(firebaseUid, idProofUrl, null);
            }
        });
    }

    // ── Upload ID proof — correct Supabase REST Storage URL ───────────────
    private void uploadIdProof(String uid, Uri uri, UploadCallback callback) {
        new Thread(() -> {
            try {
                byte[] bytes = readBytes(uri);
                if (bytes == null || bytes.length == 0) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(this,
                                "Could not read ID file. Please select a valid file.",
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String ext      = getExtension(uri);
                String fileName = uid + "_id_" + System.currentTimeMillis() + "." + ext;
                String path     = "id_proofs/" + fileName;
                String mimeType = getMimeType(ext);

                // ✅ Correct Supabase Storage REST endpoint
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET_ID_PROOF + "/" + path;
                Log.d(TAG, "ID upload URL: " + uploadUrl + " | size=" + bytes.length + " | mime=" + mimeType);

                Request req = new Request.Builder()
                        .url(uploadUrl)
                        .post(RequestBody.create(bytes, MediaType.parse(mimeType)))
                        .addHeader("apikey",        SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("Content-Type",  mimeType)
                        .addHeader("x-upsert",      "true")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String respBody = r.body() != null ? r.body().string() : "";
                    Log.d(TAG, "ID upload: HTTP " + r.code() + " | " + respBody);

                    if (r.isSuccessful()) {
                        // ✅ Public URL stored in DB
                        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/"
                                + BUCKET_ID_PROOF + "/" + path;
                        Log.d(TAG, "ID proof uploaded OK. URL: " + publicUrl);
                        runOnUiThread(() -> callback.onDone(publicUrl));
                    } else {
                        Log.e(TAG, "ID upload failed HTTP " + r.code() + ": " + respBody);
                        runOnUiThread(() -> {
                            showLoading(false);
                            String msg = "ID upload failed (HTTP " + r.code() + ").";
                            if (r.code() == 400)
                                msg += "\nCheck bucket name/RLS policy in Supabase.";
                            else if (r.code() == 401 || r.code() == 403)
                                msg += "\nUnauthorised — check SUPABASE_ANON_KEY.";
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadIdProof exception: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ── Upload business image ─────────────────────────────────────────────
    private void uploadBusinessImage(String uid, Uri uri, UploadCallback callback) {
        new Thread(() -> {
            try {
                byte[] bytes = readBytes(uri);
                if (bytes == null || bytes.length == 0) {
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
                    Log.d(TAG, "Biz image: HTTP " + r.code() + " | url=" + publicUrl);
                    runOnUiThread(() -> callback.onDone(publicUrl));
                }
            } catch (Exception e) {
                Log.w(TAG, "uploadBusinessImage non-fatal: " + e.getMessage());
                runOnUiThread(() -> callback.onDone(null));
            }
        }).start();
    }

    // ── Submit form data — use shared repository path so admin auto-approve is honored ──
    private void submitFormData(String firebaseUid, String idProofUrl, String bizImgUrl) {

        // ── 1. Collect & validate all required fields before sending ──────
        String userName     = text(etOwnerName);
        String userEmail    = text(etEmail);
        String businessName = text(etBusinessName);
        String category     = text(actvServiceCategory);
        String phone        = text(etPhone);
        String city         = text(etCity);
        String pincode      = text(etPincode);
        String address      = text(etAddress);
        String description  = text(etDescription);

        int experienceYrs;
        try { experienceYrs = Integer.parseInt(text(etExperience)); }
        catch (Exception e) { experienceYrs = -1; }

        if (TextUtils.isEmpty(firebaseUid)) {
            showLoading(false);
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(userName)) {
            showLoading(false);
            tilOwnerName.setError("Owner name is required");
            tilOwnerName.requestFocus();
            return;
        } else { tilOwnerName.setError(null); }

        if (TextUtils.isEmpty(userEmail) || !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            showLoading(false);
            tilEmail.setError("Valid email is required");
            tilEmail.requestFocus();
            return;
        } else { tilEmail.setError(null); }

        if (TextUtils.isEmpty(businessName)) {
            showLoading(false);
            tilBusinessName.setError("Business name is required");
            tilBusinessName.requestFocus();
            return;
        } else { tilBusinessName.setError(null); }

        if (TextUtils.isEmpty(category) || category.equalsIgnoreCase("Select Category")) {
            showLoading(false);
            Toast.makeText(this, "Please select a service category", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
            showLoading(false);
            tilPhone.setError("Valid phone number is required");
            tilPhone.requestFocus();
            return;
        } else { tilPhone.setError(null); }

        if (experienceYrs < 0 || experienceYrs > 60) {
            showLoading(false);
            tilExperience.setError("Enter valid experience (0–60 years)");
            tilExperience.requestFocus();
            return;
        } else { tilExperience.setError(null); }

        if (TextUtils.isEmpty(city)) {
            showLoading(false);
            tilCity.setError("City is required");
            tilCity.requestFocus();
            return;
        } else { tilCity.setError(null); }

        if (TextUtils.isEmpty(pincode) || pincode.length() < 6) {
            showLoading(false);
            tilPincode.setError("Pincode is required");
            tilPincode.requestFocus();
            return;
        } else { tilPincode.setError(null); }

        if (TextUtils.isEmpty(address)) {
            showLoading(false);
            tilAddress.setError("Business address is required");
            tilAddress.requestFocus();
            return;
        } else { tilAddress.setError(null); }

        if (TextUtils.isEmpty(idProofUrl)) {
            showLoading(false);
            if (tvIdError != null) tvIdError.setVisibility(View.VISIBLE);
            Toast.makeText(this, "ID proof upload is required. Please upload your ID.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!checkboxTerms.isChecked()) {
            showLoading(false);
            Toast.makeText(this, "You must read and accept the Terms & Conditions.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProviderApplicationRepository.submitApplication(
                firebaseUid,
                userName,
                userEmail,
                phone,
                businessName,
                category,
                description,
                address,
                city,
                "", // area
                pincode,
                address, // fullAddress
                String.valueOf(experienceYrs),
                bizImgUrl,
                idProofUrl,
                selectedLat,
                selectedLng,
                new ProviderApplicationRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            showLoading(false);
                            showSuccess();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            String userMsg;
                            if ("DUPLICATE".equals(message)) {
                                userMsg = "You already have a pending or approved provider application.";
                            } else if ("NETWORK_ERROR".equals(message)) {
                                userMsg = "Network error. Please check your internet and try again.";
                            } else {
                                userMsg = "Submission failed: " + (message != null ? message : "Unknown error");
                            }
                            Toast.makeText(ProviderOnboardingActivity.this, userMsg, Toast.LENGTH_LONG).show();
                            if (checkboxTerms.isChecked()) startPulseAnimation();
                        });
                    }
                });
    }

    // ── Success Screen ────────────────────────────────────────────────────
    private void showSuccess() {
        onboardingScroll.animate()
                .alpha(0f).translationY(-30f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    onboardingScroll.setVisibility(View.GONE);
                    successOverlay.setVisibility(View.VISIBLE);
                    successOverlay.setAlpha(0f);
                    successOverlay.setTranslationY(40f);
                    successOverlay.animate().alpha(1f).translationY(0f)
                            .setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
                    if (successIconFrame != null) {
                        successIconFrame.setScaleX(0f);
                        successIconFrame.setScaleY(0f);
                        successIconFrame.animate().scaleX(1f).scaleY(1f)
                                .setStartDelay(200).setDuration(500)
                                .setInterpolator(new OvershootInterpolator(1.5f)).start();
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
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            default:    return "image/jpeg";
        }
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int n;
            while ((n = is.read(data)) != -1) buf.write(data, 0, n);
            buf.flush();
            return buf.toByteArray();
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

    interface UploadCallback { void onDone(String url); }
}

