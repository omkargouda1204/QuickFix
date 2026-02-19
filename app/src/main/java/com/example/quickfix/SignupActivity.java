package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageView;
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
 * SignupActivity — Registers a new user via the QuickFix backend API.
 *
 * Flow:
 *   1. User fills name / phone / email / password.
 *   2. POST /api/auth/signup → backend creates user + sends real OTP email.
 *   3. Navigate to OTPVerificationActivity (email, purpose=verification).
 *   4. OTPVerificationActivity calls POST /api/auth/verify-otp → activates account.
 *   5. On success → MainActivity.
 */
public class SignupActivity extends AppCompatActivity {

    // ── UI ────────────────────────────────────────────────────────────────
    private TextInputLayout   fullNameLayout, phoneLayout, emailSignupLayout,
                              passwordSignupLayout, confirmPasswordLayout;
    private TextInputEditText fullNameInput, phoneInput, emailSignupInput,
                              passwordSignupInput, confirmPasswordInput;
    private MaterialButton    signupButton, googleSignupButton;
    private ImageView         backButton;
    private TextView          loginLink;

    // ── Firebase (Google Sign-In only) ────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignupLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        configureGoogleSignUp();
        setupClickListeners();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────

    private void initializeViews() {
        fullNameLayout        = findViewById(R.id.fullNameLayout);
        phoneLayout           = findViewById(R.id.phoneLayout);
        emailSignupLayout     = findViewById(R.id.emailSignupLayout);
        passwordSignupLayout  = findViewById(R.id.passwordSignupLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        fullNameInput        = findViewById(R.id.fullNameInput);
        phoneInput           = findViewById(R.id.phoneInput);
        emailSignupInput     = findViewById(R.id.emailSignupInput);
        passwordSignupInput  = findViewById(R.id.passwordSignupInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        signupButton       = findViewById(R.id.signupButton);
        googleSignupButton = findViewById(R.id.googleSignupButton);
        backButton         = findViewById(R.id.backButton);
        loginLink          = findViewById(R.id.loginLink);
    }

    private void configureGoogleSignUp() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignupResult(task);
                    }
                }
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // Click listeners
    // ─────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {
        if (backButton      != null) backButton.setOnClickListener(v -> finish());
        if (loginLink       != null) loginLink.setOnClickListener(v -> finish());
        if (signupButton    != null) signupButton.setOnClickListener(v -> {
            if (!isFinishing()) signupWithBackend();
        });
        if (googleSignupButton != null) googleSignupButton.setOnClickListener(v -> {
            if (!isFinishing()) signupWithGoogle();
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Backend signup — POST /api/auth/signup
    // ──────────────────────────────────────────────────────────────���──────

    private void signupWithBackend() {
        String fullName        = safeText(fullNameInput);
        String phone           = safeText(phoneInput);
        String email           = safeText(emailSignupInput);
        String password        = safeText(passwordSignupInput);
        String confirmPassword = safeText(confirmPasswordInput);

        if (!validateSignupInputs(fullName, phone, email, password, confirmPassword)) return;

        setLoading(true);

        try {
            JSONObject body = new JSONObject();
            body.put("name",     fullName);
            body.put("email",    email);
            body.put("phone",    phone);
            body.put("password", password);
            body.put("role",     "user");

            ApiClient.post("/api/auth/signup", body, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    setLoading(false);
                    // Account created + real OTP email sent by backend
                    // Navigate to OTP screen — backend verifies OTP
                    launchOTPScreen(email, "verification");
                }

                @Override
                public void onError(String errorMessage) {
                    setLoading(false);
                    // Surface specific field errors
                    if (errorMessage.toLowerCase().contains("email")) {
                        emailSignupLayout.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("password")) {
                        passwordSignupLayout.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("name")) {
                        fullNameLayout.setError(errorMessage);
                    } else if (errorMessage.toLowerCase().contains("phone")) {
                        phoneLayout.setError(errorMessage);
                    } else {
                        Toast.makeText(SignupActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
            });

        } catch (Exception e) {
            setLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Navigate to OTP screen
    // ─────────────────────────────────────────────────────────────────────

    private void launchOTPScreen(String email, String purpose) {
        Intent intent = new Intent(this, OTPVerificationActivity.class);
        intent.putExtra("email",   email);
        intent.putExtra("purpose", purpose);   // "verification" or "password_reset"
        startActivity(intent);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────

    private boolean validateSignupInputs(String fullName, String phone,
                                          String email, String password,
                                          String confirmPassword) {
        boolean valid = true;

        fullNameLayout.setError(null);
        phoneLayout.setError(null);
        emailSignupLayout.setError(null);
        passwordSignupLayout.setError(null);
        confirmPasswordLayout.setError(null);

        if (TextUtils.isEmpty(fullName) || fullName.length() < 2) {
            fullNameLayout.setError("Name must be at least 2 characters.");
            valid = false;
        }
        if (TextUtils.isEmpty(phone) || !isValidPhone(phone)) {
            phoneLayout.setError("Enter a valid phone number (10–15 digits).");
            valid = false;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailSignupLayout.setError("Enter a valid email address.");
            valid = false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 8) {
            passwordSignupLayout.setError("Password must be at least 8 characters.");
            valid = false;
        } else if (!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")) {
            passwordSignupLayout.setError("Password needs uppercase, lowercase, and a digit.");
            valid = false;
        }
        if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError("Passwords do not match.");
            valid = false;
        }
        return valid;
    }

    private boolean isValidPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= 10 && digits.length() <= 15;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Google Sign-Up (still uses Firebase Auth)
    // ─────────────────────────────────────────────────────────────────────

    private void signupWithGoogle() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> googleSignupLauncher.launch(mGoogleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignupResult(Task<GoogleSignInAccount> completedTask) {
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
            Toast.makeText(this, "Google sign-up failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        if (signupButton == null) return;
        signupButton.setEnabled(!loading);
        signupButton.setText(loading ? "Creating account..." : getString(R.string.signup_button));
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private String safeText(TextInputEditText input) {
        if (input == null || input.getText() == null) return "";
        return input.getText().toString().trim();
    }
}
