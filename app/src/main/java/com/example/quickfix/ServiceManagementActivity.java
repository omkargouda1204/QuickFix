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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.AdminCategory;
import com.example.quickfix.AdminService;
import com.example.quickfix.BuildConfig;
import com.example.quickfix.R;
import com.example.quickfix.SupabaseClient;
import com.example.quickfix.adapters.AdminServiceAdapter;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
 * ServiceManagementActivity — Modern Admin: Manage services.
 * Full CRUD: Add / Edit / Delete / Toggle-Status via Supabase REST API.
 * Search + Category filter.
 */
public class ServiceManagementActivity extends AppCompatActivity {

    private static final String TAG          = "ServiceMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON      = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView       rvServices;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout       shimmerLayout, emptyStateLayout;
    private TextView           tvServiceCount;
    private TextInputEditText  etSearch;
    private AutoCompleteTextView acCategoryFilter;
    private ExtendedFloatingActionButton fabAdd;

    // Data
    private AdminServiceAdapter           adapter;
    private final List<AdminService>      allServices      = new ArrayList<>();
    private final List<AdminService>      filteredServices = new ArrayList<>();
    private final List<AdminCategory>     categories       = new ArrayList<>();
    private final List<String>            categoryNames    = new ArrayList<>();
    private String                        selectedFilterCategory = "All";

