package com.chess.application;

import com.chess.ChessApplication;
import com.chess.domain.model.Color;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Two games ending together for one player must both count. */
@SpringBootTest(classes = ChessApplication.class)
@DisplayName("Ratings — games that end at the same moment")
class RatingConcurrencyTest {

    @Autowired GameApplicationService engine;
    @Autowired GamePersistenceService persistence;
    @Autowired UserRepository users;

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

    private String onlineGame(UUID white, String whiteName, UUID black, String blackName) {
        String id = engine.createGame(new CreateGameRequest(null, "NONE", 1)).gameId();
        persistence.persistNewOnlineGame(id, white, whiteName, 1200, black, blackName, 1200,
                TimeControlKind.unlimited, 0, 0);
        return id;
    }

    @RepeatedTest(15)
    @DisplayName("both of a player's results are counted, not just the last one written")
    void simultaneousResultsAreBothCounted() throws Exception {
        UUID me     = ensureUser("rc_test_me");
        UUID first  = ensureUser("rc_test_first");
        UUID second = ensureUser("rc_test_second");
        String game1 = onlineGame(me, "rc_test_me", first, "rc_test_first");
        String game2 = onlineGame(me, "rc_test_me", second, "rc_test_second");
        var before = users.findById(me).orElseThrow();
        int playedBefore = before.getGamesPlayed(), lossesBefore = before.getLosses();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<?>> results = List.of(
                pool.submit(() -> { go.await(); return engine.resign(game1, Color.WHITE); }),
                pool.submit(() -> { go.await(); return engine.resign(game2, Color.WHITE); }));
            go.countDown();
            for (var result : results) result.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        var after = users.findById(me).orElseThrow();
        assertEquals(playedBefore + 2, after.getGamesPlayed(), "games played");
        assertEquals(lossesBefore + 2, after.getLosses(), "losses");
    }
}
