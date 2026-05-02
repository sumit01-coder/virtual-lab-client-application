package com.virtuallab.client.offline;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.text.TextUtils;

import com.virtuallab.client.api.ApiClient;
import com.virtuallab.client.api.dto.ApiEnvelope;
import com.virtuallab.client.api.dto.LabDetailsPayload;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class OfflineSyncManager {

    public interface SyncListener {
        void onSyncMessage(String message);
    }

    private static final String PREF = "offline_sync_pref";
    private static final String KEY_LAST_SYNC = "last_sync_time";
    private static final long SYNC_WINDOW_MS = 30L * 60L * 1000L;

    private final Context appContext;
    private final OfflineSimulationManager offlineManager;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public OfflineSyncManager(Context context) throws Exception {
        this.appContext = context.getApplicationContext();
        this.offlineManager = new OfflineSimulationManager(appContext);
        this.prefs = appContext.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void syncDownloadedSimulations(boolean force, SyncListener listener) {
        if (!isInternetAvailable()) return;

        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_SYNC, 0L);
        if (!force && (now - last) < SYNC_WINDOW_MS) return;

        executor.execute(() -> {
            List<SimulationMeta> downloaded = offlineManager.getStorageManager().getAllMetas();
            int updatedCount = 0;

            for (SimulationMeta local : downloaded) {
                try {
                    int labId = Integer.parseInt(local.labId);
                    Response<ApiEnvelope<LabDetailsPayload>> response = ApiClient.get().labDetails(labId).execute();
                    ApiEnvelope<LabDetailsPayload> envelope = response.body();
                    if (!response.isSuccessful() || envelope == null || !envelope.status || envelope.data == null) {
                        continue;
                    }

                    LabDetailsPayload remote = envelope.data;
                    String remoteVersion = safe(remote.version);
                    String remoteZipUrl = safe(remote.simulation_zip_url);
                    if (TextUtils.isEmpty(remoteVersion) || TextUtils.isEmpty(remoteZipUrl)) {
                        continue;
                    }

                    if (remoteVersion.equals(local.version)) {
                        continue;
                    }

                    SimulationMeta next = new SimulationMeta();
                    next.labId = local.labId;
                    next.title = !TextUtils.isEmpty(remote.title) ? remote.title : local.title;
                    next.version = remoteVersion;
                    next.checksum = safe(remote.checksum);
                    next.mainFile = !TextUtils.isEmpty(remote.main_file) ? remote.main_file : local.mainFile;
                    next.thumbnailUrl = !TextUtils.isEmpty(remote.thumbnail_url) ? remote.thumbnail_url : local.thumbnailUrl;
                    next.lastUpdated = safe(remote.last_updated);

                    final Object lock = new Object();
                    final boolean[] done = {false};
                    final boolean[] ok = {false};

                    offlineManager.downloadSimulation(next, remoteZipUrl, new DownloadProgressListener() {
                        @Override public void onStarted() {}
                        @Override public void onProgress(int percent, long downloadedBytes, long totalBytes, long bytesPerSecond, long etaSeconds) {}
                        @Override public void onCancelled() { finish(false); }
                        @Override public void onCompleted(SimulationMeta simulationMeta) { finish(true); }
                        @Override public void onError(String message, Throwable throwable) { finish(false); }

                        private void finish(boolean success) {
                            synchronized (lock) {
                                ok[0] = success;
                                done[0] = true;
                                lock.notifyAll();
                            }
                        }
                    });

                    synchronized (lock) {
                        while (!done[0]) {
                            lock.wait();
                        }
                    }

                    if (ok[0]) {
                        updatedCount++;
                        if (listener != null) {
                            listener.onSyncMessage("Auto synced offline lab: " + next.title + " (" + next.version + ")");
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
            if (updatedCount > 0 && listener != null) {
                listener.onSyncMessage("Offline sync complete. " + updatedCount + " simulation(s) updated.");
            }
        });
    }

    public void syncSingleLabIfChanged(SimulationMeta local, LabDetailsPayload remote, SyncListener listener) {
        if (local == null || remote == null || !isInternetAvailable()) return;

        String remoteVersion = safe(remote.version);
        String remoteZipUrl = safe(remote.simulation_zip_url);
        if (TextUtils.isEmpty(remoteVersion) || TextUtils.isEmpty(remoteZipUrl)) return;
        if (remoteVersion.equals(local.version)) return;

        SimulationMeta next = new SimulationMeta();
        next.labId = local.labId;
        next.title = !TextUtils.isEmpty(remote.title) ? remote.title : local.title;
        next.version = remoteVersion;
        next.checksum = safe(remote.checksum);
        next.mainFile = !TextUtils.isEmpty(remote.main_file) ? remote.main_file : local.mainFile;
        next.thumbnailUrl = !TextUtils.isEmpty(remote.thumbnail_url) ? remote.thumbnail_url : local.thumbnailUrl;
        next.lastUpdated = safe(remote.last_updated);

        offlineManager.downloadSimulation(next, remoteZipUrl, new DownloadProgressListener() {
            @Override public void onStarted() {}
            @Override public void onProgress(int percent, long downloadedBytes, long totalBytes, long bytesPerSecond, long etaSeconds) {}
            @Override public void onCancelled() {}
            @Override public void onCompleted(SimulationMeta simulationMeta) {
                if (listener != null) listener.onSyncMessage("Auto synced latest offline package for " + simulationMeta.title);
            }
            @Override public void onError(String message, Throwable throwable) {}
        });
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.Network net = cm.getActiveNetwork();
        if (net == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(net);
        return cap != null && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
