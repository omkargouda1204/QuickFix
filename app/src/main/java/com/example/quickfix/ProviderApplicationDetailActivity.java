package com.example.quickfix;

import android.content.Intent;
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
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ProviderApplicationDetailActivity — Full details view for a single provider application.
 */
public class ProviderApplicationDetailActivity extends BaseActivity {

    public static final String EXTRA_APP_ID           = "app_id";
    public static final String EXTRA_FIREBASE_UID     = "firebase_uid";
    public static final String EXTRA_USER_ID          = "user_id";
    public static final String EXTRA_BUSINESS_NAME    = "business_name";
    public static final String EXTRA_OWNER_NAME       = "owner_name";
    public static final String EXTRA_EMAIL            = "email";
    public static final String EXTRA_PHONE            = "phone";
    public static final String EXTRA_CATEGORY         = "category";
    public static final String EXTRA_AREA             = "area";
    public static final String EXTRA_CITY             = "city";
    public static final String EXTRA_ADDRESS          = "address";
    public static final String EXTRA_EXPERIENCE       = "experience";
    public static final String EXTRA_DESCRIPTION      = "description";
    public static final String EXTRA_STATUS           = "status";
    public static final String EXTRA_SUBMITTED_AT     = "submitted_at";
    public static final String EXTRA_REJECTION_REASON = "rejection_reason";
    public static final String EXTRA_BUSINESS_IMG     = "business_image_url";
    public static final String EXTRA_ID_PROOF         = "id_proof_url";

    private TextView     tvHeaderStatus, tvHeaderBusinessName;
    private TextView     tvBusinessName, tvOwnerName, tvEmail, tvPhone;
    private TextView     tvCategory, tvArea, tvExperience, tvDescription;
    private TextView     tvDate, tvRejectionReason;
    private LinearLayout layoutRejectionReason, layoutActionButtons;
    private TextView     tvBusinessImage, tvIdProof;
    private ProgressBar  pbIdProof, progressBar;
    private MaterialButton btnApprove, btnReject;

    private ProviderApplication app;
    private String adminUid = "";

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

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void bindViews() {
        tvHeaderStatus       = findViewById(R.id.tvHeaderStatus);
        tvHeaderBusinessName = findViewById(R.id.tvHeaderBusinessName);
        tvBusinessName       = findViewById(R.id.tvBusinessName);
        tvOwnerName          = findViewById(R.id.tvOwnerName);
        tvEmail              = findViewById(R.id.tvEmail);
        tvPhone              = findViewById(R.id.tvPhone);
        tvCategory           = findViewById(R.id.tvCategory);
        tvArea               = findViewById(R.id.tvArea);
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
        app.area            = safe(i.getStringExtra(EXTRA_AREA));
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

    private void populateUI() {
        set(tvHeaderBusinessName, app.businessName);
        applyStatusBadge(tvHeaderStatus, app.status);

        set(tvBusinessName, app.businessName);
        set(tvOwnerName,    app.name);
        set(tvEmail,        app.email);
        set(tvPhone,        app.phone);

        set(tvCategory,    app.category);
        String location = !app.area.isEmpty() ? app.area : app.city;
        if (!app.address.isEmpty()) {
            location = app.address + (location.isEmpty() ? "" : ", " + location);
        }
        set(tvArea,        location);
        set(tvExperience,  app.experience.isEmpty() ? "-" : app.experience + " years");
        set(tvDescription, app.description);
        set(tvDate, fmtDate(app.submittedAt));

        if (!app.rejectionReason.isEmpty()) {
            layoutRejectionReason.setVisibility(View.VISIBLE);
            set(tvRejectionReason, app.rejectionReason);
        }

        if ("pending".equalsIgnoreCase(app.status)) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            btnApprove.setOnClickListener(v -> doApprove());
            btnReject.setOnClickListener(v -> doReject());
        }
    }

    private void applyStatusBadge(TextView tv, String status) {
        if (tv == null) return;
        String s = status != null ? status.toLowerCase(Locale.US) : "pending";
        if ("approved".equals(s)) {
            tv.setText("APPROVED");
            tv.setBackgroundResource(R.drawable.bg_status_approved);
        } else if ("rejected".equals(s)) {
            tv.setText("REJECTED");
            tv.setBackgroundResource(R.drawable.bg_status_rejected);
        } else {
            tv.setText("PENDING");
            tv.setBackgroundResource(R.drawable.bg_status_pending);
        }
    }

    private void doApprove() {
        showProgress(true);
        ProviderApplicationRepository.approveApplication(app, adminUid, new ProviderApplicationRepository.Callback() {
            @Override public void onSuccess() {
                showProgress(false);
                toast("Approved!");
                finish();
            }
            @Override public void onError(String msg) { showProgress(false); toast(msg); }
        });
    }

    private void doReject() {
        // Show simplified rejection for brevity
        ProviderApplicationRepository.rejectApplication(app.id, "Rejected by admin", adminUid, new ProviderApplicationRepository.Callback() {
            @Override public void onSuccess() { toast("Rejected"); finish(); }
            @Override public void onError(String msg) { toast(msg); }
        });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void set(TextView tv, String v) {
        if (tv != null) tv.setText((v == null || v.isEmpty()) ? "-" : v);
    }

    private String safe(String v) { return v == null ? "" : v.trim(); }

    private String fmtDate(String iso) {
        if (iso == null || iso.isEmpty()) return "-";
        return iso.substring(0, 10); // Simplified
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
