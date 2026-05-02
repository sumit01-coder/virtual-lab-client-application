package com.virtuallab.client;

public final class Config {
    private Config() {}

    // Change this to your domain
    public static final String BASE_URL = "https://www.virtuallabsimulator.com/android_api/client/";
    // Use your Google OAuth Web Client ID from Google Cloud Console
    public static final String GOOGLE_WEB_CLIENT_ID = "572529233442-s85h72fmmt4ct82lq8a4prrnsfta37h6.apps.googleusercontent.com";

    // GitHub release source for app updates.
    public static final String GITHUB_RELEASE_OWNER = "sumit01-coder";
    public static final String GITHUB_RELEASE_REPO = "virtual-lab-client-application";
    public static final String GITHUB_REPOSITORY_FULL_NAME = GITHUB_RELEASE_OWNER + "/" + GITHUB_RELEASE_REPO;
    public static final String GITHUB_RELEASES_URL = "https://github.com/" + GITHUB_REPOSITORY_FULL_NAME + "/releases";
    public static final String GITHUB_LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/" + GITHUB_REPOSITORY_FULL_NAME + "/releases/latest";
}
