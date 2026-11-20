# SDXL retouch evaluation - 2026-11-20

## Decision

Research preview only. Do not replace Keeper's production AttGAN model yet.
SDXL with a revised anatomical mask removes more of the lower-chin beard in the
private test set, but some outputs still look overly smooth or have flattened
shadows. This does not meet the requested no-review-needed quality bar. There is
no claim of 100% reliability or proven identity preservation within edited skin.

No Android runtime, database, user image or app version was changed for this
evaluation. No new APK is published. Personal inputs, generated comparisons and
model weights remain outside Git and release assets.

## Completed Experiments

- Prepared face-aligned masks for ten private source photos.
- Pilot 1: three lighting conditions, three denoising strengths (0.65/0.85/0.99),
  nine outputs. The original mask missed beard extending below the detected chin.
- Pilot 2: same three photos at 0.85/0.99, six outputs. Extended the lower-jaw
  region by 0.35 mouth-width units and protected semantic facial parts/clothing
  with the existing BiSeNet parser, without requiring a positive skin label in
  shadowed areas.
- Validation: ten photos at strength 0.85, 1024-square aligned context, 25 nominal
  steps, guidance 6, seed 42. All ten completed. Strength here is diffusion
  denoising strength, not AttGAN's preview blending opacity.
- Every completed output checked exact RGB preservation outside the selected
  mask and in protected regions. Source-file SHA256 checks passed for all ten.
  These are compositing safeguards, not proof of aesthetic quality or anatomical
  correctness of the segmentation.

The old AttGAN baseline and new candidate use different masks: this is an
end-to-end workflow comparison, not an isolated model-quality experiment. There
is no clean-shaven paired ground truth, objective beard-removal success score,
blind human study, or completed mobile performance benchmark.

## Measured Desktop Results

Final ten-image validation on RTX 5050 Laptop GPU with CPU model offload:

| Measurement | Result |
|---|---|
| Model download (weights/config/tokenizers) | 6,941,223,330 bytes, approximately 6.94 GB |
| Mean inference time | 19.23 seconds/image |
| Observed inference range | 18.77-22.00 seconds/image |
| Peak CUDA allocation | 5.37 GiB |
| Peak CUDA reservation | 5.74 GiB |
| Peak host process working set | 8.97 GiB |
| Original source files changed | None |

Timing excludes initial model load and post-inference image writing. Host/GPU
figures are desktop measurements with offloading, not Android requirements and
must not be simply added to estimate phone RAM. The connected phone reports
approximately 12 GB physical RAM with about 4 GB available at one observation.
Storage capacity alone is not evidence that this configuration will run on it.

## Reproduction

Use a separate virtual environment. The tested CUDA stack was PyTorch
2.9.1+cu128, torchvision 0.24.1+cu128, Diffusers 0.35.2, Transformers 4.57.3,
Accelerate 1.11.0, huggingface-hub 0.36.0 and safetensors 0.6.2. Supporting
versions: NumPy 2.4.6, MediaPipe 1.2.3, Pillow 11.3.0, ONNX Runtime 1.26.0 and
psutil 7.2.2. The existing global CPU environment was left unchanged.

```powershell
python tools/retouch_diffusion_trial.py download --model <private-model-directory>
python tools/retouch_diffusion_trial.py prepare --input <private-photos> --output <private-mask-review> --landmarker app/src/main/assets/retouch/face_landmarker.task
python tools/retouch_diffusion_trial.py run --input <private-photos> --output <private-results> --model <private-model-directory> --landmarker app/src/main/assets/retouch/face_landmarker.task --parser app/src/main/assets/retouch/face_parser.onnx --jaw-margin 0.35 --strengths 0.85 --baseline <private-existing-device-results>
```

Inference uses local files only and disables Hub/Transformers network access.
Downloading public model/dependency files is separate from inference; photos are
never uploaded. Use only the official public model revision pinned in the tool.
Large-file downloads initially encountered transport errors. The optional
`download_verified_ranges.py` helper checks HTTP byte ranges, preserves completed
parts for resuming, and requires the final whole-file SHA256 before installing
the download. After a verified alternative download, the `manifest` command can
record local file hashes. Downloaded code/weights must not be added to this repo.

Model/data redistribution and usage terms still need review before mobile
distribution. The next mobile stage requires a bounded-memory/quantized runtime
prototype plus revalidation that quantization does not degrade these results.

## Primary Sources

- Model and OpenRAIL++ license reference: https://huggingface.co/diffusers/stable-diffusion-xl-1.0-inpainting-0.1
- Pinned revision: `115134f363124c53c7d878647567d04daf26e41e`
- Diffusers memory/offload documentation: https://huggingface.co/docs/diffusers/optimization/memory
- Candidate native runtime, not yet integrated/tested here: https://github.com/leejet/stable-diffusion.cpp
