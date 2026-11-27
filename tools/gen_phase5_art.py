#!/usr/bin/env python3
"""
Phase 5 art: the airship blocks in the Corporea aesthetic (same vocabulary as gen_phase4_art.py:
living wood in a dark border with crystal studs, living rock with wood bands and a crystal vein,
dark crystal bricks with a bright rim, iris sockets, translucent faceted crystal), each in its own
colour:
  * Mana Levitation Pylon - Manasteel blue (two blocks tall; the gem is drawn by the renderer);
  * Gaia Thruster - Gaia slate, white-gold and pink;
  * Bifrost Road Projector - Elementium pink with a rainbow prism;
  * Eye of the Flugel Navigation Core - Gaia slate and white-gold, a gold crystal collar;
  * Bifrost trail - translucent rainbow glass in 4 fading stages;
  * the pylon's botanical rune circle (entity textures, drawn under the ship).
Reuses the model helpers of gen_phase4_art.py (grid-tiled UVs, crystals split to the translucent
layer with inward twins, full-bright glow). 100% original procedural art.
Usage: gen_phase5_art.py <assets/viridian_engineering dir>
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_phase4_art as p4  # noqa: E402  (reads the same output dir from argv)
from gen_phase4_art import (BLUE, PINK, GAIA, WOOD, ROCK, BORDER, tile, save, wood, rock, border, studs,
                            framed_wood, crystal_bricks, rock_banded, iris, crystal, el, faces, write_model,
                            T, NS, write_state, facing_state, H, h2, _ramp)

OUT = p4.OUT
ENTITY = os.path.join(OUT, "textures", "entity")
os.makedirs(ENTITY, exist_ok=True)

GOLD = [p4.hexc(c) for c in ("#2a1a05", "#5c3d0c", "#a8741c", "#e9b84a", "#ffe39a", "#fffaea")]
RAINBOW = [p4.hexc(c) for c in ("#ff6b8a", "#ffb35c", "#fff27a", "#7dff9a", "#6fd3ff", "#b48cff")]
# a pale prism ramp, dark..bright, for the shaded crystal helper
PRISM = [p4.hexc(c) for c in ("#2b1840", "#5a3a8a", "#8f7fd6", "#a9e6ff", "#fff4b8", "#ffffff")]

# Directional blocks authored pointing UP (like the cannon): blockstate turns for each facing.
UPWARD = {"up": (0, 0), "down": (180, 0), "north": (90, 0), "south": (90, 180), "west": (90, 270), "east": (90, 90)}


# ---------------------------------------------------------------- textures
def bell(seed):
    """White-gold banded nozzle bell: slate plates with gold rims and rivets."""
    t = tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            n = h2(x, y, seed)
            px[x, y] = GAIA[2] if n > 0.75 else GAIA[1]
        px[0, y] = BORDER; px[15, y] = BORDER
    for x in range(16):
        for y in (0, 1, 14, 15):
            px[x, y] = GOLD[3] if y in (1, 14) else GOLD[2]
        px[x, 8] = GAIA[3]
    for x in (3, 8, 12):
        px[x, 4] = GOLD[4]; px[x, 11] = GOLD[4]
    return t


def prism_crystal(seed):
    """Rainbow prism: the shaded crystal with soft rainbow bands running diagonally."""
    t = crystal(PRISM, seed); px = t.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            band = RAINBOW[((x + y) // 3) % 6]
            k = 0.45
            px[x, y] = (int(r * (1 - k) + band[0] * k), int(g * (1 - k) + band[1] * k), int(b * (1 - k) + band[2] * k), a)
    return t


def trail(alpha, seed):
    """Bifrost trail glass: horizontal rainbow bands with a soft shimmer, see-through."""
    t = tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            c = RAINBOW[min(5, y * 6 // 16)]
            s = 0.85 + 0.15 * math.sin((x + y * 0.5) * 0.9 + seed)
            edge = x in (0, 15) or y in (0, 15)
            k = 1.0 if edge else s
            px[x, y] = (min(255, int(c[0] * k + (40 if edge else 0))), min(255, int(c[1] * k + (40 if edge else 0))),
                        min(255, int(c[2] * k + (40 if edge else 0))), min(255, alpha + (40 if edge else 0)))
    return t


def pointer(seed):
    """Gaia wood face with a white-gold arrow (the core's front)."""
    t = framed_wood(GAIA, seed); px = t.load()
    for y in range(3, 9):                      # arrowhead, point up
        w = y - 2
        for x in range(8 - w, 8 + w):
            px[x, y] = GOLD[4] if x in (8 - w, 8 + w - 1) else GOLD[3]
    for y in range(9, 13):                     # shaft
        px[7, y] = GOLD[3]; px[8, y] = GOLD[2]
    return t


def textures():
    save(framed_wood(BLUE, 300), "p5_wood_blue")
    save(rock_banded(BLUE, 301), "p5_rock_band_blue")
    save(crystal_bricks(BLUE, 302), "p5_bricks_blue")
    save(crystal(BLUE, 303), "p5_crystal_blue")
    save(bell(304), "p5_thruster_bell")
    save(iris(GAIA, 305, centre=PINK, frame="rock"), "p5_thruster_back")
    save(prism_crystal(306), "p5_prism")
    save(iris(PINK, 307, centre=PRISM), "p5_projector_base")
    save(crystal(GOLD, 308), "p5_crystal_gold")
    save(pointer(309), "p5_core_front")
    save(iris(GAIA, 310, centre=GOLD, frame="rock"), "p5_core_socket")
    for age, a in enumerate((190, 150, 105, 60)):
        save(trail(a, 311 + age), f"p5_bifrost_trail_{age}")


# ---------------------------------------------------------------- rune circle (entity textures)
def _put(px, n, x, y, c, a):
    if 0 <= x < n and 0 <= y < n:
        r, g, b, a0 = px[x, y]
        if a > a0:
            px[x, y] = c + (a,)


def _ring(px, n, r, w, c, a):
    cx = cy = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - cx, y - cy)
            k = 1 - abs(d - r) / w
            if k > 0:
                _put(px, n, x, y, c, int(a * min(1, k * 1.6)))


