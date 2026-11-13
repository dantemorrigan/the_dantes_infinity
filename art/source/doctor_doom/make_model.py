"""Doctor Doom: geometry, texture, Blockbench project and preview, all generated from this file.

Space is the one GeoModel uses: Y up, the model faces -Z, 16 units per block, feet at the origin. The eight animated
bones (root, body, head, arm_r, arm_l, cape, leg_r, leg_l) keep the pivots the animation file was authored for.
Slanted parts live in `bind_*` child bones with a fixed rotation, because GeoModel has no per-cube rotation.
Angles below are right-handed; Bedrock's `bind_rotation` is their negative.

Every face gets its own texture rectangle at 2 texels per unit, painted from the texel's model-space position so
patterns line up across neighbouring boxes.

Run from the repository root: python3 art/source/doctor_doom/make_model.py [preview.png]
"""
import base64
import io
import json
import math
import sys
import uuid

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = "src/main/resources/assets/infinity_gauntlet/"
GEO = ROOT + "doctor_doom/doctor_doom.geo.json"
ANIM = ROOT + "doctor_doom/doctor_doom.animation.json"
TEXTURE = ROOT + "textures/entity/doctor_doom/doctor_doom.png"
BBMODEL = "art/doctor_doom/blockbench/doctor_doom.bbmodel"
D = 2  # texels per model unit
TEX = 256
FACES = ("north", "south", "east", "west", "up", "down")

BONES = {}
CUBES = []


def bone(name, parent, pivot, rot=None):
    BONES[name] = {"name": name, "parent": parent, "pivot": [float(v) for v in pivot], "rot": rot}


def box(b, x0, x1, y0, y1, z0, z1, mat, side=-1, **kw):
    faces = {f: kw.pop(f, mat) for f in FACES}
    assert x1 > x0 and y1 > y0 and z1 > z0, (b, mat)
    CUBES.append({"bone": b, "o": [x0, y0, z0], "s": [x1 - x0, y1 - y0, z1 - z0], "faces": faces, "p": kw,
                  "side": side, "name": kw.pop("name", mat)})


class Side:
    """Parts are written for the -X side and mirrored for +X."""

    def __init__(self, k):
        self.k = k
        self.suf = "_r" if k < 0 else "_l"

    def box(self, b, xa, xb, y0, y1, z0, z1, mat, **kw):
        if self.k > 0:
            xa, xb = -xb, -xa
            e, w = kw.pop("east", None), kw.pop("west", None)
            if e: kw["west"] = e
            if w: kw["east"] = w
        box(b, xa, xb, y0, y1, z0, z1, mat, side=self.k, **kw)

    def bone(self, name, parent, pivot, rot):
        if self.k > 0:
            pivot = (-pivot[0], pivot[1], pivot[2])
            rot = (rot[0], -rot[1], -rot[2])
        bone(name + self.suf, parent, pivot, rot)
        return name + self.suf


# ---------------------------------------------------------------------------------------------------- geometry

