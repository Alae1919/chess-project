package com.chess.infrastructure.persistence;

import com.chess.domain.board.BoardFactory;
import com.chess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameSession — a first move that takes too long")
class GameSessionFirstMoveTest {

    private static final Duration LIMIT = Duration.ofSeconds(30);

    /** A clock that only moves when the test says so. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private TestClock time;
    private GameSession session;

    @BeforeEach
    void freshGame() {
        time = new TestClock();
        session = new GameSession("g", BoardFactory.startingPosition(), null, 1, time);
    }

    private void play(String uci) {
        var move = MoveGenerator.generateLegalMoves(session.board()).stream()
            .filter(m -> m.toUci().equals(uci)).findFirst().orElseThrow();
        session.applyMove(move);
    }

    @Test
    @DisplayName("White has the limit to make the first move, counted from the start of the game")
    void whiteIsOverdueAfterTheLimit() {
        time.advance(Duration.ofSeconds(29));
        assertFalse(session.firstMoveOverdue(LIMIT));

        time.advance(Duration.ofSeconds(2));
        assertTrue(session.firstMoveOverdue(LIMIT));
    }

    @Test
    @DisplayName("Black has the limit too, counted from White's move")
    void blackIsOverdueAfterTheLimit() {
        time.advance(Duration.ofSeconds(25));
        play("e2e4");

        time.advance(Duration.ofSeconds(29));
        assertFalse(session.firstMoveOverdue(LIMIT));

        time.advance(Duration.ofSeconds(2));
        assertTrue(session.firstMoveOverdue(LIMIT));
    }

    @Test
    @DisplayName("once both sides have moved, a slow move is no longer an abort")
    void startedGamesAreNeverOverdue() {
        play("e2e4");
        play("e7e5");

        time.advance(Duration.ofHours(1));

        assertFalse(session.firstMoveOverdue(LIMIT));
    }

    @Test
    @DisplayName("a game that is already over is never overdue")
    void finishedGamesAreNeverOverdue() {
        session.resign(com.chess.domain.model.Color.WHITE);

        time.advance(Duration.ofHours(1));

        assertFalse(session.firstMoveOverdue(LIMIT));
    }
}
