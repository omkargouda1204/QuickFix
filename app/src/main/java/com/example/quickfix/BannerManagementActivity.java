package com.example.quickfix;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.quickfix.adapters.BannerAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import java.util.Calendar;
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
 * BannerManagementActivity — Admin page to add/edit/delete home screen promotional banners.
 * Banners are stored in Supabase `banners` table. Images are uploaded to `banner-images` bucket.
 */
public class BannerManagementActivity extends AppCompatActivity {

    private static final String TAG = "BannerMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BUCKET_BANNERS = "banner-images";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView          rvBanners;
    private SwipeRefreshLayout    swipeRefresh;
    private LinearLayout          shimmerLayout, emptyStateLayout;
    private TextView              tvTotalBanners, tvActiveBanners, tvExpiredBanners;
    private ExtendedFloatingActionButton fabAdd;

    // Adapter
    private BannerAdapter         adapter;
    private final List<Banner>    banners = new ArrayList<>();

    // Image picker state for dialog
    private Uri               selectedImageUri = null;
    private ImageView         dialogImagePreview;
    private View              dialogUploadPlaceholder;
    private TextView          dialogUploadProgress;
    private String            uploadedImageUrl = null;

    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_banner_management);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        registerImagePicker();
        bindViews();
        setupRecyclerView();
        setupSwipeRefresh();
        setupFab();
        loadBanners();
    }

    private void registerImagePicker() {
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        if (dialogImagePreview != null) {
                            dialogImagePreview.setVisibility(View.VISIBLE);
                            dialogUploadPlaceholder.setVisibility(View.GONE);
                            Glide.with(this).load(uri).centerCrop().into(dialogImagePreview);
                        }
                    }
                });
    }

    private void bindViews() {
        rvBanners        = findViewById(R.id.rvBanners);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        shimmerLayout    = findViewById(R.id.shimmerLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvTotalBanners   = findViewById(R.id.tvTotalBanners);
        tvActiveBanners  = findViewById(R.id.tvActiveBanners);
        tvExpiredBanners = findViewById(R.id.tvExpiredBanners);
        fabAdd           = findViewById(R.id.fabAddBanner);

        ImageButton btnBack    = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadBanners());
    }

    private void setupRecyclerView() {
        adapter = new BannerAdapter(this, banners, new BannerAdapter.Listener() {
            @Override
            public void onEdit(Banner banner) { showAddEditDialog(banner); }

            @Override
            public void onDelete(Banner banner) { confirmDelete(banner); }

            @Override
            public void onToggleActive(Banner banner, boolean active) {
                toggleBannerActive(banner, active);
            }
        });
        rvBanners.setLayoutManager(new LinearLayoutManager(this));
        rvBanners.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadBanners);
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showAddEditDialog(null));
    }

    // ── Load banners from Supabase ─────────────────────────────────────────
    private void loadBanners() {
        setLoading(true);
        // Fetch ALL rows with no filters — admin needs to see everything
        String url = SUPABASE_URL + "/rest/v1/banners?select=*";

        Log.d(TAG, "loadBanners URL: " + url);

        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey",         ANON_KEY)
                .addHeader("Authorization",  "Bearer " + ANON_KEY)
                .addHeader("Accept",         "application/json")
                .addHeader("Content-Type",   "application/json")
                .get()
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                Log.e(TAG, "loadBanners onFailure: " + e.getMessage());
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }

            @Override public void onResponse(Call call, Response resp) throws IOException {
                int    code = resp.code();
                String body = resp.body() != null ? resp.body().string() : "[]";
                Log.d(TAG, "loadBanners HTTP " + code);
                Log.d(TAG, "loadBanners BODY: " + body);
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    if (code == 200) {
                        parseBanners(body);
                    } else {
                        Log.e(TAG, "loadBanners FAILED HTTP " + code + ": " + body);
                        showError("Load failed: HTTP " + code + "\n" + body);
                    }
                });
            }
        });
    }

    private void parseBanners(String json) {
        banners.clear();
        try {
            JSONArray arr = new JSONArray(json);
            Log.d(TAG, "parseBanners: received " + arr.length() + " rows");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Banner b = new Banner();
                b.id           = o.optString("id");
                b.title        = o.optString("title");
                b.imageUrl     = o.optString("image_url");
                b.redirectType = o.optString("redirect_type");
                b.redirectId   = o.optString("redirect_id");
                b.startDate    = o.optString("start_date");
                b.expiryDate   = o.optString("expiry_date");
                b.priority     = o.optInt("priority", 1);
                b.createdAt    = o.optString("created_at");
                // DB schema: is_active boolean — no status column
                b.isActive = o.optBoolean("is_active", true);
                b.status   = b.isActive ? "active" : "inactive";
                banners.add(b);
                Log.d(TAG, "  banner[" + i + "]: id=" + b.id + " title=" + b.title
                        + " isActive=" + b.isActive);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseBanners exception: " + e.getMessage(), e);
        }
        Log.d(TAG, "parseBanners: parsed " + banners.size() + " banners");
        adapter.setItems(banners);
        updateStats();
        if (banners.isEmpty()) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            rvBanners.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            rvBanners.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
        if (rvBanners        != null) rvBanners.setVisibility(View.GONE);
    }

    private void updateStats() {
        int total   = banners.size();
        int active  = 0;
        int expired = 0;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        for (Banner b : banners) {
            if (b.isActive) active++;
            if (b.expiryDate != null && !b.expiryDate.isEmpty()
                    && b.expiryDate.compareTo(today) < 0) expired++;
        }
        if (tvTotalBanners  != null) tvTotalBanners.setText(String.valueOf(total));
        if (tvActiveBanners != null) tvActiveBanners.setText(String.valueOf(active));
        if (tvExpiredBanners!= null) tvExpiredBanners.setText(String.valueOf(expired));
        Log.d(TAG, "updateStats: total=" + total + " active=" + active + " expired=" + expired);
    }

    // ── Add / Edit Dialog ─────────────────────────────────────────────────
    private void showAddEditDialog(Banner existing) {
        boolean isEdit = (existing != null);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_banner, null);

        // Bind dialog views
        TextView          tvTitle          = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputEditText etBannerTitle    = dialogView.findViewById(R.id.etBannerTitle);
        AutoCompleteTextView actvRedirect  = dialogView.findViewById(R.id.actvRedirectType);
        TextInputEditText etRedirectId     = dialogView.findViewById(R.id.etRedirectId);
        TextInputEditText etPriority       = dialogView.findViewById(R.id.etPriority);
        TextInputEditText etExpiry         = dialogView.findViewById(R.id.etExpiryDate);
        SwitchMaterial    switchActive     = dialogView.findViewById(R.id.switchActive);
        View              cardImage        = dialogView.findViewById(R.id.cardImageUpload);

        dialogImagePreview      = dialogView.findViewById(R.id.ivBannerImagePreview);
        dialogUploadPlaceholder = dialogView.findViewById(R.id.layoutUploadPlaceholder);
        dialogUploadProgress    = dialogView.findViewById(R.id.tvUploadProgress);

        // Redirect type dropdown
        String[] types = {"service", "category", "external"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, types);
        actvRedirect.setAdapter(typeAdapter);

        // Pre-fill if editing
        tvTitle.setText(isEdit ? "Edit Banner" : "Add Banner");
        uploadedImageUrl = null;
        selectedImageUri = null;

        if (isEdit) {
            etBannerTitle.setText(existing.title);
            actvRedirect.setText(existing.redirectType, false);
            etRedirectId.setText(existing.redirectId);
            etPriority.setText(String.valueOf(existing.priority));
            etExpiry.setText(existing.expiryDate);
            switchActive.setChecked(existing.isActive);
            uploadedImageUrl = existing.imageUrl;
            if (existing.imageUrl != null && !existing.imageUrl.isEmpty()) {
                dialogImagePreview.setVisibility(View.VISIBLE);
                dialogUploadPlaceholder.setVisibility(View.GONE);
                Glide.with(this).load(existing.imageUrl).centerCrop().into(dialogImagePreview);
            }
        }

        // Image picker
        cardImage.setOnClickListener(v -> imagePicker.launch("image/*"));

        // Date picker
        etExpiry.setOnClickListener(v -> showDatePicker(etExpiry));
        etExpiry.setOnFocusChangeListener((v, f) -> { if (f) showDatePicker(etExpiry); });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title    = getText(etBannerTitle);
            String redirect = actvRedirect.getText().toString().trim();
            String redirId  = getText(etRedirectId);
            String expiry   = getText(etExpiry);
            boolean active  = switchActive.isChecked();
            int priority    = 1;
            try { priority = Integer.parseInt(getText(etPriority)); } catch (Exception ignored) {}

            if (title.isEmpty()) {
                setError(dialogView.findViewById(R.id.tilBannerTitle), "Title is required");
                return;
            }

            if (selectedImageUri != null && uploadedImageUrl == null) {
                // Upload image first, then save
                uploadBannerImage(selectedImageUri, title, redirect, redirId, expiry, active,
                        priority, isEdit ? existing.id : null, dialog);
            } else {
                saveBanner(title, uploadedImageUrl != null ? uploadedImageUrl :
                                (isEdit ? existing.imageUrl : ""),
                        redirect, redirId, expiry, active, priority,
                        isEdit ? existing.id : null, dialog);
            }
        });

        dialog.show();
    }

    private void uploadBannerImage(Uri uri, String title, String redirect, String redirId,
                                   String expiry, boolean active, int priority,
                                   String bannerId, AlertDialog dialog) {
        if (dialogUploadProgress != null) dialogUploadProgress.setVisibility(View.VISIBLE);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String prefix = user != null ? user.getUid() : "admin";

        SupabaseStorageHelper.uploadFile(this, uri, BUCKET_BANNERS, prefix,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override public void onSuccess(String publicUrl) {
                        uploadedImageUrl = publicUrl;
                        if (dialogUploadProgress != null)
                            dialogUploadProgress.setVisibility(View.GONE);
                        saveBanner(title, publicUrl, redirect, redirId, expiry, active,
                                priority, bannerId, dialog);
                    }
                    @Override public void onError(String message) {
                        if (dialogUploadProgress != null)
                            dialogUploadProgress.setVisibility(View.GONE);
                        Toast.makeText(BannerManagementActivity.this,
                                "Image upload failed: " + message, Toast.LENGTH_SHORT).show();
                        // Save without image
                        saveBanner(title, "", redirect, redirId, expiry, active,
                                priority, bannerId, dialog);
                    }
                });
    }

    private void saveBanner(String title, String imageUrl, String redirectType,
                            String redirectId, String expiryDate, boolean isActive,
                            int priority, String existingId, AlertDialog dialog) {
        try {
            JSONObject body = new JSONObject();
            body.put("title",         title);
            body.put("image_url",     imageUrl != null ? imageUrl : "");
            body.put("redirect_type", redirectType);
            body.put("redirect_id",   redirectId);
            body.put("expiry_date",   expiryDate.isEmpty() ? JSONObject.NULL : expiryDate);
            body.put("is_active",     isActive);
            // NO 'status' column — DB schema only has is_active (boolean)
            body.put("priority",      priority);

            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request.Builder rb2 = new Request.Builder()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal");

            if (existingId != null) {
                // PATCH
                rb2.url(SUPABASE_URL + "/rest/v1/banners?id=eq." + existingId)
                   .patch(rb);
            } else {
                // POST
                body.put("id", UUID.randomUUID().toString());
                rb = RequestBody.create(body.toString(), JSON);
                rb2.url(SUPABASE_URL + "/rest/v1/banners").post(rb);
            }

            http.newCall(rb2.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    main.post(() -> Toast.makeText(BannerManagementActivity.this,
                            "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(Call call, Response resp) throws IOException {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(BannerManagementActivity.this,
                                    existingId != null ? "Banner updated" : "Banner added",
                                    Toast.LENGTH_SHORT).show();
                            loadBanners();
                        } else {
                            try {
                                String err = resp.body() != null ? resp.body().string() : "";
                                Toast.makeText(BannerManagementActivity.this,
                                        "Error " + resp.code() + ": " + err, Toast.LENGTH_LONG).show();
                            } catch (IOException ignored) {}
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "saveBanner: " + e.getMessage());
        }
    }

    private void toggleBannerActive(Banner banner, boolean newState) {
        try {
            JSONObject body = new JSONObject();
            body.put("is_active", newState);
            // NO 'status' column in DB — only is_active (boolean)
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/banners?id=eq." + banner.id)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(rb)
                    .build();
            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    main.post(() -> Toast.makeText(BannerManagementActivity.this,
                            "Update failed", Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(Call call, Response resp) throws IOException {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            banner.isActive = newState;
                            banner.status   = newState ? "active" : "inactive";
                            adapter.notifyDataSetChanged();
                            updateStats();
                            Toast.makeText(BannerManagementActivity.this,
                                    newState ? "Banner enabled" : "Banner disabled",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "toggleBannerActive: " + e.getMessage());
        }
    }

    private void confirmDelete(Banner banner) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Banner")
                .setMessage("Delete \"" + banner.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteBanner(banner))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBanner(Banner banner) {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/banners?id=eq." + banner.id)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Prefer", "return=minimal")
                .delete()
                .build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                main.post(() -> Toast.makeText(BannerManagementActivity.this,
                        "Delete failed", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response resp) throws IOException {
                main.post(() -> {
                    if (resp.isSuccessful()) {
                        banners.remove(banner);
                        adapter.notifyDataSetChanged();
                        updateStats();
                        if (banners.isEmpty()) {
                            emptyStateLayout.setVisibility(View.VISIBLE);
                            rvBanners.setVisibility(View.GONE);
                        }
                        Toast.makeText(BannerManagementActivity.this,
                                "Banner deleted", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void showDatePicker(TextInputEditText field) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            String date = String.format(java.util.Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
            field.setText(date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setLoading(boolean loading) {
        if (shimmerLayout != null)
            shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvBanners != null)
            rvBanners.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (emptyStateLayout != null && loading)
            emptyStateLayout.setVisibility(View.GONE);
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }
}

