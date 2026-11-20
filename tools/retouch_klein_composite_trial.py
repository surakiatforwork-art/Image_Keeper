"""Compare camera-color and gradient compositing of private Klein edits."""
import argparse
import hashlib
import json
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageOps
from retouch_diffusion_trial import geometry
from retouch_photometric import camera_match, inward_compose, screened_compose


def main():
    cli=argparse.ArgumentParser(description=__doc__)
    for name in ("input","raw","output","landmarker","parser"):
        cli.add_argument("--"+name,type=Path,required=True)
    cli.add_argument("--tight-lips",action="store_true")
    args=cli.parse_args()
    if args.output.resolve()==args.input.resolve() or args.input.resolve() in args.output.resolve().parents or args.output.resolve()==args.raw.resolve():
        raise ValueError("Use a separate output directory")
    args.output.mkdir(parents=True,exist_ok=True)
    options=ort.SessionOptions(); options.intra_op_num_threads=2
    parsing=ort.InferenceSession(str(args.parser),sess_options=options,providers=["CPUExecutionProvider"])
    config=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    report=[]
    with mp.tasks.vision.FaceLandmarker.create_from_options(config) as detector:
        for raw_file in sorted(args.raw.glob("*_raw_p*.png")):
            stem=raw_file.name.split("_raw_p")[0]
            file=args.input/f"{stem}.jpg"
            digest=hashlib.sha256(file.read_bytes()).hexdigest()
            source=np.array(ImageOps.exif_transpose(Image.open(file)).convert("RGB"))
            raw=np.array(Image.open(raw_file).convert("RGB")); size=raw.shape[0]
            points=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=source)).face_landmarks[0]
            aligned,aligned_mask,mask,protected,matrix,span=geometry(source,points,size,.35,parsing,tight_lips=args.tight_lips)
            overlay=aligned.copy().astype(np.float32)
            overlay[aligned_mask>0]=overlay[aligned_mask>0]*.55+np.array([30,230,130])*.45
            locked=cv2.warpAffine(protected,matrix,(size,size),flags=cv2.INTER_NEAREST)>0
            overlay[locked]=overlay[locked]*.55+np.array([255,50,50])*.45
            Image.fromarray(overlay.astype(np.uint8)).save(args.output/f"{stem}_mask-review.png")
            found=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=raw)).face_landmarks[0]
            indices=[33,133,362,263,4,168]
            expected=cv2.transform(np.array([[(points[i].x*source.shape[1],points[i].y*source.shape[0]) for i in indices]],np.float32),matrix)[0]
            observed=np.array([(found[i].x*size,found[i].y*size) for i in indices],np.float32)
            adjustment,_=cv2.estimateAffinePartial2D(observed,expected,method=cv2.LMEDS)
            if adjustment is None:
                raise ValueError("Generated face alignment failed")
            scale=float(np.hypot(adjustment[0,0],adjustment[1,0]))
            residual=float(np.median(np.linalg.norm(cv2.transform(observed[None],adjustment)[0]-expected,axis=1)))
            if not .92<=scale<=1.08 or abs(float(np.arctan2(adjustment[1,0],adjustment[0,0])))>.08 or residual>size*.015:
                raise ValueError("Generated face geometry drifted beyond trial limits")
            raw=cv2.warpAffine(raw,adjustment,(size,size),borderMode=cv2.BORDER_REFLECT_101)
            value=cv2.resize(aligned,(512,512)).astype(np.float32)/255
            value=(value-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)
            labels=parsing.run(None,{parsing.get_inputs()[0].name:np.ascontiguousarray(value.transpose(2,0,1)[None])})[0].squeeze().astype(np.uint8)
            labels=cv2.resize(labels,(size,size),interpolation=cv2.INTER_NEAREST)
            reference=np.where((labels==1)&(aligned_mask==0),255,0).astype(np.uint8)
            corrected=camera_match(aligned,raw,reference)
            inverse=cv2.invertAffineTransform(matrix)
            warp=lambda image:cv2.warpAffine(image,inverse,(source.shape[1],source.shape[0]),borderMode=cv2.BORDER_REFLECT_101)
            restored=warp(raw); matched=warp(corrected)
            x,y,w,h=cv2.boundingRect(mask)
            poisson=cv2.cvtColor(cv2.seamlessClone(cv2.cvtColor(restored,cv2.COLOR_RGB2BGR),
                cv2.cvtColor(source,cv2.COLOR_RGB2BGR),mask.copy(),(x+w//2,y+h//2),cv2.NORMAL_CLONE),cv2.COLOR_BGR2RGB)
            variants={"Camera field":matched,"Gradient match":poisson,
                      "Screened":screened_compose(source,matched,mask),
                      "Screened soft":screened_compose(source,matched,mask,.005)}
            panels={"Original":source,"Klein old blend":np.array(Image.open(args.raw/(raw_file.stem.replace("_raw_","_klein_")+".png")).convert("RGB"))}
            for label,image in variants.items():
                result=image if label.startswith("Screened") else inward_compose(source,image,mask,span)
                assert np.array_equal(result[mask==0],source[mask==0])
                assert np.array_equal(result[protected>0],source[protected>0])
                Image.fromarray(result).save(args.output/f"{raw_file.stem}_{label.replace(' ','_')}.png")
                panels[label]=result
            sheet=Image.new("RGB",(384*len(panels),424),"#eef0f5"); draw=ImageDraw.Draw(sheet)
            for index,(label,image) in enumerate(panels.items()):
                crop=cv2.warpAffine(image,matrix,(size,size),borderMode=cv2.BORDER_REFLECT_101)
                sheet.paste(Image.fromarray(crop).resize((384,384)),(index*384,40)); draw.text((index*384+10,12),label,fill="#14213d")
            sheet.save(args.output/f"{raw_file.stem}_compare.png")
            pair=Image.new("RGB",(1024,552),"#eef0f5"); pair_draw=ImageDraw.Draw(pair)
            for index,label in enumerate(("Original","Screened soft")):
                crop=cv2.warpAffine(panels[label],matrix,(size,size),borderMode=cv2.BORDER_REFLECT_101)
                pair.paste(Image.fromarray(crop).resize((512,512)),(index*512,40))
                pair_draw.text((index*512+12,12),label,fill="#14213d")
            pair.save(args.output/f"{raw_file.stem}_before-after.png")
            assert digest==hashlib.sha256(file.read_bytes()).hexdigest()
            report.append(dict(file=file.name,raw=raw_file.name,source_sha256=digest,
                tight_lips=args.tight_lips,source_unchanged=True,protected_pixels_unchanged=True))
    (args.output/"report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps(dict(completed=len(report))),flush=True)


if __name__=="__main__": main()
