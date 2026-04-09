package com.chess.domain.rules;

import com.chess.domain.board.*;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MoveValidator tests.
 *
 * T9 — isLegal() guards:
 *   T9-1  empty source square
 *   T9-2  opponent's piece
 *   T9-3  capture own piece
 *   T9-4  invalid geometry
 *   T9-5  move leaves king in check
 *
 * T10 — pawn move completeness:
 *   T10-1  single step forward
 *   T10-2  double step (starting rank only)
 *   T10-3  diagonal capture
 *   T10-4  en passant
 *   T10-5  promotion (any back-rank move)
 *   T10-6  backward moves forbidden
 *   T10-7  sideways / illegal diagonals
 */
@DisplayName("MoveValidator")
class MoveValidatorTest {

    // ====================================================================
    // T9 — isLegal() GUARDS
    // ====================================================================

    @Nested
    @DisplayName("T9 — isLegal() guards")
    class Guards {

        // Position: white king e1, black king e8, white rook a1
        private final Board board =
            FenParser.parse("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");

        @Test
        @DisplayName("T9-1 — empty source square returns false")
        void emptySourceSquare() {
            // b1 is empty
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("b1"), Square.of("b5"))));
        }

        @Test
        @DisplayName("T9-2 — moving the opponent's piece returns false")
        void opponentPiece() {
            // It is White's turn; try to move the black king on e8
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e8"), Square.of("d8"))));
        }

        @Test
        @DisplayName("T9-3 — capturing a friendly piece returns false")
        void captureFriendlyPiece() {
            // White rook a1 and white king e1 — try rook "captures" king
            Piece king = board.pieceAt(Square.of("e1")).orElseThrow();
            assertFalse(MoveValidator.isLegal(board,
                Move.capture(Square.of("a1"), Square.of("e1"), king)));
        }

        @Test
        @DisplayName("T9-4 — geometrically invalid move returns false")
        void invalidGeometry() {
            // Rook cannot move diagonally
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("a1"), Square.of("b2"))));
        }

        @Test
        @DisplayName("T9-5 — move that exposes own king to check returns false")
        void leavesKingInCheck() {
            // White rook on e4 is pinned by black rook e8 — moving it exposes white king e1
            // Keep a black king on a8 so the position remains valid.
            Board pinned = FenParser.parse("k3r3/8/8/8/4R3/8/8/4K3 w - - 0 1");
            // e-file: white king e1, white rook e4, black rook e8
            // Moving the rook off the e-file is illegal (pin)
            assertFalse(MoveValidator.isLegal(pinned,
                Move.normal(Square.of("e4"), Square.of("d4"))));
        }

        @Test
        @DisplayName("legal move is accepted")
        void legalMoveAccepted() {
            // White rook a1 moves to a5 — legal
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("a1"), Square.of("a5"))));
        }
    }

    // ====================================================================
    // T10 — PAWN VALIDATION
    // ====================================================================

    @Nested
    @DisplayName("T10 — pawn moves")
    class PawnMoves {

        // ---- T10-1 Single step -----------------------------------------

        @Test
        @DisplayName("T10-1 white pawn single step forward — legal")
        void whiteSingleStep() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e3"))));
        }

        @Test
        @DisplayName("T10-1 black pawn single step forward — legal")
        void blackSingleStep() {
            Board board = FenParser.parse("4k3/4p3/8/8/8/8/8/4K3 b - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("e7"), Square.of("e6"))));
        }

        @Test
        @DisplayName("T10-1 pawn single step blocked by own piece — illegal")
        void singleStepBlocked() {
            // White pawn e2 blocked by white rook e3
            Board board = FenParser.parse("4k3/8/8/8/8/4R3/4P3/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e3"))));
        }

        @Test
        @DisplayName("T10-1 pawn single step blocked by enemy piece — illegal")
        void singleStepBlockedByEnemy() {
            // Enemy pawn on e3 blocks white pawn on e2
            Board board = FenParser.parse("4k3/8/8/8/8/4p3/4P3/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e3"))));
        }

        // ---- T10-2 Double step -----------------------------------------

        @Test
        @DisplayName("T10-2 white pawn double step from rank 2 — legal")
        void whiteDoubleStep() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e4"))));
        }

        @Test
        @DisplayName("T10-2 black pawn double step from rank 7 — legal")
        void blackDoubleStep() {
            Board board = FenParser.parse("4k3/4p3/8/8/8/8/8/4K3 b - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("e7"), Square.of("e5"))));
        }

        @Test
        @DisplayName("T10-2 double step from non-starting rank — illegal")
        void doubleStepFromWrongRank() {
            // Pawn has already moved to e3 — cannot double push from there
            Board board = FenParser.parse("4k3/8/8/8/8/4P3/8/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e3"), Square.of("e5"))));
        }

        @Test
        @DisplayName("T10-2 double step blocked by piece on intermediate square")
        void doubleStepBlockedOnIntermediate() {
            Board board = FenParser.parse("4k3/8/8/8/8/4p3/4P3/4K3 w - - 0 1");
            // e3 blocked → double push e2→e4 illegal
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e4"))));
        }

        @Test
        @DisplayName("T10-2 double step blocked on destination square")
        void doubleStepBlockedOnDestination() {
            Board board = FenParser.parse("4k3/8/8/8/4p3/8/4P3/4K3 w - - 0 1");
            // e4 blocked → double push e2→e4 illegal
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e4"))));
        }

        // ---- T10-3 Diagonal capture ------------------------------------

        @Test
        @DisplayName("T10-3 white pawn diagonal capture — legal")
        void whiteDiagonalCapture() {
            // Black pawn on d3, white pawn on e2
            Board board = FenParser.parse("4k3/8/8/8/8/3p4/4P3/4K3 w - - 0 1");
            Piece blackPawn = board.pieceAt(Square.of("d3")).orElseThrow();
            assertTrue(MoveValidator.isLegal(board,
                Move.capture(Square.of("e2"), Square.of("d3"), blackPawn)));
        }

        @Test
        @DisplayName("T10-3 diagonal move to empty square — illegal (no capture)")
        void diagonalToEmptySquare() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            // d3 is empty — white pawn cannot move there diagonally
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("d3"))));
        }

        @Test
        @DisplayName("T10-3 diagonal capture of own piece — illegal")
        void diagonalCaptureOwnPiece() {
            // Own rook on d3
            Board board = FenParser.parse("4k3/8/8/8/8/3R4/4P3/4K3 w - - 0 1");
            Piece ownRook = board.pieceAt(Square.of("d3")).orElseThrow();
            assertFalse(MoveValidator.isLegal(board,
                Move.capture(Square.of("e2"), Square.of("d3"), ownRook)));
        }

        // ---- T10-4 En passant ------------------------------------------

        @Test
        @DisplayName("T10-4 en passant immediately after double push — legal")
        void enPassantLegal() {
            // White pawn e5, black just double-pushed to d5, ep target d6
            Board board = FenParser.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("e5"), Square.of("d6"))));
        }

        @Test
        @DisplayName("T10-4 en passant not available without prior double push")
        void enPassantNotAvailableWithoutEpTarget() {
            // No en-passant target
            Board board = FenParser.parse("4k3/8/8/3pP3/8/8/8/4K3 w - - 0 2");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e5"), Square.of("d6"))));
        }

        @Test
        @DisplayName("T10-4 en passant that exposes own king — illegal")
        void enPassantExposesKing() {
            // White king on e5, white pawn on d5, black pawn just pushed to d5,
            // but a black rook on e8 would pin — tricky: simplify with direct check
            // White pawn e5 ep would remove the d5 pawn, exposing white king on h5
            // to black rook on a5 (horizontal pin)
            Board board = FenParser.parse("4k3/8/8/r2pPK2/8/8/8/8 w - d6 0 2");
            // Moving e5→d6 (en passant) removes d5 pawn → white king h5 exposed to a5 rook
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e5"), Square.of("d6"))));
        }

        // ---- T10-5 Promotion -------------------------------------------

        @Test
        @DisplayName("T10-5 white pawn reaching rank 8 — all four promotions legal")
        void whitePawnPromotion() {
            Board board = FenParser.parse("7k/4P3/8/8/8/8/8/4K3 w - - 0 1");
            for (PieceType pt : new PieceType[]{PieceType.QUEEN, PieceType.ROOK,
                                                PieceType.BISHOP, PieceType.KNIGHT}) {
                assertTrue(MoveValidator.isLegal(board,
                    Move.promotion(Square.of("e7"), Square.of("e8"), null, pt)),
                    "Promotion to " + pt + " should be legal");
            }
        }

        @Test
        @DisplayName("T10-5 black pawn reaching rank 1 — promotion legal")
        void blackPawnPromotion() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4p3/7K b - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.promotion(Square.of("e2"), Square.of("e1"),
                               null, PieceType.QUEEN)));
        }

        // ---- T10-6 Backward moves forbidden ----------------------------

        @Test
        @DisplayName("T10-6 white pawn moving backward — illegal")
        void whitePawnBackward() {
            Board board = FenParser.parse("4k3/8/8/8/8/4P3/8/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e3"), Square.of("e2"))));
        }

        @Test
        @DisplayName("T10-6 black pawn moving backward — illegal")
        void blackPawnBackward() {
            Board board = FenParser.parse("4k3/8/4p3/8/8/8/8/4K3 b - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e6"), Square.of("e7"))));
        }

        // ---- T10-7 Sideways / illegal diagonals ------------------------

        @Test
        @DisplayName("T10-7 pawn moving sideways — illegal")
        void pawnSideways() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("f2"))));
        }

        @Test
        @DisplayName("T10-7 pawn moving two squares diagonally — illegal")
        void pawnTwoSquaresDiagonal() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("g4"))));
        }

        // ---- T10-5 Pawn does not leave king in check -------------------

        @Test
        @DisplayName("T10-5 pawn move that leaves king in check — illegal")
        void pawnMoveLeavesKingInCheck() {
            // White king on e1, black rook on e8, white pawn on e2
            // Pawn is NOT pinned here (it's in front of the king on the same file)
            // → single step is actually legal.
            // Let's try a diagonal pin:
            // White king d1, black bishop h5, white pawn e2 is pinned diagonally
            Board board = FenParser.parse("4k3/8/8/7b/8/8/4P3/3K4 w - - 0 1");
            // Moving e2→e3 breaks the diagonal pin d1-e2-h5? No — pawn is on e2.
            // d1 king, e2 pawn, h5 bishop: diagonal d1→h5 passes through e2. Pin!
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e2"), Square.of("e3"))));
        }
    }

    // ====================================================================
    // T9 — CASTLING EDGE CASES
    // ====================================================================

    @Nested
    @DisplayName("T9 — castling legality edge cases")
    class CastlingEdgeCases {

        @Test
        @DisplayName("king-side castling through attacked square — illegal")
        void castlingThroughAttackedSquare() {
            // Black rook on f8 attacks f1
            Board board = FenParser.parse("5r1k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }

        @Test
        @DisplayName("castling while king is in check — illegal")
        void castlingWhileInCheck() {
            // Black rook on e8 attacks e1 — king is in check
            Board board = FenParser.parse("4r2k/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }

        @Test
        @DisplayName("castling with piece in the way — illegal")
        void castlingWithPieceInPath() {
            // Knight on g1 blocks king-side castling
            Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K1NR w KQ - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }

        @Test
        @DisplayName("castling rights absent — illegal even with clear path")
        void castlingRightsAbsent() {
            // No castling rights despite clear path
            Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }

        @Test
        @DisplayName("valid king-side castling — legal")
        void validKingSideCastling() {
            Board board = FenParser.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }
    }
}
