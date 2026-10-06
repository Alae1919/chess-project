"""Estimates how strong each level of the app's AI is, by playing it against Stockfish held back to a chosen Elo.

    python calibrate.py --stockfish ..\\tools\\engine\\external\\stockfish\\stockfish-windows-x86-64-universal.exe

For each level the opponent's Elo is moved towards the level's own over a few short matches, so the
games are close; the level's rating is then the one that best explains all the results (maximum
likelihood), with an error bar. The numbers are on Stockfish's UCI_Elo scale, which is not the same
as a human rating on Lichess or in a club, but orders the levels and puts them in a known range.
"""
from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path

import chess.engine

from match import Spec, Tally, play_match

ENGINE = Path(__file__).parent.parent / "tools" / "engine" / "rexchess-uci.cmd"
GUESS = {1: 1400, 2: 1500, 3: 1700, 4: 1900, 5: 2150, 6: 2400}   # where to start looking for each level
SF_MIN, SF_MAX = 1320, 3190                                      # the range Stockfish's UCI_Elo accepts


def expected(rating: float, opponent: float) -> float:
    return 1.0 / (1.0 + 10 ** ((opponent - rating) / 400.0))


def estimate(rounds: list[tuple[float, Tally]]) -> tuple[float, float]:
    """The rating that best explains the results against opponents of known ratings, and its standard error.

    A half point is added for and against each round, so a clean sweep still gives a finite answer."""
    def balance(r: float) -> float:
        return sum(t.games * ((t.wins + 0.5 * t.draws + 0.5) / (t.games + 1) - expected(r, opp)) for opp, t in rounds)

    lo, hi = 400.0, 4000.0
    for _ in range(60):
        mid = (lo + hi) / 2
        lo, hi = (mid, hi) if balance(mid) > 0 else (lo, mid)
    r = (lo + hi) / 2
    k = (math.log(10) / 400.0) ** 2
    information = sum(t.games * expected(r, opp) * (1 - expected(r, opp)) * k for opp, t in rounds)
    return r, (1.0 / math.sqrt(information)) if information > 0 else float("inf")


def calibrate_level(level: int, stockfish: str, pairs: int, rounds_count: int, movetime_ms: int, concurrency: int,
                    start: float) -> dict:
    ours = Spec(str(ENGINE), {"Level": str(level)}, f"level {level}")
    rounds: list[tuple[float, Tally]] = []
    opponent = min(max(start, SF_MIN), SF_MAX)
    for i in range(rounds_count):
        theirs = Spec(stockfish, {"UCI_LimitStrength": "true", "UCI_Elo": str(int(round(opponent))), "Threads": "1", "Hash": "16"},
                      f"stockfish {opponent:.0f}")
        tally = play_match(ours, theirs, pairs, chess.engine.Limit(time=movetime_ms / 1000.0),
                           concurrency=concurrency, seed=100 * level + i)
        rounds.append((opponent, tally))
        rating, error = estimate(rounds)
        print(f"  level {level} vs Stockfish {opponent:.0f}: +{tally.wins} ={tally.draws} -{tally.losses}"
              f"  -> estimate {rating:.0f} +/- {error:.0f}", flush=True)
        opponent = min(max(rating, SF_MIN), SF_MAX)   # the next round is played at the current best guess
    rating, error = estimate(rounds)
    return {"level": level, "elo": round(rating), "error": round(1.96 * error),
            "rounds": [{"opponent": opp, "wins": t.wins, "draws": t.draws, "losses": t.losses} for opp, t in rounds]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--stockfish", required=True, help="path to the Stockfish executable")
    parser.add_argument("--levels", type=int, nargs="*", default=[1, 2, 3, 4, 5, 6])
    parser.add_argument("--pairs", type=int, default=8, help="opening pairs per round (two games each)")
    parser.add_argument("--rounds", type=int, default=3, help="rounds per level")
    parser.add_argument("--movetime", type=int, default=300, help="Stockfish's time per move, in milliseconds")
    parser.add_argument("--concurrency", type=int, default=3)
    parser.add_argument("--out", type=Path, default=Path(__file__).parent / "runs" / "calibration.json")
    args = parser.parse_args()

    started = time.time()
    results = []
    for level in args.levels:
        print(f"level {level}", flush=True)
        results.append(calibrate_level(level, args.stockfish, args.pairs, args.rounds, args.movetime,
                                       args.concurrency, GUESS.get(level, 2000)))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(results, indent=2))

    print(f"\n| Level | Elo (Stockfish UCI_Elo scale, {args.movetime} ms/move) |\n|---|---|")
    for r in results:
        print(f"| {r['level']} | {r['elo']} +/- {r['error']} |")
    print(f"\n{time.time() - started:.0f} s, saved to {args.out}")


if __name__ == "__main__":
    main()
