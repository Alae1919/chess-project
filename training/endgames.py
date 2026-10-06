"""Can the engine win the textbook endgames? It plays one side against a random mover and must deliver mate.

    python endgames.py --eval nnue --net data\\v1.nnue
    python endgames.py --eval classical

A network trained on game positions has seen few of these, so this is where it would stumble: a flat
score gives the search nothing to steer by until the mate is inside its horizon.
"""
from __future__ import annotations

import argparse
import random
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import chess
import chess.engine

ENGINE = Path(__file__).resolve().parent.parent / "tools" / "engine" / "rexchess-uci.cmd"

# the strong side's pieces against a bare king
ENDGAMES = {
    "KQ vs K": [chess.QUEEN],
    "KR vs K": [chess.ROOK],
    "KRR vs K": [chess.ROOK, chess.ROOK],
    "KBB vs K": [chess.BISHOP, chess.BISHOP],
    "KBN vs K": [chess.BISHOP, chess.KNIGHT],
    "KP vs K": [chess.PAWN],
}


def random_position(extra: list[int], rng: random.Random) -> chess.Board:
    while True:
        board = chess.Board(None)
        squares = rng.sample(range(64), 2 + len(extra) + 1)
        board.set_piece_at(squares[0], chess.Piece(chess.KING, chess.WHITE))
        board.set_piece_at(squares[1], chess.Piece(chess.KING, chess.BLACK))
        for piece_type, sq in zip(extra, squares[2:]):
            if piece_type == chess.PAWN and chess.square_rank(sq) in (0, 7):
                break
            board.set_piece_at(sq, chess.Piece(piece_type, chess.WHITE))
        else:
            board.turn = chess.WHITE
            if board.is_valid() and not board.is_game_over() and not board.is_check():
                return board


def play(extra: list[int], seed: int, eval_mode: str, net: str | None, movetime: float, max_moves: int) -> tuple[bool, int]:
    rng = random.Random(seed)
    board = random_position(extra, rng)
    engine = chess.engine.SimpleEngine.popen_uci([str(ENGINE)])
    try:
        options = {"Eval": eval_mode}
        if net and eval_mode == "nnue":
            options["EvalFile"] = str(Path(net).resolve())
        engine.configure(options)
        for _ in range(max_moves):
            if board.is_game_over(claim_draw=True):
                break
            if board.turn == chess.WHITE:
                board.push(engine.play(board, chess.engine.Limit(time=movetime)).move)
            else:
                board.push(rng.choice(list(board.legal_moves)))
        return board.is_checkmate() and board.turn == chess.BLACK, board.fullmove_number
    finally:
        engine.quit()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--eval", choices=["nnue", "classical"], default="nnue")
    parser.add_argument("--net", help="a network file (default: the bundled one)")
    parser.add_argument("--games", type=int, default=20, help="starting positions per endgame")
    parser.add_argument("--movetime", type=float, default=0.1)
    parser.add_argument("--max-moves", type=int, default=100, help="the engine's moves before giving up")
    parser.add_argument("--concurrency", type=int, default=4)
    args = parser.parse_args()

    print(f"{'endgame':<10} {'mated':>6} {'average move of the mate':>26}")
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        for name, extra in ENDGAMES.items():
            results = list(pool.map(lambda s: play(extra, s, args.eval, args.net, args.movetime, 2 * args.max_moves),
                                    range(args.games)))
            mates = [moves for ok, moves in results if ok]
            average = f"{sum(mates) / len(mates):.1f}" if mates else "-"
            print(f"{name:<10} {len(mates):>3}/{args.games:<2} {average:>26}", flush=True)


if __name__ == "__main__":
    main()
