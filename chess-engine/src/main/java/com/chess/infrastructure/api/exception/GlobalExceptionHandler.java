package com.chess.infrastructure.api.exception;

import com.chess.domain.board.InvalidFenException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

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
        return problem(status, errorCode, title, ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String errorCode,
                                   String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("https://chess-engine/errors/" + errorCode));
        return pd;
    }
}
