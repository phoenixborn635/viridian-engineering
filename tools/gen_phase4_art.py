#!/usr/bin/env python3
"""
Phase 4 art, pass 2 (2026-11-24, user feedback): the Corporea aesthetic carried over in each block's
own colour. Its vocabulary:
  * living-wood planks inside a dark border, with small glowing crystal studs in the corners;
  * living-rock panels crossed by wood bands and a thin crystal vein;
  * dark crystal "bricks" with a bright rim (the Archive Pylon body);
  * round iris sockets (rock rim, wood ring, glowing centre) for ports;
  * dark screens with a glowing grid for controllers;
  * diagonal-faceted crystal for cores and inlays.
Colours: Terrasteel green (reactor, battery, mana pump), Elementium pink (logistics), Gaia slate with
white-gold and pink (cannon), Manasteel blue (manasteel pump).

Writes textures (p4_*), every Phase 4 block model and blockstate, the formed-multiblock piece models
(models/block/formed/*, drawn by the controllers' renderers), the 3x3x3 cannon model and both pumps.
100% original procedural art. Usage: gen_phase4_art.py <assets/viridian_engineering dir>
"""
import json
import os
import sys

from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
BLOCK = os.path.join(OUT, "textures", "block")
MODELS = os.path.join(OUT, "models", "block")
STATES = os.path.join(OUT, "blockstates")
for d in (BLOCK, MODELS, STATES, os.path.join(MODELS, "formed"), os.path.join(MODELS, "mana_pump"),
          os.path.join(MODELS, "manasteel_pump")):
    os.makedirs(d, exist_ok=True)
NS = "viridian_engineering:block/"


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def h2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


WOOD = [hexc(c) for c in ("#3b2418", "#5a3826", "#764b33", "#946345", "#b07e5a")]
ROCK = [hexc(c) for c in ("#6f6c66", "#8f8c84", "#aeaba2", "#c9c6bd", "#e2dfd6")]
BORDER = hexc("#2a201b")
# accent ramps: darkest .. brightest (index 0..5)
GREEN = [hexc(c) for c in ("#06200f", "#0d3d1f", "#16703a", "#35b866", "#8ff0b4", "#e6fff0")]
PINK = [hexc(c) for c in ("#22071d", "#43103a", "#7c2468", "#cf4fae", "#f6a6e2", "#fff0fb")]
GAIA = [hexc(c) for c in ("#120f19", "#241f30", "#3c3450", "#7d6f96", "#f3d9a0", "#fff8e6")]
BLUE = [hexc(c) for c in ("#071526", "#10304f", "#1c5a8f", "#3d9be0", "#9fd6ff", "#effaff")]
RED = [hexc(c) for c in ("#2a0406", "#5e0b10", "#a3161d", "#e8333a", "#ff8a7a", "#fff0ea")]
LAVA = [hexc(c) for c in ("#3a0d02", "#8a2405", "#d4500c", "#ff9a2e", "#ffe28a", "#fff6d0")]
DARK = hexc("#120d0a")


def tile(n=16):
    return Image.new("RGBA", (n, n))


def save(img, name):
    img.save(os.path.join(BLOCK, name + ".png"))


