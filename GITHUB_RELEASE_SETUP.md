# GitHub Release Setup

This Android client now checks GitHub releases from:

`sumit01-coder/virtual-lab-client-application`

## 1. Create the GitHub repository

Create this public repository on GitHub:

`https://github.com/sumit01-coder/virtual-lab-client-application`

Use a public repository because the app downloads release APKs directly from GitHub.

## 2. Push this Android project into that repo

This folder is currently inside a larger parent git workspace, so create a dedicated repo for this Android client before pushing it.

Suggested local flow:

```powershell
cd "d:\public_html (6)\android_studio_java_client"
git init -b main
git add .
git commit -m "Initial Virtual Lab client app"
git remote add origin https://github.com/sumit01-coder/virtual-lab-client-application.git
git push -u origin main
```

## 3. Configure release signing

Use one stable release keystore for every public APK. Debug APKs are for testing only and are much more likely to trigger Android/Play Protect warnings when shared.

Set these environment variables before publishing:

```powershell
$env:VL_RELEASE_STORE_FILE="D:\keys\virtual-lab-release.jks"
$env:VL_RELEASE_STORE_PASSWORD="your-store-password"
$env:VL_RELEASE_KEY_ALIAS="virtual-lab"
$env:VL_RELEASE_KEY_PASSWORD="your-key-password"
$env:GITHUB_TOKEN="your-github-token"
```

## 4. Publish a GitHub release

Use the release script. It updates [app/build.gradle](app/build.gradle), builds a signed release APK, creates the GitHub release, and uploads the APK.

```powershell
.\release_to_github.ps1 -VersionName "1.2.1" -ReleaseNotes "Fixes and improvements."
```

Optional: pass `-VersionCode 13`. If omitted, the script increases the current `versionCode` by 1.

The uploaded asset name will be similar to:

`virtual-lab-client-1.2.1.apk`

The GitHub tag will match the app version:

Examples:

- `v1.0.1`
- `v1.1.0`

## 5. Important signing rule

To update an already-installed Android app, the new APK must be signed with the same key as the installed APK.

- For local testing, install debug APKs manually and keep them separate from public releases.
- For production updates, use the same stable release keystore for every release.

If the signing key changes, Android will refuse to install the update over the existing app.
If `versionCode` is not higher than the installed app, Android will not treat the APK as an update and Settings may still show the old installed version.

## 6. How the app behaves

The Settings screen now:

- shows the current app version
- checks the latest GitHub release
- downloads the APK through Android's Download Manager
- opens the Android installer for the downloaded APK

If the repo does not exist yet, or there is no release/APK asset, the app will show that status in Settings.