def _line(px, n, x0, y0, x1, y1, c, a):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        _put(px, n, round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c, a)


def _glyph(px, n, cx, cy, ang, seed, c, a):
    """A small procedural rune: 2-4 strokes on a 5x7 cell, turned to face the centre."""
    pts = [(-2, -3), (2, -3), (-2, 0), (2, 0), (-2, 3), (2, 3), (0, -3), (0, 3), (0, 0)]
    ca, sa = math.cos(ang), math.sin(ang)
    k = 2 + int(h2(seed, 1, 7) * 3)
    for s in range(k):
        i = int(h2(seed, s * 3 + 2, 9) * len(pts)); j = int(h2(seed, s * 3 + 5, 11) * len(pts))
        (ax, ay), (bx, by) = pts[i], pts[j]
        rot = lambda px_, py_: (cx + px_ * ca - py_ * sa, cy + px_ * sa + py_ * ca)
        x0, y0 = rot(ax, ay); x1, y1 = rot(bx, by)
        _line(px, n, x0, y0, x1, y1, c, a)


def magic_circles():
    n = 128
    pale, mid, bright = (150, 215, 255), (60, 160, 240), (230, 248, 255)
    leaf = (120, 240, 170)
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0)); px = img.load()
    c = (n - 1) / 2
    _ring(px, n, 62, 1.6, mid, 200)
    _ring(px, n, 58, 1.0, pale, 230)
    _ring(px, n, 44, 1.0, pale, 220)
    _ring(px, n, 40, 1.4, mid, 180)
    # runes between the outer rings
    for i in range(24):
        a = i / 24 * 2 * math.pi
        _glyph(px, n, c + math.cos(a) * 51, c + math.sin(a) * 51, a + math.pi / 2, 400 + i, bright, 235)
    # eight leaf-shaped petals (Botania's flower motif) between the inner rings and the hub
    for i in range(8):
        a = i / 8 * 2 * math.pi
        for t in range(0, 101):
            tt = t / 100
            r = 12 + tt * 26
            w = math.sin(tt * math.pi) * 0.22
            for side in (-1, 1):
                x = c + math.cos(a + side * w) * r; y = c + math.sin(a + side * w) * r
                _put(px, n, round(x), round(y), leaf, 210)
        _line(px, n, c + math.cos(a) * 12, c + math.sin(a) * 12, c + math.cos(a) * 38, c + math.sin(a) * 38, pale, 150)
    # two interlocked squares (an eight-point star)
    for off in (0, math.pi / 4):
        pts = [(c + math.cos(off + k * math.pi / 2) * 40, c + math.sin(off + k * math.pi / 2) * 40) for k in range(4)]
        for k in range(4):
            _line(px, n, *pts[k], *pts[(k + 1) % 4], pale, 170)
    _ring(px, n, 10, 1.2, bright, 240)
    img.save(os.path.join(ENTITY, "levitation_magic_circle.png"))

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0)); px = img.load()
    _ring(px, n, 62, 1.4, pale, 220)
    _ring(px, n, 54, 0.9, mid, 200)
    for i in range(12):
        a = i / 12 * 2 * math.pi
        _glyph(px, n, c + math.cos(a) * 58, c + math.sin(a) * 58, a + math.pi / 2, 500 + i, bright, 230)
    for k in range(6):  # a six-point star of mana lines
        a0 = k / 6 * 2 * math.pi; a1 = (k + 2) / 6 * 2 * math.pi
        _line(px, n, c + math.cos(a0) * 50, c + math.sin(a0) * 50, c + math.cos(a1) * 50, c + math.sin(a1) * 50, mid, 200)
    _ring(px, n, 18, 2.0, bright, 230)
    _ring(px, n, 6, 3.0, (255, 255, 255), 250)
    img.save(os.path.join(ENTITY, "levitation_magic_circle_inner.png"))


