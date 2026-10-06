package com.chess.infrastructure.api.exception;

import com.chess.domain.board.InvalidFenException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.NoSuchElementException;

/**
 * Converts all chess-specific exceptions to RFC-7807 ProblemDetail responses.
 *
 * Why ProblemDetail?
 *   • It is the Spring 6 standard (RFC 7807).
 *   • Clients get a consistent JSON envelope: type, title, status, detail.
 *   • No custom error DTO needed.
 *
 * Example response body:
 * {
 *   "type":   "https://chess-engine/errors/game-not-found",
 *   "title":  "Game Not Found",
 *   "status": 404,
 *   "detail": "Game not found: abc-123"
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex);
    }

    @ExceptionHandler(GameNotFoundException.class)
    ProblemDetail handleGameNotFound(GameNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "game-not-found", "Game Not Found", ex);
    }

    @ExceptionHandler(IllegalMoveException.class)
    ProblemDetail handleIllegalMove(IllegalMoveException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "illegal-move", "Illegal Move", ex);
    }

    @ExceptionHandler(GameOverException.class)
    ProblemDetail handleGameOver(GameOverException ex) {
        return problem(HttpStatus.CONFLICT, "game-over", "Game Already Over", ex);
    }

    @ExceptionHandler(NotYourTurnException.class)
    ProblemDetail handleNotYourTurn(NotYourTurnException ex) {
        return problem(HttpStatus.CONFLICT, "not-your-turn", "Not Your Turn", ex);
    }

    @ExceptionHandler(AiBusyException.class)
    ProblemDetail handleAiBusy(AiBusyException ex) {
        return problem(HttpStatus.CONFLICT, "ai-busy", "AI Is Thinking", ex);
    }

    @ExceptionHandler(DrawDeclinedException.class)
    ProblemDetail handleDrawDeclined(DrawDeclinedException ex) {
        return problem(HttpStatus.CONFLICT, "draw-declined", "Draw Declined", ex);
    }

    @ExceptionHandler(NotAPlayerException.class)
    ProblemDetail handleNotAPlayer(NotAPlayerException ex) {
        return problem(HttpStatus.FORBIDDEN, "not-a-player", "Not A Player", ex);
    }

    /** An action that doesn't fit the game's current state, e.g. undo with no moves played. */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "conflict", "Conflict", ex);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNoSuchElement(NoSuchElementException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex);
    }

    @ExceptionHandler(InvalidFenException.class)
    ProblemDetail handleInvalidFen(InvalidFenException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-fen", "Bad Request", ex);
    }

    /**
     * Handles @Valid / @Validated constraint violations on request bodies.
     * Overrides the Spring default to stay on ProblemDetail.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);

        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST,
            "validation-error", "Validation Failed", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    // ---- Helper -------------------------------------------------------

    private ProblemDetail problem(HttpStatus status, String errorCode,
                                   String title, Exception ex) {
        return problem(status, errorCode, title, safeDetail(ex, title));
    }

    /** Words that only an internal error message contains: class names, packages, lookups. */
    private static final java.util.regex.Pattern INTERNAL = java.util.regex.Pattern.compile(
        "com\\.chess|java\\.|jakarta\\.|org\\.(springframework|hibernate|postgresql)|No enum constant|\\.java:\\d+");

    /**
     * What to tell the client. Our own explanations ("There is no draw offer to answer") go
     * out as written; a message that shows class names or stack frames, or none at all,
     * becomes {@code fallback}: those are for the logs, not for whoever sent the request.
     */
    static String safeDetail(Exception ex, String fallback) {
        String message = ex.getMessage();
        if (message == null || message.isBlank() || INTERNAL.matcher(message).find()) return fallback;
        return message;
    }

    private ProblemDetail problem(HttpStatus status, String errorCode,
                                   String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://chess-engine/errors/" + errorCode));
        return pd;
    }
}
