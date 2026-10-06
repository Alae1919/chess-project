package com.chess.application;

import com.chess.api.dto.GameDto;
import com.chess.api.dto.MatchHistoryDto;
import com.chess.infrastructure.api.dto.GameStateResponse;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Square;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.entity.DatabaseEnums.PieceKind;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.DatabaseEnums.GameEndReason;
import com.chess.persistence.entity.*;
import com.chess.infrastructure.persistence.GameMetadata;
import com.chess.infrastructure.persistence.GameStore;
import com.chess.persistence.repository.EloHistoryRepository;
import com.chess.persistence.repository.GameRepository;
import com.chess.persistence.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class GamePersistenceService {

    private final GameRepository       gameRepo;
    private final UserRepository       userRepo;
    private final EloHistoryRepository eloRepo;
    private final GameStore            gameStore;

    public GamePersistenceService(GameRepository gameRepo,
                                   UserRepository userRepo,
                                   EloHistoryRepository eloRepo,
                                   GameStore gameStore) {
        this.gameRepo   = gameRepo;
        this.userRepo   = userRepo;
        this.eloRepo    = eloRepo;
        this.gameStore  = gameStore;
    }

    // ── Game creation ─────────────────────────────────────────────────────────

    /**
     * Creates a DB record for an online game that was paired by matchmaking or friend invite.
     * Both players are known upfront; color assignment is done by the caller.
     */
    @Transactional
    public void persistNewOnlineGame(String gameId,
                                     UUID whiteUserId, String whiteUsername, Integer whiteElo,
                                     UUID blackUserId, String blackUsername, Integer blackElo,
                                     TimeControlKind tcType, long initialMs, long incrementMs) {
        var entity = new GameEntity();
        entity.setId(UUID.fromString(gameId));
        entity.setMode(GameMode.online);
        entity.setStatus(GameStatus.active);
        entity.setCurrentTurn(PlayerSide.white);

        entity.setWhiteUserId(whiteUserId);
        entity.setWhiteUsername(whiteUsername);
        entity.setWhiteElo(whiteElo);

        entity.setBlackUserId(blackUserId);
        entity.setBlackUsername(blackUsername);
        entity.setBlackElo(blackElo);

        entity.setTimeControlType(tcType);
        entity.setTimeControlInitialMs(initialMs);
        entity.setTimeControlIncrementMs(incrementMs);
        entity.setWhiteTimeRemainingMs(initialMs);
        entity.setBlackTimeRemainingMs(initialMs);

        gameRepo.save(entity);

        gameStore.findById(gameId).ifPresent(s -> {
            s.setMetadata(buildMetadata(entity));
            s.initClock(entity.getWhiteTimeRemainingMs(), entity.getBlackTimeRemainingMs(),
                        entity.getTimeControlIncrementMs());
        });
    }

    /**
     * Creates a DB record for a newly started game.
     * Called by GameApplicationService.createGame() after the in-memory session is ready.
     */
    @Transactional
    public void persistNewGame(String gameId, GameDto.CreateGameRequest req,
                               UUID userId, String username) {
        var entity = new GameEntity();
        entity.setId(UUID.fromString(gameId));
        entity.setMode(GameMode.valueOf(req.mode()));
        entity.setStatus(GameStatus.active);
        entity.setCurrentTurn(PlayerSide.white);

        boolean userIsWhite = !"black".equalsIgnoreCase(req.playerColor());
        boolean isAiMode    = "ai".equalsIgnoreCase(req.mode());

        if (userIsWhite) {
            entity.setWhiteUserId(userId);
            entity.setWhiteUsername(username);
            if (isAiMode) {
                entity.setBlackIsAi(true);
                entity.setBlackUsername("AI");
                entity.setBlackAiDifficulty(req.aiDifficulty());
            } else {
                entity.setBlackUserId(userId);
                entity.setBlackUsername(username);
            }
        } else {
            entity.setBlackUserId(userId);
            entity.setBlackUsername(username);
            if (isAiMode) {
                entity.setWhiteIsAi(true);
                entity.setWhiteUsername("AI");
                entity.setWhiteAiDifficulty(req.aiDifficulty());
            } else {
                entity.setWhiteUserId(userId);
                entity.setWhiteUsername(username);
            }
        }

        var tc = req.timeControl();
        entity.setTimeControlType(TimeControlKind.valueOf(tc.type()));
        entity.setTimeControlInitialMs(tc.initialMs());
        entity.setTimeControlIncrementMs(tc.incrementMs());
        entity.setWhiteTimeRemainingMs(tc.initialMs());
        entity.setBlackTimeRemainingMs(tc.initialMs());

        if (req.fen() != null && !req.fen().isBlank()) {
            entity.setCurrentFen(req.fen());
            entity.setStartingFen(req.fen());
        }

        gameRepo.save(entity);

        // Cache metadata and initialise clock on the in-memory session
        gameStore.findById(gameId).ifPresent(s -> {
            s.setMetadata(buildMetadata(entity));
            s.initClock(entity.getWhiteTimeRemainingMs(), entity.getBlackTimeRemainingMs(),
                        entity.getTimeControlIncrementMs());
        });
    }

    // ── Move persistence ──────────────────────────────────────────────────────

    /**
     * Appends a move to the DB and updates all FEN-derived columns.
     * Called by GameApplicationService after every successful submitMove / playAiMove.
     *
     * @param uciMove     UCI string as returned by Move.toUci(), e.g. "e2e4" or "e7e8q"
     * @param newFen      Full FEN string after the move was applied
     * @param colorPlayed "white" or "black" — the side that just moved
     */
    @Transactional
    public void persistMove(UUID dbGameId, String uciMove, String newFen, String colorPlayed) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        // 1-based half-move count, taken from what is stored: a session rebuilt from a bare
        // position has forgotten the earlier moves
        int moveNumber = game.getMoves().size() + 1;

        String norm = uciMove.toLowerCase();
        int fromFile = norm.charAt(0) - 'a', fromRank = norm.charAt(1) - '1';
        int toFile   = norm.charAt(2) - 'a', toRank   = norm.charAt(3) - '1';

        // En passant: check pre-move FEN's ep-target field against destination
        String[] preParts = game.getCurrentFen().split(" ");
        String epTarget = preParts.length >= 4 ? preParts[3] : "-";
        boolean isEnPassant = !epTarget.equals("-")
            && (epTarget.charAt(0) - 'a') == toFile
            && (epTarget.charAt(1) - '1') == toRank;

        // Update FEN and all derived columns
        game.setCurrentFen(newFen);
        var played = PlayerSide.valueOf(colorPlayed.toLowerCase());
        game.setCurrentTurn(played == PlayerSide.white ? PlayerSide.black : PlayerSide.white);
        String[] newParts = newFen.split(" ");
        if (newParts.length >= 3) {
            String c = newParts[2];
            game.setWhiteKingsideCastle(c.contains("K"));
            game.setWhiteQueensideCastle(c.contains("Q"));
            game.setBlackKingsideCastle(c.contains("k"));
            game.setBlackQueensideCastle(c.contains("q"));
        }
        if (newParts.length >= 4 && !newParts[3].equals("-")) {
            game.setEnPassantCol(newParts[3].charAt(0) - 'a');
            game.setEnPassantRow(newParts[3].charAt(1) - '1');
        } else {
            game.setEnPassantCol(null);
            game.setEnPassantRow(null);
        }
        if (newParts.length >= 5) game.setHalfMoveClock(Integer.parseInt(newParts[4]));
        if (newParts.length >= 6) game.setFullMoveNumber(Integer.parseInt(newParts[5]));

        // Promotion piece (5th char of normalised UCI)
        PieceKind promotionPiece = null;
        if (norm.length() >= 5) {
            promotionPiece = switch (norm.charAt(4)) {
                case 'q' -> PieceKind.queen;
                case 'r' -> PieceKind.rook;
                case 'b' -> PieceKind.bishop;
                default  -> PieceKind.knight;
            };
        }

        var toSquare = new Square(toFile, toRank);
        PieceKind pieceKind = promotionPiece != null
            ? promotionPiece
            : PieceKind.valueOf(extractPieceType(newFen, toSquare));

        boolean isCastling = pieceKind == PieceKind.king && Math.abs(toFile - fromFile) == 2;

        var moveEntity = new GameMoveEntity();
        moveEntity.setGame(game);
        moveEntity.setMoveNumber(moveNumber);
        moveEntity.setColor(played);
        moveEntity.setFromRow(fromRank);
        moveEntity.setFromCol(fromFile);
        moveEntity.setToRow(toRank);
        moveEntity.setToCol(toFile);
        moveEntity.setPieceType(pieceKind);
        moveEntity.setPieceColor(played);
        moveEntity.setAlgebraicNotation(norm);
        moveEntity.setEnPassant(isEnPassant);
        if (isCastling) moveEntity.setIsCastling(toFile > fromFile ? "kingside" : "queenside");
        if (promotionPiece != null) moveEntity.setPromotion(promotionPiece);

        // Persist the live clock values from the in-memory session
        gameStore.findById(dbGameId.toString()).ifPresent(s -> {
            game.setWhiteTimeRemainingMs(s.whiteTimeRemainingMs());
            game.setBlackTimeRemainingMs(s.blackTimeRemainingMs());
        });

        game.getMoves().add(moveEntity);
        gameRepo.save(game);
    }

    /**
     * Reverts the last move in the DB.
     * Called by GameApplicationService after undoLastMove() succeeds.
     *
     * @param previousFen   FEN after undo (the board state before the undone move)
     * @param previousColor Active color after undo (lowercase, e.g. "white")
     */
    @Transactional
    public void undoLastMove(UUID dbGameId, String previousFen, String previousColor) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        game.setCurrentFen(previousFen);
        game.setCurrentTurn(PlayerSide.valueOf(previousColor.toLowerCase()));
        String[] parts = previousFen.split(" ");
        if (parts.length >= 5) game.setHalfMoveClock(Integer.parseInt(parts[4]));
        if (parts.length >= 6) game.setFullMoveNumber(Integer.parseInt(parts[5]));
        var moves = game.getMoves();
        if (!moves.isEmpty()) moves.remove(moves.size() - 1);
        gameRepo.save(game);
    }

    // ── Game finalisation ─────────────────────────────────────────────────────

    /**
     * Marks a game as finished, records ELO changes, updates user stats.
     * Called by GameApplicationService when a terminal state is detected.
     */
    @Transactional
    public void finaliseGame(UUID dbGameId, String winner, String reason) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        // A game finishes once: never count its result in the players' stats twice
        if (game.getStatus() == GameStatus.finished) return;
        game.setStatus(GameStatus.finished);
        game.setResultWinner(winner != null ? PlayerSide.valueOf(winner.toLowerCase()) : null);
        game.setResultReason(reason != null ? GameEndReason.valueOf(reason.toLowerCase()) : null);
        gameRepo.save(game);
        updateUserStats(game, winner);
    }

    /** The game was called off: it ends with no result, and nobody's rating or stats change. */
    @Transactional
    public void abortGame(UUID dbGameId) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        if (game.getStatus() == GameStatus.finished || game.getStatus() == GameStatus.aborted) return;
        game.setStatus(GameStatus.aborted);
        gameRepo.save(game);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<MatchHistoryDto.MatchHistory> getMatchHistory(UUID userId, int page, int size) {
        Page<GameEntity> games = gameRepo.findMatchHistoryForUser(userId, PageRequest.of(page, size));
        // one query for the whole page, not one per game
        Map<UUID, Long> moveCounts = new HashMap<>();
        if (games.hasContent()) {
            for (Object[] row : gameRepo.countMovesByGameIds(
                    games.getContent().stream().map(GameEntity::getId).toList())) {
                moveCounts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return games.map(g -> toMatchHistoryDto(g, userId, moveCounts.getOrDefault(g.getId(), 0L).intValue()));
    }

    public List<GameDto.SavedGame> getSavedGames(UUID userId) {
        return gameRepo.findActiveGamesForUser(userId)
            .stream().map(g -> toSavedGameDto(g, userId)).toList();
    }

    /** Returns a SavedGame DTO for a game that is already persisted (read-only). */
    public GameDto.SavedGame saveCurrentGame(String engineGameId, UUID userId) {
        var game = gameRepo.findById(UUID.fromString(engineGameId)).orElseThrow();
        return toSavedGameDto(game, userId);
    }

    @Transactional
    public void deleteSavedGame(UUID gameId, UUID userId) {
        var game = gameRepo.findById(gameId)
            .orElseThrow(() -> new EntityNotFoundException("Game not found"));
        if (!userId.equals(game.getWhiteUserId()) && !userId.equals(game.getBlackUserId()))
            throw new IllegalArgumentException("Access denied");
        // An online game belongs to both players: one of them can't wipe it, and it
        // would otherwise keep running in memory while the database calls it aborted
        if (game.getMode() == GameMode.online)
            throw new IllegalStateException("An online game can't be deleted; resign instead");
        game.setStatus(GameStatus.aborted);
        gameRepo.save(game);
        gameStore.delete(gameId.toString());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateUserStats(GameEntity game, String winner) {
        UUID white = game.getWhiteUserId(), black = game.getBlackUserId();

        // Lock the players' rows before reading them, lowest id first: two games that end
        // together for one player then take turns instead of overwriting each other, and
        // two games between the same pair can't wait on each other.
        java.util.stream.Stream.of(white, black)
            .filter(java.util.Objects::nonNull).distinct().sorted()
            .forEach(userRepo::findByIdForUpdate);

        // One person in both seats (a local game): it counts as one game played, but
        // can't be won or lost
        if (white != null && white.equals(black)) {
            userRepo.findById(white).ifPresent(u -> {
                u.setGamesPlayed(u.getGamesPlayed() + 1);
                userRepo.save(u);
            });
            return;
        }

        // Only online games between two people are rated. Both ratings are read
        // first so the two changes are computed from the same pre-game values.
        Integer whiteNew = null, blackNew = null;
        if (game.getMode() == GameMode.online && white != null && black != null) {
            var whiteUser = userRepo.findById(white).orElse(null);
            var blackUser = userRepo.findById(black).orElse(null);
            if (whiteUser != null && blackUser != null) {
                double whiteScore = winner == null ? 0.5 : "white".equals(winner) ? 1 : 0;
                int whiteDelta = EloCalculator.delta(whiteUser.getElo(), blackUser.getElo(), whiteScore);
                whiteNew = EloCalculator.apply(whiteUser.getElo(), whiteDelta);
                blackNew = EloCalculator.apply(blackUser.getElo(), -whiteDelta);
                game.setWhiteEloChange(whiteNew - whiteUser.getElo());
                game.setBlackEloChange(blackNew - blackUser.getElo());
                gameRepo.save(game);
            }
        }
        updateForPlayer(white, "white", winner, whiteNew);
        updateForPlayer(black, "black", winner, blackNew);
    }

    /** Records the result for one player; {@code newElo} is null for unrated games. */
    private void updateForPlayer(UUID userId, String color, String winner, Integer newElo) {
        if (userId == null) return;
        var user = userRepo.findById(userId).orElse(null);
        if (user == null) return;

        user.setGamesPlayed(user.getGamesPlayed() + 1);
        if (winner == null) {
            user.setDraws(user.getDraws() + 1);
            user.setCurrentStreak(0);
        } else if (winner.equals(color)) {
            user.setWins(user.getWins() + 1);
            int streak = user.getCurrentStreak() + 1;
            user.setCurrentStreak(streak);
            if (streak > user.getBestStreak()) user.setBestStreak(streak);
        } else {
            user.setLosses(user.getLosses() + 1);
            user.setCurrentStreak(0);
        }
        if (newElo != null) user.setElo(newElo);
        userRepo.save(user);
        if (newElo != null) eloRepo.save(new EloHistoryEntity(user, newElo));
    }

    private MatchHistoryDto.MatchHistory toMatchHistoryDto(GameEntity g, UUID userId, int movesCount) {
        boolean isWhite = userId.equals(g.getWhiteUserId());
        String  color   = isWhite ? "white" : "black";
        String  result;
        if (g.getResultWinner() == null)                    result = "draw";
        else if (g.getResultWinner().name().equals(color))  result = "win";
        else                                                 result = "loss";

        String opponentUsername = isWhite ? g.getBlackUsername() : g.getWhiteUsername();
        String tcLabel = g.getTimeControlType().name() + " "
            + (g.getTimeControlInitialMs() / 60_000) + "m";

        return new MatchHistoryDto.MatchHistory(
            g.getId().toString(), opponentUsername, g.getMode().name(),
            tcLabel, result, color,
            movesCount, isWhite ? g.getWhiteEloChange() : g.getBlackEloChange(),
            g.getUpdatedAt()
        );
    }

    private GameDto.SavedGame toSavedGameDto(GameEntity g, UUID userId) {
        boolean isWhite     = userId.equals(g.getWhiteUserId());
        String opponentName = isWhite ? g.getBlackUsername() : g.getWhiteUsername();
        String playerColor  = isWhite ? "white" : "black";
        return new GameDto.SavedGame(
            g.getId().toString(), g.getId().toString(),
            opponentName, g.getMode().name(),
            g.getFullMoveNumber(), playerColor,
            g.getOpening(), g.getUpdatedAt(), g.getCurrentFen()
        );
    }

    // ── Full game DTO (session cache → DB fallback) ───────────────────────────

    /**
     * Builds a fully-populated GameDto.Game.
     * Uses the in-memory session metadata when available (no DB hit).
     * Falls back to the DB on the first call after a server restart,
     * then caches metadata on the session for subsequent calls.
     */
    public GameDto.Game toFullGameDto(String gameId, GameStateResponse engineState) {
        var sessionOpt = gameStore.findById(gameId);

        if (sessionOpt.isPresent()) {
            var s = sessionOpt.get();
            // Cache metadata on first access after a restore
            if (s.metadata() == null) {
                var e = gameRepo.findById(UUID.fromString(gameId)).orElseThrow();
                s.setMetadata(buildMetadata(e));
                s.initClock(e.getWhiteTimeRemainingMs(), e.getBlackTimeRemainingMs(),
                            e.getTimeControlIncrementMs());
            }
            var offeredBy = s.drawOfferedBy();
            return buildFromMetadata(s.metadata(), engineState,
                    s.whiteTimeRemainingMs(), s.blackTimeRemainingMs())
                .withDrawOfferedBy(offeredBy == null ? null : offeredBy.name().toLowerCase());
        }

        // Extreme fallback: session evicted (shouldn't occur in normal flow)
        var e = gameRepo.findById(UUID.fromString(gameId)).orElseThrow();
        return buildFromEntity(e, engineState);
    }

    // ── Private DTO builders ──────────────────────────────────────────────────

    /** Move list for the UI: coordinates plus SAN; row follows the frontend convention (0 = rank 8). */
    private List<GameDto.Move> buildMoves(GameStateResponse st) {
        List<String> uci = st.moveHistory();
        List<String> san = st.sanHistory();
        if (uci == null || san == null) return List.of();
        List<GameDto.Move> out = new java.util.ArrayList<>();
        for (int i = 0; i < uci.size() && i < san.size(); i++) {
            String m = uci.get(i);
            var from = new GameDto.Square(8 - (m.charAt(1) - '0'), m.charAt(0) - 'a');
            var to   = new GameDto.Square(8 - (m.charAt(3) - '0'), m.charAt(2) - 'a');
            String notation = san.get(i);
            out.add(new GameDto.Move(from, to, null, null, null, null, null,
                notation.endsWith("+") || notation.endsWith("#"),
                notation.endsWith("#"), notation, null));
        }
        return out;
    }

    private GameDto.Game buildFromMetadata(GameMetadata m, GameStateResponse engineState,
                                           long whiteTimeMs, long blackTimeMs) {
        var white = new GameDto.GamePlayer(
            m.whiteUserId() != null ? m.whiteUserId().toString() : null,
            m.whiteUsername(), m.whiteElo(), "white",
            whiteTimeMs, null, m.whiteIsAi(), m.whiteAiDifficulty());

        var black = new GameDto.GamePlayer(
            m.blackUserId() != null ? m.blackUserId().toString() : null,
            m.blackUsername(), m.blackElo(), "black",
            blackTimeMs, null, m.blackIsAi(), m.blackAiDifficulty());

        var tc = new GameDto.TimeControl(
            m.timeControlType(), m.timeControlInitialMs(), m.timeControlIncrementMs());

        // Castling, en passant, halfMoveClock, fullMoveNumber are all in the FEN
        String[] parts = engineState.fen().split(" ");
        String castleStr = parts.length >= 3 ? parts[2] : "KQkq";
        var castling = new GameDto.CastlingRights(
            castleStr.contains("K"), castleStr.contains("Q"),
            castleStr.contains("k"), castleStr.contains("q"));

        GameDto.Square ep = null;
        if (parts.length >= 4 && !"-".equals(parts[3])) {
            ep = new GameDto.Square(parts[3].charAt(1) - '1', parts[3].charAt(0) - 'a');
        }
        int halfMove = parts.length >= 5 ? Integer.parseInt(parts[4]) : 0;
        int fullMove = parts.length >= 6 ? Integer.parseInt(parts[5]) : 1;

        return new GameDto.Game(
            engineState.gameId(), m.mode(), engineState.status(),
            white, black,
            null, buildMoves(engineState),
            engineState.activeColor().toLowerCase(),
            tc, ep, castling, halfMove, fullMove,
            withEloChanges(buildResult(engineState.status(), engineState.activeColor()),
                           engineState.gameId()),
            null,              // opening — not cached (changes in early game)
            m.createdAt(),
            null,              // updatedAt — not cached (changes every move)
            engineState.fen(),
            engineState.legalMoves(),
            engineState.moveHistory(),
            engineState.lastMove(),
            null);
    }

    private GameDto.Game buildFromEntity(GameEntity e, GameStateResponse engineState) {
        var white = new GameDto.GamePlayer(
            e.getWhiteUserId() != null ? e.getWhiteUserId().toString() : null,
            e.getWhiteUsername(), e.getWhiteElo(), "white",
            e.getWhiteTimeRemainingMs(), null, e.isWhiteIsAi(), e.getWhiteAiDifficulty());

        var black = new GameDto.GamePlayer(
            e.getBlackUserId() != null ? e.getBlackUserId().toString() : null,
            e.getBlackUsername(), e.getBlackElo(), "black",
            e.getBlackTimeRemainingMs(), null, e.isBlackIsAi(), e.getBlackAiDifficulty());

        var tc = new GameDto.TimeControl(
            e.getTimeControlType().name(),
            e.getTimeControlInitialMs(),
            e.getTimeControlIncrementMs());

        var castling = new GameDto.CastlingRights(
            e.isWhiteKingsideCastle(), e.isWhiteQueensideCastle(),
            e.isBlackKingsideCastle(), e.isBlackQueensideCastle());

        GameDto.Square ep = e.getEnPassantRow() != null
            ? new GameDto.Square(e.getEnPassantRow(), e.getEnPassantCol()) : null;

        return new GameDto.Game(
            e.getId().toString(), e.getMode().name().toLowerCase(), engineState.status(),
            white, black,
            null, buildMoves(engineState),
            engineState.activeColor().toLowerCase(),
            tc, ep, castling,
            e.getHalfMoveClock(), e.getFullMoveNumber(),
            withEloChanges(buildResult(engineState.status(), engineState.activeColor()),
                           engineState.gameId()),
            e.getOpening(), e.getCreatedAt(), e.getUpdatedAt(),
            engineState.fen(), engineState.legalMoves(),
            engineState.moveHistory(), engineState.lastMove(), null);
    }

    private GameMetadata buildMetadata(GameEntity e) {
        return new GameMetadata(
            e.getMode().name().toLowerCase(),
            e.getWhiteUsername(), e.getWhiteUserId(), e.getWhiteElo(),
            e.isWhiteIsAi(), e.getWhiteAiDifficulty(),
            e.getBlackUsername(), e.getBlackUserId(), e.getBlackElo(),
            e.isBlackIsAi(), e.getBlackAiDifficulty(),
            e.getTimeControlType().name(),
            e.getTimeControlInitialMs(), e.getTimeControlIncrementMs(),
            e.getCreatedAt());
    }

    /** Adds the stored rating changes to a result, once the game has been scored. */
    private GameDto.GameResult withEloChanges(GameDto.GameResult result, String gameId) {
        if (result == null) return null;
        return gameRepo.findById(UUID.fromString(gameId))
            .filter(g -> g.getWhiteEloChange() != null)
            .map(g -> new GameDto.GameResult(result.winner(), result.reason(),
                                             g.getWhiteEloChange(), g.getBlackEloChange()))
            .orElse(result);
    }

    private GameDto.GameResult buildResult(String status, String activeColor) {
        return switch (status) {
            case "CHECKMATE"      -> new GameDto.GameResult(
                activeColor.equalsIgnoreCase("WHITE") ? "black" : "white", "checkmate");
            case "STALEMATE"      -> new GameDto.GameResult(null, "stalemate");
            case "DRAW_50_MOVE"   -> new GameDto.GameResult(null, "fifty_move_rule");
            case "DRAW_INSUFFICIENT_MATERIAL" -> new GameDto.GameResult(null, "insufficient_material");
            case "DRAW_REPETITION"            -> new GameDto.GameResult(null, "threefold_repetition");
            case "WHITE_RESIGNED" -> new GameDto.GameResult("black", "resignation");
            case "BLACK_RESIGNED" -> new GameDto.GameResult("white", "resignation");
            case "DRAW_AGREED"    -> new GameDto.GameResult(null, "draw_agreement");
            case "WHITE_ABANDONED" -> new GameDto.GameResult("black", "abandonment");
            case "BLACK_ABANDONED" -> new GameDto.GameResult("white", "abandonment");
            case "WHITE_FLAGGED"  -> new GameDto.GameResult("black", "timeout");
            case "BLACK_FLAGGED"  -> new GameDto.GameResult("white", "timeout");
            default               -> null;
        };
    }

    private String extractPieceType(String fen, Square to) {
        try {
            return FenParser.parse(fen).pieceAt(to)
                .map(p -> p.type().name().toLowerCase())
                .orElse("pawn");
        } catch (Exception e) { return "pawn"; }
    }
}
