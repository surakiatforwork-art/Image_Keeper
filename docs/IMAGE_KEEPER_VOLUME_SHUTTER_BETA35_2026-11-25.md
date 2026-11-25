# Image Keeper — Volume Shutter and Capture Haptics (Beta 35)

Date: 25 Nov 2026
Application: com.branchphotovault
VersionCode: 53
VersionName: 1.4.0-beta.35-VolumeShutterHaptics-Private

## Behavior

- Inside the in-app CameraX capture screen, either physical **Volume Up** or **Volume Down** triggers exactly the same photo action as the circular shutter button.
- The first KEY_DOWN fires capture; repeat events are consumed without extra photos. The release event is consumed even if the app navigates back to the folder during capture completion.
- Once the camera composable is left, physical volume controls revert to their normal operating-system behavior.
- The existing capture lock prevents double shots while ImageCapture writes its file. Both on-screen and hardware controls share this lock and a camera-ready check.
- On successful CameraX onImageSaved, the phone emits one short low-amplitude 26 ms vibration (amplitude 48 / 255). No vibration is emitted for capture errors.
- Vibration respects the device HAPTIC_FEEDBACK_ENABLED system setting and safely skips devices without a vibrator. android.permission.VIBRATE is a normal (non-runtime) permission.

## Compatibility and data safety

- No modifications to branch data, Room database, images, gallery preferences, send/export, or offline Smart Shave.
- Original package ID and local signing pipeline preserved. Update with adb install -r only; do not uninstall or clear data.
- CameraX front/back selection and mirroring remain unchanged.
- Hardware interception exists only when CapturePhotoScreen is mounted.
- No expensive processing or animation added.

## Verification

- Gradle :app:assembleDebug :app:testDebugUnitTest: BUILD SUCCESSFUL.
- VolumeShutterKeyDispatcherTest: 3 tests, 0 failures, covering volume up/down, long-press repeats, normal-volume behavior outside camera, and a key release after navigation.
- Device vibration quality and live shutter photography should be checked on the physical test phone, since a JVM test cannot validate motor amplitude or CameraX output.
