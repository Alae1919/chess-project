package com.chess.infrastructure.api.exception;

/**
 * Thrown when a human submits a move but it is the AI's turn,
 * or the AI is asked to play but it is the human's turn.
 */
public class NotYourTurnException extends RuntimeException {
    public NotYourTurnException(String message) {
        super(message);
    }
}
