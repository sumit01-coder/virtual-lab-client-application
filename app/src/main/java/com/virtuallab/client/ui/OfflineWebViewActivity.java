package com.virtuallab.client.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.SimulationMeta;

import java.io.File;

public class OfflineWebViewActivity extends AppCompatActivity {

    private OfflineSimulationManager manager;
    private String labId;
    private File tempDir;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_webview);

        labId = getIntent().getStringExtra("lab_id");
        if (labId == null || labId.trim().isEmpty()) {
            Toast.makeText(this, "Invalid offline lab", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        try {
            manager = new OfflineSimulationManager(this);
            tempDir = manager.decryptToTemp(labId);
            SimulationMeta meta = manager.getStorageManager().getMeta(labId);
            String mainFile = meta != null ? meta.mainFile : "index.html";

            File index = new File(tempDir, mainFile);
            if (!index.exists()) {
                index = new File(tempDir, "index.html");
            }
            if (!index.exists()) {
                throw new IllegalStateException("Main HTML file not found after extraction");
            }

            WebView webView = findViewById(R.id.offlineWebView);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMediaPlaybackRequiresUserGesture(false);

            webView.setWebViewClient(new WebViewClient());
            webView.loadUrl("file://" + index.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, "Offline load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null && labId != null) {
            File target = new File(getCacheDir(), "temp_simulations" + File.separator + labId);
            deleteRecursive(target);
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        // noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
