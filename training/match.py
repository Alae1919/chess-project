"""Plays two UCI engines against each other and reports who is stronger.

Games come in pairs: each opening is played twice with the colours swapped, which removes
most of the luck of the opening. Each move gets a fixed time (or node count), the same for
both sides. Games are adjudicated when one side is hopelessly ahead for several moves, and
drawn on repetition, the fifty-move rule, insufficient material, or after a long game.

    .venv\\Scripts\\python match.py \\
        --a ..\\tools\\engine\\rexchess-uci.cmd --a-opt Level=6 \\
        --b ..\\tools\\engine\\rexchess-uci.cmd --b-opt Level=1 \\
        --pairs 20 --movetime 100

Results are from engine A's point of view. The Elo difference comes with a 95% error bar. With --sprt
(for example --sprt 0 5, "is A at least 5 Elo better?") the run stops as soon as the evidence is decisive.
"""
from __future__ import annotations

import argparse
import math
import random
import shlex
import sys
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

import chess
import chess.engine

# A small set of sound, varied openings (UCI move lists): enough variety for a few hundred games.
OPENINGS = [
    "e2e4 e7e5 g1f3 b8c6 f1b5", "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6", "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3",
    "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e5", "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6", "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4",
    "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5", "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5", "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3",
    "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4", "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4", "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6",
    "c2c4 e7e5 b1c3 g8f6 g1f3 b8c6", "c2c4 g8f6 b1c3 e7e6 e2e4", "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6", "g1f3 g8f6 c2c4 b7b6 g2g3 c8b7",
    "e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4", "e2e4 e7e5 b1c3 g8f6 f1c4 f6e4", "d2d4 f7f5 g1f3 g8f6 g2g3 e7e6",
    "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6", "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6", "d2d4 e7e6 c2c4 f8b4 c1d2 d8e7",
    "e2e4 c7c5 c2c3 d7d5 e4d5 d8d5 d2d4 g8f6", "e2e4 e7e5 f1c4 g8f6 d2d3 f8c5", "d2d4 d7d5 g1f3 g8f6 c1f4 e7e6",
    "e2e4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7", "d2d4 g8f6 g1f3 e7e6 c1g5 h7h6", "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 g8f6",
    "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6", "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3 e7e6",
]


@dataclass
class Spec:
    command: str
    options: dict[str, str] = field(default_factory=dict)
    label: str = ""


@dataclass
class Tally:
    wins: int = 0
    draws: int = 0
    losses: int = 0

    @property
    def games(self) -> int:
        return self.wins + self.draws + self.losses

    @property
    def score(self) -> float:
        return (self.wins + 0.5 * self.draws) / self.games if self.games else 0.5


def elo_from_score(score: float) -> float:
    score = min(max(score, 1e-6), 1 - 1e-6)
    return -400.0 * math.log10(1.0 / score - 1.0)


def elo_report(t: Tally) -> tuple[float, float]:
    """Elo difference and its 95% half-width, from the win/draw/loss counts."""
    n = t.games
    if n == 0:
        return 0.0, float("inf")
    s = t.score
    var = (t.wins * (1 - s) ** 2 + t.draws * (0.5 - s) ** 2 + t.losses * (0 - s) ** 2) / n
    se = math.sqrt(var / n) if n > 1 else 1.0
    lo, hi = elo_from_score(s - 1.96 * se), elo_from_score(s + 1.96 * se)
    return elo_from_score(s), (hi - lo) / 2


def likelihood_of_superiority(t: Tally) -> float:
    n = t.wins + t.losses
    if n == 0:
        return 0.5
    return 0.5 * (1 + math.erf((t.wins - t.losses) / math.sqrt(2 * n)))


def sprt_llr(t: Tally, elo0: float, elo1: float) -> float:
    """Approximate log-likelihood ratio for H1 (Elo >= elo1) against H0 (Elo <= elo0)."""
    n = t.games
    if n < 2:
        return 0.0
    s = t.score
    var = (t.wins * (1 - s) ** 2 + t.draws * (0.5 - s) ** 2 + t.losses * s ** 2) / n
    if var <= 0:
        return 0.0
    s0 = 1 / (1 + 10 ** (-elo0 / 400))
    s1 = 1 / (1 + 10 ** (-elo1 / 400))
    return (s1 - s0) * (2 * s - s0 - s1) * n / (2 * var)


def open_engine(spec: Spec) -> chess.engine.SimpleEngine:
    # a relative path to a .cmd or .sh wrapper only resolves from the right folder, so make it absolute
    parts = shlex.split(spec.command, posix=False)
    if Path(parts[0]).exists():
        parts[0] = str(Path(parts[0]).resolve())
    engine = chess.engine.SimpleEngine.popen_uci(parts)
    configure = {k: v for k, v in spec.options.items() if k in engine.options}
    for name in spec.options:
        if name not in engine.options:
            print(f"warning: {spec.label or command} has no option {name}", file=sys.stderr)
    if configure:
        engine.configure({k: (int(v) if v.lstrip("-").isdigit() else (v == "true") if v in ("true", "false") else v)
                          for k, v in configure.items()})
    return engine


