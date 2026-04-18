package com.example.quickfix;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Locale;

public class HelpSupportActivity extends BaseActivity {

    private TextInputLayout tilIssueSubject;
    private TextInputLayout tilIssueDescription;
    private TextInputEditText etIssueSubject;
    private TextInputEditText etIssueDescription;
    private MaterialButton btnCallSupport;
    private MaterialButton btnEmailSupport;
    private MaterialButton btnSubmitIssue;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        bindViews();
        setupToolbar();
        setupActions();
        setupValidationClear();
    }

    private void bindViews() {
        tilIssueSubject = findViewById(R.id.tilIssueSubject);
        tilIssueDescription = findViewById(R.id.tilIssueDescription);
        etIssueSubject = findViewById(R.id.etIssueSubject);
        etIssueDescription = findViewById(R.id.etIssueDescription);
        btnCallSupport = findViewById(R.id.btnCallSupport);
        btnEmailSupport = findViewById(R.id.btnEmailSupport);
        btnSubmitIssue = findViewById(R.id.btnSubmitIssue);
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarHelpSupport);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void setupActions() {
        if (btnCallSupport != null) {
            btnCallSupport.setOnClickListener(v -> openDialer());
        }
        if (btnEmailSupport != null) {
            btnEmailSupport.setOnClickListener(v -> composeSupportEmail(
                    getString(R.string.help_support_contact_email_subject),
                    getString(R.string.help_support_contact_email_body)
            ));
        }
        if (btnSubmitIssue != null) {
            btnSubmitIssue.setOnClickListener(v -> submitIssueReport());
        }
    }

    private void setupValidationClear() {
        TextWatcher clearErrorsWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearErrors();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        if (etIssueSubject != null) {
            etIssueSubject.addTextChangedListener(clearErrorsWatcher);
        }
        if (etIssueDescription != null) {
            etIssueDescription.addTextChangedListener(clearErrorsWatcher);
        }
    }

    private void openDialer() {
        String supportPhone = getString(R.string.help_support_phone_value);
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + supportPhone));
        startActivitySafely(dialIntent, getString(R.string.help_support_no_phone_app));
    }

    private void submitIssueReport() {
        clearErrors();

        String subject = clean(etIssueSubject != null ? etIssueSubject.getText() : null);
        String description = clean(etIssueDescription != null ? etIssueDescription.getText() : null);

        boolean hasError = false;
        if (subject.isEmpty()) {
            hasError = true;
            if (tilIssueSubject != null) {
                tilIssueSubject.setError(getString(R.string.help_support_error_subject_required));
            }
        }

        if (description.isEmpty()) {
            hasError = true;
            if (tilIssueDescription != null) {
                tilIssueDescription.setError(getString(R.string.help_support_error_description_required));
            }
        } else if (description.length() < 10) {
            hasError = true;
            if (tilIssueDescription != null) {
                tilIssueDescription.setError(getString(R.string.help_support_error_description_short));
            }
        }

        if (hasError) {
            return;
        }

        String issueBody = buildIssueEmailBody(description);
        String issueSubject = String.format(Locale.US, "%s: %s",
                getString(R.string.help_support_issue_email_subject_prefix),
                subject);

        composeSupportEmail(issueSubject, issueBody);
    }

    private String buildIssueEmailBody(String description) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = user != null ? safe(user.getUid()) : "";
        String email = user != null ? safe(user.getEmail()) : "";

        return description + "\n\n---\n"
                + "App: QuickFix Android\n"
                + "User UID: " + (uid.isEmpty() ? "N/A" : uid) + "\n"
                + "User Email: " + (email.isEmpty() ? "N/A" : email);
    }

    private void composeSupportEmail(String subject, String body) {
        String supportEmail = getString(R.string.help_support_email_value);
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{supportEmail});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            startActivity(Intent.createChooser(emailIntent, getString(R.string.help_support_email_chooser)));
            Toast.makeText(this, R.string.help_support_issue_success, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.help_support_no_email_app, Toast.LENGTH_LONG).show();
        }
    }

    private void startActivitySafely(Intent intent, String errorMessage) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void clearErrors() {
        if (tilIssueSubject != null) {
            tilIssueSubject.setError(null);
            tilIssueSubject.setErrorEnabled(false);
        }
        if (tilIssueDescription != null) {
            tilIssueDescription.setError(null);
            tilIssueDescription.setErrorEnabled(false);
        }
    }

    private String clean(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

