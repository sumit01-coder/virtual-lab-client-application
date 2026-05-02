package com.virtuallab.client.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.virtuallab.client.R;
import com.virtuallab.client.ui.adapter.OnboardingAdapter;

import java.util.Arrays;

public class OnboardingActivity extends AppCompatActivity {

    private static final String PREFS_APP = "app_prefs";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";

    private View dot1, dot2, dot3;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        prefs = getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE);

        ViewPager2 pager = findViewById(R.id.onboardingPager);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
        MaterialButton btn = findViewById(R.id.btnGetStarted);
        TextView skip = findViewById(R.id.btnSkip);

        OnboardingAdapter adapter = new OnboardingAdapter(Arrays.asList(
                new OnboardingAdapter.Item("Explore Virtual Labs", "Discover hands-on experiments in Physics, Chemistry, and Biology."),
                new OnboardingAdapter.Item("Learn by Doing", "Adjust controls, test hypotheses, and visualize outcomes instantly."),
                new OnboardingAdapter.Item("Track Your Progress", "Earn badges, keep streaks, and improve every day.")
        ));

        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateDots(position);
                btn.setText(position == 2 ? "Get Started" : "Next");
            }
        });

        btn.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < 2) {
                pager.setCurrentItem(current + 1, true);
            } else {
                completeOnboardingAndOpenMain();
            }
        });

        skip.setOnClickListener(v -> completeOnboardingAndOpenMain());
    }

    private void updateDots(int pos) {
        dot1.setBackgroundResource(pos == 0 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        dot2.setBackgroundResource(pos == 1 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
        dot3.setBackgroundResource(pos == 2 ? R.drawable.bg_dot_active : R.drawable.bg_dot_inactive);
    }

    private void completeOnboardingAndOpenMain() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

