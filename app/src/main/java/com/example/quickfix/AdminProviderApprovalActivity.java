package com.example.quickfix;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.example.quickfix.adapters.ProviderApplicationAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminProviderApprovalActivity extends BaseActivity
        implements ProviderApplicationAdapter.OnActionListener {

    private static final String TAG = "AdminProviderApproval";

    private RecyclerView       recyclerView;
    // Use fully-qualified name to avoid IDE indexing issue with swiperefreshlayout AAR
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private ProgressBar        progressBar;
    private LinearLayout       emptyState, errorState;
    private TextView           tvResultCount, tvErrorMessage;
    private TextInputEditText  etSearch;
    private ChipGroup          chipGroupFilter;

    private ProviderApplicationAdapter      adapter;
    private final List<ProviderApplication> allApplications = new ArrayList<>();
    private String currentFilter = "all";
    private String adminUid = "";

    // Launcher for detail screen — reload list if status changed
    private final ActivityResultLauncher<Intent> detailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            loadApplications();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_admin_provider_approval);
        } catch (Exception e) {
            Log.e(TAG, "layout failed: " + e.getMessage(), e);
            Toast.makeText(this, "Failed to open screen.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { redirectToAdminLogin(); return; }
        adminUid = user.getUid();
        setupToolbar();
        bindViews();
        setupRecyclerView();
        setupChipFilter();
        setupSearch();
        setupSwipeRefresh();
        verifyAdminAndLoad(user);
    }

    private void setupToolbar() {
        Toolbar tb = findViewById(R.id.toolbar);
        if (tb == null) return;
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");   // title shown in plain TextView, not ActionBar
        }
        tb.setNavigationOnClickListener(v -> finish());
    }

    private void bindViews() {
        recyclerView    = findViewById(R.id.recyclerView);
        swipeRefresh    = findViewById(R.id.swipeRefresh);
        progressBar     = findViewById(R.id.progressBar);
        emptyState      = findViewById(R.id.emptyState);
        errorState      = findViewById(R.id.errorState);
        tvResultCount   = findViewById(R.id.tvResultCount);
        tvErrorMessage  = findViewById(R.id.tvErrorMessage);
        etSearch        = findViewById(R.id.etSearch);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        MaterialButton btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) btnRetry.setOnClickListener(v -> loadApplications());
    }

    private void setupRecyclerView() {
        if (recyclerView == null) return;
        adapter = new ProviderApplicationAdapter(this);
        adapter.setOnActionListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);
    }

    private void setupChipFilter() {
        if (chipGroupFilter == null) return;
        chipGroupFilter.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if      (id == R.id.chipAll)      currentFilter = "all";
            else if (id == R.id.chipPending)  currentFilter = "pending";
            else if (id == R.id.chipApproved) currentFilter = "approved";
            else if (id == R.id.chipRejected) currentFilter = "rejected";
            applyFilterAndSearch();
        });
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilterAndSearch(); }
        });
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh == null) return;
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent_green);
        swipeRefresh.setOnRefreshListener(this::loadApplications);
    }

    private void verifyAdminAndLoad(@NonNull FirebaseUser user) {
        showLoading(true);
        String uid   = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";
        SupabaseClient.getUserRoleWithEmail(uid, email,
                new SupabaseClient.RoleInfoCallback() {
                    @Override
                    public void onSuccess(SupabaseClient.RoleInfo info) {
                        if (isFinishing() || isDestroyed()) return;
                        if (info == null || !info.isAdmin) {
                            showLoading(false);
                            toast("Access denied. Admin only.");
                            finish();
                            return;
                        }
                        loadApplications();
                    }
                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        toast("Could not verify admin role.");
                        finish();
                    }
                });
    }

    private void loadApplications() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        showLoading(true);
        hideAllContentStates();
        ProviderApplicationRepository.fetchApplications(null,
                new ProviderApplicationRepository.ApplicationListCallback() {
                    @Override
                    public void onSuccess(List<ProviderApplication> list) {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        allApplications.clear();
                        if (list != null) allApplications.addAll(list);
                        applyFilterAndSearch();
                    }
                    @Override
                    public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        showErrorState("Failed to load.\n"
                                + (message != null ? message : "Unknown error"));
                    }
                });
    }

    private void applyFilterAndSearch() {
        String query = "";
        if (etSearch != null && etSearch.getText() != null)
            query = etSearch.getText().toString().trim().toLowerCase(Locale.US);
        List<ProviderApplication> filtered = new ArrayList<>();
        for (ProviderApplication app : allApplications) {
            if (app == null) continue;
            if (!"all".equals(currentFilter)
                    && !currentFilter.equalsIgnoreCase(safe(app.status))) continue;
            if (!query.isEmpty() && !matchesSearch(app, query)) continue;
            filtered.add(app);
        }
        if (adapter != null) adapter.setData(filtered);
        int n = filtered.size();
        if (tvResultCount != null)
            tvResultCount.setText(String.format(Locale.US, "%d %s", n,
                    n == 1 ? "application" : "applications"));
        if (n == 0)
            showEmptyState(query.isEmpty()
                    ? "No " + currentFilter + " applications"
                    : "No results for: " + query);
        else
            showRecyclerView();
    }

    private boolean matchesSearch(@NonNull ProviderApplication app, @NonNull String q) {
        return containsIC(app.businessName, q) || containsIC(app.name, q)
                || containsIC(app.city, q)     || containsIC(app.category, q)
                || containsIC(app.email, q)    || containsIC(app.phone, q);
    }

    @Override
    public void onApprove(ProviderApplication app) {
        if (app == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Approve Provider")
                .setMessage("Approve \"" + display(app.businessName) + "\"?\n\n"
                        + "Status → Approved. Provider role will be granted.")
                .setPositiveButton("Approve", (d, w) -> doApprove(app))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doApprove(@NonNull ProviderApplication app) {
        if (adminUid.isEmpty()) { toast("Session expired."); return; }
        showLoading(true);
        ProviderApplicationRepository.approveApplication(app, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override public void onSuccess() {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        toast(getString(R.string.approve_success));
                        app.status = "approved";
                        if (adapter != null) adapter.updateItem(app);
                        applyFilterAndSearch();
                        logAction("APPROVE_PROVIDER", app);
                    }
                    @Override public void onError(String msg) {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        showDialog("Approval Failed", "Could not approve.\n\n" + safe(msg));
                    }
                });
    }

    @Override
    public void onReject(ProviderApplication app) {
        if (app == null) return;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(getString(R.string.reject_reason_hint));
        input.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
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
                    String reason = input.getText() != null
                            ? input.getText().toString().trim() : "";
                    if (reason.isEmpty()) {
                        toast(getString(R.string.error_reject_reason_empty));
                        return;
                    }
                    doReject(app, reason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doReject(@NonNull ProviderApplication app, @NonNull String reason) {
        if (adminUid.isEmpty()) { toast("Session expired."); return; }
        showLoading(true);
        ProviderApplicationRepository.rejectApplication(safe(app.id), reason, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override public void onSuccess() {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        toast(getString(R.string.reject_success));
                        app.status = "rejected";
                        app.rejectionReason = reason;
                        if (adapter != null) adapter.updateItem(app);
                        applyFilterAndSearch();
                        logAction("REJECT_PROVIDER", app);
                    }
                    @Override public void onError(String msg) {
                        if (isFinishing() || isDestroyed()) return;
                        showLoading(false);
                        showDialog("Rejection Failed", "Could not reject.\n\n" + safe(msg));
                    }
                });
    }

    @Override
    public void onViewDetails(ProviderApplication app) {
        if (app == null) { toast("No details available."); return; }

        Intent intent = new Intent(this, ProviderApplicationDetailActivity.class);
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_APP_ID,           safe(app.id));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_FIREBASE_UID,     safe(app.firebaseUid));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_USER_ID,          safe(app.userId));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_BUSINESS_NAME,    safe(app.businessName));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_OWNER_NAME,       safe(app.name));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_EMAIL,            safe(app.email));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_PHONE,            safe(app.phone));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_CATEGORY,         safe(app.category));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_CITY,             safe(app.city));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_ADDRESS,          safe(app.address));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_EXPERIENCE,       safe(app.experience));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_DESCRIPTION,      safe(app.description));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_STATUS,           safe(app.status));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_SUBMITTED_AT,     safe(app.submittedAt));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_REJECTION_REASON, safe(app.rejectionReason));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_BUSINESS_IMG,     safe(app.businessImageUrl));
        intent.putExtra(ProviderApplicationDetailActivity.EXTRA_ID_PROOF,         safe(app.idProofUrl));
        detailLauncher.launch(intent);
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
            if (emptyState   != null) emptyState.setVisibility(View.GONE);
            if (errorState   != null) errorState.setVisibility(View.GONE);
        }
    }

    private void hideAllContentStates() {
        if (emptyState != null) emptyState.setVisibility(View.GONE);
        if (errorState != null) errorState.setVisibility(View.GONE);
    }

    private void showRecyclerView() {
        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        if (emptyState   != null) emptyState.setVisibility(View.GONE);
        if (errorState   != null) errorState.setVisibility(View.GONE);
    }

    private void showEmptyState(String message) {
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (errorState   != null) errorState.setVisibility(View.GONE);
        if (emptyState   != null) {
            emptyState.setVisibility(View.VISIBLE);
            TextView t = emptyState.findViewById(R.id.tvEmptyTitle);
            if (t != null) t.setText(message != null ? message : "No data");
        }
    }

    private void showErrorState(String msg) {
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (emptyState   != null) emptyState.setVisibility(View.GONE);
        if (errorState   != null) {
            errorState.setVisibility(View.VISIBLE);
            if (tvErrorMessage != null) tvErrorMessage.setText(safe(msg));
        }
    }

    private boolean containsIC(String field, String q) {
        return field != null && !field.isEmpty()
                && field.toLowerCase(Locale.US).contains(q);
    }

    private String safe(String v)    { return v != null ? v : ""; }
    private String display(String v) { return (v != null && !v.trim().isEmpty()) ? v.trim() : "-"; }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void showDialog(String title, String msg) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(title).setMessage(msg)
                .setPositiveButton("OK", null).show();
    }

    private void logAction(String action, ProviderApplication app) {
        SupabaseClient.logAdminAction(adminUid, action, "provider_applications",
                safe(app.id), action + ": " + safe(app.businessName));
    }

    private void redirectToAdminLogin() {
        startActivity(new Intent(this, AdminLoginActivity.class));
        finish();
    }
}
