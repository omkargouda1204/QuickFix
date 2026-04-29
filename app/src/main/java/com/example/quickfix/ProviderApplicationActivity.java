package com.example.quickfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;
import java.util.Locale;

public class ProviderApplicationActivity extends BaseActivity {

    private static final String TAG = "ProvAppActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputEditText etBusinessName, etOwnerName, etEmail, etPhone, etPincode, etAddress, etExperience, etDescription;
    private TextInputLayout tilBusinessName, tilPhone, tilPincode, tilAddress, tilExperience, tilDescription;
    private AutoCompleteTextView actCategory;
    private NestedScrollView mainScroll;
    private TextView         tvTermsScrollMsg, tvLocationLabel, tvLoadingMsg, tvIdProofLabel, tvBizImageLabel, tvIdError, tvLocationError;
    private MaterialCheckBox cbTerms;
    private MaterialButton   btnSubmit, btnSetBusinessLocation;
    private FrameLayout      successOverlay, loadingOverlay;
    private LinearLayout     layoutLocationDisplay, layoutIdProof, layoutBizImage;
    private ImageView        ivIdProofPreview, ivBizImagePreview;
    private TextView         tvStepIndicator1, tvStepIndicator2, tvStepIndicator3;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean termsScrolledToBottom = false;
    private boolean isSubmitting          = false;

    private double             selectedLat = Double.NaN;
    private double             selectedLng = Double.NaN;
    private String             selectedArea = "";

    private Uri idProofUri = null;
    private Uri businessImgUri = null;
    private String idProofUrl = "";
    private String businessImgUrl = "";

    private AppLocationManager locationManager;

    private static final String[] CATEGORIES = {
            "Plumbing", "Electrical", "Cleaning", "Repair & Maintenance",
            "AC Service", "Painting", "Carpentry", "Pest Control",
            "Appliance Repair", "Security Systems", "Landscaping", "Other"
    };

