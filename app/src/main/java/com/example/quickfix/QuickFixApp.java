package com.example.quickfix;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

/**
 * QuickFixApp — Custom Application class.
 * Applies saved dark/light mode AND saved locale preference on every app start.
 * Also creates FCM notification channels so they are always ready before a push arrives.
 */
public class QuickFixApp extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        // Apply saved locale at the Application level so all resources load correctly
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Re-apply locale if the system config changes (e.g. screen rotation, system language change)
        LocaleHelper.wrap(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dark/light mode from ThemeManager (shared across all roles)
        ThemeManager.applyFromPrefs(this);
        // Create FCM notification channels (safe to call multiple times — Android deduplicates)
        MyFirebaseMessagingService.ensureNotificationChannels(this);
    }
}
