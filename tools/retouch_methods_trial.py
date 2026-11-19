"""Compare offline candidates; private images are never committed or uploaded."""
import argparse
import hashlib
import json
from pathlib import Path
import time

import cv2
import mediapipe as mp
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageDraw, ImageOps
from retouch_trial import read_image, reduce_hair


def compose(original, candidate, alpha):
    result = original.copy()
    active = alpha > 0
    blended = np.clip(original.astype(np.float32)*(1-alpha[:,:,None])+
        candidate.astype(np.float32)*alpha[:,:,None],0,255).astype(np.uint8)
    result[active] = blended[active]
    return result


def candidates(rgb, landmarks, session):
    _, mask, box = reduce_hair(rgb,landmarks,1.)
    h,w = rgb.shape[:2]
    p = np.array([(q.x*w,q.y*h) for q in landmarks],np.float32)
    span = np.linalg.norm(p[291]-p[61])
    lips = np.zeros_like(mask)
    cv2.fillPoly(lips,[p[[61,40,37,0,267,270,291,321,314,17,84,91]].astype(np.int32)],255)
    radius = max(2,int(span*.035))
    lips = cv2.dilate(lips,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(radius*2+1,radius*2+1)))
    mask[lips>0] = 0
    alpha = cv2.GaussianBlur(mask.astype(np.float32)/255,(0,0),max(1,span*.025))
    alpha[mask==0] = 0
    gray = cv2.cvtColor(rgb,cv2.COLOR_RGB2GRAY)
    kernel = max(5,int(span*.09)|1)
    hair = cv2.morphologyEx(gray,cv2.MORPH_BLACKHAT,
        cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kernel,kernel)))
    sparse = np.where((hair>7)&(mask>0),255,0).astype(np.uint8)
    sparse = cv2.dilate(sparse,np.ones((3,3),np.uint8))
    sparse[mask==0] = 0
    result = {}
    clean = cv2.inpaint(rgb,sparse,max(2,span*.025),cv2.INPAINT_NS)
    result['Sparse_NS'] = compose(rgb,clean,alpha)
    filled = cv2.inpaint(rgb,mask,max(3,span*.04),cv2.INPAINT_TELEA)
    result['Full_Telea'] = compose(rgb,filled,alpha*.85)

    # Transfer only a bounded amount of colour/lightness from clean cheek samples.
    lab = cv2.cvtColor(rgb,cv2.COLOR_RGB2LAB).astype(np.float32)
    sample = np.zeros_like(mask)
    for center in [p[50],p[280],p[205],p[425]]:
        cv2.circle(sample,tuple(center.astype(int)),max(3,int(span*.16)),255,-1)
    sample[mask>0] = 0
    skin = lab[sample>0]
    if len(skin):
        target = np.median(skin,axis=0)
        baseline = cv2.GaussianBlur(lab,(0,0),max(2,span*.12))
        # Preserve broad facial shading instead of painting a uniform skin colour.
        delta = np.minimum(np.maximum(target[0]-baseline[:,:,0]-9,0),36)
        corrected = cv2.cvtColor(clean,cv2.COLOR_RGB2LAB).astype(np.float32)
        corrected[:,:,0] += delta*.85
        for channel in [1,2]:
            corrected[:,:,channel] += np.clip(target[channel]-baseline[:,:,channel],-8,8)*.45
        corrected = cv2.cvtColor(np.clip(corrected,0,255).astype(np.uint8),cv2.COLOR_LAB2RGB)
        result['Skin_Hybrid'] = compose(rgb,corrected,alpha*.9)

    x0,y0,x1,y1 = box
    crop = rgb[y0:y1,x0:x1]
    for label, holes in ([('MIGAN_sparse',sparse),('MIGAN_full',mask)] if session else []):
        image = np.ascontiguousarray(cv2.resize(crop,(512,512)).transpose(2,0,1)[None])
        keep = cv2.resize(255-holes[y0:y1,x0:x1],(512,512),interpolation=cv2.INTER_NEAREST)
        keep = np.ascontiguousarray(keep[None,None])
        output = session.run(None,{'image':image,'mask':keep})[0][0].transpose(1,2,0)
        generated = rgb.copy()
        generated[y0:y1,x0:x1] = cv2.resize(output,(x1-x0,y1-y0))
        result[label] = compose(rgb,generated,alpha*.9)
    return result, mask, lips, box


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--assets',type=Path,required=True)
    parser.add_argument('--migan',action='store_true')
    args = parser.parse_args()
    if args.input.resolve()==args.output.resolve():
        raise ValueError('Output must differ from input')
    args.output.mkdir(parents=True,exist_ok=True)
    options = mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(
        model_asset_path=str(args.assets/'face_landmarker.task')),num_faces=1)
    config = ort.SessionOptions()
    config.intra_op_num_threads = 2
    config.enable_cpu_mem_arena = False
    config.enable_mem_pattern = False
    config.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    session = ort.InferenceSession(str(args.assets/'migan.onnx'),sess_options=config,
        providers=['CPUExecutionProvider']) if args.migan else None
    report = []
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        for source in sorted(args.input.glob('18807*_0.jpg')):
            started = time.perf_counter()
            sha = hashlib.sha256(source.read_bytes()).hexdigest()
            rgb = read_image(source)
            faces = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=rgb)).face_landmarks
            if not faces:
                report.append({'file':source.name,'status':'no_face'})
                continue
            outputs,mask,lips,box = candidates(rgb,faces[0],session)
            columns = {'Original':rgb,**outputs}
            sheet = Image.new('RGB',(len(columns)*300,410),'#f3f5f4')
            draw = ImageDraw.Draw(sheet)
            for index,(label,pixels) in enumerate(columns.items()):
                if label != 'Original':
                    assert np.array_equal(pixels[mask==0],rgb[mask==0])
                    assert np.array_equal(pixels[lips>0],rgb[lips>0])
                    Image.fromarray(pixels).save(args.output/f'{source.stem}_{label}.png')
                tile = ImageOps.contain(Image.fromarray(pixels).crop(box),(292,370))
                sheet.paste(tile,(index*300+(300-tile.width)//2,32))
                draw.text((index*300+8,10),label,fill='#173b33')
            sheet.save(args.output/f'{source.stem}_methods.jpg',quality=95)
            assert sha==hashlib.sha256(source.read_bytes()).hexdigest()
            report.append({'file':source.name,'status':'ok','source_sha256':sha,
                'source_unchanged':True,'outside_mask_and_lips_unchanged':True,
                'seconds':round(time.perf_counter()-started,3)})
            print(source.name,report[-1]['seconds'],flush=True)
    (args.output/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')


if __name__=='__main__':
    main()
