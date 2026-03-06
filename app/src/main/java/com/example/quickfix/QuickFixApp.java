package com.example.quickfix;

import android.app.Application;

/**
 * QuickFixApp — Custom Application class.
 * Applies saved dark/light mode preference on every app start.
 */
public class QuickFixApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Apply dark/light mode based on saved admin preference
        AppSettings.applyDarkMode(this);
    }
}
