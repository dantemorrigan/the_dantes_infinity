"""3D details of the Armor of Uru, drawn on top of the vanilla armor by UruDetailLayer.

Boxes are written in MODEL PART coordinates (pixels, Y down, front = -Z), exactly like the player model, and
converted to item-model coordinates for a model drawn after part.translateAndRotate() + a 180 deg turn around X:
item (x, y, z) = (X + 8, 8 - Y, 8 - Z).

Run from the repository root: python3 art/source/uru/make_models.py
"""
import json

ROOT = "src/main/resources/assets/infinity_gauntlet/"
UV = {"graphite": [0, 0, 4, 4], "gold": [4, 0, 8, 4], "silver": [8, 0, 12, 4], "rune": [12, 0, 16, 4],
      "gem": [0, 4, 4, 8], "dark": [4, 4, 8, 8], "wing": [8, 4, 12, 8]}
GLOW = {"rune", "gem"}


def box(x0, y0, z0, x1, y1, z1, mat, rot=None):
    return (min(x0, x1), min(y0, y1), min(z0, z1), max(x0, x1), max(y0, y1), max(z0, z1), mat, rot)


def mirror(b):
    x0, y0, z0, x1, y1, z1, mat, rot = b
    if rot:
        rot = dict(rot, angle=-rot["angle"], origin=(-rot["origin"][0],) + tuple(rot["origin"][1:]))
    return box(-x1, y0, z0, -x0, y1, z1, mat, rot)


def element(b):
    x0, y0, z0, x1, y1, z1, mat, rot = b
    frm = [round(x0 + 8, 3), round(8 - y1, 3), round(8 - z1, 3)]
    to = [round(x1 + 8, 3), round(8 - y0, 3), round(8 - z0, 3)]
    for v in frm + to:
        assert -16 <= v <= 32, b
    e = {"from": frm, "to": to,
         "faces": {f: {"uv": UV[mat], "texture": "#d"} for f in ("north", "south", "east", "west", "up", "down")}}
    if mat in GLOW:
        e["light_emission"] = 15
        e["shade"] = False
    if rot:
        ox, oy, oz = rot["origin"]
        e["rotation"] = {"angle": rot["angle"], "axis": rot["axis"], "origin": [ox + 8, 8 - oy, 8 - oz]}
    return e


def wing_right():
    """Stepped fan going up, out and back, like the winged helms of the concept."""
    return [box(-5.8, -6.8, -1.5, -5.0, -3.6, 2.0, "gold"),
            box(-7.0, -6.6, -1.0, -5.4, -5.2, 3.6, "wing"),
            box(-8.2, -8.2, -0.4, -6.0, -6.6, 4.4, "wing"),
            box(-9.2, -9.9, 0.4, -6.8, -8.2, 5.2, "wing"),
            box(-9.8, -11.4, 1.4, -7.8, -9.9, 5.8, "wing")]


HELMET = [box(-1.5, -10.0, -5.6, 1.5, -6.5, -5.0, "gold"),
          box(-0.8, -9.5, -5.8, 0.8, -7.0, -5.6, "rune"),
          box(-0.75, -9.7, -5.0, 0.75, -9.0, 4.6, "silver")] + wing_right() + [mirror(b) for b in wing_right()]

DIAMOND = {"angle": 45, "axis": "z", "origin": (0, 4.5, -3.4)}
TABARD_FRONT = [box(-2.0, 11.0, -3.5, 2.0, 17.0, -3.1, "graphite"),
                box(-2.35, 11.0, -3.55, -2.0, 17.35, -3.05, "gold"),
                box(2.0, 11.0, -3.55, 2.35, 17.35, -3.05, "gold"),
                box(-2.0, 17.0, -3.55, 2.0, 17.35, -3.05, "gold"),
                box(-0.6, 12.2, -3.65, 0.6, 15.8, -3.5, "rune")]
CHEST = ([box(-2.1, 2.4, -3.7, 2.1, 6.6, -3.1, "gold", DIAMOND),
          box(-1.3, 3.2, -3.95, 1.3, 5.8, -3.6, "gem", DIAMOND),
          box(-4.6, -1.7, -3.4, 4.6, -0.9, 3.4, "silver"),
          box(-5.4, 10.0, -3.4, 5.4, 11.0, 3.4, "gold"),
          box(-1.0, 9.8, -3.65, 1.0, 11.2, -3.35, "silver")]
         + TABARD_FRONT + [box(b[0], b[1], -b[5], b[3], b[4], -b[2], b[6]) for b in TABARD_FRONT])

PAULDRON_RIGHT = [box(-4.8, -3.8, -3.6, 2.4, 0.6, 3.6, "graphite"),
                  box(-4.6, -4.2, -3.3, 2.2, -3.8, 3.3, "silver"),
                  box(-4.95, 0.4, -3.75, 2.55, 1.1, 3.75, "gold"),
                  box(-5.05, -3.0, -1.0, -4.85, 0.0, 1.0, "rune"),
                  box(-4.3, 6.0, -3.3, 2.3, 7.0, 3.3, "gold"),
                  box(-4.45, 7.4, -0.6, -4.3, 9.6, 0.6, "rune")]

KNEE = [box(-2.2, 4.0, -3.1, 2.2, 7.4, -2.5, "gold"),
        box(-1.6, 4.6, -3.3, 1.6, 6.8, -3.0, "graphite"),
        box(-0.4, 5.0, -3.45, 0.4, 6.4, -3.3, "rune")]

BOOT = [box(-3.3, 8.6, -3.3, 3.3, 9.5, 3.3, "gold"),
        box(-3.1, 10.6, -3.8, 3.1, 12.9, -3.05, "silver")]

MODELS = {"helmet": HELMET, "chest": CHEST, "pauldron_right": PAULDRON_RIGHT,
          "pauldron_left": [mirror(b) for b in PAULDRON_RIGHT], "knee": KNEE, "boot": BOOT}

if __name__ == "__main__":
    for name, boxes in MODELS.items():
        model = {"textures": {"d": "infinity_gauntlet:item/uru_detail", "particle": "infinity_gauntlet:item/uru_detail"},
                 "elements": [element(b) for b in boxes]}
        with open(f"{ROOT}models/item/uru_detail_{name}.json", "w") as f:
            json.dump(model, f, indent=1)
            f.write("\n")
        with open(f"{ROOT}items/uru_detail_{name}.json", "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"infinity_gauntlet:item/uru_detail_{name}"}}, f, indent=2)
            f.write("\n")
