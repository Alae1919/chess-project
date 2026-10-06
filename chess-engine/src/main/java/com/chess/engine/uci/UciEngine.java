package com.chess.engine.uci;

import com.chess.engine.core.Move;
import com.chess.engine.core.Perft;
import com.chess.engine.core.Position;
import com.chess.engine.core.eval.Evaluator;
import com.chess.engine.core.eval.Evaluators;
import com.chess.engine.core.search.SearchInfo;
import com.chess.engine.core.search.SearchLimits;
import com.chess.engine.core.search.SearchResult;
import com.chess.engine.core.search.Searcher;
import com.chess.engine.player.AiLevel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The engine speaking the Universal Chess Interface, the text protocol chess GUIs and
 * match tools use to talk to an engine. Besides the standard commands it understands
 * {@code bench} (a fixed search over fixed positions, whose node count changes whenever the
 * search does) and {@code perft} (leaf counts, for checking move generation).
 *
 * Input is read on the calling thread; a search runs on its own thread, so {@code stop},
 * {@code isready} and {@code quit} are answered while it thinks.
 */
public final class UciEngine {

    public static final String NAME = "REXCHESS";
    public static final String AUTHOR = "REXCHESS";

    private final BufferedReader in;
    private final PrintWriter out;

    private Position position = Position.startPosition();
    private Searcher searcher;
    private Supplier<Evaluator> evaluatorFactory = Evaluators.supplier();
    private int hashMegabytes = 16;
    private int multiPv = 1;
    /** 0 = full strength; 1 to 6 plays like that level of the app's AI (see AiLevel). */
    private int level = 0;
    private Evaluators.Mode evalMode = Evaluators.Mode.NNUE;
    private java.nio.file.Path evalFile;
    private final Random random = new Random();
    private Thread worker;

    public UciEngine(InputStream input, OutputStream output) {
        this.in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = new PrintWriter(output, true, StandardCharsets.UTF_8);
    }

    /** Reads commands until {@code quit} or the end of the input. */
    public void run() throws IOException {
        for (String line; (line = in.readLine()) != null; ) {
            if (!handle(line.trim())) break;
        }
        stopSearch();
    }

    // ================================================================================================
    //  Commands
    // ================================================================================================

    /** @return false when the engine should exit */
    boolean handle(String line) {
        if (line.isEmpty()) return true;
        String[] tokens = line.split("\\s+");
        switch (tokens[0]) {
            case "uci" -> identify();
            case "isready" -> send("readyok");
            case "ucinewgame" -> { stopSearch(); searcher().newGame(); position = Position.startPosition(); }
            case "setoption" -> setOption(tokens);
            case "position" -> setPosition(tokens);
            case "go" -> go(tokens);
            case "stop" -> stopSearch();
            case "bench" -> bench(tokens.length > 1 ? parseInt(tokens[1], 8) : 8);
            case "perft" -> perft(tokens.length > 1 ? parseInt(tokens[1], 4) : 4);
            case "d" -> send("Fen: " + position.toFen() + "\nKey: " + Long.toHexString(position.key()));
            case "quit" -> { return false; }
            default -> { /* unknown commands are ignored, as the protocol asks */ }
        }
        return true;
    }

    private void identify() {
        send("id name " + NAME);
        send("id author " + AUTHOR);
        send("option name Hash type spin default 16 min 1 max 1024");
        send("option name MultiPV type spin default 1 min 1 max 16");
        send("option name Level type spin default 0 min 0 max 6");
        send("option name Eval type combo default nnue var nnue var classical");
        send("option name EvalFile type string default <bundled>");
        send("uciok");
    }

    private void setOption(String[] t) {
        // setoption name <words...> [value <words...>]
        int nameAt = indexOf(t, "name"), valueAt = indexOf(t, "value");
        if (nameAt < 0) return;
        String name = String.join(" ", Arrays.copyOfRange(t, nameAt + 1, valueAt < 0 ? t.length : valueAt)).toLowerCase();
        String value = valueAt < 0 ? "" : String.join(" ", Arrays.copyOfRange(t, valueAt + 1, t.length));
        switch (name) {
            case "hash" -> { hashMegabytes = clamp(parseInt(value, 16), 1, 1024); searcher = null; }
            case "multipv" -> multiPv = clamp(parseInt(value, 1), 1, 16);
            case "level" -> level = clamp(parseInt(value, 0), 0, 6);
            case "eval" -> chooseEvaluation(value.equalsIgnoreCase("classical") ? Evaluators.Mode.CLASSICAL : Evaluators.Mode.NNUE, evalFile);
            case "evalfile" -> chooseEvaluation(evalMode, value.isBlank() || value.startsWith("<") ? null : java.nio.file.Path.of(value));
            default -> send("info string unknown option " + name);
        }
    }

    private void chooseEvaluation(Evaluators.Mode mode, java.nio.file.Path file) {
        evalMode = mode;
        evalFile = file;
        send("info string " + Evaluators.configure(mode, file));
        searcher = null;   // a new evaluator is made with the next search
    }

