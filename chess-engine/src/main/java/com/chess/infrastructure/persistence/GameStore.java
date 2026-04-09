package com.chess.infrastructure.persistence;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory game store.
 *
 * Thread-safety: ConcurrentHashMap ensures safe concurrent reads.
 * Individual GameSession mutations are serialised by the service layer
 * (one request per game at a time is the natural REST model).
 *
 * Swap for a Redis or JPA implementation later by replacing this class —
 * the service only depends on the method signatures below.
 */
@Repository
public final class GameStore {

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();

    /** Creates a new session and returns its generated ID. */
    public String save(GameSession session) {
        games.put(session.id(), session);
        return session.id();
    }

    public Optional<GameSession> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }

    public void delete(String id) {
        games.remove(id);
    }

    public boolean exists(String id) {
        return games.containsKey(id);
    }

    /** Generates a fresh UUID for a new game. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
