#!/usr/bin/env python3
"""
Corporea Archive Pylon pedestal (Phase 3): textures, block model, per-bay glow models and the
multipart blockstate. Botania-first style (living-rock body, living-wood trim, violet crystal),
100% original procedural pixel art. The floating crystal above it is VEPylonModel (BER).

Geometry (model faces NORTH; hitbox in ArchivePylonBlock.SHAPE):
  plinth [0,0,0]-[16,2,16]   body [1,2,1]-[15,11,15]   cap [0,11,0]-[16,13,16]
  four crystal studs on the cap corners; four drive bays on the front, slot 0 on the viewer's left.

Usage: gen_archive_pylon.py <assets/viridian_engineering dir>
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_textures_botanic as bt  # noqa: E402  (palettes and wood/rock painters)

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
MODELS = os.path.join(OUT, "models", "block")
STATES = os.path.join(OUT, "blockstates")
BT_BLOCK = os.path.join(OUT, "textures", "block")
NS = "viridian_engineering:block/"
os.makedirs(MODELS, exist_ok=True)
os.makedirs(STATES, exist_ok=True)
os.makedirs(BT_BLOCK, exist_ok=True)
bt.BLOCK = BT_BLOCK

# Texture columns of the four bays on the front face (2 px wide), and their rows.
BAY_COLS = (2, 5, 9, 12)
BAY_ROWS = range(6, 13)


# ------------------------------------------------------------------ textures
def textures():
    V, R, W = bt.VIOLET, bt.ROCK, bt.WOOD

    body = bt.tile(); bt.rock(body, 40)
    px = body.load()
    for x in range(16):                      # violet veins just under the cap and above the plinth
        px[x, 6] = V[2]; px[x, 13] = V[1]
    for y in range(6, 14, 2):
        px[7, y] = V[2]; px[8, y + 1] = V[3]
    bt.save(body, "corporea_archive_pylon_body")

    front = bt.tile(); bt.rock(front, 41)
    px = front.load()
    for x in range(1, 15):                   # wooden bay surround
        px[x, 5] = W[1]; px[x, 13] = W[1]
    for y in range(5, 14):
        px[1, y] = W[1]; px[14, y] = W[1]
    for c in BAY_COLS:                       # dark empty bays with a faint rim
        for y in BAY_ROWS:
            px[c, y] = V[0]; px[c + 1, y] = V[0]
        px[c, 6] = V[1]; px[c + 1, 12] = V[1]
    bt.save(front, "corporea_archive_pylon_front")

    lit = bt.tile(); px = lit.load()         # a mounted drive: glowing violet crystal strip
    for y in range(16):
        for x in range(16):
            px[x, y] = V[4] if (x + y) % 4 else V[5]
            if x % 2 == 1 and y % 5 == 0:
                px[x, y] = V[3]
    bt.save(lit, "corporea_archive_pylon_bay_lit")

    trim = bt.tile(); bt.wood(trim, 42, vertical=False)
    bt.frame(trim, W[0], W[4])
    bt.save(trim, "corporea_archive_pylon_trim")

    top = bt.tile(); bt.wood(top, 43)
    bt.frame(top, W[0], W[4])
    px = top.load()
    for y in range(16):                      # crystal socket the floating pylon hovers over
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 4.8:
                px[x, y] = V[3] if d > 3.6 else R[3] if d > 2.6 else V[1]
    px[7, 7] = V[4]; px[8, 8] = V[5]
    bt.save(top, "corporea_archive_pylon_top")


# ------------------------------------------------------------------ models
def el(frm, to, faces, rotation=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def all_faces(tex, cull=(), skip=()):
    out = {}
    for s in ("north", "south", "east", "west", "up", "down"):
        if s in skip:
            continue
        out[s] = {"texture": tex}
        if s in cull:
            out[s]["cullface"] = s
    return out


def write_model(name, model):
    with open(os.path.join(MODELS, name + ".json"), "w") as fh:
        json.dump(model, fh, indent=2)
    print("wrote model", name)


def models():
    tex = {
        "body": NS + "corporea_archive_pylon_body", "front": NS + "corporea_archive_pylon_front",
        "trim": NS + "corporea_archive_pylon_trim", "top": NS + "corporea_archive_pylon_top",
        "crystal": NS + "corporea_crystal", "particle": NS + "corporea_archive_pylon_body",
    }
    plinth = all_faces("#trim", cull=("down", "north", "south", "east", "west"))
    body = all_faces("#body", skip=("up", "down"))
    body["north"] = {"texture": "#front", "uv": [1, 5, 15, 14]}
    cap = all_faces("#trim", cull=("north", "south", "east", "west"))
    cap["up"] = {"texture": "#top"}
    elements = [
        el([0, 0, 0], [16, 2, 16], plinth),
        el([1, 2, 1], [15, 11, 15], body),
        el([0, 11, 0], [16, 13, 16], cap),
    ]
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):   # crystal studs on the cap corners
        elements.append(el([x, 13, z], [x + 2, 15, z + 2], all_faces("#crystal", skip=("down",)),
                           rotation={"angle": 45, "axis": "y", "origin": [x + 1, 14, z + 1]}))
    write_model("corporea_archive_pylon", {"parent": "minecraft:block/block", "textures": tex, "elements": elements})

    glow = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    for i, c in enumerate(BAY_COLS):
        x0, x1 = 16 - c - 2, 16 - c          # north-face texture column c maps to world x 16-c-1
        write_model(f"corporea_archive_pylon_bay_{i}", {
            "parent": "minecraft:block/block",
            "textures": {"lit": NS + "corporea_archive_pylon_bay_lit", "particle": NS + "corporea_archive_pylon_bay_lit"},
            "elements": [el([x0, 3, 0.95], [x1, 10, 1],
                            {"north": {"texture": "#lit", "uv": [c, 6, c + 2, 13], "neoforge_data": glow}})],
        })


ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def blockstate():
    parts = []
    for facing, y in ROT.items():
        apply = {"model": NS + "corporea_archive_pylon"}
        if y:
            apply["y"] = y
        parts.append({"when": {"facing": facing}, "apply": apply})
    for facing, y in ROT.items():
        for i in range(4):
            apply = {"model": NS + f"corporea_archive_pylon_bay_{i}"}
            if y:
                apply["y"] = y
            parts.append({"when": {"facing": facing, f"bay_{i}": "true"}, "apply": apply})
    sides = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "down": {"x": 90}}
    for side, r in sides.items():   # pipe sockets (the block only sets these on allowed sides)
        parts.append({"when": {side: "true"}, "apply": dict({"model": NS + "corporea_socket"}, **r)})
    with open(os.path.join(STATES, "corporea_archive_pylon.json"), "w") as fh:
        json.dump({"multipart": parts}, fh, indent=2)
    print("wrote blockstate corporea_archive_pylon")


if __name__ == "__main__":
    textures()
    models()
    blockstate()
