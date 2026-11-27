#!/usr/bin/env python3
"""
Original textures for every Phase 2-5 block and item in the spec (section 2 registry),
plus the two pylon model textures (64x64, Botania-style silhouette, original art).
Nothing is copied or recoloured from another mod. Reuses helpers from gen_textures.py.

Usage: gen_textures_phase2_5.py <assets/viridian_engineering dir>
"""
import math
import os
import sys

sys.argv = [sys.argv[0], sys.argv[1]] if len(sys.argv) > 1 else sys.argv
import gen_textures as g  # noqa: E402
from PIL import Image  # noqa: E402

BLOCK, ITEM = g.BLOCK, g.ITEM
MODEL = os.path.join(g.OUT, "textures", "entity")
os.makedirs(MODEL, exist_ok=True)
hexc, h2 = g.hexc, g.h2

# extra tiers: corporea (violet), gaia (pale stone + rose runes)
g.METALS["corporea"] = ["#1a0a28", "#3a1856", "#6a2c94", "#a560d4", "#efd2ff"]
g.WOODS["corporea"] = ["#1e1329", "#2d1f3d", "#3a2a4f", "#51406a"]
g.METALS["gaia"] = ["#2b2633", "#5d5570", "#9c94b0", "#d8d2e6", "#ffffff"]
g.WOODS["gaia"] = ["#8a8496", "#a7a1b4", "#c4bfcf", "#e1dde8"]

ACCENT = {
    "manasteel": hexc("#5fc8ff"), "elementium": hexc("#ff8ae6"), "terrasteel": hexc("#7dff8a"),
    "corporea": hexc("#d27bff"), "gaia": hexc("#ff7fb8"),
}
written = []


def save(img, folder, name):
    img.save(os.path.join(folder, name + ".png"))
    written.append(os.path.join(os.path.basename(folder), name))


def panel(tier):
    return g.draw_casing_tile(tier, *([False] * 8))


def plain_panel(tier):
    """Casing frame without the centre gem, as a base for detailed faces."""
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    g.casing_interior(px, tier, 11)
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[tier]]
    for i in range(16):
        px[i, 0] = rim; px[i, 15] = rim; px[0, i] = rim; px[15, i] = rim
        if 0 < i < 15:
            px[i, 1] = light if 1 < i < 14 else body
            px[1, i] = light if 1 < i < 14 else body
            px[i, 14] = shadow if 1 < i < 14 else body
            px[14, i] = shadow if 1 < i < 14 else body
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px[x, y] = spark
    return img


def glyph(img, rows, pal, ox=0, oy=0):
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in pal and pal[ch] is not None:
                px[ox + x, oy + y] = pal[ch]


def fill(img, x0, y0, x1, y1, c):
    px = img.load()
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = c


def recess(img, x0, y0, x1, y1, tier, inner="#0d0714"):
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[tier]]
    fill(img, x0, y0, x1, y1, hexc(inner))
    px = img.load()
    for x in range(x0, x1 + 1):
        px[x, y0] = rim; px[x, y1] = light
    for y in range(y0, y1 + 1):
        px[x0, y] = rim; px[x1, y] = light


