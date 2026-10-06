"""Turns a Lichess monthly dump into training positions: quiet positions with a Stockfish score.

    python extract.py data/raw/lichess_db_standard_rated_2016-01.pgn.zst

The dump is decompressed as a stream (it is never written out unpacked) and only the games
that carry Lichess's computer analysis (`[%eval ...]` comments) are looked at; that is a small
share of all games. Each move of such a game has the engine's score for the position after it.

A position is kept when
  * at least 10 half-moves have been played,
  * the side to move is not in check,
  * the next move is not a capture or a promotion (so the score is of a quiet position, which
    a static evaluation can be expected to get right),
  * the score is within +-3000 centipawns (forced mates count as +-3000).
Games that were abandoned, started from a set-up position, or were played by weak players
(--min-elo) are skipped. Output: data/positions/<month>.bin, 32 bytes per position (see records.py).
"""
from __future__ import annotations

import argparse
import io
import multiprocessing as mp
import re
import sys
import threading
import time
from pathlib import Path

import chess
import numpy as np
import zstandard

from records import MATE_CP, RECORD, pack_pieces

OUT = Path(__file__).parent / "data" / "positions"
RESULT_CODE = {b"1-0": 2, b"1/2-1/2": 1, b"0-1": 0}
BAD_TERMINATIONS = (b"Abandoned", b"Rules infraction", b"Unterminated")
TOKEN = re.compile(r"\{([^}]*)\}|\d+\.(?:\.\.)?|\$\d+|(1-0|0-1|1/2-1/2|\*)|([^\s{}]+)")
EVAL = re.compile(r"\[%eval (#?)(-?\d+(?:\.\d+)?)\]")
MIN_PLY = 10


def read_games(path: Path, min_elo: int):
    """Yields (result code, movetext) for every analysed game worth using; also counts all games."""
    stats = {"games": 0, "analysed": 0, "kept": 0}
    with open(path, "rb") as raw:
        stream = io.BufferedReader(zstandard.ZstdDecompressor(max_window_size=2 ** 31).stream_reader(raw), 1 << 22)
        result = termination = None
        white_elo = black_elo = 0
        has_fen = False
        for line in stream:
            if line.startswith(b"["):
                if line.startswith(b"[Event "):
                    stats["games"] += 1
                    result = termination = None
                    white_elo = black_elo = 0
                    has_fen = False
                elif line.startswith(b"[Result "):
                    result = RESULT_CODE.get(line[9:-3])
                elif line.startswith(b"[Termination "):
                    termination = line[14:-3]
                elif line.startswith(b"[WhiteElo "):
                    white_elo = _number(line[11:-3])
                elif line.startswith(b"[BlackElo "):
                    black_elo = _number(line[11:-3])
                elif line.startswith(b"[FEN "):
                    has_fen = True
            elif line.startswith(b"1.") and b"[%eval" in line:
                stats["analysed"] += 1
                if (result is not None and not has_fen and termination not in BAD_TERMINATIONS
                        and min(white_elo, black_elo) >= min_elo):
                    stats["kept"] += 1
                    yield result, line.decode("utf-8", "replace"), stats
    yield None, None, stats


def _number(text: bytes) -> int:
    try:
        return int(text)
    except ValueError:
        return 0


