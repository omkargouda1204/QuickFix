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
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
        String primaryUrl = SUPABASE_URL + "/rest/v1/offers?select=*"
                + "&is_active=eq.true"
                + "&or=(expiry_date.is.null,expiry_date.gte.now())"
                + "&order=created_at.desc";
        String fallbackUrl = SUPABASE_URL + "/rest/v1/offers?select=*"
                + "&is_active=eq.true"
                + "&or=(expiry_date.is.null,expiry_date.gte." + today + ")"
                + "&order=created_at.desc";
        Log.d(TAG, "loadOffers -> " + primaryUrl);

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
                    main.post(() -> {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        parseOffers(finalBody);
                        updateStats();
                    });
                } else {
                    Log.e("ERROR", finalBody);
                    Log.e("RESPONSE", finalBody);
                    main.post(() -> {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        showError("Failed to load offers (HTTP " + finalCode + ")");
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
                offer.description      = o.optString("description", "");
                offer.offerCode        = o.optString("offer_code", "");
                offer.discountType     = o.optString("discount_type", "percentage");
                offer.discountValue    = o.optDouble("discount_value", 0);
                offer.minBookingAmount = o.optDouble("min_booking_amount", 0);
                offer.expiryDate       = o.optString("expiry_date", "");
                offer.isActive         = o.optBoolean("is_active", true);
                offer.status           = offer.isActive ? "active" : "inactive";
                offer.createdAt        = o.optString("created_at");
                offers.add(offer);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseOffers exception: " + e.getMessage(), e);
        }
        Log.d(TAG, "parseOffers: parsed " + offers.size() + " offers");
        adapter.setItems(offers);
        adapter.notifyDataSetChanged();
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
                boolean isEdit = (existingId != null && !existingId.isEmpty());
                String url = isEdit
                        ? SUPABASE_URL + "/rest/v1/offers?id=eq." + existingId
                        : SUPABASE_URL + "/rest/v1/offers";
                String method = isEdit ? "PATCH" : "POST";

                // Exact DB columns: offer_code, discount_value, min_booking_amount, is_active
                JSONObject payload = new JSONObject();
                if (!isEdit) payload.put("id", UUID.randomUUID().toString());
                payload.put("title", title);
                payload.put("description", desc != null ? desc : "");
                payload.put("offer_code", code);
                payload.put("discount_type", discountType);
                payload.put("discount_value", discountValue);
                payload.put("min_booking_amount", minAmount);
                payload.put("is_active", isActive);
                if (expiryDate != null && !expiryDate.isEmpty()) {
                    payload.put("expiry_date", expiryDate);
                } else {
                    payload.put("expiry_date", JSONObject.NULL);
                }

                Log.d(TAG, "saveOffer " + method + " payload=" + payload);
                if (isEdit) {
                    Log.e("UPDATE_BODY", payload.toString());
                }
                Log.e("REQUEST", payload.toString());
                SaveResult result = executeSave(url, method, payload);
                Log.d(TAG, "saveOffer response HTTP " + result.code + ": " + result.body);
                Log.e("RESPONSE", result.body != null ? result.body : "");

                main.post(() -> {
                    if (result.success) {
                        dialog.dismiss();
                        Toast.makeText(OfferManagementActivity.this,
                                isEdit ? "Offer updated" : "Offer created",
                                Toast.LENGTH_SHORT).show();
                        loadOffers();
                        if (sendNotif) {
                            String discStr = discountType.equals("percentage")
                                    ? (int) discountValue + "% OFF"
                                    : "₹" + (int) discountValue + " OFF";
                            sendOfferNotification(title, code, discStr);
                        }
                        if (!isEdit) {
                            FcmNotificationHelper.notifyAdminOfferCreated(title, null);
                        }
                    } else {
                        Log.e("ERROR", result.body);
                        Log.e(TAG, "saveOffer FAILED " + result.code + ": " + result.body);
                        Toast.makeText(OfferManagementActivity.this,
                                "Save failed (" + result.code + "): " + result.body,
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
                JSONObject payload = new JSONObject();
                payload.put("is_active", newState);
                Log.e("REQUEST", payload.toString());
                String patchUrl = SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id;
                Log.d(TAG, "toggleOfferActive PATCH " + patchUrl + " is_active=" + newState);
                SaveResult result = executePatch(patchUrl, payload);

                main.post(() -> {
                    if (result.success) {
                        offer.isActive = newState;
                        offer.status   = newState ? "active" : "inactive";
                        adapter.notifyDataSetChanged();
                        updateStats();
                        Toast.makeText(OfferManagementActivity.this,
                                newState ? "Offer enabled" : "Offer disabled",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("ERROR", result.body);
                        Log.e(TAG, "toggleOfferActive FAILED HTTP " + result.code + ": " + result.body);
                        Toast.makeText(OfferManagementActivity.this,
                                "Could not update offer (" + result.code + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "toggleOfferActive exception", e);
                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Could not update offer", Toast.LENGTH_SHORT).show());
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
                String deleteUrl = SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id;
                Log.e("DELETE_URL", deleteUrl);
                Log.d(TAG, "deleteOffer DELETE " + deleteUrl);
                try (Response resp = http.newCall(new Request.Builder()
                        .url(deleteUrl)
                        .delete()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build()).execute()) {

                    String body = resp.body() != null ? resp.body().string() : "";
                    Log.d(TAG, "deleteOffer HTTP " + resp.code() + ": " + body);
                    Log.e("RESPONSE", body);

                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            offers.remove(offer);
                            adapter.notifyDataSetChanged();
                            showListOrEmpty();
                            loadOffers();
                            Toast.makeText(OfferManagementActivity.this,
                                    "Offer deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e("ERROR", body);
                            Log.e(TAG, "deleteOffer FAILED HTTP " + resp.code() + ": " + body);
                            Toast.makeText(OfferManagementActivity.this,
                                    "Delete failed (" + resp.code() + "): " + body,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteOffer exception", e);
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
