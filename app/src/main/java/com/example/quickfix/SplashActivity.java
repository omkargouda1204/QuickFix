package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 3000; // 3 seconds total

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // setContentView ensures the layout is inflated and real content is shown
        setContentView(R.layout.activity_splash);

        // Bind views from ConstraintLayout
        ImageView splashLogo = findViewById(R.id.splash_logo);
        TextView splashText = findViewById(R.id.splash_text);
        TextView splashTagline = findViewById(R.id.splash_tagline);

        // Hide all views first so animation always fades in from invisible
        splashLogo.setAlpha(0f);
        splashText.setAlpha(0f);
        splashTagline.setAlpha(0f);

        // Load animation (fade + scale)
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);

        // Animate logo immediately
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashLogo.setAlpha(1f);   // make visible so animation renders
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashLogo.startAnimation(anim);

        // Animate text with 150ms delay for staggered effect
        Animation animText = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);
        animText.setStartOffset(150);
        animText.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashText.setAlpha(1f);
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashText.startAnimation(animText);

        // Animate tagline with 300ms delay
        Animation animTag = AnimationUtils.loadAnimation(this, R.anim.splash_fade_scale);
        animTag.setStartOffset(300);
        animTag.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                splashTagline.setAlpha(1f);
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }

            @Override
            public void onAnimationEnd(Animation a) {
            }
        });
        splashTagline.startAnimation(animTag);

        // Auto-navigate to LoginActivity after 3 seconds
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, SPLASH_DURATION);
    }
}
