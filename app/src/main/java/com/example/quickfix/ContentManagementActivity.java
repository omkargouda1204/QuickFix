package com.example.quickfix;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.AdminCategoryAdapter;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ContentManagementActivity — Modern Admin: Manage service categories.
 * Full CRUD: Add / Edit / Delete categories via Supabase REST API.
 */
public class ContentManagementActivity extends AppCompatActivity {

    private static final String TAG = "ContentMgmt";
    private static final String SUPABASE_URL  = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY      = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON       = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView          rvCategories;
    private SwipeRefreshLayout    swipeRefresh;
    private LinearLayout          shimmerLayout, emptyStateLayout;
    private TextView              tvCategoryCount;
    private TextInputEditText     etSearch;
    private ExtendedFloatingActionButton fabAdd;

    // Data
    private AdminCategoryAdapter  adapter;
    private final List<AdminCategory> allCategories      = new ArrayList<>();
    private final List<AdminCategory> filteredCategories = new ArrayList<>();

    // Auth
    private String adminUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_management);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        adminUid = user.getUid();

        bindViews();
        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        setupFab();
        loadCategories();
    }

    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvCategories      = findViewById(R.id.rvCategories);
        swipeRefresh      = findViewById(R.id.swipeRefresh);
        shimmerLayout     = findViewById(R.id.shimmerLayout);
        emptyStateLayout  = findViewById(R.id.emptyStateLayout);
        tvCategoryCount   = findViewById(R.id.tvCategoryCount);
        etSearch          = findViewById(R.id.etSearch);
        fabAdd            = findViewById(R.id.fabAddCategory);

        ImageButton btnBack    = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadCategories());
    }

    private void setupRecyclerView() {
        adapter = new AdminCategoryAdapter(this, filteredCategories, new AdminCategoryAdapter.Listener() {
            @Override public void onEdit(AdminCategory cat)   { showCategoryDialog(cat); }
            @Override public void onDelete(AdminCategory cat) { confirmDelete(cat); }
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(adapter);

        // Hide FAB on scroll down, show on scroll up
        rvCategories.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@androidx.annotation.NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8)  fabAdd.shrink();
                else if (dy < -8) fabAdd.extend();
            }
        });
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterList(String query) {
        filteredCategories.clear();
        String q = query.trim().toLowerCase();
        for (AdminCategory cat : allCategories) {
            if (q.isEmpty()
                    || (cat.name != null && cat.name.toLowerCase().contains(q))
                    || (cat.description != null && cat.description.toLowerCase().contains(q))) {
                filteredCategories.add(cat);
            }
        }
        adapter.notifyDataSetChanged();
        updateCountLabel();
        emptyStateLayout.setVisibility(filteredCategories.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent_green);
        swipeRefresh.setOnRefreshListener(this::loadCategories);
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showCategoryDialog(null));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOAD CATEGORIES
    // ─────────────────────────────────────────────────────────────────────
    private void loadCategories() {
        showShimmer(true);
        emptyStateLayout.setVisibility(View.GONE);

        // Use select=* to avoid HTTP 400 (42703) if icon_url/description don't exist
        String url = SUPABASE_URL + "/rest/v1/service_categories"
                + "?select=*"
                + "&order=name.asc";

        Request req = supabaseBuilder(url).get().build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull IOException e) {
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }

            @Override public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    if (code == 200) {
                        parseCategories(body);
                    } else {
                        showError("Failed to load categories (HTTP " + code + ")");
                    }
                });
            }
        });
    }

    private void parseCategories(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            allCategories.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                AdminCategory cat = new AdminCategory();
                cat.id          = o.optString("id",          "");
                cat.name        = o.optString("name",        "");
                // These columns may or may not exist — optString returns "" safely
                cat.description = o.optString("description", "");
                cat.iconUrl     = o.optString("icon_url",    "");
                cat.createdAt   = o.optString("created_at",  "");
                cat.providerCount = 0;
                allCategories.add(cat);
            }
            String query = etSearch != null && etSearch.getText() != null
                    ? etSearch.getText().toString() : "";
            filterList(query);
        } catch (Exception e) {
            Log.e(TAG, "parseCategories: " + e.getMessage());
            showError("Parse error: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADD / EDIT DIALOG
    // ─────────────────────────────────────────────────────────────────────
    private void showCategoryDialog(AdminCategory existing) {
        boolean isEdit = existing != null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_category, null);

        TextInputLayout tilName = dialogView.findViewById(R.id.tilCategoryName);
        TextInputEditText etName = dialogView.findViewById(R.id.etCategoryName);
        TextInputEditText etDesc = dialogView.findViewById(R.id.etDescription);
        TextInputEditText etIcon = dialogView.findViewById(R.id.etIconUrl);

        if (isEdit) {
            if (etName != null) etName.setText(existing.name);
            if (etDesc != null) etDesc.setText(existing.description);
            if (etIcon != null) etIcon.setText(existing.iconUrl);
        }

        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle(isEdit ? "✏️ Edit Category" : "➕ Add Category")
                .setView(dialogView)
                .setPositiveButton(isEdit ? "Update" : "Add", (d, w) -> {
                    String name = etName != null && etName.getText() != null
                            ? etName.getText().toString().trim() : "";
                    String desc = etDesc != null && etDesc.getText() != null
                            ? etDesc.getText().toString().trim() : "";
                    String icon = etIcon != null && etIcon.getText() != null
                            ? etIcon.getText().toString().trim() : "";

                    if (name.isEmpty()) {
                        tilName.setError("Category name is required");
                        return;
                    }
                    if (isEdit) updateCategory(existing.id, name, desc, icon);
                    else        insertCategory(name, desc, icon);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // INSERT CATEGORY
    // ─────────────────────────────────────────────────────────────────────
    private void insertCategory(String name, String desc, String icon) {
        try {
            JSONObject body = new JSONObject();
            body.put("id",   UUID.randomUUID().toString());
            body.put("name", name);  // ✅ name column always exists
            // Only include optional columns if non-empty (avoids 42703 if column missing)
            if (desc != null && !desc.isEmpty())  body.put("description", desc);
            if (icon != null && !icon.isEmpty())  body.put("icon_url",    icon);

            String url = SUPABASE_URL + "/rest/v1/service_categories";
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = supabaseBuilder(url)
                    .addHeader("Prefer", "return=minimal")
                    .post(rb).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws IOException {
                    int code = response.code(); response.close();
                    main.post(() -> {
                        if (code == 201 || code == 200 || code == 204) {
                            toast("✅ Category added");
                            logAdminAction("category_added", name);
                            loadCategories();
                        } else {
                            showError("Failed to add category (HTTP " + code + ")");
                        }
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE CATEGORY
    // ─────────────────────────────────────────────────────────────────────
    private void updateCategory(String id, String name, String desc, String icon) {
        try {
            JSONObject body = new JSONObject();
            body.put("name", name);  // ✅ always exists
            if (desc != null && !desc.isEmpty())  body.put("description", desc);
            if (icon != null && !icon.isEmpty())  body.put("icon_url",    icon);

            String url = SUPABASE_URL + "/rest/v1/service_categories?id=eq." + id;
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = supabaseBuilder(url)
                    .addHeader("Prefer", "return=minimal")
                    .patch(rb).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws IOException {
                    int code = response.code(); response.close();
                    main.post(() -> {
                        if (code == 200 || code == 204) {
                            toast("✅ Category updated");
                            logAdminAction("category_updated", name);
                            loadCategories();
                        } else {
                            showError("Failed to update category (HTTP " + code + ")");
                        }
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE CATEGORY
    // ─────────────────────────────────────────────────────────────────────
    private void confirmDelete(AdminCategory cat) {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("🗑 Delete Category")
                .setMessage("Delete \"" + cat.name + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteCategory(cat))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategory(AdminCategory cat) {
        String url = SUPABASE_URL + "/rest/v1/service_categories?id=eq." + cat.id;
        Request req = supabaseBuilder(url).delete().build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull IOException e) {
                main.post(() -> showError("Network error: " + e.getMessage()));
            }
            @Override public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) throws IOException {
                int code = response.code(); response.close();
                main.post(() -> {
                    if (code == 200 || code == 204) {
                        toast("🗑 Category deleted");
                        logAdminAction("category_deleted", cat.name);
                        loadCategories();
                    } else {
                        showError("Failed to delete category (HTTP " + code + ")");
                    }
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────
    private Request.Builder supabaseBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Accept",        "application/json");
    }

    private void showShimmer(boolean show) {
        shimmerLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        rvCategories.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateCountLabel() {
        tvCategoryCount.setText(filteredCategories.size() + " categor" +
                (filteredCategories.size() == 1 ? "y" : "ies"));
    }

    private void showError(String msg) {
        Toast.makeText(this, "❌ " + msg, Toast.LENGTH_LONG).show();
        Log.e(TAG, msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void logAdminAction(String action, String target) {
        SupabaseClient.logAdminAction(adminUid, action, "service_categories", target, null);
    }
}
