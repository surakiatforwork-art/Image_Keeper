# Image Keeper — Gallery toolbar refinement (Beta 34)

Date: 2026-11-25
Version: 1.4.0-beta.34-GalleryToolbar-Private (versionCode 52)

## Requested UX
- Move grid/list layout selection and 3/4/5/6-column density controls into the **top-right three-dot menu** within each branch gallery.
- Move the **sort dropdown** to the top-right toolbar **outside** that menu, immediately to its left.
- Remove the large gallery-configuration card from the scrolling photo area, leaving more room for photos.

## Behavior
- Sort toolbar shows "ล่าสุด" or "เก่าสุด" and offers newest-to-oldest or oldest-to-newest.
- The three-dot menu contains **ตาราง**, **รายการ**, and the four column-count controls.
- Selecting a column count switches directly to grid mode.
- The existing per-branch settings persist across branch re-entry and app restart.
- When one or more photos are selected, existing download/send/delete/clear-selection actions take priority in the toolbar. The sort/menu controls return when selection ends.

## Safety and verification
- No changes to the photo database or its schema, media files, storage, export, camera or Smart Shave.
- No clear/uninstall performed on test phone.
- Gradle `:app:assembleDebug :app:testDebugUnitTest` succeeded.
- Updated the attached Infinix X6856 from versionCode 51 to 52 with `adb install -r`; Android reported `Success`; app launched.
- APK: `release_private/ImageKeeper_Beta34_GalleryToolbar_v52_PRIVATE.apk`.
