# Image Keeper 1.4.7 — Home overflow menu

Date: 26 November 2026
App ID: com.branchphotovault
Version: 1.4.7; Version Code 63

## Request implemented
- Removed the separate Refresh and Settings buttons from the home page's top-right header.
- Added a single **vertical three-dot overflow menu** at the same position.
- Menu entries with vector icons:
  - **รีเฟรชรายการสาขา** — calls the pre-existing BranchListViewModel.syncBranches. While a sync runs, the entry is disabled and shows a small progress spinner to prevent repeated taps.
  - **ตั้งค่า** — uses the same Settings navigation callback as the earlier gear button.
- Menu closes immediately after tapping either command, or when tapping outside.
- No other branch filters, database queries, photo paths, Smart Shave, bottom search, gallery, permissions, or persistence modified.

## Test results
- `:app:assembleDebug :app:testDebugUnitTest`: Build successful.
- In-place install on Infinix X6856 from 1.4.6 (62) to 1.4.7 (63): ADB Success; no uninstall or clear data.
- Visual screenshot `research/ui_header_overflow_v147/menu.png`: displays both entries in the new menu.
- Navigation screenshot `research/ui_header_overflow_v147/settings.png`: Settings opened through the new menu, with persisted theme and update card intact.
- APK: `release_private/ImageKeeper_v1.4.7_OverflowMenu_v63.apk`
- Distribution retains the existing Android debug signing identity to support in-place upgrades from the earlier GitHub APKs.
