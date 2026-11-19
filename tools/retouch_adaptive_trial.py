"""Offline-only blend refinement using private, previously generated AttGAN outputs."""
import argparse
import hashlib
import json
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw, ImageOps
from retouch_trial import read_image, reduce_hair


def smoothstep(value, low, high):
    t=np.clip((value-low)/(high-low),0,1)
    return t*t*(3-2*t)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--previous',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--landmarker',type=Path,required=True)
    args=parser.parse_args()
    if args.input.resolve()==args.output.resolve() or args.previous.resolve()==args.output.resolve():
        raise ValueError('Use a new output directory')
    args.output.mkdir(parents=True,exist_ok=True)
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(
        model_asset_path=str(args.landmarker)),num_faces=1)
    report=[]
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for source in sorted(args.input.glob('18807*_0.jpg')):
            digest=hashlib.sha256(source.read_bytes()).hexdigest()
            rgb=read_image(source)
            edited=read_image(args.previous/f'{source.stem}_Direct_100pct.png')
            assert rgb.shape==edited.shape
            original=rgb.astype(np.float32)
            generated=edited.astype(np.float32)
            active=np.any(rgb!=edited,axis=2)
            faces=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not faces:
                report.append({'file':source.name,'status':'no_face'})
                continue
            _,_,box=reduce_hair(rgb,faces[0],1.)
            span=np.hypot((faces[0][61].x-faces[0][291].x)*rgb.shape[1],
                (faces[0][61].y-faces[0][291].y)*rgb.shape[0])
            luma=np.dot(original,[.2126,.7152,.0722]).astype(np.float32)
            generated_luma=np.dot(generated,[.2126,.7152,.0722]).astype(np.float32)
            # Evidence only, not a trained hair detector: avoid importing arbitrary skin changes.
            lift=cv2.GaussianBlur(generated_luma-luma,(0,0),max(1,span*.025))
            hair=cv2.morphologyEx(luma,cv2.MORPH_BLACKHAT,
                cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(max(3,int(span*.045)|1),)*2))
            hair=cv2.GaussianBlur(hair,(0,0),max(1,span*.015))
            evidence=np.maximum(smoothstep(lift,2,20),smoothstep(hair,3,13)*smoothstep(lift,0,7))
            evidence=cv2.GaussianBlur(evidence,(0,0),max(1,span*.025))
            p=np.array([(q.x*rgb.shape[1],q.y*rgb.shape[0]) for q in faces[0]],np.float32)
            jaw_prior=np.zeros(luma.shape,np.uint8)
            cv2.polylines(jaw_prior,[p[[58,172,136,150,149,176,148,152,377,400,378,379,365,397,288]].astype(np.int32)],
                False,255,max(3,int(span*.32)),cv2.LINE_AA)
            jaw_prior=cv2.GaussianBlur(jaw_prior.astype(np.float32)/255,(0,0),max(1,span*.045))
            jaw_evidence=np.maximum(evidence,jaw_prior*.95)
            current=np.clip(original*.15+generated*.85,0,255).astype(np.uint8)
            current[~active]=rgb[~active]
            outputs={'Current 85%':current}
            for label,floor,signal in [('Adaptive balanced',.3,evidence),('Jaw-aware natural',.3,jaw_evidence)]:
                blend=(floor+(1-floor)*signal)*.85
                blend[~active]=0
                result=np.clip(original+(generated-original)*blend[:,:,None],0,255).astype(np.uint8)
                result[~active]=rgb[~active]
                assert np.array_equal(result[~active],rgb[~active])
                outputs[label]=result
                Image.fromarray(result).save(args.output/f'{source.stem}_{label.replace(" ","_")}.png')
            sheet=Image.new('RGB',((len(outputs)+1)*320,430),'#f3f5f4')
            draw=ImageDraw.Draw(sheet)
            for index,(label,pixels) in enumerate({'Original':rgb,**outputs}.items()):
                tile=ImageOps.contain(Image.fromarray(pixels).crop(box),(312,390))
                sheet.paste(tile,(index*320+(320-tile.width)//2,32))
                draw.text((index*320+8,10),label,fill='#173b33')
            sheet.save(args.output/f'{source.stem}_adaptive.jpg',quality=95)
            assert digest==hashlib.sha256(source.read_bytes()).hexdigest()
            report.append({'file':source.name,'status':'ok','source_unchanged':True,
                'outside_previous_edit_unchanged':True,
                'mean_hair_evidence_in_previous_edit':float(np.mean(evidence[active]))})
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    main()
