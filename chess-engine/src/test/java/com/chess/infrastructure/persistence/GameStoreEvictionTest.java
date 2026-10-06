package com.chess.infrastructure.persistence;

import com.chess.domain.board.BoardFactory;
import com.chess.domain.model.Color;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameStore — dropping sessions nobody uses")
class GameStoreEvictionTest {

    private static final Duration FINISHED = Duration.ofMinutes(5);
    private static final Duration IDLE     = Duration.ofHours(2);

    /** A clock that only moves when the test says so. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private TestClock time;
    private GameStore store;

    @BeforeEach
    void emptyStore() {
        time = new TestClock();
        store = new GameStore();
    }

    private GameSession add(String id, Color aiColor) {
        var session = new GameSession(id, BoardFactory.startingPosition(), aiColor, 1, time);
        store.save(session);
        return session;
    }

    private GameSession addOnline(String id) {
        var session = add(id, null);
        session.setMetadata(new GameMetadata("online", null, null, null, false, null,
                null, null, null, false, null, "unlimited", 0, 0, time.instant()));
        return session;
    }

    private int sweep() { return store.evictIdle(time.instant(), FINISHED, IDLE); }

    @Test
    @DisplayName("a finished game goes after a few minutes")
    void finishedGamesGoSoon() {
        add("done", null).resign(Color.WHITE);

        time.advance(Duration.ofMinutes(4));
        assertEquals(0, sweep());
        assertTrue(store.exists("done"));

        time.advance(Duration.ofMinutes(2));
        assertEquals(1, sweep());
        assertFalse(store.exists("done"));
    }

    @Test
    @DisplayName("a game still under way is kept for hours")
    void liveGamesStayAWhile() {
        add("live", Color.BLACK);

        time.advance(Duration.ofHours(1));
        assertEquals(0, sweep());

        time.advance(Duration.ofHours(2));
        assertEquals(1, sweep());
        assertFalse(store.exists("live"));
    }

    @Test
    @DisplayName("using a game starts its idle time again")
    void accessKeepsASessionAlive() {
        add("busy", null);

        time.advance(Duration.ofHours(1).plusMinutes(50));
        store.findById("busy");                  // someone looks at the game
        time.advance(Duration.ofMinutes(30));

        assertEquals(0, sweep());
        assertTrue(store.exists("busy"));
    }

    @Test
    @DisplayName("a live online game is never dropped for being quiet: its clock and countdowns need it")
    void liveOnlineGamesStay() {
        addOnline("online");

        time.advance(Duration.ofHours(10));

        assertEquals(0, sweep());
        assertTrue(store.exists("online"));
    }

    @Test
    @DisplayName("a finished online game goes like any other")
    void finishedOnlineGamesGo() {
        addOnline("online").resign(Color.BLACK);

        time.advance(Duration.ofMinutes(6));

        assertEquals(1, sweep());
    }

    @Test
    @DisplayName("a session whose request is running right now is not pulled out from under it")
    void sessionInUseStays() {
        var session = add("locked", null);
        session.resign(Color.WHITE);
        time.advance(Duration.ofMinutes(10));

        session.turnLock().lock();
        try {
            assertEquals(0, sweep());
        } finally {
            session.turnLock().unlock();
        }
        assertEquals(1, sweep());
    }

    @Test
    @DisplayName("a session whose AI is searching is not dropped either")
    void sessionThinkingStays() {
        var session = add("thinking", Color.BLACK);
        session.resign(Color.WHITE);
        time.advance(Duration.ofMinutes(10));

        assertTrue(session.tryBeginAiSearch());
        assertEquals(0, sweep());

        session.endAiSearch();
        assertEquals(1, sweep());
    }
}
