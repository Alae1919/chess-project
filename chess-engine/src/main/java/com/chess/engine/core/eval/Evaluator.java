package com.chess.engine.core.eval;

import com.chess.engine.core.Position;

/**
 * Scores a position. The search calls {@link #onMake} after every move it plays and
 * {@link #onUnmake} when it takes the move back, so an evaluator that keeps running state
 * (a neural network's first layer, say) can update it by the few squares that changed instead
 * of starting over. One that keeps none ignores both.
 *
 * Not thread-safe: one evaluator belongs to one search.
 */
public interface Evaluator {

    /** Starts from {@code pos}: rebuilds any running state. Called at the root of a search. */
    void reset(Position pos);

    /** {@code move} has just been made on {@code pos}. */
    void onMake(Position pos, int move);

    /** The last move reported to {@link #onMake} has been taken back. */
    void onUnmake();

    /** The score in centipawns from the point of view of the side to move; positive is better. */
    int evaluate(Position pos);
}
