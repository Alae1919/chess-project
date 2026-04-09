package com.chess.domain.board;

import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T8 — CastlingRights.update() tests.
 *
 * Covers:
 *   • King move revokes both rights for that color
 *   • Rook move from home square revokes that side's right
 *   • Rook CAPTURED on home square revokes that side's right (T8 fix)
 *   • Other pieces moving do not change rights
 *   • Rights already false stay false
 */
@DisplayName("CastlingRights.update()")
class CastlingRightsTest {

    private static final CastlingRights ALL  = CastlingRights.all();
    private static final CastlingRights NONE = CastlingRights.none();

    // Helper: build a CastlingRights from booleans
    private CastlingRights rights(boolean wKS, boolean wQS, boolean bKS, boolean bQS) {
        return new CastlingRights(wKS, wQS, bKS, bQS);
    }

    // Helper: build a Piece
    private Piece piece(Color c, PieceType t) { return Piece.of(c, t); }

    // ---- King moves ----------------------------------------------------

    @Test
    @DisplayName("white king move revokes both white castling rights")
    void whiteKingMoveRevokesWhiteRights() {
        Move move = Move.normal(Square.of("e1"), Square.of("f1"));
        Piece king = piece(Color.WHITE, PieceType.KING);
        CastlingRights after = ALL.update(move, king);

        assertFalse(after.whiteKingSide());
        assertFalse(after.whiteQueenSide());
        // Black unchanged
        assertTrue(after.blackKingSide());
        assertTrue(after.blackQueenSide());
    }

    @Test
    @DisplayName("black king move revokes both black castling rights")
    void blackKingMoveRevokesBlackRights() {
        Move move = Move.normal(Square.of("e8"), Square.of("f8"));
        Piece king = piece(Color.BLACK, PieceType.KING);
        CastlingRights after = ALL.update(move, king);

        assertFalse(after.blackKingSide());
        assertFalse(after.blackQueenSide());
        // White unchanged
        assertTrue(after.whiteKingSide());
        assertTrue(after.whiteQueenSide());
    }

    // ---- Rook moves from home squares ----------------------------------

    @Test
    @DisplayName("white h1 rook move revokes white king-side right")
    void whiteKingSideRookMove() {
        Move move = Move.normal(Square.of("h1"), Square.of("h4"));
        CastlingRights after = ALL.update(move, piece(Color.WHITE, PieceType.ROOK));

        assertFalse(after.whiteKingSide());
        assertTrue(after.whiteQueenSide());  // queen-side unchanged
    }

    @Test
    @DisplayName("white a1 rook move revokes white queen-side right")
    void whiteQueenSideRookMove() {
        Move move = Move.normal(Square.of("a1"), Square.of("a4"));
        CastlingRights after = ALL.update(move, piece(Color.WHITE, PieceType.ROOK));

        assertFalse(after.whiteQueenSide());
        assertTrue(after.whiteKingSide());   // king-side unchanged
    }

    @Test
    @DisplayName("black h8 rook move revokes black king-side right")
    void blackKingSideRookMove() {
        Move move = Move.normal(Square.of("h8"), Square.of("h5"));
        CastlingRights after = ALL.update(move, piece(Color.BLACK, PieceType.ROOK));

        assertFalse(after.blackKingSide());
        assertTrue(after.blackQueenSide());
    }

    @Test
    @DisplayName("black a8 rook move revokes black queen-side right")
    void blackQueenSideRookMove() {
        Move move = Move.normal(Square.of("a8"), Square.of("a5"));
        CastlingRights after = ALL.update(move, piece(Color.BLACK, PieceType.ROOK));

        assertFalse(after.blackQueenSide());
        assertTrue(after.blackKingSide());
    }

    @Test
    @DisplayName("rook move from non-home square does not change rights")
    void rookMoveFromNonHomeSquare() {
        Move move = Move.normal(Square.of("h4"), Square.of("h7"));
        CastlingRights after = ALL.update(move, piece(Color.WHITE, PieceType.ROOK));

        assertEquals(ALL, after);  // nothing changed
    }

    // ---- Rook captured on home square (T8 fix) -------------------------

    @Test
    @DisplayName("T8 — white h1 rook captured: white king-side right revoked")
    void captureOfWhiteKingSideRook() {
        // An enemy piece captures the white rook on h1
        Piece capturedRook = piece(Color.WHITE, PieceType.ROOK);
        Move capture = Move.capture(Square.of("h8"), Square.of("h1"), capturedRook);
        // Moving piece is a black rook (irrelevant type for this test — could be any enemy)
        CastlingRights after = ALL.update(capture, piece(Color.BLACK, PieceType.ROOK));

        assertFalse(after.whiteKingSide(),  "white K-side right should be revoked");
        assertTrue(after.whiteQueenSide(),  "white Q-side right should be unchanged");
    }

    @Test
    @DisplayName("T8 — white a1 rook captured: white queen-side right revoked")
    void captureOfWhiteQueenSideRook() {
        Piece capturedRook = piece(Color.WHITE, PieceType.ROOK);
        Move capture = Move.capture(Square.of("a8"), Square.of("a1"), capturedRook);
        CastlingRights after = ALL.update(capture, piece(Color.BLACK, PieceType.QUEEN));

        assertFalse(after.whiteQueenSide(), "white Q-side right should be revoked");
        assertTrue(after.whiteKingSide(),   "white K-side right should be unchanged");
    }

    @Test
    @DisplayName("T8 — black h8 rook captured: black king-side right revoked")
    void captureOfBlackKingSideRook() {
        Piece capturedRook = piece(Color.BLACK, PieceType.ROOK);
        Move capture = Move.capture(Square.of("h1"), Square.of("h8"), capturedRook);
        CastlingRights after = ALL.update(capture, piece(Color.WHITE, PieceType.ROOK));

        assertFalse(after.blackKingSide(),  "black K-side right should be revoked");
        assertTrue(after.blackQueenSide(),  "black Q-side right should be unchanged");
    }

    @Test
    @DisplayName("T8 — black a8 rook captured: black queen-side right revoked")
    void captureOfBlackQueenSideRook() {
        Piece capturedRook = piece(Color.BLACK, PieceType.ROOK);
        Move capture = Move.capture(Square.of("a1"), Square.of("a8"), capturedRook);
        CastlingRights after = ALL.update(capture, piece(Color.WHITE, PieceType.QUEEN));

        assertFalse(after.blackQueenSide(), "black Q-side right should be revoked");
        assertTrue(after.blackKingSide(),   "black K-side right should be unchanged");
    }

    // ---- Non-rook/king moves do not change rights ----------------------

    @Test
    @DisplayName("pawn move does not change castling rights")
    void pawnMoveNoEffect() {
        Move move = Move.normal(Square.of("e2"), Square.of("e4"));
        CastlingRights after = ALL.update(move, piece(Color.WHITE, PieceType.PAWN));
        assertEquals(ALL, after);
    }

    @Test
    @DisplayName("rights that are already false stay false")
    void alreadyFalseStayFalse() {
        Move move = Move.normal(Square.of("e1"), Square.of("f1"));
        CastlingRights after = NONE.update(move, piece(Color.WHITE, PieceType.KING));
        assertEquals(NONE, after);
    }
}
