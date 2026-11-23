"""Private pilot: learn a bounded RGB residual from aligned Klein contact sheets."""
import argparse
import json
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import torch
from torch import nn
from torch.nn import functional as F
from PIL import Image


class Student(nn.Module):
    def __init__(self):
        super().__init__()
        layers = []
        for i, dilation in enumerate([1, 2, 4, 8, 4, 2, 1]):
            layers.extend([nn.Conv2d(4 if not i else 24, 24, 3, padding=dilation, dilation=dilation), nn.ReLU()])
        self.net = nn.Sequential(*layers, nn.Conv2d(24, 3, 1))
        nn.init.zeros_(self.net[-1].bias)
        nn.init.zeros_(self.net[-1].weight)

    def forward(self, x, mask):
        return (x + .40 * mask * self.net(torch.cat([x, mask], 1)).tanh()).clamp(0, 1)


def main():
    p = argparse.ArgumentParser()
    for field in ("pairs", "output", "landmarker"):
        p.add_argument("--" + field, required=True, type=Path)
    p.add_argument("--focus-detail", action="store_true")
    p.add_argument("--extra-pairs", type=Path)
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    torch.manual_seed(23)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    options = mp.tasks.vision.FaceLandmarkerOptions(base_options=mp.tasks.BaseOptions(model_asset_path=str(args.landmarker)))
    tensor = lambda x: torch.from_numpy(x.copy()).float().to(device) / 255
    cases = []
    with mp.tasks.vision.FaceLandmarker.create_from_options(options) as detector:
        paths = sorted(args.pairs.glob("*_compare.png"))
        assert len(paths) == 10
        held = {p.stem for p in paths[-2:]}
        if args.extra_pairs:
            for path in sorted(args.extra_pairs.glob("*_compare.png")):
                # Rejected teacher generations can still produce a single-panel sheet.
                with Image.open(path) as probe:
                    if probe.width != 768:
                        continue
                if "holdout" in path.name:
                    held.add(path.stem)
                paths.append(path)
        for path in paths:
            sheet = np.array(Image.open(path).convert("RGB"))
            assert sheet.shape[0] == 424 and sheet.shape[1] % 384 == 0 and sheet.shape[1] >= 768
            src, dst = [np.ascontiguousarray(x) for x in (sheet[40:, :384], sheet[40:, -384:])]
            face = detector.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=src)).face_landmarks[0]
            pt = np.array([(q.x*384, q.y*384) for q in face])
            mask = np.zeros((384, 384), np.uint8)
            poly = pt[[61, 17, 291, 454, 365, 397, 377, 152, 148, 176, 172, 234]].copy()
            poly[4:11, 1] += 8
            cv2.fillPoly(mask, [poly.astype(np.int32)], 255)
            mask[:int(np.ceil(pt[17, 1]))+2] = 0
            mask = (255*np.clip(cv2.distanceTransform(mask, cv2.DIST_L2, 5)/5, 0, 1)).astype(np.uint8)
            cases.append((path.stem, tensor(src.transpose(2,0,1)), tensor(dst.transpose(2,0,1)), tensor(mask[None])))
    train = [case for case in cases if case[0] not in held]
    model = Student().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=.001)
    for step in range(900 if args.focus_detail else 400):
        idx = torch.randint(len(train), (2,)).tolist()
        x,y,m = (torch.stack([train[i][k][:,195:355,72:312] for i in idx]) for k in (1,2,3))
        if step % 2:
            x,y,m = (a.flip([-1]) for a in (x,y,m))
        pred = model(x,m)
        err = (pred-y)*m
        loss = err.abs().mean() + .6*(err[:,:,:,1:]-err[:,:,:,:-1]).abs().mean()
        if args.focus_detail:
            weight = 1 + 6*((x-y).abs().mean(1,keepdim=True)/.18).clamp(0,1)
            loss = (err.abs()*weight).mean()
            for dim in (2,3):
                loss += .6*(err.diff(dim=dim).abs()*weight.narrow(dim,1,weight.size(dim)-1)).mean()
        opt.zero_grad(); loss.backward(); opt.step()
    model.eval()
    torch.save(model.state_dict(), args.output/"student.pt")
    report = []
    with torch.no_grad():
        for name,x,y,m in (case for case in cases if case[0] in held):
            out = model(x[None],m[None])[0]
            if not report:
                Image.fromarray((x.permute(1,2,0).cpu().numpy()*255).round().astype(np.uint8)).save(args.output/"student-input.png")
                Image.fromarray((m[0].cpu().numpy()*255).round().astype(np.uint8)).convert("RGB").save(args.output/"student-mask.png")
            assert torch.equal(out[:,m[0]==0],x[:,m[0]==0])
            err = lambda v: float(((v-y).abs()*m).sum()/(3*m.sum())*255)
            panel = torch.cat([x,out,y],2).permute(1,2,0)
            Image.fromarray((panel.cpu().numpy()*255).round().astype(np.uint8)).save(args.output/(name+".png"))
            report.append(dict(case=name, original_error=err(x), student_error=err(out)))
    (args.output/"report.json").write_text(json.dumps(report,indent=2))
    print(report)


if __name__ == "__main__":
    main()
