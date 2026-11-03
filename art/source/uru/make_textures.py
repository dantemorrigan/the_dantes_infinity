"""Armor of Uru textures.

1. Repaints the pack's worn-armor UV layers (2x vanilla layout, 128x64) closer to the concept art: dark forged
   graphite instead of light grey, silver bevels, gold cuffs/knee frames and cyan runes on the outer faces.
2. Paints the small atlas used by the 3D details (wings, pauldrons, gem, tabard...).

Run from the repository root: python3 art/source/uru/make_textures.py
"""
import colorsys
import random

from PIL import Image

SRC = "art/source/uru/pack_%s.png"
OUT = "src/main/resources/assets/infinity_gauntlet/textures/entity/equipment/%s/uru.png"
ATLAS = "src/main/resources/assets/infinity_gauntlet/textures/item/uru_detail.png"

TEAL, TEAL_DIM = (70, 232, 240, 255), (24, 110, 122, 255)
GOLD, GOLD_DARK = (214, 168, 74, 255), (138, 98, 40, 255)
SILVER, SHADOW = (176, 182, 194, 255), (16, 16, 22, 255)

RUNES = [
    ["#..#", "#.#.", "##..", "#..#", "#.#.", "##..", "#..."],   # fehu-ish
    [".#..", "###.", "#.#.", ".#..", ".#..", ".#..", ".#.."],   # tiwaz
    ["#..#", "##.#", "#.##", "#..#", "#..#", "#..#", "#..#"],   # mannaz-ish
    ["#...", "##..", "#.#.", "#..#", "#.#.", "##..", "#..."],   # thurisaz-ish
    [".#.", "#.#", ".#.", "#.#", ".#.", ".#.", ".#."],          # ingwaz/othala-ish
]


def classify(px):
    r, g, b, a = px
    if a == 0:
        return "none"
    if b > r + 40 and g > r + 40:
        return "teal"
    if r > b + 45 and g > b + 15:
        return "gold"
    return "metal"


