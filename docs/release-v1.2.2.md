# Image Keeper v1.2.2

- Fix retouch crash: MediaPipe MPImage owns its input bitmap. Detection now
  receives a dedicated copy so closing MPImage cannot recycle a Compose preview.
- Add moustache, beard (chin/jaw) and combined modes. Default is both.
- Beard coverage follows lower face landmarks, avoiding the mouth and nose.
- Save separate edited copies with original branch, route and capture time.

Verification:
- Release, debug and instrumentation APK builds and release vital lint passed.
- Both on-device RetouchRuntimeTest cases passed on Infinix X6856 / Android 16:
  bitmap decoding + Face Landmarker lifecycle (preview bitmap remains usable),
  and MI-GAN ONNX inference on a synthetic masked image.
- Actual beard-removal appearance still requires user review. Landmark masks
  approximate regions, not individual facial hairs; dense beards may look
  unnatural. Compare before/after and retain originals.
