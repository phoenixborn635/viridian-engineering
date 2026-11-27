#!/usr/bin/env python3
"""
Terrasteel mana pipe + Corporea pipe: the Manasteel pipe's look at a smaller size (6px tube),
with a solid light line down the middle and a brighter pulse that travels along it.

Construction (like Create's fluid pipe):
  * arms (one model per direction, no blockstate rotation) and straight centre faces take their
    slice of a "strip" texture by WORLD position along the pipe axis, so the pulse slides
    continuously along a whole run, always towards +X / +Y / +Z;
  * bends / junctions / end caps use connection-aware cells (mitred joins);
  * a pipe end (exactly one connection) gets an arm + flange on its open side, like manasteel.

Sheet 64x64 per frame (uv unit = 4px; 2 texels per block pixel, same density as manasteel):
  strip, length along v : px (0,0)   12x32  -> uv [0,0,3,8]     (v = block pixel / 2)
  strip, length along u : px (0,48)  32x12  -> uv [0,12,8,15]
  cell m (0..15)        : px (16+12c, 12r)  -> uv [4+3c, 3r, 7+3c, 3r+3]
      bit 1 = L (u-), 2 = R (u+), 4 = U (v-), 8 = D (v+)
  flange face           : px (32,48) 16x16 -> uv [8,12,12,16]
  flange edge           : px (48,48) 16x3  -> uv [12,12,16,12.75]

Usage: gen_small_pipes.py <assets/viridian_engineering dir>
"""
import json
import os
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
BLOCK = os.path.join(OUT, "textures", "block")
MODELS = os.path.join(OUT, "models", "block")
STATES = os.path.join(OUT, "blockstates")
NS = "viridian_engineering:block/"


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


PIPES = {
    # edge, shadow, body, light, pulse  (same roles as the Manasteel palette)
    "terrasteel_mana_pipe": ["#123f17", "#22702a", "#3fa63a", "#83de5f", "#f0ffe2"],
    "corporea_pipe": ["#1a0a28", "#3a1856", "#6a2c94", "#a560d4", "#fbeaff"],
}
FRAMES = 16          # pulse moves 2 texels (1 block pixel) per frame -> one block per cycle
FRAMETIME = 2
N = 12               # texels across the tube
L, R, U, D = 1, 2, 4, 8


def profile(edge, shadow, body, light, line):
    #       0     1       2     3     4      5     6     7      8     9     10      11
    return [edge, shadow, body, body, body, line, line, body, body, body, shadow, edge]


def line_colour(pal, p, frame):
    """Centre line at strip position p (texels, 0..31 across one block)."""
    edge, shadow, body, light, pulse = pal
    head = (frame * 2) % 32
    d = (p - head) % 32           # distance behind the pulse head
    if d < 2:
        return pulse
    if d < 8:
        return mix(pulse, light, (d - 1) / 7)
    return light


def strip(px, ox, oy, pal, frame, along_v):
    edge, shadow, body, light, pulse = pal
    for p in range(32):
        prof = profile(edge, shadow, body, light, line_colour(pal, p, frame))
        for c in range(N):
            if along_v:
                px[ox + c, oy + p] = prof[c]
            else:
                px[ox + p, oy + c] = prof[c]


def cell(px, ox, oy, mask, pal):
    """Bends / junctions / ends: each pixel shows the tube from the nearest open side (mitred).
    The line stays at its resting brightness here."""
    edge, shadow, body, light, pulse = pal
    prof = profile(edge, shadow, body, light, light)
    single = mask in (L, R, U, D)
    for y in range(N):
        for x in range(N):
            if not mask:
                ring = min(x, y, N - 1 - x, N - 1 - y)
                c = [edge, shadow, light, body, shadow, edge][ring]
            else:
                best = None
                for bit, dist, horizontal in ((L, x, True), (R, N - 1 - x, True), (U, y, False), (D, N - 1 - y, False)):
                    if mask & bit and (best is None or dist < best[0]):
                        best = (dist, horizontal)
                c = prof[y] if best[1] else prof[x]
                if single:
                    far = {L: N - 1 - x, R: x, U: N - 1 - y, D: y}[mask]
                    across = y if mask in (L, R) else x
                    if far == 0:
                        c = edge
                    elif far == 1 and 0 < across < N - 1:
                        c = shadow
            px[ox + x, oy + y] = c


def flange(px, pal):
    edge, shadow, body, light, pulse = pal
    for y in range(16):
        for x in range(16):
            ring = min(x, y, 15 - x, 15 - y)
            c = [edge, light, light, body][ring] if ring < 4 else edge if ring == 4 else shadow
            px[32 + x, 48 + y] = c
    for x in range(16):
        px[48 + x, 48] = edge
        px[48 + x, 49] = light
        px[48 + x, 50] = edge