def build():
    bone("root", None, (0, 0, 0))
    bone("body", "root", (0, 19.5, 0))
    bone("head", "body", (0, 29.1, 0))

    # --- head: steel mask set into a deep hood. Eye slits are real holes with the eyes painted on a recess behind.
    box("head", -3, 3, 30, 36.5, -3.7, -0.4, "hood_in", north="eyes", name="face_recess")
    box("head", -3.1, 3.1, 35.0, 36.75, -4.2, -3.7, "mask", name="mask_brow")
    box("head", -2.7, 2.7, 35.0, 35.5, -4.5, -4.2, "mask", name="mask_brow_ridge")
    box("head", -0.5, 0.5, 34.0, 35.0, -4.2, -3.7, "mask", name="mask_bridge")
    box("head", -0.5, 0.5, 32.5, 34.75, -4.65, -4.2, "mask", name="mask_nose")
    box("head", -3.1, 3.1, 30, 34.0, -4.2, -3.7, "mask", name="mask_cheeks")
    box("head", -1.75, 1.75, 30.5, 32.25, -4.45, -4.2, "mask", north="grille", name="mask_mouth")
    box("head", -1.5, 1.5, 29.6, 30.5, -4.3, -3.5, "mask", name="mask_chin")
    box("head", -4.2, 4.2, 29.6, 37.0, -0.4, 4.2, "cloth", north="hood_in", name="hood_back")
    box("head", -3.9, 3.9, 36.6, 38.0, -4.8, 3.9, "cloth", down="hood_in", name="hood_top")
    box("head", -2.9, 2.9, 38.0, 38.8, -4.0, 3.3, "cloth", name="hood_crown")
    box("head", -3.3, 3.3, 36.1, 36.9, -5.3, -4.6, "cloth", down="hood_in", south="hood_in", name="hood_lip")
    box("head", -3.2, 3.2, 27.6, 30.6, 3.4, 5.3, "cloth", name="hood_tail")
    for s in (Side(-1), Side(1)):
        s.box("head", -3.1, -2.5, 34.0, 35.0, -4.2, -3.7, "mask", name="mask_temple")
        s.box("head", -3.3, -3.0, 30.2, 36.5, -3.9, -0.8, "mask", name="mask_side")
        s.box("head", -4.4, -3.1, 30.0, 36.8, -4.9, -0.4, "cloth", east="hood_in", name="hood_side")
        fall = s.bone("bind_hood_fall", "head", (-3.85, 31.4, 0), (0, 0, -14))
        s.box(fall, -4.7, -3.1, 28.3, 31.4, -3.9, 3.3, "cloth", east="hood_in", name="hood_fall")

    # --- body: quilted tunic, gorget, the cloak's mantle with two clasps and a chain, belt, split skirt.
    box("body", -5, 5, 19.3, 27.8, -2.7, 2.7, "tunic", north="quilt", name="torso")
    box("body", -4, 4, 20, 26.5, -3.2, -2.7, "tunic", north="quilt", name="chest")
    box("body", -4.5, 4.5, 16.8, 19.5, -2.6, 2.6, "tunic", north="quilt", name="abdomen")
    box("body", -2.6, 2.6, 27.8, 30.0, -2.2, 2.2, "steel_dark", bands=2, name="gorget")
    box("body", -5.6, 5.6, 26.8, 28.5, -1.2, 3.9, "cloth", name="mantle_yoke")
    box("body", -2.6, 2.6, 27.1, 28.9, -3.6, -1.2, "cloth", name="cowl")
    box("body", -5.2, 5.2, 15.4, 17.0, -3.0, 3.0, "leather", name="belt")
    box("body", -1.25, 1.25, 15.0, 17.4, -3.5, -3.0, "gold", north="buckle", name="buckle")
    box("body", 2.6, 4.4, 13.0, 15.6, -3.7, -2.9, "leather", north="pouch", name="pouch")
    box("body", -6.0, -5.1, 11.4, 15.6, -1.5, 1.3, "leather", name="holster")
    box("body", -6.25, -5.25, 15.6, 16.7, -0.7, 0.6, "gold", name="pistol_grip")
    box("body", -4.6, 4.6, 14.4, 15.4, -2.5, 2.5, "steel_dark", name="hips")
    box("body", -1.0, 1.0, 8.5, 15.4, -3.35, -2.85, "cloth", north="tab", name="skirt_tab")
    box("body", -4.9, 4.9, 9.9, 15.4, 2.2, 2.9, "skirt", hem=9.9, name="skirt_back")
    for i in range(7):  # the chain sags between the clasps
        x = -2.4 + i * 0.8
        y = 25.45 - 0.95 * (1 - (x / 2.4) ** 2)
        box("body", x - 0.4, x + 0.4, y - 0.25, y + 0.25, -3.6, -3.2, "chain", link=i, name="chain")
    for s in (Side(-1), Side(1)):
        s.box("body", -6.1, -2.6, 26.2, 28.3, -3.7, -1.2, "cloth", name="mantle_front")
        s.box("body", -4.5, -2.7, 26.3, 27.2, -3.75, -3.2, "cloth", name="clasp_strap")
        s.box("body", -4.9, -2.3, 24.9, 26.3, -3.9, -3.2, "gold", north="clasp", name="clasp_wide")
        s.box("body", -4.4, -2.8, 24.4, 26.8, -3.9, -3.2, "gold", north="clasp", name="clasp_tall")
        s.box("body", -4.1, -3.1, 25.1, 26.1, -4.2, -3.9, "gold", north="clasp", name="clasp_boss")
        s.box("body", -4.9, -1.0, 9.6, 15.4, -3.0, -2.3, "skirt", hem=9.6, name="skirt_front")
        s.box("body", -5.2, -4.5, 10.2, 15.4, -2.6, 2.6, "skirt", hem=10.2, name="skirt_side")

    # --- arms: layered pauldrons, mail sleeve with a cloth wrap, plated gauntlet, fist.
    for s in (Side(-1), Side(1)):
        arm = "arm" + s.suf
        bone(arm, "body", (6.28 * s.k, 26.65, 0))
        s.box(arm, -8.3, -4.9, 20.4, 27.2, -1.75, 1.75, "mail", name="upper_arm")
        s.box(arm, -8.45, -4.75, 21.6, 23.4, -1.9, 1.9, "wrap", name="arm_wrap")
        s.box(arm, -9.3, -5.1, 26.4, 28.9, -2.7, 2.7, "steel", name="pauldron")
        s.box(arm, -8.7, -5.6, 28.9, 29.4, -1.9, 1.9, "steel", name="pauldron_crest")
        p2 = s.bone("bind_pauldron_mid", arm, (-7.8, 26.6, 0), (0, 0, -12))
        s.box(p2, -9.7, -6.0, 24.6, 26.6, -2.5, 2.5, "steel", name="pauldron_mid")
        p3 = s.bone("bind_pauldron_low", arm, (-8.0, 24.8, 0), (0, 0, -18))
        s.box(p3, -9.6, -6.6, 23.3, 24.8, -2.2, 2.2, "steel", name="pauldron_low")
        s.box(arm, -8.6, -4.6, 19.2, 21.0, -2.1, 2.1, "steel", name="elbow")
        s.box(arm, -7.4, -5.8, 19.4, 20.8, 2.1, 2.8, "steel", name="elbow_spike")
        s.box(arm, -8.4, -4.8, 14.0, 19.2, -1.9, 1.9, "steel", bands=3, name="forearm")
        s.box(arm, -8.8, -4.4, 17.5, 19.0, -2.3, 2.3, "steel", name="cuff")
        s.box(arm, -9.2, -8.4, 15.0, 17.5, -1.4, 1.4, "steel", name="vambrace_fin")
        s.box(arm, -9.0, -8.4, 14.2, 15.0, -1.1, 1.1, "steel", name="vambrace_fin_low")
        s.box(arm, -8.55, -4.65, 13.6, 14.3, -2.05, 2.05, "gold", name="wrist_band")
        s.box(arm, -8.1, -5.1, 11.4, 13.7, -1.6, 1.6, "steel_dark", name="hand")
        s.box(arm, -8.45, -8.1, 11.7, 13.4, -1.3, 1.3, "steel", name="knuckle_plate")
        s.box(arm, -8.0, -5.6, 10.5, 11.5, -1.5, 1.5, "steel_dark", bands=2, name="fingers")
        s.box(arm, -6.3, -5.0, 11.0, 12.6, -2.2, -1.6, "steel_dark", name="thumb")

    # --- the cloak: nine strips in three tiers that step outward and back, plus two slanted panels per side.
    bone("cape", "body", (0, 27.45, 2.95))
    box("cape", -5.9, 5.9, 26.6, 28.2, 2.6, 4.0, "cloth", name="cape_collar")
    hems = [1.2, 0.4, 0.9, 0.2, 0.6, 0.1, 1.0, 0.3, 1.4]
    tiers = [(18.5, 27.0, 1.3, 3.2), (9.5, 18.5, 1.55, 3.7), (None, 9.5, 1.8, 4.3)]
    for k in range(-4, 5):
        for y0, y1, w, z in tiers:
            hem = hems[k + 4]
            z += 0.35 * (k % 2)
            box("cape", (k - 0.5) * w, (k + 0.5) * w, hem if y0 is None else y0, y1, z, z + 0.8, "cape",
                north="cape_in", hem=hem, name="cape_strip")
    for s in (Side(-1), Side(1)):
        side = s.bone("bind_cape_side", "cape", (-6.2, 26.6, 3.4), (0, 0, -7))
        s.box(side, -7.2, -5.6, 1.4, 26.6, 2.9, 4.0, "cape", north="cape_in", hem=1.4, name="cape_side")
        edge = s.bone("bind_cape_edge", "cape", (-7.6, 22.5, 3.2), (0, 0, -11))
        s.box(edge, -8.6, -7.0, 2.2, 22.5, 2.5, 3.7, "cape", north="cape_in", hem=2.2, name="cape_edge")

    # --- legs: mail thigh with a cuisse, knee cop, banded greave, armored boot.
    for s in (Side(-1), Side(1)):
        leg = "leg" + s.suf
        bone(leg, "root", (2.39 * s.k, 15.25, 0))
        s.box(leg, -4.35, -0.45, 8.4, 15.4, -1.85, 1.85, "mail", name="thigh")
        s.box(leg, -4.0, -0.8, 9.2, 14.2, -2.2, -1.85, "steel", name="cuisse")
        s.box(leg, -4.55, -0.25, 6.8, 9.2, -2.5, 2.0, "steel", name="knee")
        s.box(leg, -3.3, -1.5, 7.3, 8.7, -2.95, -2.5, "steel", name="knee_boss")
        s.box(leg, -4.25, -0.55, 2.6, 6.9, -1.9, 1.85, "steel", bands=3, name="greave")
        s.box(leg, -2.9, -1.9, 2.8, 6.8, -2.3, -1.9, "steel", name="greave_ridge")
        s.box(leg, -3.9, -0.9, 3.2, 6.4, 1.85, 2.3, "steel", name="calf_plate")
        s.box(leg, -4.45, -0.35, 2.0, 2.7, -2.1, 2.05, "gold", name="boot_band")
        s.box(leg, -4.4, -0.4, 0.5, 2.1, -2.6, 2.1, "steel_dark", name="boot")
        s.box(leg, -4.2, -0.6, 0.5, 1.7, -3.7, -2.6, "steel", name="toe_cap")
        s.box(leg, -4.5, -0.3, 0, 0.5, -3.85, 2.25, "sole", name="sole")


