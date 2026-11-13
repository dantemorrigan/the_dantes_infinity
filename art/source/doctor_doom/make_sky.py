"""Latveria's sky: a stormy Carpathian night. All layers are baked here and drawn by client/doom/LatveriaSky.

Layers (all wrap seamlessly around the horizon where they are bands):
  dome      storm-dark gradient with turbulence, greenish toward the horizon
  clouds_a  heavy slow clouds, dark with sickly green rims (alpha)
  clouds_b  a second, faster layer for parallax
  ridges    three ridgelines of the Carpathians with snow caps and pine ragged edges, filled below the horizon with fog colour
  aurora    green curtains of "Doom's light" high in the north
  moon      a big pale moon with a green halo (disc and glow)
  bolt      a forked lightning bolt

Run from the repository root: python3 art/source/doctor_doom/make_sky.py
"""
import math

import numpy as np
from PIL import Image, ImageFilter

OUT = "src/main/resources/assets/infinity_gauntlet/textures/environment/latveria/"
FOG = (0x3a, 0x4a, 0x46)  # must match dimension_type latveria fog_color
rng = np.random.default_rng(1947)


def periodic_noise(w, h, cells_x, cells_y, octaves=5, persistence=0.55, seed=0):
    """Fractal value noise that wraps in x."""
    r = np.random.default_rng(seed)
    total = np.zeros((h, w))
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cx, cy = cells_x * 2 ** o, cells_y * 2 ** o
        grid = r.random((cy + 2, cx))
        xs = np.linspace(0, cx, w, endpoint=False)
        ys = np.linspace(0, cy, h, endpoint=False)
        x0 = np.floor(xs).astype(int)
        y0 = np.floor(ys).astype(int)
        fx, fy = xs - x0, ys - y0
        fx = fx * fx * (3 - 2 * fx)
        fy = fy * fy * (3 - 2 * fy)
        g = lambda yy, xx: grid[np.ix_(yy, xx % cx)]
        a = g(y0, x0) * (1 - fx)[None, :] + g(y0, x0 + 1) * fx[None, :]
        b = g(y0 + 1, x0) * (1 - fx)[None, :] + g(y0 + 1, x0 + 1) * fx[None, :]
        total += (a * (1 - fy)[:, None] + b * fy[:, None]) * amp
        norm += amp
        amp *= persistence
    return total / norm


def save(name, arr):
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(OUT + name + ".png")


def mix(a, b, t):
    return a + (b - a) * t[..., None] if np.ndim(t) == 2 else a + (b - a) * t


# ---------------------------------------------------------------------------------------------------- dome
def dome():
    w, h = 1024, 256
    v = np.linspace(0, 1, h)[:, None] * np.ones((1, w))  # 0 zenith .. 1 band top
    n = periodic_noise(w, h, 6, 3, 6, 0.6, 1)
    swirl = periodic_noise(w, h, 3, 2, 4, 0.5, 2)
    zen, mid, low = np.array([8, 14, 16.0]), np.array([20, 34, 36.0]), np.array([58, 80, 70.0])
    t = v ** 1.5
    base = np.where((t < 0.5)[..., None], mix(zen, mid, np.clip(t * 2, 0, 1)), mix(mid, low, np.clip((t - 0.5) * 2, 0, 1)))
    turb = (n - 0.5) * 2
    glow = np.clip(swirl - 0.5, 0, 1) * 2
    out = base * (1 + 0.45 * turb[..., None]) + np.array([4, 30, 14.0]) * glow[..., None] * (0.3 + v[..., None])
    a = np.full((h, w), 255.0)
    return np.dstack([out, a])


