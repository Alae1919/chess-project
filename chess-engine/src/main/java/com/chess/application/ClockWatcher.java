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
        // each step on its own, so one failing never keeps the others from running
        run("end games out of time", engineService::expireFlaggedGames);
        run("call off stalled games", () -> engineService.abortStalledOnlineGames(firstMoveTimeout));
        run("save results that failed to save", engineService::saveUnsavedResults);
    }

    private void run(String what, java.util.function.Supplier<java.util.List<com.chess.infrastructure.api.dto.GameStateResponse>> step) {
        try {
            tellPlayers(step.get());
        } catch (RuntimeException e) {
            log.warn("Clock watcher could not {}", what, e);
        }
    }

    private void tellPlayers(java.util.List<com.chess.infrastructure.api.dto.GameStateResponse> ended) {
        for (var over : ended) {
            var game = persistService.toFullGameDto(over.gameId(), over);
            wsManager.broadcast(over.gameId(), "GAME_OVER", game);
        }
    }
}
