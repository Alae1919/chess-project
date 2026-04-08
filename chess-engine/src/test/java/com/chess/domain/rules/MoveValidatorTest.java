package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MoveValidator")
class MoveValidatorTest {

    @Nested
    @DisplayName("Rook moves")
    class RookMoves {
        // "7R/8/8/8/8/8/8/3r1R2" — same position as original TourTest
        // Using standard FEN: rook on f1 (white), rook on d1 (black), rook on h8
        // Kings required for legality checks; rooks as in comment (h8, d1 black, f1 white)
        Board board = FenParser.parse("7R/7k/8/8/8/8/4K3/3r1R2 w - - 0 1");

        @Test @DisplayName("rook moves horizontally and vertically")
        void rookLegalMoves() {
            // White rook on f1 (file=5, rank=0)
            Square f1 = Square.of("f1");
            assertTrue(MoveValidator.isLegal(board, Move.normal(f1, Square.of("g1"))));
            assertTrue(MoveValidator.isLegal(board, Move.normal(f1, Square.of("f7"))));
            // Cannot move diagonally
            assertFalse(MoveValidator.isLegal(board, Move.normal(f1, Square.of("g2"))));
        }

        @Test @DisplayName("rook cannot jump over pieces")
        void rookBlockedByPiece() {
            // Black rook on d1 blocks the path from f1 to a1
            assertFalse(MoveValidator.isLegal(board, Move.normal(Square.of("f1"), Square.of("a1"))));
        }
    }

    @Nested
    @DisplayName("Check detection")
    class CheckTests {
        @Test @DisplayName("move leaving king in check is illegal")
        void moveIntoCheck() {
            // White king on e1, black rook on e8, white rook on e4
            // Moving white rook off the e-file exposes the king to check
            Board board = FenParser.parse("4r3/8/8/8/4R3/8/8/4K3 w - - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.normal(Square.of("e4"), Square.of("f4"))));
        }

        @Test @DisplayName("legal moves when in check")
        void blockingCheck() {
            // White king e1 in check from black rook e8, white rook a4 can block
            Board board = FenParser.parse("4r3/8/8/8/R7/8/8/4K3 w - - 0 1");
            assertTrue(MoveValidator.isLegal(board,
                Move.normal(Square.of("a4"), Square.of("e4"))));
        }
    }

    @Nested
    @DisplayName("Castling")
    class CastlingTests {
        @Test @DisplayName("king-side castling is legal when path is clear")
        void kingSideCastling() {
            Board board = FenParser.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
            // White king-side castling: e1 → g1
            assertTrue(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }

        @Test @DisplayName("castling through attacked square is illegal")
        void castlingThroughAttackedSquare() {
            // Black rook on f8 attacks f1, blocking king-side castling
            Board board = FenParser.parse("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
            assertFalse(MoveValidator.isLegal(board,
                Move.castling(Square.of("e1"), Square.of("g1"))));
        }
    }

    @Nested
    @DisplayName("En-passant")
    class EnPassantTests {
        @Test @DisplayName("en-passant capture is legal immediately after double push")
        void enPassantLegal() {
            // After black plays d7-d5 (ep target is d6), white pawn on e5 can capture
            Board board = FenParser.parse("8/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
            assertTrue(MoveValidator.isLegal(board,
                Move.enPassant(Square.of("e5"), Square.of("d6"),
                    board.pieceAt(Square.of("d5")).orElse(null))));
        }
    }
}
