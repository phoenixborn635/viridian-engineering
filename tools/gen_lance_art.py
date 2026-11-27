#!/usr/bin/env python3
"""
Gaia Kinetic Lance, 3D (2026-11-25, user: "a 3D lance like Create's own tools, with a rotating cog,
more pristine"). Writes, in the style of Create's custom-rendered tools:

  models/item/gaia_kinetic_lance/item.json   the lance (static parts) with its display transforms
  models/item/gaia_kinetic_lance/cog.json    the cog around the shaft (spun by client/LanceRenderer)
  models/item/gaia_kinetic_lance/gem.json    crystal strips and inlays (drawn full-bright)
  textures/item/lance/*.png                  polished white-gold, pale silver, a bound living-wood
                                             grip, the cog's gold, pink-white gaia crystal

The lance is modelled along +y from the pommel (y -16) to the blade's point (y 32), centred on
x = z = 8: pommel, bound grip with gold rings, a stepped vamplate, the cog, a slim shaft with crystal
strips, a gold collar, and a four-edged leaf blade with crystal inlays in its flats.
No two faces share a plane facing the same way (checked by tools/check_zfight.py).
100% original. Usage: gen_lance_art.py <assets/viridian_engineering dir>
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_phase4_art import hexc, h2  # noqa: E402
from PIL import Image  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
TEX = os.path.join(OUT, "textures", "item", "lance")
MOD = os.path.join(OUT, "models", "item", "gaia_kinetic_lance")
os.makedirs(TEX, exist_ok=True)
os.makedirs(MOD, exist_ok=True)
NS = "viridian_engineering:item/lance/"

GOLD = [hexc(c) for c in ("#5c3d0c", "#a8741c", "#e0b04a", "#f6d688", "#fff1c8", "#fffbef")]
SILVER = [hexc(c) for c in ("#4a4560", "#7c7596", "#aaa4c4", "#d2cde6", "#eeebf8", "#ffffff")]
WOOD = [hexc(c) for c in ("#2e1b12", "#4f3021", "#6e4530", "#8f5f42", "#b07e5a")]
PINK = [hexc(c) for c in ("#7c2468", "#cf4fae", "#f6a6e2", "#ffd6f2", "#fff0fb", "#ffffff")]


def img(fn):
    t = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = t.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = fn(x, y)
    t2 = t
    return t2


def save(t, name):
    t.save(os.path.join(TEX, name + ".png"))


def textures():
    # polished white-gold: smooth vertical gradient with one bright sheen band, faint edge shadow
    save(img(lambda x, y: GOLD[3 if y > 9 else 4 if y > 3 else 5] if x not in (0, 15) else GOLD[2]), "gold")
    # pale silver (the lance's body): cool gradient, a sheen line at x 4, slight dark at the edges
    def silver(x, y):
        v = 4 - (y // 6)
        if x == 4 or x == 5:
            v = 5
        if x in (0, 15) or y in (0, 15):
            v -= 1
        return SILVER[max(1, v)]
    save(img(silver), "silver")
    # living-wood grip bound with diagonal leather-dark wraps and gold thread
    def grip(x, y):
        wrap = (x + y) % 5
        if wrap == 0:
            return WOOD[0]
        if wrap == 1:
            return GOLD[2] if (y % 10) < 2 else WOOD[1]
        return WOOD[3] if h2(x // 2, y, 7) > 0.6 else WOOD[2]
    save(img(grip), "grip")
    # cog: gold with darker tooth faces and a bright rim
    def cog(x, y):
        if y in (0, 1):
            return GOLD[5]
        if y in (14, 15):
            return GOLD[1]
        return GOLD[3] if (x // 4) % 2 == 0 else GOLD[2]
    save(img(cog), "cog")
    # gaia crystal (pink to white, facets)
    def gem(x, y):
        v = 3 + (1 if x < 8 else 0) + (1 if y < 4 else 0) - (1 if y > 11 else 0)
        if (x + y) % 7 == 0:
            v = 5
        return PINK[max(1, min(5, v))]
    save(img(gem), "gem")


def el(frm, to, tex, sides=("north", "south", "east", "west", "up", "down"), rotation=None, uvs=None):
    faces = {}
    for s in sides:
        f = {"texture": tex}
        f["uv"] = uv(s, frm, to)
        faces[s] = f
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def uv(face, a, b):
    """UVs from the element's own size, wrapped into the 16 px sprite (pieces are small)."""
    (x0, y0, z0), (x1, y1, z1) = a, b
    w = {"north": x1 - x0, "south": x1 - x0, "east": z1 - z0, "west": z1 - z0, "up": x1 - x0, "down": x1 - x0}[face]
    h = {"north": y1 - y0, "south": y1 - y0, "east": y1 - y0, "west": y1 - y0, "up": z1 - z0, "down": z1 - z0}[face]
    w, h = min(16, w), min(16, h)
    u0 = (16 - w) / 2
    v0 = 0 if face in ("up", "down") else max(0, min(16 - h, (32 - y1) % 16 if h < 16 else 0))
    return [round(u0, 3), round(v0, 3), round(u0 + w, 3), round(v0 + h, 3)]


SIDES = ("north", "south", "east", "west")


