package com.virtuallab.client;

import android.app.Application;

import com.virtuallab.client.data.AppSettingsPrefs;
import com.virtuallab.client.data.SessionStore;

public class VirtualLabApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SessionStore.init(this);
        AppSettingsPrefs.applySavedDisplayPreferences(this);
    }
}