# ---------------------------------------------------------------- base materials
def wood(img, seed=1, vertical=True, box=(0, 0, 16, 16)):
    px = img.load()
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            a, b = (x, y) if vertical else (y, x)
            seam = (a + (b // 5 + seed) % 2) % 5 == 0
            n = h2(a, b // 3, seed)
            c = WOOD[1] if seam else WOOD[3] if n > 0.72 else WOOD[2]
            if not seam and h2(a, b, seed + 9) > 0.93:
                c = WOOD[4]
            px[x, y] = c


def rock(img, seed=2, box=(0, 0, 16, 16)):
    px = img.load()
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            n = (h2(x // 2, y // 2, seed) + h2(x, y, seed + 3)) / 2
            c = ROCK[3] if n > 0.55 else ROCK[2] if n > 0.25 else ROCK[1]
            if h2(x, y, seed + 7) > 0.96:
                c = ROCK[4]
            px[x, y] = c


def border(img, c=BORDER, inset=0, size=16):
    px = img.load()
    a, b = inset, size - 1 - inset
    for i in range(a, b + 1):
        px[i, a] = c; px[i, b] = c; px[a, i] = c; px[b, i] = c


def studs(img, pal, pts=((2, 2), (13, 2), (2, 13), (13, 13))):
    px = img.load()
    for (x, y) in pts:
        px[x, y] = pal[4]


def framed_wood(pal, seed):
    """Corporea frame: wood planks, dark border, glowing corner studs."""
    t = tile(); wood(t, seed, vertical=False); border(t); studs(t, pal)
    return t


def crystal_bricks(pal, seed):
    """Archive-pylon body: dark crystal bricks with a bright rim and corner sparks."""
    t = tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            row = y // 4
            off = 0 if row % 2 == 0 else 3
            mortar = (y % 4 == 0) or ((x + off) % 7 == 0)
            n = h2(x, y, seed)
            px[x, y] = pal[0] if mortar else (pal[2] if n > 0.8 else pal[1])
    border(t, pal[3])
    px[1, 1] = pal[5]; px[14, 1] = pal[5]; px[1, 14] = pal[5]; px[14, 14] = pal[5]
    return t


def rock_banded(pal, seed, slit=False):
    """Living rock with wood bands top and bottom and a crystal vein; optionally a barred glass slit."""
    t = tile(); rock(t, seed); px = t.load()
    for x in range(16):
        px[x, 1] = WOOD[2]; px[x, 2] = WOOD[1]; px[x, 13] = WOOD[1]; px[x, 14] = WOOD[2]
        px[x, 0] = BORDER; px[x, 15] = BORDER
    if slit:
        for y in range(3, 13):
            for x in range(5, 11):
                px[x, y] = pal[1] if (x in (5, 10)) else pal[0]
            px[7, y] = pal[2]
        for x in range(5, 11):
            px[x, 7] = WOOD[1]; px[x, 8] = WOOD[2]
    else:
        for x in range(16):
            px[x, 8] = pal[2] if h2(x, 8, seed) > 0.3 else pal[3]
        px[8, 8] = pal[4]
    return t


def iris(pal, seed, centre=None, frame="wood"):
    """Port socket: framed face with a round iris and a glowing centre."""
    t = tile()
    if frame == "wood":
        wood(t, seed, vertical=False)
    else:
        rock(t, seed)
    border(t)
    centre = centre or pal
    rings = [(5.8, BORDER), (5.0, ROCK[2] if frame == "wood" else WOOD[2]), (4.0, centre[1]), (3.0, centre[2]),
             (2.0, centre[3]), (1.0, centre[4])]
    px = t.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            for r, c in rings:
                if d <= r:
                    px[x, y] = c
    px[6, 6] = centre[5]
    studs(t, pal)
    return t


def screen(pal, seed, rows=None):
    """Controller face: wood frame, dark glass with a glowing grid (or a glyph)."""
    t = framed_wood(pal, seed); px = t.load()
    for y in range(3, 13):
        for x in range(3, 13):
            px[x, y] = pal[1] if y % 3 == 0 else pal[0]
    if rows:
        for yy, row in enumerate(rows):
            for xx, ch in enumerate(row):
                if ch == "#":
                    px[4 + xx, 4 + yy] = pal[4]
    else:
        for gy in range(3):
            for gx in range(3):
                bx, by = 4 + gx * 3, 4 + gy * 3
                px[bx, by] = pal[3]; px[bx + 1, by] = pal[3]; px[bx, by + 1] = pal[3]; px[bx + 1, by + 1] = pal[4]
    return t


def _mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def _ramp(pal, v):
    """Smooth colour at position v (0..5) along a palette ramp."""
    v = max(0.0, min(5.0, v))
    i = min(int(v), 4)
    return _mix(pal[i], pal[i + 1], v - i)


def crystal(pal, seed):
    """Translucent faceted crystal, in the style of the Archive Pylon gem: soft vertical shading
    (light at the top), two facet columns, a bright rim along the top and left edges, a few
    sparkles. Slightly see-through (alpha ~215-240); rendered in the translucent layer."""
    t = tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            facet = 0.45 if 5 <= x <= 10 else 0.0           # the middle facet catches less light
            v = 4.3 - y / 15 * 2.2 - facet + (h2(x // 2, y // 3, seed) - 0.5) * 0.35
            a = 222 - int(facet * 20)
            if y == 0 or x == 0:
                v, a = 4.7, 238                               # lit rim
            elif y == 15 or x == 15:
                v, a = 2.4, 235                               # shaded rim
            if h2(x, y, seed + 5) > 0.965 and 0 < x < 15 and 0 < y < 15:
                v, a = 5.0, 240                               # sparkle
            px[x, y] = _ramp(pal, v) + (a,)
    return t


def bearing(pal, seed):
    t = tile(); rock(t, seed); border(t)
    px = t.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 5.2:
                px[x, y] = DARK if d <= 3.0 else WOOD[3] if d <= 4.0 else WOOD[1]
    studs(t, pal)
    return t


def glass(pal, seed):
    t = tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = pal[0] if (x + 2 * y) % 7 else pal[1]
    return t


def textures():
    # shared materials
    save(framed_wood(GREEN, 70), "p4_wood_green")
    save(framed_wood(PINK, 71), "p4_wood_pink")
    save(framed_wood(GAIA, 72), "p4_wood_gaia")
    t = tile(); rock(t, 73); border(t); save(t, "p4_rock")
    save(rock_banded(GREEN, 74), "p4_rock_band_green")
    save(rock_banded(PINK, 75), "p4_rock_band_pink")
    save(rock_banded(GAIA, 76), "p4_rock_band_gaia")
    save(crystal_bricks(GREEN, 77), "p4_bricks_green")
    save(crystal_bricks(PINK, 78), "p4_bricks_pink")
    save(crystal_bricks(GAIA, 79), "p4_bricks_gaia")
    save(crystal(GREEN, 80), "p4_crystal_green")
    save(crystal(PINK, 81), "p4_crystal_pink")
    save(crystal(GAIA, 82), "p4_crystal_gaia")
    save(glass(GREEN, 83), "p4_glass_green")

    # reactor
    t = tile(); rock(t, 84); border(t); studs(t, GREEN)
    px = t.load()
    for i in range(3, 13):  # wood inner frame
        px[i, 3] = WOOD[2]; px[i, 12] = WOOD[2]; px[3, i] = WOOD[2]; px[12, i] = WOOD[2]
    px[7, 7] = GREEN[3]; px[8, 8] = GREEN[3]; px[7, 8] = GREEN[2]; px[8, 7] = GREEN[4]
    save(t, "p4_reactor_casing")
    save(iris(GREEN, 85, centre=LAVA, frame="rock"), "p4_lava_port")
    save(iris(GREEN, 86, frame="rock"), "p4_mana_port")
    save(bearing(GREEN, 87), "p4_shaft_port")
    save(screen(GREEN, 88), "p4_reactor_front")
    save(crystal(GREEN, 89), "p4_reactor_core")

    # battery
    save(rock_banded(GREEN, 90, slit=True), "p4_battery_side")
    save(framed_wood(GREEN, 91), "p4_battery_top")
    save(screen(GREEN, 92, rows=[".####.", ".#..#.", ".####.", ".####.", ".####.", "......"]), "p4_battery_front")
    t = iris(GREEN, 93, frame="rock"); px = t.load()
    for (x, y) in ((7, 3), (6, 4), (8, 4), (7, 12), (6, 11), (8, 11)):  # two-way arrows
        px[x, y] = GREEN[5]
    save(t, "p4_battery_port")

    # elementium (same names the shaped models already use)
    save(iris(PINK, 94), "p4_funnel_mouth")
    t = framed_wood(PINK, 95)
    px = t.load()
    for (x, y) in ((4, 7), (5, 6), (5, 7), (5, 8), (6, 5), (6, 7), (6, 9)) + tuple((i, 7) for i in range(6, 12)):
        px[x, y] = PINK[4]
    save(t, "p4_tunnel_roof")
    save(screen(PINK, 96, rows=["#.####", "......", "#.###.", "......", "#.####", "......"]), "p4_request_screen")
    save(framed_wood(PINK, 97), "p4_elementium_trim")
    t = tile(); wood(t, 98); border(t); save(t, "p4_livingwood")
    save(rock_banded(PINK, 99), "p4_livingrock_framed")

    # cannon
    t = iris(GAIA, 100, centre=PINK); save(t, "p4_cannon_muzzle")
    save(crystal([PINK[0], PINK[1], PINK[2], PINK[3], GAIA[4], GAIA[5]], 102), "p4_cannon_core")
    save(iris(GAIA, 103, centre=BLUE, frame="rock"), "p4_cannon_mana_port")
    save(iris(GAIA, 104, centre=RED, frame="rock"), "p4_cannon_redstone_port")
    t = tile(); wood(t, 101, vertical=False); px = t.load()
    for y in range(16):
        for x in (0, 1, 7, 8, 14, 15):
            px[x, y] = GAIA[2] if x % 7 else GAIA[4]
    save(t, "p4_cannon_barrel")

    pumps()


# ---------------------------------------------------------------- pumps (32x32, Create pump UV layout)
def pump_texture(name, pal):
    t = tile(32); px = t.load()
    # flange plate (uv 0,0..6,6 -> px 0..12): rock face with wood rim and a glowing bore
    rock(t, 110, box=(0, 0, 12, 12))
    for i in range(12):
        px[i, 0] = BORDER; px[i, 11] = BORDER; px[0, i] = BORDER; px[11, i] = BORDER
    for i in range(1, 11):
        px[i, 1] = WOOD[2]; px[i, 10] = WOOD[1]; px[1, i] = WOOD[2]; px[10, i] = WOOD[1]
    for y in range(3, 9):
        for x in range(3, 9):
            d = ((x - 5.5) ** 2 + (y - 5.5) ** 2) ** 0.5
            px[x, y] = pal[0] if d < 1.6 else pal[2] if d < 2.6 else ROCK[1]
    # flange edge (uv 6.5..8 x 0..6 -> px 13..16 x 0..12): wood edge with a stud
    wood(t, 111, box=(12, 0, 16, 12))
    px[14, 5] = pal[4]
    # The cog (Create's mechanical_pump/cog model; 2026-11-25 redo, user: the old one looked gross). Its UVs:
    #   px 0..18 x 13..16  the teeth bars' faces seen along the axis (a bar runs across the whole cog;
    #                      px (2, 14) is also sampled as the whole octagonal hub face)
    #   px 20..30 x 13..16 the teeth tips
    #   px 0..18 x 18..28  the teeth's long side faces (18 long x 10 deep)
    #   px 20..30 x 18..23 the octagonal hub's sides
    # The old art painted a round rock disc into the side strip, which every tooth then showed sliced.
    # Now: machined tier metal, light along the leading edge and dark along the trailing one, a thin
    # glowing mana groove down the middle of the sides and a crystal stud at the hub.
    metal = METAL[name]
    for x in range(32):
        for y in range(12, 32):
            px[x, y] = (0, 0, 0, 0)
    for x in range(18):                       # teeth faces
        px[x, 13] = metal[4]; px[x, 14] = metal[3]; px[x, 15] = metal[2]
    for x in (0, 17):
        for y in (13, 14, 15):
            px[x, y] = metal[1]               # tooth ends a shade darker
    px[2, 14] = metal[3]                      # hub face sample
    for y in (13, 14, 15):                    # crystal stud where the bar crosses the hub
        px[8, y] = pal[4] if y == 14 else pal[3]; px[9, y] = pal[3]
    for x in range(20, 30):                   # teeth tips
        px[x, 13] = metal[4]; px[x, 14] = metal[4]; px[x, 15] = metal[3]
    for x in range(18):                       # teeth sides: machined bands, mana groove in the middle
        for y in range(18, 28):
            v = 3 if y < 21 else 2 if y < 26 else 1
            if y == 18:
                v = 4
            if y == 27:
                v = 0
            if (x + y) % 9 == 0 and 19 <= y <= 26:
                v = max(0, v - 1)             # faint tooling marks
            px[x, y] = metal[v]
        px[x, 22] = pal[2]; px[x, 23] = pal[3] if x % 3 else pal[4]
    for x in range(20, 30):                   # hub sides
        for y in range(18, 23):
            px[x, y] = metal[2] if y not in (18, 22) else metal[3 if y == 18 else 1]
    save(t, name)


# machined tier metals for the pump cogs (dark .. light)
METAL = {
    "mana_pump": [hexc(c) for c in ("#132a1d", "#24503a", "#3f7f5c", "#74b48c", "#c3ecd0")],          # Terrasteel
    "manasteel_pump": [hexc(c) for c in ("#14243d", "#264a78", "#4479b5", "#86b6e6", "#d2e8ff")],     # Manasteel
}


def pumps():
    pump_texture("mana_pump", GREEN)
    pump_texture("manasteel_pump", BLUE)
    for name in ("mana_pump", "manasteel_pump"):
        tex = NS + name
        write_model(f"{name}/block", {"4": tex, "particle": tex}, None, parent="create:block/mechanical_pump/block")
        write_model(f"{name}/cog", {"2": tex, "particle": tex}, None, parent="create:block/mechanical_pump/cog")
        write_model(f"{name}/item", {"4": tex, "particle": tex}, None, parent="create:block/mechanical_pump/item")
        variants = {
            "facing=down": {"model": tex + "/block", "x": 180},
            "facing=east": {"model": tex + "/block", "x": 90, "y": 90},
            "facing=north": {"model": tex + "/block", "x": 90},
            "facing=south": {"model": tex + "/block", "x": 90, "y": 180},
            "facing=up": {"model": tex + "/block"},
            "facing=west": {"model": tex + "/block", "x": 90, "y": 270},
        }
        # Create's pump also has a waterlogged property
        full = {}
        for k, v in variants.items():
            for w in ("false", "true"):
                full[f"{k},waterlogged={w}"] = v
        with open(os.path.join(STATES, name + ".json"), "w") as fh:
            json.dump({"variants": full}, fh, indent=2)


# ---------------------------------------------------------------- model helpers
ALL = ("north", "south", "east", "west", "up", "down")
GLOW = {"neoforge_data": {"block_light": 15, "sky_light": 15}}


def el(frm, to, faces_, glow=False, rotation=None):
    e = {"from": frm, "to": to, "faces": faces_}
    if glow:
        e.update(GLOW)
        e["shade"] = False
    if rotation:
        e["rotation"] = rotation
    return e


def faces(tex, sides=ALL, cull=(), **over):
    out = {}
    for s in sides:
        f = {"texture": over.get(s, tex)}
        if s in cull:
            f["cullface"] = s
        out[s] = f
    return out


# Vanilla derives a face's UV from the element's position when none is given. Outside 0..16 that
# UV runs off the sprite and samples whatever sits next to it on the block atlas (redstone, obsidian,
# ...), and parts whose neighbours are transparent vanish. So every element is cut along the 16-px
# grid and each face gets an explicit UV from its position inside its own grid cell: big pieces tile
# their texture once per block instead of bleeding, and nothing ever leaves its sprite.
def _cuts(lo, hi):
    pts = [lo]
    g = (int(lo // 16) + 1) * 16
    while g < hi - 1e-6:
        pts.append(float(g))
        g += 16
    pts.append(hi)
    return list(zip(pts[:-1], pts[1:]))


def _uv(face, a, b):
    (x0, y0, z0), (x1, y1, z1) = a, b
    return {
        "down": [x0, 16 - z1, x1, 16 - z0],
        "up": [x0, z0, x1, z1],
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0],
        "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0],
        "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
    }[face]


_EDGE = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}


def tile_elements(elements):
    out = []
    for e in elements:
        frm, to = e["from"], e["to"]
        for xs in _cuts(frm[0], to[0]):
            for ys in _cuts(frm[1], to[1]):
                for zs in _cuts(frm[2], to[2]):
                    lo = [xs[0], ys[0], zs[0]]
                    hi = [xs[1], ys[1], zs[1]]
                    cell = [(v // 16) * 16 for v in lo]
                    la = [round(lo[i] - cell[i], 4) for i in range(3)]
                    lb = [round(hi[i] - cell[i], 4) for i in range(3)]
                    faces_ = {}
                    for name, f in e["faces"].items():
                        axis, side = _EDGE[name]
                        # keep only faces on the original element's outside
                        if (hi if side else lo)[axis] != (to if side else frm)[axis]:
                            continue
                        f = dict(f)
                        f.setdefault("uv", [round(v, 4) for v in _uv(name, la, lb)])
                        faces_[name] = f
                    if not faces_:
                        continue
                    n = dict(e)
                    n["from"], n["to"], n["faces"] = lo, hi, faces_
                    out.append(n)
    return out


CRYSTAL_TEX = ("#gem", "#pink", "#core")
_OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def is_crystal(e):
    return all(f["texture"] in CRYSTAL_TEX for f in e["faces"].values())


def _face_rect(e, name):
    axis, side = _EDGE[name]
    plane = (e["to"] if side else e["from"])[axis]
    others = [a for a in range(3) if a != axis]
    return plane, [(min(e["from"][a], e["to"][a]), max(e["from"][a], e["to"][a])) for a in others]


def _touching(e, name, others):
    """True if another element shows a face on the plane of e's face `name`, facing back into e,
    overlapping it: e's inward twin there would be coplanar with that face and z-fight with it
    (a gem resting on a post, two stacked gems)."""
    plane, rect = _face_rect(e, name)
    opp = _OPP[name]
    rot = e.get("rotation")
    for o in others:
        if o is e or o.get("rotation") != rot or opp not in o["faces"]:
            continue
        p2, r2 = _face_rect(o, opp)
        if abs(p2 - plane) > 1e-4:
            continue
        if all(min(h1, h2) - max(l1, l2) > 1e-4 for (l1, h1), (l2, h2) in zip(rect, r2)):
            return True
    return False


def crystal_elements(elements, solids=()):
    """Crystals are see-through, so their far side must exist: every face gets an inward-facing twin
    (a zero-thickness element on the same plane facing into the gem; the outer face hides it from
    outside), and each facet shows the whole crystal texture. No twin where the gem rests against
    another element's face (it would z-fight with that face; the contact is hidden anyway)."""
    out = []
    everything = list(elements) + list(solids)
    for e0 in elements:
        e = dict(e0)
        e["faces"] = {n: dict(f, uv=f.get("uv", [0, 0, 16, 16])) for n, f in e["faces"].items()}
        e.pop("shade", None)
        out.append(e)
        for n, f in e["faces"].items():
            if _touching(e0, n, everything):
                continue
            axis, side = _EDGE[n]
            plane = (e["to"] if side else e["from"])[axis]
            a, b = list(e["from"]), list(e["to"])
            a[axis] = b[axis] = plane
            inner = dict(e)
            inner["from"], inner["to"] = a, b
            inner["faces"] = {_OPP[n]: dict(f)}
            out.append(inner)
    return out


def _model(textures, elements, parent, extra, render_type=None):
    m = {"parent": parent, "textures": textures}
    if elements is not None:
        m["elements"] = elements
    if render_type:
        m["render_type"] = render_type
    if extra:
        m.update(extra)
    return m


def _dump(name, m):
    with open(os.path.join(MODELS, name + ".json"), "w") as fh:
        json.dump(m, fh, indent=2)


def split(elements):
    solid = [e for e in elements if not is_crystal(e)]
    gems = [e for e in elements if is_crystal(e)]
    return tile_elements(solid), crystal_elements(gems, solid)


def write_model(name, textures, elements, parent="minecraft:block/block", extra=None):
    """A block/item model. Crystals go to a translucent child of a NeoForge composite model; the
    rest stays cutout."""
    if elements is None:
        _dump(name, _model(textures, None, parent, extra))
        return
    solid, gems = split(elements)
    if not gems:
        _dump(name, _model(textures, solid, parent, extra, "minecraft:cutout_mipped"))
        return
    m = {"loader": "neoforge:composite", "parent": parent, "textures": textures, "children": {
        "solid": {"parent": "minecraft:block/block", "textures": textures, "elements": solid,
                  "render_type": "minecraft:cutout_mipped"},
        "crystal": {"parent": "minecraft:block/block", "textures": textures, "elements": gems,
                    "render_type": "minecraft:translucent"},
    }}
    if extra:
        m.update(extra)
    _dump(name, m)


def write_piece(name, textures, elements):
    """A piece drawn by a renderer: <name> (solid, cutout) and <name>_crystal (translucent)."""
    solid, gems = split(elements)
    _dump(name, _model(textures, solid, "minecraft:block/block", None))
    _dump(name + "_crystal", _model(textures, gems, "minecraft:block/block", None))


def T(*pairs):
    return {n: NS + v for n, v in pairs}


def cube(name, all_tex, **sides):
    tex = {"particle": NS + sides.get("north", all_tex)}
    for s in ALL:
        tex[s] = NS + sides.get(s, all_tex)
    write_model(name, tex, None, parent="minecraft:block/cube")


def hidden(name, particle):
    """A formed member: draws nothing (the controller renders the structure)."""
    write_model("formed/" + name, {"particle": NS + particle}, [])


# ---------------------------------------------------------------- models
def elementium_models():
    write_model("elementium_smart_funnel", {
        **T(("mouth", "p4_funnel_mouth"), ("trim", "p4_elementium_trim"), ("wood", "p4_livingwood"),
            ("rock", "p4_livingrock_framed"), ("gem", "p4_crystal_pink")), "particle": NS + "p4_elementium_trim"}, [
        el([1, 1, 0], [15, 15, 4], faces("#trim", cull=("north",), north="#mouth")),
        el([1, 0, 4], [15, 16, 12], faces("#wood")),
        el([0, 0, 12], [16, 16, 16], faces("#rock", cull=("south",))),
        el([6.5, 6.5, -0.25], [9.5, 9.5, 0.5], faces("#gem"), glow=True),
        el([0.5, 6, 6], [1, 10, 10], faces("#gem", sides=("west", "north", "south", "up", "down")), glow=True),
        el([15, 6, 6], [15.5, 10, 10], faces("#gem", sides=("east", "north", "south", "up", "down")), glow=True),
    ])
    write_model("elementium_smart_tunnel", {
        **T(("rock", "p4_livingrock_framed"), ("wood", "p4_livingwood"), ("trim", "p4_elementium_trim"),
            ("roof", "p4_tunnel_roof"), ("gem", "p4_crystal_pink")), "particle": NS + "p4_elementium_trim"}, [
        el([0, 0, 0], [16, 6, 16], faces("#rock", cull=("down", "north", "south", "east", "west"))),
        el([13, 6, 0], [16, 14, 16], faces("#wood", cull=("east",))),
        el([0, 6, 0], [3, 14, 3], faces("#wood")),
        el([0, 6, 13], [3, 14, 16], faces("#wood")),
        el([0, 14, 0], [16, 16, 16], faces("#trim", cull=("up",), up="#roof")),
        el([6, 9, 6], [10, 14, 10], faces("#gem"), glow=True),
        el([7, 7.5, 7], [9, 9, 9], faces("#gem"), glow=True),
    ])
    write_model("elementium_request_interface", {
        **T(("rock", "p4_livingrock_framed"), ("wood", "p4_livingwood"), ("trim", "p4_elementium_trim"),
            ("screen", "p4_request_screen"), ("gem", "p4_crystal_pink")), "particle": NS + "p4_elementium_trim"}, [
        el([2, 0, 2], [14, 4, 14], faces("#rock", cull=("down",))),
        el([4, 4, 4], [12, 10, 12], faces("#wood")),
        el([1, 10, 1], [15, 13, 15], faces("#trim", north="#screen")),
        el([2, 10.5, 0.75], [14, 12.5, 1], faces("#screen", sides=("north",)), glow=True),
        el([6.5, 13, 6.5], [9.5, 16, 9.5], faces("#gem"), glow=True,
           rotation={"angle": 45, "axis": "y", "origin": [8, 14, 8]}),
    ])


def reactor_models():
    # unformed blocks: full cubes in the Corporea style
    cube("terrasteel_reactor_casing", "p4_reactor_casing")
    cube("terrasteel_mana_reactor_controller", "p4_reactor_casing", north="p4_reactor_front")
    cube("terrasteel_lava_port", "p4_lava_port")
    cube("terrasteel_mana_port", "p4_mana_port")
    cube("terrasteel_shaft_port", "p4_reactor_casing", north="p4_shaft_port")
    write_model("terrasteel_mana_reactor_core", {"core": NS + "p4_reactor_core", "particle": NS + "p4_reactor_core"},
                [el([0, 0, 0], [16, 16, 16], faces("#core"), glow=True)])
    hidden("hidden_reactor", "p4_reactor_casing")
    hidden("hidden_core", "p4_reactor_core")

    tx = {**T(("rock", "p4_rock"), ("band", "p4_rock_band_green"), ("wood", "p4_wood_green"),
              ("bricks", "p4_bricks_green"), ("gem", "p4_crystal_green")), "particle": NS + "p4_rock_band_green"}
    # outer wall, ground course: plinth, banded rock, crystal inlay on the outside
    write_piece("formed/reactor_wall_low", tx, [
        el([0, 0, -1], [16, 3, 16], faces("#wood")),
        el([0, 3, 0], [16, 16, 16], faces("#band", south="#rock")),
        el([6, 6, -0.3], [10, 12, 0], faces("#gem", sides=("north", "east", "west", "up", "down")), glow=True),
    ])
    # outer wall, top course: rock with a wood coping and a glowing channel on the bowl side
    write_piece("formed/reactor_wall_high", tx, [
        el([0, 0, 0], [16, 12, 16], faces("#rock", north="#bricks")),
        el([0, 12, -1], [16, 16, 16.5], faces("#wood")),
        el([0, 6, 16], [16, 8, 16.3], faces("#gem", sides=("south", "up", "down", "east", "west")), glow=True),
        el([7, 16, 6], [9, 17, 10], faces("#gem"), glow=True),
    ])
    # corners: wood pillars; the top one carries a crystal spire
    write_piece("formed/reactor_corner_low", tx, [
        el([-1, 0, -1], [16, 3, 16], faces("#wood")),
        el([0, 3, 0], [16, 16, 16], faces("#bricks")),
    ])
    write_piece("formed/reactor_corner_high", tx, [
        el([0, 0, 0], [16, 12, 16], faces("#bricks")),
        el([-1, 12, -1], [16.5, 16, 16.5], faces("#wood")),
        el([5, 16, 5], [11, 24, 11], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 16, 8]}),
        el([6.5, 24, 6.5], [9.5, 28, 9.5], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 24, 8]}),
    ])
    # inner ring around the pit (pit to the SOUTH): rock, wood top with a mana channel, bricks facing the pit
    write_piece("formed/reactor_inner", tx, [
        el([0, 0, 0], [16, 14, 16], faces("#rock", south="#bricks")),
        el([0, 14, 0], [16, 16, 16], faces("#wood")),
        # the channel's ends run into the next ring piece; end faces would sit on the wood's end faces (z-fight)
        el([0, 15.5, 6], [16, 16.2, 10], faces("#gem", sides=("up", "north", "south")), glow=True),
    ])
    write_piece("formed/reactor_inner_corner", tx, [
        el([0, 0, 0], [16, 14, 16], faces("#rock", south="#bricks", east="#bricks")),
        el([0, 14, 0], [16, 16, 16], faces("#wood")),
        el([6, 15.5, 6], [16, 16.2, 10], faces("#gem", sides=("up", "north", "south", "west")), glow=True),
        el([6, 15.5, 10], [10, 16.2, 16], faces("#gem", sides=("up", "east", "west")), glow=True),
    ])
    # pedestal under the floating core
    write_piece("formed/reactor_pedestal", tx, [
        el([1, 0, 1], [15, 4, 15], faces("#rock")),
        el([4, 4, 4], [12, 11, 12], faces("#wood")),
        el([2, 11, 2], [14, 14, 14], faces("#bricks")),
        el([3, 14, 3], [13, 14.3, 13], faces("#gem", sides=("up", "north", "south", "east", "west")), glow=True),
        el([1.5, 11.5, 1.5], [3, 13, 3], faces("#gem"), glow=True), el([13, 11.5, 1.5], [14.5, 13, 3], faces("#gem"), glow=True),
        el([1.5, 11.5, 13], [3, 13, 14.5], faces("#gem"), glow=True), el([13, 11.5, 13], [14.5, 13, 14.5], faces("#gem"), glow=True),
    ])
    # the core and its ring (animated by the controller's renderer)
    write_piece("formed/reactor_core", {"gem": NS + "p4_reactor_core", "particle": NS + "p4_reactor_core"}, [
        el([4.5, 3, 4.5], [11.5, 13, 11.5], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 8, 8]}),
        el([6, 13, 6], [10, 16, 10], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 8, 8]}),
        el([6, 0, 6], [10, 3, 10], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 8, 8]}),
        el([2, 6, 7], [4, 10, 9], faces("#gem"), glow=True), el([12, 6, 7], [14, 10, 9], faces("#gem"), glow=True),
    ])
    write_piece("formed/reactor_ring", {"wood": NS + "p4_wood_green", "gem": NS + "p4_crystal_green",
                                        "particle": NS + "p4_wood_green"}, [
        el([-4, 7, -4], [20, 9, -2], faces("#wood")), el([-4, 7, 18], [20, 9, 20], faces("#wood")),
        el([-4, 7, -2], [-2, 9, 18], faces("#wood")), el([18, 7, -2], [20, 9, 18], faces("#wood")),
        el([7, 6, -5], [9, 10, -1], faces("#gem"), glow=True), el([7, 6, 17], [9, 10, 21], faces("#gem"), glow=True),
        el([-5, 6, 7], [-1, 10, 9], faces("#gem"), glow=True), el([17, 6, 7], [21, 10, 9], faces("#gem"), glow=True),
    ])


