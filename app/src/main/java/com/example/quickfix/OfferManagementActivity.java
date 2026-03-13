package com.example.quickfix;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.OfferAdapter;
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

import android.text.TextUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OfferManagementActivity — Admin page to manage discount offers/coupons.
 * Offers stored in Supabase `offers` table.
 * When an offer is created with "Send notification" checked → FCM push sent to all users.
 */
public class OfferManagementActivity extends BaseActivity {

    private static final String TAG          = "OfferMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON_MT   = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView       rvOffers;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout       shimmerLayout, emptyStateLayout;
    private TextView           tvTotalOffers, tvActiveOffers, tvExpiredOffers;
    private ExtendedFloatingActionButton fabAdd;

    // Data
    private OfferAdapter      adapter;
    private final List<Offer> offers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offer_management);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        bindViews();
        setupRecyclerView();
        setupSwipeRefresh();
        setupFab();
        loadOffers();
    }

    private void bindViews() {
        rvOffers         = findViewById(R.id.rvOffers);
        swipeRefresh     = findViewById(R.id.swipeRefresh);
        shimmerLayout    = findViewById(R.id.shimmerLayout);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvTotalOffers    = findViewById(R.id.tvTotalOffers);
        tvActiveOffers   = findViewById(R.id.tvActiveOffers);
        tvExpiredOffers  = findViewById(R.id.tvExpiredOffers);
        fabAdd           = findViewById(R.id.fabAddOffer);

        ImageButton btnBack    = findViewById(R.id.btnBack);
        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnBack    != null) btnBack.setOnClickListener(v -> finish());
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadOffers());
    }

    private void setupRecyclerView() {
        adapter = new OfferAdapter(this, offers, new OfferAdapter.Listener() {
            @Override public void onEdit(Offer o)   { showAddEditDialog(o); }
            @Override public void onDelete(Offer o) { confirmDelete(o); }
            @Override public void onToggleActive(Offer o, boolean active) { toggleOfferActive(o, active); }
        });
        rvOffers.setLayoutManager(new LinearLayoutManager(this));
        rvOffers.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.primary);
        swipeRefresh.setOnRefreshListener(this::loadOffers);
    }

    private void setupFab() {
        if (fabAdd != null) fabAdd.setOnClickListener(v -> showAddEditDialog(null));
    }

    // ── LOAD OFFERS ── Thread-based (same pattern as MainActivity) ─────────
    private void loadOffers() {
        setLoading(true);
        String url = SUPABASE_URL + "/rest/v1/offers?select=*&order=created_at.desc";
        Log.d(TAG, "loadOffers → " + url);

        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    int    code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "[]";
                    Log.d(TAG, "loadOffers HTTP " + code + " | body_len=" + body.length());
                    main.post(() -> {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        if (code == 200) {
                            parseOffers(body);
                        } else {
                            Log.e(TAG, "loadOffers FAILED " + code + ": " + body);
                            showError("Failed to load offers (HTTP " + code + ")");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "loadOffers exception: " + e.getMessage(), e);
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    showError("Network error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void parseOffers(String json) {
        offers.clear();
        try {
            JSONArray arr = new JSONArray(json);
            Log.d(TAG, "parseOffers: " + arr.length() + " rows received");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Offer offer            = new Offer();
                offer.id               = o.optString("id");
                offer.title            = o.optString("title");
                offer.description      = o.optString("description");

                String coupon = o.optString("coupon_code", "");
                if (TextUtils.isEmpty(coupon)) coupon = o.optString("offer_code", "");
                offer.offerCode = coupon;

                String dType = o.optString("discount_type", "percentage");
                offer.discountType = dType;

                double dVal = o.has("discount_percent")
                        ? o.optDouble("discount_percent", 0)
                        : o.optDouble("discount_value", 0);
                offer.discountValue = dVal;

                offer.minBookingAmount = o.optDouble("min_booking_amount", 0);

                String endDate = o.optString("end_date", "");
                if (TextUtils.isEmpty(endDate)) endDate = o.optString("expiry_date", "");
                offer.expiryDate = endDate;

                String status = o.optString("status", "");
                if (!TextUtils.isEmpty(status)) {
                    offer.isActive = "active".equalsIgnoreCase(status);
                    offer.status = status;
                } else if (o.has("active")) {
                    offer.isActive = o.optBoolean("active", true);
                    offer.status = offer.isActive ? "active" : "inactive";
                } else {
                    offer.isActive = o.optBoolean("is_active", true);
                    offer.status = offer.isActive ? "active" : "inactive";
                }

                offer.createdAt = o.optString("created_at");
                offers.add(offer);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseOffers exception: " + e.getMessage(), e);
        }
        Log.d(TAG, "parseOffers: parsed " + offers.size() + " offers");
        adapter.setItems(offers);
        updateStats();
        showListOrEmpty();
    }

    private void showListOrEmpty() {
        boolean empty = offers.isEmpty();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvOffers         != null) rvOffers.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateStats() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        int total = offers.size(), active = 0, expired = 0;
        for (Offer o : offers) {
            if (o.isActive) active++;
            if (o.expiryDate != null && !o.expiryDate.isEmpty()
                    && o.expiryDate.compareTo(today) < 0) expired++;
        }
        if (tvTotalOffers   != null) tvTotalOffers.setText(String.valueOf(total));
        if (tvActiveOffers  != null) tvActiveOffers.setText(String.valueOf(active));
        if (tvExpiredOffers != null) tvExpiredOffers.setText(String.valueOf(expired));
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
        if (rvOffers         != null) rvOffers.setVisibility(View.GONE);
    }

    // ── ADD / EDIT DIALOG ─────────────────────────────────────────────────
    private void showAddEditDialog(Offer existing) {
        boolean isEdit = (existing != null);
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_offer, null);

        TextView             tvTitle   = dv.findViewById(R.id.tvDialogTitle);
        TextInputEditText    etTitle   = dv.findViewById(R.id.etOfferTitle);
        TextInputEditText    etDesc    = dv.findViewById(R.id.etOfferDescription);
        TextInputEditText    etCode    = dv.findViewById(R.id.etOfferCode);
        AutoCompleteTextView actvType  = dv.findViewById(R.id.actvDiscountType);
        TextInputEditText    etValue   = dv.findViewById(R.id.etDiscountValue);
        TextInputEditText    etMinAmt  = dv.findViewById(R.id.etMinAmount);
        TextInputEditText    etExpiry  = dv.findViewById(R.id.etOfferExpiry);
        SwitchMaterial       swActive  = dv.findViewById(R.id.switchOfferActive);
        CheckBox             cbNotif   = dv.findViewById(R.id.cbSendNotification);

        String[] types = {"percentage", "fixed"};
        actvType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, types));

        tvTitle.setText(isEdit ? "Edit Offer" : "Add Offer");

        if (isEdit) {
            etTitle.setText(existing.title);
            etDesc.setText(existing.description);
            etCode.setText(existing.offerCode);
            actvType.setText(existing.discountType, false);
            etValue.setText(String.valueOf((int) existing.discountValue));
            etMinAmt.setText(String.valueOf((int) existing.minBookingAmount));
            etExpiry.setText(existing.expiryDate);
            swActive.setChecked(existing.isActive);
            if (cbNotif != null) cbNotif.setChecked(false);
        }

        etExpiry.setOnClickListener(v -> showDatePicker(etExpiry));
        etExpiry.setOnFocusChangeListener((v, f) -> { if (f) showDatePicker(etExpiry); });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dv).create();
        dv.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dv.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String  title      = getET(etTitle);
            String  desc       = getET(etDesc);
            String  code       = getET(etCode).toUpperCase();
            String  type       = actvType.getText().toString().trim();
            String  valStr     = getET(etValue);
            String  minStr     = getET(etMinAmt);
            String  expiry     = getET(etExpiry);
            boolean active     = swActive.isChecked();
            boolean sendNotif  = cbNotif != null && cbNotif.isChecked() && !isEdit;

            if (title.isEmpty()) {
                setFieldError(dv.findViewById(R.id.tilOfferTitle), "Title is required"); return;
            }
            if (code.isEmpty()) {
                setFieldError(dv.findViewById(R.id.tilOfferCode), "Code is required"); return;
            }
            if (type.isEmpty()) {
                Toast.makeText(this, "Select discount type", Toast.LENGTH_SHORT).show(); return;
            }
            if (valStr.isEmpty()) {
                setFieldError(dv.findViewById(R.id.tilDiscountValue), "Value is required"); return;
            }

            double value  = 0, minAmt = 0;
            try { value  = Double.parseDouble(valStr); } catch (Exception ignored) {}
            try { minAmt = Double.parseDouble(minStr);  } catch (Exception ignored) {}

            saveOffer(title, desc, code, type, value, minAmt, expiry, active,
                    isEdit ? existing.id : null, sendNotif, dialog);
        });
        dialog.show();
    }

    // ── SAVE OFFER (Thread-based) ─────────────────────────────────────────
    private void saveOffer(String title, String desc, String code, String discountType,
                            double discountValue, double minAmount, String expiryDate,
                            boolean isActive, String existingId, boolean sendNotif,
                            AlertDialog dialog) {
        new Thread(() -> {
            try {
                String url;
                String method;
                if (existingId != null && !existingId.isEmpty()) {
                    url    = SUPABASE_URL + "/rest/v1/offers?id=eq." + existingId;
                    method = "PATCH";
                } else {
                    url    = SUPABASE_URL + "/rest/v1/offers";
                    method = "POST";
                }

                // Try modern schema first
                JSONObject modern = new JSONObject();
                if (existingId == null || existingId.isEmpty()) modern.put("id", UUID.randomUUID().toString());
                modern.put("title", title);
                modern.put("description", desc != null ? desc : "");
                modern.put("coupon_code", code);
                modern.put("discount_type", discountType);
                modern.put("discount_percent", discountValue);
                modern.put("min_booking_amount", minAmount);
                modern.put("end_date", (expiryDate == null || expiryDate.isEmpty()) ? JSONObject.NULL : expiryDate);
                modern.put("active", isActive);
                modern.put("status", isActive ? "active" : "inactive");

                SaveResult result = executeSave(url, method, modern);

                if (!result.success) {
                    // Fallback legacy schema
                    JSONObject legacy = new JSONObject();
                    if (existingId == null || existingId.isEmpty()) legacy.put("id", UUID.randomUUID().toString());
                    legacy.put("title", title);
                    legacy.put("description", desc != null ? desc : "");
                    legacy.put("offer_code", code);
                    legacy.put("discount_type", discountType);
                    legacy.put("discount_value", discountValue);
                    legacy.put("min_booking_amount", minAmount);
                    legacy.put("expiry_date", (expiryDate == null || expiryDate.isEmpty()) ? JSONObject.NULL : expiryDate);
                    legacy.put("is_active", isActive);

                    result = executeSave(url, method, legacy);
                }

                final SaveResult finalResult = result;
                main.post(() -> {
                    if (finalResult.success) {
                        dialog.dismiss();
                        Toast.makeText(OfferManagementActivity.this,
                                existingId != null ? "Offer updated" : "Offer created",
                                Toast.LENGTH_SHORT).show();
                        loadOffers();
                        if (sendNotif) {
                            String discStr = discountType.equals("percentage")
                                    ? (int) discountValue + "% OFF"
                                    : "₹" + (int) discountValue + " OFF";
                            sendOfferNotification(title, code, discStr);
                        }
                        if (existingId == null || existingId.isEmpty()) {
                            FcmNotificationHelper.notifyAdminOfferCreated(title, null);
                        }
                    } else {
                        Log.e(TAG, "saveOffer FAILED " + finalResult.code + ": " + finalResult.body);
                        Toast.makeText(OfferManagementActivity.this,
                                "Save failed (HTTP " + finalResult.code + ")",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "saveOffer exception: " + e.getMessage(), e);
                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ── SEND OFFER NOTIFICATION ────────────────────────────────────────────
    private void sendOfferNotification(String offerTitle, String code, String discStr) {
        new Thread(() -> {
            try {
                // 1. Store broadcast notification in `notifications` table (in-app users)
                JSONObject notif = new JSONObject();
                notif.put("id",      UUID.randomUUID().toString());
                notif.put("title",   "New Offer Available 🎉");
                notif.put("message", "Get " + discStr + " on your booking. Use code " + code + " now!");
                notif.put("type",    "offer");
                notif.put("is_read", false);

                try (Response r = http.newCall(new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/notifications")
                        .post(RequestBody.create(notif.toString(), JSON_MT))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build()).execute()) {
                    Log.d(TAG, "sendOfferNotification store user notifications HTTP " + r.code());
                }

                // 1b. Store admin notification row for Admin Notifications screen.
                JSONObject adminNotif = new JSONObject();
                adminNotif.put("title", "New Offer Published");
                adminNotif.put("message", "Offer " + code + " is live. " + discStr);
                adminNotif.put("type", "offer");
                adminNotif.put("is_read", false);

                try (Response ar = http.newCall(new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/admin_notifications")
                        .post(RequestBody.create(adminNotif.toString(), JSON_MT))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build()).execute()) {
                    String ab = ar.body() != null ? ar.body().string() : "";
                    Log.d(TAG, "sendOfferNotification store admin_notifications HTTP " + ar.code() + " | " + ab);
                }

                // 2. Fetch all user FCM tokens and trigger FCM push
                String tokenUrl = SUPABASE_URL + "/rest/v1/users"
                        + "?select=fcm_token&fcm_token=not.is.null";
                try (Response tr = http.newCall(new Request.Builder()
                        .url(tokenUrl)
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept",        "application/json")
                        .build()).execute()) {
                    if (tr.code() == 200) {
                        String tokBody = tr.body() != null ? tr.body().string() : "[]";
                        JSONArray tokens = new JSONArray(tokBody);
                        Log.d(TAG, "sendOfferNotification: " + tokens.length() + " user tokens found");
                        for (int i = 0; i < tokens.length(); i++) {
                            String token = tokens.getJSONObject(i).optString("fcm_token", "");
                            if (!token.isEmpty()) {
                                FcmNotificationHelper.sendOfferFcm(token,
                                        "New Offer Available 🎉",
                                        "Get " + discStr + " — use code " + code);
                            }
                        }
                    }
                }

                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Notification sent to all users ✓", Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                Log.e(TAG, "sendOfferNotification exception: " + e.getMessage(), e);
            }
        }).start();
    }

    // ── TOGGLE ACTIVE (Thread-based) ──────────────────────────────────────
    private void toggleOfferActive(Offer offer, boolean newState) {
        new Thread(() -> {
            try {
                JSONObject modern = new JSONObject();
                modern.put("active", newState);
                modern.put("status", newState ? "active" : "inactive");
                SaveResult result = executePatch(SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id, modern);

                if (!result.success) {
                    JSONObject legacy = new JSONObject();
                    legacy.put("is_active", newState);
                    result = executePatch(SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id, legacy);
                }

                final SaveResult finalResult = result;
                main.post(() -> {
                    if (finalResult.success) {
                        offer.isActive = newState;
                        offer.status   = newState ? "active" : "inactive";
                        adapter.notifyDataSetChanged();
                        updateStats();
                        Toast.makeText(OfferManagementActivity.this,
                                newState ? "Offer enabled" : "Offer disabled",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "toggleOfferActive FAILED " + finalResult.code + ": " + finalResult.body);
                        Toast.makeText(OfferManagementActivity.this,
                                "Could not update setting", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "toggleOfferActive exception", e);
                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Could not update setting", Toast.LENGTH_SHORT).show());
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

    // ── DELETE (Thread-based) ─────────────────────────────────────────────
    private void confirmDelete(Offer offer) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Offer")
                .setMessage("Delete \"" + offer.title + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteOffer(offer))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOffer(Offer offer) {
        new Thread(() -> {
            try {
                try (Response resp = http.newCall(new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id)
                        .delete()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Prefer",        "return=minimal")
                        .build()).execute()) {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            offers.remove(offer);
                            adapter.notifyDataSetChanged();
                            updateStats();
                            showListOrEmpty();
                            Toast.makeText(OfferManagementActivity.this,
                                    "Offer deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(OfferManagementActivity.this,
                                    "Delete failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Delete error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────
    private void setLoading(boolean loading) {
        if (shimmerLayout    != null) shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvOffers         != null) rvOffers.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (emptyStateLayout != null && loading) emptyStateLayout.setVisibility(View.GONE);
    }

    private void showDatePicker(TextInputEditText field) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) ->
                field.setText(String.format(java.util.Locale.getDefault(),
                        "%04d-%02d-%02d", y, m + 1, d)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String getET(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setFieldError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }
}
