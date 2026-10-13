package com.chess.application;

import com.chess.api.dto.ShowcaseDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The landing page's analysis is a real search of the story's position, reported faithfully. */
@DisplayName("ShowcaseService")
class ShowcaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");
    private static final int DEPTH = 8;   // shallower than production, so the test is quick
    private static ShowcaseDto.Showcase showcase;

    @BeforeAll
    static void analyse() throws Exception {
        var service = new ShowcaseService(false, Clock.fixed(NOW, ZoneOffset.UTC), DEPTH);
        showcase = service.showcase().get(60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("tells Légal's story: 5.Nxe5 in the position after 4...g6")
    void theStory() {
        assertEquals("rn1qkbnr/ppp2p1p/3p2p1/4p3/2B1P1b1/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 5", showcase.startFen());
        assertEquals(new ShowcaseDto.MoveRef("f3e5", "Nxe5"), showcase.playerMove());
        assertEquals("rn1qkbnr/ppp2p1p/3p2p1/4N3/2B1P1b1/2N5/PPPP1PPP/R1BQK2R b KQkq - 0 5", showcase.fen());
        assertEquals(NOW, showcase.analysedAt());
    }

    @Test
    @DisplayName("taking the queen is mate in two for White, and the engine shows how")
    void theTrap() {
        var trap = showcase.trap();
        assertEquals(new ShowcaseDto.MoveRef("g4d1", "Bxd1"), trap.move());
        assertEquals(new ShowcaseDto.Score(null, 2), trap.score());
        assertEquals(List.of("Bxf7+", "Ke7", "Nd5#"), trap.refutation());
        assertTrue(trap.nodes() > 0);
    }

    @Test
    @DisplayName("the engine does not take the queen, and its reply is the first of its lines")
    void theReply() {
        var engine = showcase.engine();
        assertNotEquals("g4d1", engine.bestMove().uci());
        assertEquals(DEPTH, engine.depth());
        assertEquals(ShowcaseService.LINES, engine.lines().size());
        assertEquals(engine.bestMove(), engine.lines().get(0).move());
        assertEquals(engine.score(), engine.lines().get(0).score());
        assertEquals(engine.bestMove().san(), engine.lines().get(0).pv().get(0));
        // White is better after the exchange of the knight for the queen's attacker
        assertNotNull(engine.score().cp());
        assertTrue(engine.score().cp() > 0, "scores are from White's point of view");
    }

    @Test
    @DisplayName("lines are best first, from White's point of view (Black to move wants them low)")
    void linesBestFirst() {
        var lines = showcase.engine().lines();
        for (int i = 1; i < lines.size(); i++) {
            assertTrue(lines.get(i).score().cp() >= lines.get(i - 1).score().cp(), "line " + i);
        }
    }

    @Test
    @DisplayName("reports one iteration per depth, with growing totals that end at the search's own")
    void iterations() {
        var engine = showcase.engine();
        var its = engine.iterations();
        assertEquals(DEPTH, its.size());
        for (int i = 0; i < its.size(); i++) {
            assertEquals(i + 1, its.get(i).depth());
            if (i > 0) assertTrue(its.get(i).nodes() > its.get(i - 1).nodes());
        }
        var last = its.get(its.size() - 1);
        assertEquals(engine.nodes(), last.nodes());
        assertEquals(engine.bestMove(), last.bestMove());
    }

    @Test
    @DisplayName("the position after the reply is the reply played on the searched position")
    void replyFen() {
        var pos = com.chess.engine.core.Position.fromFen(showcase.fen());
        String best = showcase.engine().bestMove().uci();
        for (int m : pos.legalMoves()) {
            if (com.chess.engine.core.Move.toUci(m).equals(best)) pos.makeMove(m);
        }
        assertEquals(pos.toFen(), showcase.replyFen());
        assertNotEquals(showcase.fen(), showcase.replyFen());
    }

    @Test
    @DisplayName("scores turn to White's point of view, mates included")
    void whiteScore() {
        String whiteToMove = "4k3/8/8/8/8/8/8/4K3 w - - 0 1";
        String blackToMove = "4k3/8/8/8/8/8/8/4K3 b - - 0 1";
        assertEquals(new ShowcaseDto.Score(120, null), ShowcaseService.whiteScore(whiteToMove, 120));
        assertEquals(new ShowcaseDto.Score(-120, null), ShowcaseService.whiteScore(blackToMove, 120));
        // the side to move mates in 2 (3 plies): positive for White to move, negative for Black to move
        int mateIn3Plies = com.chess.engine.core.search.Searcher.MATE - 3;
        assertEquals(new ShowcaseDto.Score(null, 2), ShowcaseService.whiteScore(whiteToMove, mateIn3Plies));
        assertEquals(new ShowcaseDto.Score(null, -2), ShowcaseService.whiteScore(blackToMove, mateIn3Plies));
    }

    @Test
    @DisplayName("a line in SAN stops at the first move that is not legal")
    void sanLine() {
        String start = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertEquals(List.of("e4", "e5", "Nf3"), ShowcaseService.san(start, List.of("e2e4", "e7e5", "g1f3", "a1a8")));
    }
}
