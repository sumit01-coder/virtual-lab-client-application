package com.virtuallab.client.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.virtuallab.client.R;
import com.virtuallab.client.offline.OfflineSimulationManager;
import com.virtuallab.client.offline.SimulationMeta;
import com.virtuallab.client.security.SecurityPolicy;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OfflineWebViewActivity extends AppCompatActivity {

    private String labId;
    private File tempDir;
    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

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

        prepareOfflineSimulation();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        File cleanupDir = tempDir != null
                ? tempDir
                : labId != null ? new File(getCacheDir(), "temp_simulations" + File.separator + labId) : null;
        ioExecutor.execute(() -> deleteRecursive(cleanupDir));
        ioExecutor.shutdown();
        super.onDestroy();
    }

    private void prepareOfflineSimulation() {
        ioExecutor.execute(() -> {
            try {
                OfflineSimulationManager preparedManager = new OfflineSimulationManager(this);
                File preparedTempDir = preparedManager.decryptToTemp(labId);
                SimulationMeta meta = preparedManager.getStorageManager().getMeta(labId);
                String mainFile = meta != null && meta.mainFile != null && !meta.mainFile.trim().isEmpty()
                        ? meta.mainFile
                        : "index.html";

                File index = new File(preparedTempDir, mainFile);
                if (!index.exists()) {
                    index = new File(preparedTempDir, "index.html");
                }
                if (!index.exists()) {
                    throw new IllegalStateException("Main HTML file not found after extraction");
                }

                if (destroyed) {
                    deleteRecursive(preparedTempDir);
                    return;
                }

                File finalIndex = index;
                mainHandler.post(() -> {
                    if (destroyed) return;
                    tempDir = preparedTempDir;
                    loadPreparedFile(finalIndex);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (destroyed) return;
                    Toast.makeText(this, "Offline load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadPreparedFile(File index) {
        FrameLayout container = findViewById(R.id.offlineWebViewContainer);
        View loadingPanel = findViewById(R.id.offlineLoadingPanel);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        SecurityPolicy.applyOfflineWebViewPolicy(webView);

        webView.setWebViewClient(new WebViewClient());
        container.addView(webView);
        loadingPanel.setVisibility(View.GONE);
        webView.loadUrl("file://" + index.getAbsolutePath());
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
