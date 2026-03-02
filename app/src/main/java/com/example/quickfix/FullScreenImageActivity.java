package com.example.quickfix;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;

/**
 * FullScreenImageActivity — Displays a single image full-screen with zoom.
 *
 * Usage:
 *   Intent i = new Intent(context, FullScreenImageActivity.class);
 *   i.putExtra(EXTRA_IMAGE_URL, imageUrl);
 *   i.putExtra(EXTRA_TITLE, "Business Image");
 *   startActivity(i);
 */
public class FullScreenImageActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "image_url";
    public static final String EXTRA_TITLE     = "image_title";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String title    = getIntent().getStringExtra(EXTRA_TITLE);

        TextView tvTitle     = findViewById(R.id.tvImageTitle);
        ImageView imageView  = findViewById(R.id.fullscreenImageView);
        ProgressBar progress = findViewById(R.id.imageProgress);
        MaterialButton btnClose = findViewById(R.id.btnCloseImage);

        if (title != null) tvTitle.setText(title);

        btnClose.setOnClickListener(v -> finish());
        imageView.setOnClickListener(v -> finish()); // tap anywhere to close

        if (imageUrl == null || imageUrl.isEmpty()) {
            Toast.makeText(this, "Image not available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progress.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(imageUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                               @NonNull Target<Drawable> target,
                                               boolean isFirstResource) {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(FullScreenImageActivity.this,
                                "Failed to load image", Toast.LENGTH_SHORT).show();
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                   Target<Drawable> target,
                                                   @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        progress.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(imageView);
    }
}

