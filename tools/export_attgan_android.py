"""Export the official generator with fixed clean-shaven controls for Android."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

import numpy as np
import onnx
from onnx import numpy_helper, helper, TensorProto
import onnxruntime as ort
import torch


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--lab',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    sys.path.insert(0,str(args.lab/'AttGAN'))
    from attgan import Generator
    settings=json.loads(next((args.lab/'attgan_weights').rglob('setting.txt')).read_text())
    torch.set_num_threads(2)
    torch.backends.mkldnn.enabled=False
    generator=Generator(settings['enc_dim'],settings['enc_layers'],settings['enc_norm'],settings['enc_acti'],
        settings['dec_dim'],settings['dec_layers'],settings['dec_norm'],settings['dec_acti'],
        settings['n_attrs'],settings['shortcut_layers'],settings['inject_layers'],settings['img_size'])
    generator.load_state_dict(torch.load(next((args.lab/'attgan_weights').rglob('weights.199.pth')),
        map_location='cpu',weights_only=True)['G'])
    generator.eval()

    class CleanShaven(torch.nn.Module):
        def __init__(self):
            super().__init__()
            self.generator=generator
            controls=torch.zeros((1,settings['n_attrs']))
            controls[0,settings['attrs'].index('Mustache')]=-1.
            controls[0,settings['attrs'].index('No_Beard')]=1.
            self.register_buffer('controls',controls)

        def forward(self,image):
            return ((self.generator(image,self.controls)+1)*127.5).clamp(0,255)

    wrapper=CleanShaven().eval()
    sample=torch.linspace(-1,1,3*256*256).reshape(1,3,256,256)
    full=args.lab/'attgan-fp32.onnx'
    torch.onnx.export(wrapper,sample,str(full),input_names=['image'],output_names=['rgb'],
        opset_version=17,dynamo=False,external_data=False)
    graph=onnx.load(str(full))
    # Store large weights at half precision, but keep all CPU operators in float32.
    # This avoids relying on mobile float16 Conv/ConvTranspose kernel support.
    casts=[]
    for initializer in graph.graph.initializer:
        if initializer.data_type==TensorProto.FLOAT and np.prod(initializer.dims)>128:
            name=initializer.name
            data=numpy_helper.to_array(initializer).astype(np.float16)
            initializer.CopyFrom(numpy_helper.from_array(data,name+'_fp16'))
            casts.append(helper.make_node('Cast',[name+'_fp16'],[name],to=TensorProto.FLOAT))
    previous=list(graph.graph.node)
    del graph.graph.node[:]
    graph.graph.node.extend(casts+previous)
    onnx.checker.check_model(graph)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    onnx.save(graph,str(args.output))
    del graph
    with torch.inference_mode():
        expected=wrapper(sample).numpy()
    config=ort.SessionOptions()
    config.intra_op_num_threads=2
    config.graph_optimization_level=ort.GraphOptimizationLevel.ORT_ENABLE_BASIC
    session=ort.InferenceSession(str(args.output),sess_options=config,providers=['CPUExecutionProvider'])
    actual=session.run(None,{'image':sample.numpy()})[0]
    error=np.abs(expected-actual)
    assert np.isfinite(actual).all()
    assert float(error.mean())<.5, 'Weight-storage conversion changed output excessively'
    print(json.dumps({'bytes':args.output.stat().st_size,'sha256':hashlib.sha256(args.output.read_bytes()).hexdigest(),
        'mean_pixel_error':float(error.mean()),'max_pixel_error':float(error.max())},indent=2))


if __name__=='__main__':
    main()
