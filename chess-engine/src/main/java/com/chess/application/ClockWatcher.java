package com.chess.application;

import com.chess.infrastructure.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends games on time. A flag only falls when somebody looks at the clock, and
 * nobody looks while a player has simply stopped moving, so this checks all live
 * games twice a second and tells the players when one runs out.
 */
@Component
public class ClockWatcher {

    private static final Logger log = LoggerFactory.getLogger(ClockWatcher.class);

    private final GameApplicationService engineService;
    private final GamePersistenceService persistService;
    private final WebSocketSessionManager wsManager;

    public ClockWatcher(GameApplicationService engineService,
                        GamePersistenceService persistService,
                        WebSocketSessionManager wsManager) {
        this.engineService  = engineService;
        this.persistService = persistService;
        this.wsManager      = wsManager;
    }

    @Scheduled(fixedDelay = 500)
    public void endGamesOutOfTime() {
        try {
            for (var ended : engineService.expireFlaggedGames()) {
                var game = persistService.toFullGameDto(ended.gameId(), ended);
                wsManager.broadcast(ended.gameId(), "GAME_OVER", game);
            }
        } catch (RuntimeException e) {
            // never let one bad game stop the watcher
            log.warn("Clock watcher failed", e);
        }
    }
}
