package com.chess.application;

import com.chess.infrastructure.persistence.GameStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Keeps the in-memory games from piling up: see {@link GameStore#evictIdle}. */
@Component
public class GameSessionJanitor {

    private static final Logger log = LoggerFactory.getLogger(GameSessionJanitor.class);

    private static final Duration FINISHED_GAME_KEPT_FOR = Duration.ofMinutes(5);
    private static final Duration LIVE_GAME_KEPT_FOR     = Duration.ofHours(2);

    private final GameStore store;

    public GameSessionJanitor(GameStore store) {
        this.store = store;
    }

    @Scheduled(fixedDelay = 60_000)
    public void sweep() {
        try {
            int dropped = store.evictIdle(Instant.now(), FINISHED_GAME_KEPT_FOR, LIVE_GAME_KEPT_FOR);
            if (dropped > 0) log.debug("Dropped {} idle game sessions from memory", dropped);
        } catch (RuntimeException e) {
            log.warn("Could not drop idle game sessions", e);
        }
    }
}
