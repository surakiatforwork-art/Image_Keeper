"""Prepare a compact, CPU-compatible face parser with label-only output."""
import argparse
import hashlib
import json

import cv2
import numpy as np
import onnx
from onnx import helper, numpy_helper, TensorProto
import onnxruntime as ort
from PIL import Image


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',required=True)
    parser.add_argument('--output',required=True)
    parser.add_argument('--sample',required=True)
    args=parser.parse_args()
    graph=onnx.load(args.source)
    first=graph.graph.output[0].name
    del graph.graph.output[:]
    graph.graph.node.extend([
        helper.make_node('ArgMax',[first],['label_int64'],axis=1,keepdims=0),
        helper.make_node('Cast',['label_int64'],['labels'],to=TensorProto.UINT8)])
    graph.graph.output.extend([helper.make_tensor_value_info('labels',TensorProto.UINT8,[1,512,512])])
    casts=[]
    for tensor in graph.graph.initializer:
        if tensor.data_type==TensorProto.FLOAT and np.prod(tensor.dims)>128:
            name=tensor.name
            tensor.CopyFrom(numpy_helper.from_array(numpy_helper.to_array(tensor).astype(np.float16),name+'_half'))
            casts.append(helper.make_node('Cast',[name+'_half'],[name],to=TensorProto.FLOAT))
    nodes=list(graph.graph.node)
    del graph.graph.node[:]
    graph.graph.node.extend(casts+nodes)
    onnx.checker.check_model(graph)
    onnx.save(graph,args.output)
    # CPU operators remain float32; only storage precision changes.
    options=ort.SessionOptions()
    options.intra_op_num_threads=2
    image=cv2.resize(np.array(Image.open(args.sample).convert('RGB')),(512,512)).astype(np.float32)/255
    image=(image-np.array([.485,.456,.406],np.float32))/np.array([.229,.224,.225],np.float32)
    image=np.ascontiguousarray(image.transpose(2,0,1)[None])
    original=ort.InferenceSession(args.source,sess_options=options,providers=['CPUExecutionProvider'])
    expected=original.run([original.get_outputs()[0].name],{original.get_inputs()[0].name:image})[0].argmax(1)
    del original
    prepared=ort.InferenceSession(args.output,sess_options=options,providers=['CPUExecutionProvider'])
    actual=prepared.run(None,{'input':image})[0]
    agreement=float(np.mean(expected==actual))
    assert agreement>.995
    with open(args.output,'rb') as stream:
        data=stream.read()
    print(json.dumps({'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'label_agreement':agreement},indent=2))


if __name__=='__main__':
    main()
