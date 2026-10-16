"""Coat variants recoloured from the shepherd texture: the pattern stays, the palette changes.
Eyes, nose and mouth keep their colours; the gear area (rows 128+) is copied untouched."""
import colorsys, json, os, sys
from PIL import Image

# Usage: python tools/make_variants.py   (after tools/export_geckolib.py has written textures/entity/dog.png)
P = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = f"{P}/src/main/resources/assets/goodboys/textures/entity"
src = Image.open(f"{T}/dog.png").convert("RGBA")
bb = json.load(open(f"{P}/art/blockbench/dog.bbmodel", encoding="utf-8"))

# pixels that must never be recoloured: the nose cube, eye whites/pupils, the red mouth and its outline
keep = set()
for e in bb["elements"]:
    if e["name"] == "nose":
        for f in e["faces"].values():
            u0, v0, u1, v1 = f["uv"]
            for x in range(int(min(u0, u1)), int(max(u0, u1))):
                for y in range(int(min(v0, v1)), int(max(v0, v1))):
                    keep.add((x, y))
KEEP_COLOURS = {(0xeb, 0xeb, 0xeb), (0x19, 0x16, 0x16), (0x8b, 0x06, 0x2a), (0, 0, 0)}


def lum(c):
    r, g, b = c[:3]
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255


def mix(a, b, f):
    f = max(0.0, min(1.0, f))
    return tuple(int(a[i] + (b[i] - a[i]) * f) for i in range(3))


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# luminance of the shepherd palette spans roughly 0.05 (saddle) .. 0.5 (tan stripes)
VARIANTS = {
    # black shepherd: charcoal with a warm sheen where the tan was
    "black": lambda l: mix(hexc("141416"), hexc("4a4246"), (l - 0.04) / 0.45),
    # white shepherd: cream, the saddle only a shade darker
    "white": lambda l: mix(hexc("cdbfa8"), hexc("f6f1e6"), (l - 0.04) / 0.42),
    # sable / wolf grey: dark grey saddle, silver-fawn body
    "sable": lambda l: mix(hexc("2e2a28"), hexc("b4a896"), (l - 0.04) / 0.46),
}

for name, fn in VARIANTS.items():
    img = src.copy()
    for y in range(min(128, img.height)):
        for x in range(img.width):
            c = img.getpixel((x, y))
            if c[3] == 0 or (x, y) in keep or c[:3] in KEEP_COLOURS:
                continue
            h, s, v = colorsys.rgb_to_hsv(*(ch / 255 for ch in c[:3]))
            if s > 0.6 and (h < 0.03 or h > 0.9) and v > 0.4:  # reds (tongue, mouth) stay
                continue
            img.putpixel((x, y), fn(lum(c)) + (255,))
    img.save(f"{T}/dog_{name}.png")
    print("wrote", name)
