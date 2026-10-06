package com.chess.application;

import com.chess.infrastructure.persistence.GameSession;
import com.chess.persistence.entity.DatabaseEnums.GameMode;
import com.chess.persistence.entity.DatabaseEnums.GameStatus;
import com.chess.persistence.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Online games that were under way when the server stopped. Their clocks and countdowns
 * live in memory, so a game nobody touches after a restart would otherwise never be
 * looked at again: it would sit "active" with its clock frozen.
 *
 * Each is brought back into memory, which also puts it in front of the clock watcher and
 * the first-move timer. Its players are not connected yet, so each gets the usual
 * disconnect countdown: they have the usual time to come back, or the game ends.
 */
@Component
public class GameRecovery {

    private static final Logger log = LoggerFactory.getLogger(GameRecovery.class);

    private final GameRepository gameRepository;
    private final GameApplicationService engine;
    private final AbandonmentService abandonment;
    private final boolean recoverOnStartup;

    public GameRecovery(GameRepository gameRepository, GameApplicationService engine,
                        AbandonmentService abandonment,
                        @Value("${app.recover-games-on-startup:true}") boolean recoverOnStartup) {
        this.gameRepository   = gameRepository;
        this.engine           = engine;
        this.abandonment      = abandonment;
        this.recoverOnStartup = recoverOnStartup;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (recoverOnStartup) recoverActiveOnlineGames();
    }

    /** Restores every active online game and starts a countdown for each of its players. */
    public void recoverActiveOnlineGames() {
        int restored = 0;
        for (UUID id : gameRepository.findIdsByModeAndStatus(GameMode.online, GameStatus.active)) {
            try {
                String gameId = id.toString();
                GameSession session = engine.restore(gameId);
                if (session.isOver()) continue;
                var game = gameRepository.findById(id).orElse(null);
                if (game == null) continue;
                abandonment.playerDisconnected(gameId, String.valueOf(game.getWhiteUserId()));
                abandonment.playerDisconnected(gameId, String.valueOf(game.getBlackUserId()));
                restored++;
            } catch (RuntimeException e) {
                log.warn("Could not recover game {}", id, e);
            }
        }
        if (restored > 0) log.info("Recovered {} online game(s) that were under way", restored);
    }
}
