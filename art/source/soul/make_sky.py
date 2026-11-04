#!/usr/bin/env python3
"""Soul world sky from the pack's soul_horizon.png: a seamless 360-degree cloud band (sun removed), a zenith gradient
and a sun glow. Run from the repo root."""
from PIL import Image, ImageFilter, ImageDraw
import numpy as np, pathlib
ENV = pathlib.Path('src/main/resources/assets/infinity_gauntlet/textures/environment')
src = Image.open(ENV / 'soul_horizon.png').convert('RGB')
W, H = src.size
a = np.asarray(src).astype(np.float32)
# Remove the sun and its reflection: fill the middle columns from the same rows a bit to the side, blended.
c0, c1 = int(W * 0.43), int(W * 0.57)
w = c1 - c0
left = a[:, c0 - w:c0]
right = a[:, c1:c1 + w]
t = np.linspace(0, 1, w)[None, :, None]
a[:, c0:c1] = left[:, ::-1] * (1 - t) + right[:, ::-1] * t
band = np.concatenate([a, a[:, ::-1]], axis=1)  # mirrored copy -> seamless when wrapped
img = Image.fromarray(band.clip(0, 255).astype(np.uint8)).resize((2048, 342), Image.LANCZOS)
img.save(ENV / 'soul_sky_band.png')
# Zenith gradient: band's top colour to near-black.
top = np.asarray(img)[:6].reshape(-1, 3).mean(0)
g = Image.new('RGB', (4, 256))
for y in range(256):
    k = y / 255  # 0 = zenith, 1 = band top
    col = tuple(int(v) for v in (np.array([6, 0, 0]) * (1 - k) + top * k))
    for x in range(4): g.putpixel((x, y), col)
g.save(ENV / 'soul_sky_dome.png')
# Sun glow.
s = 128
glow = Image.new('RGBA', (s, s))
px = glow.load()
for y in range(s):
    for x in range(s):
        d = ((x - s / 2) ** 2 + (y - s / 2) ** 2) ** 0.5 / (s / 2)
        al = max(0.0, 1 - d) ** 2.2
        px[x, y] = (255, 40, 25, int(al * 200))
glow.save(ENV / 'soul_sun_glow.png')
print('ok', img.size)
