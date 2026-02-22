package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
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
 * LoginActivity
 * ──────────────────────────────────────────────────────────────────────────
 * Email / Password flow
 *   1. signInWithEmailAndPassword.
 *   2. user.reload() to get the LATEST isEmailVerified flag from Firebase.
 *   3. If verified → MainActivity.
 *   4. If NOT verified → dialog offering Resend or "I've verified" re-check.
 *
 * Google Sign-In flow
 *   1. Google One-Tap → Firebase credential → upsert Supabase → MainActivity.
 *      (Google accounts are always email-verified in Firebase.)
 *
 * NO Firestore. NO backend.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private TextInputLayout   emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton    loginButton, googleSignInButton;
    private TextView          forgotPasswordLink, signUpLink;

    // ── Firebase / Google ─────────────────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient mGoogleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        bindViews();
        setupGoogleSignIn();
        setupClickListeners();

        // If already signed-in + verified, skip login screen entirely
        FirebaseUser current = mAuth.getCurrentUser();
        if (current != null) {
            current.reload()
                    .addOnSuccessListener(t -> {
                        FirebaseUser u = mAuth.getCurrentUser();
                        if (u != null && u.isEmailVerified()) {
                            goHome();
                        }
                    })
                    // Silently ignore reload failure on startup — show login normally
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Startup reload failed (network?): " + e.getMessage()));
        }
    }

    // ── View binding ──────────────────────────────────────────────────────

    private void bindViews() {
        emailLayout        = findViewById(R.id.emailLayout);
        passwordLayout     = findViewById(R.id.passwordLayout);
        emailInput         = findViewById(R.id.emailInput);
        passwordInput      = findViewById(R.id.passwordInput);
        loginButton        = findViewById(R.id.loginButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        signUpLink         = findViewById(R.id.signUpLink);
    }

    // ── Google Sign-In setup ──────────────────────────────────────────────

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
                        Task<GoogleSignInAccount> task =
                                GoogleSignIn.getSignedInAccountFromIntent(
                                        result.getData());
                        handleGoogleResult(task);
                    } else {
                        showBusy(false);
                        Log.w(TAG, "Google sign-in cancelled");
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
                if (!isFinishing())
                    startActivity(new Intent(this, ForgotPasswordActivity.class));
            });

        if (signUpLink != null)
            signUpLink.setOnClickListener(v -> {
                if (!isFinishing())
                    startActivity(new Intent(this, SignupActivity.class));
            });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD LOGIN
    // ══════════════════════════════════════════════════════════════════════

    private void doEmailLogin() {
        String email = text(emailInput);
        String pass  = text(passwordInput);

        if (!validateInputs(email, pass)) return;

        showBusy(true);

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showBusy(false);
                        toast("Login failed — please try again.");
                        return;
                    }
                    // reload() fetches the LATEST isEmailVerified from Firebase
                    // servers — the local SDK cache can be stale after signup.
                    user.reload()
                            .addOnSuccessListener(this, unused -> {
                                showBusy(false);
                                FirebaseUser fresh = mAuth.getCurrentUser();
                                if (fresh != null && fresh.isEmailVerified()) {
                                    Log.d(TAG, "Login OK — email verified");
                                    toast("Login successful!");
                                    goHome();
                                } else {
                                    Log.w(TAG, "Login OK — email NOT verified");
                                    showVerificationDialog(fresh);
                                }
                            })
                            .addOnFailureListener(this, e -> {
                                showBusy(false);
                                Log.e(TAG, "reload() failed: " + e.getMessage());
                                // reload failed (network?) — check cached value
                                FirebaseUser cached = mAuth.getCurrentUser();
                                if (cached != null && cached.isEmailVerified()) {
                                    toast("Login successful!");
                                    goHome();
                                } else {
                                    toast("Could not verify email status. "
                                            + "Check your internet connection.");
                                    mAuth.signOut();
                                }
                            });
                })
                .addOnFailureListener(this, e -> {
                    showBusy(false);
                    handleLoginError(e);
                });
    }

    // ──────────────────────────────────────────────────────────────────────

    private void handleLoginError(Exception ex) {
        if (ex == null) { toast("Login failed. Please try again."); return; }

        String msg  = ex.getMessage();
        String code = (ex instanceof FirebaseAuthException)
                ? ((FirebaseAuthException) ex).getErrorCode() : "";

        Log.e(TAG, "Login error code=" + code + " msg=" + msg);

        if (code.equals("ERROR_USER_NOT_FOUND")
                || (msg != null && msg.contains("no user record"))) {
            err(emailLayout,
                    "No account found with this email. Please sign up.");

        } else if (code.equals("ERROR_WRONG_PASSWORD")
                || (msg != null && (msg.contains("password is invalid")
                || msg.contains("INVALID_LOGIN_CREDENTIALS")))) {
            err(passwordLayout,
                    "Incorrect password. Try again or use Forgot Password.");

        } else if (code.equals("ERROR_INVALID_EMAIL")
                || (msg != null && msg.contains("badly formatted"))) {
            err(emailLayout, "Invalid email address format.");

        } else if (code.equals("ERROR_TOO_MANY_REQUESTS")
                || (msg != null && msg.contains("too many"))) {
            toast("Too many failed attempts. Account temporarily blocked."
                    + " Try again later or reset your password.");

        } else if (code.equals("ERROR_OPERATION_NOT_ALLOWED")) {
            toast("Email/password login is not enabled. "
                    + "Enable it in Firebase Console → Authentication "
                    + "→ Sign-in method → Email/Password.");

        } else if (msg != null && (msg.contains("NETWORK")
                || msg.contains("network"))) {
            toast("Network error. Check your internet connection.");

        } else {
            toast("Login failed: " + (msg != null ? msg : "Unknown error"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────

    private void showVerificationDialog(FirebaseUser user) {
        new AlertDialog.Builder(this)
                .setTitle("Email Not Verified")
                .setMessage("Your email has not been verified yet.\n\n"
                        + "1. Check your inbox (and spam folder) for the verification link.\n"
                        + "2. Click the link to verify.\n"
                        + "3. Tap \"I've Verified\" to continue.")
                .setPositiveButton("Resend Email", (d, w) -> {
                    if (user == null) { mAuth.signOut(); return; }
                    user.sendEmailVerification()
                            .addOnSuccessListener(this, unused -> {
                                Log.d(TAG, "Verification email resent");
                                toast("✅ Verification email sent! Check your inbox.");
                                mAuth.signOut();
                            })
                            .addOnFailureListener(this, e -> {
                                Log.e(TAG, "Resend verification failed: " + e.getMessage());
                                String msg = e.getMessage();
                                if (msg != null && msg.contains("too many")) {
                                    toast("Too many requests. Wait a few minutes and try again.");
                                } else {
                                    toast("Could not resend: "
                                            + (msg != null ? msg : "Unknown error"));
                                }
                                mAuth.signOut();
                            });
                })
                .setNeutralButton("I've Verified", (d, w) -> {
                    if (user == null) { mAuth.signOut(); return; }
                    showBusy(true);
                    user.reload()
                            .addOnSuccessListener(this, unused -> {
                                showBusy(false);
                                FirebaseUser r = mAuth.getCurrentUser();
                                if (r != null && r.isEmailVerified()) {
                                    Log.d(TAG, "Post-dialog reload: now verified");
                                    toast("✅ Email verified! Logging you in…");
                                    goHome();
                                } else {
                                    Log.w(TAG, "Post-dialog reload: still not verified");
                                    toast("Email still not verified. "
                                            + "Click the link in your inbox first.");
                                    mAuth.signOut();
                                }
                            })
                            .addOnFailureListener(this, e -> {
                                showBusy(false);
                                Log.e(TAG, "reload() in dialog failed: " + e.getMessage());
                                toast("Could not check verification status. "
                                        + "Check your internet and try logging in again.");
                                mAuth.signOut();
                            });
                })
                .setNegativeButton("Cancel", (d, w) -> mAuth.signOut())
                .setCancelable(false)
                .show();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  GOOGLE LOGIN
    // ══════════════════════════════════════════════════════════════════════

    private void doGoogleLogin() {
        showBusy(true);
        mGoogleClient.signOut().addOnCompleteListener(this,
                t -> googleLauncher.launch(mGoogleClient.getSignInIntent()));
    }

    private void handleGoogleResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            if (acct == null) {
                showBusy(false);
                toast("Google Sign-In failed: no account returned.");
                return;
            }
            firebaseWithGoogle(acct);
        } catch (ApiException e) {
            showBusy(false);
            Log.e(TAG, "Google ApiException code=" + e.getStatusCode());
            toast("Google Sign-In failed (code " + e.getStatusCode()
                    + "). Ensure SHA-1 is registered in Firebase Console.");
        }
    }

    private void firebaseWithGoogle(GoogleSignInAccount acct) {
        AuthCredential cred =
                GoogleAuthProvider.getCredential(acct.getIdToken(), null);

        mAuth.signInWithCredential(cred)
                .addOnSuccessListener(this, authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        showBusy(false);
                        toast("Google login succeeded but user is null. Try again.");
                        return;
                    }

                    String name  = acct.getDisplayName() != null
                            ? acct.getDisplayName() : "";
                    String email = acct.getEmail() != null
                            ? acct.getEmail() : "";

                    Log.d(TAG, "Google auth OK uid=" + user.getUid());

                    // Upsert Supabase profile (non-blocking)
                    SupabaseClient.upsertUser(
                            user.getUid(), name, email, "", "user",
                            new SupabaseClient.Callback2() {
                                @Override public void onSuccess() {
                                    showBusy(false);
                                    toast("Login successful!");
                                    goHome();
                                }
                                @Override public void onError(String e) {
                                    showBusy(false);
                                    Log.w(TAG, "Supabase upsert failed (non-fatal): " + e);
                                    goHome(); // proceed regardless
                                }
                            });
                })
                .addOnFailureListener(this, e -> {
                    showBusy(false);
                    Log.e(TAG, "Google Firebase auth failed: " + e.getMessage());
                    toast("Google Sign-In failed: " + e.getMessage());
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
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
        err(emailLayout,    null);
        err(passwordLayout, null);
    }

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
        if (loginButton != null) {
            loginButton.setEnabled(!busy);
            loginButton.setText(busy ? "Logging in…" : "Login");
        }
        if (googleSignInButton != null) googleSignInButton.setEnabled(!busy);
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
