package com.chess.infrastructure.api.exception;

/** The AI is already working out its move for this game; a second request would only race it. */
public class AiBusyException extends RuntimeException {
    public AiBusyException(String gameId) {
        super("The AI is already thinking about game " + gameId);
    }
}
