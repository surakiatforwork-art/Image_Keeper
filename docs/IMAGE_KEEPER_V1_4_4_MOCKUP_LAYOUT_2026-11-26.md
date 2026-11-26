# Image Keeper 1.4.4 — Home Screen Mockup Alignment

Date: 26 November 2026
Version code: 60
Version name: 1.4.4
Package: com.branchphotovault (unchanged)

## Source / design direction
- Matched the user's selected two-panel light/dark Ghost Gallery mockup from the current conversation, beyond color-only reskin.
- Launcher icon preserved, with a ghost-only in-app header logo.

## Native Compose UI changes
- Rebuilt the home screen with a tall gradient blue-violet (light) / indigo-purple (dark) ribbon header.
- Added curved translucent decorative ribbons inside the header and noninteractive bottom-of-screen wave graphics.
- Made the rounded search field float into the header edge, with a lavender border and subtle elevation.
- Redesigned Account/Sort filters as larger, softly elevated glass-like cards with an intentional two-column arrangement.
- Improved hierarchy and spacing around the branch-count row, section title and last-sync metadata.
- Redesigned branch rows as layered gallery-folder cards with a gradient purple folder emblem, image count, refined typography, rounded border and soft shadow.
- Restyled the plus floating action into a prominent violet-gradient rounded square.
- Preserved real branch data and pre-existing search/filter/sort/navigation events; no new Room migrations or permissions.

## Validation
- `:app:assembleDebug :app:testDebugUnitTest`: BUILD SUCCESSFUL.
- `:app:assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- In-place upgrade from v1.4.3 to v1.4.4 on physical Infinix X6856 and Android emulator: Android ADB reported Success; installed versionCode 60 confirmed.
- Captured and visually inspected actual screenshots:
  - `research/ui_mockup_match_v144/phone_top.png`: light theme, real CJ MORE branch and 47 photos; confirms actual branch card matches intended hierarchy.
  - `research/ui_mockup_match_v144/emulator_dark_loaded.png`: dark theme styling, header, filters and decorative waves.
- Emulator splash experienced a transient "isn't responding" dialog during cold startup; choosing Wait reached the functional UI. This is a separate startup-performance issue and is **not** claimed to be solved by this visual update.
- Existing photo data was not cleared or migrated.

## Distribution
- `release_private/ImageKeeper_v1.4.4_MockupLayout_v60.apk`
- Same legacy debug signing identity for safe upgrades of previously installed private builds; not a Play Store production signing configuration.
