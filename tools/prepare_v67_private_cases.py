"""Prepare authorized local evaluation images outside Git; never uploads or modifies sources."""
import argparse
import hashlib
import json
from pathlib import Path

import mediapipe as mp
import numpy as np
from PIL import Image, ImageOps


def main():
    cli=argparse.ArgumentParser(description=__doc__)
    cli.add_argument("--input",type=Path,nargs="+",required=True)
    cli.add_argument("--output",type=Path,required=True)
    cli.add_argument("--landmarker",type=Path,required=True)
    args=cli.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    manifest=[]
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for i,path in enumerate(args.input):
            if args.output.resolve()==path.parent.resolve() or path.parent.resolve() in args.output.resolve().parents:
                raise ValueError("Use a separate private output directory")
            digest=hashlib.sha256(path.read_bytes()).hexdigest()
            rgb=np.array(ImageOps.exif_transpose(Image.open(path)).convert("RGB"))
            found=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not found: raise ValueError(f"No face in {path.name}")
            name=f"case-{i+1}"
            Image.fromarray(rgb).save(args.output/f"{name}.png")
            (args.output/f"{name}.json").write_text(json.dumps([[p.x,p.y,p.z] for p in found[0]]),encoding="utf-8")
            assert hashlib.sha256(path.read_bytes()).hexdigest()==digest
            manifest.append(dict(name=name,source=path.name,source_sha256=digest,source_unchanged=True))
    (args.output/"manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    print(f"Prepared {len(manifest)} local-only cases")


if __name__=="__main__": main()
