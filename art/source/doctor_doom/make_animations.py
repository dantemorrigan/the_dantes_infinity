"""Doctor Doom's animation clips, written as poses and exported to Bedrock keyframes and the Blockbench timeline.

Values are Bedrock degrees, the same numbers the animation file stores. For this rig:
  arm / forearm rx < 0   swings forward and up (the elbow bends forward)
  arm_r rz > 0, arm_l rz < 0   lifts a hanging arm out to the side; a raised arm (rx past -90) spreads with the
                               opposite sign, and an arm held straight forward spreads with ry (arm_r ry < 0)
  body / head rx > 0   leans forward / looks down;  ry > 0 turns toward +X (the arm_l side)
  leg rx < 0 steps forward;  shin rx > 0 bends the knee
  cape rx > 0 billows back;  root position: y up, z < 0 forward
GeoAnimation eases between neighbouring keys, so keys are poses (extremes), never dense samples.

The cloak is driven from the `cape` track of each clip: `cape_mid` and `cape_low` repeat it late and smaller
(follow-through), the side wings spread with the billow, and every segment gets a wind flutter of its own phase.

Run from the repository root: python3 art/source/doctor_doom/make_animations.py [gif directory]
"""
import json
import math
import os
import sys
import uuid

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_model as model  # noqa: E402

SHORT = {"aR": "arm_r", "aL": "arm_l", "fR": "forearm_r", "fL": "forearm_l", "lR": "leg_r", "lL": "leg_l",
         "sR": "shin_r", "sL": "shin_l", "wR": "cape_wing_r", "wL": "cape_wing_l"}
CLIPS = {}
GROUPS = {}


class Clip:
    def __init__(self, name, length, loop=False, group="misc", wind=1.0, period=None):
        self.name, self.length, self.loop, self.group = name, length, loop, group
        self.wind = wind
        self.period = period or (length / max(1, round(length / 1.5)) if loop else 1.4)
        self.rot, self.pos = {}, {}
        CLIPS[name] = self
        GROUPS.setdefault(group, []).append(name)

    def k(self, t, pos=None, spin=None, **bones):
        """One pose: bone=(rx, ry, rz) or a single rx. `pos` moves the root, `spin` rotates it."""
        for short, v in bones.items():
            v = (v, 0, 0) if isinstance(v, (int, float)) else v
            self.rot.setdefault(SHORT.get(short, short), {})[round(t, 3)] = tuple(float(a) for a in v)
        if pos is not None: self.pos.setdefault("root", {})[round(t, 3)] = tuple(float(a) for a in pos)
        if spin is not None: self.rot.setdefault("root", {})[round(t, 3)] = tuple(float(a) for a in spin)
        return self

    def rest(self, t):
        """Every bone used so far returns to the bind pose."""
        for track in self.rot.values(): track.setdefault(round(t, 3), (0.0, 0.0, 0.0))
        for track in self.pos.values(): track.setdefault(round(t, 3), (0.0, 0.0, 0.0))
        return self