def battery_models():
    cube("terrasteel_mana_battery_casing", "p4_battery_side", up="p4_battery_top", down="p4_battery_top")
    cube("terrasteel_mana_battery_controller", "p4_battery_side", north="p4_battery_front", up="p4_battery_top", down="p4_battery_top")
    cube("terrasteel_mana_battery_port", "p4_battery_port")
    hidden("hidden_battery", "p4_battery_side")
    tx = {**T(("rock", "p4_rock"), ("band", "p4_rock_band_green"), ("wood", "p4_wood_green"),
              ("glass", "p4_glass_green"), ("gem", "p4_crystal_green"), ("bricks", "p4_bricks_green")),
          "particle": NS + "p4_rock_band_green"}
    # side panel (outward NORTH): rock jambs, a recessed window (the stored mana glows behind it), wood bands
    write_piece("formed/battery_side", tx, [
        el([0, 0, 0], [5, 16, 16], faces("#rock")),
        el([11, 0, 0], [16, 16, 16], faces("#rock")),
        el([5, 0, 3], [11, 16, 16], faces("#glass")),
        # wood plinth band: no west/east/down faces, those lie on the rock jambs' faces (z-fight)
        el([0, 0, -0.4], [16, 1.5, 3], faces("#wood", sides=("north", "up", "south"))),
        el([5, 7.5, 0.5], [11, 8.5, 3], faces("#wood")),
        el([1.5, 4, -0.2], [3.5, 12, 0], faces("#gem", sides=("north", "east", "west", "up", "down")), glow=True),
        el([12.5, 4, -0.2], [14.5, 12, 0], faces("#gem", sides=("north", "east", "west", "up", "down")), glow=True),
    ])
    # corner (north-west): wood pillar with a crystal band
    write_piece("formed/battery_corner", tx, [
        el([0, 0, 0], [16, 16, 16], faces("#bricks")),
        # wood band: only its outer faces (the east/south/down ones lie on the bricks' faces)
        el([-0.3, 0, -0.3], [16, 2, 16], faces("#wood", sides=("north", "west", "up"))),
        el([-0.3, 7, -0.3], [16, 9, 16], faces("#gem", sides=("north", "west", "up", "down")), glow=True),
    ])
    write_piece("formed/battery_center", tx, [])
    # cap over the whole 3x3 (drawn at the top centre block): wood plate, crystal crown and corner finials
    cap = [el([-16, 16, -16], [32, 18, 32], faces("#wood")),
           el([-11, 18, -11], [27, 20, 27], faces("#bricks")),
           el([3, 20, 3], [13, 25, 13], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 20, 8]}),
           el([5.5, 25, 5.5], [10.5, 28, 10.5], faces("#gem"), glow=True, rotation={"angle": 45, "axis": "y", "origin": [8, 25, 8]})]
    for (x, z) in ((-15, -15), (28, -15), (-15, 28), (28, 28)):
        cap.append(el([x, 18, z], [x + 3, 21, z + 3], faces("#gem"), glow=True))
    write_piece("formed/battery_cap", tx, cap)


