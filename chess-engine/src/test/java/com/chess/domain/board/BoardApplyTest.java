package com.chess.domain.board;

import com.chess.domain.model.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Board.apply(Move) covering all five move categories.
 *
 * T3 — normal move
 * T4 — capture
 * T5 — promotion (all four piece types)
 * T6 — en passant
 * T7 — castling (all four variants)
 */
@DisplayName("Board.apply()")
class BoardApplyTest {

    // ====================================================================
    // T3 — NORMAL MOVE
    // ====================================================================

    @Nested
    @DisplayName("T3 — normal move")
    class NormalMove {

        // White king e1, black king e8 — simplest legal position
        private final Board board = FenParser.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1");

        @Test
        @DisplayName("source square is emptied")
        void sourceIsEmpty() {
            Board after = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            assertTrue(after.isEmpty(Square.of("e1")));
        }

        @Test
        @DisplayName("destination is occupied by the moved piece")
        void destinationIsOccupied() {
            Board after = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            var d1 = after.pieceAt(Square.of("d1"));
            assertTrue(d1.isPresent());
            assertEquals(PieceType.KING, d1.get().type());
            assertEquals(Color.WHITE,    d1.get().color());
        }

        @Test
        @DisplayName("active color switches to the opponent")
        void activeColorSwitches() {
            Board after = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            assertEquals(Color.BLACK, after.activeColor());
        }

        @Test
        @DisplayName("move count on the piece is incremented")
        void moveCountIncremented() {
            Board after = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            int count = after.pieceAt(Square.of("d1")).get().moveCount();
            assertEquals(1, count);
        }

        @Test
        @DisplayName("half-move clock increments for non-pawn non-capture")
        void halfMoveClockIncrements() {
            Board after = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            assertEquals(1, after.halfMoveClock());
        }

        @Test
        @DisplayName("full-move number increments only after Black moves")
        void fullMoveAfterBlack() {
            // White moves first — no increment
            Board after1 = board.apply(Move.normal(Square.of("e1"), Square.of("d1")));
            assertEquals(1, after1.fullMoveNumber());
            // Then Black moves — increment
            Board after2 = after1.apply(Move.normal(Square.of("e8"), Square.of("d8")));
            assertEquals(2, after2.fullMoveNumber());
        }

        @Test
        @DisplayName("en-passant target is cleared after non-pawn move")
        void epTargetCleared() {
            // Position where ep was available
            Board withEp = FenParser.parse("4k3/8/8/8/4Pp2/8/8/4K3 b - e3 0 1");
            // Black king moves — ep target should be cleared
            Board after = withEp.apply(Move.normal(Square.of("e8"), Square.of("d8")));
            assertTrue(after.enPassantTarget().isEmpty());
        }
    }

    // ====================================================================
    // T4 — CAPTURE
    // ====================================================================

    @Nested
    @DisplayName("T4 — capture")
    class Capture {

        // White rook on a1, black rook on a8, both kings present
        private final Board board =
            FenParser.parse("r3k3/8/8/8/8/8/8/R3K3 w - - 10 5");

        @Test
        @DisplayName("captured piece is removed from the board")
        void capturedPieceDisappears() {
            Piece blackRook = board.pieceAt(Square.of("a8")).orElseThrow();
            Move capture = Move.capture(Square.of("a1"), Square.of("a8"), blackRook);
            Board after = board.apply(capture);
            // The capturing rook is now on a8
            var a8 = after.pieceAt(Square.of("a8"));
            assertTrue(a8.isPresent());
            assertEquals(Color.WHITE, a8.get().color());
            assertEquals(PieceType.ROOK, a8.get().type());
        }

        @Test
        @DisplayName("source square is emptied after capture")
        void sourceEmptied() {
            Piece blackRook = board.pieceAt(Square.of("a8")).orElseThrow();
            Board after = board.apply(Move.capture(Square.of("a1"), Square.of("a8"), blackRook));
            assertTrue(after.isEmpty(Square.of("a1")));
        }

        @Test
        @DisplayName("half-move clock resets to 0 on capture")
        void halfMoveClockResets() {
            Piece blackRook = board.pieceAt(Square.of("a8")).orElseThrow();
            Board after = board.apply(Move.capture(Square.of("a1"), Square.of("a8"), blackRook));
            assertEquals(0, after.halfMoveClock());
        }

