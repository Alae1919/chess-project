package com.chess.engine.uci;

import com.chess.engine.core.eval.Evaluators;

import java.io.IOException;

/**
 * Runs the engine on standard input and output, speaking UCI, so any chess GUI or match tool
 * can play it:
 *
 * <pre>java -cp chess-engine/target/classes com.chess.engine.uci.UciMain</pre>
 *
 * The engine core has no Spring dependency, so the compiled classes are all it needs.
 */
public final class UciMain {

    private UciMain() {}

    public static void main(String[] args) throws IOException {
        UciEngine engine = new UciEngine(System.in, System.out);
        // "bench [depth] [classical | network file]" on the command line runs it once and exits, like Stockfish's
        if (args.length > 0 && args[0].equals("bench")) {
            int depth = args.length > 1 ? Integer.parseInt(args[1]) : 8;
            if (args.length > 2) {
                boolean classical = args[2].equalsIgnoreCase("classical");
                System.out.println(Evaluators.configure(classical ? Evaluators.Mode.CLASSICAL : Evaluators.Mode.NNUE,
                        classical ? null : java.nio.file.Path.of(args[2])));
            }
            Bench.Result r = Bench.run(depth, 16, Evaluators.supplier());
            System.out.println("Nodes searched: " + r.nodes());
            System.out.println("Nodes/second: " + r.nodesPerSecond());
            return;
        }
        engine.run();
    }
}
