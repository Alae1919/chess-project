package com.chess.application;

import com.chess.domain.board.BoardFactory;
import com.chess.domain.model.*;
import com.chess.domain.rules.*;
import com.chess.engine.player.AiPlayer;
import com.chess.engine.search.AlphaBetaSearch;
import com.chess.infrastructure.api.FenSerializer;
import com.chess.infrastructure.api.dto.*;
import com.chess.infrastructure.api.exception.*;
import com.chess.infrastructure.persistence.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API-facing use-case orchestrator.
 *
 * Each public method corresponds to one REST endpoint.
 * This class holds no chess rules — it delegates to the domain layer.
 * It does hold application logic: session lifecycle, AI triggering,
 * turn ownership, response mapping.
 */
@Service
public final class GameApplicationService {

    private final GameStore store;

    public GameApplicationService(GameStore store) {
        this.store = store;
    }

    // ----------------------------------------------------------------
    // USE CASE 1 — Create a game
    // ----------------------------------------------------------------

    /**
     * Creates a new game session and returns the initial state.
     *
     * @param request Parameters (FEN, aiColor, aiDepth).
     * @return The full initial game state.
     */
    public GameStateResponse createGame(CreateGameRequest request) {
        var board = (request.fen() == null || request.fen().isBlank())
            ? BoardFactory.startingPosition()
            : BoardFactory.fromFen(request.fen());

        Color aiColor = parseAiColor(request.aiColor());
        String id     = GameStore.newId();
        var session   = new GameSession(id, board, aiColor, request.aiDepth());
        store.save(session);

        return toResponse(session);
    }

    // ----------------------------------------------------------------
    // USE CASE 2 — Get game state
    // ----------------------------------------------------------------

    public GameStateResponse getGame(String gameId) {
        return toResponse(requireSession(gameId));
    }

    // ----------------------------------------------------------------
    // USE CASE 3 — Get legal moves
    // ----------------------------------------------------------------

    public LegalMovesResponse getLegalMoves(String gameId) {
        GameSession session = requireSession(gameId);
        List<String> moves  = legalMoveStrings(session);
        return new LegalMovesResponse(
            gameId,
            session.board().activeColor().name(),
            moves
        );
    }

    // ----------------------------------------------------------------
    // USE CASE 4 — Human submits a move
    // ----------------------------------------------------------------

    /**
     * Applies a human move and returns the updated game state.
     *
     * Validation order:
     *   1. Game exists
     *   2. Game is not over
     *   3. It is not the AI's turn
     *   4. The move is in the legal move list
     */
    public GameStateResponse submitMove(String gameId, String uciMove) {
        GameSession session = requireSession(gameId);

        if (session.isOver())
            throw new GameOverException(gameId);

        Color active = session.board().activeColor();
        if (session.aiColor() != null && session.aiColor() == active)
            throw new NotYourTurnException(
                "It is the AI's turn (" + active + "). Call /ai-move instead.");

        Move move = parseMoveFromLegalList(session, uciMove);
        session.applyMove(move);
        return toResponse(session);
    }

    // ----------------------------------------------------------------
    // USE CASE 5 — Ask the AI to play
    // ----------------------------------------------------------------

    /**
     * Lets the AI choose and apply a move, then returns the updated state.
     *
     * Validation order:
     *   1. Game exists
     *   2. Game is not over
     *   3. It is the AI's turn
     *   4. AI finds a move (always true if game is not over)
     */
    public GameStateResponse playAiMove(String gameId) {
        GameSession session = requireSession(gameId);

        if (session.isOver())
            throw new GameOverException(gameId);

        Color active = session.board().activeColor();
        if (session.aiColor() == null || session.aiColor() != active)
            throw new NotYourTurnException(
                "It is the human's turn (" + active + "). Call /moves instead.");

        AlphaBetaSearch search = new AlphaBetaSearch();
        Optional<Move> best = search.findBestMove(session.board(), session.aiDepth());

        // This should never be empty if isOver() returned false, but guard anyway
        Move move = best.orElseThrow(() ->
            new IllegalStateException("AI found no move in a non-terminal position"));

        session.applyMove(move);
        return toResponse(session);
    }

    // ----------------------------------------------------------------
    // USE CASE 6 — Abandon / delete a game
    // ----------------------------------------------------------------

    public void deleteGame(String gameId) {
        requireSession(gameId); // ensures it exists
        store.delete(gameId);
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private GameSession requireSession(String gameId) {
        return store.findById(gameId)
            .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    /**
     * Finds the Move object matching a UCI string in the legal move list.
     * Throws IllegalMoveException if the string matches no legal move.
     */
    private Move parseMoveFromLegalList(GameSession session, String uciMove) {
        String normalized = uciMove.toLowerCase().trim();
        return MoveGenerator.generateLegalMoves(session.board())
            .stream()
            .filter(m -> m.toString().equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalMoveException(uciMove));
    }

    private List<String> legalMoveStrings(GameSession session) {
        if (session.isOver()) return List.of();
        return MoveGenerator.generateLegalMoves(session.board())
            .stream()
            .map(Move::toString)
            .sorted()                    // deterministic order
            .collect(Collectors.toList());
    }

    /** Maps a GameSession to the full API response. */
    private GameStateResponse toResponse(GameSession session) {
        return new GameStateResponse(
            session.id(),
            FenSerializer.toFen(session.board()),
            session.board().activeColor().name(),
            session.state().name(),
            session.lastMove(),
            session.moveHistory(),
            legalMoveStrings(session)
        );
    }

    /** Parses "WHITE" / "BLACK" / "NONE" → Color or null. */
    private Color parseAiColor(String s) {
        return switch (s.toUpperCase()) {
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "NONE"  -> null;
            default -> throw new IllegalArgumentException(
                "aiColor must be WHITE, BLACK, or NONE");
        };
    }
}
