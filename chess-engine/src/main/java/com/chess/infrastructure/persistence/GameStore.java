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
public class GameStore {

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();

    /** Creates a new session and returns its generated ID. */
    public String save(GameSession session) {
        games.put(session.id(), session);
        return session.id();
    }

    public Optional<GameSession> findById(String id) {
        GameSession session = games.get(id);
        if (session != null) session.touch();
        return Optional.ofNullable(session);
    }

    /**
     * Drops sessions nobody has used for a while, so memory doesn't grow with every game ever
     * opened (an AI game carries a transposition table of about 20 MB). A dropped game loads
     * back from the database the next time it is used.
     *
     * <ul>
     *   <li>a finished game goes after {@code finishedAfter}</li>
     *   <li>a live game goes after {@code idleAfter}, except an online one: its clocks and
     *       disconnect countdowns need the session, and they end the game on their own</li>
     *   <li>a session a request is using right now, or whose AI is searching, stays</li>
     * </ul>
     *
     * @return how many sessions were dropped
     */
    public int evictIdle(java.time.Instant now, java.time.Duration finishedAfter,
                         java.time.Duration idleAfter) {
        int[] dropped = {0};
        games.values().removeIf(session -> {
            boolean drop = shouldEvict(session, now, finishedAfter, idleAfter);
            if (drop) dropped[0]++;
            return drop;
        });
        return dropped[0];
    }

    private static boolean shouldEvict(GameSession session, java.time.Instant now,
                                       java.time.Duration finishedAfter, java.time.Duration idleAfter) {
        if (session.turnLock().isLocked() || session.isAiSearching()) return false;
        java.time.Duration idle = java.time.Duration.between(session.lastAccessAt(), now);
        if (session.isOver()) return idle.compareTo(finishedAfter) > 0;
        if (session.isOnline()) return false;
        return idle.compareTo(idleAfter) > 0;
    }

    /** A snapshot of every session currently in memory. */
    public java.util.List<GameSession> all() {
        return java.util.List.copyOf(games.values());
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
