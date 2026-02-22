package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * EmailVerificationActivity
 * ──────────────────────────────────────────────────────────────────────────
 * Shown immediately after signup (email/password flow only).
 * The user must verify their email before they can log in.
 *
 * "I've Verified My Email"
 *   → user.reload() → checks isEmailVerified() from Firebase server
 *   → if true  → MainActivity
 *   → if false → shows clear toast to click the link first
 *
 * "Resend Verification Email"
 *   → sendEmailVerification() with addOnSuccessListener + addOnFailureListener
 *   → no silent failures
 *
 * Back / "Back to Login" → sign out → LoginActivity
 */
public class EmailVerificationActivity extends AppCompatActivity {

    private static final String TAG = "EmailVerificationActivity";

    private TextView       emailDisplay;
    private MaterialButton checkVerifiedButton, resendEmailButton;
    private TextView       backToLoginLink;

    private FirebaseAuth mAuth;
    private String       email;   // passed from SignupActivity

    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_verification);

        mAuth = FirebaseAuth.getInstance();
        email = getIntent().getStringExtra("email");

        bindViews();
        setupClickListeners();

        // Hardware back → go to Login
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        navigateToLogin();
                    }
                });
    }

    // ── View binding ───────────────────────────────────────────────────────
    private void bindViews() {
        emailDisplay        = findViewById(R.id.emailDisplay);
        checkVerifiedButton = findViewById(R.id.checkVerifiedButton);
        resendEmailButton   = findViewById(R.id.resendEmailButton);
        backToLoginLink     = findViewById(R.id.backToLoginLink);

        if (emailDisplay != null && email != null)
            emailDisplay.setText(email);
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        if (checkVerifiedButton != null)
            checkVerifiedButton.setOnClickListener(v -> checkIfVerified());

        if (resendEmailButton != null)
            resendEmailButton.setOnClickListener(v -> resendEmail());

        if (backToLoginLink != null)
            backToLoginLink.setOnClickListener(v -> navigateToLogin());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CHECK VERIFICATION STATUS
    // ══════════════════════════════════════════════════════════════════════
    private void checkIfVerified() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // Session expired — go back to login
            navigateToLogin();
            return;
        }

        setBusy(checkVerifiedButton, true, "Checking…", "I've Verified My Email");

        // reload() fetches the latest isEmailVerified from Firebase servers
        user.reload()
                .addOnSuccessListener(this, unused -> {
                    setBusy(checkVerifiedButton, false, "Checking…",
                            "I've Verified My Email");

                    FirebaseUser fresh = mAuth.getCurrentUser();
                    if (fresh != null && fresh.isEmailVerified()) {
                        Log.d(TAG, "Email verified — navigating to home");
                        Toast.makeText(this,
                                "✅ Email verified! Welcome to QuickFix!",
                                Toast.LENGTH_SHORT).show();
                        goHome();
                    } else {
                        Log.d(TAG, "Email still not verified");
                        Toast.makeText(this,
                                "Your email is NOT verified yet.\n\nPlease check your inbox and click the verification link, then try again.",
                                Toast.LENGTH_LONG).show();
                        // Do NOT navigate away
                    }
                })
                .addOnFailureListener(this, e -> {
                    setBusy(checkVerifiedButton, false, "Checking…",
                            "I've Verified My Email");
                    Log.e(TAG, "reload() failed: " + e.getMessage());
                    Toast.makeText(this,
                            "Could not check verification status. "
                            + "Check your internet connection and try again.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RESEND VERIFICATION EMAIL
    // ══════════════════════════════════════════════════════════════════════
    private void resendEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this,
                    "Session expired. Please sign in again.",
                    Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        setBusy(resendEmailButton, true, "Sending…",
                "Resend Verification Email");

        user.sendEmailVerification()
                .addOnSuccessListener(this, unused -> {
                    setBusy(resendEmailButton, false, "Sending…",
                            "Resend Verification Email");
                    Log.d(TAG, "Verification email resent to " + email);
                    Toast.makeText(this,
                            "✅ Verification email resent to " + email
                            + ".\nCheck your inbox (and spam folder).",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(this, e -> {
                    setBusy(resendEmailButton, false, "Sending…",
                            "Resend Verification Email");
                    Log.e(TAG, "resend failed: " + e.getMessage());

                    String msg = e.getMessage();
                    if (msg != null && msg.contains("too many")) {
                        Toast.makeText(this,
                                "Too many requests. Wait a few minutes and try again.",
                                Toast.LENGTH_LONG).show();
                    } else if (msg != null && (msg.contains("NETWORK")
                            || msg.contains("network"))) {
                        Toast.makeText(this,
                                "No internet connection. Please try again.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Failed to resend: "
                                + (msg != null ? msg : "Unknown error"),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void setBusy(MaterialButton btn, boolean busy,
                          String busyText, String idleText) {
        if (btn == null) return;
        btn.setEnabled(!busy);
        btn.setText(busy ? busyText : idleText);
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void navigateToLogin() {
        mAuth.signOut();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}

