package com.chess.application;

import com.chess.api.dto.GameDto;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
import com.chess.domain.rules.GameStateChecker;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
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
public class GameApplicationService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GameApplicationService.class);

    private final GameStore              store;
    private final GameRepository         gameRepository;
    private final GamePersistenceService persistenceService;
    /** Restoring reads lazy collections, so it needs a session of its own. */
    private final TransactionTemplate    readOnly;
    private final Object[] restoreLocks = newLocks(16);

    public GameApplicationService(GameStore store,
                                   GameRepository gameRepository,
                                   GamePersistenceService persistenceService,
                                   PlatformTransactionManager transactionManager) {
        this.store              = store;
        this.gameRepository     = gameRepository;
        this.persistenceService = persistenceService;
        this.readOnly           = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /** What the AI is told about the game when it is its turn: the position, how it was reached, the clocks. */
    private record AiTurn(Board position, Board start, List<String> moves, long whiteMs, long blackMs, long incrementMs) { }

    private static Object[] newLocks(int n) {
        Object[] locks = new Object[n];
        for (int i = 0; i < n; i++) locks[i] = new Object();
        return locks;
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

    /** Brings a stored game back into memory if it isn't there; used when the server starts. */
    public GameSession restore(String gameId) {
        return requireSession(gameId);
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

    /**
     * @param seat the colour the caller plays, or null when they play both (a local game);
     *             the move is refused unless it is that colour's turn
     */
    public GameStateResponse submitMove(String gameId, String uciMove, Color seat) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) throw new GameOverException(gameId);
            // Too late: the clock fell before this move arrived. The clock watcher ends the game.
            if (session.flaggedSide() != null) throw new GameOverException(gameId);

            Color active = session.board().activeColor();
            if (session.aiColor() != null && session.aiColor() == active)
                throw new NotYourTurnException(
                        "It is the AI's turn (" + active + "). Call /ai-move instead.");
            if (seat != null && seat != active)
                throw new NotYourTurnException("It is not your turn.");

            String colorPlayed = active.name().toLowerCase();
            Move move = parseMoveFromLegalList(session, uciMove);
            session.applyMove(move);

            GameStateResponse r = toResponse(session);
            persistenceService.persistMove(UUID.fromString(gameId), uciMove, r.fen(), colorPlayed);
            finaliseIfTerminal(gameId, r.status(), r.activeColor());
            return r;
        });
    }

    // ----------------------------------------------------------------
    // USE CASE 5 — Ask the AI to play
    // ----------------------------------------------------------------

    public GameStateResponse playAiMove(String gameId) {
        GameSession session = requireSession(gameId);
        // One search per game at a time: a second request would only race the first
        if (!session.tryBeginAiSearch()) throw new AiBusyException(gameId);
        try {
            // Take a snapshot under the lock, but search without it, so a resignation or an
            // undo is never held up behind the AI
            AiTurn turn = underTurnLock(session, () -> {
                if (session.isOver()) throw new GameOverException(gameId);
                if (session.flaggedSide() != null) throw new GameOverException(gameId);
                Color active = session.board().activeColor();
                if (session.aiColor() == null || session.aiColor() != active)
                    throw new NotYourTurnException(
                            "It is the human's turn (" + active + "). Call /moves instead.");
                return new AiTurn(session.board(), session.initialBoard(), List.copyOf(session.moveHistory()),
                        session.whiteTimeRemainingMs(), session.blackTimeRemainingMs(), session.clockIncrementMs());
            });
            Board position = turn.position();

            Move move = session.aiPlayer().chooseMove(position, turn.start(), turn.moves(),
                    turn.whiteMs(), turn.blackMs(), turn.incrementMs());

            return underTurnLock(session, () -> {
                if (session.isOver()) throw new GameOverException(gameId);
                // Boards are immutable, so a different one means the game moved on (an undo,
                // say) while the AI was thinking: its move no longer fits
                if (session.board() != position)
                    throw new IllegalStateException("The position changed while the AI was thinking");

                String colorPlayed = position.activeColor().name().toLowerCase();
                session.applyMove(move);

                GameStateResponse r = toResponse(session);
                if (r.lastMove() != null)
                    persistenceService.persistMove(UUID.fromString(gameId), r.lastMove(), r.fen(), colorPlayed);
                finaliseIfTerminal(gameId, r.status(), r.activeColor());
                return r;
            });
        } finally {
            session.endAiSearch();
        }
    }

    // ----------------------------------------------------------------
    // USE CASE — Undo the last move
    // ----------------------------------------------------------------

    /**
     * Takes back the last {@code plies} moves (1 or 2) as one step. Two plies is how a player
     * takes back their own move against the AI: the AI's reply goes with it, and it is their
     * turn again. Either all of them are undone or none is.
     */
    public GameStateResponse undoLastMove(String gameId, int plies) {
        if (plies < 1 || plies > 2) throw new IllegalArgumentException("plies must be 1 or 2");
        GameSession session = requireSession(gameId);
        stopAiSearch(session);
        return underTurnLock(session, () -> {
            // Undoing past the end would reopen a finished (and already scored) game
            if (session.isOver()) throw new GameOverException(gameId);
            if (session.moveHistory().size() < plies) throw new IllegalStateException("No moves to undo");
            for (int i = 0; i < plies; i++) session.undoLastMove();
            GameStateResponse r = toResponse(session);
            persistenceService.undoLastMoves(UUID.fromString(gameId),
                    r.fen(), r.activeColor().toLowerCase(), plies);
            return r;
        });
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
        stopAiSearch(session);
        return underTurnLock(session, () -> {
            if (session.isOver()) throw new GameOverException(gameId);
            session.resign(side != null ? side : session.board().activeColor());
            GameStateResponse r = toResponse(session);
            finaliseIfTerminal(gameId, r.status(), r.activeColor());
            return r;
        });
    }

    /**
     * {@code side} offers a draw ({@code null}: a local game, where the one person
     * at the board is both players, so it is simply agreed). If the opponent has an
     * offer on the table, this accepts it. The AI always declines.
     */
    public GameStateResponse offerDraw(String gameId, Color side) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) throw new GameOverException(gameId);
            if (session.aiColor() != null) {
                throw new DrawDeclinedException("The AI declined the draw offer.");
            }
            if (side == null || session.drawOfferedBy() == side.opposite()) {
                return agreeDraw(gameId, session);
            }
            session.offerDraw(side);
            return toResponse(session);
        });
    }

    /** {@code side} accepts the draw the opponent offered. */
    public GameStateResponse acceptDraw(String gameId, Color side) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) throw new GameOverException(gameId);
            requireOfferFromOpponent(session, side);
            return agreeDraw(gameId, session);
        });
    }

    /** {@code side} turns down the draw the opponent offered. */
    public GameStateResponse declineDraw(String gameId, Color side) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) throw new GameOverException(gameId);
            requireOfferFromOpponent(session, side);
            session.clearDrawOffer();
            return toResponse(session);
        });
    }

    private static void requireOfferFromOpponent(GameSession session, Color side) {
        if (side == null || session.drawOfferedBy() != side.opposite())
            throw new IllegalStateException("There is no draw offer to answer.");
    }

    private GameStateResponse agreeDraw(String gameId, GameSession session) {
        session.agreeDraw();
        GameStateResponse r = toResponse(session);
        finaliseIfTerminal(gameId, r.status(), r.activeColor());
        return r;
    }

    // ----------------------------------------------------------------
    // USE CASE — A player left
    // ----------------------------------------------------------------

    /** {@code loser} left the game for good. Empty if the game was already over. */
    public Optional<GameStateResponse> abandon(String gameId, Color loser) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) return Optional.<GameStateResponse>empty();
            session.abandon(loser);
            GameStateResponse r = toResponse(session);
            finaliseIfTerminal(gameId, r.status(), r.activeColor());
            return Optional.of(r);
        });
    }

    /** Calls the game off: no result and no rating change. Empty if it was already over. */
    public Optional<GameStateResponse> abort(String gameId) {
        GameSession session = requireSession(gameId);
        return underTurnLock(session, () -> {
            if (session.isOver()) return Optional.<GameStateResponse>empty();
            session.abort();
            GameStateResponse r = toResponse(session);
            finaliseIfTerminal(gameId, r.status(), r.activeColor());
            return Optional.of(r);
        });
    }

    /**
     * Calls off every online game in which a side has taken longer than {@code limit} to
     * make its first move, and returns those games so the caller can tell the players.
     * Such a game is not scored: nobody lost a game that never started.
     */
    public List<GameStateResponse> abortStalledOnlineGames(java.time.Duration limit) {
        List<GameStateResponse> ended = new java.util.ArrayList<>();
        for (GameSession session : store.all()) {
            if (!session.isOnline() || !session.firstMoveOverdue(limit)) continue;
            try {
                underTurnLock(session, () -> {
                    // a move may have arrived while we waited for the lock
                    if (!session.firstMoveOverdue(limit)) return null;
                    session.abort();
                    GameStateResponse r = toResponse(session);
                    finaliseIfTerminal(session.id(), r.status(), r.activeColor());
                    ended.add(r);
                    return null;
                });
            } catch (RuntimeException e) {
                log.warn("Could not call off stalled game {}; will retry", session.id(), e);
            }
        }
        return ended;
    }

    /** After this many failed attempts a result is given up on, so a broken row can't be retried forever. */
    private static final int MAX_SAVE_ATTEMPTS = 10;

    /**
     * Stores the result of every game that ended in memory but could not be written to the
     * database (it was down for a moment, say), and returns those games so the players can be
     * told: their original announcement never went out.
     */
    public List<GameStateResponse> saveUnsavedResults() {
        List<GameStateResponse> saved = new java.util.ArrayList<>();
        for (GameSession session : store.all()) {
            if (!session.isOver() || session.resultSaved()) continue;
            underTurnLock(session, () -> {
                if (session.resultSaved()) return null;
                GameStateResponse r = toResponse(session);
                try {
                    finaliseIfTerminal(session.id(), r.status(), r.activeColor());
                    saved.add(r);
                } catch (RuntimeException e) {
                    if (session.recordSaveFailure() >= MAX_SAVE_ATTEMPTS) {
                        session.markResultSaved();
                        log.error("Giving up saving the result of game {}", session.id(), e);
                    } else {
                        log.warn("Could not save the result of game {}; will retry", session.id(), e);
                    }
                }
                return null;
            });
        }
        return saved;
    }

    // ----------------------------------------------------------------
    // USE CASE — Time runs out
    // ----------------------------------------------------------------

    /**
     * Ends every game whose side to move has run out of time, and returns the
     * finished games so the caller can tell the players.
     */
    public List<GameStateResponse> expireFlaggedGames() {
        List<GameStateResponse> ended = new java.util.ArrayList<>();
        for (GameSession session : store.all()) {
            if (session.flaggedSide() == null) continue; // cheap look first, without the lock
            try {
                underTurnLock(session, () -> {
                    // a move may have beaten the clock while we waited for the lock
                    Color flagged = session.flaggedSide();
                    if (flagged == null) return null;
                    session.flag(flagged);
                    GameStateResponse r = toResponse(session);
                    // The game is over in memory from here on. If saving it fails, the
                    // result is saved later by saveUnsavedResults(), and the players told then.
                    finaliseIfTerminal(session.id(), r.status(), r.activeColor());
                    ended.add(r);
                    return null;
                });
            } catch (RuntimeException e) {
                log.warn("Could not end game {} on time; will retry", session.id(), e);
            }
        }
        return ended;
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
        GameSession live = store.findById(gameId).orElse(null);
        if (live != null) return live;
        // One restore per game at a time: two requests must not each build a session,
        // or one of them would go on playing a session the store no longer holds
        synchronized (restoreLocks[Math.floorMod(gameId.hashCode(), restoreLocks.length)]) {
            return store.findById(gameId)
                    .orElseGet(() -> readOnly.execute(status -> restoreGameFromDatabase(gameId)));
        }
    }

    /** The AI's result would be thrown away (the game ended or the position changed): let it stop thinking. */
    private static void stopAiSearch(GameSession session) {
        if (session.aiPlayer() != null) session.aiPlayer().stop();
    }

    /** Runs {@code action} holding the game's turn lock, so requests for one game take turns. */
    private static <T> T underTurnLock(GameSession session, java.util.function.Supplier<T> action) {
        session.turnLock().lock();
        try {
            return action.get();
        } finally {
            session.turnLock().unlock();
        }
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
        int aiDifficulty = GameDto.CreateGameRequest.DEFAULT_AI_DIFFICULTY;
        if (Boolean.TRUE.equals(dbGame.isWhiteIsAi())) {
            aiColor = Color.WHITE;
            if (dbGame.getWhiteAiDifficulty() != null)
                aiDifficulty = dbGame.getWhiteAiDifficulty();
        } else if (Boolean.TRUE.equals(dbGame.isBlackIsAi())) {
            aiColor = Color.BLACK;
            if (dbGame.getBlackAiDifficulty() != null)
                aiDifficulty = dbGame.getBlackAiDifficulty();
        }

        // Replaying the stored moves brings back the move list and the repetition
        // history; a game whose start was never recorded can't be replayed from a custom
        // FEN and loads as a bare position
        GameSession session = replayStoredMoves(gameId, dbGame, aiColor, aiDifficulty);
        if (session == null) session = new GameSession(gameId, board, aiColor, aiDifficulty);
        session.initClock(dbGame.getWhiteTimeRemainingMs(), dbGame.getBlackTimeRemainingMs(),
                dbGame.getTimeControlIncrementMs(), dbGame.getTurnStartedAt());
        // Known from the start, so the clock watcher and the first-move timer can tell it is
        // an online game without waiting for a request to fill this in
        session.setMetadata(persistenceService.metadataFor(dbGame));
        if (dbGame.getStatus() == GameStatus.aborted)
            session.restoreOutcome(GameStateChecker.State.ABORTED);
        else if (dbGame.getStatus() == GameStatus.finished)
            session.restoreOutcome(storedOutcome(dbGame));
        store.save(session);
        return session;
    }

    /**
     * A session rebuilt by playing the stored moves from the position the game began
     * from, or null when they don't lead to the stored position.
     */
    private static GameSession replayStoredMoves(String gameId, GameEntity dbGame,
                                                  Color aiColor, int aiDifficulty) {
        if (dbGame.getMoves().isEmpty()) return null;
        Board start = dbGame.getStartingFen() == null
                ? BoardFactory.startingPosition()
                : FenParser.parse(dbGame.getStartingFen());
        var session = new GameSession(gameId, start, aiColor, aiDifficulty);
        try {
            for (var stored : dbGame.getMoves()) {
                String uci = stored.getAlgebraicNotation(); // stored as plain UCI
                Move move = MoveGenerator.generateLegalMoves(session.board()).stream()
                        .filter(m -> m.toUci().equals(uci))
                        .findFirst().orElse(null);
                if (move == null) return null;
                session.applyMove(move);
            }
        } catch (IllegalStateException e) { // a move after the game ended
            return null;
        }
        // Same pieces, side to move and castling rights as the stored position?
        String[] replayed = FenSerializer.toFen(session.board()).split(" ");
        String[] stored   = dbGame.getCurrentFen().split(" ");
        for (int i = 0; i < 3; i++) if (!replayed[i].equals(stored[i])) return null;
        return session;
    }

    /** The engine state for how a stored game ended, or null if the engine has none for it. */
    private static GameStateChecker.State storedOutcome(GameEntity game) {
        if (game.getResultReason() == null) return null;
        boolean whiteWon = game.getResultWinner() == PlayerSide.white;
        return switch (game.getResultReason()) {
            case checkmate       -> GameStateChecker.State.CHECKMATE;
            case stalemate       -> GameStateChecker.State.STALEMATE;
            case fifty_move_rule -> GameStateChecker.State.DRAW_50_MOVE;
            case insufficient_material -> GameStateChecker.State.DRAW_INSUFFICIENT_MATERIAL;
            case threefold_repetition  -> GameStateChecker.State.DRAW_REPETITION;
            case draw_agreement  -> GameStateChecker.State.DRAW_AGREED;
            case abandonment     -> whiteWon ? GameStateChecker.State.BLACK_ABANDONED
                                             : GameStateChecker.State.WHITE_ABANDONED;
            case timeout         -> whiteWon ? GameStateChecker.State.BLACK_FLAGGED
                                             : GameStateChecker.State.WHITE_FLAGGED;
            case resignation     -> whiteWon ? GameStateChecker.State.BLACK_RESIGNED
                                             : GameStateChecker.State.WHITE_RESIGNED;
            default              -> null;
        };
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
            case "DRAW_INSUFFICIENT_MATERIAL" -> reason = "insufficient_material";
            case "DRAW_REPETITION"            -> reason = "threefold_repetition";
            case "WHITE_RESIGNED" -> { winner = "black"; reason = "resignation"; }
            case "BLACK_RESIGNED" -> { winner = "white"; reason = "resignation"; }
            case "WHITE_ABANDONED" -> { winner = "black"; reason = "abandonment"; }
            case "BLACK_ABANDONED" -> { winner = "white"; reason = "abandonment"; }
            case "WHITE_FLAGGED"  -> { winner = "black"; reason = "timeout"; }
            case "BLACK_FLAGGED"  -> { winner = "white"; reason = "timeout"; }
            case "DRAW_AGREED"    -> reason = "draw_agreement";
            case "ABORTED"        -> {
                persistenceService.abortGame(UUID.fromString(gameId));
                store.findById(gameId).ifPresent(GameSession::markResultSaved);
                return;
            }
            default               -> { return; }
        }
        persistenceService.finaliseGame(UUID.fromString(gameId), winner, reason);
        store.findById(gameId).ifPresent(GameSession::markResultSaved);
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
