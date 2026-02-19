package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * ForgotPasswordActivity — full backend-driven forgot password flow.
 *
 * Step 1: Enter email → POST /api/auth/forgot-password → real OTP email sent.
 * Step 2: OTPVerificationActivity launched → user enters OTP →
 *         POST /api/auth/verify-otp (purpose=password_reset).
 * Step 3: On OTP verified → show reset-password fields →
 *         POST /api/auth/reset-password → success → LoginActivity.
 *
 * NO demo OTP. NO local random OTP. Everything goes through the backend.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────
    private ImageView         backButton;
    private LinearLayout      emailSection, resetPasswordSection;
    private TextInputLayout   emailLayoutForgot, newPasswordLayout, confirmPasswordLayout;
    private TextInputEditText emailInputForgot, newPasswordInput, confirmPasswordInput;
    private MaterialButton    sendOtpButton, resetPasswordButton;
    private TextView          backToLoginLink;

    // ── State ─────────────────────────────────────────────────────────────
    private String userEmail;
    private String verifiedOtp;   // returned from OTPVerificationActivity

    // ── OTP screen launcher ───────────────────────────────────────────────
    private ActivityResultLauncher<Intent> otpLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        initializeViews();
        registerOtpLauncher();
        setupClickListeners();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────────────────────────────────

    private void initializeViews() {
        backButton           = findViewById(R.id.backButton);
        emailSection         = findViewById(R.id.emailSection);
        resetPasswordSection = findViewById(R.id.resetPasswordSection);

        emailLayoutForgot    = findViewById(R.id.emailLayoutForgot);
        newPasswordLayout    = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout= findViewById(R.id.confirmPasswordLayout);

        emailInputForgot     = findViewById(R.id.emailInputForgot);
        newPasswordInput     = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        sendOtpButton        = findViewById(R.id.sendOtpButton);
        resetPasswordButton  = findViewById(R.id.resetPasswordButton);
        backToLoginLink      = findViewById(R.id.backToLoginLink);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Register OTP result launcher
    // ─────────────────────────────────────────────────────────────────────

    private void registerOtpLauncher() {
        otpLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handleOtpResult
        );
    }

    private void handleOtpResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
            boolean verified = result.getData().getBooleanExtra("verified", false);
            verifiedOtp      = result.getData().getStringExtra("otp");

            if (verified && verifiedOtp != null) {
                // OTP confirmed — show the new-password form
                showResetPasswordSection();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Click listeners
    // ─────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (backButton != null)
            backButton.setOnClickListener(v -> finish());

        if (sendOtpButton != null)
            sendOtpButton.setOnClickListener(v -> sendOTP());

        if (resetPasswordButton != null)
            resetPasswordButton.setOnClickListener(v -> resetPassword());

        if (backToLoginLink != null)
            backToLoginLink.setOnClickListener(v -> {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 1 — Send OTP via backend POST /api/auth/forgot-password
    // ─────────────────────────────────────────────────────────────────────

    private void sendOTP() {
        userEmail = emailInputForgot.getText() != null
                ? emailInputForgot.getText().toString().trim() : "";

        emailLayoutForgot.setError(null);

        if (TextUtils.isEmpty(userEmail)) {
            emailLayoutForgot.setError("Email is required.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            emailLayoutForgot.setError("Enter a valid email address.");
            return;
        }

        sendOtpButton.setEnabled(false);
        sendOtpButton.setText("Sending OTP...");

        try {
            JSONObject body = new JSONObject();
            body.put("email", userEmail);

            ApiClient.post("/api/auth/forgot-password", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    sendOtpButton.setEnabled(true);
                    sendOtpButton.setText(getString(R.string.send_otp));

                    // Backend sends the real OTP email silently
                    // Launch OTP verification screen
                    Intent intent = new Intent(ForgotPasswordActivity.this,
                            OTPVerificationActivity.class);
                    intent.putExtra("email",   userEmail);
                    intent.putExtra("purpose", "password_reset");
                    otpLauncher.launch(intent);
                }

                @Override
                public void onError(String errorMessage) {
                    sendOtpButton.setEnabled(true);
                    sendOtpButton.setText(getString(R.string.send_otp));
                    emailLayoutForgot.setError(errorMessage);
                    Toast.makeText(ForgotPasswordActivity.this,
                            errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            sendOtpButton.setEnabled(true);
            sendOtpButton.setText(getString(R.string.send_otp));
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 2 — Show reset-password section
    // ─────────────────────────────────────────────────────────────────────

    private void showResetPasswordSection() {
        if (emailSection         != null) emailSection.setVisibility(View.GONE);
        if (resetPasswordSection != null) resetPasswordSection.setVisibility(View.VISIBLE);
        Toast.makeText(this,
                "OTP verified! Please enter your new password.", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 3 — Reset password via backend POST /api/auth/reset-password
    // ─────────────────────────────────────────────────────────────────────

    private void resetPassword() {
        String newPassword     = newPasswordInput     != null && newPasswordInput.getText()     != null
                ? newPasswordInput.getText().toString().trim()     : "";
        String confirmPassword = confirmPasswordInput != null && confirmPasswordInput.getText() != null
                ? confirmPasswordInput.getText().toString().trim() : "";

        if (newPasswordLayout     != null) newPasswordLayout.setError(null);
        if (confirmPasswordLayout != null) confirmPasswordLayout.setError(null);

        if (TextUtils.isEmpty(newPassword)) {
            if (newPasswordLayout != null) newPasswordLayout.setError("Password is required.");
            return;
        }
        if (newPassword.length() < 8) {
            if (newPasswordLayout != null)
                newPasswordLayout.setError("Password must be at least 8 characters.");
            return;
        }
        if (!newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*")
                || !newPassword.matches(".*\\d.*")) {
            if (newPasswordLayout != null)
                newPasswordLayout.setError("Password needs uppercase, lowercase, and a digit.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            if (confirmPasswordLayout != null)
                confirmPasswordLayout.setError("Passwords do not match.");
            return;
        }
        if (verifiedOtp == null) {
            Toast.makeText(this, "OTP not verified. Please start over.", Toast.LENGTH_LONG).show();
            return;
        }

        if (resetPasswordButton != null) {
            resetPasswordButton.setEnabled(false);
            resetPasswordButton.setText("Resetting...");
        }

        try {
            JSONObject body = new JSONObject();
            body.put("email",        userEmail);
            body.put("otp",          verifiedOtp);
            body.put("new_password", newPassword);

            ApiClient.post("/api/auth/reset-password", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Toast.makeText(ForgotPasswordActivity.this,
                            "Password reset successfully! Please log in.",
                            Toast.LENGTH_LONG).show();

                    Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onError(String errorMessage) {
                    if (resetPasswordButton != null) {
                        resetPasswordButton.setEnabled(true);
                        resetPasswordButton.setText(getString(R.string.reset_password));
                    }
                    Toast.makeText(ForgotPasswordActivity.this,
                            errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            if (resetPasswordButton != null) {
                resetPasswordButton.setEnabled(true);
                resetPasswordButton.setText(getString(R.string.reset_password));
            }
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
