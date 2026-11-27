#!/usr/bin/env python3
"""
GUI art (2026-11-25): the Corporea Crafting Terminal and the Reactor / Battery controller panels,
drawn in the block art's vocabulary: a living-wood frame in a dark border with glowing corner
studs, dark crystal "screens" with a faint glowing grid, living-rock panels with inset slots.
Terminal = Corporea violet; controllers = Terrasteel green screens with lava / mana gauges.

The layout constants here MUST match the Java screens:
  client/CorporeaTerminalScreen.java, client/ControllerScreens.java.
100% original procedural art. Usage: gen_gui.py <assets/viridian_engineering dir>
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_phase4_art import hexc, h2, WOOD, ROCK, GREEN, BLUE, LAVA, BORDER  # noqa: E402
from gen_items import VIOLET, GOLD  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
GUI = os.path.join(OUT, "textures", "gui")
os.makedirs(GUI, exist_ok=True)

SLOT_FILL = hexc("#5b5751")
SLOT_DARK = hexc("#2b2622")
SLOT_LIGHT = ROCK[4]


class Canvas:
    def __init__(self, w, h):
        self.img = Image.new("RGBA", (w, h))
        self.px = self.img.load()
        self.w, self.h = w, h

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def fill(self, x0, y0, x1, y1, c):
        """Inclusive rectangle; c may be a colour or fn(x, y)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c(x, y) if callable(c) else c)

    def frame(self, x0, y0, x1, y1, tl, br):
        for x in range(x0, x1 + 1):
            self.put(x, y0, tl); self.put(x, y1, br)
        for y in range(y0, y1 + 1):
            self.put(x0, y, tl); self.put(x1, y, br)
        self.put(x1, y0, BORDER if tl != br else tl)
        self.put(x0, y1, BORDER if tl != br else tl)

    def save(self, name):
        self.img.save(os.path.join(GUI, name + ".png"))


