"""Generates the burnt / cracked textures of the gauntlet and stones after the Infinity Snap.

Run from the repository root: python3 art/source/snap/make_damaged.py
"""
import colorsys
import random
from pathlib import Path

from PIL import Image

ITEM = Path("src/main/resources/assets/infinity_gauntlet/textures/item")
STONES = ["power", "space", "reality", "soul", "mind", "time"]


def scorch(im, rng, sat, light, soot, cracks, crack_len):
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    # Desaturate / darken, with blotchy soot.
    blot = [[0.0] * w for _ in range(h)]
    for _ in range(soot):
        cx, cy, r = rng.randrange(w), rng.randrange(h), rng.uniform(w * 0.04, w * 0.14)
        for y in range(max(0, int(cy - r)), min(h, int(cy + r) + 1)):
            for x in range(max(0, int(cx - r)), min(w, int(cx + r) + 1)):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / r
                if d < 1:
                    blot[y][x] = max(blot[y][x], (1 - d) * 0.55)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            ll = ll * light * (1 - blot[y][x]) * rng.uniform(0.9, 1.05)
            ss = ss * sat
            r2, g2, b2 = colorsys.hls_to_rgb(hh, ll, ss)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    # Cracks: dark random walks with a faint light edge.
    for _ in range(cracks):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        ang = rng.uniform(0, 6.283)
        for _ in range(int(crack_len * w)):
            ix, iy = int(x), int(y)
            if 0 <= ix < w and 0 <= iy < h and px[ix, iy][3] > 0:
                px[ix, iy] = (14, 10, 8, px[ix, iy][3])
                ex, ey = ix + 1, iy
                if ex < w and px[ex, ey][3] > 0:
                    r, g, b, a = px[ex, ey]
                    px[ex, ey] = (min(255, r + 25), min(255, g + 20), min(255, b + 15), a)
            ang += rng.uniform(-0.6, 0.6)
            import math
            x += math.cos(ang)
            y += math.sin(ang)
            if rng.random() < 0.04:
                ang += rng.choice([-1.2, 1.2])
    return im


def main():
    rng = random.Random(1234)
    gold = dict(sat=0.45, light=0.62, soot=14, cracks=10, crack_len=0.35)
    stone = dict(sat=0.3, light=0.5, soot=3, cracks=3, crack_len=0.45)
    scorch(Image.open(ITEM / "gauntlet.png"), rng, **{**gold, "cracks": 6}).save(ITEM / "gauntlet_damaged.png")
    scorch(Image.open(ITEM / "gauntlet_model.png"), rng, **gold).save(ITEM / "gauntlet_model_damaged.png")
    for s in STONES:
        scorch(Image.open(ITEM / f"gauntlet_{s}.png"), rng, **{**stone, "cracks": 1, "soot": 0}).save(ITEM / f"gauntlet_{s}_damaged.png")
        scorch(Image.open(ITEM / f"gauntlet_model_{s}.png"), rng, **{**stone, "cracks": 4}).save(ITEM / f"gauntlet_model_{s}_damaged.png")
        scorch(Image.open(ITEM / f"{s}_stone.png"), rng, **stone).save(ITEM / f"{s}_stone_damaged.png")


if __name__ == "__main__":
    main()