def play_game(white: Spec, black: Spec, opening: str, limit: chess.engine.Limit, max_plies: int) -> float:
    """Returns White's score: 1, 0.5 or 0."""
    board = chess.Board()
    for uci in opening.split():
        board.push_uci(uci)
    engines = {chess.WHITE: open_engine(white), chess.BLACK: open_engine(black)}
    hopeless = 0
    try:
        while not board.is_game_over(claim_draw=True) and board.ply() < max_plies:
            engine = engines[board.turn]
            result = engine.play(board, limit, info=chess.engine.INFO_SCORE)
            if result.move is None:
                break
            score = result.info.get("score") if result.info else None
            if score is not None:
                cp = score.pov(board.turn).score(mate_score=10_000)
                hopeless = hopeless + 1 if cp is not None and abs(cp) >= 1000 else 0
                if hopeless >= 6 and board.ply() > 20:
                    winner_is_mover = cp > 0
                    return 1.0 if (board.turn == chess.WHITE) == winner_is_mover else 0.0
            board.push(result.move)
        outcome = board.outcome(claim_draw=True)
        if outcome is None or outcome.winner is None:
            return 0.5
        return 1.0 if outcome.winner == chess.WHITE else 0.0
    finally:
        for engine in engines.values():
            try:
                engine.quit()
            except Exception:
                pass


def play_match(a: Spec, b: Spec, pairs: int, limit: chess.engine.Limit, concurrency: int = 2, max_plies: int = 240,
               seed: int = 1, on_game=None, should_stop=None) -> Tally:
    """Plays `pairs` opening pairs of A against B. `on_game(tally)` is called after every game, and the
    match ends early once `should_stop(tally)` is true (an SPRT that has reached its answer, say)."""
    rng = random.Random(seed)
    order = OPENINGS[:]
    rng.shuffle(order)
    openings = [order[i % len(order)] for i in range(pairs)]
    tally = Tally()
    lock = threading.Lock()
    done = threading.Event()

    def one_pair(index: int) -> None:
        for a_is_white in (True, False):
            if done.is_set():
                return
            white, black = (a, b) if a_is_white else (b, a)
            white_score = play_game(white, black, openings[index], limit, max_plies)
            a_score = white_score if a_is_white else 1.0 - white_score
            with lock:
                if a_score == 1.0:
                    tally.wins += 1
                elif a_score == 0.0:
                    tally.losses += 1
                else:
                    tally.draws += 1
                if on_game:
                    on_game(tally)
                if should_stop and should_stop(tally):
                    done.set()

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(one_pair, range(pairs)))
    return tally


def run(args: argparse.Namespace) -> Tally:
    a = Spec(args.a, dict(o.split("=", 1) for o in args.a_opt), "A")
    b = Spec(args.b, dict(o.split("=", 1) for o in args.b_opt), "B")
    limit = chess.engine.Limit(time=args.movetime / 1000.0) if not args.nodes else chess.engine.Limit(nodes=args.nodes)

    def report(t: Tally) -> None:
        elo, err = elo_report(t)
        line = f"games {t.games}: +{t.wins} ={t.draws} -{t.losses}  score {t.score:.3f}  Elo {elo:+.0f} +/- {err:.0f}"
        if args.sprt:
            line += f"  LLR {sprt_llr(t, *args.sprt):+.2f}"
        print(line, flush=True)

    # with an SPRT, stop as soon as it decides (5% error either way)
    bound = math.log(0.95 / 0.05)
    stop = (lambda t: abs(sprt_llr(t, *args.sprt)) >= bound) if args.sprt else None
    return play_match(a, b, args.pairs, limit, args.concurrency, args.max_plies, args.seed, report, stop)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--a", required=True, help="command that starts engine A (a UCI engine)")
    p.add_argument("--b", required=True, help="command that starts engine B")
    p.add_argument("--a-opt", action="append", default=[], metavar="NAME=VALUE", help="a UCI option for A (repeatable)")
    p.add_argument("--b-opt", action="append", default=[], metavar="NAME=VALUE", help="a UCI option for B (repeatable)")
    p.add_argument("--pairs", type=int, default=10, help="opening pairs (each is two games)")
    p.add_argument("--movetime", type=int, default=100, help="milliseconds per move")
    p.add_argument("--nodes", type=int, default=0, help="nodes per move instead of time")
    p.add_argument("--concurrency", type=int, default=2, help="games at once")
    p.add_argument("--max-plies", type=int, default=240)
    p.add_argument("--seed", type=int, default=1)
    p.add_argument("--sprt", type=float, nargs=2, metavar=("ELO0", "ELO1"),
                   help="test whether A is better than B by ELO1 rather than ELO0, and stop once that is decided")
    args = p.parse_args()
    t = run(args)
    elo, err = elo_report(t)
    print(f"\nFinal: {t.games} games, +{t.wins} ={t.draws} -{t.losses}, score {t.score:.3f}")
    print(f"Elo difference (A - B): {elo:+.0f} +/- {err:.0f}   likelihood of superiority {likelihood_of_superiority(t):.1%}")


if __name__ == "__main__":
    main()
