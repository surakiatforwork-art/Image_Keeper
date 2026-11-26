# Image Keeper — Gallery work UI and work-session continuity

Date: 26 Nov 2026
VersionCode: 54
VersionName: 1.4.0-beta.36-GalleryWorkContinuity-Private

## User-requested presentation

- Change the main app bar to a bright, calm sky-blue gallery-app style, with white readable navigation, status and actions.
- Create an isolated, purple hooded-ghost **vector app mascot**: only the ghost; no raven, PHANTOM wordmark or extra illustration from the supplied reference.
- Update launcher foreground and main app title mascot.
- Improve branch list rows with a store icon, name, account, code, photo count and navigation chevron.
- Clarify the branch list headline and total-count label; retain existing Account, sorting, only-with-photos, sync, settings and add-branch workflows.
- Make the branch gallery header a compact sky-blue-tinted information row, with a file count pill.
- Preserve the existing top-right Sort dropdown and separate three-dot menu for grid/list mode and column density (3/4/5/6).
- Preserve list/grid presentation, import, CameraX capture and selection/export/GhostShift/Smart Shave.

## Fix — continuity on switching applications

- Normal Home / switching apps: use the existing Navigation Compose back stack. Do not rebuild or reset the current branch.
- Cold start after process death/recreation: `BranchWorkSession` restores the most recently opened branch from app-private `SharedPreferences` when the nav destination starts on the branch list.
- A branch is remembered on opening an existing branch or on finishing Add Branch.
- Explicit Back arrow **and system Back** from the branch clears the active branch work-session state.
- Search query, Account filter, branch sort and only-with-photos filter are stored in `SavedStateHandle` **and** app-private preferences, so users need not re-enter their search after a cold start.
- Stored data is limited to branch/account identifiers and browsing UI preferences. It does not include images or personal/media content.
- Restore never uninstalls or clears local photo data; the Room schema remains untouched.

## Camera/status bar polish

- Restore original Android status/navigation bar colors and icon appearances after leaving the dark CameraX screen. The new sky-blue app bar uses white icons in light theme.

## Verification

- Gradle `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- App v54 installed over v53 on emulator-5580 using `adb install -r`: Success, launched without observed crash.
- Manual process-death reproduction on emulator: injected test-only active branch `SESSION_TEST / 99999`, force-stopped app and relaunched. Screenshot `research/ui_unslop/beta36_restored_branch.png` showed the branch-gallery screen with Sort, three-dot controls, capture and import instead of returning to the home branch list. The branch title is 'Branch' only because the test id has no matching Room row.
- Home→return test: `am start` brought the existing task to front instead of launching a new one.
- Explicit Back from the restored branch reset `image_keeper_work_session_v1.xml` to an empty map.
- Search-state test: entered `SSHOP123` on the emulator, verified saved preference survived an app force-stop/start. Removed test-only search preference afterward.
- Automated on-device `BranchWorkSessionTest` was **built** but emulator AndroidTest runner reported `Process crashed`, without a useful crash trace in the available buffer; therefore do not claim this test passed.
- Existing JVM unit tests all passed; no physical-phone installation was attempted because only emulator-5580 was connected for this build.

## Release

`release_private/ImageKeeper_Beta36_GalleryWorkContinuity_v54_PRIVATE.apk`

SHA256: `B0751F11C3E656427E9901D97E643BD8066ED217164F804AB0CCDB2B213997E0`

## Source files

- `navigation/BranchWorkSession.kt`, `navigation/BranchPhotoVaultNavHost.kt`
- `ui/viewmodel/BranchListViewModel.kt`
- `ui/components/BranchListItemCard.kt`, `ui/components/GlassComponents.kt`, `ui/components/BranchGalleryControls.kt`
- `ui/screens/BranchListScreen.kt`, `ui/screens/BranchPreviewScreen.kt`, `ui/screens/CapturePhotoScreen.kt`
- `ui/theme/Theme.kt`
- `res/drawable/keeper_hooded_ghost.xml`, `res/drawable/keeper_launcher_foreground.xml`
- `app/build.gradle.kts`
