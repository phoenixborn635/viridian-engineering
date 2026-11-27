#!/usr/bin/env python3
"""
Procedural, 100% original pixel art for Viridian Engineering (spec section 6).
Nothing here is read from, traced from or recoloured from another mod's textures;
palettes are only *inspired* by Botania's metals (manasteel blue, elementium pink,
terrasteel green) and its living/dream woods.

Usage: gen_textures.py <assets/viridian_engineering dir>
"""
import os
import sys
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
BLOCK = os.path.join(OUT, "textures", "block")
ITEM = os.path.join(OUT, "textures", "item")
os.makedirs(BLOCK, exist_ok=True)
os.makedirs(ITEM, exist_ok=True)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def h2(x, y, seed):
    """Deterministic per-pixel hash in [0,1)."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


# ----------------------------------------------------------------- palettes
METALS = {
    # dark rim, shadow, body, light, sparkle
    "manasteel": ["#132a63", "#24489b", "#3a6fd1", "#74a8f2", "#d4ecff"],
    "elementium": ["#5a1d52", "#923a84", "#cc5fb7", "#ee9adc", "#ffe1f7"],
    "terrasteel": ["#123f17", "#22702a", "#3fa63a", "#83de5f", "#e2ffc6"],
    "corporea": ["#1c0a2e", "#3d1764", "#6a2fae", "#a66be6", "#f0dcff"],
}
# pipe bore colours (deep, mid) per tier
HOLES = {
    "manasteel": ("#0b1430", "#162a5c"),
    "terrasteel": ("#06200c", "#0f3a18"),
    "corporea": ("#0e0418", "#22093a"),
}
WOODS = {
    # seam, dark grain, base, light
    "manasteel": ["#7d7461", "#a79d84", "#c9c0a6", "#e2dbc4"],   # pale living wood
    "elementium": ["#6b7a86", "#93a5b2", "#b8cad6", "#d9e7ef"],  # cool dream wood
    "terrasteel": ["#6f716a", "#979a91", "#bdbfb5", "#dcdcd2"],  # living rock plates
}


# ----------------------------------------------------------------- casing tiles
def casing_interior(px, tier, seed):
    seam, dark, base, light = [hexc(c) for c in WOODS[tier]]
    for y in range(16):
        for x in range(16):
            r = h2(x, y, seed)
            c = base
            if r < 0.18:
                c = dark
            elif r > 0.86:
                c = light
            if tier == "terrasteel":
                # 8x8 stone plates with bevel; tiles seamlessly at 16
                if x % 8 == 0 or y % 8 == 0:
                    c = seam
                elif x % 8 == 1 or y % 8 == 1:
                    c = light
            else:
                # staggered planks, 4 px tall, seamless across tiles
                row = y // 4
                offset = 0 if row % 2 == 0 else 8
                if y % 4 == 3:
                    c = seam
                elif (x + offset) % 16 == 0:
                    c = seam
                elif (x + offset) % 16 == 1 and y % 4 != 3:
                    c = light if r > 0.4 else base
            px[x, y] = c


def frame_px(px, x, y, tone):
    px[x, y] = tone


def draw_casing_tile(tier, up, down, left, right, tl, tr, bl, br, seed=7):
    """Connection flags follow Create's CTContext (true = connected on that side)."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    casing_interior(px, tier, seed)
    rim, shadow, body, light, spark = [hexc(c) for c in METALS[tier]]

    # straight frame edges (2 px): outer dark rim, inner metal with bevel
    for i in range(16):
        if not up:
            px[i, 0] = rim
            px[i, 1] = light if 1 < i < 14 else body
        if not down:
            px[i, 15] = rim
            px[i, 14] = shadow if 1 < i < 14 else body
        if not left:
            px[0, i] = rim
            px[1, i] = light if 1 < i < 14 else body
        if not right:
            px[15, i] = rim
            px[14, i] = shadow if 1 < i < 14 else body

    # outer corners where two frame edges meet: rivet
    def rivet(cx, cy):
        px[cx, cy] = spark

    if not up and not left:
        px[1, 1] = body; rivet(2, 2)
    if not up and not right:
        px[14, 1] = body; rivet(13, 2)
    if not down and not left:
        px[1, 14] = body; rivet(2, 13)
    if not down and not right:
        px[14, 14] = body; rivet(13, 13)

    # inner corners: both sides connected but the diagonal is not
    def inner(x0, y0):
        for dx in range(2):
            for dy in range(2):
                px[x0 + dx, y0 + dy] = body
        px[x0 + (0 if x0 == 0 else 1), y0 + (0 if y0 == 0 else 1)] = rim

    if up and left and not tl:
        inner(0, 0)
    if up and right and not tr:
        inner(14, 0)
    if down and left and not bl:
        inner(0, 14)
    if down and right and not br:
        inner(14, 14)

    # isolated block: small mana gem emblem in the middle
    if not (up or down or left or right):
        for (x, y) in [(7, 6), (8, 6), (6, 7), (9, 7), (6, 8), (9, 8), (7, 9), (8, 9)]:
            px[x, y] = shadow
        for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
            px[x, y] = body
        px[7, 7] = spark
    return img