def cannon_models():
    """Gaia Arcane Cannon, 3x3x3 (coordinates -16..32 around the centre block). Authored mounted on
    the floor and firing UP (out of the core's tip); the blockstate turns it to any of the 6 facings
    (wall-mounted fires sideways, ceiling-mounted fires down).

    An arcane focus grown from one stem, every part attached: a small living-rock foot against the
    mounting surface with four wood roots reaching to the corners (two end in the mana and redstone
    ports), a living-wood stem with a crystal collar, and a cup whose four corner claws hold the big
    translucent gaia core. The beam and bolts leave from the core's tip. The core and three orbiting
    shards are drawn and spun by the block entity renderer.
    """
    tx = {**T(("rock", "p4_rock_band_gaia"), ("stone", "p4_rock"), ("wood", "p4_wood_gaia"),
              ("bricks", "p4_bricks_gaia"), ("gem", "p4_crystal_gaia"),
              ("pink", "p4_crystal_pink"), ("core", "p4_cannon_core"), ("manaport", "p4_cannon_mana_port"),
              ("rsport", "p4_cannon_redstone_port")), "particle": NS + "p4_bricks_gaia"}
    ry = lambda ox, oy, oz, a=45: {"angle": a, "axis": "y", "origin": [ox, oy, oz]}
    frame = [
        # foot: a stepped living-rock boss
        el([-1, -16, -1], [17, -13, 17], faces("#stone")),
        el([1, -13, 1], [15, -11, 15], faces("#stone")),
        # stem with a crystal collar, then the cup with a glowing socket
        el([4, -11, 4], [12, -5, 12], faces("#wood")),
        el([3.5, -9, 3.5], [12.5, -8, 12.5], faces("#pink"), glow=True),
        el([-1, -5, -1], [17, -3, 17], faces("#bricks")),
        el([5, -3, 5], [11, -2.5, 11], faces("#pink", sides=("up", "north", "south", "east", "west")), glow=True),
    ]
    # four claws on the cup's corners (the front stays open for the beam), crystal-tipped
    for (x, z) in ((0, 0), (14, 0), (0, 14), (14, 14)):
        frame.append(el([x, -3, z], [x + 2, 11, z + 2], faces("#wood")))
        frame.append(el([x + 0.25, 11, z + 0.25], [x + 1.75, 14, z + 1.75], faces("#pink"), glow=True))
    # four living-wood roots from the foot to the corners. They start above the foot's bottom and end
    # below its top, so no root face shares a plane with the foot (that z-fought).
    for frm, to in (([14, -15.5, 5.5], [31, -13.5, 10.5]), ([-15, -15.5, 5.5], [2, -13.5, 10.5]),
                    ([5.5, -15.5, 14], [10.5, -13.5, 31]), ([5.5, -15.5, -15], [10.5, -13.5, 2])):
        frame.append(el(frm, to, faces("#wood"), rotation=ry(8, -15, 8)))
    # two ports: a living-wood conduit from the stem out to the face of the side block, ending in a
    # flange flush with that face and centred on it, so an outside pipe or lever meets it seamlessly.
    # Mana on the +x side (blue iris), redstone on the -x side (red iris).
    def port(sign, tex):
        if sign > 0:
            tube, flange, face = ([12, -11, 5], [29, -5, 11]), ([29, -13, 3], [32, -3, 13]), "east"
        else:
            tube, flange, face = ([-13, -11, 5], [4, -5, 11]), ([-16, -13, 3], [-13, -3, 13]), "west"
        frame.append(el(tube[0], tube[1], faces("#wood")))
        frame.append(el([tube[0][0] + 4 if sign > 0 else tube[1][0] - 7, -11.5, 4.5],
                        [tube[0][0] + 7 if sign > 0 else tube[1][0] - 4, -4.5, 11.5],
                        faces(tex == "#manaport" and "#pink" or "#pink"), glow=True))           # crystal band
        f = el(flange[0], flange[1], faces("#bricks", **{face: tex}))
        f["faces"][face]["uv"] = [0, 0, 16, 16]
        frame.append(f)
    port(1, "#manaport")
    port(-1, "#rsport")
    write_model("gaia_arcane_cannon", tx, frame, extra={"ambientocclusion": False})

    # animated (block entity renderer): the core held in the claws, and three orbiting shards
    rc = ry(8, 8, 8)
    core = [
        el([3, 3, 3], [13, 13, 13], faces("#core"), glow=True, rotation=rc),
        el([5, 13, 5], [11, 15.5, 11], faces("#core"), glow=True, rotation=rc),
        el([7, 15.5, 7], [9, 17.5, 9], faces("#core"), glow=True, rotation=rc),
        el([5, 0.5, 5], [11, 3, 11], faces("#core"), glow=True, rotation=rc),
        el([7, -1, 7], [9, 0.5, 9], faces("#core"), glow=True, rotation=rc),
    ]
    write_piece("formed/cannon_core", tx, core)
    shards = [el([21.00, 6, 7.50], [22.00, 10, 8.50], faces("#gem"), glow=True),
              el([0.75, 8, 19.19], [1.75, 12, 20.19], faces("#gem"), glow=True),
              el([0.75, 4, -4.19], [1.75, 8, -3.19], faces("#gem"), glow=True)]
    write_piece("formed/cannon_shards", tx, shards)

    # the item shows everything at rest
    write_model("gaia_arcane_cannon_item", tx, frame + core + shards, extra={"display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.2, 0.2, 0.2]}}})
    hidden("hidden_cannon", "p4_bricks_gaia")


# ---------------------------------------------------------------- blockstates
H = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270)}
D = {**H, "up": (270, 0), "down": (90, 0)}


