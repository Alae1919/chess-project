package com.chess.engine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Engine core — Position")
class PositionTest {

    private static String[] uci(int[] moves) {
        return Arrays.stream(moves).mapToObj(Move::toUci).sorted().toArray(String[]::new);
    }

    // ---- against python-chess ---------------------------------------------------------------

    @Test
    @DisplayName("the Polyglot hash matches python-chess for 400 positions")
    void polyglotKeysMatchPythonChess() {
        for (String[] row : CoreFixtures.read("polyglot-keys.csv")) {
            long expected = Long.parseUnsignedLong(row[1], 16);
            assertEquals(Long.toHexString(expected), Long.toHexString(Position.fromFen(row[0]).key()), row[0]);
        }
    }

    @Test
    @DisplayName("the legal moves match python-chess for 400 positions")
    void legalMovesMatchPythonChess() {
        for (String[] row : CoreFixtures.read("legal-moves.csv")) {
            String[] expected = row[1].isBlank() ? new String[0] : row[1].trim().split(" ");
            assertArrayEquals(expected, uci(Position.fromFen(row[0]).legalMoves()), row[0]);
        }
    }

    @Test
    @DisplayName("FEN reads and writes back unchanged")
    void fenRoundTrips() {
        for (String[] row : CoreFixtures.read("polyglot-keys.csv")) {
            assertEquals(row[0], Position.fromFen(row[0]).toFen());
        }
    }

    // ---- make and unmake ---------------------------------------------------------------------

    @Test
    @DisplayName("every move can be taken back exactly, and the hash is kept up to date move by move")
    void makeUnmakeRestoresAndKeyIsIncremental() {
        for (String[] row : CoreFixtures.read("polyglot-keys.csv")) {
            Position pos = Position.fromFen(row[0]);
            String before = pos.toFen();
            long keyBefore = pos.key();
            for (int move : pos.legalMoves()) {
                pos.makeMove(move);
                assertEquals(Position.fromFen(pos.toFen()).key(), pos.key(),
                    "incremental key after " + Move.toUci(move) + " from " + before);
                pos.unmakeMove();
                assertEquals(before, pos.toFen(), "after taking back " + Move.toUci(move));
                assertEquals(keyBefore, pos.key());
            }
        }
    }

    @Test
    @DisplayName("a null move can be taken back")
    void nullMoveRestores() {
        Position pos = Position.fromFen("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2");
        String before = pos.toFen();
        long key = pos.key();

        pos.makeNullMove();
        assertEquals(Position.BLACK, pos.sideToMove());
        assertEquals(Position.fromFen(pos.toFen()).key(), pos.key());
        pos.unmakeNullMove();

        assertEquals(before, pos.toFen());
        assertEquals(key, pos.key());
    }

    @Test
    @DisplayName("moves are found by their UCI text, and unknown text is not a move")
    void parsesUci() {
        Position pos = Position.startPosition();

        assertEquals("e2e4", Move.toUci(pos.parseUci("e2e4")));
        assertEquals(Move.NONE, pos.parseUci("e2e5"));
        assertEquals(Move.NONE, pos.parseUci("nonsense"));
    }

    @Test
    @DisplayName("a promotion names its piece in UCI")
    void promotionUci() {
        Position pos = Position.fromFen("8/P7/8/8/8/8/8/k6K w - - 0 1");

        assertArrayEquals(new String[]{"a7a8b", "a7a8n", "a7a8q", "a7a8r", "h1g1", "h1g2", "h1h2"}, uci(pos.legalMoves()));
    }

    @Test
    @DisplayName("castling moves the rook too, and taking the rook ends the right")
    void castlingAndRights() {
        Position pos = Position.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        pos.makeMove(pos.parseUci("e1g1"));
        assertEquals("r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", pos.toFen());
        pos.makeMove(pos.parseUci("a8a1"));
        assertEquals("4k2r/8/8/8/8/8/8/r4RK1 w k - 0 2", pos.toFen());
    }

    @Test
    @DisplayName("en passant removes the right pawn, and only for one move")
    void enPassant() {
        Position pos = Position.fromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");

        pos.makeMove(pos.parseUci("e5d6"));

        assertEquals("4k3/8/3P4/8/8/8/8/4K3 b - - 0 1", pos.toFen());
    }

    // ---- invalid input -----------------------------------------------------------------------

    @Test
    @DisplayName("text that is not a position is refused")
    void badFenIsRefused() {
        for (String bad : List.of("", "not a fen", "8/8/8/8/8/8/8/8 w - - 0 1", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1",
                                  "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1")) {
            assertThrows(IllegalArgumentException.class, () -> Position.fromFen(bad), bad);
        }
    }

    // ---- draws -------------------------------------------------------------------------------

    @Test
    @DisplayName("a position seen before is a repetition, until a pawn moves or a piece is taken")
    void repetition() {
        Position pos = Position.fromFen("4k3/8/8/8/8/8/4P3/4K1N1 w - - 0 1");
        assertFalse(pos.isRepetition());

        for (String move : new String[]{"g1f3", "e8e7", "f3g1", "e7e8"}) pos.makeMove(pos.parseUci(move));

        assertTrue(pos.isRepetition(), "the start position is back");

        pos.makeMove(pos.parseUci("e2e4"));
        assertFalse(pos.isRepetition(), "a pawn move makes the old positions unreachable");
    }

    @Test
    @DisplayName("fifty moves without a capture or pawn move")
    void fiftyMoveRule() {
        assertFalse(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 99 80").isFiftyMoveDraw());
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 100 80").isFiftyMoveDraw());
    }

    @Test
    @DisplayName("positions where nobody can mate")
    void insufficientMaterial() {
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1").isInsufficientMaterial());
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/8/3NK3 w - - 0 1").isInsufficientMaterial());
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/8/2B1K3 w - - 0 1").isInsufficientMaterial());
        // d8 and c1 are both dark squares; c8 is light
        assertTrue(Position.fromFen("3bk3/8/8/8/8/8/8/2B1K3 w - - 0 1").isInsufficientMaterial(), "bishops on one colour");
        assertFalse(Position.fromFen("2b1k3/8/8/8/8/8/8/2B1K3 w - - 0 1").isInsufficientMaterial(), "bishops on opposite colours");
        assertFalse(Position.fromFen("4k3/8/8/8/8/8/8/2NNK3 w - - 0 1").isInsufficientMaterial());
        assertFalse(Position.fromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1").isInsufficientMaterial());
        assertFalse(Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1").isInsufficientMaterial());
    }

    // ---- attacks -----------------------------------------------------------------------------

    @Test
    @DisplayName("check is seen for every kind of attacker, and kings two files apart are not in check")
    void check() {
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1").inCheck());
        assertTrue(Position.fromFen("4k3/8/8/8/8/5n2/8/4K3 w - - 0 1").inCheck());
        assertTrue(Position.fromFen("4k3/8/8/8/8/8/3p4/4K3 w - - 0 1").inCheck());
        assertTrue(Position.fromFen("4k3/8/8/8/1b6/8/8/4K3 w - - 0 1").inCheck());
        assertFalse(Position.fromFen("8/8/8/8/3K1k2/8/8/8 w - - 0 1").inCheck());
        assertFalse(Position.startPosition().inCheck());
    }
}
