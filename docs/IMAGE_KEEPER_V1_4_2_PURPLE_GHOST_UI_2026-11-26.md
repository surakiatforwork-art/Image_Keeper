# Image Keeper v1.4.2 — Purple Ghost UI

## Scope
- **Launcher icon intentionally unchanged** (Ghost Gallery icon from v1.4.0/v1.4.1).
- Simplified the small **in-app toolbar icon** to the purple-hooded ghost's face, with no photo folder or four thumbnails. The new image resource is `drawable-nodpi/keeper_ghost_inapp.png`.
- Matching Material 3 color palette across Home, Gallery, list/grid controls, Settings and dialogs: vivid purple/indigo primary, soft lavender surfaces, small sky-blue highlights and deep navy-purple dark mode.
- Existing `System / Light / Dark` theme selector stays functional and persistent.
- Home title subtitle now inherits correct toolbar content color in both light and dark modes.
- No modifications to Room schema, captured photos, CameraX, Smart Shave, GhostShift integration, gallery sorting or branch-session restoration.

## Verification
- `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL.
- 51 JVM tests passed; 0 failures.
- Android emulator installed via `adb install -r` successfully; versionCode 58, versionName 1.4.2.
- App screenshot UI should be checked on physical test phone: emulator may be covered by another running test app.
- APK build: `release_private/ImageKeeper_v1.4.2_PurpleGhostUI_v58_PRIVATE.apk`.

## Delivery
This is a local development/test build. No GitHub release created as not requested.
