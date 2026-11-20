# Offline retouch follow-up - 2026-11-20

## Status

Research only, not a production model replacement. The user rejected SDXL's
flat/waxy skin. This follow-up tests instruction editing with FLUX.2 Klein 4B,
then local camera-color matching and screened gradient compositing. Visual
inspection suggests better preservation of natural folds and lighting than the
previous SDXL trial, but residual upper-lip hair/shading and occasional generated
skin changes still need evaluation. No claim of universally natural results,
100% beard removal, or proven identity preservation is made.

Keeper's Android code, version, database and stored images are unchanged. There
is no new APK. All private photographs, generated images, model weights and
temporary downloads stay outside Git and release assets. Inference is local;
Hub and Transformers network access is disabled during each model trial.

## What Was Tested

- SDXL camera-style prompts, lower guidance, tighter masked context, and two
  denoising strengths on three photos: did not sufficiently fix flat skin.
- SDXL Poisson blending, bounded microtexture and low-frequency shading repair:
  blending cannot recover missing plausible texture. Mixing original skin back
  in also brings facial hair back; rejected as a primary solution.
- Klein: ten source photos, two fixed prompts, seed 42, 768-square eye-aligned
  reference, four distilled steps, guidance 1. All 20 edits completed.
- Camera-color fitting and gradient matching compared on those 20 saved edits,
  without regenerating or cherry-picking seeds for individual photos.
- Plain feather blending caused a visible cheek color boundary. Ordinary
  Poisson blending and wide feathering reduced the boundary but pulled original
  moustache darkness back into narrow upper-lip areas.
- Screened Poisson compositing adds an interior generated-color constraint
  (strength 0.08), while retaining original boundary pixels. A robust bounded
  Lab camera-color field is fitted only on unedited reference skin. This avoids
  directly pasting original beard texture into the replacement skin.
- A second ten-photo run uses seed 77 and the same prompt across all images,
  with the screened compositor, to test repeatability rather than selecting a
  favorable prompt per image. All ten completed, with source-hash and pixel
  safeguards passing.
- A stronger third instruction was tested on three lighting conditions. It
  improved some residual hair but introduced obvious relighting/patch artifacts
  and changed mouth appearance in the backlit case. Rejected as a default.
- An opt-in tighter lip-protection mask was compared on the original 20 edits
  and the three strong-instruction edits. It erodes the landmark lip polygon
  while preserving its core, rather than retaining the whole semantic lip
  region. It therefore does NOT guarantee original lip-boundary pixels remain
  unchanged. It remains experimental and is not the production geometry.
- A softer screening weight (0.005) was compared on the three strong edits. It
  did not sufficiently repair their relighting artifacts. Strong-prompt outputs
  are not approved; a good-looking favorable example must not hide this failure.

Source SHA256 checks and exact RGB preservation outside the editable mask and
in protected anatomy are verified for completed outputs. Protected areas cover
eyes, glasses, nose, lips and clothing using landmarks and BiSeNet. These masks
can themselves be imperfect. Geometry checks reject excessive eye/nose anchor
drift; passing them does not prove all edited facial geometry is unchanged.

OpenCV's `seamlessClone` was observed mutating its mask argument in place.
Every call now receives a copy, with regression coverage for protected holes.

## Desktop Measurements

RTX 5050 Laptop GPU, 8 GB VRAM. The first complete 20-edit Klein validation:

| Measurement | Observed |
|---|---|
| Downloaded checkpoint/config/tokenizers | 4,832,210,098 bytes |
| Mean model inference | 9.43 seconds/image |
| Model inference range | 9.38-9.75 seconds/image |
| Peak allocated CUDA memory | approximately 5.86 GiB |
| Input photo files modified | None |

Inference timings exclude loading, segmentation, alignment, color matching,
screened sparse solving and file writing. Do not present these as end-to-end
latency or Android timings. This checkpoint uses NVIDIA-specific bitsandbytes
NF4; installing its weights in Android is not sufficient to run it. Mobile
runtime conversion, memory, thermal behavior and output quality are untested.

## Provenance And Reproduction

Upstream: `black-forest-labs/FLUX.2-klein-4B`. The evaluated checkpoint is a
community NF4 conversion, not an official upstream mobile artifact:
`hlhc/FLUX.2-klein-4B-bnb-4bit`, pinned revision
`199c1411ab9dc4111e9fe7e8e3dc3fc4e63c2648`. Its model card identifies Apache-2.0
and the upstream base. Production distribution still requires license/provenance
review and ideally a reproducible conversion from official weights. No remote
model Python code is enabled. LFS SHA256 hashes are verified on download.

Test environment: Python 3.12, torch 2.9.1+cu128, torchvision 0.24.1+cu128,
diffusers 0.40.0, transformers 5.17.0, bitsandbytes 0.49.2, huggingface-hub
1.33.0, safetensors 0.9.0rc1, scipy 1.18.1, NumPy 2.4.6, MediaPipe 1.2.3,
Pillow 11.3.0, ONNX Runtime 1.26.0. Separate research environments keep the
existing app and SDXL environment unchanged.

```powershell
python tools/retouch_klein_trial.py download --model <private-model>
python tools/retouch_klein_trial.py run --input <private-photos> --output <private-results> --model <private-model> --landmarker app/src/main/assets/retouch/face_landmarker.task --parser app/src/main/assets/retouch/face_parser.onnx --composite screened --prompt-index 1 --seed 77
python tools/retouch_klein_composite_trial.py --input <private-photos> --raw <private-results-with-raw-edits> --output <private-comparison-directory> --landmarker app/src/main/assets/retouch/face_landmarker.task --parser app/src/main/assets/retouch/face_parser.onnx
python -m unittest discover -s tools -p 'test_retouch_*.py' -v
```

The optional third fixed instruction explicitly requests removal of residual
stubble; invoke `--prompts 3 --prompt-index 2`. Stronger wording is an experiment,
not a guarantee, and the three-image pilot was rejected as a default. The
compositor's `--tight-lips` switch is an additional opt-in geometry experiment.

Six compositing regression tests pass. There is no clean-shaven paired ground
truth, blind human evaluation, or automated trustworthy aesthetic acceptance
score. User-visible comparisons, not the passing safeguards, determine whether
the result is acceptable.

## Sources

- https://huggingface.co/black-forest-labs/FLUX.2-klein-4B
- https://huggingface.co/hlhc/FLUX.2-klein-4B-bnb-4bit/tree/199c1411ab9dc4111e9fe7e8e3dc3fc4e63c2648
- https://github.com/huggingface/diffusers

Next gates: verify residual-hair handling without changing lip anatomy, evaluate
more people/lighting, then prove an offline Android runtime on the test phone.
Do not replace the production model solely on these desktop experiments.
