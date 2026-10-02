package com.chess.application;

import com.chess.domain.model.Color;
import com.chess.infrastructure.api.exception.GameNotFoundException;
import com.chess.infrastructure.api.exception.NotAPlayerException;
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

    public GameAccess(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
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
        return userId != null
            && (userId.equals(game.getWhiteUserId()) || userId.equals(game.getBlackUserId()));
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
