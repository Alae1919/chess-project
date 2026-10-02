package com.chess.infrastructure.api.exception;

/** The opponent turned a draw offer down (the AI always does). */
public class DrawDeclinedException extends RuntimeException {
    public DrawDeclinedException(String message) {
        super(message);
    }
}
