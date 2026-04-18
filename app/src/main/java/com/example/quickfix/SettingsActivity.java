package com.example.quickfix;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
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
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.android.material.appbar.MaterialToolbar;

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

    private String pendingProfileImagePath = "";
    private String pendingDeleteUid = "";
    private boolean isDeleteInProgress = false;
    private boolean supabaseDeleteCompleted = false;

    // ── Views ─────────────────────────────────────────────────────────────
    private LinearLayout   settingsChangePassword, settingsHelpSupport, settingsDeleteAccount;
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
        setupListeners();
        syncUserSettingsFromServer();
    }

    // ── View binding ──────────────────────────────────────────────────────
    private void bindViews() {
        settingsChangePassword = findViewById(R.id.settingsChangePassword);
        settingsHelpSupport    = findViewById(R.id.settingsHelpSupport);
        settingsDeleteAccount  = findViewById(R.id.settingsDeleteAccount);
        loadingOverlay         = findViewById(R.id.settingsLoadingOverlay);
        tvDeletingMessage      = findViewById(R.id.tvDeletingMessage);
    }

    // ── Click / change listeners ──────────────────────────────────────────
    private void setupListeners() {
        if (settingsChangePassword != null)
            settingsChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(this, ForgotPasswordActivity.class)));

        if (settingsHelpSupport != null)
            settingsHelpSupport.setOnClickListener(v ->
                    startActivity(new Intent(this, HelpSupportActivity.class)));

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
        supabaseDeleteCompleted = false;

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            clearLocalDataAndRedirect(getString(R.string.settings_delete_success));
            return;
        }

        pendingDeleteUid = user.getUid() != null ? user.getUid() : "";
        if (pendingDeleteUid.isEmpty()) {
            isDeleteInProgress = false;
            showError(getString(R.string.settings_delete_uid_missing));
            return;
        }

        showOverlay(getString(R.string.settings_delete_overlay_checking));
        SupabaseClient.getAccountStatusByUid(pendingDeleteUid, new SupabaseClient.AccountStatusCallback() {
            @Override
            public void onSuccess(SupabaseClient.UserAccountStatus status) {
                if (isFinishing() || isDestroyed()) return;

                String serverUid = status != null ? safe(status.firebaseUid) : "";
                if (!TextUtils.isEmpty(serverUid) && !pendingDeleteUid.equals(serverUid)) {
                    hideOverlay();
                    isDeleteInProgress = false;
                    showError(getString(R.string.settings_delete_uid_mismatch));
                    return;
                }

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
            showError(getString(R.string.settings_delete_uid_missing));
            return;
        }

        updateOverlay(getString(R.string.settings_delete_overlay_profile_image));
        deleteProfileImageIfPresent(() -> deleteSupabaseAccountData(uid.trim(), user));
    }

    private void deleteProfileImageIfPresent(Runnable onComplete) {
        if (pendingProfileImagePath.isEmpty()) {
            MAIN.post(onComplete);
            return;
        }

        new Thread(() -> {
            try {
                if (BuildConfig.SUPABASE_URL == null || BuildConfig.SUPABASE_URL.trim().isEmpty()
                        || BuildConfig.SUPABASE_ANON_KEY == null || BuildConfig.SUPABASE_ANON_KEY.trim().isEmpty()) {
                    Log.w(TAG, "Supabase storage config missing, skipping profile image delete.");
                    MAIN.post(onComplete);
                    return;
                }

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
        updateOverlay(getString(R.string.settings_delete_overlay_firebase));
        if (user == null) {
            clearLocalDataAndRedirect(getString(R.string.settings_delete_success));
            return;
        }

        user.delete()
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Firebase user deleted successfully");
                    clearLocalDataAndRedirect(getString(R.string.settings_delete_success));
                })
                .addOnFailureListener(e -> {
                    String msg = e.getMessage() != null ? e.getMessage() : "";
                    Log.w(TAG, "Firebase delete failed: " + msg);

                    // Firebase requires a recent login before deleting the account
                    if (e instanceof FirebaseAuthRecentLoginRequiredException
                            || msg.contains("REQUIRES_RECENT_LOGIN")
                            || msg.contains("requires-recent-login")) {
                        hideOverlay();
                        isDeleteInProgress = false;
                        promptReauthAndDelete(user);
                    } else {
                        hideOverlay();
                        isDeleteInProgress = false;
                        showError(buildFirebaseDeleteFailureMessage(msg));
                    }
                });
    }

    private void deleteSupabaseAccountData(String uid, FirebaseUser user) {
        updateOverlay(getString(R.string.settings_delete_overlay_supabase));
        SupabaseClient.hardDeleteUserData(uid, new SupabaseClient.Callback2() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Supabase data deletion successful for uid: " + uid);
                supabaseDeleteCompleted = true;
                deleteFirebaseUser(user);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "deleteSupabaseAccountData failed: " + message);
                // Log detailed error for debugging
                logDetailedDeletionError("Supabase deletion failed", message);
                
                hideOverlay();
                isDeleteInProgress = false;
                showError(getString(R.string.settings_delete_supabase_error, message));
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
        showOverlay(getString(R.string.settings_delete_overlay_reauth));
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
                                showError(getString(R.string.settings_delete_reauth_failed));
                            });
                })
                .addOnFailureListener(e -> {
                    hideOverlay();
                    isDeleteInProgress = false;
                    showError(getString(R.string.settings_delete_google_reauth_failed));
                });
    }

    /** Show password input dialog for email/password users, then retry deletion. */
    private void reauthWithPassword(FirebaseUser user) {
        EditText etPassword = new EditText(this);
        etPassword.setHint(R.string.password_hint);
        etPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_delete_password_title)
                .setMessage(R.string.settings_delete_password_message)
                .setView(etPassword)
                .setPositiveButton(R.string.settings_delete_password_confirm, (d, w) -> {
                    if (isDeleteInProgress) return;
                    isDeleteInProgress = true;

                    String password = etPassword.getText() != null
                            ? etPassword.getText().toString().trim() : "";
                    if (password.isEmpty()) {
                        Toast.makeText(this, R.string.settings_delete_password_empty, Toast.LENGTH_SHORT).show();
                        isDeleteInProgress = false;
                        return;
                    }
                    String email = user.getEmail() != null ? user.getEmail() : "";
                    if (email.isEmpty()) {
                        isDeleteInProgress = false;
                        showError(getString(R.string.settings_delete_email_missing));
                        return;
                    }
                    showOverlay(getString(R.string.settings_delete_overlay_reauth));
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
                                showError(getString(R.string.settings_delete_password_incorrect));
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
        updateOverlay(getString(R.string.settings_delete_overlay_signout));

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
        supabaseDeleteCompleted = false;

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

    private void syncUserSettingsFromServer() {
        FirebaseUser current = mAuth != null ? mAuth.getCurrentUser() : null;
        if (current == null) return;

        SupabaseClient.fetchUserSettings(current.getUid(), current.getEmail(),
                new SupabaseClient.UserSettingsCallback() {
                    @Override
                    public void onSuccess(SupabaseClient.UserSettings settings) {
                        if (settings == null) return;
                        UserPreferenceManager.applyFromServer(
                                SettingsActivity.this,
                                current.getUid(),
                                settings.darkMode,
                                settings.language
                        );
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "syncUserSettingsFromServer failed: " + message);
                    }
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

    private String buildFirebaseDeleteFailureMessage(String rawMessage) {
        String detail = rawMessage != null ? rawMessage.trim() : "";
        if (supabaseDeleteCompleted) {
            return getString(R.string.settings_delete_firebase_error_after_data_removed,
                    detail.isEmpty() ? "Unknown error" : detail);
        }
        return getString(R.string.settings_delete_firebase_error,
                detail.isEmpty() ? "Unknown error" : detail);
    }

    /**
     * Logs detailed deletion error for debugging.
     * This helps identify which tables/operations failed during account deletion.
     */
    private void logDetailedDeletionError(String context, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══ ACCOUNT DELETION ERROR LOG ═══\n");
        sb.append("Context: ").append(context).append("\n");
        sb.append("Timestamp: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", 
            java.util.Locale.US).format(new java.util.Date())).append("\n");
        sb.append("User ID: ").append(pendingDeleteUid).append("\n");
        sb.append("Error: ").append(errorMessage).append("\n");
        sb.append("Has profile image: ").append(!pendingProfileImagePath.isEmpty()).append("\n");
        sb.append("Supabase delete completed: ").append(supabaseDeleteCompleted).append("\n");
        sb.append("═══════════════════════════════════\n");
        
        Log.e(TAG, sb.toString());
        
        // Also log to Android's crash reporting if available
        try {
            if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                android.util.Log.d(TAG, "Full deletion error context: " + sb.toString());
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not log detailed error: " + e.getMessage());
        }
    }
}
