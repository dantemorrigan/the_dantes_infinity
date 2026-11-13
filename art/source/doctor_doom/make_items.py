"""Doom's items: pack icons become item textures, plus the worn armor textures, item models and the logo texture.

Run from the repository root: python3 art/source/doctor_doom/make_items.py
"""
import json
import os

from PIL import Image

ASSETS = "src/main/resources/assets/infinity_gauntlet/"
ICONS = "art/doctor_doom/doombots/icons/"
ITEMS = ["doom_helmet", "doom_chestplate", "doom_leggings", "doom_boots", "doom_gauntlet", "latveria_key", "castle_seal",
         "doombot_controller", "arc_reactor", "doom_circuit", "mechanical_heart", "doom_grimoire", "time_platform",
         "jade_shard", "repair_tool", "latveria_banner"]

STEEL, STEEL_HI, STEEL_LO = (92, 98, 106), (138, 146, 154), (48, 52, 60)
GREEN, GREEN_HI, GREEN_LO = (30, 84, 48), (48, 116, 66), (16, 46, 28)
GOLD, GOLD_LO = (190, 152, 70), (112, 84, 36)


def noise(x, y, s):
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFF) / 255.0


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c) + (255,)


def rect(img, x, y, w, h, color, seed=1, bevel=True):
    px = img.load()
    for j in range(h):
        for i in range(w):
            f = 0.92 + 0.16 * noise(x + i, y + j, seed)
            if bevel and (i == 0 or j == 0): f *= 1.18
            if bevel and (i == w - 1 or j == h - 1): f *= 0.78
            px[x + i, y + j] = shade(color, f)


def dot(img, x, y, color):
    img.putpixel((x, y), color + (255,))


def armor():
    """Vanilla 64x32 armor sheets: layer 1 = helmet, chestplate, boots; layer 2 = leggings."""
    a = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # helmet: a green hood around a steel mask
    for x, y, w, h in ((8, 0, 8, 8), (0, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)):
        rect(a, x, y, w, h, GREEN, 3, False)
    rect(a, 16, 0, 8, 8, GREEN_LO, 3, False)
    rect(a, 8, 8, 8, 8, GREEN, 3, False)
    rect(a, 9, 9, 6, 7, STEEL, 5)
    for x in (10, 13):
        dot(a, x, 11, (20, 22, 24)); dot(a, x, 12, (90, 255, 120))
    dot(a, 11, 12, (20, 22, 24)) if False else None
    for x in (10, 12, 14):
        dot(a, x, 14, (26, 28, 30))
    for x in range(8, 16): dot(a, x, 8, GREEN_LO)
    # hat layer: the hood's lip
    for x in range(40, 48): dot(a, x, 8, GREEN_HI)
    for y in range(8, 16): dot(a, 40, y, GREEN_HI); dot(a, 47, y, GREEN_HI)
    # chestplate: quilted tunic, clasps and chain, steel arms
    rect(a, 20, 16, 8, 4, GREEN, 7, False)
    rect(a, 16, 20, 4, 12, GREEN_LO, 7, False)
    rect(a, 28, 20, 4, 12, GREEN_LO, 7, False)
    rect(a, 20, 20, 8, 12, GREEN, 7, False)
    rect(a, 32, 20, 8, 12, GREEN_LO, 9, False)
    for j in range(22, 31, 3):
        for i in range(20, 28): dot(a, i, j, GREEN_LO)
    for i in (20, 23, 26):
        for j in range(20, 32): dot(a, i, j, GREEN_LO)
    for x in (21, 26): dot(a, x, 21, GOLD); dot(a, x, 22, GOLD_LO)
    for x in range(22, 26): dot(a, x, 22 + (1 if x in (23, 24) else 0), GOLD)
    for x in range(20, 28): dot(a, x, 30, (70, 48, 32))
    dot(a, 23, 30, GOLD); dot(a, 24, 30, GOLD)
    for x, y, w, h in ((44, 16, 4, 4), (40, 20, 4, 12), (44, 20, 4, 12), (48, 20, 4, 12), (52, 20, 4, 12)):
        rect(a, x, y, w, h, STEEL, 11)
    for x in range(40, 56):
        dot(a, x, 23, STEEL_LO); dot(a, x, 27, STEEL_LO); dot(a, x, 20, STEEL_HI)
    # boots: the lower leg only
    for x, w in ((0, 4), (4, 4), (8, 4), (12, 4)):
        rect(a, x, 26, w, 6, STEEL_LO if x in (0, 8) else STEEL, 13)
    for x in range(0, 16): dot(a, x, 26, GOLD)
    b = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # leggings: belt and a skirt of the tunic over steel legs
    for x, y, w, h in ((16, 27, 4, 5), (20, 27, 8, 5), (28, 27, 4, 5), (32, 27, 8, 5)):
        rect(b, x, y, w, h, GREEN, 15, False)
    for x in range(16, 40): dot(b, x, 27, (70, 48, 32))
    for x, w in ((0, 4), (4, 4), (8, 4), (12, 4)):
        rect(b, x, 20, w, 9, STEEL, 17)
        rect(b, x, 20, w, 3, GREEN, 15, False)
    rect(b, 4, 16, 4, 4, STEEL, 17)
    for x in range(0, 16): dot(b, x, 25, STEEL_LO)
    for sub, img in (("humanoid", a), ("humanoid_leggings", b)):
        path = ASSETS + "textures/entity/equipment/%s/doom.png" % sub
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
    with open(ASSETS + "equipment/doom.json", "w") as f:
        json.dump({"layers": {"humanoid": [{"texture": "infinity_gauntlet:doom"}],
                              "humanoid_leggings": [{"texture": "infinity_gauntlet:doom"}]}}, f, indent=2)
        f.write("\n")


def items():
    for name in ITEMS:
        Image.open(ICONS + name + ".png").convert("RGBA").save(ASSETS + "textures/item/%s.png" % name)
        with open(ASSETS + "items/%s.json" % name, "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "infinity_gauntlet:item/" + name}}, f, indent=2)
            f.write("\n")
        with open(ASSETS + "models/item/%s.json" % name, "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "infinity_gauntlet:item/" + name}}, f, indent=2)
            f.write("\n")


def logo():
    img = Image.open("art/doctor_doom/logo/the_fury_of_doom.webp").convert("RGBA")
    img = img.resize((1024, round(1024 * img.height / img.width)), Image.LANCZOS)
    sheet = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
    sheet.alpha_composite(img, (0, (512 - img.height) // 2))
    os.makedirs(ASSETS + "textures/gui/doctor_doom", exist_ok=True)
    sheet.save(ASSETS + "textures/gui/doctor_doom/logo.png")


if __name__ == "__main__":
    armor()
    items()
    logo()
    print("items:", len(ITEMS))
