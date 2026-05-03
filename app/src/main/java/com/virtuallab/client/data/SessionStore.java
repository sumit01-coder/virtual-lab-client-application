package com.virtuallab.client.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public final class SessionStore {
    private static final String PREF = "session_store";
    private static final String SECURE_PREF = "secure_session_store";
    private static final String KEY_TOKEN = "auth_token";
    private static String authToken;
    private static Context appContext;
    private static boolean secureStorageAvailable;

    private SessionStore() {}

    public static void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
            SharedPreferences sp = prefs(appContext);
            authToken = sp.getString(KEY_TOKEN, null);
            migrateLegacyTokenIfNeeded(sp);
        }
    }

    public static Context app() {
        return appContext;
    }

    public static void setAuthToken(String token) {
        authToken = token;
        if (appContext != null) {
            prefs(appContext).edit().putString(KEY_TOKEN, token).apply();
            appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply();
        }
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static boolean isLoggedIn() {
        return authToken != null && !authToken.trim().isEmpty();
    }

    public static void clear() {
        authToken = null;
        if (appContext != null) {
            prefs(appContext).edit().remove(KEY_TOKEN).apply();
            appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_TOKEN).apply();
        }
    }

    public static boolean isSecureStorageAvailable(Context context) {
        if (context != null) {
            prefs(context.getApplicationContext());
        }
        return secureStorageAvailable;
    }

    private static SharedPreferences prefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            SharedPreferences securePrefs = EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREF,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            secureStorageAvailable = true;
            return securePrefs;
        } catch (Exception ignored) {
            secureStorageAvailable = false;
            return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        }
    }

    private static void migrateLegacyTokenIfNeeded(SharedPreferences targetPrefs) {
        if (appContext == null || !secureStorageAvailable || authToken != null) {
            return;
        }
        SharedPreferences legacy = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String legacyToken = legacy.getString(KEY_TOKEN, null);
        if (legacyToken == null || legacyToken.trim().isEmpty()) {
            return;
        }
        targetPrefs.edit().putString(KEY_TOKEN, legacyToken).apply();
        legacy.edit().remove(KEY_TOKEN).apply();
        authToken = legacyToken;
    }
}
