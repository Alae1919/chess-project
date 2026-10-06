package com.chess.engine.core.eval;

import com.chess.engine.core.Position;
import com.chess.engine.core.eval.nnue.NnueEvaluator;
import com.chess.engine.core.eval.nnue.NnueNetwork;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HybridEvaluator")
class HybridEvaluatorTest {

    private static NnueNetwork net;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = HybridEvaluatorTest.class.getResourceAsStream("/nnue/tiny.nnue")) {
            net = NnueNetwork.load(in);
        }
    }

    private static int scoreOf(Evaluator evaluator, String fen) {
        Position pos = Position.fromFen(fen);
        evaluator.reset(pos);
        return evaluator.evaluate(pos);
    }

    @Test
    @DisplayName("scores a lone-king ending like the classical evaluation, which knows how to drive the king to a corner")
    void bareKing() {
        for (String fen : new String[] {
                "8/8/8/4k3/8/8/8/R3K3 w - - 0 1",           // rook against a bare king
                "8/8/3k4/8/8/2B5/3N4/4K3 w - - 0 1",        // bishop and knight
                "4k3/8/8/8/8/8/8/4K2Q b - - 0 1" }) {       // the bare side to move
            HybridEvaluator hybrid = new HybridEvaluator(new NnueEvaluator(net));

            assertEquals(scoreOf(new ClassicalEvaluator(), fen), scoreOf(hybrid, fen), fen);
        }
    }

    @Test
    @DisplayName("everywhere else it is the network, exactly")
    void otherwiseTheNetwork() {
        for (String fen : new String[] {
                Position.START_FEN,
                "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
                "8/5p2/4k3/8/8/4K3/4P3/8 w - - 0 1",           // a pawn each: neither king is bare
                "4kb2/8/8/8/8/8/8/4K2R w K - 0 1" }) {
            HybridEvaluator hybrid = new HybridEvaluator(new NnueEvaluator(net));

            assertEquals(scoreOf(new NnueEvaluator(net), fen), scoreOf(hybrid, fen), fen);
        }
    }

    @Test
    @DisplayName("taking the last piece and taking it back gives the network its score again")
    void takingTheLastPieceAndBack() {
        String fen = "4k3/8/8/8/8/8/r7/R3K3 w - - 0 1";
        Position pos = Position.fromFen(fen);
        HybridEvaluator hybrid = new HybridEvaluator(new NnueEvaluator(net));
        hybrid.reset(pos);
        int before = hybrid.evaluate(pos);

        int capture = pos.parseUci("a1a2");
        pos.makeMove(capture);
        hybrid.onMake(pos, capture);
        assertTrue(HybridEvaluator.hasBareKing(pos));
        pos.unmakeMove();
        hybrid.onUnmake();

        assertEquals(before, hybrid.evaluate(pos));
        assertEquals(scoreOf(new NnueEvaluator(net), fen), before);
    }

    @Test
    @DisplayName("keeps the network's running sums right through a lone-king ending and back out of it")
    void staysInSync() {
        Random random = new Random(5);
        for (int game = 0; game < 30; game++) {
            Position pos = Position.fromFen("4k3/pppppppp/8/8/8/8/PPPPPPPP/4K3 w - - 0 1");
            HybridEvaluator hybrid = new HybridEvaluator(new NnueEvaluator(net));
            NnueEvaluator reference = new NnueEvaluator(net);
            hybrid.reset(pos);
            int made = 0;
            for (; made < 120; made++) {
                int[] moves = pos.legalMoves();
                if (moves.length == 0) break;
                int move = moves[random.nextInt(moves.length)];
                pos.makeMove(move);
                hybrid.onMake(pos, move);
            }
            // whatever happened, taking it all back gives the network's own score for the start again
            for (; made > 0; made--) {
                pos.unmakeMove();
                hybrid.onUnmake();
            }
            reference.reset(pos);
            assertEquals(reference.evaluate(pos), hybrid.evaluate(pos), "game " + game);
        }
    }
}
