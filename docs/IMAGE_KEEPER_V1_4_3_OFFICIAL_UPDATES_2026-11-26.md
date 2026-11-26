# Image Keeper 1.4.3 — Purple Ghost UI & Official Update Center

Date: 26 November 2026
Package: `com.branchphotovault`
Version: `1.4.3`, code `59`
Distribution: GitHub **Stable Release** (not Pre-release)

## Design
- Keeps the approved purple hooded ghost + gallery folder **launcher icon unchanged**.
- The **in-app** top toolbar uses a simpler ghost-only illustration, no photo folder or extra text.
- Material 3 light theme: lavender white backgrounds, blue-to-violet toolbar gradient, purple actions, subtle card outlines.
- Material 3 dark theme: dark navy/indigo background, violet toolbar, purple foreground and accessible contrast.
- Theme choice (System/Light/Dark), branch session restore, gallery controls and image tools remain intact.

## Update Center
- Settings → **อัปเดตแอป**.
- Checks the latest stable GitHub Release through `https://api.github.com/repos/surakiatforwork-art/Image_Keeper/releases/latest`.
- Compares semantic version against the installed app; shows stable release version and notes.
- Downloads official APK through Android DownloadManager, with visible notification and persistent progress when leaving/reopening Settings.
- Requires a GitHub Release asset with a SHA-256 digest. On completion verifies its actual SHA-256, package name `com.branchphotovault`, signing certificate and strictly newer versionCode.
- Opens Android's install dialog through app-scoped FileProvider. On Android 8+, asks the user to allow installation from this source if needed, then requires explicit user confirmation. **No silent install or auto-update**.
- Handles no-network, missing APK, invalid digest, wrong signing certificate, outdated APK and download failure without touching existing app data.
- No third-party update SDK or remote code execution.

## Testing
- Gradle `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: PASS.
- JVM unit tests: 54 passed, 0 failures (includes version comparison tests).
- Offline Android instrumentation: three updater metadata/security parser tests passed; branch work-session and theme persistence tests passed separately.
- Emulator API connection failed due missing DNS (UnknownHostException for `api.github.com`). This is **an emulator network/environment issue**, not a verified successful end-to-end GitHub download. GitHub latest stable JSON, apk digest and asset URLs were inspected successfully on the connected Windows PC.
- Real-device end-to-end full APK download/installer path should be validated as a follow-up.
- Package remains signed with the same Android debug signing certificate as earlier distributed GitHub builds, because changing it would break in-place upgrades and access to existing local photo data. A formal Play Store-style signing migration has not been performed.

## APK
`release_private/ImageKeeper_v1.4.3_OfficialUpdates_v59.apk`
