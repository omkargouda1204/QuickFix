package com.example.quickfix;

import android.app.Application;

/**
 * QuickFixApp — Custom Application class.
 * Applies saved dark/light mode preference on every app start before any Activity is created.
 */
public class QuickFixApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dark/light mode from ThemeManager (shared across all roles)
        ThemeManager.applyFromPrefs(this);
    }
}
