"""Private offline AttGAN benchmark, not an Android integration."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import time

import cv2
import mediapipe as mp
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageOps
from retouch_trial import read_image, reduce_hair


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--lab',type=Path,required=True)
    parser.add_argument('--landmarker',type=Path,required=True)
    parser.add_argument('--final-only',action='store_true')
    args=parser.parse_args()
    if args.input.resolve()==args.output.resolve():
        raise ValueError('Use a separate output directory')
    args.output.mkdir(parents=True,exist_ok=True)
    sys.path.insert(0,str(args.lab/'AttGAN'))
    from attgan import Generator
    settings_path=next((args.lab/'attgan_weights').rglob('setting.txt'))
    settings=json.loads(settings_path.read_text())
    print(settings,flush=True)
    torch.set_num_threads(2)
    torch.backends.mkldnn.enabled=False
    model=Generator(settings['enc_dim'],settings['enc_layers'],settings['enc_norm'],settings['enc_acti'],
        settings['dec_dim'],settings['dec_layers'],settings['dec_norm'],settings['dec_acti'],
        settings['n_attrs'],settings['shortcut_layers'],settings['inject_layers'],settings['img_size'])
    checkpoint=next((args.lab/'attgan_weights').rglob('*.pth'))
    state=torch.load(checkpoint,map_location='cpu',weights_only=True)
    print('Checkpoint:',checkpoint,'keys:',list(state),flush=True)
    model.load_state_dict(state['G'])
    del state
    model.eval()
    size=settings['img_size']
    # Neutral non-target controls; only facial hair controls change between runs.
    base=torch.zeros((1,settings['n_attrs']))
    mustache=settings['attrs'].index('Mustache')
    beard=settings['attrs'].index('No_Beard')
    base[0,mustache]=.5
    base[0,beard]=-.5
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(
        model_asset_path=str(args.landmarker)),num_faces=1)
    report=[]
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
            # Limit replacement to perioral hair and the lower jaw, not clean upper cheeks.
            mask.fill(0)
            mouth=p[[61,40,37,0,267,270,291]].copy()
            mouth[:,1]-=max(2,span*.035)
            top=p[2,1]+span*.04
            cv2.fillPoly(mask,[np.concatenate([mouth,[[mouth[-1,0],top],[mouth[0,0],top]]]).astype(np.int32)],255)
            jaw=p[[58,172,136,150,149,176,148,152,377,400,378,379,365,397,288]]
            cv2.fillPoly(mask,[np.concatenate([jaw,[p[291]+[span*.12,span*.02],
                p[17]+[0,span*.04],p[61]+[-span*.12,span*.02]]]).astype(np.int32)],255)
            radius=max(2,int(span*.16))
            mask=cv2.dilate(mask,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(radius*2+1,radius*2+1)))
            protected=np.zeros((h,w),np.uint8)
            cv2.fillPoly(protected,[p[[61,40,37,0,267,270,291,321,314,17,84,91]].astype(np.int32)],255)
            cv2.fillPoly(protected,[p[[98,2,327,4]].astype(np.int32)],255)
            mask[protected>0]=0
            left=(p[33]+p[133])/2
            right=(p[362]+p[263])/2
            direction=right-left
            norm=np.linalg.norm(direction)
            scale=size*.25/norm
            a,b=direction[0]*scale/norm,direction[1]*scale/norm
            linear=np.array([[a,b],[-b,a]],np.float32)
            matrix=np.column_stack([linear,np.array([size*.5,size*.45])-linear@((left+right)/2)]).astype(np.float32)
            inverse=cv2.invertAffineTransform(matrix)
            aligned=cv2.warpAffine(rgb,matrix,(size,size),borderMode=cv2.BORDER_REFLECT_101)
            tensor=torch.from_numpy(np.ascontiguousarray(aligned.transpose(2,0,1))).float()[None]/127.5-1
            with torch.inference_mode():
                features=model(tensor,mode='enc')
                baseline=model(features,base,mode='dec')[0].permute(1,2,0).numpy()*127.5+127.5
            if not args.final_only:
                Image.fromarray(aligned).save(args.output/f'{source.stem}_aligned.png')
                Image.fromarray(baseline.clip(0,255).astype(np.uint8)).save(args.output/f'{source.stem}_baseline.png')
            distance=cv2.distanceTransform(mask,cv2.DIST_L2,5)
            alpha=np.minimum(distance/max(2,span*.055),1)
            outputs={}
            for strength in ([1.] if args.final_only else [.5,1.,1.5]):
                target=base.clone()
                target[0,mustache]=-strength
                target[0,beard]=strength
                with torch.inference_mode():
                    generated=model(features,target,mode='dec')[0].permute(1,2,0).numpy()*127.5+127.5
                if strength == 1. and not args.final_only:
                    Image.fromarray(generated.clip(0,255).astype(np.uint8)).save(args.output/f'{source.stem}_generated.png')
                # Use the edit delta to avoid importing the model's entire face reconstruction.
                delta=cv2.GaussianBlur(np.clip(generated-baseline,-45,45),(0,0),1.5)
                delta=cv2.warpAffine(delta,inverse,(w,h),borderMode=cv2.BORDER_REFLECT_101)
                edited=np.clip(rgb.astype(np.float32)+delta*alpha[:,:,None],0,255).astype(np.uint8)
                edited[mask==0]=rgb[mask==0]
                assert np.array_equal(edited[mask==0],rgb[mask==0])
                assert np.array_equal(edited[protected>0],rgb[protected>0])
                label=f'AttGAN_{strength:.1f}'
                if not args.final_only:
                    outputs[label]=edited
                    Image.fromarray(edited).save(args.output/f'{source.stem}_{label}.png')
                if strength == 1.:
                    restored=cv2.warpAffine(generated,inverse,(w,h),borderMode=cv2.BORDER_REFLECT_101)
                    sample=np.zeros((h,w),np.uint8)
                    for center in [p[50],p[280],p[205],p[425]]:
                        cv2.circle(sample,tuple(center.astype(int)),max(3,int(span*.12)),255,-1)
                    # Match clean cheek colour, never use dark beard pixels as reference.
                    original_lab=cv2.cvtColor(rgb,cv2.COLOR_RGB2LAB).astype(np.float32)
                    restored_lab=cv2.cvtColor(restored.clip(0,255).astype(np.uint8),cv2.COLOR_RGB2LAB).astype(np.float32)
                    offset=np.median((original_lab-restored_lab)[sample>0],axis=0)
                    matched=cv2.cvtColor((restored_lab+offset).clip(0,255).astype(np.uint8),cv2.COLOR_LAB2RGB)
                    # Retain bounded fine texture without reintroducing dark hair strands.
                    fine=rgb.astype(np.float32)-cv2.GaussianBlur(rgb,(0,0),.8).astype(np.float32)
                    matched=np.clip(matched.astype(np.float32)+np.clip(fine,-2.5,2.5)*.6,0,255)
                    for amount in [.7,.85,1.]:
                        label=f'Direct_{amount*100:.0f}pct'
                        blend=alpha[:,:,None]*amount
                        direct=(rgb.astype(np.float32)*(1-blend)+matched.astype(np.float32)*blend).clip(0,255).astype(np.uint8)
                        direct[mask==0]=rgb[mask==0]
                        assert np.array_equal(direct[protected>0],rgb[protected>0])
                        outputs[label]=direct
                        Image.fromarray(direct).save(args.output/f'{source.stem}_{label}.png')
            columns={'Original':rgb,**outputs}
            sheet=Image.new('RGB',(len(columns)*320,430),'#f3f5f4')
            draw=ImageDraw.Draw(sheet)
            for index,(label,pixels) in enumerate(columns.items()):
                tile=ImageOps.contain(Image.fromarray(pixels).crop(box),(312,390))
                sheet.paste(tile,(index*320+(320-tile.width)//2,32))
                draw.text((index*320+8,10),label,fill='#173b33')
            sheet.save(args.output/f'{source.stem}_attgan.jpg',quality=95)
            assert sha==hashlib.sha256(source.read_bytes()).hexdigest()
            report.append({'file':source.name,'status':'ok','source_sha256':sha,
                'source_and_protected_pixels_unchanged':True,'seconds':round(time.perf_counter()-started,3)})
            print(report[-1],flush=True)
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


if __name__=='__main__':
    main()
