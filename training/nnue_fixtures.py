"""Writes the small, fixed network and the reference scores the engine's tests check against.

    python nnue_fixtures.py

A network with random weights (hidden size 32, so the file is 50 KB) and 1,000 positions from
random play with the exact integer score golden.py gives each. The Java tests load the same file
and must reproduce every score: that pins the file format and the integer arithmetic down on
both sides, independently of how well any trained network plays. The weights are large enough
that sums go far into the clamped range, which is where mistakes in the arithmetic show.
"""
from __future__ import annotations

import csv
from pathlib import Path

import torch

import export
import golden
from nnue import Net

OUT = Path(__file__).parent.parent / "chess-engine" / "src" / "test" / "resources" / "nnue"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    torch.manual_seed(20261006)
    net = Net(32)
    with torch.no_grad():
        net.ft_weight.normal_(0.0, 0.3)
        net.ft_bias.normal_(0.0, 0.2)
        net.out.weight.normal_(0.0, 0.4)
        net.out.bias.fill_(0.07)
    net.clip()

    path = OUT / "tiny.nnue"
    export.write(path, export.quantise(net.export_state()))
    reference = golden.Network(path)
    boards = golden.random_positions(1000, seed=77)
    with open(OUT / "tiny-golden.csv", "w", newline="") as f:
        writer = csv.writer(f)
        for board in boards:
            writer.writerow([board.fen(), reference.evaluate(board)])
    scores = [reference.evaluate(b) for b in boards]
    print(f"wrote {path.name} ({path.stat().st_size / 1024:.0f} KB) and tiny-golden.csv: "
          f"{len(boards)} positions, scores {min(scores)}..{max(scores)}")


if __name__ == "__main__":
    main()
