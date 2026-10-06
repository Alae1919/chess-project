"""Builds an opening book from the games of strong players in a Lichess dump.

    python make_book.py data/raw/lichess_db_standard_rated_2016-01.pgn.zst --out ../chess-engine/src/main/resources/openings/book.bin

For every game where both players are rated at least --min-elo, the first --plies half-moves are replayed and each
(position, move played) pair is counted. A move goes in the book when it was played at least --min-count times in that
position and by at least --min-share of the games that reached it; its weight is how often it was played, so the AI
chooses among the book moves in proportion to what strong players actually did.

The file is in the Polyglot format (16 bytes per entry, big-endian, sorted by position key), which the engine reads
with PolyglotBook and any chess GUI can read too. The key is the same Zobrist hash the engine computes.
"""
from __future__ import annotations

import argparse
import io
import re
import struct
import time
from collections import Counter, defaultdict
from pathlib import Path

import chess
import chess.polyglot
import zstandard

SAN_TOKEN = re.compile(r"\{[^}]*\}|\d+\.(?:\.\.)?|\$\d+|(?:1-0|0-1|1/2-1/2|\*)|([^\s{}]+)")


def polyglot_move(board: chess.Board, move: chess.Move) -> int:
    """The 16-bit Polyglot encoding: to file, to row, from file, from row, promotion; castling is king takes rook."""
    to = move.to_square
    if board.is_castling(move):
        to = chess.square(7 if board.is_kingside_castling(move) else 0, chess.square_rank(move.from_square))
    promotion = (move.promotion - 1) if move.promotion else 0   # knight 1 .. queen 4
    return (chess.square_file(to) | chess.square_rank(to) << 3
            | chess.square_file(move.from_square) << 6 | chess.square_rank(move.from_square) << 9
            | promotion << 12)


def games(path: Path, min_elo: int):
    """Yields the movetext of every game both of whose players are rated at least min_elo."""
    with open(path, "rb") as raw:
        stream = io.BufferedReader(zstandard.ZstdDecompressor(max_window_size=2 ** 31).stream_reader(raw), 1 << 22)
        white = black = 0
        usable = True
        for line in stream:
            if line.startswith(b"[Event "):
                white = black = 0
                usable = True
            elif line.startswith(b"[WhiteElo "):
                white = _number(line[11:-3])
            elif line.startswith(b"[BlackElo "):
                black = _number(line[11:-3])
            elif line.startswith((b"[FEN ", b"[Variant ")) and not line.startswith(b'[Variant "Standard"'):
                usable = False
            elif line.startswith(b"[Termination ") and line[14:-3] in (b"Abandoned", b"Rules infraction", b"Unterminated"):
                usable = False
            elif line.startswith(b"1.") and usable and white >= min_elo and black >= min_elo:
                yield line.decode("utf-8", "replace")


def _number(text: bytes) -> int:
    try:
        return int(text)
    except ValueError:
        return 0


def count_moves(path: Path, min_elo: int, plies: int, max_games: int) -> tuple[dict, int]:
    counts: dict[int, Counter] = defaultdict(Counter)      # position key -> Counter of encoded moves
    used = 0
    started = time.time()
    for movetext in games(path, min_elo):
        board = chess.Board()
        played = 0
        for token in SAN_TOKEN.finditer(movetext):
            san = token.group(1)
            if san is None:
                continue
            try:
                move = board.parse_san(san.rstrip("?!"))
            except ValueError:
                break
            counts[chess.polyglot.zobrist_hash(board)][polyglot_move(board, move)] += 1
            board.push(move)
            played += 1
            if played >= plies:
                break
        used += 1
        if used % 20000 == 0:
            print(f"\r{used:,} games, {len(counts):,} positions ({time.time() - started:.0f} s)", end="", flush=True)
        if max_games and used >= max_games:
            break
    print()
    return counts, used


def select(counts: dict, min_count: int, min_share: float) -> list[tuple[int, int, int]]:
    entries = []
    for key, moves in counts.items():
        total = sum(moves.values())
        for move, n in moves.items():
            if n >= min_count and n / total >= min_share:
                entries.append((key, move, min(n, 65535)))
    entries.sort(key=lambda e: (e[0], -e[2]))
    return entries


def write(path: Path, entries: list[tuple[int, int, int]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        for key, move, weight in entries:
            f.write(struct.pack(">QHHI", key, move, weight, 0))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--min-elo", type=int, default=2000)
    parser.add_argument("--plies", type=int, default=16, help="half-moves of each game to learn from")
    parser.add_argument("--min-count", type=int, default=30, help="games a move must have been played in")
    parser.add_argument("--min-share", type=float, default=0.05, help="share of the games reaching the position")
    parser.add_argument("--max-games", type=int, default=0)
    args = parser.parse_args()

    counts, used = count_moves(args.dump, args.min_elo, args.plies, args.max_games)
    entries = select(counts, args.min_count, args.min_share)
    write(args.out, entries)
    positions = len({e[0] for e in entries})
    print(f"{used:,} games rated {args.min_elo}+ -> {len(entries):,} moves in {positions:,} positions, "
          f"{args.out.stat().st_size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
