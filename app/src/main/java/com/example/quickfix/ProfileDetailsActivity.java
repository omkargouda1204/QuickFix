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

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ProfileDetailsActivity — Read-only view of the user's profile.
 *
 * <p>Load order (fastest → authoritative):
 * <ol>
 *   <li>Intent extras passed by {@link ProfileActivity} — shown instantly, zero latency.</li>
 *   <li>Firebase {@link FirebaseUser} metadata — fills any gaps left by missing extras.</li>
 *   <li>Supabase {@code public.users} row — authoritative; overwrites the above on arrival.</li>
 *   <li>Auto-seed — if Supabase has no row yet, one is created from Firebase + extras.</li>
 * </ol>
 *
 * <p>Displays: Avatar initial, Full Name, Email, Phone, City, Address.
 */
public class ProfileDetailsActivity extends AppCompatActivity {

    private static final String TAG = "ProfileDetails";

    // ── Intent extras (passed by ProfileActivity for instant pre-fill) ────
    public static final String EXTRA_NAME  = "extra_profile_name";
    public static final String EXTRA_EMAIL = "extra_profile_email";
    public static final String EXTRA_PHONE = "extra_profile_phone";

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
    private String firebaseUid  = "";
    private String cachedName   = "";
    private String cachedEmail  = "";
    private String cachedPhone  = "";

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

        // ── Step 1: instant pre-fill from intent extras ──────────────────
        String extraName  = getIntent().getStringExtra(EXTRA_NAME);
        String extraEmail = getIntent().getStringExtra(EXTRA_EMAIL);
        String extraPhone = getIntent().getStringExtra(EXTRA_PHONE);

        // ── Step 2: fill any gaps from Firebase metadata ─────────────────
        if (TextUtils.isEmpty(extraName)  && user.getDisplayName() != null)
            extraName  = user.getDisplayName().trim();
        if (TextUtils.isEmpty(extraEmail) && user.getEmail() != null)
            extraEmail = user.getEmail().trim();
        // Firebase phone number (set for phone-auth users)
        if (TextUtils.isEmpty(extraPhone) && user.getPhoneNumber() != null)
            extraPhone = user.getPhoneNumber().trim();

        // Show whatever we have right away — no spinner needed for this pass
        if (!TextUtils.isEmpty(extraName) || !TextUtils.isEmpty(extraEmail)) {
            applyProfile(
                    safeStr(extraName),
                    safeStr(extraEmail),
                    safeStr(extraPhone),
                    "", "", null);
        }

