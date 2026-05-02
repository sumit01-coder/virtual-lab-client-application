package com.virtuallab.client.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubReleaseInfo {
    private static final Pattern VERSION_PART_PATTERN = Pattern.compile("\\d+");

    public final String version;
    public final String notes;
    public final String htmlUrl;
    public final String publishedAt;
    public final String apkName;
    public final String apkDownloadUrl;

    public GitHubReleaseInfo(
            String version,
            String notes,
            String htmlUrl,
            String publishedAt,
            String apkName,
            String apkDownloadUrl
    ) {
        this.version = safe(version);
        this.notes = safe(notes);
        this.htmlUrl = safe(htmlUrl);
        this.publishedAt = safe(publishedAt);
        this.apkName = safe(apkName);
        this.apkDownloadUrl = safe(apkDownloadUrl);
    }

    public boolean hasApkAsset() {
        return !apkDownloadUrl.isEmpty();
    }

    public boolean isNewerThan(String currentVersion) {
        return compareVersions(version, currentVersion) > 0;
    }

    public String getDisplayNotes() {
        if (!notes.isEmpty()) {
            return notes;
        }
        if (!publishedAt.isEmpty()) {
            return "Published on " + publishedAt;
        }
        return "No release notes were added to this GitHub release.";
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = extractVersionParts(left);
        int[] rightParts = extractVersionParts(right);
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.length ? leftParts[i] : 0;
            int r = i < rightParts.length ? rightParts[i] : 0;
            if (l != r) {
                return l > r ? 1 : -1;
            }
        }
        return normalizeVersion(left).compareToIgnoreCase(normalizeVersion(right));
    }

    private static int[] extractVersionParts(String value) {
        Matcher matcher = VERSION_PART_PATTERN.matcher(normalizeVersion(value));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        int[] parts = new int[count];
        matcher.reset();
        int index = 0;
        while (matcher.find()) {
            try {
                parts[index++] = Integer.parseInt(matcher.group());
            } catch (NumberFormatException ignored) {
                parts[index - 1] = 0;
            }
        }
        return parts;
    }

    private static String normalizeVersion(String value) {
        String cleaned = safe(value);
        while (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
