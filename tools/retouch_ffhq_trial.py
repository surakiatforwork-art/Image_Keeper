"""Private face-trained MI-GAN experiment, using upstream weights and code."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import time

import cv2
import mediapipe as mp
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageOps
from retouch_trial import read_image, reduce_hair


def generate(model, rgb, holes):
    keep = torch.from_numpy((holes==0).astype(np.float32))[None,None]
    image = torch.from_numpy(np.ascontiguousarray(rgb.transpose(2,0,1))).float()[None]/127.5-1
    tensor = torch.cat([keep-.5,image*keep],dim=1)
    with torch.inference_mode():
        output = model(tensor)[0]
    return ((output.permute(1,2,0).numpy()+1)*127.5).clip(0,255).astype(np.uint8)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--lab',type=Path,required=True)
    parser.add_argument('--landmarker',type=Path,required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    spec = importlib.util.spec_from_file_location('migan_inference',args.lab/'MI-GAN/lib/model_zoo/migan_inference.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    torch.set_num_threads(2)
    torch.backends.mkldnn.enabled = False
    torch.manual_seed(0)
    model = module.Generator(resolution=256)
    model.load_state_dict(torch.load(args.lab/'migan_256_ffhq.pt',map_location='cpu',weights_only=True))
    model.eval()
    report = []
    options = mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(
        model_asset_path=str(args.landmarker)),num_faces=1)
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for source in sorted(args.input.glob('18807*_0.jpg')):
            started=time.perf_counter()
            sha=hashlib.sha256(source.read_bytes()).hexdigest()
            rgb=read_image(source)
            faces=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not faces:
                report.append({'file':source.name,'status':'no_face'})
                continue
            h,w=rgb.shape[:2]
            p=np.array([(q.x*w,q.y*h) for q in faces[0]],np.float32)
            _,mask,box=reduce_hair(rgb,faces[0],1.)
            span=np.linalg.norm(p[291]-p[61])
            radius=max(2,int(span*.075))
            mask=cv2.dilate(mask,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(radius*2+1,radius*2+1)))
            protected=np.zeros((h,w),np.uint8)
            cv2.fillPoly(protected,[p[[61,40,37,0,267,270,291,321,314,17,84,91]].astype(np.int32)],255)
            # Protect the underside of the nose as well as the complete lip outline.
            cv2.fillPoly(protected,[p[[98,97,2,326,327,294,278,344,440,275,4,45,220,115,48,64]].astype(np.int32)],255)
            mask[protected>0]=0
            eye_left=(p[33]+p[133])/2
            eye_right=(p[362]+p[263])/2
            direction=eye_right-eye_left
            scale=96/np.linalg.norm(direction)
            a,b=direction[0]*scale/np.linalg.norm(direction),direction[1]*scale/np.linalg.norm(direction)
            linear=np.array([[a,b],[-b,a]],np.float32)
            center=(eye_left+eye_right)/2
            matrix=np.column_stack([linear,np.array([128,102])-linear@center]).astype(np.float32)
            aligned=cv2.warpAffine(rgb,matrix,(256,256),borderMode=cv2.BORDER_REFLECT_101)
            hole=cv2.warpAffine(mask,matrix,(256,256),flags=cv2.INTER_NEAREST)
            aligned_gray=cv2.cvtColor(aligned,cv2.COLOR_RGB2GRAY)
            hair=cv2.morphologyEx(aligned_gray,cv2.MORPH_BLACKHAT,
                cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(7,7)))
            sparse=np.where((hair>5)&(hole>0),255,0).astype(np.uint8)
            sparse=cv2.dilate(sparse,np.ones((3,3),np.uint8))
            sparse[hole==0]=0
            alpha=cv2.GaussianBlur(mask.astype(np.float32)/255,(0,0),max(1,span*.045))
            alpha[mask==0]=0
            # Regeneration is composited only within the editable original-space mask.
            inverse=cv2.invertAffineTransform(matrix)
            outputs={}
            for label,holes,amount in [('FFHQ_sparse',sparse,1.),('FFHQ_full',hole,1.),('FFHQ_soft',hole,.7)]:
                generated=generate(model,aligned,holes)
                restored=cv2.warpAffine(generated,inverse,(w,h),borderMode=cv2.BORDER_REFLECT_101)
                blend=alpha[:,:,None]*amount
                result=(rgb.astype(np.float32)*(1-blend)+restored.astype(np.float32)*blend).clip(0,255).astype(np.uint8)
                result[mask==0]=rgb[mask==0]
                assert np.array_equal(result[mask==0],rgb[mask==0])
                assert np.array_equal(result[protected>0],rgb[protected>0])
                outputs[label]=result
                Image.fromarray(result).save(args.output/f'{source.stem}_{label}.png')
            columns={'Original':rgb,**outputs}
            sheet=Image.new('RGB',(len(columns)*320,430),'#f3f5f4')
            draw=ImageDraw.Draw(sheet)
            for index,(label,pixels) in enumerate(columns.items()):
                tile=ImageOps.contain(Image.fromarray(pixels).crop(box),(312,390))
                sheet.paste(tile,(index*320+(320-tile.width)//2,32))
                draw.text((index*320+8,10),label,fill='#173b33')
            sheet.save(args.output/f'{source.stem}_ffhq.jpg',quality=95)
            assert sha==hashlib.sha256(source.read_bytes()).hexdigest()
            report.append({'file':source.name,'status':'ok','source_sha256':sha,
                'source_and_protected_pixels_unchanged':True,'seconds':round(time.perf_counter()-started,3)})
            print(report[-1],flush=True)
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


if __name__=='__main__':
    main()
