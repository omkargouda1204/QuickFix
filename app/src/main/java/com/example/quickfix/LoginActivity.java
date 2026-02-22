package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * LoginActivity — Firebase Email/Password + Google Sign-In.
 *
 * Flow (Email/Password):
 *   1. User enters email + password
 *   2. Firebase signInWithEmailAndPassword
 *   3. Check FirebaseUser.isEmailVerified()
 *   4. If verified → navigate to MainActivity
 *   5. If NOT verified → prompt to resend verification email
 *
 * Flow (Google):
 *   1. Google Sign-In → Firebase credential
 *   2. Navigate to MainActivity (Google accounts are auto-verified)
 */
public class LoginActivity extends AppCompatActivity {

    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton loginButton, googleSignInButton;
    private TextView forgotPasswordLink, signUpLink;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // If user is already logged in and verified, skip login
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            navigateToHome();
            return;
        }

        initializeViews();
        configureGoogleSignIn();
        setupClickListeners();
    }

    private void initializeViews() {
        emailLayout        = findViewById(R.id.emailLayout);
        passwordLayout     = findViewById(R.id.passwordLayout);
        emailInput         = findViewById(R.id.emailInput);
        passwordInput      = findViewById(R.id.passwordInput);
        loginButton        = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        signUpLink         = findViewById(R.id.signUpLink);
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    }
                }
        );
    }

    private void setupClickListeners() {
        if (loginButton != null)
            loginButton.setOnClickListener(v -> loginWithFirebase());

        if (googleSignInButton != null)
            googleSignInButton.setOnClickListener(v -> signInWithGoogle());

        if (forgotPasswordLink != null)
            forgotPasswordLink.setOnClickListener(v -> {
                if (!isFinishing())
                    startActivity(new Intent(this, ForgotPasswordActivity.class));
            });

        if (signUpLink != null)
            signUpLink.setOnClickListener(v -> {
                if (!isFinishing())
                    startActivity(new Intent(this, SignupActivity.class));
            });
    }

    // ── Email / Password Login ────────────────────────────────────────────

    private void loginWithFirebase() {
        String email    = emailInput    != null && emailInput.getText()    != null
                ? emailInput.getText().toString().trim()    : "";
        String password = passwordInput != null && passwordInput.getText() != null
                ? passwordInput.getText().toString().trim() : "";

        if (!validateInputs(email, password)) return;

        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.isEmailVerified()) {
                            setLoading(false);
                            Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        } else {
                            setLoading(false);
                            // Email not verified — offer to resend
                            showEmailNotVerifiedDialog(user);
                        }
                    } else {
                        setLoading(false);
                        String errMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed. Please try again.";
                        // Surface friendly errors
                        if (errMsg != null && (errMsg.contains("no user record")
                                || errMsg.contains("user-not-found"))) {
                            emailLayout.setError("No account found with this email.");
                        } else if (errMsg != null && (errMsg.contains("password is invalid")
                                || errMsg.contains("wrong-password"))) {
                            passwordLayout.setError("Incorrect password.");
                        } else {
                            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void showEmailNotVerifiedDialog(FirebaseUser user) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Email Not Verified")
                .setMessage("Please verify your email before logging in.\n\nCheck your inbox for the verification link.")
                .setPositiveButton("Resend Email", (dialog, which) -> {
                    if (user != null) {
                        user.sendEmailVerification()
                                .addOnCompleteListener(t -> {
                                    if (t.isSuccessful()) {
                                        Toast.makeText(this,
                                                "Verification email sent! Check your inbox.",
                                                Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(this,
                                                "Failed to send email. Try again.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                    mAuth.signOut();
                })
                .setNegativeButton("OK", (dialog, which) -> mAuth.signOut())
                .setCancelable(false)
                .show();
    }

    // ── Google Sign-In ─────────────────────────────────────────────────────

    private void signInWithGoogle() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Google Sign-In failed.";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean validateInputs(String email, String password) {
        boolean isValid = true;
        if (emailLayout    != null) emailLayout.setError(null);
        if (passwordLayout != null) passwordLayout.setError(null);

        if (TextUtils.isEmpty(email)) {
            if (emailLayout != null) emailLayout.setError(getString(R.string.error_email_required));
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (emailLayout != null) emailLayout.setError(getString(R.string.error_invalid_email));
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            if (passwordLayout != null) passwordLayout.setError(getString(R.string.error_password_required));
            isValid = false;
        }

        return isValid;
    }

    private void setLoading(boolean loading) {
        if (loginButton        != null) loginButton.setEnabled(!loading);
        if (googleSignInButton != null) googleSignInButton.setEnabled(!loading);
        if (loginButton        != null) loginButton.setText(loading ? "Logging in…" : "Login");
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
