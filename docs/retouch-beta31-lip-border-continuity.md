# Smart Shave Beta 31 — Lip Border Continuity (Private)

Date: 2026-11-23 (Asia/Bangkok)
Repo: `C:\Project_winsurf\Time_Keeper`
Package: `com.branchphotovault`
Candidate: `1.4.0-beta.31-SmartShaveLipContinuity-Private` (versionCode 49)

## Why

User feedback after Beta 30 One-Tap: the vermilion border still has untouched stubble on some photos, and adjacent AI-generated skin looks noticeably different from original facial skin; beard removal itself is moving in the right direction but remains incomplete. This stage **does not change the Chin Student 03 / V6.8 beard reconstruction** because that line of work has improved.

## Implemented

- New `retouch/LipBorderHarmonizer.kt`: a **local, edge-limited skin continuity pass** performed after the existing offline V6 moustache pass and Chin Student 03 beard pass, with no new ONNX model, API, or extra full-face inference.
- Finds the **real 12-vertex outer lip contour** from original MediaPipe landmarks. Marks a narrow annulus immediately outside LipSafe's immutable lip pixels, max approx. 17% of actual mouth width.
- Proposes skin colour ONLY from adjacent skin in the final image that **was already edited by AI**, with positive donor evidence; if no reliable edited donor is present it **skips** the pixel instead of inventing broad new skin.
- Requires residual darkness or a visible local mismatch with already generated skin before altering the ring. Uses a smooth radial feather towards existing result. Avoids blurring entire face or modifying chin silhouette.
- Enforces `LipSafeRasterGuard.protectOriginal` on the final image **after** this pass; original lip/vermilion pixels are exact.
- `SmartShaveAuto.run(...,lipBlend=true)` enables the automatic stage. A private test-only parameter `lipBlend=false` allows A/B tests without **adding UI toggles**; the single “ลบหนวดและเคราด้วย AI” action remains unchanged.
- Existing source images and saved photo entries are not modified by the processing function; save remains explicit and creates an edited copy.

## Verification so far

- Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL (Beta 31 candidate before version bump).
- Android API36 emulator:
  - Full V6+Chin Student source-photo fixture -> LipBorderHarmonizer: `OK (1 test)`; examined 5,952 nearby pixels, found 4,245 edited-skin donor candidates, changed 2,027 pixels, 6,561 protected lip pixels EXACT original. Lip-specific pass 152ms on emulator. This is **not** total AI latency.
  - Three private test fixtures with pre-existing V6.7 output -> LipBorderHarmonizer: `OK (1 test)`; changed respectively **2,445 / 2,385 / 1,080** pixels, with source image and lip invariants preserved.
  - Side-by-side audit: `research/lip_beta31_audit/fixture_before_after.jpg`, `fixture_mouth_zoom.jpg`, `case_1_before_after.jpg` through `case_3_before_after.jpg`, and `case3_lip_zoom.jpg`.
- **Visual caution:** Differences are localized/subtle on the fixtures. Some thick moustache hairs and a larger chin/skin patch remain. This is NOT validated as fully natural skin or complete removal on the user's personal photos. Additional training / proposals may be needed for large patch tone mismatch.

## Important cautions / acceptance gate

1. Do not claim the lip seam is fixed simply because pixels changed or instrumentation passed. **User must compare Beta30 vs Beta31 with the SAME personal photo** at 100% magnification, especially upper/lower border and both mouth corners.
2. Keep original protected lips exact; no mouth reshaping, no new lipstick/teeth/teeth blur. Background, cheek and chin contour outside small lip annulus should not be changed by this stage.
3. Preserve the **single AI button** UI and the current Chin Student 03 / contour V6.8 beard path. Do not expose experiment toggles again.
4. Private research model licensing has not been cleared for public distribution. Keep APK, models, photo comparisons and phone backups OUT of public GitHub releases.
5. When upgrading test phone, use same signer/package, verify versionCode increment, back up private app data and check archive integrity; install with `adb install -r` only. Never `adb uninstall` or `pm clear`.

## Next quality iteration

A separate **skin-patch tone matching** (not global blur) may be required to address colour differences in larger edited areas outside the narrow lip ring. That requires broader consented image validation and cannot be assumed solved by this local seam pass. Improve dense residual hair with face-specific evidence, not generic brightness boosts.