    // ── File pickers ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<String[]> idProofPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    idProofUri = uri;
                    ivIdProofPreview.setImageURI(uri);
                    ivIdProofPreview.setColorFilter(null);
                    tvIdProofLabel.setText("ID Proof Selected ✅");
                    tvIdProofLabel.setTextColor(getResources().getColor(R.color.success_green));
                    checkFormValid();
                }
            });

    private final ActivityResultLauncher<String[]> businessImgPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    businessImgUri = uri;
                    ivBizImagePreview.setImageURI(uri);
                    ivBizImagePreview.setColorFilter(null);
                    tvBizImageLabel.setText("Business Image Selected ✅");
                    tvBizImageLabel.setTextColor(getResources().getColor(R.color.success_green));
                    checkFormValid();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_application);

        locationManager = new AppLocationManager(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setupToolbar();
        bindViews();
        autoFetchEmail(user);
        setupCategoryDropdown();
        prefillFromSupabase(user);
        setupTermsScrollDetection();
        setupFieldWatchers();
        setupImagePickers();
        setupSubmitButton(user);
        setupLocationLogic();
        checkExistingApplication(user.getUid());
    }

    private void autoFetchEmail(FirebaseUser user) {
        if (user != null && user.getEmail() != null) {
            etEmail.setText(user.getEmail());
            etEmail.setEnabled(false);
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        etBusinessName  = findViewById(R.id.etBusinessName);
        etOwnerName     = findViewById(R.id.etOwnerName);
        etEmail         = findViewById(R.id.etEmail);
        etPhone         = findViewById(R.id.etPhone);
        etPincode       = findViewById(R.id.etPincode);
        etAddress       = findViewById(R.id.etAddress);
        etExperience    = findViewById(R.id.etExperience);
        etDescription   = findViewById(R.id.etDescription);
        actCategory     = findViewById(R.id.actCategory);

        tilBusinessName = findViewById(R.id.tilBusinessName);
        tilPhone        = findViewById(R.id.tilPhone);
        tilPincode      = findViewById(R.id.tilPincode);
        tilAddress      = findViewById(R.id.tilAddress);
        tilExperience   = findViewById(R.id.tilExperience);
        tilDescription  = findViewById(R.id.tilDescription);

        mainScroll       = findViewById(R.id.mainScroll);
        tvTermsScrollMsg = findViewById(R.id.tvTermsScrollMsg);
        cbTerms          = findViewById(R.id.cbTerms);
        btnSubmit        = findViewById(R.id.btnSubmit);
        btnSetBusinessLocation = findViewById(R.id.btnSetBusinessLocation);
        tvLocationLabel  = findViewById(R.id.tvLocationLabel);
        layoutLocationDisplay = findViewById(R.id.layoutLocationDisplay);
        
        layoutIdProof    = findViewById(R.id.layoutIdProof);
        layoutBizImage   = findViewById(R.id.layoutBizImage);
        ivIdProofPreview = findViewById(R.id.ivIdProofPreview);
        ivBizImagePreview= findViewById(R.id.ivBizImagePreview);
        tvIdProofLabel   = findViewById(R.id.tvIdProofLabel);
        tvBizImageLabel  = findViewById(R.id.tvBizImageLabel);
        tvIdError        = findViewById(R.id.tvIdError);
        tvLocationError  = findViewById(R.id.tvLocationError);

        loadingOverlay   = findViewById(R.id.loadingOverlay);
        tvLoadingMsg     = findViewById(R.id.tvLoadingMsg);
        successOverlay   = findViewById(R.id.successOverlay);
        
        tvStepIndicator1 = findViewById(R.id.tvStepIndicator1);
        tvStepIndicator2 = findViewById(R.id.tvStepIndicator2);
        tvStepIndicator3 = findViewById(R.id.tvStepIndicator3);
        
        findViewById(R.id.btnSuccessHome).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
    }

    private void setupCategoryDropdown() {
        actCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, CATEGORIES));
        actCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = CATEGORIES[position];
            updateHeader(selected);
            checkFormValid();
        });
    }

    private void updateHeader(String category) {
        TextView title = findViewById(R.id.tvHeaderTitle);
        if (title != null) title.setText("Become a " + category + " Provider");
        TextView icon = findViewById(R.id.tvHeaderIcon);
        if (icon != null) {
            String emoji = "🏪"; 
            if (category.contains("Plumb"))      emoji = "🪠";
            else if (category.contains("Elect"))  emoji = "⚡";
            else if (category.contains("Clean"))  emoji = "🧹";
            else if (category.contains("Repair")) emoji = "🛠️";
            else if (category.contains("AC"))      emoji = "❄️";
            else if (category.contains("Paint"))   emoji = "🎨";
            else if (category.contains("Carp"))    emoji = "🪚";
            else if (category.contains("Pest"))    emoji = "🐜";
            icon.setText(emoji);
        }
    }

    private void prefillFromSupabase(FirebaseUser user) {
        UserRepository.fetchUserByUid(user.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onSuccess(UserModel u) {
                if (u != null && !TextUtils.isEmpty(u.name)) {
                    etOwnerName.setText(u.name);
                    updateStepIndicators(1);
                }
            }
            @Override public void onError(String msg) {
                if (user.getDisplayName() != null) etOwnerName.setText(user.getDisplayName());
            }
        });
    }

    private void setupLocationLogic() {
        btnSetBusinessLocation.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
                return;
            }
            fetchLocation();
        });
    }

    private void fetchLocation() {
        showLoading(true, "Fetching GPS location...");
        locationManager.fetchLiveLocation(new AppLocationManager.LiveLocationCallback() {
            @Override
            public void onSuccess(String city, String area, double lat, double lng) {
                runOnUiThread(() -> {
                    showLoading(false, "");
                    selectedLat = lat;
                    selectedLng = lng;
                    
                    // Resolve area name
                    selectedArea = (area != null && !area.isEmpty()) ? area : city;
                    if (selectedArea == null || selectedArea.isEmpty()) selectedArea = "Unknown Area";
                    
                    layoutLocationDisplay.setVisibility(View.VISIBLE);
                    tvLocationLabel.setText(selectedArea);
                    tvLocationError.setVisibility(View.GONE);
                    
                    updateStepIndicators(2);
                    checkFormValid();
                });
            }
            @Override public void onFailure(String error) {
                runOnUiThread(() -> { 
                    showLoading(false, ""); 
                    showError("Location failed: " + error); 
                });
            }
        });
    }

    private void setupTermsScrollDetection() {
        mainScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (termsScrolledToBottom) return;
            // The terms scroll box is a ScrollView inside the main NestedScrollView or something similar.
            // Wait, looking at the layout, it's a ScrollView with id scrollTerms.
            View scrollTerms = findViewById(R.id.scrollTerms);
            if (scrollTerms instanceof android.widget.ScrollView) {
                android.widget.ScrollView sv = (android.widget.ScrollView) scrollTerms;
                View child = sv.getChildAt(0);
                if (child != null) {
                    int diff = child.getMeasuredHeight() - (sv.getMeasuredHeight() + sv.getScrollY());
                    if (diff <= 10) { 
                        termsScrolledToBottom = true;
                        cbTerms.setEnabled(true);
                        tvTermsScrollMsg.setText("✅ Terms read. You can now accept.");
                        tvTermsScrollMsg.setTextColor(getResources().getColor(R.color.success_green));
                        checkFormValid();
                    }
                }
            }
        });
    }

    private void setupFieldWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { checkFormValid(); }
        };
        etBusinessName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etPincode.addTextChangedListener(watcher);
        etAddress.addTextChangedListener(watcher);
        etExperience.addTextChangedListener(watcher);
        etDescription.addTextChangedListener(watcher);
        cbTerms.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) updateStepIndicators(3);
            checkFormValid();
        });
    }

    private void checkFormValid() {
        String bName = str(etBusinessName);
        String phone = str(etPhone);
        String pin   = str(etPincode);
        String exp   = str(etExperience);
        String desc  = str(etDescription);
        String addr  = str(etAddress);
        String cat   = str(actCategory);

        boolean bNameOk = !bName.isEmpty() && bName.length() >= 3 && bName.length() <= 80;
        boolean phoneOk = phone.matches("\\d{10}");
        boolean pinOk   = pin.matches("\\d{6}");
        boolean expOk   = false;
        if (!exp.isEmpty()) {
            try {
                int years = Integer.parseInt(exp);
                expOk = years >= 1 && years <= 50;
            } catch (Exception ignored) {
                expOk = false;
            }
        }
        boolean descOk  = desc.length() >= 10 && desc.length() <= 300;
        boolean addrOk  = !addr.isEmpty() && addr.length() >= 10 && addr.length() <= 180;
        boolean catOk   = !cat.isEmpty();
        boolean locOk   = !Double.isNaN(selectedLat) && !Double.isNaN(selectedLng) && !TextUtils.isEmpty(selectedArea);
        boolean idOk    = idProofUri != null;
        boolean termsOk = cbTerms.isChecked();

        // Real-time error messages
        if (!phone.isEmpty() && !phoneOk) tilPhone.setError("Enter a valid 10-digit phone number");
        else tilPhone.setError(null);

        if (!pin.isEmpty() && !pinOk) tilPincode.setError("Enter valid 6-digit pincode");
        else tilPincode.setError(null);

        if (!desc.isEmpty() && desc.length() < 10) tilDescription.setError("Minimum 10 characters required");
        else tilDescription.setError(null);

        if (!exp.isEmpty() && !expOk) tilExperience.setError("Experience must be 1-50");
        else tilExperience.setError(null);

        // Required field indicators for others (optional visual feedback)
        if (bName.isEmpty()) tilBusinessName.setHelperText("Required *"); else tilBusinessName.setHelperText(null);
        if (addr.isEmpty()) tilAddress.setHelperText("Required *"); else tilAddress.setHelperText(null);

        // Visibility-based errors
        tvIdError.setVisibility(idOk ? View.GONE : View.VISIBLE);
        tvLocationError.setVisibility(locOk ? View.GONE : View.VISIBLE);
        if (!locOk) tvLocationError.setText("Please set location with GPS to continue");
        else tvLocationError.setText("");

        boolean isValid = bNameOk && phoneOk && pinOk && expOk && descOk && addrOk && catOk && locOk && idOk && termsOk;
        
        btnSubmit.setEnabled(isValid);
        btnSubmit.setAlpha(isValid ? 1f : 0.5f);
    }

    private void setupImagePickers() {
        layoutIdProof.setOnClickListener(v -> idProofPicker.launch(new String[]{"image/*"}));
        layoutBizImage.setOnClickListener(v -> businessImgPicker.launch(new String[]{"image/*"}));
    }

    private void setupSubmitButton(FirebaseUser user) {
        btnSubmit.setOnClickListener(v -> {
            if (!btnSubmit.isEnabled()) {
                Toast.makeText(this, "Please complete all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!cbTerms.isChecked()) {
                Toast.makeText(this, "Accept terms to continue", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isSubmitting) return;
            isSubmitting = true;
            showLoading(true, "Uploading ID Proof...");
            
            SupabaseStorageHelper.uploadFile(this, idProofUri, SupabaseStorageHelper.SUPABASE_BUCKET_ID_PROOF, user.getUid(), new SupabaseStorageHelper.UploadCallback() {
                @Override public void onSuccess(String idUrl) {
                    idProofUrl = idUrl;
                    if (businessImgUri != null) {
                        runOnUiThread(() -> tvLoadingMsg.setText("Uploading business image..."));
                        SupabaseStorageHelper.uploadFile(ProviderApplicationActivity.this, businessImgUri, SupabaseStorageHelper.SUPABASE_BUCKET_BUSINESS_IMG, user.getUid(), new SupabaseStorageHelper.UploadCallback() {
                            @Override public void onSuccess(String bizUrl) { businessImgUrl = bizUrl; finalSubmit(user); }
                            @Override public void onError(String msg) { isSubmitting = false; showLoading(false, ""); showError("Image upload failed: " + msg); }
                        });
                    } else {
                        finalSubmit(user);
                    }
                }
                @Override public void onError(String msg) { isSubmitting = false; showLoading(false, ""); showError("ID upload failed: " + msg); }
            });
        });
    }

    private void finalSubmit(FirebaseUser user) {
        runOnUiThread(() -> tvLoadingMsg.setText("Finalizing application..."));
        ProviderApplicationRepository.submitApplication(
                user.getUid(), str(etOwnerName), user.getEmail(), str(etPhone), str(etBusinessName),
                str(actCategory), str(etDescription), "", "", selectedArea, "",
                "", str(etExperience), businessImgUrl, idProofUrl, selectedLat, selectedLng,
                new ProviderApplicationRepository.Callback() {
                    @Override public void onSuccess() { isSubmitting = false; showLoading(false, ""); successOverlay.setVisibility(View.VISIBLE); }
                    @Override public void onError(String msg) { isSubmitting = false; showLoading(false, ""); showError("Submission failed: " + msg); }
                });
    }

    private void updateStepIndicators(int step) {
        if (tvStepIndicator1 == null) return;
        int active = R.drawable.bg_step_circle_active;
        int inactive = R.drawable.bg_step_circle_inactive;
        tvStepIndicator1.setBackgroundResource(step >= 1 ? active : inactive);
        tvStepIndicator2.setBackgroundResource(step >= 2 ? active : inactive);
        tvStepIndicator3.setBackgroundResource(step >= 3 ? active : inactive);
    }

    private void checkExistingApplication(String uid) {
        ProviderApplicationRepository.checkApplicationStatus(uid, new ProviderApplicationRepository.StatusCallback() {
            @Override public void onFound(String status) {
                if ("approved".equals(status)) { startActivity(new Intent(ProviderApplicationActivity.this, ProviderDashboardActivity.class)); finish(); }
                else if ("pending".equals(status)) { findViewById(R.id.layoutStatusBadge).setVisibility(View.VISIBLE); btnSubmit.setEnabled(false); btnSubmit.setText("Pending Review"); }
            }
            @Override public void onNotFound() {}
            @Override public void onError(String m) {}
        });
    }

    private String str(android.widget.TextView tv) { return (tv != null && tv.getText() != null) ? tv.getText().toString().trim() : ""; }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private void showError(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    private void showLoading(boolean show, String msg) { loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE); tvLoadingMsg.setText(msg); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) fetchLocation();
            else showPermissionDeniedDialog();
        }
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this).setTitle("Permission Required").setMessage("Location permission is mandatory to set business location.")
                .setPositiveButton("Grant", (d, w) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100))
                .setNegativeButton("Cancel", null).show();
    }
}