# ---------------------------------------------------------------------------------------------------- painting

def noise(ix, iy, iz):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535 * 2 - 1


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def mul(c, f):
    return tuple(v * f for v in c)


STEEL, STEEL_LO = (60, 66, 74), (26, 29, 34)
GOLD, GOLD_HI, GOLD_LO = (150, 118, 56), (200, 168, 92), (86, 64, 30)
PLATES = {"steel", "steel_dark", "gold", "clasp", "buckle", "leather", "pouch", "mask", "chain"}
FACE_LIGHT = {"up": 1.1, "down": 0.68, "north": 1.0, "south": 0.92, "east": 0.9, "west": 0.9}


def paint(mat, c):
    x, y, z, face, p = c["x"], c["y"], c["z"], c["face"], c["p"]
    ix, iy, iz = math.floor(x * D + 1e-6), math.floor(y * D + 1e-6), math.floor(z * D + 1e-6)
    ax = abs(x)
    grain = 0.05
    if mat == "cloth":
        t = z if face in ("east", "west") else x
        fold = 0.5 + 0.5 * math.sin(t * 2.1 + 0.6 * math.sin(y * 0.3))
        col = lerp((15, 34, 24), (34, 68, 45), fold)
    elif mat in ("cape", "cape_in"):
        if face in ("north", "south"):
            col = lerp((12, 28, 20), (32, 64, 42), math.sin(math.pi * c["fu"]) ** 0.8)
        else:
            col = (13, 30, 21)
        col = mul(col, 0.78 + 0.22 * min(1, y / 27))
        if y < p.get("hem", 0) + 0.6: col = mul(col, 0.7)
        if mat == "cape_in": col = mul(col, 0.5)
    elif mat == "hood_in":
        col, grain = (9, 16, 12), 0.15
    elif mat == "tunic":
        col = (40, 56, 34)
    elif mat == "quilt":
        gx, gy = (ix + 300) % 3, iy % 3
        if gx == 0 or gy == 0: col = (22, 33, 22)
        elif gx == 1 and gy == 2: col = (72, 94, 52)
        else: col = (46, 66, 38)
    elif mat == "skirt":
        t = z if face in ("east", "west") else x
        col = lerp((28, 42, 27), (48, 66, 38), 0.5 + 0.5 * math.sin(t * 1.7 + 1.0))
        if y < p["hem"] + 0.5: col = (20, 30, 20)
    elif mat == "tab":
        col = GOLD_LO if ax > 0.5 or y < 9.0 else (20, 42, 28)
    elif mat in ("steel", "steel_dark"):
        col = STEEL if mat == "steel" else (38, 42, 50)
        k = p.get("bands")
        if k and face not in ("up", "down"):
            if iy % k == 0: col = STEEL_LO
            elif iy % k == k - 1: col = mul(col, 1.25)
        col = mul(col, 0.92 + 0.16 * c["fv_inv"])
    elif mat == "mask":
        col = (104, 110, 115)
        if face == "north":
            if 32.5 < y < 34.0: col = mul(col, 1.12)
            if y > 35.75: col = mul(col, 0.74)
            if ax > 2.5: col = mul(col, 0.86)
            if y < 30.6: col = mul(col, 0.8)
            if 31 < y < 33.4 and abs((ax - 0.9) - (33.4 - y) * 0.54) < 0.26: col = mul(col, 0.7)
            for rx, ry in ((2.25, 36.25), (2.25, 30.75), (0.25, 36.25)):
                if abs(ax - rx) < 0.26 and abs(y - ry) < 0.26: col = (52, 56, 60)
        else:
            col = mul(col, 0.8)
    elif mat == "grille":
        edge = c["j"] == 0 or c["j"] == c["h"] - 1 or c["i"] == 0 or c["i"] == c["w"] - 1
        col = (92, 98, 103) if edge else ((14, 15, 17) if c["i"] % 2 == 1 else (122, 128, 133))
        grain = 0.02
    elif mat == "eyes":
        col, grain = (7, 8, 9), 0.0
        if 0.5 <= ax < 2.5 and 34.0 <= y < 35.0:
            if y >= 34.5: col = (10, 9, 9)
            elif 1.0 <= ax < 2.0: col = (158, 112, 44) if ax >= 1.5 else (104, 66, 24)
            else: col = (132, 128, 116)
    elif mat in ("gold", "clasp", "buckle", "chain"):
        col = GOLD
        if mat == "clasp" and face == "north":
            r = math.hypot(ax - 3.6, y - 25.6)
            col = GOLD_HI if r < 0.45 else (GOLD_LO if r < 0.95 else GOLD)
        if mat == "buckle" and face == "north" and 0.25 < c["fu"] < 0.75 and 0.25 < c["fv"] < 0.75:
            col = (58, 42, 22)
        if mat == "chain":
            col = GOLD_HI if p["link"] % 2 == 0 else (124, 96, 44)
    elif mat in ("leather", "pouch"):
        col = (58, 40, 27)
        if mat == "pouch" and c["fv"] < 0.4: col = (76, 54, 35)
        if mat == "pouch" and 0.4 <= c["fv"] < 0.62 and 0.35 < c["fu"] < 0.65: col = GOLD
    elif mat == "wrap":
        col = (100, 80, 54) if (ix + iy + iz) % 3 else (66, 50, 33)
    elif mat == "mail":
        col = (50, 54, 62) if (ix + iy + iz) % 2 else (30, 33, 39)
        grain = 0.03
    elif mat == "sole":
        col = (20, 20, 22)
    else:
        raise KeyError(mat)

    f = 1 + grain * noise(ix, iy, iz)
    if mat not in ("eyes", "hood_in", "cape_in"): f *= FACE_LIGHT[face]
    if mat in PLATES and c["w"] >= 3 and c["h"] >= 3:
        if c["i"] == 0 or c["j"] == 0: f *= 1.22
        elif c["i"] == c["w"] - 1 or c["j"] == c["h"] - 1: f *= 0.72
    return tuple(max(0, min(255, int(round(v * f)))) for v in col)