def process_chunk(games: list[tuple[int, str]]) -> bytes:
    """Replays each game, picks its quiet positions, and returns them as packed records."""
    boards: list[tuple[int, ...]] = []
    meta: list[tuple[int, int, int, int]] = []  # stm, result, score, ply
    for result, movetext in games:
        board = chess.Board()
        pending = None
        for m in TOKEN.finditer(movetext):
            comment, _, san = m.group(1), m.group(2), m.group(3)
            if comment is not None:
                found = EVAL.search(comment)
                if found:
                    value = found.group(2)
                    if found.group(1):  # "#3" is a mate for White, "#-3" a mate for Black
                        score = MATE_CP if int(value) > 0 else -MATE_CP
                    else:
                        score = int(round(float(value) * 100))
                    pending = score if (abs(score) <= MATE_CP and board.ply() >= MIN_PLY and not board.is_check()) else None
                    if pending is not None:
                        snapshot = (_bitboards(board), 1 if board.turn == chess.BLACK else 0, result, pending, board.ply())
                    continue
            elif san is not None:
                san = san.rstrip("?!")
                try:
                    move = board.parse_san(san)
                except ValueError:
                    break  # a move we cannot read: drop the rest of this game
                if pending is not None:
                    if not board.is_capture(move) and move.promotion is None:
                        boards.append(snapshot[0])
                        meta.append(snapshot[1:])
                    pending = None
                board.push(move)
    return _records(boards, meta)


def _bitboards(board: chess.Board) -> tuple[int, ...]:
    white, black = board.occupied_co[chess.WHITE], board.occupied_co[chess.BLACK]
    types = (board.pawns, board.knights, board.bishops, board.rooks, board.queens, board.kings)
    return tuple(t & white for t in types) + tuple(t & black for t in types)


def _records(boards: list, meta: list) -> bytes:
    if not boards:
        return b""
    occ, pieces = pack_pieces(np.array(boards, dtype=np.uint64))
    records = np.zeros(len(boards), dtype=RECORD)
    stm, result, score, ply = (np.array(c) for c in zip(*meta))
    records["occ"] = occ
    records["pieces"] = pieces
    records["stm"] = stm
    records["result"] = result
    records["score"] = score
    records["ply"] = np.minimum(ply, 255)
    return records.tobytes()


def chunked(games, size: int):
    chunk = []
    for result, movetext, stats in games:
        if result is None:
            if chunk:
                yield chunk, stats
            return
        chunk.append((result, movetext))
        if len(chunk) >= size:
            yield chunk, stats
            chunk = []


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump", type=Path, help="a .pgn.zst monthly dump")
    parser.add_argument("--min-elo", type=int, default=1400, help="skip games where either player is rated lower")
    parser.add_argument("--max-games", type=int, default=0, help="stop after this many usable games (0 = all)")
    parser.add_argument("--workers", type=int, default=max(1, (mp.cpu_count() or 2) - 2))
    parser.add_argument("--chunk", type=int, default=200, help="games per task")
    args = parser.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    month = re.search(r"(\d{4}-\d{2})", args.dump.name)
    target = OUT / f"{month.group(1) if month else args.dump.stem}.bin"
    started, positions, last = time.time(), 0, 0.0

    games = read_games(args.dump, args.min_elo)
    if args.max_games:
        games = _limit(games, args.max_games)
    stats = {"games": 0, "analysed": 0, "kept": 0}
    in_flight = threading.Semaphore(args.workers * 8)  # the reader must not run far ahead of the workers
    with open(target, "wb") as out, mp.Pool(args.workers) as pool:
        def tasks():
            nonlocal stats
            for chunk, s in chunked(games, args.chunk):
                stats = s
                in_flight.acquire()
                yield chunk

        for blob in pool.imap(process_chunk, tasks(), chunksize=1):
            in_flight.release()
            out.write(blob)
            positions += len(blob) // RECORD.itemsize
            if time.time() - last > 10:
                last = time.time()
                print(f"\r{stats['games']:>10,} games read, {stats['analysed']:>8,} analysed, "
                      f"{positions:>11,} positions  ({time.time() - started:.0f} s)", end="", flush=True)
    print(f"\n{stats['games']:,} games, {stats['analysed']:,} analysed, {stats['kept']:,} used "
          f"-> {positions:,} positions in {target} ({time.time() - started:.0f} s)")


def _limit(games, max_games: int):
    count = 0
    for item in games:
        yield item
        count += 1
        if item[0] is not None and count >= max_games:
            yield None, None, item[2]
            return


if __name__ == "__main__":
    main()
