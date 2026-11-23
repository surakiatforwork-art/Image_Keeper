# Smart Shave Beta 30 — One-Tap AI (private)

Date: 2026-11-23 (Thailand)
Based on: Beta 29 (versionCode 47), Codex Chin Student 03 + experimental V6.8 contour compositing.
Output: `com.branchphotovault` versionCode **48**, versionName `1.4.0-beta.30-SmartShaveOneTap-Private`.

## User requirement

The user wants a **single "ลบหนวดและเคราด้วย AI" button that starts processing immediately**, without having to select moustache vs beard, the V6/AttGAN engine, iteration count, coverage, student, V6.7, contour, V6.6, and additional cleanup toggles followed by a separate Start button.

This release is a **workflow simplification**, not new model training or proof of better facial hair removal on every person.

## What changed

- `ui/screens/MoustacheRetouchDialog.kt` now has **one primary AI action**: `ลบหนวดและเคราด้วย AI`. The button executes directly and shows stage-by-stage status, no secondary confirmation/start action.
- Removes visible mode toggles, multiple engine choices, number of AI rounds and experiment sliders from the retouch dialog. The app automatically processes **BOTH** moustache and beard. It uses original landmark data, source photo and private bundled ONNX models.
- `retouch/SmartShaveAuto.kt` implements the private offline automatic recipe:
  1. **Moustache:** V6.7 with source-exact LipSafe, 84% coverage and **one** V6 pass for speed, original landmarks, V6.7 residual lip cleanup.
  2. **Beard/Chin:** unchanged Codex `Chin Student 03` checkpoint with V6.8 natural contour compositing enabled. Automatically processes the image produced by stage 1.
  3. If a specialized model fails, use the V6.6 beard fallback if available; otherwise a single V6 BOTH pass or show an honest partial-success message.
  4. Failure in one region does not automatically delete the safe result from the other region. The source photo is never rewritten.
- Preserves native-resolution output, on-device 2-finger zoom/pan, optional Healing Brush/Restore/AI-brush, before-after press-and-hold, output intensity adjustment (visible only *after* AI output), Undo/Redo, and **Save edited copy** (not auto-save).
- Only if a photo contains multiple faces is a face selector shown; it is not a model mode toggle.
- `ui/components/ManualRetouchEditor.kt`: manual-edit tool list is **not shown before processing**. After a generated result, controls scroll **independently below the fixed photo** while the main AI and Save buttons remain accessible.
- New integration test `SmartShaveOneTapDeviceTest.kt` tests the actual bundled model pipeline without Gallery/database access: output copy allocated, original bitmap untouched, lip pixels exact, facial-hair changes present, and benchmark logging.

## Test results

- `testDebugUnitTest` + `assembleDebug` + `assembleDebugAndroidTest` all PASS; 46 JVM tests.
- Android API 36 x86_64 emulator, real ONNX processing with fixed public research beard fixture: **PASS** `OK (1 test)`. Pipeline: Chin Student, ~3.7s on Emulator with one integrated test. 13,304 changed pixels, 6,561 exact protected lip pixels.
- Physical Infinix X6856: **APK installed successfully** at 2026-11-23 ~17:03, `adb install -r` package `com.branchphotovault`, versionCode 48. The installed app retained `firstInstallTime=2026-11-19 20:11:52`; internal `databases` 756K, `files` 16M, `shared_prefs` 12K, original `branch_photo_vault.db` present. No uninstall, cache clear or database deletion.
- Physical ARM64 **integrated test did NOT complete successfully**: the test started, V6 progress reached the mask stage, then the phone disconnected from ADB before the test returned a result. Do not represent that test as PASSED. Emulator full pipeline is verified; full physical photo quality/UI still requires user testing.

## Build / safe installation records

APK (private, do not publish):
`release_private/ImageKeeper_SmartShave_Beta30_OneTap_v48_PRIVATE.apk`

SHA256:
`B2FF92C3BA14318E99F898321FAB6868DCEA96CACAFCC51D62FE6625D2C6EC5E`

Certificate SHA256:
`2b3d3efff71526168656b137a2df5625c08bf488a0656ddacfbd54a0ff286a74`
(matches Beta 29)

Pre-update backup (verified tar including `branch_photo_vault.db`; only app private data):
`release_private/ImageKeeper_preBeta30_appdata_private_backup_20261123_170244.tar.gz`
129 entries / 103 files. Shared/external media paths were not exported; they were not changed.

Logs:
- `one-tap-beta30-build.log`
- `one-tap-beta30-emu-test.txt`
- `one-tap-beta30-infinix-test.txt` (incomplete due to ADB disconnect)

## Acceptance test for the user

Open a photo in Image Keeper > Smart Shave.
- The preview should be prominent, with **no need to set any model toggles**.
- Tap **"ลบหนวดและเคราด้วย AI" ONCE**. The first stage handles moustache and the next stage handles beard/chin automatically.
- Examine exact lip border, residual moustache and jawline on the same photos used in Beta 28/29.
- Optionally use manual brush and output slider; Save edited copy **only after inspection**.
- Verify photo originals remain untouched and folders remain present.

## Known limitations

- Model-generated chin shape and subtle lip transitions can still look unnatural on some real photos. A unified workflow does **not** guarantee all facial hair will disappear with identity-perfect texture.
- If multiple faces appear, the current selector chooses the current detection order; there is no autonomous multi-face processing.
- Private bundled model provenance/licensing is under review. Avoid public APK/model releases.
- Need physical Infinix test with real consented photos after ADB reconnect, UI inspection including Healing Brush two-finger zoom, and timing/performance measurements.
- Do not confuse “single button to start AI” with “automatically saving/overwriting photos.” The latter is intentionally NOT performed to preserve user data and let the user review.
