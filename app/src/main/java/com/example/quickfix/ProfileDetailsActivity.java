package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * ProfileDetailsActivity — Read-only view of the user's profile.
 * <p>
 * Displays: Avatar, Name, Email, Phone, City, Address.<br>
 * Data source: Supabase public.users table (keyed by firebase_uid).<br>
 * No edit functionality — profile is strictly read-only.
 */
public class ProfileDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ProfileDetails";

    // ── Header views ──────────────────────────────────────────────────────
    private ImageView   ivAvatar;
    private ImageView   btnBack;
    private TextView    tvAvatarInitial;
    private TextView    tvName;
    private TextView    tvEmail;
    private FrameLayout loadingOverlay;

    // ── View-mode card ────────────────────────────────────────────────────
    private MaterialCardView cardViewMode;
    private TextView tvDisplayName;
    private TextView tvDisplayEmail;
    private TextView tvDisplayPhone;
    private TextView tvDisplayCity;
    private TextView tvDisplayAddress;

    // ── State ─────────────────────────────────────────────────────────────
    private String firebaseUid;
    private String cachedEmail = "";
    private String cachedName  = "";

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_details);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        firebaseUid = user.getUid();

        bindViews();
        setupClickListeners();
        loadProfile();
    }

    // ── Bind all views ────────────────────────────────────────────────────
    private void bindViews() {
        ivAvatar        = findViewById(R.id.ivAvatar);
        tvAvatarInitial = findViewById(R.id.tvAvatarInitial);
        tvName          = findViewById(R.id.tvName);
        tvEmail         = findViewById(R.id.tvEmail);
        btnBack         = findViewById(R.id.btnBack);
        loadingOverlay  = findViewById(R.id.loadingOverlay);

        cardViewMode     = findViewById(R.id.cardViewMode);
        tvDisplayName    = findViewById(R.id.tvDisplayName);
        tvDisplayEmail   = findViewById(R.id.tvDisplayEmail);
        tvDisplayPhone   = findViewById(R.id.tvDisplayPhone);
        tvDisplayCity    = findViewById(R.id.tvDisplayCity);
        tvDisplayAddress = findViewById(R.id.tvDisplayAddress);

        // Always show the read-only card
        if (cardViewMode != null) cardViewMode.setVisibility(View.VISIBLE);
    }

    // ── Click listeners ───────────────────────────────────────────────────
    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    // ── Load profile from Supabase ────────────────────────────────────────
    private void loadProfile() {
        showLoading(true);
        new Thread(() -> {
            try {
                String url = BuildConfig.SUPABASE_URL
                        + "/rest/v1/users?firebase_uid=eq." + firebaseUid
                        + "&select=name,email,phone,city,address,profile_image_url&limit=1";

                Request req = new Request.Builder().url(url)
                        .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "loadProfile HTTP " + r.code() + " | " + body);

                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o      = arr.getJSONObject(0);
                            String name       = nullSafe(o, "name");
                            String email      = nullSafe(o, "email");
                            String phone      = nullSafe(o, "phone");
                            String city       = nullSafe(o, "city");
                            String address    = nullSafe(o, "address");
                            String rawImgUrl  = o.isNull("profile_image_url") ? null
                                                : o.optString("profile_image_url", "");
                            String imgUrl     = TextUtils.isEmpty(rawImgUrl) ? null : rawImgUrl;
                            MAIN.post(() -> applyProfile(name, email, phone, city, address, imgUrl));
                        } else {
                            // No row in Supabase yet — fall back to Firebase metadata
                            FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
                            String fbEmail = (fbUser != null && fbUser.getEmail() != null)
                                             ? fbUser.getEmail() : "";
                            String fbName  = (fbUser != null && fbUser.getDisplayName() != null)
                                             ? fbUser.getDisplayName() : "";
                            MAIN.post(() -> applyProfile(fbName, fbEmail, "", "", "", null));
                        }
                    } else {
                        MAIN.post(() -> showSnack("Failed to load profile (HTTP " + r.code() + ")"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile: " + e.getMessage(), e);
                MAIN.post(() -> showSnack("Network error. Please check your connection."));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    /**
     * Populate all read-only display rows and cache values for later reference.
     */
    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        // Null-safe caching
        cachedName    = name    != null ? name    : "";
        cachedEmail   = email   != null ? email   : "";

        // Header name + email
        String displayName = TextUtils.isEmpty(cachedName) ? splitEmail(cachedEmail) : cachedName;
        if (tvName  != null) tvName.setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        if (tvEmail != null) tvEmail.setText(cachedEmail);

        // Avatar
        applyAvatar(cachedName, imgUrl);

        // View-mode card rows
        setTextOrFallback(tvDisplayName,    cachedName,                    "—");
        setTextOrFallback(tvDisplayEmail,   cachedEmail,                   "—");
        setTextOrFallback(tvDisplayPhone,   phone   != null ? phone   : "", "Not set");
        setTextOrFallback(tvDisplayCity,    city    != null ? city    : "", "Not set");
        setTextOrFallback(tvDisplayAddress, address != null ? address : "", "Not set");
    }

    private void applyAvatar(String name, String imgUrl) {
        if (ivAvatar == null || tvAvatarInitial == null) return;

        if (!TextUtils.isEmpty(imgUrl)) {
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitial.setVisibility(View.GONE);
            Glide.with(this)
                    .load(imgUrl)
                    .circleCrop()
                    .placeholder(R.drawable.bg_profile_avatar_inner)
                    .error(R.drawable.bg_profile_avatar_inner)
                    .into(ivAvatar);
        } else {
            ivAvatar.setVisibility(View.GONE);
            tvAvatarInitial.setVisibility(View.VISIBLE);
            String initial = !TextUtils.isEmpty(name)
                             ? String.valueOf(name.charAt(0)).toUpperCase()
                             : !TextUtils.isEmpty(cachedEmail)
                               ? String.valueOf(cachedEmail.charAt(0)).toUpperCase()
                               : "U";
            tvAvatarInitial.setText(initial);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showSnack(String msg) {
        View root = findViewById(android.R.id.content);
        if (root != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        else Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** Sets TextView text; shows {@code fallback} when the value is blank. */
    private void setTextOrFallback(TextView tv, String value, String fallback) {
        if (tv == null) return;
        tv.setText(TextUtils.isEmpty(value) ? fallback : value);
    }

    /** Derives a display name from the part of an e-mail before '@'. */
    private String splitEmail(String email) {
        if (TextUtils.isEmpty(email)) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** Reads a JSON string field safely, returning {@code ""} instead of {@code "null"}. */
    private String nullSafe(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }
}
