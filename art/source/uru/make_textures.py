"""Armor of Uru textures (painted from scratch, 4x vanilla resolution).

* Worn layers: 256x128 (the vanilla 64x32 armor UV layout at 4x): forged graphite plates with bevels, lames,
  gold trim, engraved glowing runes, a gem socket, a visor, sabatons.
* Detail atlas for the 3D parts (64x64, 16x16 swatches): plate, gold, silver, rune plate, gem, feather, dark,
  gold rune.
* Item icons: the pack's icons, re-toned to the darker forged metal of the worn set.

Run from the repository root: python3 art/source/uru/make_textures.py
"""
import colorsys
import math
import random

from PIL import Image

ASSETS = "src/main/resources/assets/infinity_gauntlet/textures/"
PACK_ICON = "art/source/uru/pack_icon_%s.png"
S = 4  # pixels per vanilla texel

# palette
G = [(14, 14, 20), (25, 26, 34), (38, 40, 51), (54, 57, 72), (72, 76, 94), (98, 104, 126)]
SILVER = [(104, 110, 126), (156, 164, 180), (206, 212, 224), (240, 244, 250)]
GOLD = [(78, 50, 14), (132, 90, 28), (190, 140, 52), (230, 186, 86), (252, 228, 150)]
CYAN = [(9, 40, 48), (20, 118, 134), (52, 200, 222), (150, 246, 255), (240, 255, 255)]

