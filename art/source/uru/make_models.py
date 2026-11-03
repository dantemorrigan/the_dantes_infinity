"""3D details of the Armor of Uru, drawn on top of the vanilla armor by UruDetailLayer.

Boxes are written in MODEL PART coordinates (pixels, Y down, front = -Z), exactly like the player model, and
converted to item-model coordinates for a model drawn after part.translateAndRotate() + a 180 deg turn around X:
item (x, y, z) = (X + 8, 8 - Y, 8 - Z).

Run from the repository root: python3 art/source/uru/make_models.py
"""
import json

ROOT = "src/main/resources/assets/infinity_gauntlet/"
UV = {"graphite": [0, 0, 4, 4], "gold": [4, 0, 8, 4], "silver": [8, 0, 12, 4], "rune": [12, 0, 16, 4],
      "gem": [0, 4, 4, 8], "wing": [4, 4, 8, 8], "dark": [8, 4, 12, 8], "goldrune": [12, 4, 16, 8]}
GLOW = {"rune", "gem", "goldrune"}


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
    """A fan of feathers on the side of the helm, spreading up and outward (reads from the front and the side)."""
    fan = lambda angle: {"angle": angle, "axis": "z", "origin": (-5.6, -5.0, 1.0)}
    return [box(-6.0, -6.8, -1.6, -5.0, -3.4, 2.8, "gold"),                 # root
            box(-6.6, -11.8, 0.2, -5.6, -5.0, 3.2, "wing", fan(0)),         # upright feather
            box(-6.6, -11.0, 0.6, -5.6, -5.0, 3.8, "wing", fan(22.5)),      # leaning out
            box(-6.6, -10.0, 1.0, -5.6, -5.0, 4.4, "wing", fan(45)),        # most outward
            box(-6.4, -8.0, 2.6, -5.8, -5.0, 5.4, "silver", fan(45))]       # back tip


HELMET = ([box(-0.6, -9.8, -5.4, 0.6, -9.0, 4.8, "gold"),              # crest ridge
           box(-0.35, -10.8, -4.0, 0.35, -9.8, 2.6, "silver"),          # fin
           box(-1.7, -10.3, -5.75, 1.7, -6.6, -5.0, "goldrune"),        # front crest plate
           box(-5.35, -4.0, -5.35, -4.7, 0.6, -2.0, "silver"),          # cheek guards
           box(4.7, -4.0, -5.35, 5.35, 0.6, -2.0, "silver")]
          + wing_right() + [mirror(b) for b in wing_right()])

DIAMOND = {"angle": 45, "axis": "z", "origin": (0, 4.5, -3.4)}
TABARD_FRONT = [box(-2.2, 11.1, -3.45, 2.2, 16.8, -3.1, "graphite"),
                box(-1.5, 16.8, -3.45, 1.5, 17.8, -3.1, "graphite"),
                box(-0.7, 17.8, -3.45, 0.7, 18.6, -3.1, "graphite"),
                box(-2.5, 11.1, -3.5, -2.2, 16.9, -3.05, "gold"),
                box(2.2, 11.1, -3.5, 2.5, 16.9, -3.05, "gold"),
                box(-1.8, 16.8, -3.5, -1.5, 17.9, -3.05, "gold"),
                box(1.5, 16.8, -3.5, 1.8, 17.9, -3.05, "gold"),
                box(-0.5, 12.0, -3.6, 0.5, 16.2, -3.45, "rune")]
CHEST = ([box(-4.4, -1.8, -3.45, 4.4, -0.6, 3.45, "silver"),               # gorget
          box(-3.6, 0.3, -3.35, 0.1, 1.1, -3.05, "gold", {"angle": -22.5, "axis": "z", "origin": (-1.75, 0.7, -3.2)}),
          box(-0.1, 0.3, -3.35, 3.6, 1.1, -3.05, "gold", {"angle": 22.5, "axis": "z", "origin": (1.75, 0.7, -3.2)}),
          box(-2.3, 2.2, -3.75, 2.3, 6.8, -3.1, "gold", DIAMOND),
          box(-1.45, 3.05, -4.05, 1.45, 5.95, -3.65, "gem", DIAMOND),
          box(-5.4, 10.0, -3.4, 5.4, 11.1, 3.4, "gold"),                  # belt
          box(-1.3, 9.6, -3.75, 1.3, 11.5, -3.35, "goldrune")]            # buckle
         + TABARD_FRONT + [box(b[0], b[1], -b[5], b[3], b[4], -b[2], b[6]) for b in TABARD_FRONT])

PAULDRON_RIGHT = [box(-5.0, -4.0, -3.8, 2.6, -1.0, 3.8, "graphite"),     # top plate
                  box(-4.8, -4.4, -3.5, 2.4, -4.0, 3.5, "silver"),
                  box(-3.2, -4.9, -0.4, 0.6, -4.4, 0.4, "gold"),          # ridge
                  box(-5.15, -1.3, -3.95, 2.75, -0.7, 3.95, "gold"),
                  box(-5.3, -0.9, -3.6, 2.2, 1.2, 3.6, "graphite"),       # second lame
                  box(-5.45, 1.0, -3.75, 2.3, 1.5, 3.75, "gold"),
                  box(-5.0, 1.5, -3.3, 1.8, 3.2, 3.3, "graphite"),        # third lame
                  box(-5.55, -0.6, -1.2, -5.3, 0.9, 1.2, "rune"),
                  box(-4.35, 6.5, -3.35, 2.35, 9.8, 3.35, "graphite"),    # vambrace
                  box(-4.55, 6.2, -3.55, 2.55, 6.8, 3.55, "gold"),
                  box(-4.55, 9.6, -3.55, 2.55, 10.2, 3.55, "gold"),
                  box(-4.5, 7.2, -0.7, -4.35, 9.2, 0.7, "rune")]

KNEE = [box(-2.3, 3.8, -3.2, 2.3, 7.6, -2.5, "gold"),
        box(-1.7, 4.4, -3.45, 1.7, 7.0, -3.15, "graphite"),
        box(-0.45, 4.9, -3.6, 0.45, 6.5, -3.45, "rune"),
        box(-2.9, 4.2, -2.8, -2.5, 7.0, -1.0, "silver"),
        box(2.5, 4.2, -2.8, 2.9, 7.0, -1.0, "silver")]

BOOT = [box(-3.35, 7.6, -3.35, 3.35, 8.6, 3.35, "gold"),
        box(-1.0, 8.8, -3.4, 1.0, 10.6, -3.1, "goldrune"),
        box(-3.1, 10.8, -3.9, 3.1, 13.0, -3.05, "silver"),
        box(-2.2, 11.6, -4.4, 2.2, 13.0, -3.9, "silver"),
        box(-3.1, 11.8, 3.05, 3.1, 13.0, 3.5, "gold")]

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
