package com.chess.application;

import com.chess.ChessApplication;
import com.chess.domain.model.Color;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.infrastructure.persistence.GameSession;
import com.chess.infrastructure.persistence.GameStore;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/** What happens to games that were under way when the server stopped. */
@SpringBootTest(classes = ChessApplication.class)
@DisplayName("Game recovery — after a restart")
class GameRecoveryTest {

    @Autowired GameApplicationService engine;
    @Autowired GamePersistenceService persistence;
    @Autowired GameRecovery recovery;
    @Autowired GameStore store;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @SpyBean   AbandonmentService abandonment;

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

    /** An online game, one minute a side, White has moved: Black's clock is running. */
    private String blackToMove(UUID white, UUID black) {
        String id = engine.createGame(new CreateGameRequest(null, "NONE", 1)).gameId();
        persistence.persistNewOnlineGame(id, white, "gr_white", 1200, black, "gr_black", 1200,
                TimeControlKind.blitz, 60_000, 0);
        engine.submitMove(id, "e2e4", Color.WHITE);
        return id;
    }

    @Test
    @DisplayName("the time the server was down is charged to the player whose clock was running")
    void downtimeIsCharged() {
        UUID white = ensureUser("gr_test_white");
        UUID black = ensureUser("gr_test_black");
        String id = blackToMove(white, black);
        doNothing().when(abandonment).playerDisconnected(any(), any());

        // Black's clock started 20 seconds ago, then the server went down
        jdbc.update("update games set turn_started_at = now() - interval '20 seconds' where id = cast(? as uuid)", id);
        store.delete(id);

        recovery.recoverActiveOnlineGames();

        GameSession restored = store.findById(id).orElseThrow();
        long blackLeft = restored.blackTimeRemainingMs();
        assertTrue(blackLeft <= 40_500 && blackLeft >= 37_000, "black has " + blackLeft + " ms left");
        assertEquals(60_000, restored.whiteTimeRemainingMs(), "White's clock was not running");
    }

    @Test
    @DisplayName("a restored online game is recognised as online, so its timers apply")
    void restoredGameKnowsItIsOnline() {
        UUID white = ensureUser("gr_test_white2");
        UUID black = ensureUser("gr_test_black2");
        String id = blackToMove(white, black);
        doNothing().when(abandonment).playerDisconnected(any(), any());
        store.delete(id);

        recovery.recoverActiveOnlineGames();

        assertTrue(store.findById(id).orElseThrow().isOnline());
    }

    @Test
    @DisplayName("players who have not come back are given the disconnect countdown")
    void absentPlayersGetACountdown() {
        UUID white = ensureUser("gr_test_white3");
        UUID black = ensureUser("gr_test_black3");
        String id = blackToMove(white, black);
        doNothing().when(abandonment).playerDisconnected(any(), any());
        store.delete(id);

        recovery.recoverActiveOnlineGames();

        verify(abandonment).playerDisconnected(eq(id), eq(white.toString()));
        verify(abandonment).playerDisconnected(eq(id), eq(black.toString()));
    }
}