def face_frame(cube, face):
    """Top-left corner and the U and V edge vectors of a face, in GeoModel's vertex order."""
    (x0, y0, z0), (sx, sy, sz) = cube["o"], cube["s"]
    x1, y1, z1 = x0 + sx, y0 + sy, z0 + sz
    return {
        "north": ((x1, y1, z0), (-sx, 0, 0), (0, -sy, 0), (0, 0, -1)),
        "south": ((x0, y1, z1), (sx, 0, 0), (0, -sy, 0), (0, 0, 1)),
        "east": ((x1, y1, z1), (0, 0, -sz), (0, -sy, 0), (1, 0, 0)),
        "west": ((x0, y1, z0), (0, 0, sz), (0, -sy, 0), (-1, 0, 0)),
        "up": ((x0, y1, z0), (sx, 0, 0), (0, 0, sz), (0, 1, 0)),
        "down": ((x0, y0, z1), (sx, 0, 0), (0, 0, -sz), (0, -1, 0)),
    }[face]


def texture():
    rects = []
    for ci, cube in enumerate(CUBES):
        for face in FACES:
            _, u, v, _ = face_frame(cube, face)
            w = max(1, round(max(abs(a) for a in u) * D))
            h = max(1, round(max(abs(a) for a in v) * D))
            rects.append((h, w, ci, face))
    rects.sort(key=lambda r: (-r[0], -r[1], r[2], FACES.index(r[3])))
    img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = img.load()
    x = y = shelf = 0
    for h, w, ci, face in rects:
        if x + w > TEX:
            x, y, shelf = 0, y + shelf, 0
        shelf = max(shelf, h)
        assert y + h <= TEX, "texture atlas is full"
        cube = CUBES[ci]
        cube.setdefault("uv", {})[face] = (x, y, w, h)
        tl, u, v, _ = face_frame(cube, face)
        for j in range(h):
            for i in range(w):
                fu, fv = (i + 0.5) / w, (j + 0.5) / h
                pos = [tl[a] + u[a] * fu + v[a] * fv for a in range(3)]
                ctx = {"x": pos[0], "y": pos[1], "z": pos[2], "face": face, "p": cube["p"], "fu": fu, "fv": fv,
                       "fv_inv": 1 - fv if face not in ("up", "down") else 0.5, "i": i, "j": j, "w": w, "h": h}
                px[x + i, y + j] = paint(cube["faces"][face], ctx) + (255,)
        x += w
    print("atlas rows used:", y + shelf, "of", TEX)
    return img


