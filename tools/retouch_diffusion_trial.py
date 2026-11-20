"""Private, local-only SDXL inpainting evaluation; never modifies input photos."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time

MODEL_ID = "diffusers/stable-diffusion-xl-1.0-inpainting-0.1"
REVISION = "115134f363124c53c7d878647567d04daf26e41e"
PATTERNS = ["model_index.json", "README.md", "*/config.json", "scheduler/*.json",
            "tokenizer/*", "tokenizer_2/*", "*/*.fp16.safetensors"]


def download(destination):
    from huggingface_hub import snapshot_download
    snapshot_download(MODEL_ID, revision=REVISION, local_dir=str(destination),
                      allow_patterns=PATTERNS, token=False, max_workers=2)
    write_manifest(destination)


def write_manifest(destination):
    required = ["text_encoder/model.fp16.safetensors", "text_encoder_2/model.fp16.safetensors",
                "unet/diffusion_pytorch_model.fp16.safetensors", "vae/diffusion_pytorch_model.fp16.safetensors"]
    if any(not (destination / name).is_file() for name in required):
        raise ValueError("Download all four model components before creating the manifest")
    manifest = []
    for file in sorted(destination.rglob("*")):
        if not file.is_file() or ".cache" in file.parts or ".parts-" in str(file) or file.name == "keeper-manifest.json":
            continue
        digest = hashlib.sha256()
        with file.open("rb") as stream:
            for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
                digest.update(chunk)
        manifest.append(dict(path=file.relative_to(destination).as_posix(), bytes=file.stat().st_size,
                             sha256=digest.hexdigest()))
    (destination / "keeper-manifest.json").write_text(json.dumps(dict(
        model=MODEL_ID, revision=REVISION, files=manifest), indent=2), encoding="utf-8")
    print(json.dumps(dict(model=MODEL_ID, revision=REVISION,
                          bytes=sum(row["bytes"] for row in manifest))), flush=True)


def geometry(rgb, landmarks, size, jaw_margin=0., parser=None):
    import cv2
    import numpy as np
    height, width = rgb.shape[:2]
    p = np.array([(q.x * width, q.y * height) for q in landmarks], np.float32)
    eye_l = (p[33] + p[133]) / 2
    eye_r = (p[362] + p[263]) / 2
    dx, dy = eye_r - eye_l
    norm2 = float(dx * dx + dy * dy)
    if norm2 < 4:
        raise ValueError("Face is too small")
    a, b = dx * size * .25 / norm2, dy * size * .25 / norm2
    linear = np.array([[a, b], [-b, a]], np.float32)
    transform = np.column_stack((linear, np.array([size * .5, size * .40]) - linear @ ((eye_l + eye_r) / 2))).astype(np.float32)
    p = cv2.transform(p[None], transform)[0]
    span = float(np.linalg.norm(p[291] - p[61]))
    mask = np.zeros((size, size), np.uint8)
    mouth = p[[61, 40, 37, 0, 267, 270, 291]].copy()
    mouth[:, 1] -= max(2, span * .035)
    # Use anatomical regions, not darkness thresholds, so shadows do not create gaps.
    nose = p[2]
    cv2.fillPoly(mask, [np.concatenate((mouth, [[mouth[-1, 0], nose[1]], [mouth[0, 0], nose[1]]])).astype(np.int32)], 255)
    jaw = p[[58, 172, 136, 150, 149, 176, 148, 152, 377, 400, 378, 379, 365, 397, 288]]
    cv2.fillPoly(mask, [np.concatenate((jaw, [p[291], p[17], p[61]])).astype(np.int32)], 255)
    if jaw_margin > 0:
        cv2.fillPoly(mask,[np.concatenate((jaw,jaw[::-1]+[0,span*jaw_margin])).astype(np.int32)],255)
    radius = max(2, int(span * .10))
    mask = cv2.dilate(mask, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (radius * 2 + 1,) * 2))
    face_hull = np.zeros_like(mask)
    cv2.fillConvexPoly(face_hull, cv2.convexHull(p[:468].astype(np.int32)), 255)
    if jaw_margin > 0:
        extension=max(1,int(span*jaw_margin))
        face_hull=cv2.dilate(face_hull,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(extension*2+1,)*2))
    mask = cv2.bitwise_and(mask, face_hull)
    protected = np.zeros_like(mask)
    for indices in ([61, 40, 37, 0, 267, 270, 291, 321, 314, 17, 84, 91], [98, 2, 327, 4]):
        cv2.fillPoly(protected, [p[indices].astype(np.int32)], 255)
    protected = cv2.dilate(protected, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3)))
    mask[protected > 0] = 0
    aligned = cv2.warpAffine(rgb, transform, (size, size), borderMode=cv2.BORDER_REFLECT_101)
    if parser is not None:
        value=cv2.resize(aligned,(512,512)).astype(np.float32)/255
        value=(value-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)
        value=np.ascontiguousarray(value.transpose(2,0,1)[None])
        labels=parser.run(None,{parser.get_inputs()[0].name:value})[0].squeeze().astype(np.uint8)
        labels=cv2.resize(labels,(size,size),interpolation=cv2.INTER_NEAREST)
        # Protect semantic anatomy and clothing, without demanding a confident skin label in shadows.
        protected[np.isin(labels,[2,3,4,5,6,7,8,9,10,11,12,13,15,16,18])]=255
        mask[protected>0]=0
    inverse = cv2.invertAffineTransform(transform)
    original_mask = cv2.warpAffine(mask, inverse, (width, height), flags=cv2.INTER_NEAREST)
    original_protected = cv2.warpAffine(protected, inverse, (width, height), flags=cv2.INTER_NEAREST)
    original_mask[original_protected > 0] = 0
    if np.count_nonzero(original_mask) < 100:
        raise ValueError("Editable face area is too small")
    assert not np.any(original_mask[original_protected > 0])
    return aligned, mask, original_mask, original_protected, transform, span / float((a*a+b*b)**.5)


def run(args):
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import cv2
    import mediapipe as mp
    import numpy as np
    import torch
    import psutil
    from PIL import Image, ImageDraw, ImageOps
    from diffusers import StableDiffusionXLInpaintPipeline
    source_dir, output = args.input.resolve(), args.output.resolve()
    if source_dir == output or source_dir in output.parents or output in source_dir.parents:
        raise ValueError("Output must be separate from the input directory")
    if args.size % 64 or not 512 <= args.size <= 1024:
        raise ValueError("Size must be 512-1024 and divisible by 64")
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is required for this desktop trial; no silent CPU fallback")
    manifest = json.loads((args.model / "keeper-manifest.json").read_text(encoding="utf-8"))
    if manifest["model"] != MODEL_ID or manifest["revision"] != REVISION:
        raise ValueError("Model manifest does not match the pinned trial revision")
    for row in manifest["files"]:
        file = args.model / row["path"]
        if not file.is_file() or file.stat().st_size != row["bytes"]:
            raise ValueError(f"Incomplete model file: {row['path']}")
    output.mkdir(parents=True, exist_ok=True)
    files = [source_dir / name for name in args.files] if args.files else sorted(source_dir.glob("18807*_0.jpg"))
    if any(file.parent != source_dir for file in files):
        raise ValueError("Use filenames from the supplied input directory only")
    hashes = {file.name: hashlib.sha256(file.read_bytes()).hexdigest() for file in files}
    started = time.perf_counter()
    pipe = StableDiffusionXLInpaintPipeline.from_pretrained(str(args.model),
        torch_dtype=torch.float16, variant="fp16", use_safetensors=True, local_files_only=True,
        add_watermarker=False)
    pipe.enable_model_cpu_offload()
    pipe.enable_vae_tiling()
    pipe.enable_vae_slicing()
    prompt = ("photograph of the same person, clean shaven lower face, natural smooth skin with fine pores, "
              "consistent natural skin tone and existing lighting, realistic facial anatomy")
    negative = ("beard, mustache, moustache, stubble, facial hair, different person, altered lips, "
                "plastic skin, wax, white patch, makeup, beauty filter, deformed face, oversmoothed")
    report = dict(model=MODEL_ID, revision=REVISION, device=torch.cuda.get_device_name(),
        torch=torch.__version__, size=args.size, steps=args.steps, seed=args.seed, guidance=args.guidance,
        prompt=prompt, negative_prompt=negative, model_load_seconds=time.perf_counter()-started, results=[])
    options = mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)), num_faces=1)
    face_parser=None
    if args.parser:
        import onnxruntime as ort
        settings=ort.SessionOptions()
        settings.intra_op_num_threads=2
        face_parser=ort.InferenceSession(str(args.parser),sess_options=settings,providers=["CPUExecutionProvider"])
    report["jaw_margin"]=args.jaw_margin
    report["semantic_protection"]=args.parser is not None
    try:
        with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
            for file in files:
                rgb = np.array(ImageOps.exif_transpose(Image.open(file)).convert("RGB"))
                faces = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)).face_landmarks
                if not faces:
                    report["results"].append(dict(file=file.name, status="no_face"))
                    continue
                aligned, aligned_mask, mask, protected, transform, span = geometry(rgb, faces[0], args.size,args.jaw_margin,face_parser)
                Image.fromarray(aligned_mask).save(output / f"{file.stem}_mask.png")
                panels = [("Original", aligned)]
                if args.baseline:
                    baseline_file = args.baseline / f"{file.stem}.png"
                    if baseline_file.is_file():
                        base = np.array(Image.open(baseline_file).convert("RGB"))
                        if base.shape != rgb.shape:
                            raise ValueError("Baseline must have the original dimensions")
                        panels.append(("Current AttGAN", cv2.warpAffine(base, transform, (args.size, args.size), borderMode=cv2.BORDER_REFLECT_101)))
                for strength in args.strengths:
                    torch.cuda.reset_peak_memory_stats()
                    tick = time.perf_counter()
                    with torch.inference_mode():
                        generated = pipe(prompt=prompt, negative_prompt=negative,
                            image=Image.fromarray(aligned), mask_image=Image.fromarray(aligned_mask),
                            width=args.size, height=args.size, strength=strength, guidance_scale=args.guidance,
                            num_inference_steps=args.steps, generator=torch.Generator("cpu").manual_seed(args.seed)).images[0]
                    torch.cuda.synchronize()
                    seconds = time.perf_counter()-tick
                    generated = np.array(generated.convert("RGB"))
                    restored = cv2.warpAffine(generated, cv2.invertAffineTransform(transform),
                        (rgb.shape[1], rgb.shape[0]), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT_101)
                    distance = cv2.distanceTransform(mask, cv2.DIST_L2, 5)
                    alpha = np.minimum(distance / max(2., span * .045), 1.)[:, :, None]
                    result = np.rint(rgb.astype(np.float32) * (1-alpha) + restored * alpha).clip(0,255).astype(np.uint8)
                    assert np.array_equal(result[mask == 0], rgb[mask == 0])
                    assert np.array_equal(result[protected > 0], rgb[protected > 0])
                    name = f"{file.stem}_sdxl_{strength:.2f}.png"
                    Image.fromarray(result).save(output / name)
                    panels.append((f"SDXL {strength:.2f}", cv2.warpAffine(result, transform, (args.size,args.size), borderMode=cv2.BORDER_REFLECT_101)))
                    row = dict(file=file.name, status="completed", strength=strength, seconds=seconds,
                        peak_cuda_allocated_gib=torch.cuda.max_memory_allocated()/1024**3,
                        peak_cuda_reserved_gib=torch.cuda.max_memory_reserved()/1024**3,
                        peak_host_rss_gib=getattr(psutil.Process().memory_info(),"peak_wset",psutil.Process().memory_info().rss)/1024**3,
                        changed_pixels=int(np.any(result != rgb, axis=2).sum()), outside_mask_unchanged=True,
                        protected_pixels_unchanged=True, output=name)
                    report["results"].append(row)
                    (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
                    print(json.dumps(row), flush=True)
                sheet = Image.new("RGB", (384*len(panels), 424), "#eef0f5")
                draw = ImageDraw.Draw(sheet)
                for index, (label, image) in enumerate(panels):
                    sheet.paste(Image.fromarray(image).resize((384,384)), (index*384,40))
                    draw.text((index*384+12,12),label,fill="#14213d")
                sheet.save(output / f"{file.stem}_compare.png")
    finally:
        report["source_hashes_unchanged"] = all(hashlib.sha256(file.read_bytes()).hexdigest()==hashes[file.name] for file in files)
        report["source_sha256"] = hashes
        (output / "report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        if not report["source_hashes_unchanged"]:
            raise RuntimeError("Source files changed during the trial")


def prepare(args):
    import mediapipe as mp
    import numpy as np
    from PIL import Image, ImageDraw, ImageOps
    if args.input.resolve() == args.output.resolve() or args.input.resolve() in args.output.resolve().parents:
        raise ValueError("Use a separate output directory")
    args.output.mkdir(parents=True, exist_ok=True)
    options = mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for file in sorted(args.input.glob("18807*_0.jpg")):
            digest = hashlib.sha256(file.read_bytes()).hexdigest()
            rgb = np.array(ImageOps.exif_transpose(Image.open(file)).convert("RGB"))
            faces = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not faces:
                continue
            aligned, mask, _, _, _, _ = geometry(rgb,faces[0],1024)
            overlay = aligned.copy()
            overlay[mask>0] = (overlay[mask>0].astype(float)*.6 + np.array([50,220,160])*.4).astype(np.uint8)
            sheet = Image.new("RGB",(1024,552),"#eef0f5")
            sheet.paste(Image.fromarray(aligned).resize((512,512)),(0,40))
            sheet.paste(Image.fromarray(overlay).resize((512,512)),(512,40))
            ImageDraw.Draw(sheet).text((12,12),"Original aligned / editable region (eyes, lips and nose protected)",fill="#14213d")
            sheet.save(args.output/f"{file.stem}_mask-review.png")
            assert hashlib.sha256(file.read_bytes()).hexdigest()==digest
    print("Prepared private mask previews; no inference and no input modifications.",flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    dl = commands.add_parser("download")
    dl.add_argument("--model", type=Path, required=True)
    verify = commands.add_parser("manifest")
    verify.add_argument("--model", type=Path, required=True)
    prep = commands.add_parser("prepare")
    for name in ("input", "output", "landmarker"):
        prep.add_argument(f"--{name}", type=Path, required=True)
    trial = commands.add_parser("run")
    for name in ("input", "output", "model", "landmarker"):
        trial.add_argument(f"--{name}", type=Path, required=True)
    trial.add_argument("--baseline", type=Path)
    trial.add_argument("--files", nargs="*")
    trial.add_argument("--strengths", nargs="+", type=float, default=[.65,.85,.99])
    trial.add_argument("--size", type=int, default=1024)
    trial.add_argument("--steps", type=int, default=25)
    trial.add_argument("--seed", type=int, default=42)
    trial.add_argument("--guidance", type=float, default=6.)
    trial.add_argument("--jaw-margin",type=float,default=0.)
    trial.add_argument("--parser",type=Path)
    options = parser.parse_args()
    if options.command == "download":
        download(options.model)
    elif options.command == "manifest":
        write_manifest(options.model)
    elif options.command == "prepare":
        prepare(options)
    else:
        if not all(0 < value <= 1 for value in options.strengths):
            parser.error("Strength must be greater than zero and at most one")
        if not 0 <= options.jaw_margin <= .6 or options.jaw_margin > 0 and not options.parser:
            parser.error("Expanded jaw masks require --parser and a margin from 0 to 0.6")
        run(options)
