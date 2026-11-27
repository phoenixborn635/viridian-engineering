#!/usr/bin/env python3
"""
Floating pylon crystal texture for client/VEPylonModel (32x32): a faceted gem (core, cap, tip) and
small orbiting shards. Each box face is shaded like a cut gem: lit front facet, mid sides, dark back,
bright top, a pale highlight edge and a soft vertical gradient. 100% original procedural art.

Box layout (Minecraft entity UV convention, texOffs -> size w,h,d):
  core  (0, 0)  4x8x4      cap (16, 0) 3x3x3      tip (16, 6) 2x3x2      shard (24, 0) 1x3x1

Usage: gen_pylon_crystal.py <assets/viridian_engineering dir>
"""
import os
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
ENTITY = os.path.join(OUT, "textures", "entity")
os.makedirs(ENTITY, exist_ok=True)

BOXES = [((0, 0), (4, 8, 4)), ((16, 0), (3, 3, 3)), ((16, 6), (2, 3, 2)), ((24, 0), (1, 3, 1))]


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(px, x0, y0, w, h, base, top_light, edge, alpha):
    for y in range(h):
        t = 0 if h <= 1 else y / (h - 1)
        row = mix(top_light, base, t)            # lighter towards the top of every facet
        for x in range(w):
            c = row
            if x == 0:
                c = mix(row, edge, 0.65)          # pale cut edge on the leading side
            elif x == w - 1 and w > 2:
                c = mix(row, (0, 0, 0), 0.18)     # slight shadow on the trailing side
            px[x0 + x, y0 + y] = c + (alpha,)


def crystal(name, pal):
    dark, mid, light, pale, white = (hexc(c) for c in pal)
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for (u, v), (w, h, d) in BOXES:
        paint(px, u + d, v, w, d, light, white, white, 235)              # top
        paint(px, u + d + w, v, w, d, dark, mid, mid, 235)               # bottom
        paint(px, u, v + d, d, h, mid, light, pale, 215)                 # side
        paint(px, u + d, v + d, w, h, light, pale, white, 225)           # front (lit facet)
        paint(px, u + d + w, v + d, d, h, mid, light, pale, 215)         # side
        paint(px, u + 2 * d + w, v + d, w, h, dark, mid, light, 205)     # back
    # a couple of inner sparkles on the core's front facet
    px[5, 6] = white + (255,); px[6, 9] = pale + (255,)
    img.save(os.path.join(ENTITY, name + ".png"))
    print("wrote", name)


if __name__ == "__main__":
    crystal("corporea_archive_pylon", ["#2c0f4a", "#6a2fae", "#a66be6", "#dcbcff", "#fbf3ff"])
    crystal("mana_levitation_pylon", ["#0c2f5a", "#1f6fc0", "#4fb0ff", "#b8e6ff", "#f2fbff"])
