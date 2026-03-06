package com.example.quickfix;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ProviderApplicationDetailActivity — Full details view for a single provider application.
 *
 * - Displays all fields from provider_applications table.
 * - Business Image: opens FullScreenImageActivity with the public URL.
 * - ID Proof: fetches a signed URL from Supabase private bucket (admin only), then opens viewer.
 * - Approve / Reject buttons (shown only for pending applications).
 */
public class ProviderApplicationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_APP_ID           = "app_id";
    public static final String EXTRA_FIREBASE_UID     = "firebase_uid";
    public static final String EXTRA_BUSINESS_NAME    = "business_name";
    public static final String EXTRA_OWNER_NAME       = "owner_name";
    public static final String EXTRA_EMAIL            = "email";
    public static final String EXTRA_PHONE            = "phone";
    public static final String EXTRA_CATEGORY         = "category";
    public static final String EXTRA_CITY             = "city";
    public static final String EXTRA_ADDRESS          = "address";
    public static final String EXTRA_EXPERIENCE       = "experience";
    public static final String EXTRA_DESCRIPTION      = "description";
    public static final String EXTRA_STATUS           = "status";
    public static final String EXTRA_SUBMITTED_AT     = "submitted_at";
    public static final String EXTRA_REJECTION_REASON = "rejection_reason";
    public static final String EXTRA_BUSINESS_IMG     = "business_image_url";
    public static final String EXTRA_ID_PROOF         = "id_proof_url";

    private static final String TAG = "ProvAppDetail";

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView     tvHeaderStatus, tvHeaderBusinessName;
    private TextView     tvBusinessName, tvOwnerName, tvEmail, tvPhone;
    private TextView     tvCategory, tvCity, tvAddress, tvExperience, tvDescription;
    private TextView     tvDate, tvRejectionReason;
    private LinearLayout layoutRejectionReason, layoutActionButtons;
    private TextView     tvBusinessImage, tvIdProof;
    private ProgressBar  pbIdProof, progressBar;
    private MaterialButton btnApprove, btnReject;

    // ── Data ───────────────────────────────────────────────────────────────
    private ProviderApplication app;
    private String adminUid = "";

    // ═══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_application_detail);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) adminUid = user.getUid();

        bindViews();
        setupToolbar();
        extractExtras();
        populateUI();
    }

    // ── Toolbar ────────────────────────────────────────────────────────────
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("");
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    // ── Bind views ─────────────────────────────────────────────────────────
    private void bindViews() {
        tvHeaderStatus       = findViewById(R.id.tvHeaderStatus);
        tvHeaderBusinessName = findViewById(R.id.tvHeaderBusinessName);

        tvBusinessName       = findViewById(R.id.tvBusinessName);
        tvOwnerName          = findViewById(R.id.tvOwnerName);
        tvEmail              = findViewById(R.id.tvEmail);
        tvPhone              = findViewById(R.id.tvPhone);
        tvCategory           = findViewById(R.id.tvCategory);
        tvCity               = findViewById(R.id.tvCity);
        tvAddress            = findViewById(R.id.tvAddress);
        tvExperience         = findViewById(R.id.tvExperience);
        tvDescription        = findViewById(R.id.tvDescription);
        tvDate               = findViewById(R.id.tvDate);
        tvRejectionReason    = findViewById(R.id.tvRejectionReason);
        layoutRejectionReason= findViewById(R.id.layoutRejectionReason);
        layoutActionButtons  = findViewById(R.id.layoutActionButtons);
        tvBusinessImage      = findViewById(R.id.tvBusinessImage);
        tvIdProof            = findViewById(R.id.tvIdProof);
        pbIdProof            = findViewById(R.id.pbIdProof);
        progressBar          = findViewById(R.id.progressBar);
        btnApprove           = findViewById(R.id.btnApprove);
        btnReject            = findViewById(R.id.btnReject);
    }

    // ── Extract Intent extras into ProviderApplication POJO ────────────────
    private void extractExtras() {
        Intent i = getIntent();
        app = new ProviderApplication();
        app.id              = safe(i.getStringExtra(EXTRA_APP_ID));
        app.firebaseUid     = safe(i.getStringExtra(EXTRA_FIREBASE_UID));
        app.businessName    = safe(i.getStringExtra(EXTRA_BUSINESS_NAME));
        app.name            = safe(i.getStringExtra(EXTRA_OWNER_NAME));
        app.email           = safe(i.getStringExtra(EXTRA_EMAIL));
        app.phone           = safe(i.getStringExtra(EXTRA_PHONE));
        app.category        = safe(i.getStringExtra(EXTRA_CATEGORY));
        app.city            = safe(i.getStringExtra(EXTRA_CITY));
        app.address         = safe(i.getStringExtra(EXTRA_ADDRESS));
        app.experience      = safe(i.getStringExtra(EXTRA_EXPERIENCE));
        app.description     = safe(i.getStringExtra(EXTRA_DESCRIPTION));
        app.status          = safe(i.getStringExtra(EXTRA_STATUS));
        app.submittedAt     = safe(i.getStringExtra(EXTRA_SUBMITTED_AT));
        app.rejectionReason = safe(i.getStringExtra(EXTRA_REJECTION_REASON));
        app.businessImageUrl= safe(i.getStringExtra(EXTRA_BUSINESS_IMG));
        app.idProofUrl      = safe(i.getStringExtra(EXTRA_ID_PROOF));
    }

    // ── Populate UI ────────────────────────────────────────────────────────
    private void populateUI() {
        // Header
        set(tvHeaderBusinessName, app.businessName);
        applyStatusBadge(tvHeaderStatus, app.status);

        // Contact section
        set(tvBusinessName, app.businessName);
        set(tvOwnerName,    app.name);
        set(tvEmail,        app.email);
        set(tvPhone,        app.phone);

        // Service section
        set(tvCategory,    app.category);
        set(tvCity,        app.city);
        set(tvAddress,     app.address);
        set(tvExperience,  app.experience.isEmpty() ? "-" : app.experience + " years");
        set(tvDescription, app.description);

        // Meta section
        set(tvDate, fmtDate(app.submittedAt));

        // Rejection reason
        if (!app.rejectionReason.isEmpty()) {
            layoutRejectionReason.setVisibility(View.VISIBLE);
            set(tvRejectionReason, app.rejectionReason);
        }

        // Business image
        if (!app.businessImageUrl.isEmpty()) {
            tvBusinessImage.setOnClickListener(v -> openImage(app.businessImageUrl, "Business Image"));
        } else {
            tvBusinessImage.setText("No image uploaded");
            tvBusinessImage.setTextColor(Color.parseColor("#9E9E9E"));
        }

        // ID proof — signed URL (admin only, private bucket)
        if (!app.idProofUrl.isEmpty()) {
            tvIdProof.setOnClickListener(v -> openIdProofSecure());
        } else {
            tvIdProof.setText("No ID proof uploaded");
            tvIdProof.setTextColor(Color.parseColor("#9E9E9E"));
        }

        // Action buttons — only for pending
        String status = app.status.toLowerCase(Locale.US);
        if ("pending".equals(status) && !adminUid.isEmpty()) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            btnApprove.setOnClickListener(v -> confirmApprove());
            btnReject.setOnClickListener(v -> showRejectDialog());
        }
    }

    // ── Status badge ───────────────────────────────────────────────────────
    private void applyStatusBadge(TextView tv, String status) {
        if (tv == null) return;
        String s = status != null ? status.toLowerCase(Locale.US) : "pending";
        switch (s) {
            case "approved":
                tv.setText("APPROVED");
                tv.setBackgroundResource(R.drawable.bg_status_approved);
                tv.setTextColor(Color.parseColor("#1B5E20"));
                break;
            case "rejected":
                tv.setText("REJECTED");
                tv.setBackgroundResource(R.drawable.bg_status_rejected);
                tv.setTextColor(Color.parseColor("#B71C1C"));
                break;
            default:
                tv.setText("PENDING");
                tv.setBackgroundResource(R.drawable.bg_status_pending);
                tv.setTextColor(Color.parseColor("#E65100"));
                break;
        }
    }

    // ── Open business image (public URL) ───────────────────────────────────
    private void openImage(String url, String title) {
        Intent intent = new Intent(this, FullScreenImageActivity.class);
        intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_URL, url);
        intent.putExtra(FullScreenImageActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    // ── Open ID proof via signed URL (private bucket, admin only) ──────────
    private void openIdProofSecure() {
        if (pbIdProof != null) pbIdProof.setVisibility(View.VISIBLE);
        tvIdProof.setText("Fetching secure link…");
        tvIdProof.setTextColor(Color.parseColor("#9E9E9E"));

        // Use the bucket name from BuildConfig (QuickFix bucket for ID proofs)
        String bucket = BuildConfig.SUPABASE_BUCKET_ID_PROOF;
        SupabaseStorageHelper.getSignedUrl(bucket, app.idProofUrl, 300,
                new SupabaseStorageHelper.SignedUrlCallback() {
                    @Override
                    public void onSuccess(String signedUrl) {
                        if (pbIdProof != null) pbIdProof.setVisibility(View.GONE);
                        tvIdProof.setText("Tap to view ID proof (secure)");
                        tvIdProof.setTextColor(Color.parseColor("#D32F2F"));
                        openImage(signedUrl, "ID Proof");
                    }

                    @Override
                    public void onError(String message) {
                        if (pbIdProof != null) pbIdProof.setVisibility(View.GONE);
                        tvIdProof.setText("Tap to view ID proof (secure)");
                        tvIdProof.setTextColor(Color.parseColor("#D32F2F"));
                        toast("Could not load ID proof: " + message);
                    }
                });
    }

    // ── Approve ────────────────────────────────────────────────────────────
    private void confirmApprove() {
        new AlertDialog.Builder(this)
                .setTitle("Approve Provider")
                .setMessage("Approve \"" + display(app.businessName) + "\"?\n\nStatus → Approved. Provider role will be granted.")
                .setPositiveButton("Approve", (d, w) -> doApprove())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doApprove() {
        showProgress(true);
        ProviderApplicationRepository.approveApplication(app, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override public void onSuccess() {
                        showProgress(false);
                        app.status = "approved";
                        applyStatusBadge(tvHeaderStatus, "approved");
                        layoutActionButtons.setVisibility(View.GONE);
                        toast("Provider approved successfully!");
                        setResult(RESULT_OK);
                    }
                    @Override public void onError(String msg) {
                        showProgress(false);
                        toast("Approval failed: " + safe(msg));
                    }
                });
    }

    // ── Reject ─────────────────────────────────────────────────────────────
    private void showRejectDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(getString(R.string.reject_reason_hint));
        input.setTextColor(Color.BLACK);
        input.setLines(3);
        input.setMaxLines(5);
        int p = dp(16);
        input.setPadding(p, p / 2, p, p / 2);
        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(p, 0, p, 0);
        input.setLayoutParams(lp);
        wrap.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reject_dialog_title))
                .setMessage(getString(R.string.reject_dialog_message))
                .setView(wrap)
                .setPositiveButton("Reject", (d, w) -> {
                    String reason = input.getText() != null ? input.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        toast(getString(R.string.error_reject_reason_empty));
                        return;
                    }
                    doReject(reason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doReject(String reason) {
        showProgress(true);
        ProviderApplicationRepository.rejectApplication(app.id, reason, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override public void onSuccess() {
                        showProgress(false);
                        app.status = "rejected";
                        app.rejectionReason = reason;
                        applyStatusBadge(tvHeaderStatus, "rejected");
                        layoutActionButtons.setVisibility(View.GONE);
                        layoutRejectionReason.setVisibility(View.VISIBLE);
                        set(tvRejectionReason, reason);
                        toast(getString(R.string.reject_success));
                        setResult(RESULT_OK);
                    }
                    @Override public void onError(String msg) {
                        showProgress(false);
                        toast("Rejection failed: " + safe(msg));
                    }
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void showProgress(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (btnApprove  != null) btnApprove.setEnabled(!show);
        if (btnReject   != null) btnReject.setEnabled(!show);
    }

    private void set(TextView tv, String value) {
        if (tv == null) return;
        tv.setText((value != null && !value.trim().isEmpty()) ? value.trim() : "-");
    }

    private String safe(String v) { return v != null ? v : ""; }

    private String display(String v) {
        return (v != null && !v.trim().isEmpty()) ? v.trim() : "-";
    }

    private String fmtDate(String iso) {
        if (iso == null || iso.trim().isEmpty()) return "-";
        try {
            String s = iso.length() > 19 ? iso.substring(0, 19) : iso;
            Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(s);
            if (d == null) return iso.length() >= 10 ? iso.substring(0, 10) : iso;
            return new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(d);
        } catch (Exception e) {
            return iso.length() >= 10 ? iso.substring(0, 10) : iso;
        }
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}


