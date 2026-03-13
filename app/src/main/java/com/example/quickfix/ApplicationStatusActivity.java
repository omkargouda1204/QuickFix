package com.example.quickfix;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ApplicationStatusActivity — shown when user taps "Start / List Your Business"
 * and already has an application on record.
 *
 * Handles all four cases:
 *  • pending  → "Under review" message, no actions
 *  • approved → "Already a provider" message
 *  • rejected → rejection reason + 24-hour countdown to re-apply
 *  • loading  → shows spinner while fetching from Supabase
 */
public class ApplicationStatusActivity extends BaseActivity {

    private static final String TAG = "AppStatusActivity";

    // ── Views ─────────────────────────────────────────────────────────────
    private ProgressBar      progressBar;
    private View             contentLayout;
    private TextView         tvStatusTitle, tvStatusSubtitle, tvStatusBadge;
    private TextView         tvBusinessName, tvCategory, tvCity, tvAppliedDate;
    private LinearLayout     layoutRejectionSection, layoutCountdown, layoutApprovedInfo;
    private TextView         tvRejectionReason, tvCountdown;
    private MaterialButton   btnAction;
    private View             statusIconView;

    private CountDownTimer countDownTimer;

    // ═════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_application_status);

        setupToolbar();
        bindViews();
        loadStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void setupToolbar() {
        Toolbar tb = findViewById(R.id.toolbar);
        if (tb == null) return;
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Application Status");
        }
        tb.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        progressBar            = findViewById(R.id.progressBar);
        contentLayout          = findViewById(R.id.contentLayout);
        tvStatusTitle          = findViewById(R.id.tvStatusTitle);
        tvStatusSubtitle       = findViewById(R.id.tvStatusSubtitle);
        tvStatusBadge          = findViewById(R.id.tvStatusBadge);
        tvBusinessName         = findViewById(R.id.tvBusinessName);
        tvCategory             = findViewById(R.id.tvCategory);
        tvCity                 = findViewById(R.id.tvCity);
        tvAppliedDate          = findViewById(R.id.tvAppliedDate);
        layoutRejectionSection = findViewById(R.id.layoutRejectionSection);
        layoutCountdown        = findViewById(R.id.layoutCountdown);
        layoutApprovedInfo     = findViewById(R.id.layoutApprovedInfo);
        tvRejectionReason      = findViewById(R.id.tvRejectionReason);
        tvCountdown            = findViewById(R.id.tvCountdown);
        btnAction              = findViewById(R.id.btnAction);
        statusIconView         = findViewById(R.id.statusIconView);
    }

    private void loadStatus() {
        showLoading(true);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        ProviderApplicationRepository.fetchApplicationFull(user.getUid(),
                new ProviderApplicationRepository.ApplicationCallback() {
                    @Override public void onFound(ProviderApplication app) {
                        showLoading(false);
                        populateUI(app, user);
                    }
                    @Override public void onNotFound() {
                        showLoading(false);
                        // No application found — go directly to form
                        openOnboarding();
                    }
                    @Override public void onError(String message) {
                        showLoading(false);
                        Log.e(TAG, "loadStatus error: " + message);
                        // On error, allow opening form
                        openOnboarding();
                    }
                });
    }

    private void populateUI(ProviderApplication app, FirebaseUser user) {
        // Common fields
        setText(tvBusinessName, app.businessName);
        setText(tvCategory,     app.category);
        setText(tvCity,         app.city);
        setText(tvAppliedDate,  "Applied: " + fmtDate(app.submittedAt));

        String status = app.status != null ? app.status.toLowerCase(Locale.US) : "pending";

        switch (status) {
            case "approved":
                showApprovedState();
                break;
            case "rejected":
                showRejectedState(app, user.getUid());
                break;
            default: // pending or unknown
                showPendingState();
                break;
        }
    }

    // ── PENDING ───────────────────────────────────────────────────────────
    private void showPendingState() {
        applyBadge("PENDING", R.color.warning_text, R.color.warning_bg);
        setText(tvStatusTitle,    "Application Under Review");
        setText(tvStatusSubtitle, "Our team will review your request and get back to you soon.");

        if (layoutRejectionSection != null) layoutRejectionSection.setVisibility(View.GONE);
        if (layoutCountdown        != null) layoutCountdown.setVisibility(View.GONE);
        if (layoutApprovedInfo     != null) layoutApprovedInfo.setVisibility(View.GONE);

        if (statusIconView != null) statusIconView.setBackgroundResource(R.drawable.bg_status_pending);

        if (btnAction != null) {
            btnAction.setText("Back to Home");
            btnAction.setOnClickListener(v -> goHome());
        }
    }

    // ── APPROVED ──────────────────────────────────────────────────────────
    private void showApprovedState() {
        applyBadge("APPROVED", R.color.success_text, R.color.success_bg);
        setText(tvStatusTitle,    "You're a Service Provider! 🎉");
        setText(tvStatusSubtitle, "Your account is approved. Manage your services from the Provider Dashboard.");

        if (layoutRejectionSection != null) layoutRejectionSection.setVisibility(View.GONE);
        if (layoutCountdown        != null) layoutCountdown.setVisibility(View.GONE);
        if (layoutApprovedInfo     != null) layoutApprovedInfo.setVisibility(View.VISIBLE);

        if (statusIconView != null) statusIconView.setBackgroundResource(R.drawable.bg_status_approved);

        if (btnAction != null) {
            btnAction.setText("Go to Provider Dashboard");
            btnAction.setOnClickListener(v -> {
                startActivity(new Intent(this, ProviderDashboardActivity.class));
                finish();
            });
        }
    }

    // ── REJECTED ──────────────────────────────────────────────────────────
    private void showRejectedState(ProviderApplication app, String uid) {
        applyBadge("REJECTED", R.color.error_red, R.color.chip_disabled_bg);
        setText(tvStatusTitle,    "Application Not Approved");
        setText(tvStatusSubtitle, "Unfortunately your application was not approved this time.");

        if (layoutRejectionSection != null) {
            layoutRejectionSection.setVisibility(View.VISIBLE);
            if (!app.rejectionReason.isEmpty()) {
                setText(tvRejectionReason, app.rejectionReason);
            } else {
                setText(tvRejectionReason, "No specific reason provided.");
            }
        }
        if (layoutApprovedInfo != null) layoutApprovedInfo.setVisibility(View.GONE);
        if (statusIconView != null) statusIconView.setBackgroundResource(R.drawable.bg_status_rejected);

        // 24-hour countdown
        long rejectedAtMs = parseTimestampMs(app.rejectedAt.isEmpty() ? app.reviewedAt : app.rejectedAt);
        long nowMs        = System.currentTimeMillis();
        long elapsedMs    = nowMs - rejectedAtMs;
        long waitMs       = 24L * 60 * 60 * 1000; // 24 hours
        long remainingMs  = waitMs - elapsedMs;

        if (remainingMs <= 0) {
            // 24 hours have passed — allow reapply
            if (layoutCountdown != null) layoutCountdown.setVisibility(View.GONE);
            if (btnAction != null) {
                btnAction.setText("Apply Again");
                btnAction.setEnabled(true);
                btnAction.setAlpha(1f);
                btnAction.setOnClickListener(v -> openOnboarding());
            }
        } else {
            // Show countdown
            if (layoutCountdown != null) layoutCountdown.setVisibility(View.VISIBLE);
            startCountdown(remainingMs);

            if (btnAction != null) {
                btnAction.setText("Can't Apply Yet");
                btnAction.setEnabled(false);
                btnAction.setAlpha(0.5f);
            }
        }
    }

    private void startCountdown(long remainingMs) {
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(remainingMs, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                long h = millisUntilFinished / (1000 * 60 * 60);
                long m = (millisUntilFinished % (1000 * 60 * 60)) / (1000 * 60);
                long s = (millisUntilFinished % (1000 * 60)) / 1000;
                String countdown = String.format(Locale.US,
                        "You can reapply in %02d:%02d:%02d", h, m, s);
                setText(tvCountdown, countdown);
            }
            @Override public void onFinish() {
                setText(tvCountdown, "You can now reapply!");
                if (btnAction != null) {
                    btnAction.setText("Apply Again");
                    btnAction.setEnabled(true);
                    btnAction.setAlpha(1f);
                    btnAction.setOnClickListener(v -> openOnboarding());
                }
                if (layoutCountdown != null) layoutCountdown.setVisibility(View.GONE);
            }
        }.start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private void showLoading(boolean show) {
        if (progressBar   != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (contentLayout != null) contentLayout.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void applyBadge(String text, int textColorRes, int bgColorRes) {
        if (tvStatusBadge == null) return;
        tvStatusBadge.setText(text);
        try {
            tvStatusBadge.setTextColor(getColor(textColorRes));
            tvStatusBadge.setBackgroundTintList(ColorStateList.valueOf(getColor(bgColorRes)));
        } catch (Exception ignored) {}
    }

    private void setText(TextView tv, String value) {
        if (tv == null) return;
        tv.setText((value != null && !value.trim().isEmpty()) ? value.trim() : "-");
    }

    private void openOnboarding() {
        startActivity(new Intent(this, ProviderOnboardingActivity.class));
        finish();
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private String fmtDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "-";
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s);
            if (d == null) return iso.length() >= 10 ? iso.substring(0, 10) : iso;
            return new SimpleDateFormat("dd MMM yyyy", Locale.US).format(d);
        } catch (Exception e) {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }

    private long parseTimestampMs(String iso) {
        if (iso == null || iso.isEmpty()) return System.currentTimeMillis() - (25L * 3600 * 1000);
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s);
            return d != null ? d.getTime() : System.currentTimeMillis() - (25L * 3600 * 1000);
        } catch (Exception e) {
            return System.currentTimeMillis() - (25L * 3600 * 1000);
        }
    }
}

