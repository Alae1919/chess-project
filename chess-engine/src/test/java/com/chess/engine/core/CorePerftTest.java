package com.chess.engine.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Perft counts for the standard test positions (published at chessprogramming.org). They are
 * a hard test: one wrong rule anywhere in the first few plies changes the number.
 */
@DisplayName("Engine core — perft")
class CorePerftTest {

    private static void check(String fen, long... expected) {
        Position pos = Position.fromFen(fen);
        for (int d = 1; d <= expected.length; d++) {
            assertEquals(expected[d - 1], Perft.perft(pos, d), "depth " + d + " of " + fen);
        }
        assertEquals(fen, pos.toFen(), "perft must leave the position as it found it");
    }

    @Test @DisplayName("the starting position")
    void startPosition() {
        check(Position.START_FEN, 20, 400, 8_902, 197_281, 4_865_609);
    }

    @Test @DisplayName("Kiwipete: castling, pins, en passant and promotions together")
    void kiwipete() {
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2_039, 97_862, 4_085_603);
    }

    @Test @DisplayName("position 3: en passant that exposes a king along a rank")
    void position3() {
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2_812, 43_238, 674_624);
    }

    @Test @DisplayName("position 4: promotions and castling out of and into check")
    void position4() {
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9_467, 422_333);
    }

    @Test @DisplayName("position 4, colours reversed")
    void position4Mirrored() {
        check("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1", 6, 264, 9_467, 422_333);
    }

    @Test @DisplayName("position 5")
    void position5() {
        check("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1_486, 62_379, 2_103_487);
    }

    @Test @DisplayName("position 6")
    void position6() {
        check("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 46, 2_079, 89_890, 3_894_594);
    }
}