# ---------------------------------------------------------------- models
def corner_posts(lo, hi, y0, y1, tip, tex="#wood", tipped="#gem"):
    out = []
    for (x, z) in ((lo, lo), (hi - 2, lo), (lo, hi - 2), (hi - 2, hi - 2)):
        out.append(el([x, y0, z], [x + 2, y1, z + 2], faces(tex)))
        out.append(el([x + 0.25, y1, z + 0.25], [x + 1.75, y1 + tip, z + 1.75], faces(tipped), glow=True))
    return out


def pylon_models():
    tx = {**T(("stone", "p4_rock"), ("rock", "p5_rock_band_blue"), ("wood", "p5_wood_blue"),
              ("bricks", "p5_bricks_blue"), ("gem", "p5_crystal_blue")), "particle": NS + "p5_bricks_blue"}
    lower = [
        el([1, 0, 1], [15, 2, 15], faces("#stone", cull=("down",))),
        el([2, 2, 2], [14, 4, 14], faces("#rock")),
        el([4, 4, 4], [12, 16, 12], faces("#wood")),
        el([3.5, 9, 3.5], [12.5, 10, 12.5], faces("#gem"), glow=True),
    ] + corner_posts(2, 14, 4, 12, 2)
    upper = [
        el([5, 0, 5], [11, 7, 11], faces("#wood")),
        el([4.5, 3, 4.5], [11.5, 4, 11.5], faces("#gem"), glow=True),
        el([2, 7, 2], [14, 10, 14], faces("#bricks")),
        el([5, 10, 5], [11, 10.5, 11], faces("#gem", sides=("up", "north", "south", "east", "west")), glow=True),
    ] + corner_posts(2, 14, 10, 14, 2)
    write_model("mana_levitation_pylon_lower", tx, lower)
    write_model("mana_levitation_pylon_upper", tx, upper)
    up = [dict(e, **{"from": [e["from"][0], e["from"][1] + 16, e["from"][2]],
                     "to": [e["to"][0], e["to"][1] + 16, e["to"][2]]}) for e in upper]
    write_model("mana_levitation_pylon_item", tx, lower + up, extra={"display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.3, 0.3, 0.3]}}})
    write_state("mana_levitation_pylon", {"half=lower": {"model": NS + "mana_levitation_pylon_lower"},
                                          "half=upper": {"model": NS + "mana_levitation_pylon_upper"}})


def thruster_models():
    """Gaia Thruster, 3x3x3 (coordinates -16..32 around the centre block), authored mounted on the floor
    with the bell pointing UP; the blockstate turns it to all 6 facings (like the cannon). A living-rock
    mounting plate with four wood struts, a gaia-brick combustion chamber banded in white-gold with
    glowing pink crystal windows, a throat, and a three-tier flared white-gold bell around a crystal
    heart. The mana (blue iris) and redstone (red iris) ports sit in the foot layer exactly where the
    cannon's are, flush with the side faces."""
    tx = {**T(("stone", "p4_rock"), ("rock", "p4_rock_band_gaia"), ("bricks", "p4_bricks_gaia"),
              ("bell", "p5_thruster_bell"), ("wood", "p4_wood_gaia"), ("core", "p4_cannon_core"),
              ("pink", "p4_crystal_pink"), ("manaport", "p4_cannon_mana_port"), ("rsport", "p4_cannon_redstone_port")),
          "particle": NS + "p4_bricks_gaia"}
    m = [
        # mounting plate, stepped
        el([-12, -16, -12], [28, -14, 28], faces("#stone")),
        el([-8, -14, -8], [24, -12.5, 24], faces("#rock")),
        # combustion chamber with two white-gold bands
        el([-4, -12.5, -4], [20, 1, 20], faces("#bricks")),
        el([-4.5, -9, -4.5], [20.5, -7.5, 20.5], faces("#bell")),
        el([-4.5, -2.5, -4.5], [20.5, -1, 20.5], faces("#bell")),
        # throat
        el([0, 1, 0], [16, 5, 16], faces("#bell")),
    ]
    # glowing crystal windows on the chamber's four sides (between the bands)
    for side in ("north", "south", "east", "west"):
        if side == "north": a, b = [2, -6.5, -4.6], [14, -3, -4.1]
        elif side == "south": a, b = [2, -6.5, 20.1], [14, -3, 20.6]
        elif side == "west": a, b = [-4.6, -6.5, 2], [-4.1, -3, 14]
        else: a, b = [20.1, -6.5, 2], [20.6, -3, 14]
        m.append(el(a, b, faces("#pink"), glow=True))
    # four struts from the plate's corners up the chamber
    for (x, z) in ((-10, -10), (24, -10), (-10, 24), (24, 24)):
        m.append(el([x, -14, z], [x + 2, -4, z + 2], faces("#wood")))
        m.append(el([x + 0.25, -4, z + 0.25], [x + 1.75, -1.5, z + 1.75], faces("#pink"), glow=True))
    # the bell: three hollow tiers, each wider, walls 2 px
    def ring(lo, hi, y0, y1):
        return [el([lo, y0, lo], [hi, y1, lo + 2], faces("#bell")), el([lo, y0, hi - 2], [hi, y1, hi], faces("#bell")),
                el([lo, y0, lo + 2], [lo + 2, y1, hi - 2], faces("#bell")), el([hi - 2, y0, lo + 2], [hi, y1, hi - 2], faces("#bell"))]
    m += ring(-2, 18, 5, 13) + ring(-6, 22, 13, 21) + ring(-10, 26, 21, 30)
    # the bell's floors between tiers (closing the steps) and the crystal heart in the throat
    # lids between the rings: lifted 0.05 off the rings' bottom planes (the same plane z-fought)
    m.append(el([-6, 13.05, -6], [22, 13.55, 22], faces("#bell", sides=("up", "down"))))
    m.append(el([-10, 21.05, -10], [26, 21.55, 26], faces("#bell", sides=("up", "down"))))
    m[-2]["faces"]["up"]["uv"] = [0, 0, 16, 16]
    m[-1]["faces"]["up"]["uv"] = [0, 0, 16, 16]
    m.append(el([2, 5, 2], [14, 8, 14], faces("#core"), glow=True))
    # ports (as on the cannon): a wood conduit from the chamber to a flange flush with the side face
    for sign, tex in ((1, "#manaport"), (-1, "#rsport")):
        if sign > 0:
            tube, flange, face = ([20, -12, 5], [29, -6, 11]), ([29, -13, 3], [32, -3, 13]), "east"
        else:
            tube, flange, face = ([-13, -12, 5], [-4, -6, 11]), ([-16, -13, 3], [-13, -3, 13]), "west"
        m.append(el(tube[0], tube[1], faces("#wood")))
        f = el(flange[0], flange[1], faces("#bricks", **{face: tex}))
        f["faces"][face]["uv"] = [0, 0, 16, 16]
        m.append(f)
    write_model("gaia_thruster", tx, m, extra={"ambientocclusion": False})
    write_model("gaia_thruster_item", tx, m, extra={"ambientocclusion": False, "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.2, 0.2, 0.2]}}})
    facing_state("gaia_thruster", UPWARD)
    write_model("formed/hidden_thruster", {"particle": NS + "p4_bricks_gaia"}, [])
    write_state("gaia_thruster_part", {"": {"model": NS + "formed/hidden_thruster"}})


def projector_models():
    """Authored lens UP: an elementium wood base, a living-rock collar, four crystal-tipped prongs
    holding a turned rainbow prism."""
    tx = {**T(("base", "p5_projector_base"), ("rock", "p4_livingrock_framed"), ("wood", "p4_wood_pink"),
              ("gem", "p5_prism"), ("pink", "p4_crystal_pink")), "particle": NS + "p4_wood_pink"}
    ry = {"angle": 45, "axis": "y", "origin": [8, 8, 8]}
    m = [
        el([0, 0, 0], [16, 5, 16], faces("#wood", cull=("down",), up="#base")),
        el([2, 5, 2], [14, 8, 14], faces("#rock")),
        el([5, 8, 5], [11, 13, 11], faces("#gem"), glow=True, rotation=ry),
        el([6.5, 13, 6.5], [9.5, 15.5, 9.5], faces("#gem"), glow=True, rotation=ry),
    ] + corner_posts(2, 14, 8, 13, 2, tipped="#pink")
    write_model("bifrost_projector", tx, m)
    facing_state("bifrost_projector", UPWARD)


def core_models():
    """Authored facing NORTH: a stepped living-rock pedestal, a gaia wood stem with a gold crystal
    collar, a brick cup with a gold iris socket (the Eye floats above it), and a white-gold pointer
    fin on the front showing which way the ship will fly."""
    tx = {**T(("stone", "p4_rock"), ("rock", "p4_rock_band_gaia"), ("wood", "p4_wood_gaia"),
              ("bricks", "p4_bricks_gaia"), ("socket", "p5_core_socket"), ("front", "p5_core_front"),
              ("core", "p5_crystal_gold")), "particle": NS + "p4_bricks_gaia"}
    m = [
        el([1, 0, 1], [15, 3, 15], faces("#stone", cull=("down",))),
        el([2, 3, 2], [14, 5, 14], faces("#rock", north="#front")),
        el([4, 5, 4], [12, 9, 12], faces("#wood", sides=("north", "south", "east", "west"))),
        el([3.5, 6.5, 3.5], [12.5, 7.5, 12.5], faces("#core"), glow=True),
        el([2, 9, 2], [14, 12, 14], faces("#bricks", up="#socket")),
        el([7, 3, 0.5], [9, 11, 2], faces("#wood")),
        el([7.25, 11, 0.75], [8.75, 13.5, 1.75], faces("#core"), glow=True),
    ]
    m[4]["faces"]["up"]["uv"] = [0, 0, 16, 16]
    write_model("flugel_navigation_core", tx, m)
    facing_state("flugel_navigation_core", H)


def trail_models():
    v = {}
    for age in range(4):
        name = f"bifrost_trail_{age}"
        with open(os.path.join(p4.MODELS, name + ".json"), "w") as fh:
            json.dump({"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                       "textures": {"all": NS + f"p5_bifrost_trail_{age}", "particle": NS + f"p5_bifrost_trail_{age}"}},
                      fh, indent=2)
        v[f"age={age}"] = {"model": NS + name}
    write_state("bifrost_trail", v)


if __name__ == "__main__":
    textures()
    magic_circles()
    pylon_models()
    thruster_models()
    projector_models()
    core_models()
    trail_models()
    print("phase 5 art written")