# ---------------------------------------------------------------------------------------------------- output

def r3(v):
    return round(v + 0.0, 4)


def write_geo():
    bones = []
    for b in BONES.values():
        out = {"name": b["name"], "pivot": [r3(v) for v in b["pivot"]]}
        if b["parent"]: out["parent"] = b["parent"]
        if b["rot"]: out["bind_rotation"] = [r3(-v) for v in b["rot"]]
        cubes = []
        for c in CUBES:
            if c["bone"] != b["name"]: continue
            cubes.append({"origin": [r3(v) for v in c["o"]], "size": [r3(v) for v in c["s"]],
                          "uv": {f: {"uv": [c["uv"][f][0], c["uv"][f][1]], "uv_size": [c["uv"][f][2], c["uv"][f][3]]}
                                 for f in FACES}})
        if cubes: out["cubes"] = cubes
        bones.append(out)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.doctor_doom", "texture_width": TEX, "texture_height": TEX},
        "bones": bones}]}
    with open(GEO, "w") as f:
        json.dump(geo, f, separators=(",", ":"))
        f.write("\n")


def write_bbmodel(img):
    """Keeps the project's animations and the animated groups' ids; replaces the boxes and the texture."""
    with open(BBMODEL) as f:
        bb = json.load(f)
    ids = {}

    def collect(node):
        if isinstance(node, dict):
            ids[node["name"]] = node["uuid"]
            for child in node["children"]: collect(child)

    for node in bb["outliner"]: collect(node)
    space = uuid.UUID("6f1d0c1e-5b0a-4a57-9f0e-d00dd00dd00d")
    elements, members = [], {}
    for i, c in enumerate(CUBES):
        b = BONES[c["bone"]]
        group = b if not b["rot"] else BONES[b["parent"]]
        e = {"name": c["name"], "type": "cube", "box_uv": False, "autouv": 0, "color": 0,
             "from": [r3(v) for v in c["o"]], "to": [r3(c["o"][a] + c["s"][a]) for a in range(3)],
             "origin": [r3(v) for v in b["pivot"]], "uuid": str(uuid.uuid5(space, "cube%d" % i)),
             "faces": {f: {"uv": [c["uv"][f][0], c["uv"][f][1], c["uv"][f][0] + c["uv"][f][2],
                                  c["uv"][f][1] + c["uv"][f][3]], "texture": 0} for f in FACES}}
        if b["rot"]: e["rotation"] = [r3(v) for v in b["rot"]]
        elements.append(e)
        members.setdefault(group["name"], []).append(e["uuid"])

    def group(name):
        b = BONES[name]
        kids = [group(k["name"]) for k in BONES.values() if k["parent"] == name and not k["rot"]]
        return {"name": name, "origin": [r3(v) for v in b["pivot"]], "rotation": [0, 0, 0], "color": 0,
                "uuid": ids.get(name) or str(uuid.uuid5(space, name)), "export": True, "isOpen": True,
                "children": kids + members.get(name, [])}

    buf = io.BytesIO()
    img.save(buf, "PNG")
    bb["elements"] = elements
    bb["outliner"] = [group("root")]
    bb["resolution"] = {"width": TEX, "height": TEX}
    bb["textures"][0]["source"] = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()
    for key in ("width", "height", "uv_width", "uv_height"):
        if key in bb["textures"][0]: bb["textures"][0][key] = TEX
    with open(BBMODEL, "w") as f:
        json.dump(bb, f, separators=(",", ":"))
        f.write("\n")


