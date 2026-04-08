package com.chess.domain.model;

/**
 * Represents the color of a chess piece or player.
 * Replaces the original boolean primitive (NOIR=true, BLANC=false).
 */
public enum Color {
    WHITE, BLACK;

    /** Returns the opposite color. */
    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
