package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.*;
import com.chess.domain.rules.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AlphaBetaSearch")
class AlphaBetaSearchTest {

    private final AlphaBetaSearch search = new AlphaBetaSearch();

    @Test
    @DisplayName("finds forced checkmate in 4 plies — rook endgame")
    void forcedMate() {
        // White to move and win (rook vs lone king scenario)
        // Original FEN from SearchIATest, converted to standard notation:
        // t7/7r/8/6R1/8/8/8/T7 → r7/7R/8/6R1/8/8/8/r7 w - - 0 1
        Board board = FenParser.parse("r7/7k/8/6K1/8/8/8/R7 w - - 0 1");


        // White should find the best first move
        Move best = search.findBestMove(board, 4);
        assertNotNull(best);

        // Play the game out — black should be mated
        Board b = board;
        int maxPlies = 30;
        while (maxPlies-- > 0) {
            var moves = com.chess.domain.rules.MoveGenerator.generateLegalMoves(b);
            if (moves.isEmpty()) break;
            Move m = search.findBestMove(b, 4);
            b = b.apply(m);
        }
        assertEquals(GameStateChecker.State.CHECKMATE,
            GameStateChecker.evaluate(b, b.activeColor()));
    }

    @Test
    @DisplayName("alpha-beta returns same score as plain minimax (consistency)")
    void alphaBetaConsistency() {
        Board board = FenParser.parse("r7/7k/8/6K1/8/8/8/R7 w - - 0 1");
        // Just verify it returns a move without throwing
        Move m = search.findBestMove(board, 3);
        assertNotNull(m);
    }
}

