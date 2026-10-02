package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Move;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SanFormatter")
class SanFormatterTest {

    /** Plays the UCI move on the board and returns its SAN. */
    private static String san(String fen, String uci) {
        Board board = FenParser.parse(fen);
        Move move = MoveGenerator.generateLegalMoves(board).stream()
            .filter(m -> m.toUci().equals(uci))
            .findFirst().orElseThrow();
        Board after = board.apply(move);
        return SanFormatter.format(board, move, GameStateChecker.evaluate(after, after.activeColor()));
    }

    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test void pawnPush()      { assertEquals("e4",  san(START, "e2e4")); }
    @Test void knightMove()    { assertEquals("Nf3", san(START, "g1f3")); }

    @Test void pawnCapture() {
        assertEquals("exd5", san("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1", "e4d5"));
    }

    @Test void pieceCapture() {
        assertEquals("Bxd5", san("4k3/8/8/3p4/8/8/6B1/4K3 w - - 0 1", "g2d5"));
    }

    @Test void castling() {
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
        assertEquals("O-O",   san(fen, "e1g1"));
        assertEquals("O-O-O", san(fen, "e1c1"));
    }

    @Test void promotionWithCheck() {
        assertEquals("a8=Q+", san("7k/P7/8/8/8/8/8/K7 w - - 0 1", "a7a8q"));
    }

    @Test void disambiguatesByFile() {
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"));
    }

    @Test void disambiguatesByRank() {
        assertEquals("R1a3", san("4k3/R7/8/8/8/8/8/R3K3 w - - 0 1", "a1a3"));
    }

    @Test void checkmate() {
        assertEquals("Qxf7#", san("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1", "h5f7"));
    }
}
