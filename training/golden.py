"""The exact integer arithmetic of the quantised network, written independently of the trainer.

The engine (Java) must give the same score as this, bit for bit; golden.csv holds positions and
the scores computed here, and the Java tests check them. Also usable on its own:

    python golden.py first.nnue --out golden.csv --val data/val.bin
    python golden.py first.nnue --fen "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

The forward pass, for a position with side to move `stm`:

    acc[p]   = ft_bias + sum of ft_weights[feature] for the active features of perspective p   (16-bit)
    hidden   = acc[stm] followed by acc[other]                                                  (2 x hidden)
    c        = clamp(hidden, 0, QA)
    total    = sum(c * c * out_weight) + out_bias * QA                                          (64-bit)
    score    = floor((total * scale + D / 2) / D)   with D = QA * QA * QB                       (centipawns, stm)
"""
from __future__ import annotations

import argparse
import csv
import random
import struct
import zlib
from pathlib import Path

import chess
import numpy as np

MAGIC = b"RXNN"


class Network:
    def __init__(self, path: Path):
        data = path.read_bytes()
        if data[:4] != MAGIC:
            raise ValueError("not an RXNN file")
        if zlib.crc32(data[:-4]) != struct.unpack("<I", data[-4:])[0]:
            raise ValueError("checksum mismatch")
        self.version, self.features, self.hidden, self.qa, self.qb, self.scale = struct.unpack("<6I", data[4:28])
        pos = 28
        n = self.features * self.hidden
        self.ft_w = np.frombuffer(data, "<i2", n, pos).astype(np.int64).reshape(self.features, self.hidden)
        pos += 2 * n
        self.ft_b = np.frombuffer(data, "<i2", self.hidden, pos).astype(np.int64)
        pos += 2 * self.hidden
        self.out_w = np.frombuffer(data, "<i2", 2 * self.hidden, pos).astype(np.int64)
        pos += 4 * self.hidden
        (self.out_b,) = struct.unpack("<i", data[pos:pos + 4])

    def active_features(self, board: chess.Board, perspective: chess.Color) -> list[int]:
        """The active inputs for one view of the board."""
        active = []
        for sq, piece in board.piece_map().items():
            relative = sq if perspective == chess.WHITE else sq ^ 56
            active.append((0 if piece.color == perspective else 384) + (piece.piece_type - 1) * 64 + relative)
        return active

    def accumulator(self, board: chess.Board, perspective: chess.Color) -> np.ndarray:
        acc = self.ft_b.copy()
        for f in self.active_features(board, perspective):
            acc += self.ft_w[f]
        return acc

    def evaluate(self, board: chess.Board) -> int:
        """Centipawns from the side to move's point of view."""
        mine = self.accumulator(board, board.turn)
        theirs = self.accumulator(board, not board.turn)
        hidden = np.concatenate([mine, theirs])
        if np.abs(hidden).max() > 32767:
            raise OverflowError("a hidden sum left the 16-bit range")
        c = np.clip(hidden, 0, self.qa)
        total = int((c * c * self.out_w).sum()) + self.out_b * self.qa
        d = self.qa * self.qa * self.qb
        return (total * self.scale + d // 2) // d   # floor division, as Math.floorDiv in the engine


def board_from_record(occ: int, pieces: bytes, stm: int) -> chess.Board:
    board = chess.Board(None)
    codes = []
    for byte in pieces:
        codes += [byte & 15, byte >> 4]
    i = 0
    for sq in range(64):
        if occ >> sq & 1:
            code = codes[i]
            i += 1
            board.set_piece_at(sq, chess.Piece(code % 6 + 1, chess.WHITE if code < 6 else chess.BLACK))
    board.turn = chess.WHITE if stm == 0 else chess.BLACK
    return board


def random_positions(count: int, seed: int) -> list[chess.Board]:
    """Positions from random play: odd ones, with big material swings, to exercise the extremes."""
    rng = random.Random(seed)
    out = []
    while len(out) < count:
        board = chess.Board()
        for _ in range(rng.randint(8, 90)):
            moves = list(board.legal_moves)
            if not moves:
                break
            board.push(rng.choice(moves))
        if not board.is_game_over():
            out.append(board)
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("net", type=Path)
    parser.add_argument("--out", type=Path, help="write fen,score rows here")
    parser.add_argument("--val", type=Path, help="validation positions to sample from")
    parser.add_argument("--count", type=int, default=500, help="positions from each source")
    parser.add_argument("--fen", help="evaluate this one position and print the score")
    args = parser.parse_args()

    net = Network(args.net)
    if args.fen:
        board = chess.Board(args.fen)
        print(net.evaluate(board))
        return

    boards = random_positions(args.count, seed=2024)
    if args.val:
        from records import RECORD
        records = np.fromfile(args.val, dtype=RECORD)[:args.count]
        boards += [board_from_record(int(r["occ"]), bytes(r["pieces"]), int(r["stm"])) for r in records]
    if args.out:
        with open(args.out, "w", newline="") as f:
            writer = csv.writer(f)
            for board in boards:
                writer.writerow([board.fen(), net.evaluate(board)])
        print(f"wrote {len(boards)} positions to {args.out}")
    else:
        for board in boards[:5]:
            print(board.fen(), net.evaluate(board))


if __name__ == "__main__":
    main()
