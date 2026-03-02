package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.example.quickfix.adapters.ProviderApplicationAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminProviderApprovalActivity — Admin screen to review, approve and reject
 * provider applications from the provider_applications Supabase table.
 *
 * Security: verifies admin role via Supabase before loading data.
 * Only accessible from AdminDashboardActivity.
 */
public class AdminProviderApprovalActivity extends AppCompatActivity
        implements ProviderApplicationAdapter.OnActionListener {

    private static final String TAG = "AdminProviderApproval";

    // ── Views ─────────────────────────────────────────────────────────────
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState, errorState;
    private TextView tvResultCount, tvErrorMessage;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupFilter;
    private com.google.android.material.button.MaterialButton btnRetry;

    // ── Adapter & data ────────────────────────────────────────────────────
    private ProviderApplicationAdapter adapter;
    private List<ProviderApplication> allApplications = new ArrayList<>();
    private String currentFilter = "all";

    // ── Firebase ──────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private String adminUid;

    // ═══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_provider_approval);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            redirectToAdminLogin();
            return;
        }
        adminUid = user.getUid();

        setupToolbar();
        bindViews();
        setupRecyclerView();
        setupChipFilter();
        setupSearch();
        setupSwipeRefresh();

        // Verify admin role before showing data
        verifyAdminRoleAndLoad(user);
    }

    // ── Toolbar ───────────────────────────────────────────────────────────
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Provider Applications");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Bind views ────────────────────────────────────────────────────────
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
        btnRetry        = findViewById(R.id.btnRetry);

        btnRetry.setOnClickListener(v -> loadApplications());
    }

    // ── RecyclerView ──────────────────────────────────────────────────────
    private void setupRecyclerView() {
        adapter = new ProviderApplicationAdapter(this);
        adapter.setOnActionListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);
    }

    // ── Chip filter ───────────────────────────────────────────────────────
    private void setupChipFilter() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipAll)      currentFilter = "all";
            else if (id == R.id.chipPending)   currentFilter = "pending";
            else if (id == R.id.chipApproved)  currentFilter = "approved";
            else if (id == R.id.chipRejected)  currentFilter = "rejected";

            applyFilterAndSearch();
        });
    }

    // ── Search ────────────────────────────────────────────────────────────
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void onTextChanged(CharSequence s, int i, int i1, int i2) {}
            @Override public void afterTextChanged(Editable s) { applyFilterAndSearch(); }
        });
    }

    // ── Swipe to refresh ─────────────────────────────────────────────────
    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent_green);
        swipeRefresh.setOnRefreshListener(this::loadApplications);
    }

    // ── Admin role verification ───────────────────────────────────────────
    private void verifyAdminRoleAndLoad(FirebaseUser user) {
        showLoading(true);
        SupabaseClient.getUserRoleWithEmail(
                user.getUid(),
                user.getEmail(),
                new SupabaseClient.RoleInfoCallback() {
                    @Override
                    public void onSuccess(SupabaseClient.RoleInfo info) {
                        if (!info.isAdmin) {
                            showLoading(false);
                            Toast.makeText(AdminProviderApprovalActivity.this,
                                    "Access denied. Admin only.", Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        loadApplications();
                    }

                    @Override
                    public void onError(String message) {
                        showLoading(false);
                        // If we can't verify, block access
                        Toast.makeText(AdminProviderApprovalActivity.this,
                                "Could not verify admin role.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
    }

    // ── Load data ─────────────────────────────────────────────────────────
    private void loadApplications() {
        showLoading(true);
        hideAllStates();

        ProviderApplicationRepository.fetchApplications(null,
                new ProviderApplicationRepository.ApplicationListCallback() {
                    @Override
                    public void onSuccess(List<ProviderApplication> applications) {
                        showLoading(false);
                        swipeRefresh.setRefreshing(false);
                        allApplications.clear();
                        allApplications.addAll(applications);
                        applyFilterAndSearch();
                    }

                    @Override
                    public void onError(String message) {
                        showLoading(false);
                        swipeRefresh.setRefreshing(false);
                        showErrorState("Failed to load applications.\n" + message);
                    }
                });
    }

    // ── Filter + Search logic ─────────────────────────────────────────────
    private void applyFilterAndSearch() {
        String query = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";

        List<ProviderApplication> filtered = new ArrayList<>();
        for (ProviderApplication app : allApplications) {
            // Status filter
            boolean statusMatch = "all".equals(currentFilter)
                    || currentFilter.equalsIgnoreCase(app.status);
            if (!statusMatch) continue;

            // Text search
            if (!query.isEmpty()) {
                boolean textMatch = contains(app.businessName, query)
                        || contains(app.name, query)
                        || contains(app.city, query)
                        || contains(app.category, query);
                if (!textMatch) continue;
            }

            filtered.add(app);
        }

        adapter.setData(filtered);

        if (filtered.isEmpty()) {
            showEmptyState(query.isEmpty()
                    ? "No " + currentFilter + " applications"
                    : "No results for \"" + query + "\"");
        } else {
            showRecyclerView();
        }

        tvResultCount.setText(filtered.size() + " application" + (filtered.size() == 1 ? "" : "s"));
    }

    private boolean contains(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  APPROVE ACTION
    // ═══════════════════════════════════════════════════════════════════════
    @Override
    public void onApprove(ProviderApplication application) {
        new AlertDialog.Builder(this)
                .setTitle("Approve Provider")
                .setMessage("Approve " + application.businessName + "?\n\n"
                        + "This will:\n"
                        + "• Set application status → approved\n"
                        + "• Create provider profile\n"
                        + "• Grant provider role to user")
                .setPositiveButton("Approve", (dialog, which) ->
                        performApprove(application))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performApprove(ProviderApplication application) {
        showLoading(true);

        ProviderApplicationRepository.approveApplication(application, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        showLoading(false);
                        Toast.makeText(AdminProviderApprovalActivity.this,
                                getString(R.string.approve_success), Toast.LENGTH_SHORT).show();

                        // Update item in adapter
                        application.status = "approved";
                        adapter.updateItem(application);
                        applyFilterAndSearch();

                        // Log admin action
                        SupabaseClient.logAdminAction(adminUid, "APPROVE_PROVIDER",
                                "provider_applications", application.id,
                                "Approved: " + application.businessName);
                    }

                    @Override
                    public void onError(String message) {
                        showLoading(false);
                        new AlertDialog.Builder(AdminProviderApprovalActivity.this)
                                .setTitle("Approval Failed")
                                .setMessage("Could not approve the application.\n\n" + message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  REJECT ACTION
    // ═══════════════════════════════════════════════════════════════════════
    @Override
    public void onReject(ProviderApplication application) {
        // Show dialog to collect rejection reason
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.reject_dialog_title));
        builder.setMessage(getString(R.string.reject_dialog_message));

        // Input field for rejection reason
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(getString(R.string.reject_reason_hint));
        input.setTextColor(0xFF000000);
        input.setLines(3);
        input.setMaxLines(5);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);

        // Wrap in a FrameLayout for padding
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(padding, 0, padding, 0);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);

        builder.setPositiveButton("Reject", (dialog, which) -> {
            String reason = input.getText().toString().trim();
            if (reason.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_reject_reason_empty),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            performReject(application, reason);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void performReject(ProviderApplication application, String reason) {
        showLoading(true);

        ProviderApplicationRepository.rejectApplication(application.id, reason, adminUid,
                new ProviderApplicationRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        showLoading(false);
                        Toast.makeText(AdminProviderApprovalActivity.this,
                                getString(R.string.reject_success), Toast.LENGTH_SHORT).show();

                        // Update item in adapter
                        application.status = "rejected";
                        application.rejectionReason = reason;
                        adapter.updateItem(application);
                        applyFilterAndSearch();

                        // Log admin action
                        SupabaseClient.logAdminAction(adminUid, "REJECT_PROVIDER",
                                "provider_applications", application.id,
                                "Rejected: " + application.businessName + " | Reason: " + reason);
                    }

                    @Override
                    public void onError(String message) {
                        showLoading(false);
                        new AlertDialog.Builder(AdminProviderApprovalActivity.this)
                                .setTitle("Rejection Failed")
                                .setMessage("Could not reject the application.\n\n" + message)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
    }

    // ── UI state helpers ──────────────────────────────────────────────────
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            errorState.setVisibility(View.GONE);
        }
    }

    private void hideAllStates() {
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showRecyclerView() {
        recyclerView.setVisibility(View.VISIBLE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
    }

    private void showEmptyState(String title) {
        recyclerView.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        TextView tvTitle = emptyState.findViewById(R.id.tvEmptyTitle);
        if (tvTitle != null) tvTitle.setText(title);
    }

    private void showErrorState(String msg) {
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.VISIBLE);
        if (tvErrorMessage != null) tvErrorMessage.setText(msg);
    }

    private void redirectToAdminLogin() {
        startActivity(new Intent(this, AdminLoginActivity.class));
        finish();
    }
}

