package com.chess.engine.core.eval.nnue;

import com.chess.engine.core.Move;
import com.chess.engine.core.Position;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NnueEvaluator")
class NnueEvaluatorTest {

    private static NnueNetwork net;

    @BeforeAll
    static void loadNetwork() throws IOException {
        net = NnueNetwork.parse(NnueNetworkTest.tinyBytes());
    }

    record Golden(String fen, int score) { }

    static List<Golden> golden(String resource) throws IOException {
        List<Golden> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                NnueEvaluatorTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                int comma = line.lastIndexOf(',');
                rows.add(new Golden(line.substring(0, comma), Integer.parseInt(line.substring(comma + 1).trim())));
            }
        }
        return rows;
    }

    @Test
    @DisplayName("gives exactly the scores the Python reference computes, for 1000 positions")
    void matchesTheReference() throws IOException {
        List<Golden> rows = golden("/nnue/tiny-golden.csv");
        assertEquals(1000, rows.size());
        NnueEvaluator eval = new NnueEvaluator(net);

        for (Golden g : rows) {
            Position pos = Position.fromFen(g.fen());
            eval.reset(pos);
            assertEquals(g.score(), eval.evaluate(pos), g.fen());
        }
    }

    @Test
    @DisplayName("the scores differ between positions and are not all zero (the check above is not vacuous)")
    void scoresVary() throws IOException {
        long distinct = golden("/nnue/tiny-golden.csv").stream().map(Golden::score).distinct().count();
        assertTrue(distinct > 300, "only " + distinct + " distinct scores");
    }

    /** Every legal move from {@code fen}: the running sums must equal a rebuild, and return after taking it back. */
    private void checkEveryMove(String fen) {
        Position pos = Position.fromFen(fen);
        NnueEvaluator eval = new NnueEvaluator(net);
        eval.reset(pos);
        short[][] root = {eval.current()[0].clone(), eval.current()[1].clone()};

        int[] moves = pos.legalMoves();
        assertTrue(moves.length > 0, fen);
        for (int move : moves) {
            pos.makeMove(move);
            eval.onMake(pos, move);
            assertArrayEquals(rebuilt(pos)[0], eval.current()[0], fen + " after " + Move.toUci(move) + " (white view)");
            assertArrayEquals(rebuilt(pos)[1], eval.current()[1], fen + " after " + Move.toUci(move) + " (black view)");
            assertEquals(eval.evaluateFromScratch(pos), eval.evaluate(pos), fen + " after " + Move.toUci(move));
            pos.unmakeMove();
            eval.onUnmake();
            assertArrayEquals(root[0], eval.current()[0]);
            assertArrayEquals(root[1], eval.current()[1]);
        }
    }

    private short[][] rebuilt(Position pos) {
        NnueEvaluator fresh = new NnueEvaluator(net);
        fresh.reset(pos);
        return fresh.current();
    }

    @Test
    @DisplayName("castling moves the rook as well, for both sides and both wings")
    void castling() {
        checkEveryMove("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        checkEveryMove("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
    }

    @Test
    @DisplayName("en passant removes the pawn that is not on the square moved to")
    void enPassant() {
        checkEveryMove("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        checkEveryMove("4k3/8/8/8/3pP3/8/8/4K3 b - e3 0 1");
    }

    @Test
    @DisplayName("promotions, with and without a capture, change the pawn into the new piece")
    void promotions() {
        checkEveryMove("1n2k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        checkEveryMove("4k3/8/8/8/8/8/p7/1N2K3 b - - 0 1");
        checkEveryMove("r3k2r/1P6/8/8/8/8/6p1/R3K1R1 w Qkq - 0 1");
    }

    @Test
    @DisplayName("captures of every kind of piece")
    void captures() {
        checkEveryMove("r1bqkbnr/pppp1ppp/2n5/4p3/3PP3/5N2/PPP2PPP/RNBQKB1R b KQkq - 0 3");
        checkEveryMove("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR w KQkq - 0 4");
        checkEveryMove("r2q1rk1/pp2bppp/2n1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQ1RK1 w - - 0 9");
    }

    @Test
    @DisplayName("along long random games the running sums always equal a rebuild, and unwind exactly")
    void randomGames() {
        Random random = new Random(11);
        for (int game = 0; game < 40; game++) {
            Position pos = Position.startPosition();
            NnueEvaluator eval = new NnueEvaluator(net);
            eval.reset(pos);
            short[][] root = {eval.current()[0].clone(), eval.current()[1].clone()};
            int made = 0;
            while (made < 200) {
                int[] moves = pos.legalMoves();
                if (moves.length == 0 || pos.isInsufficientMaterial()) break;
                int move = moves[random.nextInt(moves.length)];
                pos.makeMove(move);
                eval.onMake(pos, move);
                made++;
                assertEquals(eval.evaluateFromScratch(pos), eval.evaluate(pos), "game " + game + " move " + made + ": " + pos.toFen());
            }
            for (; made > 0; made--) {
                pos.unmakeMove();
                eval.onUnmake();
            }
            assertArrayEquals(root[0], eval.current()[0]);
            assertArrayEquals(root[1], eval.current()[1]);
        }
    }

    @Test
    @DisplayName("a null move does not change the sums, only whose turn it is")
    void nullMoveKeepsTheSums() {
        Position pos = Position.fromFen("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3");
        NnueEvaluator eval = new NnueEvaluator(net);
        eval.reset(pos);
        short[] white = eval.current()[0].clone();

        pos.makeNullMove();

        assertArrayEquals(white, eval.current()[0]);
        assertEquals(eval.evaluateFromScratch(pos), eval.evaluate(pos));
    }

    @Test
    @DisplayName("the stack grows past its first size on a very deep line")
    void deepLine() {
        Position pos = Position.fromFen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        NnueEvaluator eval = new NnueEvaluator(net);
        eval.reset(pos);
        // a rook and a king shuffling back and forth: 400 plies deep, with no capture to end it
        String[] cycle = {"a1a2", "e8e7", "a2a1", "e7e8"};
        for (int i = 0; i < 400; i++) {
            int move = pos.parseUci(cycle[i % 4]);
            assertNotEquals(Move.NONE, move, "move " + i + " in " + pos.toFen());
            pos.makeMove(move);
            eval.onMake(pos, move);
        }

        assertEquals(eval.evaluateFromScratch(pos), eval.evaluate(pos));
    }

    @Test
    @DisplayName("a search with the network is deterministic and plays a legal move; it still finds a mate")
    void searchesWithTheNetwork() {
        Position pos = Position.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");

        SearchResult first = new Searcher(new NnueEvaluator(net), 1).search(pos.copy(), SearchLimits.depth(6));
        SearchResult second = new Searcher(new NnueEvaluator(net), 1).search(pos.copy(), SearchLimits.depth(6));

        assertEquals("a1a8", Move.toUci(first.bestMove()));
        assertTrue(first.isMate());
        assertEquals(first.nodes(), second.nodes());

        Position start = Position.startPosition();
        SearchResult opening = new Searcher(new NnueEvaluator(net), 1).search(start.copy(), SearchLimits.depth(5));
        assertNotEquals(Move.NONE, start.parseUci(Move.toUci(opening.bestMove())));
    }
}
