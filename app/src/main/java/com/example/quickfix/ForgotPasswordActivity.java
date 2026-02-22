package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ForgotPasswordActivity — Firebase Password Reset Email.
 *
 * Flow:
 *   1. User enters their email address.
 *   2. Firebase sendPasswordResetEmail() sends a password reset link.
 *   3. User clicks the link in their inbox to set a new password.
 *   4. User returns to LoginActivity to sign in with the new password.
 *
 * No OTP, no backend, no SMTP required.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private ImageView         backButton;
    private TextInputLayout   emailLayoutForgot;
    private TextInputEditText emailInputForgot;
    private MaterialButton    sendResetEmailButton;
    private TextView          backToLoginLink;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        backButton           = findViewById(R.id.backButton);
        emailLayoutForgot    = findViewById(R.id.emailLayoutForgot);
        emailInputForgot     = findViewById(R.id.emailInputForgot);
        sendResetEmailButton = findViewById(R.id.sendOtpButton);   // reuse existing button id
        backToLoginLink      = findViewById(R.id.backToLoginLink);
    }

    private void setupClickListeners() {
        if (backButton != null)
            backButton.setOnClickListener(v -> finish());

        if (sendResetEmailButton != null)
            sendResetEmailButton.setOnClickListener(v -> sendPasswordResetEmail());

        if (backToLoginLink != null)
            backToLoginLink.setOnClickListener(v -> navigateToLogin());
    }

    private void sendPasswordResetEmail() {
        String email = emailInputForgot != null && emailInputForgot.getText() != null
                ? emailInputForgot.getText().toString().trim() : "";

        if (emailLayoutForgot != null) emailLayoutForgot.setError(null);

        if (TextUtils.isEmpty(email)) {
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError("Email is required.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError("Enter a valid email address.");
            return;
        }

        if (sendResetEmailButton != null) {
            sendResetEmailButton.setEnabled(false);
            sendResetEmailButton.setText("Sending…");
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    if (sendResetEmailButton != null) {
                        sendResetEmailButton.setEnabled(true);
                        sendResetEmailButton.setText("Send Reset Link");
                    }

                    if (task.isSuccessful()) {
                        // Show success dialog
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Reset Email Sent")
                                .setMessage("A password reset link has been sent to:\n\n"
                                        + email
                                        + "\n\nCheck your inbox and click the link to reset your password.")
                                .setPositiveButton("Go to Login", (dialog, which) -> navigateToLogin())
                                .setCancelable(false)
                                .show();
                    } else {
                        String errMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed to send reset email. Try again.";
                        if (errMsg != null && (errMsg.contains("no user record")
                                || errMsg.contains("user-not-found"))) {
                            if (emailLayoutForgot != null)
                                emailLayoutForgot.setError("No account found with this email.");
                        } else {
                            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
