"""God Emperor Doom (Secret Wars, 2015): Doom's own texture recoloured into the ivory robes, pale-gold trim and
silvered mask of the ruler of Battleworld. Shading is kept: every pixel keeps its lightness inside the new palette.

Run from the repository root: python3 art/source/doctor_doom/make_god_doom.py
"""
import colorsys

from PIL import Image

SRC = "src/main/resources/assets/infinity_gauntlet/textures/entity/doctor_doom/doctor_doom.png"
OUT = "src/main/resources/assets/infinity_gauntlet/textures/entity/doctor_doom/god_emperor_doom.png"

IVORY = (246, 240, 224)
SILVER = (214, 220, 230)
GOLD = (240, 196, 92)


def tint(base, k):
    return tuple(max(0, min(255, int(c * k))) for c in base)


def recolor(p):
    r, g, b, a = p
    if a == 0:
        return p
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    hue = h * 360
    if s > 0.2 and 90 <= hue <= 170:
        # Doom's green cloth: ivory robes, the dark folds become soft shadow.
        k = 0.58 + min(1.0, l / 0.22) * 0.42
        return tint(IVORY, k) + (a,)
    if s > 0.25 and 25 <= hue <= 55 and l > 0.25:
        # Gold trim: brighter, warmer.
        return tint(GOLD, 0.75 + l * 0.5) + (a,)
    if s > 0.2 and (hue < 25 or hue > 330) and l < 0.35:
        # Leather straps and belts: deep gold.
        return tint(GOLD, 0.45 + l * 1.2) + (a,)
    if s > 0.35 and 15 <= hue <= 45 and l <= 0.25:
        return tint(GOLD, 0.5 + l) + (a,)
    if l < 0.05:
        # The darkest gaps (eye slits, seams) stay dark: they read as depth.
        return (18, 16, 14, a)
    # Steel: a silvered mask and plates under the robes.
    k = 0.55 + min(1.0, l / 0.35) * 0.45
    return tint(SILVER, k) + (a,)


FX = "src/main/resources/assets/infinity_gauntlet/textures/fx/doom/"


def noise(x, y, seed):
    """Smooth value noise in [0, 1]."""
    import math
    def h(i, j):
        n = (i * 374761393 + j * 668265263 + seed * 1442695041) & 0xFFFFFFFF
        n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
        return (n ^ (n >> 16)) / 0xFFFFFFFF
    xi, yi = math.floor(x), math.floor(y)
    fx, fy = x - xi, y - yi
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b, c, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(x, y, seed, octaves=5):
    v, amp, tot = 0.0, 1.0, 0.0
    for o in range(octaves):
        v += noise(x * 2 ** o, y * 2 ** o, seed + o) * amp
        tot += amp
        amp *= 0.5
    return v / tot


def incursion_earth(size=384):
    """The other Earth of the last Incursion: oceans, continents, clouds, lit from one side, a burning red rim."""
    import math
    img = Image.new("RGBA", (size, size))
    px = img.load()
    c = size / 2
    r0 = size * 0.40
    for y in range(size):
        for x in range(size):
            dx, dy = (x + 0.5 - c) / r0, (y + 0.5 - c) / r0
            d = math.hypot(dx, dy)
            if d <= 1:
                z = math.sqrt(1 - d * d)
                u, v = math.atan2(dx, z) * 1.6 + 3, dy * 1.6 + 3
                land = fbm(u * 1.4, v * 1.4, 7)
                cloud = fbm(u * 2.6 + 9, v * 3.2, 31)
                if land > 0.53:
                    t = min(1, (land - 0.53) / 0.2)
                    col = (int(70 + 70 * t), int(110 + 30 * t), int(50 + 20 * t))
                else:
                    t = land / 0.53
                    col = (int(18 + 20 * t), int(52 + 50 * t), int(110 + 70 * t))
                if cloud > 0.58:
                    k = min(1, (cloud - 0.58) / 0.15)
                    col = tuple(int(a * (1 - k) + 245 * k) for a in col)
                light = max(0.12, 0.25 + 0.85 * (-dx * 0.55 - dy * 0.35 + z * 0.6))
                col = tuple(min(255, int(a * light)) for a in col)
                rim = max(0, (d - 0.86) / 0.14)
                col = (min(255, int(col[0] + 255 * rim)), int(col[1] * (1 - rim * 0.6) + 60 * rim), int(col[2] * (1 - rim)))
                px[x, y] = col + (255,)
            elif d < 1.25:
                k = (1.25 - d) / 0.25
                px[x, y] = (255, 90, 40, int(255 * k ** 2.2))
            else:
                px[x, y] = (0, 0, 0, 0)
    img.save(FX + "incursion_earth.png")


def cracks(w=640, h=360, seed=5):
    """Reality breaking: glowing branching cracks across the view, white at the core, gold at the edge."""
    import math, random
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    from PIL import ImageDraw, ImageFilter
    glow = Image.new("RGBA", (w, h))
    dg, dc = ImageDraw.Draw(glow), ImageDraw.Draw(img)

    def branch(x, y, ang, length, width, depth):
        while length > 0 and 0 <= x < w and 0 <= y < h:
            step = rnd.uniform(8, 22)
            ang += rnd.uniform(-0.6, 0.6)
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            dg.line([(x, y), (nx, ny)], fill=(255, 200, 90, 200), width=int(width * 4 + 2))
            dc.line([(x, y), (nx, ny)], fill=(255, 250, 235, 255), width=max(1, int(width)))
            if depth < 3 and rnd.random() < 0.18:
                branch(nx, ny, ang + rnd.choice([-1, 1]) * rnd.uniform(0.6, 1.2), length * 0.5, width * 0.6, depth + 1)
            x, y = nx, ny
            length -= step
            width = max(1, width * 0.97)

    for i in range(9):
        a = i * math.tau / 9 + rnd.uniform(-0.2, 0.2)
        branch(w / 2 + rnd.uniform(-30, 30), h / 2 + rnd.uniform(-20, 20), a, rnd.uniform(220, 420), 3.2, 0)
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    out = Image.alpha_composite(glow, img)
    out.save(FX + "multiverse_cracks.png")


def main():
    incursion_earth()
    cracks()
    img = Image.open(SRC).convert("RGBA")
    out = Image.new("RGBA", img.size)
    src, dst = img.load(), out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            dst[x, y] = recolor(src[x, y])
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
