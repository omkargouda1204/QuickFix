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
import android.widget.TextView;
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
 * ProviderApplicationActivity — User-facing "Become a Service Provider" form.
 *
 * Key behaviours:
 *  • Terms ScrollView must be scrolled to bottom before checkbox enables.
 *  • Checkbox must be checked AND all fields filled before submit enables.
 *  • Shows loading spinner on submit, prevents double-click.
 *  • Checks for duplicate submission (pending/approved) before inserting.
 *  • Shows success overlay with redirect to MainActivity on close.
 */
public class ProviderApplicationActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout layoutBusinessName, layoutOwnerName, layoutPhone,
            layoutEmail, layoutCategory, layoutCity, layoutExperience, layoutDescription;
    private TextInputEditText etBusinessName, etOwnerName, etPhone,
            etEmail, etCity, etExperience, etDescription;
    private AutoCompleteTextView spinnerCategory;
    private ScrollView termsScrollView;
    private TextView tvScrollHint;
    private MaterialCheckBox checkboxTerms;
    private MaterialButton btnSubmit, btnSuccessHome;
    private FrameLayout successOverlay;
    private LinearLayout layoutStatusBadge;

    // ── State ─────────────────────────────────────────────────────────────
    private boolean termsScrolledToBottom = false;
    private boolean isSubmitting = false;

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;

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

        mAuth = FirebaseAuth.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();
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

        // Check if user already has a pending/approved application
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
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    // ── Bind views ────────────────────────────────────────────────────────
    private void bindViews() {
        layoutBusinessName  = findViewById(R.id.layoutBusinessName);
        layoutOwnerName     = findViewById(R.id.layoutOwnerName);
        layoutPhone         = findViewById(R.id.layoutPhone);
        layoutEmail         = findViewById(R.id.layoutEmail);
        layoutCategory      = findViewById(R.id.layoutCategory);
        layoutCity          = findViewById(R.id.layoutCity);
        layoutExperience    = findViewById(R.id.layoutExperience);
        layoutDescription   = findViewById(R.id.layoutDescription);

        etBusinessName  = findViewById(R.id.etBusinessName);
        etOwnerName     = findViewById(R.id.etOwnerName);
        etPhone         = findViewById(R.id.etPhone);
        etEmail         = findViewById(R.id.etEmail);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        etCity          = findViewById(R.id.etCity);
        etExperience    = findViewById(R.id.etExperience);
        etDescription   = findViewById(R.id.etDescription);

        termsScrollView  = findViewById(R.id.termsScrollView);
        tvScrollHint     = findViewById(R.id.tvScrollHint);
        checkboxTerms    = findViewById(R.id.checkboxTerms);
        btnSubmit        = findViewById(R.id.btnSubmit);
        successOverlay   = findViewById(R.id.successOverlay);
        btnSuccessHome   = findViewById(R.id.btnSuccessHome);
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

    // ── Pre-fill email & name from Firebase ───────────────────────────────
    private void prefillFromFirebase(FirebaseUser user) {
        if (user.getEmail() != null) etEmail.setText(user.getEmail());
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
                // User has scrolled to (or near) the bottom
                termsScrolledToBottom = true;
                checkboxTerms.setEnabled(true);
                tvScrollHint.setVisibility(View.GONE);
                tvScrollHint.setText("✅  Thank you for reading the terms.");
                tvScrollHint.setVisibility(View.VISIBLE);
                tvScrollHint.setBackground(null);
                TextView hint = tvScrollHint;
                hint.setBackgroundResource(0);
                hint.setBackgroundColor(0xFFE8F5E9);
                hint.setTextColor(0xFF1B5E20);
            }
        });
    }

    // ── Text watchers for all fields ──────────────────────────────────────
    private void setupFieldWatchers() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateSubmitButtonState(); }
        };

        etBusinessName.addTextChangedListener(watcher);
        etOwnerName.addTextChangedListener(watcher);
        etPhone.addTextChangedListener(watcher);
        etEmail.addTextChangedListener(watcher);
        etCity.addTextChangedListener(watcher);
        etExperience.addTextChangedListener(watcher);
        etDescription.addTextChangedListener(watcher);

        checkboxTerms.setOnCheckedChangeListener((btn, checked) -> updateSubmitButtonState());
    }

    private void updateSubmitButtonState() {
        boolean allFilled = isNotEmpty(etBusinessName)
                && isNotEmpty(etOwnerName)
                && isNotEmpty(etPhone)
                && isNotEmpty(etEmail)
                && isNotEmpty(spinnerCategory)
                && isNotEmpty(etCity)
                && isNotEmpty(etExperience)
                && isNotEmpty(etDescription);

        boolean ready = allFilled && checkboxTerms.isChecked();
        btnSubmit.setEnabled(ready);
        btnSubmit.setAlpha(ready ? 1f : 0.5f);
    }

    private boolean isNotEmpty(TextView tv) {
        return tv.getText() != null && !tv.getText().toString().trim().isEmpty();
    }

    // ── Submit ────────────────────────────────────────────────────────────
    private void setupSubmitButton(FirebaseUser user) {
        btnSubmit.setOnClickListener(v -> {
            if (isSubmitting) return;
            if (!validateFields()) return;

            isSubmitting = true;
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Submitting…");

            String uid          = user.getUid();
            String name         = etOwnerName.getText().toString().trim();
            String email        = etEmail.getText().toString().trim();
            String phone        = etPhone.getText().toString().trim();
            String businessName = etBusinessName.getText().toString().trim();
            String category     = spinnerCategory.getText().toString().trim();
            String description  = etDescription.getText().toString().trim();
            String city         = etCity.getText().toString().trim();
            String experience   = etExperience.getText().toString().trim();

            ProviderApplicationRepository.submitApplication(
                    uid, name, email, phone,
                    businessName, category, description,
                    "",   // address (not collected separately in this form)
                    city, experience,
                    null, // businessImageUrl
                    null, // idProofUrl
                    new ProviderApplicationRepository.Callback() {
                        @Override
                        public void onSuccess() {
                            isSubmitting = false;
                            showSuccessOverlay();
                        }

                        @Override
                        public void onError(String message) {
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

    // ── Field Validation ──────────────────────────────────────────────────
    private boolean validateFields() {
        boolean valid = true;

        if (isNotEmpty(etBusinessName)) {
            layoutBusinessName.setError(null);
        } else {
            layoutBusinessName.setError(getString(R.string.error_business_name_required));
            valid = false;
        }

        if (isNotEmpty(etOwnerName)) {
            layoutOwnerName.setError(null);
        } else {
            layoutOwnerName.setError(getString(R.string.error_owner_name_required));
            valid = false;
        }

        if (isNotEmpty(etPhone)) {
            layoutPhone.setError(null);
        } else {
            layoutPhone.setError(getString(R.string.error_phone_required));
            valid = false;
        }

        if (isNotEmpty(etEmail)) {
            layoutEmail.setError(null);
        } else {
            layoutEmail.setError(getString(R.string.error_email_required));
            valid = false;
        }

        if (isNotEmpty(spinnerCategory)) {
            layoutCategory.setError(null);
        } else {
            layoutCategory.setError(getString(R.string.error_category_required));
            valid = false;
        }

        if (isNotEmpty(etCity)) {
            layoutCity.setError(null);
        } else {
            layoutCity.setError(getString(R.string.error_city_required));
            valid = false;
        }

        if (isNotEmpty(etExperience)) {
            layoutExperience.setError(null);
        } else {
            layoutExperience.setError(getString(R.string.error_experience_required));
            valid = false;
        }

        if (isNotEmpty(etDescription)) {
            layoutDescription.setError(null);
        } else {
            layoutDescription.setError(getString(R.string.error_description_required));
            valid = false;
        }

        if (!checkboxTerms.isChecked()) {
            showError(getString(R.string.error_terms_not_accepted));
            valid = false;
        }

        return valid;
    }

    // ── Existing application check ────────────────────────────────────────
    private void checkExistingApplication(String uid) {
        ProviderApplicationRepository.checkApplicationStatus(uid,
                new ProviderApplicationRepository.StatusCallback() {
                    @Override
                    public void onFound(String status) {
                        if ("approved".equals(status)) {
                            // Already a provider — redirect to provider dashboard
                            Toast.makeText(ProviderApplicationActivity.this,
                                    "Your application is already approved!", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(ProviderApplicationActivity.this,
                                    ProviderDashboardActivity.class));
                            finish();
                        } else if ("pending".equals(status)) {
                            // Show pending status badge & disable form
                            showStatusBadge();
                            disableFormForResubmission();
                        }
                        // rejected → allow resubmission (form stays enabled)
                    }

                    @Override
                    public void onNotFound() { /* No previous application — form ready */ }

                    @Override
                    public void onError(String message) {
                        // Non-fatal — let user proceed
                    }
                });
    }

    private void showStatusBadge() {
        if (layoutStatusBadge != null)
            layoutStatusBadge.setVisibility(View.VISIBLE);
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
        successOverlay.animate().alpha(1f).setDuration(300).start();
    }

    private void setupSuccessButton() {
        btnSuccessHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