def write_state(name, variants):
    with open(os.path.join(STATES, name + ".json"), "w") as fh:
        json.dump({"variants": variants}, fh, indent=2)


def facing_state(name, rotations, model=None):
    model = NS + (model or name)
    v = {}
    for k, (x, y) in rotations.items():
        e = {"model": model}
        if x: e["x"] = x
        if y: e["y"] = y
        v[f"facing={k}"] = e
    write_state(name, v)


def formed_state(name, hidden_model):
    write_state(name, {"formed=false": {"model": NS + name}, "formed=true": {"model": NS + "formed/" + hidden_model}})


def blockstates():
    facing_state("elementium_smart_funnel", D)
    facing_state("elementium_smart_tunnel", H)
    facing_state("elementium_request_interface", H)
    facing_state("terrasteel_mana_reactor_controller", H)
    facing_state("terrasteel_shaft_port", D)
    facing_state("terrasteel_mana_battery_controller", H)
    write_state("terrasteel_lava_port", {"": {"model": NS + "terrasteel_lava_port"}})
    write_state("terrasteel_mana_port", {"": {"model": NS + "terrasteel_mana_port"}})
    write_state("terrasteel_mana_battery_port", {"": {"model": NS + "terrasteel_mana_battery_port"}})
    formed_state("terrasteel_reactor_casing", "hidden_reactor")
    formed_state("terrasteel_mana_reactor_core", "hidden_core")
    formed_state("terrasteel_mana_battery_casing", "hidden_battery")
    # cannon: horizontal facings turn the 3x3x3 model; vertical values exist but are never placed
    facing_state("gaia_arcane_cannon", {"up": (0, 0), "down": (180, 0), "north": (90, 0), "south": (90, 180),
                                        "west": (90, 270), "east": (90, 90)})
    write_state("gaia_arcane_cannon_part", {"": {"model": NS + "formed/hidden_cannon"}})