RUNES = [
    ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"],  # mannaz
    ["..#..", ".###.", "#.#.#", "..#..", "..#..", "..#..", "..#.."],  # tiwaz
    ["#..#.", "#.#..", "##...", "#..#.", "#.#..", "##...", "#...."],  # fehu
    ["#....", "##...", "#.#..", "#..#.", "#.#..", "##...", "#...."],  # thurisaz
    ["..#..", ".#.#.", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"],  # ingwaz/othala
    ["#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#", "#...#"],  # gebo-ish
]


class Canvas:
    def __init__(self, w, h, rnd):
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.p = self.img.load()
        self.w, self.h = w, h
        self.rnd = rnd

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[x, y] = tuple(int(v) for v in c[:3]) + (a,)

    def get(self, x, y):
        return self.p[x, y]

    # --- primitives -------------------------------------------------------------------------------------------

    def plate(self, x0, y0, x1, y1, tone=0, rivets=False, engrave=None):
        """Forged graphite plate: soft vertical gradient, faint hammer noise and brushed lines, 2px bevel."""
        r = self.rnd
        for y in range(y0, y1):
            t = (y - y0) / max(1, y1 - y0 - 1)
            for x in range(x0, x1):
                base = [G[3][i] * (1 - t) + G[2][i] * t for i in range(3)]
                n = r.uniform(-5, 5) + (3 if (y * 7 + x * 3) % 11 == 0 else 0) + tone
                self.set(x, y, [max(0, min(255, v + n)) for v in base])
        for x in range(x0, x1):
            self.set(x, y0, G[5]); self.set(x, y0 + 1, G[4])
            self.set(x, y1 - 1, G[0]); self.set(x, y1 - 2, G[1])
        for y in range(y0, y1):
            self.set(x0, y, G[5] if y < y1 - 2 else G[1]); self.set(x0 + 1, y, G[4] if y < y1 - 2 else G[1])
            self.set(x1 - 1, y, G[0]); self.set(x1 - 2, y, G[1])
        if engrave:  # thin engraved line inset from the edge
            ex0, ey0, ex1, ey1 = x0 + 4, y0 + 4, x1 - 5, y1 - 5
            for x in range(ex0, ex1 + 1):
                self.set(x, ey0, G[1]); self.set(x, ey1, G[4])
            for y in range(ey0, ey1 + 1):
                self.set(ex0, y, G[1]); self.set(ex1, y, G[4])
        if rivets:
            for cx, cy in ((x0 + 3, y0 + 3), (x1 - 5, y0 + 3), (x0 + 3, y1 - 5), (x1 - 5, y1 - 5)):
                self.rivet(cx, cy)

    def rivet(self, x, y):
        self.set(x, y, SILVER[2]); self.set(x + 1, y, SILVER[1]); self.set(x, y + 1, SILVER[1]); self.set(x + 1, y + 1, SILVER[0])
        self.set(x, y, SILVER[3])

    def gold_h(self, x0, x1, y, thick=3):
        """Horizontal gold trim with highlight on top and shadow below, sparse glints."""
        ramp = {2: [GOLD[4], GOLD[1]], 3: [GOLD[4], GOLD[2], GOLD[1]], 4: [GOLD[4], GOLD[3], GOLD[2], GOLD[0]]}[thick]
        for i, c in enumerate(ramp):
            for x in range(x0, x1):
                cc = c
                if i == 0 and (x * 5 + y) % 9 == 0:
                    cc = (255, 248, 210)
                self.set(x, y + i, cc)
        for x in range(x0, x1):
            self.set(x, y + thick, G[0])

    def gold_v(self, x, y0, y1, thick=2):
        for y in range(y0, y1):
            self.set(x, y, GOLD[4] if thick > 1 else GOLD[3])
            if thick > 1:
                self.set(x + 1, y, GOLD[2])
            if thick > 2:
                self.set(x + 2, y, GOLD[1])
        for y in range(y0, y1):
            self.set(x + thick, y, G[0])

    def gold_line(self, xa, ya, xb, yb, thick=3):
        steps = int(max(abs(xb - xa), abs(yb - ya))) + 1
        for s in range(steps):
            t = s / max(1, steps - 1)
            x, y = round(xa + (xb - xa) * t), round(ya + (yb - ya) * t)
            for k in range(thick):
                self.set(x, y + k, [GOLD[4], GOLD[3], GOLD[2], GOLD[1]][min(k, 3)])
            self.set(x, y + thick, G[0])

    def lames(self, x0, y0, x1, y1, n, gold_last=False):
        """Horizontal overlapping plates (articulated armor). Gold only on the last edge, if asked: gold is an accent."""
        h = (y1 - y0) / n
        for i in range(n):
            a, b = round(y0 + i * h), round(y0 + (i + 1) * h)
            self.plate(x0, a, x1, b, tone=-i * 2)
            for x in range(x0, x1):  # the next plate overlaps this one: a thin shadow line
                self.set(x, b - 1, G[0])
        if gold_last:
            self.gold_h(x0, x1, y1 - 3, 2)

    def rune(self, x, y, glyph, scale=2):
        """Engraved rune: dark socket, glowing cyan strokes with a white-hot core."""
        gw, gh = len(glyph[0]), len(glyph)
        cells = [(gx, gy) for gy in range(gh) for gx in range(gw) if glyph[gy][gx] == "#"]
        for gx, gy in cells:
            for dx in range(-1, scale + 1):
                for dy in range(-1, scale + 1):
                    self.set(x + gx * scale + dx, y + gy * scale + dy, CYAN[0])
        for gx, gy in cells:
            for dx in range(scale):
                for dy in range(scale):
                    core = dx == scale // 2 and dy == scale // 2
                    self.set(x + gx * scale + dx, y + gy * scale + dy, CYAN[3] if core else CYAN[2])

    def socket(self, cx, cy, r):
        """Gold diamond frame with a faceted cyan gem."""
        for y in range(cy - r - 2, cy + r + 3):
            for x in range(cx - r - 2, cx + r + 3):
                d = abs(x - cx) + abs(y - cy)
                if d <= r - 1:
                    up = (x - cx) + (y - cy) < 0
                    c = CYAN[3] if d < r * 0.35 else (CYAN[2] if up else CYAN[1])
                    if d < 2:
                        c = CYAN[4]
                    self.set(x, y, c)
                elif d <= r + 1:
                    self.set(x, y, GOLD[4] if y < cy else GOLD[2])
                elif d <= r + 2:
                    self.set(x, y, GOLD[0])

    def fill(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, c)


def R(x, y, w, h):
    """Vanilla texel rectangle -> 4x pixel rectangle."""
    return x * S, y * S, (x + w) * S, (y + h) * S


def helmet(c):
    # front 8,8 8x8
    x0, y0, x1, y1 = R(8, 8, 8, 8)
    c.plate(x0, y0, x1, y1)
    c.gold_line(x0 + 1, y0 + 9, x0 + 16, y0 + 16, 3)       # brow V
    c.gold_line(x0 + 16, y0 + 16, x1 - 2, y0 + 9, 3)
    c.fill(x0 + 3, y0 + 17, x1 - 3, y0 + 22, G[0])        # visor slit
    c.fill(x0 + 3, y0 + 17, x1 - 3, y0 + 18, (6, 6, 9))
    for x in range(x0 + 4, x1 - 4, 3):
        c.set(x, y0 + 20, CYAN[1])                         # faint glow in the eyes
    c.fill(x0 + 14, y0 + 10, x0 + 18, y1 - 2, SILVER[1])  # nose guard
    for y in range(y0 + 10, y1 - 2):
        c.set(x0 + 14, y, SILVER[3]); c.set(x0 + 17, y, SILVER[0])
    for x in range(x0 + 6, x1 - 6, 3):                     # breathing vents
        if not (x0 + 13 <= x <= x0 + 18):
            c.fill(x, y0 + 25, x + 1, y0 + 29, G[0])
    c.rune(x0 + 11, y0 + 1, ["#...#", "##.##", "#.#.#"], 2)
    # sides 0,8 and 16,8
    for sx in (0, 16):
        a0, b0, a1, b1 = R(sx, 8, 8, 8)
        c.plate(a0, b0, a1, b1, rivets=True)
        cx, cy = a0 + 16, b0 + 13
        for y in range(cy - 8, cy + 9):
            for x in range(cx - 8, cx + 9):
                d = math.hypot(x - cx, y - cy)
                if 6.2 <= d <= 8.2:
                    c.set(x, y, GOLD[4] if y < cy else GOLD[2])
                elif d < 6.2:
                    c.set(x, y, G[1])
        c.rune(cx - 5, cy - 7, RUNES[1], 2)
        c.gold_h(a0, a1, b1 - 6, 3)
    # back 24,8
    a0, b0, a1, b1 = R(24, 8, 8, 8)
    c.plate(a0, b0, a1, b0 + 12)
    c.lames(a0, b0 + 12, a1, b1, 3)
    c.rune(a0 + 11, b0 + 1, RUNES[4], 2)
    # top 8,0
    a0, b0, a1, b1 = R(8, 0, 8, 8)
    c.plate(a0, b0, a1, b1)
    c.fill(a0 + 14, b0, a0 + 18, b1, SILVER[1])
    for y in range(b0, b1):
        c.set(a0 + 14, y, SILVER[3]); c.set(a0 + 17, y, SILVER[0])
    c.fill(*R(16, 0, 8, 8), G[1])


def chest(c):
    # front 20,20 8x12
    x0, y0, x1, y1 = R(20, 20, 8, 12)
    mid = (x0 + x1) // 2
    c.plate(x0, y0, mid, y0 + 26, engrave=True)
    c.plate(mid, y0, x1, y0 + 26, engrave=True)
    c.lames(x0, y0 + 26, x1, y1, 4, gold_last=True)
    c.gold_line(x0 + 1, y0 + 2, mid, y0 + 12, 3)            # collar V
    c.gold_line(mid, y0 + 12, x1 - 2, y0 + 2, 3)
    c.socket(mid, y0 + 18, 7)
    c.rune(mid - 5, y0 + 30, RUNES[1], 2)
    # back 32,20
    a0, b0, a1, b1 = R(32, 20, 8, 12)
    c.plate(a0, b0, a1, b0 + 28, engrave=True)
    c.lames(a0, b0 + 28, a1, b1, 3)
    m = (a0 + a1) // 2
    c.gold_line(a0 + 1, b0 + 2, m, b0 + 14, 3)
    c.gold_line(m, b0 + 14, a1 - 2, b0 + 2, 3)
    c.fill(m - 2, b0 + 16, m + 2, b0 + 28, SILVER[1])
    c.rune(m - 5, b0 + 3, RUNES[0], 2)
    # sides 16,20 and 28,20
    for sx in (16, 28):
        a0, b0, a1, b1 = R(sx, 20, 4, 12)
        c.lames(a0, b0, a1, b1, 5)
    # top 20,16 and bottom 28,16
    a0, b0, a1, b1 = R(20, 16, 8, 4)
    c.plate(a0, b0, a1, b1)
    c.gold_h(a0, a1, b0 + 6, 2)
    c.fill(*R(28, 16, 8, 4), G[1])


def arms(c):
    """Arm faces of the chestplate (base 40,16). Outer face = first 4 texels."""
    faces = [(40, "outer"), (44, "front"), (48, "inner"), (52, "back")]
    for fx, kind in faces:
        a0, b0, a1, b1 = R(fx, 20, 4, 12)
        c.lames(a0, b0, a1, b0 + 16, 2)                        # rerebrace
        c.plate(a0, b0 + 16, a1, b0 + 24)                     # elbow: a silver couter, gold stays on the cuffs
        c.fill(a0 + 1, b0 + 18, a1 - 1, b0 + 21, SILVER[1])
        c.fill(a0 + 1, b0 + 18, a1 - 1, b0 + 19, SILVER[3])
        c.plate(a0, b0 + 24, a1, b1, engrave=kind != "inner")  # vambrace
        c.gold_h(a0, a1, b1 - 6, 3)
        if kind in ("outer", "front"):
            c.rune(a0 + 3, b0 + 27, RUNES[2] if kind == "outer" else RUNES[5], 2 if kind == "outer" else 1)
    a0, b0, a1, b1 = R(44, 16, 4, 4)
    c.plate(a0, b0, a1, b1)
    c.fill(*R(48, 16, 4, 4), G[1])


def boots(c):
    """Leg faces of the humanoid layer: only the boot (lower part) is painted."""
    for fx, kind in [(0, "outer"), (4, "front"), (8, "inner"), (12, "back")]:
        a0, b0, a1, b1 = R(fx, 20, 4, 12)
        top = b0 + 22
        c.plate(a0, top, a1, top + 12, engrave=kind == "front")  # greave
        c.gold_h(a0, a1, top, 4)
        c.lames(a0, top + 12, a1, b1, 2)                       # sabaton
        if kind == "front":
            c.fill(a0 + 3, b1 - 8, a1 - 3, b1 - 1, SILVER[1])
            c.fill(a0 + 3, b1 - 8, a1 - 3, b1 - 7, SILVER[3])
            c.rune(a0 + 3, top + 4, ["#.#", ".#.", "#.#"], 2)
        if kind == "outer":
            c.rune(a0 + 5, top + 4, ["#.#", ".#.", "#.#"], 2)
    c.fill(*R(8, 16, 4, 4), G[0])


def leggings(c):
    # waist (body 20,20 front, 32,20 back, sides): belt + faulds on the lower third only
    for fx, w in [(16, 4), (20, 8), (28, 4), (32, 8)]:
        a0, b0, a1, b1 = R(fx, 20, w, 12)
        c.gold_h(a0, a1, b0 + 30, 4)
        c.lames(a0, b0 + 35, a1, b1, 2, gold_last=True)
    m = R(20, 20, 8, 12)
    c.socket((m[0] + m[2]) // 2, m[1] + 32, 4)
    for fx, kind in [(0, "outer"), (4, "front"), (8, "inner"), (12, "back")]:
        a0, b0, a1, b1 = R(fx, 20, 4, 12)
        c.lames(a0, b0, a1, b0 + 22, 2)                        # cuisse
        c.plate(a0, b0 + 22, a1, b1)                           # greave (under the boots)
        c.gold_h(a0, a1, b0 + 22, 3)
        if kind == "outer":
            c.gold_v(a0 + 2, b0 + 2, b0 + 20, 2)
            c.rune(a0 + 6, b0 + 3, RUNES[3], 1)
        if kind == "front":
            c.rune(a0 + 5, b0 + 4, RUNES[1], 1)
    a0, b0, a1, b1 = R(4, 16, 4, 4)
    c.plate(a0, b0, a1, b1)


def atlas(rnd):
    c = Canvas(64, 64, rnd)
    c.plate(0, 0, 16, 16, engrave=True)                                   # plate
    for y in range(16):                                                    # gold
        for x in range(16, 32):
            t = y / 15
            base = [GOLD[3][i] * (1 - t) + GOLD[1][i] * t for i in range(3)]
            c.set(x, y, [min(255, v + rnd.uniform(-8, 8)) for v in base])
    for x in range(16, 32):
        c.set(x, 0, GOLD[4]); c.set(x, 15, GOLD[0])
    for y in range(16):
        c.set(16, y, GOLD[4]); c.set(31, y, GOLD[0])
    for k in range(4):                                                     # filigree dots
        c.set(19 + k * 3, 7, GOLD[4]); c.set(20 + k * 3, 8, GOLD[1])
    for y in range(16):                                                    # silver
        for x in range(32, 48):
            t = y / 15
            base = [SILVER[2][i] * (1 - t) + SILVER[0][i] * t for i in range(3)]
            c.set(x, y, [min(255, v + rnd.uniform(-6, 6)) for v in base])
    for x in range(32, 48):
        c.set(x, 0, SILVER[3]); c.set(x, 15, G[1])
    for y in range(16):
        c.set(32, y, SILVER[3]); c.set(47, y, G[1])
    c.fill(48, 0, 64, 16, G[1])                                            # rune plate
    for x in range(48, 64):
        c.set(x, 0, G[4]); c.set(x, 15, G[0])
    c.rune(51, 1, RUNES[1], 2)
    c.fill(0, 16, 16, 32, CYAN[1])                                         # gem
    c.socket(8, 24, 7)
    c.fill(16, 16, 32, 32, (0, 0, 0))                                      # feather
    for y in range(16, 32):
        for x in range(16, 32):
            u, v = x - 16, y - 16
            edge = u == 0 or v == 0
            vane = (u + v) % 3 == 0
            col = SILVER[3] if edge else SILVER[2] if vane else SILVER[1]
            if v == 7 or v == 8:
                col = GOLD[3]  # quill
            c.set(x, y, col)
    c.fill(32, 16, 48, 32, G[1])                                           # dark
    c.fill(48, 16, 64, 32, GOLD[2])                                        # gold rune
    for x in range(48, 64):
        c.set(x, 16, GOLD[4]); c.set(x, 31, GOLD[0])
    c.rune(51, 17, RUNES[0], 2)
    return c.img


def retone_icon(name):
    img = Image.open(PACK_ICON % name).convert("RGBA")
    p = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = p[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            gold = r > b + 45 and g > b + 15
            cyan = b > r + 40 and g > r + 40
            if gold or cyan or l < 0.12:
                continue
            l2 = l * 0.62 if l < 0.78 else l * 0.92
            r2, g2, b2 = colorsys.hls_to_rgb(0.65, l2, min(0.12, s + 0.05))
            p[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    img.save(ASSETS + f"item/uru_{name}.png")


if __name__ == "__main__":
    rnd = random.Random(2611)
    hum = Canvas(64 * S, 32 * S, rnd)
    helmet(hum)
    chest(hum)
    arms(hum)
    boots(hum)
    hum.img.save(ASSETS + "entity/equipment/humanoid/uru.png")
    leg = Canvas(64 * S, 32 * S, rnd)
    leggings(leg)
    leg.img.save(ASSETS + "entity/equipment/humanoid_leggings/uru.png")
    atlas(rnd).save(ASSETS + "item/uru_detail.png")
    for piece in ("helmet", "chestplate", "leggings", "boots"):
        retone_icon(piece)