def lance_models():
    T = {"gold": NS + "gold", "silver": NS + "silver", "grip": NS + "grip", "cog": NS + "cog", "gem": NS + "gem",
         "particle": NS + "silver"}
    E = []
    # pommel, grip and its rings
    E.append(el([6.5, -15, 6.5], [9.5, -13, 9.5], "#gold"))
    E.append(el([7, -13, 7], [9, -2, 9], "#grip", SIDES))
    E.append(el([6.75, -10, 6.75], [9.25, -9, 9.25], "#gold"))
    E.append(el([6.75, -5.5, 6.75], [9.25, -4.5, 9.25], "#gold"))
    # stepped vamplate (hand guard)
    E.append(el([5.5, -2, 5.5], [10.5, -1, 10.5], "#gold"))
    E.append(el([4.5, -1, 4.5], [11.5, 1, 11.5], "#silver"))
    E.append(el([5, 1, 5], [11, 2, 11], "#gold"))
    E.append(el([6, 2, 6], [10, 3, 10], "#silver", SIDES + ("up",)))
    # shaft and collar
    E.append(el([7, 3, 7], [9, 18, 9], "#silver", SIDES))
    E.append(el([6.5, 18, 6.5], [9.5, 20, 9.5], "#gold"))
    # four-edged leaf blade: a broad flat blade (x) crossed by a narrower fin (z), tapering to the point
    for frm, to in (([5, 20, 7.25], [11, 23, 8.75]), ([5.5, 23, 7.25], [10.5, 26, 8.75]),
                    ([6.25, 26, 7.25], [9.75, 28.5, 8.75]), ([7, 28.5, 7.4], [9, 30.5, 8.6]), ([7.5, 30.5, 7.6], [8.5, 32, 8.4])):
        E.append(el(frm, to, "#silver"))
    E.append(el([7.25, 20.05, 5.5], [8.75, 24, 10.5], "#gold"))   # overhangs the collar: keeps its bottom face
    for frm, to in (([7.3, 24, 6.5], [8.7, 26.5, 9.5]), ([7.35, 26.5, 7.0], [8.65, 28.2, 9.0])):
        E.append(el(frm, to, "#gold", ("north", "south", "east", "west", "up")))  # bottoms rest inside the fin below
    item = {"parent": "minecraft:block/block", "textures": T, "elements": E, "gui_light": "front",
            "display": {
                "gui": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.34, 0.34, 0.34]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
                "fixed": {"rotation": [0, 0, -45], "translation": [0, 0, 0], "scale": [0.45, 0.45, 0.45]},
                "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [0.6, 0.6, 0.6]},
                # held: the grip in the fist, the lance couched forward and slightly up
                "thirdperson_righthand": {"rotation": [-80, 0, 0], "translation": [0, 1.5, -8.5], "scale": [0.85, 0.85, 0.85]},
                "thirdperson_lefthand": {"rotation": [-80, 0, 0], "translation": [0, 1.5, -8.5], "scale": [0.85, 0.85, 0.85]},
                "firstperson_righthand": {"rotation": [-70, -8, 8], "translation": [1, -1, -6], "scale": [0.7, 0.7, 0.7]},
                "firstperson_lefthand": {"rotation": [-70, 8, -8], "translation": [-1, -1, -6], "scale": [0.7, 0.7, 0.7]},
            }}
    with open(os.path.join(MOD, "item.json"), "w") as fh:
        json.dump(item, fh, indent=2)

    # the cog: a disc with eight teeth (four straight, four turned 45 degrees), floating just over the vamplate
    C = [el([5.5, 3.5, 5.5], [10.5, 5, 10.5], "#cog")]
    teeth = [([7.25, 3.6, 4], [8.75, 4.9, 5.5], "south"), ([7.25, 3.6, 10.5], [8.75, 4.9, 12], "north"),
             ([4, 3.6, 7.25], [5.5, 4.9, 8.75], "east"), ([10.5, 3.6, 7.25], [12, 4.9, 8.75], "west")]
    ALL6 = ("north", "south", "east", "west", "up", "down")
    for frm, to, inner in teeth:
        C.append(el(frm, to, "#cog", tuple(f for f in ALL6 if f != inner)))
    for frm, to, inner in teeth:
        C.append(el(frm, to, "#cog", rotation={"angle": 45, "axis": "y", "origin": [8, 4.25, 8]}))
    with open(os.path.join(MOD, "cog.json"), "w") as fh:
        json.dump({"parent": "minecraft:block/block", "textures": T, "elements": C}, fh, indent=2)

    # crystal: strips up the four sides of the shaft, a band in the collar, inlays in the blade's flats
    G = []
    for frm, to, skip in (([6.75, 6, 7.5], [7, 16, 8.5], "east"), ([9, 6, 7.5], [9.25, 16, 8.5], "west"),
                          ([7.5, 6, 6.75], [8.5, 16, 7], "south"), ([7.5, 6, 9], [8.5, 16, 9.25], "north")):
        G.append(el(frm, to, "#gem", tuple(s for s in ("north", "south", "east", "west", "up", "down") if s != skip)))
    for z0, z1, skip in ((7.1, 7.25, "south"), (8.75, 8.9, "north")):
        for x0, x1 in ((5.6, 7.1), (8.9, 10.4)):
            G.append(el([x0, 20.6, z0], [x1, 22.4, z1], "#gem", tuple(s for s in ("north", "south", "east", "west", "up", "down") if s != skip)))
    with open(os.path.join(MOD, "gem.json"), "w") as fh:
        json.dump({"parent": "minecraft:block/block", "textures": T, "elements": G}, fh, indent=2)


if __name__ == "__main__":
    textures()
    lance_models()
    print("lance art written")
