package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * SettingsActivity — App settings: notifications and account preferences.
 *
 * Delete Account flow:
 *   1. Show confirmation dialog.
 *   2. Remove related Supabase data and storage objects by firebase_uid.
 *   3. Delete the Firebase Authentication account.
 *   4. If Firebase requires recent login, re-authenticate and retry the delete.
 *   5. Clear all local state, sign out, and redirect to LoginActivity.
 */
public class SettingsActivity extends BaseActivity {

    private static final String TAG             = "SettingsActivity";
    private static final String PREFS_NAME      = "quickfix_prefs";
    private static final String KEY_PUSH_NOTIF  = "notif_push";
    private static final String KEY_EMAIL_NOTIF = "notif_email";
    private static final String KEY_BOOKING_REM = "notif_booking_reminders";

    private String pendingProfileImagePath = "";
    private String pendingDeleteUid = "";
    private boolean isDeleteInProgress = false;

    // ── Views ─────────────────────────────────────────────────────────────
    private SwitchMaterial switchPush, switchEmail, switchBookingRem;
    private SwitchMaterial switchDarkMode;
    private LinearLayout   settingsChangePassword, settingsDeleteAccount;
    private FrameLayout    loadingOverlay;
    private TextView       tvDeletingMessage;

    // ── Auth / Prefs ──────────────────────────────────────────────────────
    private SharedPreferences prefs;
    private FirebaseAuth      mAuth;
    private GoogleSignInClient mGoogleClient;

    // ── HTTP ──────────────────────────────────────────────────────────────
    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs  = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mAuth  = FirebaseAuth.getInstance();