# ---------------------------------------------------------------------------------------------------- preview

def rotation(tx, ty, tz):
    tx, ty, tz = (math.radians(a) for a in (tx, ty, tz))
    rx = np.array([[1, 0, 0], [0, math.cos(tx), -math.sin(tx)], [0, math.sin(tx), math.cos(tx)]])
    ry = np.array([[math.cos(ty), 0, math.sin(ty)], [0, 1, 0], [-math.sin(ty), 0, math.cos(ty)]])
    rz = np.array([[math.cos(tz), -math.sin(tz), 0], [math.sin(tz), math.cos(tz), 0], [0, 0, 1]])
    return rz @ ry @ rx


def sample(clip, time):
    """Bedrock keyframes -> {bone: (rotation, position)} with linear interpolation, as GeoAnimation does."""
    out = {}
    for name, channels in clip["bones"].items():
        vals = []
        for ch in ("rotation", "position"):
            keys = sorted((float(t), v) for t, v in channels.get(ch, {}).items()) if isinstance(channels.get(ch), dict) else []
            v = [0, 0, 0]
            for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
                if t0 <= time <= t1:
                    f = (time - t0) / (t1 - t0) if t1 > t0 else 0
                    v = [v0[a] + (v1[a] - v0[a]) * f for a in range(3)]
            if keys and time >= keys[-1][0]: v = keys[-1][1]
            if keys and time <= keys[0][0]: v = keys[0][1]
            vals.append(v)
        out[name] = vals
    return out


