package com.virtuallab.client.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public final class AppSettingsPrefs {
    private static final String PREF = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_LANGUAGE = "language_tag";
    private static final String DEFAULT_LANGUAGE = "en";

    private AppSettingsPrefs() {}

    public static void applySavedDisplayPreferences(Context context) {
        applyDarkMode(context);
        applyLanguage(context);
    }

    public static boolean isDarkModeEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkModeEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public static boolean areNotificationsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_NOTIFICATIONS, true);
    }

    public static void setNotificationsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    public static String getLanguageTag(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public static void setLanguageTag(Context context, String languageTag) {
        String safeTag = languageTag == null || languageTag.trim().isEmpty()
                ? DEFAULT_LANGUAGE
                : languageTag.trim();
        prefs(context).edit().putString(KEY_LANGUAGE, safeTag).apply();
    }

    public static void applyDarkMode(Context context) {
        AppCompatDelegate.setDefaultNightMode(
                isDarkModeEnabled(context)
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    public static void applyLanguage(Context context) {
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(getLanguageTag(context))
        );
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
