"""Tests for the data pipeline. Run: .venv\\Scripts\\python -m unittest test_pipeline -v"""
import random
import unittest

import chess
import numpy as np

import extract
from records import RECORD, pack_pieces, position_hash, unpack_pieces


def piece_codes(board: chess.Board) -> np.ndarray:
    """The 64 square codes the engine's numbering gives this board (-1 = empty)."""
    out = np.full(64, -1, dtype=np.int8)
    for sq, piece in board.piece_map().items():
        out[sq] = (0 if piece.color == chess.WHITE else 6) + piece.piece_type - 1
    return out


def random_boards(count: int, seed: int = 7) -> list[chess.Board]:
    rng = random.Random(seed)
    boards = []
    while len(boards) < count:
        board = chess.Board()
        for _ in range(rng.randint(0, 120)):
            moves = list(board.legal_moves)
            if not moves:
                break
            board.push(rng.choice(moves))
        boards.append(board)
    return boards


class RecordsTest(unittest.TestCase):
    def test_a_position_survives_packing_and_unpacking(self):
        boards = random_boards(300)
        bitboards = np.array([extract._bitboards(b) for b in boards], dtype=np.uint64)

        occ, pieces = pack_pieces(bitboards)
        unpacked = unpack_pieces(occ, pieces)

        for board, got in zip(boards, unpacked):
            self.assertTrue(np.array_equal(got, piece_codes(board)), board.fen())

    def test_record_is_32_bytes(self):
        self.assertEqual(RECORD.itemsize, 32)

    def test_the_hash_ignores_labels_but_not_the_position_or_the_side_to_move(self):
        boards = random_boards(2)
        bitboards = np.array([extract._bitboards(b) for b in boards], dtype=np.uint64)
        occ, pieces = pack_pieces(bitboards)
        records = np.zeros(3, dtype=RECORD)
        records["occ"], records["pieces"] = occ[[0, 0, 1]], pieces[[0, 0, 1]]
        records["score"][1] = 500           # another label for the same position
        records["stm"][2] = 1

        h = position_hash(records)

        self.assertEqual(h[0], h[1])
        self.assertNotEqual(h[0], h[2])


class ExtractTest(unittest.TestCase):
    @staticmethod
    def unpack(blob: bytes) -> np.ndarray:
        return np.frombuffer(blob, dtype=RECORD)

    def play(self, movetext: str, result: int = 2) -> np.ndarray:
        return self.unpack(extract.process_chunk([(result, movetext)]))

    def evals(self, moves: list[str], scores: list[str]) -> str:
        """Movetext with a [%eval] comment after every move."""
        out, number = [], 1
        for i, (san, score) in enumerate(zip(moves, scores)):
            out.append(f"{number}. {san}" if i % 2 == 0 else f"{number}... {san}")
            out.append(f"{{ [%eval {score}] [%clk 0:05:00] }}")
            if i % 2 == 1:
                number += 1
        return " ".join(out) + " 1-0"

    OPENING = ["e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7", "Re1", "b5", "Bb3", "d6"]

    def test_early_positions_are_skipped(self):
        # 14 plies: the positions after ply 10, 11, 12 and 13 qualify; ply 14 has no next move
        records = self.play(self.evals(self.OPENING, ["0.2"] * 14))

        self.assertEqual([int(p) for p in records["ply"]], [10, 11, 12, 13])

    def test_scores_are_white_point_of_view_in_centipawns_and_mates_are_clipped(self):
        # the score after ply p is the p-th in the list, so these are the positions after plies 10 to 13
        scores = ["0.2"] * 9 + ["0.2", "-1.37", "2.5", "#4", "0.0"]
        records = self.play(self.evals(self.OPENING, scores))

        self.assertEqual([int(s) for s in records["score"]], [20, -137, 250, 3000])

    def test_a_mate_for_black_is_negative(self):
        scores = ["0.2"] * 10 + ["#-2", "0.0", "0.0", "0.0"]
        records = self.play(self.evals(self.OPENING, scores))

        self.assertEqual(int(records["score"][1]), -3000)  # the position after ply 11

    def test_scores_beyond_3000_are_dropped(self):
        scores = ["0.2"] * 10 + ["31.0", "0.0", "0.0", "0.0"]
        records = self.play(self.evals(self.OPENING, scores))

        self.assertEqual([int(p) for p in records["ply"]], [10, 12, 13])  # ply 11 scored 31 pawns

    def test_a_position_followed_by_a_capture_is_not_quiet(self):
        # Nxe4 is the 14th half-move and Nxe5 the 15th, so the positions before them (13 and 14) are not quiet
        moves = ["e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6", "O-O", "Be7", "Re1", "b5", "Bb3", "Nxe4", "Nxe5"]
        records = self.play(self.evals(moves, ["0.1"] * 15))

        self.assertEqual([int(p) for p in records["ply"]], [10, 11, 12])

    def test_the_side_to_move_and_the_result_are_recorded(self):
        records = self.play(self.evals(self.OPENING, ["0.2"] * 14), result=0)

        self.assertEqual([int(x) for x in records["stm"]], [0, 1, 0, 1])  # after ply 10 White is to move
        self.assertEqual(set(records["result"]), {0})

    def test_positions_in_check_are_skipped(self):
        # 1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7# - build a game where a check lands at ply >= 10
        moves = ["e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Bxc6", "dxc6", "Nxe5", "Qd4", "Nf3", "Qxe4+", "Be2", "Bd6"]
        records = self.play(self.evals(moves, ["0.2"] * 14))

        board = chess.Board()
        for san in moves[:12]:
            board.push_san(san)
        self.assertTrue(board.is_check())
        self.assertNotIn(12, list(records["ply"]))

    def test_an_unreadable_move_ends_the_game_without_crashing(self):
        records = self.play(self.evals(self.OPENING[:6], ["0.2"] * 6).replace("Bb5", "Zz9"))

        self.assertEqual(len(records), 0)

    def test_the_pieces_in_a_record_are_the_ones_on_the_board(self):
        records = self.play(self.evals(self.OPENING, ["0.2"] * 14))
        board = chess.Board()
        for san in self.OPENING[:10]:
            board.push_san(san)

        got = unpack_pieces(records["occ"][:1], records["pieces"][:1])[0]

        self.assertTrue(np.array_equal(got, piece_codes(board)))


if __name__ == "__main__":
    unittest.main()
