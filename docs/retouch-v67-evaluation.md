# Smart Shave V6.7 research evaluation

Date: 2026-11-23

## Status

Experimental, opt-in and OFF by default. This is not a production-quality
clean-shave solution and is not claimed to match Meitu. V6.6 remains the
private-build default. Existing original images are not replaced by processing;
the user reviews the result before saving.

## Implementation

- Preserve original protected lip/nose pixels and refine lip-edge blending.
- Restrict focused residual repair to learned hair evidence; allow clean skin
  donors independently and reject ambiguous round dark spots.
- Keep dense-beard generation inside a source-derived, inward-feathered matte.
- Gate upper-lip specialist edits using residual hair confidence.
- Match bounded neck color offsets using visible low-hair-confidence skin.
- Keep the old path available for direct comparison without additional
  generative passes.

A strict original-landmark face-oval gate was tested and rejected as a default:
it restores visible beard bands where hair extends past the anatomical oval.
It remains diagnostic-only. Landmarks do not reveal the true hidden chin.

## Evidence and limitations

Existing Meitu client research indicates distinct facial-hair task categories
and a server-backed task registration. It does not recover the model or prove
the active runtime implementation. No proprietary endpoints, binaries or
weights were reused. The compositor here is independently implemented.

Three local fixtures were compared with identical one-pass settings. One is
a screenshot proxy rather than a camera original. V6.7 end-to-end times were
5.019, 5.972 and 6.739 seconds on the x86_64 Android emulator, NOT a phone
benchmark. Original bitmap and protected-pixel assertions passed. Visual
inspection still shows residual hair, over-smooth skin and visible skin
transitions. Fewer changed pixels is not proof of better appearance.

42 JVM tests passed. Eleven focused Android regression tests passed, plus
one three-fixture comparison test. Debug and release builds compiled.
The Android pipeline tests use fixed original landmarks, so they do not
validate native face detection on an ARM64 device. No physical device was
installed or tested during this evaluation.

## Distribution

Source-only research prerelease. Research-only FaceBench and beard-segmentation
weights are not redistributed. No private APKs, personal photographs, backups
or evaluation assets are published. Private debug weights are not part of the
release variant; builds without research assets fall back to the baseline UI.
The retouch pipeline is offline; normal application features may use Internet.

Further quality work needs a better clean-shave proposal and a reliable beard
boundary/lighting model, not simply a wider mask or stronger blur. Tilt-aware
neck reference sampling and broader authorized photo evaluation remain open.