        @Test
        @DisplayName("active color switches after capture")
        void activeColorSwitches() {
            Piece blackRook = board.pieceAt(Square.of("a8")).orElseThrow();
            Board after = board.apply(Move.capture(Square.of("a1"), Square.of("a8"), blackRook));
            assertEquals(Color.BLACK, after.activeColor());
        }
    }

    // ====================================================================
    // T5 — PROMOTION
    // ====================================================================

    @Nested
    @DisplayName("T5 — pawn promotion")
    class Promotion {

        // White pawn on e7, black king on e8 corner, white king on e1
        // We place the kings away from the promotion square to avoid check issues
        private final Board board =
            FenParser.parse("7k/4P3/8/8/8/8/8/4K3 w - - 0 1");

        private Board promote(PieceType target) {
            Move m = Move.promotion(Square.of("e7"), Square.of("e8"),
                                    null, target);
            return board.apply(m);
        }

        @Test
        @DisplayName("promotion to Queen")
        void promoteToQueen() {
            Board after = promote(PieceType.QUEEN);
            var e8 = after.pieceAt(Square.of("e8")).orElseThrow();
            assertEquals(PieceType.QUEEN, e8.type());
            assertEquals(Color.WHITE,     e8.color());
        }

        @Test
        @DisplayName("promotion to Rook")
        void promoteToRook() {
            Board after = promote(PieceType.ROOK);
            assertEquals(PieceType.ROOK,
                after.pieceAt(Square.of("e8")).orElseThrow().type());
        }

        @Test
        @DisplayName("promotion to Bishop")
        void promoteToBishop() {
            Board after = promote(PieceType.BISHOP);
            assertEquals(PieceType.BISHOP,
                after.pieceAt(Square.of("e8")).orElseThrow().type());
        }

        @Test
        @DisplayName("promotion to Knight")
        void promoteToKnight() {
            Board after = promote(PieceType.KNIGHT);
            assertEquals(PieceType.KNIGHT,
                after.pieceAt(Square.of("e8")).orElseThrow().type());
        }

        @Test
        @DisplayName("pawn is removed from source square after promotion")
        void pawnRemovedFromSource() {
            Board after = promote(PieceType.QUEEN);
            assertTrue(after.isEmpty(Square.of("e7")));
        }

        @Test
        @DisplayName("promotion with capture — captured piece replaced")
        void promotionWithCapture() {
            // White pawn on a7 captures black rook on b8, promotes to queen
            Board capturePos = FenParser.parse("1r5k/P7/8/8/8/8/8/4K3 w - - 0 1");
            Piece blackRook  = capturePos.pieceAt(Square.of("b8")).orElseThrow();
            Move  m          = Move.promotion(Square.of("a7"), Square.of("b8"),
                                              blackRook, PieceType.QUEEN);
            Board after      = capturePos.apply(m);

            var b8 = after.pieceAt(Square.of("b8")).orElseThrow();
            assertEquals(PieceType.QUEEN, b8.type());
            assertEquals(Color.WHITE,     b8.color());
            assertTrue(after.isEmpty(Square.of("a7")));
        }
    }

    // ====================================================================
    // T6 — EN PASSANT
    // ====================================================================

    @Nested
    @DisplayName("T6 — en passant")
    class EnPassant {

        // White pawn e5, black pawn d5 (just double-pushed), ep target d6
        private final Board board =
            FenParser.parse("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");

        @Test
        @DisplayName("captured pawn is removed from its real square (d5, not d6)")
        void capturedPawnRemovedCorrectly() {
            Piece blackPawn = board.pieceAt(Square.of("d5")).orElseThrow();
            Move ep = Move.enPassant(Square.of("e5"), Square.of("d6"), blackPawn);
            Board after = board.apply(ep);

            // The black pawn on d5 must be gone
            assertTrue(after.isEmpty(Square.of("d5")));
            // And the white pawn must be on d6
            var d6 = after.pieceAt(Square.of("d6")).orElseThrow();
            assertEquals(Color.WHITE,    d6.color());
            assertEquals(PieceType.PAWN, d6.type());
        }

        @Test
        @DisplayName("source square is emptied after en passant")
        void sourceSquareEmptied() {
            Piece blackPawn = board.pieceAt(Square.of("d5")).orElseThrow();
            Board after = board.apply(
                Move.enPassant(Square.of("e5"), Square.of("d6"), blackPawn));
            assertTrue(after.isEmpty(Square.of("e5")));
        }

