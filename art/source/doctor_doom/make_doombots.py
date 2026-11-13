"""The six Doombots: geometry and texture per variant and one shared animation file.

Built on the Doctor Doom generator (same space, same painter, same atlas packing). Every variant has the bones root,
body, head, arm_r, arm_l, leg_r, leg_l and cape, so one set of clips drives them all. Timings follow the pack's
README (shot flash at 0.65 s, shield open at 0.8 s, dive impact at 1.65 s, cast release at 1.2 s, repair pulses at
1.1 / 1.6 / 2.1 s, halberd blows at 0.6 / 1.3 / 1.8 s).

Run from the repository root: python3 art/source/doctor_doom/make_doombots.py [preview.png]
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_model as m  # noqa: E402

ROOT = m.ROOT
VARIANTS = ["soldier", "bastion", "interceptor", "arcanist", "engineer", "royal_guard"]
GLOW = (96, 255, 128)


def face(c):
    x, y, p = abs(c["x"]), c["y"], c["p"]
    eye = p.get("eye", 25.0)
    col = (112, 118, 124)
    if c["face"] != "north": return (88, 94, 100)
    if eye - 0.25 <= y < eye + 0.5 and 0.5 <= x < 2.0: return GLOW if y < eye + 0.25 else (18, 30, 22)
    if y < eye - 1.4 and x < 1.5: return (20, 22, 24) if c["i"] % 2 else (130, 136, 142)
    if x > 2.4 or y > eye + 1.6: col = m.mul(col, 0.82)
    return col


def glow(c):
    return GLOW if (c["i"] + c["j"]) % 2 == 0 else (48, 170, 84)


def reactor(c):
    r = math.hypot(c["fu"] - 0.5, c["fv"] - 0.5)
    return (255, 236, 150) if r < 0.2 else ((240, 170, 40) if r < 0.38 else (120, 88, 36))


def trim(c):
    edge = c["i"] == 0 or c["j"] == 0 or c["i"] == c["w"] - 1 or c["j"] == c["h"] - 1
    return (190, 152, 70) if edge else (30, 84, 48)


def black(c):
    return (34, 36, 42)


def shield_face(c):
    """The Bastion's tower shield: a gold frame, a green field with a steel plate and Doom's mask in the middle."""
    fu, fv, x, y = c["fu"], c["fv"], abs(c["fu"] - 0.5), c["fv"]
    if c["face"] != "north": return (74, 80, 88)
    if fu < 0.06 or fu > 0.94 or fv < 0.035 or fv > 0.965: return (196, 158, 72)
    if fu < 0.10 or fu > 0.90 or fv < 0.06 or fv > 0.94: return (110, 82, 34)
    base = (30, 84, 48) if (int(fv * 28) + int(fu * 12)) % 2 else (26, 74, 42)
    # the steel boss with the mask
    dx, dy = (fu - 0.5) / 0.34, (fv - 0.40) / 0.25
    if dx * dx + dy * dy < 1:
        r = dx * dx + dy * dy
        col = (126, 132, 138) if r < 0.82 else (176, 140, 62)
        if 0.30 < fv < 0.36 and 0.16 < x < 0.30: col = (96, 255, 128) if r < 0.82 else col
        if 0.43 < fv < 0.50 and x < 0.07: col = (74, 78, 84)
        if 0.50 < fv < 0.56 and x < 0.13: col = (30, 32, 34) if int(fu * 40) % 2 else (150, 156, 160)
        return col
    # chevrons below the boss
    for k, off in enumerate((0.66, 0.74, 0.82)):
        if abs((fv - off) - 0.14 * x * 2) < 0.012: return (196, 158, 72)
    return base


m.EXTRA.update({"botface": face, "glow": glow, "reactor": reactor, "trim": trim, "black": black, "shield_face": shield_face})


def chassis(wide=0.0, hood=True, eye=25.0, steel="steel", legs=True):
    """The shared robot body. `wide` thickens the frame (the Bastion)."""
    w = wide
    m.bone("root", None, (0, 0, 0))
    m.bone("body", "root", (0, 12, 0))
    m.bone("head", "body", (0, 21.5, 0))
    m.box("head", -3, 3, 22, 28, -3, 2.6, "mask", north="botface", eye=eye, name="mask")
    m.box("head", -0.5, 0.5, eye - 1.5, eye, -3.4, -3, "mask", name="nose")
    m.box("body", -2, 2, 20.5, 22.4, -1.8, 1.8, "steel_dark", name="neck")
    if hood:
        m.box("head", -3.7, 3.7, 21.6, 28.4, 0.4, 3.5, "cloth", north="hood_in", name="hood_back")
        m.box("head", -3.4, 3.4, 28, 29.2, -3.6, 3.2, "cloth", down="hood_in", name="hood_top")
        for s in (m.Side(-1), m.Side(1)):
            s.box("head", -3.9, -3, 22.2, 28.2, -3.5, 0.4, "cloth", east="hood_in", name="hood_side")
    m.box("body", -4.6 - w, 4.6 + w, 12, 21.4, -2.6 - w * 0.5, 2.6 + w * 0.5, steel, bands=4, name="torso")
    m.box("body", -4.8 - w, 4.8 + w, 11.4, 12.8, -2.8 - w * 0.5, 2.8 + w * 0.5, "leather", name="belt")
    m.box("body", -1, 1, 11.2, 13, -3.2 - w * 0.5, -2.8 - w * 0.5, "gold", north="buckle", name="buckle")
    for s in (m.Side(-1), m.Side(1)):
        arm = "arm" + s.suf
        m.bone(arm, "body", ((5.9 + w) * s.k, 20.4, 0))
        s.box(arm, -7.6 - w * 1.6, -4.6 - w, 12.6, 20.6, -1.7 - w * 0.4, 1.7 + w * 0.4, steel, bands=3, name="arm")
        s.box(arm, -8.2 - w * 1.8, -4.4 - w, 19.6, 22.2, -2.4 - w * 0.5, 2.4 + w * 0.5, steel, name="pauldron")
        s.box(arm, -7.4 - w * 1.5, -4.8 - w, 10.4, 12.8, -1.5 - w * 0.4, 1.5 + w * 0.4, "steel_dark", name="hand")
        if not legs: continue
        leg = "leg" + s.suf
        m.bone(leg, "root", ((2.3 + w * 0.6) * s.k, 12, 0))
        s.box(leg, -4.2 - w, -0.4, 6.2, 12.2, -1.9 - w * 0.4, 1.9 + w * 0.4, steel, bands=3, name="thigh")
        s.box(leg, -4.4 - w, -0.2, 5.4, 7.4, -2.4 - w * 0.4, 1.9 + w * 0.4, "steel", name="knee")
        s.box(leg, -4.1 - w, -0.5, 1.6, 5.6, -1.8 - w * 0.4, 1.8 + w * 0.4, steel, bands=3, name="shin")
        s.box(leg, -4.3 - w, -0.3, 0, 1.8, -3.0 - w * 0.4, 2.0 + w * 0.4, "steel_dark", name="boot")
    m.bone("cape", "body", (0, 21, 2.8 + w * 0.5))


def cape(length, width=4.6, trimmed=False):
    for k in range(-2, 3):
        hem = 21 - length + (0.6 if k % 2 else 0)
        z = 2.9 + 0.3 * (k % 2)
        x0, x1 = (k - 0.5) * width * 0.44, (k + 0.5) * width * 0.44
        m.box("cape", x0, x1, hem, 21.2, z, z + 0.7, "cape", north="cape_in", hem=hem, name="cape_strip")
        if trimmed: m.box("cape", x0, x1, hem - 0.6, hem, z, z + 0.7, "gold", name="cape_trim")


def soldier():
    chassis()
    m.box("body", -3.2, 3.2, 12.8, 20.4, -3.1, -2.6, "tunic", north="quilt", name="tabard")
    cape(12)
    for s in (m.Side(-1), m.Side(1)):
        s.box("body", -3.6, -2.4, 19, 20.2, -3.4, -2.9, "gold", name="clasp")
    m.box("arm_r", -7.9, -4.3, 12.2, 13.2, -2.0, 2.0, "gold", name="emitter_ring")
    m.box("arm_r", -6.6, -5.6, 9.8, 10.6, -1.0, 1.0, "glow", name="emitter")


def bastion():
    """A walking fortress: broad plated torso, stacked pauldrons, a glowing reactor and a tower shield planted at its side."""
    w = 2.0
    m.bone("root", None, (0, 0, 0))
    m.bone("body", "root", (0, 12, 0))
    m.bone("head", "body", (0, 22.5, 0))
    # head: a masked helm with a gold crest
    m.box("head", -3.5, 3.5, 23, 29, -3.9, 3.1, "mask", north="botface", eye=26.2, name="mask")
    m.box("head", -4.1, 4.1, 28.4, 30.4, -4.4, 3.7, "steel", name="helmet_top")
    m.box("head", -1.0, 1.0, 30.2, 34.2, -3.4, 3.6, "gold", name="crest")
    m.box("head", -4.5, 4.5, 22.8, 28.6, 0.9, 4.1, "steel_dark", name="helmet_back")
    m.box("head", -0.6, 0.6, 25.0, 28.4, -4.7, -3.9, "mask", name="nose_guard")
    for side in (m.Side(-1), m.Side(1)):
        side.box("head", -4.6, -3.6, 23, 28.6, -4.0, 0.9, "steel", name="cheek")
    # torso
    m.box("body", -7.6, 7.6, 14, 23, -3.9, 3.9, "steel", bands=4, name="torso")
    m.box("body", -6.2, 6.2, 14.8, 22, -4.7, -3.9, "steel", name="breastplate")
    m.box("body", -3.0, 3.0, 15.8, 20.8, -5.3, -4.7, "gold", name="reactor_ring")
    m.box("body", -2.0, 2.0, 16.8, 19.8, -5.6, -5.3, "reactor", name="reactor")
    m.box("body", -3.2, 3.2, 23, 24.8, -2.8, 2.8, "steel_dark", name="neck")
    m.box("body", -7.9, 7.9, 11.4, 14.2, -4.3, 4.3, "leather", name="belt")
    m.box("body", -1.7, 1.7, 11.6, 14, -4.8, -4.3, "gold", north="buckle", name="buckle")
    m.box("body", -4.6, 4.6, 5.6, 11.4, -4.1, -3.3, "trim", name="tabard")
    m.box("body", -5.2, 5.2, 12.2, 22.6, 3.9, 7.2, "steel_dark", name="backpack")
    for x in (-3.2, 0, 3.2):
        m.box("body", x - 0.5, x + 0.5, 13.5, 21.5, 7.2, 7.6, "gold", name="vent")
    for side in (m.Side(-1), m.Side(1)):
        side.box("body", -7.8, -1.2, 6.8, 11.6, -4.3, 4.3, "steel_dark", name="tasset")
        side.box("body", -3.6, -2.4, 22.4, 26.4, 4.2, 5.6, "steel", name="exhaust")
    # arms, each with a three-tier pauldron
    for side in (m.Side(-1), m.Side(1)):
        arm = "arm" + side.suf
        m.bone(arm, "body", ((6.4 + w) * side.k, 21.4, 0))
        side.box(arm, -13.0, -6.4, 20.6, 25.6, -4.0, 4.0, "steel", name="pauldron")
        side.box(arm, -13.6, -7.4, 24.6, 27.0, -3.5, 3.5, "trim", name="pauldron_trim")
        side.box(arm, -12.4, -8.2, 26.6, 28.4, -2.6, 2.6, "steel", name="pauldron_top")
        side.box(arm, -12.2, -6.8, 13.2, 20.8, -3.0, 3.0, "steel", bands=3, name="upper_arm")
        side.box(arm, -12.8, -6.2, 10.8, 13.8, -3.5, 3.5, "steel", name="elbow")
        side.box(arm, -12.4, -6.6, 4.4, 11.0, -3.2, 3.2, "steel", bands=3, name="forearm")
        side.box(arm, -12.9, -6.1, 5.4, 6.6, -3.5, 3.5, "gold", name="cuff")
        side.box(arm, -12.0, -7.0, 0.6, 4.8, -2.9, 2.9, "steel_dark", name="fist")
        side.box(arm, -12.2, -6.8, 0.2, 1.4, -3.1, 3.1, "gold", name="knuckles")
    for side in (m.Side(-1), m.Side(1)):
        leg = "leg" + side.suf
        m.bone(leg, "root", (3.4 * side.k, 12, 0))
        side.box(leg, -6.6, -0.6, 6.2, 12.4, -3.0, 3.0, "steel", bands=3, name="thigh")
        side.box(leg, -6.8, -0.4, 4.6, 7.6, -3.6, 3.1, "steel", name="knee")
        side.box(leg, -6.4, -0.8, 1.6, 5.0, -2.8, 2.8, "steel", bands=3, name="shin")
        side.box(leg, -7.0, -0.2, 0, 2.2, -4.6, 3.2, "steel_dark", name="boot")
        side.box(leg, -6.8, -0.4, 2.0, 2.8, -3.2, 3.0, "gold", name="boot_band")
    # the tower shield, strapped to the left forearm (it swings with the arm, never through it): 11 wide, 22 tall
    m.bone("shield", "arm_l", (9.7, 8, -4))
    m.box("shield", 7.0, 18.0, 4.0, 26.0, -7.0, -4.6, "steel", north="shield_face", name="shield")
    m.box("shield", 6.4, 18.6, 25.6, 26.6, -7.4, -4.4, "gold", name="shield_top")
    m.box("shield", 6.4, 7.2, 4.0, 26.0, -7.4, -4.4, "gold", name="shield_edge_l")
    m.box("shield", 17.8, 18.6, 4.0, 26.0, -7.4, -4.4, "gold", name="shield_edge_r")
    m.box("shield", 11.0, 14.0, 12.0, 17.0, -7.9, -7.0, "steel_dark", name="shield_boss")
    m.box("arm_l", 6.6, 9.4, 4.0, 8.0, -5.4, -2.6, "steel_dark", name="grip")


def interceptor():
    chassis(hood=True)
    m.box("body", -1.2, 1.2, 14, 19, -3.0, -2.6, "glow", name="core")
    m.box("body", -3.6, 3.6, 12.6, 21, 2.6, 4.6, "steel_dark", name="jetpack")
    for s in (m.Side(-1), m.Side(1)):
        s.box("body", -3.2, -1.6, 10.8, 12.6, 2.9, 4.3, "steel", down="glow", name="thruster")
        w = s.bone("bind_fin", "body", (-3.6, 19, 4), (0, 0, -30))
        s.box(w, -8.6, -3.6, 18.2, 19.4, 3.2, 4.4, "steel", name="fin")
        s.box("leg" + s.suf, -3.6, -1.0, -0.2, 0.2, -1.0, 1.0, "glow", name="boot_jet")
        s.box("arm" + s.suf, -8.3, -7.6, 14, 17, -0.8, 0.8, "glow", name="arm_light")


def arcanist():
    chassis(legs=False)
    for s in (m.Side(-1), m.Side(1)):  # legs stay as bones so the shared clips apply
        m.bone("leg" + s.suf, "root", (2.3 * s.k, 12, 0))
        s.box("arm" + s.suf, -8.4, -4.4, 12.4, 15.4, -2.3, 2.3, "cloth", name="sleeve")
    m.box("body", -4.9, 4.9, 12.6, 21.5, -2.9, 2.9, "cloth", name="robe_top")
    m.box("body", -5.2, 5.2, 7, 12.6, -3.2, 3.2, "cloth", name="robe_mid")
    m.box("body", -5.6, 5.6, 2.4, 7, -3.6, 3.6, "cloth", name="robe_low")
    m.box("body", -1.2, 1.2, 4, 12.4, -3.9, -3.5, "trim", name="stole")
    m.box("body", -2.4, 2.4, 19.2, 20, -3.2, -2.9, "chain", link=0, name="chain")
    for s in (m.Side(-1), m.Side(1)):
        s.box("body", -3.6, -2.4, 19, 20.4, -3.3, -2.9, "gold", name="clasp")
    m.box("body", -1.4, 1.4, 0.4, 2.4, -1.4, 1.4, "glow", name="hover_core")
    cape(15, 5.0)


def engineer():
    chassis()
    m.box("body", -3.4, 3.4, 8.4, 19.6, -3.1, -2.6, "tunic", name="apron")
    m.box("body", -1, 1, 15, 17, -3.4, -3.1, "glow", name="gear_emblem")
    for s in (m.Side(-1), m.Side(1)):
        s.box("body", -4.6, -2.6, 9.2, 11.4, -3.6, -2.9, "leather", north="pouch", name="pouch")
        a = s.bone("bind_servo", "body", (-3, 20, 3), (0, 0, 32))
        s.box(a, -3.8, -2.4, 19.6, 27.4, 2.6, 4.0, "gold", name="servo_arm")
        b = s.bone("bind_servo_tip", a, (-3.1, 27.2, 3.3), (0, 0, -70))
        s.box(b, -3.7, -2.5, 26.6, 32.2, 2.7, 3.9, "steel", name="servo_fore")
        s.box(b, -4.0, -2.2, 32.2, 33.4, 2.5, 4.1, "glow" if s.k > 0 else "steel_dark", name="servo_tool")
    m.box("body", -2.6, 2.6, 13, 20.6, 2.6, 4.2, "steel_dark", name="backpack")
    m.box("arm_r", -6.6, -5.6, 7.6, 10.6, -0.5, 0.5, "steel", name="wrench")
    m.box("arm_r", -7.1, -5.1, 6.6, 7.8, -0.6, 0.6, "gold", name="wrench_head")


def royal_guard():
    chassis(steel="black")
    m.box("body", -3.4, 3.4, 12.8, 20.6, -3.1, -2.6, "trim", name="tabard")
    m.box("body", -1.2, 1.2, 5, 12.4, -3.2, -2.8, "trim", name="tasset")
    m.box("head", -0.6, 0.6, 29.2, 31.4, -2.2, 2.6, "gold", name="crest")
    for s in (m.Side(-1), m.Side(1)):
        p = s.bone("bind_plate", "arm" + s.suf, (-8.4, 22.4, 0), (0, 0, 14))
        s.box(p, -10.2, -5.8, 21.4, 23.6, -3.0, 3.0, "trim", name="shoulder_plate")
    cape(19, 5.4, trimmed=True)
    m.box("arm_r", -6.6, -5.6, 1.5, 35, -3.1, -2.1, "black", name="halberd_pole")
    m.box("arm_r", -6.9, -5.3, 35, 38.5, -3.2, -2.0, "gold", name="halberd_spike")
    m.box("arm_r", -6.4, -5.8, 28.5, 34.5, -6.4, -3.1, "steel", name="halberd_blade")
    m.box("arm_r", -6.4, -5.8, 30.5, 32.5, -2.1, -0.6, "steel", name="halberd_hook")
    m.box("arm_r", -6.5, -5.7, 31, 32, -3.1, -2.1, "glow", name="halberd_gem")


BUILD = {"soldier": soldier, "bastion": bastion, "interceptor": interceptor, "arcanist": arcanist,
         "engineer": engineer, "royal_guard": royal_guard}

# ---------------------------------------------------------------------------------------------------- clips

CLIPS = {}


def clip(name, length, loop=False):
    CLIPS[name] = {"length": length, "loop": loop, "rot": {}, "pos": {}}

    def k(t, pos=None, spos=None, **bones):
        if spos is not None: CLIPS[name]["pos"].setdefault("shield", {})[round(t, 3)] = [float(a) for a in spos]
        for b, v in bones.items():
            v = (v, 0, 0) if isinstance(v, (int, float)) else v
            CLIPS[name]["rot"].setdefault(b, {})[round(t, 3)] = [float(a) for a in v]
        if pos is not None: CLIPS[name]["pos"].setdefault("root", {})[round(t, 3)] = [float(a) for a in pos]
    return k


def clips():
    k = clip("idle", 3.0, True)
    k(0, head=(0, 0, 0), body=0, arm_r=(0, 0, 3), arm_l=(0, 0, -3), cape=4)
    k(1.0, head=(0, 12, 0)); k(2.0, head=(0, -10, 0))
    k(1.5, body=-1.5, arm_r=(0, 0, 5), arm_l=(0, 0, -5), cape=7)
    k = clip("walk", 1.2, True)
    k(0, leg_l=-26, leg_r=26, arm_l=20, arm_r=-20, cape=9, pos=(0, 0, 0))
    k(0.3, pos=(0, 0.5, 0)); k(0.9, pos=(0, 0.5, 0))
    k(0.6, leg_l=26, leg_r=-26, arm_l=-20, arm_r=20, cape=13, pos=(0, 0, 0))
    k = clip("hover", 2.0, True)
    k(0, pos=(0, 0, 0), body=6, leg_l=10, leg_r=16, arm_l=(-6, 0, -12), arm_r=(-6, 0, 12), cape=22)
    k(1.0, pos=(0, 1.2, 0), body=4, leg_l=16, leg_r=10, cape=28)
    k = clip("alert", 0.7)
    k(0.2, head=(0, 28, 0)); k(0.45, head=(-4, 0, 0), body=(0, 12, 0), arm_r=(-20, 0, 6)); k(0.7, head=0, body=0, arm_r=0)
    k = clip("stagger", 0.5)
    k(0.12, body=(-18, 0, 0), head=(-10, 0, 0), arm_r=(20, 0, 20), arm_l=(20, 0, -20), pos=(0, 0, 1.2), cape=-4)
    k(0.5, body=0, head=0, arm_r=0, arm_l=0, pos=(0, 0, 0), cape=0)
    k = clip("shutdown", 2.0)
    k(0.6, head=(34, 0, 0), arm_r=(6, 0, 2), arm_l=(6, 0, -2))
    k(1.1, body=(14, 0, 0), leg_l=-22, leg_r=-22, pos=(0, -1.2, 0))
    k(2.0, head=(40, 0, 0), body=(30, 0, 0), leg_l=-50, leg_r=-50, arm_r=(-20, 0, 4), arm_l=(-20, 0, -4), pos=(0, -3.6, 1.5), cape=12)
    k = clip("salute", 1.8)
    k(0.5, arm_r=(10, 0, 4)); k(0.9, arm_l=(-62, -48, 0), body=(2, 0, 0)); k(1.2, head=(16, 0, 0)); k(1.5, head=0, arm_l=(-62, -48, 0))
    k(1.8, arm_l=0, arm_r=0, body=0)
    k = clip("activate", 2.5)
    k(0, head=(40, 0, 0), body=(30, 0, 0), leg_l=-50, leg_r=-50, arm_r=(-20, 0, 4), arm_l=(-20, 0, -4), pos=(0, -3.6, 1.5))
    k(0.9, head=(-6, 0, 0)); k(1.6, body=(4, 0, 0), leg_l=-10, leg_r=-10, pos=(0, -0.6, 0))
    k(2.1, arm_r=(0, 0, 14), arm_l=(0, 0, -14), body=(-4, 0, 0))
    k(2.5, head=0, body=0, leg_l=0, leg_r=0, arm_r=0, arm_l=0, pos=(0, 0, 0))
    k = clip("shoot", 1.2)
    k(0.4, arm_r=(-88, 0, 0), body=(0, 10, 0), head=(0, -6, 0)); k(0.65, arm_r=(-92, 0, 0))
    k(0.75, arm_r=(-104, 0, 0), body=(-5, 10, 0), pos=(0, 0, 0.6)); k(1.2, arm_r=0, body=0, head=0, pos=(0, 0, 0))
    k = clip("shield", 2.8)
    # 0-0.7 s guard up, 0.7-1.2 s charge behind the shield, 1.25 s slam, 1.5 s guard down, then an open, winded recovery
    k(0, arm_l=0, shield=0, body=0, arm_r=0, head=0, leg_l=0, leg_r=0, pos=(0, 0, 0))
    k(0.25, arm_l=(-42, 0, 0), shield=(42, 0, 0), body=(6, 0, 0), leg_l=-12, leg_r=10, pos=(0, -0.5, 0))
    k(0.7, arm_l=(-42, 0, 0), shield=(42, 0, 0), body=(14, 0, 0), arm_r=(-30, 0, -8), leg_l=-20, leg_r=16)
    k(0.95, leg_l=18, leg_r=-18)
    k(1.15, body=(12, 0, 0), arm_r=(-150, 0, -6), leg_l=-14, leg_r=12)
    k(1.25, body=(26, 0, 0), arm_r=(-30, 0, -6), pos=(0, -1.2, 0), leg_l=-24, leg_r=20)
    k(1.5, arm_l=(-42, 0, 0), shield=(42, 0, 0), body=(16, 0, 0), arm_r=(-20, 0, -6), pos=(0, -0.8, 0))
    k(1.9, arm_l=(-6, 0, -4), shield=(6, 0, 0), body=(18, 0, 0), head=(18, 0, 0), arm_r=(10, 0, 8), leg_l=-8, leg_r=6, pos=(0, -1.0, 0))
    k(2.4, body=(16, 0, 0), head=(14, 0, 0))
    k(2.8, arm_l=0, shield=0, body=0, arm_r=0, head=0, leg_l=0, leg_r=0, pos=(0, 0, 0))
    k = clip("dive", 2.2)
    k(0.35, body=(18, 0, 0), leg_l=-28, leg_r=-28, pos=(0, -1.0, 0))
    k(0.8, body=(-8, 0, 0), leg_l=10, leg_r=18, arm_r=(10, 0, 20), arm_l=(10, 0, -20), cape=6)
    k(1.2, body=(-4, 0, 0), arm_r=(-150, 0, -8), arm_l=(10, 0, -20))
    k(1.65, body=(62, 0, 0), arm_r=(-150, 0, -8), arm_l=(-150, 0, 8), leg_l=40, leg_r=40, cape=50)
    k(1.85, body=(30, 0, 0), arm_r=(-60, 0, 0), arm_l=(-60, 0, 0), leg_l=-30, leg_r=-30, pos=(0, -1.2, 0), cape=10)
    k(2.2, body=0, arm_r=0, arm_l=0, leg_l=0, leg_r=0, pos=(0, 0, 0), cape=0)
    k = clip("cast", 2.0)
    k(0.5, arm_r=(-40, 0, 56), arm_l=(-40, 0, -56), head=(-8, 0, 0), cape=10)
    k(1.1, arm_r=(-70, 30, 0), arm_l=(-70, -30, 0), body=(8, 0, 0), head=(6, 0, 0))
    k(1.2, arm_r=(-92, 6, 0), arm_l=(-92, -6, 0), body=(-6, 0, 0), cape=24)
    k(2.0, arm_r=0, arm_l=0, body=0, head=0, cape=0)
    k = clip("repair", 3.0)
    k(0.5, body=(24, 0, 0), head=(14, 0, 0)); k(0.8, arm_r=(-70, 0, -6), arm_l=(-50, 0, 6))
    for t in (1.1, 1.6, 2.1):
        k(t - 0.12, arm_r=(-62, 0, -6)); k(t, arm_r=(-80, 0, -6))
    k(2.4, body=(24, 0, 0), head=(14, 0, 0), arm_r=(-70, 0, -6), arm_l=(-50, 0, 6))
    k(3.0, body=0, head=0, arm_r=0, arm_l=0)
    k = clip("combo", 2.4)
    k(0.45, arm_r=(-150, 0, -10), body=(-8, -22, 0), cape=4)
    k(0.6, arm_r=(-40, 0, 6), body=(20, 26, 0), pos=(0, 0, -1.5), cape=18)
    k(1.15, arm_r=(-30, 0, 70), body=(4, 40, 0), pos=(0, 0, -1.5))
    k(1.3, arm_r=(-70, 40, 0), body=(6, -36, 0), pos=(0, 0, -2.2), cape=(14, 0, 10))
    k(1.6, arm_r=(-20, 0, 10), body=(10, -10, 0), leg_l=-20, leg_r=16)
    k(1.8, arm_r=(-86, 0, 0), body=(22, 0, 0), leg_l=-34, leg_r=22, pos=(0, -0.6, -4), cape=26)
    k(2.0, arm_r=(-86, 0, 0), body=(22, 0, 0), leg_l=-34, leg_r=22, pos=(0, -0.6, -4))
    k(2.4, arm_r=0, body=0, leg_l=0, leg_r=0, pos=(0, 0, 0), cape=0)
    out = {}
    for name, c in CLIPS.items():
        bones = {}
        for kind, tracks in (("rotation", c["rot"]), ("position", c["pos"])):
            for b, track in tracks.items():
                if c["loop"]:
                    track.setdefault(0.0, track[min(track)]); track[round(c["length"], 3)] = track[0.0]
                else:
                    track.setdefault(0.0, [0.0, 0.0, 0.0])
                bones.setdefault(b, {})[kind] = {("%.3f" % t).rstrip("0").rstrip(".") or "0": v for t, v in sorted(track.items())}
        out["animation.doombot." + name] = {"loop": c["loop"], "animation_length": c["length"], "bones": bones}
    with open(ROOT + "doctor_doom/doombot.animation.json", "w") as f:
        json.dump({"format_version": "1.8.0", "animations": out}, f, separators=(",", ":"))
        f.write("\n")


def main():
    import numpy as np
    from PIL import Image
    clips()
    shots = []
    for name in VARIANTS:
        m.BONES.clear(); m.CUBES.clear()
        BUILD[name]()
        image = m.texture()
        os.makedirs(ROOT + "textures/entity/doctor_doom", exist_ok=True)
        image.save(ROOT + "textures/entity/doctor_doom/doombot_%s.png" % name)
        m.GEO = ROOT + "doctor_doom/doombot_%s.geo.json" % name
        m.write_geo()
        print(name, len(m.CUBES), "cubes")
        if len(sys.argv) > 1: shots.append(m.render(np.array(image), -28, 9, 12, (300, 520), 17))
    if shots:
        sheet = Image.new("RGBA", (300 * len(shots), 520), (20, 30, 24, 255))
        for i, s in enumerate(shots): sheet.alpha_composite(s, (i * 300, 0))
        sheet.convert("RGB").save(sys.argv[1])


if __name__ == "__main__":
    main()
