package com.chess.infrastructure.api.exception;

/** Thrown when a game ID does not exist in the store. */
public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String gameId) {
        super("Game not found: " + gameId);
    }
}
