#!/usr/bin/env python3
"""
Terra Drive cartridges as seen in an Archive Pylon's bays (2026-11-25, user: "their edges displayed in
3D like AE2 / Refined Storage drive bays"). One piece per tier, models/block/drive/cartridge_<tier>
(+ _crystal), drawn by client/ArchivePylonRenderer in each occupied bay:

  a slim cartridge (2 px wide, the bay's height) standing 0.75 px proud of the pylon's front, a
  living-wood case, a little rock pull handle, and two crystal edge strips plus a top LED in the
  tier's colour (16K Manasteel blue, 64K Terrasteel green, 256K Elementium pink, 1M Gaia gold).

Modelled for bay 0 (x 0..2) on a NORTH-facing pylon; the renderer moves it to its bay and turns it.
Faces touching the pylon body (z = 1) or the case are left out, so nothing z-fights.
100% original. Usage: gen_drive_cartridges.py <assets/viridian_engineering dir>
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_phase4_art as p4  # noqa: E402
from gen_phase4_art import el, faces, write_piece, NS  # noqa: E402

os.makedirs(os.path.join(p4.MODELS, "drive"), exist_ok=True)

TIERS = {"16k": "p5_crystal_blue", "64k": "p4_crystal_green", "256k": "p4_crystal_pink", "1m": "p5_crystal_gold"}


def no(*omit):
    return tuple(s for s in p4.ALL if s not in omit)


def cartridges():
    for tier, gem in TIERS.items():
        tx = {"case": NS + "corporea_archive_pylon_trim", "rock": NS + "p4_rock", "gem": NS + gem,
              "particle": NS + "corporea_archive_pylon_trim"}
        write_piece(f"drive/cartridge_{tier}", tx, [
            el([0.05, 3.3, 0.25], [1.95, 9.7, 1], faces("#case", sides=no("south"))),
            el([0.55, 5.0, 0.0], [1.45, 6.0, 0.25], faces("#rock", sides=no("south"))),
            el([0.05, 3.6, 0.1], [0.4, 8.2, 0.25], faces("#gem", sides=no("south")), glow=True),
            el([1.6, 3.6, 0.1], [1.95, 8.2, 0.25], faces("#gem", sides=no("south")), glow=True),
            el([0.6, 8.6, 0.1], [1.4, 9.3, 0.25], faces("#gem", sides=no("south")), glow=True),
        ])


if __name__ == "__main__":
    cartridges()
    print("drive cartridges written")
