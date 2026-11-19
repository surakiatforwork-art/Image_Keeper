"""Private visual QA: original, previous prototype, current Android result at 85%."""
import argparse
from pathlib import Path

import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw, ImageOps
from retouch_trial import read_image, reduce_hair


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--previous',type=Path,required=True)
    parser.add_argument('--device',type=Path,required=True)
    parser.add_argument('--landmarker',type=Path,required=True)
    args=parser.parse_args()
    options=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(
        model_asset_path=str(args.landmarker)),num_faces=1)
    sheets=[]
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for source in sorted(args.input.glob('18807*_0.jpg')):
            rgb=read_image(source)
            faces=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not faces:
                continue
            _,_,box=reduce_hair(rgb,faces[0],1.)
            old=read_image(args.previous/f'{source.stem}_Direct_100pct.png')
            current=read_image(args.device/f'{source.stem}.png')
            assert rgb.shape==old.shape==current.shape
            blend=lambda pixels: np.clip(rgb.astype(np.float32)*.15+pixels.astype(np.float32)*.85,0,255).astype(np.uint8)
            sheet=Image.new('RGB',(960,430),'#f3f5f4')
            draw=ImageDraw.Draw(sheet)
            for i,(label,pixels) in enumerate([('Original',rgb),('AttGAN prototype 85%',blend(old)),('Android AI pipeline 85%',blend(current))]):
                tile=ImageOps.contain(Image.fromarray(pixels).crop(box),(312,390))
                sheet.paste(tile,(i*320+(320-tile.width)//2,32))
                draw.text((i*320+8,10),label,fill='#173b33')
            sheet.save(args.device/f'{source.stem}_compare.jpg',quality=95)
            sheets.append(sheet)
    overview=Image.new('RGB',(960,430*len(sheets)),'#f3f5f4')
    for i,sheet in enumerate(sheets):
        overview.paste(sheet,(0,i*430))
    overview.save(args.device/'overview.jpg',quality=95)


if __name__=='__main__':
    main()
