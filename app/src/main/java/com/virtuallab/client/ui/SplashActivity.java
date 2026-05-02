package com.virtuallab.client.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.data.AppSettingsPrefs;
import com.virtuallab.client.data.SessionStore;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_APP = "app_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionStore.init(getApplicationContext());
        AppSettingsPrefs.applySavedDisplayPreferences(this);
        // Pre-warm Retrofit/OkHttp off the UI thread to avoid first-screen jank/ANR.
        new Thread(() -> {
            try {
                ApiClient.get();
            } catch (Throwable ignored) {
            }
        }, "api-prewarm").start();
        setContentView(R.layout.activity_splash);
        findViewById(android.R.id.content).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);
            boolean onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false);
            Class<?> nextScreen = onboardingDone ? MainActivity.class : OnboardingActivity.class;
            startActivity(new Intent(SplashActivity.this, nextScreen));
            finish();
            overridePendingTransition(R.anim.fade_slide_in, R.anim.fade_out);
        }, 1400);
    }
}

