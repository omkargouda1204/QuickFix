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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
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
    private String providerIdForBooking;
    private String serviceIdForBooking;
    private String providerName;
    private String providerCategory;
    private String providerCity;

    private static class UserMeta {
        String id;
        String name;
        String phone;
    }

    private static class ReviewItem {
        String customerName;
        int rating;
        String comment;
        String createdAt;
    }

    private static class BusinessMediaItem {
        String id;
        String providerId;
        String mediaUrl;
        String mediaType;
        String thumbnailUrl;
        int displayOrder;
        String createdAt;
        String updatedAt;

        static BusinessMediaItem fromJson(JSONObject json) {
            if (json == null) return null;
            BusinessMediaItem item = new BusinessMediaItem();
            item.id = json.optString("id", "");
            item.providerId = json.optString("provider_id", "");
            item.mediaUrl = json.optString("media_url", "");
            item.mediaType = json.optString("media_type", "");
            item.thumbnailUrl = json.optString("thumbnail_url", "");
            item.displayOrder = json.optInt("display_order", 0);
            item.createdAt = json.optString("created_at", "");
            item.updatedAt = json.optString("updated_at", "");
            return item;
        }

        boolean isVideo() {
            return "video".equalsIgnoreCase(mediaType);
        }
    }

    private static final int MAX_SHOWCASE_ITEMS = 8;

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
        providerIdForBooking = i.getStringExtra(EXTRA_PROVIDER_ID);
        serviceIdForBooking = i.getStringExtra("service_id");
        if (providerIdForBooking == null || providerIdForBooking.trim().isEmpty()) {
            providerIdForBooking = providerFirebaseUid;
        }
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
        loadRecentReviews();
        loadShowcaseMedia();

        // When opened from lightweight provider search suggestions, fetch complete profile.
        if (providerIdForBooking != null && !providerIdForBooking.trim().isEmpty()) {
            boolean needsFullLoad = (phone == null || phone.trim().isEmpty())
                    || (description == null || description.trim().isEmpty())
                    || (experience == null || experience.trim().isEmpty());
            if (needsFullLoad) {
                fetchProviderDetailsById(providerIdForBooking);
            }
        }

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

        // Always use Firebase UID as plain string for TEXT columns in Supabase.
        final String firebaseUid = currentUser.getUid();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_book_service, null);

        TextInputEditText etBookDate = dialogView.findViewById(R.id.etBookDate);
        TextInputEditText etBookTime = dialogView.findViewById(R.id.etBookTime);
        TextInputEditText etBookAddress = dialogView.findViewById(R.id.etBookAddress);
        TextInputEditText etBookDescription = dialogView.findViewById(R.id.etBookDescription);

        final long minBookingDateMs = startOfDayMillis(Calendar.getInstance());
        Calendar maxWindow = Calendar.getInstance();
        maxWindow.add(Calendar.DAY_OF_YEAR, 7);
        final long maxBookingDateMs = endOfDayMillis(maxWindow);

        // Date picker
        etBookDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            DatePickerDialog pickerDialog = new DatePickerDialog(this, (view, year, month, day) -> {
                String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
                etBookDate.setText(date);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            pickerDialog.getDatePicker().setMinDate(minBookingDateMs);
            pickerDialog.getDatePicker().setMaxDate(maxBookingDateMs);
            pickerDialog.show();
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
                if (!isBookingDateAllowed(date, minBookingDateMs, maxBookingDateMs)) {
                    etBookDate.setError("Choose date from today to next 7 days");
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
                submitBooking(firebaseUid, date, time, address, description);
            });
        });

        dialog.show();
    }

    private void submitBooking(String customerId, String date, String time,
                               String address, String description) {
        Toast.makeText(this, "Submitting booking...", Toast.LENGTH_SHORT).show();

        if (providerIdForBooking == null || providerIdForBooking.trim().isEmpty()) {
            Toast.makeText(this, "Provider not available. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                UserMeta customerMeta = fetchUserByFirebaseUid(customerId);

                // Canonical provider_id for bookings is provider_profiles.id.
                String providerProfileId = providerIdForBooking != null ? providerIdForBooking.trim() : "";
                if (providerProfileId.isEmpty() && providerFirebaseUid != null && !providerFirebaseUid.trim().isEmpty()) {
                    providerProfileId = fetchProviderProfileIdByFirebaseUid(providerFirebaseUid.trim());
                }
                if (providerProfileId.isEmpty()) {
                    mainHandler.post(() -> Toast.makeText(ProviderDetailActivity.this,
                            "Booking failed: provider profile not found.", Toast.LENGTH_LONG).show());
                    return;
                }

                String customerIdForInsert = (customerMeta != null && customerMeta.id != null
                        && !customerMeta.id.trim().isEmpty()) ? customerMeta.id.trim() : customerId;

                JSONObject bookingBody = new JSONObject();
                bookingBody.put("customer_id", customerIdForInsert);
                bookingBody.put("provider_id", providerProfileId);
                bookingBody.put("customer_name", (customerMeta == null || customerMeta.name == null || customerMeta.name.isEmpty())
                        ? "Customer" : customerMeta.name);
                bookingBody.put("customer_phone", (customerMeta == null || customerMeta.phone == null) ? "" : customerMeta.phone);
                bookingBody.put("provider_name", providerName != null ? providerName : "");
                if (serviceIdForBooking == null || serviceIdForBooking.trim().isEmpty()) {
                    bookingBody.put("service_id", JSONObject.NULL);
                } else {
                    bookingBody.put("service_id", serviceIdForBooking.trim());
                }
                bookingBody.put("service_name", providerCategory != null ? providerCategory : "Service");
                bookingBody.put("category", providerCategory != null ? providerCategory : "");
                bookingBody.put("booking_date", date);
                bookingBody.put("booking_time", time);
                bookingBody.put("address", address);
                bookingBody.put("city", providerCity != null ? providerCity : "");
                bookingBody.put("price", 0);
                bookingBody.put("description", description != null ? description : "");
                bookingBody.put("status", "pending");

                Log.d("BOOKING_DATA", bookingBody.toString());

                String url = SUPABASE_URL + "/rest/v1/bookings";

                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(bookingBody.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

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
                                FcmNotificationHelper.notifyProviderNewBooking(
                                        providerFirebaseUid,
                                        bookingBody.optString("customer_name", "Customer"),
                                        bookingBody.optString("service_name", "Service")
                                );
                                Toast.makeText(ProviderDetailActivity.this,
                                        "Booking submitted successfully!",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Log.e(TAG, "BOOKING_ERROR HTTP " + code + " | " + resp);
                                String msg = resp.trim().isEmpty() ? ("HTTP " + code) : resp;
                                Toast.makeText(ProviderDetailActivity.this,
                                        "Booking failed: " + msg,
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

    private void loadRecentReviews() {
        final String providerId = providerIdForBooking != null ? providerIdForBooking.trim() : "";
        final String firebaseUid = providerFirebaseUid != null ? providerFirebaseUid.trim() : "";
        if (providerId.isEmpty() && firebaseUid.isEmpty()) {
            showRecentReviews(new ArrayList<>());
            return;
        }

        new Thread(() -> {
            List<ReviewItem> reviews = new ArrayList<>();
            try {
                String url = SUPABASE_URL + "/rest/v1/provider_reviews"
                        + "?select=customer_name,rating,comment,created_at"
                        + "&order=created_at.desc&limit=5";

                if (!providerId.isEmpty() && !firebaseUid.isEmpty() && !providerId.equals(firebaseUid)) {
                    url += "&or=(provider_id.eq." + Uri.encode(providerId)
                            + ",provider_id.eq." + Uri.encode(firebaseUid) + ")";
                } else if (!providerId.isEmpty()) {
                    url += "&provider_id=eq." + Uri.encode(providerId);
                } else {
                    url += "&provider_id=eq." + Uri.encode(firebaseUid);
                }

                Request request = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "[]";
                    if (response.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            String comment = safe(o.optString("comment", ""));
                            if (comment.isEmpty()) continue;

                            ReviewItem item = new ReviewItem();
                            item.customerName = safe(o.optString("customer_name", "Customer"));
                            if (item.customerName.isEmpty()) item.customerName = "Customer";
                            item.rating = o.optInt("rating", 0);
                            item.comment = comment;
                            item.createdAt = safe(o.optString("created_at", ""));
                            reviews.add(item);
                        }
                    } else {
                        Log.w(TAG, "loadRecentReviews HTTP " + response.code() + " | " + body);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "loadRecentReviews failed: " + e.getMessage());
            }

            List<ReviewItem> finalReviews = reviews;
            mainHandler.post(() -> showRecentReviews(finalReviews));
        }).start();
    }

    private void showRecentReviews(List<ReviewItem> reviews) {
        View card = findViewById(R.id.cardRecentReviews);
        LinearLayout container = findViewById(R.id.layoutRecentReviews);
        TextView tvEmpty = findViewById(R.id.tvRecentReviewsEmpty);
        TextView tvCount = findViewById(R.id.tvRecentReviewsCount);

        if (card == null || container == null || tvEmpty == null || tvCount == null) return;

        container.removeAllViews();

        if (reviews == null || reviews.isEmpty()) {
            card.setVisibility(View.VISIBLE);
            tvCount.setText("");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        card.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        tvCount.setText(reviews.size() + " review" + (reviews.size() == 1 ? "" : "s"));

        for (int i = 0; i < reviews.size(); i++) {
            ReviewItem item = reviews.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, i == 0 ? 0 : dp(10), 0, 0);

            TextView header = new TextView(this);
            header.setText(item.customerName + " • " + item.rating + "/5");
            header.setTextColor(getResources().getColor(R.color.text_primary));
            header.setTextSize(13f);
            header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(header);

            TextView comment = new TextView(this);
            comment.setText(item.comment);
            comment.setTextColor(getResources().getColor(R.color.text_secondary));
            comment.setTextSize(13f);
            comment.setPadding(0, dp(3), 0, 0);
            row.addView(comment);

            container.addView(row);

            if (i < reviews.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                );
                lp.topMargin = dp(10);
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(0xFFEAEAEA);
                container.addView(divider);
            }
        }
    }

    private void loadShowcaseMedia() {
        final String providerProfileId = providerIdForBooking != null ? providerIdForBooking.trim() : "";
        final String providerUid = providerFirebaseUid != null ? providerFirebaseUid.trim() : "";

        if (providerProfileId.isEmpty() && providerUid.isEmpty()) {
            showShowcaseMedia(new ArrayList<>());
            return;
        }

        new Thread(() -> {
            List<BusinessMediaItem> items = new ArrayList<>();
            try {
                String url = SUPABASE_URL + "/rest/v1/business_media"
                        + "?select=id,provider_id,media_url,media_type,thumbnail_url,display_order,created_at,updated_at"
                        + "&order=display_order.asc,created_at.desc"
                        + "&limit=" + MAX_SHOWCASE_ITEMS;

                if (!providerProfileId.isEmpty() && !providerUid.isEmpty() && !providerProfileId.equals(providerUid)) {
                    url += "&or=(provider_id.eq." + Uri.encode(providerProfileId)
                            + ",provider_id.eq." + Uri.encode(providerUid) + ")";
                } else if (!providerUid.isEmpty()) {
                    url += "&provider_id=eq." + Uri.encode(providerUid);
                } else {
                    url += "&provider_id=eq." + Uri.encode(providerProfileId);
                }

                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = httpClient.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            BusinessMediaItem item = BusinessMediaItem.fromJson(arr.getJSONObject(i));
                            if (item == null || safe(item.mediaUrl).isEmpty()) continue;
                            items.add(item);
                        }
                    } else {
                        Log.w(TAG, "loadShowcaseMedia HTTP " + r.code() + " | " + body);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "loadShowcaseMedia failed: " + e.getMessage());
            }

            mainHandler.post(() -> showShowcaseMedia(items));
        }).start();
    }

    private void showShowcaseMedia(List<BusinessMediaItem> items) {
        View card = findViewById(R.id.cardShowcaseMedia);
        LinearLayout container = findViewById(R.id.layoutShowcaseMedia);
        TextView tvEmpty = findViewById(R.id.tvShowcaseEmpty);
        View scroll = findViewById(R.id.scrollShowcase);

        if (card == null || container == null || tvEmpty == null || scroll == null) return;

        container.removeAllViews();
        card.setVisibility(View.VISIBLE);

        if (items == null || items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            scroll.setVisibility(View.GONE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);

        for (BusinessMediaItem item : items) {
            FrameLayout tile = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(120));
            lp.rightMargin = dp(10);
            tile.setLayoutParams(lp);
            tile.setBackgroundColor(0xFFF2F2F2);

            ImageView preview = new ImageView(this);
            preview.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(preview);

            String loadUrl = item.isVideo() && !safe(item.thumbnailUrl).isEmpty()
                    ? item.thumbnailUrl
                    : item.mediaUrl;

            Glide.with(this)
                    .load(loadUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .centerCrop()
                    .into(preview);

            if (item.isVideo()) {
                TextView badge = new TextView(this);
                FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                );
                badgeLp.leftMargin = dp(8);
                badgeLp.topMargin = dp(8);
                badge.setLayoutParams(badgeLp);
                badge.setText("VIDEO");
                badge.setTextSize(10f);
                badge.setTextColor(0xFFFFFFFF);
                badge.setBackgroundColor(0xAA000000);
                badge.setPadding(dp(6), dp(2), dp(6), dp(2));
                tile.addView(badge);
            }

            tile.setOnClickListener(v -> openShowcaseMedia(item));
            container.addView(tile);
        }
    }

    private void openShowcaseMedia(BusinessMediaItem item) {
        if (item == null || safe(item.mediaUrl).isEmpty()) return;

        try {
            if (item.isVideo()) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(item.mediaUrl), "video/*");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, FullScreenImageActivity.class);
                intent.putExtra(FullScreenImageActivity.EXTRA_IMAGE_URL, item.mediaUrl);
                intent.putExtra(FullScreenImageActivity.EXTRA_TITLE, "Business Showcase");
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open media", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchProviderDetailsById(String providerId) {
        new Thread(() -> {
            try {
                JSONObject data = fetchProviderRecord("provider_profiles", providerId);
                if (data == null) return;

                JSONObject finalData = data;
                mainHandler.post(() -> {
                    providerFirebaseUid = finalData.optString("firebase_uid", providerFirebaseUid);
                    providerIdForBooking = finalData.optString("id", providerIdForBooking);
                    providerName = finalData.optString("business_name", providerName);
                    providerCategory = finalData.optString("category", providerCategory);
                    providerCity = finalData.optString("city", providerCity);

                    bindViews(
                            finalData.optString("business_name", providerName),
                            finalData.optString("owner_name", finalData.optString("user_name", "")),
                            finalData.optString("category", providerCategory),
                            finalData.optString("city", providerCity),
                            finalData.optString("phone", ""),
                            finalData.optString("email", ""),
                            finalData.optString("description", ""),
                            finalData.optString("experience_yrs", finalData.optString("experience", "")),
                            finalData.optString("business_image_url", ""),
                            finalData.optBoolean("is_verified", false),
                            (float) finalData.optDouble("rating", 0d)
                    );
                    loadRecentReviews();
                    loadShowcaseMedia();
                });
            } catch (Exception e) {
                Log.e(TAG, "fetchProviderDetailsById: " + e.getMessage());
            }
        }).start();
    }

    private JSONObject fetchProviderRecord(String table, String providerId) {
        try {
            String url = SUPABASE_URL + "/rest/v1/" + table
                    + "?id=eq." + providerId
                    + "&select=*&limit=1";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.code() != 200) return null;
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return null;
                return arr.getJSONObject(0);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBookingDateAllowed(String yyyyMmDd, long minMs, long maxMs) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            fmt.setLenient(false);
            Date parsed = fmt.parse(yyyyMmDd);
            if (parsed == null) return false;
            long selected = startOfDayMillis(parsed.getTime());
            return selected >= startOfDayMillis(minMs) && selected <= startOfDayMillis(maxMs);
        } catch (ParseException e) {
            return false;
        }
    }

    private long startOfDayMillis(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long endOfDayMillis(Calendar cal) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTimeInMillis();
    }

    private long startOfDayMillis(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return startOfDayMillis(c);
    }

    private UserMeta fetchUserByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) return null;
        String encoded = Uri.encode(firebaseUid.trim());
        String url = SUPABASE_URL + "/rest/v1/users"
                + "?firebase_uid=eq." + encoded
                + "&select=id,name,phone&limit=1";
        return fetchUserMeta(url);
    }

    private UserMeta fetchUserMeta(String url) {
        try {
            Request req = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = httpClient.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) return null;

                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return null;

                JSONObject o = arr.getJSONObject(0);
                UserMeta meta = new UserMeta();
                meta.id = o.optString("id", "");
                meta.name = o.optString("name", "");
                meta.phone = o.optString("phone", "");
                return meta;
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchUserMeta failed: " + e.getMessage());
            return null;
        }
    }

    private String fetchProviderProfileIdByFirebaseUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.trim().isEmpty()) return "";
        try {
            String url = SUPABASE_URL + "/rest/v1/provider_profiles"
                    + "?firebase_uid=eq." + Uri.encode(firebaseUid.trim())
                    + "&select=id&limit=1";

            Request request = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "[]";
                if (response.code() != 200) return "";

                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return "";
                return arr.getJSONObject(0).optString("id", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchProviderProfileIdByFirebaseUid: " + e.getMessage());
            return "";
        }
    }

    private void setText(int viewId, String text) {
        TextView tv = findViewById(viewId);
        if (tv != null) tv.setText(text != null ? text : "-");
    }

    private String notEmpty(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
