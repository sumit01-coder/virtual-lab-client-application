package com.virtuallab.client;

import android.app.Application;

import com.virtuallab.client.data.AppSettingsPrefs;
import com.virtuallab.client.data.SessionGuard;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.security.SecurityPolicy;
import com.virtuallab.client.util.CrashReportManager;

public class VirtualLabApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SessionStore.init(this);
        SecurityPolicy.installProcessGuards();
        CrashReportManager.install(this);
        SessionGuard.register(this);
        AppSettingsPrefs.applySavedDisplayPreferences(this);
    }
}
