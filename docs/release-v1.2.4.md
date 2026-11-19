# Image Keeper v1.2.4

- Replace general-purpose MI-GAN retouch with an offline AttGAN clean-shaven generator.
- Align the face, match cheek colour, and blend only the moustache/lower jaw with protected nose/lips.
- Keep moustache, beard and combined modes. Default blend strength is 85%.
- Original images and their existing metadata remain unchanged; edits are separate copies.
- Bundle the model for offline use; no photo uploads or external processing service.
- This is still experimental retouch: dense hair may remain and skin may appear softer.

Model provenance and notices are in `assets/retouch/SOURCES.txt` and `AttGAN-LICENSE.txt`.
The model uses fixed clean-shaven controls with half-precision weight storage and
float32 CPU inference. Conversion was checked against the original generator.

Validation: three Android instrumentation tests passed on the connected Infinix
X6856 (Android 16). The real-photo batch covered ten separately supplied private
photos and all three region modes on the first photo. Input bitmap, protected
nose/lip pixels and background checks passed. Normal foreground runs took about
1.8-2.4 seconds per photo on this device; the initial background test was paused
by the device's battery manager and is excluded from the timing estimate.
Private fixtures and generated example photos are not bundled or published.
