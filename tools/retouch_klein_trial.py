"""Offline instruction-editing evaluation. Private images never leave this computer."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys
import time
from urllib.request import urlopen

REPO="hlhc/FLUX.2-klein-4B-bnb-4bit"
REVISION="199c1411ab9dc4111e9fe7e8e3dc3fc4e63c2648"


def download(folder):
    # A community NF4 conversion of the upstream Apache-2.0 model, for evaluation only.
    with urlopen(f"https://huggingface.co/api/models/{REPO}/revision/{REVISION}?blobs=true",timeout=60) as stream:
        metadata=json.load(stream)
    if metadata["sha"]!=REVISION:
        raise ValueError("Unexpected model revision")
    selected=[]
    for row in metadata["siblings"]:
        name=row["rfilename"]
        path=PurePosixPath(name)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Unsafe artifact path")
        if not (name.endswith((".json",".safetensors",".jinja")) or name in ("README.md","LICENSE.md")):
            continue
        target=folder.joinpath(*path.parts)
        target.parent.mkdir(parents=True,exist_ok=True)
        url=f"https://huggingface.co/{REPO}/resolve/{REVISION}/{name}"
        expected=row.get("lfs",{}).get("sha256")
        if not target.exists():
            if expected:
                subprocess.run([sys.executable,str(Path(__file__).with_name("download_verified_ranges.py")),
                    "--url",url,"--output",str(target),"--size",str(row["size"]),"--sha256",expected,"--workers","8"],check=True)
            else:
                with urlopen(url,timeout=120) as source, target.with_suffix(target.suffix+".download").open("wb") as out:
                    while data:=source.read(1024*1024): out.write(data)
                target.with_suffix(target.suffix+".download").replace(target)
        with target.open("rb") as source:
            digest=hashlib.file_digest(source,"sha256").hexdigest()
        if target.stat().st_size!=row["size"] or expected and digest!=expected:
            raise ValueError(f"Model integrity failure: {name}")
        selected.append(dict(path=name,bytes=target.stat().st_size,sha256=digest))
        print(f"Ready {name}",flush=True)
    (folder/"keeper-manifest.json").write_text(json.dumps(dict(repo=REPO,revision=REVISION,files=selected),indent=2),encoding="utf-8")


def run(args):
    os.environ["HF_HUB_OFFLINE"]="1"
    os.environ["TRANSFORMERS_OFFLINE"]="1"
    import cv2
    import mediapipe as mp
    import numpy as np
    import onnxruntime as ort
    import torch
    from PIL import Image, ImageDraw, ImageOps
    from diffusers import Flux2KleinPipeline
    from retouch_diffusion_trial import geometry
    from retouch_photometric import camera_match, inward_compose, screened_compose
    source_dir=args.input.resolve(); output=args.output.resolve()
    if source_dir==output or source_dir in output.parents or output in source_dir.parents:
        raise ValueError("Use a separate private output directory")
    manifest=json.loads((args.model/"keeper-manifest.json").read_text())
    if manifest["repo"]!=REPO or manifest["revision"]!=REVISION:
        raise ValueError("Unexpected checkpoint provenance")
    for row in manifest["files"]:
        if (args.model/row["path"]).stat().st_size!=row["bytes"]:
            raise ValueError("Incomplete checkpoint")
    if not torch.cuda.is_available(): raise RuntimeError("Desktop CUDA trial only")
    pipe=Flux2KleinPipeline.from_pretrained(str(args.model),torch_dtype=torch.bfloat16,
        local_files_only=True,use_safetensors=True).to("cuda")
    pipe.vae.enable_tiling()
    output.mkdir(parents=True,exist_ok=True)
    settings=ort.SessionOptions(); settings.intra_op_num_threads=2
    parsing=ort.InferenceSession(str(args.parser),sess_options=settings,providers=["CPUExecutionProvider"])
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    files=[source_dir/name for name in args.files] if args.files else sorted(source_dir.glob("18807*_0.jpg"))
    if any(file.parent!=source_dir for file in files): raise ValueError("Use source filenames only")
    hashes={file.name:hashlib.sha256(file.read_bytes()).hexdigest() for file in files}
    prompts=[
        "Remove only the moustache and beard from this photograph. Keep the same person, exact facial shape, lips, pose, framing, glasses, lighting, shadows, original skin tone and natural photographic skin texture. Do not smooth or beautify the skin. Everything else must remain unchanged.",
        "Make the person in this exact photo clean shaven. Remove the facial hair above the upper lip, on the chin and along the jaw. Preserve the original camera grain, skin imperfections and creases, shadow under the chin, exposure and skin color. No makeup, no beauty retouching, no face reshaping.",
        "Retouch this exact photograph to show the same person completely clean-shaven. Erase every moustache whisker above and beside the upper lip, all hair below the lower lip, all beard and dark stubble on the chin and under the jaw. Replace only the facial hair with realistic bare skin matching nearby skin texture and lighting. Retain the person's natural wrinkles, skin blemishes, asymmetry, facial proportions and original lips. Do not beautify, smooth skin, change face shape or relight the photograph."
    ]
    report=dict(repo=REPO,revision=REVISION,community_quantization=True,size=args.size,steps=4,seed=args.seed,
        composite=args.composite,results=[])
    try:
        with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
            for file in files:
                rgb=np.array(ImageOps.exif_transpose(Image.open(file)).convert("RGB"))
                faces=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
                if not faces:
                    report["results"].append(dict(file=file.name,status="no_face")); continue
                aligned,aligned_mask,mask,protected,matrix,span=geometry(rgb,faces[0],args.size,.35,parsing)
                panels={"Original":aligned}
                if args.previous:
                    previous=np.array(Image.open(args.previous/f"{file.stem}_sdxl_0.85.png").convert("RGB"))
                    panels["SDXL previous"]=cv2.warpAffine(previous,matrix,(args.size,args.size),borderMode=cv2.BORDER_REFLECT_101)
                for index,prompt in enumerate(prompts[:args.prompts]):
                    if args.prompt_index is not None and index!=args.prompt_index: continue
                    torch.cuda.reset_peak_memory_stats()
                    start=time.perf_counter()
                    with torch.inference_mode():
                        edited=pipe(image=Image.fromarray(aligned),prompt=prompt,height=args.size,width=args.size,
                            num_inference_steps=4,guidance_scale=1.,generator=torch.Generator("cpu").manual_seed(args.seed)).images[0]
                    torch.cuda.synchronize()
                    seconds=time.perf_counter()-start
                    generated=np.array(edited.convert("RGB"))
                    Image.fromarray(generated).save(output/f"{file.stem}_raw_p{index}.png")
                    detected=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=np.ascontiguousarray(generated))).face_landmarks
                    if not detected:
                        report["results"].append(dict(file=file.name,status="generated_face_not_found",prompt=prompt))
                        continue
                    anchors=[33,133,362,263,4,168]
                    expected=np.array([(faces[0][i].x*rgb.shape[1],faces[0][i].y*rgb.shape[0]) for i in anchors],np.float32)
                    expected=cv2.transform(expected[None],matrix)[0]
                    observed=np.array([(detected[0][i].x*args.size,detected[0][i].y*args.size) for i in anchors],np.float32)
                    alignment,_=cv2.estimateAffinePartial2D(observed,expected,method=cv2.LMEDS)
                    if alignment is None:
                        report["results"].append(dict(file=file.name,status="alignment_failed",prompt=prompt)); continue
                    scale=float(np.hypot(alignment[0,0],alignment[1,0]))
                    residual=float(np.median(np.linalg.norm(cv2.transform(observed[None],alignment)[0]-expected,axis=1)))
                    if not .92<=scale<=1.08 or abs(float(np.arctan2(alignment[1,0],alignment[0,0])))>.08 or residual>args.size*.015:
                        report["results"].append(dict(file=file.name,status="geometry_drift",prompt=prompt,scale=scale,residual=residual)); continue
                    generated=cv2.warpAffine(generated,alignment,(args.size,args.size),borderMode=cv2.BORDER_REFLECT_101)
                    if args.composite=="screened":
                        value=cv2.resize(aligned,(512,512)).astype(np.float32)/255
                        value=(value-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)
                        labels=parsing.run(None,{parsing.get_inputs()[0].name:np.ascontiguousarray(value.transpose(2,0,1)[None])})[0].squeeze().astype(np.uint8)
                        labels=cv2.resize(labels,(args.size,args.size),interpolation=cv2.INTER_NEAREST)
                        reference=np.where((labels==1)&(aligned_mask==0),255,0).astype(np.uint8)
                        generated=camera_match(aligned,generated,reference)
                    restored=cv2.warpAffine(generated,cv2.invertAffineTransform(matrix),(rgb.shape[1],rgb.shape[0]),borderMode=cv2.BORDER_REFLECT_101)
                    if args.composite=="gradient":
                        x,y,w,h=cv2.boundingRect(mask)
                        restored=cv2.cvtColor(cv2.seamlessClone(cv2.cvtColor(restored,cv2.COLOR_RGB2BGR),
                            cv2.cvtColor(rgb,cv2.COLOR_RGB2BGR),mask.copy(),(x+w//2,y+h//2),cv2.NORMAL_CLONE),cv2.COLOR_BGR2RGB)
                        result=inward_compose(rgb,restored,mask,span)
                    elif args.composite=="screened":
                        result=screened_compose(rgb,restored,mask)
                    else:
                        alpha=np.minimum(cv2.distanceTransform(mask,cv2.DIST_L2,5)/max(2.,span*.06),1.)[:,:,None]
                        result=np.rint(rgb*(1-alpha)+restored*alpha).clip(0,255).astype(np.uint8)
                    assert np.array_equal(result[mask==0],rgb[mask==0])
                    assert np.array_equal(result[protected>0],rgb[protected>0])
                    name=f"{file.stem}_klein_p{index}.png"
                    Image.fromarray(result).save(output/name)
                    panels[f"Klein prompt {index}"]=cv2.warpAffine(result,matrix,(args.size,args.size),borderMode=cv2.BORDER_REFLECT_101)
                    report["results"].append(dict(file=file.name,status="completed",prompt=prompt,output=name,seconds=seconds,
                        peak_cuda_gib=torch.cuda.max_memory_allocated()/1024**3,alignment_scale=scale,anchor_residual=residual,
                        outside_mask_unchanged=True,protected_pixels_unchanged=True))
                    (output/"report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
                    print(json.dumps(report["results"][-1]),flush=True)
                sheet=Image.new("RGB",(384*len(panels),424),"#eef0f5"); draw=ImageDraw.Draw(sheet)
                for index,(name,pixels) in enumerate(panels.items()):
                    sheet.paste(Image.fromarray(pixels).resize((384,384)),(index*384,40)); draw.text((index*384+10,12),name,fill="#14213d")
                sheet.save(output/f"{file.stem}_compare.png")
    finally:
        report["source_hashes_unchanged"]=all(hashlib.sha256(file.read_bytes()).hexdigest()==hashes[file.name] for file in files)
        (output/"report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
        assert report["source_hashes_unchanged"]


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    commands=parser.add_subparsers(dest="command",required=True)
    dl=commands.add_parser("download"); dl.add_argument("--model",type=Path,required=True)
    trial=commands.add_parser("run")
    for name in ("input","output","model","landmarker","parser"):
        trial.add_argument("--"+name,type=Path,required=True)
    trial.add_argument("--previous",type=Path)
    trial.add_argument("--files",nargs="*")
    trial.add_argument("--size",type=int,default=768)
    trial.add_argument("--seed",type=int,default=42)
    trial.add_argument("--prompts",type=int,choices=[1,2,3],default=2)
    trial.add_argument("--prompt-index",type=int,choices=[0,1,2])
    trial.add_argument("--composite",choices=["plain","gradient","screened"],default="plain")
    args=parser.parse_args()
    if args.command=="download": download(args.model)
    else:
        if not 512<=args.size<=1024 or args.size%32:
            parser.error("Use a size from 512-1024 divisible by 32")
        if args.prompt_index is not None and args.prompt_index>=args.prompts:
            parser.error("Prompt index must be included in --prompts")
        run(args)
