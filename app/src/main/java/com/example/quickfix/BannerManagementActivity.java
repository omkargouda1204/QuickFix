package com.example.quickfix;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * BannerManagementActivity — Admin page to add/edit/delete home screen promotional banners.
 * Banners are stored in Supabase `banners` table. Images are uploaded to `banner-images` bucket.
 */
public class BannerManagementActivity extends BaseActivity {

    private static final String TAG            = "BannerMgmt";
    private static final String SUPABASE_URL   = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY       = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BUCKET_BANNERS = "banner-images";
    private static final MediaType JSON_MT     = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
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
    private ImageView         dialogImagePreview = null;
    private View              dialogUploadPlaceholder = null;
    private TextView          dialogUploadProgress    = null;
    private String            uploadedImageUrl        = null;

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
                            if (dialogUploadPlaceholder != null)
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
            @Override public void onEdit(Banner b)   { showAddEditDialog(b); }
            @Override public void onDelete(Banner b) { confirmDelete(b); }
            @Override public void onToggleActive(Banner b, boolean active) { toggleBannerActive(b, active); }
        });
        rvBanners.setLayoutManager(new LinearLayoutManager(this));
        rvBanners.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadBanners);
    }

    private void setupFab() {
        if (fabAdd != null) fabAdd.setOnClickListener(v -> showAddEditDialog(null));
    }

    private static class CountStats {
        int total;
        int active;
        int expired;
    }

    // ── LOAD BANNERS ── uses Thread (same reliable pattern as MainActivity) ──
    private void loadBanners() {
        setLoading(true);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        String primaryUrl = SUPABASE_URL + "/rest/v1/banners?select=*"
                + "&is_active=eq.true"
                + "&or=(expiry_date.is.null,expiry_date.gte.now())"
                + "&order=priority.asc,created_at.desc";
        String fallbackUrl = SUPABASE_URL + "/rest/v1/banners?select=*"
                + "&is_active=eq.true"
                + "&or=(expiry_date.is.null,expiry_date.gte." + today + ")"
                + "&order=priority.asc,created_at.desc";
        Log.d(TAG, "loadBanners -> " + primaryUrl);

        new Thread(() -> {
            try {
                String[] urls = new String[]{primaryUrl, fallbackUrl};
                int code = 0;
                String body = "[]";
                boolean success = false;

                for (String url : urls) {
                    Request req = new Request.Builder()
                            .url(url)
                            .addHeader("apikey", ANON_KEY)
                            .addHeader("Authorization", "Bearer " + ANON_KEY)
                            .addHeader("Accept", "application/json")
                            .build();
                    try (Response resp = http.newCall(req).execute()) {
                        code = resp.code();
                        body = resp.body() != null ? resp.body().string() : "[]";
                        if (resp.isSuccessful()) {
                            success = true;
                            break;
                        }
                    }
                }

                final int finalCode = code;
                final String finalBody = body;
                if (success) {
                    CountStats stats = fetchBannerCounts(today);
                    main.post(() -> {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        parseBanners(finalBody);
                        applyBannerStats(stats);
                    });
                } else {
                    Log.e("ERROR", finalBody);
                    Log.e("RESPONSE", finalBody);
                    main.post(() -> {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        showError("Failed to load banners (HTTP " + finalCode + ")");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "loadBanners exception", e);
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }
        }).start();
    }

    private CountStats fetchBannerCounts(String today) {
        CountStats stats = new CountStats();
        String base = SUPABASE_URL + "/rest/v1/banners?select=id";
        stats.total = fetchCount(base);
        stats.active = fetchCount(base
                + "&is_active=eq.true"
                + "&or=(expiry_date.is.null,expiry_date.gte." + today + ")");
        stats.expired = fetchCount(base + "&expiry_date=lt." + today);
        return stats;
    }

    private int fetchCount(String url) {
        try {
            Request req = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .addHeader("Prefer", "count=exact")
                    .addHeader("Range", "0-0")
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                String body = resp.body() != null ? resp.body().string() : "[]";
                if (!resp.isSuccessful()) {
                    Log.e("ERROR", body);
                    return 0;
                }
                String contentRange = resp.header("Content-Range", "");
                if (contentRange != null && contentRange.contains("/")) {
                    String totalPart = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
                    if (!"*".equals(totalPart)) {
                        return Integer.parseInt(totalPart);
                    }
                }
                return new JSONArray(body).length();
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchCount exception", e);
            return 0;
        }
    }

    private void applyBannerStats(CountStats stats) {
        if (tvTotalBanners != null) tvTotalBanners.setText(String.valueOf(stats.total));
        if (tvActiveBanners != null) tvActiveBanners.setText(String.valueOf(stats.active));
        if (tvExpiredBanners != null) tvExpiredBanners.setText(String.valueOf(stats.expired));
    }

    private void parseBanners(String json) {
        banners.clear();
        try {
            JSONArray arr = new JSONArray(json);
            Log.d(TAG, "parseBanners: " + arr.length() + " rows received");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Banner b       = new Banner();
                b.id           = o.optString("id");
                b.title        = o.optString("title");
                b.imageUrl     = SupabaseStorageHelper.getPublicObjectUrl(
                        BUCKET_BANNERS,
                        o.optString("image_url", ""));
                b.redirectType = o.optString("redirect_type", "category");
                b.redirectId   = o.optString("redirect_id", "");
                b.startDate    = o.optString("start_date", "");
                b.expiryDate   = o.optString("expiry_date", "");
                b.isActive     = o.optBoolean("is_active", true);
                b.status       = b.isActive ? "active" : "inactive";
                b.priority     = o.optInt("priority", 1);
                b.createdAt    = o.optString("created_at");
                banners.add(b);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseBanners exception", e);
        }
        Log.d(TAG, "parseBanners: parsed " + banners.size() + " banners");
        adapter.setItems(banners);
        adapter.notifyDataSetChanged();
        showListOrEmpty();
    }

    private void showListOrEmpty() {
        boolean empty = banners.isEmpty();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvBanners        != null) rvBanners.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
        if (rvBanners        != null) rvBanners.setVisibility(View.GONE);
    }

    // ── ADD / EDIT DIALOG ─────────────────────────────────────────────────
    private void showAddEditDialog(Banner existing) {
        boolean isEdit = (existing != null);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_banner, null);

        TextView             tvDlgTitle  = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputEditText    etTitle     = dialogView.findViewById(R.id.etBannerTitle);
        AutoCompleteTextView actvRedir   = dialogView.findViewById(R.id.actvRedirectType);
        TextInputEditText    etRedirId   = dialogView.findViewById(R.id.etRedirectId);
        TextInputEditText    etPriority  = dialogView.findViewById(R.id.etPriority);
        TextInputEditText    etExpiry    = dialogView.findViewById(R.id.etExpiryDate);
        SwitchMaterial       swActive    = dialogView.findViewById(R.id.switchActive);
        View                 cardImage   = dialogView.findViewById(R.id.cardImageUpload);

        dialogImagePreview      = dialogView.findViewById(R.id.ivBannerImagePreview);
        dialogUploadPlaceholder = dialogView.findViewById(R.id.layoutUploadPlaceholder);
        dialogUploadProgress    = dialogView.findViewById(R.id.tvUploadProgress);

        String[] types = {"service", "category", "external"};
        actvRedir.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, types));

        tvDlgTitle.setText(isEdit ? "Edit Banner" : "Add Banner");
        uploadedImageUrl = null;
        selectedImageUri = null;

        if (isEdit) {
            etTitle.setText(existing.title);
            actvRedir.setText(existing.redirectType, false);
            etRedirId.setText(existing.redirectId);
            etPriority.setText(String.valueOf(existing.priority));
            etExpiry.setText(existing.expiryDate);
            swActive.setChecked(existing.isActive);
            uploadedImageUrl = SupabaseStorageHelper.getPublicObjectUrl(BUCKET_BANNERS, existing.imageUrl);
            if (uploadedImageUrl != null && !uploadedImageUrl.isEmpty()) {
                dialogImagePreview.setVisibility(View.VISIBLE);
                dialogUploadPlaceholder.setVisibility(View.GONE);
                Glide.with(this).load(uploadedImageUrl).centerCrop().into(dialogImagePreview);
            }
        }

        if (cardImage != null) cardImage.setOnClickListener(v -> imagePicker.launch("image/*"));
        etExpiry.setOnClickListener(v -> showDatePicker(etExpiry));
        etExpiry.setOnFocusChangeListener((v, f) -> { if (f) showDatePicker(etExpiry); });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogView).create();
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title   = getET(etTitle);
            String redir   = actvRedir.getText().toString().trim();
            String redirId = getET(etRedirId);
            String expiry  = getET(etExpiry);
            boolean active = swActive.isChecked();
            int priority   = 1;
            try { priority = Integer.parseInt(getET(etPriority)); } catch (Exception ignored) {}

            if (title.isEmpty()) {
                setFieldError(dialogView.findViewById(R.id.tilBannerTitle), "Title is required");
                return;
            }
            if (!isEdit && selectedImageUri == null && (uploadedImageUrl == null || uploadedImageUrl.isEmpty())) {
                Toast.makeText(this, "Banner image is required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedImageUri != null) {
                uploadBannerImage(selectedImageUri, title, redir, redirId, expiry,
                        active, priority, isEdit ? existing.id : null, dialog);
            } else {
                String imgUrl = uploadedImageUrl != null ? uploadedImageUrl
                        : (isEdit ? SupabaseStorageHelper.getPublicObjectUrl(BUCKET_BANNERS, existing.imageUrl) : "");
                saveBanner(title, imgUrl != null ? imgUrl : "", redir, redirId,
                        expiry, active, priority, isEdit ? existing.id : null, dialog);
            }
        });
        dialog.show();
    }

    private void uploadBannerImage(Uri uri, String title, String redirect, String redirId,
                                    String expiry, boolean active, int priority,
                                    String bannerId, AlertDialog dialog) {
        if (dialogUploadProgress != null) {
            dialogUploadProgress.setVisibility(View.VISIBLE);
            dialogUploadProgress.setText("Compressing and uploading image...");
        }

        SupabaseStorageHelper.uploadBannerImage(this, uri,
                new SupabaseStorageHelper.UploadCallback() {
                    @Override
                    public void onSuccess(String url) {
                        Log.d(TAG, "uploadBannerImage SUCCESS url=" + url);
                        uploadedImageUrl = url;
                        if (dialogUploadProgress != null) dialogUploadProgress.setVisibility(View.GONE);
                        saveBanner(title, url, redirect, redirId, expiry, active, priority, bannerId, dialog);
                    }

                    @Override
                    public void onError(String msg) {
                        Log.e(TAG, "uploadBannerImage FAILED: " + msg);
                        if (dialogUploadProgress != null) dialogUploadProgress.setVisibility(View.GONE);
                        Toast.makeText(BannerManagementActivity.this,
                                "Image upload failed: " + msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── SAVE BANNER (INSERT or PATCH) ─────────────────────────────────────
    private void saveBanner(String title, String imageUrl, String redirectType,
                             String redirectId, String expiryDate, boolean isActive,
                             int priority, String existingId, AlertDialog dialog) {
        new Thread(() -> {
            try {
                boolean isEdit = (existingId != null && !existingId.isEmpty());
                String method = isEdit ? "PATCH" : "POST";
                String url = isEdit
                        ? SUPABASE_URL + "/rest/v1/banners?id=eq." + existingId
                        : SUPABASE_URL + "/rest/v1/banners";

                // Use DB column names per spec:
                //   active (bool), redirect_value, expiry_date, image_url, title, priority
                JSONObject payload = new JSONObject();
                if (!isEdit) payload.put("id", UUID.randomUUID().toString());
                payload.put("title",          title);
                payload.put("image_url",      imageUrl != null ? imageUrl : "");
                payload.put("redirect_type",  redirectType != null ? redirectType : "category");
                payload.put("redirect_id", redirectId != null ? redirectId : "");
                payload.put("priority", priority);
                payload.put("is_active", isActive);
                if (!isEdit) {
                    payload.put("start_date", new java.text.SimpleDateFormat("yyyy-MM-dd",
                            java.util.Locale.US).format(new java.util.Date()));
                }
                if (expiryDate != null && !expiryDate.isEmpty()) {
                    payload.put("expiry_date", expiryDate);
                } else {
                    payload.put("expiry_date", JSONObject.NULL);
                }

                Log.d(TAG, "saveBanner " + method + " payload=" + payload);
                if (isEdit) {
                    Log.e("UPDATE_BODY", payload.toString());
                }
                Log.e("REQUEST", payload.toString());
                SaveResult result = executeSave(url, method, payload);
                Log.d(TAG, "saveBanner response HTTP " + result.code + ": " + result.body);
                Log.e("RESPONSE", result.body != null ? result.body : "");

                main.post(() -> {
                    if (result.success) {
                        dialog.dismiss();
                        Toast.makeText(BannerManagementActivity.this,
                                isEdit ? "Banner updated" : "Banner added",
                                Toast.LENGTH_SHORT).show();
                        loadBanners();
                    } else {
                        Log.e("ERROR", result.body);
                        Log.e(TAG, "saveBanner FAILED HTTP " + result.code + ": " + result.body);
                        Toast.makeText(BannerManagementActivity.this,
                                "Save failed (" + result.code + "): " + result.body,
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "saveBanner exception", e);
                main.post(() -> Toast.makeText(BannerManagementActivity.this,
                        "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void toggleBannerActive(Banner banner, boolean newState) {
        new Thread(() -> {
            try {
                // DB column is "active" per spec
                JSONObject payload = new JSONObject();
                payload.put("is_active", newState);
                Log.e("REQUEST", payload.toString());
                String patchUrl = SUPABASE_URL + "/rest/v1/banners?id=eq." + banner.id;
                Log.d(TAG, "toggleBannerActive PATCH " + patchUrl + " is_active=" + newState);
                SaveResult rs = executePatch(patchUrl, payload);
                Log.d(TAG, "toggleBannerActive HTTP " + rs.code + ": " + rs.body);

                main.post(() -> {
                    if (rs.success) {
                        banner.isActive = newState;
                        banner.status   = newState ? "active" : "inactive";
                        adapter.notifyDataSetChanged();
                        Toast.makeText(BannerManagementActivity.this,
                                newState ? "Banner enabled" : "Banner disabled",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("ERROR", rs.body);
                        Log.e(TAG, "toggleBannerActive FAILED HTTP " + rs.code + ": " + rs.body);
                        Toast.makeText(BannerManagementActivity.this,
                                "Could not update banner (" + rs.code + ")", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "toggleBannerActive exception", e);
                main.post(() -> Toast.makeText(BannerManagementActivity.this,
                        "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private static class SaveResult {
        boolean success;
        int code;
        String body;
    }

    private SaveResult executeSave(String url, String method, JSONObject payload) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .method(method, RequestBody.create(payload.toString(), JSON_MT))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            SaveResult r = new SaveResult();
            r.code = resp.code();
            r.body = resp.body() != null ? resp.body().string() : "";
            r.success = (r.code == 200 || r.code == 201 || r.code == 204);
            return r;
        }
    }

    private SaveResult executePatch(String url, JSONObject payload) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .patch(RequestBody.create(payload.toString(), JSON_MT))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build();
        try (Response resp = http.newCall(req).execute()) {
            SaveResult r = new SaveResult();
            r.code = resp.code();
            r.body = resp.body() != null ? resp.body().string() : "";
            r.success = resp.isSuccessful();
            return r;
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────
    private void confirmDelete(Banner banner) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Banner")
                .setMessage("Delete \"" + banner.title + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteBanner(banner))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteBanner(Banner banner) {
        new Thread(() -> {
            try {
                String deleteUrl = SUPABASE_URL + "/rest/v1/banners?id=eq." + banner.id;
                Log.e("DELETE_URL", deleteUrl);
                Log.d(TAG, "deleteBanner DELETE " + deleteUrl);
                Request req = new Request.Builder()
                        .url(deleteUrl)
                        .delete()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    Log.d(TAG, "deleteBanner HTTP " + resp.code() + ": " + body);
                    Log.e("RESPONSE", body);
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            banners.remove(banner);
                            adapter.notifyDataSetChanged();
                            showListOrEmpty();
                            loadBanners();
                            Toast.makeText(BannerManagementActivity.this,
                                    "Banner deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e("ERROR", body);
                            Log.e(TAG, "deleteBanner FAILED HTTP " + resp.code() + ": " + body);
                            Toast.makeText(BannerManagementActivity.this,
                                    "Delete failed (" + resp.code() + "): " + body,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteBanner exception", e);
                main.post(() -> Toast.makeText(BannerManagementActivity.this,
                        "Delete error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────
    private void setLoading(boolean loading) {
        if (shimmerLayout    != null) shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvBanners        != null) rvBanners.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (emptyStateLayout != null && loading) emptyStateLayout.setVisibility(View.GONE);
    }

    private void showDatePicker(TextInputEditText field) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            field.setText(String.format(java.util.Locale.getDefault(),
                    "%04d-%02d-%02d", y, m + 1, d));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String getET(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setFieldError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }
}

