package com.chess.engine.uci;

import com.chess.engine.core.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UCI engine")
class UciEngineTest {

    private ByteArrayOutputStream captured;
    private UciEngine engine;

    @BeforeEach
    void start() {
        captured = new ByteArrayOutputStream();
        engine = new UciEngine(new ByteArrayInputStream(new byte[0]), captured);
    }

    @AfterEach
    void stop() {
        engine.handle("stop");
    }

    private String output() { return captured.toString(StandardCharsets.UTF_8); }

    private String awaitOutput(String marker, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (output().contains(marker)) return output();
            Thread.sleep(10);
        }
        fail("timed out waiting for '" + marker + "'; output so far:\n" + output());
        return output();
    }

    private String bestMoveAfter(String goCommand) throws InterruptedException {
        engine.handle(goCommand);
        Matcher m = Pattern.compile("bestmove (\\S+)").matcher(awaitOutput("bestmove", 10_000));
        assertTrue(m.find());
        return m.group(1);
    }

    @Test
    @DisplayName("introduces itself, lists its options and says it is done")
    void uci() {
        engine.handle("uci");

        String out = output();
        assertTrue(out.contains("id name"));
        assertTrue(out.contains("option name Hash type spin"));
        assertTrue(out.contains("option name MultiPV type spin"));
        assertTrue(out.contains("option name Level type spin"));
        assertTrue(out.trim().endsWith("uciok"));
    }

    @Test
    @DisplayName("answers isready, ignores what it doesn't know, and quits")
    void basics() {
        engine.handle("isready");
        assertTrue(output().contains("readyok"));

        assertTrue(engine.handle("somethingelse entirely"));
        assertTrue(engine.handle(""));
        assertFalse(engine.handle("quit"));
    }

    @Test
    @DisplayName("sets up a position from the start and a list of moves")
    void positionFromStartpos() {
        engine.handle("position startpos moves e2e4 e7e5");
        engine.handle("d");

        assertTrue(output().contains("Fen: rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"), output());
    }

    @Test
    @DisplayName("sets up a position from a FEN, then plays moves on it")
    void positionFromFen() {
        engine.handle("position fen 4k3/8/8/8/8/8/4P3/4K3 w - - 0 1 moves e2e4");
        engine.handle("d");

        assertTrue(output().contains("Fen: 4k3/8/8/8/4P3/8/8/4K3 b - e3 0 1"), output());
    }

    @Test
    @DisplayName("an illegal move in the list is reported and the rest is dropped")
    void illegalMoveInPosition() {
        engine.handle("position startpos moves e2e4 e2e4 g8f6");
        engine.handle("d");

        assertTrue(output().contains("info string illegal move e2e4"));
        assertTrue(output().contains("Fen: rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"), output());
    }

    @Test
    @DisplayName("a bad FEN is reported and the old position stays")
    void badFen() {
        engine.handle("position fen not a fen");
        engine.handle("d");

        assertTrue(output().contains("info string bad position"));
        assertTrue(output().contains("Fen: " + Position.START_FEN));
    }

    @Test
    @DisplayName("finds a mate in one and says so in UCI's terms")
    void searchesForMate() throws Exception {
        engine.handle("position fen 6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");

        assertEquals("a1a8", bestMoveAfter("go depth 5"));
        assertTrue(output().contains("score mate 1"), output());
    }

    @Test
    @DisplayName("reports progress with depth, score, nodes, speed and the line")
    void infoLines() throws Exception {
        engine.handle("position startpos");

        bestMoveAfter("go depth 4");

        assertTrue(Pattern.compile("info depth 4 score cp -?\\d+ nodes \\d+ nps \\d+ hashfull \\d+ time \\d+ pv [a-h][1-8][a-h][1-8]")
                          .matcher(output()).find(), output());
    }

    @Test
    @DisplayName("go movetime ends in about that time")
    void movetime() throws Exception {
        engine.handle("position startpos");
        long start = System.nanoTime();

        bestMoveAfter("go movetime 150");

        assertTrue((System.nanoTime() - start) / 1_000_000 < 800);
    }

    @Test
    @DisplayName("stop ends an endless search and gets a move")
    void stopEndsInfiniteSearch() throws Exception {
        engine.handle("position startpos");
        engine.handle("go infinite");
        awaitOutput("info depth", 10_000);

        engine.handle("stop");

        assertTrue(output().contains("bestmove "), output());
    }

    @Test
    @DisplayName("MultiPV asks for several lines")
    void multiPv() throws Exception {
        engine.handle("setoption name MultiPV value 3");
        engine.handle("position startpos");

        bestMoveAfter("go depth 4");

        assertTrue(output().contains("multipv 3"), output());
    }

    @Test
    @DisplayName("Level plays like the app's AI at that level: a legal move, quickly")
    void level() throws Exception {
        engine.handle("setoption name Level value 1");
        engine.handle("position startpos");

        String move = bestMoveAfter("go");

        assertNotEquals(com.chess.engine.core.Move.NONE, Position.startPosition().parseUci(move));
    }

    @Test
    @DisplayName("perft counts the positions: 8,902 after three moves from the start")
    void perft() {
        engine.handle("position startpos");
        engine.handle("perft 3");

        assertTrue(output().contains("Nodes searched: 8902"), output());
        assertTrue(output().contains("e2e4: 600"), "per-move counts, as perft divide prints them");
    }

    @Test
    @DisplayName("go perft is the same as perft")
    void goPerft() {
        engine.handle("position startpos");
        engine.handle("go perft 2");

        assertTrue(output().contains("Nodes searched: 400"), output());
    }

    @Test
    @DisplayName("eval prints the static score of the position, from the side to move's point of view")
    void eval() {
        engine.handle("setoption name Eval value classical");
        engine.handle("position fen 4k3/8/8/8/8/8/PPPPPPPP/4K3 w - - 0 1");
        engine.handle("eval");
        engine.handle("position fen 4k3/pppppppp/8/8/8/8/8/4K3 b - - 0 1");
        engine.handle("eval");

        Matcher m = Pattern.compile("Static evaluation: (-?\\d+) cp").matcher(output());
        assertTrue(m.find(), output());
        int white = Integer.parseInt(m.group(1));
        assertTrue(m.find(), output());
        int black = Integer.parseInt(m.group(1));
        assertTrue(white > 300, "eight extra pawns are worth a lot: " + white);
        assertTrue(black > 300, "the same for the side to move, as Black: " + black);
    }

    // ---- bench -------------------------------------------------------------------------------------

    @Test
    @DisplayName("the bench positions are all valid, and there are enough of them to be a fair sample")
    void benchPositionsAreValid() {
        assertTrue(Bench.POSITIONS.size() >= 30);
        for (String fen : Bench.POSITIONS) assertDoesNotThrow(() -> Position.fromFen(fen), fen);
    }

    @Test
    @DisplayName("bench is deterministic: the same node count every time, which is what makes it a signature")
    void benchIsDeterministic() {
        Bench.Result a = Bench.run(4, 4, com.chess.engine.core.eval.ClassicalEvaluator::new);
        Bench.Result b = Bench.run(4, 4, com.chess.engine.core.eval.ClassicalEvaluator::new);

        assertEquals(a.nodes(), b.nodes());
        assertTrue(a.nodes() > 1_000);
    }

    @Test
    @DisplayName("the bench command prints the node count and speed")
    void benchCommand() {
        engine.handle("bench 3");

        assertTrue(Pattern.compile("Nodes searched: \\d+").matcher(output()).find(), output());
        assertTrue(output().contains("Nodes/second:"));
    }
}