        @Test
        @DisplayName("half-move clock resets after en passant (pawn capture)")
        void halfMoveClockResets() {
            Piece blackPawn = board.pieceAt(Square.of("d5")).orElseThrow();
            Board after = board.apply(
                Move.enPassant(Square.of("e5"), Square.of("d6"), blackPawn));
            assertEquals(0, after.halfMoveClock());
        }

        @Test
        @DisplayName("en-passant target is cleared after en passant is played")
        void epTargetCleared() {
            Piece blackPawn = board.pieceAt(Square.of("d5")).orElseThrow();
            Board after = board.apply(
                Move.enPassant(Square.of("e5"), Square.of("d6"), blackPawn));
            assertTrue(after.enPassantTarget().isEmpty());
        }

        @Test
        @DisplayName("pawn double push sets the correct en-passant target")
        void doublePushSetsEpTarget() {
            // White pawn on e2 double-pushes to e4 → ep target should be e3
            Board initial = FenParser.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1");
            Board after   = initial.apply(Move.normal(Square.of("e2"), Square.of("e4")));
            assertTrue(after.enPassantTarget().isPresent());
            assertEquals(Square.of("e3"), after.enPassantTarget().get());
        }
    }

    // ====================================================================
    // T7 — CASTLING
    // ====================================================================

    @Nested
    @DisplayName("T7 — castling")
    class Castling {

        @Test
        @DisplayName("white king-side castling — king on g1, rook on f1")
        void whiteKingSide() {
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
            Board after = board.apply(Move.castling(Square.of("e1"), Square.of("g1")));

            assertKingRookPosition(after, Square.of("g1"), Square.of("f1"), Color.WHITE);
            assertTrue(after.isEmpty(Square.of("e1")));
            assertTrue(after.isEmpty(Square.of("h1")));
        }

        @Test
        @DisplayName("white queen-side castling — king on c1, rook on d1")
        void whiteQueenSide() {
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
            Board after = board.apply(Move.castling(Square.of("e1"), Square.of("c1")));

            assertKingRookPosition(after, Square.of("c1"), Square.of("d1"), Color.WHITE);
            assertTrue(after.isEmpty(Square.of("e1")));
            assertTrue(after.isEmpty(Square.of("a1")));
        }

        @Test
        @DisplayName("black king-side castling — king on g8, rook on f8")
        void blackKingSide() {
            // Black to move
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
            Board after = board.apply(Move.castling(Square.of("e8"), Square.of("g8")));

            assertKingRookPosition(after, Square.of("g8"), Square.of("f8"), Color.BLACK);
            assertTrue(after.isEmpty(Square.of("e8")));
            assertTrue(after.isEmpty(Square.of("h8")));
        }

        @Test
        @DisplayName("black queen-side castling — king on c8, rook on d8")
        void blackQueenSide() {
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
            Board after = board.apply(Move.castling(Square.of("e8"), Square.of("c8")));

            assertKingRookPosition(after, Square.of("c8"), Square.of("d8"), Color.BLACK);
            assertTrue(after.isEmpty(Square.of("e8")));
            assertTrue(after.isEmpty(Square.of("a8")));
        }

        @Test
        @DisplayName("castling rights revoked for the castled side after castling")
        void castlingRightsRevokedAfterCastling() {
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
            Board after = board.apply(Move.castling(Square.of("e1"), Square.of("g1")));
            // White no longer has any castling rights
            assertFalse(after.castlingRights().whiteKingSide());
            assertFalse(after.castlingRights().whiteQueenSide());
            // Black's rights are unchanged
            assertTrue(after.castlingRights().blackKingSide());
            assertTrue(after.castlingRights().blackQueenSide());
        }

        private void assertKingRookPosition(Board after, Square kingSquare,
                                             Square rookSquare, Color color) {
            var king = after.pieceAt(kingSquare).orElseThrow(
                () -> new AssertionError("No king on " + kingSquare));
            assertEquals(PieceType.KING, king.type());
            assertEquals(color,          king.color());

            var rook = after.pieceAt(rookSquare).orElseThrow(
                () -> new AssertionError("No rook on " + rookSquare));
            assertEquals(PieceType.ROOK, rook.type());
            assertEquals(color,          rook.color());
        }
    }
}
