package com.chess.domain.board;

import com.chess.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FenParser tests.
 *
 * Section A — Valid FEN round-trips           (baseline)
 * Section B — T2 strict validation            (invalid FEN rejection)
 */
@DisplayName("FenParser")
class FenParserTest {

    // ====================================================================
    // A — VALID FEN
    // ====================================================================

    @Test
    @DisplayName("parses starting position — piece placement")
    void startingPositionPieces() {
        Board board = FenParser.parse(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        // White queen on d1
        var d1 = board.pieceAt(Square.of("d1"));
        assertTrue(d1.isPresent());
        assertEquals(PieceType.QUEEN, d1.get().type());
        assertEquals(Color.WHITE,     d1.get().color());

        // Black king on e8
        var e8 = board.pieceAt(Square.of("e8"));
        assertTrue(e8.isPresent());
        assertEquals(PieceType.KING, e8.get().type());
        assertEquals(Color.BLACK,    e8.get().color());

        // e4 is empty
        assertTrue(board.isEmpty(Square.of("e4")));
    }

    @Test
    @DisplayName("active color parsed correctly")
    void activeColor() {
        Board white = FenParser.parse("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        Board black = FenParser.parse("4k3/8/8/8/8/8/8/4K3 b - - 0 1");
        assertEquals(Color.WHITE, white.activeColor());
        assertEquals(Color.BLACK, black.activeColor());
    }

    @Test
    @DisplayName("castling rights parsed correctly")
    void castlingRights() {
        Board board = FenParser.parse(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        CastlingRights cr = board.castlingRights();
        assertTrue(cr.whiteKingSide());
        assertTrue(cr.whiteQueenSide());
        assertTrue(cr.blackKingSide());
        assertTrue(cr.blackQueenSide());
    }

    @Test
    @DisplayName("en-passant target parsed correctly")
    void enPassantTarget() {
        Board board = FenParser.parse("4k3/8/8/8/4Pp2/8/8/4K3 b - e3 0 1");
        assertTrue(board.enPassantTarget().isPresent());
        assertEquals(Square.of("e3"), board.enPassantTarget().get());
    }

    @Test
    @DisplayName("half-move clock and full-move number parsed")
    void moveClocks() {
        Board board = FenParser.parse("4k3/8/8/8/8/8/8/4K3 w - - 42 7");
        assertEquals(42, board.halfMoveClock());
        assertEquals(7,  board.fullMoveNumber());
    }

    @Test
    @DisplayName("round-trip: parse then toFen returns same string")
    void roundTrip() {
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertEquals(fen, FenParser.toFen(FenParser.parse(fen)));
    }

    @Test
    @DisplayName("round-trip on custom position")
    void roundTripCustom() {
        String fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
        assertEquals(fen, FenParser.toFen(FenParser.parse(fen)));
    }

    // ====================================================================
    // B — T2: INVALID FEN REJECTION
    // ====================================================================

    @Nested
    @DisplayName("T2 — invalid FEN is rejected")
    class InvalidFen {

        // ---- B1: wrong number of ranks ---------------------------------

        @Test
        @DisplayName("too few ranks (7 instead of 8)")
        void tooFewRanks() {
            // Only 7 ranks — missing one
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/4K3 w - - 0 1"));
        }

        @Test
        @DisplayName("too many ranks (9 instead of 8)")
        void tooManyRanks() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/8/8/4K3 w - - 0 1"));
        }

        // ---- B2: rank width --------------------------------------------

        @Test
        @DisplayName("rank wider than 8 squares via digit overflow")
        void rankTooWideViaDigit() {
            // "9" is not a legal digit; parser sees it and must reject
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/8/9 w - - 0 1"));
        }

        @Test
        @DisplayName("rank wider than 8 squares via pieces")
        void rankTooWideViaPieces() {
            // 8 pieces + 1 extra 'P' = 9 squares
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("PPPPPPPPPP/pppppppp/8/8/8/8/pppppppp/4k2K w - - 0 1"));
        }

        @Test
        @DisplayName("rank shorter than 8 squares")
        void rankTooShort() {
            // Rank 1 has only 4 squares (K = 1 piece, 3 = 3 empty = 4 total)
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/8/K3 w - - 0 1"));
        }

        // ---- B3: illegal characters ------------------------------------

        @ParameterizedTest(name = "illegal char ''{0}''")
        @ValueSource(strings = {
            "4k3/8/8/8/8/8/8/4K2X w - - 0 1",   // 'X' not a piece
            "4k3/8/8/8/8/8/8/4K2! w - - 0 1",   // '!' symbol
            "4k3/8/8/8/8/8/8/4K2O w - - 0 1",   // 'O' (zero) — not FEN
        })
        @DisplayName("illegal character in piece placement")
        void illegalChar(String fen) {
            assertThrows(InvalidFenException.class, () -> FenParser.parse(fen));
        }

        // ---- B4: missing kings -----------------------------------------

        @Test
        @DisplayName("no white king")
        void noWhiteKing() {
            // Replace white king with a rook
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/8/4R3 w - - 0 1"));
        }

        @Test
        @DisplayName("no black king")
        void noBlackKing() {
            // Replace black king with a rook
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4r3/8/8/8/8/8/8/4K3 w - - 0 1"));
        }

        @Test
        @DisplayName("neither king present")
        void noKings() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4r3/8/8/8/8/8/8/4R3 w - - 0 1"));
        }

        // ---- B5: too many kings ----------------------------------------

        @Test
        @DisplayName("two white kings")
        void twoWhiteKings() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("4k3/8/8/8/8/8/8/3KK3 w - - 0 1"));
        }

        @Test
        @DisplayName("two black kings")
        void twoBlackKings() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("3kk3/8/8/8/8/8/8/4K3 w - - 0 1"));
        }

        // ---- B6: miscellaneous -----------------------------------------

        @Test
        @DisplayName("null FEN throws")
        void nullFen() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse(null));
        }

        @Test
        @DisplayName("blank FEN throws")
        void blankFen() {
            assertThrows(InvalidFenException.class,
                () -> FenParser.parse("   "));
        }
    }
}
