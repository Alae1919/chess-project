package com.chess.application;

import com.chess.ChessApplication;
import com.chess.domain.model.Color;
import com.chess.infrastructure.api.dto.CreateGameRequest;
import com.chess.persistence.entity.DatabaseEnums.TimeControlKind;
import com.chess.persistence.entity.UserEntity;
import com.chess.persistence.entity.UserPreferencesEntity;
import com.chess.persistence.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Saving a move runs inside the game's turn lock: what it costs, every player waits for. */
@SpringBootTest(classes = ChessApplication.class, properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Saving a move")
class MovePersistenceCostTest {

    @Autowired GameApplicationService engine;
    @Autowired GamePersistenceService persistence;
    @Autowired UserRepository users;
    @Autowired EntityManagerFactory emf;

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

    /** How many SQL statements one move costs */
    private long statementsFor(String id, String uci, Color side) {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        engine.submitMove(id, uci, side);
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("costs a few statements, the same late in the game as early: earlier moves are not loaded")
    void costDoesNotGrowWithTheGame() {
        UUID white = ensureUser("mp_white");
        UUID black = ensureUser("mp_black");
        String id = engine.createGame(new CreateGameRequest(null, "NONE", 1)).gameId();
        persistence.persistNewOnlineGame(id, white, "mp_white", 1200, black, "mp_black", 1200,
                TimeControlKind.rapid, 600_000, 0);

        // the Ruy Lopez, Breyer variation: twenty moves, no position twice
        List<String> line = List.of("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7",
                "f1e1", "b7b5", "a4b3", "d7d6", "c2c3", "e8g8", "h2h3", "c6b8", "d2d4", "b8d7");
        engine.submitMove(id, line.get(0), Color.WHITE);
        long early = statementsFor(id, line.get(1), Color.BLACK);
        for (int i = 2; i < line.size() - 1; i++) engine.submitMove(id, line.get(i), i % 2 == 0 ? Color.WHITE : Color.BLACK);
        long late = statementsFor(id, line.get(line.size() - 1), Color.BLACK);

        assertTrue(early <= 4, "move 2 took " + early + " statements");
        assertEquals(early, late, "move 20 took " + late + " statements, move 2 took " + early);
    }
}
