package com.virtuallab.client.offline;

public interface DownloadProgressListener {
    void onStarted();
    void onProgress(int percent, long downloadedBytes, long totalBytes, long bytesPerSecond, long etaSeconds);
    void onCancelled();
    void onCompleted(SimulationMeta simulationMeta);
    void onError(String message, Throwable throwable);
}
