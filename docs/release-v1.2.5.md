# Image Keeper v1.2.5

- One-button offline photo retouch: analyse, protect, retouch, harmonise, and preview.
- Keep AttGAN and add a compact BiSeNet ResNet18 anatomical parsing model.
- Protect predicted eyes, brows, glasses, ears, nose, mouth, lips, clothing and background.
  Allow edits only within the separate moustache/lower-jaw mask.
- Select cheek colour samples from predicted skin; adapt left/right matching when
  illumination differs. Bound local colour/brightness shifts and recover fine texture.
- Show processing stages. Stop safely when usable edit/reference skin cannot be found.
- Reset stale previews before reruns, preventing accidental saving of an older result
  after a failed attempt. Original files remain unchanged; save only a separate copy.

This is an automatic model-assisted pipeline with deterministic tool/safety decisions,
not an on-device conversational agent. BiSeNet parses facial anatomy, not beard hair.
Skin softness and residual hair remain possible; review before saving.

All models are bundled. No inference API, photo uploads, subscription or usage quota.
Private sample photos are never included in source commits or release assets.

Validation: four local colour-guard tests and four Android instrumentation tests
passed. The device suite includes the real parser and AttGAN models, all ten
separately supplied private sample photos, three region modes on the first photo,
progress-stage order, unchanged input bitmaps, protected nose/lip pixels and
background checks. Normal runs on Infinix X6856 / Android 16 took about 2.5-2.9
seconds; the first retouch in a fresh process took about 9 seconds.
All ten generated device previews were visually reviewed; this is not a guarantee
of realistic output on every person, lighting condition or image.
