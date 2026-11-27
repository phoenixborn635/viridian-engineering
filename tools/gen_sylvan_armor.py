#!/usr/bin/env python3
"""
Sylvan armor, 3D (2026-11-25, user: "like Botania's Terrasteel set, lavender, slightly more rounded
spikes, a real 3D model when worn"). One source of truth for the worn model:

  * the part tree below (helm with swept leaf blades, cuirass with a crystal, layered pauldrons with
    blades, vambraces, belt, greaves, boots with heel fins). Blades are chains of shrinking segments,
    each turned a little further than the last, so they curve and end in a rounded tip;
  * a UV packer that gives every cube its own patch of a 64x128 texture;
  * a painter for that texture (lavender plates with soft top light and darker rims, white-gold trim,
    pale blade leaves with a gold edge, soft green crystals, a hint of moss low on the plates);
  * the Java class client/SylvanArmorGeometry.java (generated: do not edit by hand) that builds the
    two meshes (outer: helmet, chestplate, boots; inner: leggings) with exactly these cubes and UVs.

The parts are in the vanilla humanoid part spaces (pixels, y down, front -z; arm pivots at x -5/+5,
y 2; leg pivots x -1.9/+1.9, y 12). Left-side parts are the right side mirrored, sharing its UVs.
Geometry and art are original. Usage: gen_sylvan_armor.py <assets/viridian_engineering dir> <java src root>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_phase4_art import hexc, h2  # noqa: E402
from PIL import Image  # noqa: E402

ASSETS = sys.argv[1] if len(sys.argv) > 1 else "."
JAVA = sys.argv[2] if len(sys.argv) > 2 else "."
TEX_W, TEX_H = 64, 128

LAV = [hexc(c) for c in ("#241a36", "#3d2f5c", "#5f4c8c", "#8a74b8", "#b7a4dd", "#e2d8f6", "#f7f3ff")]
GOLD = [hexc(c) for c in ("#3a2608", "#6e4a12", "#b07d24", "#e8b84a", "#ffe39a", "#fff8e0")]
GEM = [hexc(c) for c in ("#0b3320", "#16703a", "#35b866", "#8ff0b4", "#e6fff0")]
WOOD = [hexc(c) for c in ("#3b2418", "#5a3826", "#764b33", "#946345", "#b07e5a")]
MOSS = [hexc(c) for c in ("#24421a", "#3b6a28", "#5c9a3a")]


# ============================================================================ the part tree
class Part:
    def __init__(self, name, parent, offset=(0, 0, 0), rot=(0, 0, 0)):
        self.name, self.parent, self.offset, self.rot = name, parent, offset, rot
        self.cubes = []          # (x, y, z, w, h, d, kind, grow, mirror, uvkey)
        self.children = []


class Mesh:
    def __init__(self):
        self.parts = {}
        for name, off in (("head", (0, 0, 0)), ("hat", (0, 0, 0)), ("body", (0, 0, 0)),
                          ("right_arm", (-5, 2, 0)), ("left_arm", (5, 2, 0)),
                          ("right_leg", (-1.9, 12, 0)), ("left_leg", (1.9, 12, 0))):
            self.parts[name] = Part(name, None, off)

    def part(self, name, parent, offset=(0, 0, 0), rot=(0, 0, 0)):
        p = Part(name, parent, offset, rot)
        self.parts[name] = p
        self.parts[parent].children.append(p)
        return p

    def cube(self, part, x, y, z, w, h, d, kind, grow=0.0, uvkey=None, mirror=False):
        self.parts[part].cubes.append((x, y, z, w, h, d, kind, grow, mirror, uvkey or f"{part}.{len(self.parts[part].cubes)}"))


def mirrored(x, w):
    return -x - w


def blade(m, name, parent, at, rot, length, width=2, side=1, uvbase=None):
    """A curved leaf blade growing up (-y) from `at`: segments shrink and each turns 14 degrees further
    back (xRot) and outward (zRot by side), so the tip curls and ends rounded (1 px wide)."""
    import math
    n = max(1, math.ceil(length / 3))
    segs = []
    left = length
    for i in range(n):
        h = min(3, left)
        w = width if n == 1 else max(1, round(width - (width - 1) * i / (n - 1)))
        segs.append((w, h))
        left -= h
    prev, pos, r = parent, at, rot
    for i, (w, h) in enumerate(segs):
        pn = f"{name}_{i}"
        m.part(pn, prev, pos, r)
        key = f"{uvbase or name}_{i}"
        m.cube(pn, -w / 2, -h, -0.5, w, h, 1, "blade", 0.0, key, mirror=(side < 0))
        prev, pos, r = pn, (0, -h, 0), (-14, 0, 10 * side)


def build():
    outer, inner = Mesh(), Mesh()
    o = outer
    # ---------------------------------------------------------------- helmet
    o.part("helm", "head")
    o.cube("helm", -4.5, -8.6, -4.5, 9, 9, 9, "plate")
    o.cube("helm", -4.5, -5.6, -4.5, 9, 1, 9, "rim", 0.3, "helm.band")
    o.cube("helm", -1, -10.1, -4.6, 2, 2, 9, "trim", 0.0, "helm.crest")
    o.cube("helm", -1, -6.2, -5.4, 2, 4, 1, "trim", 0.0, "helm.nasal")
    o.cube("helm", -1, -8.4, -5.5, 2, 2, 1, "gem", 0.0, "helm.gem")
    o.cube("helm", -5.2, -4.4, -3, 1, 4, 5, "plate", 0.0, "helm.cheek")
    o.cube("helm", mirrored(-5.2, 1), -4.4, -3, 1, 4, 5, "plate", 0.0, "helm.cheek", mirror=True)
    for i, (z, y, rot, ln) in enumerate(((-2.5, -6, (-18, -12, -30), 6), (0.5, -6.5, (-34, -18, -38), 8),
                                         (3.0, -5.5, (-52, -22, -42), 10))):
        blade(o, f"helm_blade_r{i}", "helm", (-4.5, y, z), rot, ln, 3, 1, f"helm_blade{i}")
        blade(o, f"helm_blade_l{i}", "helm", (4.5, y, z), (rot[0], -rot[1], -rot[2]), ln, 3, -1, f"helm_blade{i}")
    blade(o, "helm_blade_back", "helm", (0, -8.6, 3.5), (-62, 0, 0), 7, 3, 1)

    # ---------------------------------------------------------------- chestplate
    o.part("cuirass", "body")
    o.cube("cuirass", -5, -0.5, -3, 10, 7, 6, "plate")
    o.cube("cuirass", -4, -1.5, -3, 8, 1, 6, "rim", 0.0, "cuirass.gorget")
    o.cube("cuirass", -4.5, 6.5, -2.5, 9, 4, 5, "plate", 0.0, "cuirass.abdomen")
    o.cube("cuirass", -4.5, 6.5, -2.5, 9, 1, 5, "rim", 0.25, "cuirass.waist")
    o.cube("cuirass", -1.5, 1.5, -3.8, 3, 3, 1, "gem", 0.0, "cuirass.gem")
    o.cube("cuirass", -2.5, 1, -3.5, 5, 4, 1, "trim", 0.0, "cuirass.setting")
    o.cube("cuirass", -1, 0, 3, 2, 7, 1, "rim", 0.0, "cuirass.spine")
    for side, arm in ((1, "right_arm"), (-1, "left_arm")):
        pn = f"pauldron_{'r' if side > 0 else 'l'}"
        o.part(pn, arm)
        mir = side < 0
        fx = (lambda x, w: x) if not mir else mirrored
        o.cube(pn, fx(-4, 5), -3, -3, 5, 4, 6, "plate", 0.0, "pauldron.main", mir)
        o.cube(pn, fx(-3.5, 4), -4, -2.5, 4, 1, 5, "plate", 0.0, "pauldron.cap", mir)
        o.cube(pn, fx(-4, 5), 0.6, -3, 5, 1, 6, "rim", 0.15, "pauldron.rim", mir)
        o.cube(pn, fx(-3.5, 5), 1.5, -2.5, 5, 8.5, 5, "plate", 0.0, "vambrace", mir)
        o.cube(pn, fx(-3.5, 5), 9, -2.5, 5, 1, 5, "rim", 0.3, "vambrace.cuff", mir)
        x0 = -4 * side
        blade(o, f"{pn}_blade0", pn, (x0, -3, -1), (-28, -22 * side, -38 * side), 7, 3, side, "pauldron_blade0")
        blade(o, f"{pn}_blade1", pn, (x0, -2, 2), (-46, -28 * side, -56 * side), 5, 2, side, "pauldron_blade1")

    # ---------------------------------------------------------------- boots
    for side, leg in ((1, "right_leg"), (-1, "left_leg")):
        pn = f"boot_{'r' if side > 0 else 'l'}"
        o.part(pn, leg)
        mir = side < 0
        fx = (lambda x, w: x) if not mir else mirrored
        o.cube(pn, fx(-2.5, 5), 8, -2.5, 5, 4, 5, "plate", 0.0, "boot.main", mir)
        o.cube(pn, fx(-3, 6), 7, -3, 6, 2, 6, "rim", 0.0, "boot.cuff", mir)
        o.cube(pn, fx(-2.5, 5), 10, -3.5, 5, 2, 1, "plate", 0.0, "boot.toe", mir)
        blade(o, f"{pn}_fin", pn, (-3 * side, 9, 1.5), (-58, 0, -24 * side), 5, 2, side, "boot_fin")

    # ---------------------------------------------------------------- leggings (inner mesh)
    i = inner
    i.part("belt", "body")
    i.cube("belt", -4.5, 8.5, -2.5, 9, 3, 5, "plate", 0.0, "belt")
    i.cube("belt", -4.5, 8.5, -2.5, 9, 1, 5, "rim", 0.2, "belt.band")
    i.cube("belt", -1, 9, -3.2, 2, 2, 1, "gem", 0.0, "belt.gem")
    for side, leg in ((1, "right_leg"), (-1, "left_leg")):
        pn = f"greave_{'r' if side > 0 else 'l'}"
        i.part(pn, leg)
        mir = side < 0
        fx = (lambda x, w: x) if not mir else mirrored
        i.cube(pn, fx(-2.5, 5), -0.2, -2.5, 5, 7, 5, "plate", 0.0, "greave", mir)
        i.cube(pn, fx(-2, 4), 3, -3.4, 4, 3, 1, "trim", 0.0, "greave.knee", mir)
        i.cube(pn, fx(-2.7, 1), 0, -1.5, 1, 5, 3, "plate", 0.0, "greave.tasset", mir)
    return outer, inner


# ============================================================================ UV packing
def pack(meshes):
    """Shelf-packs one patch per uvkey (patch = 2(d+w) x (d+h), rounded up)."""
    import math
    sizes = {}
    for m in meshes:
        for p in m.parts.values():
            for (x, y, z, w, h, d, kind, grow, mir, key) in p.cubes:
                pw, ph = math.ceil(2 * (d + w)), math.ceil(d + h)
                if key in sizes:
                    assert sizes[key][:2] == (pw, ph), key
                else:
                    sizes[key] = (pw, ph, (w, h, d, kind))
    order = sorted(sizes, key=lambda k: (-sizes[k][1], -sizes[k][0]))
    uv, x, y, row = {}, 0, 0, 0
    for k in order:
        pw, ph, _ = sizes[k]
        if x + pw > TEX_W:
            x, y, row = 0, y + row, 0
        uv[k] = (x, y)
        x += pw
        row = max(row, ph)
    assert y + row <= TEX_H, f"texture full ({y + row} px)"
    return uv, sizes


# ============================================================================ painting
def paint(uv, sizes):
    import math
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    for k, (u, v) in uv.items():
        _, _, (w, h, d, kind) = sizes[k]
        w, h, d = math.ceil(w), math.ceil(h), math.ceil(d)
        seed = sum(ord(c) for c in k)
        faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                 "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
        for face, (x0, y0, fw, fh) in faces.items():
            for yy in range(fh):
                for xx in range(fw):
                    c = shade(kind, face, xx, yy, fw, fh, seed)
                    if c is not None and 0 <= x0 + xx < TEX_W and 0 <= y0 + yy < TEX_H:
                        px[x0 + xx, y0 + yy] = c
    return img


def shade(kind, face, x, y, w, h, seed):
    edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
    n = h2(x, y, seed)
    light = {"top": 1.0, "front": 0.0, "left": -0.3, "right": -0.3, "back": -0.5, "bottom": -1.2}[face]
    if kind == "plate":
        # soft vertical light on the sides, a darker rim, a few rivets, moss creeping up from the bottom edge
        v = 3.4 + light - (y / max(1, h - 1)) * 0.9 * (face not in ("top", "bottom")) + (n - 0.5) * 0.35
        if edge:
            v -= 1.0
        if face in ("front", "left", "right", "back") and y == h - 1 and h > 3 and h2(x, y, seed + 7) > 0.6:
            return MOSS[1 + int(n * 2)]
        if face == "front" and h >= 4 and w >= 4 and (x, y) in ((1, 1), (w - 2, 1)):
            return GOLD[3]
        return LAV[max(0, min(6, int(round(v))))]
    if kind == "trim":
        # polished white-gold: lit top, a bright line along the upper edge, darker lower edge
        v = 3 + (1 if face == "top" else 0) - (1 if face == "bottom" else 0)
        if face not in ("top", "bottom"):
            v += 1 if y == 0 else (-1 if y == h - 1 and h > 1 else 0)
        if n > 0.93:
            v += 1
        return GOLD[max(1, min(5, v))]
    if kind == "rim":
        # a dark lavender band with one fine white-gold line along its top edge
        if face in ("front", "back", "left", "right") and y == 0 and h > 1:
            return GOLD[3]
        return LAV[2] if face != "top" else LAV[3]
    if kind == "blade":
        # pale lavender leaf: lighter toward the middle, a fine gold midrib only on wide segments
        if face in ("front", "back"):
            if w >= 3 and x == w // 2:
                return GOLD[4]
            return LAV[6] if (0 < x < w - 1) else LAV[5]
        return LAV[4] if face in ("left", "right") else LAV[5]
    if kind == "gem":
        v = 2 + (1 if face in ("front", "top") else 0) + (1 if (x == 0 and y == 0) else 0) - (1 if edge and face != "top" else 0)
        return GEM[max(0, min(4, v))]
    if kind == "wood":
        return WOOD[2 + (1 if n > 0.6 else 0)]
    return None


# ============================================================================ Java
def jf(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return (s if s not in ("-0", "") else "0") + "F"


def java(outer, inner, uv):
    lines = [
        "package dev.viridianengineering.client;",
        "",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeDeformation;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "",
        "/**",
        " * GENERATED by tools/gen_sylvan_armor.py from its part tree, together with",
        " * textures/models/armor/sylvan_3d.png. Do not edit by hand: change the generator and re-run it.",
        " */",
        "public final class SylvanArmorGeometry {",
        f"    public static final int TEX_W = {TEX_W}, TEX_H = {TEX_H};",
        "",
        "    private SylvanArmorGeometry() {}",
        "",
        "    private static float r(float degrees) { return degrees * (float) Math.PI / 180F; }",
        "",
    ]
    for name, m in (("outer", outer), ("inner", inner)):
        lines.append(f"    public static MeshDefinition {name}() {{")
        lines.append("        MeshDefinition mesh = new MeshDefinition();")
        lines.append("        PartDefinition root = mesh.getRoot();")
        emitted = set()

        def emit(p, var_parent):
            var = "p_" + p.name
            cb = "CubeListBuilder.create()"
            for (x, y, z, w, h, d, kind, grow, mir, key) in p.cubes:
                u, v = uv[key]
                cb += f".texOffs({u}, {v})" + (".mirror()" if mir else ".mirror(false)")
                cb += f".addBox({jf(x)}, {jf(y)}, {jf(z)}, {jf(w)}, {jf(h)}, {jf(d)}, new CubeDeformation({jf(grow + 0.01)}))"
            ox, oy, oz = p.offset
            rx, ry, rz = p.rot
            pose = (f"PartPose.offsetAndRotation({jf(ox)}, {jf(oy)}, {jf(oz)}, r({jf(rx)}), r({jf(ry)}), r({jf(rz)}))"
                    if any(p.rot) else f"PartPose.offset({jf(ox)}, {jf(oy)}, {jf(oz)})")
            lines.append(f"        PartDefinition {var} = {var_parent}.addOrReplaceChild(\"{p.name}\", {cb}, {pose});")
            emitted.add(p.name)
            for c in p.children:
                emit(c, var)

        for root_name in ("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"):
            emit(m.parts[root_name], "root")
        lines.append("        return mesh;")
        lines.append("    }")
        lines.append("")
    lines.append("}")
    path = os.path.join(JAVA, "dev", "viridianengineering", "client", "SylvanArmorGeometry.java")
    with open(path, "w", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")
    # unused-variable warnings are fine: every part is declared for readability of the tree


if __name__ == "__main__":
    o, i = build()
    uv, sizes = pack([o, i])
    os.makedirs(os.path.join(ASSETS, "textures", "models", "armor"), exist_ok=True)
    paint(uv, sizes).save(os.path.join(ASSETS, "textures", "models", "armor", "sylvan_3d.png"))
    java(o, i, uv)
    print(f"sylvan armor: {len(uv)} uv patches")
