package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

/**
 * SignupActivity
 * ──────────────────────────────────────────────────────────────────────────
 * Email / Password flow
 *   1. Validate inputs locally (name, phone, email, password, confirm).
 *   2. createUserWithEmailAndPassword  → Firebase Auth account created.
 *   3. updateProfile  → set display name.
 *   4. SupabaseClient.upsertUser  → save profile to Supabase (non-blocking).
 *   5. sendEmailVerification  → Firebase emails a verification link.
 *      • addOnSuccessListener  → toast + sign-out + open EmailVerification screen.
 *      • addOnFailureListener  → toast warning but still proceed to that screen.
 *   6. mAuth.signOut()  → user MUST verify before logging in.
 *   7. Start EmailVerificationActivity.
 *
 * Google Sign-In flow
 *   1. signOut first → account-chooser always appears.
 *   2. Firebase signInWithCredential.
 *   3. SupabaseClient.upsertUser  → save / update profile.
 *   4. Navigate to MainActivity (Google = already verified by Google).
 *
 * NO Firestore. NO backend server. Pure Firebase Auth + Supabase REST.
 */
public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    // ── Views ──────────────────────────────────────────────────────────────
    private TextInputLayout   fullNameLayout, phoneLayout,
                              emailSignupLayout, passwordSignupLayout,
                              confirmPasswordLayout;
    private TextInputEditText fullNameInput, phoneInput,
                              emailSignupInput, passwordSignupInput,
                              confirmPasswordInput;
    private MaterialButton    signupButton, googleSignupButton;
    private ImageView         backButton;
    private TextView          loginLink;

    // ── Auth ───────────────────────────────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupGoogleSignIn();
        setupClickListeners();
    }

    // ── View binding ───────────────────────────────────────────────────────
    private void bindViews() {
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

    // ── Google Sign-In setup ───────────────────────────────────────────────
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .requestProfile()
                        .build();

        mGoogleClient = GoogleSignIn.getClient(this, gso);

        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null) {
                        handleGoogleResult(
                                GoogleSignIn.getSignedInAccountFromIntent(
                                        result.getData()));
                    } else {
                        showBusy(false);
                        Log.w(TAG, "Google sign-up cancelled");
                    }
                });
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        if (backButton         != null) backButton.setOnClickListener(v -> finish());
        if (loginLink          != null) loginLink.setOnClickListener(v -> finish());
        if (signupButton       != null) signupButton.setOnClickListener(v -> doEmailSignup());
        if (googleSignupButton != null) googleSignupButton.setOnClickListener(v -> doGoogleSignup());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD SIGNUP
    // ══════════════════════════════════════════════════════════════════════
    private void doEmailSignup() {
        final String name    = text(fullNameInput);
        final String phone   = text(phoneInput);
        final String email   = text(emailSignupInput);
        final String pass    = text(passwordSignupInput);
        final String confirm = text(confirmPasswordInput);

        if (!validate(name, phone, email, pass, confirm)) return;

        showBusy(true);

        // Step 1 — create Firebase Auth account
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showBusy(false);
                        toast("Account created but user is null — please try logging in.");
                        return;
                    }
                    Log.d(TAG, "Account created uid=" + user.getUid());

                    // Step 2 — set display name (best-effort, non-blocking)
                    user.updateProfile(
                            new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build())
                            .addOnCompleteListener(t ->
                                    Log.d(TAG, "displayName update: "
                                            + (t.isSuccessful() ? "OK" : "failed")));

                    // Step 3 — upsert Supabase profile (non-blocking)
                    SupabaseClient.upsertUser(
                            user.getUid(), name, email, phone, "user",
                            new SupabaseClient.Callback2() {
                                @Override public void onSuccess() {
                                    sendVerificationEmail(user, email);
                                }
                                @Override public void onError(String err) {
                                    Log.w(TAG, "Supabase upsert failed (non-fatal): " + err);
                                    sendVerificationEmail(user, email);
                                }
                            });
                })
                .addOnFailureListener(this, e -> {
                    showBusy(false);
                    handleSignupError(e);
                });
    }

    /**
     * Step 4 — Send the Firebase email-verification link.
     *
     * Uses addOnSuccessListener + addOnFailureListener (more reliable than
     * addOnCompleteListener when the activity may be briefly paused).
     * Regardless of outcome: sign out + open EmailVerificationActivity.
     */
    private void sendVerificationEmail(FirebaseUser user, String email) {
        user.sendEmailVerification()
                .addOnSuccessListener(this, unused -> {
                    showBusy(false);
                    Log.d(TAG, "Verification email sent to " + email);
                    toast("✅ Verification email sent to " + email
                            + ". Check your inbox (and spam folder).");
                    mAuth.signOut();
                    openVerificationScreen(email);
                })
                .addOnFailureListener(this, e -> {
                    showBusy(false);
                    Log.w(TAG, "sendEmailVerification failed: " + e.getMessage());
                    // Still navigate — user can resend from the next screen
                    toast("⚠️ Account created but verification email could not be sent."
                            + " You can resend it on the next screen.");
                    mAuth.signOut();
                    openVerificationScreen(email);
                });
    }

    private void openVerificationScreen(String email) {
        Intent i = new Intent(this, EmailVerificationActivity.class);
        i.putExtra("email", email);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    // ── Firebase signup error mapping ──────────────────────────────────────
    private void handleSignupError(Exception ex) {
        String msg  = ex.getMessage();
        String code = (ex instanceof FirebaseAuthException)
                ? ((FirebaseAuthException) ex).getErrorCode() : "";

        Log.e(TAG, "Signup error code=" + code + " msg=" + msg);

        if ("ERROR_EMAIL_ALREADY_IN_USE".equals(code)
                || (msg != null && msg.contains("already in use"))) {
            err(emailSignupLayout, "This email is already registered. Please log in.");

        } else if ("ERROR_WEAK_PASSWORD".equals(code)
                || (msg != null && msg.contains("weak"))) {
            err(passwordSignupLayout, "Password is too weak — use at least 6 characters.");

        } else if ("ERROR_INVALID_EMAIL".equals(code)
                || (msg != null && msg.contains("badly formatted"))) {
            err(emailSignupLayout, "Invalid email address format.");

        } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)
                || (msg != null && msg.contains("operation-not-allowed"))) {
            toast("❌ Email/Password sign-up is DISABLED.\n"
                    + "Go to Firebase Console → Authentication"
                    + " → Sign-in method → Email/Password → Enable.");

        } else if (msg != null && (msg.contains("NETWORK_ERROR")
                || msg.contains("network") || msg.contains("NETWORK"))) {
            toast("No internet connection. Please check your network and try again.");

        } else {
            toast("Sign up failed: " + (msg != null ? msg : "Unknown error"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GOOGLE SIGN-UP
    // ══════════════════════════════════════════════════════════════════════
    private void doGoogleSignup() {
        showBusy(true);
        // Sign out first so the account-chooser dialog always shows
        mGoogleClient.signOut().addOnCompleteListener(this,
                t -> googleLauncher.launch(mGoogleClient.getSignInIntent()));
    }

    private void handleGoogleResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct == null) {
                showBusy(false);
                toast("Google Sign-In returned no account. Try again.");
                return;
            }
            firebaseAuthWithGoogle(acct);
        } catch (ApiException e) {
            showBusy(false);
            Log.e(TAG, "Google ApiException status=" + e.getStatusCode());
            // Status code 10 = developer_error → SHA-1 not registered
            if (e.getStatusCode() == 10) {
                toast("Google Sign-In configuration error.\n"
                        + "Ensure your app's SHA-1 fingerprint is added in\n"
                        + "Firebase Console → Project Settings → Android app.");
            } else {
                toast("Google Sign-In failed (code " + e.getStatusCode() + ").");
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential cred =
                GoogleAuthProvider.getCredential(acct.getIdToken(), null);

        mAuth.signInWithCredential(cred)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showBusy(false);
                        toast("Google Sign-In succeeded but user is null. Try again.");
                        return;
                    }

                    boolean isNew = authResult.getAdditionalUserInfo() != null
                            && Boolean.TRUE.equals(
                            authResult.getAdditionalUserInfo().isNewUser());

                    Log.d(TAG, "Google auth OK uid=" + user.getUid() + " isNew=" + isNew);

                    String gName  = acct.getDisplayName() != null
                            ? acct.getDisplayName() : "";
                    String gEmail = acct.getEmail() != null
                            ? acct.getEmail() : "";

                    SupabaseClient.upsertUser(
                            user.getUid(), gName, gEmail, "", "user",
                            new SupabaseClient.Callback2() {
                                @Override public void onSuccess() {
                                    showBusy(false);
                                    toast(isNew
                                            ? "✅ Account created with Google! Welcome to QuickFix."
                                            : "Welcome back, " + gName + "!");
                                    goHome();
                                }
                                @Override public void onError(String err) {
                                    showBusy(false);
                                    Log.w(TAG, "Supabase upsert (Google) failed: " + err);
                                    goHome(); // proceed even if profile save fails
                                }
                            });
                })
                .addOnFailureListener(this, e -> {
                    showBusy(false);
                    Log.e(TAG, "Firebase Google credential failed: " + e.getMessage());
                    toast("Google Sign-In failed: " + e.getMessage());
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VALIDATION
    // ══════════════════════════════════════════════════════════════════════
    private boolean validate(String name, String phone,
                              String email, String pass, String confirm) {
        clearErrors();
        boolean ok = true;

        if (TextUtils.isEmpty(name)) {
            err(fullNameLayout, "Full name is required.");
            ok = false;
        } else if (name.length() < 3) {
            err(fullNameLayout, "Name must be at least 3 characters.");
            ok = false;
        }

        if (TextUtils.isEmpty(phone)) {
            err(phoneLayout, "Phone number is required.");
            ok = false;
        } else if (!phone.matches("\\d{10,15}")) {
            err(phoneLayout, "Enter a valid 10–15 digit phone number.");
            ok = false;
        }

        if (TextUtils.isEmpty(email)) {
            err(emailSignupLayout, "Email address is required.");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            err(emailSignupLayout, "Enter a valid email address.");
            ok = false;
        }

        if (TextUtils.isEmpty(pass)) {
            err(passwordSignupLayout, "Password is required.");
            ok = false;
        } else if (pass.length() < 6) {
            err(passwordSignupLayout, "Password must be at least 6 characters.");
            ok = false;
        }

        if (TextUtils.isEmpty(confirm)) {
            err(confirmPasswordLayout, "Please confirm your password.");
            ok = false;
        } else if (!pass.equals(confirm)) {
            err(confirmPasswordLayout, "Passwords do not match.");
            ok = false;
        }

        return ok;
    }

    private void clearErrors() {
        err(fullNameLayout, null);
        err(phoneLayout, null);
        err(emailSignupLayout, null);
        err(passwordSignupLayout, null);
        err(confirmPasswordLayout, null);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private String text(TextInputEditText v) {
        return (v != null && v.getText() != null)
                ? v.getText().toString().trim() : "";
    }

    private void err(TextInputLayout l, String msg) {
        if (l != null) l.setError(msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void showBusy(boolean busy) {
        if (signupButton       != null) {
            signupButton.setEnabled(!busy);
            signupButton.setText(busy ? "Creating Account…" : "Sign Up");
        }
        if (googleSignupButton != null) googleSignupButton.setEnabled(!busy);
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
