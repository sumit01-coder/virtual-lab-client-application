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
import com.virtuallab.client.util.NetworkHealthManager;

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
            if (!onboardingDone) {
                openScreen(OnboardingActivity.class);
                return;
            }
            NetworkHealthManager.checkAsync(this, result -> {
                if (result.isGoodForServerData()) {
                    openScreen(MainActivity.class);
                } else {
                    Intent i = new Intent(SplashActivity.this, NetworkStatusActivity.class);
                    i.putExtra(NetworkStatusActivity.EXTRA_NEXT_SCREEN, NetworkStatusActivity.NEXT_MAIN);
                    startActivity(i);
                    finish();
                    overridePendingTransition(R.anim.fade_slide_in, R.anim.fade_out);
                }
            });
        }, 1400);
    }

    private void openScreen(Class<?> screen) {
        startActivity(new Intent(SplashActivity.this, screen));
        finish();
        overridePendingTransition(R.anim.fade_slide_in, R.anim.fade_out);
    }
}

