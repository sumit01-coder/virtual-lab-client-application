package com.virtuallab.client.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.OfflineSyncManager;
import com.virtuallab.client.offline.SimulationMeta;

import java.util.ArrayList;
import java.util.List;

public class OfflineLabsActivity extends AppCompatActivity implements OfflineLabAdapter.ActionListener {

    private static final long APP_STORAGE_LIMIT = 1024L * 1024L * 1024L;
    private static final long MIN_SYNC_INTERVAL_MS = 2L * 60L * 1000L;

    private OfflineSimulationManager manager;
    private OfflineSyncManager syncManager;
    private TextView txtStorageUsed;
    private TextView txtDownloadedCount;
    private TextView txtStoragePercent;
    private ProgressBar storageProgress;
    private RecyclerView recycler;
    private TextView txtEmpty;
    private OfflineLabAdapter adapter;
    private long lastSyncAttemptAt = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_labs);

        try {
            manager = new OfflineSimulationManager(this);
            syncManager = new OfflineSyncManager(this);
        } catch (Exception e) {
            Toast.makeText(this, "Security setup failed", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        txtStorageUsed = findViewById(R.id.txtStorageUsed);
        txtDownloadedCount = findViewById(R.id.txtDownloadedCount);
        txtStoragePercent = findViewById(R.id.txtStoragePercent);
        storageProgress = findViewById(R.id.storageProgress);
        recycler = findViewById(R.id.recyclerOfflineLabs);
        txtEmpty = findViewById(R.id.txtEmptyOffline);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OfflineLabAdapter(new ArrayList<>(), this);
        recycler.setAdapter(adapter);

        findViewById(R.id.btnStorageDetails).setOnClickListener(v ->
                startActivity(new Intent(this, StorageDetailsActivity.class)));

        setupBottomNav();
    }

    @Override
    protected void onResume() {
        super.onResume();
        long now = System.currentTimeMillis();
        if (syncManager != null && (now - lastSyncAttemptAt) >= MIN_SYNC_INTERVAL_MS) {
            lastSyncAttemptAt = now;
            syncManager.syncDownloadedSimulations(false, message ->
                    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()));
        }
        refresh();
    }

    private void refresh() {
        List<SimulationMeta> list = manager.getStorageManager().getAllMetas();
        adapter.update(list);
        txtEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        txtDownloadedCount.setText(list.size() + (list.size() == 1 ? " lab available offline" : " labs available offline"));

        long used = manager.calculateStorageUsed();
        txtStorageUsed.setText(OfflineSimulationManager.formatSize(used) + " / " + OfflineSimulationManager.formatSize(APP_STORAGE_LIMIT));
        int pct = (int) ((used * 100L) / APP_STORAGE_LIMIT);
        storageProgress.setProgress(Math.min(100, Math.max(0, pct)));
        txtStoragePercent.setText(Math.min(100, Math.max(0, pct)) + "%");
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setSelectedItemId(R.id.nav_offline);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                return true;
            } else if (id == R.id.nav_labs) {
                startActivity(new Intent(this, MainActivity.class).putExtra("tab", "labs"));
                return true;
            } else if (id == R.id.nav_progress) {
                startActivity(new Intent(this, MainActivity.class).putExtra("tab", "progress"));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, MainActivity.class).putExtra("tab", "profile"));
                return true;
            }
            return true;
        });
    }

    @Override
    public void onOpenOffline(@NonNull SimulationMeta meta) {
        manager.openOfflineSimulation(this, meta.labId);
    }

    @Override
    public void onUpdate(@NonNull SimulationMeta meta) {
        Toast.makeText(this, "Update available. Open lab details to download latest version.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDelete(@NonNull SimulationMeta meta) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Offline Simulation")
                .setMessage("This removes encrypted files for " + meta.title + ".")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    manager.deleteOfflineSimulation(meta.labId);
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}
