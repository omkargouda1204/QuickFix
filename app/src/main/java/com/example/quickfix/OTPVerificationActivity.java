package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * OTPVerificationActivity — verifies OTP by calling backend /api/auth/verify-otp.
 *
 * NO demo OTP. NO local comparison.
 * The 6-digit OTP is emailed by the backend via real Gmail SMTP.
 * This screen sends the entered OTP to the backend for server-side verification.
 *
 * Extras expected from caller:
 *   "email"   — the email address the OTP was sent to
 *   "purpose" — "verification" | "password_reset"
 */
public class OTPVerificationActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────
    private TextInputLayout   otpSignupLayout;
    private TextInputEditText otpSignupInput;
    private MaterialButton    verifyButton;
    private TextView          emailDisplay, resendOtpLink, timerText;
    private ImageView         backButton;

    // ── State ─────────────────────────────────────────────────────────────
    private String email;
    private String purpose;   // "verification" | "password_reset"
    private CountDownTimer resendTimer;
    private static final long TIMER_DURATION = 60_000L; // 60 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        initializeViews();

        // Get data from caller
        email   = getIntent().getStringExtra("email");
        purpose = getIntent().getStringExtra("purpose");
        if (purpose == null || purpose.isEmpty()) purpose = "verification";

        // Display the email so user knows where to look
        if (emailDisplay != null && email != null) {
            emailDisplay.setText(email);
        }

        setupClickListeners();
        startResendTimer();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Views
    // ─────────────────────────────────────────────────────────────────────

    private void initializeViews() {
        otpSignupLayout = findViewById(R.id.otpSignupLayout);
        otpSignupInput  = findViewById(R.id.otpSignupInput);
        verifyButton    = findViewById(R.id.verifyButton);
        emailDisplay    = findViewById(R.id.emailDisplay);
        resendOtpLink   = findViewById(R.id.resendOtpLink);
        timerText       = findViewById(R.id.timerText);
        backButton      = findViewById(R.id.backButton);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Click listeners
    // ─────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (backButton    != null) backButton.setOnClickListener(v -> finish());
        if (verifyButton  != null) verifyButton.setOnClickListener(v -> verifyOTP());
        if (resendOtpLink != null) resendOtpLink.setOnClickListener(v -> resendOTP());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Verify OTP — calls backend /api/auth/verify-otp
    // ─────────────────────────────────────────────────────────────────────

    private void verifyOTP() {
        if (otpSignupInput == null) return;
        String enteredOTP = otpSignupInput.getText() != null
                ? otpSignupInput.getText().toString().trim() : "";

        otpSignupLayout.setError(null);

        if (TextUtils.isEmpty(enteredOTP)) {
            otpSignupLayout.setError("Please enter the OTP.");
            return;
        }
        if (enteredOTP.length() != 6 || !enteredOTP.matches("\\d{6}")) {
            otpSignupLayout.setError("OTP must be exactly 6 digits.");
            return;
        }

        setVerifyLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("email",   email);
            body.put("otp",     enteredOTP);
            body.put("purpose", purpose);

            ApiClient.post("/api/auth/verify-otp", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    setVerifyLoading(false);
                    Toast.makeText(OTPVerificationActivity.this,
                            "Email verified successfully!", Toast.LENGTH_SHORT).show();

                    if ("verification".equals(purpose)) {
                        // Account activated — go to MainActivity
                        navigateToHome();
                    } else {
                        // password_reset OTP confirmed — go back to ForgotPassword
                        // to show the reset-password form
                        Intent intent = new Intent();
                        intent.putExtra("email",     email);
                        intent.putExtra("otp",       enteredOTP);
                        intent.putExtra("verified",  true);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    setVerifyLoading(false);
                    otpSignupLayout.setError(errorMessage);
                    Toast.makeText(OTPVerificationActivity.this,
                            errorMessage, Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            setVerifyLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Resend OTP — calls backend /api/auth/send-otp
    // ─────────────────────────────────────────────────────────────────────

    private void resendOTP() {
        if (resendOtpLink != null) resendOtpLink.setEnabled(false);

        try {
            JSONObject body = new JSONObject();
            body.put("email",   email);
            body.put("purpose", purpose);

            ApiClient.post("/api/auth/send-otp", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Toast.makeText(OTPVerificationActivity.this,
                            "A new OTP has been sent to " + email
                                    + ". Check your inbox and spam folder.",
                            Toast.LENGTH_LONG).show();
                    if (otpSignupInput  != null) otpSignupInput.setText("");
                    if (otpSignupLayout != null) otpSignupLayout.setError(null);
                    startResendTimer();
                }

                @Override
                public void onError(String errorMessage) {
                    Toast.makeText(OTPVerificationActivity.this,
                            "Failed to resend OTP: " + errorMessage, Toast.LENGTH_LONG).show();
                    if (resendOtpLink != null) resendOtpLink.setEnabled(true);
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (resendOtpLink != null) resendOtpLink.setEnabled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Countdown timer for Resend button
    // ─────────────────────────────────────────────────────────────────────

    private void startResendTimer() {
        if (resendOtpLink != null) resendOtpLink.setEnabled(false);
        if (timerText     != null) timerText.setVisibility(View.VISIBLE);

        if (resendTimer != null) resendTimer.cancel();

        resendTimer = new CountDownTimer(TIMER_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (timerText != null)
                    timerText.setText("Resend in " + (millisUntilFinished / 1000) + "s");
            }
            @Override
            public void onFinish() {
                if (resendOtpLink != null) resendOtpLink.setEnabled(true);
                if (timerText     != null) timerText.setVisibility(View.GONE);
            }
        }.start();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void setVerifyLoading(boolean loading) {
        if (verifyButton == null) return;
        verifyButton.setEnabled(!loading);
        verifyButton.setText(loading ? "Verifying..." : getString(R.string.verify_button));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) resendTimer.cancel();
    }
}