def ease(keys, t, length, loop):
    """A track's value at t with GeoAnimation's smoothstep between keys."""
    if not keys: return (0.0, 0.0, 0.0)
    if loop: t %= length
    t = min(max(t, 0.0), length)
    times = sorted(keys)
    if t <= times[0]: return keys[times[0]]
    for t0, t1 in zip(times, times[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0)
            f = f * f * (3 - 2 * f)
            return tuple(keys[t0][a] + (keys[t1][a] - keys[t0][a]) * f for a in range(3))
    return keys[times[-1]]


def finish(clip):
    """Closes every track at both ends and builds the cloak's secondary motion."""
    length = clip.length
    for track in list(clip.rot.values()) + list(clip.pos.values()):
        if clip.loop:
            first = track[min(track)]
            track.setdefault(0.0, first)
            track[round(length, 3)] = track[0.0]
        else:
            track.setdefault(0.0, (0.0, 0.0, 0.0))
            track.setdefault(round(length, 3), (0.0, 0.0, 0.0))
    cape = dict(clip.rot.get("cape", {0.0: (0.0, 0.0, 0.0), round(length, 3): (0.0, 0.0, 0.0)}))
    manual = {b: dict(clip.rot.get(b, {})) for b in ("cape_wing_r", "cape_wing_l")}

    def flutter(t, amp, phase):
        env = 1.0 if clip.loop else max(0.0, min(1.0, t / 0.3, (length - t) / 0.3))
        return amp * clip.wind * env * math.sin(2 * math.pi * t / clip.period + phase)

    def times(lag, phase):
        out = {0.0, round(length, 3)}
        for t in cape:
            t2 = t + lag
            if clip.loop: t2 %= length
            if 0 <= t2 <= length: out.add(round(t2, 3))
        n = -2
        while True:  # the flutter's crests and troughs
            t = (math.pi / 2 + n * math.pi - phase) * clip.period / (2 * math.pi)
            n += 1
            if t > length: break
            if t > 0.04 and t < length - 0.04 and all(abs(t - o) > 0.07 for o in out): out.add(round(t, 3))
        return sorted(out)

    def layer(name, lag, gain, amp, phase, side=0):
        track = {}
        for t in times(lag, phase):
            tt = 0.0 if (clip.loop and t == round(length, 3)) else t
            base = ease(cape, tt - lag if clip.loop else max(0.0, tt - lag), length, clip.loop)
            if side:  # wings: spread with the billow, flap against each other
                extra = ease(manual[name], tt, length, clip.loop) if manual[name] else (0, 0, 0)
                rx = 0.25 * base[0] + flutter(tt, amp * 0.6, phase) + extra[0]
                rz = side * (max(0.0, base[0]) * 0.22 + flutter(tt, amp, phase + 0.7) + 1.5 * clip.wind) + extra[2]
                track[t] = (rx, extra[1], rz)
            elif name == "cape":
                track[t] = (max(-6.0, base[0] + flutter(tt, amp, phase)), base[1], base[2] + flutter(tt, amp * 0.4, phase + 1.3))
            else:
                track[t] = (gain * base[0] + flutter(tt, amp, phase), 0.0, gain * base[2] + flutter(tt, amp * 0.5, phase + 1.3))
        clip.rot[name] = {t: tuple(round(v, 2) for v in val) for t, val in track.items()}

    layer("cape", 0.0, 1.0, 1.2, 0.0)
    layer("cape_mid", 0.12, 0.45, 2.6, -0.9)
    layer("cape_low", 0.26, 0.4, 4.2, -1.8)
    layer("cape_wing_r", 0.08, 0, 2.2, -0.5, side=1)
    layer("cape_wing_l", 0.08, 0, 2.2, -2.1, side=-1)


# ---------------------------------------------------------------------------------------------------- clips

def clips():
    # ------------------------------------------------------------------ idles
    c = Clip("idle", 3.0, True, "idle")
    c.k(0, body=0, head=0, aR=(2, 0, 5), aL=(2, 0, -5), fR=-10, fL=-10, cape=5)
    c.k(1.5, body=-2, head=1.5, aR=(0, 0, 7), aL=(0, 0, -7), fR=-13, fL=-13, cape=(9, 0, -1))

    c = Clip("idle_arms_crossed", 6.0, True, "idle", wind=0.8)
    c.k(0, body=-3, head=(4, 0, 0), aR=(-32, 0, -10), fR=(-88, 62, 0), aL=(-36, 0, 10), fL=(-96, -62, 0), cape=4)
    c.k(1.5, body=-5, head=(5, 8, 0), cape=7)
    c.k(3.0, body=-3, head=(4, 0, 0), aR=(-33, 0, -10), fR=(-90, 62, 0), aL=(-37, 0, 10), fL=(-98, -62, 0), cape=4)
    c.k(4.5, body=-5, head=(5, -8, 0), cape=7)

    c = Clip("idle_menace", 4.0, True, "idle")
    c.k(0, body=(2, -5, 0), head=(6, -10, 0), aR=(-52, 0, -8), fR=-96, aL=(4, 0, -9), fL=-14, cape=6)
    c.k(1.0, fR=-104, aR=(-55, 0, -8))
    c.k(2.0, body=(0, -7, 0), head=(3, -14, 2), aR=(-52, 0, -8), fR=-94, aL=(2, 0, -11), fL=-18, cape=9)
    c.k(3.0, fR=-104, aR=(-55, 0, -8))

    c = Clip("idle_survey", 5.0, True, "idle", wind=0.9)
    c.k(0, body=(0, 0, 0), head=(0, 0, 0), aR=(16, 0, -3), fR=(-22, -30, 0), aL=(16, 0, 3), fL=(-22, 30, 0), cape=5)
    c.k(1.1, body=(-2, 9, 0), head=(-3, 32, 0), cape=(7, 0, 3))
    c.k(2.3, body=(-2, 9, 0), head=(-1, 28, 0))
    c.k(3.5, body=(-2, -9, 0), head=(-3, -32, 0), cape=(7, 0, -3))
    c.k(4.5, body=(-1, -6, 0), head=(0, -22, 0))

    c = Clip("hover", 3.0, True, "idle", wind=2.2, period=1.0)
    c.k(0, pos=(0, 4.0, 0), body=6, head=-4, aR=(-8, 0, 16), aL=(-8, 0, -16), fR=-22, fL=-22,
        lR=8, sR=14, lL=-6, sL=24, cape=24)
    c.k(1.5, pos=(0, 5.6, 0), body=4, head=-2, aR=(-5, 0, 20), aL=(-5, 0, -20), fR=-28, fL=-28,
        lR=11, sR=10, lL=-9, sL=30, cape=30)

    c = Clip("walk", 1.2, True, "idle", wind=1.3, period=0.6)
    c.k(0, lL=-24, sL=5, lR=24, sR=10, aL=(14, 0, -3), aR=(-14, 0, 3), fL=-12, fR=-20, body=(3, 4, 0), cape=11,
        pos=(0, 0, 0))
    c.k(0.3, sR=40, pos=(0, 0.6, 0))
    c.k(0.6, lL=24, sL=10, lR=-24, sR=5, aL=(-14, 0, -3), aR=(14, 0, 3), fL=-20, fR=-12, body=(3, -4, 0), cape=14,
        pos=(0, 0, 0))
    c.k(0.9, sL=40, pos=(0, 0.6, 0))

    # ------------------------------------------------------------------ basic attacks
    c = Clip("gauntlet_punch", 0.9, group="basic")
    c.k(0.22, body=(0, -18, 0), aR=(20, 0, 10), fR=-112, aL=(-38, 0, -6), fL=-100, cape=(4, 0, 4))
    c.k(0.38, body=(8, 22, 0), aR=(-88, 0, -6), fR=-6, aL=(-30, 0, -10), fL=-105, pos=(0, 0, -2.5),
        lL=-14, lR=12, sR=10, cape=(16, 0, -6))
    c.k(0.52, body=(8, 22, 0), aR=(-88, 0, -6), fR=-6, pos=(0, 0, -2.5), lL=-14, lR=12, sR=10)
    c.rest(0.9)

    c = Clip("backhand", 1.0, group="basic")
    c.k(0.25, body=(4, -28, 0), aL=(-75, -40, 0), fL=-62, aR=(6, 0, 10), fR=-30, head=(0, 14, 0), cape=(5, 0, -6))
    c.k(0.42, body=(0, 30, 0), aL=(-82, 55, 0), fL=-5, head=(0, -10, 0), pos=(0, 0, -1.2), cape=(14, 0, 10))
    c.k(0.56, body=(0, 34, 0), aL=(-80, 62, 0), fL=-8, pos=(0, 0, -1.2))
    c.rest(1.0)

    c = Clip("boot_kick", 1.1, group="basic")
    c.k(0.3, lR=-50, sR=72, body=-10, aR=(10, 0, 20), aL=(10, 0, -20), fR=-30, fL=-30, cape=3)
    c.k(0.45, lR=-86, sR=4, body=-18, pos=(0, 0, -1.0), cape=(18, 0, 0))
    c.k(0.58, lR=-86, sR=4, body=-18, pos=(0, 0, -1.0))
    c.rest(1.1)

    c = Clip("armored_combo", 1.95, group="basic")  # blows land on ticks 12, 17 and 20
    c.k(0.4, body=(0, -14, 0), aR=(10, 0, 8), fR=-115, aL=(-30, 0, -6), fL=-100)
    c.k(0.6, body=(5, 18, 0), aR=(-85, 0, -5), fR=-5, aL=(-35, 0, -6), fL=-105, pos=(0, 0, -1), cape=(10, 0, -4))
    c.k(0.72, body=(0, 22, 0), aR=(-40, 0, 4), fR=-100, aL=(15, 0, -10), fL=-115, pos=(0, 0, -1))
    c.k(0.85, body=(-10, -22, 0), aL=(-88, 0, 5), fL=-5, aR=(-165, 0, -8), fR=-40, pos=(0, 0.5, -2), head=-8,
        cape=(12, 0, 6))
    c.k(1.0, body=(26, 10, 0), aR=(-52, 0, -8), fR=-10, aL=(-20, 0, -14), fL=-70, pos=(0, -1.2, -3.2),
        lL=-22, sL=30, lR=6, sR=22, head=8, cape=(26, 0, 0))
    c.k(1.25, body=(26, 10, 0), aR=(-52, 0, -8), fR=-10, aL=(-20, 0, -14), fL=-70, pos=(0, -1.2, -3.2),
        lL=-22, sL=30, lR=6, sR=22, head=8)
    c.rest(1.95)

    # ------------------------------------------------------------------ sweeping attacks
    c = Clip("cleave", 1.5, group="sweep")
    c.k(0.45, body=(0, -45, 0), aR=(-20, 0, 85), fR=-20, aL=(-25, 0, -8), fL=-80, head=(0, 30, 0),
        lL=-10, lR=8, cape=(6, 0, 8))
    c.k(0.58, body=(4, 5, 0), aR=(-60, 0, 70), fR=-5, pos=(0, 0, -1.2))
    c.k(0.70, body=(6, 50, 0), aR=(-90, 25, 0), fR=0, head=(0, -25, 0), pos=(0, 0, -2.2), lL=-16, lR=14, sR=12,
        cape=(18, 0, -14))
    c.k(0.90, body=(8, 62, 0), aR=(-85, 45, 0), fR=-25, pos=(0, 0, -2.2), aL=(10, 0, -25), fL=-40)
    c.k(1.05, cape=(10, 0, 10))
    c.rest(1.5)

    c = Clip("overhead_smash", 1.6, group="sweep")
    c.k(0.5, body=-16, head=-15, aR=(-170, 0, -10), aL=(-170, 0, 10), fR=-35, fL=-35, pos=(0, 1.0, 0), cape=2)
    c.k(0.72, body=38, head=4, aR=(-70, 0, -12), aL=(-70, 0, 12), fR=-5, fL=-5, pos=(0, -1.6, -2.2),
        lR=-35, sR=50, lL=-35, sL=50, cape=34)
    c.k(0.95, body=38, head=4, aR=(-70, 0, -12), aL=(-70, 0, 12), fR=-5, fL=-5, pos=(0, -1.6, -2.2),
        lR=-35, sR=50, lL=-35, sL=50, cape=20)
    c.rest(1.6)

    c = Clip("cape_sweep", 1.4, group="sweep", wind=1.6)
    c.k(0.3, spin=(0, -35, 0), body=(6, -15, 0), aR=(-10, 0, 30), aL=(-30, 0, 20), fR=-40, fL=-70, cape=(8, 0, 6))
    c.k(0.65, spin=(0, 180, 0), body=(4, 10, 0), aR=(-10, 0, 80), aL=(-10, 0, -80), fR=-10, fL=-10,
        cape=(55, 0, -10), wR=(0, 0, 20), wL=(0, 0, -20), pos=(0, 0.6, 0))
    c.k(1.0, spin=(0, 360, 0), body=(2, 12, 0), aR=(-10, 0, 60), aL=(-10, 0, -60), fR=-20, fL=-20,
        cape=(40, 0, 12), wR=(0, 0, 14), wL=(0, 0, -14))
    c.k(1.4, spin=(0, 360, 0), body=0, aR=0, aL=0, fR=0, fL=0, cape=0, wR=0, wL=0, pos=(0, 0, 0))

    c = Clip("jet_slam", 2.95, group="sweep", wind=1.8)  # jets on tick 7, apex on 23, ground wave on 28
    c.k(0.35, pos=(0, -1.5, 0), body=20, lR=-32, sR=48, lL=-32, sL=48, aR=(25, 0, 14), aL=(25, 0, -14), fR=-30,
        fL=-30, cape=10)
    c.k(0.9, pos=(0, 14, 0), body=-10, head=-10, lR=15, sR=25, lL=-10, sL=40, aR=(25, 0, 25), aL=(25, 0, -25),
        fR=-20, fL=-20, cape=-4)
    c.k(1.15, pos=(0, 16, -1), body=-15, head=-5, aR=(-170, 0, -10), fR=-45, aL=(10, 0, -30), fL=-40,
        lR=10, sR=35, lL=-20, sL=55, cape=18)
    c.k(1.4, pos=(0, -1.6, -5), body=35, head=10, aR=(-60, 0, -10), fR=-10, aL=(20, 0, -34), fL=-30,
        lR=-35, sR=50, lL=-35, sL=50, cape=52)
    c.k(1.75, pos=(0, -1.6, -5), body=35, head=10, aR=(-60, 0, -10), fR=-10, aL=(20, 0, -34), fL=-30,
        lR=-35, sR=50, lL=-35, sL=50, cape=14)
    c.rest(2.95)

    # ------------------------------------------------------------------ magic
    c = Clip("gauntlet_bolt", 1.9, group="magic")  # the palm fires at 0.9 s; the Latveria scene relies on it
    c.k(0.35, body=(4, -14, 0), aR=(15, 0, 12), fR=-95, aL=(4, 0, -8), fL=-20, head=(2, 8, 0), cape=3)
    c.k(0.70, body=(0, -6, 0), aR=(-50, 0, 6), fR=-60)
    c.k(0.90, body=(-4, 14, 0), aR=(-92, 0, -2), fR=-4, aL=(12, 0, -14), fL=-25, head=(0, -6, 0), cape=(19, 0, -3))
    c.k(1.00, aR=(-99, 0, -2), fR=-14, pos=(0, 0, 0.7))
    c.k(1.25, body=(-3, 12, 0), aR=(-92, 0, -2), fR=-4, pos=(0, 0, 0), cape=(12, 0, 2))
    c.rest(1.9)

    c = Clip("sorcery_bolt", 2.75, group="magic", wind=1.3)  # runes on tick 12, seal on 18, release on 22
    c.k(0.3, body=(0, 10, 0), head=5, aL=(-70, 0, -40), fL=(-70, -20, 0), aR=(-30, 0, 10), fR=-80, cape=5)
    c.k(0.6, aL=(-120, 0, 20), fL=(-40, 0, 0), head=-4, body=(2, 6, 0))
    c.k(0.9, body=8, head=8, aL=(-45, 0, 18), fL=(-95, -30, 0), aR=(-45, 0, -18), fR=(-95, 30, 0), cape=2)
    c.k(1.1, body=-6, head=-4, aL=(-90, 0, 8), fL=-5, aR=(-90, 0, -8), fR=-5, pos=(0, 0, 0.6), cape=26)
    c.k(1.5, body=-5, aL=(-90, 0, 8), fL=-5, aR=(-90, 0, -8), fR=-5, pos=(0, 0, 0), cape=14)
    c.k(2.0, body=0, head=0, aL=(-30, 0, -6), fL=-40, aR=(-30, 0, 6), fR=-40, cape=6)
    c.rest(2.75)

    c = Clip("hex_barrage", 2.4, group="magic", wind=1.2)
    c.k(0.2, body=(0, -14, 0), aR=(10, 0, 10), fR=-110, aL=(-20, 0, -8), fL=-80)
    c.k(0.38, body=(0, 16, 0), aR=(-90, 0, -4), fR=-8, cape=(10, 0, -4))
    c.k(0.7, body=(0, 14, 0), aL=(10, 0, -10), fL=-110, aR=(-60, 0, 0), fR=-70)
    c.k(0.88, body=(0, -16, 0), aL=(-90, 0, 4), fL=-8, cape=(10, 0, 4))
    c.k(1.2, body=(0, -14, 0), aR=(10, 0, 10), fR=-110, aL=(-60, 0, 0), fL=-70)
    c.k(1.38, body=(0, 16, 0), aR=(-90, 0, -4), fR=-8, cape=(10, 0, -4))
    c.k(1.7, body=(10, 0, 0), aR=(-20, 0, 6), fR=-120, aL=(-20, 0, -6), fL=-120, head=8, cape=2)
    c.k(1.9, body=(-8, 0, 0), aR=(-92, 0, -10), fR=-4, aL=(-92, 0, 10), fL=-4, head=-4, cape=22)
    c.k(2.05, body=(-8, 0, 0), aR=(-92, 0, -10), fR=-4, aL=(-92, 0, 10), fL=-4)
    c.rest(2.4)

    c = Clip("arcane_nova", 3.0, group="magic", wind=1.5)
    c.k(0.6, body=18, head=14, aR=(-40, 0, -22), fR=(-110, 30, 0), aL=(-40, 0, 22), fL=(-110, -30, 0),
        pos=(0, -1.2, 0), lR=-26, sR=38, lL=-26, sL=38, cape=2)
    c.k(1.2, body=24, head=18, aR=(-44, 0, -24), fR=(-116, 30, 0), aL=(-44, 0, 24), fL=(-116, -30, 0),
        pos=(0, -1.6, 0), lR=-32, sR=46, lL=-32, sL=46, cape=0)
    c.k(1.45, body=-16, head=-22, aR=(-35, 0, 78), fR=-8, aL=(-35, 0, -78), fL=-8, pos=(0, 2.5, 0),
        lR=6, sR=10, lL=-6, sL=18, cape=52, wR=(0, 0, 16), wL=(0, 0, -16))
    c.k(2.2, body=-14, head=-20, aR=(-38, 0, 74), fR=-10, aL=(-38, 0, -74), fL=-10, pos=(0, 3.0, 0),
        lR=6, sR=10, lL=-6, sL=18, cape=34, wR=(0, 0, 10), wL=(0, 0, -10))
    c.rest(3.0)

    c = Clip("lightning_call", 2.6, group="magic", wind=1.4)
    c.k(0.6, body=-8, head=-22, aR=(-172, 0, -6), fR=-6, aL=(6, 0, -14), fL=-30, cape=8)
    c.k(1.3, body=-10, head=-24, aR=(-176, 0, -10), fR=-10, cape=14)
    c.k(1.55, body=24, head=6, aR=(-62, 0, -6), fR=-4, pos=(0, 0, -1.5), lL=-16, sL=14, lR=10, sR=14,
        cape=40)
    c.k(1.9, body=24, head=6, aR=(-62, 0, -6), fR=-4, pos=(0, 0, -1.5), lL=-16, sL=14, lR=10, sR=14, cape=16)
    c.rest(2.6)

    # The ascension (God Emperor Doom): he lifts his arms, takes the power, floats with arms spread over his hall,
    # closes his fist as the multiverse breaks, and holds the pose of a god while everything turns white.
    c = Clip("ascension", 24.0, group="magic", wind=2.2, period=1.6)
    c.k(1.0, body=-6, head=-14, aR=(-40, 0, 40), aL=(-40, 0, -40), fR=-20, fL=-20, cape=12)
    c.k(2.6, body=-14, head=-30, aR=(-160, 0, 20), aL=(-160, 0, -20), fR=-5, fL=-5, cape=30)
    c.k(3.2, body=-4, head=-6, aR=(-20, 0, 95), aL=(-20, 0, -95), fR=0, fL=0, cape=46, wR=(0, 0, 30), wL=(0, 0, -30))
    for t in (5.0, 8.0, 11.0, 14.0, 17.0):
        j = 1 if int(t) % 2 else -1
        c.k(t, body=-4 + j, head=-6 + j * 2, aR=(-20 + j * 3, 0, 92), aL=(-20 - j * 3, 0, -92), cape=34 + j * 6,
            wR=(0, 0, 26 + j * 4), wL=(0, 0, -26 - j * 4), lL=6, lR=-6, sL=10, sR=10)
    c.k(18.6, body=-10, head=-18, aR=(-150, 0, 10), fR=-60, aL=(-20, 0, -90), cape=40)
    c.k(19.2, body=8, head=4, aR=(-95, 0, 0), fR=-10, aL=(-30, 0, -60), cape=52)
    c.k(22.0, body=2, head=-2, aR=(-30, 0, 70), aL=(-30, 0, -70), fR=-10, fL=-10, cape=30)
    c.k(24.0, body=0, head=0, aR=(-20, 0, 60), aL=(-20, 0, -60), cape=24)

    # Power siphon: the gauntlet hand reaches for the challenger's Infinity Gauntlet, the other hand braces the wrist,
    # and the whole body strains against the pull until it is released or broken.
    c = Clip("infinity_siphon", 5.0, group="magic", wind=1.6)
    brace = dict(lL=-14, sL=10, lR=12, sR=6)
    c.k(0.6, body=-6, head=-4, aR=(-92, 8, 0), fR=-4, aL=(-62, -34, 0), fL=-40, cape=10, pos=(0, -0.4, 0), **brace)
    for i, t in enumerate([1.0, 1.4, 1.8, 2.2, 2.6, 3.0, 3.4, 3.8, 4.2]):
        j = 1 if i % 2 else -1
        c.k(t, body=-7 + j * 1.5, head=-5 - j * 2, aR=(-94 + j * 3, 8 + j * 2, j * 2), fR=-4 - j * 2,
            aL=(-62 + j * 2, -34, 0), fL=-40, cape=12 + j * 3, pos=(0, -0.5 + j * 0.1, 0), **brace)
    c.k(4.5, body=10, head=4, aR=(-70, 0, 0), fR=-30, aL=(-20, 0, -10), fL=-10, cape=24, pos=(0, -0.2, 0))
    c.rest(5.0)

    c = Clip("seven_suns", 5.95, group="magic", wind=2.0, period=1.1)
    hang = dict(lR=6, sR=14, lL=-8, sL=24)
    c.k(1.6, body=-8, head=-12, aR=(-100, -30, 0), aL=(-100, 30, 0), fR=-25, fL=-25, pos=(0, 3, 0), cape=18, **hang)
    c.k(1.8, body=-12, head=-20, aR=(-150, 0, -25), aL=(-150, 0, 25), fR=-10, fL=-10, pos=(0, 5, 0), cape=28, **hang)
    c.k(3.0, body=-13, head=-22, aR=(-155, 0, -30), aL=(-155, 0, 30), fR=-6, fL=-6, pos=(0, 5.6, 0), cape=34, **hang)
    c.k(4.2, body=-12, head=-20, aR=(-150, 0, -25), aL=(-150, 0, 25), fR=-10, fL=-10, pos=(0, 5, 0), cape=28, **hang)
    c.k(4.6, body=18, head=10, aR=(-60, 0, 10), aL=(-60, 0, -10), fR=-5, fL=-5, pos=(0, 2, 0), cape=46, **hang)
    c.rest(5.95)

    c = Clip("rift_step", 1.9, group="magic", wind=1.4)  # rift line on tick 7, crossing on 14, collapse on 21
    c.k(0.2, body=(0, -20, 0), aR=(-140, 0, -30), fR=-30, aL=(-10, 0, -10), fL=-40, head=(-8, 10, 0), cape=4)
    c.k(0.35, body=(10, 25, 0), aR=(-40, 0, -30), fR=-10, head=(6, -10, 0), cape=(16, 0, -8))
    c.k(0.55, body=(12, 10, 0), lL=-35, sL=20, lR=14, sR=10, pos=(0, 0, -3), aR=(-25, 0, -10), fR=-50, cape=30)
    c.k(0.7, body=20, pos=(0, -0.8, -6), aR=(-20, 0, -10), aL=(-20, 0, 10), fR=-70, fL=-70, lL=14, sL=12,
        lR=-30, sR=24, head=10, cape=38)
    c.k(1.05, body=8, pos=(0, 0, -6), aR=(-10, 0, 8), aL=(-10, 0, -8), fR=-30, fL=-30, lL=0, sL=0, lR=0, sR=0,
        head=0, cape=16)
    c.rest(1.9)

    c = Clip("ovoid_echo", 3.65, group="magic", wind=1.3)  # echoes on ticks 18 and 30, flash on 34
    c.k(0.7, body=10, head=22, aR=(-95, 0, -22), fR=(-125, 20, 0), aL=(-95, 0, 22), fL=(-125, -20, 0), cape=2)
    c.k(1.5, body=12, head=26, aR=(-97, 0, -22), fR=(-128, 20, 0), aL=(-97, 0, 22), fL=(-128, -20, 0), cape=4)
    c.k(1.7, body=-14, head=-25, aR=(-60, 0, 70), fR=-10, aL=(-60, 0, -70), fL=-10, pos=(0, 1.5, 0), cape=44)
    c.k(2.5, body=-12, head=-22, aR=(-58, 0, 66), fR=-12, aL=(-58, 0, -66), fL=-12, pos=(0, 1.8, 0), cape=26)
    c.rest(3.65)

    c = Clip("time_platform", 4.0, group="magic", wind=1.2)  # floor marks on tick 16, platform live on 32, reset on 52
    c.k(0.8, body=30, head=6, aR=(-40, 0, 5), fR=-20, aL=(10, 0, -16), fL=-30, pos=(0, -2.4, -0.5),
        lR=-48, sR=66, lL=14, sL=58, cape=22)
    c.k(1.4, body=32, head=8, aR=(-42, 0, 5), fR=-16, aL=(10, 0, -16), fL=-30, pos=(0, -2.4, -0.5),
        lR=-48, sR=66, lL=14, sL=58, cape=12)
    c.k(1.9, body=-5, head=-8, aR=(-25, 0, 40), aL=(-25, 0, -40), fR=-25, fL=-25, pos=(0, 4, 0),
        lR=0, sR=0, lL=0, sL=0, cape=2)
    c.k(2.6, body=-4, head=-6, aR=(-22, 0, 36), aL=(-22, 0, -36), fR=-28, fL=-28, pos=(0, 4.5, 0), cape=10)
    c.k(3.3, body=4, head=4, aR=(-8, 0, 14), aL=(-8, 0, -14), fR=-20, fL=-20, pos=(0, 0.6, 0), cape=24)
    c.rest(4.0)

    c = Clip("latverian_edict", 6.3, group="magic", wind=1.2)  # zone on tick 10, seal on 46, column on 50, reset on 80
    c.k(0.5, head=-6, aR=(-95, 0, -5), fR=-5, aL=(-40, 0, 20), fL=(-110, -35, 0), cape=6)
    c.k(1.2, body=(0, -25, 0), head=(-4, -20, 0), aR=(-95, -45, 0), fR=-8, cape=(8, 0, 6))
    c.k(1.9, body=(0, 25, 0), head=(-4, 20, 0), aR=(-95, 35, 0), fR=-8, cape=(8, 0, -6))
    c.k(2.3, body=8, head=10, aR=(-45, 0, -18), fR=(-100, 30, 0), aL=(-45, 0, 18), fL=(-100, -30, 0), cape=4)
    c.k(2.5, body=-10, head=-18, aR=(-160, 0, -25), aL=(-160, 0, 25), fR=-15, fL=-15, pos=(0, 3, 0),
        lR=6, sR=12, lL=-8, sL=22, cape=34)
    c.k(4.0, body=-11, head=-20, aR=(-163, 0, -28), aL=(-163, 0, 28), fR=-12, fL=-12, pos=(0, 3.6, 0),
        lR=6, sR=12, lL=-8, sL=22, cape=24)
    c.k(4.8, body=4, head=2, aR=(-30, 0, 16), aL=(-30, 0, -16), fR=-20, fL=-20, pos=(0, 0.8, 0), lR=0, sR=0,
        lL=0, sL=0, cape=20)
    c.rest(6.3)

    c = Clip("iron_bastion", 4.35, group="magic", wind=0.8)  # shield up on tick 12, breaks from tick 61
    guard = dict(aR=(-75, 0, -28), fR=(-95, 25, 0), aL=(-75, 0, 28), fL=(-95, -25, 0), lL=-18, sL=10, lR=16, sR=14)
    c.k(0.6, body=10, head=8, pos=(0, -0.6, 0), cape=4, **guard)
    c.k(1.8, body=14, head=10, pos=(0, -0.8, 0), cape=2, **guard)
    c.k(3.05, body=10, head=8, pos=(0, -0.6, 0), cape=4, **guard)
    c.k(3.35, body=-8, head=-6, aR=(-50, 0, 60), aL=(-50, 0, -60), fR=-20, fL=-20, pos=(0, 0.4, 0), lL=-6, lR=6,
        sL=0, sR=0, cape=30)
    c.k(3.7, body=-6, head=-4, aR=(-46, 0, 54), aL=(-46, 0, -54), fR=-22, fL=-22, pos=(0, 0.2, 0), cape=16)
    c.rest(4.35)

    c = Clip("doombot_drop", 2.65, group="magic")
    c.k(0.6, body=-6, head=-20, aL=(-165, 0, 12), fL=-10, aR=(5, 0, 10), fR=-25, cape=8)
    c.k(1.1, body=-7, head=-22, aL=(-168, 0, 14), fL=-6)
    c.k(1.4, body=10, head=6, aL=(-75, 0, 5), fL=0, cape=(20, 0, 4))
    c.k(2.0, body=10, head=6, aL=(-75, 0, 5), fL=0, aR=(5, 0, 10), fR=-25, cape=10)
    c.rest(2.65)

    c = Clip("phase_shift", 2.1, group="magic", wind=1.6)  # arcs on tick 5, the shift on 18, shards on 33
    c.k(0.25, body=8, head=10, aR=(-30, 0, 20), aL=(-30, 0, -20), fR=-50, fL=-50, spin=(0, 0, 0), cape=10)
    c.k(0.6, body=14, head=18, aR=(-50, 0, -30), fR=(-80, 40, 0), aL=(-50, 0, 30), fL=(-80, -40, 0),
        spin=(0, 0, 0), pos=(0, -0.6, 0), cape=-4, wR=(0, 30, -10), wL=(0, -30, 10))
    c.k(0.9, spin=(0, 150, 0), pos=(0, -1.0, 0), cape=24, wR=(0, 20, 4), wL=(0, -20, -4))
    c.k(1.65, spin=(0, 360, 0), body=-8, head=-8, aR=(-40, 0, 62), fR=-12, aL=(-40, 0, -62), fL=-12,
        pos=(0, 0.6, 0), cape=34, wR=(0, 0, 12), wL=(0, 0, -12))
    c.k(2.1, spin=(0, 360, 0), body=0, head=0, aR=0, fR=0, aL=0, fL=0, cape=0, wR=0, wL=0, pos=(0, 0, 0))
    # The decoy gambit is the same spin: he vanishes in it and two Doombots in his likeness are left behind.
    d = Clip("decoy_gambit", c.length, group="magic", wind=c.wind)
    d.rot = {b: dict(keys) for b, keys in c.rot.items()}
    d.pos = {b: dict(keys) for b, keys in c.pos.items()}

    # ------------------------------------------------------------------ reactions and scenes
    c = Clip("block", 1.2, group="react", wind=0.8)
    c.k(0.2, body=(6, 12, 0), head=6, aR=(-70, 0, -30), fR=(-100, 20, 0), aL=(-20, 0, -8), fL=-60, lL=-10, lR=10,
        cape=6)
    c.k(0.8, body=(8, 12, 0), head=6, aR=(-72, 0, -30), fR=(-102, 20, 0), aL=(-20, 0, -8), fL=-60, lL=-10, lR=10)
    c.rest(1.2)

    c = Clip("stagger", 1.0, group="react")
    c.k(0.15, body=(-16, 8, 0), head=(-12, 0, 6), aR=(20, 0, 25), aL=(15, 0, -30), fR=-20, fL=-35,
        pos=(0, 0, 1.5), lR=-14, sR=10, cape=-5)
    c.k(0.45, body=(6, 0, 0), head=(6, 0, 0), aR=(-10, 0, 10), aL=(-10, 0, -10), pos=(0, 0, 0.8), cape=14)
    c.rest(1.0)

    c = Clip("stagger_heavy", 1.6, group="react")
    c.k(0.2, body=(-22, -10, 0), head=(-16, 0, -8), aR=(30, 0, 35), aL=(25, 0, -40), fR=-25, fL=-30,
        pos=(0, 0, 2.5), lL=-18, sL=12, cape=-5)
    c.k(0.6, body=(26, 0, 0), head=(20, 0, 0), aR=(-40, 0, 6), fR=-30, aL=(8, 0, -14), fL=-40,
        pos=(0, -3.4, 1.5), lR=-62, sR=74, lL=16, sL=78, cape=26)
    c.k(1.0, body=(22, 0, 0), head=(4, 0, 0), aR=(-42, 0, 6), fR=-28, aL=(8, 0, -14), fL=-40,
        pos=(0, -3.4, 1.5), lR=-62, sR=74, lL=16, sL=78, cape=12)
    c.rest(1.6)

    c = Clip("taunt", 2.4, group="react")
    c.k(0.5, body=(-4, -10, 0), head=(-8, 0, 0), aL=(-80, 20, 0), fL=-30, aR=(10, 0, 16), fR=(-70, 40, 0), cape=6)
    c.k(0.9, aL=(-84, 35, 0), fL=-12, head=(-10, -6, 0))
    c.k(1.3, body=(2, -6, 0), head=(4, 0, 0), aL=(-45, 0, -5), fL=-4, cape=(10, 0, 3))
    c.k(1.9, body=(2, -6, 0), head=(4, 0, 0), aL=(-45, 0, -5), fL=-4, aR=(10, 0, 16), fR=(-70, 40, 0))
    c.rest(2.4)

    c = Clip("intro", 5.0, group="react", wind=1.4)  # the Latveria scene plays it forward to arrive, backward to leave
    c.k(0, pos=(0, -6, 0), head=(20, 12, 0), cape=32, aR=(4, 0, 12), aL=(4, 0, -12), fR=-20, fL=-20)
    c.k(1.3, pos=(0, 2, 0), aR=(0, 0, 18), aL=(0, 0, -18))
    c.k(1.6, cape=42)
    c.k(2.9, pos=(0, 0, 0), aR=(0, 0, 6), aL=(0, 0, -6), fR=-12, fL=-12)
    c.k(3.0, head=(12, 0, 0))
    c.k(4.1, head=(-8, 0, 0))
    c.rest(5.0)

    c = Clip("defeat", 3.6, group="react", wind=0.7)
    c.k(0.5, body=(-12, 6, 0), head=(-10, 0, 5), aR=(15, 0, 20), aL=(10, 0, -24), fR=-20, fL=-25, pos=(0, 0, 1),
        cape=-3)
    c.k(1.5, body=25, head=30, aR=(10, 0, 6), aL=(10, 0, -6), fR=-10, fL=-10, pos=(0, -6.6, 0),
        lR=-8, sR=96, lL=-8, sL=96, cape=18)
    c.k(2.6, body=38, head=38, aR=(-35, 0, 4), fR=-12, aL=(14, 0, -8), fL=-6, pos=(0, -6.6, 0),
        lR=-8, sR=96, lL=-8, sL=96, cape=8)
    c.k(3.6, body=40, head=40, aR=(-36, 0, 4), fR=-12, aL=(14, 0, -8), fL=-6, pos=(0, -6.6, 0),
        lR=-8, sR=96, lL=-8, sL=96, cape=6)
    for track in list(c.rot.values()) + list(c.pos.values()):  # he stays down
        track[3.6] = track[max(track)]


# ---------------------------------------------------------------------------------------------------- output

def num(v):
    v = round(v, 2)
    return int(v) if v == int(v) else v


def tkey(t):
    return ("%.3f" % t).rstrip("0").rstrip(".") or "0"


def write_json():
    out = {}
    for c in CLIPS.values():
        bones = {}
        for b in model.BONES:
            entry = {}
            if b in c.rot: entry["rotation"] = {tkey(t): [num(v) for v in c.rot[b][t]] for t in sorted(c.rot[b])}
            if b in c.pos: entry["position"] = {tkey(t): [num(v) for v in c.pos[b][t]] for t in sorted(c.pos[b])}
            if entry: bones[b] = entry
        out["animation.doctor_doom." + c.name] = {"loop": c.loop, "animation_length": c.length, "bones": bones}
    with open(model.ANIM, "w") as f:
        json.dump({"format_version": "1.8.0", "animations": out}, f, separators=(",", ":"))
        f.write("\n")


def write_bbmodel():
    with open(model.BBMODEL) as f:
        bb = json.load(f)
    ids = {}

    def collect(node):
        if isinstance(node, dict):
            ids[node["name"]] = node["uuid"]
            for child in node["children"]: collect(child)

    for node in bb["outliner"]: collect(node)
    space = uuid.UUID("6f1d0c1e-5b0a-4a57-9f0e-d00dd00dd00d")
    anims = []
    for c in CLIPS.values():
        animators = {}
        for b in model.BONES:
            frames = []
            for channel, tracks, sign in (("rotation", c.rot, (-1, -1, 1)), ("position", c.pos, (1, 1, 1))):
                for t in sorted(tracks.get(b, {})):
                    v = tracks[b][t]
                    frames.append({"channel": channel, "data_points": [{a: str(num(v[i] * sign[i])) for i, a in enumerate("xyz")}],
                                   "uuid": str(uuid.uuid5(space, "%s/%s/%s/%s" % (c.name, b, channel, t))),
                                   "time": t, "color": -1, "interpolation": "linear"})
            if frames: animators[ids[b]] = {"name": b, "type": "bone", "keyframes": frames}
        anims.append({"uuid": str(uuid.uuid5(space, "clip/" + c.name)), "name": c.name,
                      "loop": "loop" if c.loop else "once", "override": False, "length": c.length, "snapping": 20,
                      "selected": False, "animators": animators})
    bb["animations"] = anims
    with open(model.BBMODEL, "w") as f:
        json.dump(bb, f, separators=(",", ":"))
        f.write("\n")


def pose_at(c, t):
    names = set(c.rot) | set(c.pos)
    return {b: (ease(c.rot.get(b), t, c.length, c.loop), ease(c.pos.get(b), t, c.length, c.loop)) for b in names}


def gifs(directory, fps=12, cell=(250, 330)):
    """One looping GIF per group, every clip of the group side by side."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    os.makedirs(directory, exist_ok=True)
    tex = np.array(model.texture())
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    except OSError:
        font = None
    for group, names in GROUPS.items():
        rows = [names[i:i + 6] for i in range(0, len(names), 6)]
        cols = max(len(r) for r in rows)
        longest = max(CLIPS[n].length for n in names)
        frames = []
        for f in range(int(longest * fps)):
            t = f / fps
            sheet = Image.new("RGBA", (cols * cell[0], len(rows) * (cell[1] + 24)), (20, 30, 24, 255))
            draw = ImageDraw.Draw(sheet)
            for r, row in enumerate(rows):
                for i, n in enumerate(row):
                    c = CLIPS[n]
                    tt = t if c.loop else min(t % (c.length + 0.6), c.length)
                    shot = model.render(tex, -28, 10, 6.0, cell, 21.0, pose_at(c, tt), ss=1)
                    sheet.alpha_composite(shot, (i * cell[0], r * (cell[1] + 24) + 24))
                    draw.text((i * cell[0] + 6, r * (cell[1] + 24) + 4), n, fill=(220, 228, 220, 255), font=font)
            frames.append(sheet.convert("P", palette=Image.ADAPTIVE, colors=96))
        frames[0].save(os.path.join(directory, "doom_%s.gif" % group), save_all=True, append_images=frames[1:],
                       duration=int(1000 / fps), loop=0, optimize=False)
        print("gif", group, len(frames), "frames")


if __name__ == "__main__":
    model.build()
    clips()
    for clip in CLIPS.values(): finish(clip)
    missing = [b for c in CLIPS.values() for b in list(c.rot) + list(c.pos) if b not in model.BONES]
    assert not missing, missing
    write_json()
    write_bbmodel()
    print(len(CLIPS), "clips:", ", ".join("%s %d" % (g, len(n)) for g, n in GROUPS.items()))
    if len(sys.argv) > 1: gifs(sys.argv[1])
