package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;

/**
 * ForgotPasswordActivity — Firebase sendPasswordResetEmail
 * ──────────────────────────────────────────────────────────────────────────
 * Flow:
 *   1. User enters email + client-side validation.
 *   2. sendPasswordResetEmail  →
 *        addOnSuccessListener  → show confirmation dialog → go to Login.
 *        addOnFailureListener  → map FirebaseAuthException codes → show error.
 *
 * Important Firebase behaviour:
 *   Firebase always calls the SUCCESS listener even when the email is NOT
 *   registered — this is intentional anti-enumeration protection.
 *   The reset email is only DELIVERED if the account actually exists.
 *   We communicate this clearly in the success dialog.
 *
 * REQUIRES: Firebase Console → Authentication → Sign-in method →
 *           Email/Password → Enabled.
 *
 * NO Firestore. NO backend. No OTP. Pure Firebase.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String TAG = "ForgotPasswordActivity";

    private ImageView         backButton;
    private TextInputLayout   emailLayoutForgot;
    private TextInputEditText emailInputForgot;
    private MaterialButton    sendResetButton;
    private TextView          backToLoginLink;

    private FirebaseAuth mAuth;

    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();
        bindViews();
        setupClickListeners();
    }

    // ── View binding ───────────────────────────────────────────────────────
    private void bindViews() {
        backButton        = findViewById(R.id.backButton);
        emailLayoutForgot = findViewById(R.id.emailLayoutForgot);
        emailInputForgot  = findViewById(R.id.emailInputForgot);
        sendResetButton   = findViewById(R.id.sendOtpButton);    // reuses layout id
        backToLoginLink   = findViewById(R.id.backToLoginLink);
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        if (backButton      != null) backButton.setOnClickListener(v -> finish());
        if (sendResetButton != null) sendResetButton.setOnClickListener(v -> sendReset());
        if (backToLoginLink != null) backToLoginLink.setOnClickListener(v -> goToLogin());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SEND PASSWORD RESET EMAIL
    // ══════════════════════════════════════════════════════════════════════
    private void sendReset() {
        // Clear any previous error
        if (emailLayoutForgot != null) emailLayoutForgot.setError(null);

        final String email = emailInputForgot != null
                && emailInputForgot.getText() != null
                ? emailInputForgot.getText().toString().trim() : "";

        // ── Client-side validation ─────────────────────────────────────────
        if (TextUtils.isEmpty(email)) {
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError("Email address is required.");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError("Enter a valid email address.");
            return;
        }

        setBusy(true);

        // ── Firebase sendPasswordResetEmail ────────────────────────────────
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(this, unused -> {
                    // Firebase reports success even for unregistered emails
                    // (anti-enumeration) — reset email is only SENT if the
                    // account actually exists in Firebase Auth.
                    setBusy(false);
                    Log.d(TAG, "sendPasswordResetEmail success for: " + email);
                    showSuccessDialog(email);
                })
                .addOnFailureListener(this, e -> {
                    setBusy(false);
                    handleResetError(e, email);
                });
    }

    // ── Success dialog ─────────────────────────────────────────────────────
    private void showSuccessDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("📧 Check Your Email")
                .setMessage("A password reset link has been sent to:\n\n"
                        + email
                        + "\n\n"
                        + "• Check your inbox and spam folder.\n"
                        + "• Click the link to set a new password.\n"
                        + "• Return here and log in with your new password.\n\n"
                        + "Note: The email will only arrive if this address is\n"
                        + "registered in QuickFix.")
                .setPositiveButton("Go to Login", (d, w) -> goToLogin())
                .setCancelable(false)
                .show();
    }

    // ── Firebase error mapping ─────────────────────────────────────────────
    private void handleResetError(Exception ex, String email) {
        String msg  = ex.getMessage();
        String code = (ex instanceof FirebaseAuthException)
                ? ((FirebaseAuthException) ex).getErrorCode() : "";

        Log.e(TAG, "sendPasswordResetEmail FAILED code=" + code + " msg=" + msg);

        if ("ERROR_INVALID_EMAIL".equals(code)
                || (msg != null && msg.contains("badly formatted"))) {
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError("Invalid email address format.");

        } else if ("ERROR_USER_NOT_FOUND".equals(code)
                || (msg != null && msg.contains("no user record"))) {
            // Normally Firebase won't surface this, but handle it anyway
            if (emailLayoutForgot != null)
                emailLayoutForgot.setError(
                        "No account found for this email. Please sign up.");

        } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
            Toast.makeText(this,
                    "❌ Password reset is not enabled.\n"
                    + "Go to Firebase Console → Authentication"
                    + " → Sign-in method → Email/Password → Enable.",
                    Toast.LENGTH_LONG).show();

        } else if ("ERROR_TOO_MANY_REQUESTS".equals(code)
                || (msg != null && msg.contains("too many"))) {
            Toast.makeText(this,
                    "Too many requests. Please wait a few minutes and try again.",
                    Toast.LENGTH_LONG).show();

        } else if (msg != null
                && (msg.contains("NETWORK_ERROR")
                || msg.contains("network")
                || msg.contains("NETWORK"))) {
            Toast.makeText(this,
                    "No internet connection. Check your network and try again.",
                    Toast.LENGTH_LONG).show();

        } else {
            Toast.makeText(this,
                    "Failed to send reset email: "
                    + (msg != null ? msg : "Unknown error"),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void setBusy(boolean busy) {
        if (sendResetButton != null) {
            sendResetButton.setEnabled(!busy);
            sendResetButton.setText(busy ? "Sending…" : "Send Reset Link");
        }
    }

    private void goToLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}
