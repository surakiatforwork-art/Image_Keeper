"""Local compositing experiments on existing SDXL outputs; no source edits or uploads."""
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


def compose(source, candidate, mask, span):
    distance=cv2.distanceTransform(mask,cv2.DIST_L2,5)
    alpha=np.minimum(distance/max(2.,span*.07),1.)[:,:,None]
    value=np.rint(source.astype(np.float32)*(1-alpha)+candidate.astype(np.float32)*alpha).clip(0,255).astype(np.uint8)
    value[mask==0]=source[mask==0]
    return value


def harmonize(source, generated, mask, span):
    x,y,w,h=cv2.boundingRect(mask)
    center=(x+w//2,y+h//2)
    cloned=cv2.cvtColor(cv2.seamlessClone(cv2.cvtColor(generated,cv2.COLOR_RGB2BGR),
        cv2.cvtColor(source,cv2.COLOR_RGB2BGR),mask.copy(),center,cv2.NORMAL_CLONE),cv2.COLOR_BGR2RGB)
    # Microtexture is bounded so original dark hair cannot simply be pasted back.
    fine=source.astype(np.float32)-cv2.GaussianBlur(source.astype(np.float32),(0,0),.8)
    fine=np.clip(fine,-1.5,2.)*.55
    textured=np.clip(cloned.astype(np.float32)+fine,0,255).astype(np.uint8)
    original_lab=cv2.cvtColor(source.astype(np.float32)/255,cv2.COLOR_RGB2Lab)
    new_lab=cv2.cvtColor(cloned.astype(np.float32)/255,cv2.COLOR_RGB2Lab)
    sigma=max(2.,span*.12)
    old_light=cv2.GaussianBlur(original_lab[:,:,0],(0,0),sigma)
    new_light=cv2.GaussianBlur(new_lab[:,:,0],(0,0),sigma)
    correction=np.clip(old_light-new_light,-5.,5.)
    new_lab[:,:,0]+=correction*.35
    shaded=np.clip(cv2.cvtColor(new_lab,cv2.COLOR_Lab2RGB)*255,0,255)
    shaded=np.clip(shaded+fine,0,255).astype(np.uint8)
    gray=cv2.cvtColor(source,cv2.COLOR_RGB2GRAY).astype(np.float32)
    kernel=max(3,int(span*.09)|1)
    hair=cv2.morphologyEx(gray,cv2.MORPH_BLACKHAT,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kernel,kernel)))
    evidence=np.clip((hair-2.5)/8.,0,1)
    evidence=cv2.dilate(evidence,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(max(3,int(span*.045)|1),)*2))
    evidence=cv2.GaussianBlur(evidence,(0,0),max(.8,span*.012))
    outputs={"Poisson":compose(source,cloned,mask,span),
            "Poisson texture":compose(source,textured,mask,span),
            "Shading texture":compose(source,shaded,mask,span)}
    for floor in (.15,.35):
        amount=(floor+(1-floor)*evidence)[:,:,None]
        adaptive=np.clip(source.astype(np.float32)+(cloned.astype(np.float32)-source)*amount,0,255).astype(np.uint8)
        outputs[f"Selective {floor:.2f}"]=compose(source,adaptive,mask,span)
    return outputs


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("input","previous","output","landmarker","parser"):
        parser.add_argument("--"+name,type=Path,required=True)
    args=parser.parse_args()
    if args.output.resolve()==args.input.resolve() or args.input.resolve() in args.output.resolve().parents or args.output.resolve()==args.previous.resolve():
        raise ValueError("Use separate output and source directories")
    args.output.mkdir(parents=True,exist_ok=True)
    settings=ort.SessionOptions(); settings.intra_op_num_threads=2
    parsing=ort.InferenceSession(str(args.parser),sess_options=settings,providers=["CPUExecutionProvider"])
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    report=[]
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for file in sorted(args.input.glob("18807*_0.jpg")):
            digest=hashlib.sha256(file.read_bytes()).hexdigest()
            source=np.array(ImageOps.exif_transpose(Image.open(file)).convert("RGB"))
            generated=np.array(Image.open(args.previous/f"{file.stem}_sdxl_0.85.png").convert("RGB"))
            if source.shape!=generated.shape:
                raise ValueError("Comparison dimensions differ")
            faces=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=source)).face_landmarks
            if not faces:
                raise ValueError("Face detection failed")
            _,_,mask,protected,matrix,span=geometry(source,faces[0],1024,.35,parsing)
            outputs=harmonize(source,generated,mask,span)
            panels={"Original":source,"Previous SDXL":generated,**outputs}
            sheet=Image.new("RGB",(384*len(panels),424),"#eef0f5")
            draw=ImageDraw.Draw(sheet)
            for index,(name,pixels) in enumerate(panels.items()):
                if name in outputs:
                    assert np.array_equal(pixels[mask==0],source[mask==0])
                    assert np.array_equal(pixels[protected>0],source[protected>0])
                    Image.fromarray(pixels).save(args.output/f"{file.stem}_{name.replace(' ','_')}.png")
                aligned=cv2.warpAffine(pixels,matrix,(1024,1024),borderMode=cv2.BORDER_REFLECT_101)
                sheet.paste(Image.fromarray(aligned).resize((384,384)),(index*384,40))
                draw.text((index*384+10,12),name,fill="#14213d")
            sheet.save(args.output/f"{file.stem}_compare.png")
            assert digest==hashlib.sha256(file.read_bytes()).hexdigest()
            report.append(dict(file=file.name,source_unchanged=True,protected_pixels_unchanged=True))
    (args.output/"report.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    print(json.dumps(dict(completed=len(report),originals_unchanged=True)),flush=True)


if __name__=="__main__":
    main()
