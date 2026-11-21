# Smart Shave V5 — 360° Facial Hair Mapping & Face Reconstruction (research)

Status: **experimental; not part of the current Image Keeper retouch UI or stable APK.**

## Research direction

Separate **search**, **hair identification**, **skin reconstruction**, and **identity-safe compositing**. Previous AttGAN results are the established offline baseline and MUST remain the default until evidence shows improvements on multiple held-out faces.

1. Map a continuous 360° search zone around the entire outer lip contour. Unlike upper-lip / chin rectangle heuristics, it makes no directional assumptions. The region is a search envelope, not a hair segmentation prediction or permission to alter pixels.
2. Filter proposals through a separately licensed hair segmenter, face parsing, and protected lips/nose/jaw anatomy. Geometry alone must not repaint any skin.
3. Give an offline generative model enough **whole-face context** to reconstruct skin obscured by hair. Producing a clean-shaven face reference is permissible as an intermediate proposal; writing it over eyes, lips, original jawline, clothing, or background is not.
4. Composite only verified repair regions into the original photo with inward-only feather. Outside-mask and protected feature pixels must remain byte-identical. Preserve the original photo's tone, blur/noise, resolution, and identity.
5. New jaw silhouettes hidden by a beard require a separately verified generative geometry proposal. Landmarks are estimates, not ground truth. Do not silently reshape the chin.

## Initial implementation

- `Lip360Geometry.kt` — pure Kotlin closed-lip-contour geometry, 360° polar diagnostics, protected lip/face-edge checks, and safe lower-face search.
- `Lip360Landmarks.kt` — MediaPipe adapter converting real face landmarks to the 360° geometry.
- `Lip360GeometryTest.kt` — five JVM tests for eight directions, lip/nose/jaw protection, angle invariance, and proportional scaling.
- `Lip360DevicePrototypeTest.kt` — research-only Android smoke test of search coverage and angular sectors on optional local fixtures. Skips if private data is not present.
- `V5LamaAndroidRuntimeTest.kt` — optional research-only ONNX Runtime test. Requires a separately supplied `lama_lab_only.onnx` in androidTest assets; skips without it. **No LaMa or research-only beard model is included in the production APK.**

## Initial observations and release gate

PC experiments using an offline full-face-context LaMa reconstruction and learned beard proposal improve search coverage beyond fixed rectangular masks, but the resulting skin does not yet consistently match the existing offline AttGAN baseline or look natural on dense beards. Large beard silhouettes below the predicted jawline remain unsolved. Mobile inference for the tested 102 MB LaMa model did not return in an acceptable timeframe on the test device; further optimization or another compact reconstruction model is required.

**Do not enable V5 in the production retouch UI, ship research-trained model weights, or replace the verified AttGAN baseline until:**

- representative before/after pairs from multiple independently selected people pass blinded visual review;
- beard mask precision/recall and edge safety are checked, not inferred from a couple of examples;
- the candidate removes more facial hair than baseline without flat skin, color cast, distorted lips/jaw, sharpness changes, or identity drift;
- Android memory use, processing time, cancellation, and performance with high-resolution photos are validated;
- third-party model/data licenses are verified.

The image datasets, user photos, generated comparisons, and local lab report intentionally remain in the local (ignored) `research/clean_shave_lab` workspace. Do not upload them to GitHub or bundle into public APKs.
