#!/usr/bin/env python3
"""
Item sprites, pass 2 (2026-11-25, user feedback "match the theme, don't look dumb").
Same vocabulary as the block art (gen_phase4_art.py): living-wood in a dark border, living-rock
caps, faceted crystal with a bright rim, glowing studs. Light comes from the top-left; every sprite
gets a coloured (not black) outline taken from the material it surrounds.

Terra Drives: living-wood cartridge with a rock cap, a faceted data crystal in a dark window and tier
pips; crystal colour by tier (16k Manasteel blue, 64k Terrasteel green, 256k Elementium pink,
1M Gaia white-gold). Arcane Corporea Spark: faceted four-point star (Corporea violet, white-gold
tips). Wireless Terminal: hand-held living-wood slate with a glowing-grid screen and crystal antenna.
Alfheim Infusion Module: rock card in a wood frame with an elven-gate iris (green to gold).
100% original procedural art. Usage: gen_items.py <assets/viridian_engineering dir>
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_phase4_art import hexc, h2, WOOD, ROCK, GREEN, PINK, GAIA, BLUE  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
ITEM = os.path.join(OUT, "textures", "item")
os.makedirs(ITEM, exist_ok=True)

VIOLET = [hexc(c) for c in ("#1a0b2e", "#341656", "#5a2a8f", "#9058d6", "#cfa6ff", "#f6ecff")]
ELVEN = [hexc(c) for c in ("#0b2414", "#17502a", "#3f9a3c", "#a9d65a", "#f4e58a", "#fffbe0")]
GOLD = [hexc(c) for c in ("#3a2608", "#6e4a12", "#b07d24", "#e8b84a", "#ffe39a", "#fff8e0")]


class Sprite:
    def __init__(self):
        self.px = {}

    def put(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.px[(x, y)] = c

    def rect(self, x0, y0, x1, y1, fn):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, fn(x, y))

    def outline(self, factor=0.32, skip=()):
        add = {}
        for (x, y), c in self.px.items():
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n in self.px or n in add or not (0 <= n[0] < 16 and 0 <= n[1] < 16) or n in skip:
                    continue
                add[n] = tuple(int(v * factor) for v in c[:3]) + (255,)
        self.px.update(add)

    def save(self, name):
        img = Image.new("RGBA", (16, 16))
        for (x, y), c in self.px.items():
            img.putpixel((x, y), c)
        img.save(os.path.join(ITEM, name + ".png"))


def wood_px(seed):
    def f(x, y):
        v = 2 + (1 if h2(x // 2, y, seed) > 0.62 else 0) - (1 if h2(x, y // 3, seed + 5) > 0.8 else 0)
        if x % 4 == 0:
            v -= 1  # plank seams
        return WOOD[max(1, min(4, v))]
    return f


def rock_px(seed):
    def f(x, y):
        v = 2 + (1 if h2(x, y, seed) > 0.7 else 0) - (1 if h2(x, y, seed + 9) > 0.85 else 0)
        return ROCK[max(1, min(4, v))]
    return f


def bevel(s, x0, y0, x1, y1, ramp, hi=4, lo=1):
    """Top/left edge highlight, bottom/right edge shadow inside a filled rectangle."""
    for x in range(x0, x1 + 1):
        s.put(x, y0, ramp[hi] if x < x1 else ramp[hi - 1])
        s.put(x, y1, ramp[lo])
    for y in range(y0, y1 + 1):
        s.put(x0, y, ramp[hi] if y < y1 else ramp[lo + 1])
        s.put(x1, y, ramp[lo])


def gem_diamond(s, cx, cy, r, ramp):
    """Faceted diamond: left/top facets light, right/bottom dark, white glint."""
    for y in range(cy - r, cy + r + 1):
        w = r - abs(y - cy)
        for x in range(cx - w, cx + w + 1):
            left = x <= cx
            top = y <= cy
            v = 4 if (left and top) else 3 if (top or left) else 2
            if abs(x - cx) + abs(y - cy) == r:
                v = 5 if (left and top) else 1 if not (left or top) else 2
            s.put(x, y, ramp[v])
    s.put(cx - 1, cy - 1, ramp[5])


# ---------------------------------------------------------------------------------------------------
def terra_drive(name, ramp, pips):
    s = Sprite()
    # body: living wood cartridge x3..12, y3..14 (rounded bottom corners)
    s.rect(3, 3, 12, 14, wood_px(11 + pips))
    bevel(s, 3, 3, 12, 14, WOOD)
    del s.px[(3, 14)], s.px[(12, 14)]
    # rock cap y1..3, slightly narrower, with handle notch
    s.rect(4, 1, 11, 3, rock_px(3))
    bevel(s, 4, 1, 11, 3, ROCK)
    # tier gem on the cap
    s.put(7, 2, ramp[4]); s.put(8, 2, ramp[3])
    # dark data window y5..11, x5..10 with crystal rim
    s.rect(5, 5, 10, 11, lambda x, y: ramp[0])
    for x in range(5, 11):
        s.put(x, 5, ramp[1])
    for y in range(5, 12):
        s.put(5, y, ramp[1])
    # faceted data crystal
    gem_diamond(s, 8, 8, 3, ramp)
    # glow lines across the window
    s.put(6, 10, ramp[2]); s.put(10, 7, ramp[2])
    # tier pips on the bottom band
    for i in range(4):
        x = 5 + i * 2 - (1 if i else 0) if False else 5 + i * 2
        s.put(x, 13, ramp[4] if i < pips else WOOD[0])
    s.outline()
    s.save(name)


def spark():
    s = Sprite()
    cx, cy = 7, 7
    # four-point star: long vertical, shorter horizontal
    arms = {(0, -1): 6, (0, 1): 6, (-1, 0): 5, (1, 0): 5}
    for (dx, dy), ln in arms.items():
        for i in range(ln + 1):
            w = max(0, (ln - i) // 3)
            for j in range(-w, w + 1):
                x = cx + dx * i + (j if dy else 0)
                y = cy + dy * i + (j if dx else 0)
                light = (x - cx) + (y - cy) < 0 or ((x - cx) <= 0 and (y - cy) <= 0)
                edge = j in (-w, w) and w > 0
                v = 4 if light else 2
                if edge:
                    v = 3 if light else 1
                s.put(x, y, VIOLET[v])
            tip = (cx + dx * ln, cy + dy * ln)
            s.put(*tip, GOLD[4])
    # diagonal facets between arms
    for d in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        s.put(cx + d[0] * 2, cy + d[1] * 2, VIOLET[3] if d[0] + d[1] < 0 else VIOLET[2])
        s.put(cx + d[0], cy + d[1], VIOLET[4] if d[0] + d[1] <= 0 else VIOLET[3])
    # glowing core
    s.put(cx, cy, VIOLET[5]); s.put(cx - 1, cy, GOLD[5]); s.put(cx, cy - 1, GOLD[5])
    s.put(cx + 1, cy, VIOLET[4]); s.put(cx, cy + 1, VIOLET[4])
    s.outline(0.28)
    # sparkles (no outline)
    for p, c in (((13, 2), GOLD[4]), ((2, 12), VIOLET[4]), ((12, 13), GOLD[3])):
        s.put(*p, c)
    s.save("arcane_corporea_spark")


def wireless_terminal():
    s = Sprite()
    # antenna: wood stick with crystal shard, top-right
    for y in range(1, 4):
        s.put(11, y + 1, WOOD[2] if y < 3 else WOOD[1])
    s.put(11, 1, VIOLET[4]); s.put(12, 0, VIOLET[5]); s.put(12, 1, VIOLET[3]); s.put(11, 0, VIOLET[3])
    # slate body x2..12, y4..15
    s.rect(2, 4, 12, 15, wood_px(7))
    bevel(s, 2, 4, 12, 15, WOOD)
    del s.px[(2, 15)], s.px[(12, 15)]
    # screen x4..10, y6..11: dark with glowing grid
    def scr(x, y):
        gx, gy = (x - 4) % 3 == 0, (y - 6) % 3 == 0
        if gx and gy:
            return VIOLET[3]
        if gx or gy:
            return VIOLET[1]
        return VIOLET[0]
    s.rect(4, 6, 10, 11, scr)
    # item dots on the screen
    s.put(5, 7, PINK[4]); s.put(8, 7, GREEN[4]); s.put(5, 10, BLUE[4]); s.put(8, 10, GAIA[4])
    s.put(10, 6, VIOLET[5])  # glare
    # screen bezel (rock)
    for x in range(3, 12):
        s.put(x, 5, ROCK[3]); s.put(x, 12, ROCK[1])
    for y in range(5, 13):
        s.put(3, y, ROCK[3] if y < 12 else ROCK[1]); s.put(11, y, ROCK[1])
    # crystal button + studs
    s.put(7, 14, VIOLET[4]); s.put(6, 14, VIOLET[2]); s.put(8, 14, VIOLET[2])
    s.put(4, 14, GOLD[3]); s.put(10, 14, GOLD[3])
    s.outline()
    s.save("corporea_wireless_terminal")


def alfheim_module():
    s = Sprite()
    # card x1..14, y2..13 living rock
    s.rect(1, 2, 14, 13, rock_px(21))
    bevel(s, 1, 2, 14, 13, ROCK)
    # living-wood bands top and bottom, crystal studs at the ends
    for x in range(1, 15):
        s.put(x, 2, WOOD[4] if x < 14 else WOOD[2]); s.put(x, 3, WOOD[2])
        s.put(x, 12, WOOD[2]); s.put(x, 13, WOOD[1])
    for (x, y) in ((2, 3), (13, 3), (2, 12), (13, 12)):
        s.put(x, y, ELVEN[4])
    # thin crystal vein across the rock
    for x in (3, 4, 11, 12):
        s.put(x, 8 if x < 8 else 7, ELVEN[3])
    # contact pins (white-gold) along the bottom
    for x in range(3, 13, 2):
        s.put(x, 14, GOLD[4]); s.put(x, 15, GOLD[2])
    # iris: wood ring, elven-gate centre
    cx, cy = 7.5, 7.5
    for y in range(2, 14):
        for x in range(1, 15):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 2.6:
                # swirl: angle + radius -> green..gold
                import math
                a = (math.atan2(y - cy, x - cx) + math.pi) / (2 * math.pi)
                t = (a * 2 + d * 0.35) % 1.0
                v = 5 if d < 0.9 else 4 if t < 0.35 else 3 if t < 0.7 else 2
                s.put(x, y, ELVEN[v])
            elif d <= 3.6:
                s.put(x, y, WOOD[3] if (x + y) < 15 else WOOD[1])
            elif d <= 4.3:
                s.put(x, y, WOOD[0] if (x + y) >= 15 else ROCK[1])
    s.outline()
    s.save("alfheim_infusion_module")


if __name__ == "__main__":
    terra_drive("terra_drive_16k", BLUE, 1)
    terra_drive("terra_drive_64k", GREEN, 2)
    terra_drive("terra_drive_256k", PINK, 3)
    terra_drive("terra_drive_1m", GAIA[:2] + GOLD[2:], 4)
    spark()
    wireless_terminal()
    alfheim_module()
