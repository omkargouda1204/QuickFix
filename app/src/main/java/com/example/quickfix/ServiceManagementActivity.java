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
import android.widget.Button;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
 * ServiceManagementActivity — Admin CRUD for the `services` table.
 *
 * Actual DB columns (Supabase):
 *   id, provider_uid, title, description, category, price,
 *   price_unit, duration_mins, is_active, created_at
 *
 * Columns that do NOT exist (never query them):
 *   name, icon, icon_url, category_id, base_price
 */
public class ServiceManagementActivity extends BaseActivity {

    private static final String TAG          = "ServiceMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON      = MediaType.get("application/json; charset=utf-8");

    // ── Exact columns that exist in the services table ────────────────────
    private static final String SELECT_COLS = "*";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                String token = com.example.quickfix.AuthTokenProvider.getIdTokenBlocking(5);
                String accessToken = (token != null && !token.isEmpty()) ? token : BuildConfig.SUPABASE_ANON_KEY;
                Log.d("TOKEN", accessToken);
                okhttp3.Request request = original.newBuilder()
                        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json")
                        .build();
                return chain.proceed(request);
            })
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
        loadServices();
    }

    // ── Bind views ────────────────────────────────────────────────────────
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
                if (dy > 8) fabAdd.shrink(); else if (dy < -8) fabAdd.extend();
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

    // ── LOAD SERVICES ─────────────────────────────────────────────────────
    private void loadServices() {
        showShimmer(true);
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);

        // Only select columns that actually exist in the services table
        String url = SUPABASE_URL + "/rest/v1/services"
                + "?select=" + SELECT_COLS
                + "&order=title.asc";
        Log.d(TAG, "API_URL=" + url);

        Request req = supabaseBuilder(url).get().build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                    ensureDefaultServicesExist();
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int    code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                Log.d(TAG, "loadServices HTTP " + code + " | " + body.substring(0, Math.min(300, body.length())));
                main.post(() -> {
                    showShimmer(false);
                    swipeRefresh.setRefreshing(false);
                    if (code == 200) {
                        parseServices(body);
                        if (allServices.isEmpty()) ensureDefaultServicesExist();
                    } else {
                        showError("Failed to load services (HTTP " + code + "): " + body);
                        ensureDefaultServicesExist();
                    }
                });
            }
        });
    }

    private void parseServices(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            allServices.clear();
            categoryNames.clear();
            categoryNames.add("All");

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o   = arr.getJSONObject(i);
                AdminService s = new AdminService();
                s.id           = o.optString("id",           "");
                s.providerUid  = o.optString("provider_uid", "");
                s.title        = o.optString("title",        "");
                s.category     = o.optString("category",     "");
                s.description  = o.optString("description",  "");
                s.price        = o.optDouble("price",        0);
                s.priceUnit    = o.optString("price_unit",   "");
                s.durationMins = o.optInt("duration_mins",   0);
                s.isActive     = o.optBoolean("is_active",   true);
                s.createdAt    = o.optString("created_at",   "");
                allServices.add(s);

                // Build dynamic category filter list from data
                if (s.category != null && !s.category.isEmpty()
                        && !categoryNames.contains(s.category)) {
                    categoryNames.add(s.category);
                }
            }
            setupCategoryFilterDropdown();
            applyFilters();
        } catch (Exception e) {
            Log.e(TAG, "parseServices: " + e.getMessage());
            showError("Parse error: " + e.getMessage());
        }
    }

    private void setupCategoryFilterDropdown() {
        if (acCategoryFilter == null) return;
        ArrayAdapter<String> filterAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categoryNames);
        acCategoryFilter.setAdapter(filterAdapter);
        acCategoryFilter.setText("All", false);
        acCategoryFilter.setOnItemClickListener((parent, v, pos, id) -> {
            selectedFilterCategory = categoryNames.get(pos);
            applyFilters();
        });
    }

    // ── FILTER ────────────────────────────────────────────────────────────
    private void applyFilters() {
        String query = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";
        String catFilter = selectedFilterCategory != null ? selectedFilterCategory : "All";

        filteredServices.clear();
        for (AdminService svc : allServices) {
            boolean matchSearch = query.isEmpty()
                    || (svc.title       != null && svc.title.toLowerCase().contains(query))
                    || (svc.description != null && svc.description.toLowerCase().contains(query))
                    || (svc.category    != null && svc.category.toLowerCase().contains(query));
            boolean matchCat = catFilter.equals("All")
                    || (svc.category != null && svc.category.equals(catFilter));
            if (matchSearch && matchCat) filteredServices.add(svc);
        }
        adapter.notifyDataSetChanged();
        updateCountLabel();
        if (emptyStateLayout != null)
            emptyStateLayout.setVisibility(filteredServices.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── ADD / EDIT DIALOG ─────────────────────────────────────────────────
    private void showServiceDialog(AdminService existing) {
        boolean isEdit = existing != null;
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_edit_service, null);

        TextInputLayout   tilTitle    = dialogView.findViewById(R.id.tilServiceName);
        TextInputEditText etTitle     = dialogView.findViewById(R.id.etServiceName);
        TextInputEditText etPrice     = dialogView.findViewById(R.id.etPrice);
        TextInputEditText etDesc      = dialogView.findViewById(R.id.etServiceDescription);
        TextInputEditText etDuration  = dialogView.findViewById(R.id.etIconUrl); // reuse field for duration
        AutoCompleteTextView acCat    = dialogView.findViewById(R.id.acCategory);
        SwitchMaterial     swActive   = dialogView.findViewById(R.id.switchActive);

        // Relabel the "icon" field to "Duration (mins)" since icon_url doesn't exist
        TextInputLayout tilIcon = dialogView.findViewById(R.id.tilIconUrl);
        if (tilIcon != null) tilIcon.setHint("Duration (mins)");

        Button btnSaveService = dialogView.findViewById(R.id.btnSaveService);
        if (btnSaveService != null) {
            btnSaveService.setText(isEdit ? "Update Service" : "Save Service");
        }

        // Populate category dropdown from existing data
        List<String> catOptions = new ArrayList<>(categoryNames);
        if (!catOptions.contains("All")) catOptions.remove("All");
        // Add standard categories if list is empty
        if (catOptions.isEmpty() || (catOptions.size() == 1 && catOptions.get(0).equals("All"))) {
            catOptions.clear();
            catOptions.add("Plumbing"); catOptions.add("Electrical"); catOptions.add("Cleaning");
            catOptions.add("Repair"); catOptions.add("AC Service"); catOptions.add("Painting");
            catOptions.add("Carpentry");
        } else {
            catOptions.remove("All");
        }

        if (acCat != null) acCat.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, catOptions));

        if (isEdit) {
            if (etTitle    != null) etTitle.setText(existing.title);
            if (etPrice    != null) etPrice.setText(String.valueOf((int) existing.price));
            if (etDesc     != null) etDesc.setText(existing.description);
            if (etDuration != null) etDuration.setText(existing.durationMins > 0
                    ? String.valueOf(existing.durationMins) : "");
            if (acCat      != null) acCat.setText(existing.category, false);
            if (swActive   != null) swActive.setChecked(existing.isActive);
        } else {
            if (swActive != null) swActive.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(isEdit ? "✏️ Edit Service" : "➕ Add Service")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();

        if (btnSaveService != null) {
            btnSaveService.setOnClickListener(v -> {
                String title    = t(etTitle);
                String priceStr = t(etPrice);
                String desc     = t(etDesc);
                String durStr   = t(etDuration);
                String cat      = t(acCat);
                boolean active  = swActive != null && swActive.isChecked();

                if (title.isEmpty()) {
                    if (tilTitle != null) tilTitle.setError("Required");
                    return;
                }

                double price;
                try { price = Double.parseDouble(priceStr); } catch (Exception e) { price = 0; }
                int dur;
                try { dur = Integer.parseInt(durStr); } catch (Exception e) { dur = 0; }

                if (isEdit) updateService(existing.id, title, cat, desc, price, dur, active);
                else        insertService(title, cat, desc, price, dur, active);
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    // ── INSERT SERVICE ────────────────────────────────────────────────────
    private void insertService(String title, String category,
                               String desc, double price, int durationMins, boolean active) {
        try {
            JSONObject body = new JSONObject();
            body.put("id",            UUID.randomUUID().toString());
            body.put("provider_uid",  adminUid);
            body.put("title",         title);
            body.put("category",      category);
            body.put("description",   desc);
            body.put("price",         price);
            body.put("price_unit",    "per visit");
            body.put("duration_mins", durationMins);
            body.put("is_active",     active);
            Log.d(TAG, "API_URL=" + SUPABASE_URL + "/rest/v1/services");
            Log.d(TAG, "RESPONSE_BODY=" + body.toString());

            Request req = supabaseBuilder(SUPABASE_URL + "/rest/v1/services")
                    .addHeader("Prefer", "return=minimal")
                    .post(RequestBody.create(body.toString(), JSON)).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code();
                    String rb = response.body() != null ? response.body().string() : "";
                    response.close();
                    Log.d(TAG, "insertService HTTP " + code + " | " + rb);
                    main.post(() -> {
                        if (code == 201 || code == 200 || code == 204) {
                            toast("✅ Service added");
                            SupabaseClient.logAdminAction(adminUid, "service_added", "services", title, null);
                            loadServices();
                        } else showError("Failed to add service (HTTP " + code + "): " + rb);
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ── UPDATE SERVICE ────────────────────────────────────────────────────
    private void updateService(String id, String title, String category,
                               String desc, double price, int durationMins, boolean active) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider_uid",  adminUid);
            body.put("title",         title);
            body.put("category",      category);
            body.put("description",   desc);
            body.put("price",         price);
            body.put("price_unit",    "per visit");
            body.put("duration_mins", durationMins);
            body.put("is_active",     active);
            Log.d(TAG, "API_URL=" + SUPABASE_URL + "/rest/v1/services?id=eq." + id);
            Log.d(TAG, "RESPONSE_BODY=" + body.toString());

            Request req = supabaseBuilder(SUPABASE_URL + "/rest/v1/services?id=eq." + id)
                    .addHeader("Prefer", "return=minimal")
                    .patch(RequestBody.create(body.toString(), JSON)).build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    main.post(() -> showError("Network error: " + e.getMessage()));
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    int code = response.code();
                    String rb = response.body() != null ? response.body().string() : "";
                    response.close();
                    Log.d(TAG, "updateService HTTP " + code + " | " + rb);
                    main.post(() -> {
                        if (code == 200 || code == 204) {
                            toast("✅ Service updated");
                            SupabaseClient.logAdminAction(adminUid, "service_updated", "services", title, null);
                            loadServices();
                        } else showError("Failed to update service (HTTP " + code + "): " + rb);
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ── TOGGLE STATUS ─────────────────────────────────────────────────────
    private void toggleServiceStatus(AdminService svc, boolean newStatus) {
        try {
            JSONObject body = new JSONObject();
            body.put("is_active", newStatus);  // ✅ this column exists
            Log.d(TAG, "API_URL=" + SUPABASE_URL + "/rest/v1/services?id=eq." + svc.id);
            Log.d(TAG, "RESPONSE_BODY=" + body.toString());

            Request req = supabaseBuilder(SUPABASE_URL + "/rest/v1/services?id=eq." + svc.id)
                    .addHeader("Prefer", "return=minimal")
                    .patch(RequestBody.create(body.toString(), JSON)).build();

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
                            toast(newStatus ? "✅ Activated" : "⛔ Disabled");
                        } else showError("Failed to update status (HTTP " + code + ")");
                    });
                }
            });
        } catch (Exception e) { showError(e.getMessage()); }
    }

    // ── DELETE SERVICE ────────────────────────────────────────────────────
    private void confirmDeleteService(AdminService svc) {
        new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("🗑 Delete Service")
                .setMessage("Delete \"" + svc.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteService(svc))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteService(AdminService svc) {
        Request req = supabaseBuilder(SUPABASE_URL + "/rest/v1/services?id=eq." + svc.id)
                .delete().build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                main.post(() -> showError("Network error: " + e.getMessage()));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code(); response.close();
                main.post(() -> {
                    if (code == 200 || code == 204) {
                        toast("🗑 Deleted");
                        SupabaseClient.logAdminAction(adminUid, "service_deleted", "services", svc.title, null);
                        loadServices();
                    } else showError("Failed to delete (HTTP " + code + ")");
                });
            }
        });
    }

    // ── AUTO-SEED DEFAULT SERVICES ────────────────────────────────────────
    private static final String[][] DEFAULT_SERVICES = {
        {"Plumbing",    "Plumbing",    "Pipes, taps, leaks, drainage repairs",           "499",  "60"},
        {"Electrical",  "Electrical",  "Wiring, switches, circuit breakers",             "399",  "45"},
        {"Cleaning",    "Cleaning",    "Deep cleaning, housekeeping, sanitization",      "299",  "120"},
        {"Repair",      "Repair",      "Appliance and furniture repairs",                "349",  "60"},
        {"AC Service",  "AC Service",  "AC installation, servicing, gas refilling",     "699",  "90"},
        {"Painting",    "Painting",    "Interior and exterior painting services",        "599",  "180"},
        {"Carpentry",   "Carpentry",   "Furniture, doors, windows, woodwork",           "449",  "90"},
    };

    private void ensureDefaultServicesExist() {
        new Thread(() -> {
            try {
                // Check if table already has data
                String checkUrl = SUPABASE_URL + "/rest/v1/services?select=id&limit=1";
                try (Response cr = http.newCall(supabaseBuilder(checkUrl).get().build()).execute()) {
                    int code = cr.code();
                    String body = cr.body() != null ? cr.body().string() : "[]";
                    if (code == 200 && new JSONArray(body).length() > 0) return; // already seeded
                    if (code == 400) {
                        Log.w(TAG, "ensureDefault: table error HTTP 400 – " + body); return;
                    }
                }
                // Seed
                for (String[] row : DEFAULT_SERVICES) {
                    try {
                        JSONObject b = new JSONObject();
                        b.put("id",            UUID.randomUUID().toString());
                        b.put("provider_uid",  adminUid);
                        b.put("title",         row[0]);
                        b.put("category",      row[1]);
                        b.put("description",   row[2]);
                        b.put("price",         Double.parseDouble(row[3]));
                        b.put("price_unit",    "per visit");
                        b.put("duration_mins", Integer.parseInt(row[4]));
                        b.put("is_active",     true);
                        Log.d(TAG, "API_URL=" + SUPABASE_URL + "/rest/v1/services");
                        Log.d(TAG, "RESPONSE_BODY=" + b.toString());
                        Request req = supabaseBuilder(SUPABASE_URL + "/rest/v1/services")
                                .addHeader("Prefer", "resolution=ignore-duplicates,return=minimal")
                                .post(RequestBody.create(b.toString(), JSON)).build();
                        try (Response r = http.newCall(req).execute()) {
                            Log.d(TAG, "Seeded '" + row[0] + "' HTTP " + r.code());
                        }
                    } catch (Exception ex) {
                        Log.e(TAG, "Seed error " + row[0] + ": " + ex.getMessage());
                    }
                }
                main.post(this::loadServices);
            } catch (Exception e) {
                Log.e(TAG, "ensureDefaultServicesExist: " + e.getMessage());
            }
        }).start();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────
    private Request.Builder supabaseBuilder(String url) {
        return new Request.Builder().url(url)
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Accept",        "application/json");
    }

    private void showShimmer(boolean show) {
        if (shimmerLayout != null) shimmerLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        if (rvServices    != null) rvServices.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateCountLabel() {
        if (tvServiceCount == null) return;
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

    /** Safe text extractor for any TextView */
    private String t(android.widget.TextView tv) {
        return tv != null && tv.getText() != null ? tv.getText().toString().trim() : "";
    }
}
