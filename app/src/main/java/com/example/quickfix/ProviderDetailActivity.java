package com.example.quickfix;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;

/**
 * ProviderDetailActivity — full detail view for a single approved provider.
 * Launched from ProvidersListActivity.
 */
public class ProviderDetailActivity extends BaseActivity {

    public static final String EXTRA_PROVIDER_ID  = "provider_id";
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
        String businessName= i.getStringExtra(EXTRA_BUSINESS_NAME);
        String ownerName   = i.getStringExtra(EXTRA_OWNER_NAME);
        String category    = i.getStringExtra(EXTRA_CATEGORY);
        String city        = i.getStringExtra(EXTRA_CITY);
        String phone       = i.getStringExtra(EXTRA_PHONE);
        String email       = i.getStringExtra(EXTRA_EMAIL);
        String description = i.getStringExtra(EXTRA_DESCRIPTION);
        String experience  = i.getStringExtra(EXTRA_EXPERIENCE);
        String imageUrl    = i.getStringExtra(EXTRA_IMAGE_URL);
        boolean isVerified = i.getBooleanExtra(EXTRA_IS_VERIFIED, false);
        float   rating     = i.getFloatExtra(EXTRA_RATING, 0f);
        boolean openBooking= i.getBooleanExtra(EXTRA_OPEN_BOOKING, false);

        bindViews(businessName, ownerName, category, city, phone,
                  email, description, experience, imageUrl, isVerified, rating);

        if (openBooking) {
            // Trigger book action immediately
            Toast.makeText(this, "Booking feature coming soon!", Toast.LENGTH_SHORT).show();
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
        btnBook.setOnClickListener(v ->
            Toast.makeText(this, "Booking feature coming soon!", Toast.LENGTH_SHORT).show());
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

