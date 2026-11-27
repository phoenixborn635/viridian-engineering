#!/usr/bin/env python3
"""
Viridian Phase 5a art (2026-11-25): the gaia gear, in the same Botania-first vocabulary as the blocks
(living-wood, living-rock, faceted translucent crystal, coloured outlines) and the tier palettes
(Terrasteel green for the Sylvan set, Gaia slate + white-gold + pink for the lance and gaia parts,
Manasteel blue for the air tanks).

Writes:
  * item sprites: gaia_spirit_sheet, mana_mechanism, incomplete_mana_mechanism, gaia_kinetic_lance,
    sylvan_helmet/chestplate/leggings/boots, pneumatic_bracer_tanks, livingrock_mana_pack,
    pneumatic_striders, pneumatic_boot_thrusters;
  * armor textures: textures/models/armor/sylvan_layer_1.png / _layer_2.png (64x32 humanoid layout);
  * the four 3D attachment models drawn on the player (models/block/gear/*, solid + _crystal halves) and
    their texture gear_tank.
Crystal elements never put a face on the plane of the solid they sit on (their see-through inner twin
would z-fight with it), so each gem leaves out the side it is mounted by.
100% original procedural art. Usage: gen_gear_art.py <assets/viridian_engineering dir>
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_phase4_art as p4  # noqa: E402
from gen_phase4_art import hexc, h2, WOOD, ROCK, GREEN, PINK, GAIA, BLUE, BORDER, el, faces, write_piece, T, NS  # noqa: E402
from gen_items import Sprite, wood_px, rock_px, bevel, gem_diamond, GOLD  # noqa: E402

OUT = p4.OUT
ARMOR = os.path.join(OUT, "textures", "models", "armor")
os.makedirs(ARMOR, exist_ok=True)
os.makedirs(os.path.join(p4.MODELS, "gear"), exist_ok=True)

MOSS = [hexc(c) for c in ("#14260f", "#24421a", "#3b6a28", "#5c9a3a", "#8fcf5e", "#d6f5b0")]


# ============================================================================ item sprites
def metal_sheet(name, palette, accent):
    s = Sprite()
    # Slanted metal plate with a bright edge and a thin mana vein.
    for y in range(4, 13):
        off = (12 - y) // 2
        for x in range(2 + off, 12 + off):
            v = 2 if h2(x, y, 41) > 0.7 else 1
            if y == 4:
                v = 4
            elif x == 2 + off:
                v = 3
            elif y == 12 or x == 11 + off:
                v = 0
            s.put(x, y, palette[v])
    for x in range(5, 12):
        s.put(x + (12 - 8) // 2 - 2, 8, accent[3] if x % 3 else accent[4])
    s.put(12, 5, palette[5])
    s.put(11, 5, palette[4])
    s.outline(0.3)
    s.save(name)


def progression_sheets():
    metal_sheet("manasteel_sheet", BLUE, BLUE)
    metal_sheet("elementium_sheet", PINK, PINK)
    metal_sheet("terrasteel_sheet", GREEN, GREEN)


def cog(s, cx, cy, ramp_rim, teeth=8, missing=()):
    import math
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            a = (math.atan2(y - cy, x - cx) + math.pi) / (2 * math.pi)
            tooth = int(a * teeth) % teeth
            on_tooth = (a * teeth) % 1.0 < 0.5 and tooth not in missing
            if d <= 4.3:
                s.put(x, y, WOOD[3] if (x + y) < cx + cy else WOOD[2])
            elif d <= 5.3:
                s.put(x, y, ramp_rim[3] if (x + y) < cx + cy else ramp_rim[2])
            elif d <= 6.6 and on_tooth:
                s.put(x, y, ramp_rim[2] if (x + y) < cx + cy else ramp_rim[1])


def mana_mechanism(incomplete=False):
    s = Sprite()
    cog(s, 7.5, 7.5, GREEN, missing=(1, 2, 5) if incomplete else ())
    if incomplete:
        for (x, y) in ((6, 6), (7, 6), (8, 7), (7, 8)):
            s.put(x, y, WOOD[1])
        s.put(7, 7, ROCK[2])
    else:
        gem_diamond(s, 7, 7, 2, BLUE)
        s.put(4, 4, GOLD[4]); s.put(11, 11, GOLD[3])
    s.outline()
    s.save("incomplete_mana_mechanism" if incomplete else "mana_mechanism")


def lance():
    s = Sprite()
    # livingwood shaft from the bottom-left corner up to the head (diagonal, 2 px thick)
    for i in range(1, 10):
        x, y = i, 15 - i
        s.put(x, y, WOOD[3]); s.put(x + 1, y, WOOD[2]); s.put(x, y - 1, WOOD[4] if i % 3 else WOOD[2])
    # pink crystal band and the terrasteel vamplate (guard) near the grip
    for (x, y) in ((4, 11), (5, 10), (5, 11), (4, 10)):
        s.put(x, y, PINK[4] if (x + y) % 2 else PINK[3])
    for (x, y) in ((6, 10), (7, 9), (8, 10), (6, 8), (5, 9), (7, 11)):
        s.put(x, y, GREEN[3] if x + y < 17 else GREEN[2])
    # gaia spearhead: a long white-gold leaf blade
    blade = [(10, 5), (11, 4), (12, 3), (13, 2), (14, 1), (10, 4), (11, 3), (12, 2), (13, 1), (11, 5), (12, 4), (13, 3),
             (9, 6), (10, 6), (9, 5), (14, 2), (12, 5), (13, 4)]
    for (x, y) in blade:
        lit = (x - y) > 9 or y < 3
        s.put(x, y, GAIA[5] if lit else GAIA[4] if (x + y) % 3 else GOLD[3])
    s.put(15, 0, GAIA[5])
    # mid-rib crystal
    for (x, y) in ((10, 5), (11, 4), (12, 3)):
        s.put(x, y, PINK[4])
    # pommel
    s.put(0, 15, ROCK[3]); s.put(1, 15, ROCK[2]); s.put(0, 14, ROCK[2])
    s.outline(0.3)
    s.save("gaia_kinetic_lance")


LAV = [hexc(c) for c in ("#241a36", "#3d2f5c", "#5f4c8c", "#8a74b8", "#b7a4dd", "#e2d8f6")]


def armor_icon(name, shape):
    """shape: rows of chars; # = lavender plate, = = white-gold trim, l = pale leaf blade, w = wood, g = gem, m = moss.
    (Lavender since 2026-11-25, matching the 3D worn model; Terrasteel-like leaf blades.)"""
    s = Sprite()
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "#":
                c = LAV[3] if h2(x, y, 51) > 0.75 else LAV[2]
                if y > 0 and shape[y - 1][x] in ". l":
                    c = LAV[4]
                s.put(x, y, c)
            elif ch == "l":
                s.put(x, y, LAV[5] if (x + y) % 3 else GOLD[4])
            elif ch == "=":
                s.put(x, y, GAIA[4] if (x + y) % 2 else GOLD[3])
            elif ch == "w":
                s.put(x, y, WOOD[3] if h2(x, y, 52) > 0.5 else WOOD[2])
            elif ch == "g":
                s.put(x, y, GREEN[4] if (x + y) % 2 else GREEN[5])   # green crystals, as on the worn model
            elif ch == "m":
                s.put(x, y, MOSS[3] if h2(x, y, 53) > 0.5 else MOSS[4])
    s.outline(0.3)
    s.save(name)


HELMET = [
    "l..............l",
    ".l.....==.....l.",
    "l.l.========.l.l",
    ".l=##########=l.",
    "..=##########=..",
    ".l#####gg#####l.",
    "l.####=##=####.l",
    "..###..==..###..",
    "..##m..==..m##..",
    "..##........##..",
    "..==........==..",
    "................",
]
CHEST = [
    "l..............l",
    ".l===......===l.",
    "l=###=....=###=l",
    ".####w====w####.",
    ".#####w##w#####.",
    ".#m####gg####m#.",
    "..#####gg#####..",
    "...##########...",
    "...####ww####...",
    "...###m##m###...",
    "...##########...",
    "...===wwww===...",
    "...##########...",
    "...####..####...",
]
LEGS = [
    "................",
    "...==========...",
    "...#wwwwwwww#...",
    "...####gg####...",
    "...####..####...",
    "...###....###...",
    "...###....###...",
    "...#m#....#m#...",
    "...###....###...",
    "...===....===...",
    "...###....###...",
    "...###....###...",
    "...ww......ww...",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".l===......===l.",
    "l.###......###.l",
    "..#g#......#g#..",
    "..###......###..",
    "..#m#......#m#..",
    "..####.....####.",
    ".=####....=####.",
    ".=====....=====.",
]


def module_tanks():
    s = Sprite()
    for cx in (5, 10):
        s.rect(cx - 2, 3, cx + 1, 12, lambda x, y: BLUE[3] if x == cx - 2 else BLUE[2] if x < cx + 1 else BLUE[1])
        for x in range(cx - 2, cx + 2):
            s.put(x, 2, ROCK[3]); s.put(x, 13, ROCK[1]); s.put(x, 7, WOOD[3])
        s.put(cx - 1, 1, ROCK[2])
        s.put(cx - 1, 10, GREEN[4])
    s.outline()
    s.save("pneumatic_bracer_tanks")


def module_pack():
    s = Sprite()
    s.rect(3, 2, 12, 13, rock_px(61))
    bevel(s, 3, 2, 12, 13, ROCK)
    for x in range(3, 13):
        s.put(x, 5, WOOD[2]); s.put(x, 10, WOOD[1])
    gem_diamond(s, 7, 7, 3, BLUE)
    for (x, y) in ((4, 1), (11, 1)):
        s.put(x, y, BLUE[4]); s.put(x, y + 1, BLUE[3])
    s.put(5, 12, MOSS[4]); s.put(10, 12, MOSS[3])
    s.outline()
    s.save("livingrock_mana_pack")


def module_striders():
    s = Sprite()
    s.rect(5, 1, 10, 6, lambda x, y: BLUE[3] if x == 5 else BLUE[2] if x < 10 else BLUE[1])
    s.rect(7, 7, 8, 11, lambda x, y: ROCK[3] if x == 7 else ROCK[1])
    s.rect(4, 12, 11, 14, rock_px(62)); bevel(s, 4, 12, 11, 14, ROCK)
    for x in range(5, 11):
        s.put(x, 6, WOOD[3])
    s.put(7, 3, GREEN[4]); s.put(8, 3, GREEN[3])
    s.outline()
    s.save("pneumatic_striders")


def module_thrusters():
    s = Sprite()
    # nozzle bell opening to the right, gaia slate with white-gold lip and a pink glow
    for y in range(3, 13):
        w = 2 + abs(y - 8) // 2
        for x in range(3, 8 + (5 - abs(y - 8)) // 1):
            s.put(x, y, GAIA[2] if (x + y) % 3 else GAIA[3])
    for y in range(3, 13):
        s.put(12 - abs(y - 8) // 2, y, GOLD[4] if y < 8 else GOLD[3])
    for y in range(6, 11):
        s.put(10, y, PINK[4]); s.put(11 - (1 if y in (6, 10) else 0), y, PINK[3])
    s.rect(1, 6, 3, 10, wood_px(63))
    s.outline(0.3)
    s.save("pneumatic_boot_thrusters")


# Sylvan armor icons, pass 2 (2026-11-25, user: the noisy pass 1 looked bad). Hand-placed pixel maps in
# Botania's armor-icon language: chunky rounded forms, a dark outline in the material's own colour, few
# clean shades lit from the top left, living-wood leaf sprigs where Terrasteel has its branches.
# o = outline, 2..5 = lavender shades (5 lightest), y/Y = white-gold dark/light, g/G = crystal dark/light,
# w/W = living wood dark/light, . = empty.
ICON_HELMET = [
    "................",
    "..w..........w..",
    ".wWw.oooooo.wWw.",
    "..wWo455554oWw..",
    "...o45555554o...",
    "..o4555YY5554o..",
    "..o455YgGY554o..",
    ".o34555YY55543o.",
    ".o34oooooooo43o.",
    ".o3o........o3o.",
    ".o3o........o3o.",
    ".o2o........o2o.",
    ".oy2o......o2yo.",
    "..oyo......oyo..",
    "...o........o...",
    "................",
]
ICON_CHEST = [
    "................",
    ".ww..........ww.",
    "wWWwo......owWWw",
    ".wo45o....o54ow.",
    ".o4555oooo5554o.",
    ".o355554455553o.",
    ".o35554YY45553o.",
    "..o3554gG4553o..",
    "..o355YggY553o..",
    "...o35544553o...",
    "...oyyyYYyyyo...",
    "...o34444443o...",
    "...o33444433o...",
    "...o333oo333o...",
    "....oooooooo....",
    "................",
]
ICON_LEGS = [
    "................",
    "...oooooooooo...",
    "...oyyyGGyyyo...",
    "...o45544554o...",
    "...o455oo554o...",
    "...o45o..o54o...",
    "...o45o..o54o...",
    "..oy45o..o54yo..",
    "..o445o..o544o..",
    "...o45o..o54o...",
    "...o35o..o53o...",
    "...o34o..o43o...",
    "...oyyo..oyyo...",
    "...ooo....ooo...",
    "................",
    "................",
]
ICON_BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "..oooo....oooo..",
    ".wo54o...wo54o..",
    "wWo54o..wWo54o..",
    ".wo45o...wo45o..",
    "..o45o....o45o..",
    "..oyYo....oyYo..",
    ".o4555o..o4555o.",
    ".o33333o.o33333o",
    ".ooooooo.ooooooo",
    "................",
]


def draw_icon(name, rows):
    import gen_items
    lav = [hexc(c) for c in ("#1e1530", "#3d2f5c", "#5f4c8c", "#8a74b8", "#b7a4dd", "#e2d8f6")]
    pal = {"o": lav[0], "2": lav[1], "3": lav[2], "4": lav[3], "5": lav[4],
           "y": hexc("#b07d24"), "Y": hexc("#ffe39a"), "g": hexc("#16703a"), "G": hexc("#8ff0b4"),
           "w": hexc("#5a3826"), "W": hexc("#946345")}
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, row)
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch])
    img.save(os.path.join(gen_items.ITEM, name + ".png"))


def items():
    progression_sheets()
    metal_sheet("gaia_spirit_sheet", GAIA, PINK)
    mana_mechanism()
    mana_mechanism(incomplete=True)
    lance()
    draw_icon("sylvan_helmet", ICON_HELMET)
    draw_icon("sylvan_chestplate", ICON_CHEST)
    draw_icon("sylvan_leggings", ICON_LEGS)
    draw_icon("sylvan_boots", ICON_BOOTS)
    module_tanks()
    module_pack()
    module_striders()
    module_thrusters()


# ============================================================================ armor layers (64x32)
def boxes(img, u, v, w, h, d, painter):
    """Paints the six faces of a humanoid model box at texture (u, v) with size (w, h, d)."""
    faces_ = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }
    px = img.load()
    for name, (x0, y0, fw, fh) in faces_.items():
        for y in range(fh):
            for x in range(fw):
                c = painter(name, x, y, fw, fh)
                if c is not None:
                    px[x0 + x, y0 + y] = c


def plate(seed, trim_rows=(), gem=None, moss=True):
    def paint(face, x, y, w, h):
        n = h2(x + 7 * len(face), y, seed)
        c = GREEN[2] if n > 0.3 else GREEN[1]
        if n > 0.88:
            c = GREEN[3]
        if x == 0 or x == w - 1:
            c = WOOD[1]                                    # living-wood seams down the edges
        if y in trim_rows or (y == 0 and face not in ("top", "bottom")):
            c = GAIA[4] if (x % 2 == 0) else GOLD[3]       # white-gold trim
        if moss and face in ("front", "back", "left", "right") and y > h - 3 and h2(x, y, seed + 3) > 0.55:
            c = MOSS[3] if h2(x, y, seed + 4) > 0.5 else MOSS[2]   # moss creeping up from the bottom
        if gem and face == "front" and gem(x, y, w, h):
            c = BLUE[4] if (x + y) % 2 else BLUE[5]
        return c + (255,) if len(c) == 3 else c
    return paint


def armor_layers():
    l1 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # helmet (head box 8x8x8 at 0,0): visor slit and a crystal brow gem
    def helm(face, x, y, w, h):
        c = plate(71, trim_rows=(1,), gem=lambda x, y, w, h: (x, y) in ((3, 2), (4, 2), (3, 1), (4, 1)))(face, x, y, w, h)
        if face == "front" and 4 <= y <= 5 and 1 <= x <= 6:
            return BORDER
        if face == "bottom":
            return None
        return c
    boxes(l1, 0, 0, 8, 8, 8, helm)
    # chestplate body (16,16 8x12x4) with a gaia emblem; arms (40,16 4x12x4) with bracer bands
    boxes(l1, 16, 16, 8, 12, 4, plate(72, trim_rows=(6,),
                                      gem=lambda x, y, w, h: abs(x - 3.5) + abs(y - 3.5) <= 1.5))
    boxes(l1, 40, 16, 4, 12, 4, plate(73, trim_rows=(4, 9)))
    # boots (leg box 0,16 4x12x4): only the lower part is armored
    def boot(face, x, y, w, h):
        if face in ("front", "back", "left", "right") and y < 7:
            return None
        return plate(74, trim_rows=(7,))(face, x, y, w, h)
    boxes(l1, 0, 16, 4, 12, 4, boot)
    l1.save(os.path.join(ARMOR, "sylvan_layer_1.png"))

    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    # leggings: legs (0,16) and the belt part of the body (16,16)
    boxes(l2, 0, 16, 4, 12, 4, plate(75, trim_rows=(5,), gem=lambda x, y, w, h: (x, y) in ((1, 2), (2, 2))))
    def belt(face, x, y, w, h):
        if face in ("front", "back", "left", "right") and y < 8:
            return None
        return plate(76, trim_rows=(8,), moss=False)(face, x, y, w, h)
    boxes(l2, 16, 16, 8, 12, 4, belt)
    l2.save(os.path.join(ARMOR, "sylvan_layer_2.png"))


# ============================================================================ 3D attachments
def gear_textures():
    t = p4.tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            c = BLUE[2] if h2(x // 2, y, 81) > 0.35 else BLUE[1]
            if x in (0, 15):
                c = BLUE[0]
            if x in (1, 2):
                c = BLUE[3]                       # lit edge of the cylinder
            if y in (0, 15):
                c = WOOD[1]
            if y in (5, 10):
                c = WOOD[3]                       # living-wood hoops
            px[x, y] = c
    p4.save(t, "gear_tank")


def gear_models():
    tx = {**T(("tank", "gear_tank"), ("rock", "p4_rock"), ("wood", "p4_wood_green"), ("bricks", "p4_bricks_gaia"),
              ("gaia", "p4_wood_gaia"), ("gem", "p5_crystal_blue"), ("pink", "p4_crystal_pink")),
          "particle": NS + "gear_tank"}
    no = lambda *omit: tuple(s for s in p4.ALL if s not in omit)

    # forearm air cylinder, long axis vertical; the gauge gem looks outward (north)
    write_piece("gear/bracer_tank", tx, [
        el([6.5, 4.5, 6.5], [9.5, 11.5, 9.5], faces("#tank", sides=no("up", "down"))),
        el([6, 3.5, 6], [10, 4.5, 10], faces("#rock")),
        el([6, 11.5, 6], [10, 12.5, 10], faces("#rock")),
        el([6.2, 7.5, 6.2], [9.8, 8.5, 9.8], faces("#wood")),
        el([7.5, 12.5, 7.5], [8.5, 13.5, 8.5], faces("#rock", sides=no("down"))),
        el([7.2, 9, 6.1], [8.8, 10.6, 6.5], faces("#gem", sides=no("south")), glow=True),
    ])
    # dorsal livingrock mana core: a wood back plate (south, against the body), the rock core, wood straps,
    # a big crystal in front and two crystal vents on top
    write_piece("gear/mana_pack", tx, [
        el([3, 2, 8.5], [13, 14, 10], faces("#wood")),
        el([4.5, 3.5, 5.5], [11.5, 12.5, 8.5], faces("#rock", sides=no("south"))),
        el([3.75, 5, 5], [12.25, 6, 8.5], faces("#gaia", sides=no("south"))),
        el([3.75, 10, 5], [12.25, 11, 8.5], faces("#gaia", sides=no("south"))),
        el([6, 6.5, 4.6], [10, 9.5, 5.5], faces("#gem", sides=no("south")), glow=True),
        el([5, 12.5, 6.25], [6.5, 14, 7.75], faces("#gem", sides=no("down")), glow=True),
        el([9.5, 12.5, 6.25], [11, 14, 7.75], faces("#gem", sides=no("down")), glow=True),
    ])
    # leg piston linkage on the outer side of the leg
    write_piece("gear/strider", tx, [
        el([6.5, 9, 7], [9.5, 13, 9], faces("#tank")),
        el([7.5, 4, 7.5], [8.5, 9, 8.5], faces("#rock", sides=no("up", "down"))),
        el([6, 8.4, 6.8], [10, 9.4, 9.2], faces("#wood")),
        el([6.5, 2.5, 7], [9.5, 4, 9], faces("#rock")),
        el([7, 11, 6.6], [9, 12, 7], faces("#gem", sides=no("south")), glow=True),
    ])
    # ankle exhaust nozzle behind the heel, the bell opening outward (north)
    write_piece("gear/boot_thruster", tx, [
        el([5, 6, 9], [11, 10, 10.5], faces("#wood")),
        el([6, 6.5, 6], [10, 9.5, 9], faces("#bricks", sides=no("south"))),
        el([5.5, 6, 4.5], [10.5, 10, 6], faces("#gaia", sides=no("south"))),
        el([7, 7, 4.3], [9, 9, 4.5], faces("#pink", sides=no("south")), glow=True),
    ])


# ============================================================================ Sylvan icons + modules, pass 3
# (2026-11-25, user: pass 2 icons still bad; modules must fit the 3D armor.) Hand-placed 16x16 maps in the
# Terrasteel icon language: full, chunky silhouettes, a deep-violet outline, 1x2 vertical shade clusters
# like Botania's metals, dark living-wood branches with pale lavender leaf tips (the 3D model's blades).
# Digits 0..7 = violet ramp (0 outline, 7 highlight); b/B/c bark; g/G/H crystal; y/Y white-gold;
# r/s/t/u livingrock; p/P gaia-pink glow; . = empty.
SYL_PAL = {'0': '#1a0e30', '1': '#311d58', '2': '#4c2f86', '3': '#6542b0', '4': '#8460d2', '5': '#a483e8', '6': '#c4abf6', '7': '#ebe1ff', 'b': '#2c160d', 'B': '#4a2812', 'c': '#73431f', 'g': '#12703c', 'G': '#5fe89c', 'H': '#d4ffe6', 'y': '#a97a22', 'Y': '#f6d77a', 'L': '#8fd16a', 'l': '#3f8a2c', 'r': '#8b8676', 's': '#b9b5a5', 't': '#dcd8ca', 'u': '#f1eee4', 'p': '#e0559f', 'P': '#ffb3dc'}
ICON_HELMET = [
    '.76..........67.',
    '.675........576.',
    '..5bB......Bb5..',
    '...Bc012210cB...',
    '.76c23455432c67.',
    '7652345675432567',
    '.5b2456GH6542b5.',
    '..13456gg65431..',
    '..124321123421..',
    '..234100001432..',
    '..2340....0432..',
    '..1340....0431..',
    '..12y0....0y21..',
    '...010....010...',
    '................',
    '................',
]
ICON_CHEST = [
    '.76..........67.',
    '.675........576.',
    '..5bB......Bb5..',
    '.1Bc45000054cB1.',
    '1234565yy5654321',
    '023456yGHy654320',
    '0123456gg6543210',
    '.012345yy543210.',
    '...2345565432...',
    '...1221221221...',
    '...3455655543...',
    '...2345545432...',
    '...1234434321...',
    '...0123003210...',
    '....000..000....',
    '................',
]
ICON_LEGS = [
    '................',
    '................',
    '...0000000000...',
    '...122yGHy221...',
    '...2345665432...',
    '...2345005432...',
    '...2345..5432...',
    '..c2345..5432c..',
    '76b3456..6543b67',
    '.5.2345..5432.5.',
    '...2345..5432...',
    '...1234..4321...',
    '...12y3..3y21...',
    '...0110..0110...',
    '................',
    '................',
]
ICON_BOOTS = [
    '................',
    '................',
    '................',
    '................',
    '.76..........67.',
    '.675........576.',
    '..5bB......Bb5..',
    '...c345..543c...',
    '...2456..6542...',
    '...2345..5432...',
    '...1yY1..1Yy1...',
    '..23456..65432..',
    '.123456..654321.',
    '.012344..443210.',
    '.000000..000000.',
]
ICON_TANKS = [
    '................',
    '...yy......yy...',
    '.0yYYy0..0yYYy0.',
    '.065420..065420.',
    '.076420..076420.',
    '.065420..065420.',
    '.bBcBBbbbbBBcBb.',
    '.065420..065420.',
    '.065G20..065G20.',
    '.064g20..064g20.',
    '.054320..054320.',
    '.bBcBBbbbbBBcBb.',
    '.054320..054320.',
    '.0yyyy0..0yyyy0.',
    '..0000....0000..',
    '................',
]
ICON_PACK = [
    '................',
    '...G........G...',
    '..0g0......0g0..',
    '.0uuuuuuuuuuuu0.',
    '.0tssssssssssr0.',
    '.03455555555430.',
    '.0tsss0gg0sssr0.',
    '.0tss0gGHg0ssr0.',
    '.0ts0gGHHGg0sr0.',
    '.0tss0gGGg0ssr0.',
    '.0tsss0gg0sssr0.',
    '.02344444444320.',
    '.0sssssssssssr0.',
    '.0rrrrrrrrrrrr0.',
    '..000000000000..',
    '................',
]
ICON_STRIDERS = [
    '................',
    '....00000000....',
    '....06544320....',
    '....07554320....',
    '....065G4320....',
    '....06544320....',
    '....0yYYyyy0....',
    '......0760......',
    '......0760......',
    '.....bBccBb.....',
    '......0650......',
    '......0540......',
    '...0000000000...',
    '...0tssssssr0...',
    '...0rrrrrrrr0...',
    '....00000000....',
]
ICON_THRUSTERS = [
    '................',
    '................',
    '................',
    '.............0..',
    '...........00Y..',
    '.........0077Yp.',
    '.bbb...00775pYp.',
    '.cbB00055555pYP.',
    '.bBc44444444pyP.',
    '.Bcb00022444pyP.',
    '.bbb...00222pyp.',
    '.........0022yp.',
    '...........00y..',
    '.............0..',
    '................',
    '................',
]


def draw_map(name, rows, seed):
    import hashlib
    import gen_items
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        assert len(row) == 16, (name, y, row)
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch in "23456":
                n = hashlib.md5(f"{x},{y // 2},{seed}".encode()).digest()[0] / 255
                ch = str(min(7, max(1, int(ch) + (1 if n > 0.78 else -1 if n < 0.22 else 0))))
            img.putpixel((x, y), tuple(hexc(SYL_PAL[ch])[:3]) + (255,))
    img.save(os.path.join(gen_items.ITEM, name + ".png"))


def sylvan_icons():
    for i, (name, rows) in enumerate((
            ("sylvan_helmet", ICON_HELMET), ("sylvan_chestplate", ICON_CHEST), ("sylvan_leggings", ICON_LEGS),
            ("sylvan_boots", ICON_BOOTS), ("pneumatic_bracer_tanks", ICON_TANKS),
            ("livingrock_mana_pack", ICON_PACK), ("pneumatic_striders", ICON_STRIDERS),
            ("pneumatic_boot_thrusters", ICON_THRUSTERS))):
        draw_map(name, rows, i)


def _c(k):
    return hexc(SYL_PAL[k])


def sylvan_module_textures():
    """gear_tank (lavender cylinder, white-gold hoops), gear_plate (lavender plate), gear_bark, gear_gold."""
    t = p4.tile(); px = t.load()
    across = "0766554433322210"                       # lit from the left, like the icons
    for y in range(16):
        for x in range(16):
            k = across[x]
            if k in "23456" and h2(x, y // 2, 91) > 0.8:
                k = str(int(k) + 1)
            if y in (0, 15):
                k = "y"
            elif y in (4, 11):
                k = "Y" if x < 6 else "y"
            px[x, y] = _c(k)
    p4.save(t, "gear_tank")
    t = p4.tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            n = h2(x, y // 2, 92)
            k = "4" if n > 0.55 else "3"
            if n > 0.9:
                k = "5"
            if x == 0 or y == 0:
                k = "5"
            if x == 15 or y == 15:
                k = "1"
            px[x, y] = _c(k)
    p4.save(t, "gear_plate")
    t = p4.tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            n = h2(x // 2, y, 93)
            px[x, y] = _c("c" if n > 0.8 else "B" if n > 0.35 else "b")
    p4.save(t, "gear_bark")
    t = p4.tile(); px = t.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = _c("Y" if (x + y) % 7 == 0 or y < 2 else "y")
    p4.save(t, "gear_gold")


def sylvan_module_models():
    """Attachment models: block space centred on (8, 8, 8), +y up, north (-z) = outward, south = the body.
    Kept compact so they sit outside the 3D Sylvan armor (SylvanArmorLayer places them)."""
    tx = {**T(("tank", "gear_tank"), ("plate", "gear_plate"), ("bark", "gear_bark"), ("gold", "gear_gold"),
              ("rock", "p4_rock"), ("gem", "p4_crystal_green"), ("pink", "p4_crystal_pink")),
          "particle": NS + "gear_plate"}
    no = lambda *omit: tuple(s for s in p4.ALL if s not in omit)

    # forearm air cylinder: gold-capped lavender tank on a bark bracket, green gauge, valve on top
    write_piece("gear/bracer_tank", tx, [
        el([6.5, 3.5, 6.5], [9.5, 12.5, 9.5], faces("#tank", sides=no("up", "down"))),
        el([6, 2.5, 6], [10, 3.5, 10], faces("#gold")),
        el([6, 12.5, 6], [10, 13.5, 10], faces("#gold")),
        el([6.25, 7.5, 6.25], [9.75, 8.5, 9.75], faces("#bark", sides=no("up", "down"))),
        el([7.5, 13.5, 7.5], [8.5, 14.5, 8.5], faces("#gold", sides=no("down"))),
        el([7, 4.5, 9.5], [9, 11.5, 10], faces("#bark", sides=no("north"))),
        el([7.2, 9.5, 6.1], [8.8, 11, 6.5], faces("#gem", sides=no("south")), glow=True),
    ])
    # dorsal livingrock mana core: thin bark back plate (south), rock core with lavender bands,
    # a crystal facing out and two crystal vents
    write_piece("gear/mana_pack", tx, [
        el([3.5, 2.5, 9], [12.5, 13.5, 10], faces("#bark")),
        el([4.5, 3.5, 6], [11.5, 12.5, 9], faces("#rock", sides=no("south"))),
        el([4.25, 5, 5.75], [11.75, 6, 9], faces("#plate", sides=no("south"))),
        el([4.25, 10, 5.75], [11.75, 11, 9], faces("#plate", sides=no("south"))),
        el([6.5, 6.75, 5.2], [9.5, 9.25, 6], faces("#gem", sides=no("south")), glow=True),
        el([5.25, 12.5, 6.75], [6.75, 14, 8.25], faces("#gem", sides=no("down")), glow=True),
        el([9.25, 12.5, 6.75], [10.75, 14, 8.25], faces("#gem", sides=no("down")), glow=True),
    ])
    # leg piston: lavender cylinder up top, gold collar, polished rod, bark knee knuckle, rock foot
    write_piece("gear/strider", tx, [
        el([6.75, 8.5, 7], [9.25, 13, 9], faces("#tank")),
        el([6.5, 8, 6.75], [9.5, 8.5, 9.25], faces("#gold", sides=no("up"))),
        el([7.5, 4, 7.5], [8.5, 8, 8.5], faces("#gold", sides=no("up", "down"))),
        el([7, 3, 7], [9, 4, 9], faces("#bark")),
        el([6.75, 1.5, 7], [9.25, 3, 9], faces("#rock", sides=no("up"))),
        el([7.25, 10.5, 6.6], [8.75, 12, 7], faces("#gem", sides=no("south")), glow=True),
    ])
    # heel exhaust: bark mount (south), lavender bell opening outward, white-gold lip, gaia-pink glow
    write_piece("gear/boot_thruster", tx, [
        el([5.5, 6, 9], [10.5, 10, 10], faces("#bark")),
        el([6.5, 6.5, 7], [9.5, 9.5, 9], faces("#plate", sides=no("south"))),
        el([5.75, 5.75, 5], [10.25, 10.25, 7], faces("#tank", sides=no("south"))),
        el([5.5, 5.5, 4.5], [10.5, 10.5, 5], faces("#gold", sides=no("south"))),
        el([6.75, 6.75, 4.3], [9.25, 9.25, 4.5], faces("#pink", sides=no("south")), glow=True),
    ])


if __name__ == "__main__":
    items()
    sylvan_icons()          # pass 3 replaces the armor + module icons drawn by items()
    armor_layers()
    sylvan_module_textures()
    sylvan_module_models()
    print("gear art written")