        // ── Step 3: authoritative load from Supabase ─────────────────────
        loadProfile(safeStr(extraName), safeStr(extraEmail), safeStr(extraPhone));
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
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LOAD FROM SUPABASE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Fetches the user row from Supabase.
     * <ul>
     *   <li>If a row is found → {@link #applyProfile} with all fields.</li>
     *   <li>If no row found   → calls {@link #seedSupabaseRow} to create one,
     *       then {@link #applyProfile} with the seed data so the screen stays populated.</li>
     *   <li>On network error  → shows a snackbar; existing pre-filled data stays visible.</li>
     * </ul>
     *
     * @param fallbackName  best-known name before Supabase responds (from extras / Firebase)
     * @param fallbackEmail best-known email before Supabase responds
     * @param fallbackPhone best-known phone before Supabase responds
     */
    private void loadProfile(String fallbackName, String fallbackEmail, String fallbackPhone) {
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
                            // ── Row found — populate everything ──────────
                            JSONObject o     = arr.getJSONObject(0);
                            String name      = nullSafe(o, "name");
                            String email     = nullSafe(o, "email");
                            String phone     = nullSafe(o, "phone");
                            String city      = nullSafe(o, "city");
                            String address   = nullSafe(o, "address");
                            String rawImg    = o.isNull("profile_image_url") ? null
                                               : o.optString("profile_image_url", "");
                            String imgUrl    = TextUtils.isEmpty(rawImg) ? null : rawImg;

                            // Merge: use Firebase/extras values if DB fields are blank
                            if (TextUtils.isEmpty(name))  name  = fallbackName;
                            if (TextUtils.isEmpty(email)) email = fallbackEmail;
                            if (TextUtils.isEmpty(phone)) phone = fallbackPhone;

                            final String fName = name, fEmail = email,
                                         fPhone = phone, fCity = city,
                                         fAddr = address, fImg = imgUrl;
                            MAIN.post(() -> applyProfile(fName, fEmail, fPhone, fCity, fAddr, fImg));
                        } else {
                            // ── No row yet — seed it, then display what we have ──
                            Log.i(TAG, "No Supabase row for uid=" + firebaseUid + " — seeding.");
                            seedSupabaseRow(fallbackName, fallbackEmail, fallbackPhone);
                            MAIN.post(() -> applyProfile(
                                    fallbackName, fallbackEmail, fallbackPhone, "", "", null));
                        }
                    } else {
                        Log.w(TAG, "loadProfile HTTP " + r.code());
                        MAIN.post(() -> showSnack("Could not refresh profile (HTTP " + r.code() + ")"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadProfile exception: " + e.getMessage(), e);
                // Don't clear pre-filled data — just warn the user
                MAIN.post(() -> showSnack("Network error. Showing cached data."));
            } finally {
                MAIN.post(() -> showLoading(false));
            }
        }).start();
    }

    /**
     * Inserts a new row into {@code public.users} using the best data we have.
     * Fire-and-forget (background thread, no callback needed here).
     */
    private void seedSupabaseRow(String name, String email, String phone) {
        try {
            JSONObject body = new JSONObject();
            body.put("firebase_uid", firebaseUid);
            body.put("name",         TextUtils.isEmpty(name)  ? "" : name.trim());
            body.put("email",        TextUtils.isEmpty(email) ? "" : email.trim());
            body.put("phone",        TextUtils.isEmpty(phone) ? "" : phone.trim());
            body.put("is_user",              true);
            body.put("is_service_provider",  false);
            body.put("is_admin",             false);

            Request req = new Request.Builder()
                    .url(BuildConfig.SUPABASE_URL + "/rest/v1/users")
                    .post(RequestBody.create(
                            body.toString(),
                            MediaType.get("application/json; charset=utf-8")))
                    .addHeader("apikey",        BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                    .addHeader("Content-Type",  "application/json")
                    .addHeader("Prefer",        "resolution=ignore-duplicates,return=minimal")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                Log.d(TAG, "seedSupabaseRow HTTP " + r.code());
            }
        } catch (Exception e) {
            Log.w(TAG, "seedSupabaseRow (non-fatal): " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  APPLY PROFILE TO UI
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Populates every visible field with the supplied profile data.
     * All parameters are null-safe; missing values show a "—" / "Not set" fallback.
     */
    private void applyProfile(String name, String email, String phone,
                               String city, String address, String imgUrl) {
        // Cache (null-safe)
        cachedName  = name  != null ? name.trim()  : "";
        cachedEmail = email != null ? email.trim() : "";
        cachedPhone = phone != null ? phone.trim() : "";

        // ── Header: name + email ─────────────────────────────────────────
        String displayName = TextUtils.isEmpty(cachedName)
                ? splitEmail(cachedEmail) : cachedName;
        if (tvName  != null) tvName .setText(TextUtils.isEmpty(displayName) ? "User" : displayName);
        if (tvEmail != null) tvEmail.setText(cachedEmail);

        // ── Avatar initial ───────────────────────────────────────────────
        applyAvatar(cachedName, imgUrl);

        // ── Card rows ────────────────────────────────────────────────────
        setTextOrFallback(tvDisplayName,    cachedName,                          "—");
        setTextOrFallback(tvDisplayEmail,   cachedEmail,                         "—");
        setTextOrFallback(tvDisplayPhone,   cachedPhone,                         "Not set");
        setTextOrFallback(tvDisplayCity,    city    != null ? city.trim()    : "", "Not set");
        setTextOrFallback(tvDisplayAddress, address != null ? address.trim() : "", "Not set");
    }

    /** Shows avatar image (Glide) or a coloured initial circle as fallback. */
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

    /** Sets {@code tv} to {@code value}, or {@code fallback} when value is blank. */
    private void setTextOrFallback(TextView tv, String value, String fallback) {
        if (tv == null) return;
        tv.setText(TextUtils.isEmpty(value) ? fallback : value);
    }

    /** Derives a display name from the part of an e-mail address before '@'. */
    private static String splitEmail(String email) {
        if (TextUtils.isEmpty(email)) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /** Reads a JSON string field safely; returns {@code ""} instead of {@code "null"}. */
    private static String nullSafe(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return o.optString(key, "").trim();
    }

    /** Returns {@code s} if non-null, otherwise {@code ""}. */
    private static String safeStr(String s) { return s != null ? s.trim() : ""; }
}
