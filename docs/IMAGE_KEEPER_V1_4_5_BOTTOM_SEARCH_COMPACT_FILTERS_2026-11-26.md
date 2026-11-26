# Image Keeper 1.4.5 — Bottom Search & Compact Filters

Date: 26 November 2026
Package: `com.branchphotovault`
Version: `1.4.5` (versionCode 61)

## UI changes
- Relocated the branch search input from the scrolling home-page header to a fixed **bottom search bar**, above Android navigation controls.
- Bottom search is independent from the branch list's scroll state. It remains reachable when there are hundreds of branches.
- Search query continues to use the existing `BranchListViewModel.updateSearch` handler and existing SavedStateHandle; no database or preference changes.
- Compact one-row Account and Sort drop-down controls: 44dp-tall tappable chips rather than bulky 66dp cards.
- Replaced the large checkbox row with a **มีรูปเท่านั้น** compact toggle chip, preserving the existing `onlyWithPhotos` filter setting.
- Branch count remains next to the compact chip for quick context.
- Gallery header, ghost mascot, cards, branch sorting and original launcher artwork are unchanged.

## Keyboard / interaction
- Android's window resize now positions the bottom search directly above the IME.
- Removed duplicate `imePadding` after observing an oversized empty band while the keyboard was open on Infinix X6856.
- The add-branch FAB is hidden while the keyboard is visible, preventing overlap.
- The original Material3 footer keeps the Android navigation bar unobstructed.
- When the keyboard closes, the bottom search returns to its fixed resting position and the FAB reappears.

## Validation
- `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL.
- JVM unit tests: 54/54 passed.
- In-place `adb install -r` upgrade: v1.4.4 → v1.4.5 on Infinix X6856 (Android 16); success with no uninstall or clear data.
- Visual checks with real branch list: `research/ui_bottom_search_v145/phone_home.png`; with opened keyboard: `research/ui_bottom_search_v145/keyboard_fixed.png`. Verified search bar correctly lifts above keyboard.
- Tested with 567 branches loaded from the existing data source, without modifying existing branch photographs.

## APK
`release_private/ImageKeeper_v1.4.5_BottomSearchCompactFilters_v61.apk`

## Signing
Retains existing debug signing identity for seamless upgrade of existing Image Keeper installs; not a Play Store production signing key.
