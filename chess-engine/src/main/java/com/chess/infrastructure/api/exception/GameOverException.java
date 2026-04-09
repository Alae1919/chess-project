package com.chess.infrastructure.api.exception;

/** Thrown when an action is attempted on an already-finished game. */
public class GameOverException extends RuntimeException {
    public GameOverException(String gameId) {
        super("Game " + gameId + " is already over");
    }
}