def omni_index(up, down, left, right, tl, tr, bl, br):
    """Port of Create's AllCTTypes.OMNIDIRECTIONAL.getTextureIndex (MIT)."""
    tile_x = tile_y = 0
    borders = (not up) + (not down) + (not left) + (not right)
    if up: tile_x += 1
    if down: tile_x += 2
    if left: tile_y += 1
    if right: tile_y += 2
    if borders == 0:
        if tr: tile_x += 1
        if tl: tile_x += 2
        if br: tile_y += 2
        if bl: tile_y += 1
    if borders == 1:
        if not right and (tl or bl):
            tile_y = 4; tile_x = -1 + (1 if bl else 0) + (2 if tl else 0)
        if not left and (tr or br):
            tile_y = 5; tile_x = -1 + (1 if br else 0) + (2 if tr else 0)
        if not down and (tl or tr):
            tile_y = 6; tile_x = -1 + (1 if tl else 0) + (2 if tr else 0)
        if not up and (bl or br):
            tile_y = 7; tile_x = -1 + (1 if bl else 0) + (2 if br else 0)
    if borders == 2:
        if (up and left and tl) or (down and left and bl) or (up and right and tr) or (down and right and br):
            tile_x += 3
    return tile_x + 8 * tile_y


def casing(tier):
    name = f"{tier}_casing"
    draw_casing_tile(tier, *([False] * 8)).save(os.path.join(BLOCK, name + ".png"))
    sheet = Image.new("RGBA", (128, 128))
    rendered = {}
    for mask in range(256):
        up, down, left, right, tl, tr, bl, br = [(mask >> i) & 1 == 1 for i in range(8)]
        # corners only exist when both adjacent sides connect (CTContext rule)
        if (tl and not (up and left)) or (tr and not (up and right)) or \
                (bl and not (down and left)) or (br and not (down and right)):
            continue
        idx = omni_index(up, down, left, right, tl, tr, bl, br)
        tile = draw_casing_tile(tier, up, down, left, right, tl, tr, bl, br)
        data = tile.tobytes()
        if idx in rendered and rendered[idx] != data:
            # Same index requested by visually different contexts: keep the first, report it.
            print(f"  note: {name} tile {idx} shared by differing contexts")
            continue
        rendered[idx] = data
        sheet.paste(tile, ((idx % 8) * 16, (idx // 8) * 16))
    sheet.save(os.path.join(BLOCK, name + "_connected.png"))
    print(f"{name}: base + connected sheet ({len(rendered)} tiles)")


# ----------------------------------------------------------------- manasteel pipe sheet (32x32)
def pipe_cell(img, ox, oy, opens, tier="manasteel"):
    """8x8 side face of a pipe core. opens: set of 'L','R','U','D' (pipe continues that way)."""
    rim, shadow, body, light, spark = [hexc(c) for c in METALS[tier]]
    px = img.load()
    horiz = "L" in opens or "R" in opens
    vert = "U" in opens or "D" in opens
    for y in range(8):
        for x in range(8):
            c = body
            if horiz and not vert:
                c = light if y == 2 else shadow if y == 5 else body
            elif vert and not horiz:
                c = light if x == 2 else shadow if x == 5 else body
            else:
                c = light if (x == 2 and y <= 5) or (y == 2 and x <= 5) else body
            if h2(x + ox, y + oy, 3) > 0.9:
                c = spark if c == light else light
            px[ox + x, oy + y] = c
    # banded rims on the closed sides
    for i in range(8):
        if "U" not in opens: px[ox + i, oy] = rim; px[ox + i, oy + 1] = shadow
        if "D" not in opens: px[ox + i, oy + 7] = rim; px[ox + i, oy + 6] = shadow
        if "L" not in opens: px[ox, oy + i] = rim; px[ox + 1, oy + i] = shadow
        if "R" not in opens: px[ox + 7, oy + i] = rim; px[ox + 6, oy + i] = shadow


def pipe_end(img, ox, oy, tier="manasteel"):
    rim, shadow, body, light, spark = [hexc(c) for c in METALS[tier]]
    px = img.load()
    for y in range(8):
        for x in range(8):
            edge = x in (0, 7) or y in (0, 7)
            ring = x in (1, 6) or y in (1, 6)
            hole = 2 <= x <= 5 and 2 <= y <= 5
            c = rim if edge else light if ring and (x == 1 or y == 1) else body if ring else shadow
            if hole:
                c = hexc(HOLES[tier][0]) if 3 <= x <= 4 and 3 <= y <= 4 else hexc(HOLES[tier][1])
            px[ox + x, oy + y] = c
    px[ox + 2, oy + 2] = spark


def manasteel_pipe_sheet(tier="manasteel"):
    img = Image.new("RGBA", (32, 32))
    cells = {  # uv (16ths) -> px = uv * 2, as used by Create's BlockStateGen.pipe()
        ("L", "U"): (24, 8), ("R", "U"): (16, 8), ("L", "D"): (24, 0), ("R", "D"): (16, 0),
        ("L", "R"): (8, 16), ("U", "D"): (0, 16), ("U",): (8, 8), ("D",): (0, 0),
        ("L",): (8, 0), ("R",): (0, 8),
    }
    for opens, (ox, oy) in cells.items():
        pipe_cell(img, ox, oy, set(opens), tier)
    pipe_end(img, 24, 16, tier)
    img.save(os.path.join(BLOCK, f"{tier}_pipes_connected.png"))
    print(f"{tier}_pipes_connected: 32x32 sheet")


def manasteel_pipe_attachments(tier="manasteel"):
    """32x32 sheet for the pipe *attachment* partials (connection stubs, rim collars, end rims,
    drains, casing plate). Create draws those from its own copper `pipes` texture;
    ManasteelPipeAttachmentModel swaps that sprite for this one at bake time, so the
    regions below follow the UVs of Create's partial models (uv * 2 = px)."""
    rim, shadow, body, light, spark = [hexc(c) for c in METALS[tier]]
    hole_dark, hole = hexc(HOLES[tier][0]), hexc(HOLES[tier][1])
    img = Image.new("RGBA", (32, 32))
    px = img.load()

    def col_tone(x):  # cross-section shading across an 8px pipe, matches pipe_cell()
        return [rim, shadow, light, body, body, shadow, shadow, rim][x]

    # connection stub (px 0-8 x 0-4) + general fill of the upper-left block
    for y in range(16):
        for x in range(16):
            c = col_tone(x % 8)
            if c == light and h2(x, y, 11) > 0.85:
                c = spark
            px[x, y] = c
    # rim collar where two pipes meet at a bend (px 8-16 x 0-4): a thin raised band
    for y in range(4):
        for x in range(8, 16):
            px[x, y] = [rim, light, body, rim][y] if x not in (8, 15) else rim

    def flange(ox, oy, size=10):
        for y in range(size):
            for x in range(size):
                e = x in (0, size - 1) or y in (0, size - 1)
                r = x in (1, size - 2) or y in (1, size - 2)
                c = rim if e else (light if (x == 1 or y == 1) else shadow) if r else body
                if 3 <= x <= size - 4 and 3 <= y <= size - 4:
                    c = hole_dark if 4 <= x <= size - 5 and 4 <= y <= size - 5 else hole
                px[ox + x, oy + y] = c
        px[ox + 2, oy + 2] = spark

    flange(22, 22)                       # rim / drain end face
    for y in range(22, 32):              # rim + drain side strips (px 18-22)
        for x in range(18, 22):
            px[x, y] = [rim, light, body, rim][x - 18]
    for y in range(16, 22):              # drain spout parts (px 24-32 x 16-22)
        for x in range(24, 32):
            px[x, y] = rim if y in (16, 21) or x in (24, 31) else (hole if (x + y) % 2 else shadow)
    # Casing plate face (px 2-12 x 22-32). Mana and item junctions use a solid plate.
    for y in range(22, 32):
        for x in range(2, 12):
            e = x in (2, 11) or y in (22, 31)
            center = 5 <= x <= 8 and 25 <= y <= 28
            px[x, y] = rim if e else hole_dark if center and tier == "manasteel" else body
    px[3, 23] = light
    if tier != "manasteel":
        # A small four-point engraving marks the cover where the channels meet.
        for x, y in ((6, 24), (5, 26), (7, 26), (6, 28)):
            px[x, y] = light
        for x, y in ((6, 25), (6, 26), (6, 27)):
            px[x, y] = shadow
    img.save(os.path.join(BLOCK, f"{tier}_pipes.png"))
    print(f"{tier}_pipes: attachment sheet")


def encased_manasteel_pipe():
    img = draw_casing_tile("manasteel", *([False] * 8))
    px = img.load()
    rim, shadow, body, light, spark = [hexc(c) for c in METALS["manasteel"]]
    for y in range(4, 12):
        for x in range(4, 12):
            edge = x in (4, 11) or y in (4, 11)
            px[x, y] = rim if edge else hexc("#0b1430") if 6 <= x <= 9 and 6 <= y <= 9 else shadow
    px[5, 5] = light
    img.save(os.path.join(BLOCK, "encased_manasteel_pipe.png"))
    print("encased_manasteel_pipe")


# ----------------------------------------------------------------- terrasteel mana pipe (animated)
def terrasteel_mana_pipe():
    rim, shadow, body, light, spark = [hexc(c) for c in METALS["terrasteel"]]
    glow = [hexc("#7dffb0"), hexc("#b8ffd6"), hexc("#ecfff4")]
    frames = 4
    sheet = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        # Layout (used by the hand-written models):
        #   [5..11) x [5..11)  core face
        #   [5..11) x [0..5)   arm side (length runs along y)
        #   [0..5)  x [5..11)  arm end ring
        for y in range(5, 11):
            for x in range(5, 11):
                edge = x in (5, 10) or y in (5, 10)
                px[x, y] = rim if edge else body
        cx = [(7, 7), (8, 7), (7, 8), (8, 8)]
        for i, (x, y) in enumerate(cx):
            px[x, y] = glow[(i + f) % 3]
        for y in range(0, 5):
            for x in range(5, 11):
                c = shadow if x in (5, 10) else body
                if x == 6: c = light
                if x in (7, 8):
                    c = glow[2] if (y - f) % 4 == 0 else glow[0]
                px[x, y] = c
        for y in range(5, 11):
            for x in range(0, 5):
                ring = x in (0, 4) or y in (5, 10)
                px[x, y] = rim if ring else glow[(x + y + f) % 3] if 1 <= x <= 3 and 7 <= y <= 8 else body
        sheet.paste(img, (0, 16 * f))
    sheet.save(os.path.join(BLOCK, "terrasteel_mana_pipe.png"))
    with open(os.path.join(BLOCK, "terrasteel_mana_pipe.png.mcmeta"), "w") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": false\n  }\n}\n')
    print("terrasteel_mana_pipe: 4-frame animation")


# ----------------------------------------------------------------- item: alfheim infusion module
MODULE = [
    "................",
    "......aaaa......",
    "....aabbbbaa....",
    "...abbccccbba...",
    "..abccddddccba..",
    "..abcdeffedcba..",
    ".abcdefggfedcba.",
    ".abcdfgsSgfdcba.",
    ".abcdfgSsgfdcba.",
    ".abcdefggfedcba.",
    "..abcdeffedcba..",
    "..abccddddccba..",
    "...abbccccbba...",
    "....aabhhbaa....",
    "......ahha......",
    ".......hh.......",
]
MODULE_PAL = {
    ".": (0, 0, 0, 0),
    "a": hexc("#3b2a4f"),   # dark dreamwood rim
    "b": hexc("#6c5a86"),
    "c": hexc("#9b8bb8"),   # dreamwood plate
    "d": hexc("#5a1d52"),   # elementium setting
    "e": hexc("#923a84"),
    "f": hexc("#cc5fb7"),
    "g": hexc("#ee9adc"),   # gem
    "s": hexc("#ffe1f7"),
    "S": hexc("#ffffff"),
    "h": hexc("#d9b84a"),   # gold coupling to the press
}


def module_item():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, row in enumerate(MODULE):
        for x, ch in enumerate(row):
            px[x, y] = MODULE_PAL[ch]
    img.save(os.path.join(ITEM, "alfheim_infusion_module.png"))
    print("alfheim_infusion_module (item)")


def main():
    for t in ("manasteel", "elementium", "terrasteel"):
        casing(t)
    manasteel_pipe_sheet()
    manasteel_pipe_attachments()
    encased_manasteel_pipe()
    terrasteel_mana_pipe()
    module_item()


if __name__ == "__main__":
    main()
