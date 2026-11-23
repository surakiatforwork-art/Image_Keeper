"""Private local comparison of source-aligned and generated jaw landmarks. No uploads."""
import argparse
import json
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import onnxruntime as ort
from PIL import Image


def main():
    cli=argparse.ArgumentParser(description=__doc__)
    for name in ("photo","landmarks","model","landmarker","output"):
        cli.add_argument("--"+name,type=Path,required=True)
    args=cli.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    rgb=np.array(Image.open(args.photo).convert("RGB"))
    h,w=rgb.shape[:2]; points=np.array(json.loads(args.landmarks.read_text()))[:,:2]*[w,h]
    left=points[[33,133]].mean(0); right=points[[362,263]].mean(0)
    delta=right-left; distance=np.linalg.norm(delta); center=(left+right)/2
    depth=np.dot([-delta[1],delta[0]],points[152]-center)/distance
    scale=min(48/distance,138/depth)
    a,b=delta/distance*scale
    matrix=np.array([[a,b,104-a*center[0]-b*center[1]],[-b,a,92+b*center[0]-a*center[1]]],np.float32)
    aligned=cv2.warpAffine(rgb,matrix,(208,256),borderMode=cv2.BORDER_REPLICATE)
    options=ort.SessionOptions();options.intra_op_num_threads=3
    session=ort.InferenceSession(str(args.model),sess_options=options,providers=["CPUExecutionProvider"])
    generated=session.run(None,{session.get_inputs()[0].name:np.ascontiguousarray((aligned.astype(np.float32)/127.5-1).transpose(2,0,1)[None])})[0][0]
    generated=np.ascontiguousarray(np.rint((generated.transpose(1,2,0)+1)*127.5).clip(0,255).astype(np.uint8))
    expected=cv2.transform(points.astype(np.float32)[None],matrix)[0]
    config=mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)),num_faces=1)
    with mp.tasks.vision.FaceLandmarker.create_from_options(config) as detector:
        found=detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB,data=generated)).face_landmarks
    result={"source_chin":expected[152].tolist(),"detected":bool(found)}
    if found:
        observed=np.array([(p.x*208,p.y*256) for p in found[0]],np.float32)
        result.update(generated_chin=observed[152].tolist(),chin_delta=(observed[152]-expected[152]).tolist(),
                      expected=expected.tolist(),observed=observed.tolist())
    for name,array in (("aligned",aligned),("proposal",generated)):
        Image.fromarray(array).save(args.output/f"{name}.png")
    (args.output/"geometry.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps({k:v for k,v in result.items() if k not in ("expected","observed")}))


if __name__=="__main__": main()
