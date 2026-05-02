package com.virtuallab.client.offline;

public class SimulationMeta {
    public String labId;
    public String title;
    public String subject;
    public String difficulty;
    public String duration;
    public String version;
    public long fileSize;
    public String encryptedFilePath;
    public String mainFile;
    public long downloadedAt;
    public String checksum;
    public String thumbnailUrl;
    public String lastUpdated;
    public String overview;
    public String objectives;
    public String materials;
    public String procedure;

    public boolean isUpdateAvailable(String serverVersion) {
        return serverVersion != null && !serverVersion.trim().isEmpty() && !serverVersion.equals(version);
    }
}
