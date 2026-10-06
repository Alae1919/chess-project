package com.chess.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalysisService")
class AnalysisServiceTest {

    private final AnalysisService service = new AnalysisService();

    @Test
    @DisplayName("scores are from White's point of view, whoever is to move")
    void whitePointOfView() {
        // White a queen up
        assertTrue(service.analyse("4k3/8/8/8/8/8/8/3QK3 w - - 0 1").scoreCp() > 500);
        assertTrue(service.analyse("4k3/8/8/8/8/8/8/3QK3 b - - 0 1").scoreCp() > 500);
        // Black a queen up
        assertTrue(service.analyse("3qk3/8/8/8/8/8/8/4K3 w - - 0 1").scoreCp() < -500);
        assertTrue(service.analyse("3qk3/8/8/8/8/8/8/4K3 b - - 0 1").scoreCp() < -500);
    }

    @Test
    @DisplayName("names the best move and says when it is mate")
    void bestMoveAndMate() {
        var result = service.analyse("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1");

        assertEquals("a1a8", result.bestMove());
        assertTrue(result.mate());
        assertTrue(result.scoreCp() > 30_000);
    }

    @Test
    @DisplayName("a mate for Black is a large negative score")
    void blackMates() {
        var result = service.analyse("r5k1/8/8/8/8/8/5PPP/6K1 b - - 0 1");

        assertTrue(result.mate());
        assertTrue(result.scoreCp() < -30_000);
    }

    @Test
    @DisplayName("a position with no moves has no best move")
    void noMoves() {
        var result = service.analyse("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");

        assertNull(result.bestMove());
    }

    @Test
    @DisplayName("text that is not a position is refused")
    void badFen() {
        assertThrows(IllegalArgumentException.class, () -> service.analyse("nonsense"));
    }
}
