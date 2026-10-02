package com.chess.infrastructure.api.exception;

/** Thrown when the caller is not one of the players of the game they are acting on. */
public class NotAPlayerException extends RuntimeException {
    public NotAPlayerException(String gameId) {
        super("You are not a player in game " + gameId);
    }
}
