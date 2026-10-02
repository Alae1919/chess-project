package com.chess.application;

import com.chess.domain.board.Board;
import com.chess.domain.board.BoardFactory;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.*;
import com.chess.domain.rules.*;
import com.chess.engine.player.AiPlayer;
import com.chess.infrastructure.api.FenSerializer;
import com.chess.infrastructure.api.dto.*;
import com.chess.infrastructure.api.exception.*;
import com.chess.infrastructure.persistence.*;
import com.chess.persistence.entity.GameEntity;
import com.chess.persistence.repository.GameRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API-facing use-case orchestrator.
 *
 * Responsibilities:
 *  - Drive the in-memory chess engine (GameStore / GameSession)
 *  - Delegate DB persistence to GamePersistenceService after every state change
 *  - Restore sessions from DB when they are no longer in memory
 *
 * Chess rules live exclusively in the domain layer; HTTP concerns stay in the controller.
 */
@Service
public final class GameApplicationService {

    private final GameStore              store;
    private final GameRepository         gameRepository;
    private final GamePersistenceService persistenceService;

    public GameApplicationService(GameStore store,
                                   GameRepository gameRepository,
                                   GamePersistenceService persistenceService) {
        this.store              = store;
        this.gameRepository     = gameRepository;
        this.persistenceService = persistenceService;
    }

    // ----------------------------------------------------------------
    // USE CASE 1 — Create a game
    // ----------------------------------------------------------------

    public GameStateResponse createGame(CreateGameRequest request) {
        var board = (request.fen() == null || request.fen().isBlank())
                ? BoardFactory.startingPosition()
                : BoardFactory.fromFen(request.fen());

        Color aiColor = parseAiColor(request.aiColor());
        String id = GameStore.newId();
        var session = new GameSession(id, board, aiColor, request.aiDepth());
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
        return new LegalMovesResponse(gameId, session.board().activeColor().name(),
                legalMoveStrings(session));
    }

    // ----------------------------------------------------------------
    // USE CASE 4 — Human submits a move
    // ----------------------------------------------------------------

    public GameStateResponse submitMove(String gameId, String uciMove) {
        GameSession session = requireSession(gameId);

        if (session.isOver()) throw new GameOverException(gameId);

        Color active = session.board().activeColor();
        if (session.aiColor() != null && session.aiColor() == active)
            throw new NotYourTurnException(
                    "It is the AI's turn (" + active + "). Call /ai-move instead.");

        String colorPlayed = active.name().toLowerCase();
        Move move = parseMoveFromLegalList(session, uciMove);
        session.applyMove(move);

        GameStateResponse r = toResponse(session);
        persistenceService.persistMove(UUID.fromString(gameId), uciMove,
                r.fen(), r.moveHistory().size(), colorPlayed);
        finaliseIfTerminal(gameId, r.status(), r.activeColor());
        return r;
    }

    // ----------------------------------------------------------------
    // USE CASE 5 — Ask the AI to play
    // ----------------------------------------------------------------

    public GameStateResponse playAiMove(String gameId) {
        GameSession session = requireSession(gameId);

        if (session.isOver()) throw new GameOverException(gameId);

        Color active = session.board().activeColor();
        if (session.aiColor() == null || session.aiColor() != active)
            throw new NotYourTurnException(
                    "It is the human's turn (" + active + "). Call /moves instead.");

        String colorPlayed = active.name().toLowerCase();
        Move move = session.aiPlayer().chooseMove(session.board());

        session.applyMove(move);

        GameStateResponse r = toResponse(session);
        if (r.lastMove() != null)
            persistenceService.persistMove(UUID.fromString(gameId), r.lastMove(),
                    r.fen(), r.moveHistory().size(), colorPlayed);
        finaliseIfTerminal(gameId, r.status(), r.activeColor());
        return r;
    }

    // ----------------------------------------------------------------
    // USE CASE — Undo the last move
    // ----------------------------------------------------------------

    public GameStateResponse undoLastMove(String gameId) {
        GameSession session = requireSession(gameId);
        session.undoLastMove();
        GameStateResponse r = toResponse(session);
        persistenceService.undoLastMove(UUID.fromString(gameId),
                r.fen(), r.activeColor().toLowerCase());
        return r;
    }

    // ----------------------------------------------------------------
    // USE CASE 6 — Abandon / delete a game
    // ----------------------------------------------------------------

    public void deleteGame(String gameId) {
        requireSession(gameId);
        store.delete(gameId);
        gameRepository.deleteById(UUID.fromString(gameId));
    }

    // ----------------------------------------------------------------
    // USE CASE 7 — Resign / draw
    // ----------------------------------------------------------------

