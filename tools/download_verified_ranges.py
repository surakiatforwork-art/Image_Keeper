"""Resumable bounded-concurrency HTTPS download, verified before use."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import urlparse


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url",required=True)
    parser.add_argument("--output",type=Path,required=True)
    parser.add_argument("--size",type=int,required=True)
    parser.add_argument("--sha256",required=True)
    parser.add_argument("--workers",type=int,default=4)
    args=parser.parse_args()
    if urlparse(args.url).scheme!="https" or not re.fullmatch("[0-9a-f]{64}",args.sha256) or args.size<=0:
        parser.error("HTTPS URL, positive byte size and SHA256 are required")
    if not 1<=args.workers<=8:
        parser.error("Use 1-8 workers")
    if args.output.exists():
        raise FileExistsError("Refusing to replace an existing output")
    parts=args.output.parent/(args.output.name+".parts-"+args.sha256[:12])
    parts.mkdir(parents=True,exist_ok=True)
    chunk=8*1024*1024
    tasks=[(index,start,min(args.size-1,start+chunk-1)) for index,start in enumerate(range(0,args.size,chunk))]
    def fetch(task):
        index,start,end=task
        file=parts/f"{index:06}.bin"
        header=parts/f"{index:06}.headers"
        if file.exists() and file.stat().st_size==end-start+1:
            return file
        for attempt in range(4):
            result=subprocess.run(["curl.exe","--silent","--show-error","--location","--fail",
                "--connect-timeout","20","--max-time","180","--range",f"{start}-{end}",
                "--dump-header",str(header),"--output",str(file),args.url],capture_output=True,text=True)
            headers=header.read_text(errors="replace") if header.exists() else ""
            ranges=re.findall(r"content-range:\s*bytes\s+(\d+)-(\d+)/(\d+)",headers,re.I)
            if result.returncode==0 and ranges and tuple(map(int,ranges[-1]))==(start,end,args.size) and file.stat().st_size==end-start+1:
                return file
            if attempt<3:
                time.sleep(2**attempt)
        raise RuntimeError(f"Range {index} failed: {result.stderr[-300:]}")
    pool=ThreadPoolExecutor(max_workers=args.workers)
    try:
        files=[]
        for index,file in enumerate(pool.map(fetch,tasks)):
            files.append(file)
            if index%16==0 or index+1==len(tasks):
                print(f"Downloaded {index+1}/{len(tasks)} checked ranges",flush=True)
    finally:
        pool.shutdown(wait=True,cancel_futures=True)
    pending=args.output.with_name(args.output.name+".verified-download")
    digest=hashlib.sha256()
    with pending.open("wb") as out:
        for file in files:
            with file.open("rb") as source:
                while data:=source.read(1024*1024):
                    digest.update(data)
                    out.write(data)
    if pending.stat().st_size!=args.size or digest.hexdigest()!=args.sha256:
        raise RuntimeError("Final SHA256 mismatch: download not installed")
    if args.output.exists():
        raise FileExistsError("Refusing to replace an existing output; choose a new filename")
    pending.rename(args.output)
    print(f"Verified SHA256 {digest.hexdigest()}",flush=True)


if __name__=="__main__":
    main()
