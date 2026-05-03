package com.virtuallab.client.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.core.content.FileProvider;

import com.google.gson.Gson;
import com.virtuallab.client.BuildConfig;
import com.virtuallab.client.Config;
import com.virtuallab.client.security.SecurityPolicy;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class GitHubUpdateManager {
    private static final String UPDATE_PREF = "github_update_store";
    private static final String KEY_DOWNLOAD_ID = "download_id";
    private static final String KEY_DOWNLOAD_PATH = "download_path";
    private static final String KEY_DOWNLOAD_VERSION = "download_version";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .addInterceptor(SecurityPolicy.trustedEndpointInterceptor())
            .build();
    private static final Gson GSON = new Gson();

    private GitHubUpdateManager() {}

    public interface ReleaseCheckCallback {
        void onSuccess(GitHubReleaseInfo releaseInfo);
        void onError(String message);
    }

    public static final class DownloadStatus {
        public final boolean successful;
        public final boolean failed;
        public final boolean running;
        public final boolean paused;
        public final int progressPercent;
        public final long bytesDownloaded;
        public final long totalBytes;
        public final String message;

        public DownloadStatus(boolean successful, String message) {
            this(successful, false, false, false, -1, 0L, -1L, message);
        }

        public DownloadStatus(
                boolean successful,
                boolean failed,
                boolean running,
                boolean paused,
                int progressPercent,
                long bytesDownloaded,
                long totalBytes,
                String message
        ) {
            this.successful = successful;
            this.failed = failed;
            this.running = running;
            this.paused = paused;
            this.progressPercent = progressPercent;
            this.bytesDownloaded = Math.max(0L, bytesDownloaded);
            this.totalBytes = totalBytes;
            this.message = message == null || message.trim().isEmpty() ? "Unknown status" : message.trim();
        }

        public String getByteProgressText() {
            if (totalBytes > 0L) {
                return formatBytes(bytesDownloaded) + " / " + formatBytes(totalBytes);
            }
            if (bytesDownloaded > 0L) {
                return formatBytes(bytesDownloaded) + " downloaded";
            }
            return "";
        }
    }

    public static void fetchLatestRelease(ReleaseCheckCallback callback) {
        Request request = new Request.Builder()
                .url(Config.GITHUB_LATEST_RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "VirtualLabClient")
                .build();

        HTTP.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Could not reach GitHub releases: " + safe(e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful()) {
                        if (closeable.code() == 404) {
                            callback.onError("GitHub repository or release not found yet for " + Config.GITHUB_REPOSITORY_FULL_NAME + ".");
                            return;
                        }
                        if (closeable.code() == 403) {
                            callback.onError("GitHub rate limit reached. Please try again in a little while.");
                            return;
                        }
                        callback.onError("GitHub update check failed with HTTP " + closeable.code() + ".");
                        return;
                    }

                    String body = closeable.body() != null ? closeable.body().string() : "";
                    GitHubReleaseResponse parsed = GSON.fromJson(body, GitHubReleaseResponse.class);
                    if (parsed == null) {
                        callback.onError("GitHub returned an empty release response.");
                        return;
                    }

                    ReleaseAsset apkAsset = null;
                    if (parsed.assets != null) {
                        for (ReleaseAsset asset : parsed.assets) {
                            if (asset == null || asset.browser_download_url == null) {
                                continue;
                            }
                            String name = asset.name == null ? "" : asset.name.toLowerCase(Locale.US);
                            String downloadUrl = asset.browser_download_url.toLowerCase(Locale.US);
                            if (name.endsWith(".apk") || downloadUrl.endsWith(".apk")) {
                                apkAsset = asset;
                                break;
                            }
                        }
                    }

                    String version = firstNonEmpty(parsed.tag_name, parsed.name, "unknown");
                    String notes = firstNonEmpty(parsed.body, "");
                    String htmlUrl = firstNonEmpty(parsed.html_url, Config.GITHUB_RELEASES_URL);
                    String publishedAt = firstNonEmpty(parsed.published_at, "");
                    String apkName = apkAsset != null ? firstNonEmpty(apkAsset.name, "update.apk") : "";
                    String apkDownloadUrl = apkAsset != null ? firstNonEmpty(apkAsset.browser_download_url, "") : "";

                    callback.onSuccess(new GitHubReleaseInfo(
                            version,
                            notes,
                            htmlUrl,
                            publishedAt,
                            apkName,
                            apkDownloadUrl
                    ));
                } catch (Exception e) {
                    callback.onError("Could not read GitHub release details: " + safe(e.getMessage()));
                }
            }
        });
    }

    public static long enqueueDownload(Context context, GitHubReleaseInfo releaseInfo) {
        if (releaseInfo == null || !releaseInfo.hasApkAsset()) {
            throw new IllegalStateException("No APK asset available for download.");
        }

        Context appContext = context.getApplicationContext();
        File downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir == null) {
            throw new IllegalStateException("App downloads folder is not available.");
        }
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw new IllegalStateException("Could not create app downloads folder.");
        }

        clearStoredDownload(appContext);

        File destination = new File(
                downloadsDir,
                "virtual-lab-client-" + sanitizeFilePart(releaseInfo.version) + ".apk"
        );
        if (destination.exists() && !destination.delete()) {
            throw new IllegalStateException("Could not replace the previous downloaded APK.");
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(releaseInfo.apkDownloadUrl))
                .setTitle("Virtual Lab update")
                .setDescription("Downloading version " + releaseInfo.version)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setMimeType(APK_MIME);
        request.setDestinationUri(Uri.fromFile(destination));

        DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            throw new IllegalStateException("Android download manager is unavailable.");
        }

        long downloadId = downloadManager.enqueue(request);
        prefs(appContext).edit()
                .putLong(KEY_DOWNLOAD_ID, downloadId)
                .putString(KEY_DOWNLOAD_PATH, destination.getAbsolutePath())
                .putString(KEY_DOWNLOAD_VERSION, releaseInfo.version)
                .apply();
        return downloadId;
    }

    public static DownloadStatus getDownloadStatus(Context context, long downloadId) {
        DownloadManager downloadManager = (DownloadManager) context.getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return new DownloadStatus(false, "Android download manager is unavailable.");
        }

        Cursor cursor = null;
        try {
            cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cursor == null || !cursor.moveToFirst()) {
                return new DownloadStatus(false, true, false, false, -1, 0L, -1L, "Update download could not be found.");
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int percent = total > 0L ? (int) Math.min(100L, Math.max(0L, downloaded * 100L / total)) : -1;
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                return new DownloadStatus(true, false, false, false, 100, downloaded, total, "Update downloaded successfully.");
            }
            if (status == DownloadManager.STATUS_FAILED) {
                return new DownloadStatus(false, true, false, false, percent, downloaded, total, decodeFailureReason(reason));
            }
            if (status == DownloadManager.STATUS_PAUSED) {
                return new DownloadStatus(false, false, false, true, percent, downloaded, total, "Update download is paused.");
            }
            return new DownloadStatus(false, false, true, false, percent, downloaded, total, "Update download is still in progress.");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static boolean installDownloadedApk(Context context) {
        Context appContext = context.getApplicationContext();
        File apkFile = getDownloadedApkFile(appContext);
        if (apkFile == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !appContext.getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + appContext.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(settingsIntent);
            throw new SecurityException("Install permission is not enabled for this app.");
        }

        Uri apkUri = FileProvider.getUriForFile(
                appContext,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                apkFile
        );
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }

    public static boolean hasDownloadedApk(Context context) {
        return getDownloadedApkFile(context) != null;
    }

    public static File getDownloadedApkFile(Context context) {
        String path = prefs(context.getApplicationContext()).getString(KEY_DOWNLOAD_PATH, "");
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path);
        return file.exists() ? file : null;
    }

    public static String getDownloadedVersion(Context context) {
        return firstNonEmpty(
                prefs(context.getApplicationContext()).getString(KEY_DOWNLOAD_VERSION, ""),
                ""
        );
    }

    public static long getStoredDownloadId(Context context) {
        return prefs(context.getApplicationContext()).getLong(KEY_DOWNLOAD_ID, -1L);
    }

    public static boolean cancelDownload(Context context, long downloadId) {
        Context appContext = context.getApplicationContext();
        DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager != null && downloadId > 0L) {
            downloadManager.remove(downloadId);
        }
        clearStoredDownload(appContext);
        return true;
    }

    public static void clearStoredDownload(Context context) {
        Context appContext = context.getApplicationContext();
        File file = getDownloadedApkFile(appContext);
        if (file != null) {
            // Best effort cleanup for stale update packages.
            file.delete();
        }
        prefs(appContext).edit()
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_PATH)
                .remove(KEY_DOWNLOAD_VERSION)
                .apply();
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            return firstNonEmpty(packageInfo.versionName, "1.0.0");
        } catch (Exception ignored) {
            return "1.0.0";
        }
    }

    public static long getCurrentVersionCode(Context context) {
        try {
            PackageManager packageManager = context.getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return packageInfo.getLongVersionCode();
            }
            return packageInfo.versionCode;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static String getCurrentVersionDisplay(Context context) {
        long versionCode = getCurrentVersionCode(context);
        String versionName = getCurrentVersionName(context);
        if (versionCode > 0) {
            return versionName + " (" + versionCode + ")";
        }
        return versionName;
    }

    public static String getRepositoryFullName() {
        return Config.GITHUB_REPOSITORY_FULL_NAME;
    }

    public static String getReleasesPageUrl() {
        return Config.GITHUB_RELEASES_URL;
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(UPDATE_PREF, Context.MODE_PRIVATE);
    }

    private static String decodeFailureReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "Android could not resume the APK download.";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "No storage device is available for the update APK.";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "The update file already exists.";
            case DownloadManager.ERROR_FILE_ERROR:
                return "Android could not write the update APK.";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "GitHub returned an invalid APK response.";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "Not enough space is available for the update APK.";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "GitHub redirected the APK download too many times.";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "GitHub returned an unexpected response code.";
            case DownloadManager.ERROR_UNKNOWN:
            default:
                return "Android could not complete the APK download.";
        }
    }

    private static String sanitizeFilePart(String value) {
        return firstNonEmpty(value, "latest").replaceAll("[^a-zA-Z0-9._-]", "-");
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unknown error" : value.trim();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB"};
        int unitIndex = -1;
        do {
            value = value / 1024D;
            unitIndex++;
        } while (value >= 1024D && unitIndex < units.length - 1);
        return String.format(Locale.US, value >= 100D ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class GitHubReleaseResponse {
        String tag_name;
        String name;
        String body;
        String html_url;
        String published_at;
        ReleaseAsset[] assets;
    }

    private static final class ReleaseAsset {
        String name;
        String browser_download_url;
    }
}
