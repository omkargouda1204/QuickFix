package com.example.quickfix;

import android.content.Context;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * UserPreferenceManager keeps per-user language and dark mode in a small observable state.
 * It is backed by ThemeManager + LocaleHelper and notifies active screens on changes.
 */
public final class UserPreferenceManager {

    public interface Listener {
        void onPreferencesChanged(boolean darkMode, String languageCode);
    }

    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static boolean initialized;
    private static boolean darkMode;
    private static String languageCode = "en";

    private UserPreferenceManager() {}

    public static synchronized void init(Context context) {
        if (context == null) return;
        darkMode = ThemeManager.isUserDarkMode(context);
        languageCode = LocaleHelper.getSavedLocaleCode(context);
        initialized = true;
    }

    public static synchronized void addListener(Listener listener) {
        if (listener == null) return;
        LISTENERS.add(listener);
    }

    public static synchronized void removeListener(Listener listener) {
        if (listener == null) return;
        LISTENERS.remove(listener);
    }

    public static synchronized boolean isDarkMode(Context context) {
        ensureInit(context);
        return darkMode;
    }

    public static synchronized String getLanguageCode(Context context) {
        ensureInit(context);
        return languageCode;
    }

    public static void setDarkMode(Context context, boolean value) {
        if (context == null) return;
        synchronized (UserPreferenceManager.class) {
            ensureInit(context);
            darkMode = value;
        }
        ThemeManager.toggleUser(context, value);
        notifyListeners();
    }

    public static void setLanguage(Context context, String code) {
        if (context == null) return;
        String safe = code == null || code.trim().isEmpty() ? "en" : code.trim();
        synchronized (UserPreferenceManager.class) {
            ensureInit(context);
            languageCode = safe;
        }
        LocaleHelper.setLocale(context, safe);
        notifyListeners();
    }

    public static void applyFromServer(Context context, String firebaseUid, boolean dark, String lang) {
        if (context == null) return;
        String safeLang = (lang == null || lang.trim().isEmpty()) ? "en" : lang.trim();
        ThemeManager.saveUserForUid(context, firebaseUid, dark);
        LocaleHelper.saveLocaleForUid(context, firebaseUid, safeLang);
        synchronized (UserPreferenceManager.class) {
            darkMode = dark;
            languageCode = safeLang;
            initialized = true;
        }
        ThemeManager.apply(dark);
        LocaleHelper.setLocale(context, safeLang);
        notifyListeners();
    }

    private static void notifyListeners() {
        boolean mode;
        String lang;
        synchronized (UserPreferenceManager.class) {
            mode = darkMode;
            lang = languageCode;
        }
        for (Listener listener : LISTENERS) {
            if (listener != null) listener.onPreferencesChanged(mode, lang);
        }
    }

    private static void ensureInit(Context context) {
        if (!initialized) init(context);
    }
}

