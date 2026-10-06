package com.chess.engine.core.eval;

import com.chess.engine.core.eval.nnue.NnueEvaluator;
import com.chess.engine.core.eval.nnue.NnueNetwork;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Chooses the evaluation every new search uses: the trained network if there is one, else the
 * hand-written classical evaluation. The network comes from a file named in the configuration,
 * or else the one bundled with the application; if neither can be loaded the engine plays on
 * with the classical evaluation rather than failing.
 *
 * The network itself is immutable and shared; each call to {@link #create()} makes a new
 * evaluator with its own running state, for one search's use.
 */
public final class Evaluators {

    public enum Mode { NNUE, CLASSICAL }

    private static Mode mode = Mode.NNUE;
    private static NnueNetwork configured;        // from a file
    private static NnueNetwork bundled;           // from the classpath
    private static boolean bundledTried;
    private static String problem;                // why the network in use is not the one asked for, or null

    private Evaluators() {}

    /**
     * Sets how new evaluators are made.
     *
     * @param file a network file to use instead of the bundled one, or null
     * @return a one-line description of what will be used, mentioning any problem
     */
    public static synchronized String configure(Mode wanted, Path file) {
        mode = wanted;
        configured = null;
        problem = null;
        if (wanted == Mode.NNUE && file != null) {
            try {
                configured = NnueNetwork.load(file);
            } catch (IOException | IllegalArgumentException e) {
                problem = "could not load network " + file + " (" + e.getMessage() + ")";
            }
        }
        return describe();
    }

    /** A new evaluator, for one search. */
    public static Evaluator create() {
        NnueNetwork net = network();
        return net == null ? new ClassicalEvaluator() : new NnueEvaluator(net);
    }

    public static Supplier<Evaluator> supplier() {
        return Evaluators::create;
    }

    public static synchronized String describe() {
        NnueNetwork net = networkLocked();
        String used = net == null ? "classical evaluation" : "neural network evaluation (" + net.hidden() + " hidden units)";
        String note = problem;
        if (mode == Mode.NNUE && net == null && note == null) note = "no network is bundled";
        return note == null ? used : used + ": " + note;
    }

    private static synchronized NnueNetwork network() {
        return networkLocked();
    }

    private static NnueNetwork networkLocked() {
        if (mode == Mode.CLASSICAL) return null;
        if (configured != null) return configured;
        if (!bundledTried) {
            bundledTried = true;
            try {
                bundled = NnueNetwork.loadBundled();
            } catch (IOException | IllegalArgumentException e) {
                problem = "the bundled network is unusable (" + e.getMessage() + ")";
            }
        }
        return bundled;
    }
}
