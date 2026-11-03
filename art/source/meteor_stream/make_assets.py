"""Textures of the Meteor Stream: the artillery ring on the ground (4 frames) and the ability wheel icon.

Run from the repository root: python3 art/source/meteor_stream/make_assets.py
"""
import math
from PIL import Image, ImageDraw, ImageFilter

PARTICLE = "src/main/resources/assets/infinity_gauntlet/textures/particle/meteor_reticle_%d.png"
ICON = "src/main/resources/assets/infinity_gauntlet/textures/gui/ability_wheel/icons_64/meteor_stream.png"
N = 256
C = N / 2


def ring_frame(i):
    """Orange-red artillery ring: outer ring, tick marks, rotating inner arcs, centre cross, soft glow."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    glow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d, g = ImageDraw.Draw(img), ImageDraw.Draw(glow)
    hot, core = (255, 120, 40), (255, 214, 140)
    # soft fill toward the rim, so the area reads as a zone
    for r in range(int(C) - 2, int(C) - 40, -1):
        a = int(70 * (1 - (int(C) - r) / 40))
        g.ellipse([C - r, C - r, C + r, C + r], outline=(255, 70, 20, max(0, a)))
    g.ellipse([8, 8, N - 8, N - 8], outline=(255, 90, 30, 160), width=10)
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    img.alpha_composite(glow)
    d.ellipse([6, 6, N - 6, N - 6], outline=hot + (255,), width=5)
    d.ellipse([12, 12, N - 12, N - 12], outline=core + (200,), width=1)
    # tick marks
    for k in range(48):
        a = k * math.tau / 48
        long = k % 4 == 0
        r0, r1 = C - (24 if long else 16), C - 8
        d.line([(C + math.cos(a) * r0, C + math.sin(a) * r0), (C + math.cos(a) * r1, C + math.sin(a) * r1)], fill=hot + (230,), width=3 if long else 1)
    # rotating inner arcs
    spin = i * math.tau / 16
    for ring_r, width, count in ((C * 0.62, 4, 4), (C * 0.36, 3, 3)):
        for k in range(count):
            a0 = spin * (1 if ring_r > C * 0.5 else -1) + k * math.tau / count
            d.arc([C - ring_r, C - ring_r, C + ring_r, C + ring_r], math.degrees(a0), math.degrees(a0) + 50, fill=core + (235,), width=width)
    # centre cross + dot
    d.line([(C - 22, C), (C + 22, C)], fill=core + (255,), width=3)
    d.line([(C, C - 22), (C, C + 22)], fill=core + (255,), width=3)
    d.ellipse([C - 5, C - 5, C + 5, C + 5], fill=(255, 240, 200, 255))
    return img


def icon():
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # sun in the corner
    d.ellipse([40, 2, 62, 24], fill=(255, 196, 70, 255))
    d.ellipse([44, 6, 58, 20], fill=(255, 232, 140, 255))
    # meteors streaking down-left with fiery tails
    for x0, y0, length in ((46, 22, 28), (34, 20, 24), (54, 30, 22), (26, 34, 18)):
        for t in range(length):
            k = t / length
            x, y = x0 - t * 0.9, y0 + t * 0.9
            r = 1 + 3 * (1 - k)
            col = (255, int(90 + 140 * (1 - k)), int(30 + 40 * (1 - k)), int(255 * (1 - 0.7 * k)))
            d.ellipse([x - r, y - r, x + r, y + r], fill=col)
        d.ellipse([x0 - length * 0.9 - 4, y0 + length * 0.9 - 4, x0 - length * 0.9 + 4, y0 + length * 0.9 + 4], fill=(60, 40, 36, 255), outline=(255, 150, 60, 255))
    # ground ring
    d.ellipse([4, 48, 60, 62], outline=(255, 110, 40, 255), width=2)
    d.ellipse([16, 52, 48, 59], outline=(255, 214, 140, 200), width=1)
    return im


if __name__ == "__main__":
    for i in range(4):
        ring_frame(i).save(PARTICLE % i)
    icon().save(ICON)
