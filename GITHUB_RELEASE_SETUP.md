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

## 3. Build the APK for release uploads

Update the app version in [app/build.gradle](app/build.gradle) before each GitHub release:

- Increase `versionCode`
- Change `versionName`

Example:

```gradle
versionCode 2
versionName "1.0.1"
```

Build the APK:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is:

`app\build\outputs\apk\debug\app-debug.apk`

## 4. Publish a GitHub release

Create a GitHub release whose tag matches the app version.

Examples:

- `v1.0.1`
- `v1.1.0`

Attach the APK asset to that release. The updater will look for the first `.apk` asset on the latest release.

Recommended asset name:

`virtual-lab-client-1.0.1.apk`

## 5. Important signing rule

To update an already-installed Android app, the new APK must be signed with the same key as the installed APK.

- For local testing, uploading the APK built from this machine is usually enough.
- For production updates, use one stable release keystore for every release.

If the signing key changes, Android will refuse to install the update over the existing app.

## 6. How the app behaves

The Settings screen now:

- shows the current app version
- checks the latest GitHub release
- downloads the APK through Android's Download Manager
- opens the Android installer for the downloaded APK

If the repo does not exist yet, or there is no release/APK asset, the app will show that status in Settings.
