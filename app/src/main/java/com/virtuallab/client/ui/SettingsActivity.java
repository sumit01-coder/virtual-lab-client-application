package com.virtuallab.client.ui;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
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
import com.virtuallab.client.data.AppSettingsPrefs;
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
    private ProgressBar progressUpdate;
    private Button btnCheckUpdate;
    private Button btnDownloadUpdate;
    private Button btnViewRelease;
    private Button btnInstallUpdate;

    private GitHubReleaseInfo latestRelease;
    private long activeDownloadId = -1L;
    private boolean languageSelectionReady;
    private boolean downloadReceiverRegistered;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            long storedId = GitHubUpdateManager.getStoredDownloadId(SettingsActivity.this);
            if (downloadId == -1L || (downloadId != activeDownloadId && downloadId != storedId)) {
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
        refreshUpdateCard(false);
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
        progressUpdate = findViewById(R.id.progressUpdate);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        btnDownloadUpdate = findViewById(R.id.btnDownloadUpdate);
        btnViewRelease = findViewById(R.id.btnViewRelease);
        btnInstallUpdate = findViewById(R.id.btnInstallUpdate);
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
        txtCurrentVersion.setText("Current version: " + GitHubUpdateManager.getCurrentVersionName(this));
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
        syncDownloadButtons();
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
            txtUpdateStatus.setText("Update available. Version " + releaseInfo.version + " is ready to download.");
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
            txtUpdateStatus.setText("Downloading update through Android's download manager...");
            syncDownloadButtons();
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
            txtUpdateStatus.setText("Update downloaded. Tap install to update the app.");
            syncDownloadButtons();
            Toast.makeText(this, "Update ready to install", Toast.LENGTH_LONG).show();
        } else {
            activeDownloadId = -1L;
            txtUpdateStatus.setText("Update download failed: " + status.message);
            GitHubUpdateManager.clearStoredDownload(this);
            syncDownloadButtons();
            Toast.makeText(this, status.message, Toast.LENGTH_LONG).show();
        }
    }

    private void installDownloadedUpdate() {
        if (!GitHubUpdateManager.installDownloadedApk(this)) {
            txtUpdateStatus.setText("Allow installs from this app, then tap install again.");
        }
    }

    private void syncDownloadButtons() {
        boolean hasDownloadedUpdate = GitHubUpdateManager.hasDownloadedApk(this);
        btnInstallUpdate.setVisibility(hasDownloadedUpdate ? View.VISIBLE : View.GONE);
        if (hasDownloadedUpdate) {
            String version = GitHubUpdateManager.getDownloadedVersion(this);
            btnInstallUpdate.setText(version.isEmpty()
                    ? "Install downloaded update"
                    : ("Install downloaded update (" + version + ")"));
        }
    }

    private void setCheckingState(boolean checking) {
        progressUpdate.setVisibility(checking ? View.VISIBLE : View.GONE);
        btnCheckUpdate.setEnabled(!checking);
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

