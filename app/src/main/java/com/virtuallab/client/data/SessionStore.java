package com.virtuallab.client.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionStore {
    private static final String PREF = "session_store";
    private static final String KEY_TOKEN = "auth_token";
    private static String authToken;
    private static Context appContext;

    private SessionStore() {}

    public static void init(Context context) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
            SharedPreferences sp = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            authToken = sp.getString(KEY_TOKEN, null);
        }
    }

    public static Context app() {
        return appContext;
    }

    public static void setAuthToken(String token) {
        authToken = token;
        if (appContext != null) {
            appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TOKEN, token)
                    .apply();
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
            appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_TOKEN)
                    .apply();
        }
    }
}
