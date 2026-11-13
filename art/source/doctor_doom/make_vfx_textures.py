"""Soft white textures for Doom's world effects (tinted by vertex colour, drawn additively-looking with the eyes type).

Run from the repository root: python3 art/source/doctor_doom/make_vfx_textures.py
"""
import math

from PIL import Image

OUT = "src/main/resources/assets/infinity_gauntlet/textures/fx/doom/"


def save(name, w, h, f):
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            a = max(0.0, min(1.0, f((x + 0.5) / w, (y + 0.5) / h)))
            px[x, y] = (255, 255, 255, int(a * 255))
    img.save(OUT + name + ".png")


def glow(u, v):
    r = math.hypot(u - 0.5, v - 0.5) * 2
    return (1 - r) ** 2.2 if r < 1 else 0


def core(u, v):
    r = math.hypot(u - 0.5, v - 0.5) * 2
    return 1 if r < 0.35 else max(0, 1 - (r - 0.35) / 0.65) ** 3


def ring(u, v):
    r = math.hypot(u - 0.5, v - 0.5) * 2
    return math.exp(-((r - 0.88) / 0.07) ** 2) + 0.25 * math.exp(-((r - 0.7) / 0.18) ** 2)


def streak(u, v):
    across = math.exp(-((v - 0.5) / 0.2) ** 2)
    return across * (u ** 0.6)


def flame(u, v):
    across = math.exp(-((u - 0.5) / (0.18 + 0.2 * v)) ** 2)
    return across * (1 - v) ** 1.4


def column(u, v):
    edge = math.exp(-((u - 0.5) / 0.32) ** 2)
    return edge * (1 - v) ** 0.8 * (0.75 + 0.25 * math.sin(v * 40))


def hexes(u, v):
    # A hexagonal lattice of glowing lines, for the Iron Bastion's shell.
    x, y = u * 6, v * 6 * 1.1547
    best = 9
    for cx in range(-1, 8):
        for cy in range(-1, 9):
            px = cx + (0.5 if cy % 2 else 0)
            py = cy * 0.866
            best = min(best, math.hypot(x - px, y - py))
    edge = abs(best - 0.5)
    return 0.12 + 0.88 * math.exp(-(edge / 0.06) ** 2)


save("glow", 64, 64, glow)
save("core", 64, 64, core)
save("ring", 128, 128, ring)
save("streak", 64, 16, streak)
save("flame", 32, 64, flame)
save("column", 32, 64, column)
save("hex", 128, 128, hexes)
print("ok")
