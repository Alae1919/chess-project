package com.chess.application;

import com.chess.domain.model.Color;
import com.chess.infrastructure.api.exception.GameNotFoundException;
import com.chess.infrastructure.api.exception.NotAPlayerException;
import com.chess.infrastructure.persistence.GameMetadata;
import com.chess.infrastructure.persistence.GameStore;
import com.chess.persistence.entity.DatabaseEnums.PlayerSide;
import com.chess.persistence.entity.GameEntity;
import com.chess.persistence.repository.GameRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may act on a game: only its players.
 *
 * Every mode records its human players in the white/black seats (an AI game
 * fills the human's seat, a local game fills both with the same user), so this
 * one rule covers AI, local and online games.
 */
@Component
public class GameAccess {

    private final GameRepository gameRepository;
    private final GameStore      store;

    public GameAccess(GameRepository gameRepository, GameStore store) {
        this.gameRepository = gameRepository;
        this.store          = store;
    }

    /**
     * What a request needs to know about a game's players: its mode, who sits where, and whose turn it is.
     *
     * @param mode        "ai", "local" or "online"
     * @param activeColor the side to move
     */
    public record Seats(String mode, UUID whiteUserId, UUID blackUserId, Color activeColor) {
        public boolean online() { return "online".equals(mode); }

        /** The colour `userId` plays, or null when they hold both seats (a local game) */
        public Color seatOf(UUID userId) {
            boolean white = userId.equals(whiteUserId);
            boolean black = userId.equals(blackUserId);
            if (white && black) return null;
            return white ? Color.WHITE : Color.BLACK;
        }
    }

    /**
     * The game's seats, provided the caller plays in it. From the game's session when it is in memory (every
     * game being played is), so a move, a draw offer or a resignation reads nothing from the database to be
     * allowed; from the stored game otherwise.
     *
     * @throws GameNotFoundException for an unknown or malformed id (404)
     * @throws NotAPlayerException   when the caller is not one of its players (403)
     */
    public Seats requireSeats(String gameId, UUID callerId) {
        var live = store.findById(gameId).filter(s -> s.metadata() != null);
        if (live.isPresent()) {
            GameMetadata m = live.get().metadata();
            if (!isPlayer(m.whiteUserId(), m.blackUserId(), callerId)) throw new NotAPlayerException(gameId);
            return new Seats(m.mode(), m.whiteUserId(), m.blackUserId(), live.get().board().activeColor());
        }
        GameEntity game = requirePlayer(gameId, callerId);
        return new Seats(game.getMode().name(), game.getWhiteUserId(), game.getBlackUserId(),
            game.getCurrentTurn() == PlayerSide.black ? Color.BLACK : Color.WHITE);
    }

    /**
     * The stored game, provided the caller plays in it.
     *
     * @throws GameNotFoundException for an unknown or malformed id (404)
     * @throws NotAPlayerException   when the caller is not one of its players (403)
     */
    public GameEntity requirePlayer(String gameId, UUID callerId) {
        GameEntity game = parseUuid(gameId)
            .flatMap(gameRepository::findById)
            .orElseThrow(() -> new GameNotFoundException(gameId));
        if (!isPlayer(game, callerId)) throw new NotAPlayerException(gameId);
        return game;
    }

    public static boolean isPlayer(GameEntity game, UUID userId) {
        return isPlayer(game.getWhiteUserId(), game.getBlackUserId(), userId);
    }

    private static boolean isPlayer(UUID white, UUID black, UUID userId) {
        return userId != null && (userId.equals(white) || userId.equals(black));
    }

    /**
     * The colour a player plays, or null when they hold both seats (a local game).
     * Only meaningful for one of the game's players.
     */
    public static Color seatOf(GameEntity game, UUID userId) {
        boolean white = userId.equals(game.getWhiteUserId());
        boolean black = userId.equals(game.getBlackUserId());
        if (white && black) return null;
        return white ? Color.WHITE : Color.BLACK;
    }

    private static Optional<UUID> parseUuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
