#!/usr/bin/env python3
"""
Z-fighting check for the mod's JSON models (not part of the build).

Two faces z-fight when they face the same way, lie on the same plane and overlap. This walks every
model under models/ (composite children, and a renderer piece together with its <piece>_crystal half,
count as one model) and reports such pairs. Elements with a rotation are compared only with elements
that have the same rotation. Usage: check_zfight.py <assets/viridian_engineering dir>
"""
import json
import os
import sys

AXIS = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}
EPS = 1e-3


def faces_of(elements, tag):
    out = []
    for i, e in enumerate(elements or []):
        frm, to = e["from"], e["to"]
        rot = json.dumps(e.get("rotation"), sort_keys=True)
        for name in e.get("faces", {}):
            axis, side = AXIS[name]
            plane = to[axis] if side else frm[axis]
            others = [a for a in range(3) if a != axis]
            rect = [(min(frm[a], to[a]), max(frm[a], to[a])) for a in others]
            if any(hi - lo < EPS for lo, hi in rect):
                continue  # degenerate face: draws nothing
            out.append((name, plane, rect, rot, f"{tag}#{i}"))
    return out


def model_faces(path, data):
    fs = faces_of(data.get("elements"), os.path.basename(path))
    for cname, child in (data.get("children") or {}).items():
        if isinstance(child, dict):
            fs += faces_of(child.get("elements"), f"{os.path.basename(path)}:{cname}")
    return fs


def overlap(r1, r2):
    a = 1.0
    for (l1, h1), (l2, h2) in zip(r1, r2):
        d = min(h1, h2) - max(l1, l2)
        if d <= EPS:
            return 0.0
        a *= d
    return a


def check(fs):
    hits = []
    for i in range(len(fs)):
        for j in range(i + 1, len(fs)):
            a, b = fs[i], fs[j]
            if a[0] != b[0] or abs(a[1] - b[1]) > EPS or a[3] != b[3]:
                continue
            ov = overlap(a[2], b[2])
            if ov > EPS:
                hits.append((a[4], b[4], a[0], a[1], round(ov, 3)))
    return hits


def main(root):
    models = os.path.join(root, "models")
    seen, total = set(), 0
    for dirpath, _, files in os.walk(models):
        for f in sorted(files):
            if not f.endswith(".json"):
                continue
            path = os.path.join(dirpath, f)
            if path in seen:
                continue
            group = [path]
            if not f.endswith("_crystal.json"):
                twin = path[:-5] + "_crystal.json"
                if os.path.exists(twin):
                    group.append(twin)
            else:
                base = path[:-len("_crystal.json")] + ".json"
                if os.path.exists(base):
                    continue  # handled with its base
            fs = []
            for p in group:
                seen.add(p)
                with open(p) as fh:
                    try:
                        fs += model_faces(p, json.load(fh))
                    except json.JSONDecodeError:
                        print("bad json", p)
            for h in check(fs):
                total += 1
                print(f"{os.path.relpath(path, models)}: {h[0]} vs {h[1]} both {h[2]} at {h[3]} (overlap {h[4]} px^2)")
    print(f"{total} z-fighting face pairs")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ".")
