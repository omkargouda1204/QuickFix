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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Random;

public class ForgotPasswordActivity extends AppCompatActivity {

    // UI Components
    private ImageView backButton;
    private LinearLayout emailSection, otpSection, resetPasswordSection;
    private TextInputLayout emailLayoutForgot, otpLayout, newPasswordLayout, confirmPasswordLayout;
    private TextInputEditText emailInputForgot, otpInput, newPasswordInput, confirmPasswordInput;
    private MaterialButton sendOtpButton, verifyOtpButton, resetPasswordButton;
    private TextView resendOtpLink, backToLoginLink;

    // Firebase
    private FirebaseAuth mAuth;

    // OTP variables
    private String generatedOTP;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Initialize views
        initializeViews();

        // Set up click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.backButton);
        emailSection = findViewById(R.id.emailSection);
        otpSection = findViewById(R.id.otpSection);
        resetPasswordSection = findViewById(R.id.resetPasswordSection);

        emailLayoutForgot = findViewById(R.id.emailLayoutForgot);
        otpLayout = findViewById(R.id.otpLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        emailInputForgot = findViewById(R.id.emailInputForgot);
        otpInput = findViewById(R.id.otpInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        sendOtpButton = findViewById(R.id.sendOtpButton);
        verifyOtpButton = findViewById(R.id.verifyOtpButton);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);

        resendOtpLink = findViewById(R.id.resendOtpLink);
        backToLoginLink = findViewById(R.id.backToLoginLink);
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Send OTP button
        sendOtpButton.setOnClickListener(v -> sendOTP());

        // Verify OTP button
        verifyOtpButton.setOnClickListener(v -> verifyOTP());

        // Reset Password button
        resetPasswordButton.setOnClickListener(v -> resetPassword());

        // Resend OTP link
        resendOtpLink.setOnClickListener(v -> sendOTP());

        // Back to Login link
        backToLoginLink.setOnClickListener(v -> {
            Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void sendOTP() {
        userEmail = emailInputForgot.getText().toString().trim();

        // Validate email
        emailLayoutForgot.setError(null);
        if (TextUtils.isEmpty(userEmail)) {
            emailLayoutForgot.setError(getString(R.string.error_email_required));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            emailLayoutForgot.setError(getString(R.string.error_invalid_email));
            return;
        }

        // Show loading
        sendOtpButton.setEnabled(false);
        sendOtpButton.setText("Sending...");

        // Send password reset email using Firebase
        mAuth.sendPasswordResetEmail(userEmail)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        sendOtpButton.setEnabled(true);
                        sendOtpButton.setText(R.string.send_otp);

                        if (task.isSuccessful()) {
                            // Generate OTP for demo purposes (In production, use Firebase email link)
                            generatedOTP = generateOTP();

                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Password reset email sent! (OTP for demo: " + generatedOTP + ")",
                                    Toast.LENGTH_LONG).show();

                            // Show OTP section
                            showOTPSection();
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Failed to send reset email: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private String generateOTP() {
        // Generate 6-digit OTP
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    private void showOTPSection() {
        emailSection.setVisibility(View.GONE);
        otpSection.setVisibility(View.VISIBLE);
        resetPasswordSection.setVisibility(View.GONE);
    }

    private void verifyOTP() {
        String enteredOTP = otpInput.getText().toString().trim();

        // Validate OTP
        otpLayout.setError(null);
        if (TextUtils.isEmpty(enteredOTP)) {
            otpLayout.setError("Please enter OTP");
            return;
        }

        if (enteredOTP.length() != 6) {
            otpLayout.setError("OTP must be 6 digits");
            return;
        }

        // Show loading
        verifyOtpButton.setEnabled(false);
        verifyOtpButton.setText("Verifying...");

        // Verify OTP (Demo implementation)
        if (enteredOTP.equals(generatedOTP)) {
            verifyOtpButton.setEnabled(true);
            verifyOtpButton.setText(R.string.verify_otp);

            Toast.makeText(this, "OTP Verified Successfully!", Toast.LENGTH_SHORT).show();

            // Show reset password section
            showResetPasswordSection();
        } else {
            verifyOtpButton.setEnabled(true);
            verifyOtpButton.setText(R.string.verify_otp);

            otpLayout.setError("Invalid OTP. Please try again.");
            Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResetPasswordSection() {
        emailSection.setVisibility(View.GONE);
        otpSection.setVisibility(View.GONE);
        resetPasswordSection.setVisibility(View.VISIBLE);
    }

    private void resetPassword() {
        String newPassword = newPasswordInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Reset errors
        newPasswordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        // Validate passwords
        if (TextUtils.isEmpty(newPassword)) {
            newPasswordLayout.setError(getString(R.string.error_password_required));
            return;
        }

        if (newPassword.length() < 6) {
            newPasswordLayout.setError(getString(R.string.error_password_length));
            return;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError("Please confirm password");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match");
            return;
        }

        // Show loading
        resetPasswordButton.setEnabled(false);
        resetPasswordButton.setText("Resetting...");

        // Note: Firebase password reset via email doesn't allow direct password change
        // The user needs to click the link in email. This is a demo flow.
        // In production, use Firebase Auth email action codes.

        Toast.makeText(this,
                getString(R.string.password_reset_success) + "\nPlease check your email to complete the reset.",
                Toast.LENGTH_LONG).show();

        // Navigate back to login
        Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}

