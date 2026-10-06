"""Quantises a trained network and writes the file the engine loads.

    python export.py runs/first/best.pt --out first.nnue

File layout, all little-endian:

    "RXNN"                      magic
    u32  version                (1)
    u32  features, hidden       (768, 256)
    u32  QA, QB, scale          (1024, 1024, 400)
    i16  ft_weights[features][hidden]     one row per input: switching a piece on adds one row
    i16  ft_bias[hidden]
    i16  out_weights[2 * hidden]          side to move's half first
    i32  out_bias
    u32  CRC-32 of everything before it

Quantisation: ft weights and bias x QA, output weights x QB, output bias x QA x QB.
See golden.py for the exact integer arithmetic that turns these into a score.
"""
from __future__ import annotations

import argparse
import struct
import zlib
from pathlib import Path

import numpy as np
import torch

from nnue import FEATURES, QA, QB, SCALE, Net

MAGIC = b"RXNN"
VERSION = 1
MAX_PIECES = 32


def quantise(state: dict) -> dict:
    ft_w = state["ft_weight"].cpu().numpy()            # features x hidden
    ft_b = state["ft_bias"].cpu().numpy()
    out_w = state["out_weight"].cpu().numpy().reshape(-1)
    out_b = float(state["out_bias"].cpu().numpy().reshape(-1)[0])
    q = {
        "ft_w": np.round(ft_w * QA).astype(np.int64),
        "ft_b": np.round(ft_b * QA).astype(np.int64),
        "out_w": np.round(out_w * QB).astype(np.int64),
        "out_b": int(round(out_b * QA * QB)),
    }
    for name in ("ft_w", "ft_b", "out_w"):
        if q[name].min() < -32768 or q[name].max() > 32767:
            raise SystemExit(f"{name} does not fit in 16 bits ({q[name].min()}..{q[name].max()})")
    # a hidden unit's running sum is held in 16 bits by the engine: it must never overflow, whatever the position
    worst = np.abs(q["ft_b"]) + np.sort(np.abs(q["ft_w"]), axis=0)[-MAX_PIECES:].sum(axis=0)
    if worst.max() > 32767:
        raise SystemExit(f"a hidden unit could overflow 16 bits ({worst.max()}); train with a tighter weight limit")
    return q


def write(path: Path, q: dict) -> None:
    hidden = q["ft_b"].shape[0]
    body = bytearray(MAGIC)
    body += struct.pack("<6I", VERSION, FEATURES, hidden, QA, QB, SCALE)
    body += q["ft_w"].astype("<i2").tobytes()
    body += q["ft_b"].astype("<i2").tobytes()
    body += q["out_w"].astype("<i2").tobytes()
    body += struct.pack("<i", q["out_b"])
    body += struct.pack("<I", zlib.crc32(bytes(body)))
    path.write_bytes(bytes(body))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("checkpoint", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    checkpoint = torch.load(args.checkpoint, map_location="cpu")
    net = Net(checkpoint["hidden"])
    net.load_state_dict(checkpoint["net"])
    q = quantise(net.export_state())
    write(args.out, q)
    print(f"wrote {args.out} ({args.out.stat().st_size / 1024:.0f} KB), hidden {q['ft_b'].shape[0]}, "
          f"ft weights {q['ft_w'].min()}..{q['ft_w'].max()}, out weights {q['out_w'].min()}..{q['out_w'].max()}, "
          f"out bias {q['out_b']}")


if __name__ == "__main__":
    main()
