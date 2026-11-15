# Draws the Latverian villager clothes (type overlay) over the vanilla 64x64 villager layout.
import random, sys
from PIL import Image
random.seed(7)
OUT = sys.argv[1]
BABY_SRC = sys.argv[2]
im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
px = im.load()

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)

def cloth(c, x, y):
    # A little weave so the cloth is not flat: lighter and darker threads at random.
    k = 1.0 + random.choice([0, 0, 0, 0.07, -0.07, -0.12])
    px[x, y] = shade(c, k)

def rect(x0, y0, w, h, c, weave=True):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            if weave: cloth(c, x, y)
            else: px[x, y] = c if len(c) == 4 else c + (255,)

def box(u, v, w, h, d):
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h)}

GREEN = (38, 84, 52); GREEN_D = (26, 60, 38); BLACK = (24, 24, 28); RED = (150, 28, 30); GOLD = (214, 172, 58)
FUR = (52, 44, 40); FUR_D = (34, 28, 26); BOOT = (40, 28, 20); TROUSER = (32, 32, 38)

# Body: the green coat, a red-and-black embroidered collar, gold buttons, a black belt with a gold buckle.
b = box(16, 20, 8, 12, 6)
for f in b.values(): rect(*f, GREEN)
fx, fy, fw, fh = b['front']
for x in range(fx, fx + fw):
    px[x, fy] = (RED if x % 2 else BLACK) + (255,)
    px[x, fy + 1] = (BLACK if x % 2 else RED) + (255,)
for side in ('left', 'right', 'back'):
    sx, sy, sw, sh = b[side]
    for x in range(sx, sx + sw): px[x, sy] = (RED if x % 2 else BLACK) + (255,)
for y in (fy + 3, fy + 5, fy + 7): px[fx + fw // 2, y] = GOLD + (255,)
for x in range(fx + fw // 2, fx + fw // 2 + 1):
    for y in range(fy + 2, fy + fh - 3): pass
belt = fy + fh - 3
for f in ('front', 'left', 'right', 'back'):
    sx, sy, sw, sh = b[f]
    for x in range(sx, sx + sw): px[x, belt] = BLACK + (255,); px[x, belt + 1] = BLACK + (255,)
px[fx + fw // 2 - 1, belt] = GOLD + (255,); px[fx + fw // 2, belt] = GOLD + (255,)
px[fx + fw // 2 - 1, belt + 1] = GOLD + (255,); px[fx + fw // 2, belt + 1] = GOLD + (255,)

# The long coat over it (the robe layer): darker green, opening down the front, a red folk band and a black hem.
r = box(0, 38, 8, 20, 6)
for f in r.values(): rect(*f, GREEN_D)
for f in ('front', 'left', 'right', 'back'):
    sx, sy, sw, sh = r[f]
    for x in range(sx, sx + sw):
        px[x, sy + sh - 1] = BLACK + (255,)
        px[x, sy + sh - 2] = BLACK + (255,)
        px[x, sy + sh - 4] = (RED if x % 2 else GOLD) + (255,)
        px[x, sy + sh - 5] = RED + (255,)
fx, fy, fw, fh = r['front']
for y in range(fy, fy + fh - 2): px[fx + fw // 2 - 1, y] = BLACK + (255,); px[fx + fw // 2, y] = BLACK + (255,)
for y in range(fy + 1, fy + 11, 3): px[fx + fw // 2 - 2, y] = GOLD + (255,); px[fx + fw // 2 + 1, y] = GOLD + (255,)

# Sleeves, with black cuffs; the hands stay bare (the lower rows of the crossed hands' front are left clear).
for (u, v, w, h, d) in ((44, 22, 4, 8, 4), (40, 38, 8, 4, 4)):
    a = box(u, v, w, h, d)
    for name, f in a.items(): rect(*f, GREEN)
    if (u, v) == (44, 22):
        for name in ('front', 'left', 'right', 'back'):
            sx, sy, sw, sh = a[name]
            for x in range(sx, sx + sw):
                for y in range(sy + sh - 3, sy + sh): px[x, y] = (0, 0, 0, 0)
                px[x, sy + sh - 4] = BLACK + (255,)
        bx, by, bw, bh = a['bottom']
        for x in range(bx, bx + bw):
            for y in range(by, by + bh): px[x, y] = (0, 0, 0, 0)
    else:
        for name in ('front', 'back', 'top', 'bottom'):
            sx, sy, sw, sh = a[name]
            for y in range(sy, sy + sh): px[sx, y] = BLACK + (255,); px[sx + sw - 1, y] = BLACK + (255,)

# Legs: black trousers, tall brown boots.
l = box(0, 22, 4, 12, 4)
for f in l.values(): rect(*f, TROUSER)
for name in ('front', 'left', 'right', 'back'):
    sx, sy, sw, sh = l[name]
    rect(sx, sy + sh - 5, sw, 5, BOOT)
    for x in range(sx, sx + sw): px[x, sy + sh - 5] = shade(BOOT, 1.4)
rect(*l['bottom'], BOOT)

# The hat layer: a tall fur kalpak with a green band and a gold badge in front; the face stays clear.
h = box(32, 0, 8, 10, 8)
rect(*h['top'], FUR)
for name in ('front', 'left', 'right', 'back'):
    sx, sy, sw, sh = h[name]
    for x in range(sx, sx + sw):
        for y in range(sy, sy + 3): px[x, y] = shade(FUR if (x + y) % 3 else FUR_D, 1.0 + random.choice([0, .1, -.1]))
        px[x, sy + 3] = GREEN + (255,)
fx, fy, fw, fh = h['front']
px[fx + fw // 2 - 1, fy + 3] = GOLD + (255,); px[fx + fw // 2, fy + 3] = GOLD + (255,)
im.save(OUT)

# The baby: the taiga baby's clothes recoloured to Latveria's (cloth to green, fur to dark fur, trims to red).
src = Image.open(BABY_SRC).convert('RGBA')
out = Image.new('RGBA', src.size, (0, 0, 0, 0))
sp, op = src.load(), out.load()
for x in range(src.size[0]):
    for y in range(src.size[1]):
        r_, g_, b_, a_ = sp[x, y]
        if a_ == 0: continue
        lum = (r_ + g_ + b_) / 3
        if lum > 150: c = RED                     # light trims become red embroidery
        elif r_ > g_ + 25: c = GREEN              # the brown coat becomes green
        else: c = FUR                              # dark parts stay a dark fur
        op[x, y] = shade(c, 0.75 + lum / 400) if c is not RED else RED + (255,)
out.save(OUT.replace('type', 'baby'))
print('ok')
