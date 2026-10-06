"""How well does each evaluation agree with Stockfish and with the game results?

    python engine_loss.py --net runs/v1/best.pt --nnue ..\\chess-engine\\src\\main\\resources\\nnue\\default.nnue

Asks the Java engine, over UCI, for its static score of held-out positions (the classical evaluation,
and the exported network), and reports the same error the network was trained on, plus the error against
Stockfish's score alone and the correlation with it. With --net, the float network is measured too, which
shows what quantisation and the Java implementation cost (very little, if all is well).
"""
from __future__ import annotations

import argparse
import math
import os
import re
import subprocess
from pathlib import Path

import numpy as np
import torch

from golden import board_from_record
from nnue import SCALE, Net, decode
from records import RECORD

ENGINE = Path(__file__).resolve().parent.parent / "tools" / "engine" / "rexchess-uci.cmd"
STATIC = re.compile(r"Static evaluation: (-?\d+) cp")


def engine_scores(fens: list[str], options: list[str]) -> np.ndarray:
    """The engine's static score (centipawns, side to move) for each position."""
    proc = subprocess.Popen([str(ENGINE)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1,
                            env={**os.environ})
    scores = []
    try:
        for line in options:
            proc.stdin.write(f"setoption name {line}\n")
        proc.stdin.write("isready\n")
        proc.stdin.flush()
        for line in proc.stdout:
            if "readyok" in line:
                break
        else:
            raise RuntimeError("the engine did not start (is JAVA_HOME a JDK 17+, and is the engine compiled?)")
        batch = 20   # small: both pipes hold only a few KB, so a big batch would deadlock
        for start in range(0, len(fens), batch):
            chunk = fens[start:start + batch]
            proc.stdin.write("".join(f"position fen {fen}\neval\n" for fen in chunk))
            proc.stdin.flush()
            got = 0
            while got < len(chunk):
                line = proc.stdout.readline()
                if not line:
                    raise RuntimeError("the engine exited (is JAVA_HOME a JDK 17+, and is the engine compiled?)")
                match = STATIC.search(line)
                if match:
                    scores.append(int(match.group(1)))
                    got += 1
        proc.stdin.write("quit\n")
        proc.stdin.flush()
    finally:
        proc.kill()
    return np.array(scores, dtype=np.float64)


def sigmoid(x):
    return 1.0 / (1.0 + np.exp(-np.asarray(x, dtype=np.float64) / SCALE))


def report(name: str, cp: np.ndarray, score: np.ndarray, result: np.ndarray, lam: float) -> None:
    prediction = sigmoid(cp)
    trained_on = np.mean((prediction - (lam * sigmoid(score) + (1 - lam) * result)) ** 2)
    vs_stockfish = np.mean((prediction - sigmoid(score)) ** 2)
    vs_result = np.mean((prediction - result) ** 2)
    correlation = np.corrcoef(prediction, sigmoid(score))[0, 1]
    print(f"{name:<22} training error {trained_on:.5f}   vs Stockfish {vs_stockfish:.5f}   vs result {vs_result:.5f}   "
          f"correlation with Stockfish {correlation:.3f}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--val", type=Path, default=Path(__file__).parent / "data" / "val.bin")
    parser.add_argument("--count", type=int, default=20000)
    parser.add_argument("--lam", type=float, default=0.75)
    parser.add_argument("--net", type=Path, help="a training checkpoint, for the float network")
    parser.add_argument("--nnue", type=Path, help="the exported network file, for the Java engine")
    args = parser.parse_args()

    records = np.fromfile(args.val, dtype=RECORD)[:args.count]
    boards = [board_from_record(int(r["occ"]), bytes(r["pieces"]), int(r["stm"])) for r in records]
    fens = [b.fen() for b in boards]
    sign = np.where(records["stm"] == 0, 1.0, -1.0)
    score = records["score"].astype(np.float64) * sign                       # for the side to move
    result_white = records["result"].astype(np.float64) / 2.0
    result = np.where(records["stm"] == 0, result_white, 1.0 - result_white)
    print(f"{len(records):,} held-out positions; the best constant guess scores "
          f"{np.mean((0.5 - (args.lam * sigmoid(score) + (1 - args.lam) * result)) ** 2):.5f} (training error)")

    report("classical (Java)", engine_scores(fens, ["Eval value classical"]), score, result, args.lam)
    if args.net:
        checkpoint = torch.load(args.net, map_location="cpu")
        net = Net(checkpoint["hidden"])
        net.load_state_dict(checkpoint["net"])
        raw = torch.from_numpy(records.view(np.uint8).reshape(len(records), 32).copy())
        with torch.no_grad():
            cp = (net(*decode(raw)[:3]) * SCALE).numpy()
        report("network (float)", cp, score, result, args.lam)
    if args.nnue:
        cp = engine_scores(fens, [f"Eval value nnue", f"EvalFile value {args.nnue.resolve()}"])
        report("network (Java, int)", cp, score, result, args.lam)


if __name__ == "__main__":
    main()
