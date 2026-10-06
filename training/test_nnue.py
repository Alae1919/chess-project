"""Tests for the network, its quantisation and the integer reference. Run: .venv\\Scripts\\python -m unittest test_nnue -v"""
import tempfile
import unittest
from pathlib import Path

import chess
import numpy as np
import torch

import export
import extract
import golden
from nnue import FEATURES, SCALE, Net, decode
from records import RECORD, pack_pieces
from test_pipeline import random_boards


def records_for(boards: list[chess.Board], scores=None, results=None) -> torch.Tensor:
    bitboards = np.array([extract._bitboards(b) for b in boards], dtype=np.uint64)
    occ, pieces = pack_pieces(bitboards)
    records = np.zeros(len(boards), dtype=RECORD)
    records["occ"], records["pieces"] = occ, pieces
    records["stm"] = [0 if b.turn == chess.WHITE else 1 for b in boards]
    records["score"] = scores if scores is not None else 0
    records["result"] = results if results is not None else 1
    return torch.from_numpy(records.view(np.uint8).reshape(len(boards), 32).copy())


class DecodeTest(unittest.TestCase):
    def test_the_inputs_are_the_pieces_seen_from_each_side(self):
        boards = random_boards(200, seed=3)
        reference = golden.Network.__new__(golden.Network)  # only its active_features() method is used

        white, black, stm, _, _ = decode(records_for(boards))

        for i, board in enumerate(boards):
            for view, perspective in ((white, chess.WHITE), (black, chess.BLACK)):
                expected = sorted(reference.active_features(board, perspective))
                got = torch.nonzero(view[i]).squeeze(1).tolist()
                self.assertEqual(got, expected, f"{board.fen()} perspective {perspective}")
            self.assertEqual(int(stm[i]), 0 if board.turn == chess.WHITE else 1)

    def test_scores_and_results_are_from_the_side_to_move(self):
        white_to_move = chess.Board()
        black_to_move = chess.Board()
        black_to_move.push_san("e4")

        _, _, _, score, result = decode(records_for([white_to_move, black_to_move], scores=[120, 120], results=[2, 2]))

        self.assertEqual(score.tolist(), [120.0, -120.0])
        self.assertEqual(result.tolist(), [1.0, 0.0])  # White won: good for White to move, bad for Black

    def test_a_drawn_game_is_half_a_point_for_both(self):
        _, _, _, _, result = decode(records_for([chess.Board(), chess.Board()], results=[1, 1]))
        self.assertEqual(result.tolist(), [0.5, 0.5])


class SymmetryTest(unittest.TestCase):
    def test_the_same_position_with_colours_swapped_scores_the_same(self):
        torch.manual_seed(1)
        net = Net(16)
        boards = random_boards(100, seed=5)
        mirrored = [b.mirror() for b in boards]  # flips the board and swaps the colours and the side to move

        a = net(*decode(records_for(boards))[:3])
        b = net(*decode(records_for(mirrored))[:3])

        self.assertTrue(torch.allclose(a, b, atol=1e-5))


class QuantisationTest(unittest.TestCase):
    def setUp(self):
        torch.manual_seed(2)
        self.net = Net(32)
        with torch.no_grad():
            self.net.ft_weight.normal_(0, 0.3)
            self.net.ft_bias.normal_(0, 0.2)
            self.net.out.weight.normal_(0, 0.4)
            self.net.out.bias.fill_(0.1)
        self.net.clip()
        self.dir = tempfile.TemporaryDirectory()
        self.path = Path(self.dir.name) / "t.nnue"
        export.write(self.path, export.quantise(self.net.export_state()))

    def tearDown(self):
        self.dir.cleanup()

    def test_the_integer_score_follows_the_float_network(self):
        reference = golden.Network(self.path)
        boards = random_boards(150, seed=9)

        with torch.no_grad():
            floats = self.net(*decode(records_for(boards))[:3]) * SCALE
        ints = np.array([reference.evaluate(b) for b in boards])

        error = np.abs(floats.numpy() - ints)
        self.assertLess(error.mean(), 1.5, f"mean error {error.mean():.2f} cp")
        self.assertLess(error.max(), 6.0, f"max error {error.max():.2f} cp")
        self.assertGreater(np.abs(ints).max(), 20, "the test net should produce non-trivial scores")

    def test_a_damaged_file_is_rejected(self):
        data = bytearray(self.path.read_bytes())
        data[100] ^= 0xFF
        self.path.write_bytes(bytes(data))

        with self.assertRaises(ValueError):
            golden.Network(self.path)

    def test_the_file_has_the_documented_size(self):
        hidden = 32
        expected = 4 + 6 * 4 + 2 * FEATURES * hidden + 2 * hidden + 2 * 2 * hidden + 4 + 4
        self.assertEqual(self.path.stat().st_size, expected)


if __name__ == "__main__":
    unittest.main()
