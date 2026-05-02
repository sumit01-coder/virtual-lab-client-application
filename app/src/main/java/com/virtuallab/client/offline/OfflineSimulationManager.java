package com.virtuallab.client.offline;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;

import com.virtuallab.client.ui.OfflineSimulationDetailsActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OfflineSimulationManager {

    private static final String SECURE_DIR = "secure_simulations";
    private static final String TEMP_DIR = "temp_simulations";

    private final Context appContext;
    private final SecureStorageManager storageManager;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private volatile Call activeCall;

    public OfflineSimulationManager(@NonNull Context context) throws Exception {
        this.appContext = context.getApplicationContext();
        this.storageManager = new SecureStorageManager(appContext);
        this.httpClient = new OkHttpClient.Builder().build();
    }

    public void downloadSimulation(@NonNull SimulationMeta requestMeta,
                                   @NonNull String zipUrl,
                                   @NonNull DownloadProgressListener listener) {
        String normalizedZipUrl = normalizeHttpsUrl(zipUrl);
        if (normalizedZipUrl == null) {
            listener.onError("Download blocked: only HTTPS URLs are allowed", null);
            return;
        }

        postMain(listener::onStarted);

        ioExecutor.execute(() -> {
            File tempZip = new File(appContext.getCacheDir(), "download_" + requestMeta.labId + ".zip");
            try {
                Request request = new Request.Builder().url(normalizedZipUrl).build();
                activeCall = httpClient.newCall(request);
                Response response = activeCall.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IllegalStateException("Failed to download file. HTTP " + response.code());
                }

                long total = response.body().contentLength();
                long start = System.currentTimeMillis();
                long downloaded = 0;
                byte[] buffer = new byte[8192];

                try (InputStream in = response.body().byteStream();
                     OutputStream out = new FileOutputStream(tempZip)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;

                        long elapsedMs = Math.max(1, System.currentTimeMillis() - start);
                        long bytesPerSec = (downloaded * 1000L) / elapsedMs;
                        long remaining = Math.max(0L, total - downloaded);
                        long eta = bytesPerSec > 0 ? remaining / bytesPerSec : -1L;
                        int percent = total > 0 ? (int) ((downloaded * 100L) / total) : 0;

                        final long d = downloaded;
                        final long t = total;
                        final long bps = bytesPerSec;
                        final long e = eta;
                        final int p = Math.min(100, Math.max(0, percent));
                        postMain(() -> listener.onProgress(p, d, t, bps, e));
                    }
                }

                if (!TextUtils.isEmpty(requestMeta.checksum)) {
                    String localChecksum = sha256(tempZip);
                    if (!requestMeta.checksum.equalsIgnoreCase(localChecksum)) {
                        throw new IllegalStateException("Checksum mismatch. Download discarded.");
                    }
                }

                requestMeta.fileSize = tempZip.length();
                requestMeta.downloadedAt = System.currentTimeMillis();
                encryptAndSave(requestMeta, tempZip);
                // noinspection ResultOfMethodCallIgnored
                tempZip.delete();
                postMain(() -> listener.onCompleted(requestMeta));
            } catch (Exception ex) {
                // noinspection ResultOfMethodCallIgnored
                tempZip.delete();
                postMain(() -> {
                    if (activeCall != null && activeCall.isCanceled()) {
                        listener.onCancelled();
                    } else {
                        listener.onError(ex.getMessage(), ex);
                    }
                });
            } finally {
                activeCall = null;
            }
        });
    }

    public void cancelDownload() {
        if (activeCall != null) {
            activeCall.cancel();
        }
    }

    public void encryptAndSave(@NonNull SimulationMeta meta, @NonNull File plainZip) throws Exception {
        File secureDir = new File(appContext.getFilesDir(), SECURE_DIR);
        if (!secureDir.exists() && !secureDir.mkdirs()) {
            throw new IllegalStateException("Cannot create secure storage");
        }

        File encryptedTarget = new File(secureDir, "sim_" + meta.labId + "_" + meta.version.replace('.', '_') + ".bin");

        MasterKey key = new MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                appContext,
                encryptedTarget,
                key,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        try (InputStream in = new FileInputStream(plainZip);
             OutputStream out = encryptedFile.openFileOutput()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        meta.encryptedFilePath = encryptedTarget.getAbsolutePath();
        storageManager.saveMeta(meta);
    }

    public File decryptToTemp(@NonNull String labId) throws Exception {
        SimulationMeta meta = storageManager.getMeta(labId);
        if (meta == null || TextUtils.isEmpty(meta.encryptedFilePath)) {
            throw new IllegalStateException("Offline simulation not found");
        }

        File encrypted = new File(meta.encryptedFilePath);
        if (!encrypted.exists()) throw new IllegalStateException("Encrypted file missing");

        File targetDir = new File(appContext.getCacheDir(), TEMP_DIR + File.separator + labId);
        deleteRecursively(targetDir);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IllegalStateException("Unable to create temp folder");
        }

        MasterKey key = new MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();

        EncryptedFile encryptedFile = new EncryptedFile.Builder(
                appContext,
                encrypted,
                key,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build();

        File tempZip = new File(targetDir, "temp.zip");
        try (InputStream in = encryptedFile.openFileInput();
             OutputStream out = new FileOutputStream(tempZip)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        unzipSecurely(tempZip, targetDir);
        // noinspection ResultOfMethodCallIgnored
        tempZip.delete();
        return targetDir;
    }

    public void unzipSecurely(@NonNull File zipFile, @NonNull File outputDir) throws Exception {
        try (InputStream in = new FileInputStream(zipFile)) {
            ZipSecurityUtils.unzipSecurely(in, outputDir);
        }
    }

    public void openOfflineSimulation(@NonNull Context context, @NonNull String labId) {
        Intent i = new Intent(context, OfflineSimulationDetailsActivity.class);
        i.putExtra("lab_id", labId);
        context.startActivity(i);
    }

    public void deleteOfflineSimulation(@NonNull String labId) {
        SimulationMeta meta = storageManager.getMeta(labId);
        if (meta != null && !TextUtils.isEmpty(meta.encryptedFilePath)) {
            // noinspection ResultOfMethodCallIgnored
            new File(meta.encryptedFilePath).delete();
        }
        File tempDir = new File(appContext.getCacheDir(), TEMP_DIR + File.separator + labId);
        deleteRecursively(tempDir);
        storageManager.deleteMeta(labId);
    }

    public void clearAllOfflineData() {
        List<SimulationMeta> list = storageManager.getAllMetas();
        for (SimulationMeta meta : list) {
            if (!TextUtils.isEmpty(meta.encryptedFilePath)) {
                // noinspection ResultOfMethodCallIgnored
                new File(meta.encryptedFilePath).delete();
            }
        }
        deleteRecursively(new File(appContext.getFilesDir(), SECURE_DIR));
        deleteRecursively(new File(appContext.getCacheDir(), TEMP_DIR));
        storageManager.clearAll();
    }

    public long calculateStorageUsed() {
        long total = 0L;
        for (SimulationMeta meta : storageManager.getAllMetas()) {
            if (!TextUtils.isEmpty(meta.encryptedFilePath)) {
                File f = new File(meta.encryptedFilePath);
                if (f.exists()) total += f.length();
            }
        }
        return total;
    }

    public SecureStorageManager getStorageManager() {
        return storageManager;
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "0 MB";
        double mb = bytes / (1024d * 1024d);
        return String.format(Locale.US, "%.2f MB", mb);
    }

    public static String formatSpeed(long bytesPerSecond) {
        return formatSize(bytesPerSecond) + "/s";
    }

    public static String formatEta(long sec) {
        if (sec < 0) return "Calculating...";
        long m = sec / 60;
        long s = sec % 60;
        return String.format(Locale.US, "%02dm %02ds", m, s);
    }

    public static String sha256(@NonNull File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file); DigestInputStream dis = new DigestInputStream(in, md)) {
            byte[] buffer = new byte[8192];
            //noinspection StatementWithEmptyBody
            while (dis.read(buffer) != -1) {
            }
        }
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format(Locale.US, "%02x", b));
        return sb.toString();
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        // noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void postMain(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static boolean isSupportedHttpsUrl(String raw) {
        return normalizeHttpsUrl(raw) != null;
    }

    private static String normalizeHttpsUrl(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        s = s.replace("\\/", "/");
        s = s.replace("&amp;", "&");
        if (s.startsWith("//")) {
            s = "https:" + s;
        }
        if (s.startsWith("http://virtuallabsimulator.com")
                || s.startsWith("http://www.virtuallabsimulator.com")) {
            s = "https://" + s.substring("http://".length());
        }
        s = s.replace(" ", "%20");
        HttpUrl parsed = HttpUrl.parse(s);
        if (parsed == null) return null;
        if (!"https".equalsIgnoreCase(parsed.scheme())) return null;
        return parsed.toString();
    }
}
