package com.example.quickfix;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * ProviderDetailActivity — full detail view for a single approved provider.
 * Launched from ProvidersListActivity or SearchResultsActivity.
 */
public class ProviderDetailActivity extends BaseActivity {

    private static final String TAG = "ProviderDetail";

    public static final String EXTRA_PROVIDER_ID  = "provider_id";
    public static final String EXTRA_CUSTOMER_ID  = "customer_id";
    public static final String EXTRA_FIREBASE_UID = "firebase_uid";
    public static final String EXTRA_BUSINESS_NAME= "business_name";
    public static final String EXTRA_OWNER_NAME   = "owner_name";
    public static final String EXTRA_CATEGORY     = "category";
    public static final String EXTRA_CITY         = "city";
    public static final String EXTRA_PHONE        = "phone";
    public static final String EXTRA_EMAIL        = "email";
    public static final String EXTRA_DESCRIPTION  = "description";
    public static final String EXTRA_EXPERIENCE   = "experience";
    public static final String EXTRA_IMAGE_URL    = "image_url";
    public static final String EXTRA_IS_VERIFIED  = "is_verified";
    public static final String EXTRA_RATING       = "rating";
    public static final String EXTRA_OPEN_BOOKING = "open_booking";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String providerFirebaseUid;
    private String providerName;
    private String providerCategory;
    private String providerCity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_detail);

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("");
        }

        // Read extras
        Intent i           = getIntent();
        providerFirebaseUid= i.getStringExtra(EXTRA_FIREBASE_UID);
        String businessName= i.getStringExtra(EXTRA_BUSINESS_NAME);
        String ownerName   = i.getStringExtra(EXTRA_OWNER_NAME);
        providerCategory   = i.getStringExtra(EXTRA_CATEGORY);
        providerCity       = i.getStringExtra(EXTRA_CITY);
        String phone       = i.getStringExtra(EXTRA_PHONE);
        String email       = i.getStringExtra(EXTRA_EMAIL);
        String description = i.getStringExtra(EXTRA_DESCRIPTION);
        String experience  = i.getStringExtra(EXTRA_EXPERIENCE);
        String imageUrl    = i.getStringExtra(EXTRA_IMAGE_URL);
        boolean isVerified = i.getBooleanExtra(EXTRA_IS_VERIFIED, false);
        float   rating     = i.getFloatExtra(EXTRA_RATING, 0f);
        boolean openBooking= i.getBooleanExtra(EXTRA_OPEN_BOOKING, false);

        providerName = businessName != null ? businessName : "";

        bindViews(businessName, ownerName, providerCategory, providerCity, phone,
                  email, description, experience, imageUrl, isVerified, rating);

        if (openBooking) {
            showBookingDialog();
        }
    }

    private void bindViews(String businessName, String ownerName, String category,
                           String city, String phone, String email,
                           String description, String experience,
                           String imageUrl, boolean isVerified, float rating) {

        // Business image
        ImageView ivImage = findViewById(R.id.ivDetailBusinessImage);
        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.bg_profile_header_gradient)
                .error(R.drawable.bg_profile_header_gradient)
                .centerCrop()
                .into(ivImage);
        }

        // Text fields
        setText(R.id.tvDetailBusinessName, businessName);
        setText(R.id.tvDetailOwnerName,    "by " + notEmpty(ownerName, "—"));
        setText(R.id.tvDetailCategory,     notEmpty(category, "Service"));
        setText(R.id.tvDetailCity,         notEmpty(city, "—"));

        // Experience
        TextView tvExp = findViewById(R.id.tvDetailExperience);
        if (experience != null && !experience.isEmpty()) {
            tvExp.setText(experience + " yrs exp");
            tvExp.setVisibility(View.VISIBLE);
        } else {
            tvExp.setVisibility(View.GONE);
        }

        // Verified
        View tvVerified = findViewById(R.id.tvDetailVerified);
        tvVerified.setVisibility(isVerified ? View.VISIBLE : View.GONE);

        // Rating
        View ratingRow = findViewById(R.id.ratingRow);
        if (rating > 0) {
            ratingRow.setVisibility(View.VISIBLE);
            setText(R.id.tvDetailRating, String.format("%.1f", rating));
        } else {
            ratingRow.setVisibility(View.GONE);
        }

        // Phone
        LinearLayout rowPhone = findViewById(R.id.rowPhone);
        if (phone != null && !phone.isEmpty()) {
            rowPhone.setVisibility(View.VISIBLE);
            setText(R.id.tvDetailPhone, phone);
        }

        // Email
        LinearLayout rowEmail = findViewById(R.id.rowEmail);
        if (email != null && !email.isEmpty()) {
            rowEmail.setVisibility(View.VISIBLE);
            setText(R.id.tvDetailEmail, email);
        }

        // Description
        View cardDesc = findViewById(R.id.cardDescription);
        if (description != null && !description.isEmpty()) {
            cardDesc.setVisibility(View.VISIBLE);
            setText(R.id.tvDetailDescription, description);
        }

        // Call button
        MaterialButton btnCall = findViewById(R.id.btnDetailCall);
        if (phone != null && !phone.isEmpty()) {
            btnCall.setOnClickListener(v -> {
                try {
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone));
                    startActivity(dial);
                } catch (Exception e) {
                    Toast.makeText(this, "Cannot open dialer", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnCall.setEnabled(false);
            btnCall.setAlpha(0.5f);
        }

        // Book button
        MaterialButton btnBook = findViewById(R.id.btnDetailBook);
        btnBook.setOnClickListener(v -> showBookingDialog());
    }

    // ── Booking Dialog ──────────────────────────────────────────────────────

    private void showBookingDialog() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to book a service", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_book_service, null);

        TextInputEditText etBookDate = dialogView.findViewById(R.id.etBookDate);
        TextInputEditText etBookTime = dialogView.findViewById(R.id.etBookTime);
        TextInputEditText etBookAddress = dialogView.findViewById(R.id.etBookAddress);
        TextInputEditText etBookDescription = dialogView.findViewById(R.id.etBookDescription);

        // Date picker
        etBookDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) -> {
                String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
                etBookDate.setText(date);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        etBookDate.setFocusable(false);

        // Time picker
        etBookTime.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new TimePickerDialog(this, (view, hour, minute) -> {
                String time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                etBookTime.setText(time);
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show();
        });
        etBookTime.setFocusable(false);

        // Pre-fill address with provider city if available
        if (providerCity != null && !providerCity.isEmpty()) {
            etBookAddress.setText(providerCity);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Book Service")
                .setView(dialogView)
                .setPositiveButton("Confirm Booking", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String date = etBookDate.getText() != null ? etBookDate.getText().toString().trim() : "";
                String time = etBookTime.getText() != null ? etBookTime.getText().toString().trim() : "";
                String address = etBookAddress.getText() != null ? etBookAddress.getText().toString().trim() : "";
                String description = etBookDescription.getText() != null ? etBookDescription.getText().toString().trim() : "";

                if (date.isEmpty()) {
                    etBookDate.setError("Select a date");
                    return;
                }
                if (time.isEmpty()) {
                    etBookTime.setError("Select a time");
                    return;
                }
                if (address.isEmpty()) {
                    etBookAddress.setError("Address is required");
                    return;
                }

                dialog.dismiss();
                submitBooking(currentUser.getUid(), date, time, address, description);
            });
        });

        dialog.show();
    }

    private void submitBooking(String customerId, String date, String time,
                               String address, String description) {
        Toast.makeText(this, "Submitting booking...", Toast.LENGTH_SHORT).show();

        // Fetch customer details from users table
        new Thread(() -> {
            String customerName = "";
            String customerPhone = "";

            try {
                String userUrl = SUPABASE_URL + "/rest/v1/users"
                        + "?firebase_uid=eq." + customerId
                        + "&select=name,phone&limit=1";

                Request userReq = new Request.Builder().url(userUrl)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = httpClient.newCall(userReq).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject user = arr.getJSONObject(0);
                            customerName = user.optString("name", "");
                            customerPhone = user.optString("phone", "");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch customer info: " + e.getMessage());
            }

            // Create booking JSON — only columns that exist in the bookings table
            try {
                JSONObject bookingBody = new JSONObject();
                // Required for RLS checks and provider dashboard queries
                bookingBody.put("customer_id", customerId);
                bookingBody.put("firebase_uid", providerFirebaseUid != null ? providerFirebaseUid : "");
                bookingBody.put("customer_name", customerName.isEmpty() ? "Customer" : customerName);
                bookingBody.put("customer_phone", customerPhone);
                bookingBody.put("provider_name", providerName);
                bookingBody.put("service_name", providerCategory != null ? providerCategory : "");
                bookingBody.put("category", providerCategory != null ? providerCategory : "");
                bookingBody.put("booking_date", date);
                bookingBody.put("booking_time", time);
                bookingBody.put("address", address);
                bookingBody.put("city", providerCity != null ? providerCity : "");
                bookingBody.put("price", 0);
                bookingBody.put("status", "pending");
                bookingBody.put("description", description);

                Log.d(TAG, "Booking payload: " + bookingBody.toString());

                String url = SUPABASE_URL + "/rest/v1/bookings";

                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(bookingBody.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                final String custName = customerName.isEmpty() ? "Customer" : customerName;

                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        mainHandler.post(() ->
                                Toast.makeText(ProviderDetailActivity.this,
                                        "Booking failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        int code = response.code();
                        String resp = response.body() != null ? response.body().string() : "";
                        response.close();

                        mainHandler.post(() -> {
                            if (code == 200 || code == 201 || code == 204) {
                                Toast.makeText(ProviderDetailActivity.this,
                                        "Booking submitted successfully!",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Log.e(TAG, "Booking failed: HTTP " + code + " | " + resp);
                                String shortMsg = (resp != null && !resp.trim().isEmpty()) ? resp : ("HTTP " + code);
                                Toast.makeText(ProviderDetailActivity.this,
                                        "Booking failed: " + shortMsg,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "submitBooking error: " + e.getMessage());
                mainHandler.post(() ->
                        Toast.makeText(ProviderDetailActivity.this,
                                "Error creating booking",
                                Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setText(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv != null) tv.setText(text != null ? text : "—");
    }

    private String notEmpty(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}

