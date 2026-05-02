package com.virtuallab.client.ui;

import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;

public class StorageDetailsActivity extends AppCompatActivity {

    private static final long APP_STORAGE_LIMIT = 1024L * 1024L * 1024L;

    private OfflineSimulationManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_details);

        try {
            manager = new OfflineSimulationManager(this);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to initialize secure storage", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        findViewById(R.id.btnClearAllOffline).setOnClickListener(v -> showClearDialog());
        findViewById(R.id.btnStorageBack).setOnClickListener(v -> finish());
        refresh();
    }

    private void refresh() {
        long simulation = manager.calculateStorageUsed();
        long appData = getCacheDir().length();
        long other = Math.max(0L, APP_STORAGE_LIMIT - (simulation + appData));

        ((TextView) findViewById(R.id.txtStorageSimulations)).setText(OfflineSimulationManager.formatSize(simulation));
        ((TextView) findViewById(R.id.txtStorageAppData)).setText(OfflineSimulationManager.formatSize(appData));
        ((TextView) findViewById(R.id.txtStorageOther)).setText(OfflineSimulationManager.formatSize(other));

        int pct = (int) ((simulation * 100L) / APP_STORAGE_LIMIT);
        ProgressBar donut = findViewById(R.id.storageDonutProgress);
        donut.setProgress(Math.min(100, Math.max(0, pct)));
    }

    private void showClearDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear all offline data?")
                .setMessage("This permanently deletes all encrypted simulations and cached offline files.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    manager.clearAllOfflineData();
                    Toast.makeText(this, "All offline data removed", Toast.LENGTH_LONG).show();
                    refresh();
                })
                .show();
    }
}