def sheet(name, pal_hex):
    pal = [hexc(c) for c in pal_hex]
    out = Image.new("RGBA", (64, 64 * FRAMES))
    for f in range(FRAMES):
        img = Image.new("RGBA", (64, 64))
        px = img.load()
        strip(px, 0, 0, pal, f, along_v=True)
        strip(px, 0, 48, pal, f, along_v=False)
        for m in range(16):
            cell(px, 16 + 12 * (m % 4), 12 * (m // 4), m, pal)
        flange(px, pal)
        out.paste(img, (0, 64 * f))
    out.save(os.path.join(BLOCK, name + ".png"))
    with open(os.path.join(BLOCK, name + ".png.mcmeta"), "w") as fh:
        fh.write('{\n  "animation": {\n    "frametime": %d,\n    "interpolate": false\n  }\n}\n' % FRAMETIME)
    print("wrote texture", name)


# ---------------------------------------------------------------- geometry helpers
VEC = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
# texture u+ / v+ as world directions, per face (vanilla auto-uv orientation)
FACE_UV = {
    "north": ("west", "down"), "south": ("east", "down"),
    "east": ("north", "down"), "west": ("south", "down"),
    "up": ("east", "south"), "down": ("east", "north"),
}
FACE_DIRS = {  # face-local L, R, U, D as world directions
    f: (OPP[u], u, OPP[v], v) for f, (u, v) in FACE_UV.items()
}
AXIS = {"x": 0, "y": 1, "z": 2}


def axis_of(d):
    return "xyz"[[abs(c) for c in VEC[d]].index(1)]


def tube_faces(axis, c0, c1, tex="#0"):
    """Side faces of a 6px tube along `axis` from block pixel c0 to c1, with strip uvs by world position."""
    faces = {}
    a0, a1 = c0 / 2, c1 / 2
    for f, (u, v) in FACE_UV.items():
        if axis_of(f) == axis:
            continue
        if axis_of(u) == axis:
            lo, hi = (a0, a1) if VEC[u][AXIS[axis]] > 0 else (a1, a0)
            faces[f] = {"uv": [lo, 12, hi, 15], "texture": tex}
        else:
            lo, hi = (a0, a1) if VEC[v][AXIS[axis]] > 0 else (a1, a0)
            faces[f] = {"uv": [0, lo, 3, hi], "texture": tex}
    return faces


def box(axis, c0, c1, lo=5, hi=11):
    frm, to = [lo, lo, lo], [hi, hi, hi]
    frm[AXIS[axis]], to[AXIS[axis]] = c0, c1
    return frm, to


def arm_piece(direction, tex="#0"):
    ax = axis_of(direction)
    c0, c1 = (11, 16) if VEC[direction][AXIS[ax]] > 0 else (0, 5)
    frm, to = box(ax, c0, c1)
    return {"from": frm, "to": to, "faces": tube_faces(ax, c0, c1, tex)}


def flange_piece(direction, tex="#0"):
    ax = axis_of(direction)
    c0, c1 = (14.5, 16) if VEC[direction][AXIS[ax]] > 0 else (0, 1.5)
    frm, to = box(ax, c0, c1, 4, 12)
    faces = {}
    for f, (u, v) in FACE_UV.items():
        if axis_of(f) == ax:
            faces[f] = {"uv": [8, 12, 12, 16], "texture": tex}
        else:  # thickness runs along the pipe axis
            faces[f] = {"uv": [12, 12, 16, 12.75], "texture": tex}
            if axis_of(u) == ax:
                faces[f]["rotation"] = 90
    faces[direction]["cullface"] = direction
    return {"from": frm, "to": to, "faces": faces}


def arm_element(length=5, tex="#0", z0=0):
    """Generic north-model tube piece along z (used by the Corporea port models)."""
    frm, to = box("z", z0, z0 + length)
    return {"from": frm, "to": to, "faces": tube_faces("z", z0, z0 + length, tex)}


def cell_uv(m):
    c, r = m % 4, m // 4
    return [4 + 3 * c, 3 * r, 7 + 3 * c, 3 * r + 3]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2)


def pipe(name):
    tex = {"0": NS + name, "particle": NS + name}
    base = os.path.join(MODELS, name)
    if os.path.isdir(base):
        for old in os.listdir(base):
            os.remove(os.path.join(base, old))
    parts = []

    def model(path, elements, extra=None):
        data = {"textures": tex, "elements": elements}
        if extra:
            data.update(extra)
        write_json(os.path.join(MODELS, name, path + ".json"), data)
        return NS + name + "/" + path

    # centre faces
    for face, dirs in FACE_DIRS.items():
        for m in range(16):
            when = {d: ("true" if m & bit else "false") for d, bit in zip(dirs, (L, R, U, D))}
            when[face] = "false"
            if m in (L | R, U | D):  # straight through this face: use the travelling strip
                ax = axis_of(dirs[0] if m == L | R else dirs[2])
                f = tube_faces(ax, 5, 11)[face]
            else:
                f = {"uv": cell_uv(m), "texture": "#0"}
            ref = model(f"face_{face}_{m}", [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": {face: f}}])
            parts.append({"when": when, "apply": {"model": ref}})
    # arms, and arm + flange on the open side of a pipe end
    for d in VEC:
        parts.append({"when": {d: "true"}, "apply": {"model": model(f"arm_{d}", [arm_piece(d)])}})
        when = {x: "false" for x in VEC}
        when[OPP[d]] = "true"
        parts.append({"when": when, "apply": {"model": model(f"end_{d}", [arm_piece(d), flange_piece(d)])}})
    write_json(os.path.join(STATES, name + ".json"), {"multipart": parts})
    # item: a north-south length with flanges at both ends
    core = {f: tube_faces("z", 5, 11)[f] for f in ("east", "west", "up", "down")}
    model("item", [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": core},
                   arm_piece("north"), arm_piece("south"), flange_piece("north"), flange_piece("south")],
          {"parent": "block/block"})
    print("wrote models + blockstate", name)


def main():
    for name, pal in PIPES.items():
        sheet(name, pal)
        pipe(name)


if __name__ == "__main__":
    main()
