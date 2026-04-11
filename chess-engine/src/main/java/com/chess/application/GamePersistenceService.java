package com.chess.application;

import com.chess.api.dto.GameDto;
import com.chess.api.dto.MatchHistoryDto;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Move;
import com.chess.domain.rules.MoveGenerator;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.entity.DatabaseEnums.PieceKind;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.DatabaseEnums.GameEndReason;
import com.chess.persistence.entity.*;
import com.chess.persistence.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GamePersistenceService {

    private final GameRepository      gameRepo;
    private final UserRepository      userRepo;
    private final EloHistoryRepository eloRepo;
    private final SavedGameRepository savedGameRepo;
    private final GameApplicationService gameAppService;  // in-memory engine store

    public GamePersistenceService(GameRepository gameRepo,
                                   UserRepository userRepo,
                                   EloHistoryRepository eloRepo,
                                   SavedGameRepository savedGameRepo,
                                   GameApplicationService gameAppService) {
        this.gameRepo      = gameRepo;
        this.userRepo      = userRepo;
        this.eloRepo       = eloRepo;
        this.savedGameRepo = savedGameRepo;
        this.gameAppService = gameAppService;
    }

    /**
     * Creates a DB record for a newly started game.
     * Called after GameApplicationService.createGame() succeeds.
     */
    @Transactional
    public void persistNewGame(String gameId, GameDto.CreateGameRequest req,
                               UUID userId, String username) {
        var entity = new GameEntity();
        entity.setMode(GameMode.valueOf(req.mode()));
        entity.setStatus(GameStatus.active);
        entity.setCurrentTurn(PlayerSide.white);

        boolean userIsWhite = !"black".equalsIgnoreCase(req.playerColor());
        if (userIsWhite) {
            entity.setWhiteUserId(userId);
            entity.setWhiteUsername(username);
            entity.setBlackIsAi(true);
            entity.setBlackUsername("AI");
            entity.setBlackAiDifficulty(req.aiDifficulty());
        } else {
            entity.setBlackUserId(userId);
            entity.setBlackUsername(username);
            entity.setWhiteIsAi(true);
            entity.setWhiteUsername("AI");
            entity.setWhiteAiDifficulty(req.aiDifficulty());
        }

        var tc = req.timeControl();
        entity.setTimeControlType(TimeControlKind.valueOf(tc.type()));
        entity.setTimeControlInitialMs(tc.initialMs());
        entity.setTimeControlIncrementMs(tc.incrementMs());
        entity.setWhiteTimeRemainingMs(tc.initialMs());
        entity.setBlackTimeRemainingMs(tc.initialMs());

        if (req.fen() != null && !req.fen().isBlank())
            entity.setCurrentFen(req.fen());

        // Override ID to match in-memory engine game ID
        // (We use a custom insert with the UUID from the engine store)
        gameRepo.save(entity);
    }

    /**
     * Appends a move to the DB and updates the FEN.
     * Called after GameApplicationService.submitMove() or playAiMove() succeeds.
     */
    @Transactional
    public void persistMove(UUID dbGameId, Move engineMove, String newFen,
                            int moveNumber, String color) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        game.setCurrentFen(newFen);
        var played = PlayerSide.valueOf(color.toLowerCase());
        game.setCurrentTurn(played == PlayerSide.white ? PlayerSide.black : PlayerSide.white);

        var moveEntity = new GameMoveEntity();
        moveEntity.setGame(game);
        moveEntity.setMoveNumber(moveNumber);
        moveEntity.setColor(played);
        moveEntity.setFromRow(engineMove.from().rank());
        moveEntity.setFromCol(engineMove.from().file());
        moveEntity.setToRow(engineMove.to().rank());
        moveEntity.setToCol(engineMove.to().file());
        moveEntity.setPieceType(PieceKind.valueOf(extractPieceType(newFen, engineMove)));
        moveEntity.setPieceColor(played);
        moveEntity.setAlgebraicNotation(engineMove.toString());
        moveEntity.setEnPassant(engineMove.isEnPassant());
        if (engineMove.isCastling()) moveEntity.setIsCastling(
            engineMove.to().file() > engineMove.from().file() ? "kingside" : "queenside");
        if (engineMove.isPromotion())
            moveEntity.setPromotion(PieceKind.valueOf(engineMove.promotion().name().toLowerCase()));

        game.getMoves().add(moveEntity);
        gameRepo.save(game);
    }

    /**
     * Marks a game as finished, records ELO changes, updates user stats.
     */
    @Transactional
    public void finaliseGame(UUID dbGameId, String winner, String reason) {
        var game = gameRepo.findById(dbGameId).orElseThrow();
        game.setStatus(GameStatus.finished);
        game.setResultWinner(winner != null ? PlayerSide.valueOf(winner.toLowerCase()) : null);
        game.setResultReason(reason != null ? GameEndReason.valueOf(reason.toLowerCase()) : null);
        gameRepo.save(game);

        // Update stats for both registered players
        updateUserStats(game, winner);
    }

    public Page<MatchHistoryDto.MatchHistory> getMatchHistory(UUID userId, int page, int size) {
        var pageable = PageRequest.of(page, size);
        return gameRepo.findMatchHistoryForUser(userId, pageable)
            .map(g -> toMatchHistoryDto(g, userId));
    }

    public List<GameDto.SavedGame> getSavedGames(UUID userId) {
        return savedGameRepo.findByUserIdOrderBySavedAtDesc(userId)
            .stream().map(this::toSavedGameDto).toList();
    }

    @Transactional
    public GameDto.SavedGame saveCurrentGame(String engineGameId, String fen, int moveCount,
                                              com.chess.domain.model.Color aiColor,
                                              UUID userId, String username) {
        var sg = new com.chess.persistence.entity.SavedGameEntity();
        sg.setGameId(UUID.fromString(engineGameId));
        sg.setUserId(userId);

        boolean userIsWhite = aiColor == null || aiColor != com.chess.domain.model.Color.WHITE;
        sg.setPlayerColor(userIsWhite
            ? com.chess.persistence.entity.DatabaseEnums.PlayerSide.white
            : com.chess.persistence.entity.DatabaseEnums.PlayerSide.black);
        sg.setOpponentName(aiColor != null ? "AI" : "Opponent");
        sg.setMode(com.chess.persistence.entity.DatabaseEnums.GameMode.ai);
        sg.setTurnNumber(moveCount);
        sg.setThumbnailFen(fen);
        savedGameRepo.save(sg);
        return toSavedGameDto(sg);
    }

    public void deleteSavedGame(UUID savedGameId, UUID userId) {
        var sg = savedGameRepo.findById(savedGameId)
            .orElseThrow(() -> new EntityNotFoundException("Saved game not found"));
        if (!sg.getUserId().equals(userId))
            throw new IllegalArgumentException("Access denied");
        savedGameRepo.delete(sg);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateUserStats(GameEntity game, String winner) {
        updateForPlayer(game.getWhiteUserId(), "white", winner, game);
        updateForPlayer(game.getBlackUserId(), "black", winner, game);
    }

    private void updateForPlayer(UUID userId, String color, String winner, GameEntity game) {
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
        userRepo.save(user);
        eloRepo.save(new EloHistoryEntity(user, user.getElo()));
    }

    private MatchHistoryDto.MatchHistory toMatchHistoryDto(GameEntity g, UUID userId) {
        boolean isWhite = userId.equals(g.getWhiteUserId());
        String  color   = isWhite ? "white" : "black";
        String  result;
        if (g.getResultWinner() == null)              result = "draw";
        else if (g.getResultWinner().name().equals(color)) result = "win";
        else                                           result = "loss";

        String opponentUsername = isWhite ? g.getBlackUsername() : g.getWhiteUsername();
        String tcLabel = g.getTimeControlType().name() + " "
            + (g.getTimeControlInitialMs() / 60_000) + "m";

        return new MatchHistoryDto.MatchHistory(
            g.getId().toString(), opponentUsername, g.getMode().name(),
            tcLabel, result, color,
            g.getMoves().size(), null, g.getUpdatedAt()
        );
    }

    private GameDto.SavedGame toSavedGameDto(SavedGameEntity s) {
        return new GameDto.SavedGame(
            s.getId().toString(), s.getOpponentName(), s.getMode().name(),
            s.getTurnNumber(), s.getPlayerColor().name(),
            s.getOpening(), s.getSavedAt(), s.getThumbnailFen()
        );
    }

    private String extractPieceType(String fen, Move move) {
        // Parse FEN to get piece type — delegates to existing FenParser
        try {
            var board = FenParser.parse(fen);
            return board.pieceAt(move.to())
                .map(p -> p.type().name().toLowerCase())
                .orElse("pawn");
        } catch (Exception e) { return "pawn"; }
    }
}
