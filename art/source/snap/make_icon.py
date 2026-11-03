"""Ability wheel icon for the Infinity Snap: the full gauntlet with a white snap burst at the fingertips.

Run from the repository root: python3 art/source/snap/make_icon.py
"""
import math
from PIL import Image, ImageDraw

ITEM = "src/main/resources/assets/infinity_gauntlet/textures/item/"
OUT = "src/main/resources/assets/infinity_gauntlet/textures/gui/ability_wheel/icons_64/infinity_snap.png"

icon = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
burst = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
d = ImageDraw.Draw(burst)
cx, cy = 40, 14
for i in range(12):
    a = i * math.pi / 6 + 0.2
    r = 17 if i % 2 == 0 else 11
    d.line([(cx, cy), (cx + math.cos(a) * r, cy + math.sin(a) * r)], fill=(255, 255, 255, 230), width=2)
for r, alpha in ((9, 70), (6, 150), (3, 255)):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, alpha))
icon.alpha_composite(burst)

g = Image.open(ITEM + "gauntlet.png").convert("RGBA")
for s in ["power", "space", "reality", "soul", "mind", "time"]:
    g.alpha_composite(Image.open(ITEM + f"gauntlet_{s}.png").convert("RGBA"))
g = g.resize((46, 46), Image.NEAREST)
icon.alpha_composite(g, (4, 16))
icon.alpha_composite(burst.crop((cx - 4, cy - 4, cx + 5, cy + 5)), (cx - 4, cy - 4))
icon.save(OUT)
