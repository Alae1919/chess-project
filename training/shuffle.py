"""Merges extracted months, drops duplicate positions, shuffles, and splits off a validation set.

    python shuffle.py                       # every data/positions/*.bin -> data/train.bin, data/val.bin
    python shuffle.py 2016-01 2017-01       # just those months

The same position (same pieces, same side to move) turns up thousands of times in the opening
phase; keeping one copy stops the network spending its capacity on the first few moves.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

from records import RECORD, position_hash

DATA = Path(__file__).parent / "data"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("months", nargs="*", help="months to use (default: all extracted)")
    parser.add_argument("--val", type=float, default=0.01, help="share of positions held out for validation")
    parser.add_argument("--val-max", type=int, default=200_000, help="cap on the validation set size")
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()

    files = [DATA / "positions" / f"{m}.bin" for m in args.months] or sorted((DATA / "positions").glob("*.bin"))
    if not files:
        raise SystemExit("nothing to shuffle: run extract.py first")
    records = np.concatenate([np.fromfile(f, dtype=RECORD) for f in files])
    print(f"{len(records):,} positions from {len(files)} file(s)")

    _, first = np.unique(position_hash(records), return_index=True)
    records = records[np.sort(first)]
    print(f"{len(records):,} after removing duplicates")

    records = records[np.random.default_rng(args.seed).permutation(len(records))]
    n_val = min(args.val_max, max(1, int(len(records) * args.val)))
    val, train = records[:n_val], records[n_val:]
    train.tofile(DATA / "train.bin")
    val.tofile(DATA / "val.bin")
    print(f"train {len(train):,} -> data/train.bin, validation {len(val):,} -> data/val.bin")

    score, result = train["score"].astype(np.float32), train["result"]
    print(f"mean |score| {np.abs(score).mean():.0f} cp, "
          f"white wins {np.mean(result == 2):.1%}, draws {np.mean(result == 1):.1%}, black wins {np.mean(result == 0):.1%}")
    print(f"side to move: white {np.mean(train['stm'] == 0):.1%}; mean ply {train['ply'].mean():.1f}; "
          f"mean pieces {np.mean([bin(int(o)).count('1') for o in train['occ'][:20000]]):.1f}")


if __name__ == "__main__":
    main()
