package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    // UI Components
    private TextInputLayout fullNameLayout, phoneLayout, emailSignupLayout, passwordSignupLayout, confirmPasswordLayout;
    private TextInputEditText fullNameInput, phoneInput, emailSignupInput, passwordSignupInput, confirmPasswordInput;
    private MaterialButton signupButton, googleSignupButton;
    private ImageView backButton;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirestore;
    private GoogleSignInClient mGoogleSignInClient;

    // Activity Result Launcher for Google Sign-Up
    private ActivityResultLauncher<Intent> googleSignupLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();

        // Initialize UI components
        initializeViews();

        // Configure Google Sign-Up
        configureGoogleSignUp();

        // Set up click listeners
        setupClickListeners();
    }

    private void initializeViews() {
        fullNameLayout = findViewById(R.id.fullNameLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        emailSignupLayout = findViewById(R.id.emailSignupLayout);
        passwordSignupLayout = findViewById(R.id.passwordSignupLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);

        fullNameInput = findViewById(R.id.fullNameInput);
        phoneInput = findViewById(R.id.phoneInput);
        emailSignupInput = findViewById(R.id.emailSignupInput);
        passwordSignupInput = findViewById(R.id.passwordSignupInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);

        signupButton = findViewById(R.id.signupButton);
        googleSignupButton = findViewById(R.id.googleSignupButton);
        backButton = findViewById(R.id.backButton);
    }

    private void configureGoogleSignUp() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        handleGoogleSignupResult(task);
                    }
                }
        );
    }

    private void setupClickListeners() {
        // Back button
        backButton.setOnClickListener(v -> finish());

        // Sign Up button
        signupButton.setOnClickListener(v -> signupWithEmail());

        // Google Sign Up button
        googleSignupButton.setOnClickListener(v -> signupWithGoogle());

        // Login link
        findViewById(R.id.loginLink).setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void signupWithEmail() {
        String fullName = fullNameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();
        String email = emailSignupInput.getText().toString().trim();
        String password = passwordSignupInput.getText().toString().trim();
        String confirmPassword = confirmPasswordInput.getText().toString().trim();

        // Validate inputs
        if (!validateSignupInputs(fullName, phone, email, password, confirmPassword)) {
            return;
        }

        // Show loading state
        signupButton.setEnabled(false);
        signupButton.setText("Creating Account...");

        // Create user with Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    signupButton.setEnabled(true);
                    signupButton.setText(R.string.signup_button);

                    if (task.isSuccessful()) {
                        // User created successfully
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Save user info to Firestore
                            saveUserInfo(user.getUid(), fullName, phone, email);

                            // Generate and send OTP
                            sendOTP(email);
                        }
                    } else {
                        // Sign up failed
                        String errorMessage = task.getException() != null
                            ? task.getException().getMessage()
                            : getString(R.string.error_signup_failed);

                        if (errorMessage.contains("already in use")) {
                            emailSignupLayout.setError(getString(R.string.error_user_exists));
                        }

                        Toast.makeText(SignupActivity.this, "Sign up failed: " + errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private boolean validateSignupInputs(String fullName, String phone, String email, String password, String confirmPassword) {
        boolean isValid = true;

        // Reset errors
        fullNameLayout.setError(null);
        phoneLayout.setError(null);
        emailSignupLayout.setError(null);
        passwordSignupLayout.setError(null);
        confirmPasswordLayout.setError(null);

        // Validate full name
        if (TextUtils.isEmpty(fullName)) {
            fullNameLayout.setError(getString(R.string.error_fullname_required));
            isValid = false;
        } else if (fullName.length() < 3) {
            fullNameLayout.setError(getString(R.string.error_fullname_length));
            isValid = false;
        }

        // Validate phone
        if (TextUtils.isEmpty(phone)) {
            phoneLayout.setError(getString(R.string.error_phone_required));
            isValid = false;
        } else if (!isValidPhoneNumber(phone)) {
            phoneLayout.setError(getString(R.string.error_phone_invalid));
            isValid = false;
        }

        // Validate email
        if (TextUtils.isEmpty(email)) {
            emailSignupLayout.setError(getString(R.string.error_email_required));
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailSignupLayout.setError(getString(R.string.error_invalid_email));
            isValid = false;
        }

        // Validate password
        if (TextUtils.isEmpty(password)) {
            passwordSignupLayout.setError(getString(R.string.error_password_required));
            isValid = false;
        } else if (password.length() < 6) {
            passwordSignupLayout.setError(getString(R.string.error_password_length));
            isValid = false;
        }

        // Validate confirm password
        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_password_required));
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordLayout.setError(getString(R.string.error_passwords_not_match));
            isValid = false;
        }

        return isValid;
    }

    private boolean isValidPhoneNumber(String phone) {
        // Remove any non-digit characters
        String digitsOnly = phone.replaceAll("\\D", "");
        // Check if it's 10-15 digits (international standard)
        return digitsOnly.length() >= 10 && digitsOnly.length() <= 15;
    }

    private void saveUserInfo(String userId, String fullName, String phone, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("uid", userId);
        user.put("fullName", fullName);
        user.put("phone", phone);
        user.put("email", email);
        user.put("createdAt", System.currentTimeMillis());
        user.put("emailVerified", false);

        mFirestore.collection("users").document(userId)
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    // User info saved
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(SignupActivity.this, "Error saving user info: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void sendOTP(String email) {
        // In production, integrate with a backend service for OTP generation and sending
        // For demo purposes, we'll generate a 6-digit OTP and show it

        int otp = (int) (100000 + Math.random() * 900000);
        String otpString = String.valueOf(otp);

        // Navigate to OTP verification activity
        Intent intent = new Intent(SignupActivity.this, OTPVerificationActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("otp", otpString);
        intent.putExtra("isSignup", true);
        startActivity(intent);
    }

    private void signupWithGoogle() {
        // Sign out first to allow account selection
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            googleSignupLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignupResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken(), account.getDisplayName(), account.getEmail());
        } catch (ApiException e) {
            Toast.makeText(this, "Google sign up failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken, String displayName, String email) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Save user info to Firestore
                            saveUserInfo(user.getUid(), displayName != null ? displayName : "User", "", email);

                            // For Google Sign-Up, auto-verify email and go to home
                            Toast.makeText(SignupActivity.this, getString(R.string.account_created), Toast.LENGTH_SHORT).show();
                            navigateToHome();
                        }
                    } else {
                        Toast.makeText(SignupActivity.this,
                                "Authentication failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

