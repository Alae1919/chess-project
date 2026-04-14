package com.chess.engine.eval;

import com.chess.domain.board.Board;
import com.chess.domain.model.Color;

/**
 * Stub for a future neural-network-based position evaluator.
 *
 * To activate it, replace the body of {@link #evaluate} with a real inference call
 * and wire it into the search:
 *
 *   AlphaBetaSearch search = new AlphaBetaSearch(new NeuralNetworkEvaluator());
 *
 * Possible input representations:
 *   - 768-bit bitboard vector (12 piece types × 64 squares) — standard for NNUE
 *   - FEN string forwarded to an external Python inference service via HTTP
 *   - Serialised board tensor fed to a DJL (Deep Java Library) model
 *
 * Data collection for training:
 *   Run the engine with position logging enabled (see PositionLogger) to accumulate
 *   (FEN, depth, score) tuples. Filter for positions at depth ≥ 4 and use the
 *   centipawn scores as regression labels.
 */
public final class NeuralNetworkEvaluator implements PositionEvaluator {

    private final Evaluator fallback = new Evaluator();

    @Override
    public int evaluate(Board board, Color color) {
        // TODO: replace with real neural network inference
        // Example skeleton:
        //   float[] inputVector = BoardEncoder.encode(board);          // 768-dim bitboard
        //   float rawScore = model.predict(inputVector);               // centipawn output
        //   return (int)(rawScore * (color == Color.WHITE ? 1 : -1));
        return fallback.evaluate(board, color);
    }
}
