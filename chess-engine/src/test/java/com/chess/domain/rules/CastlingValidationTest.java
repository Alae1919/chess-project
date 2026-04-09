package com.chess.domain.rules;

import com.chess.domain.board.*;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T11 — Castling prohibition rules.
 *
 * The six cases where castling is forbidden:
 *   T11-1  Castling rights absent (king already moved)
 *   T11-2  Rook already moved (rights revoked)
 *   T11-3  King currently in check
 *   T11-4  King passes through an attacked square
 *   T11-5  King's destination is attacked
 *   T11-6  Piece stands between king and rook
 *
 * Plus three "happy path" cases to confirm legal castling is accepted.
 */
@DisplayName("T11 — Castling validation")
class CastlingValidationTest {

    // ---- T11-1: castling rights absent (king or rook already moved) ----

    @Test
    @DisplayName("T11-1a king-side castling forbidden when rights absent (king moved)")
    void kingSideForbiddenNoRights() {
        // No castling rights in FEN (-) despite clear path and rooks on corners
        Board board = FenParser.parse("4k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-1b queen-side castling forbidden when rights absent")
    void queenSideForbiddenNoRights() {
        Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    @Test
    @DisplayName("T11-1c black king-side castling forbidden when rights absent")
    void blackKingSideForbiddenNoRights() {
        Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R b - - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e8"), Square.of("g8"))));
    }

    // ---- T11-2: rook-side rights specifically absent -------------------

    @Test
    @DisplayName("T11-2a king-side only: queen-side castling forbidden")
    void queenSideForbiddenWhenOnlyKingSideRight() {
        // Only white king-side right (K), not queen-side
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w K - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
        // But king-side is still legal
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-2b queen-side only: king-side castling forbidden")
    void kingSideForbiddenWhenOnlyQueenSideRight() {
        // Only white queen-side right (Q)
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w Q - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    // ---- T11-3: king currently in check --------------------------------

    @Test
    @DisplayName("T11-3 castling forbidden when king is in check")
    void forbiddenWhileInCheck() {
        // Black rook on e8 attacks e1 — white king is in check
        Board board = FenParser.parse("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))),
            "Cannot castle while in check");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))),
            "Cannot castle while in check (queen-side)");
    }

    // ---- T11-4: king passes through attacked square --------------------

    @Test
    @DisplayName("T11-4a king-side: f1 attacked → castling forbidden")
    void forbiddenThroughAttackedSquareF1() {
        // Black rook on f8 attacks f1 (king must pass through f1 for O-O)
        Board board = FenParser.parse("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-4b queen-side: d1 attacked → castling forbidden")
    void forbiddenThroughAttackedSquareD1() {
        // Black rook on d8 attacks d1 (king passes through d1 for O-O-O)
        Board board = FenParser.parse("3r3k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    // ---- T11-5: king's destination is attacked -------------------------

    @Test
    @DisplayName("T11-5a king-side: g1 attacked → castling forbidden")
    void forbiddenDestinationG1Attacked() {
        // Black rook on g8 attacks g1
        Board board = FenParser.parse("6rk/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-5b queen-side: c1 attacked → castling forbidden")
    void forbiddenDestinationC1Attacked() {
        // Black rook on c8 attacks c1
        Board board = FenParser.parse("2r4k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    // ---- T11-6: piece between king and rook ----------------------------

    @Test
    @DisplayName("T11-6a bishop on f1 blocks king-side castling")
    void blockedByBishopF1() {
        // White bishop on f1 stands between king (e1) and rook (h1)
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3KB1R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-6b knight on g1 blocks king-side castling")
    void blockedByKnightG1() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K1NR w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("T11-6c bishop on c1 blocks queen-side castling")
    void blockedByBishopC1() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R1B1K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    @Test
    @DisplayName("T11-6d knight on b1 blocks queen-side castling")
    void blockedByKnightB1() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/RN2K2R w KQ - 0 1");
        assertFalse(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    // ---- Happy path: valid castling accepted ---------------------------

    @Test
    @DisplayName("white king-side castling legal when all conditions met")
    void whiteKingSideLegal() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("g1"))));
    }

    @Test
    @DisplayName("white queen-side castling legal when all conditions met")
    void whiteQueenSideLegal() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e1"), Square.of("c1"))));
    }

    @Test
    @DisplayName("black king-side castling legal")
    void blackKingSideLegal() {
        Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1");
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e8"), Square.of("g8"))));
    }

    @Test
    @DisplayName("black queen-side castling legal")
    void blackQueenSideLegal() {
        Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1");
        assertTrue(MoveValidator.isLegal(board,
            Move.castling(Square.of("e8"), Square.of("c8"))));
    }
}
