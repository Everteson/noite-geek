import sys, os
from PIL import Image, ImageDraw
d = sys.argv[1]; out = sys.argv[2]
names = ["manha-1","manha-2","tarde-1","tarde-2","noite-1","noite-2","madrugada-1","madrugada-2"]
W, H = 640, 360
sheet = Image.new("RGB", (W*2, H*4), "black")
for i, n in enumerate(names):
    im = Image.open(os.path.join(d, n + ".png")).convert("RGB").resize((W, H))
    ImageDraw.Draw(im).text((10, 10), n, fill="yellow")
    sheet.paste(im, ((i % 2) * W, (i // 2) * H))
sheet.save(out, quality=85)