# ======================================================================== Tier 2 (elementium logistics)
def elementium_logistics():
    t = "elementium"
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[t]]
    acc = ACCENT[t]
    # smart funnel: front is a tapered mouth, side shows the flap hinge
    f = plain_panel(t)
    recess(f, 3, 3, 12, 12, t)
    glyph(f, ["  ....  ", " ..  .. ", "..    ..", ".  aa  .", ".  aa  .", "..    ..", " ..  .. ", "  ....  "],
          {".": shadow, "a": acc}, 4, 4)
    save(f, BLOCK, "elementium_smart_funnel_front")
    s = plain_panel(t)
    fill(s, 2, 6, 13, 9, shadow)
    for x in range(3, 13, 3):
        s.load()[x, 7] = acc
    save(s, BLOCK, "elementium_smart_funnel_side")
    # smart tunnel: top has two flaps, side is an arch
    top = plain_panel(t)
    fill(top, 2, 4, 13, 11, hexc("#140a14"))
    for x in range(2, 14):
        top.load()[x, 4] = light
        top.load()[x, 11] = rim
    for y in range(5, 11):
        for x in (4, 7, 8, 11):
            top.load()[x, y] = body if (x + y) % 2 else shadow
    top.load()[7, 7] = acc; top.load()[8, 8] = acc
    save(top, BLOCK, "elementium_smart_tunnel_top")
    side = plain_panel(t)
    for y in range(6, 15):
        for x in range(3, 13):
            if (x - 7.5) ** 2 / 25 + (y - 14) ** 2 / 64 <= 1:
                side.load()[x, y] = hexc("#140a14")
    side.load()[7, 6] = acc; side.load()[8, 6] = acc
    save(side, BLOCK, "elementium_smart_tunnel_side")
    # request interface: rune display
    r = plain_panel(t)
    recess(r, 3, 3, 12, 12, t, "#1a0b1c")
    glyph(r, ["a.a.a.", "......", "a.aaa.", "......", "aa.a.a", "......"],
          {"a": acc, ".": None}, 5, 5)
    save(r, BLOCK, "elementium_request_interface_front")
    save(panel(t), BLOCK, "elementium_request_interface_side")


# ======================================================================== Tier 4 (terrasteel power)
def terrasteel_power():
    t = "terrasteel"
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[t]]
    acc = ACCENT[t]
    lava = [hexc("#ff7a1a"), hexc("#ffb02e"), hexc("#ffe27a")]
    # reactor controller: window onto a green-gold flame core
    c = plain_panel(t)
    recess(c, 3, 3, 12, 12, t, "#0c1a0a")
    for y in range(4, 12):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 8.5)
            if d < 1.8: c.load()[x, y] = hexc("#f4fff0")
            elif d < 3.0: c.load()[x, y] = acc
            elif d < 4.0 and h2(x, y, 5) > 0.4: c.load()[x, y] = lava[int(h2(x, y, 9) * 3)]
    save(c, BLOCK, "terrasteel_mana_reactor_controller_front")
    save(panel(t), BLOCK, "terrasteel_mana_reactor_controller_side")
    # reactor core (centre of the 3x3x3), and its ports
    core = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            v = h2(x, y, 21)
            col = hexc("#f4fff0") if d < 2 else acc if d < 4.5 else lava[1] if (d < 6 and v > 0.55) else hexc("#1d4a18") if v > 0.5 else hexc("#163d13")
            core.load()[x, y] = col
    save(core, BLOCK, "terrasteel_mana_reactor_core")
    lp = plain_panel(t); recess(lp, 4, 4, 11, 11, t, "#2a0e00")
    for y in range(5, 11):
        for x in range(5, 11):
            lp.load()[x, y] = lava[int(h2(x, y, 4) * 3)]
    save(lp, BLOCK, "terrasteel_lava_port")
    sp = plain_panel(t); recess(sp, 5, 5, 10, 10, t, "#101010")
    glyph(sp, ["bb", "bb"], {"b": hexc("#8a7a5a")}, 7, 7)
    save(sp, BLOCK, "terrasteel_shaft_port")
    mp = plain_panel(t); recess(mp, 4, 4, 11, 11, t, "#06140a")
    for y in range(5, 11):
        for x in range(5, 11):
            if 1.2 < math.hypot(x - 7.5, y - 7.5) < 2.8: mp.load()[x, y] = acc
    save(mp, BLOCK, "terrasteel_mana_port")

    # battery casing: glass cell with a mana column; controller: gauge
    bc = plain_panel(t)
    fill(bc, 4, 2, 11, 13, hexc("#0a2216"))
    for y in range(3, 13):
        for x in range(5, 11):
            bc.load()[x, y] = hexc("#2bd6ff") if x in (6, 7) else hexc("#1a8fd0") if x in (8, 9) else hexc("#0e5a8a")
    for x in range(4, 12):
        bc.load()[x, 2] = rim; bc.load()[x, 13] = rim
    save(bc, BLOCK, "terrasteel_mana_battery_casing")
    bt = plain_panel(t)
    for y in range(4, 12):
        for x in range(4, 12):
            if math.hypot(x - 7.5, y - 7.5) < 3.6: bt.load()[x, y] = hexc("#1a8fd0")
    bt.load()[7, 7] = hexc("#d8f6ff")
    save(bt, BLOCK, "terrasteel_mana_battery_casing_top")
    ctl = plain_panel(t)
    recess(ctl, 3, 3, 12, 12, t, "#071a10")
    for i, y in enumerate(range(11, 4, -1)):
        for x in range(5, 11):
            ctl.load()[x, y] = hexc("#2bd6ff") if i < 5 else hexc("#0e2a3a")
    for y in (5, 7, 9, 11):
        ctl.load()[4, y] = light
    save(ctl, BLOCK, "terrasteel_mana_battery_controller_front")
    save(panel(t), BLOCK, "terrasteel_mana_battery_controller_side")


