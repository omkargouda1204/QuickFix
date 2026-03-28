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
import com.example.quickfix.api.OfferApi;
import com.example.quickfix.api.RetrofitProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OfferManagementActivity — Admin page to manage discount offers/coupons.
 * Offers stored in Supabase `offers` table.
 */
public class OfferManagementActivity extends BaseActivity {

    private static final String TAG          = "OfferMgmt";
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private final Handler main = new Handler(Looper.getMainLooper());
    private OfferApi offerApi;

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

        offerApi = RetrofitProvider.get().create(OfferApi.class);

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

    private void loadOffers() {
        setLoading(true);
        offerApi.getAllOffers(ANON_KEY, "Bearer " + ANON_KEY, "*", "created_at.desc")
                .enqueue(new Callback<List<Offer>>() {
                    @Override
                    public void onResponse(Call<List<Offer>> call, Response<List<Offer>> response) {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            offers.clear();
                            offers.addAll(response.body());
                            adapter.setItems(offers);
                            updateStats();
                            showListOrEmpty();
                        } else {
                            showError("Failed to load offers: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Offer>> call, Throwable t) {
                        setLoading(false);
                        swipeRefresh.setRefreshing(false);
                        showError("Network error: " + t.getMessage());
                    }
                });
    }

    private void showListOrEmpty() {
        boolean empty = offers.isEmpty();
        if (emptyStateLayout != null) emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvOffers         != null) rvOffers.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateStats() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new java.util.Date());
        int total = offers.size(), active = 0, expired = 0;
        for (Offer o : offers) {
            boolean notExpired = o.expiryDate == null || o.expiryDate.isEmpty() || o.expiryDate.compareTo(today) >= 0;
            if (o.isActive && notExpired) {
                active++;
            } else if (o.expiryDate != null && !o.expiryDate.isEmpty() && o.expiryDate.compareTo(today) < 0) {
                expired++;
            }
        }
        if (tvTotalOffers   != null) tvTotalOffers.setText(String.valueOf(total));
        if (tvActiveOffers  != null) tvActiveOffers.setText(String.valueOf(active));
        if (tvExpiredOffers != null) tvExpiredOffers.setText(String.valueOf(expired));
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

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
        actvType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, types));

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
            if (cbNotif != null) cbNotif.setVisibility(View.GONE);
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

            if (title.isEmpty()) { setFieldError(dv.findViewById(R.id.tilOfferTitle), "Title is required"); return; }
            if (code.isEmpty()) { setFieldError(dv.findViewById(R.id.tilOfferCode), "Code is required"); return; }
            if (valStr.isEmpty()) { setFieldError(dv.findViewById(R.id.tilDiscountValue), "Value is required"); return; }

            double value = Double.parseDouble(valStr);
            double minAmt = minStr.isEmpty() ? 0 : Double.parseDouble(minStr);

            saveOffer(title, desc, code, type, value, minAmt, expiry, active, isEdit ? existing.id : null, sendNotif, dialog);
        });
        dialog.show();
    }

    private void saveOffer(String title, String desc, String code, String discountType,
                            double discountValue, double minAmount, String expiryDate,
                            boolean isActive, String existingId, boolean sendNotif,
                            AlertDialog dialog) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", desc);
        // Compatibility: support both detailed schema and simplified schema.
        body.put("offer_code", code);
        body.put("code", code);
        body.put("discount_type", discountType);
        body.put("discount_value", discountValue);
        body.put("discount", (int) discountValue);
        body.put("min_booking_amount", minAmount);
        body.put("is_active", isActive);
        body.put("expiry_date", (expiryDate == null || expiryDate.isEmpty()) ? null : expiryDate);

        if (existingId != null) {
            executeOfferUpdate(existingId, body, title, discountType, discountValue, code, sendNotif, dialog, false);
        } else {
            body.put("id", UUID.randomUUID().toString());
            executeOfferCreate(body, title, discountType, discountValue, code, sendNotif, dialog, false);
        }
    }

    private void executeOfferCreate(Map<String, Object> body,
                                    String title,
                                    String discountType,
                                    double discountValue,
                                    String code,
                                    boolean sendNotif,
                                    AlertDialog dialog,
                                    boolean fallbackMode) {
        offerApi.createOffer(ANON_KEY, "Bearer " + ANON_KEY, "application/json", "return=minimal", body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(OfferManagementActivity.this, "✓ Offer saved", Toast.LENGTH_SHORT).show();
                            loadOffers();
                            if (sendNotif) {
                                String discStr = "percentage".equals(discountType)
                                        ? (int) discountValue + "% OFF"
                                        : "₹" + (int) discountValue + " OFF";
                                sendOfferNotification(title, code, discStr);
                            }
                            return;
                        }

                        if (!fallbackMode) {
                            executeOfferCreate(buildOfferFallbackBody(body), title, discountType, discountValue, code, sendNotif, dialog, true);
                        } else {
                            showErrorDialog("Save Failed", "Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showErrorDialog("Error", t.getMessage());
                    }
                });
    }

    private void executeOfferUpdate(String id,
                                    Map<String, Object> body,
                                    String title,
                                    String discountType,
                                    double discountValue,
                                    String code,
                                    boolean sendNotif,
                                    AlertDialog dialog,
                                    boolean fallbackMode) {
        offerApi.updateOffer(ANON_KEY, "Bearer " + ANON_KEY, "application/json", "return=minimal", "eq." + id, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(OfferManagementActivity.this, "✓ Offer saved", Toast.LENGTH_SHORT).show();
                            loadOffers();
                            if (sendNotif) {
                                String discStr = "percentage".equals(discountType)
                                        ? (int) discountValue + "% OFF"
                                        : "₹" + (int) discountValue + " OFF";
                                sendOfferNotification(title, code, discStr);
                            }
                            return;
                        }

                        if (!fallbackMode) {
                            executeOfferUpdate(id, buildOfferFallbackBody(body), title, discountType, discountValue, code, sendNotif, dialog, true);
                        } else {
                            showErrorDialog("Save Failed", "Error " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showErrorDialog("Error", t.getMessage());
                    }
                });
    }

    private Map<String, Object> buildOfferFallbackBody(Map<String, Object> source) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("title", source.get("title"));
        fallback.put("description", source.get("description"));
        fallback.put("code", source.get("code"));
        fallback.put("discount", source.get("discount"));
        fallback.put("is_active", source.get("is_active"));
        fallback.put("expiry_date", source.get("expiry_date"));
        if (source.containsKey("id")) fallback.put("id", source.get("id"));
        return fallback;
    }

    private void sendOfferNotification(String offerTitle, String code, String discStr) {
        new Thread(() -> {
            try {
                String tokenUrl = BuildConfig.SUPABASE_URL + "/rest/v1/users?select=fcm_token&fcm_token=not.is.null";
                okhttp3.Request req = new okhttp3.Request.Builder()
                        .url(tokenUrl)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .build();
                try (okhttp3.Response resp = new okhttp3.OkHttpClient().newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        org.json.JSONArray tokens = new org.json.JSONArray(resp.body().string());
                        for (int i = 0; i < tokens.length(); i++) {
                            String token = tokens.getJSONObject(i).optString("fcm_token");
                            if (!token.isEmpty()) {
                                FcmNotificationHelper.sendOfferFcm(token, "New Offer Available 🎉", "Get " + discStr + " — use code " + code);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Notification error", e);
            }
        }).start();
    }

    private void toggleOfferActive(Offer offer, boolean newState) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_active", newState);
        offerApi.updateOffer(ANON_KEY, "Bearer " + ANON_KEY, "application/json", "return=minimal", "eq." + offer.id, body)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            offer.isActive = newState;
                            adapter.notifyDataSetChanged();
                            updateStats();
                        } else {
                            showError("Failed to update: " + response.code());
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showError("Network error: " + t.getMessage());
                    }
                });
    }

    private void confirmDelete(Offer offer) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Offer")
                .setMessage("Delete \"" + offer.title + "\"?")
                .setPositiveButton("Delete", (d, w) -> deleteOffer(offer))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOffer(Offer offer) {
        offerApi.deleteOffer(ANON_KEY, "Bearer " + ANON_KEY, "eq." + offer.id)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(OfferManagementActivity.this, "✓ Deleted", Toast.LENGTH_SHORT).show();
                            loadOffers();
                        } else {
                            showError("Delete failed: " + response.code());
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showError("Network error: " + t.getMessage());
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (shimmerLayout    != null) shimmerLayout.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (rvOffers         != null) rvOffers.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (emptyStateLayout != null && loading) emptyStateLayout.setVisibility(View.GONE);
    }

    private void showDatePicker(TextInputEditText field) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            field.setText(String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private String getET(TextInputEditText et) {
        return et != null && et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setFieldError(TextInputLayout til, String msg) {
        if (til != null) til.setError(msg);
    }

    private void showErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }
}
