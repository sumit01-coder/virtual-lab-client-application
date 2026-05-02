package com.virtuallab.client.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.offline.DownloadProgressListener;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.SimulationMeta;

public class DownloadSimulationActivity extends AppCompatActivity {

    private OfflineSimulationManager manager;
    private ProgressBar progressBar;
    private TextView txtPercent;
    private TextView txtSize;
    private TextView txtSpeed;
    private TextView txtEta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_simulation);

        try {
            manager = new OfflineSimulationManager(this);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot initialize secure download", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        progressBar = findViewById(R.id.downloadProgress);
        txtPercent = findViewById(R.id.txtDownloadPercent);
        txtSize = findViewById(R.id.txtDownloadSize);
        txtSpeed = findViewById(R.id.txtDownloadSpeed);
        txtEta = findViewById(R.id.txtDownloadEta);

        String labId = getIntent().getStringExtra("lab_id");
        String title = getIntent().getStringExtra("title");
        String version = getIntent().getStringExtra("version");
        String checksum = getIntent().getStringExtra("checksum");
        String zipUrl = getIntent().getStringExtra("zip_url");
        String mainFile = getIntent().getStringExtra("main_file");
        String thumb = getIntent().getStringExtra("thumbnail_url");
        String lastUpdated = getIntent().getStringExtra("last_updated");
        String subject = getIntent().getStringExtra("subject");
        String difficulty = getIntent().getStringExtra("difficulty");
        String duration = getIntent().getStringExtra("duration");
        String overview = getIntent().getStringExtra("overview");
        String objectives = getIntent().getStringExtra("objectives");
        String materials = getIntent().getStringExtra("materials");
        String procedure = getIntent().getStringExtra("procedure");

        ((TextView) findViewById(R.id.txtDownloadLabName)).setText(title);
        findViewById(R.id.btnCancelDownload).setOnClickListener(v -> {
            manager.cancelDownload();
            Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
            finish();
        });

        SimulationMeta meta = new SimulationMeta();
        meta.labId = labId;
        meta.title = title;
        meta.version = version;
        meta.subject = subject;
        meta.difficulty = difficulty;
        meta.duration = duration;
        meta.checksum = checksum;
        meta.mainFile = mainFile == null || mainFile.trim().isEmpty() ? "index.html" : mainFile;
        meta.thumbnailUrl = thumb;
        meta.lastUpdated = lastUpdated;
        meta.overview = overview;
        meta.objectives = objectives;
        meta.materials = materials;
        meta.procedure = procedure;

        Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
        manager.downloadSimulation(meta, zipUrl, new DownloadProgressListener() {
            @Override
            public void onStarted() {
                progressBar.setProgress(0);
            }

            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes, long bytesPerSecond, long etaSeconds) {
                progressBar.setProgress(percent);
                txtPercent.setText(percent + "%");
                txtSize.setText(OfflineSimulationManager.formatSize(downloadedBytes) + " / " + OfflineSimulationManager.formatSize(totalBytes));
                txtSpeed.setText(OfflineSimulationManager.formatSpeed(bytesPerSecond));
                txtEta.setText(OfflineSimulationManager.formatEta(etaSeconds));
            }

            @Override
            public void onCancelled() {
                finish();
            }

            @Override
            public void onCompleted(SimulationMeta simulationMeta) {
                Toast.makeText(DownloadSimulationActivity.this, "Offline ready", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(DownloadSimulationActivity.this, DownloadCompleteActivity.class);
                i.putExtra("lab_id", simulationMeta.labId);
                i.putExtra("title", simulationMeta.title);
                i.putExtra("version", simulationMeta.version);
                i.putExtra("file_size", simulationMeta.fileSize);
                i.putExtra("downloaded_at", simulationMeta.downloadedAt);
                i.putExtra("thumbnail_url", simulationMeta.thumbnailUrl);
                startActivity(i);
                finish();
            }

            @Override
            public void onError(String message, Throwable throwable) {
                String msg = message == null ? "" : message.trim();
                if (msg.contains("only HTTPS URLs are allowed")) {
                    msg = "Offline package URL is invalid from server. Please try another lab or contact admin.";
                }
                Toast.makeText(DownloadSimulationActivity.this, "Download failed: " + msg, Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
