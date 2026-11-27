#!/usr/bin/env python3
"""
Terrasteel mana pipe and Corporea pipe, built exactly like the Manasteel pipe (user request
2026-11-24): Create's own pipe model system (BlockStateGen.pipe() core + Create's connection /
rim / drain / casing partials, re-pointed at our sheets by client/VEPipeModel). This script writes
  textures/block/<tier>_pipes_connected.png  (core faces, Create's 32x32 layout)
  textures/block/<tier>_pipes.png            (attachment partials, Create's `pipes` layout)
  models/block/<pipe>/core_{x,y,z}.json, item.json
The energy line and travelling pulse are not in the textures: client/PipePulseRenderer draws them
live along the pipe's real connections and flow direction.

Usage: gen_tier_pipes.py <assets/viridian_engineering dir>
"""
import copy
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
import gen_textures as gt  # noqa: E402

gt.BLOCK = os.path.join(OUT, "textures", "block")
NS = "viridian_engineering:block/"
PIPES = {"terrasteel": "terrasteel_mana_pipe", "corporea": "corporea_pipe"}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2)
    print("wrote", os.path.relpath(path, OUT))


def models(tier, pipe):
    base = os.path.join(OUT, "models", "block", pipe)
    tex = NS + f"{tier}_pipes_connected"
    for axis in "xyz":
        write(os.path.join(base, f"core_{axis}.json"), {
            "parent": f"create:block/fluid_pipe/core_{axis}",
            "textures": {"0": tex, "particle": tex},
        })
    # Item model: the Manasteel pipe's item model with this tier's sheet.
    with open(os.path.join(OUT, "models", "block", "manasteel_fluid_pipe", "item.json")) as fh:
        item = json.load(fh)
    item = copy.deepcopy(item)
    item["textures"] = {"0": tex, "particle": tex}
    write(os.path.join(base, "item.json"), item)


if __name__ == "__main__":
    for tier, pipe in PIPES.items():
        gt.manasteel_pipe_sheet(tier)
        gt.manasteel_pipe_attachments(tier)
        models(tier, pipe)
