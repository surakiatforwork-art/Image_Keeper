# Image Keeper — Bright Gallery & Folder View Preferences

Date: 2026-11-25
Candidate version: 1.4.0-beta.33-BrightGalleryLayouts-Private (versionCode 51)

## User-visible changes
- Refreshed light/dark palette: a clear sky-blue action, soft cyan-tinted background, and a restrained peach/coral accent.
- Each store-branch folder now has two layout modes: **ตาราง** (photo grid) and **รายการ** (file-manager style list).
- Grid sizes are selectable: **3, 4, 5, or 6 columns**, with live updates.
- Sorting works in both layouts: **ล่าสุด → เก่าสุด** (newest first) or **เก่าสุด → ล่าสุด** (oldest first).
- List rows include a thumbnail, stored filename and local capture/import date and time.
- **Per-branch persistence**: layout, column count and sort order are saved in private Android preferences and restored on reopening the same branch and after app restart. Each branch remembers its own choices.

## Safeguards
- The photo database schema, media files and repository query remain unchanged.
- Sorting affects only gallery presentation. Multi-selection retains the order the user tapped photos, preserving existing export/send behavior.
- No permissions, destructive migrations or third-party SDKs were added.
- Installer keeps the original package ID and signature and uses adb install -r (never uninstall/clear).

## Tests
- Gradle :app:assembleDebug :app:testDebugUnitTest — PASS.
- Gradle :app:assembleDebugAndroidTest — PASS.
- Android emulator API app-install upgrade from v50 to v51 — PASS.
- Android instrumented GalleryViewPreferencesTest — PASS (per-branch isolation, reload, grid/list, column and sort restoration).

## Files
- app/src/main/java/com/branchphotovault/data/repository/GalleryViewPreferences.kt
- app/src/main/java/com/branchphotovault/ui/components/BranchGalleryControls.kt
- app/src/main/java/com/branchphotovault/ui/screens/BranchPreviewScreen.kt
- app/src/main/java/com/branchphotovault/ui/viewmodel/BranchPreviewViewModel.kt
- app/src/main/java/com/branchphotovault/ui/theme/Color.kt
- app/src/main/java/com/branchphotovault/ui/theme/Theme.kt
- app/src/androidTest/java/com/branchphotovault/GalleryViewPreferencesTest.kt
- app/build.gradle.kts