# ======================================================================== Tier 4 (corporea)
def corporea_pipe():
    """Same UV layout as the Terrasteel mana pipe (core / arm side / arm end), animated."""
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS["corporea"]]
    glow = [hexc("#b05cff"), hexc("#dca4ff"), hexc("#fff0ff")]
    frames = 4
    sheet = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        px = img.load()
        for y in range(5, 11):
            for x in range(5, 11):
                px[x, y] = rim if x in (5, 10) or y in (5, 10) else body
        px[7 + f % 2, 7 + (f // 2) % 2] = glow[2]
        for y in range(0, 5):
            for x in range(5, 11):
                c = shadow if x in (5, 10) else body
                if x == 6: c = light
                if x in (7, 8): c = glow[1] if (y + f) % 4 == 0 else glow[0]
                px[x, y] = c
        for y in range(5, 11):
            for x in range(0, 5):
                ring = x in (0, 4) or y in (5, 10)
                px[x, y] = rim if ring else glow[(x + y + f) % 3] if 1 <= x <= 3 and 7 <= y <= 8 else body
        sheet.paste(img, (0, 16 * f))
    sheet.save(os.path.join(BLOCK, "corporea_pipe.png"))
    with open(os.path.join(BLOCK, "corporea_pipe.png.mcmeta"), "w") as fh:
        fh.write('{\n  "animation": {\n    "frametime": 3,\n    "interpolate": false\n  }\n}\n')
    written.append("block/corporea_pipe (animated)")


def corporea_ports():
    t = "corporea"
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[t]]
    acc = ACCENT[t]
    save(panel(t), BLOCK, "corporea_casing")
    save(plain_panel(t), BLOCK, "corporea_port_side")
    arrow_in = ["...a...", "...a...", "...a...", "a..a..a", ".a.a.a.", "..aaa..", "...a..."]
    arrow_out = arrow_in[::-1]
    gear = [".a.a.a.", "aaaaaaa", ".aa.aa.", "aa...aa", ".aa.aa.", "aaaaaaa", ".a.a.a."]
    for name, art in (("input", arrow_in), ("output", arrow_out), ("machine", gear)):
        img = plain_panel(t)
        recess(img, 3, 3, 12, 12, t)
        glyph(img, art, {"a": acc, ".": None}, 5, 5)
        save(img, BLOCK, f"corporea_{name}_port_front")
    # bridge port: top socket where a native Botania Corporea Spark sits
    b = plain_panel(t)
    for y in range(3, 13):
        for x in range(3, 13):
            if abs(x - 7.5) + abs(y - 7.5) <= 4.5:
                b.load()[x, y] = hexc("#0d0714") if abs(x - 7.5) + abs(y - 7.5) <= 2.5 else light
    b.load()[7, 7] = acc; b.load()[8, 8] = acc
    save(b, BLOCK, "corporea_bridge_port_top")
    bs = plain_panel(t)
    glyph(bs, ["a.....a", ".a...a.", "..a.a..", "...a..."], {"a": acc, ".": None}, 5, 6)
    save(bs, BLOCK, "corporea_bridge_port_side")


def corporea_terminal():
    t = "corporea"
    acc = ACCENT[t]
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[t]]
    f = plain_panel(t)
    recess(f, 2, 2, 13, 13, t, "#120a1e")
    for gy in range(3):
        for gx in range(3):
            x0, y0 = 4 + gx * 3, 4 + gy * 3
            fill(f, x0, y0, x0 + 1, y0 + 1, hexc("#4a2a6e"))
    f.load()[7, 7] = acc; f.load()[8, 7] = acc; f.load()[7, 8] = acc; f.load()[8, 8] = spark
    save(f, BLOCK, "corporea_crafting_terminal_front")
    top = plain_panel(t)
    glyph(top, ["aaaaaa", "a....a", "a.bb.a", "a.bb.a", "a....a", "aaaaaa"],
          {"a": light, "b": acc, ".": None}, 5, 5)
    save(top, BLOCK, "corporea_crafting_terminal_top")
    save(plain_panel(t), BLOCK, "corporea_crafting_terminal_side")


