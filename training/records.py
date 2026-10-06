"""The on-disk format for training positions, shared by extract, shuffle and the trainer.

One position is 32 bytes, all little-endian:

    occupancy  u64     bit s is set when square s (a1 = 0, b1 = 1 ... h8 = 63) holds a piece
    pieces     16 B    the pieces of the occupied squares, lowest square first, one 4-bit code each
                       (low nibble first); code = color * 6 + type, with the engine's numbering:
                       color 0 = white, 1 = black; type 0..5 = pawn knight bishop rook queen king
    stm        u8      side to move: 0 = white, 1 = black
    result     u8      the game's result for White: 0 = loss, 1 = draw, 2 = win
    score      i16     the Stockfish evaluation in centipawns from White's point of view
    ply        u8      half-moves played so far
    padding    3 B

Castling rights and en passant are not stored: the network does not see them.
"""
from __future__ import annotations

import numpy as np

RECORD = np.dtype([
    ("occ", "<u8"),
    ("pieces", "u1", (16,)),
    ("stm", "u1"),
    ("result", "u1"),
    ("score", "<i2"),
    ("ply", "u1"),
    ("pad", "u1", (3,)),
])
assert RECORD.itemsize == 32

MATE_CP = 3000


def pack_pieces(bitboards: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """From N x 12 piece bitboards (index color * 6 + type) to the occupancy and the packed pieces."""
    n = bitboards.shape[0]
    squares = np.arange(64, dtype=np.uint64)
    code_per_square = np.full((n, 64), -1, dtype=np.int8)
    for code in range(12):
        bits = ((bitboards[:, code, None] >> squares) & np.uint64(1)).astype(bool)
        code_per_square[bits] = code
    occupied = code_per_square >= 0
    occ = np.zeros(n, dtype=np.uint64)
    for sq in range(64):
        occ |= occupied[:, sq].astype(np.uint64) << np.uint64(sq)
    rank = np.cumsum(occupied, axis=1) - 1  # position among the occupied squares
    rows = np.broadcast_to(np.arange(n)[:, None], (n, 64))
    nibbles = np.zeros((n, 32), dtype=np.uint8)
    nibbles[rows[occupied], rank[occupied]] = code_per_square[occupied].astype(np.uint8)
    pieces = nibbles[:, 0::2] | (nibbles[:, 1::2] << 4)
    return occ, pieces


def unpack_pieces(occ: np.ndarray, pieces: np.ndarray) -> np.ndarray:
    """The inverse of pack_pieces, for tests: N x 64 array of piece codes, -1 for an empty square."""
    n = occ.shape[0]
    nibbles = np.empty((n, 32), dtype=np.uint8)
    nibbles[:, 0::2] = pieces & 0x0F
    nibbles[:, 1::2] = pieces >> 4
    squares = np.arange(64, dtype=np.uint64)
    occupied = ((occ[:, None] >> squares) & np.uint64(1)).astype(bool)
    rank = np.cumsum(occupied, axis=1) - 1
    rows = np.broadcast_to(np.arange(n)[:, None], (n, 64))
    out = np.full((n, 64), -1, dtype=np.int8)
    out[occupied] = nibbles[rows[occupied], rank[occupied]].astype(np.int8)
    return out


def position_hash(records: np.ndarray) -> np.ndarray:
    """A 64-bit hash of what the network sees (pieces and side to move), to find duplicates."""
    words = records["pieces"].view("<u8").reshape(len(records), 2)
    h = records["occ"] * np.uint64(0x9E3779B97F4A7C15)
    h ^= (words[:, 0] + np.uint64(0x7F4A7C15)) * np.uint64(0xC2B2AE3D27D4EB4F)
    h ^= (words[:, 1] + np.uint64(0x165667B1)) * np.uint64(0x85EBCA77C2B2AE63)
    h ^= records["stm"].astype(np.uint64) * np.uint64(0xD6E8FEB86659FD93)
    h ^= h >> np.uint64(29)
    return h
