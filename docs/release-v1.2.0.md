# Image Keeper v1.2.0

Adds an experimental, offline upper-lip moustache retouch tool to Photo Viewer.

## Usage

Open a saved photo, select "แต่งภาพ · ลดหนวด", choose a numbered face if
needed, and run automatic retouch. Compare before/after, adjust strength,
then save a separate edited copy. The original image and its database record
are not replaced. The copy preserves branch/account, capture route, capture
timestamp, month, and tags, and records its source ID in the note. The copy
can use existing Gallery, GhostShift, Timestamp and ZIP-transfer actions.

## Implementation and limits

- MediaPipe Face Landmarker 0.10.21 locates facial anatomy.
- The mask is a conservative upper-lip landmark heuristic, not a trained
  facial-hair segmentation model. It does not automatically classify hairs.
- MI-GAN Places2 ONNX pipeline v2 fills the mask through ONNX Runtime 1.20.0.
  This is the publisher's available preconverted general inpainting pipeline,
  not the FFHQ model and not a dedicated beard-removal model.
- The generated crop is composited through a feathered local mask.
- Decoding is bounded to a 2560-pixel long edge using power-of-two sampling.
  Very large originals may therefore yield a lower-resolution edited copy.
- Intended for visible, approximately frontal upper lips. Dense facial hair,
  face angles and unusual lighting can produce artifacts. Inspect each result.
- Up to five faces can be selected by numbered guides; no face recognition.
- Models and notices are bundled under assets/retouch; images are not uploaded.

## Verification

Release build, release vital lint, and existing unit tests passed.
The bundled ONNX model was loaded on desktop CPU and ran a synthetic masked
512x512 RGB input; its output had shape 1x3x512x512 and uint8 type.
Model source URLs and SHA256 hashes are recorded in assets/retouch/SOURCES.txt.
No connected Android device was available for runtime or visual quality tests.
No claim of beard-removal quality or measured phone latency is made yet.
