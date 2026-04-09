package com.chess.infrastructure.api.exception;

/** Thrown when the submitted move is not in the legal move list. */
public class IllegalMoveException extends RuntimeException {
    public IllegalMoveException(String move) {
        super("Illegal move: " + move);
    }
}
