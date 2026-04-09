package com.chess.domain.rules;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Color;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckDetector")
class CheckDetectorTest {

    @Test
    @DisplayName("detects king in check from rook")
    void rookCheck() {
        Board board = FenParser.parse("4r3/k7/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(CheckDetector.isInCheck(board, Color.WHITE));
    }

    @Test
    @DisplayName("no check in starting position")
    void noCheckAtStart() {
        Board board = FenParser.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        assertFalse(CheckDetector.isInCheck(board, Color.WHITE));
        assertFalse(CheckDetector.isInCheck(board, Color.BLACK));
    }

    @Test
    @DisplayName("detects checkmate")
    void checkmate() {
        // Scholar's mate
        Board board = FenParser.parse("r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 1");
        assertEquals(GameStateChecker.State.CHECKMATE,
            GameStateChecker.evaluate(board, Color.BLACK));
    }
}