    // Auth
    private String adminUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_management);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        adminUid = user.getUid();

        bindViews();
        setupRecyclerView();
        setupSearch();
        setupSwipeRefresh();
        setupFab();
        loadCategories(); // loads categories first, then services
    }

    // ─────────────────────────────────────────────────────────────────────
    private void bindViews() {
        rvServices       = findViewById(R.id.rvServices);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        shimmerLayout    = findViewById(R.id.shimmerLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvServiceCount   = findViewById(R.id.tvServiceCount);
        etSearch         = findViewById(R.id.etSearch);
        acCategoryFilter = findViewById(R.id.acCategoryFilter);
        fabAdd           = findViewById(R.id.fabAddService);

        ImageButton btnBack    = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadServices());
    }

    private void setupRecyclerView() {
        adapter = new AdminServiceAdapter(this, filteredServices, new AdminServiceAdapter.Listener() {
            @Override public void onEdit(AdminService svc)   { showServiceDialog(svc); }
            @Override public void onDelete(AdminService svc) { confirmDeleteService(svc); }
            @Override public void onToggleStatus(AdminService svc, boolean newStatus) {
                toggleServiceStatus(svc, newStatus);
            }
        });
        rvServices.setLayoutManager(new LinearLayoutManager(this));
        rvServices.setAdapter(adapter);

        rvServices.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 8) fabAdd.shrink();
                else if (dy < -8) fabAdd.extend();
            }
        });
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary, R.color.accent_green);
        swipeRefresh.setOnRefreshListener(this::loadServices);
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showServiceDialog(null));
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOAD CATEGORIES (for filter dropdown + dialog)
    // ─────────────────────────────────────────────────────────────────────
    private void loadCategories() {
        String url = SUPABASE_URL + "/rest/v1/service_categories"
                + "?select=id,name&order=name.asc";
        Request req = supabaseBuilder(url).get().build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> loadServices()); // proceed even if categories fail
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                main.post(() -> {
                    if (code == 200) parseCategories(body);
                    loadServices();
                });
            }
        });
    }

    private void parseCategories(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            categories.clear();
            categoryNames.clear();
            categoryNames.add("All");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                AdminCategory cat = new AdminCategory();
                cat.id   = o.optString("id",   "");
                cat.name = o.optString("name",  "");
                categories.add(cat);
                categoryNames.add(cat.name);
            }
            // Set up category filter dropdown
            if (acCategoryFilter != null) {
                ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                        this, android.R.layout.simple_dropdown_item_1line, categoryNames);
                acCategoryFilter.setAdapter(filterAdapter);
                acCategoryFilter.setText("All", false);
                acCategoryFilter.setOnItemClickListener((parent, v, pos, id) -> {
                    selectedFilterCategory = categoryNames.get(pos);
                    applyFilters();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "parseCategories: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOAD SERVICES
    // ─────────────────────────────────────────────────────────────────────
    private void loadServices() {
        showShimmer(true);
        emptyStateLayout.setVisibility(View.GONE);

        String url = SUPABASE_URL + "/rest/v1/services"
                + "?select=id,name,category_id,description,base_price,icon_url,is_active,created_at"
                + ",service_categories(name)"
                + "&order=name.asc";

        Request req = supabaseBuilder(url).get().build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    if (code == 200) parseServices(body);
                    else showError("Failed to load services (HTTP " + code + ")");
                });
            }
        });
    }

    private void parseServices(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            allServices.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                AdminService svc = new AdminService();
                svc.id           = o.optString("id",          "");
                svc.name         = o.optString("name",        "");
                svc.categoryId   = o.optString("category_id", "");
                svc.description  = o.optString("description", "");
                svc.basePrice    = o.optDouble("base_price",  0);
                svc.iconUrl      = o.optString("icon_url",    "");
                svc.isActive     = o.optBoolean("is_active",  true);
                svc.createdAt    = o.optString("created_at",  "");
                // Embedded join result
                JSONObject catObj = o.optJSONObject("service_categories");
                svc.categoryName = catObj != null
                        ? catObj.optString("name", "Uncategorized") : "Uncategorized";
                allServices.add(svc);
            }
            applyFilters();
        } catch (Exception e) {
            Log.e(TAG, "parseServices: " + e.getMessage());
            showError("Parse error");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FILTER
    // ─────────────────────────────────────────────────────────────────────
    private void applyFilters() {
        String query = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";
        String catFilter = selectedFilterCategory != null ? selectedFilterCategory : "All";

        filteredServices.clear();
        for (AdminService svc : allServices) {
            boolean matchSearch = query.isEmpty()
                    || (svc.name != null && svc.name.toLowerCase().contains(query))
                    || (svc.description != null && svc.description.toLowerCase().contains(query))
                    || (svc.categoryName != null && svc.categoryName.toLowerCase().contains(query));
            boolean matchCat = catFilter.equals("All")
                    || (svc.categoryName != null && svc.categoryName.equals(catFilter));
            if (matchSearch && matchCat) filteredServices.add(svc);
        }
        adapter.notifyDataSetChanged();
        updateCountLabel();
        emptyStateLayout.setVisibility(filteredServices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADD / EDIT SERVICE DIALOG
    // ─────────────────────────────────────────────────────────────────────
    private void showServiceDialog(AdminService existing) {
        boolean isEdit = existing != null;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_service, null);

        TextInputLayout    tilName  = dialogView.findViewById(R.id.tilServiceName);
        TextInputEditText  etName   = dialogView.findViewById(R.id.etServiceName);
        TextInputEditText  etPrice  = dialogView.findViewById(R.id.etPrice);
        TextInputEditText  etDesc   = dialogView.findViewById(R.id.etServiceDescription);
        TextInputEditText  etIcon   = dialogView.findViewById(R.id.etIconUrl);
        AutoCompleteTextView acCat  = dialogView.findViewById(R.id.acCategory);
        SwitchMaterial     swActive = dialogView.findViewById(R.id.switchActive);

        // Populate category dropdown inside dialog
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categoryNames);
        if (acCat != null) acCat.setAdapter(catAdapter);

        if (isEdit) {
            if (etName   != null) etName.setText(existing.name);
            if (etPrice  != null) etPrice.setText(String.valueOf((int) existing.basePrice));
            if (etDesc   != null) etDesc.setText(existing.description);
            if (etIcon   != null) etIcon.setText(existing.iconUrl);
            if (acCat    != null) acCat.setText(existing.categoryName, false);
            if (swActive != null) swActive.setChecked(existing.isActive);
        }

        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle(isEdit ? "✏️ Edit Service" : "➕ Add Service")
                .setView(dialogView)
                .setPositiveButton(isEdit ? "Update" : "Add", (d, w) -> {
                    String name  = etName  != null && etName.getText()  != null ? etName.getText().toString().trim()  : "";
                    String price = etPrice != null && etPrice.getText() != null ? etPrice.getText().toString().trim() : "0";
                    String desc  = etDesc  != null && etDesc.getText()  != null ? etDesc.getText().toString().trim()  : "";
                    String icon  = etIcon  != null && etIcon.getText()  != null ? etIcon.getText().toString().trim()  : "";
                    String catName = acCat != null && acCat.getText()   != null ? acCat.getText().toString().trim()   : "";
                    boolean active = swActive != null && swActive.isChecked();

                    if (name.isEmpty()) { if (tilName != null) tilName.setError("Name required"); return; }

                    // Resolve category id
                    String catId = "";
                    for (AdminCategory c : categories) {
                        if (c.name != null && c.name.equals(catName)) { catId = c.id; break; }
                    }

                    double basePrice;
                    try { basePrice = Double.parseDouble(price); } catch (Exception e) { basePrice = 0; }

                    if (isEdit) updateService(existing.id, name, catId, desc, basePrice, icon, active);
                    else        insertService(name, catId, desc, basePrice, icon, active);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // INSERT SERVICE
    // ─────────────────────────────────────────────────────────────────────
    private void insertService(String name, String catId,
                               String desc, double price, String icon, boolean active) {
        try {
            JSONObject body = new JSONObject();
            body.put("id",          UUID.randomUUID().toString());
            body.put("name",        name);
            body.put("category_id", catId);
            body.put("description", desc);
            body.put("base_price",  price);
            body.put("icon_url",    icon);
            body.put("is_active",   active);

            String url = SUPABASE_URL + "/rest/v1/services";
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = supabaseBuilder(url).addHeader("Prefer", "return=minimal").post(rb).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code(); response.close();
                    main.post(() -> {
                        if (code == 201 || code == 200 || code == 204) {
                            toast("✅ Service added");
                            logAdminAction("service_added", name);
                            loadServices();
                        } else showError("Failed to add service (HTTP " + code + ")");
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE SERVICE
    // ─────────────────────────────────────────────────────────────────────
    private void updateService(String id, String name, String catId,
                               String desc, double price, String icon, boolean active) {
        try {
            JSONObject body = new JSONObject();
            body.put("name",        name);
            body.put("category_id", catId);
            body.put("description", desc);
            body.put("base_price",  price);
            body.put("icon_url",    icon);
            body.put("is_active",   active);

            String url = SUPABASE_URL + "/rest/v1/services?id=eq." + id;
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = supabaseBuilder(url).addHeader("Prefer", "return=minimal").patch(rb).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code(); response.close();
                    main.post(() -> {
                        if (code == 200 || code == 204) {
                            toast("✅ Service updated");
                            logAdminAction("service_updated", name);
                            loadServices();
                        } else showError("Failed to update service (HTTP " + code + ")");
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOGGLE STATUS
    // ─────────────────────────────────────────────────────────────────────
    private void toggleServiceStatus(AdminService svc, boolean newStatus) {
        try {
            JSONObject body = new JSONObject();
            body.put("is_active", newStatus);

            String url = SUPABASE_URL + "/rest/v1/services?id=eq." + svc.id;
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = supabaseBuilder(url).addHeader("Prefer", "return=minimal").patch(rb).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code(); response.close();
                    main.post(() -> {
                        if (code == 200 || code == 204) {
                            svc.isActive = newStatus;
                            adapter.notifyDataSetChanged();
                            toast(newStatus ? "✅ Service activated" : "⛔ Service disabled");
                        } else showError("Failed to update status (HTTP " + code + ")");
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE SERVICE
    // ─────────────────────────────────────────────────────────────────────
    private void confirmDeleteService(AdminService svc) {
        new AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("🗑 Delete Service")
                .setMessage("Delete \"" + svc.name + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteService(svc))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteService(AdminService svc) {
        String url = SUPABASE_URL + "/rest/v1/services?id=eq." + svc.id;
        Request req = supabaseBuilder(url).delete().build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> showError("Network error: " + e.getMessage()));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code(); response.close();
                main.post(() -> {
                    if (code == 200 || code == 204) {
                        toast("🗑 Service deleted");
                        logAdminAction("service_deleted", svc.name);
                        loadServices();
                    } else showError("Failed to delete service (HTTP " + code + ")");
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
        rvServices.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateCountLabel() {
        int n = filteredServices.size();
        tvServiceCount.setText(n + " service" + (n == 1 ? "" : "s"));
    }

    private void showError(String msg) {
        Toast.makeText(this, "❌ " + msg, Toast.LENGTH_LONG).show();
        Log.e(TAG, msg);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void logAdminAction(String action, String target) {
        SupabaseClient.logAdminAction(adminUid, action, "services", target, null);
    }
}
