package com.example.quickfix;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity — all QuickFix Activities extend this instead of AppCompatActivity directly.
 *
 * <p>Sole responsibility: wrap the base context with the user's saved locale so every
 * Activity automatically renders in the correct language without any per-Activity code.
 *
 * <p>The locale is applied in {@link #attachBaseContext(Context)} which is called by the
 * framework <em>before</em> {@code onCreate()}, ensuring {@code getResources()} and
 * {@code getString()} return strings in the right language from the very first call.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context base) {
        // Wrap the context with the persisted locale before the Activity inflates
        super.attachBaseContext(LocaleHelper.wrap(base));
    }
}
