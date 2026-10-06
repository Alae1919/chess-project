package com.chess.engine.core.search;

import com.chess.engine.core.Move;
import com.chess.engine.core.Position;
import com.chess.engine.core.eval.ClassicalEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Searcher")
class SearcherTest {

    private static Searcher searcher() { return new Searcher(new ClassicalEvaluator(), 8); }

    private static SearchResult search(String fen, SearchLimits limits) {
        return searcher().search(Position.fromFen(fen), limits);
    }

    // ---- forced mates, against a brute-force oracle ---------------------------------------------

    private record MateCase(String fen, int length, Set<String> winningMoves) { }

    private static List<MateCase> mateCases() throws Exception {
        List<MateCase> cases = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                SearcherTest.class.getResourceAsStream("/engine/mates.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                cases.add(new MateCase(parts[0], Integer.parseInt(parts[1]), Set.of(parts[2].trim().split(" "))));
            }
        }
        return cases;
    }

    @Test
    @DisplayName("finds the shortest forced mate, with a move that really mates, in 36 positions python-chess brute-forced")
    void findsForcedMates() throws Exception {
        List<MateCase> cases = mateCases();
        assertEquals(36, cases.size());
        for (MateCase c : cases) {
            SearchResult result = search(c.fen(), SearchLimits.depth(2 * c.length() + 4));

            assertTrue(c.winningMoves().contains(result.bestMoveUci()),
                c.fen() + ": played " + result.bestMoveUci() + ", mating moves are " + c.winningMoves());
            assertTrue(result.isMate(), c.fen() + ": score " + result.score());
            assertEquals(c.length(), result.mateIn(), c.fen() + ": shortest mate");
        }
    }

    @Test
    @DisplayName("a mate score is the same when the table already holds the position from an earlier search")
    void mateDistanceSurvivesTheTable() throws Exception {
        Searcher s = searcher();
        for (MateCase c : mateCases()) {
            if (c.length() < 2) continue;
            SearchResult first = s.search(Position.fromFen(c.fen()), SearchLimits.depth(2 * c.length() + 4));
            SearchResult again = s.search(Position.fromFen(c.fen()), SearchLimits.depth(2 * c.length() + 4));
            assertEquals(c.length(), first.mateIn(), c.fen());
            assertEquals(c.length(), again.mateIn(), "second search of " + c.fen());
        }
    }

    // ---- tactics -------------------------------------------------------------------------------

    @Test
    @DisplayName("takes a queen that is simply hanging")
    void takesAHangingQueen() {
        SearchResult r = search("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1", SearchLimits.depth(3));

        assertEquals("e4d5", r.bestMoveUci());
        assertTrue(r.score() > 0, "a pawn up against a bare king: " + r.score());
    }

    @Test
    @DisplayName("answers a check with a legal move that gets out of it")
    void answersACheck() {
        String fen = "4k3/8/8/8/8/8/4r3/4K3 w - - 0 1";   // the rook on e2 checks the king; Kxe2 is the only way out of check
        Position pos = Position.fromFen(fen);
        assertTrue(pos.inCheck());

        SearchResult r = searcher().search(pos, SearchLimits.depth(4));

        assertNotEquals(Move.NONE, r.bestMove());
        pos.makeMove(r.bestMove());
        assertFalse(pos.isAttacked(pos.kingSquare(Position.WHITE), Position.BLACK), "the king must not be left in check");
    }

    @Test
    @DisplayName("prefers winning material to a quiet move")
    void winsMaterial() {
        // the black rook on d4 is undefended and attacked by the rook on d1
        SearchResult r = search("4k3/8/8/8/3r4/8/8/3RK3 w - - 0 1", SearchLimits.depth(4));

        assertEquals("d1d4", r.bestMoveUci());
    }

    @Test
    @DisplayName("scores positions nobody can win as a draw")
    void drawnPositions() {
        SearchResult r = search("4k3/8/8/8/8/8/8/3NK3 w - - 0 1", SearchLimits.depth(6));

        assertEquals(0, r.score());
    }

    @Test
    @DisplayName("says so when there is no move: checkmate and stalemate")
    void noLegalMoves() {
        SearchResult mated = search("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1", SearchLimits.depth(3));
        assertEquals(Move.NONE, mated.bestMove());
        assertEquals(-Searcher.MATE, mated.score());

        SearchResult stalemated = search("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1", SearchLimits.depth(3));
        assertEquals(Move.NONE, stalemated.bestMove());
        assertEquals(0, stalemated.score());
    }

    // ---- limits --------------------------------------------------------------------------------

    @Test
    @DisplayName("stops near a node limit, even a tiny one, and still returns a legal move")
    void respectsNodeLimits() {
        for (long limit : new long[]{50, 500, 5_000}) {
            Position pos = Position.fromFen(Position.START_FEN);
            SearchResult r = searcher().search(pos, SearchLimits.nodes(limit));

            assertTrue(r.nodes() <= limit + limit / 2 + 64, "limit " + limit + ", searched " + r.nodes());
            assertNotEquals(Move.NONE, pos.parseUci(r.bestMoveUci()), "must be a legal move");
        }
    }

    @Test
    @DisplayName("stops near the time it was given")
    void respectsMoveTime() {
        long start = System.nanoTime();
        SearchResult r = search(Position.START_FEN, SearchLimits.moveTime(200));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 600, "took " + elapsedMs + " ms");
        assertTrue(r.depth() >= 3, "depth " + r.depth());
    }

    @Test
    @DisplayName("budgets from the clock: a short clock gets a short search")
    void budgetsFromTheClock() {
        long start = System.nanoTime();
        SearchLimits clock = new SearchLimits(0, 0, 0, 2_000, 2_000, 0, 0, 0, 1);
        searcher().search(Position.fromFen(Position.START_FEN), clock);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 800, "two seconds on the clock should not take " + elapsedMs + " ms");
    }

    @Test
    @DisplayName("a single legal move is played at once when the clock is running")
    void singleLegalMove() {
        long start = System.nanoTime();
        SearchResult r = search("k7/8/1K6/8/8/8/8/7R b - - 0 1", SearchLimits.moveTime(1000));

        assertEquals("a8b8", r.bestMoveUci());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 200);
    }

    // ---- properties ----------------------------------------------------------------------------

    @Test
    @DisplayName("leaves the position as it found it")
    void leavesThePositionAlone() {
        Position pos = Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        String before = pos.toFen();
        long key = pos.key();

        searcher().search(pos, SearchLimits.depth(5));

        assertEquals(before, pos.toFen());
        assertEquals(key, pos.key());
    }

    @Test
    @DisplayName("the same search twice gives the same answer")
    void deterministic() {
        String fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
        SearchResult a = search(fen, SearchLimits.depth(6));
        SearchResult b = search(fen, SearchLimits.depth(6));

        assertEquals(a.bestMoveUci(), b.bestMoveUci());
        assertEquals(a.nodes(), b.nodes());
        assertEquals(a.score(), b.score());
    }

    @Test
    @DisplayName("MultiPV returns different moves, best first")
    void multiPv() {
        SearchResult r = search(Position.START_FEN, SearchLimits.depth(5).withMultiPv(3));

        assertEquals(3, r.lines().size());
        assertEquals(3, r.lines().stream().map(SearchResult.Line::move).distinct().count());
        for (int i = 1; i < r.lines().size(); i++) {
            assertTrue(r.lines().get(i - 1).score() >= r.lines().get(i).score(), Arrays.toString(r.lines().toArray()));
        }
        assertEquals(r.lines().get(0).move(), r.bestMove());
    }

    @Test
    @DisplayName("reports progress after each iteration")
    void reportsProgress() {
        List<SearchInfo> seen = new ArrayList<>();

        searcher().search(Position.startPosition(), SearchLimits.depth(5), seen::add);

        assertEquals(5, seen.size());
        for (int i = 0; i < seen.size(); i++) assertEquals(i + 1, seen.get(i).depth());
        assertTrue(seen.get(4).pv().length >= 1);
    }

    @Test
    @DisplayName("is quick enough to be worth having: a quarter of a million positions a second at least")
    void speed() {
        Searcher s = searcher();
        s.search(Position.startPosition(), SearchLimits.depth(7));                // warm up the JIT
        long start = System.nanoTime();
        SearchResult r = s.search(Position.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"),
                                  SearchLimits.depth(8));
        double seconds = (System.nanoTime() - start) / 1e9;

        long nps = (long) (r.nodes() / seconds);
        System.out.println("search speed: " + nps + " nodes/s over " + r.nodes() + " nodes");
        assertTrue(nps > 250_000, "only " + nps + " nodes a second");
    }
}
