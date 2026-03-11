package com.example.quickfix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.util.Locale;

/**
 * LocaleHelper — single source of truth for multi-language support.
 *
 * <p>Usage:
 * <ul>
 *   <li>Call {@link #wrap(Context)} from every {@code Activity.attachBaseContext()}
 *       and from {@code Application.attachBaseContext()}.</li>
 *   <li>Call {@link #setLocale(Context, String)} when the user picks a language.</li>
 *   <li>Call {@link #getSavedLocaleCode(Context)} to read the persisted code.</li>
 * </ul>
 *
 * <p>Supported locale codes: {@code en}, {@code kn}, {@code hi}, {@code ta},
 * {@code te}, {@code ml}.  Any unknown code falls back to {@code en}.
 */
public final class LocaleHelper {

    private static final String TAG        = "LocaleHelper";
    private static final String PREFS_NAME = "quickfix_prefs";
    private static final String KEY_LANG   = "selected_language";      // display name key (legacy)
    private static final String KEY_LOCALE = "selected_locale_code";   // BCP-47 code key

    private LocaleHelper() {}   // utility class – no instances

    // ─────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Wraps the given context with the user's saved locale.
     * Call this from {@code attachBaseContext()} in every Activity and Application.
     *
     * @param ctx the base context passed to {@code attachBaseContext()}
     * @return a new context configured with the correct locale
     */
    public static Context wrap(Context ctx) {
        try {
            String code = getSavedLocaleCode(ctx);
            return applyLocale(ctx, new Locale(code));
        } catch (Exception e) {
            Log.e(TAG, "wrap() failed, returning original context: " + e.getMessage());
            return ctx;
        }
    }

    /**
     * Persists the chosen locale and updates the app-wide default.
     * After calling this, call {@code Activity.recreate()} to apply visually.
     *
     * @param ctx         any valid context
     * @param localeCode  BCP-47 code, e.g. "en", "kn", "hi", "ta", "te", "ml"
     */
    public static void setLocale(Context ctx, String localeCode) {
        try {
            String safe = sanitizeCode(localeCode);
            saveLocaleCode(ctx, safe);
            Locale locale = new Locale(safe);
            Locale.setDefault(locale);
            applyLocale(ctx.getApplicationContext(), locale);
            Log.d(TAG, "setLocale → " + safe);
        } catch (Exception e) {
            Log.e(TAG, "setLocale() failed: " + e.getMessage());
        }
    }

    /**
     * Returns the currently persisted BCP-47 locale code (default: "en").
     */
    public static String getSavedLocaleCode(Context ctx) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            // Prefer the new code key; fall back to converting the old display-name key
            if (prefs.contains(KEY_LOCALE)) {
                return sanitizeCode(prefs.getString(KEY_LOCALE, "en"));
            }
            // Legacy migration: convert stored display name → locale code
            String displayName = prefs.getString(KEY_LANG, "English");
            return displayNameToCode(displayName);
        } catch (Exception e) {
            Log.e(TAG, "getSavedLocaleCode() failed: " + e.getMessage());
            return "en";
        }
    }

    /**
     * Returns the display name for the currently saved locale
     * (e.g. "Kannada", "Hindi", "English").
     */
    public static String getSavedDisplayName(Context ctx) {
        return codeToDisplayName(getSavedLocaleCode(ctx));
    }

    /** Converts a language display name to its BCP-47 code. */
    public static String displayNameToCode(String displayName) {
        if (displayName == null) return "en";
        switch (displayName.trim()) {
            case "Kannada":   return "kn";
            case "Hindi":     return "hi";
            case "Tamil":     return "ta";
            case "Telugu":    return "te";
            case "Malayalam": return "ml";
            default:          return "en";
        }
    }

    /** Converts a BCP-47 code to its display name. */
    public static String codeToDisplayName(String code) {
        if (code == null) return "English";
        switch (code.trim()) {
            case "kn": return "Kannada";
            case "hi": return "Hindi";
            case "ta": return "Tamil";
            case "te": return "Telugu";
            case "ml": return "Malayalam";
            default:   return "English";
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Applies a locale to a context, handling both modern (API 17+) and legacy APIs.
     * Returns a new context with the locale applied (important for API 24+).
     */
    @SuppressLint("ObsoleteSdkInt")
    @SuppressWarnings("deprecation")
    private static Context applyLocale(Context ctx, Locale locale) {
        Locale.setDefault(locale);
        Resources res = ctx.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            return ctx.createConfigurationContext(config);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            res.updateConfiguration(config, res.getDisplayMetrics());
            return ctx;
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
            return ctx;
        }
    }

    private static void saveLocaleCode(Context ctx, String code) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String displayName = codeToDisplayName(code);
        prefs.edit()
                .putString(KEY_LOCALE, code)
                .putString(KEY_LANG, displayName)   // keep legacy key in sync
                .apply();
    }

    /** Guards against null/empty/unknown codes; falls back to "en". */
    private static String sanitizeCode(String code) {
        if (code == null || code.trim().isEmpty()) return "en";
        switch (code.trim().toLowerCase(Locale.ENGLISH)) {
            case "kn": case "kannada":   return "kn";
            case "hi": case "hindi":     return "hi";
            case "ta": case "tamil":     return "ta";
            case "te": case "telugu":    return "te";
            case "ml": case "malayalam": return "ml";
            default:                     return "en";
        }
    }
}

