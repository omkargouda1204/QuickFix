package com.example.quickfix;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

/**
 * QuickFixApp applies locale, theme, and cached admin settings at startup.
 */
public class QuickFixApp extends Application {

    private static QuickFixApp instance;

    public static QuickFixApp getInstance() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.wrap(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        ThemeManager.applyFromPrefs(this);
        MyFirebaseMessagingService.ensureNotificationChannels(this);

        // Keep local cache in sync with Supabase admin_settings on app start.
        AdminSettingsRepository.syncInBackground(this);
    }
}