# ---------------------------------------------------------------------------------------------------- clouds
def clouds(name, seed, cx, cy, density, lift, w=2048, h=256):
    n = periodic_noise(w, h, cx, cy, 6, 0.58, seed)
    n2 = periodic_noise(w, h, cx * 2, cy * 2, 4, 0.5, seed + 9)
    y = np.linspace(0, 1, h)[:, None]
    fade = np.sin(np.pi * np.clip(y, 0, 1)) ** 1.6  # thin at the top and bottom of the band, so no edge shows
    d = np.clip((n - (1 - density)) / density, 0, 1) * fade
    d = np.clip(d + (n2 - 0.5) * 0.25 * d, 0, 1)
    edge = np.clip(1 - d * 2.2, 0, 1)  # thin parts catch the glow
    dark = np.array([20, 31, 34.0]) + lift
    rim = np.array([120, 185, 150.0])
    rgb = mix(dark, rim, np.clip(edge * (0.35 + 0.7 * n2), 0, 1))
    # underlit: the lower edge is a little brighter, as if lit by the city's torches
    shade = 1 + 0.25 * (y - 0.5)
    rgb = rgb * shade[..., None]
    alpha = np.clip(d ** 0.7 * 1.15, 0, 1) * 245
    return np.dstack([rgb, alpha])


# ---------------------------------------------------------------------------------------------------- ridges
def ridgeline(w, base, amp, seed, cells, sharp):
    n = periodic_noise(w, 1, cells, 1, 6, 0.5, seed)[0]
    ridge = 1 - np.abs(n * 2 - 1)  # sharp crests
    n = (n - n.min()) / (n.max() - n.min())
    ridge = (ridge - ridge.min()) / (ridge.max() - ridge.min())
    return base - amp * (sharp * ridge ** 1.3 + (1 - sharp) * n)


def ridges():
    w, h = 4096, 256
    rows = np.arange(h)[:, None]
    fog = np.array(FOG, float)
    rgb = np.zeros((h, w, 3)) + fog
    alpha = np.zeros((h, w))
    horizon = h * 0.657
    layers = [  # base row, amplitude, colour, snow depth (rows), seed, crest noise cells, sharpness
        (0.545 * h, 0.30 * h, np.array([78, 98, 104.0]), 22, 11, 9, 0.85),
        (0.600 * h, 0.22 * h, np.array([44, 60, 64.0]), 10, 21, 13, 0.75),
        (0.650 * h, 0.14 * h, np.array([19, 29, 31.0]), 0, 31, 19, 0.6),
    ]
    for base, amp, col, snow, seed, cells, sharp in layers:
        line = ridgeline(w, base, amp, seed, cells, sharp)
        mask = rows >= line[None, :]
        depth = np.clip((rows - line[None, :]) / (h * 0.28), 0, 1)
        gully = 0.82 + 0.18 * periodic_noise(w, h, 300, 3, 2, 0.5, seed + 3)
        shade = mix(col * 1.3, col * 0.65, depth) * gully[..., None]
        height = np.clip((base - line) / amp, 0, 1)
        if snow:
            snowline = line + snow * (0.5 + 1.1 * periodic_noise(w, 1, 160, 1, 3, 0.5, seed + 7)[0]) * height ** 0.9
            cap = (rows >= line[None, :]) & (rows < snowline[None, :]) & (height[None, :] > 0.28)
            lit = 0.82 + 0.18 * periodic_noise(w, h, 260, 3, 2, 0.5, seed + 4)
            fill = np.where(cap[..., None], np.array([204, 222, 222.0]) * lit[..., None], shade)
        else:
            fill = shade
        rgb = np.where(mask[..., None], fill, rgb)
        alpha = np.where(mask, 255, alpha)
    below = rows >= horizon
    rgb = np.where(below[..., None] & (alpha[..., None] == 0), fog, rgb)
    alpha = np.where(below, 255, alpha)
    haze = np.clip((rows - horizon + 46) / 90.0, 0, 1)
    rgb = mix(rgb, fog, haze * 0.7)
    return np.dstack([rgb, alpha])


# ---------------------------------------------------------------------------------------------------- aurora
def aurora():
    w, h = 2048, 256
    x = np.linspace(0, 1, w, endpoint=False)[None, :]
    y = np.linspace(0, 1, h)[:, None]
    n = periodic_noise(w, h, 24, 2, 4, 0.5, 5)
    sway = periodic_noise(w, 1, 5, 1, 3, 0.5, 6)[0][None, :]
    streaks = np.clip(np.sin(x * 2 * np.pi * 90 + n * 9) * 0.5 + 0.5, 0, 1) ** 2.4
    lobe = np.exp(-(((x - 0.5 + (sway - 0.5) * 0.12) / 0.2) ** 2))  # one great curtain in the north (u=0.5)
    vertical = np.exp(-(((y - 0.78) / 0.32) ** 2)) * (1 - y) ** 0.4
    a = np.clip(streaks * lobe * vertical * (0.45 + 0.55 * n), 0, 1)
    rgb = mix(np.array([40, 230, 120.0]), np.array([170, 255, 210.0]), np.clip((y - 0.2) * 1.4, 0, 1) * np.ones((1, w)))
    return np.dstack([rgb, a * 255])