        // Google client — used only for re-auth if the account is Google-linked
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleClient = GoogleSignIn.getClient(this, gso);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbarSettings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null)
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        bindViews();
        loadPrefs();
        setupListeners();
    }

    // ── View binding ──────────────────────────────────────────────────────
    private void bindViews() {
        switchPush             = findViewById(R.id.switchPushNotifications);
        switchEmail            = findViewById(R.id.switchEmailNotifications);
        switchBookingRem       = findViewById(R.id.switchBookingReminders);
        switchDarkMode         = findViewById(R.id.switchDarkMode);
        settingsChangePassword = findViewById(R.id.settingsChangePassword);
        settingsDeleteAccount  = findViewById(R.id.settingsDeleteAccount);
        loadingOverlay         = findViewById(R.id.settingsLoadingOverlay);
        tvDeletingMessage      = findViewById(R.id.tvDeletingMessage);
    }

    // ── Load persisted prefs ──────────────────────────────────────────────
    private void loadPrefs() {
        if (switchPush       != null) switchPush.setChecked(prefs.getBoolean(KEY_PUSH_NOTIF,  true));
        if (switchEmail      != null) switchEmail.setChecked(prefs.getBoolean(KEY_EMAIL_NOTIF, true));
        if (switchBookingRem != null) switchBookingRem.setChecked(prefs.getBoolean(KEY_BOOKING_REM, true));
        if (switchDarkMode    != null) switchDarkMode.setChecked(ThemeManager.isUserDarkMode(this));
    }

    // ── Click / change listeners ──────────────────────────────────────────
    private void setupListeners() {
        if (switchPush != null)
            switchPush.setOnCheckedChangeListener((v, c) ->
                    prefs.edit().putBoolean(KEY_PUSH_NOTIF, c).apply());

        if (switchEmail != null)
            switchEmail.setOnCheckedChangeListener((v, c) ->
                    prefs.edit().putBoolean(KEY_EMAIL_NOTIF, c).apply());

        if (switchBookingRem != null)
            switchBookingRem.setOnCheckedChangeListener((v, c) ->
                    prefs.edit().putBoolean(KEY_BOOKING_REM, c).apply());

        if (switchDarkMode != null)
            switchDarkMode.setOnCheckedChangeListener((v, checked) ->
                    ThemeManager.toggleUser(this, checked));

        if (settingsChangePassword != null)
            settingsChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));

        if (settingsDeleteAccount != null)
            settingsDeleteAccount.setOnClickListener(v -> showDeleteConfirmDialog());
    }

    @Override
    protected boolean isAdminThemeScreen() {
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DELETE ACCOUNT — STEP 1: confirmation dialog
    // ═════════════════════════════════════════════════════════════════════
    private void showDeleteConfirmDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        CheckBox cbConfirm = dialogView != null ? dialogView.findViewById(R.id.cbConfirmDeleteAccount) : null;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_delete_dialog_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setView(dialogView)
                .setPositiveButton(R.string.settings_delete_dialog_confirm, (d, w) -> beginDeleteFlow())
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            android.widget.Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) positive.setEnabled(false);
            if (cbConfirm != null) {
                cbConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (positive != null) positive.setEnabled(isChecked);
                });
            }
        });

        dialog.show();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DELETE ACCOUNT — STEP 2: start the deletion flow
    // ═════════════════════════════════════════════════════════════════════
    private void beginDeleteFlow() {
        if (isDeleteInProgress) return;
        isDeleteInProgress = true;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            clearLocalDataAndRedirect("Your account and related data were permanently deleted.");
            return;
        }

        pendingDeleteUid = user.getUid() != null ? user.getUid() : "";
        if (pendingDeleteUid.isEmpty()) {
            isDeleteInProgress = false;
            showError("Could not determine your account ID. Please sign in again and retry.");
            return;
        }

        showOverlay("Preparing account deletion...");
        SupabaseClient.getAccountStatusByUid(pendingDeleteUid, new SupabaseClient.AccountStatusCallback() {
            @Override
            public void onSuccess(SupabaseClient.UserAccountStatus status) {
                if (isFinishing() || isDestroyed()) return;

                pendingProfileImagePath = status != null ? safe(status.profileImagePath) : "";
                continueDeleteFlow(user, pendingDeleteUid);
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                Log.w(TAG, "Account status lookup failed, continuing with deletion: " + message);
                pendingProfileImagePath = "";
                continueDeleteFlow(user, pendingDeleteUid);
            }
        });
    }

    private void continueDeleteFlow(FirebaseUser user, String uid) {
        if (isFinishing() || isDestroyed()) return;

        if (uid == null || uid.trim().isEmpty()) {
            hideOverlay();
            isDeleteInProgress = false;
            showError("Could not determine your account ID. Please try again.");
            return;
        }

        updateOverlay("Removing profile image...");
        deleteProfileImageIfPresent(() -> deleteSupabaseAccountData(uid.trim(), user));
    }

    private void deleteProfileImageIfPresent(Runnable onComplete) {
        if (pendingProfileImagePath.isEmpty()) {
            MAIN.post(onComplete);
            return;
        }

        new Thread(() -> {
            try {
                String path = extractBucketPath(pendingProfileImagePath, SupabaseStorageHelper.BUCKET_PROFILE_IMAGES);
                if (path.isEmpty()) {
                    Log.w(TAG, "Profile image path parse failed, continuing deletion.");
                    MAIN.post(onComplete);
                    return;
                }

                String url = BuildConfig.SUPABASE_URL + "/storage/v1/object/"
                        + SupabaseStorageHelper.BUCKET_PROFILE_IMAGES + "/" + path;

                Request req = new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    Log.d(TAG, "Profile image delete HTTP " + r.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "deleteProfileImageIfPresent non-fatal: " + e.getMessage());
            }
            MAIN.post(onComplete);
        }).start();
    }

    // ══════════════════════════════════════
    //  STEP B: DELETE user from Firebase Authentication
    // ═════════════════════════════════════════════════════════════════════
    private void deleteFirebaseUser(FirebaseUser user) {
        updateOverlay("Deleting account from authentication...");
        if (user == null) {
            clearLocalDataAndRedirect("Your account and related data were permanently deleted.");
            return;
        }

        user.delete()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Firebase user deleted successfully");
                    clearLocalDataAndRedirect("Your account and related data were permanently deleted.");
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Log.w(TAG, "Firebase delete failed: " + msg);

                    // Firebase requires a recent login before deleting the account
                    if (msg.contains("REQUIRES_RECENT_LOGIN")
                            || msg.contains("requires-recent-login")) {
                        hideOverlay();
                        isDeleteInProgress = false;
                        promptReauthAndDelete(user);
                    } else {
                        hideOverlay();
                        isDeleteInProgress = false;
                        showError("Failed to delete account from Firebase Authentication. Please try again.\n\n" + msg);
                    }
                });
    }

    private void deleteSupabaseAccountData(String uid, FirebaseUser user) {
        updateOverlay("Deleting data from Supabase...");
        SupabaseClient.hardDeleteUserData(uid, new SupabaseClient.Callback2() {
            @Override
            public void onSuccess() {
                deleteFirebaseUser(user);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "deleteSupabaseAccountData failed: " + message);
                hideOverlay();
                isDeleteInProgress = false;
                showError("Failed to delete your data from Supabase. Please try again.\n\n" + message);
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════
    //  RE-AUTH: ask for password (email users) or silent Google re-auth
    // ═════════════════════════════════════════════════════════════════════
    private void promptReauthAndDelete(FirebaseUser user) {
        // Determine sign-in provider
        boolean isGoogle = false;
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                isGoogle = true;
                break;
            }
        }

        if (isGoogle) {
            reauthWithGoogle(user);
        } else {
            reauthWithPassword(user);
        }
    }

    /** Re-authenticate Google user silently, then retry deletion. */
    private void reauthWithGoogle(FirebaseUser user) {
        showOverlay("Re-authenticating...");
        mGoogleClient.silentSignIn()
                .addOnSuccessListener(acct -> {
                    AuthCredential credential =
                            GoogleAuthProvider.getCredential(acct.getIdToken(), null);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(u -> {
                                Log.d(TAG, "Google re-auth success");
                                deleteFirebaseUser(user);
                            })
                            .addOnFailureListener(e -> {
                                hideOverlay();
                                isDeleteInProgress = false;
                                showError("Re-authentication failed. Please sign in again and retry.");
                            });
                })
                .addOnFailureListener(e -> {
                    hideOverlay();
                    isDeleteInProgress = false;
                    showError("Could not re-authenticate with Google. Please sign in again and retry.");
                });
    }

    /** Show password input dialog for email/password users, then retry deletion. */
    private void reauthWithPassword(FirebaseUser user) {
        EditText etPassword = new EditText(this);
        etPassword.setHint("Enter your password to confirm");
        etPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Your Password")
                .setMessage("Please enter your password to permanently delete your account.")
                .setView(etPassword)
                .setPositiveButton("Confirm and Delete", (d, w) -> {
                    String password = etPassword.getText() != null
                            ? etPassword.getText().toString().trim() : "";
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
                        isDeleteInProgress = false;
                        return;
                    }
                    String email = user.getEmail() != null ? user.getEmail() : "";
                    if (email.isEmpty()) {
                        isDeleteInProgress = false;
                        showError("Could not retrieve account email. Please contact support.");
                        return;
                    }
                    showOverlay("Re-authenticating...");
                    AuthCredential credential =
                            EmailAuthProvider.getCredential(email, password);
                    user.reauthenticate(credential)
                            .addOnSuccessListener(u -> {
                                Log.d(TAG, "Email re-auth success");
                                deleteFirebaseUser(user);
                            })
                            .addOnFailureListener(e -> {
                                hideOverlay();
                                isDeleteInProgress = false;
                                showError("Incorrect password. Please try again.");
                            });
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    hideOverlay();
                    isDeleteInProgress = false;
                })
                .setCancelable(false)
                .show();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  STEP C: Clear local data + redirect to Login
    // ═════════════════════════════════════════════════════════════════════
    private void clearLocalDataAndRedirect(String completionMessage) {
        updateOverlay("Signing out...");

        // Clear ALL SharedPreferences keys for this app
        prefs.edit().clear().apply();

        // Also clear the locale/theme prefs stored in the same file
        try {
            getSharedPreferences("quickfix_prefs", Context.MODE_PRIVATE)
                    .edit().clear().apply();
        } catch (Exception e) {
            Log.w(TAG, "clearLocalData prefs error (non-fatal): " + e.getMessage());
        }

        // Sign out Firebase (user may already be deleted, so guard the call)
        try {
            mAuth.signOut();
        } catch (Exception e) {
            Log.w(TAG, "signOut error (non-fatal): " + e.getMessage());
        }

        try {
            if (mGoogleClient != null) {
                mGoogleClient.signOut();
            }
        } catch (Exception e) {
            Log.w(TAG, "Google signOut error (non-fatal): " + e.getMessage());
        }

        pendingDeleteUid = "";
        pendingProfileImagePath = "";

        // Small delay so overlay is visible briefly, then navigate
        MAIN.postDelayed(() -> {
            hideOverlay();
            isDeleteInProgress = false;
            if (completionMessage != null && !completionMessage.trim().isEmpty()) {
                Toast.makeText(this, completionMessage, Toast.LENGTH_LONG).show();
            }
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        }, 600);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  UI HELPERS
    // ═════════════════════════════════════════════════════════════════════
    private void showOverlay(String message) {
        MAIN.post(() -> {
            if (tvDeletingMessage != null) tvDeletingMessage.setText(message);
            if (loadingOverlay   != null) loadingOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void updateOverlay(String message) {
        MAIN.post(() -> {
            if (tvDeletingMessage != null) tvDeletingMessage.setText(message);
        });
    }

    private void hideOverlay() {
        MAIN.post(() -> {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
        });
    }

    private void showError(String message) {
        MAIN.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    private String extractBucketPath(String rawPath, String bucket) {
        if (rawPath == null) return "";
        String path = rawPath.trim();
        if (path.isEmpty()) return "";

        String markerPublic = "/object/public/" + bucket + "/";
        int idxPublic = path.indexOf(markerPublic);
        if (idxPublic >= 0) {
            return path.substring(idxPublic + markerPublic.length());
        }

        String marker = "/object/" + bucket + "/";
        int idx = path.indexOf(marker);
        if (idx >= 0) {
            return path.substring(idx + marker.length());
        }

        String prefix = bucket + "/";
        if (path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }

        return path;
    }

    private String safe(String value) {
        return value != null ? value.trim() : "";
    }
}
