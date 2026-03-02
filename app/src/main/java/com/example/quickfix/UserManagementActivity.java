package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.UserAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * UserManagementActivity — complete admin user management screen.
 *
 * Features:
 *  • Gradient header with embedded search bar
 *  • Stats cards (Total / Providers / Admins / Verified) — loaded from Supabase
 *  • Filter chips: All / Users / Providers / Admins / Verified / Unverified
 *  • Sort: Newest / Oldest / Name A–Z
 *  • RecyclerView with modern user cards + 5 action buttons each
 *  • View Details → BottomSheet
 *  • Change Role → AlertDialog with radio buttons
 *  • Verify / Unverify — PATCH to Supabase + in-place update
 *  • Suspend / Activate — PATCH is_active to Supabase
 *  • Delete — confirmation dialog → DELETE from Supabase + remove from list
 *  • Pull-to-refresh
 *  • Shimmer loading / error / empty states
 */
public class UserManagementActivity extends AppCompatActivity
        implements UserAdapter.ActionListener {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextInputEditText etSearch;
    private ChipGroup         chipGroup;
    private Spinner           spinnerSort;
    private TextView          tvUserCount;
    private TextView          tvStatTotal, tvStatProviders, tvStatAdmins, tvStatVerified;
    private RecyclerView      recycler;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout      loadingLayout, errorLayout, emptyLayout;
    private TextView          tvErrorMsg;

    // ── State ──────────────────────────────────────────────────────────────
    private UserAdapter adapter;
    private String currentFilter = null;   // null = all
    private String currentSort   = "newest";
    private String currentQuery  = "";
    private String adminUid      = "";

    private static final String[] SORT_LABELS = {"Newest First", "Oldest First", "Name A–Z"};
    private static final String[] SORT_KEYS   = {"newest", "oldest", "name_az"};

    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) { finish(); return; }
        adminUid = me.getUid();

        bindViews();
        setupSort();
        setupChips();
        setupSearch();
        setupSwipeRefresh();
        loadData();
    }

    // ── View binding ───────────────────────────────────────────────────────
    private void bindViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        etSearch        = findViewById(R.id.etSearch);
        chipGroup       = findViewById(R.id.chipGroupFilter);
        spinnerSort     = findViewById(R.id.spinnerSort);
        tvUserCount     = findViewById(R.id.tvUserCount);
        tvStatTotal     = findViewById(R.id.tvStatTotal);
        tvStatProviders = findViewById(R.id.tvStatProviders);
        tvStatAdmins    = findViewById(R.id.tvStatAdmins);
        tvStatVerified  = findViewById(R.id.tvStatVerified);
        recycler        = findViewById(R.id.recyclerUsers);
        swipeRefresh    = findViewById(R.id.swipeRefresh);
        loadingLayout   = findViewById(R.id.loadingLayout);
        errorLayout     = findViewById(R.id.errorLayout);
        emptyLayout     = findViewById(R.id.emptyLayout);
        tvErrorMsg      = findViewById(R.id.tvErrorMsg);

        findViewById(R.id.btnRetry).setOnClickListener(v -> loadData());

        adapter = new UserAdapter(this);
        adapter.setListener(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.setHasFixedSize(false);

        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent_green);
    }

    // ── Sort spinner ───────────────────────────────────────────────────────
    private void setupSort() {
        ArrayAdapter<String> sa = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SORT_LABELS);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(sa);
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                currentSort = SORT_KEYS[pos];
                applyFilter();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // ── Filter chips ───────────────────────────────────────────────────────
    private void setupChips() {
        chipGroup.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) { currentFilter = null; applyFilter(); return; }
            int id = ids.get(0);
            if      (id == R.id.chipAll)        currentFilter = null;
            else if (id == R.id.chipUsers)      currentFilter = "user";
            else if (id == R.id.chipProviders)  currentFilter = "provider";
            else if (id == R.id.chipAdmins)     currentFilter = "admin";
            else if (id == R.id.chipVerified)   currentFilter = "verified";
            else if (id == R.id.chipUnverified) currentFilter = "unverified";
            applyFilter();
        });
    }

    // ── Search ─────────────────────────────────────────────────────────────
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void afterTextChanged(Editable e) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {
                currentQuery = s != null ? s.toString() : "";
                applyFilter();
            }
        });
    }

    // ── Swipe refresh ──────────────────────────────────────────────────────
    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(this::loadData);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOAD DATA
    // ══════════════════════════════════════════════════════════════════════
    private void loadData() {
        showState("loading");

        // Stats (non-blocking — update whenever they arrive)
        UserRepository.fetchStats(new UserRepository.StatsCallback() {
            @Override
            public void onSuccess(int total, int prov, int adm, int ver) {
                tvStatTotal    .setText(String.valueOf(total));
                tvStatProviders.setText(String.valueOf(prov));
                tvStatAdmins   .setText(String.valueOf(adm));
                tvStatVerified .setText(String.valueOf(ver));
                animateStats();
            }
            @Override public void onError(String msg) { /* non-fatal */ }
        });

        // Full user list
        UserRepository.fetchAllUsers(new UserRepository.UsersCallback() {
            @Override
            public void onSuccess(java.util.List<UserModel> users) {
                swipeRefresh.setRefreshing(false);
                adapter.setData(users);
                applyFilter();
                if (users.isEmpty()) showState("empty");
                else                 showState("list");
            }
            @Override
            public void onError(String msg) {
                swipeRefresh.setRefreshing(false);
                tvErrorMsg.setText(msg);
                showState("error");
            }
        });
    }

    // ── Apply current filter/search/sort ───────────────────────────────────
    private void applyFilter() {
        adapter.applyFilter(currentQuery, currentFilter, currentSort);
        int count = adapter.getDisplayCount();
        tvUserCount.setText(count + " user" + (count != 1 ? "s" : ""));
        if (count == 0 && !currentQuery.isEmpty()) showState("empty");
        else if (count == 0 && currentFilter != null) showState("empty");
        else if (count > 0) {
            emptyLayout.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    // ── UI state helper ────────────────────────────────────────────────────
    private void showState(String state) {
        loadingLayout.setVisibility("loading".equals(state) ? View.VISIBLE : View.GONE);
        errorLayout  .setVisibility("error".equals(state)   ? View.VISIBLE : View.GONE);
        emptyLayout  .setVisibility("empty".equals(state)   ? View.VISIBLE : View.GONE);
        recycler     .setVisibility("list".equals(state)    ? View.VISIBLE : View.GONE);
    }

    private void animateStats() {
        try {
            android.view.animation.Animation anim =
                    AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
            tvStatTotal.startAnimation(anim);
            tvStatProviders.startAnimation(anim);
            tvStatAdmins.startAnimation(anim);
            tvStatVerified.startAnimation(anim);
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ADAPTER ACTION CALLBACKS
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void onViewDetails(UserModel u) {
        View v = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_user_details, null);

        // Avatar
        TextView bsAvatar = v.findViewById(R.id.bsAvatar);
        String init = (u.name != null && !u.name.isEmpty())
                ? String.valueOf(u.name.charAt(0)).toUpperCase() : "?";
        bsAvatar.setText(init);
        bsAvatar.getBackground().setTint(u.getRoleColor());

        // Fields
        setText(v, R.id.bsName,      u.name);
        setText(v, R.id.bsRoleLabel, u.getRoleLabel());
        setText(v, R.id.bsEmail,     empty(u.email)     ? "—" : u.email);
        setText(v, R.id.bsPhone,     empty(u.phone)     ? "—" : u.phone);
        setText(v, R.id.bsUid,       empty(u.firebaseUid) ? "—" : u.firebaseUid);
        setText(v, R.id.bsVerified,  u.isVerified ? "Yes ✓" : "No ✗");
        setText(v, R.id.bsStatus,    u.isActive   ? "Active ✓" : "Suspended ✗");
        setText(v, R.id.bsCreated,   fmt(u.createdAt));
        setText(v, R.id.bsUpdated,   fmt(u.updatedAt));

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(v);
        sheet.show();
    }

    @Override
    public void onChangeRole(UserModel u) {
        int current = u.isAdmin ? 2 : u.isServiceProvider ? 1 : 0;
        String[] roles = {"User", "Provider", "Admin"};

        new AlertDialog.Builder(this)
                .setTitle("Change Role — " + u.name)
                .setSingleChoiceItems(roles, current, null)
                .setPositiveButton("Apply", (dialog, w) -> {
                    int sel = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    if (sel < 0 || sel == current) return;
                    boolean iu = sel == 0, ip = sel == 1, ia = sel == 2;
                    UserRepository.changeRole(u.firebaseUid, iu, ip, ia, new UserRepository.ActionCallback() {
                        @Override public void onSuccess() {
                            u.isUser = iu; u.isServiceProvider = ip; u.isAdmin = ia;
                            adapter.updateItem(u);
                            toast("Role → " + roles[sel]);
                            SupabaseClient.logAdminAction(adminUid, "role_changed",
                                    "users", u.firebaseUid, roles[sel]);
                        }
                        @Override public void onError(String msg) { toast("Error: " + msg); }
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onToggleVerify(UserModel u) {
        boolean next = !u.isVerified;
        String label = next ? "Verify" : "Unverify";
        new AlertDialog.Builder(this)
                .setTitle(label + " user?")
                .setMessage(label + " account for " + u.name + "?")
                .setPositiveButton(label, (d, w) ->
                        UserRepository.setVerified(u.firebaseUid, next, new UserRepository.ActionCallback() {
                            @Override public void onSuccess() {
                                u.isVerified = next;
                                adapter.updateItem(u);
                                toast((next ? "Verified: " : "Unverified: ") + u.name);
                                SupabaseClient.logAdminAction(adminUid,
                                        next ? "user_verified" : "user_unverified",
                                        "users", u.firebaseUid, null);
                            }
                            @Override public void onError(String msg) { toast("Error: " + msg); }
                        }))
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onToggleActive(UserModel u) {
        boolean next   = !u.isActive;
        String action  = next ? "Activate" : "Suspend";
        new AlertDialog.Builder(this)
                .setTitle(action + " user?")
                .setMessage(action + " account for " + u.name + "?")
                .setPositiveButton(action, (d, w) ->
                        UserRepository.setActive(u.firebaseUid, next, new UserRepository.ActionCallback() {
                            @Override public void onSuccess() {
                                u.isActive = next;
                                adapter.updateItem(u);
                                toast((next ? "Activated: " : "Suspended: ") + u.name);
                                SupabaseClient.logAdminAction(adminUid,
                                        next ? "user_activated" : "user_suspended",
                                        "users", u.firebaseUid, null);
                            }
                            @Override public void onError(String msg) { toast("Error: " + msg); }
                        }))
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDelete(UserModel u) {
        new AlertDialog.Builder(this)
                .setTitle("Delete user?")
                .setMessage("Permanently delete " + u.name + "?\n\nThis cannot be undone.")
                .setPositiveButton("Delete", (d, w) ->
                        UserRepository.deleteUser(u.firebaseUid, new UserRepository.ActionCallback() {
                            @Override public void onSuccess() {
                                adapter.removeItem(u.firebaseUid);
                                toast("Deleted: " + u.name);
                                SupabaseClient.logAdminAction(adminUid, "user_deleted",
                                        "users", u.firebaseUid, null);
                                // Decrement total stat
                                try {
                                    int cur = Integer.parseInt(tvStatTotal.getText().toString());
                                    tvStatTotal.setText(String.valueOf(Math.max(0, cur - 1)));
                                } catch (Exception ignored) {}
                                if (adapter.getDisplayCount() == 0) showState("empty");
                            }
                            @Override public void onError(String msg) { toast("Error: " + msg); }
                        }))
                .setNegativeButton("Cancel", null).show();
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void setText(View root, int id, String val) {
        TextView tv = root.findViewById(id);
        if (tv != null) tv.setText(val != null ? val : "—");
    }

    private boolean empty(String s) { return s == null || s.isEmpty(); }

    private String fmt(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
