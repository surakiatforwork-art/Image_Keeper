# Keeper experimental Rust image core

This beta retains Android Bitmap decoding, scaling, JPEG compression, CameraX, Room, gallery, Smart Shave inference, and UI. The native path combines EXIF rotation/reflection, optional mirror, and centered crop for ARGB_8888 images requiring rotation or mirror. Simple crops stay on Android's native Bitmap path. If the library cannot load or transformation fails, the existing Kotlin/Android path is used. A successful native call emits a debug timing line with tag KeeperRust.

## Build (Windows)
Install Rust via rustup and Android NDK with Clang. Run:
```powershell
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
powershell -File native/keeper-core/build-android.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```
The native core uses no_std and has no external Rust crates. The four checked-in small shared libraries allow an ordinary Gradle build without Rust. Rebuild them when native code changes. This beta was built with Rust 1.100.0 and NDK 21.4.7075529; ELF LOAD segments align to 16 KiB. This does not certify other native libraries in the app.

## Tests on PC
```powershell
rustup target add wasm32-wasip1
rustc native/keeper-core/src/lib.rs --edition=2021 --test --target wasm32-wasip1 -o native/keeper-core/geometry-tests.wasm
node native/keeper-core/run-geometry-tests.cjs
```
The tests verify all eight EXIF orientations, optional mirrors, and valid pixel coordinates. Android JNI integration and real-device latency/quality comparison remain beta validation tasks. No speedup is asserted without measurement.
