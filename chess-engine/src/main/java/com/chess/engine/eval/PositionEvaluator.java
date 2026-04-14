package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;

/**
 * Strategy interface for position evaluation.
 *
 * Implement this interface to plug in a different evaluator — for example a
 * neural network or NNUE model — without touching the search code.
 *
 * Usage:
 *   AlphaBetaSearch search = new AlphaBetaSearch(new NeuralNetworkEvaluator());
 *
 * The default constructor of {@link AlphaBetaSearch} uses {@link Evaluator}
 * (the handcrafted evaluator), so all existing callers need no changes.
 */
public interface PositionEvaluator {

    /**
     * Returns a score in centipawns from the perspective of {@code color}.
     * Positive = good for {@code color}, negative = bad.
     */
    int evaluate(Board board, Color color);
}