def wood_fn(seed):
    def f(x, y):
        seam = (y + (x // 9 + seed) % 2 * 2) % 5 == 0
        n = h2(x // 3, y, seed)
        c = WOOD[1] if seam else WOOD[3] if n > 0.72 else WOOD[2]
        if not seam and h2(x, y, seed + 9) > 0.95:
            c = WOOD[4]
        return c
    return f


def rock_fn(seed):
    def f(x, y):
        n = (h2(x // 2, y // 2, seed) + h2(x, y, seed + 3)) / 2
        c = ROCK[3] if n > 0.58 else ROCK[2] if n > 0.22 else ROCK[1]
        if h2(x, y, seed + 7) > 0.975:
            c = ROCK[4]
        return c
    return f


def screen_fn(pal, step=9):
    def f(x, y):
        if x % step == 0 and y % step == 0:
            return pal[2]
        if x % step == 0 or y % step == 0:
            return pal[1]
        return pal[0]
    return f


def stud(c, x, y, pal):
    c.put(x, y, pal[4]); c.put(x + 1, y, pal[3]); c.put(x, y + 1, pal[3]); c.put(x + 1, y + 1, pal[2])


def wood_window(c, w, h, pal, header=11):
    """Outer frame: dark border, living-wood body, a 2px darker header band, corner studs."""
    c.fill(0, 0, w - 1, h - 1, wood_fn(4))
    c.frame(0, 0, w - 1, h - 1, BORDER, BORDER)
    c.frame(1, 1, w - 2, h - 2, WOOD[4], WOOD[1])
    for x in range(2, w - 2):
        c.put(x, header, WOOD[0])
    for (x, y) in ((3, 3), (w - 5, 3), (3, h - 5), (w - 5, h - 5)):
        stud(c, x, y, pal)


def inset_panel(c, x0, y0, x1, y1, body, rim_dark, rim_light):
    c.fill(x0, y0, x1, y1, body)
    c.frame(x0 - 1, y0 - 1, x1 + 1, y1 + 1, rim_dark, rim_light)


def slot(c, x, y, size=18):
    """Vanilla-geometry slot (item at x+1,y+1) in rock colours."""
    c.fill(x, y, x + size - 1, y + size - 1, SLOT_FILL)
    for i in range(size):
        c.put(x + i, y, SLOT_DARK); c.put(x, y + i, SLOT_DARK)
        c.put(x + i, y + size - 1, SLOT_LIGHT); c.put(x + size - 1, y + i, SLOT_LIGHT)
    c.put(x + size - 1, y, SLOT_FILL); c.put(x, y + size - 1, SLOT_FILL)


def screen_slot(c, x, y, pal):
    c.fill(x, y, x + 17, y + 17, pal[0])
    for i in range(18):
        c.put(x + i, y, BORDER); c.put(x, y + i, BORDER)
        c.put(x + i, y + 17, pal[2]); c.put(x + 17, y + i, pal[2])
    c.put(x + 1, y + 1, pal[1])


# =================================================================================== terminal
def terminal():
    W, H = 256, 266
    c = Canvas(256, 272)
    wood_window(c, W, H, VIOLET)
    # network screen (left)
    inset_panel(c, 5, 13, 154, 175, screen_fn(VIOLET), BORDER, VIOLET[2])
    # search field
    inset_panel(c, 8, 15, 119, 25, VIOLET[0], VIOLET[1], VIOLET[3])
    # grid 8x8 at (8,30): slot bg from (7,29)
    for gy in range(8):
        for gx in range(8):
            screen_slot(c, 7 + gx * 18, 29 + gy * 18, VIOLET)
    # right rock panel
    inset_panel(c, 158, 13, 250, 175, rock_fn(7), BORDER, ROCK[4])
    for x in range(158, 251):  # wood band under the header label
        c.put(x, 25, WOOD[2]); c.put(x, 26, WOOD[1])
    for gy in range(3):
        for gx in range(3):
            slot(c, 165 + gx * 18, 39 + gy * 18)
    # result: crystal-rimmed 22x22 around (230,58)
    c.fill(225, 53, 250 - 1, 78, VIOLET[1])
    c.frame(225, 53, 249, 78, VIOLET[3], VIOLET[1])
    slot(c, 229, 57)
    # arrow grid -> result
    for i, x in enumerate(range(220, 225)):
        c.put(x, 66, VIOLET[3])
    for d in range(3):
        c.put(224 - d, 66 - d, VIOLET[4]); c.put(224 - d, 66 + d, VIOLET[4])
    # dividers
    for x in range(161, 248):
        c.put(x, 97, WOOD[1]); c.put(x, 98, WOOD[3])
        c.put(x, 136, WOOD[1]); c.put(x, 137, WOOD[3])
    # page-number plate
    inset_panel(c, 177, 122, 232, 133, ROCK[1], SLOT_DARK, ROCK[4])
    # quantity field
    inset_panel(c, 192, 140, 232, 150, VIOLET[0], VIOLET[1], VIOLET[3])
    # job-status strip
    inset_panel(c, 161, 155, 232, 166, VIOLET[0], VIOLET[1], VIOLET[3])
    # inventory panel
    inset_panel(c, 5, 179, 250, 262, rock_fn(11), BORDER, ROCK[4])
    for gy in range(3):
        for gx in range(9):
            slot(c, 47 + gx * 18, 183 + gy * 18)
    for gx in range(9):
        slot(c, 47 + gx * 18, 241)
    # side emblems: small faceted crystals in wood sockets
    for cx in (26, 229):
        for y in range(190, 252):
            for x in range(cx - 11, cx + 12):
                d = abs(x - cx) + abs(y - 221) * 0.42
                if d <= 11:
                    c.put(x, y, WOOD[1] if d > 9.5 else WOOD[2] if d > 7.5 else
                          (VIOLET[4] if (x <= cx and y <= 221) else VIOLET[3] if (x <= cx or y <= 221) else VIOLET[2]))
        c.put(cx - 2, 214, VIOLET[5]); c.put(cx - 1, 213, VIOLET[5])
    c.save("corporea_terminal")


# =================================================================================== widgets
ICONS = {
    "sort_name": [
        ".###....#.",
        "#...#...#.",
        "#####...#.",
        "#...#...#.",
        "#...#...#.",
        "........#.",
        "#####...#.",
        "...#...###",
        "..#.....#.",
        "#####.....",
    ],
    "sort_count": [
        "##........",
        "##........",
        "##.##.....",
        "##.##.....",
        "##.##.##..",
        "##.##.##..",
        "##.##.##.#",
        "##.##.##.#",
        "##.##.##.#",
        "..........",
    ],
    "sort_mod": [
        "..######..",
        ".#......#.",
        "#..##....#",
        "#..##....#",
        "#........#",
        ".#......#.",
        "..#....#..",
        "...#..#...",
        "....##....",
        "..........",
    ],
    "to_patterns": [
        "########..",
        "#......#..",
        "#.#.#.##..",
        "#......#..",
        "#.#.#.#...",
        "#......####",
        "#.#.#.#.#.",
        "#.......#.",
        "#########.",
        "..........",
    ],
    "to_items": [
        ".########.",
        "#........#",
        "#........#",
        "##########",
        "#...##...#",
        "#...##...#",
        "#........#",
        "#........#",
        ".########.",
        "..........",
    ],
    "record": [
        "....#.....",
        "...###....",
        "..#.#.#...",
        ".#..#..#..",
        "#########.",
        ".#..#..#..",
        "..#.#.#...",
        "...###....",
        "....#.....",
        "..........",
    ],
    "left": [
        "......#...",
        ".....##...",
        "....###...",
        "...####...",
        "..#####...",
        "...####...",
        "....###...",
        ".....##...",
        "......#...",
        "..........",
    ],
    "right": [
        "...#......",
        "...##.....",
        "...###....",
        "...####...",
        "...#####..",
        "...####...",
        "...###....",
        "...##.....",
        "...#......",
        "..........",
    ],
    "cancel": [
        "..........",
        ".##....##.",
        "..##..##..",
        "...####...",
        "....##....",
        "...####...",
        "..##..##..",
        ".##....##.",
        "..........",
        "..........",
    ],
    "jobs": [
        "...####...",
        "..#....#..",
        ".#..##..#.",
        ".#.#..#.#.",
        ".#.#..#.#.",
        ".#..##..#.",
        "..#....#..",
        "...####...",
        "....##....",
        "..........",
    ],
}
ICON_ORDER = ["sort_name", "sort_count", "sort_mod", "to_patterns", "to_items", "record", "left", "right", "cancel", "jobs"]


def widgets():
    c = Canvas(128, 64)
    # 14x14 buttons: normal (0,0), hover (14,0), disabled (28,0)
    for i, (body, rim_hi, rim_lo, glow) in enumerate((
            (wood_fn(2), WOOD[4], WOOD[1], None),
            (wood_fn(2), VIOLET[4], VIOLET[2], VIOLET[3]),
            (lambda x, y: WOOD[1], WOOD[2], WOOD[0], None))):
        x0 = i * 14
        c.fill(x0, 0, x0 + 13, 13, body)
        c.frame(x0, 0, x0 + 13, 13, BORDER, BORDER)
        c.frame(x0 + 1, 1, x0 + 12, 12, rim_hi, rim_lo)
        if glow:
            for d in ((2, 2), (11, 2), (2, 11), (11, 11)):
                c.put(x0 + d[0], d[1], glow)
    # 10x10 icons at (i*10, 16): light glyph with a dark drop shadow
    for i, name in enumerate(ICON_ORDER):
        rows = ICONS[name]
        x0 = i * 10
        for y, row in enumerate(rows):
            for x, ch in enumerate(row[:10]):
                if ch == "#":
                    if x + 1 < 10 and y + 1 < 10:
                        c.put(x0 + x + 1, 16 + y + 1, VIOLET[0]) if c.px[x0 + x + 1, 16 + y + 1][3] == 0 else None
        for y, row in enumerate(rows):
            for x, ch in enumerate(row[:10]):
                if ch == "#":
                    c.put(x0 + x, 16 + y, hexc("#f7eeff"))
    # status lamps 6x6 at (i*6, 28): powered green, unpowered red, paused amber
    for i, pal in enumerate((GREEN, [hexc(v) for v in ("#2a0406", "#5e0b10", "#a3161d", "#e8333a", "#ff8a7a", "#fff0ea")], GOLD)):
        x0 = i * 6
        for y in range(6):
            for x in range(6):
                d = ((x - 2.5) ** 2 + (y - 2.5) ** 2) ** 0.5
                if d <= 3.0:
                    c.put(x0 + x, 28 + y, BORDER if d > 2.4 else pal[4] if (x + y) < 4 else pal[3])
    c.save("terminal_widgets")


# =================================================================================== controllers
def gauge_well(c, x0, y0, x1, y1, pal):
    """Dark glass well with a wood rim; inner area = (x0+2, y0+2)-(x1-2, y1-2)."""
    c.fill(x0, y0, x1, y1, WOOD[2])
    c.frame(x0, y0, x1, y1, BORDER, BORDER)
    c.frame(x0 + 1, y0 + 1, x1 - 1, y1 - 1, WOOD[1], WOOD[4])
    c.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, lambda x, y: pal[0] if (y - y0) % 8 else pal[1])


def fill_strip(c, x0, y0, w, h, pal, seed, vertical=True):
    """Liquid fill: bright left edge, darker right edge, soft bands, sparkles."""
    for y in range(h):
        for x in range(w):
            t = x / max(1, w - 1) if vertical else y / max(1, h - 1)
            v = 3.6 - t * 1.5 + (h2(x // 2, (y if vertical else x) // 3, seed) - 0.5) * 0.6
            if (x == 0 and vertical) or (y == 0 and not vertical):
                v = 4.6
            if h2(x, y, seed + 3) > 0.975:
                v = 5
            i = max(0, min(5, int(round(v))))
            c.put(x0 + x, y0 + y, pal[i])


def ticks(c, x0, y0, h, n, colour):
    for k in range(1, n):
        y = y0 + round(k * h / n)
        c.put(x0, y, colour); c.put(x0 + 1, y, colour)


def controller(name, has_lava):
    W, H = 176, 126
    c = Canvas(256, 256)
    wood_window(c, W, H, GREEN)
    inset_panel(c, 5, 13, 170, 121, screen_fn(GREEN, 8), BORDER, GREEN[2])
    if has_lava:
        gauge_well(c, 8, 15, 25, 80, LAVA)        # inner (10,17)-(23,78) 14x62
        gauge_well(c, 36, 15, 53, 80, BLUE)       # inner (38,17)-(51,78)
        ticks(c, 10, 17, 62, 4, LAVA[3]); ticks(c, 38, 17, 62, 4, BLUE[3])
        # speed bar well (9,104)-(166,113): inner (11,106)-(164,111) 154x6
        gauge_well(c, 9, 104, 166, 113, GREEN)
        fill_strip(c, 180, 0, 14, 62, LAVA, 3)
        fill_strip(c, 196, 0, 14, 62, BLUE, 5)
        fill_strip(c, 0, 200, 154, 6, GREEN, 7, vertical=False)
        fill_strip(c, 0, 210, 154, 6, [hexc(v) for v in ("#2a0406", "#5e0b10", "#a3161d", "#e8333a", "#ff8a7a", "#fff0ea")], 8, vertical=False)
    else:
        gauge_well(c, 9, 15, 38, 116, BLUE)       # inner (11,17)-(36,114) 26x98
        fill_strip(c, 212, 0, 26, 98, BLUE, 9)
        # unstable overlay (red) for the same well
        fill_strip(c, 212, 100, 26, 98, [hexc(v) for v in ("#2a0406", "#5e0b10", "#a3161d", "#e8333a", "#ff8a7a", "#fff0ea")], 4)
    c.save(name)


def status_panels():
    pylon = Canvas(256, 256)
    wood_window(pylon, 176, 126, GREEN)
    inset_panel(pylon, 5, 13, 170, 121, screen_fn(GREEN, 8), BORDER, GREEN[2])
    gauge_well(pylon, 9, 15, 38, 116, BLUE)
    fill_strip(pylon, 212, 0, 26, 98, BLUE, 17)
    pylon.save("levitation_pylon")

    requests = Canvas(256, 256)
    wood_window(requests, 176, 126, GREEN)
    inset_panel(requests, 5, 13, 170, 121, screen_fn(GREEN, 8), BORDER, GREEN[2])
    for y in range(29, 98, 17):
        requests.fill(8, y, 167, y, BORDER)
    requests.save("request_interface")


if __name__ == "__main__":
    terminal()
    widgets()
    controller("reactor_controller", True)
    controller("battery_controller", False)
    status_panels()
