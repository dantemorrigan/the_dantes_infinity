"""Generates Latverian player-skin NPC textures (64x64 standard Minecraft player layout with 2nd-layer clothes),
the Latverian Citizen Cloak item & worn equipment textures, and the Generator Disruptor item texture & models.
"""
import json
import math
import os
import random
from PIL import Image

ASSETS = "src/main/resources/assets/infinity_gauntlet/"


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def weave(px, x, y, c, rng, amp=0.07):
    k = 1.0 + rng.choice([0.0, 0.0, amp, -amp, -amp * 1.4])
    px[x, y] = shade(c, k)


def rect(px, x0, y0, w, h, c, rng=None):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            if rng is not None:
                weave(px, x, y, c, rng)
            else:
                px[x, y] = c if len(c) == 4 else c + (255,)


def box(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def fill_box(px, u, v, w, h, d, c, rng=None):
    b = box(u, v, w, h, d)
    for f in b.values():
        rect(px, *f, c, rng)
    return b


def draw_base_human(px, rng, skin, hair, eye, beard=None, female=False):
    # Head (0, 0, 8, 8, 8)
    hb = fill_box(px, 0, 0, 8, 8, 8, skin, rng)
    fx, fy, fw, fh = hb["front"]
    # Hair on top, back, sides, and forehead
    rect(px, *hb["top"], hair, rng)
    rect(px, *hb["back"], hair, rng)
    for side in ("left", "right"):
        sx, sy, sw, sh = hb[side]
        rect(px, sx, sy, sw, 3 if not female else 6, hair, rng)
        for y in range(sy + 3, sy + (7 if female else 5)):
            px[sx if side == "left" else sx + sw - 1, y] = shade(hair, 0.9)
    # Front forehead hair
    rect(px, fx, fy, fw, 2, hair, rng)
    if female:
        px[fx, fy + 2] = shade(hair, 0.95)
        px[fx + fw - 1, fy + 2] = shade(hair, 0.95)
        px[fx, fy + 3] = shade(hair, 0.85)
        px[fx + fw - 1, fy + 3] = shade(hair, 0.85)
    # Eyebrows
    brow = shade(hair, 0.75)
    for x in (fx + 1, fx + 2, fx + 5, fx + 6):
        px[x, fy + 3] = brow
    # Eyes
    px[fx + 1, fy + 4] = (242, 242, 245, 255)
    px[fx + 2, fy + 4] = eye + (255,)
    px[fx + 5, fy + 4] = eye + (255,)
    px[fx + 6, fy + 4] = (242, 242, 245, 255)
    # Nose & mouth
    px[fx + 3, fy + 5] = shade(skin, 0.88)
    px[fx + 4, fy + 5] = shade(skin, 0.84)
    for x in range(fx + 2, fx + 6):
        px[x, fy + 6] = shade((165, 95, 85) if female else skin, 0.82 if female else 0.76)

    if beard is not None:
        # Moustache & beard
        for x in range(fx + 1, fx + 7):
            px[x, fy + 6] = shade(beard, 0.92)
        for x in range(fx, fx + 8):
            px[x, fy + 7] = shade(beard, 0.85)
        for side in ("left", "right"):
            sx, sy, sw, sh = hb[side]
            rect(px, sx, sy + 6, sw, 2, beard, rng)


def draw_latveria_coat(px, rng, coat, coat_dark, trim_red, gold, trouser, boot):
    # Torso base (16, 16, 8, 12, 4)
    tb = fill_box(px, 16, 16, 8, 12, 4, coat, rng)
    fx, fy, fw, fh = tb["front"]
    # Embroidered Carpathian collar
    for x in range(fx, fx + fw):
        px[x, fy] = (trim_red if x % 2 == 0 else (24, 24, 28)) + (255,)
        px[x, fy + 1] = ((24, 24, 28) if x % 2 == 0 else trim_red) + (255,)
    # Gold buttons down chest
    for y in (fy + 3, fy + 5, fy + 7):
        px[fx + 3, y] = gold + (255,)
        px[fx + 4, y] = shade(gold, 0.8)
    # Belt
    belt_y = fy + fh - 3
    for f in ("front", "left", "right", "back"):
        sx, sy, sw, sh = tb[f]
        for x in range(sx, sx + sw):
            px[x, belt_y] = (28, 24, 22, 255)
            px[x, belt_y + 1] = (20, 18, 16, 255)
    px[fx + 3, belt_y] = gold + (255,)
    px[fx + 4, belt_y] = gold + (255,)
    px[fx + 3, belt_y + 1] = shade(gold, 0.85)
    px[fx + 4, belt_y + 1] = shade(gold, 0.85)

    # Jacket overlay (16, 32, 8, 12, 4)
    jb = box(16, 32, 8, 12, 4)
    for side in ("left", "right", "back"):
        rect(px, *jb[side], coat_dark, rng)
    jfx, jfy, jfw, jfh = jb["front"]
    for y in range(jfy + 2, jfy + jfh):
        for x in (jfx, jfx + 1, jfx + jfw - 2, jfx + jfw - 1):
            weave(px, x, y, coat_dark, rng)
        px[jfx + 2, y] = trim_red + (255,)
        px[jfx + jfw - 3, y] = trim_red + (255,)

    # Arms: Right arm (40, 16, 4, 12, 4), Left arm (32, 48, 4, 12, 4)
    for u, v, ou, ov in ((40, 16, 40, 32), (32, 48, 48, 48)):
        ab = fill_box(px, u, v, 4, 12, 4, coat, rng)
        # Cuffs & hands
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = ab[name]
            for x in range(sx, sx + sw):
                px[x, sy + sh - 4] = trim_red + (255,)
                px[x, sy + sh - 3] = (24, 24, 28, 255)
        # Sleeve overlay cuff
        ob = box(ou, ov, 4, 12, 4)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = ob[name]
            rect(px, sx, sy, sw, 7, coat_dark, rng)
            for x in range(sx, sx + sw):
                px[x, sy + 7] = gold + (255,)

    # Legs: Right leg (0, 16, 4, 12, 4), Left leg (16, 48, 4, 12, 4)
    for u, v, ou, ov in ((0, 16, 0, 32), (16, 48, 0, 48)):
        lb = fill_box(px, u, v, 4, 12, 4, trouser, rng)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = lb[name]
            rect(px, sx, sy + sh - 5, sw, 5, boot, rng)
            for x in range(sx, sx + sw):
                px[x, sy + sh - 5] = shade(boot, 1.3)
        rect(px, *lb["bottom"], boot)
        # Coat hem on upper leg overlay
        pb = box(ou, ov, 4, 12, 4)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = pb[name]
            rect(px, sx, sy, sw, 4, coat_dark, rng)
            for x in range(sx, sx + sw):
                px[x, sy + 4] = (trim_red if x % 2 == 0 else gold) + (255,)


def make_mechanic():
    rng = random.Random(101)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    skin = (218, 174, 142)
    hair = (150, 154, 160)
    beard = (135, 140, 146)
    draw_base_human(px, rng, skin, hair, (56, 112, 138), beard=beard)
    draw_latveria_coat(
        px, rng,
        coat=(36, 76, 50),
        coat_dark=(24, 52, 36),
        trim_red=(140, 36, 32),
        gold=(198, 154, 56),
        trouser=(34, 36, 42),
        boot=(42, 30, 22),
    )
    # Hands in dark leather work gloves
    for u, v in ((40, 16), (32, 48)):
        ab = box(u, v, 4, 12, 4)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = ab[name]
            rect(px, sx, sy + sh - 3, sw, 3, (54, 38, 26), rng)
        rect(px, *ab["bottom"], (46, 32, 22))
    # Hat layer (32, 0, 8, 8, 8): Brass mechanic goggles & leather strap on forehead
    hb2 = box(32, 0, 8, 8, 8)
    for side in ("front", "left", "right", "back"):
        sx, sy, sw, sh = hb2[side]
        for x in range(sx, sx + sw):
            px[x, sy + 1] = (48, 34, 24, 255)
            px[x, sy + 2] = (58, 42, 30, 255)
    fx, fy, fw, fh = hb2["front"]
    # Two brass goggle lenses with cyan-tinted glass
    for lx in (fx + 1, fx + 4):
        for dx in range(3):
            for dy in range(3):
                px[lx + dx, fy + dy] = (198, 154, 56, 255)
        px[lx + 1, fy + 1] = (110, 215, 235, 255)
    # Heavy leather mechanic apron & tool pockets on jacket front (16, 32)
    jb = box(16, 32, 8, 12, 4)
    jfx, jfy, jfw, jfh = jb["front"]
    rect(px, jfx + 1, jfy + 2, 6, 9, (78, 52, 34), rng)
    # Apron straps
    px[jfx + 1, jfy] = (64, 42, 28, 255)
    px[jfx + 1, jfy + 1] = (64, 42, 28, 255)
    px[jfx + 6, jfy] = (64, 42, 28, 255)
    px[jfx + 6, jfy + 1] = (64, 42, 28, 255)
    # Brass wrench & glowing disruptor cell in chest pocket
    px[jfx + 2, jfy + 5] = (180, 186, 194, 255)
    px[jfx + 2, jfy + 6] = (150, 156, 164, 255)
    px[jfx + 5, jfy + 5] = (92, 242, 198, 255)
    px[jfx + 5, jfy + 6] = (198, 154, 56, 255)
    return im


def make_tavern_keeper():
    rng = random.Random(202)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    skin = (224, 178, 144)
    hair = (58, 40, 30)
    draw_base_human(px, rng, skin, hair, (68, 48, 32), beard=(48, 32, 24))
    draw_latveria_coat(
        px, rng,
        coat=(116, 34, 38),       # Warm burgundy tavern waistcoat
        coat_dark=(34, 74, 48),  # Latverian green outer vest
        trim_red=(170, 48, 42),
        gold=(212, 170, 62),
        trouser=(38, 34, 32),
        boot=(46, 32, 22),
    )
    # Rolled-up cream linen shirt sleeves with skin forearms
    for u, v in ((40, 16), (32, 48)):
        ab = box(u, v, 4, 12, 4)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = ab[name]
            rect(px, sx, sy, sw, 7, (226, 216, 196), rng)
            rect(px, sx, sy + 7, sw, 5, skin, rng)
        rect(px, *ab["bottom"], shade(skin, 0.9))
    # Hat layer: Low Latverian fur cap with green band
    hb2 = box(32, 0, 8, 8, 8)
    rect(px, *hb2["top"], (48, 38, 32), rng)
    for side in ("front", "left", "right", "back"):
        sx, sy, sw, sh = hb2[side]
        rect(px, sx, sy, sw, 2, (48, 38, 32), rng)
        for x in range(sx, sx + sw):
            px[x, sy + 2] = (34, 78, 48, 255)
    # Tavern apron on jacket front (cream/linen apron with golden wheat emblem)
    jb = box(16, 32, 8, 12, 4)
    jfx, jfy, jfw, jfh = jb["front"]
    rect(px, jfx + 1, jfy + 5, 6, 7, (222, 210, 186), rng)
    px[jfx + 3, jfy + 7] = (218, 178, 58, 255)
    px[jfx + 4, jfy + 8] = (218, 178, 58, 255)
    return im


def make_citizen(idx):
    rng = random.Random(300 + idx)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()
    female = idx % 2 == 1
    skins = [(226, 184, 152), (232, 192, 162), (214, 170, 138), (228, 188, 156)]
    hairs = [(64, 44, 30), (96, 56, 32), (44, 36, 32), (120, 84, 46)]
    coats = [(38, 84, 52), (44, 76, 58), (32, 70, 46), (52, 82, 54)]
    coats_d = [(26, 60, 38), (28, 52, 40), (22, 48, 32), (34, 58, 38)]
    eyes = [(54, 98, 68), (62, 88, 124), (76, 54, 38), (58, 104, 82)]
    skin = skins[idx % 4]
    hair = hairs[idx % 4]
    draw_base_human(px, rng, skin, hair, eyes[idx % 4], beard=(hair if idx == 2 else None), female=female)
    draw_latveria_coat(
        px, rng,
        coat=coats[idx % 4],
        coat_dark=coats_d[idx % 4],
        trim_red=(150, 32, 34),
        gold=(210, 168, 58),
        trouser=(34, 34, 40),
        boot=(42, 30, 22),
    )
    # Hat layer: Fur kalpak or Carpathian hood/scarf
    hb2 = box(32, 0, 8, 8, 8)
    hat_c = (136, 32, 36) if female else (48, 40, 36)
    rect(px, *hb2["top"], hat_c, rng)
    for side in ("front", "left", "right", "back"):
        sx, sy, sw, sh = hb2[side]
        rect(px, sx, sy, sw, 2, hat_c, rng)
        for x in range(sx, sx + sw):
            px[x, sy + 2] = coats[idx % 4] + (255,)
    fx, fy, fw, fh = hb2["front"]
    px[fx + 3, fy + 2] = (212, 170, 58, 255)
    px[fx + 4, fy + 2] = (212, 170, 58, 255)
    for u, v in ((40, 16), (32, 48)):
        ab = box(u, v, 4, 12, 4)
        for name in ("front", "left", "right", "back"):
            sx, sy, sw, sh = ab[name]
            rect(px, sx, sy + sh - 3, sw, 3, skin, rng)
        rect(px, *ab["bottom"], shade(skin, 0.9))
    return im


def make_cloak_equipment():
    """Creates the 64x32 humanoid & humanoid_leggings sheets for the Latverian Citizen Cloak."""
    rng = random.Random(505)
    GREEN = (36, 82, 50)
    GREEN_D = (22, 54, 34)
    RED = (152, 32, 34)
    GOLD = (214, 172, 58)
    BLACK = (24, 24, 28)

    # Layer 1 (humanoid): Hooded mantle over shoulders, chest, back cape, and sleeves
    a = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = a.load()
    # Chest/body (16, 16, 8, 12, 4)
    tb = box(16, 16, 8, 12, 4)
    for f in tb.values():
        rect(px, *f, GREEN_D, rng)
    fx, fy, fw, fh = tb["front"]
    # Red-and-gold Carpathian clasp & trim down the cloak opening
    for x in range(fx, fx + fw):
        px[x, fy] = (RED if x % 2 == 0 else GOLD) + (255,)
        px[x, fy + 1] = GREEN + (255,)
        px[x, fy + fh - 1] = (RED if x % 2 == 0 else BLACK) + (255,)
    for y in range(fy + 1, fy + fh - 1):
        px[fx + 3, y] = BLACK + (255,)
        px[fx + 4, y] = BLACK + (255,)
        px[fx + 2, y] = (RED if y % 2 == 0 else GREEN) + (255,)
        px[fx + 5, y] = (RED if y % 2 == 0 else GREEN) + (255,)
    # Brass cloak clasp at throat
    px[fx + 2, fy + 2] = GOLD + (255,)
    px[fx + 3, fy + 2] = GOLD + (255,)
    px[fx + 4, fy + 2] = GOLD + (255,)
    px[fx + 5, fy + 2] = GOLD + (255,)

    # Back cape pattern
    bx, by, bw, bh = tb["back"]
    for x in range(bx + 1, bx + bw - 1):
        px[x, by + bh - 2] = GOLD + (255,)
        px[x, by + bh - 3] = RED + (255,)

    # Sleeves (40, 16, 4, 12, 4)
    ab = box(40, 16, 4, 12, 4)
    rect(px, *ab["top"], GREEN, rng)
    for name in ("front", "left", "right", "back"):
        sx, sy, sw, sh = ab[name]
        rect(px, sx, sy, sw, 9, GREEN_D, rng)
        for x in range(sx, sx + sw):
            px[x, sy] = GREEN + (255,)
            px[x, sy + 8] = (RED if x % 2 == 0 else GOLD) + (255,)

    # Layer 2 (leggings): Long cloak hem over upper legs
    b = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    pxb = b.load()
    lb = box(0, 16, 4, 12, 4)
    for name in ("front", "left", "right", "back"):
        sx, sy, sw, sh = lb[name]
        rect(pxb, sx, sy, sw, 6, GREEN_D, rng)
        for x in range(sx, sx + sw):
            pxb[x, sy + 5] = (RED if x % 2 == 0 else GOLD) + (255,)
            pxb[x, sy + 6] = BLACK + (255,)

    return a, b


def make_cloak_item_icon():
    """32x32 icon for the Latverian Citizen Cloak."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(707)
    GREEN = (38, 88, 54)
    GREEN_D = (22, 54, 34)
    GREEN_HI = (56, 118, 74)
    RED = (168, 36, 38)
    GOLD = (224, 180, 64)
    OUTLINE = (14, 24, 18, 255)

    for y in range(3, 29):
        half_w = 4 if y < 9 else int(5 + (y - 9) * 0.48)
        for dx in range(-half_w, half_w + 1):
            x = 16 + dx
            if not (0 <= x < 32):
                continue
            edge = abs(dx) == half_w or y == 3 or y == 28
            if edge:
                px[x, y] = OUTLINE
            elif y < 10:
                # Hood
                inner = abs(dx) <= 2 and 5 <= y <= 8
                px[x, y] = (18, 22, 20, 255) if inner else shade(GREEN if dx < 0 else GREEN_D, 0.95 + 0.08 * rng.random())
            else:
                # Cloak folds
                fold = math.sin(dx * 0.9 + y * 0.15)
                base = GREEN_HI if fold > 0.4 else (GREEN if fold > -0.2 else GREEN_D)
                if y == 26:
                    px[x, y] = (RED if x % 2 == 0 else GOLD) + (255,)
                elif y == 27:
                    px[x, y] = RED + (255,)
                elif abs(dx) <= 1 and y > 11:
                    px[x, y] = (RED if y % 2 == 0 else (24, 24, 28)) + (255,)
                else:
                    weave(px, x, y, base, rng)
    # Golden clasp at collar (y=10..11)
    for x in range(13, 20):
        px[x, 10] = GOLD + (255,)
        if x in (14, 18):
            px[x, 11] = GOLD + (255,)
    px[16, 10] = RED + (255,)
    return im


def make_disruptor_item_icon():
    """32x32 icon for the Old Mechanic's Generator Disruptor."""
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    rng = random.Random(808)
    STEEL = (96, 104, 114)
    STEEL_D = (48, 54, 62)
    COPPER = (196, 108, 62)
    GOLD = (220, 176, 62)
    CYAN = (88, 245, 210)
    OUTLINE = (18, 22, 26, 255)

    # Central cylindrical casing (x=10..21, y=7..24)
    for y in range(6, 26):
        for x in range(9, 23):
            if x in (9, 22) or y in (6, 25):
                px[x, y] = OUTLINE
            elif y in (7, 8, 23, 24):
                weave(px, x, y, STEEL_D, rng)
            elif y in (9, 10, 21, 22):
                weave(px, x, y, COPPER, rng)
            else:
                dist = math.hypot(x - 15.5, y - 15.5)
                if dist < 3.6:
                    px[x, y] = (210, 255, 245, 255) if dist < 1.8 else CYAN + (255,)
                elif dist < 4.8:
                    px[x, y] = GOLD + (255,)
                else:
                    weave(px, x, y, STEEL, rng)
    # Magnetic clamps on left & right sides + top antenna
    for side_x in (5, 6, 7, 8, 23, 24, 25, 26):
        for y in (10, 11, 19, 20):
            px[side_x, y] = COPPER + (255,) if y in (10, 19) else STEEL_D + (255,)
    for y in range(2, 7):
        px[13, y] = STEEL_D + (255,)
        px[18, y] = STEEL_D + (255,)
    px[13, 2] = CYAN + (255,)
    px[18, 2] = CYAN + (255,)
    return im


def main():
    npc_dir = ASSETS + "textures/entity/latveria/"
    os.makedirs(npc_dir, exist_ok=True)
    make_mechanic().save(npc_dir + "mechanic.png")
    make_tavern_keeper().save(npc_dir + "tavern_keeper.png")
    for i in range(4):
        make_citizen(i).save(npc_dir + f"citizen_{i + 1}.png")

    # Cloak equipment
    layer1, layer2 = make_cloak_equipment()
    os.makedirs(ASSETS + "textures/entity/equipment/humanoid", exist_ok=True)
    os.makedirs(ASSETS + "textures/entity/equipment/humanoid_leggings", exist_ok=True)
    layer1.save(ASSETS + "textures/entity/equipment/humanoid/latveria_cloak.png")
    layer2.save(ASSETS + "textures/entity/equipment/humanoid_leggings/latveria_cloak.png")
    with open(ASSETS + "equipment/latveria_cloak.json", "w") as f:
        json.dump(
            {
                "layers": {
                    "humanoid": [{"texture": "infinity_gauntlet:latveria_cloak"}],
                    "humanoid_leggings": [{"texture": "infinity_gauntlet:latveria_cloak"}],
                }
            },
            f,
            indent=2,
        )
        f.write("\n")

    # Item icons & models
    make_cloak_item_icon().save(ASSETS + "textures/item/latveria_cloak.png")
    make_disruptor_item_icon().save(ASSETS + "textures/item/generator_disruptor.png")
    for name in ("latveria_cloak", "generator_disruptor"):
        with open(ASSETS + f"items/{name}.json", "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"infinity_gauntlet:item/{name}"}}, f, indent=2)
            f.write("\n")
        with open(ASSETS + f"models/item/{name}.json", "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"infinity_gauntlet:item/{name}"}}, f, indent=2)
            f.write("\n")

    print("Generated Latveria NPC skins, cloak equipment, and quest items.")


if __name__ == "__main__":
    main()
