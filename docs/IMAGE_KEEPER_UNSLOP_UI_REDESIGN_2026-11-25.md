# Image Keeper — Unslop UI Redesign (private beta)

Date: 2026-11-25 (Asia/Bangkok)
App: Image Keeper / `com.branchphotovault`
Workspace: `C:\Project_winsurf\Time_Keeper`
Candidate: `1.4.0-beta.32-ImageKeeperUnslop-Private`, versionCode 50

## Source & credit

Applied the publicly available [unslop skill](https://github.com/heydamir/unslop), created by **Damir Sokolovsky (@heydamir)** and licensed **CC BY 4.0**. This document and the `GlassComponents.kt` comment keep attribution. The design criteria came from `skills/unslop/SKILL.md`, `references/signs.md`, `references/tokens.md`, and `references/refs.md`, read from the upstream repo.

This is an **adaptation to native Android Jetpack Compose**; the original skill includes web-oriented examples, but the audit, visual direction and nine pass ordering work in Compose as well.

## Direction — five lines

- **Product:** A photo vault organized by store branch for rapid field capture, retrieval, transfer and safe edit.
- **Character:** Precise, practical, quietly playful.
- **World:** Field photo folders, printed branch labels and a handheld work camera.
- **Moments:** The user's REAL photo thumbnails and store-branch identity; quiet UI frames the content.
- **Signature:** Preserve the established friendly little Keeper ghost + its mint folder accent, without spreading decorative mint everywhere.

## Audited problems in the original emulator home screen

1. Saturated purple/blue/pink gradient background and radial wash competed with real photo content.
2. Glassy gradient surfaces, colored shadows and emphasized borders appeared on virtually every control.
3. All UI components were oversized pills (large radius 20–32 dp), destroying hierarchy.
4. Purple gradients on primary buttons and floating action button, with a large shadow.
5. Search, checkbox, filters and sync metadata had uneven grouping/rhythm.
6. English `No branches found` and `Last sync: Never` mixed with Thai core UI.
7. Single branch rows had ambiguous hierarchy and photo counts hidden in a third descriptive line.
8. Photo editor used a generic sparkle icon for real editing controls.
9. Settings had three equal primary actions, including cleanup; web app URL wrapped into a giant multi-line field.

## Changes — nine Unslop passes

1. **Spacing:** 4dp-based sizes (8/12/16/20), equal filter/control dimensions; consistent 16dp content margins, compact 12dp gaps; unified photo list rows.
2. **Color:** Neutral paper-like light theme and calm ink-like dark theme, **one mint-teal accent** inspired by the ghost's original folder. Removed tinted gradient washes, glowing borders and colored shadows.
3. **Icons:** Retained existing Material icon family for compatibility; replaced `AutoAwesome` sparkle in Smart Shave/photo viewer with a functional pencil icon. Preserved the **actual** branded ghost illustration as the logo (not an emoji).
4. **Copy:** Thai empty states, sync metadata, search hint and settings labels; retained truthful app terminology and functionality, shortened the transfer help text.
5. **Type:** Preserved the project's bundled **Noto Sans Thai** (supports Thai reliably) and Material type hierarchy, without adding decorative fonts or glyphs.
6. **Layout:** Home search → filters → status/branch count → content. Empty-state photo-folder glyph is meaningful. Branch rows show store name/account/code, with image count aligned right.
7. **Details:** Buttons radius 12dp, cards 16dp, dropdowns and inputs 12dp, bottom sheet 20dp; quiet hairline borders, no inflated pills. Transfer date filters fit next to one another.
8. **Visuals:** No stock images or synthetic glossy blobs; hero moment remains the user's actual photo grid and the Keeper mascot.
9. **Motion:** Retained only tactile press feedback (subtle 0.98 scaling, 140ms), removed background glow effects. No new heavy animations or image generators.

## Implementation coverage

- `app/src/main/java/com/branchphotovault/ui/theme/Color.kt`
- `app/src/main/java/com/branchphotovault/ui/theme/Theme.kt`
- `app/src/main/java/com/branchphotovault/ui/components/GlassComponents.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/BranchListScreen.kt`
- `app/src/main/java/com/branchphotovault/ui/components/BranchListItemCard.kt`
- `app/src/main/java/com/branchphotovault/ui/components/RouteBadge.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/BranchPreviewScreen.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/PhotoViewerScreen.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/MoustacheRetouchDialog.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/branchphotovault/ui/screens/PhotoTransferCard.kt`

**No changes to** branch/photo Room DB schema, sync logic, gallery save, photo import/export logic, Smart Shave ONNX weights or inference, GPS, photo storage directories, UI navigation routes, package name, or original files.

## Visual reference checks

Captured from the existing `emulator-5580` (empty branches fixture; no private user phone photos read):

- Original: `research/ui_unslop/before_home_loaded.png`
- New: `research/ui_unslop/after_home_loaded.png`
- New Settings screen: `research/ui_unslop/settings_preview.png`

Actual screenshot confirms the background is flat neutral, the search/filter inputs and button shapes are restrained, the ghost is preserved, and the Thai empty state is legible.

## Release / validation

- Intended release is a **private UI beta**; do not redistribute private Smart Shave model assets or personal gallery data.
- **Build verification:** `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` passed on Windows. **46 JVM tests, 0 failures**. APK versionCode 50 installed on Android emulator successfully and launched without fatal crash.
- **Signing:** debug signer SHA-256 `2b3d3efff71526168656b137a2df5625c08bf488a0656ddacfbd54a0ff286a74` exactly matches the prior v49 test APK.
- **APK:** `release_private/ImageKeeper_Beta32_Unslop_UI_v50_PRIVATE.apk` (private; model assets included). APK SHA-256 `1E39D74A1347538B4E63BD21667C42D4532A9F4B7030CA6831DB893E6F760C35`.
- The model-loading and retouch pipeline is unchanged from v49. On-device human review remains necessary; **no installation on user's physical phone** in this UI redesign task.
- Upgrade must use **same package and signer** with `adb install -r`; confirm verified app-data backup before installing on the physical phone; no `uninstall`, `pm clear` or data migration.
- Minimum supported performance target continues to be POCO F1, with zero new on-device AI processing caused by these UI changes.

## Unslop acceptance criteria

- [x] Removes the common hue gradients, glows and oversize pills
- [x] One intentional accent with semantic colors only
- [x] Main home screen conveys branch-photo workflow at a glance
- [x] Keeps real Keeper mascot and existing Noto Sans Thai
- [x] Preserves features, navigation callbacks and photo-edit workflow
- [x] Native Android implementation, not a separate HTML mockup
- [ ] Physical phone dark/light UI smoke check on representative photo albums
- [ ] Subjective user acceptance on device