def archive_pylon_base():
    """The pedestal under the floating crystal: four drive bays on the front."""
    t = "corporea"
    acc = ACCENT[t]
    f = plain_panel(t)
    for i in range(4):
        y0 = 3 + i * 3
        recess(f, 4, y0, 11, y0 + 1, t)
        f.load()[10, y0] = acc  # indicator LED (the renderer lights it per mounted drive)
    save(f, BLOCK, "corporea_archive_pylon_front")
    save(plain_panel(t), BLOCK, "corporea_archive_pylon_side")
    top = plain_panel(t)
    for y in range(4, 12):
        for x in range(4, 12):
            if math.hypot(x - 7.5, y - 7.5) < 3.8: top.load()[x, y] = hexc("#2a1440")
            if 2.0 < math.hypot(x - 7.5, y - 7.5) < 3.0: top.load()[x, y] = acc
    save(top, BLOCK, "corporea_archive_pylon_top")


# ======================================================================== items
def items():
    # Arcane Corporea Spark: violet star with a green terrasteel heart
    save(_item([
        "................", "................", ".......a........", "......aba.......",
        "......aba.......", ".....abcba......", "..aaabcdcbaaa...", "...abccdccba....",
        "....abcdcba.....", "...abbcdcbba....", "...ab.aca.ba....", "..ab..aba..ba...",
        "..a....a....a...", "................", "................", "................"],
        {"a": "#3a1856", "b": "#a560d4", "c": "#efd2ff", "d": "#7dff8a"}), ITEM, "arcane_corporea_spark")
    # Wireless terminal: handheld slate with an antenna
    save(_item([
        "..........a.....", "..........a.....", ".........aba....", "...aaaaaaaaaaa..",
        "...abbbbbbbbba..", "...abcccccccba..", "...abcdcdcdcba..", "...abccccccсba..",
        "...abcdcdcdcba..", "...abcccccccba..", "...abbbbbbbbba..", "...abeeebfffba..",
        "...abbbbbbbbba..", "...aaaaaaaaaaa..", "................", "................"],
        {"a": "#1a0a28", "b": "#6a2c94", "c": "#120a1e", "d": "#d27bff", "e": "#7dff8a", "f": "#ffd25a"}),
        ITEM, "corporea_wireless_terminal")
    # Terra Drives: same cartridge, tier stripe colour + pip count
    tiers = {"16k": ("#9aa39a", 1), "64k": ("#5fc8ff", 2), "256k": ("#ff8ae6", 3), "1m": ("#ffd25a", 4)}
    for tier, (stripe, pips) in tiers.items():
        rows = [
            "................", "................", "..aaaaaaaaaaaa..", "..abbbbbbbbbba..",
            "..abccccccccba..", "..abcddddddcba..", "..abcdeeeedcba..", "..abcddddddcba..",
            "..abccccccccba..", "..abssssssssba..", "..abssssssssba..", "..abbbbbbbbbba..",
            "..abppppbbbbba..", "..aaaaaaaaaaaa..", "................", "................"]
        rows[12] = "..ab" + "".join("p" if i < pips else "b" for i in range(4)) + "bbbbba.."
        save(_item(rows, {"a": "#123f17", "b": "#3fa63a", "c": "#163d13", "d": "#22702a", "e": "#e2ffc6",
                          "s": stripe, "p": "#ffffff"}), ITEM, f"terra_drive_{tier}")


