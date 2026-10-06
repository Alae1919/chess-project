package com.chess.engine.player;

import com.chess.domain.board.Board;
import com.chess.domain.board.FenParser;
import com.chess.domain.model.Move;
import com.chess.domain.rules.MoveGenerator;
import com.chess.engine.core.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiPlayer")
class AiPlayerTest {

    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    private static Board board(String fen) { return FenParser.parse(fen); }

    private static boolean isLegal(Board b, Move m) {
        return MoveGenerator.generateLegalMoves(b).stream().anyMatch(x -> x.toUci().equals(m.toUci()));
    }

    /** FENs of the positions python-chess brute-forced as a mate in one. */
    private static List<String> matesInOne() throws Exception {
        List<String> fens = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                AiPlayerTest.class.getResourceAsStream("/engine/mates.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                String[] parts = line.split(",");
                if (parts[1].equals("1")) fens.add(parts[0]);
            }
        }
        return fens;
    }

    // ---- the levels -----------------------------------------------------------------------------

    @Test
    @DisplayName("every level plays a legal move, from the start and from a busy position")
    void everyLevelPlaysLegalMoves() {
        for (int level = AiLevel.EASIEST; level <= 4; level++) {
            for (String fen : List.of(Position.START_FEN, KIWIPETE)) {
                Board b = board(fen);
                Move m = new AiPlayer(com.chess.domain.model.Color.WHITE, level, new Random(3)).chooseMove(b);
                assertTrue(isLegal(b, m), "level " + level + " played " + m.toUci() + " in " + fen);
            }
        }
    }

    @Test
    @DisplayName("the strongest level plays a legal move too")
    void strongestLevel() {
        Board b = board(KIWIPETE);
        Move m = new AiPlayer(com.chess.domain.model.Color.WHITE, 6, new Random(3)).chooseMove(b, b, List.of(), 3_000, 3_000, 0);

        assertTrue(isLegal(b, m));
    }

    @Test
    @DisplayName("even the easiest level takes a mate in one: the weak levels are careless, not blind")
    void everyLevelSeesAMateInOne() throws Exception {
        for (int level = AiLevel.EASIEST; level <= 4; level++) {
            for (String fen : matesInOne()) {
                Board b = board(fen);
                Move m = new AiPlayer(com.chess.domain.model.Color.WHITE, level, new Random(9)).chooseMove(b);

                Position after = Position.fromFen(fen);
                after.makeMove(after.parseUci(m.toUci()));
                assertTrue(after.inCheck() && after.legalMoves().length == 0,
                    "level " + level + " played " + m.toUci() + " instead of mating in " + fen);
            }
        }
    }

    @Test
    @DisplayName("the same seed gives the same move, and the easiest level varies its moves from game to game")
    void randomnessIsSeeded() {
        Board b = board(Position.START_FEN);
        String first = new AiPlayer(com.chess.domain.model.Color.WHITE, 1, new Random(5)).chooseMove(b).toUci();
        String again = new AiPlayer(com.chess.domain.model.Color.WHITE, 1, new Random(5)).chooseMove(b).toUci();
        assertEquals(first, again);

        // one running sequence of random numbers: the first value of Random(0), Random(1), ... is almost the same
        Random sequence = new Random(42);
        Set<String> seen = new HashSet<>();
        for (int game = 0; game < 40; game++) {
            seen.add(new AiPlayer(com.chess.domain.model.Color.WHITE, 1, sequence).chooseMove(b).toUci());
        }
        assertTrue(seen.size() >= 3, "level 1 should vary its moves: " + seen);
    }

    @Test
    @DisplayName("levels 4 to 6 always take the best move: no randomness")
    void strongLevelsAreDeterministicEnoughToRepeat() {
        // a forced tactic: the queen is hanging
        Board b = board("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1");
        for (int seed = 0; seed < 5; seed++) {
            assertEquals("e4d5", new AiPlayer(com.chess.domain.model.Color.WHITE, 4, new Random(seed)).chooseMove(b).toUci());
        }
    }

    // ---- time -----------------------------------------------------------------------------------

    @Test
    @DisplayName("level 4 thinks for about a seventh of a second")
    void levelFourIsQuick() {
        Board b = board(KIWIPETE);
        long start = System.nanoTime();
        new AiPlayer(com.chess.domain.model.Color.WHITE, 4).chooseMove(b);

        assertTrue((System.nanoTime() - start) / 1_000_000 < 700);
    }

    @Test
    @DisplayName("on a nearly empty clock even the strongest level moves fast")
    void respectsTheGameClock() {
        Board b = board(KIWIPETE);
        long start = System.nanoTime();
        new AiPlayer(com.chess.domain.model.Color.WHITE, 6).chooseMove(b, b, List.of(), 400, 400, 0);

        assertTrue((System.nanoTime() - start) / 1_000_000 < 300, "took " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    @Test
    @DisplayName("a search can be stopped from another thread and still returns a legal move")
    void canBeStopped() throws Exception {
        Board b = board(KIWIPETE);
        AiPlayer ai = new AiPlayer(com.chess.domain.model.Color.WHITE, 6);
        Move[] result = new Move[1];
        Thread worker = new Thread(() -> result[0] = ai.chooseMove(b));
        long start = System.nanoTime();
        worker.start();
        Thread.sleep(100);
        ai.stop();
        worker.join(3_000);

        assertFalse(worker.isAlive(), "the search ignored stop()");
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_500);
        assertTrue(isLegal(b, result[0]));
    }

    // ---- the game so far ------------------------------------------------------------------------

    @Test
    @DisplayName("positions earlier in the game count: a replay of the moves makes repetitions visible")
    void historyIsReplayed() {
        Board start = board(Position.START_FEN);
        List<String> moves = List.of("g1f3", "g8f6", "f3g1", "f6g8");
        Position pos = Position.fromFen(Position.START_FEN);
        for (String m : moves) pos.makeMove(pos.parseUci(m));
        Board current = board(pos.toFen());

        Position withHistory = AiPlayer.positionWithHistory(current, start, moves);

        assertTrue(withHistory.isRepetition(), "the start position has come back");
        assertEquals(4, withHistory.ply());
    }

    @Test
    @DisplayName("history that doesn't lead to the board is ignored rather than trusted")
    void inconsistentHistoryFallsBack() {
        Board start = board(Position.START_FEN);
        Board current = board(KIWIPETE);

        Position p = AiPlayer.positionWithHistory(current, start, List.of("e2e4", "e7e5"));

        assertEquals(0, p.ply());
        assertEquals(KIWIPETE, p.toFen());
        assertEquals(KIWIPETE, AiPlayer.positionWithHistory(current, start, List.of("zzzz")).toFen());
    }

    @Test
    @DisplayName("asking for a move in a finished game is an error")
    void terminalPositionIsAnError() {
        Board mated = board("R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");

        assertThrows(IllegalStateException.class,
            () -> new AiPlayer(com.chess.domain.model.Color.BLACK, 3).chooseMove(mated));
    }
}
