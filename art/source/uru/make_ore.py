"""Uru ore overlay (drawn over vanilla stone/deepslate), its glowing glints, raw uru and the uru ingot.

Run from the repository root: python3 art/source/uru/make_ore.py
"""
from PIL import Image

T = "src/main/resources/assets/infinity_gauntlet/textures/"
OUT, BODY, LIGHT, HI, WHITE = (22, 25, 36), (78, 88, 108), (140, 152, 174), (200, 212, 230), (244, 250, 255)
GLINT, GLINT_CORE = (80, 236, 255), (220, 255, 255)

# Ore: shining metal veins. Letters: o outline, b body, l light, h highlight, g cyan glint.
ORE = [
    "................",
    "..oo........ooo.",
    ".obho......obhlo",
    ".oblbo.....oblbo",
    "..obo.......ooo.",
    "...o....oo......",
    "........ohbo....",
    ".......obhlgo...",
    "....oo.oblbbo...",
    "...ohbo.oooo....",
    "...oblgo......oo",
    "....ooo......ohb",
    ".oo.........oblo",
    "obho...oo....oo.",
    "oblbo.obgo......",
    ".ooo...oo.......",
]
SPARK = {(3, 2), (13, 2), (9, 6), (10, 7), (5, 9), (6, 10), (2, 13), (8, 14), (14, 11)}

INGOT = [
    "................",
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...ohhhhhhhhlo..",
    "..ohlllllllllbo.",
    ".ohlllgggllllbbo",
    ".olbbbbbbbbbbbbo",
    ".obbbbbbbbbbbbbo",
    "..obbbbbbbbbbbo.",
    "...oooooooooooo.",
    "................",
    "................",
    "................",
    "................",
]

RAW = [
    "................",
    "................",
    ".....ooo........",
    "....ohlbo.oo....",
    "...ohlbbboblo...",
    "..ohllbbbbbbbo..",
    "..olbbbgbbbbbo..",
    ".ohlbbbbbbbbbbo.",
    ".olbbbbbbbbgbbo.",
    ".obbbbbbbbbbbo..",
    "..obbbbbbbbbbo..",
    "..oobbbbbbbboo..",
    "....oobbbbbo....",
    "......ooooo.....",
    "................",
    "................",
]

COLORS = {"o": OUT, "b": BODY, "l": LIGHT, "h": HI, "g": GLINT}


def paint(rows, glow_only=False, sparks=()):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == ".":
                continue
            if glow_only:
                if c == "g" or (x, y) in sparks:
                    p[x, y] = GLINT_CORE + (255,) if (x, y) in sparks else GLINT + (255,)
                continue
            p[x, y] = COLORS[c] + (255,)
    return img


if __name__ == "__main__":
    paint(ORE).save(T + "block/uru_ore_overlay.png")
    paint(ORE, glow_only=True, sparks=SPARK).save(T + "block/uru_ore_glint.png")
    ingot = paint(INGOT)
    ingot.putpixel((5, 5), WHITE + (255,)); ingot.putpixel((6, 5), WHITE + (255,))
    ingot.save(T + "item/uru_ingot.png")
    raw = paint(RAW)
    raw.putpixel((6, 3), WHITE + (255,))
    raw.save(T + "item/raw_uru.png")