    /**
     * {@code side} resigns. Null means the side to move: in a local game one
     * person plays both colours.
     */
    public GameStateResponse resign(String gameId, Color side) {
        GameSession session = requireSession(gameId);
        if (session.isOver()) throw new GameOverException(gameId);
        session.resign(side != null ? side : session.board().activeColor());
        GameStateResponse r = toResponse(session);
        finaliseIfTerminal(gameId, r.status(), r.activeColor());
        return r;
    }

    public GameStateResponse offerDraw(String gameId, String username) {
        GameSession session = requireSession(gameId);
        if (session.isOver()) throw new GameOverException(gameId);
        session.agreeDraw();
        GameStateResponse r = toResponse(session);
        finaliseIfTerminal(gameId, r.status(), r.activeColor());
        return r;
    }

    // ----------------------------------------------------------------
    // USE CASE — Session metadata
    // ----------------------------------------------------------------

    public Color getAiColor(String gameId) {
        return requireSession(gameId).aiColor();
    }

    public int getMoveCount(String gameId) {
        return requireSession(gameId).moveHistory().size();
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private GameSession requireSession(String gameId) {
        return store.findById(gameId)
                .orElseGet(() -> restoreGameFromDatabase(gameId));
    }

    private GameSession restoreGameFromDatabase(String gameId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(gameId);
        } catch (IllegalArgumentException e) {
            throw new GameNotFoundException("Game not found: " + gameId);
        }
        GameEntity dbGame = gameRepository.findById(uuid)
                .orElseThrow(() -> new GameNotFoundException("Game not found: " + gameId));

        Board board = FenParser.parse(dbGame.getCurrentFen());

        Color aiColor = null;
        int aiDifficulty = 1;
        if (Boolean.TRUE.equals(dbGame.isWhiteIsAi())) {
            aiColor = Color.WHITE;
            if (dbGame.getWhiteAiDifficulty() != null)
                aiDifficulty = dbGame.getWhiteAiDifficulty();
        } else if (Boolean.TRUE.equals(dbGame.isBlackIsAi())) {
            aiColor = Color.BLACK;
            if (dbGame.getBlackAiDifficulty() != null)
                aiDifficulty = dbGame.getBlackAiDifficulty();
        }

        GameSession session = new GameSession(gameId, board, aiColor, aiDifficulty);
        session.initClock(dbGame.getWhiteTimeRemainingMs(), dbGame.getBlackTimeRemainingMs());
        store.save(session);
        return session;
    }

    /**
     * Maps terminal engine status → winner/reason and delegates to persistenceService.
     * No-op for non-terminal statuses (ONGOING, CHECK).
     */
    private void finaliseIfTerminal(String gameId, String status, String activeColor) {
        String winner = null, reason = null;
        switch (status) {
            case "CHECKMATE"      -> { winner = activeColor.equalsIgnoreCase("WHITE") ? "black" : "white";
                                       reason = "checkmate"; }
            case "STALEMATE"      -> reason = "stalemate";
            case "DRAW_50_MOVE"   -> reason = "fifty_move_rule";
            case "WHITE_RESIGNED" -> { winner = "black"; reason = "resignation"; }
            case "BLACK_RESIGNED" -> { winner = "white"; reason = "resignation"; }
            case "DRAW_AGREED"    -> reason = "draw_agreement";
            default               -> { return; }
        }
        persistenceService.finaliseGame(UUID.fromString(gameId), winner, reason);
    }

    private Move parseMoveFromLegalList(GameSession session, String uciMove) {
        String normalized = uciMove.toLowerCase().trim();
        return MoveGenerator.generateLegalMoves(session.board())
                .stream()
                .filter(m -> m.toUci().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalMoveException(uciMove));
    }

    private List<String> legalMoveStrings(GameSession session) {
        if (session.isOver()) return List.of();
        return MoveGenerator.generateLegalMoves(session.board())
                .stream()
                .map(Move::toUci)
                .sorted()
                .collect(Collectors.toList());
    }

    private GameStateResponse toResponse(GameSession session) {
        return new GameStateResponse(
                session.id(),
                FenSerializer.toFen(session.board()),
                session.board().activeColor().name(),
                session.state().name(),
                session.lastMove(),
                session.moveHistory(),
                session.sanHistory(),
                legalMoveStrings(session));
    }

    private Color parseAiColor(String s) {
        return switch (s.toUpperCase()) {
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "NONE"  -> null;
            default      -> throw new IllegalArgumentException(
                    "aiColor must be WHITE, BLACK, or NONE");
        };
    }
}
