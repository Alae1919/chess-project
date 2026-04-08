package com.chess.domain.model;

/**
 * An immutable chess piece.
 *
 * This is a pure value object. It knows its color, type, and how many times
 * it has moved (needed for castling and pawn double-step rules).
 * It does NOT reference the board — that was the root cause of circular
 * dependencies in the original design.
 */
public record Piece(Color color, PieceType type, int moveCount) {

    /** Convenience factory for a piece that has never moved. */
    public static Piece of(Color color, PieceType type) {
        return new Piece(color, type, 0);
    }

    /** Returns a copy of this piece with moveCount incremented by 1. */
    public Piece withIncrementedMoveCount() {
        return new Piece(color, type, moveCount + 1);
    }

    /** Returns a copy of this piece with moveCount decremented by 1. */
    public Piece withDecrementedMoveCount() {
        return new Piece(color, type, Math.max(0, moveCount - 1));
    }

    /** True if this piece has never moved (relevant for castling, pawn). */
    public boolean hasNeverMoved() {
        return moveCount == 0;
    }

    /** Material value in centipawns. */
    public int value() {
        return type.value();
    }

    @Override
    public String toString() {
        return color + " " + type;
    }
}
