#!/usr/bin/env python3
"""Textures for the Power Stone fall: purple meteorite block, the Orb (block + items). Run from the repo root."""
import random, math, pathlib
from PIL import Image

OUT = pathlib.Path('src/main/resources/assets/infinity_gauntlet/textures')
rnd = random.Random(7)

def clamp(v): return max(0, min(255, int(v)))

# ---- purple meteorite: dark fused rock with glowing violet veins ----
def meteorite(seed, vein_boost=0):
    r = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    base = [[0]*16 for _ in range(16)]
    for y in range(16):
        for x in range(16):
            n = r.random()*0.5 + 0.5*math.sin(x*0.9+seed)*math.cos(y*0.7-seed)
            base[y][x] = n
    for y in range(16):
        for x in range(16):
            n = base[y][x]
            px[x, y] = (clamp(38 + n*22), clamp(24 + n*12), clamp(52 + n*26), 255)
    # veins: random walks
    for v in range(3 + vein_boost):
        x, y = r.randrange(16), r.randrange(16)
        for _ in range(14):
            px[x % 16, y % 16] = (clamp(190 + r.random()*50), clamp(70 + r.random()*40), 255, 255)
            nx = (x + r.choice((-1, 0, 1))) % 16; ny = (y + r.choice((-1, 0, 1))) % 16
            x, y = nx, ny
    # crust pits
    for _ in range(10):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = (18, 12, 26, 255)
    return img

(OUT/'block').mkdir(parents=True, exist_ok=True)
meteorite(3).save(OUT/'block/purple_meteorite.png')
meteorite(11, vein_boost=3).save(OUT/'block/purple_meteorite_core.png')

# ---- the Orb: dark metal sphere with bronze bands (block face texture + item icons) ----
def orb(open_=False, size=16):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2; R = size/2 - 1
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c)/R, (y - c)/R
            d = dx*dx + dy*dy
            if d > 1: continue
            z = math.sqrt(1 - d)
            light = 0.35 + 0.65*max(0, (-dx*0.5 - dy*0.6 + z*0.6))
            col = (90, 92, 104)
            # bands
            if abs(dy) < 0.12 or abs(dx*0.8 + dy*0.2) < 0.08:
                col = (176, 128, 58)
            if open_ and abs(dy) < 0.16:
                col = (24, 10, 34)  # split seam, empty
            elif not open_ and abs(dy) < 0.05:
                col = (220, 110, 255)  # seam glowing with the stone inside
            px[x, y] = (clamp(col[0]*light), clamp(col[1]*light), clamp(col[2]*light), 255)
    return img

orb().save(OUT/'block/power_orb.png')
(OUT/'item').mkdir(parents=True, exist_ok=True)
orb().save(OUT/'item/power_orb.png')
orb(open_=True).save(OUT/'item/power_orb_open.png')
print('ok')
