package com.virtuallab.client.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DownloadCompleteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_download_complete);

        String labId = getIntent().getStringExtra("lab_id");
        String title = getIntent().getStringExtra("title");
        String version = getIntent().getStringExtra("version");
        long fileSize = getIntent().getLongExtra("file_size", 0L);
        long downloadedAt = getIntent().getLongExtra("downloaded_at", System.currentTimeMillis());
        String thumb = getIntent().getStringExtra("thumbnail_url");

        ((TextView) findViewById(R.id.txtCompleteTitle)).setText(title);
        ((TextView) findViewById(R.id.txtCompleteVersion)).setText("Version " + version);
        ((TextView) findViewById(R.id.txtCompleteSize)).setText(OfflineSimulationManager.formatSize(fileSize));

        String formattedTime = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(downloadedAt));
        ((TextView) findViewById(R.id.txtCompleteDate)).setText(formattedTime);

        if (thumb != null && !thumb.trim().isEmpty()) {
            Glide.with(this).load(thumb).into((ImageView) findViewById(R.id.imgCompleteThumb));
        }

        findViewById(R.id.btnOpenOfflineNow).setOnClickListener(v -> {
            try {
                new OfflineSimulationManager(this).openOfflineSimulation(this, labId);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open offline file", Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.btnGoOfflineLabs).setOnClickListener(v ->
                startActivity(new Intent(this, OfflineLabsActivity.class)));
    }
}
