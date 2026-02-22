package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * EmailVerificationActivity — shown after signup.
 *
 * Informs the user to check their inbox and verify their email.
 * "I've Verified" button reloads the Firebase user and checks isEmailVerified().
 * If verified → navigate to MainActivity.
 * If not yet verified → show a toast to wait.
 * "Resend Email" button sends another verification email.
 */
public class EmailVerificationActivity extends AppCompatActivity {

    private TextView         emailDisplay;
    private MaterialButton   checkVerifiedButton, resendEmailButton;
    private TextView         backToLoginLink;

    private FirebaseAuth mAuth;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        mAuth = FirebaseAuth.getInstance();
        email = getIntent().getStringExtra("email");

        initializeViews();
        setupClickListeners();

        // Handle back press — go to login
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToLogin();
            }
        });
    }

    private void initializeViews() {
        emailDisplay        = findViewById(R.id.emailDisplay);
        checkVerifiedButton = findViewById(R.id.checkVerifiedButton);
        resendEmailButton   = findViewById(R.id.resendEmailButton);
        backToLoginLink     = findViewById(R.id.backToLoginLink);

        if (emailDisplay != null && email != null) {
            emailDisplay.setText(email);
        }
    }

    private void setupClickListeners() {
        if (checkVerifiedButton != null)
            checkVerifiedButton.setOnClickListener(v -> checkEmailVerified());

        if (resendEmailButton != null)
            resendEmailButton.setOnClickListener(v -> resendVerificationEmail());

        if (backToLoginLink != null)
            backToLoginLink.setOnClickListener(v -> navigateToLogin());
    }

    private void checkEmailVerified() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            // User is signed out, go to login
            navigateToLogin();
            return;
        }

        if (checkVerifiedButton != null) {
            checkVerifiedButton.setEnabled(false);
            checkVerifiedButton.setText("Checking…");
        }

        // Reload user to get fresh verification status from Firebase server
        user.reload().addOnCompleteListener(task -> {
            if (checkVerifiedButton != null) {
                checkVerifiedButton.setEnabled(true);
                checkVerifiedButton.setText("I've Verified My Email");
            }

            if (user.isEmailVerified()) {
                Toast.makeText(this, "Email verified! Welcome to QuickFix!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this,
                        "Email not verified yet. Please check your inbox and click the verification link.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void resendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "Please sign in again to resend the verification email.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        if (resendEmailButton != null) {
            resendEmailButton.setEnabled(false);
            resendEmailButton.setText("Sending…");
        }

        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (resendEmailButton != null) {
                        resendEmailButton.setEnabled(true);
                        resendEmailButton.setText("Resend Verification Email");
                    }

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Verification email resent to " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        String errMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to resend email.";
                        Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToLogin() {
        mAuth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

