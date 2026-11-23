# Smart Shave V6.8 — Chin Contour Composite (private experiment)

Date: 2026-11-23
Based on: Codex Beta 28 / Chin Student 03 (`adae289`)
Android package: `com.branchphotovault`
Private test build installed on authorized Infinix X6856: `1.4.0-beta.29-ChinContourV68-Private` (versionCode 47)

## Why this branch exists

The user reports that Codex's Chin Student 03 made a meaningful improvement, while earlier versions sometimes produced an elongated or unusually bulging chin, residual hair and visible skin transitions. We want to preserve **exact Beta 28 behaviour** as the benchmark and add a switchable, cautious compositor instead of replacing the working neural checkpoint.

The existing 132 KB `chin-student-03.onnx` is used unchanged. There is **no newly trained or redistributed model** in this experiment.

## Changes

- `ChinContourComposite.kt` (new): signed-distance attenuation around the ORIGINAL photograph's face contour combined with beard classifier confidence from the ORIGINAL photograph.
- Strong detected beard **inside** the face gets almost full model contribution.
- Edits on the face boundary fade smoothly. Strong detected hair just below the face oval may still be edited; low-confidence areas outside the face are strongly suppressed.
- `ChinStudent.apply(...,naturalContour=false)` keeps legacy Beta 28 RGB residual compositing; true activates the above weighting. If beard classifier cannot initialize, computation falls back to legacy compositing.
- UI: **V6.8 · เกลี่ยคางตามแนวเดิม (ทดลอง)** switch appears only after enabling `ทดลองลบเครา · รุ่น 3`. The new switch is **OFF by default**, so users can turn it on and compare the SAME photo against the Beta 28 method.
- V6/AttGAN moustache processing, LipSafe, image saving, native image size, other modes and PhotoRepository are unchanged.

## Verification — 2026-11-23

Build: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline` **PASSED**.
Pure JVM tests for source contour weight/boundaries passed.
Android x86_64 emulator 5580, fully offline neural local test: `ChinContourCompositeDeviceTest` **PASSED**.
Three private emulator cases with original landmarks: `ChinContourPrivateCasesTest` **PASSED**. The last case is a screenshot proxy, so this does **not** prove generalization to all skin tones, illumination or original-camera photos.

| case | Beta28 changed pixels | V6.8 changed pixels | Beta28 edits outside original face oval | V6.8 outside edits | runtime Beta28 / V6.8 |
|---|---:|---:|---:|---:|---:|
| case-1 | 7,527 | 7,426 | 1,306 | 1,243 | 482ms / 440ms |
| case-2 | 7,560 | 7,464 | 1,309 | 1,252 | 257ms / 395ms |
| case-3 | 14,336 | 12,863 | 1,803 | 1,120 | 277ms / 448ms |

**Interpretation:** The experiment reduces edits outside the source facial outline on these specific cases without disabling chin generation; the reduction is **NOT** a validated measure of image quality, actual chin shape, hair removal or superior aesthetics. Visual comparisons show a subtle improvement in boundary conservatism, but dense beard and mismatched textures remain on the public beard fixture.

The original bitmap is unchanged; protected lip pixels and background corner pixels are identical in the targeted test.
**Physical Infinix X6856 ARM64:** the same fixed-landmark Android instrumentation test PASSED (`OK (1 test)`, 1.847 seconds including setup). It modified 7,446 model-proposal pixels versus 7,526 on the Beta 28 path; edits outside the original oval were 1,234 versus 1,285. This is a neural fixture/invariant test, NOT a real-photo visual acceptance test or total editing latency.
`V6.8` does not automatically solve the original lip-border seam problem. That remains a separate issue.

Private comparison previews and test logs:
`research/codex_v67_audit_20261123/private_compare_1.jpg`
`research/codex_v67_audit_20261123/private_compare_2.jpg`
`research/codex_v67_audit_20261123/private_compare_3.jpg`
`research/codex_v67_audit_20261123/chin_v68_device_compare.jpg`
`research/codex_v67_audit_20261123/v68-private-instrumentation.txt`

## Caveats and acceptance gate

- V6.8 is explicitly **experimental/opt-in**. Keep the Beta 28 path unchanged for a real A/B check.
- Actual user satisfaction has not yet been assessed for V6.8. It may leave more hair near the outer jaw, and it is not proof that the real hidden chin shape was reconstructed.
- Android emulator runtimes are NOT physical Infinix X6856 benchmark times.
- FaceBench and beard segmentation checkpoint rights remain **research-only / uncertain**. Keep private APKs/private photos/backups outside public Git history and do not publish the APK commercially.
- Retouch does not upload pictures to an API. This does NOT imply all Image Keeper features are offline.
- Physical ARM64 fixed-fixture model test passed. Still need physical device **UI/preview smoke test and real-photo before/after comparison** of both modes at the same settings, plus quality feedback and lip/jaw regression check before enabling V6.8 by default.

## Next recommended development

1. Capture authorized **source, Beta 28 output, V6.8 output** triplets on more diverse faces at the same resolution/settings. Use private on-device assets only.
2. Improve the *generative proposal* for dense jaw beard; source-anchored masks cannot generate missing skin detail by themselves.
3. Improve lip seam using a separate, genuinely hair-conditioned border harmonizer; never blend into protected vermilion or blur an entire face.
4. Evaluate projected chin shape and colour continuity against original lighting at full resolution, not only changed-pixel counts.
5. Keep an explicit fallback in UI and back up data before any replacement installation.

## Safety

**Installed private trial:** `release_private/ImageKeeper_SmartShave_Beta29_V68_Experimental_v47_PRIVATE.apk`, app update via `adb install -r` on Infinix X6856 on 2026-11-23 at 16:38. Certificate SHA-256 matched the preceding private APK; `firstInstallTime` remained 2026-11-19 20:11:52. App private `databases` (756K), `files` (15M), `shared_prefs` (12K) remained present after update. No uninstall, `pm clear` or gallery mutation occurred. Current app's private database remained present. It was not visually exercised through the full user retouch flow yet.

**Validated pre-update backup:** `release_private/ImageKeeper_preBeta29_appdata_private_backup_20261123_163756.tar.gz` (127 archive entries; contains the existing `branch_photo_vault.db`). This backs up internal application directories only; external/shared media files were not exported but were not modified.

Only `adb install -r` with same package + signer + increasing versionCode may replace the authorized test app. Do not `uninstall`, `pm clear`, erase images or touch the user's other apps. The research model files live in debug assets and MUST not be publicly committed.
