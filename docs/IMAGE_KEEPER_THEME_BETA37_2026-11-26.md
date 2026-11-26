# Image Keeper Beta 37 — Theme chooser

- Date: 26 November 2026
- Package: com.branchphotovault (unchanged)
- versionCode: 55
- versionName: 1.4.0-beta.37-ManualThemeSelection-Private

## Feature
- Added a three-option theme selector at the top of Settings: **ตามระบบ**, **สว่าง**, **มืด**.
- Each selection updates the entire app immediately. The preference persists in the existing app-private Jetpack DataStore.
- SYSTEM is the first-install default; forced light/dark ignores system night mode until changed by the user.
- Theme color schemes cover the branch list, gallery grid/list, Settings, toolbars, dialogs and selections.
- In dark mode, the app bar uses deep navy-blue with pale cyan text instead of blinding light-blue; the dark surfaces, text, and system navigation icon appearance have matching contrast.
- Existing camera viewfinder keeps its intentionally black camera chrome.
- No changes to the Room database, stored photos, selection/export behavior, Smart Shave, account settings or session restoration.

## Verification
- Gradle :app:assembleDebug :app:testDebugUnitTest: BUILD SUCCESSFUL.
- Gradle :app:assembleDebugAndroidTest: BUILD SUCCESSFUL.
- Android instrumented ThemePreferenceTest: PASS 1/1, all three modes survived repository reconstruction and previous preference was restored.
- Android emulator v55 upgrade without uninstall/clearing app data: PASS.
- AppThemeModeTest: tests missing/unknown selection defaults and roundtrip of three modes.

## Build
- release_private/ImageKeeper_Beta37_ManualThemeSelection_v55_PRIVATE.apk
