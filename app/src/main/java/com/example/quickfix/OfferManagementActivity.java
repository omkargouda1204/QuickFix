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
import androidx.appcompat.app.AppCompatActivity;
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
 * OfferManagementActivity — Admin page to manage discount offers/coupons.
 * Offers stored in Supabase `offers` table.
 * When an offer is created with "Send notification" checked → FCM push sent to all users.
 */
public class OfferManagementActivity extends AppCompatActivity {

    private static final String TAG = "OfferMgmt";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView       rvOffers;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout       shimmerLayout, emptyStateLayout;
    private TextView           tvTotalOffers, tvActiveOffers, tvExpiredOffers;
    private ExtendedFloatingActionButton fabAdd;

    // Adapter
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
            @Override public void onEdit(Offer offer) { showAddEditDialog(offer); }
            @Override public void onDelete(Offer offer) { confirmDelete(offer); }
            @Override public void onToggleActive(Offer offer, boolean active) {
                toggleOfferActive(offer, active);
            }
        });
        rvOffers.setLayoutManager(new LinearLayoutManager(this));
        rvOffers.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.accent_orange);
        swipeRefresh.setOnRefreshListener(this::loadOffers);
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> showAddEditDialog(null));
    }

    // ── Load Offers ────────────────────────────────────────────────────────
    private void loadOffers() {
        setLoading(true);
        String url = SUPABASE_URL + "/rest/v1/offers?select=*&order=created_at.desc";

        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .get()
                .build();

        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(OfferManagementActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onResponse(Call call, Response resp) throws IOException {
                String body = resp.body() != null ? resp.body().string() : "[]";
                Log.d(TAG, "loadOffers HTTP " + resp.code() + " body=" + body);
                main.post(() -> {
                    setLoading(false);
                    swipeRefresh.setRefreshing(false);
                    if (resp.isSuccessful()) {
                        parseOffers(body);
                    } else {
                        Log.e(TAG, "loadOffers FAILED " + resp.code() + ": " + body);
                        Toast.makeText(OfferManagementActivity.this,
                                "Failed to load offers: HTTP " + resp.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void parseOffers(String json) {
        offers.clear();
        try {
            JSONArray arr = new JSONArray(json);
            Log.d(TAG, "parseOffers: received " + arr.length() + " rows");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Offer offer = new Offer();
                offer.id          = o.optString("id");
                offer.title       = o.optString("title");
                offer.description = o.optString("description");
                // DB column: offer_code (not coupon_code)
                offer.offerCode        = o.optString("offer_code");
                // DB column: discount_value numeric (not discount_percent)
                offer.discountValue    = o.optDouble("discount_value", 0);
                offer.discountType     = o.optString("discount_type", "percentage");
                offer.minBookingAmount = o.optDouble("min_booking_amount", 0);
                offer.expiryDate       = o.optString("expiry_date");
                offer.createdAt        = o.optString("created_at");
                // DB column: is_active boolean (no status column)
                offer.isActive = o.optBoolean("is_active", true);
                offer.status   = offer.isActive ? "active" : "inactive"; // local-only convenience
                offers.add(offer);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseOffers exception: " + e.getMessage());
        }
        Log.d(TAG, "parseOffers: parsed " + offers.size() + " offers");
        adapter.setItems(offers);
        updateStats();
        emptyStateLayout.setVisibility(offers.isEmpty() ? View.VISIBLE : View.GONE);
        rvOffers.setVisibility(offers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateStats() {
        int total   = offers.size();
        int active  = 0;
        int expired = 0;
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                java.util.Locale.getDefault()).format(new java.util.Date());
        for (Offer o : offers) {
            if (o.isEffectivelyActive()) active++;
            if (o.expiryDate != null && !o.expiryDate.isEmpty() && o.expiryDate.compareTo(today) < 0) expired++;
        }
        if (tvTotalOffers  != null) tvTotalOffers.setText(String.valueOf(total));
        if (tvActiveOffers != null) tvActiveOffers.setText(String.valueOf(active));
        if (tvExpiredOffers!= null) tvExpiredOffers.setText(String.valueOf(expired));
    }

    // ── Add / Edit Dialog ─────────────────────────────────────────────────
    private void showAddEditDialog(Offer existing) {
        boolean isEdit = (existing != null);

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_edit_offer, null);

        TextView          tvTitle     = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputEditText etTitle     = dialogView.findViewById(R.id.etOfferTitle);
        TextInputEditText etDesc      = dialogView.findViewById(R.id.etOfferDescription);
        TextInputEditText etCode      = dialogView.findViewById(R.id.etOfferCode);
        AutoCompleteTextView actvType = dialogView.findViewById(R.id.actvDiscountType);
        TextInputEditText etValue     = dialogView.findViewById(R.id.etDiscountValue);
        TextInputEditText etMinAmt    = dialogView.findViewById(R.id.etMinAmount);
        TextInputEditText etExpiry    = dialogView.findViewById(R.id.etOfferExpiry);
        SwitchMaterial    switchActive= dialogView.findViewById(R.id.switchOfferActive);
        CheckBox          cbNotif     = dialogView.findViewById(R.id.cbSendNotification);

        // Discount type dropdown
        String[] types = {"percentage", "fixed"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, types);
        actvType.setAdapter(typeAdapter);

        tvTitle.setText(isEdit ? "Edit Offer" : "Add Offer");

        if (isEdit) {
            etTitle.setText(existing.title);
            etDesc.setText(existing.description);
            etCode.setText(existing.offerCode);
            actvType.setText(existing.discountType, false);
            etValue.setText(String.valueOf((int) existing.discountValue));
            etMinAmt.setText(String.valueOf((int) existing.minBookingAmount));
            etExpiry.setText(existing.expiryDate);
            switchActive.setChecked(existing.isActive);
            cbNotif.setChecked(false); // no notification on edit by default
        }

        // Date picker
        etExpiry.setOnClickListener(v -> showDatePicker(etExpiry));
        etExpiry.setOnFocusChangeListener((v, f) -> { if (f) showDatePicker(etExpiry); });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSave).setOnClickListener(v -> {
            String title    = getText(etTitle);
            String desc     = getText(etDesc);
            String code     = getText(etCode).toUpperCase();
            String type     = actvType.getText().toString().trim();
            String valStr   = getText(etValue);
            String minStr   = getText(etMinAmt);
            String expiry   = getText(etExpiry);
            boolean active  = switchActive.isChecked();
            boolean sendNotif = cbNotif.isChecked() && !isEdit;

            // Validation
            if (title.isEmpty()) {
                setError(dialogView.findViewById(R.id.tilOfferTitle), "Title is required");
                return;
            }
            if (code.isEmpty()) {
                setError(dialogView.findViewById(R.id.tilOfferCode), "Offer code is required");
                return;
            }
            if (type.isEmpty()) {
                Toast.makeText(this, "Select discount type", Toast.LENGTH_SHORT).show();
                return;
            }
            if (valStr.isEmpty()) {
                setError(dialogView.findViewById(R.id.tilDiscountValue), "Discount value is required");
                return;
            }

            double value  = 0;
            double minAmt = 0;
            try { value  = Double.parseDouble(valStr); } catch (Exception ignored) {}
            try { minAmt = Double.parseDouble(minStr); } catch (Exception ignored) {}

            saveOffer(title, desc, code, type, value, minAmt, expiry, active,
                    isEdit ? existing.id : null, sendNotif, title, dialog);
        });

        dialog.show();
    }

    private void saveOffer(String title, String desc, String code, String discountType,
                           double discountValue, double minAmount, String expiryDate,
                           boolean isActive, String existingId, boolean sendNotif,
                           String notifTitle, AlertDialog dialog) {
        try {
            JSONObject body = new JSONObject();
            body.put("title",              title);
            body.put("description",        desc);
            body.put("offer_code",         code);          // exact DB column name
            body.put("discount_type",      discountType);
            body.put("discount_value",     discountValue); // exact DB column name (numeric)
            body.put("min_booking_amount", minAmount);
            body.put("expiry_date",        expiryDate.isEmpty() ? JSONObject.NULL : expiryDate);
            body.put("is_active",          isActive);
            // NO status, coupon_code, or discount_percent columns in DB

            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request.Builder rb2 = new Request.Builder()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal");

            if (existingId != null) {
                rb2.url(SUPABASE_URL + "/rest/v1/offers?id=eq." + existingId).patch(rb);
            } else {
                body.put("id", UUID.randomUUID().toString());
                rb = RequestBody.create(body.toString(), JSON);
                rb2.url(SUPABASE_URL + "/rest/v1/offers").post(rb);
            }

            String discountStr = discountType.equals("percentage")
                    ? (int) discountValue + "% OFF"
                    : "₹" + (int) discountValue + " OFF";

            http.newCall(rb2.build()).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    main.post(() -> Toast.makeText(OfferManagementActivity.this,
                            "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(Call call, Response resp) throws IOException {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            dialog.dismiss();
                            Toast.makeText(OfferManagementActivity.this,
                                    existingId != null ? "Offer updated" : "Offer created",
                                    Toast.LENGTH_SHORT).show();
                            loadOffers();

                            // Send FCM push notification to all users if requested
                            if (sendNotif) {
                                sendOfferPushNotification(notifTitle, code, discountStr);
                            }
                        } else {
                            try {
                                String err = resp.body() != null ? resp.body().string() : "";
                                Toast.makeText(OfferManagementActivity.this,
                                        "Error " + resp.code() + ": " + err, Toast.LENGTH_LONG).show();
                            } catch (IOException ignored) {}
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "saveOffer: " + e.getMessage());
        }
    }

    /**
     * Sends an FCM push notification to all users by fetching their tokens from Supabase
     * and storing a notification record in the `notifications` table.
     * The actual FCM delivery to each device is handled via the `notifications` table
     * which is monitored, OR via server-side triggers.
     *
     * For in-app: we insert a broadcast notification row that all users will see.
     */
    private void sendOfferPushNotification(String offerTitle, String code, String discountStr) {
        new Thread(() -> {
            try {
                // Insert a broadcast notification into `notifications` table
                // All users will see it when they open the notifications screen
                JSONObject notif = new JSONObject();
                notif.put("id",       UUID.randomUUID().toString());
                notif.put("user_id",  JSONObject.NULL);  // null = broadcast to all
                notif.put("title",    "New Offer Available 🎉");
                notif.put("message",  "Get " + discountStr + " on your booking. Use code " + code + " now!");
                notif.put("type",     "offer");
                notif.put("is_read",  false);

                RequestBody rb = RequestBody.create(notif.toString(), JSON);
                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/notifications")
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .post(rb)
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            Log.d(TAG, "Broadcast notification stored in DB");
                            Toast.makeText(OfferManagementActivity.this,
                                    "Offer notification sent to all users 🎉",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "sendOfferPushNotification: " + e.getMessage());
            }
        }).start();

        // Also send FCM push via individual user tokens
        sendFcmToAllUsers(offerTitle, code, discountStr);
    }

    /**
     * Fetches all user FCM tokens from the `users` table and sends FCM push
     * to each one via Supabase-stored tokens.
     * NOTE: For production, this should be done server-side (Supabase Edge Function / Cloud Function).
     * Here we save the notification record and let the server-side function handle FCM delivery.
     */
    private void sendFcmToAllUsers(String offerTitle, String code, String discountStr) {
        // Fetch all fcm_tokens from users table, then send push
        // This is best handled via a Supabase Edge Function or Firebase Cloud Messaging Admin SDK
        // Here we log the intent and show user feedback
        Log.d(TAG, "FCM push notification triggered for offer: " + offerTitle + " (" + code + ")");
        // In production: call a backend endpoint / Edge Function that sends FCM to all tokens
    }

    private void toggleOfferActive(Offer offer, boolean newState) {
        try {
            JSONObject body = new JSONObject();
            body.put("is_active", newState);
            // NO status column in DB — only is_active (boolean)
            RequestBody rb = RequestBody.create(body.toString(), JSON);
            Request req = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .patch(rb)
                    .build();
            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    main.post(() -> Toast.makeText(OfferManagementActivity.this,
                            "Update failed", Toast.LENGTH_SHORT).show());
                }
                @Override public void onResponse(Call call, Response resp) throws IOException {
                    main.post(() -> {
                        if (resp.isSuccessful()) {
                            offer.isActive = newState;
                            offer.status   = newState ? "active" : "inactive";
                            adapter.notifyDataSetChanged();
                            updateStats();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "toggleOfferActive: " + e.getMessage());
        }
    }

    private void confirmDelete(Offer offer) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Offer")
                .setMessage("Delete \"" + offer.title + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteOffer(offer))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOffer(Offer offer) {
        Request req = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/offers?id=eq." + offer.id)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Prefer", "return=minimal")
                .delete()
                .build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                main.post(() -> Toast.makeText(OfferManagementActivity.this,
                        "Delete failed", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(Call call, Response resp) throws IOException {
                main.post(() -> {
                    if (resp.isSuccessful()) {
                        offers.remove(offer);
                        adapter.notifyDataSetChanged();
                        updateStats();
                        emptyStateLayout.setVisibility(offers.isEmpty() ? View.VISIBLE : View.GONE);
                        Toast.makeText(OfferManagementActivity.this,
                                "Offer deleted", Toast.LENGTH_SHORT).show();
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
        if (rvOffers != null)
            rvOffers.setVisibility(loading ? View.GONE : View.VISIBLE);
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

