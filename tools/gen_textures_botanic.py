#!/usr/bin/env python3
"""
"Botania-first" restyle textures (2026-11-24): living-wood and living-rock bodies with metal only
as trim and glowing crystal inlays, for the shaped (non-cube) Corporea models.
100% original procedural pixel art; palettes are only *inspired* by Botania's materials.

Usage: gen_textures_botanic.py <assets/viridian_engineering dir>
"""
import os
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
BLOCK = os.path.join(OUT, "textures", "block")
os.makedirs(BLOCK, exist_ok=True)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def h2(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


WOOD = [hexc(c) for c in ("#3b2418", "#5a3826", "#764b33", "#946345", "#b07e5a")]   # seam..highlight
ROCK = [hexc(c) for c in ("#6f6c66", "#8f8c84", "#aeaba2", "#c9c6bd", "#e2dfd6")]
VIOLET = [hexc(c) for c in ("#160a22", "#2e1446", "#5a2687", "#9b55d6", "#d9a8ff", "#fbefff")]


def tile():
    return Image.new("RGBA", (16, 16))


def wood(img, seed=1, vertical=True):
    """Living-wood: long wavy grain, darker seams every 4-5px."""
    px = img.load()
    for y in range(16):
        for x in range(16):
            a, b = (x, y) if vertical else (y, x)
            wave = (b // 5 + seed) % 2
            seam = (a + wave) % 5 == 0
            n = h2(a, b // 3, seed)
            c = WOOD[1] if seam else WOOD[3] if n > 0.72 else WOOD[2]
            if not seam and h2(a, b, seed + 9) > 0.93:
                c = WOOD[4]
            px[x, y] = c


def rock(img, seed=2):
    """Living-rock: pale, smooth stone with soft blotches and a few hairline cracks."""
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = (h2(x // 2, y // 2, seed) + h2(x, y, seed + 3)) / 2
            c = ROCK[3] if n > 0.55 else ROCK[2] if n > 0.25 else ROCK[1]
            if h2(x, y, seed + 7) > 0.96:
                c = ROCK[4]
            px[x, y] = c
    for i in range(5):  # crack
        x, y = 3 + i, 11 - (i * 3) // 4 - (i % 2)
        px[x % 16, y % 16] = ROCK[0]


def frame(img, c_out, c_in, inset=0):
    px = img.load()
    a, b = inset, 15 - inset
    for i in range(a, b + 1):
        px[i, a] = c_out; px[i, b] = c_out; px[a, i] = c_out; px[b, i] = c_out
    for i in range(a + 1, b):
        px[i, a + 1] = c_in; px[a + 1, i] = c_in


def gem(px, x, y):
    px[x, y] = VIOLET[4]


def glyph(img, rows, ox, oy, col):
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                px[ox + x, oy + y] = col


def save(img, name):
    img.save(os.path.join(BLOCK, name + ".png"))
    print("wrote", name)


# ------------------------------------------------------------------ Corporea ports
def port_frame():
    """Back and edges of the wooden mouth plate."""
    img = tile(); wood(img, 4)
    frame(img, WOOD[0], WOOD[4])
    px = img.load()
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        gem(px, x, y)
    save(img, "corporea_port_frame")


def port_mouth(name, kind):
    """Front of the plate (the face that touches the inventory / holds the spark)."""
    img = tile(); wood(img, 6, vertical=False)
    frame(img, WOOD[0], WOOD[4])
    px = img.load()
    if kind == "iris":
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d <= 5.6:
                    px[x, y] = VIOLET[2] if d > 4.4 else VIOLET[1] if d > 2.4 else VIOLET[0]
        px[6, 6] = VIOLET[4]; px[7, 7] = VIOLET[3]; px[9, 9] = VIOLET[3]
    else:  # bridge: diamond socket where a native corporea spark sits
        for y in range(16):
            for x in range(16):
                d = abs(x - 7.5) + abs(y - 7.5)
                if d <= 5:
                    px[x, y] = VIOLET[0] if d <= 2 else ROCK[3] if d <= 3 else VIOLET[2]
        px[7, 7] = VIOLET[4]; px[8, 8] = VIOLET[5]
    save(img, name)


PORT_GLYPHS = {
    "input":   ["..#..", "..#..", "#.#.#", ".###.", "..#.."],     # into the network
    "output":  ["..#..", ".###.", "#.#.#", "..#..", "..#.."],     # out of the network
    "machine": [".#.#.", "#####", "##.##", "#####", ".#.#."],     # cog
    "bridge":  ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],     # crossing links
}


def port_body(kind):
    """Living-rock body; side faces use uv window [3,4,13,12], back face [3,3,13,13]."""
    img = tile(); rock(img, 10 + len(kind))
    px = img.load()
    for x in range(16):          # violet vein band around the body (rows 4 and 11 = face edges)
        px[x, 4] = VIOLET[2]; px[x, 11] = VIOLET[2]
    for y in range(4, 12):
        px[3, y] = ROCK[0]; px[12, y] = ROCK[0]
    for y in range(5, 11):       # glyph plate
        for x in range(5, 11):
            px[x, y] = VIOLET[1]
    glyph(img, PORT_GLYPHS[kind], 6, 5 + 0, VIOLET[4])
    save(img, f"corporea_{kind}_port_body")


# ------------------------------------------------------------------ Corporea crafting terminal
def terminal():
    # wooden frame bars (front frame of the console)
    f = tile(); wood(f, 20, vertical=False)
    frame(f, WOOD[0], WOOD[4])
    px = f.load()
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        gem(px, x, y)
    save(f, "corporea_terminal_frame")

    # screen: dark violet glass, glowing 3x3 grid, slight scanline shimmer
    s = tile(); px = s.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = VIOLET[1] if (y % 3 == 0) else VIOLET[0]
    for gy in range(3):
        for gx in range(3):
            x0, y0 = 3 + gx * 4, 3 + gy * 4
            for yy in range(y0, y0 + 2):
                for xx in range(x0, x0 + 2):
                    px[xx, yy] = VIOLET[3]
            px[x0, y0] = VIOLET[4]
    px[7, 7] = VIOLET[5]
    save(s, "corporea_terminal_screen")

    # rear housing: living-rock with a wood band and a bearing where the shaft enters
    h = tile(); rock(h, 30)
    px = h.load()
    for x in range(16):
        px[x, 2] = WOOD[2]; px[x, 3] = WOOD[1]; px[x, 12] = WOOD[1]; px[x, 13] = WOOD[2]
    save(h, "corporea_terminal_housing")
    b = tile(); rock(b, 31)
    px = b.load()
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 4.6:
                px[x, y] = WOOD[0] if d <= 2.2 else WOOD[3] if d <= 3.4 else WOOD[1]
    save(b, "corporea_terminal_back")

    # crystal on top of the frame
    c = tile(); px = c.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = VIOLET[3] if (x + y) % 5 else VIOLET[4]
            if x in (0, 15) or y in (0, 15):
                px[x, y] = VIOLET[2]
    for i in range(2, 6):
        px[i, i] = VIOLET[5]
    save(c, "corporea_crystal")


def main():
    port_frame()
    port_mouth("corporea_port_mouth", "iris")
    port_mouth("corporea_bridge_port_mouth", "socket")
    for k in PORT_GLYPHS:
        port_body(k)
    terminal()


if __name__ == "__main__":
    main()