def recolor(img, rnd):
    out = img.copy()
    p, q = img.load(), out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = p[x, y]
            kind = classify(p[x, y])
            if kind == "none":
                continue
            if kind == "teal":
                q[x, y] = TEAL
            elif kind == "gold":
                hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
                r2, g2, b2 = colorsys.hls_to_rgb(hh, min(0.62, ll * 1.05), min(1, ss * 1.1))
                q[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
            else:
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                if lum > 0.72:
                    q[x, y] = SILVER
                elif lum < 0.18:
                    q[x, y] = SHADOW
                else:
                    t = (lum - 0.18) / 0.54
                    n = rnd.uniform(-0.06, 0.06)
                    base = [int(30 + 46 * (t + n)), int(31 + 46 * (t + n)), int(38 + 50 * (t + n))]
                    q[x, y] = tuple(max(0, min(255, c)) for c in base) + (a,)
    return out


def opaque(img, x, y):
    w, h = img.size
    return 0 <= x < w and 0 <= y < h and img.getpixel((x, y))[3] > 0


def bevel(img, rect):
    """Silver top/left edge, dark bottom/right edge on every opaque run of a face rectangle."""
    x0, y0, x1, y1 = rect
    p = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if p[x, y][3] == 0 or classify(p[x, y]) != "metal":
                continue
            if not opaque(img, x, y - 1) or y == y0 or not opaque(img, x - 1, y) or x == x0:
                p[x, y] = (150, 156, 168, 255)
            elif not opaque(img, x, y + 1) or y == y1 - 1 or not opaque(img, x + 1, y) or x == x1 - 1:
                p[x, y] = (20, 20, 28, 255)


def rune(img, x, y, glyph):
    p = img.load()
    for gy, row in enumerate(glyph):
        for gx, c in enumerate(row):
            if c == "#" and opaque(img, x + gx, y + gy):
                for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    X, Y = x + gx + dx, y + gy + dy
                    if opaque(img, X, Y) and classify(p[X, Y]) == "metal" and p[X, Y] != TEAL:
                        p[X, Y] = TEAL_DIM
    for gy, row in enumerate(glyph):
        for gx, c in enumerate(row):
            if c == "#" and opaque(img, x + gx, y + gy):
                p[x + gx, y + gy] = TEAL


def band(img, x0, x1, y, color=GOLD, height=2):
    p = img.load()
    for yy in range(y, y + height):
        for xx in range(x0, x1):
            if opaque(img, xx, yy):
                p[xx, yy] = color if yy == y else GOLD_DARK


def humanoid(rnd):
    img = recolor(Image.open(SRC % "humanoid").convert("RGBA"), rnd)
    faces = [(0, 16, 64, 32), (16, 0, 32, 16), (32, 40, 40, 64), (40, 40, 56, 64), (56, 40, 64, 64), (64, 40, 80, 64),
             (80, 40, 88, 64), (88, 40, 96, 64), (96, 40, 104, 64), (104, 40, 112, 64), (0, 40, 32, 64)]
    for f in faces:
        bevel(img, f)
    # helmet: runes on both cheeks, gold rim at the bottom of every side
    rune(img, 6, 20, RUNES[1])
    rune(img, 38, 20, RUNES[1])
    rune(img, 54, 19, RUNES[4])
    band(img, 0, 64, 29)
    # body: runes on the lower front (tabard) and back
    rune(img, 46, 56, RUNES[0])
    # arms: pauldron gold band, rune on the outer face and the front, gold cuff at the wrist
    band(img, 80, 112, 44)
    band(img, 80, 112, 58)
    # boots (legs region of this layer): gold cuff + silver toe line
    band(img, 0, 32, 52)
    return img


def leggings(rnd):
    img = recolor(Image.open(SRC % "humanoid_leggings").convert("RGBA"), rnd)
    for f in [(32, 40, 80, 64), (0, 40, 32, 64), (40, 32, 56, 40)]:
        bevel(img, f)
    # belt across the waist
    band(img, 32, 80, 40)
    # knee frame on the front of each leg + rune on the outer face
    p = img.load()
    for x in range(9, 15):
        for y in (48, 53):
            if opaque(img, x, y):
                p[x, y] = GOLD
    for y in range(48, 54):
        for x in (9, 14):
            if opaque(img, x, y):
                p[x, y] = GOLD
    rune(img, 2, 44, RUNES[0])
    rune(img, 17, 56, RUNES[4])
    return img


def atlas(rnd):
    """32x32: graphite (0,0), gold (8,0), silver (16,0), rune (24,0), gem (0,8), dark (8,8), wing (16,8)."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()

    def fill(x0, y0, fn):
        for y in range(8):
            for x in range(8):
                p[x0 + x, y0 + y] = fn(x, y)

    def noise(base, amp):
        return lambda x, y: tuple(max(0, min(255, c + rnd.randint(-amp, amp))) for c in base) + (255,)

    def edged(base, light, dark, amp):
        def fn(x, y):
            if x == 0 or y == 0:
                return light
            if x == 7 or y == 7:
                return dark
            return noise(base, amp)(x, y)
        return fn

    fill(0, 0, edged((50, 51, 62), (128, 134, 146, 255), (18, 18, 24, 255), 6))
    fill(8, 0, edged((206, 160, 70), (246, 210, 120, 255), (120, 84, 34, 255), 10))
    fill(16, 0, edged((170, 176, 188), (226, 230, 238, 255), (96, 100, 112, 255), 8))
    glyph = ["........", ".#....#.", ".##..##.", ".#.##.#.", ".#....#.", ".#....#.", ".#....#.", "........"]
    fill(24, 0, lambda x, y: TEAL if glyph[y][x] == "#" else (12, 40, 46, 255))
    fill(0, 8, lambda x, y: (235, 255, 255, 255) if abs(x - 3.5) + abs(y - 3.5) < 1.6 else (90, 240, 248, 255) if abs(x - 3.5) + abs(y - 3.5) < 3.2 else (30, 150, 170, 255))
    fill(8, 8, noise((26, 26, 34), 4))
    feather = lambda x, y: (232, 236, 242, 255) if y == 0 or x == 0 else (110, 116, 128, 255) if y == 7 or x == 7 else noise((178, 184, 196), 8)(x, y)
    fill(16, 8, feather)
    return img


if __name__ == "__main__":
    rnd = random.Random(26)
    humanoid(rnd).save(OUT % "humanoid")
    leggings(rnd).save(OUT % "humanoid_leggings")
    atlas(rnd).save(ATLAS)
