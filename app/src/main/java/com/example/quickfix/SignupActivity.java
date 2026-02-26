package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
 * SignupActivity — Email/Password account creation + Google Sign-Up.
 *
 * Email flow:
 *   1. Validate → createUserWithEmailAndPassword → setDisplayName
 *   2. upsertUser in Supabase (is_user=true, non-blocking)
 *   3. sendEmailVerification → sign out → EmailVerificationActivity
 *
 * Google flow:
 *   1. Google account chooser → Firebase credential
 *   2. If new: upsertUser in Supabase, then role-navigate
 *   3. If existing: role-navigate directly (don't overwrite role)
 *
 * Role routing same as LoginActivity.
 */
public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout   fullNameLayout, phoneLayout,
                              emailSignupLayout, passwordSignupLayout,
                              confirmPasswordLayout;
    private TextInputEditText fullNameInput, phoneInput,
                              emailSignupInput, passwordSignupInput,
                              confirmPasswordInput;
    private MaterialButton    signupButton, googleSignupButton;
    private ImageView         backButton;
    private TextView          loginLink;

    // Loading overlay
    private LinearLayout loadingOverlay;
    private ProgressBar  loadingSpinner;
    private TextView     loadingMessage;

    // ── Auth ──────────────────────────────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    private boolean isLoading    = false;
    private boolean isNavigating = false;

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

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loadingMessage = findViewById(R.id.loadingMessage);
    }

    // ── Google Sign-In setup ───────────────────────────────────────────────
    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();

        mGoogleClient = GoogleSignIn.getClient(this, gso);

        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleGoogleResult(
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData()));
                    } else {
                        resetLoadingState();
                        Log.w(TAG, "Google sign-up cancelled or no data");
                    }
                });
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private void setupClickListeners() {
        if (backButton  != null) backButton.setOnClickListener(v -> { if (!isLoading) finish(); });
        if (loginLink   != null) loginLink.setOnClickListener(v ->  { if (!isLoading) finish(); });
        if (signupButton!= null) signupButton.setOnClickListener(v -> doEmailSignup());
        if (googleSignupButton != null)
            googleSignupButton.setOnClickListener(v -> doGoogleSignup());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD SIGNUP
    // ══════════════════════════════════════════════════════════════════════
    private void doEmailSignup() {
        if (isLoading) return;

        final String name    = text(fullNameInput);
        final String phone   = text(phoneInput);
        final String email   = text(emailSignupInput);
        final String pass    = text(passwordSignupInput);
        final String confirm = text(confirmPasswordInput);

        if (!validate(name, phone, email, pass, confirm)) return;

        // Fast-fail: show a clear message if there's no internet at all
        if (!NetworkHelper.isOnline(this)) {
            toast("No internet connection. Please enable WiFi or mobile data and try again.");
            return;
        }

        isLoading = true;
        setButtonsEnabled(false);
        showOverlay("Creating your account…");

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        resetLoadingState();
                        toast("Account creation failed — please try again.");
                        return;
                    }
                    Log.d(TAG, "Account created uid=" + user.getUid());

                    // Set display name (non-blocking)
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build())
                            .addOnCompleteListener(t ->
                                    Log.d(TAG, "displayName=" + (t.isSuccessful() ? "OK" : "failed")));

                    // Save to Supabase then send verification email
                    updateOverlay("Saving your profile…");
                    SupabaseClient.upsertUser(
                            user.getUid(), name, email, phone,
                            true, false, false,
                            new SupabaseClient.Callback2() {
                                @Override public void onSuccess() {
                                    updateOverlay("Sending verification email…");
                                    sendVerificationEmail(user, email);
                                }
                                @Override public void onError(String err) {
                                    Log.w(TAG, "Supabase upsert failed (non-fatal): " + err);
                                    updateOverlay("Sending verification email…");
                                    sendVerificationEmail(user, email);
                                }
                            });
                })
                .addOnFailureListener(this, e -> {
                    resetLoadingState();
                    handleSignupError(e);
                });
    }

    private void sendVerificationEmail(FirebaseUser user, String email) {
        user.sendEmailVerification()
                .addOnCompleteListener(this, task -> {
                    resetLoadingState();
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Verification email sent to " + email);
                        toast("✅ Verification email sent to " + email + ". Check your inbox.");
                    } else {
                        Log.w(TAG, "sendEmailVerification failed");
                        toast("⚠️ Account created but verification email could not be sent.");
                    }
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
            toast("Email/Password sign-up is disabled. Enable it in Firebase Console.");
        } else if (msg != null && (msg.contains("NETWORK_ERROR")
                || msg.contains("network") || msg.contains("NETWORK"))) {
            if (!NetworkHelper.isOnline(this)) {
                toast("No internet connection. Please enable WiFi or mobile data and try again.");
            } else {
                toast("Unable to reach server. This may be a network provider (ISP) or DNS issue. "
                        + "Please try switching to mobile data, using a VPN, or try again later.");
            }
        } else {
            toast("Sign up failed: " + (msg != null ? msg : "Unknown error"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GOOGLE SIGN-UP
    // ══════════════════════════════════════════════════════════════════════
    private void doGoogleSignup() {
        if (isLoading) return;

        // Fast-fail: show a clear message if there's no internet at all
        if (!NetworkHelper.isOnline(this)) {
            toast("No internet connection. Please enable WiFi or mobile data and try again.");
            return;
        }

        isLoading = true;
        setButtonsEnabled(false);
        showOverlay("Connecting to Google…");

        mGoogleClient.signOut().addOnCompleteListener(this,
                t -> googleLauncher.launch(mGoogleClient.getSignInIntent()));
    }

    private void handleGoogleResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct == null) {
                resetLoadingState();
                toast("Google Sign-In returned no account. Try again.");
                return;
            }
            updateOverlay("Verifying with Firebase…");
            firebaseAuthWithGoogle(acct);
        } catch (ApiException e) {
            resetLoadingState();
            Log.e(TAG, "Google ApiException status=" + e.getStatusCode());
            if (e.getStatusCode() == 10) {
                toast("Google Sign-In config error. Check SHA-1 in Firebase Console.");
            } else {
                toast("Google Sign-In failed (code " + e.getStatusCode() + ").");
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential cred = GoogleAuthProvider.getCredential(acct.getIdToken(), null);

        mAuth.signInWithCredential(cred)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        resetLoadingState();
                        toast("Google Sign-In succeeded but user is null. Try again.");
                        return;
                    }

                    boolean isNew = authResult.getAdditionalUserInfo() != null
                            && Boolean.TRUE.equals(
                                    authResult.getAdditionalUserInfo().isNewUser());

                    String gName  = acct.getDisplayName() != null ? acct.getDisplayName() : "";
                    String gEmail = acct.getEmail() != null ? acct.getEmail()
                            : (user.getEmail() != null ? user.getEmail() : "");

                    Log.d(TAG, "Google auth OK uid=" + user.getUid()
                            + " isNew=" + isNew + " email=" + gEmail);

                    if (isNew) {
                        updateOverlay("Creating your profile…");
                        SupabaseClient.upsertUser(
                                user.getUid(), gName, gEmail, "",
                                true, false, false,
                                new SupabaseClient.Callback2() {
                                    @Override public void onSuccess() {
                                        Log.d(TAG, "Supabase profile saved: " + gEmail);
                                        updateOverlay("Almost done…");
                                        checkRoleAndNavigate(user.getUid(), gEmail, true, gName);
                                    }
                                    @Override public void onError(String err) {
                                        Log.w(TAG, "Supabase save failed (non-fatal): " + err);
                                        updateOverlay("Almost done…");
                                        checkRoleAndNavigate(user.getUid(), gEmail, true, gName);
                                    }
                                });
                    } else {
                        updateOverlay("Checking your account…");
                        checkRoleAndNavigate(user.getUid(), gEmail, false, gName);
                    }
                })
                .addOnFailureListener(this, e -> {
                    resetLoadingState();
                    Log.e(TAG, "Firebase Google credential failed: " + e.getMessage());
                    toast("Google Sign-In failed: " + e.getMessage());
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROLE-BASED NAVIGATION
    // ══════════════════════════════════════════════════════════════════════
    private void checkRoleAndNavigate(String firebaseUid, String email,
                                       boolean isNew, String displayName) {
        if (isNavigating) return;

        String safeEmail = (email != null && !email.isEmpty()) ? email : "";
        if (safeEmail.isEmpty()) {
            FirebaseUser u = mAuth.getCurrentUser();
            if (u != null && u.getEmail() != null) safeEmail = u.getEmail();
        }
        final String finalEmail = safeEmail;

        Log.d(TAG, "checkRoleAndNavigate uid=" + firebaseUid + " email=" + finalEmail);
        updateOverlay("Checking your account…");

        SupabaseClient.getUserRoleWithEmail(firebaseUid, finalEmail,
                new SupabaseClient.RoleInfoCallback() {
            @Override
            public void onSuccess(SupabaseClient.RoleInfo info) {
                if (isNavigating || isFinishing()) return;
                isNavigating = true;
                hideOverlay();

                Log.d(TAG, "Role → admin=" + info.isAdmin
                        + " provider=" + info.isServiceProvider + " email=" + finalEmail);

                String welcomeMsg = isNew
                        ? "✅ Welcome to QuickFix, " + firstName(displayName) + "!"
                        : "Welcome back, " + firstName(displayName) + "!";
                toast(welcomeMsg);

                final Intent target;
                if (info.isAdmin) {
                    target = new Intent(SignupActivity.this, AdminDashboardActivity.class);
                } else if (info.isServiceProvider) {
                    target = new Intent(SignupActivity.this, ProviderDashboardActivity.class);
                } else {
                    target = new Intent(SignupActivity.this, MainActivity.class);
                }
                target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(target);
                finish();
            }

            @Override
            public void onError(String message) {
                if (isFinishing()) return;
                resetLoadingState();
                Log.e(TAG, "Role fetch failed: " + message);

                if ("USER_NOT_FOUND".equals(message)) {
                    toast("Account created in Firebase but profile not found in database. "
                            + "Please try logging in.");
                    mAuth.signOut();
                } else {
                    new AlertDialog.Builder(SignupActivity.this)
                            .setTitle("Connection Error")
                            .setMessage("Could not load your account details.\n\n"
                                    + "Please check your internet and try again.")
                            .setPositiveButton("Retry", (d, w) -> {
                                isNavigating = false;
                                isLoading    = true;
                                showOverlay("Retrying…");
                                setButtonsEnabled(false);
                                checkRoleAndNavigate(firebaseUid, finalEmail, isNew, displayName);
                            })
                            .setNegativeButton("Cancel", (d, w) -> mAuth.signOut())
                            .setCancelable(false)
                            .show();
                }
            }
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
        err(fullNameLayout, null); err(phoneLayout, null);
        err(emailSignupLayout, null); err(passwordSignupLayout, null);
        err(confirmPasswordLayout, null);
    }

    // ── Loading helpers ────────────────────────────────────────────────────

    private void showOverlay(String message) {
        if (loadingOverlay != null) {
            if (loadingMessage != null) loadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void updateOverlay(String message) {
        if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE
                && loadingMessage != null) {
            loadingMessage.setText(message);
        }
    }

    private void hideOverlay() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void resetLoadingState() {
        isLoading = false;
        hideOverlay();
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        if (signupButton       != null) signupButton.setEnabled(enabled);
        if (googleSignupButton != null) googleSignupButton.setEnabled(enabled);
    }

    // ── Misc helpers ───────────────────────────────────────────────────────

    private String text(TextInputEditText v) {
        return (v != null && v.getText() != null) ? v.getText().toString().trim() : "";
    }

    private void err(TextInputLayout l, String msg) {
        if (l != null) l.setError(msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "there";
        return fullName.trim().split("\\s+")[0];
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideOverlay();
    }
}
