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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * ProviderApplicationActivity — "Become a Service Provider" form.
 *
 * Validators:
 *  • Business Name  : letters/spaces only (no digits, no special chars)
 *  • Phone          : exactly 10 digits
 *  • Experience     : numbers only, 1–99
 *  • Email          : pre-filled from account, read-only
 *  • Terms          : must scroll to bottom first, then tick checkbox
 */
public class ProviderApplicationActivity extends BaseActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout  layoutBusinessName, layoutOwnerName, layoutPhone,
                             layoutEmail, layoutCategory, layoutCity,
                             layoutExperience, layoutDescription;
    private TextInputEditText etBusinessName, etOwnerName, etPhone,
                              etEmail, etCity, etExperience, etDescription;
    private AutoCompleteTextView spinnerCategory;
    private ScrollView       termsScrollView;
    private View             tvScrollHint;   // now a LinearLayout in XML
    private MaterialCheckBox checkboxTerms;
    private MaterialButton   btnSubmit, btnSuccessHome;
    private FrameLayout      successOverlay;
    private LinearLayout     layoutStatusBadge;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean termsScrolledToBottom = false;
    private boolean isSubmitting          = false;

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
        checkExistingApplication(user.getUid());
    }

    // ── Toolbar ───────────────────────────────────────────────────────────
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Bind views ────────────────────────────────────────────────────────
    private void bindViews() {
        layoutBusinessName = findViewById(R.id.layoutBusinessName);
        layoutOwnerName    = findViewById(R.id.layoutOwnerName);
        layoutPhone        = findViewById(R.id.layoutPhone);
        layoutEmail        = findViewById(R.id.layoutEmail);
        layoutCategory     = findViewById(R.id.layoutCategory);
        layoutCity         = findViewById(R.id.layoutCity);
        layoutExperience   = findViewById(R.id.layoutExperience);
        layoutDescription  = findViewById(R.id.layoutDescription);

        etBusinessName = findViewById(R.id.etBusinessName);
        etOwnerName    = findViewById(R.id.etOwnerName);
        etPhone        = findViewById(R.id.etPhone);
        etEmail        = findViewById(R.id.etEmail);
        spinnerCategory= findViewById(R.id.spinnerCategory);
        etCity         = findViewById(R.id.etCity);
        etExperience   = findViewById(R.id.etExperience);
        etDescription  = findViewById(R.id.etDescription);

        termsScrollView   = findViewById(R.id.termsScrollView);
        tvScrollHint      = findViewById(R.id.tvScrollHint);
        checkboxTerms     = findViewById(R.id.checkboxTerms);
        btnSubmit         = findViewById(R.id.btnSubmit);
        successOverlay    = findViewById(R.id.successOverlay);
        btnSuccessHome    = findViewById(R.id.btnSuccessHome);
        layoutStatusBadge = findViewById(R.id.layoutStatusBadge);
    }

    // ── Category Dropdown ─────────────────────────────────────────────────
    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, CATEGORIES);
        spinnerCategory.setAdapter(adapter);
        spinnerCategory.setOnItemClickListener((parent, view, position, id) ->
                updateSubmitButtonState());
    }

    // ── Pre-fill from Firebase ────────────────────────────────────────────
    private void prefillFromFirebase(FirebaseUser user) {
        // Email — read-only, from account
        if (user.getEmail() != null) {
            etEmail.setText(user.getEmail());
        }
        etEmail.setFocusable(false);
        etEmail.setFocusableInTouchMode(false);
        etEmail.setClickable(false);
        etEmail.setCursorVisible(false);

        // Owner name pre-fill if available
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            etOwnerName.setText(user.getDisplayName());
        }
    }

    // ── Terms scroll detection ────────────────────────────────────────────
    private void setupTermsScrollDetection() {
        termsScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (termsScrolledToBottom) return;
            View child = termsScrollView.getChildAt(0);
            if (child == null) return;
            int diff = child.getHeight()
                    - (termsScrollView.getHeight() + termsScrollView.getScrollY());
            if (diff <= 8) {
                termsScrolledToBottom = true;
                checkboxTerms.setEnabled(true);
                if (tvScrollHint != null) {
                    // Change banner to "all read" success style
                    tvScrollHint.setBackgroundColor(0xFFE8F5E9);
                }
                updateSubmitButtonState();
            }
        });
    }

    // ── Text watchers ─────────────────────────────────────────────────────
    private void setupFieldWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateSubmitButtonState(); }
        };
        etBusinessName.addTextChangedListener(watcher);
        etOwnerName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etCity.addTextChangedListener(watcher);
        etExperience.addTextChangedListener(watcher);
        etDescription.addTextChangedListener(watcher);
        checkboxTerms.setOnCheckedChangeListener((btn, checked) -> updateSubmitButtonState());
    }

    // ── Real-time submit button enablement ────────────────────────────────
    private void updateSubmitButtonState() {
        String phone = str(etPhone);
        String exp   = str(etExperience);
        String biz   = str(etBusinessName);

        boolean phoneOk = phone.matches("\\d{10}");
        boolean expOk   = exp.matches("\\d{1,2}") && !exp.equals("0");
        boolean bizOk   = biz.matches("[a-zA-Z\\s.&'-]+") && !biz.isEmpty();

        boolean allFilled = bizOk
                && isNotEmpty(etOwnerName)
                && phoneOk
                && isNotEmpty(etEmail)
                && isNotEmpty(spinnerCategory)
                && isNotEmpty(etCity)
                && expOk
                && isNotEmpty(etDescription);

        boolean ready = allFilled && checkboxTerms.isChecked();
        btnSubmit.setEnabled(ready);
        btnSubmit.setAlpha(ready ? 1f : 0.5f);
    }

    // ── Submit ────────────────────────────────────────────────────────────
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
                    "",
                    str(etCity),
                    str(etExperience),
                    null,
                    null,
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
                                showStatusBadge();
                            } else if ("NETWORK_ERROR".equals(message)) {
                                showError(getString(R.string.error_network_retry));
                            } else {
                                showError("Submission failed: " + message);
                            }
                        }
                    });
        });
    }

    // ── Full validation (on submit tap) ───────────────────────────────────
    private boolean validateAllFields() {
        boolean valid = true;

        // Business Name — letters/spaces/basic punctuation only
        String biz = str(etBusinessName);
        if (biz.isEmpty()) {
            layoutBusinessName.setError("Business name is required.");
            valid = false;
        } else if (!biz.matches("[a-zA-Z\\s.&'-]+")) {
            layoutBusinessName.setError("Business name must contain letters only.");
            valid = false;
        } else {
            layoutBusinessName.setError(null);
        }

        // Owner Name
        if (str(etOwnerName).isEmpty()) {
            layoutOwnerName.setError("Owner name is required.");
            valid = false;
        } else {
            layoutOwnerName.setError(null);
        }

        // Phone — exactly 10 digits
        String phone = str(etPhone);
        if (phone.isEmpty()) {
            layoutPhone.setError("Phone number is required.");
            valid = false;
        } else if (!phone.matches("\\d{10}")) {
            layoutPhone.setError("Enter a valid 10-digit mobile number.");
            valid = false;
        } else {
            layoutPhone.setError(null);
        }

        // Email (read-only, should always be filled)
        if (str(etEmail).isEmpty()) {
            layoutEmail.setError("Email is required.");
            valid = false;
        } else {
            layoutEmail.setError(null);
        }

        // Category
        if (str(spinnerCategory).isEmpty()) {
            layoutCategory.setError("Please select a service category.");
            valid = false;
        } else {
            layoutCategory.setError(null);
        }

        // City
        if (str(etCity).isEmpty()) {
            layoutCity.setError("City is required.");
            valid = false;
        } else {
            layoutCity.setError(null);
        }

        // Years of Experience — numbers only, 1–99
        String exp = str(etExperience);
        if (exp.isEmpty()) {
            layoutExperience.setError("Years of experience is required.");
            valid = false;
        } else if (!exp.matches("\\d{1,2}") || exp.equals("0")) {
            layoutExperience.setError("Enter a valid number (1–99).");
            valid = false;
        } else {
            layoutExperience.setError(null);
        }

        // Description
        if (str(etDescription).isEmpty()) {
            layoutDescription.setError("Please describe your services.");
            valid = false;
        } else {
            layoutDescription.setError(null);
        }

        // Terms
        if (!checkboxTerms.isChecked()) {
            showError("Please read and accept the Terms & Conditions.");
            valid = false;
        }

        return valid;
    }

    // ── Existing application check ────────────────────────────────────────
    private void checkExistingApplication(String uid) {
        ProviderApplicationRepository.checkApplicationStatus(uid,
                new ProviderApplicationRepository.StatusCallback() {
                    @Override public void onFound(String status) {
                        if ("approved".equals(status)) {
                            Toast.makeText(ProviderApplicationActivity.this,
                                    "Your application is already approved!", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(ProviderApplicationActivity.this,
                                    ProviderDashboardActivity.class));
                            finish();
                        } else if ("pending".equals(status)) {
                            showStatusBadge();
                            disableFormForResubmission();
                        }
                    }
                    @Override public void onNotFound() { /* form ready */ }
                    @Override public void onError(String message) { /* non-fatal */ }
                });
    }

    private void showStatusBadge() {
        if (layoutStatusBadge != null) layoutStatusBadge.setVisibility(View.VISIBLE);
    }

    private void disableFormForResubmission() {
        btnSubmit.setEnabled(false);
        btnSubmit.setText("Application Pending Review");
        btnSubmit.setAlpha(0.6f);
    }

    // ── Success overlay ───────────────────────────────────────────────────
    private void showSuccessOverlay() {
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setAlpha(0f);
        successOverlay.animate().alpha(1f).setDuration(350).start();
    }

    private void setupSuccessButton() {
        btnSuccessHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private String str(android.widget.TextView tv) {
        return (tv != null && tv.getText() != null) ? tv.getText().toString().trim() : "";
    }

    private boolean isNotEmpty(android.widget.TextView tv) {
        return !str(tv).isEmpty();
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

