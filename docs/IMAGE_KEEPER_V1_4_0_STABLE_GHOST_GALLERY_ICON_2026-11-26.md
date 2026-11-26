# Image Keeper v1.4.0 — Ghost Gallery Icon

Release date: 26 November 2026
Package: `com.branchphotovault`
Version name: `1.4.0`
Version code: `56`
GitHub release type: Stable (not a pre-release)

## Selected artwork
- Replaced the prior mascot/launcher artwork with the user's **selected ghost-and-gallery-folder icon**, containing a purple-hooded friendly ghost, purple folder and four gallery thumbnails, without text or birds.
- The selected image is now the header brand at `drawable-nodpi/keeper_brand.png`, and the padded Android adaptive launcher artwork at `drawable-nodpi/keeper_selected_launcher.png`.
- Updated both normal/round adaptive-icon XML entries, the blue/indigo gradient launcher background, and monochrome themed-icon artwork.
- New raster source is an optimized 512 × 512 version of the user-selected artwork. Padded adaptive foreground respects launcher icon masks.

## Existing features preserved
- The manual/system/light/dark theme picker with persistent preferences.
- Branch work-session restoration after app switching and process recreation.
- Per-branch photo grid/list, 3–6 column density and newest/oldest sorting.
- Camera volume-key shutter and haptic confirmation.
- All existing photos, import/export, GhostShift workflows and offline Smart Shave remain unchanged.

## Verification
- Gradle `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- JVM unit tests: 51 passed, 0 failed.
- Android emulator instrumented `BranchWorkSessionTest` and `ThemePreferenceTest`: 2 passed, 0 failed.
- Installed APK over version 55 on emulator using `adb install -r`, confirmed versionCode 56 / versionName 1.4.0 and app process started successfully. No uninstall/clear performed.

## Distribution/signing note
- The stable GitHub release uses the existing **debug signing certificate** for binary upgrade compatibility with earlier Image Keeper betas. It is not signed with a newly introduced Play Store production signing key, because that would prevent in-place upgrades and risk access to existing app-local photo data.
- Android API minSdk=26; install over existing version.
- Release APK: `release_private/ImageKeeper_v1.4.0_Stable_GhostGallery_v56.apk`.
