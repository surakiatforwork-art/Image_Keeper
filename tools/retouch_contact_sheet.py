"""Build a private overview from completed trials, without changing source files."""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageOps


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--directory',type=Path,required=True)
    parser.add_argument('--pattern',default='*_attgan.jpg')
    parser.add_argument('--name',default='overview.jpg')
    args=parser.parse_args()
    files=sorted(args.directory.glob(args.pattern))
    if not files:
        raise ValueError('No completed comparisons found')
    width=1280
    height=450
    sheet=Image.new('RGB',(width,height*len(files)),'#f3f5f4')
    draw=ImageDraw.Draw(sheet)
    for index,path in enumerate(files):
        with Image.open(path) as image:
            tile=ImageOps.contain(image,(width,height-20))
            sheet.paste(tile,(0,index*height+20))
        draw.text((8,index*height+4),path.stem,fill='#173b33')
    sheet.save(args.directory/args.name,quality=95)


if __name__=='__main__':
    main()
