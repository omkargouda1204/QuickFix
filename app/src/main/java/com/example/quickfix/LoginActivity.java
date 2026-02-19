package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
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

import org.json.JSONObject;

/**
 * LoginActivity — Login via QuickFix backend API.
 *
 * Flow:
 *   1. User enters email + password
 *   2. POST /api/auth/login → backend verifies credentials in Supabase
 *   3. If verified → returns JWT token + user data
 *   4. Navigate to MainActivity
 */
public class LoginActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton loginButton, googleSignInButton;
    private TextView forgotPasswordLink, signUpLink;

    // ── Firebase (Google Sign-In only) ────────────────────────────────────
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        configureGoogleSignIn();
        setupClickListeners();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────
    // Click listeners
    // ─────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (loginButton        != null) loginButton.setOnClickListener(v -> loginWithBackend());
        if (googleSignInButton != null) googleSignInButton.setOnClickListener(v -> signInWithGoogle());
        if (forgotPasswordLink != null) forgotPasswordLink.setOnClickListener(v -> {
            if (!isFinishing()) startActivity(new Intent(this, ForgotPasswordActivity.class));
        });
        if (signUpLink != null) signUpLink.setOnClickListener(v -> {
            if (!isFinishing()) startActivity(new Intent(this, SignupActivity.class));
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Backend login — POST /api/auth/login
    // ─────────────────────────────────────────────────────────────────────

    private void loginWithBackend() {
        String email    = emailInput    != null && emailInput.getText()    != null
                ? emailInput.getText().toString().trim()    : "";
        String password = passwordInput != null && passwordInput.getText() != null
                ? passwordInput.getText().toString().trim() : "";

        if (!validateInputs(email, password)) return;

        setLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("email",    email);
            body.put("password", password);

            ApiClient.post("/api/auth/login", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    setLoading(false);
                    // Login successful — backend verified email + password
                    // and returned JWT token + user data
                    Toast.makeText(LoginActivity.this, "Login successful!", Toast.LENGTH_SHORT).show();
                    navigateToHome();
                }

                @Override
                public void onError(String errorMessage) {
                    setLoading(false);
                    // Show specific error on appropriate field
                    if (errorMessage.toLowerCase().contains("email")) {
                        emailLayout.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("password")) {
                        passwordLayout.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("not verified")) {
                        Toast.makeText(LoginActivity.this,
                                errorMessage + "\nCheck your email for the OTP.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            });

        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────
    // Google Sign-In (still uses Firebase Auth)
    // ─────────────────────────────────────────────────────────────────────

    private void signInWithGoogle() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Toast.makeText(this, "Welcome " + user.getDisplayName(), Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        }
                    } else {
                        String msg = task.getException() != null ? task.getException().getMessage() : "Google auth failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (ApiException e) {
            Toast.makeText(this, "Google sign-in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (loginButton == null) return;
        loginButton.setEnabled(!loading);
        loginButton.setText(loading ? "Logging in..." : getString(R.string.login_button));
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
