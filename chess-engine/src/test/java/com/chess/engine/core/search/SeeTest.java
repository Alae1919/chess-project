package com.chess.engine.core.search;

import com.chess.engine.core.Move;
import com.chess.engine.core.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Static exchange evaluation")
class SeeTest {

    private static boolean worth(String fen, String uci, int threshold) {
        Position pos = Position.fromFen(fen);
        int move = pos.parseUci(uci);
        assertNotEquals(Move.NONE, move, uci + " is not legal in " + fen);
        return See.atLeast(pos, move, threshold);
    }

    @Test
    @DisplayName("an undefended pawn is simply won")
    void freePawn() {
        assertTrue(worth("4k3/8/8/3p4/8/8/8/3QK3 w - - 0 1", "d1d5", 100));
        assertFalse(worth("4k3/8/8/3p4/8/8/8/3QK3 w - - 0 1", "d1d5", 101));
    }

    @Test
    @DisplayName("pawn takes defended pawn is an even trade")
    void evenPawnTrade() {
        String fen = "4k3/8/3p4/2p5/3P4/8/8/4K3 w - - 0 1";
        assertTrue(worth(fen, "d4c5", 0));
        assertFalse(worth(fen, "d4c5", 1));
    }

    @Test
    @DisplayName("a rook for a defended pawn loses the exchange")
    void rookForPawn() {
        assertFalse(worth("4k3/8/3p4/2p5/8/8/8/2R1K3 w - - 0 1", "c1c5", 0));
    }

    @Test
    @DisplayName("a defended piece taken by a cheaper one is a gain")
    void cheaperTakesDearer() {
        // pawn takes a defended knight: wins the knight, loses the pawn
        assertTrue(worth("4k3/8/2p5/3n4/4P3/8/8/4K3 w - - 0 1", "e4d5", 200));
    }

    @Test
    @DisplayName("x-rays count: a rook behind a rook joins the capture")
    void xRayAttackers() {
        // Rxd5 Rxd5 Rxd5: the second rook wins the pawn, and the rooks trade
        String battery = "3r2k1/8/8/3p4/8/8/3R4/3RK3 w - - 0 1";
        assertTrue(worth(battery, "d2d5", 100));
        assertFalse(worth(battery, "d2d5", 101));

        // with one rook only, taking the defended pawn loses the rook for a pawn
        String single = "3r2k1/8/8/3p4/8/8/3R4/4K3 w - - 0 1";
        assertFalse(worth(single, "d2d5", 0));
    }

    @Test
    @DisplayName("a quiet move is neutral to a safe square and loses the piece to an attacked one")
    void quietMoves() {
        assertTrue(worth("4k3/8/8/8/8/8/3N4/4K3 w - - 0 1", "d2f3", 0));

        // the pawn on d5 attacks c4 and e4
        String pawnGuarded = "4k3/8/8/3p4/8/8/3N4/4K3 w - - 0 1";
        assertFalse(worth(pawnGuarded, "d2c4", 0));
        assertFalse(worth(pawnGuarded, "d2e4", 0));
        assertTrue(worth(pawnGuarded, "d2f3", 0));
    }

    @Test
    @DisplayName("en passant wins a pawn")
    void enPassant() {
        assertTrue(worth("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6", 100));
    }

    @Test
    @DisplayName("castling is neutral")
    void castling() {
        assertTrue(worth("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", "e1g1", 0));
    }

    /** A pseudo-legal capture built by hand, for moves the legal-move list would never offer. */
    private static boolean capture(String fen, String from, String to, int threshold) {
        Position pos = Position.fromFen(fen);
        return See.atLeast(pos, Move.make(com.chess.engine.core.Bits.parse(from), com.chess.engine.core.Bits.parse(to), Move.CAPTURE), threshold);
    }

    @Test
    @DisplayName("a king may take a piece only if nothing can retake it")
    void kingCapturesLast() {
        assertTrue(capture("4k3/8/8/8/8/3q4/4K3/8 w - - 0 1", "e2", "d3", 900));
        assertFalse(capture("4k3/8/8/8/2p5/3q4/4K3/8 w - - 0 1", "e2", "d3", 0), "the pawn on c4 guards the queen");
    }
}
