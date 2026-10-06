package com.chess.application;

import com.chess.ChessApplication;
import com.chess.domain.model.Color;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * A game whose result can't be saved right now must not be lost, and must not hold up
 * the other games the clock watcher is ending.
 */
@SpringBootTest(classes = ChessApplication.class)
@DisplayName("ClockWatcher — when saving a result fails")
class ClockWatcherResilienceTest {

    @Autowired GameApplicationService engine;
    @SpyBean   GamePersistenceService persistence;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private UUID ensureUser(String username) {
        return users.findByUsername(username).orElseGet(() -> {
            var u = new UserEntity();
            u.setUsername(username);
            u.setEmail(username + "@example.com");
            u.setPasswordHash("unused");
            u.setPreferences(UserPreferencesEntity.defaultsFor(u));
            return users.save(u);
        }).getId();
    }

    /** An online blitz game with one second a side, White already on the move clock. */
    private String startOneSecondGame(UUID white, UUID black) {
        String id = engine.createGame(new CreateGameRequest(null, "NONE", 1)).gameId();
        persistence.persistNewOnlineGame(id, white, "cw_white", 1200, black, "cw_black", 1200,
                TimeControlKind.blitz, 1_000, 0);
        engine.submitMove(id, "e2e4", Color.WHITE); // starts Black's clock, which then runs out
        return id;
    }

    private String statusInDb(String id) {
        return jdbc.queryForObject("select status::text from games where id = cast(? as uuid)",
                String.class, id);
    }

    @Test
    @DisplayName("a result that failed to save is saved on a later tick, and other games are not held up")
    void failedSaveIsRetried() {
        UUID white = ensureUser("cw_test_white");
        UUID black = ensureUser("cw_test_black");
        String unlucky = startOneSecondGame(white, black);
        String lucky   = startOneSecondGame(white, black);

        // the database "fails" once while saving the first game's result
        doThrow(new IllegalStateException("database unavailable"))
            .doCallRealMethod()
            .when(persistence).finaliseGame(eq(UUID.fromString(unlucky)), any(), any());

        // the app's own clock watcher runs every 500 ms and does the work
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertEquals("finished", statusInDb(lucky), "the other game must not be held up");
            assertEquals("finished", statusInDb(unlucky), "the failed save must be retried");
        });
    }
}