# ---------------------------------------------------------------------------------------------------- moon and bolt
def moon():
    s = 256
    yy, xx = np.mgrid[0:s, 0:s]
    r = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5) / (s / 2)
    n = periodic_noise(s, s, 3, 3, 6, 0.55, 3)
    maria = np.clip((n - 0.52) * 3.2, 0, 1)
    disc = np.clip((0.985 - r) / 0.02, 0, 1)
    shade = 236 - 62 * maria - 26 * r ** 4
    craters = np.zeros((s, s))
    r2 = np.random.default_rng(5)
    for _ in range(26):
        cx, cy, cr = r2.uniform(30, 226), r2.uniform(30, 226), r2.uniform(4, 13)
        d = np.hypot(xx - cx, yy - cy) / cr
        craters += np.where(d < 1, 0.18 * (1 - d) , 0) - np.where((d > 0.85) & (d < 1.05), 0.12, 0)
    shade = shade * (1 + craters)
    rgb = np.dstack([shade * 0.94, shade * 1.0, shade * 0.93])
    return np.dstack([rgb, disc * 255])


def moon_glow():
    s = 256
    yy, xx = np.mgrid[0:s, 0:s]
    r = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5) / (s / 2)
    a = np.clip(1 - r, 0, 1) ** 2.8
    rgb = np.dstack([np.full((s, s), 120.0), np.full((s, s), 255.0), np.full((s, s), 170.0)])
    return np.dstack([rgb, a * 200])


def bolt():
    w, h = 128, 512
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = np.zeros((h, w))
    r = np.random.default_rng(7)
    x = w / 2
    pts = []
    for y in range(0, h, 6):
        x += r.normal(0, 4.5) + (w / 2 - x) * 0.05
        pts.append((x, y))
        if y > 120 and r.random() < 0.12:  # forks
            fx = x
            for k in range(1, 10):
                fx += r.normal(3.5, 3)
                pts.append((fx, y + k * 6)) if False else None
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / steps
            xi, yi = int(x0 + (x1 - x0) * t), int(y0 + (y1 - y0) * t)
            if 0 <= xi < w and 0 <= yi < h:
                px[yi, xi] = 1
    im = Image.fromarray((px * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.1))
    core = np.array(im).astype(float) / 255
    glow = np.array(im.filter(ImageFilter.GaussianBlur(7))).astype(float) / 255
    a = np.clip(core * 3 + glow * 4, 0, 1)
    rgb = np.dstack([np.full((h, w), 190.0) + 65 * core, np.full((h, w), 255.0), np.full((h, w), 215.0)])
    fade = np.clip(1 - np.arange(h)[:, None] / h * 0.35, 0, 1)
    return np.dstack([rgb, a * 255 * fade])


def vignette():
    s = 256
    yy, xx = np.mgrid[0:s, 0:s]
    r = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5) / (s / 2 * math.sqrt(2))
    a = np.clip((r - 0.42) / 0.58, 0, 1) ** 1.6
    rgb = np.dstack([np.full((s, s), 2.0), np.full((s, s), 12.0), np.full((s, s), 8.0)])
    return np.dstack([rgb, a * 215])


save("dome", dome())
save("clouds_a", clouds("a", 3, 7, 3, 0.72, np.array([0, 0, 0.0])))
save("clouds_b", clouds("b", 8, 11, 3, 0.6, np.array([10, 14, 12.0])))
save("ridges", ridges())
save("aurora", aurora())
save("moon", moon())
save("moon_glow", moon_glow())
save("bolt", bolt())
Image.fromarray(vignette().astype(np.uint8), "RGBA").save("src/main/resources/assets/infinity_gauntlet/textures/gui/doctor_doom/vignette.png")
print("ok")
