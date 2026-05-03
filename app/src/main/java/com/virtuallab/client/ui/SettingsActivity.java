package com.virtuallab.client.ui;

import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.virtuallab.client.R;
import com.virtuallab.client.Config;
import com.virtuallab.client.data.AppSettingsPrefs;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.update.GitHubReleaseInfo;
import com.virtuallab.client.update.GitHubUpdateManager;

import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private static final List<String> LANGUAGE_LABELS = Arrays.asList("English", "Hindi", "Spanish");
    private static final List<String> LANGUAGE_TAGS = Arrays.asList("en", "hi", "es");

    private SwitchCompat switchDark;
    private SwitchCompat switchNotifications;
    private Spinner spinnerLanguage;
    private TextView txtCurrentVersion;
    private TextView txtLatestVersion;
    private TextView txtUpdateStatus;
    private TextView txtReleaseNotes;
    private TextView txtUpdateRepository;
    private TextView txtSecurityLevel;
    private TextView txtSecuritySummary;
    private TextView txtSecurityEncryption;
    private TextView txtSecurityDeviceLock;
    private TextView txtSecurityBackup;
    private TextView txtSecurityNetwork;
    private ProgressBar progressUpdate;
    private Button btnCheckUpdate;
    private Button btnDownloadUpdate;
    private Button btnViewRelease;
    private Button btnInstallUpdate;
    private Button btnCancelUpdate;

    private GitHubReleaseInfo latestRelease;
    private long activeDownloadId = -1L;
    private boolean languageSelectionReady;
    private boolean downloadReceiverRegistered;
    private final Handler downloadProgressHandler = new Handler(Looper.getMainLooper());
    private final Runnable downloadProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (activeDownloadId <= 0L) {
                return;
            }
            GitHubUpdateManager.DownloadStatus status = GitHubUpdateManager.getDownloadStatus(SettingsActivity.this, activeDownloadId);
            bindDownloadProgress(status);
            if (status.successful) {
                handleDownloadCompleted(activeDownloadId);
                return;
            }
            if (status.failed) {
                handleDownloadFailed(status.message);
                return;
            }
            downloadProgressHandler.postDelayed(this, 700L);
        }
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (downloadId == -1L || downloadId != activeDownloadId) {
                return;
            }
            handleDownloadCompleted(downloadId);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        bindViews();
        setupPreferences();
        setupUpdateSection();
        updateSecurityCard();
        refreshUpdateCard(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (txtCurrentVersion != null) {
            bindCurrentVersion();
            syncDownloadButtons();
            updateSecurityCard();
            resumeStoredDownloadProgress();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!downloadReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            downloadReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (downloadReceiverRegistered) {
            unregisterReceiver(downloadReceiver);
            downloadReceiverRegistered = false;
        }
        downloadProgressHandler.removeCallbacks(downloadProgressRunnable);
    }

    private void bindViews() {
        switchDark = findViewById(R.id.switchDark);
        switchNotifications = findViewById(R.id.switchNotifications);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        txtCurrentVersion = findViewById(R.id.txtCurrentVersion);
        txtLatestVersion = findViewById(R.id.txtLatestVersion);
        txtUpdateStatus = findViewById(R.id.txtUpdateStatus);
        txtReleaseNotes = findViewById(R.id.txtReleaseNotes);
        txtUpdateRepository = findViewById(R.id.txtUpdateRepository);
        txtSecurityLevel = findViewById(R.id.txtSecurityLevel);
        txtSecuritySummary = findViewById(R.id.txtSecuritySummary);
        txtSecurityEncryption = findViewById(R.id.txtSecurityEncryption);
        txtSecurityDeviceLock = findViewById(R.id.txtSecurityDeviceLock);
        txtSecurityBackup = findViewById(R.id.txtSecurityBackup);
        txtSecurityNetwork = findViewById(R.id.txtSecurityNetwork);
        progressUpdate = findViewById(R.id.progressUpdate);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate);
        btnViewRelease = findViewById(R.id.btnViewRelease);
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate);
        btnCancelUpdate = findViewById(R.id.btnCancelUpdate);
    }

    private void setupPreferences() {
        switchDark.setChecked(AppSettingsPrefs.isDarkModeEnabled(this));
        switchNotifications.setChecked(AppSettingsPrefs.areNotificationsEnabled(this));
        switchDark.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettingsPrefs.setDarkModeEnabled(this, isChecked);
            AppSettingsPrefs.applyDarkMode(this);
        });
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) ->
                AppSettingsPrefs.setNotificationsEnabled(this, isChecked));

        spinnerLanguage.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                LANGUAGE_LABELS
        ));

        int selectedIndex = LANGUAGE_TAGS.indexOf(AppSettingsPrefs.getLanguageTag(this));
        spinnerLanguage.setSelection(selectedIndex >= 0 ? selectedIndex : 0, false);
        languageSelectionReady = true;
        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!languageSelectionReady || position < 0 || position >= LANGUAGE_TAGS.size()) {
                    return;
                }
                String nextLanguage = LANGUAGE_TAGS.get(position);
                if (nextLanguage.equals(AppSettingsPrefs.getLanguageTag(SettingsActivity.this))) {
                    return;
                }
                AppSettingsPrefs.setLanguageTag(SettingsActivity.this, nextLanguage);
                AppSettingsPrefs.applyLanguage(SettingsActivity.this);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupUpdateSection() {
        bindCurrentVersion();
        txtUpdateRepository.setText("GitHub releases: " + GitHubUpdateManager.getRepositoryFullName());
        txtUpdateRepository.setOnClickListener(v -> openUrl(GitHubUpdateManager.getReleasesPageUrl()));

        activeDownloadId = GitHubUpdateManager.getStoredDownloadId(this);
        btnCheckUpdate.setOnClickListener(v -> refreshUpdateCard(true));
        btnDownloadUpdate.setOnClickListener(v -> startUpdateDownload());
        btnViewRelease.setOnClickListener(v -> {
            String target = latestRelease != null && latestRelease.htmlUrl != null && !latestRelease.htmlUrl.isEmpty()
                    ? latestRelease.htmlUrl
                    : GitHubUpdateManager.getReleasesPageUrl();
            openUrl(target);
        });
        btnInstallUpdate.setOnClickListener(v -> installDownloadedUpdate());
        btnCancelUpdate.setOnClickListener(v -> cancelUpdateDownload());
        syncDownloadButtons();
        resumeStoredDownloadProgress();
    }

    private void updateSecurityCard() {
        int score = 0;

        boolean encryptedSession = SessionStore.isSecureStorageAvailable(this);
        if (encryptedSession) score += 30;

        boolean deviceSecure = isDeviceSecure();
        if (deviceSecure) score += 20;

        boolean backupDisabled = isBackupDisabled();
        if (backupDisabled) score += 20;

        boolean cleartextBlocked = isCleartextBlocked();
        boolean httpsApi = Config.BASE_URL != null && Config.BASE_URL.startsWith("https://");
        if (cleartextBlocked && httpsApi) score += 20;

        boolean debugOff = !isDebuggable();
        if (debugOff) score += 10;

        String level;
        if (score >= 90) {
            level = "Strong";
        } else if (score >= 70) {
            level = "Good";
        } else if (score >= 45) {
            level = "Basic";
        } else {
            level = "Weak";
        }

        txtSecurityLevel.setText(level);
        txtSecuritySummary.setText("Security score " + score + "/100. App data, updates, and network settings are checked locally.");
        setSecurityMetric(
                txtSecurityEncryption,
                encryptedSession,
                encryptedSession
                        ? "Encryption: session tokens use Android Keystore AES encryption."
                        : "Encryption: secure keystore unavailable; using fallback app-private storage."
        );
        setSecurityMetric(
                txtSecurityDeviceLock,
                deviceSecure,
                deviceSecure
                        ? "Device lock: enabled. Encrypted keys are better protected."
                        : "Device lock: not enabled. Add PIN, pattern, or biometrics for stronger protection."
        );
        setSecurityMetric(
                txtSecurityBackup,
                backupDisabled,
                backupDisabled
                        ? "Private backup: disabled for sensitive app data."
                        : "Private backup: enabled. Sensitive data may be included in device backups."
        );
        setSecurityMetric(
                txtSecurityNetwork,
                cleartextBlocked && httpsApi && debugOff,
                "Network security: HTTPS API " + (httpsApi ? "enabled" : "missing")
                        + ", cleartext " + (cleartextBlocked ? "blocked" : "allowed")
                        + ", debug " + (debugOff ? "off" : "on") + "."
        );
    }

    private void setSecurityMetric(TextView view, boolean ok, String text) {
        view.setText((ok ? "OK: " : "Check: ") + text);
        view.setBackgroundResource(ok ? R.drawable.bg_security_metric : R.drawable.bg_security_metric_warn);
        view.setTextColor(getColor(ok ? R.color.vl_text : R.color.vl_warning));
    }

    private boolean isDeviceSecure() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isDeviceSecure();
    }

    private boolean isBackupDisabled() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_ALLOW_BACKUP) == 0;
    }

    private boolean isCleartextBlocked() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) == 0;
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private void bindCurrentVersion() {
        txtCurrentVersion.setText("Current version: " + GitHubUpdateManager.getCurrentVersionDisplay(this));
    }

    private void refreshUpdateCard(boolean manualRefresh) {
        setCheckingState(true);
        txtUpdateStatus.setText(manualRefresh
                ? "Checking GitHub releases..."
                : "Looking for the latest app release...");

        GitHubUpdateManager.fetchLatestRelease(new GitHubUpdateManager.ReleaseCheckCallback() {
            @Override
            public void onSuccess(GitHubReleaseInfo releaseInfo) {
                runOnUiThread(() -> {
                    latestRelease = releaseInfo;
                    setCheckingState(false);
                    bindReleaseInfo(releaseInfo);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    latestRelease = null;
                    setCheckingState(false);
                    txtLatestVersion.setText("Latest release: unavailable");
                    txtUpdateStatus.setText(message);
                    txtReleaseNotes.setText("Create the GitHub repository and publish a release APK to enable in-app updates.");
                    btnDownloadUpdate.setVisibility(View.GONE);
                    btnViewRelease.setVisibility(View.VISIBLE);
                    syncDownloadButtons();
                });
            }
        });
    }

    private void bindReleaseInfo(GitHubReleaseInfo releaseInfo) {
        txtLatestVersion.setText("Latest release: " + releaseInfo.version);
        txtReleaseNotes.setText(releaseInfo.getDisplayNotes());
        btnViewRelease.setVisibility(View.VISIBLE);

        if (!releaseInfo.hasApkAsset()) {
            txtUpdateStatus.setText("A GitHub release exists, but it does not include an APK asset yet.");
            btnDownloadUpdate.setVisibility(View.GONE);
            syncDownloadButtons();
            return;
        }

        if (releaseInfo.isNewerThan(GitHubUpdateManager.getCurrentVersionName(this))) {
            txtUpdateStatus.setText("Update available. Download the latest APK without leaving the app.");
            btnDownloadUpdate.setVisibility(View.VISIBLE);
        } else {
            txtUpdateStatus.setText("This app is already on the latest published version.");
            btnDownloadUpdate.setVisibility(View.GONE);
        }
        syncDownloadButtons();
    }

    private void startUpdateDownload() {
        if (latestRelease == null || !latestRelease.hasApkAsset()) {
            Toast.makeText(this, "No APK release is available yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activeDownloadId = GitHubUpdateManager.enqueueDownload(this, latestRelease);
            progressUpdate.setIndeterminate(true);
            progressUpdate.setVisibility(View.VISIBLE);
            txtUpdateStatus.setText("Starting update download...");
            syncDownloadButtons();
            startDownloadProgressPolling();
            Toast.makeText(this, "Update download started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            txtUpdateStatus.setText("Could not start update download: " + safeMessage(e.getMessage()));
            Toast.makeText(this, "Download failed to start", Toast.LENGTH_LONG).show();
        }
    }

    private void handleDownloadCompleted(long downloadId) {
        GitHubUpdateManager.DownloadStatus status = GitHubUpdateManager.getDownloadStatus(this, downloadId);
        if (status.successful) {
            activeDownloadId = -1L;
            downloadProgressHandler.removeCallbacks(downloadProgressRunnable);
            txtUpdateStatus.setText("Update downloaded. Opening installer...");
            syncDownloadButtons();
            Toast.makeText(this, "Update ready to install", Toast.LENGTH_LONG).show();
            installDownloadedUpdate();
        } else {
            handleDownloadFailed(status.message);
        }
    }

    private void installDownloadedUpdate() {
        try {
            if (!GitHubUpdateManager.installDownloadedApk(this)) {
                txtUpdateStatus.setText("Downloaded update is missing. Download the latest APK again.");
            }
        } catch (ActivityNotFoundException e) {
            txtUpdateStatus.setText("No installer is available on this device.");
        } catch (SecurityException e) {
            txtUpdateStatus.setText("Allow installs from this app, then tap install again.");
        }
    }

    private void syncDownloadButtons() {
        boolean hasDownloadedApk = GitHubUpdateManager.hasDownloadedApk(this);
        boolean hasActiveDownload = activeDownloadId > 0L;
        btnCancelUpdate.setVisibility(hasActiveDownload ? View.VISIBLE : View.GONE);
        btnInstallUpdate.setVisibility(hasDownloadedApk && !hasActiveDownload ? View.VISIBLE : View.GONE);
        btnDownloadUpdate.setEnabled(!hasActiveDownload);
        btnCheckUpdate.setEnabled(!hasActiveDownload);
        if (hasActiveDownload) {
            btnDownloadUpdate.setVisibility(View.GONE);
        }
    }

    private void setCheckingState(boolean checking) {
        progressUpdate.setIndeterminate(checking);
        progressUpdate.setVisibility(checking ? View.VISIBLE : View.GONE);
        btnCheckUpdate.setEnabled(!checking);
    }

    private void startDownloadProgressPolling() {
        downloadProgressHandler.removeCallbacks(downloadProgressRunnable);
        downloadProgressHandler.post(downloadProgressRunnable);
    }

    private void resumeStoredDownloadProgress() {
        long storedId = GitHubUpdateManager.getStoredDownloadId(this);
        if (storedId <= 0L) {
            return;
        }
        GitHubUpdateManager.DownloadStatus status = GitHubUpdateManager.getDownloadStatus(this, storedId);
        if (status.running || status.paused) {
            activeDownloadId = storedId;
            bindDownloadProgress(status);
            syncDownloadButtons();
            startDownloadProgressPolling();
        } else if (status.successful) {
            activeDownloadId = -1L;
            txtUpdateStatus.setText("Update downloaded. Tap install to update the app.");
            syncDownloadButtons();
        } else if (status.failed) {
            handleDownloadFailed(status.message);
        }
    }

    private void bindDownloadProgress(GitHubUpdateManager.DownloadStatus status) {
        progressUpdate.setVisibility(View.VISIBLE);
        progressUpdate.setIndeterminate(status.progressPercent < 0);
        if (status.progressPercent >= 0) {
            progressUpdate.setProgress(status.progressPercent);
        }
        String bytes = status.getByteProgressText();
        String percent = status.progressPercent >= 0 ? status.progressPercent + "%" : "Preparing";
        txtUpdateStatus.setText(bytes.isEmpty()
                ? "Downloading update: " + percent
                : "Downloading update: " + percent + " (" + bytes + ")");
        syncDownloadButtons();
    }

    private void cancelUpdateDownload() {
        if (activeDownloadId <= 0L) {
            return;
        }
        downloadProgressHandler.removeCallbacks(downloadProgressRunnable);
        GitHubUpdateManager.cancelDownload(this, activeDownloadId);
        activeDownloadId = -1L;
        progressUpdate.setVisibility(View.GONE);
        txtUpdateStatus.setText("Update download canceled. You can start it again anytime.");
        syncDownloadButtons();
        if (latestRelease != null && latestRelease.hasApkAsset()
                && latestRelease.isNewerThan(GitHubUpdateManager.getCurrentVersionName(this))) {
            btnDownloadUpdate.setVisibility(View.VISIBLE);
        }
    }

    private void handleDownloadFailed(String message) {
        activeDownloadId = -1L;
        downloadProgressHandler.removeCallbacks(downloadProgressRunnable);
        progressUpdate.setVisibility(View.GONE);
        txtUpdateStatus.setText("Update download failed: " + safeMessage(message));
        GitHubUpdateManager.clearStoredDownload(this);
        syncDownloadButtons();
        if (latestRelease != null && latestRelease.hasApkAsset()
                && latestRelease.isNewerThan(GitHubUpdateManager.getCurrentVersionName(this))) {
            btnDownloadUpdate.setVisibility(View.VISIBLE);
        }
        Toast.makeText(this, safeMessage(message), Toast.LENGTH_LONG).show();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser available to open the release page.", Toast.LENGTH_LONG).show();
        }
    }

    private String safeMessage(String message) {
        return message == null || message.trim().isEmpty() ? "Unknown error" : message.trim();
    }
}

