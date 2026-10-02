package com.chess.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EloCalculator")
class EloCalculatorTest {

    @Test
    @DisplayName("equal ratings: the winner gains half of K")
    void equalRatings() {
        assertEquals(16, EloCalculator.delta(1200, 1200, 1.0));
        assertEquals(-16, EloCalculator.delta(1200, 1200, 0.0));
        assertEquals(0, EloCalculator.delta(1200, 1200, 0.5));
    }

    @Test
    @DisplayName("beating a stronger player is worth more than beating a weaker one")
    void upsetsPayMore() {
        assertEquals(29, EloCalculator.delta(1200, 1600, 1.0));
        assertEquals(3, EloCalculator.delta(1600, 1200, 1.0));
    }

    @Test
    @DisplayName("a draw moves the lower-rated player up")
    void drawFavoursTheUnderdog() {
        assertTrue(EloCalculator.delta(1200, 1600, 0.5) > 0);
        assertTrue(EloCalculator.delta(1600, 1200, 0.5) < 0);
    }

    @Test
    @DisplayName("expected scores of two players add up to 1")
    void expectedScoresAreComplementary() {
        assertEquals(1.0,
            EloCalculator.expectedScore(1350, 1500) + EloCalculator.expectedScore(1500, 1350), 1e-9);
    }

    @Test
    @DisplayName("ratings stop at the floor")
    void floor() {
        assertEquals(EloCalculator.FLOOR, EloCalculator.apply(105, -32));
        assertEquals(1232, EloCalculator.apply(1200, 32));
    }
}
