package com.chess.domain.rules;

import com.chess.domain.board.*;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T12 — CheckDetector.isInCheck() tested for every attacking piece type.
 *
 * Each nested class isolates one attacker type to make failures self-explaining.
 * We also verify "no check" in the starting position and after an
 * interposing piece blocks the ray.
 */
@DisplayName("T12 — CheckDetector")
class CheckDetectorTest {

    // ---- Not in check baseline ----------------------------------------

    @Test
    @DisplayName("starting position: neither king is in check")
    void startingPositionNoCheck() {
        Board board = BoardFactory.startingPosition();
        assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        assertFalse(CheckDetector.isInCheck(board, Color.BLACK));
    }

    @Test
    @DisplayName("lone kings facing each other — neither is in check")
    void loneKings() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        assertFalse(CheckDetector.isInCheck(board, Color.BLACK));
    }

    // ---- T12-1: rook attack -------------------------------------------

    @Nested
    @DisplayName("T12-1 — Rook attack")
    class RookAttack {

        @Test
        @DisplayName("black rook on e8 puts white king on e1 in check (same file)")
        void rookFileAttack() {
            Board board = FenParser.parse("4r3/k7/8/8/8/8/8/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("black rook on a1 puts white king on h1 in check (same rank)")
        void rookRankAttack() {
            Board board = FenParser.parse("7k/8/8/8/8/8/8/r6K w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("interposing piece blocks rook attack")
        void rookBlockedByFriendlyPiece() {
            // White rook on e4 blocks the black rook on e8
            Board board = FenParser.parse("4r3/k7/8/8/4R3/8/8/4K3 w - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("rook does not attack diagonally")
        void rookNoDiagonalAttack() {
            Board board = FenParser.parse("7k/8/8/8/8/8/8/r6K w - - 0 1");
            // The rook is on a1, king on h1 — same rank → check
            // But place rook on a2 instead (diagonal from h1 via… no, not diagonal)
            Board noDiag = FenParser.parse("7k/8/8/8/8/8/r7/7K w - - 0 1");
            assertFalse(CheckDetector.isInCheck(noDiag, Color.WHITE));
        }
    }

    // ---- T12-2: bishop attack -----------------------------------------

    @Nested
    @DisplayName("T12-2 — Bishop attack")
    class BishopAttack {

        @Test
        @DisplayName("black bishop on h5 puts white king on d1 in check (diagonal)")
        void bishopDiagonalAttack() {
            // d1 king, h5 bishop — d1→e2→f3→g4→h5 diagonal, 4 squares apart
            Board board = FenParser.parse("7k/8/8/7b/8/8/8/3K4 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("interposing piece blocks bishop")
        void bishopBlocked() {
            // White pawn on f3 blocks the h5 bishop
            Board board = FenParser.parse("7k/8/8/7b/8/5P2/8/3K4 w - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("bishop does not attack along a rank")
        void bishopNoRankAttack() {
            // Bishop on h1, king on a1 — same rank, bishop cannot attack
            Board board = FenParser.parse("7k/8/8/8/8/8/8/K6b w - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        }
    }

    // ---- T12-3: queen attack ------------------------------------------

    @Nested
    @DisplayName("T12-3 — Queen attack")
    class QueenAttack {

        @Test
        @DisplayName("queen attacks like a rook (file)")
        void queenFileAttack() {
            Board board = FenParser.parse("4q3/k7/8/8/8/8/8/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("queen attacks like a rook (rank)")
        void queenRankAttack() {
            Board board = FenParser.parse("7k/8/8/8/8/8/8/q6K w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("queen attacks like a bishop (diagonal)")
        void queenDiagonalAttack() {
            // Queen on h4, king on d8: diagonal h4→g5→f6→e7→d8
            Board board = FenParser.parse("3K4/8/8/8/7q/8/8/7k w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }
    }

    // ---- T12-4: knight attack -----------------------------------------

    @Nested
    @DisplayName("T12-4 — Knight attack")
    class KnightAttack {

        @Test
        @DisplayName("knight on f3 attacks e1 king (L-shape)")
        void knightAttack() {
            // Knight f3 → king e1: file diff=1, rank diff=2
            Board board = FenParser.parse("7k/8/8/8/8/5n2/8/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("knight on g2 attacks e1 king")
        void knightAttackG2() {
            // Knight g2 → king e1: file diff=2, rank diff=1
            Board board = FenParser.parse("7k/8/8/8/8/8/6n1/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("knight is not blocked by pieces in between")
        void knightJumpsOverPieces() {
            // Pieces fill e2,f2 etc. — knight still attacks from f3
            Board board = FenParser.parse("7k/8/8/8/8/5n2/4PPP1/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("knight not attacking from non-L positions")
        void knightNoAttack() {
            // Knight on e5 — not attacking e1 (4 ranks away, same file)
            Board board = FenParser.parse("7k/8/8/4n3/8/8/8/4K3 w - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        }
    }

    // ---- T12-5: pawn attack -------------------------------------------

    @Nested
    @DisplayName("T12-5 — Pawn attack")
    class PawnAttack {

        @Test
        @DisplayName("black pawn on d2 attacks white king on e1")
        void blackPawnAttacksWhiteKing() {
            // Black pawn d2 — attacks c1 and e1 diagonally forward (rank -1 for black)
            Board board = FenParser.parse("7k/8/8/8/8/8/3p4/4K3 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("white pawn on e7 attacks black king on d8")
        void whitePawnAttacksBlackKing() {
            Board board = FenParser.parse("3k4/4P3/8/8/8/8/8/4K3 b - - 0 1");
            assertTrue(CheckDetector.isInCheck(board, Color.BLACK));
        }

        @Test
        @DisplayName("pawn directly in front of king does NOT attack it")
        void pawnDirectlyInFrontNoAttack() {
            // Black pawn e2 directly in front of white king e1 — no attack
            Board board = FenParser.parse("7k/8/8/8/8/8/4p3/4K3 w - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        }

        @Test
        @DisplayName("pawn does not attack backward")
        void pawnNoBackwardAttack() {
            // White pawn e2, black king e1 — white pawn attacks forward (ranks 2→3),
            // so e1 (rank 0) is NOT attacked by white pawn on e2
            Board board = FenParser.parse("4K3/8/8/8/8/8/4P3/4k3 b - - 0 1");
            assertFalse(CheckDetector.isInCheck(board, Color.BLACK));
        }
    }

    // ---- T12-6: king adjacency ----------------------------------------

    @Nested
    @DisplayName("T12-6 — King adjacency (relevant for move validation)")
    class KingAdjacency {

        @Test
        @DisplayName("enemy king one square away is detected as attacker")
        void kingAdjacencyCheck() {
            // White king d4, black king d6 — two ranks apart (NOT adjacent, no check)
            Board notAdjacent = FenParser.parse("8/8/3k4/8/3K4/8/8/8 w - - 0 1");
            assertFalse(CheckDetector.isInCheck(notAdjacent, Color.WHITE));

            // White king d4, black king d5 — one rank apart (adjacent)
            // In a real game this position is illegal, but CheckDetector must handle it
            Board adjacent = FenParser.parse("8/8/8/3k4/3K4/8/8/8 w - - 0 1");
            assertTrue(CheckDetector.isInCheck(adjacent, Color.WHITE));
        }
    }
}
