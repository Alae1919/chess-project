package com.chess.domain.board;

/**
 * Thrown by FenParser when a FEN string is syntactically or semantically invalid.
 *
 * Using a dedicated exception (rather than IllegalArgumentException) lets callers
 * catch FEN problems specifically without catching unrelated programming errors.
 */
public final class InvalidFenException extends RuntimeException {

    public InvalidFenException(String message) {
        super(message);
    }

    public InvalidFenException(String message, Throwable cause) {
        super(message, cause);
    }
}
