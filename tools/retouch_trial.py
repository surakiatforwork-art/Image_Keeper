"""Private, offline comparison trial. Inputs/outputs must stay outside the repository."""
import argparse
import hashlib
import json
from pathlib import Path
import time

import cv2
import mediapipe as mp
import numpy as np
from PIL import Image, ImageDraw, ImageOps


def read_image(path):
    with Image.open(path) as image:
        return np.array(ImageOps.exif_transpose(image).convert('RGB'))


def reduce_hair(rgb, landmarks, strength):
    h, w = rgb.shape[:2]
    points = np.array([(p.x * w, p.y * h) for p in landmarks], np.float32)
    span = np.linalg.norm(points[291] - points[61])
    mask = np.zeros((h, w), np.uint8)
    upper = points[[61, 40, 37, 0, 267, 270, 291]].copy()
    upper[:, 1] -= max(2, span * .035)
    nose_y = points[2, 1] + span * .04
    polygon = np.concatenate([upper, [[upper[-1, 0], nose_y], [upper[0, 0], nose_y]]])
    cv2.fillPoly(mask, [polygon.astype(np.int32)], 255)
    jaw = points[[234,93,132,58,172,136,150,149,176,148,152,377,400,378,379,365,397,288,361,323,454]]
    beard = np.concatenate([jaw, [points[291]+[span*.12,span*.08],
        points[17]+[0,span*.08], points[61]+[-span*.12,span*.08]]])
    cv2.fillPoly(mask, [beard.astype(np.int32)], 255)
    # Explicit protected regions prevent smoothing lips and nostrils, even on tilted faces.
    lips = points[[61,40,37,0,267,270,291,321,314,17,84,91]].astype(np.int32)
    protect = np.zeros_like(mask)
    cv2.fillPoly(protect, [lips], 255)
    radius = max(1, int(span*.025))
    protect = cv2.dilate(protect, cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*radius+1,2*radius+1)))
    feather = cv2.GaussianBlur(mask.astype(np.float32)/255, (0,0), max(1,span*.035))
    feather[mask == 0] = 0
    feather[protect > 0] = 0
    lab = cv2.cvtColor(rgb, cv2.COLOR_RGB2LAB)
    luma = lab[:,:,0]
    # Closing reduces narrow dark hair lines without inventing facial anatomy.
    kernel = max(3, int(span*.065) | 1)
    closed = cv2.morphologyEx(luma,cv2.MORPH_CLOSE,
        cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kernel,kernel)))
    detail = np.minimum(closed.astype(np.float32)-luma, 32)
    smooth = cv2.bilateralFilter(luma,9,18,max(2,span*.04)).astype(np.float32)
    change = np.maximum(0, .8*detail + .2*(smooth-luma))
    edited = lab.copy()
    edited[:,:,0] = np.clip(luma + strength*feather*change,0,255).astype(np.uint8)
    candidate = cv2.cvtColor(edited,cv2.COLOR_LAB2RGB)
    # Keep pixels outside the selected region byte-identical to source RGB.
    result = rgb.copy()
    result[feather > 0] = candidate[feather > 0]
    box = (max(0,int(points[:,0].min()-span*.3)),max(0,int(points[:,1].min()-span*.2)),
        min(w,int(points[:,0].max()+span*.3)),min(h,int(points[:,1].max()+span*.2)))
    return result, mask, box


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--model',type=Path,required=True)
    args = parser.parse_args()
    if args.input.resolve() == args.output.resolve():
        raise ValueError('Output must not be the input directory')
    args.output.mkdir(parents=True,exist_ok=True)
    options = mp.tasks.vision.FaceLandmarkerOptions(
        base_options=mp.tasks.BaseOptions(model_asset_path=str(args.model)),num_faces=1,
        min_face_detection_confidence=.6)
    report = []
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for source in sorted(args.input.glob('18807*_0.jpg')):
            start = time.perf_counter()
            original_hash = hashlib.sha256(source.read_bytes()).hexdigest()
            rgb = read_image(source)
            detection = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb))
            if not detection.face_landmarks:
                report.append({'file':source.name,'status':'no_face'})
                continue
            columns = [Image.fromarray(rgb)]
            for label, strength in [('light',.4),('medium',.7),('strong',1.)]:
                edited, mask, box = reduce_hair(rgb,detection.face_landmarks[0],strength)
                assert np.array_equal(edited[mask == 0],rgb[mask == 0])
                image = Image.fromarray(edited)
                image.save(args.output / f'{source.stem}_{label}.png')
                columns.append(image)
            sheet = Image.new('RGB',(4*320,420),'#f3f5f4')
            draw = ImageDraw.Draw(sheet)
            for index, (image,label) in enumerate(zip(columns,['Original','Light 40%','Medium 70%','Strong 100%'])):
                tile = ImageOps.contain(image.crop(box),(312,380))
                sheet.paste(tile,(index*320+(320-tile.width)//2,32))
                draw.text((index*320+12,10),label,fill='#173b33')
            sheet.save(args.output / f'{source.stem}_compare.jpg',quality=95)
            report.append({'file':source.name,'status':'ok','seconds':round(time.perf_counter()-start,3),
                'outside_mask_unchanged':True,
                'source_sha256':original_hash,
                'source_unchanged':original_hash == hashlib.sha256(source.read_bytes()).hexdigest()})
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))


if __name__ == '__main__':
    main()
