package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SplashActivity extends BaseActivity {

    private static final int SPLASH_DURATION = 3000; // 3 seconds total
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView ensures the layout is inflated and real content is shown
        setContentView(R.layout.activity_splash);

        // Bind views from ConstraintLayout
        ImageView splashLogo = findViewById(R.id.splash_logo);
        TextView splashText = findViewById(R.id.splash_text);
        TextView splashTagline = findViewById(R.id.splash_tagline);

        // Hide all views first so animation always fades in from invisible
        splashLogo.setAlpha(0f);
        splashText.setAlpha(0f);
        splashTagline.setAlpha(0f);

        // Load animation (fade + scale)
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);

        // Animate logo immediately
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashLogo.setAlpha(1f);   // make visible so animation renders
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashLogo.startAnimation(anim);

        // Animate text with 150ms delay for staggered effect
        Animation animText = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);
        animText.setStartOffset(150);
        animText.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashText.setAlpha(1f);
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashText.startAnimation(animText);

        // Animate tagline with 300ms delay
        Animation animTag = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);
        animTag.setStartOffset(300);
        animTag.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashTagline.setAlpha(1f);
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashTagline.startAnimation(animTag);

        // Auto-navigate after splash delay.
        new Handler(Looper.getMainLooper()).postDelayed(this::routeAfterSplash, SPLASH_DURATION);
    }

    private void routeAfterSplash() {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        boolean isAdminEmail = current != null && current.getEmail() != null && current.getEmail().equals("admin@gmail.com");
        boolean fromAdmin = getIntent().getBooleanExtra("from_admin", false);

        if (isAdminEmail || fromAdmin) {
            proceedWithUser(current);
            return;
        }

        String url = BuildConfig.SUPABASE_URL + "/rest/v1/app_settings?select=maintenance_mode,maintenance_message&limit=1";
        okhttp3.Request req = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                .get()
                .build();

        new okhttp3.OkHttpClient().newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    if (AppSettings.isMaintenanceMode(SplashActivity.this)) {
                        startAndFinish(new Intent(SplashActivity.this, MaintenanceActivity.class));
                    } else {
                        proceedWithUser(current);
                    }
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                boolean isMaintenance = false;
                String msg = "";
                try {
                    String body = response.body().string();
                    org.json.JSONArray arr = new org.json.JSONArray(body);
                    if (arr.length() > 0) {
                        org.json.JSONObject obj = arr.getJSONObject(0);
                        isMaintenance = obj.optBoolean("maintenance_mode", false);
                        msg = obj.optString("maintenance_message", "");

                        AppSettings.set(SplashActivity.this, AppSettings.KEY_MAINTENANCE_MODE, isMaintenance);
                        AppSettings.setString(SplashActivity.this, AppSettings.KEY_MAINTENANCE_MSG, msg);
                    }
                } catch (Exception ignored) {}

                final boolean fMaintenance = isMaintenance;
                final String fMsg = msg;
                runOnUiThread(() -> {
                    if (fMaintenance) {
                        Intent intent = new Intent(SplashActivity.this, MaintenanceActivity.class);
                        intent.putExtra("message", fMsg);
                        startAndFinish(intent);
                    } else {
                        proceedWithUser(current);
                    }
                });
            }
        });
    }

    private void proceedWithUser(FirebaseUser current) {
        if (current == null) {
            startAndFinish(new Intent(this, LoginActivity.class));
            return;
        }

        if (!(current.isEmailVerified() || isGoogleUser(current))) {
            startAndFinish(new Intent(this, LoginActivity.class));
            return;
        }

        applyUserSettingsAndRoute(current);
    }

    private void applyUserSettingsAndRoute(FirebaseUser current) {
        final String uid = current.getUid();
        final String email = current.getEmail() == null ? "" : current.getEmail();

        SupabaseClient.fetchUserSettings(uid, email, new SupabaseClient.UserSettingsCallback() {
            @Override
            public void onSuccess(SupabaseClient.UserSettings settings) {
                if (settings != null) {


                    UserPreferenceManager.applyFromServer(
                            SplashActivity.this,
                            uid,
                            settings.darkMode,
                            settings.language
                    );
                }
                routeByRole(uid, email);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "User settings fetch failed, using cached values: " + message);
                UserPreferenceManager.init(SplashActivity.this);
                routeByRole(uid, email);
            }
        });
    }

    private void routeByRole(String uid, String email) {
        SupabaseClient.getUserRoleWithEmail(uid, email, new SupabaseClient.RoleInfoCallback() {
            @Override
            public void onSuccess(SupabaseClient.RoleInfo info) {
                if (isFinishing()) return;
                if (info.isAdmin) {
                    startAndFinish(new Intent(SplashActivity.this, AdminDashboardActivity.class));
                } else if (info.isServiceProvider) {
                    startAndFinish(new Intent(SplashActivity.this, ProviderDashboardActivity.class));
                } else {
                    startAndFinish(new Intent(SplashActivity.this, MainActivity.class));
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Role lookup failed in splash, falling back to login: " + message);
                if (isFinishing()) return;
                startAndFinish(new Intent(SplashActivity.this, LoginActivity.class));
            }
        });
    }

    private boolean isGoogleUser(FirebaseUser user) {
        for (com.google.firebase.auth.UserInfo info : user.getProviderData()) {
            if (GoogleAuthProvider.PROVIDER_ID.equals(info.getProviderId())) {
                return true;
            }
        }
        return false;
    }

    private void startAndFinish(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
