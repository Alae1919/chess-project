package com.chess.engine.search;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;
import com.chess.domain.model.Move;
import com.chess.domain.rules.MoveGenerator;
import com.chess.domain.rules.GameStateChecker;
import com.chess.engine.eval.Evaluator;

import java.util.List;

/**
 * Alpha-beta pruning search.
 *
 * Key improvement over the original SearchAlphaBeta:
 * - Works with immutable Boards → no undoMove() / history corruption risk.
 * - findBestMove() returns the best Move cleanly (no mutable bestMove field).
 * - Evaluation is delegated to Evaluator (single responsibility).
 */
public final class AlphaBetaSearch {

    private static final int NEG_INF = Integer.MIN_VALUE / 2;
    private static final int POS_INF = Integer.MAX_VALUE / 2;

    private final Evaluator evaluator = new Evaluator();

    /** Returns the best move for the current active player at the given depth. */
    public Move findBestMove(Board board, int depth) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);
        Move best = null;
        int alpha = NEG_INF;

        for (Move move : moves) {
            Board next = board.apply(move);
            int score  = -alphaBeta(next, depth - 1, NEG_INF, -alpha);
            if (best == null || score > alpha) {
                alpha = score;
                best  = move;
            }
        }
        return best;
    }

    private int alphaBeta(Board board, int depth, int alpha, int beta) {
        List<Move> moves = MoveGenerator.generateLegalMoves(board);

        if (depth == 0 || moves.isEmpty()) {
            Color active = board.activeColor();
            GameStateChecker.State state = GameStateChecker.evaluate(board, active);
            if (state == GameStateChecker.State.CHECKMATE)
                return NEG_INF + (4 - depth); // prefer faster mates
            if (GameStateChecker.isTerminal(state))
                return 0;
            return evaluator.evaluate(board, active);
        }

        for (Move move : moves) {
            Board next = board.apply(move);
            int score  = -alphaBeta(next, depth - 1, -beta, -alpha);
            if (score >= beta) return beta;  // beta cut-off
            if (score > alpha) alpha = score;
        }
        return alpha;
    }
}