def _item(rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glyph(img, rows, {k: hexc(v) for k, v in pal.items()} | {".": None, "с": hexc(pal["c"])})
    return img


# ======================================================================== Tier 5 (gaia / airships)
def tier5():
    t = "gaia"
    rim, shadow, body, light, spark = [hexc(c) for c in g.METALS[t]]
    acc = ACCENT[t]
    save(panel(t), BLOCK, "gaia_casing")
    # levitation pylon pedestal (the crystal itself is the 64x64 model texture)
    lp = plain_panel("terrasteel")
    for y in range(4, 12):
        for x in range(4, 12):
            d = math.hypot(x - 7.5, y - 7.5)
            if 2.2 < d < 3.4: lp.load()[x, y] = hexc("#7dffd8")
    save(lp, BLOCK, "mana_levitation_pylon_top")
    save(plain_panel("terrasteel"), BLOCK, "mana_levitation_pylon_side")
    # magic circle (BER decal under the ship), 64x64 with transparency
    circle = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    cp = circle.load()
    for y in range(64):
        for x in range(64):
            d = math.hypot(x - 31.5, y - 31.5)
            a = math.atan2(y - 31.5, x - 31.5)
            on = 29 < d < 31 or 21 < d < 22.2 or (22.2 < d < 29 and int((a + math.pi) / (2 * math.pi) * 24) % 2 == 0 and abs(d - 25.5) < 0.8)
            star = abs(math.sin(5 * a / 2)) * 20 > d - 1 and d < 21 and abs(abs(math.sin(5 * a / 2)) * 20 - d) < 1.2
            if on or star:
                cp[x, y] = (125, 255, 216, 220)
    circle.save(os.path.join(MODEL, "levitation_magic_circle.png"))
    written.append("entity/levitation_magic_circle")
    # bifrost projector: prism lens
    bf = plain_panel(t)
    rainbow = ["#ff5a5a", "#ffb34d", "#fff35a", "#6dff6d", "#5ad7ff", "#7a7aff", "#d77aff"]
    for y in range(4, 12):
        for x in range(4, 12):
            if abs(x - 7.5) + abs(y - 7.5) < 4.6:
                bf.load()[x, y] = hexc(rainbow[(x + y) % 7])
    bf.load()[6, 6] = spark
    save(bf, BLOCK, "bifrost_projector_front")
    save(plain_panel(t), BLOCK, "bifrost_projector_side")
    # bifrost trail: translucent rainbow glass (render type: translucent)
    tr = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, gg, b, _ = hexc(rainbow[((x + y) // 2) % 7])
            edge = x in (0, 15) or y in (0, 15)
            tr.load()[x, y] = (min(255, r + 40), min(255, gg + 40), min(255, b + 40), 220 if edge else 120)
    save(tr, BLOCK, "bifrost_trail")
    # flugel navigation core: eye
    fl = plain_panel(t)
    for y in range(4, 12):
        for x in range(3, 13):
            if ((x - 7.5) / 5) ** 2 + ((y - 7.5) / 3.2) ** 2 <= 1:
                fl.load()[x, y] = hexc("#f0e8ff")
    for y in range(5, 11):
        for x in range(5, 11):
            if math.hypot(x - 7.5, y - 7.5) < 2.4: fl.load()[x, y] = hexc("#3fa6ff")
    fl.load()[7, 7] = hexc("#081a33"); fl.load()[8, 8] = hexc("#081a33"); fl.load()[6, 6] = spark
    save(fl, BLOCK, "flugel_navigation_core_front")
    save(plain_panel(t), BLOCK, "flugel_navigation_core_side")
    # gaia thruster: nozzle
    th = plain_panel(t)
    for y in range(2, 14):
        for x in range(2, 14):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 5.6: th.load()[x, y] = shadow
            if d < 4.4: th.load()[x, y] = hexc("#1a1022")
            if d < 2.6: th.load()[x, y] = acc
            if d < 1.2: th.load()[x, y] = hexc("#fff0f8")
    save(th, BLOCK, "gaia_thruster_nozzle")
    tside = plain_panel(t)
    for x in range(3, 13):
        tside.load()[x, 7] = acc if x % 3 else spark
    save(tside, BLOCK, "gaia_thruster_side")
    # gaia arcane cannon: muzzle + barrel side
    cm = plain_panel(t)
    for y in range(3, 13):
        for x in range(3, 13):
            d = math.hypot(x - 7.5, y - 7.5)
            if 3.6 < d < 4.8: cm.load()[x, y] = light
            elif d <= 3.6: cm.load()[x, y] = hexc("#140a18")
    for (x, y) in [(7, 5), (10, 7), (8, 10), (5, 8)]:
        cm.load()[x, y] = acc
    save(cm, BLOCK, "gaia_arcane_cannon_muzzle")
    cs = plain_panel(t)
    for y in (5, 10):
        for x in range(2, 14):
            cs.load()[x, y] = rim
    for x in range(3, 13, 2):
        cs.load()[x, 7] = acc; cs.load()[x + 1, 8] = acc
    save(cs, BLOCK, "gaia_arcane_cannon_side")


# ======================================================================== pylon crystals (64x64 model textures)
# Geometry (defined in VEPylonModel.java; box UVs follow vanilla's cube layout):
#   crystal  (texOffs 0,0)   4 x 14 x 4   centre spire
#   shard    (texOffs 16,0)  2 x 10 x 2   four of them orbit the spire
#   tip      (texOffs 24,0)  2 x 4 x 2    cap above the spire
#   ring     (texOffs 0,20)  10 x 1 x 10  two floating rings, counter-rotating
def paint_box(px, u, v, w, h, d, colfn):
    faces = [  # (x0, y0, width, height, face)
        (u + d, v, w, d, "up"), (u + d + w, v, w, d, "down"),
        (u, v + d, d, h, "side"), (u + d, v + d, w, h, "front"),
        (u + d + w, v + d, d, h, "side"), (u + 2 * d + w, v + d, w, h, "back"),
    ]
    for x0, y0, fw, fh, face in faces:
        for y in range(fh):
            for x in range(fw):
                px[x0 + x, y0 + y] = colfn(face, x, y, fw, fh)


def pylon_texture(name, pal):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    dark, mid, bright, white, band = [hexc(c) for c in pal]

    def crystal(face, x, y, fw, fh):
        if face in ("up", "down"): return white
        t = y / max(1, fh - 1)
        edge = fw > 2 and (x == 0 or x == fw - 1)
        c = dark if edge else bright if t < 0.35 else mid
        if x == (0 if fw <= 2 else 1) and not edge: c = white if t < 0.6 else bright
        if (y + x * 3) % 7 == 0 and not edge: c = band
        return c

    def ring(face, x, y, fw, fh):
        if face in ("up", "down"):
            inner = 2 <= x < fw - 2 and 2 <= y < fh - 2
            return (0, 0, 0, 0) if inner else band if (x + y) % 3 == 0 else mid
        return dark if x % 2 else band

    paint_box(px, 0, 0, 4, 14, 4, crystal)
    paint_box(px, 16, 0, 2, 10, 2, crystal)
    paint_box(px, 24, 0, 2, 4, 2, lambda f, x, y, fw, fh: white if y < 2 else bright)
    paint_box(px, 0, 20, 10, 1, 10, ring)
    img.save(os.path.join(MODEL, name + ".png"))
    written.append("entity/" + name)


def main():
    elementium_logistics()
    terrasteel_power()
    corporea_pipe()
    corporea_ports()
    corporea_terminal()
    archive_pylon_base()
    items()
    tier5()
    pylon_texture("mana_levitation_pylon", ["#0f4a3a", "#2bbf8e", "#7dffd8", "#e8fff8", "#ffd25a"])
    pylon_texture("corporea_archive_pylon", ["#2a0e44", "#7a3cc0", "#c98bff", "#f6e8ff", "#7dff8a"])
    print(f"{len(written)} textures written")
    for w in written:
        print("  ", w)


if __name__ == "__main__":
    main()
