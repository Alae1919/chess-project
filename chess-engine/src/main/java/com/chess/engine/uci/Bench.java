package com.chess.engine.uci;

import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluator;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.Searcher;

import java.util.List;
import java.util.function.Supplier;

/**
 * Searches a fixed list of positions to a fixed depth and adds up the nodes. The search is
 * deterministic, so the node count is a signature of the search itself: it changes whenever
 * the search or the evaluation does, which makes an unintended change visible, and the speed
 * is the nodes per second over the run.
 */
public final class Bench {

    private Bench() {}

    public record Result(long nodes, long millis) {
        public long nodesPerSecond() { return millis <= 0 ? 0 : nodes * 1000 / millis; }
    }

    /** Openings, middlegames and endgames, tactical and quiet. */
    public static final List<String> POSITIONS = List.of(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
        "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
        "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
        "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
        "rnbqkb1r/pp2pppp/3p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R b KQkq - 0 5",
        "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 5",
        "r2q1rk1/ppp2ppp/2np1n2/2b1p1B1/2B1P1b1/2NP1N2/PPP2PPP/R2Q1RK1 w - - 0 8",
        "rnbqkbnr/ppp1pppp/8/3p4/3PP3/8/PPP2PPP/RNBQKBNR b KQkq e3 0 2",
        "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R b KQ - 0 9",
        "2r2rk1/1p1nqppp/p2pbn2/4p3/4P3/1NN1BP2/PPPQ2PP/2KR1B1R w - - 0 13",
        "r1b2rk1/2q1bppp/p2p1n2/np2p3/3NP3/1BN1B3/PPP2PPP/R2Q1RK1 w - - 0 12",
        "4r1k1/pp3ppp/8/3q4/3P4/2P2Q2/PP3PPP/4R1K1 w - - 0 1",
        "3r2k1/pp3ppp/2p5/8/4n3/2P2N2/PP3PPP/3R2K1 w - - 0 1",
        "8/8/4k3/8/8/4K3/4P3/8 w - - 0 1",
        "8/5pk1/6p1/8/8/6P1/5PK1/8 w - - 0 1",
        "6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1",
        "8/3k4/8/3P4/3K4/8/8/8 w - - 0 1",
        "2k5/8/1K6/8/8/8/8/7Q w - - 0 1",
        "8/8/8/4k3/8/3K4/3P4/8 b - - 0 1",
        "r5k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
        "5rk1/pp3ppp/2p5/8/8/2P5/PP3PPP/3R2K1 w - - 0 1",
        "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
        "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
        "8/8/1p6/p1p5/P1P1k3/1P6/5K2/8 w - - 0 1",
        "6k1/6p1/5p2/8/8/5P2/6P1/6K1 w - - 0 1",
        "r3r1k1/pp3pbp/1qp3p1/2B5/2BP2b1/Q1n2N2/P4PPP/3RR1K1 w - - 0 1",
        "2rr2k1/pp2bppp/2n1pn2/q7/3P4/2N1PN2/PP2BPPP/R2Q1RK1 w - - 0 1",
        "1r4k1/5ppp/p1p5/2P5/1P6/P4P2/6PP/4R1K1 w - - 0 1",
        "n1n1k3/8/8/8/8/8/8/4K3 w - - 0 1"
    );

    public static Result run(int depth, int hashMegabytes, Supplier<Evaluator> evaluator) {
        long nodes = 0;
        long start = System.nanoTime();
        for (String fen : POSITIONS) {
            Searcher searcher = new Searcher(evaluator.get(), hashMegabytes);
            nodes += searcher.search(Position.fromFen(fen), SearchLimits.depth(depth)).nodes();
        }
        return new Result(nodes, (System.nanoTime() - start) / 1_000_000);
    }
}