    private void setPosition(String[] t) {
        int i = 1;
        Position next;
        try {
            if (i < t.length && t[i].equals("startpos")) {
                next = Position.startPosition();
                i++;
            } else if (i < t.length && t[i].equals("fen")) {
                StringBuilder fen = new StringBuilder();
                for (i++; i < t.length && !t[i].equals("moves"); i++) fen.append(t[i]).append(' ');
                next = Position.fromFen(fen.toString().trim());
            } else {
                return;
            }
        } catch (IllegalArgumentException e) {
            send("info string bad position: " + e.getMessage());
            return;
        }
        if (i < t.length && t[i].equals("moves")) {
            for (i++; i < t.length; i++) {
                int move = next.parseUci(t[i]);
                if (move == Move.NONE) {
                    send("info string illegal move " + t[i]);
                    break;
                }
                next.makeMove(move);
            }
        }
        position = next;
    }

    private void go(String[] t) {
        stopSearch();
        long depth = 0, nodes = 0, moveTime = 0, wtime = 0, btime = 0, winc = 0, binc = 0, movesToGo = 0;
        for (int i = 1; i < t.length; i++) {
            switch (t[i]) {
                case "depth" -> depth = number(t, ++i);
                case "nodes" -> nodes = number(t, ++i);
                case "movetime" -> moveTime = number(t, ++i);
                case "wtime" -> wtime = number(t, ++i);
                case "btime" -> btime = number(t, ++i);
                case "winc" -> winc = number(t, ++i);
                case "binc" -> binc = number(t, ++i);
                case "movestogo" -> movesToGo = number(t, ++i);
                case "perft" -> { perft((int) number(t, ++i)); return; }
                default -> { /* infinite, ponder and the rest: no limit */ }
            }
        }

        final Position root = position.copy();
        final SearchLimits limits;
        if (level > 0) {
            // play like that level of the app's AI, still never overrunning the clock
            long remaining = root.sideToMove() == Position.WHITE ? wtime : btime;
            long increment = root.sideToMove() == Position.WHITE ? winc : binc;
            limits = AiLevel.of(level).limits(remaining, increment);
        } else {
            limits = new SearchLimits((int) depth, nodes, moveTime, wtime, btime, winc, binc, (int) movesToGo, multiPv);
        }
        final int candidates = level > 0 ? AiLevel.of(level).multiPv() : multiPv;
        final Searcher s = searcher();

        worker = new Thread(() -> {
            SearchResult result = s.search(root, limits.withMultiPv(candidates), this::report);
            int best = level > 0 ? AiLevel.of(level).choose(result, random) : result.bestMove();
            send("bestmove " + Move.toUci(best));
        }, "uci-search");
        worker.setDaemon(true);
        worker.start();
    }

    private void report(SearchInfo info) {
        StringBuilder sb = new StringBuilder("info depth ").append(info.depth());
        if (multiPv > 1) sb.append(" multipv ").append(info.multiPvIndex());
        sb.append(info.isMate() ? " score mate " + info.mateIn() : " score cp " + info.score());
        sb.append(" nodes ").append(info.nodes())
          .append(" nps ").append(info.nodesPerSecond())
          .append(" hashfull ").append(info.hashFullPermille())
          .append(" time ").append(info.timeMs())
          .append(" pv");
        for (int m : info.pv()) sb.append(' ').append(Move.toUci(m));
        send(sb.toString());
    }

    private void perft(int depth) {
        long start = System.nanoTime();
        var divide = Perft.divide(position.copy(), Math.max(1, depth));
        long total = 0;
        for (var e : divide.entrySet()) {
            send(e.getKey() + ": " + e.getValue());
            total += e.getValue();
        }
        long ms = Math.max(1, (System.nanoTime() - start) / 1_000_000);
        send("\nNodes searched: " + total);
        send("info string perft " + total + " nodes in " + ms + " ms");
    }

    private void bench(int depth) {
        Bench.Result r = Bench.run(depth, hashMegabytes, evaluatorFactory);
        send("Nodes searched: " + r.nodes());
        send("Time: " + r.millis() + " ms");
        send("Nodes/second: " + r.nodesPerSecond());
    }

    // ================================================================================================
    //  Helpers
    // ================================================================================================

    private Searcher searcher() {
        if (searcher == null) searcher = new Searcher(evaluatorFactory.get(), hashMegabytes);
        return searcher;
    }

    private void stopSearch() {
        if (worker == null) return;
        if (searcher != null) searcher.stop();
        try {
            worker.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        worker = null;
    }

    private synchronized void send(String line) {
        out.println(line);
        out.flush();
    }

    private static int indexOf(String[] tokens, String word) {
        for (int i = 0; i < tokens.length; i++) if (tokens[i].equals(word)) return i;
        return -1;
    }

    private static long number(String[] t, int i) {
        return i < t.length ? parseInt(t[i], 0) : 0;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return (int) Math.min(Integer.MAX_VALUE, Long.parseLong(s));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
