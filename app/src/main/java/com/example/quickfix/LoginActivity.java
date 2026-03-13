package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
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

/**
 * LoginActivity — Email/Password + Google Sign-In with role-based navigation.
 *
 * Role routing (from Supabase):
 *   is_admin = true          → AdminDashboardActivity
 *   is_service_provider = true → ProviderDashboardActivity
 *   otherwise                → MainActivity  (regular user home)
 *
 * Loading UX:
 *   - Email login  : loginButton changes text + disabled, overlay shown
 *   - Google login : googleSignInButton disabled, overlay shown with status text
 *   - Both buttons disabled while ANY operation is running (isLoading flag)
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout   emailLayout, passwordLayout;
    private TextInputEditText emailInput,  passwordInput;
    private MaterialButton    loginButton, googleSignInButton;
    private TextView          forgotPasswordLink, signUpLink;

    // Loading overlay views (full-screen dimmed overlay with spinner + message)
    private LinearLayout loadingOverlay;
    private ProgressBar  loadingSpinner;
    private TextView     loadingMessage;

    // ── Auth ──────────────────────────────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    // Guard: prevent double-navigation if user taps fast
    private boolean isNavigating = false;
    // Guard: prevent re-entry into checkRoleAndNavigate
    private boolean isLoading    = false;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupGoogleSignIn();
        setupClickListeners();

        // Auto-login: if already signed in and verified, skip login screen
        FirebaseUser current = mAuth.getCurrentUser();
        if (current != null) {
            // Google users are always considered verified; email users need the flag
            boolean alreadyVerified = current.isEmailVerified()
                    || isGoogleUser(current);
            if (alreadyVerified) {
                showOverlay("Signing you in…");
                checkRoleAndNavigate(current.getUid(), current.getEmail());
            }
        }
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        emailLayout    = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput     = findViewById(R.id.emailInput);
        passwordInput  = findViewById(R.id.passwordInput);
        loginButton    = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        signUpLink         = findViewById(R.id.signUpLink);

        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        loadingMessage = findViewById(R.id.loadingMessage);
    }

    // ── Google Sign-In ────────────────────────────────────────────────────

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
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleResult(task);
                    } else {
                        hideOverlay();
                        setButtonsEnabled(true);
                        Log.w(TAG, "Google sign-in cancelled or no data");
                    }
                });
    }

    // ── Click listeners ───────────────────────────────────────────────────

    private void setupClickListeners() {
        if (loginButton != null)
            loginButton.setOnClickListener(v -> doEmailLogin());

        if (googleSignInButton != null)
            googleSignInButton.setOnClickListener(v -> doGoogleLogin());

        if (forgotPasswordLink != null)
            forgotPasswordLink.setOnClickListener(v -> {
                if (!isLoading && !isFinishing())
                    startActivity(new Intent(this, ForgotPasswordActivity.class));
            });

        if (signUpLink != null)
            signUpLink.setOnClickListener(v -> {
                if (!isLoading && !isFinishing())
                    startActivity(new Intent(this, SignupActivity.class));
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD LOGIN
    // ══════════════════════════════════════════════════════════════════════

    private void doEmailLogin() {
        if (isLoading) return;

        String email = text(emailInput);
        String pass  = text(passwordInput);
        if (!validateInputs(email, pass)) return;

        isLoading = true;
        clearErrors();
        showOverlay("Signing in…");
        setButtonsEnabled(false);

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        resetLoadingState();
                        toast("Login failed — please try again.");
                        return;
                    }

                    // Google provider users are always verified
                    if (user.isEmailVerified() || isGoogleUser(user)) {
                        updateOverlay("Checking your account…");
                        checkRoleAndNavigate(user.getUid(), user.getEmail());
                    } else {
                        // Fresh reload to get latest verification status
                        user.reload().addOnCompleteListener(this, t -> {
                            FirebaseUser fresh = mAuth.getCurrentUser();
                            if (fresh != null && (fresh.isEmailVerified() || isGoogleUser(fresh))) {
                                updateOverlay("Checking your account…");
                                checkRoleAndNavigate(fresh.getUid(), fresh.getEmail());
                            } else {
                                resetLoadingState();
                                showVerificationDialog(fresh != null ? fresh : user);
                            }
                        });
                    }
                })
                .addOnFailureListener(this, e -> {
                    resetLoadingState();
                    handleLoginError(e);
                });
    }

    private void handleLoginError(Exception ex) {
        if (ex == null) { toast("Login failed. Please try again."); return; }

        String msg  = ex.getMessage();
        String code = (ex instanceof FirebaseAuthException)
                ? ((FirebaseAuthException) ex).getErrorCode() : "";

        Log.e(TAG, "Login error code=" + code + " msg=" + msg);

        if ("ERROR_USER_NOT_FOUND".equals(code)
                || (msg != null && msg.contains("no user record"))) {
            err(emailLayout, "No account found with this email.");

        } else if ("ERROR_WRONG_PASSWORD".equals(code)
                || (msg != null && (msg.contains("password is invalid")
                || msg.contains("INVALID_LOGIN_CREDENTIALS")))) {
            err(passwordLayout, "Incorrect password. Try Forgot Password.");

        } else if ("ERROR_INVALID_EMAIL".equals(code)
                || (msg != null && msg.contains("badly formatted"))) {
            err(emailLayout, "Invalid email address format.");

        } else if ("ERROR_TOO_MANY_REQUESTS".equals(code)
                || (msg != null && msg.contains("too many"))) {
            toast("Too many failed attempts. Try again later or reset your password.");

        } else if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
            toast("Email/password login is not enabled. Enable it in Firebase Console.");

        } else if (msg != null && (msg.contains("NETWORK") || msg.contains("network"))) {
            if (!NetworkHelper.isOnline(this)) {
                toast("No internet connection. Please enable WiFi or mobile data and try again.");
            } else {
                toast("Unable to reach server. This may be a network provider (ISP) or DNS issue. "
                        + "Please try switching to mobile data, using a VPN, or try again later.");
            }
        } else {
            toast("Login failed: " + (msg != null ? msg : "Unknown error"));
        }
    }

    private void showVerificationDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Email Not Verified")
                .setMessage("Please verify your email before logging in.\n\n"
                        + "1. Check your inbox (and spam folder).\n"
                        + "2. Click the verification link.\n"
                        + "3. Tap \"I've Verified\" to continue.")
                .setPositiveButton("Resend Email", (d, w) -> {
                    if (user == null) { mAuth.signOut(); return; }
                    user.sendEmailVerification()
                            .addOnSuccessListener(this, unused ->
                                    toast("✅ Verification email sent! Check your inbox."))
                            .addOnFailureListener(this, e ->
                                    toast("Could not resend: " + e.getMessage()));
                    mAuth.signOut();
                })
                .setNeutralButton("I've Verified", (d, w) -> {
                    if (user == null) { mAuth.signOut(); return; }
                    showOverlay("Checking verification…");
                    setButtonsEnabled(false);
                    isLoading = true;
                    user.reload().addOnCompleteListener(this, t -> {
                        FirebaseUser r = mAuth.getCurrentUser();
                        if (r != null && r.isEmailVerified()) {
                            updateOverlay("Checking your account…");
                            checkRoleAndNavigate(r.getUid(), r.getEmail());
                        } else {
                            resetLoadingState();
                            toast("Email still not verified. Click the link in your inbox.");
                            mAuth.signOut();
                        }
                    });
                })
                .setNegativeButton("Cancel", (d, w) -> mAuth.signOut())
                .setCancelable(false)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GOOGLE SIGN-IN
    // ══════════════════════════════════════════════════════════════════════

    private void doGoogleLogin() {
        if (isLoading) return;

        isLoading = true;
        setButtonsEnabled(false);
        showOverlay("Connecting to Google…");

        // Always sign out first so account chooser appears
        mGoogleClient.signOut().addOnCompleteListener(this,
                t -> googleLauncher.launch(mGoogleClient.getSignInIntent()));
    }

    private void handleGoogleResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct == null) {
                resetLoadingState();
                toast("Google Sign-In failed: no account returned.");
                return;
            }
            updateOverlay("Verifying…");
            firebaseWithGoogle(acct);
        } catch (ApiException e) {
            resetLoadingState();
            Log.e(TAG, "Google ApiException code=" + e.getStatusCode());
            if (e.getStatusCode() == 10) {
                toast("Google Sign-In config error. Check SHA-1 in Firebase Console.");
            } else {
                toast("Google Sign-In failed (code " + e.getStatusCode() + ").");
            }
        }
    }

    private void firebaseWithGoogle(GoogleSignInAccount acct) {
        AuthCredential cred = GoogleAuthProvider.getCredential(acct.getIdToken(), null);

        mAuth.signInWithCredential(cred)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        resetLoadingState();
                        toast("Google login succeeded but user is null. Try again.");
                        return;
                    }

                    boolean isNew = authResult.getAdditionalUserInfo() != null
                            && Boolean.TRUE.equals(
                                    authResult.getAdditionalUserInfo().isNewUser());

                    String name  = acct.getDisplayName() != null ? acct.getDisplayName() : "";
                    String email = acct.getEmail()        != null ? acct.getEmail()        : user.getEmail() != null ? user.getEmail() : "";

                    Log.d(TAG, "Google auth OK uid=" + user.getUid()
                            + " isNew=" + isNew + " email=" + email);

                    if (isNew) {
                        updateOverlay("Creating your profile…");
                        SupabaseClient.upsertUser(
                                user.getUid(), name, email, "",
                                true, false, false,
                                new SupabaseClient.Callback2() {
                                    @Override public void onSuccess() {
                                        updateOverlay("Almost done…");
                                        checkRoleAndNavigate(user.getUid(), email);
                                    }
                                    @Override public void onError(String err) {
                                        Log.w(TAG, "Supabase save failed (non-fatal): " + err);
                                        updateOverlay("Almost done…");
                                        // Still navigate — Firebase auth succeeded
                                        checkRoleAndNavigate(user.getUid(), email);
                                    }
                                });
                    } else {
                        updateOverlay("Checking your account…");
                        checkRoleAndNavigate(user.getUid(), email);
                    }
                })
                .addOnFailureListener(this, e -> {
                    resetLoadingState();
                    Log.e(TAG, "Google Firebase auth failed: " + e.getMessage());
                    toast("Google Sign-In failed: " + e.getMessage());
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ROLE-BASED NAVIGATION  — core routing logic
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Queries Supabase for is_admin / is_service_provider / is_user using
     * the user's email (primary) with firebase_uid as fallback, then
     * navigates to the correct activity.
     *
     * Priority:
     *   is_admin = true           → AdminDashboardActivity
     *   is_service_provider = true → ProviderDashboardActivity
     *   else                       → MainActivity
     *
     * Errors (missing record / network failure) are shown to the user —
     * the app NEVER silently opens the wrong screen.
     */
    private void checkRoleAndNavigate(String firebaseUid, String email) {
        if (isNavigating) return;

        // Always pass a non-null email so the email-first path in SupabaseClient works
        String safeEmail = (email != null && !email.isEmpty()) ? email : "";
        if (safeEmail.isEmpty()) {
            // Last resort: try to get email from current Firebase user
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

                Log.d(TAG, "Role resolved → admin=" + info.isAdmin
                        + " provider=" + info.isServiceProvider
                        + " user=" + info.isUser
                        + " | email=" + finalEmail);

                AppSettings.setLastKnownAdminRole(LoginActivity.this, info.isAdmin);

                // ── Priority routing ──────────────────────────────────
                final Intent target;
                if (info.isAdmin) {
                    Log.d(TAG, "→ AdminDashboardActivity");
                    target = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                } else if (info.isServiceProvider) {
                    Log.d(TAG, "→ ProviderDashboardActivity");
                    target = new Intent(LoginActivity.this, ProviderDashboardActivity.class);
                } else {
                    Log.d(TAG, "→ MainActivity");
                    target = new Intent(LoginActivity.this, MainActivity.class);
                }
                target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(target);
                finish();
            }

            @Override
            public void onError(String message) {
                // Do NOT navigate anywhere on error — show the problem to the user
                if (isFinishing()) return;
                resetLoadingState();
                Log.e(TAG, "Role fetch failed: " + message);

                if ("USER_NOT_FOUND".equals(message)) {
                    // Account exists in Firebase but not in Supabase users table
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Account Not Found")
                            .setMessage("Your account was authenticated but your profile "
                                    + "was not found in our database.\n\n"
                                    + "Please sign up again or contact support.")
                            .setPositiveButton("Sign Up", (d, w) -> {
                                mAuth.signOut();
                                startActivity(new Intent(LoginActivity.this, SignupActivity.class));
                            })
                            .setNegativeButton("Cancel", (d, w) -> mAuth.signOut())
                            .setCancelable(false)
                            .show();
                } else {
                    // Network error or unexpected failure
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Connection Error")
                            .setMessage("Could not load your account details.\n\n"
                                    + "Please check your internet connection and try again.")
                            .setPositiveButton("Retry", (d, w) -> {
                                isNavigating = false;
                                isLoading    = true;
                                showOverlay("Retrying…");
                                setButtonsEnabled(false);
                                checkRoleAndNavigate(firebaseUid, finalEmail);
                            })
                            .setNegativeButton("Cancel", (d, w) -> mAuth.signOut())
                            .setCancelable(false)
                            .show();
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOADING OVERLAY HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private void showOverlay(String message) {
        if (loadingOverlay != null) {
            if (loadingMessage != null) loadingMessage.setText(message);
            loadingOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void updateOverlay(String message) {
        if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
            if (loadingMessage != null) loadingMessage.setText(message);
        }
    }

    private void hideOverlay() {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    /** Re-enable both buttons and hide the overlay. Call on any failure path. */
    private void resetLoadingState() {
        isLoading = false;
        hideOverlay();
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        if (loginButton       != null) loginButton.setEnabled(enabled);
        if (googleSignInButton!= null) googleSignInButton.setEnabled(enabled);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  VALIDATION & HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private boolean validateInputs(String email, String pass) {
        clearErrors();
        boolean ok = true;
        if (TextUtils.isEmpty(email)) {
            err(emailLayout, "Email address is required.");
            ok = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            err(emailLayout, "Enter a valid email address.");
            ok = false;
        }
        if (TextUtils.isEmpty(pass)) {
            err(passwordLayout, "Password is required.");
            ok = false;
        }
        return ok;
    }

    private void clearErrors() {
        err(emailLayout, null);
        err(passwordLayout, null);
    }

    private String text(TextInputEditText v) {
        return (v != null && v.getText() != null) ? v.getText().toString().trim() : "";
    }

    private void err(TextInputLayout l, String msg) {
        if (l != null) l.setError(msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /** Returns true if the user authenticated via Google provider */
    private boolean isGoogleUser(FirebaseUser user) {
        if (user == null) return false;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())) return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Prevent window leaked if activity destroyed while loading
        hideOverlay();
    }
}
