"""Gera os arquivos que vao dentro do app a partir de arte/original/*.png:
  <nome>-banner.jpg  recorte largo e nitido do banner (1760x400)
  fundo-<periodo>.jpg  papel de parede da tela inteira, de arte/fundo/*.png (1280x720)
e o banner do app (drawable-xhdpi/banner.png, 320x180)."""
import os, sys
from PIL import Image, ImageFilter, ImageEnhance
src, assets, res = sys.argv[1], sys.argv[2], sys.argv[3]
BW, BH = 1760, 400
TOP = {"default": 0.20}
os.makedirs(assets, exist_ok=True)
for f in sorted(os.listdir(src)):
    if not f.endswith(".png"):
        continue
    name = f[:-4]
    im = Image.open(os.path.join(src, f)).convert("RGB")
    w, h = im.size
    ch = int(w * BH / BW)
    top = int(h * TOP.get(name, TOP["default"]))
    top = max(0, min(top, h - ch))
    ban = im.crop((0, top, w, top + ch)).resize((BW, BH), Image.LANCZOS)
    ban.save(os.path.join(assets, name + "-banner.jpg"), quality=86, optimize=True, progressive=True)
    print(name, im.size, "->", os.path.getsize(os.path.join(assets, name + "-banner.jpg")) // 1024, "KB banner")
fdir = os.path.join(os.path.dirname(os.path.abspath(src)), "fundo")
for f in sorted(os.listdir(fdir)) if os.path.isdir(fdir) else []:
    if not f.endswith(".png"):
        continue
    im = Image.open(os.path.join(fdir, f)).convert("RGB")
    w, h = im.size
    ch = min(h, int(w * 9 / 16))
    top = (h - ch) // 2
    wall = im.crop((0, top, w, top + ch)).resize((1280, 720), Image.LANCZOS)
    wall = wall.filter(ImageFilter.GaussianBlur(1.2))
    wall = ImageEnhance.Brightness(wall).enhance(0.72)
    out = os.path.join(assets, "fundo-" + f[:-4] + ".jpg")
    wall.save(out, quality=84, optimize=True, progressive=True)
    print("fundo", f, "->", os.path.getsize(out) // 1024, "KB")
os.makedirs(os.path.join(res, "drawable-xhdpi"), exist_ok=True)
icon = Image.open(os.path.join(src, "noite-1.png")).convert("RGB")
w, h = icon.size
icon.crop((0, 0, int(h * 16 / 9), h)).resize((320, 180), Image.LANCZOS).save(os.path.join(res, "drawable-xhdpi", "banner.png"))
