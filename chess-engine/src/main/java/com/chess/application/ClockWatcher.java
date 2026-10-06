package com.chess.application;

import com.chess.infrastructure.websocket.WebSocketSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    private final Duration firstMoveTimeout;

    public ClockWatcher(GameApplicationService engineService,
                        GamePersistenceService persistService,
                        WebSocketSessionManager wsManager,
                        @Value("${app.first-move-timeout-seconds:30}") long firstMoveTimeoutSeconds) {
        this.engineService  = engineService;
        this.persistService = persistService;
        this.wsManager      = wsManager;
        this.firstMoveTimeout = Duration.ofSeconds(firstMoveTimeoutSeconds);
    }

    @Scheduled(fixedDelay = 500)
    public void endGamesOutOfTime() {
        try {
            tellPlayers(engineService.expireFlaggedGames());
        } catch (RuntimeException e) {
            // never let one bad game stop the watcher
            log.warn("Clock watcher failed", e);
        }
        try {
            tellPlayers(engineService.abortStalledOnlineGames(firstMoveTimeout));
        } catch (RuntimeException e) {
            log.warn("Clock watcher could not call off stalled games", e);
        }
    }

    private void tellPlayers(java.util.List<com.chess.infrastructure.api.dto.GameStateResponse> ended) {
        for (var over : ended) {
            var game = persistService.toFullGameDto(over.gameId(), over);
            wsManager.broadcast(over.gameId(), "GAME_OVER", game);
        }
    }
}