def finishing_details():
    # The leaf clips and faceted gem mount outside the north face of the press.
    textures = {"particle": NS + "p4_terrasteel_trim", "frame": NS + "p4_terrasteel_trim",
                "leaf": NS + "terrasteel_casing", "gem": NS + "p4_reactor_core"}

    def detail(start, end, texture, angle=0):
        mapped = {side: {"texture": "#" + texture, "uv": [0, 0, 16, 16]} for side in ALL}
        rotation = {"origin": [8, 10, -.75], "axis": "z", "angle": angle} if angle else None
        return el(start, end, mapped, rotation=rotation)

    write_piece("alfheim_press_module", textures, [
        detail([5, 7, -1], [11, 13, 0], "frame"),
        detail([6.5, 8.5, -2], [9.5, 11.5, -1.1], "gem", 45),
        detail([2, 9, -.7], [5.75, 10, -.1], "leaf", 22.5),
        detail([2.4, 10.4, -.7], [5.5, 11.5, -.1], "leaf", -22.5),
        detail([10.25, 9, -.7], [14, 10, -.1], "leaf", -22.5),
        detail([10.5, 10.4, -.7], [13.6, 11.5, -.1], "leaf", 22.5),
    ])

    window = tile()
    shades = [(27, 54, 81, 255), (53, 103, 145, 255), (92, 155, 188, 255)]
    for y in range(16):
        for x in range(16):
            if (y >= 8 and (x < 2 or x > 13 or y in (8, 15))) or y in (0, 7):
                window.putpixel((x, y), shades[(x * 7 + y * 13) % 3])
            elif y >= 8 and x + y in (16, 17, 25) and x % 3 == 0:
                window.putpixel((x, y), (146, 215, 226, 255))
    save(window, "manasteel_pipe_window")


if __name__ == "__main__":
    textures()
    elementium_models()
    reactor_models()
    battery_models()
    cannon_models()
    blockstates()
    finishing_details()
    print("phase 4 art (pass 2) written")
