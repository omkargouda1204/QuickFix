package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AlertDialog;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.annotation.NonNull;
import java.util.List;
import android.location.Geocoder;
import android.location.Address;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.widget.TextView;
import android.util.Log;
import java.util.Locale;

/**
 * ProviderApplicationActivity — "Become a Service Provider" form.
 *
 * Requirements:
 *  • Business Location: Set via GPS, converts to area name.
 *  • Terms & Conditions: Must scroll to bottom to enable checkbox.
 *  • Validation: Ensure all fields including location are set.
 */
public class ProviderApplicationActivity extends BaseActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout  layoutBusinessName, layoutOwnerName, layoutPhone,
                             layoutEmail, layoutCategory,
                             layoutExperience, layoutDescription;
    private TextInputEditText etBusinessName, etOwnerName, etPhone,
                              etEmail, etExperience, etDescription;
    private AutoCompleteTextView spinnerCategory;
    private NestedScrollView termsScrollView;
    private View             tvScrollHint;
    private MaterialCheckBox checkboxTerms;
    private MaterialButton   btnSubmit, btnSuccessHome, btnSetBusinessLocation;
    private FrameLayout      successOverlay;
    private LinearLayout     layoutStatusBadge, layoutLocationDisplay;
    private TextView         tvSelectedLocationLabel, tvStepIndicator1, tvStepIndicator2, tvStepIndicator3;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean termsScrolledToBottom = false;
    private boolean isSubmitting          = false;

    private double             selectedLat = Double.NaN;
    private double             selectedLng = Double.NaN;
    private String             selectedCity = "";
    private String             selectedArea = "";
    private AppLocationManager locationManager;

    private static final String[] CATEGORIES = {
            "Plumbing", "Electrical", "Cleaning", "Repair & Maintenance",
            "AC Service", "Painting", "Carpentry", "Pest Control",
            "Appliance Repair", "Security Systems", "Landscaping", "Other"
    };

    // ═══════════════════════════════════════════════════════════════════════
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
        setupCategoryDropdown();
        prefillFromFirebase(user);
        setupTermsScrollDetection();
        setupFieldWatchers();
        setupSubmitButton(user);
        setupSuccessButton();
        setupLocationLogic();
        checkExistingApplication(user.getUid());
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
        layoutBusinessName = findViewById(R.id.layoutBusinessName);
        layoutOwnerName    = findViewById(R.id.layoutOwnerName);
        layoutPhone        = findViewById(R.id.layoutPhone);
        layoutEmail        = findViewById(R.id.layoutEmail);
        layoutCategory     = findViewById(R.id.layoutCategory);
        layoutExperience   = findViewById(R.id.layoutExperience);
        layoutDescription  = findViewById(R.id.layoutDescription);

        etBusinessName = findViewById(R.id.etBusinessName);
        etOwnerName    = findViewById(R.id.etOwnerName);
        etPhone        = findViewById(R.id.etPhone);
        etEmail        = findViewById(R.id.etEmail);
        spinnerCategory= findViewById(R.id.spinnerCategory);
        etExperience   = findViewById(R.id.etExperience);
        etDescription  = findViewById(R.id.etDescription);

        termsScrollView   = findViewById(R.id.termsScrollView);
        tvScrollHint      = findViewById(R.id.tvScrollHint);
        checkboxTerms     = findViewById(R.id.checkboxTerms);
        btnSubmit         = findViewById(R.id.btnSubmit);
        successOverlay    = findViewById(R.id.successOverlay);
        btnSuccessHome    = findViewById(R.id.btnSuccessHome);
        layoutStatusBadge = findViewById(R.id.layoutStatusBadge);

        btnSetBusinessLocation = findViewById(R.id.btnSetBusinessLocation);
        layoutLocationDisplay = findViewById(R.id.layoutLocationDisplay);
        tvSelectedLocationLabel = findViewById(R.id.tvSelectedLocationLabel);

        tvStepIndicator1   = findViewById(R.id.tvStepIndicator1);
        tvStepIndicator2   = findViewById(R.id.tvStepIndicator2);
        tvStepIndicator3   = findViewById(R.id.tvStepIndicator3);
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
                    
                    // Convert lat/lng to area/city using Geocoder (Req 2)
                    resolveAddress(lat, lng);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    showLoading(false, "");
                    showError("Location failed: " + error + ". Please ensure GPS is ON.");
                });
            }
        });
    }

    private void resolveAddress(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                selectedCity = addr.getLocality(); // City
                selectedArea = addr.getSubLocality(); // Area
                if (selectedArea == null || selectedArea.isEmpty()) selectedArea = addr.getFeatureName();
                
                String fullAddress = addr.getAddressLine(0);
                
                // Req 2 & 3: Display ONLY area/city name, no Plus Codes
                String display = (selectedArea != null && !selectedArea.isEmpty()) ? selectedArea : selectedCity;
                if (display == null || display.isEmpty()) display = "Unknown Location";

                layoutLocationDisplay.setVisibility(View.VISIBLE);
                tvSelectedLocationLabel.setText(display);
                btnSetBusinessLocation.setText("Change Business Location");
                
                updateStepIndicators(2);
                updateSubmitButtonState();
            }
        } catch (Exception e) {
            Log.e("ProvApp", "Geocode failed", e);
            showError("Could not resolve area name.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Enable location permission to set your business location. This is mandatory for service providers.")
                .setPositiveButton("Grant", (d, w) -> ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStepIndicators(int step) {
        if (tvStepIndicator1 == null) return;
        int activeBg = R.drawable.bg_step_circle_active;
        int inactiveBg = R.drawable.bg_step_circle_inactive;
        int white = getResources().getColor(R.color.white);
        int muted = getResources().getColor(R.color.on_gradient_muted);

        tvStepIndicator1.setBackgroundResource(step >= 1 ? activeBg : inactiveBg);
        tvStepIndicator1.setTextColor(step >= 1 ? white : muted);
        tvStepIndicator2.setBackgroundResource(step >= 2 ? activeBg : inactiveBg);
        tvStepIndicator2.setTextColor(step >= 2 ? white : muted);
        tvStepIndicator3.setBackgroundResource(step >= 3 ? activeBg : inactiveBg);
        tvStepIndicator3.setTextColor(step >= 3 ? white : muted);
    }

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        spinnerCategory.setAdapter(adapter);
        spinnerCategory.setOnItemClickListener((parent, view, position, id) -> {
            String selected = CATEGORIES[position];
            updateHeaderTitle(selected);
            updateSubmitButtonState();
        });
    }

    private void updateHeaderTitle(String category) {
        TextView title = findViewById(R.id.tvHeaderTitle);
        if (title != null) {
            title.setText("Become a " + category + " Provider");
        }
    }

    private void prefillFromFirebase(FirebaseUser user) {
        if (user.getEmail() != null) etEmail.setText(user.getEmail());
        
        // Fetch user name from Supabase users table (Req 4)
        UserRepository.fetchUserByUid(user.getUid(), new UserRepository.UserCallback() {
            @Override
            public void onSuccess(UserModel u) {
                if (u != null && !u.name.isEmpty() && !"Unknown".equals(u.name)) {
                    etOwnerName.setText(u.name);
                }
            }
            @Override
            public void onError(String msg) {
                // Fallback to Google name if Supabase fetch fails
                if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                    etOwnerName.setText(user.getDisplayName());
                }
            }
        });
    }

    private void setupTermsScrollDetection() {
        termsScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (termsScrolledToBottom) return;
            View child = v.getChildAt(0);
            if (child != null) {
                int diff = child.getMeasuredHeight() - (v.getMeasuredHeight() + scrollY);
                if (diff <= 20) { // 20px tolerance
                    termsScrolledToBottom = true;
                    checkboxTerms.setEnabled(true);
                    if (tvScrollHint != null) {
                        tvScrollHint.setBackgroundResource(R.color.success_bg);
                        TextView hintText = (TextView) ((LinearLayout) tvScrollHint).getChildAt(1);
                        hintText.setText("✅ Terms read. You can now accept.");
                        hintText.setTextColor(getResources().getColor(R.color.success_green));
                    }
                    updateSubmitButtonState();
                }
            }
        });
    }

    private void setupFieldWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSubmitButtonState(); }
        };
        etBusinessName.addTextChangedListener(watcher);
        etOwnerName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etExperience.addTextChangedListener(watcher);
        etDescription.addTextChangedListener(watcher);
        checkboxTerms.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) updateStepIndicators(3);
            updateSubmitButtonState();
        });
    }

    private void updateSubmitButtonState() {
        String phone = str(etPhone);
        String exp   = str(etExperience);
        String desc  = str(etDescription);
        String biz   = str(etBusinessName);

        // Req 10: Validation rules
        boolean phoneOk = phone.length() == 10 && phone.matches("\\d{10}");
        boolean expOk   = !exp.isEmpty() && Integer.parseInt(exp) >= 1 && Integer.parseInt(exp) <= 50;
        boolean descOk  = desc.length() >= 10 && desc.length() <= 300;
        boolean bizOk   = !biz.isEmpty() && biz.length() >= 3;
        boolean locOk   = !Double.isNaN(selectedLat);
        boolean categoryOk = !str(spinnerCategory).isEmpty();

        boolean allFilled = bizOk
                && isNotEmpty(etOwnerName)
                && phoneOk
                && categoryOk
                && locOk
                && expOk
                && descOk;

        boolean ready = allFilled && checkboxTerms.isChecked();
        btnSubmit.setEnabled(ready);
        btnSubmit.setAlpha(ready ? 1f : 0.5f);
    }

    private void setupSubmitButton(FirebaseUser user) {
        btnSubmit.setOnClickListener(v -> {
            if (isSubmitting) return;
            if (!validateAllFields()) return;

            isSubmitting = true;
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting…");

            ProviderApplicationRepository.submitApplication(
                    user.getUid(),
                    str(etOwnerName),
                    str(etEmail),
                    str(etPhone),
                    str(etBusinessName),
                    str(spinnerCategory),
                    str(etDescription),
                    "", // address
                    selectedCity,
                    selectedArea,
                    str(etExperience),
                    null, // business image
                    null, // id proof
                    selectedLat,
                    selectedLng,
                    new ProviderApplicationRepository.Callback() {
                        @Override public void onSuccess() {
                            isSubmitting = false;
                            showSuccessOverlay();
                        }
                        @Override public void onError(String message) {
                            isSubmitting = false;
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Submit Application");
                            if ("DUPLICATE".equals(message)) {
                                showError("You already have a pending or approved application.");
                            } else {
                                showError("Submission failed: " + message);
                            }
                        }
                    });
        });
    }

    private boolean validateAllFields() {
        if (str(etBusinessName).isEmpty()) {
            layoutBusinessName.setError("Business name is required.");
            return false;
        }
        if (Double.isNaN(selectedLat)) {
            showError("Please set business location");
            return false;
        }
        if (!checkboxTerms.isChecked()) {
            showError("Please accept the Terms & Conditions.");
            return false;
        }
        return true;
    }

    private void checkExistingApplication(String uid) {
        ProviderApplicationRepository.checkApplicationStatus(uid,
                new ProviderApplicationRepository.StatusCallback() {
                    @Override public void onFound(String status) {
                        if ("approved".equals(status)) {
                            startActivity(new Intent(ProviderApplicationActivity.this, ProviderDashboardActivity.class));
                            finish();
                        } else if ("pending".equals(status)) {
                            layoutStatusBadge.setVisibility(View.VISIBLE);
                            btnSubmit.setEnabled(false);
                            btnSubmit.setText("Application Pending Review");
                        }
                    }
                    @Override public void onNotFound() {}
                    @Override public void onError(String message) {}
                });
    }

    private void showSuccessOverlay() {
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setAlpha(0f);
        successOverlay.animate().alpha(1f).setDuration(350).start();
    }

    private void setupSuccessButton() {
        btnSuccessHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private String str(android.widget.TextView tv) {
        return (tv != null && tv.getText() != null) ? tv.getText().toString().trim() : "";
    }

    private boolean isNotEmpty(android.widget.TextView tv) {
        return !str(tv).isEmpty();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showLoading(boolean show, String message) {
        if (show) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
