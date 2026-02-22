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
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * SignupActivity — Firebase Email/Password Registration.
 *
 * Flow (Email/Password):
 *   1. User fills name / phone / email / password.
 *   2. Firebase createUserWithEmailAndPassword → creates account.
 *   3. Update Firebase display name.
 *   4. Save profile (name, phone, email, uid, role) to Firestore "users" collection.
 *   5. Send email verification link via Firebase.
 *   6. Navigate to EmailVerificationActivity.
 *
 * Flow (Google):
 *   1. Google Sign-In → Firebase credential → save profile to Firestore.
 *   2. Navigate to MainActivity (already verified).
 */
public class SignupActivity extends AppCompatActivity {

    private TextInputLayout   fullNameLayout, phoneLayout, emailSignupLayout,
                              passwordSignupLayout, confirmPasswordLayout;
    private TextInputEditText fullNameInput, phoneInput, emailSignupInput,
                              passwordSignupInput, confirmPasswordInput;
    private MaterialButton    signupButton, googleSignupButton;
    private ImageView         backButton;
    private TextView          loginLink;

    private FirebaseAuth       mAuth;
    private FirebaseFirestore  db;
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> googleSignupLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        initializeViews();
        configureGoogleSignUp();
        setupClickListeners();
    }

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

    private void setupClickListeners() {
        if (backButton         != null) backButton.setOnClickListener(v -> finish());
        if (loginLink          != null) loginLink.setOnClickListener(v -> finish());
        if (signupButton       != null) signupButton.setOnClickListener(v -> signupWithFirebase());
        if (googleSignupButton != null) googleSignupButton.setOnClickListener(v -> signupWithGoogle());
    }

    // ── Email / Password Signup ───────────────────────────────────────────

    private void signupWithFirebase() {
        String fullName        = safeText(fullNameInput);
        String phone           = safeText(phoneInput);
        String email           = safeText(emailSignupInput);
        String password        = safeText(passwordSignupInput);
        String confirmPassword = safeText(confirmPasswordInput);

        if (!validateSignupInputs(fullName, phone, email, password, confirmPassword)) return;

        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Update display name in Firebase Auth
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(fullName)
                                    .build();
                            user.updateProfile(profileUpdates);

                            // Save extra profile data to Firestore
                            saveUserToFirestore(user.getUid(), fullName, email, phone, () -> {
                                // Send email verification link
                                user.sendEmailVerification()
                                        .addOnCompleteListener(emailTask -> {
                                            setLoading(false);
                                            if (emailTask.isSuccessful()) {
                                                Toast.makeText(this,
                                                        "Verification email sent to " + email,
                                                        Toast.LENGTH_LONG).show();
                                            } else {
                                                Toast.makeText(this,
                                                        "Account created! Verification email could not be sent. Try resending from login.",
                                                        Toast.LENGTH_LONG).show();
                                            }
                                            // Sign out — user must verify before logging in
                                            mAuth.signOut();
                                            launchEmailVerificationScreen(email);
                                        });
                            });
                        }
                    } else {
                        setLoading(false);
                        String errMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Sign up failed. Please try again.";
                        if (errMsg != null && errMsg.contains("email address is already")) {
                            emailSignupLayout.setError("Email already registered. Please login.");
                        } else if (errMsg != null && errMsg.contains("weak-password")) {
                            passwordSignupLayout.setError("Password is too weak.");
                        } else {
                            Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToFirestore(String uid, String name, String email,
                                     String phone, Runnable onComplete) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("uid",        uid);
        userData.put("name",       name);
        userData.put("email",      email);
        userData.put("phone",      phone);
        userData.put("role",       "user");
        userData.put("verified",   false);
        userData.put("created_at", com.google.firebase.Timestamp.now());

        db.collection("users").document(uid)
                .set(userData)
                .addOnCompleteListener(t -> {
                    // Continue regardless of Firestore result
                    onComplete.run();
                });
    }

    private void launchEmailVerificationScreen(String email) {
        Intent intent = new Intent(this, EmailVerificationActivity.class);
        intent.putExtra("email", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ── Google Sign-Up ─────────────────────────────────────────────────────

    private void signupWithGoogle() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> googleSignupLauncher.launch(mGoogleSignInClient.getSignInIntent()));
    }

    private void handleGoogleSignupResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            firebaseAuthWithGoogle(account);
        } catch (ApiException e) {
            Toast.makeText(this, "Google Sign-In failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String displayName = account.getDisplayName() != null
                                    ? account.getDisplayName() : "";
                            String email = account.getEmail() != null
                                    ? account.getEmail() : "";
                            saveUserToFirestore(user.getUid(), displayName, email, "", () -> {
                                setLoading(false);
                                Toast.makeText(this, "Account created successfully!",
                                        Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                        }
                    } else {
                        setLoading(false);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Google Sign-In failed.";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Validation ──────────────────────────────────────────────────────────

    private boolean validateSignupInputs(String fullName, String phone,
                                          String email, String password,
                                          String confirmPassword) {
        boolean isValid = true;
        clearErrors();

        if (TextUtils.isEmpty(fullName)) {
            fullNameLayout.setError(getString(R.string.error_fullname_required));
            isValid = false;
        } else if (fullName.length() < 3) {
            fullNameLayout.setError(getString(R.string.error_fullname_length));
            isValid = false;
        }

        if (TextUtils.isEmpty(phone)) {
            phoneLayout.setError(getString(R.string.error_phone_required));
            isValid = false;
        } else if (!phone.matches("\\d{10,15}")) {
            phoneLayout.setError(getString(R.string.error_phone_invalid));
            isValid = false;
        }

        if (TextUtils.isEmpty(email)) {
            emailSignupLayout.setError(getString(R.string.error_email_required));
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailSignupLayout.setError(getString(R.string.error_invalid_email));
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            passwordSignupLayout.setError(getString(R.string.error_password_required));
            isValid = false;
        } else if (password.length() < 6) {
            passwordSignupLayout.setError(getString(R.string.error_password_length));
            isValid = false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError("Please confirm your password.");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_passwords_not_match));
            isValid = false;
        }

        return isValid;
    }

    private void clearErrors() {
        if (fullNameLayout        != null) fullNameLayout.setError(null);
        if (phoneLayout           != null) phoneLayout.setError(null);
        if (emailSignupLayout     != null) emailSignupLayout.setError(null);
        if (passwordSignupLayout  != null) passwordSignupLayout.setError(null);
        if (confirmPasswordLayout != null) confirmPasswordLayout.setError(null);
    }

    private String safeText(TextInputEditText input) {
        return (input != null && input.getText() != null)
                ? input.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        if (signupButton       != null) signupButton.setEnabled(!loading);
        if (googleSignupButton != null) googleSignupButton.setEnabled(!loading);
        if (signupButton       != null) signupButton.setText(loading ? "Creating Account…" : getString(R.string.signup_button));
    }
}
