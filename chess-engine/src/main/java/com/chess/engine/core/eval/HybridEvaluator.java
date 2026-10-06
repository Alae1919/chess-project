package com.chess.engine.core.eval;

import com.chess.engine.core.Position;
import com.chess.engine.core.eval.nnue.NnueEvaluator;

import static com.chess.engine.core.Position.BLACK;
import static com.chess.engine.core.Position.KING;
import static com.chess.engine.core.Position.WHITE;

/**
 * The neural network everywhere, except against a lone king. A network trained on positions from
 * real games has hardly seen a king and a bishop and a knight chasing a bare king, so its score is
 * flat there and the search has nothing to steer by until the mate is inside its horizon. The classical
 * evaluation drives the bare king to the edge and a corner, which is exactly what those endings need.
 *
 * The network's running sums are kept up to date through every move either way, so it can take over
 * again as soon as a position is not a lone-king ending (after an undo, say).
 */
final class HybridEvaluator implements Evaluator {

    private final NnueEvaluator network;
    private final ClassicalEvaluator classical = new ClassicalEvaluator();

    HybridEvaluator(NnueEvaluator network) {
        this.network = network;
    }

    @Override public void reset(Position pos) { network.reset(pos); }
    @Override public void onMake(Position pos, int move) { network.onMake(pos, move); }
    @Override public void onUnmake() { network.onUnmake(); }

    @Override
    public int evaluate(Position pos) {
        return hasBareKing(pos) ? classical.evaluate(pos) : network.evaluate(pos);
    }

    /** True when either side has nothing left but its king. */
    static boolean hasBareKing(Position pos) {
        return pos.occupancy(WHITE) == pos.bitboard(WHITE, KING) || pos.occupancy(BLACK) == pos.bitboard(BLACK, KING);
    }
}
