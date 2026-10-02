package com.chess.infrastructure.persistence;

import com.chess.domain.board.BoardFactory;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Color;
import com.chess.domain.rules.GameStateChecker.State;
import com.chess.domain.rules.MoveGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GameSession — the chess clock")
class GameSessionClockTest {

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
    void fiveMinutesAndATwoSecondIncrement() {
        time = new TestClock();
        session = new GameSession("g", BoardFactory.startingPosition(), null, 1, time);
        session.initClock(300_000, 300_000, 2_000);
    }

    private void play(String uci) {
        var move = MoveGenerator.generateLegalMoves(session.board()).stream()
            .filter(m -> m.toUci().equals(uci)).findFirst().orElseThrow();
        session.applyMove(move);
    }

    private void wait(int seconds) { time.advance(Duration.ofSeconds(seconds)); }

    @Test
    @DisplayName("nothing runs before White's first move")
    void clockWaitsForTheFirstMove() {
        wait(90);

        assertEquals(300_000, session.whiteTimeRemainingMs());
        assertEquals(300_000, session.blackTimeRemainingMs());
        assertNull(session.flaggedSide());
    }

    @Test
    @DisplayName("White's first move is free; then Black's clock runs")
    void blackRunsAfterTheFirstMove() {
        wait(90);
        play("e2e4");
        wait(10);

        assertEquals(302_000, session.whiteTimeRemainingMs(), "increment only, no time charged");
        assertEquals(290_000, session.blackTimeRemainingMs());
    }

    @Test
    @DisplayName("each move charges the time used and adds the increment")
    void chargesTimeAndAddsIncrement() {
        play("e2e4");
        wait(10);
        play("e7e5");   // black: -10s +2s
        wait(4);
        play("g1f3");   // white: -4s +2s

        assertEquals(300_000 + 2_000 - 4_000 + 2_000, session.whiteTimeRemainingMs());
        assertEquals(300_000 - 10_000 + 2_000, session.blackTimeRemainingMs());
    }

    @Test
    @DisplayName("the side to move runs out of time")
    void flagFalls() {
        play("e2e4");
        wait(299);
        assertNull(session.flaggedSide());

        wait(2);
        assertEquals(Color.BLACK, session.flaggedSide());
        assertEquals(0, session.blackTimeRemainingMs());

        session.flag(Color.BLACK);
        assertEquals(State.BLACK_FLAGGED, session.state());
        assertTrue(session.isOver());
    }

    @Test
    @DisplayName("running out of time against a bare king is a draw, not a loss")
    void flagAgainstABareKing() {
        session = new GameSession("g", FenParser.parse("4k3/8/8/8/8/8/P7/4K3 w - - 0 1"), null, 1, time);
        session.initClock(60_000, 60_000, 0);
        play("e1e2");   // black to move; black has only a king
        wait(61);

        // White's flag would fall on White's turn; here Black has only a king and
        // White is the one who can win, so Black running out is a White win...
        session.flag(Color.BLACK);
        assertEquals(State.BLACK_FLAGGED, session.state());

        // ...but White running out against the bare king is a draw
        session = new GameSession("g", FenParser.parse("4k3/8/8/8/8/8/P7/4K3 w - - 0 1"), null, 1, time);
        session.initClock(60_000, 60_000, 0);
        play("e1e2");
        play("e8e7");
        wait(61);
        assertEquals(Color.WHITE, session.flaggedSide());
        session.flag(Color.WHITE);
        assertEquals(State.DRAW_INSUFFICIENT_MATERIAL, session.state());
    }

    @Test
    @DisplayName("the clock stops when the game ends")
    void clockStopsAtTheEnd() {
        play("e2e4");
        wait(5);
        session.resign(Color.WHITE);
        wait(600);

        assertEquals(295_000, session.blackTimeRemainingMs());
        assertNull(session.flaggedSide());
    }

    @Test
    @DisplayName("an unlimited game has no clock")
    void unlimitedGamesNeverFlag() {
        session = new GameSession("g", BoardFactory.startingPosition(), null, 1, time);
        session.initClock(0, 0, 0);
        play("e2e4");
        wait(100_000);

        assertNull(session.flaggedSide());
        assertFalse(session.isTimed());
    }
}