def bone_matrices(pose):
    mats = {}
    for b in BONES.values():
        rot = list(b["rot"] or (0, 0, 0))
        pos = [0, 0, 0]
        if pose and b["name"] in pose:
            r, pos = pose[b["name"]]
            rot = [rot[a] - r[a] for a in range(3)]
        m = np.eye(4)
        m[:3, :3] = rotation(*rot)
        piv = np.array(b["pivot"])
        m[:3, 3] = piv - m[:3, :3] @ piv + np.array([-pos[0], pos[1], pos[2]])
        mats[b["name"]] = (mats[b["parent"]] @ m) if b["parent"] else m
    return mats


def render(tex, yaw, pitch, scale, size, focus_y, pose=None, ss=2):
    w, h = size[0] * ss, size[1] * ss
    s = scale * ss
    img = np.zeros((h, w, 4), np.uint8)
    depth = np.full((h, w), 1e9)
    view = rotation(pitch, 0, 0) @ rotation(0, yaw, 0)
    light = np.array([-0.45, 0.75, -0.55])
    light /= np.linalg.norm(light)
    mats = bone_matrices(pose)
    for cube in CUBES:
        m = mats[cube["bone"]]
        for face in FACES:
            tl, u, v, n = face_frame(cube, face)
            normal = view @ (m[:3, :3] @ np.array(n, float))
            if normal[2] >= -1e-6: continue
            shade = 0.5 + 0.5 * max(0.0, float(normal @ light)) + 0.12
            tl, u, v = np.array(tl, float), np.array(u, float), np.array(v, float)
            corners = [tl, tl + u, tl + u + v, tl + v]
            pts = [view @ (m[:3, :3] @ p + m[:3, 3] - np.array([0, focus_y, 0])) for p in corners]
            scr = [(w / 2 - p[0] * s, h / 2 - p[1] * s, p[2]) for p in pts]
            ux, uy, uw, uh = cube["uv"][face]
            frac = [(0, 0), (1, 0), (1, 1), (0, 1)]
            for tri in ((0, 1, 2), (0, 2, 3)):
                (ax, ay, az), (bx, by, bz), (cx, cy, cz) = (scr[i] for i in tri)
                fa, fb, fc = (frac[i] for i in tri)
                x0, x1 = max(0, int(min(ax, bx, cx))), min(w - 1, int(max(ax, bx, cx)) + 1)
                y0, y1 = max(0, int(min(ay, by, cy))), min(h - 1, int(max(ay, by, cy)) + 1)
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if x1 < x0 or y1 < y0 or abs(den) < 1e-9: continue
                gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                l1 = ((by - cy) * (gx - cx) + (cx - bx) * (gy - cy)) / den
                l2 = ((cy - ay) * (gx - cx) + (ax - cx) * (gy - cy)) / den
                l3 = 1 - l1 - l2
                zz = l1 * az + l2 * bz + l3 * cz
                sub = depth[y0:y1 + 1, x0:x1 + 1]
                ok = (l1 >= -1e-4) & (l2 >= -1e-4) & (l3 >= -1e-4) & (zz < sub)
                if not ok.any(): continue
                fu = l1 * fa[0] + l2 * fb[0] + l3 * fc[0]
                fv = l1 * fa[1] + l2 * fb[1] + l3 * fc[1]
                tx = np.clip((ux + fu * uw).astype(int), ux, ux + uw - 1)
                ty = np.clip((uy + fv * uh).astype(int), uy, uy + uh - 1)
                col = tex[ty, tx].astype(float)
                col[..., :3] = np.clip(col[..., :3] * shade, 0, 255)
                sub[ok] = zz[ok]
                img[y0:y1 + 1, x0:x1 + 1][ok] = col[ok].astype(np.uint8)
    return Image.fromarray(img, "RGBA").resize(size, Image.LANCZOS)


