# Offline retouch trials

These are desktop research tools supporting the Android integration. They never upload
photos. Keep private inputs, generated comparisons, downloaded code and weights
outside the repository. Do not attach them to an application release.

## Candidates

- `retouch_trial.py`: bounded luminance closing, three strengths.
- `retouch_methods_trial.py`: sparse Navier-Stokes, full Telea, bounded cheek-colour
  correction, and optional existing Places2 MI-GAN inference.
- `retouch_ffhq_trial.py`: official face-trained MI-GAN-256 weights, eye alignment,
  sparse/full masks and partial blending.
- `retouch_attgan_trial.py`: official AttGAN generator, targeted Mustache/No_Beard
  controls, edit-delta or colour-matched direct compositing, bounded fine texture
  and inward feathering. `--final-only` generates the selected direct blend family
  at 70%, 85%, and 100% without debug reconstruction images.
- `retouch_contact_sheet.py`: overview of completed comparison sheets.

All completed batches assert that source file hashes are unchanged and that pixels
outside the editable mask remain identical in uncompressed RGB. Later trials also
assert protected lip/nose pixels remain identical. These checks do not establish
visual quality, identity preservation throughout the edited area, or performance
on Android. There is no paired clean-shaven ground truth, so PSNR/SSIM would not
measure beard-removal success here.

## Sources

- OpenCV inpaint: https://docs.opencv.org/4.x/d7/d8b/group__photo__inpaint.html
- MI-GAN code/weights (MIT): https://github.com/Picsart-AI-Research/MI-GAN
- AttGAN implementation (MIT): https://github.com/elvisyjlin/AttGAN-PyTorch
- Mask Based Unsupervised Content Transfer (MIT):
  https://github.com/rmokady/mbu-content-tansfer
  Its linked moustache checkpoint returned HTTP 404 during this trial and was not tested.

The upstream model/data terms must be reviewed separately before bundling any new
weights in Keeper. Source-code licensing alone does not settle model/data rights.

## Local Environment

Python 3.12, NumPy 2.4.6, OpenCV 5.0.0, MediaPipe 1.2.3, PyTorch 2.13.0+cpu.
PyTorch MKLDNN is disabled in the experiments because the enabled execution path
crashed on this desktop. Optional ONNX Places2 inference could not complete under
the current Windows memory-commit pressure; it is not a completed benchmark.

Example (use private paths outside the repository):

```powershell
python tools/retouch_attgan_trial.py --input <photos> --output <private-output> --lab <private-model-directory> --landmarker app/src/main/assets/retouch/face_landmarker.task
```

`<private-model-directory>` contains the original AttGAN checkout in `AttGAN/` and
the extracted official checkpoint archive in `attgan_weights/`. No auto-download
occurs during processing.

## Observed Results (2026-11-19)

Tested ten private photos with varied illumination and mild pose changes. Selected
AttGAN direct blending at approximately 85% as the strongest current candidate for
review, not as a scientifically established best method or a production-ready model.
Restricting the mask to perioral hair and lower jaw avoids unnecessary smoothing
on clean upper cheeks. Source files and protected RGB pixels passed checks on all
ten photos. Final desktop batches took roughly 1.4-1.7 seconds per photo after model
loading, including two generator calls, three blends, verification and image writes.
Android conversion and real-device checks were subsequently completed for v1.2.4;
see `docs/release-v1.2.4.md`. These desktop timings are not Android timings.

Qualitative findings:

- Luminance closing: too weak on dense chin hair.
- Sparse Navier-Stokes: useful for fine strands, leaves dense beard shadows.
- Full Telea: unacceptable flat patch/boundary and no facial prior.
- Cheek-colour correction: can lighten skin without actually removing dense hair.
- FFHQ MI-GAN with tested alignment/masks: unacceptable colour/shape artifacts.
- AttGAN edit delta: bright patches and mismatched texture; rejected.
- AttGAN direct blend with cheek colour matching: visibly stronger beard reduction,
  but softer skin, residual moustache/jaw hair, and potential local shade mismatch.
  100% can look waxy; 70-85% offers a more conservative compromise.

This benchmark contains only one subject and no clean-shaven reference. Do not infer
cross-user reliability or exact anatomical preservation in regenerated regions.
The user accepted this approach for integration. Runtime coverage and model
provenance are recorded separately in the release notes and bundled sources notice.
