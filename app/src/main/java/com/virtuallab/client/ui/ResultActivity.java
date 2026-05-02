package com.virtuallab.client.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;

public class ResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        int practicalId = getIntent().getIntExtra("practical_id", 0);
        String simulatorUrl = getIntent().getStringExtra("simulator_url");

        findViewById(R.id.btnRetry).setOnClickListener(v -> {
            Intent i = new Intent(this, SimulationActivity.class);
            i.putExtra("practical_id", practicalId);
            i.putExtra("simulator_url", simulatorUrl);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btnNext).setOnClickListener(v -> goHome());
    }

    private void goHome() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }
}

