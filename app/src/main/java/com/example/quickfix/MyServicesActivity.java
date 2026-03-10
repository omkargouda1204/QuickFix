package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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

import com.example.quickfix.adapters.ProviderServicesAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * MyServicesActivity — Provider's services management
 */
public class MyServicesActivity extends AppCompatActivity implements ProviderServicesAdapter.ServiceActionListener {

    private static final String TAG = "MyServicesActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private RecyclerView recyclerView;
    private ProviderServicesAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private FloatingActionButton fabAdd;

    private FirebaseAuth mAuth;
    private String providerId;
    private String providerName;
    private List<ProviderService> services = new ArrayList<>();

    private final String[] categories = {
            "Plumbing", "Electrical", "Carpentry", "Painting",
            "Cleaning", "AC Repair", "Appliance Repair", "Pest Control"
    };

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String SERVICES_SELECT_COLS =
            "id,provider_uid,title,description,category,price,is_active,created_at";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_services);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        providerId = user.getUid();
        providerName = user.getDisplayName() != null ? user.getDisplayName() : "Provider";

        setupToolbar();
        initViews();
        setupRecyclerView();
        loadServices();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Services");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        fabAdd = findViewById(R.id.fabAdd);

        swipeRefresh.setOnRefreshListener(this::loadServices);
        fabAdd.setOnClickListener(v -> showAddServiceDialog(null));
    }

    private void setupRecyclerView() {
        adapter = new ProviderServicesAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadServices() {
        showLoading(true);

        if (providerId == null || providerId.isEmpty()) {
            showLoading(false);
            Toast.makeText(this, "Provider ID not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Current schema uses `services` table and provider_uid.
        String url = SUPABASE_URL + "/rest/v1/services"
                + "?provider_uid=eq." + providerId
                + "&order=created_at.desc"
                + "&select=" + SERVICES_SELECT_COLS;

        Log.d(TAG, "loadServices URL: " + url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    showLoading(false);
                    Toast.makeText(MyServicesActivity.this,
                            "Failed to load services: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "[]";
                int code = response.code();
                response.close();

                Log.d(TAG, "loadServices response: HTTP " + code + " | Body: " + body);

                mainHandler.post(() -> {
                    showLoading(false);
                    if (code == 200) {
                        parseServices(body);
                    } else {
                        Toast.makeText(MyServicesActivity.this,
                                "Error: HTTP " + code + " | " + body,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void parseServices(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            services.clear();

            for (int i = 0; i < arr.length(); i++) {
                ProviderService service = ProviderService.fromJson(arr.getJSONObject(i));
                if (service != null) {
                    services.add(service);
                }
            }

            adapter.setServices(services);

            if (services.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            Toast.makeText(this, "Error parsing services", Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading(boolean show) {
        if (swipeRefresh.isRefreshing()) {
            swipeRefresh.setRefreshing(show);
        } else {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    // ServiceActionListener implementations
    @Override
    public void onEdit(ProviderService service) {
        showAddServiceDialog(service);
    }

    @Override
    public void onDelete(ProviderService service) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Service")
                .setMessage("Delete " + service.serviceName + "?")
                .setPositiveButton("Delete", (d, w) -> deleteService(service.id))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onToggleAvailability(ProviderService service, boolean isAvailable) {
        updateServiceAvailability(service.id, isAvailable);
    }

    private void showAddServiceDialog(ProviderService existingService) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_service, null);

        TextInputEditText etServiceName = dialogView.findViewById(R.id.etServiceName);
        AutoCompleteTextView acCategory = dialogView.findViewById(R.id.acCategory);
        TextInputEditText etPrice = dialogView.findViewById(R.id.etPrice);
        TextInputEditText etDescription = dialogView.findViewById(R.id.etDescription);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, categories);
        acCategory.setAdapter(categoryAdapter);

        if (existingService != null) {
            etServiceName.setText(existingService.serviceName);
            acCategory.setText(existingService.category, false);
            etPrice.setText(String.valueOf((int) existingService.price));
            etDescription.setText(existingService.description);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existingService == null ? "Add Service" : "Edit Service")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            MaterialButton btnSave = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnSave.setOnClickListener(v -> {
                String name = etServiceName.getText().toString().trim();
                String category = acCategory.getText().toString().trim();
                String priceStr = etPrice.getText().toString().trim();
                String description = etDescription.getText().toString().trim();

                if (name.isEmpty()) {
                    etServiceName.setError("Service name required");
                    return;
                }
                if (category.isEmpty()) {
                    acCategory.setError("Category required");
                    return;
                }
                if (priceStr.isEmpty()) {
                    etPrice.setError("Price required");
                    return;
                }

                double price;
                try {
                    price = Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    etPrice.setError("Invalid price");
                    return;
                }

                // Check for duplicates
                if (existingService == null) {
                    for (ProviderService s : services) {
                        if (s.serviceName.equalsIgnoreCase(name) && s.category.equalsIgnoreCase(category)) {
                            Toast.makeText(MyServicesActivity.this,
                                    "Service already exists!",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                }

                if (existingService == null) {
                    addService(name, category, price, description);
                } else {
                    updateService(existingService.id, name, category, price, description);
                }

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void addService(String name, String category, double price, String description) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider_uid", providerId);
            body.put("title", name);
            body.put("category", category);
            body.put("price", price);
            body.put("description", description);
            body.put("price_unit", "fixed");
            body.put("duration_mins", 60);
            body.put("is_active", true);

            String url = SUPABASE_URL + "/rest/v1/services";

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() ->
                            Toast.makeText(MyServicesActivity.this,
                                    "Failed to add service: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    mainHandler.post(() -> {
                        if (code == 201 || code == 200) {
                            Toast.makeText(MyServicesActivity.this,
                                    "Service added successfully",
                                    Toast.LENGTH_SHORT).show();
                            loadServices();
                        } else {
                            Toast.makeText(MyServicesActivity.this,
                                    "Failed: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Add service error: " + e.getMessage());
            Toast.makeText(this, "Error adding service", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateService(String serviceId, String name, String category, double price, String description) {
        try {
            JSONObject body = new JSONObject();
            body.put("title", name);
            body.put("category", category);
            body.put("price", price);
            body.put("description", description);

            String url = SUPABASE_URL + "/rest/v1/services?id=eq." + serviceId;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() ->
                            Toast.makeText(MyServicesActivity.this,
                                    "Failed to update: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    mainHandler.post(() -> {
                        if (code == 200 || code == 204) {
                            Toast.makeText(MyServicesActivity.this,
                                    "Service updated successfully",
                                    Toast.LENGTH_SHORT).show();
                            loadServices();
                        } else {
                            Toast.makeText(MyServicesActivity.this,
                                    "Failed: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Update service error: " + e.getMessage());
            Toast.makeText(this, "Error updating service", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteService(String serviceId) {
        String url = SUPABASE_URL + "/rest/v1/services?id=eq." + serviceId;

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() ->
                        Toast.makeText(MyServicesActivity.this,
                                "Delete failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                response.close();

                mainHandler.post(() -> {
                    if (code == 200 || code == 204) {
                        Toast.makeText(MyServicesActivity.this,
                                "Service deleted",
                                Toast.LENGTH_SHORT).show();
                        loadServices();
                    } else {
                        Toast.makeText(MyServicesActivity.this,
                                "Delete failed: HTTP " + code,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void updateServiceAvailability(String serviceId, boolean isAvailable) {
        try {
            JSONObject body = new JSONObject();
            body.put("is_active", isAvailable);

            String url = SUPABASE_URL + "/rest/v1/services?id=eq." + serviceId;

            Request request = new Request.Builder()
                    .url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() ->
                            Toast.makeText(MyServicesActivity.this,
                                    "Update failed",
                                    Toast.LENGTH_SHORT).show());
                    loadServices();
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    int code = response.code();
                    response.close();

                    if (code != 200 && code != 204) {
                        mainHandler.post(() -> {
                            Toast.makeText(MyServicesActivity.this,
                                    "Update failed: HTTP " + code,
                                    Toast.LENGTH_SHORT).show();
                            loadServices();
                        });
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Availability update error: " + e.getMessage());
        }
    }
}

