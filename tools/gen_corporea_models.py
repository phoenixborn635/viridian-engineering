#!/usr/bin/env python3
"""
Shaped (non-cube) block models for the Corporea ports and crafting terminal.
Every model faces NORTH (the front); the blockstates rotate it. Hitboxes in
content/corporea/CorporeaShapes.java mirror these boxes, so change both together.

Usage: gen_corporea_models.py <assets/viridian_engineering dir>
"""
import json
import os
import sys


OUT = sys.argv[1] if len(sys.argv) > 1 else "."
MODELS = os.path.join(OUT, "models", "block")
NS = "viridian_engineering:block/"
ALL = ("north", "south", "east", "west", "up", "down")


def el(frm, to, faces, rotation=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def faces(tex, sides=ALL, cull=None, **override):
    out = {}
    for s in sides:
        f = {"texture": tex}
        if cull and s in cull:
            f["cullface"] = s
        f.update(override.get(s, {}))
        out[s] = f
    return out


def write(name, textures, elements):
    textures = dict(textures)
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    with open(os.path.join(MODELS, name + ".json"), "w") as fh:
        json.dump(model, fh, indent=2)
    print("wrote", name)


PIPE_TEX = NS + "corporea_pipes"


def pipe_arm(length, z0=0, tex="#pipe"):
    """Use the same continuous face strip as the pipe's straight attachment."""
    v = length / 2
    faces = {
        "east": {"texture": tex, "uv": [0, 0, 4, v], "rotation": 90},
        "west": {"texture": tex, "uv": [0, v, 4, 0], "rotation": 90},
        "up": {"texture": tex, "uv": [0, v, 4, 0], "rotation": 180},
        "down": {"texture": tex, "uv": [0, 0, 4, v], "rotation": 180},
    }
    return el([4, 4, z0], [12, 12, z0 + length], faces)


def port(kind):
    mouth = "corporea_bridge_port_mouth" if kind == "bridge" else "corporea_port_mouth"
    tex = {
        "mouth": NS + mouth, "frame": NS + "corporea_port_frame",
        "body": NS + f"corporea_{kind}_port_body", "pipe": PIPE_TEX,
        "particle": NS + "corporea_port_frame",
    }
    plate = faces("#frame", cull=("north",))
    plate["north"]["texture"] = "#mouth"
    body = faces("#body", sides=("south", "east", "west", "up", "down"))
    neck = pipe_arm(5, z0=11)
    # Use the pipe end texture so an unconnected neck has a visible rim and bore.
    neck["faces"]["south"] = {"texture": "#pipe", "uv": [11, 11, 16, 16], "cullface": "south"}
    write(f"corporea_{kind}_port", tex, [
        el([1, 1, 0], [15, 15, 3], plate),      # wooden mouth plate against the inventory
        el([3, 3, 3], [13, 13, 11], body),      # living-rock body with the type mark
        neck,                                   # pipe-sized neck (8 px, Create pipe size) meeting the pipe's straight attachment
    ])


def terminal():
    tex = {
        "frame": NS + "corporea_terminal_frame", "screen": NS + "corporea_terminal_screen",
        "housing": NS + "corporea_terminal_housing", "back": NS + "corporea_terminal_back",
        "crystal": NS + "corporea_crystal", "particle": NS + "corporea_terminal_frame",
    }
    housing = faces("#housing", sides=("south", "east", "west", "up", "down"))  # no cullface: back sits inside the block
    housing["south"]["texture"] = "#back"
    bar = lambda cull: faces("#frame", cull=cull)
    glow = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
    write("corporea_crafting_terminal", tex, [
        el([1, 1, 8], [15, 15, 14], housing),                     # rear housing; the spinning half shaft pokes out behind it
        el([0, 13, 3], [16, 16, 8], bar(("up", "east", "west"))),   # frame: top
        el([0, 0, 3], [16, 3, 8], bar(("down", "east", "west"))),   # frame: bottom
        el([0, 3, 3], [3, 13, 8], bar(("west",))),                  # frame: left
        el([13, 3, 3], [16, 13, 8], bar(("east",))),                # frame: right
        el([3, 3, 6], [13, 13, 7], {"north": {"texture": "#screen", "neoforge_data": glow}}),
        el([6.5, 11.5, 1], [9.5, 14.5, 3], faces("#crystal", sides=("north", "east", "west", "up", "down")),
           rotation={"angle": 45, "axis": "z", "origin": [8, 13, 2]}),  # crystal set in the top bar
    ])


def port_stub():
    """Short pipe stub drawn on a port's side when a corporea pipe joins there."""
    tex = {"pipe": PIPE_TEX, "particle": PIPE_TEX}
    write("corporea_port_stub", tex, [pipe_arm(3)])


ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270},
       "up": {"x": 270}, "down": {"x": 90}}


def socket():
    """An open collar around a straight stem, with its outer face ahead of the pedestal face."""
    tex = {"frame": NS + "corporea_port_frame", "pipe": PIPE_TEX, "particle": PIPE_TEX}
    outer_min, outer_max = 2.875, 13.125
    hole_min, hole_max = 3.875, 12.125

    def bar(x0, y0, x1, y1, sides):
        return el([x0, y0, -.625], [x1, y1, .5], {
            side: {"texture": "#frame", "uv": [4, 4, 12, 12]} for side in sides
        })

    write("corporea_socket", tex, [
        pipe_arm(1.25),
        bar(outer_min, outer_min, outer_max, hole_min, ("north", "south", "east", "west", "down")),
        bar(outer_min, hole_max, outer_max, outer_max, ("north", "south", "east", "west", "up")),
        bar(outer_min, hole_min, hole_min, hole_max, ("north", "south", "east", "west")),
        bar(hole_max, hole_min, outer_max, hole_max, ("north", "south", "east", "west")),
        # Inner faces across the top and bottom of the opening.
        bar(hole_min, outer_min, hole_max, hole_min, ("up",)),
        bar(hole_min, hole_max, hole_max, outer_max, ("down",)),
    ])


def terminal_blockstate():
    rot = {"north": (0, 0), "east": (0, 90), "south": (0, 180), "west": (0, 270), "up": (270, 0), "down": (90, 0)}
    parts = []
    for facing, (x, y) in rot.items():
        parts.append({"when": {"facing": facing}, "apply": {"model": NS + "corporea_crafting_terminal", "x": x, "y": y}})
    for side, r in ROT.items():
        parts.append({"when": {side: "true"}, "apply": dict({"model": NS + "corporea_socket"}, **r)})
    with open(os.path.join(OUT, "blockstates", "corporea_crafting_terminal.json"), "w") as fh:
        json.dump({"multipart": parts}, fh, indent=2)
    print("wrote blockstate terminal")


def port_blockstate(kind):
    parts = []
    for facing, r in ROT.items():
        parts.append({"when": {"facing": facing}, "apply": dict({"model": NS + f"corporea_{kind}_port"}, **r)})
    for side, r in ROT.items():
        parts.append({"when": {side: "true"}, "apply": dict({"model": NS + "corporea_port_stub"}, **r)})
    path = os.path.join(OUT, "blockstates", f"corporea_{kind}_port.json")
    with open(path, "w") as fh:
        json.dump({"multipart": parts}, fh, indent=2)
    print("wrote blockstate", kind)


def main():
    for k in ("input", "output", "machine", "bridge"):
        port(k)
        port_blockstate(k)
    port_stub()
    terminal()
    socket()
    terminal_blockstate()


if __name__ == "__main__":
    main()
