# Image Keeper v1.4.6 — One Row Filters

Date: 26 November 2026
Package: com.branchphotovault
Version: 1.4.6; versionCode 62

## Requested layout
- Account Filter / Sort / Only-with-photos all occupy a **single horizontal row**.
- Weighted widths: Filter **60%**, Sort **30%**, photo-only Checkbox **10%** (excluding small 4dp gaps).
- Photo-only checkbox is now a minimal **icon-only** control at the far right, with an accessible description and on/off state for screen readers.
- Sort options retain original semantics. The narrow control elides overly long option text without wrapping.

## Multiple account filter
- Filter drop-down shows checkboxes alongside each known Account plus **ทุก Account** as a reset.
- Users can tick **several accounts simultaneously** without the menu closing after each selection.
- An empty selection means all accounts, while checking an account applies an exact-match filter.
- Setting is stored as a StringSet in the existing app-private SharedPreferences, and mirrored to SavedStateHandle to preserve search/filter context.
- On upgrade from the earlier single-account preference, that account is migrated automatically when the multi-select setting is not present. Changes never modify the branch database or photo files.
- Branch count reflects the filtered results.

## Validation
- Gradle `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL.
- 54 JVM tests passed, 0 failures.
- In-place ADB installation on Infinix X6856: versionCode 61 → 62, success; no uninstall/clear data.
- Screenshot of real home UI: `research/ui_filter_single_row_v146/home.png`.
- Screenshot of checkbox dropdown: `research/ui_filter_single_row_v146/filter_menu.png`.
- Live multi-selection verified: JIFFY and CJ MORE checked together, and the branch count changed from 7 to 46; `research/ui_filter_single_row_v146/multi_verified.png`.
- No changes to official in-app updater, gallery, CameraX, GhostShift or Smart Shave.

## APK
`release_private/ImageKeeper_v1.4.6_CompactMultiAccountFilters_v62.apk`
Uses the unchanged Android debug signing certificate for in-place compatibility with previous releases.
