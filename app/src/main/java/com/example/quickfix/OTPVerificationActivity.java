package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class OTPVerificationActivity extends AppCompatActivity {

    // UI Components
    private TextInputLayout otpSignupLayout;
    private TextInputEditText otpSignupInput;
    private MaterialButton verifyButton;
    private TextView emailDisplay, resendOtpLink, timerText;
    private ImageView backButton;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;

    // Variables
    private String email;
    private String correctOTP;
    private boolean isSignup;
    private CountDownTimer resendTimer;
    private static final long TIMER_DURATION = 60000; // 60 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Initialize views
        initializeViews();

        // Get data from intent
        email = getIntent().getStringExtra("email");
        correctOTP = getIntent().getStringExtra("otp");
        isSignup = getIntent().getBooleanExtra("isSignup", false);

        // Display email
        if (email != null) {
            emailDisplay.setText(email);
        }

        // Set up click listeners
        setupClickListeners();

        // Start resend timer
        startResendTimer();
    }

    private void initializeViews() {
        otpSignupLayout = findViewById(R.id.otpSignupLayout);
        otpSignupInput = findViewById(R.id.otpSignupInput);
        verifyButton = findViewById(R.id.verifyButton);
        emailDisplay = findViewById(R.id.emailDisplay);
        resendOtpLink = findViewById(R.id.resendOtpLink);
        timerText = findViewById(R.id.timerText);
        backButton = findViewById(R.id.backButton);
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Verify button
        verifyButton.setOnClickListener(v -> verifyOTP());

        // Resend OTP link
        resendOtpLink.setOnClickListener(v -> resendOTP());
    }

    private void verifyOTP() {
        String enteredOTP = otpSignupInput.getText().toString().trim();

        // Validate OTP
        otpSignupLayout.setError(null);
        if (TextUtils.isEmpty(enteredOTP)) {
            otpSignupLayout.setError("Please enter OTP");
            return;
        }

        if (enteredOTP.length() != 6) {
            otpSignupLayout.setError("OTP must be 6 digits");
            return;
        }

        // Show loading state
        verifyButton.setEnabled(false);
        verifyButton.setText("Verifying...");

        // Verify OTP (Demo implementation)
        if (enteredOTP.equals(correctOTP)) {
            verifyButton.setEnabled(true);
            verifyButton.setText(R.string.verify_button);

            // Mark email as verified in Firestore
            if (isSignup && mAuth.getCurrentUser() != null) {
                mFirestore.collection("users").document(mAuth.getCurrentUser().getUid())
                        .update("emailVerified", true)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(OTPVerificationActivity.this,
                                    getString(R.string.otp_verified), Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(OTPVerificationActivity.this,
                                    "Error verifying email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
            } else {
                Toast.makeText(this, getString(R.string.otp_verified), Toast.LENGTH_SHORT).show();
                navigateToHome();
            }
        } else {
            verifyButton.setEnabled(true);
            verifyButton.setText(R.string.verify_button);
            otpSignupLayout.setError(getString(R.string.error_otp_invalid));
            Toast.makeText(this, getString(R.string.error_otp_invalid), Toast.LENGTH_SHORT).show();
        }
    }

    private void resendOTP() {
        // Generate new OTP
        int otp = (int) (100000 + Math.random() * 900000);
        correctOTP = String.valueOf(otp);

        // Show toast with new OTP (demo)
        Toast.makeText(this, "New OTP: " + correctOTP, Toast.LENGTH_LONG).show();

        // In production, send OTP via email using backend service
        // Example: sendOTPViaEmail(email, correctOTP);

        // Reset timer
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        startResendTimer();

        // Clear OTP input
        otpSignupInput.setText("");
        otpSignupLayout.setError(null);
    }

    private void startResendTimer() {
        resendOtpLink.setEnabled(false);
        timerText.setVisibility(android.view.View.VISIBLE);

        resendTimer = new CountDownTimer(TIMER_DURATION, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long secondsRemaining = millisUntilFinished / 1000;
                timerText.setText("Resend in " + secondsRemaining + "s");
            }

            @Override
            public void onFinish() {
                resendOtpLink.setEnabled(true);
                timerText.setVisibility(android.view.View.GONE);
            }
        }.start();
    }

    private void navigateToHome() {
        Intent intent = new Intent(OTPVerificationActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
    }
}