def preview(img, path):
    tex = np.array(img)
    with open(ANIM) as f:
        clips = json.load(f)["animations"]
    idle = sample(clips["animation.doctor_doom.idle"], 0.8)
    bolt = sample(clips["animation.doctor_doom.gauntlet_bolt"], 0.95)
    edict = sample(clips["animation.doctor_doom.latverian_edict"], 2.0)
    shots = [("Спереди", 0, 6, idle), ("3/4", -32, 9, idle), ("Сбоку", -90, 6, idle), ("Сзади", 180, 6, idle),
             ("Удар перчаткой", 32, 9, bolt), ("Эдикт Латверии", -20, 8, edict)]
    cw, ch, top = 430, 620, 34
    sheet = Image.new("RGBA", (cw * len(shots) + 520, ch + top), (0, 0, 0, 255))
    px = np.zeros((ch + top, sheet.size[0], 4), np.uint8)
    for yy in range(ch + top):  # smoky green backdrop
        t = yy / (ch + top)
        px[yy, :] = (int(14 + 20 * t), int(20 + 34 * t), int(16 + 22 * t), 255)
    sheet = Image.fromarray(px, "RGBA")
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 18)
    except OSError:
        font = None
    for i, (label, yaw, pitch, pose) in enumerate(shots):
        sheet.alpha_composite(render(tex, yaw, pitch, 13.5, (cw, ch), 19.5, pose), (i * cw, top))
        draw.text((i * cw + 10, 8), label, fill=(214, 222, 214, 255), font=font)
    x = cw * len(shots)
    sheet.alpha_composite(render(tex, -18, 6, 42, (520, ch), 31.5, idle), (x, top))
    draw.text((x + 10, 8), "Маска", fill=(214, 222, 214, 255), font=font)
    sheet.convert("RGB").save(path)


if __name__ == "__main__":
    build()
    image = texture()
    image.save(TEXTURE)
    write_geo()
    write_bbmodel(image)
    print(len(CUBES), "cubes,", len(BONES), "bones")
    if len(sys.argv) > 1: preview(image, sys.argv[1])
